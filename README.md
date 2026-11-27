# CapsWriter 安卓离线语音输入

[中文](#capswriter-安卓离线语音输入) · [English](#english) · [更新日志](CHANGELOG.md)

> **非官方 Android 客户端**，兼容 [CapsWriter-Offline](https://github.com/HaujetZhao/CapsWriter-Offline)（MIT License，作者 Haujet Zhao）。本项目与上游作者无隶属关系；协议、配置项和交互灵感来自上游项目。

一个用 **Kotlin** 写的 Android 客户端，连接局域网里的 [CapsWriter-Offline](https://github.com/HaujetZhao/CapsWriter-Offline) 服务端，把语音识别结果直接写进当前输入框。

它的交互刻意做得很轻：**不替换你的输入法**，而是在输入法弹出时，于输入法按键上方显示一个**完全透明的悬浮触摸区**。按住说话、松手上屏；位置和大小都可以手动校准，对齐到输入法自带的麦克风键或任意按键上。

## v1.5.0 更新

- 新增“隐藏最近任务”开关，默认开启；关闭后可以从最近任务列表切回 App。
- 新增“短按穿透到输入法”开关（按住说话模式）：按住时正常录音，短按不再被透明悬浮窗吞掉，而是通过无障碍手势把点击转发给下层输入法的语音键，让输入法自己的短按功能不受影响。
- 穿透需要 `canPerformGestures`。更新后请把 CapsWriter 无障碍服务关闭再开启一次；如果系统没有授予手势能力，短按会保持无操作，只有长按录音生效。
- 版本号 1.5.0 / versionCode 7。

## v1.4.0 更新

- 移除微信自动长按粘贴兜底；不再申请 `canPerformGestures`，避免 UI 自动化和选区风险。
- QQ 等应用恢复 `ACTION_SET_TEXT` 优先，不再强制走剪贴板；只有直接写入失败时才 `ACTION_PASTE`，最后才复制。
- 新增**英文界面**：`values-en` 全量字符串翻译，系统语言为 English 时整个 App（界面、通知、无障碍名称）自动切换为英文。
- README 增加英文版本，并补充上游仓库与 MIT License 归属。
- 版本号 1.4.0 / versionCode 6。

## v1.2.0 更新

- 加入**从最近任务列表隐藏**：`MainActivity` 设置 `android:excludeFromRecents="true"`，按 Home 后不会出现在最近任务里；回应用请点通知或桌面图标。
- 按 `config_client.py` 补齐手机端常用项：
  - `language`：自动 / 中文 / 英文 / 日文选择。
  - `threshold`：可调“最短录音”时长，短于该时长按误触取消。
  - `trash_punc`：可选的末尾标点清理（`，。,.`）。
  - `save_audio` / `audio_name_len`：可选保存 16 kHz 单声道 WAV，文件名取识别结果前 20 字。
- 新增“3 · 识别与输出”设置卡片。

## v1.1.0 更新

- 悬浮按钮的**宽度、高度可独立调整**，并按“输入法 + 横竖屏”分别保存校准配置，不再用一个固定比例适配所有键盘。
- 强化上屏链路：搜索所有应用窗口的焦点节点/可编辑父节点/可编辑候选，过滤掉输入法自己的窗口，优先 `ACTION_SET_TEXT`，失败再 `ACTION_PASTE`。
- 新增**客户端热词**：热词会作为 `context` 发给服务端，同时支持 `错词=>正确词` 本地替换和 `re:` 正则替换。

## 功能

- 手动填写 CapsWriter-Offline 服务端地址，支持 `IP`、`IP:端口`、`ws://`、`wss://`，默认端口 `6016`（旧版服务端用 `6006`）。
- 通过无障碍服务检测输入法窗口，键盘弹出时显示透明悬浮窗，键盘收起时自动移除。
- 悬浮窗使用 `TYPE_ACCESSIBILITY_OVERLAY`，层级在输入法之上；**不是**“显示在其他应用上层”权限，普通应用悬浮窗会被输入法盖住。
- 悬浮窗默认完全透明，只保留触摸区域；录音/识别状态由震动和常驻通知反馈。
- 按住说话 / 点击开关两种模式；按住模式下短按可穿透到输入法语音键，长按用于 CapsWriter。
- 可从最近任务列表隐藏（默认开启，可在权限卡片中关闭）。
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
CapsWriter-Offline-Android-v1.5.0-debug.apk
```

它是用 Android 调试证书签名的，适合自己侧载使用；要上架或长期分发，请用自己的 release keystore 重新签名。

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
   - 从旧版更新到 1.5.0 后，请把该无障碍服务**关闭再开启一次**，让新增的 `canPerformGestures` 生效；否则“短按穿透”不会工作。
4. 打开 **启用悬浮语音**。App 会在前台启动一个麦克风类型的前台服务；这一步必须在 App 可见时完成（Android 14 的后台麦克风限制）。
5. 点 **校准位置**。App 会聚焦测试输入框并弹出键盘；把蓝色虚线框拖到输入法的语音键位置，分别调好宽度/高度后点 **保存**。换一个输入法（或横屏）后再各校准一次，App 会自动记住对应配置。
6. 回到任意聊天/笔记 App，唤起输入法，按住透明区域说话，松手后文字会写入当前光标处。
7. 可选：在“客户端热词”里填入常用人名、地名或 `错词=>正确词`，保存后下一次识别生效。
8. 可选：在“识别与输出”里选择语言、最短录音时长、末尾标点清理和 WAV 录音。

> 应用已从最近任务列表隐藏；按 Home 后请通过通知或桌面图标回到应用。

> 透明区域在视觉上完全不可见，位置就是校准框的位置。找不到时，回到 App 点“校准位置”重新看一遍；或先点“重置位置”。

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

## 与 config_client.py 对照

对照的是本机 `D:\CapsWriter-Offline\config_client.py`（版本 2.6）。

| config_client.py | 手机端状态 |
| --- | --- |
| `addr` / `port` | ✅ 合并为手动服务器地址，支持默认 `6016` |
| `shortcuts` / `hold_mode` | ✅ 悬浮窗“按住说话 / 点击开关”，快捷键改为屏幕触摸 |
| `threshold` | ✅ “最短录音”滑块，默认 0.30 秒 |
| `paste` / `restore_clip` | ✅ 优先 `ACTION_SET_TEXT`，失败粘贴；粘贴后恢复剪贴板 |
| `paste_apps` | ➖ 未强制按 App 粘贴；手机统一先 `ACTION_SET_TEXT`，失败才 `ACTION_PASTE`，避免 QQ 等可直接写入的 App 被强制走剪贴板 |
| `enter_apps` | ❌ 未实现“输出后自动回车”，需要针对具体 App 做风险控制 |
| `save_audio` / `audio_name_len` | ✅ 可选 WAV，保存到 `Music/CapsWriter`，文件名取结果前 20 字 |
| `context` | ✅ 客户端热词作为 `context` 发送 |
| `language` | ✅ 自动/中文/英文/日文 |
| `trash_punc` / `trash_punc_thresh` | ✅ 可选去掉末尾 `，。,.`；阈值按语义单位可调（0–50），微信强制去掉 |
| `trash_punc_apps` | ⚠️ 目前内置微信强制去标点，未做任意 App 名单 |
| `traditional_convert` | ❌ 未内置简繁转换表 |
| `hot` / `hot_thresh` / `hot_similar` / `hot_rule` | ⚠️ 已有 `词`、`错词=>正确词`、`re:` 正则；未实现桌面端的音素模糊 RAG |
| `llm_enabled` / `llm_stop_key` | ❌ 手机端未接入桌面 LLM 角色系统 |
| `enable_tray` | ✅ 用常驻通知替代托盘 |
| `log_level` | ⚠️ 有内存诊断日志，未做等级配置 |
| `mic_seg_duration` / `mic_seg_overlap` | ✅ 固定 60/4，与服务端默认一致；暂未在 UI 暴露 |
| `file_seg_*` / `file_save_*` | ❌ 未做音频文件转录 |
| `udp_broadcast` / `udp_control` | ❌ 未做 UDP 输出/远程控制 |

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

- `testDebugUnitTest`：22 个测试全部通过（7 个地址解析 + 6 个热词解析/替换 + 9 个末尾标点/阈值测试）。
- `assembleDebug`：构建成功，versionName 1.5.0 / versionCode 7。
- `lintDebug`：无 Error，仅有依赖版本、内部资源等 warning。
- APK 通过 `apksigner verify`，使用 v2 签名。
- 当前环境没有连接 Android 真机/模拟器，因此悬浮层级、输入法检测、WAV 保存和具体机型兼容性仍需在手机上实测。首次使用建议先在本页“试一下”输入框里验证。

---

# English

**CapsWriter Android Voice Input** is an **unofficial Kotlin Android client** for [CapsWriter-Offline](https://github.com/HaujetZhao/CapsWriter-Offline) by Haujet Zhao (MIT License). It is not affiliated with the upstream author. The protocol, config semantics and interaction ideas are based on the upstream project.

It keeps your existing keyboard. When the IME appears, a **fully transparent touch target** is placed above the keyboard. Hold it to record, release to insert the recognized text, and calibrate its position and size per IME/orientation.

## Features

- Manual CapsWriter-Offline server address: `IP`, `IP:port`, `ws://`, `wss://`; default port `6016` (`6006` for older servers).
- Accessibility-based IME detection; the overlay is removed automatically when the keyboard hides.
- Uses `TYPE_ACCESSIBILITY_OVERLAY`, which is layered above the IME. It does **not** require the “display over other apps” permission and does not replace the keyboard.
- Independent width/height calibration, saved per IME and orientation.
- Hold-to-talk and tap-to-toggle modes.
- Optional recent-task hiding (enabled by default).
- Hold-to-talk pass-through: a short tap is forwarded to the IME’s own voice key; a long hold records with CapsWriter.
- Recognition language: auto / Chinese / English / Japanese.
- Adjustable minimum recording time.
- Optional trailing punctuation trimming with a configurable short-sentence threshold (mirrors `trash_punc_thresh`).
- Optional WAV recording (16 kHz mono), disabled by default; saved under the app-specific `Music/CapsWriter` folder.
- Client hotwords: bare words are sent as server `context`; `wrong=>correct` rules and `re:` regex rules are applied locally.
- Text insertion: `ACTION_SET_TEXT` first, then `ACTION_PASTE`. QQ and similar apps are not forced through the clipboard. Apps that expose no writable accessibility node still fall back to the clipboard.
- English UI via `values-en`; switch the system language to English to use it.
- No root, Shizuku or “display over other apps” permission.

## Requirements

- Android 8.0 (API 26) or newer.
- A running CapsWriter-Offline server (v2.x default `6016`, older versions often `6006`).
- Phone and computer on the same LAN; allow the TCP port through the computer firewall and bind the server to a LAN-accessible address.
- Build: JDK 17, Android SDK Platform 35, Gradle 8.9.

## Install

Use the prebuilt debug APK:

```text
CapsWriter-Offline-Android-v1.5.0-debug.apk
```

It is signed with the Android debug certificate, intended for sideloading. For public distribution, sign a release build with your own keystore.

## Setup

1. Start the CapsWriter-Offline server on your computer.
2. Open the app, enter `computer-ip:6016`, then tap **Save and test**.
3. Grant microphone and notification permissions.
4. Enable **CapsWriter floating voice input** in Android accessibility settings. On Android 13+, sideloaded apps may be blocked by “Restricted settings”; allow restricted settings in the app info menu first. After updating to 1.5.0, turn the service off and on once so the new gesture permission is applied.
5. Turn on **Enable floating voice input**. This starts a microphone-type foreground service from the visible app (required by Android 14).
6. Tap **Calibrate position**, open the test keyboard, drag/resize the blue box over the keyboard’s voice key, and save.
7. In any app, open the keyboard, hold the transparent area, speak, and release.

By default the app is hidden from recent tasks; return through the notification or the launcher icon. Disable **Hide from recent tasks** if you prefer to switch back from recents.

## Hotwords

One item per line:

```text
CapsWriter
蕲春
wrong=>correct
re:helo=>hello
```

- Bare words are added to the server `context` prompt.
- `wrong=>correct` is a literal local replacement and is also added to context.
- `re:pattern=>replacement` is a local regular-expression replacement (`$1` groups are supported).
- Longer literal sources are replaced first.

## config_client.py parity

| config_client.py | Android status |
| --- | --- |
| `addr` / `port` | ✅ manual server URL, default 6016 |
| `shortcuts` / `hold_mode` | ✅ hold-to-talk / tap-to-toggle overlay |
| `threshold` | ✅ minimum recording time slider |
| `paste` / `restore_clip` | ✅ `ACTION_SET_TEXT` first, paste fallback; clipboard restored after paste |
| `paste_apps` | ➖ not forced per app; direct text insertion first |
| `enter_apps` | ❌ no auto-enter |
| `save_audio` / `audio_name_len` | ✅ optional WAV, 20-char name suffix |
| `context` | ✅ client hotwords are sent as `context` |
| `language` | ✅ auto / Chinese / English / Japanese |
| `trash_punc` / `trash_punc_thresh` | ✅ switch + 0–50 semantic-unit threshold; WeChat forced |
| `trash_punc_apps` | ⚠️ only WeChat is forced |
| `traditional_convert` | ❌ no conversion table |
| `hot*` | ⚠️ literal/regex rules + context, no desktop phoneme RAG |
| `llm_enabled` | ❌ no LLM roles |
| `enable_tray` | ✅ foreground notification |
| `log_level` | ⚠️ in-memory diagnostics, no level setting |
| `mic_seg_duration` / `mic_seg_overlap` | ✅ fixed 60/4, matching server defaults |
| `file_*` | ❌ no file transcription |
| `udp_*` | ❌ no UDP broadcast/control |

Mobile-only additions: per-IME/orientation overlay profiles, recent-task hiding, notification channel, battery-optimization guidance.

## Build

```bash
export JAVA_HOME=/path/to/jdk-17
export ANDROID_HOME=/path/to/Android/Sdk
./gradlew assembleDebug
# app/build/outputs/apk/debug/app-debug.apk
```

## Limitations

- Android accessibility APIs cannot type into an app that exposes no writable input node (some WebView/Flutter/remote-desktop/game fields). The app then copies to the clipboard.
- A fully transparent overlay still has a rectangular hit region; Android does not allow an arbitrary non-rectangular window touch region. Size it to match the key.
- The overlay profile depends on the IME package and orientation; calibrate each keyboard once.
- Recent-task hiding is optional and enabled by default.
- Short-tap pass-through asks the accessibility service to inject a tap after temporarily making the overlay non-touchable. It only applies in hold-to-talk mode; if gesture permission is missing or the IME ignores synthetic taps, only long-press recording works.
- Self-signed `wss://` certificates are rejected by OkHttp.

## Privacy

Audio is sent only to the server address you enter. Recordings are not saved unless you enable WAV recording; logs contain connection status and text lengths, not audio. Recognized text is kept in the in-app “latest result” and may be copied to the clipboard.

## Upstream and license

- Upstream: [HaujetZhao/CapsWriter-Offline](https://github.com/HaujetZhao/CapsWriter-Offline) (MIT, Copyright (c) 2026 Haujet Zhao).
- This Android client is an independent project and is not endorsed by the upstream author. See [LICENSE](LICENSE).
