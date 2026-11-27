# CapsWriter 安卓离线语音输入

[中文](#capswriter-安卓离线语音输入) · [English](#english)

> **非官方 Android 客户端**，兼容 [CapsWriter-Offline](https://github.com/HaujetZhao/CapsWriter-Offline)（MIT License，作者 Haujet Zhao）。本项目与上游作者无隶属关系；协议、配置项和交互灵感来自上游项目。

一个用 **Kotlin** 写的 Android 客户端，连接局域网里的 [CapsWriter-Offline](https://github.com/HaujetZhao/CapsWriter-Offline) 服务端，把语音识别结果直接写进当前输入框。

它的交互刻意做得很轻：**不替换你的输入法**，而是在输入法弹出时，于输入法按键上方显示一个**完全透明的悬浮触摸区**。按住说话、松手上屏；位置和大小都可以手动校准，对齐到输入法自带的麦克风键或任意按键上。

## v1.4.0 更新

- 移除微信自动长按粘贴兜底；不再申请 `canPerformGestures`，避免 UI 自动化和选区风险。
- QQ 等应用恢复 `ACTION_SET_TEXT` 优先，不再强制走剪贴板；只有直接写入失败时才 `ACTION_PASTE`，最后才复制。
- 新增**英文界面**：`values-en` 全量字符串翻译，系统语言为 English 时整个 App（界面、通知、无障碍名称）自动切换为英文。
- README 增加英文版本，并补充上游仓库与 MIT License 归属。
- 版本号 1.4.0 / versionCode 6。

## 功能

- 手动填写 CapsWriter-Offline 服务端地址，支持 `IP`、`IP:端口`、`ws://`、`wss://`，默认端口 `6016`（旧版服务端用 `6006`）。
- 通过无障碍服务检测输入法窗口，键盘弹出时显示透明悬浮窗，键盘收起时自动移除。
- 悬浮窗使用 `TYPE_ACCESSIBILITY_OVERLAY`，层级在输入法之上；**不是**“显示在其他应用上层”权限，普通应用悬浮窗会被输入法盖住。
- 悬浮窗默认完全透明，只保留触摸区域；录音/识别状态由震动和常驻通知反馈。
- 按住说话 / 点击开关两种模式。
- 应用默认从最近任务列表隐藏，避免语音输入工具出现在任务切换界面。
- 识别语言可选自动/中文/英文/日文；可调最短录音时长；可清理末尾标点；可保存 WAV 录音。
- 校准模式：显示蓝色虚线框，**宽度和高度分别调整**，拖动到输入法按键位置后保存；位置和大小按 **输入法 + 横竖屏** 分别记忆，Gboard、搜狗、小艺等不同键盘可以各自适配。
- 录音直接以 16 kHz 单声道 float32 PCM 通过 WebSocket 流式发送，符合 CapsWriter-Offline v2.x 协议。
- 客户端热词：支持识别提示词（发送到服务端 `context`）以及 `错词=>正确词` 的本地替换，详见下文。
- 识别结果通过无障碍 `ACTION_SET_TEXT` 写入光标处；失败后自动尝试 `ACTION_PASTE`。会搜索活动窗口、焦点节点的可编辑父节点以及所有可编辑候选项，减少“只会复制”的情况。
- 如果识别期间切换了 App，优先写入新 App 当前的可编辑框；没有可写节点时才复制到剪贴板。
- 所有配置本地保存。录音默认不保存，开启后写到应用专属目录；不上传任何分析数据。

## 环境要求

- Android 8.0（API 26）及以上。
- 电脑上已启动 CapsWriter-Offline 服务端（v2.x 默认 `6016`，v1.x 常见 `6006`）。
- 手机和电脑在同一局域网，电脑防火墙放行对应 TCP 端口；服务端监听地址需允许局域网访问（例如 `0.0.0.0`）。
- 构建：JDK 17、Android SDK Platform 35、Gradle 8.9（项目自带 AGP 8.7.3 / Kotlin 2.0.21 配置）。

仓库根目录已经附带一个可直接安装的 **debug APK**：

```text
CapsWriter-Offline-Android-v1.4.0-debug.apk
```
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

仓库里的 `local.properties` 只用于本机构建，不应提交；可参考 `local.properties.example`。

## 首次使用

1. 打开 App，填入电脑的局域网地址，例如 `192.168.1.100:6016`，点 **保存并测试**。
2. 授予 **麦克风** 和 **通知** 权限。
3. 点 **去开启**，在系统无障碍设置里开启 **CapsWriter 悬浮语音输入**。
   - Android 13 及以上，侧载应用的无障碍开关可能被“受限设置”拦住。到系统“应用信息 → CapsWriter 语音输入 → 右上角菜单”里允许受限制的设置，再回来开启。
4. 打开 **启用悬浮语音**。App 会在前台启动一个麦克风类型的前台服务；这一步必须在 App 可见时完成（Android 14 的后台麦克风限制）。
5. 点 **校准位置**。App 会聚焦测试输入框并弹出键盘；把蓝色虚线框拖到输入法的语音键位置，分别调好宽度/高度后点 **保存**。换一个输入法（或横屏）后再各校准一次，App 会自动记住对应配置。
6. 回到任意聊天/笔记 App，唤起输入法，按住悬浮窗位置说话，松手后文字会写入当前光标处。
7. 可选：在“客户端热词”里填入常用人名、地名或 `错词=>正确词`，保存后下一次识别生效。
8. 可选：在“识别与输出”里选择语言、最短录音时长、末尾标点清理和 WAV 录音。

> 应用已从最近任务列表隐藏；按 Home 后请通过通知或桌面图标回到应用。

> 悬浮窗在视觉上完全不可见，位置就是校准框的位置。找不到时，回到 App 点“校准位置”重新看一遍；或先点“重置位置”。

## 实现要点

### 为什么不用普通悬浮窗

Android 的 `TYPE_APPLICATION_OVERLAY` 明确位于输入法窗口之下：

> Application overlay windows are displayed above all activity windows ... but below critical system windows like the status bar or IME.

因此普通“悬浮窗权限”做的按钮会被键盘盖住。本项目使用无障碍服务的 `TYPE_ACCESSIBILITY_OVERLAY`，并只加 `FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCH_MODAL | FLAG_LAYOUT_IN_SCREEN`，**不加** `FLAG_ALT_FOCUSABLE_IM`，这样窗口在输入法之上，同时不会抢走输入焦点、不会收起键盘。

### 输入法检测

`AccessibilityService.windows` 中查找 `TYPE_INPUT_METHOD` 窗口，取其 `boundsInScreen` 作为键盘区域。通过 `TYPE_WINDOWS_CHANGED` / `TYPE_WINDOW_STATE_CHANGED` / `TYPE_VIEW_FOCUSED` 事件加 300ms 轮询兜底。键盘出现时挂载透明触摸区，消失时移除。

位置以输入法窗口为坐标系归一化保存：

```text
profileKey = 当前输入法包名 + _ + 横/竖屏
ratioX = (targetCenterX - ime.left) / ime.width
ratioY = (targetCenterY - ime.top) / ime.height
width  = 用户校准的宽度（dp）
height = 用户校准的高度（dp）
```

因此换输入法、横竖屏、键盘高度变化后仍能落在相对位置；每个输入法/方向都有独立的宽高和位置。悬浮触摸区本身仍然是矩形窗口（Android 的窗口命中区域只能是矩形），把宽高调到刚好覆盖输入法的语音键即可，圆形/异形按键可以用正方形区域近似。

### 音频协议

每个录音会话建立一条新的 WebSocket，请求子协议 `binary`，文本帧为 JSON：

```json
{
  "task_id": "uuid",
  "source": "mic",
  "data": "<base64 little-endian float32 PCM>",
  "is_final": false,
  "time_start": 1730000000.0,
  "seg_duration": 60.0,
  "seg_overlap": 4.0,
  "context": "CapsWriter、语音输入、正确词",
  "language": "auto"
}
```

- 采样率 16000 Hz，单声道，float32，小块 100ms。
- 松手后发送 `data: ""`、`is_final: true` 的结束帧。
- `context` 来自客户端热词，Fun-ASR-Nano 等支持提示词的引擎会利用它；其他引擎会忽略。
- 服务端返回同协议的 `RecognitionMessage`，取 `is_final=true` 的 `text` 字段上屏。
- 兼容服务端 v2.x；v1.x 若协议/端口不同，请把地址改成对应端口并在服务端确认协议版本。

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

`config_client.py` 里是：

```python
trash_punc = '，。,.'
trash_punc_thresh = 8
```

实际逻辑是 **语义单位数不超过 8 时去掉末尾标点，超过 8 时保留标点**，这样才能让长句保持正常标点。手机端完全按这个逻辑实现，阈值可调 0–50：

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

无障碍 API 的能力边界就在这里：Flutter/WebView/远程桌面等如果完全不向无障碍树暴露可写输入节点，不成为当前输入法、不使用 root/Shizuku 就无法直接注入文字，只能走剪贴板。

手机端额外补了桌面没有的：按输入法+横竖屏保存悬浮位置/大小、通知栏、电池优化引导、最近任务隐藏。

> 手机端默认关闭 `save_audio`，避免无意保存隐私音频；需要时在“识别与输出”卡片打开。桌面版默认开启，这一点是有意不同。

## 权限说明

| 权限 | 用途 |
| --- | --- |
| `RECORD_AUDIO` | 录音并发送给局域网服务端 |
| `INTERNET` / `ACCESS_NETWORK_STATE` | WebSocket 连接 |
| `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_MICROPHONE` | 按住说话期间保持录音，Android 14 必需 |
| `POST_NOTIFICATIONS` | 显示“录音中/识别中”的常驻通知 |
| `VIBRATE` | 按下、松手的触觉反馈 |
| `BIND_ACCESSIBILITY_SERVICE` | 检测输入法、显示悬浮层、写入文字 |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | 可选的保活设置，避免国产 ROM 杀服务 |

App 不申请“显示在其他应用上层”，也不申请 root/Shizuku。

## 常见问题

**悬浮框没有出现**
- 确认“启用悬浮语音”已打开。
- 确认无障碍服务处于开启且已连接状态；部分 ROM 需要在最近任务里锁定 App。
- 键盘必须是真正的输入法窗口；外接物理键盘、或输入法使用了特殊全屏模式时，窗口检测可能不适用。
- 打开 App 后如果看到“后台服务未运行”，点一下悬浮区域会重新拉起 App；或直接回到 App。

**按下后没有反应**
- 检查麦克风权限和通知权限。
- 检查服务端地址是否可访问：手机浏览器打开 `http://电脑IP:6016` 不一定有网页，但至少不应连接被拒绝；更可靠的是看服务端日志是否出现客户端连接。
- Android 14 上如果服务不是从可见的 App 启动的，会被系统拒绝；请先打开 App 再试。

**识别成功但文字没进输入框**
- v1.4.0 会搜索活动窗口、焦点父节点和所有可编辑候选项，统一先尝试 `ACTION_SET_TEXT`，失败再 `ACTION_PASTE`，并保留 EditText/TextInput 类名兜底。QQ 正常情况下会直接写入，不经过剪贴板。
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
│   ├── AudioWavWriter.kt        # 可选 WAV 录音落盘
│   └── VoiceSession.kt          # 一次按住说话的完整生命周期
├── net/
│   ├── CapsWriterSocket.kt      # CapsWriter-Offline WebSocket 协议
│   ├── NetClient.kt             # OkHttp 单例
│   └── ServerProbe.kt           # 连接测试
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

音频只发送到你自己填写的 CapsWriter-Offline 服务端，不经过任何第三方服务器。App 不保存录音文件，日志只记录连接状态和识别文字长度，不记录音频内容。识别文本会显示在 App 的“最近识别”和剪贴板兜底中，请自行注意隐私。

## 验证情况

---

# English

[中文](#capswriter-安卓离线语音输入) · [English](#english)

> **Unofficial Android client** compatible with [CapsWriter-Offline](https://github.com/HaujetZhao/CapsWriter-Offline) (MIT License, by Haujet Zhao). This project is not affiliated with the upstream author; the protocol, configuration semantics, and interaction ideas are inspired by the upstream project.

An Android client written in **Kotlin** that connects to a [CapsWriter-Offline](https://github.com/HaujetZhao/CapsWriter-Offline) server on your LAN and injects speech recognition results directly into the active input field.

Its interaction is intentionally lightweight: **it does not replace your keyboard**. Instead, when the IME appears, a **fully transparent floating touch target** is displayed above the keyboard keys. Hold to speak, release to insert text; its position and size can be manually calibrated to align with your keyboard's built-in microphone key or any key you prefer.

## v1.4.0 What's New

- Removed automatic long-press paste fallback for WeChat; stopped requesting `canPerformGestures` to avoid UI automation and text-selection risks.
- Restored `ACTION_SET_TEXT` priority for QQ and similar apps instead of forcing clipboard usage; falls back to `ACTION_PASTE` only if direct insertion fails, and copies to clipboard as a last resort.
- Added **English UI**: full string translation in `values-en`; the entire app (UI, notifications, accessibility labels) automatically switches to English when the system language is set to English.
- Added English documentation to README along with upstream repository and MIT License attribution.
- Version 1.4.0 / versionCode 6.

## Features

- Manual CapsWriter-Offline server address configuration: supports `IP`, `IP:port`, `ws://`, and `wss://`; default port `6016` (`6006` for older servers).
- Accessibility-based IME window detection: shows the transparent overlay when the keyboard pops up and removes it automatically when hidden.
- Uses `TYPE_ACCESSIBILITY_OVERLAY`, layered above the IME. It does **not** require the “Display over other apps” permission (standard app overlays get covered by the keyboard).
- Completely transparent overlay by default, keeping only the touch hit region; recording/recognition states are indicated via haptic vibration and a persistent notification.
- Hold-to-talk and tap-to-toggle modes.
- Hidden from the recent tasks list by default so the voice utility stays out of your app switcher.
- Recognition language selection (Auto / Chinese / English / Japanese), adjustable minimum recording time, trailing punctuation trimming (with configurable short-sentence threshold), and optional WAV recording (16 kHz mono, saved to `Music/CapsWriter` when enabled).
- Calibration mode: displays a blue dashed box with **independent width and height adjustment**. Drag it onto your keyboard's key and save; position and size are stored per **IME + orientation**, allowing separate profiles for Gboard, Sogou, Celia, etc.
- Streams 16 kHz mono float32 PCM audio directly via WebSocket, complying with the CapsWriter-Offline v2.x protocol.
- Client hotwords: supports recognition hints (sent to server `context`) as well as local `wrong=>correct` and `re:` regex replacements (see below).
- Injects recognized text at the cursor via Accessibility `ACTION_SET_TEXT`, automatically falling back to `ACTION_PASTE`. Searches the active window, editable parent nodes of the focused node, and all editable candidates to minimize clipboard-only fallbacks.
- If you switch apps during recognition, it prioritizes inserting into the newly active app's editable field, copying to the clipboard only when no writable node exists.
- English UI via `values-en`; switch the system language to English to use it.
- No root, Shizuku, or “Display over other apps” permission required. All settings are stored locally; recordings are disabled by default and stored in app-specific storage when enabled, with zero analytics uploaded.

## Requirements

- Android 8.0 (API 26) or newer.
- A running CapsWriter-Offline server on your computer (v2.x default `6016`, v1.x commonly `6006`).
- Phone and computer on the same LAN, with the corresponding TCP port allowed through the computer firewall and the server listening on a LAN-accessible address (e.g., `0.0.0.0`).
- Build: JDK 17, Android SDK Platform 35, Gradle 8.9 (configured with AGP 8.7.3 / Kotlin 2.0.21).

A prebuilt, ready-to-install **debug APK** is included in the repository root:

```text
CapsWriter-Offline-Android-v1.4.0-debug.apk
```

It is signed with the Android debug certificate for sideloading. For public distribution, sign a release build with your own keystore.

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

The repository's `local.properties` is for local builds only and should not be committed; refer to `local.properties.example`.

## First-Time Setup

1. Start the CapsWriter-Offline server on your computer.
2. Open the app, enter your computer's LAN address (e.g., `192.168.1.100:6016`), and tap **Save and test**.
3. Grant **Microphone** and **Notifications** permissions.
4. Tap **Enable** and turn on **CapsWriter floating voice input** in Android Accessibility settings.
   - On Android 13+, the accessibility toggle for sideloaded apps may be blocked by “Restricted settings”. Go to system “App info → CapsWriter → top-right menu” and select “Allow restricted settings”, then return to enable it.
5. Turn on **Enable floating voice input**. The app starts a microphone-type foreground service; this step must be completed while the app is visible (due to Android 14 background microphone restrictions).
6. Tap **Calibrate position**. The app focuses a test input field and opens the keyboard; drag the blue dashed box over your keyboard's voice key, adjust width/height separately, and tap **Save**. Repeat once when switching keyboards or screen orientation so the app remembers each profile.
7. Open any chat or note app, bring up the keyboard, hold the floating area to speak, and release to insert text at the cursor.
8. Optional: Add frequently used names, places, or `wrong=>correct` rules in “Client hotwords”; changes take effect on the next recording after saving.
9. Optional: Configure recognition language, minimum recording duration, trailing punctuation trimming, and WAV recording under “Recognition & Output”.

> The app is hidden from recent tasks; after pressing Home, return to the app via its notification or launcher icon.

> The overlay is completely invisible—its location matches the calibration box. If you lose track of it, return to the app and tap “Calibrate position” to view it again, or tap “Reset position”.

## Implementation Notes

### Why Not a Standard Overlay?

Android's `TYPE_APPLICATION_OVERLAY` is explicitly placed beneath the IME window:

> Application overlay windows are displayed above all activity windows ... but below critical system windows like the status bar or IME.

Therefore, buttons created with standard “Display over other apps” permissions get covered by the keyboard. This project uses the Accessibility Service's `TYPE_ACCESSIBILITY_OVERLAY` with `FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCH_MODAL | FLAG_LAYOUT_IN_SCREEN` (and **without** `FLAG_ALT_FOCUSABLE_IM`), keeping the touch target above the IME without stealing input focus or dismissing the keyboard.

### IME Detection

Searches `AccessibilityService.windows` for the `TYPE_INPUT_METHOD` window and uses its `boundsInScreen` as the keyboard region, driven by `TYPE_WINDOWS_CHANGED` / `TYPE_WINDOW_STATE_CHANGED` / `TYPE_VIEW_FOCUSED` events plus a 300ms polling fallback. Mounts the transparent touch target when the keyboard appears and removes it when hidden.

Coordinates are normalized relative to the IME window bounds:

```text
profileKey = currentImePackage + "_" + orientation
ratioX = (targetCenterX - ime.left) / ime.width
ratioY = (targetCenterY - ime.top) / ime.height
width  = calibratedWidth (dp)
height = calibratedHeight (dp)
```

This keeps the touch area aligned across keyboard switches, orientation changes, and dynamic keyboard height adjustments, with independent size and position per IME/orientation. Because Android window hit regions must be rectangular, simply size the box to match your keyboard's voice key (a square region approximates circular/irregular keys well).

### Audio Protocol

Each recording session opens a new WebSocket requesting subprotocol `binary`, sending JSON text frames:

```json
{
  "task_id": "uuid",
  "source": "mic",
  "data": "<base64 little-endian float32 PCM>",
  "is_final": false,
  "time_start": 1730000000.0,
  "seg_duration": 60.0,
  "seg_overlap": 4.0,
  "context": "CapsWriter, voice input, correct word",
  "language": "auto"
}
```

- 16000 Hz sample rate, mono, float32, sent in 100ms chunks.
- Sends a closing frame with `data: ""` and `is_final: true` upon release.
- `context` is populated from client hotwords and utilized by prompt-aware engines like Fun-ASR-Nano (ignored by other engines).
- Parses `RecognitionMessage` responses from the server and inserts the `text` field when `is_final=true`.
- Compatible with server v2.x; if using v1.x, update the port accordingly and verify protocol compatibility on the server.

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

In `config_client.py`:

```python
trash_punc = '，。,.'
trash_punc_thresh = 8
```

The actual behavior **strips trailing punctuation when the semantic unit count is $\le 8$, and preserves punctuation when $> 8$**, keeping normal punctuation on longer sentences. The Android client mirrors this exact logic with a configurable threshold from 0 to 50:

- `8` (default): trims trailing punctuation for utterances with $\le 8$ semantic units.
- `0`: always trims trailing punctuation (threshold disabled).
- `50`: trims trailing punctuation on almost all short-to-medium utterances.
- Semantic unit counting: 1 Chinese character = 1, 1 English word = 1, 1 number = 1.
- WeChat (`com.tencent.mm`) bypasses the threshold and always trims trailing punctuation, matching `trash_punc_apps=['WeiXin.exe']`.

### Text Insertion

1. Enumerates the active window and all application windows to collect `FOCUS_INPUT` nodes, editable parent nodes of the focused node, and editable nodes within the active window.
2. Prioritizes editable nodes in the currently active app; never writes into background apps.
3. Builds the updated text at the current cursor/selection and executes `ACTION_SET_TEXT`.
4. If `ACTION_SET_TEXT` fails, copies text to the clipboard, executes `ACTION_PASTE`, and restores the previous clipboard contents afterward.
5. If no writable node exists, the field blocks pasting, or you switched to an app with no input field during recognition, copies the text to the clipboard and notifies you.

This marks the boundary of Android Accessibility APIs: if apps like Flutter, WebView, or remote desktop clients do not expose writable input nodes to the accessibility tree, direct text injection is impossible without becoming the active IME or using root/Shizuku, so clipboard fallback is used.

> `save_audio` is disabled by default on mobile to avoid unintentionally storing private recordings; enable it in the “Recognition & Output” card when needed (unlike the desktop client, where it defaults to on).

## Permissions

| Permission | Purpose |
| --- | --- |
| `RECORD_AUDIO` | Record audio and stream it to your LAN server |
| `INTERNET` / `ACCESS_NETWORK_STATE` | WebSocket network connectivity |
| `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_MICROPHONE` | Keep recording alive while holding to speak (required on Android 14+) |
| `POST_NOTIFICATIONS` | Display persistent “Recording / Recognizing” notification |
| `VIBRATE` | Haptic feedback on press and release |
| `BIND_ACCESSIBILITY_SERVICE` | Detect IME window, display overlay, and inject text |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Optional keep-alive setting to prevent aggressive OEM background killing |

The app never requests “Display over other apps”, root, or Shizuku.

## Troubleshooting & Limitations

**Floating overlay does not appear**
- Ensure “Enable floating voice input” is toggled on.
- Verify the Accessibility Service is enabled and connected; some OEM ROMs require locking the app in recent tasks.
- The keyboard must be a genuine IME window; hardware keyboards or specialized full-screen IME modes may not trigger window detection.
- If the app displays “Background service not running”, tap the overlay region or reopen the app to restart it.

**No response when pressing the overlay**
- Check Microphone and Notifications permissions.
- Verify server reachability: opening `http://<computer-ip>:6016` in your phone's browser may not serve a webpage, but it should not refuse connection; check the server logs to confirm client connection attempts.
- On Android 14+, starting the microphone foreground service from the background is blocked by the OS; open the app in the foreground first.

**Recognition succeeds, but text is not inserted**
- v1.4.0 scans the active window, focused parent nodes, and all editable candidates, trying `ACTION_SET_TEXT` first and `ACTION_PASTE` second (with EditText/TextInput class-name fallbacks). QQ normally receives direct text injection without touching the clipboard.
- If both accessibility actions fail in WeChat, text still falls back to the clipboard; v1.4.0 removed the automatic long-press context menu workaround to avoid accidental text selection or replacement.
- If it still only copies to the clipboard, check the last “Insertion diagnostics” entry in the in-app “Diagnostic logs”, which lists candidate node class names, package names, and editable/focused flags. Share that line and the target app name to diagnose whether no node was found or the node rejected the action.
- Apps that expose no writable input nodes to Android's accessibility tree (certain WebViews, Flutter apps, remote desktops, in-game chat boxes) cannot receive direct text injection due to Android's permission model—manual paste from the clipboard is required.
- If you switched apps mid-recognition, text is written to the new app's active editable field if available, or copied to the clipboard otherwise.

**Overlay position is misaligned**
- Recalibrate; position and dimensions are saved per “IME + orientation”, so Gboard, Sogou, etc., each need one-time calibration.
- Because Android window hit regions must be rectangular, adjust width and height to closely match the target key.
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
│   ├── AudioWavWriter.kt        # Optional WAV file writer
│   └── VoiceSession.kt          # Full lifecycle of a push-to-talk session
├── net/
│   ├── CapsWriterSocket.kt      # CapsWriter-Offline WebSocket protocol
│   ├── NetClient.kt             # OkHttp singleton
│   └── ServerProbe.kt           # Connection test probe
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

Audio is sent exclusively to the CapsWriter-Offline server address you configure, with no third-party servers involved. Recordings are not saved unless you manually enable WAV recording, and logs only record connection states and recognized text lengths—never audio content. Recognized text appears in the in-app “Latest result” view and clipboard fallback, so please handle sensitive text accordingly.

## Upstream and License

- Upstream: [HaujetZhao/CapsWriter-Offline](https://github.com/HaujetZhao/CapsWriter-Offline) (MIT, Copyright (c) 2026 Haujet Zhao).
- This Android client is an independent project and is not endorsed by the upstream author. See [LICENSE](LICENSE).
