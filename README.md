# CapsWriter 安卓离线语音输入

[中文](#capswriter-安卓离线语音输入) · [English](#english)
<div align="center">
  <img src="./images/images-removebg-preview.png" alt="CapsWriter Android" width="300" />
</div>
> **[CapsWriter-Offline](https://github.com/HaujetZhao/CapsWriter-Offline)的非官方 Android 客户端**，感谢原作者的开源精神；协议、配置项和交互灵感来自上游项目。
>
> GitHub 仓库：[xgh9191029/CapsWriter-Android](https://github.com/xgh9191029/CapsWriter-Android)

一个用 **Kotlin** 写的 Android 客户端，连接局域网里的 [CapsWriter-Offline](https://github.com/HaujetZhao/CapsWriter-Offline) 服务端，把语音识别结果直接写进当前输入框。

它的交互刻意做得很轻：**不替换你的输入法**，而是在输入法弹出时，于输入法按键上方显示一个**完全透明的悬浮触摸区**。按住说话、松手上屏；位置和大小都可以手动校准，对齐到输入法自带的麦克风键或任意按键上。
## 功能

- 主页只保留**服务端地址**和**悬浮窗设置**；权限、识别输出、测试、热词、使用指南和诊断日志收进独立的“设置”页。
- 跟随系统暗黑主题（Android 10+ 的深色模式）。
- 每次启动自动检查 GitHub Release 更新；发现新版本时弹窗提供“不再提醒 / 暂不更新 / GitHub”三个选项。
- 手动填写 CapsWriter-Offline 服务端地址，支持 `IP`、`IP:端口`、`ws://`、`wss://`，默认端口 `6016`（旧版服务端用 `6006`）。
- 通过无障碍服务检测输入法窗口，键盘弹出时显示透明悬浮窗，键盘收起时自动移除。
- 悬浮窗使用 `TYPE_ACCESSIBILITY_OVERLAY`，层级在输入法之上；**不是**“显示在其他应用上层”权限，普通应用悬浮窗会被输入法盖住。
- 悬浮窗默认完全透明，只保留触摸区域；录音/识别状态由震动和常驻通知反馈。
- 按住说话 / 点击开关两种模式；按住模式下短按可穿透到输入法自己的语音键，长按由 CapsWriter 录音。
- 可切换是否从最近任务列表隐藏，默认隐藏，避免误清后台。
- 可选**麦克风预热（低延迟）**：输入法显示期间保持麦克风预热，按下时附带最近约 500ms 预录，减少启动延迟和第一个字被吞掉；默认关闭，会增加耗电，麦克风指示器会保持显示。
- 识别语言可选自动/中文/英文/日文；可调最短录音时长；可清理末尾标点；可保存 WAV 录音。
- 校准模式：显示蓝色虚线框，**宽度和高度分别调整**，拖动到输入法按键位置后保存；位置和大小按 **输入法 + 横竖屏** 分别记忆，Gboard、搜狗、小艺等不同键盘可以各自适配。
- 录音直接以 16 kHz 单声道 float32 PCM 通过 WebSocket 流式发送，符合 CapsWriter-Offline v2.x 协议。
- 客户端热词：支持识别提示词（发送到服务端 `context`）以及 `错词=>正确词` 的本地替换，详见下文。
- 识别结果通过无障碍 `ACTION_SET_TEXT` 写入光标处，失败后再尝试 `ACTION_PASTE`。
- 如果识别期间切换了 App，优先写入新 App 当前的可编辑框；没有可写节点时才复制到剪贴板。
- 所有配置本地保存。录音默认不保存，开启后写到应用专属目录；不上传任何分析数据。

## 环境要求

- Android 8.0（API 26）及以上。
- 电脑上已启动 CapsWriter-Offline 服务端（v2.x 默认 `6016`，v1.x 常见 `6006`）。
- 手机和电脑在同一局域网，电脑防火墙放行对应 TCP 端口；服务端监听地址需允许局域网访问（例如 `0.0.0.0`）。
- 构建：JDK 17、Android SDK Platform 35、Gradle 8.9（项目自带 AGP 8.7.3 / Kotlin 2.0.21 配置）。

## 构建

```bash
# 指向你自己的 JDK / Android SDK
export JAVA_HOME=/path/to/jdk-17
export ANDROID_HOME=/path/to/Android/Sdk

./gradlew assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

Windows PowerShell：

```powershell
$env:JAVA_HOME = "C:\path\to\jdk-17"
$env:ANDROID_HOME = "C:\path\to\Android\Sdk"
.\gradlew.bat assembleDebug
```
## 首次使用

1. 打开 App，填入电脑的局域网地址，例如 `192.168.1.100:6016`，点 **保存并测试**（主页只保留服务端和悬浮窗设置，其他配置点右上角 **设置**）。
2. 点右上角 **设置**，授予 **麦克风** 和 **通知** 权限。
3. 在设置页点 **去开启**，在系统无障碍设置里开启 **CapsWriter 悬浮语音输入**。
   - Android 13 及以上，侧载应用的无障碍开关可能被“受限设置”拦住。到系统“应用信息 → CapsWriter 语音输入 → 右上角菜单”里允许受限制的设置，再回来开启。
4. 在设置页打开 **启用悬浮语音**。App 会在前台启动一个麦克风类型的前台服务；这一步必须在 App 可见时完成（Android 14 的后台麦克风限制）。
   - 从旧版更新到 1.5.0 或更高版本后，请把无障碍服务**关闭再开启一次**，让短按穿透所需的 `canPerformGestures` 生效。
5. 回到主页点 **校准位置**。App 会切到设置页的测试输入框并弹出键盘；把蓝色虚线框拖到输入法的语音键位置，分别调好宽度/高度后点 **保存**。换一个输入法（或横屏）后再各校准一次，App 会自动记住对应配置。
6. 回到任意聊天/笔记 App，唤起输入法，按住悬浮窗位置说话，松手后文字会写入当前光标处。短按会转发给输入法自己的语音键（可在主页悬浮窗设置关闭“短按穿透”）。
7. 可选：在设置页“客户端热词”里填入常用人名、地名或 `错词=>正确词`，保存后下一次识别生效。
8. 可选：在设置页“识别与输出”里选择语言、最短录音时长、末尾标点清理和 WAV 录音。
9. 可选：在主页悬浮窗设置开启 **麦克风预热（低延迟）**。开启后输入法显示期间麦克风保持预热，按下时补发最近约 500ms 音频；键盘收起约 10 秒后释放。默认关闭，会增加耗电。
10. 启动时若 GitHub 有更新的 Release，会弹出更新提示；“不再提醒”只跳过当前版本，“暂不更新”下次启动仍会提示，“GitHub”用默认浏览器打开发布页。

> 应用默认从最近任务列表隐藏；按 Home 后请通过通知或桌面图标回到应用。可在权限卡片关闭“隐藏最近任务”。

> 悬浮窗在视觉上完全不可见，位置就是校准框的位置。找不到时，回到 App 点“校准位置”重新看一遍；或先点“重置位置”。


### 客户端热词

在主界面“客户端热词”中每行写一个词：

```text
CapsWriter
蕲春
错词=>正确词
re:卡普[斯丝]=>Caps
```

- 不带 `=>` 的词会作为 `context` 发给服务端，适合人名、地名、专业术语。
- `错词=>正确词` 会同时加入 context，并在最终识别结果里做**字面替换**。
- `re:正则=>替换` 使用正则替换，替换文本支持 `$1` 分组。
- 字面替换按源词长度从长到短执行，避免“输入法”抢先替换“语音输入法”。
- 客户端替换在服务端识别完成之后执行，因此即使服务端引擎不支持 `context`，精确替换仍然有效。

### 末尾标点阈值
实际逻辑是 **语义单位数不超过 8 时去掉末尾标点，超过 8 时保留标点**，这样才能让长句保持正常标点。阈值可调 0–50：

- 8（默认）：不超过 8 个语义单位去标点。
- 0：总是去掉末尾标点（不启用阈值）。
- 50：只有很短句才会被去掉。
- 语义单位：一个中文字、一个英文单词、一个数字各算 1。
- 微信（`com.tencent.mm`）会跳过阈值强制去掉，对应 `trash_punc_apps=['WeiXin.exe']`。

### 上屏

1. 枚举活动窗口与所有应用窗口，收集 `FOCUS_INPUT` 节点、焦点节点的可编辑父节点、以及当前活动窗口内的可编辑节点。
2. 优先使用当前活动 App 的可编辑节点；不会写到后台 App 的输入框。
3. 在光标/选区处构造新文本，执行 `ACTION_SET_TEXT`。
4. 失败则把文字放入剪贴板并执行 `ACTION_PASTE`。
5. 没有可写节点、输入框禁止粘贴、或识别期间切换到的 App 没有输入框时，只复制到剪贴板并提示。

## 权限说明

| 权限 | 用途 |
| --- | --- |
| `RECORD_AUDIO` | 录音并发送给局域网服务端 |
| `INTERNET` / `ACCESS_NETWORK_STATE` | WebSocket 连接 |
| `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_MICROPHONE` | 按住说话/麦克风预热期间保持录音，Android 14 必需 |
| `POST_NOTIFICATIONS` | 显示“录音中/识别中”的常驻通知 |
| `VIBRATE` | 按下、松手的触觉反馈 |
| `BIND_ACCESSIBILITY_SERVICE` | 检测输入法、显示悬浮层、写入文字、短按穿透手势 |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | 可选的保活设置，避免国产 ROM 杀服务 |
| `REORDER_TASKS` / `GET_TASKS` | 动态切换“隐藏最近任务” |

App 不申请“显示在其他应用上层”，也不申请 root/Shizuku。

## 常见问题

**悬浮框没有出现**
- 确认“启用悬浮语音”已打开。
- 确认无障碍服务处于开启且已连接状态；部分 ROM 需要在最近任务里锁定 App。
- 键盘必须是真正的输入法窗口；外接物理键盘、或输入法使用了特殊全屏模式时，窗口检测可能不适用。
- 打开 App 后如果看到“后台服务未运行”，点一下悬浮区域会重新拉起 App；或直接回到 App。

**按下后没有反应**
- 检查麦克风权限和通知权限。
- 检查是否已经连接服务端
- 检查服务端地址是否可访问：手机浏览器打开 `http://电脑IP:6016` 不一定有网页，但至少不应连接被拒绝；更可靠的是看服务端日志是否出现客户端连接。
- Android 14 上如果服务不是从可见的 App 启动的，会被系统拒绝；请先打开 App 再试。

**识别成功但文字没进输入框**
- v1.5.0 会搜索活动窗口、焦点父节点和所有可编辑候选项，统一先尝试 `ACTION_SET_TEXT`，失败再 `ACTION_PASTE`，并保留 EditText/TextInput 类名兜底。QQ 正常情况下会直接写入，不经过剪贴板。
- 微信如果两个无障碍动作都失败，仍会复制到剪贴板；v1.4.0 已移除自动长按菜单方案，避免选中文字/替换选区的风险。
- 如果仍然只复制，查看 App 内“诊断日志”的最后一条“上屏诊断”，它会列出候选节点的类名、包名、是否 editable/focused。把这一行和 App 名发回来即可定位是“没找到节点”还是“节点拒绝动作”。
- 如果目标 App 完全不暴露可写输入节点（部分 WebView、Flutter、远程桌面、游戏内输入框），无障碍 API 无法直接注入文字，只能复制后手动粘贴。这是 Android 权限模型的限制，不是热词或服务端问题。
- 如果识别过程中切了 App，会优先写入新 App 当前的可编辑框；新 App 没有输入框时才复制。

**悬浮框位置偏了**
- 重新校准；位置和宽高按“输入法 + 横竖屏”分别保存，Gboard、搜狗等需要各自校准一次。
- 换输入法后悬浮框会自动加载对应配置；如果还没校准过，会使用默认位置。
- App 主界面的宽度/高度滑块会修改当前输入法的配置；位置以中心点保持，不会跳走。

**连接超时**
- 服务端默认只监听 `127.0.0.1` 时，手机无法访问；请改成 `0.0.0.0` 或对应局域网地址。
- 确认电脑防火墙放行 6016/TCP。
- 如果服务端是旧版，端口可能是 6006。
- 本项目支持 `ws://` 和带公网/受信任证书的 `wss://`；自签名证书默认会被 OkHttp 拒绝。

**Android 13+ 无法开启无障碍**
- 侧载应用会进入“受限设置”。打开系统设置里的应用信息，点右上角菜单选“允许受限制的设置”，然后再开启无障碍。
- 国产 ROM 可能还需要在“自启动/后台运行”里允许本应用，并关闭电池优化。

## 项目结构

```text
app/src/main/java/com/capswriter/offline/
├── audio/
│   ├── AudioCapture.kt          # 16 kHz float32 PCM 采集
│   ├── AudioPrewarmer.kt        # 麦克风预热与 500ms 预录缓冲
│   ├── AudioWavWriter.kt        # 可选 WAV 录音落盘
│   └── VoiceSession.kt          # 一次按住说话的完整生命周期
├── net/
│   ├── CapsWriterSocket.kt      # CapsWriter-Offline WebSocket 协议
│   ├── NetClient.kt             # OkHttp 单例
│   ├── ServerProbe.kt           # 连接测试
│   └── UpdateChecker.kt         # GitHub Release 更新检查
├── service/
│   ├── CapsWriterAccessibilityService.kt  # 输入法检测 + 文字上屏
│   ├── OverlayController.kt              # 透明悬浮窗、校准面板
│   ├── TextInserter.kt                   # ACTION_SET_TEXT / 粘贴
│   └── VoiceForegroundService.kt         # 麦克风前台服务
├── ui/MainActivity.kt
└── util/                        # 配置、状态、日志、地址解析、热词、输出处理
    ├── HotwordProcessor.kt      # 客户端热词解析与替换
    └── OutputTextProcessor.kt   # 末尾标点清理
```

## 隐私

音频只发送到你自己填写的 CapsWriter-Offline 服务端，不经过任何第三方服务器。录音默认不保存；开启“保存录音”后会写到应用专属目录。日志不会发送给包括开发者的任何人。识别文本会显示在 App 的“最近识别”和剪贴板兜底中，请自行注意隐私。

开启“麦克风预热”后，系统麦克风指示器会在输入法显示期间保持亮起；这是 Android 的隐私提示，App 无法隐藏。预热缓冲只在按下悬浮按钮后才发送给服务端。

## 验证情况

- 当前版本：**1.7.0 / versionCode 9**。
- `testDebugUnitTest`：22 个测试通过。
- `lintDebug`：0 Error。
- APK 使用 Android debug 证书，v2 签名。
- 当前环境没有 Android 真机/模拟器，设置分页、暗黑主题、自动更新弹窗、短按穿透、最近任务开关、麦克风预热和具体输入法兼容性仍需实机确认。
- 自动更新检查指向 `app/build.gradle.kts` 中的 `GITHUB_REPO`（当前 `xgh9191029/CapsWriter-Android`）；仓库没有 Release 时会静默跳过。

---

# English

[中文](#capswriter-安卓离线语音输入) · [English](#english)

> **Unofficial Android client for [CapsWriter-Offline](https://github.com/HaujetZhao/CapsWriter-Offline)**. Thanks to the original author for open-sourcing the project; the protocol, configuration semantics, and interaction ideas are inspired by the upstream project.
>
> GitHub repository: [xgh9191029/CapsWriter-Android](https://github.com/xgh9191029/CapsWriter-Android)

An Android client written in **Kotlin** that connects to a [CapsWriter-Offline](https://github.com/HaujetZhao/CapsWriter-Offline) server on your LAN and injects speech recognition results directly into the active input field.

Its interaction is intentionally lightweight: **it does not replace your keyboard**. Instead, when the IME appears, a **fully transparent floating touch target** is displayed above the keyboard keys. Hold to speak, release to insert text; its position and size can be manually calibrated to align with your keyboard's built-in microphone key or any key you prefer.

## Features

- The home page keeps only **server address** and **floating-target settings**; permissions, recognition/output, test, hotwords, guide and diagnostics live on a separate **Settings** page.
- Follows the system dark theme (Android 10+).
- Checks GitHub Releases once at startup; when a newer version exists, a dialog offers **Don’t remind me / Not now / GitHub**.
- Manual CapsWriter-Offline server address configuration: supports `IP`, `IP:port`, `ws://`, and `wss://`; default port `6016` (`6006` for older servers).
- Accessibility-based IME window detection: shows the transparent overlay when the keyboard pops up and removes it automatically when hidden.
- Uses `TYPE_ACCESSIBILITY_OVERLAY`, layered above the IME. It does **not** require the “Display over other apps” permission (standard app overlays get covered by the keyboard).
- Completely transparent overlay by default, keeping only the touch hit region; recording/recognition states are indicated via haptic vibration and a persistent notification.
- Hold-to-talk and tap-to-toggle modes. In hold mode, a short tap is forwarded to the IME's own voice key while a long hold records with CapsWriter.
- Optional hiding from the recent tasks list (enabled by default) to prevent accidentally clearing the background service.
- Optional **microphone prewarm**: keeps the microphone open while the IME is visible and prepends the last ~500 ms of audio on press, reducing startup delay and clipped first syllables. Off by default; higher battery use and the microphone indicator stays visible.
- Recognition language selection (Auto / Chinese / English / Japanese), adjustable minimum recording duration, trailing punctuation trimming, and optional WAV recording.
- Calibration mode: displays a blue dashed box with **independent width and height adjustment**. Drag it onto your keyboard's key and save; position and size are stored per **IME + orientation**, allowing separate profiles for Gboard, Sogou, Celia, etc.
- Streams 16 kHz mono float32 PCM audio directly via WebSocket, complying with the CapsWriter-Offline v2.x protocol.
- Client hotwords: supports recognition hints (sent to server `context`) as well as local `wrong=>correct` replacements (see below).
- Injects recognized text at the cursor via Accessibility `ACTION_SET_TEXT`.
- If you switch apps during recognition, it prioritizes inserting into the newly active app's editable field, copying to the clipboard only when no writable node exists.
- All settings are stored locally. Recordings are disabled by default and written to app-specific storage when enabled; zero analytics data is uploaded.

## Requirements

- Android 8.0 (API 26) or newer.
- A running CapsWriter-Offline server on your computer (v2.x default `6016`, v1.x commonly `6006`).
- Phone and computer on the same LAN, with the corresponding TCP port allowed through the computer firewall and the server listening on a LAN-accessible address (e.g., `0.0.0.0`).
- Build: JDK 17, Android SDK Platform 35, Gradle 8.9 (configured with AGP 8.7.3 / Kotlin 2.0.21).

## Build

```bash
# Point to your own JDK / Android SDK
export JAVA_HOME=/path/to/jdk-17
export ANDROID_HOME=/path/to/Android/Sdk

./gradlew assembleDebug
# Output: app/build/outputs/apk/debug/app-debug.apk
```

Windows PowerShell:

```powershell
$env:JAVA_HOME = "C:\path\to\jdk-17"
$env:ANDROID_HOME = "C:\path\to\Android\Sdk"
.\gradlew.bat assembleDebug
```

## First-Time Setup

1. Open the app, enter your computer's LAN address (e.g., `192.168.1.100:6016`), and tap **Save and test**. The home page keeps only server and floating-target settings; tap **Settings** for everything else.
2. Open **Settings**, then grant **Microphone** and **Notifications** permissions.
3. In Settings, tap **Enable** and turn on **CapsWriter floating voice input** in Android Accessibility settings.
   - On Android 13+, the accessibility toggle for sideloaded apps may be blocked by “Restricted settings”. Go to system “App info → CapsWriter → top-right menu” and select “Allow restricted settings”, then return to enable it.
4. In Settings, turn on **Enable floating voice input**. The app starts a microphone-type foreground service; this step must be completed while the app is visible (due to Android 14 background microphone restrictions).
   - After updating to 1.5.0 or newer, turn the accessibility service off and on once so the `canPerformGestures` permission used by short-tap pass-through is applied.
5. Return to the home page and tap **Calibrate position**. The app switches to the Settings test field and opens the keyboard; drag the blue dashed box over your keyboard's voice key, adjust width/height separately, and tap **Save**. Repeat once when switching keyboards or screen orientation so the app remembers each profile.
6. Open any chat or note app, bring up the keyboard, hold the floating area to speak, and release to insert text at the cursor. A short tap is forwarded to the IME's own voice key (disable **Pass short taps to the keyboard** in the home floating-target settings).
7. Optional: Add frequently used names, places, or `wrong=>correct` rules in “Client hotwords” on the Settings page; changes take effect on the next recording after saving.
8. Optional: Configure recognition language, minimum recording duration, trailing punctuation trimming, and WAV recording under “Recognition & Output” on the Settings page.
9. Optional: Enable **Microphone prewarm (low latency)** in the home floating-target settings. The microphone stays warm while the keyboard is visible and the last ~500 ms is prepended on press; it is released about 10 s after the keyboard hides. Off by default; higher battery use.
10. At startup, if a newer GitHub Release exists, a dialog offers **Don’t remind me** (skip this version), **Not now**, or **GitHub** (open the release page in the default browser).

> The app is hidden from recent tasks by default; after pressing Home, return to the app via its notification or launcher icon. Disable **Hide from recent tasks** in the permissions card if you prefer to return from recents.

> The overlay is completely invisible—its location matches the calibration box. If you lose track of it, return to the app and tap “Calibrate position” to view it again, or tap “Reset position”.

### Client Hotwords

Enter one rule per line in “Client hotwords” on the main screen:

```text
CapsWriter
蕲春
wrong=>correct
re:helo=>hello
```

- Plain words without `=>` are sent to the server as `context` hints (ideal for names, places, and domain terms).
- `wrong=>correct` rules are both added to `context` and applied as **literal local replacements** on the final recognized text.
- `re:pattern=>replacement` applies regular expression replacement (`$1` capture groups supported).
- Literal replacements are sorted by source length descending so longer phrases match before shorter substrings.
- Client-side replacements run after server recognition finishes, meaning exact replacements work even if the server engine does not support `context`.

### Trailing Punctuation Threshold

The actual behavior **strips trailing punctuation when the semantic unit count is $\le 8$, and preserves punctuation when $> 8$**, keeping normal punctuation on longer sentences. The threshold is configurable from 0 to 50:

- `8` (default): trims trailing punctuation for utterances with $\le 8$ semantic units.
- `0`: always trims trailing punctuation (threshold disabled).
- `50`: trims trailing punctuation on almost all short-to-medium utterances.
- Semantic unit counting: 1 Chinese character = 1, 1 English word = 1, 1 number = 1.
- WeChat (`com.tencent.mm`) bypasses the threshold and always trims trailing punctuation, matching `trash_punc_apps=['WeiXin.exe']`.

### Text Insertion

1. Enumerates the active window and all application windows to collect `FOCUS_INPUT` nodes, editable parent nodes of the focused node, and editable nodes within the active window.
2. Prioritizes editable nodes in the currently active app; never writes into background apps.
3. Builds the updated text at the current cursor/selection and executes `ACTION_SET_TEXT`.
4. If `ACTION_SET_TEXT` fails, copies text to the clipboard and executes `ACTION_PASTE`.
5. If no writable node exists, the field blocks pasting, or you switched to an app with no input field during recognition, copies the text to the clipboard and notifies you.

## Permissions

| Permission | Purpose |
| --- | --- |
| `RECORD_AUDIO` | Record audio and stream it to your LAN server |
| `INTERNET` / `ACCESS_NETWORK_STATE` | WebSocket network connectivity |
| `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_MICROPHONE` | Keep recording/prewarming alive (required on Android 14+) |
| `POST_NOTIFICATIONS` | Display persistent “Recording / Recognizing” notification |
| `VIBRATE` | Haptic feedback on press and release |
| `BIND_ACCESSIBILITY_SERVICE` | Detect IME window, display overlay, inject text, short-tap gesture |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Optional keep-alive setting to prevent aggressive OEM background killing |
| `REORDER_TASKS` / `GET_TASKS` | Toggle “Hide from recent tasks” at runtime |

The app never requests “Display over other apps”, root, or Shizuku.

## Troubleshooting

**Floating overlay does not appear**
- Ensure “Enable floating voice input” is toggled on.
- Verify the Accessibility Service is enabled and connected; some OEM ROMs require locking the app in recent tasks.
- The keyboard must be a genuine IME window; hardware keyboards or specialized full-screen IME modes may not trigger window detection.
- If the app displays “Background service not running”, tap the overlay region or reopen the app to restart it.

**No response when pressing the overlay**
- Check Microphone and Notifications permissions.
- Check whether the server is connected.
- Verify server reachability: opening `http://<computer-ip>:6016` in your phone's browser may not serve a webpage, but it should not refuse connection; check the server logs to confirm client connection attempts.
- On Android 14+, starting the microphone foreground service from the background is blocked by the OS; open the app in the foreground first.

**Recognition succeeds, but text is not inserted**
- v1.5.0 scans the active window, focused parent nodes, and all editable candidates, trying `ACTION_SET_TEXT` first and `ACTION_PASTE` second (with EditText/TextInput class-name fallbacks). QQ normally receives direct text injection without touching the clipboard.
- If both accessibility actions fail in WeChat, text still falls back to the clipboard; v1.4.0 removed the automatic long-press context menu workaround to avoid accidental text selection or replacement.
- If it still only copies to the clipboard, check the last “Insertion diagnostics” entry in the in-app “Diagnostic logs”, which lists candidate node class names, package names, and editable/focused flags. Share that line and the target app name to diagnose whether no node was found or the node rejected the action.
- Apps that expose no writable input nodes to Android's accessibility tree (certain WebViews, Flutter apps, remote desktops, in-game chat boxes) cannot receive direct text injection due to Android's permission model—manual paste from the clipboard is required.
- If you switched apps mid-recognition, text is written to the new app's active editable field if available, or copied to the clipboard otherwise.
- If short-tap pass-through or microphone prewarm does not behave as expected, disable the corresponding switch; long-press recording and the normal per-press microphone path remain available.

**Overlay position is misaligned**
- Recalibrate; position and dimensions are saved per “IME + orientation”, so Gboard, Sogou, etc., each need one-time calibration.
- Switching keyboards automatically loads the corresponding profile (or falls back to the default position if uncalibrated).
- Adjusting the width/height sliders on the main screen updates the active IME profile while preserving its center point.

**Connection timeout**
- If the server only listens on `127.0.0.1`, your phone cannot reach it; bind the server to `0.0.0.0` or your LAN IP.
- Ensure TCP port `6016` (or `6006` for v1.x) is allowed through your computer's firewall.
- Supports `ws://` and `wss://` with publicly trusted certificates; self-signed certificates are rejected by OkHttp by default.

**Cannot enable Accessibility on Android 13+**
- Sideloaded APKs trigger “Restricted settings”. Open system Settings → Apps → CapsWriter → top-right menu → “Allow restricted settings”, then enable the Accessibility Service.
- Some OEM ROMs also require enabling Auto-start / Background activity and disabling battery optimization.

## Project Structure

```text
app/src/main/java/com/capswriter/offline/
├── audio/
│   ├── AudioCapture.kt          # 16 kHz float32 PCM capture
│   ├── AudioPrewarmer.kt        # Warm microphone + 500 ms pre-roll buffer
│   ├── AudioWavWriter.kt        # Optional WAV file writer
│   └── VoiceSession.kt          # Full lifecycle of a push-to-talk session
├── net/
│   ├── CapsWriterSocket.kt      # CapsWriter-Offline WebSocket protocol
│   ├── NetClient.kt             # OkHttp singleton
│   ├── ServerProbe.kt           # Connection test probe
│   └── UpdateChecker.kt         # GitHub Release update check
├── service/
│   ├── CapsWriterAccessibilityService.kt  # IME detection + text injection
│   ├── OverlayController.kt              # Transparent overlay & calibration panel
│   ├── TextInserter.kt                   # ACTION_SET_TEXT / paste logic
│   └── VoiceForegroundService.kt         # Microphone foreground service
├── ui/MainActivity.kt
└── util/                        # Config, state, logs, URL parsing, hotwords, output trimming
    ├── HotwordProcessor.kt      # Client hotword parsing & replacement
    └── OutputTextProcessor.kt   # Trailing punctuation trimming
```

## Privacy

Audio is sent exclusively to the CapsWriter-Offline server address you configure, with no third-party servers involved. Recordings are not saved by default; if WAV saving is enabled, files go to app-specific storage. Logs are never sent to anyone, including the developer. Recognized text appears in the in-app “Latest result” view and clipboard fallback, so please handle sensitive text accordingly.

With microphone prewarm enabled, the Android microphone indicator stays visible while the keyboard is shown; the app cannot hide it. Buffered audio is sent to the server only after you press the floating target.

## Verification

- Current version: **1.7.0 / versionCode 9**.
- `testDebugUnitTest`: 22 tests pass.
- `lintDebug`: 0 errors.
- APK uses the Android debug certificate and v2 signing.
- No Android device/emulator is attached; the split Settings page, dark theme, update dialog, short-tap pass-through, recent-task toggle, microphone prewarm, and IME compatibility still require on-device verification.
- The update check targets `GITHUB_REPO` in `app/build.gradle.kts` (currently `xgh9191029/CapsWriter-Android`); it silently skips when the repository has no releases.
