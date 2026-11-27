package com.capswriter.offline.service

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.Toast
import com.capswriter.offline.R
import com.capswriter.offline.util.AppLog

/**
 * Writes text into the focused edit field.
 *
 * Different apps expose their inputs in very different ways. This tries, in order:
 *
 *  1. the focused input node of every application window;
 *  2. a focused node's editable parent;
 *  3. the largest editable node in the active window;
 *  4. ACTION_SET_TEXT, then ACTION_PASTE on the same node.
 *
 * Some apps (Flutter/WebView/remote UIs) expose no writable accessibility node at all.
 * Without becoming the active IME or using root/Shizuku there is no API that can type
 * into those fields; the clipboard remains the only safe fallback.
 */
object TextInserter {

    fun insert(service: AccessibilityService, text: String, expectedPackage: String?): Boolean {
        if (text.isBlank()) return false

        val imePackage = currentImePackage(service)
        val rawActiveRoot = runCatching { service.rootInActiveWindow }.getOrNull()
        // On some keyboards rootInActiveWindow can point at the IME window. Never use
        // the keyboard's own search box as the insertion target.
        val activeRoot = rawActiveRoot?.takeIf {
            val pkg = it.packageName?.toString()
            imePackage == null || pkg != imePackage
        }
        val roots = collectRoots(service, activeRoot, imePackage)
        val candidates = collectCandidates(roots, activeRoot)

        // Never write into a background app's window. If the active window exposes no
        // writable node, copying is safer than guessing.
        val activePackage = activeRoot?.packageName?.toString()
        val activeCandidates = if (activePackage != null) {
            candidates.filter {
                val pkg = it.packageName?.toString()
                pkg == null || pkg == activePackage
            }
        } else {
            emptyList()
        }
        // If the active window is a non-input surface (our overlay, a system surface,
        // or a window whose EditText is hidden), fall back to all application windows.
        // Otherwise a perfectly good WeChat input node can be discarded solely because
        // the active root was not WeChat's window.
        val expectedCandidates = if (expectedPackage != null) {
            candidates.filter {
                val pkg = it.packageName?.toString()
                pkg == null || pkg == expectedPackage
            }
        } else {
            emptyList()
        }
        val pool = when {
            activeCandidates.any { it.isWritable() } -> activeCandidates
            expectedCandidates.any { it.isWritable() } -> expectedCandidates
            else -> candidates
        }

        val node = pool.firstOrNull { it.isWritable() }
            ?: pool.firstOrNull()
        if (node == null) {
            logCandidates(service, activePackage, candidates)
            val pastedAtRoot = tryPasteAtRoot(service, activeRoot, text)
            if (pastedAtRoot) {
                toast(service, service.getString(R.string.toast_inserted))
                return true
            }
            copy(service, text, service.getString(R.string.toast_no_focus))
            return true
        }

        if (node.isPassword) {
            copy(service, text, service.getString(R.string.toast_no_focus))
            return true
        }

        val nodePackage = node.packageName?.toString()
        // activePackage filtering above already guarantees we are looking at the
        // currently active app. Only when there is no active root do we need the
        // original recording target as an extra safety net.
        if (activePackage == null && expectedPackage != null && nodePackage != null &&
            nodePackage != expectedPackage && !node.isWritable()
        ) {
            copy(service, text, service.getString(R.string.toast_switched_app))
            return true
        }

        // QQ and most other apps: direct text insertion first. Never force a clipboard
        // round-trip for an app that can accept ACTION_SET_TEXT.
        if (trySetText(service, node, text)) {
            AppLog.add(service, "Inserted via ACTION_SET_TEXT (${nodePackage ?: "unknown"})")
            toast(service, service.getString(R.string.toast_inserted))
            return true
        }
        if (tryPaste(service, node, text)) {
            AppLog.add(service, "Inserted via ACTION_PASTE (${nodePackage ?: "unknown"})")
            toast(service, service.getString(R.string.toast_inserted))
            return true
        }

        logCandidates(service, activePackage, candidates)
        copy(service, text, service.getString(R.string.toast_insert_failed))
        return true
    }

    private fun collectRoots(
        service: AccessibilityService,
        activeRoot: AccessibilityNodeInfo?,
        imePackage: String?,
    ): List<AccessibilityNodeInfo> {
        val roots = ArrayList<AccessibilityNodeInfo>()
        fun add(root: AccessibilityNodeInfo?) {
            if (root == null) return
            if (roots.any { it.windowId == root.windowId }) return
            roots.add(root)
        }

        add(activeRoot)

        val windows = runCatching { service.windows.orEmpty() }.getOrDefault(emptyList())
        for (window in windows) {
            // Only application windows. The keyboard's own search field must not become
            // the insertion target.
            if (window.type != AccessibilityWindowInfo.TYPE_APPLICATION) continue
            val root = runCatching { window.root }.getOrNull() ?: continue
            if (imePackage != null && root.packageName?.toString() == imePackage) continue
            add(root)
        }
        return roots
    }

    private fun currentImePackage(service: AccessibilityService): String? =
        runCatching {
            Settings.Secure.getString(
                service.contentResolver,
                Settings.Secure.DEFAULT_INPUT_METHOD,
            )?.substringBefore('/')?.takeIf { it.isNotBlank() }
        }.getOrNull()

    private fun collectCandidates(
        roots: List<AccessibilityNodeInfo>,
        activeRoot: AccessibilityNodeInfo?,
    ): List<AccessibilityNodeInfo> {
        val focused = ArrayList<AccessibilityNodeInfo>()
        val editable = ArrayList<AccessibilityNodeInfo>()

        val orderedRoots = ArrayList<AccessibilityNodeInfo>()
        if (activeRoot != null) orderedRoots.add(activeRoot)
        for (root in roots) {
            if (orderedRoots.none { it.windowId == root.windowId }) orderedRoots.add(root)
        }

        for (root in orderedRoots) {
            val focus = runCatching {
                root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            }.getOrNull()
            if (focus != null) {
                addUnique(focused, focus)
                var parent = runCatching { focus.parent }.getOrNull()
                var depth = 0
                while (parent != null && depth < 8) {
                    if (parent.isEditable) addUnique(focused, parent)
                    parent = runCatching { parent?.parent }.getOrNull()
                    depth++
                }
            }
            collectEditable(root, 0, editable)
        }

        // The active window's editable fields are better than a background window's.
        val activePackage = activeRoot?.packageName?.toString()
        val sortedEditable = editable.sortedWith(
            compareByDescending<AccessibilityNodeInfo> {
                activePackage != null && it.packageName?.toString() == activePackage
            }.thenByDescending { nodeBoundsArea(it) }
        )

        val result = ArrayList<AccessibilityNodeInfo>()
        for (node in focused + sortedEditable) {
            if (result.none { sameNode(it, node) }) {
                result.add(node)
            }
        }
        return result
    }

    private fun collectEditable(
        node: AccessibilityNodeInfo?,
        depth: Int,
        out: MutableList<AccessibilityNodeInfo>,
    ) {
        if (node == null || depth > 30) return
        if (node.isVisibleToUser && node.isEnabled && node.looksLikeInput() && !node.isPassword) {
            addUnique(out, node)
        }
        for (index in 0 until node.childCount) {
            val child = runCatching { node.getChild(index) }.getOrNull() ?: continue
            collectEditable(child, depth + 1, out)
        }
    }

    private fun addUnique(list: MutableList<AccessibilityNodeInfo>, node: AccessibilityNodeInfo) {
        if (list.any { sameNode(it, node) }) return
        list.add(node)
    }

    private fun sameNode(left: AccessibilityNodeInfo, right: AccessibilityNodeInfo): Boolean =
        left.windowId == right.windowId && left.hashCode() == right.hashCode()

    private fun trySetText(
        service: AccessibilityService,
        node: AccessibilityNodeInfo,
        text: String,
    ): Boolean {
        focusIfNeeded(node)

        val existing = node.text?.toString().orEmpty()
        var start = node.textSelectionStart
        var end = node.textSelectionEnd
        if (start !in 0..existing.length) start = existing.length
        if (end !in start..existing.length) end = start

        val result = buildString {
            append(existing.substring(0, start))
            append(text)
            append(existing.substring(end))
        }
        val cursor = start + text.length

        val performed = runCatching {
            val arguments = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, result)
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, cursor)
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, cursor)
            }
            node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
        }.getOrDefault(false)

        // Do not verify via refresh(): many custom views update asynchronously and a
        // false negative would paste the same text twice. performAction() returning
        // true is the best signal the accessibility API gives us.
        return performed
    }

    private fun tryPaste(
        service: AccessibilityService,
        node: AccessibilityNodeInfo,
        text: String,
    ): Boolean {
        focusIfNeeded(node)

        val clipboard = service.getSystemService(ClipboardManager::class.java)
        val previous = runCatching { clipboard.primaryClip }.getOrNull()

        val pasted = runCatching {
            clipboard.setPrimaryClip(ClipData.newPlainText("CapsWriter", text))
            node.performAction(AccessibilityNodeInfo.ACTION_PASTE)
        }.getOrDefault(false)

        if (!pasted) return false

        if (previous != null) {
            Handler(Looper.getMainLooper()).postDelayed({
                runCatching { clipboard.setPrimaryClip(previous) }
            }, 1_500)
        }
        return true
    }

    private fun tryPasteAtRoot(
        service: AccessibilityService,
        root: AccessibilityNodeInfo?,
        text: String,
    ): Boolean {
        if (root == null) return false
        if (!supports(root, AccessibilityNodeInfo.ACTION_PASTE)) return false
        val clipboard = service.getSystemService(ClipboardManager::class.java)
        return runCatching {
            clipboard.setPrimaryClip(ClipData.newPlainText("CapsWriter", text))
            root.performAction(AccessibilityNodeInfo.ACTION_PASTE)
        }.getOrDefault(false)
    }

    private fun focusIfNeeded(node: AccessibilityNodeInfo) {
        if (node.isFocused) return
        if (supports(node, AccessibilityNodeInfo.ACTION_FOCUS)) {
            runCatching { node.performAction(AccessibilityNodeInfo.ACTION_FOCUS) }
        }
        if (node.looksLikeInput() && supports(node, AccessibilityNodeInfo.ACTION_CLICK)) {
            runCatching { node.performAction(AccessibilityNodeInfo.ACTION_CLICK) }
        }
    }

    private fun AccessibilityNodeInfo.isWritable(): Boolean = looksLikeInput()

    private fun AccessibilityNodeInfo.looksLikeInput(): Boolean {
        if (isEditable) return true
        val name = className?.toString().orEmpty()
        if (name.contains("EditText", ignoreCase = true) ||
            name.contains("TextInput", ignoreCase = true) ||
            name.contains("TextField", ignoreCase = true)
        ) {
            return true
        }
        return supports(this, AccessibilityNodeInfo.ACTION_SET_TEXT) ||
            supports(this, AccessibilityNodeInfo.ACTION_PASTE)
    }

    private fun logCandidates(
        service: AccessibilityService,
        activePackage: String?,
        candidates: List<AccessibilityNodeInfo>,
    ) {
        val summary = candidates.take(6).joinToString(" | ") { node ->
            val name = node.className?.toString()?.substringAfterLast('.') ?: "?"
            val pkg = node.packageName?.toString() ?: "?"
            "$name@$pkg editable=${node.isEditable} focused=${node.isFocused}"
        }.ifBlank { "no candidates" }
        AppLog.add(service, "Insertion diagnostics active=${activePackage ?: "null"}: $summary")
    }

    private fun supports(node: AccessibilityNodeInfo, action: Int): Boolean =
        runCatching {
            node.actionList.any { it.id == action }
        }.getOrDefault(false)

    private fun nodeBoundsArea(node: AccessibilityNodeInfo): Long {
        val rect = android.graphics.Rect()
        runCatching { node.getBoundsInScreen(rect) }
        return rect.width().toLong() * rect.height()
    }

    private fun copy(service: AccessibilityService, text: String, message: String) {
        val clipboard = service.getSystemService(ClipboardManager::class.java)
        runCatching {
            clipboard.setPrimaryClip(ClipData.newPlainText("CapsWriter", text))
        }
        AppLog.add(service, "Direct insertion failed; copied to clipboard")
        toast(service, message)
    }

    private fun toast(service: AccessibilityService, text: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(service, text, Toast.LENGTH_SHORT).show()
        }
    }
}
