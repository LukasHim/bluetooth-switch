# BT Switch

基于 Kotlin、Jetpack Compose 与 Material 3 的 BLE 蓝牙开关控制应用。包名为
`com.lukas.bluetoothswitch`，应用名为 **BT Switch**。

## 功能

- **设备**：扫描名称以 `YX_` 或 `BK_` 开头的 BLE 设备；支持连接、断开、按设备保存“自动连接”；已连接设备排在最上方，其余设备按名称字母升序排列。
- **自动跳转**：连接成功并完成服务发现后，自动切换到“控制”页并选中该设备。
- **控制**：顶部可在多个已连接设备之间切换；读取状态、即时开关、延时动作、清除延时、读取/设置/重置密码。延时时间使用 `mm:ss` / `hh:mm:ss` 格式，并可通过时长选择器输入。
- **延时记忆**：按 MAC + 通道保存延时时间，重新进入控制页时自动恢复；可在“设置 → 通用 → 延时记忆”中逐条删除或全部清除。
- **自动刷新**：可设置关闭 / 在有延时时 / 始终三种刷新模式，以及 1–3600 秒的刷新间隔。
- **设置**：主题模式（跟随系统 / 浅色 / 深色）、动态取色（Android 12+）、自动刷新、延时记忆、关于信息。
- **协议**：按 `protocol.md` 收发明文 Hex 字节，支持 `AA 01 A0`、`AA 01 A5`、`AA 03 A1`、`AA 05 A2`、`AA 07 A4` 等指令。

## 构建

- Android Studio / Android SDK 36
- JDK 17+
- Gradle 8.13（项目自带 wrapper）
- minSdk 26，targetSdk 36

```bash
# 日常调试（未混淆，体积大，仅用于开发）
./gradlew assembleDebug

# 安装 / 分发（R8 混淆 + 资源压缩，体积小）
./gradlew assembleRelease
```

- 调试 APK：`app/build/outputs/apk/debug/app-debug.apk`
- 发布 APK：`app/build/outputs/apk/release/BTSwitch-<version>-arm64-v8a.apk`（推荐）或 `...-universal.apk`

单元测试：

```bash
./gradlew testDebugUnitTest
```

## 蓝牙权限

| 系统版本 | 运行时权限 |
| --- | --- |
| Android 12+ | `BLUETOOTH_SCAN`、`BLUETOOTH_CONNECT` |
| Android 11 及以下 | `ACCESS_FINE_LOCATION`，并需开启系统定位服务 |

应用首次进入“设备”页时会请求权限。若权限被永久拒绝，可在应用设置中手动开启。

## BLE 特征自动发现

协议未指定 Service / Characteristic UUID，因此应用在连接后自动选择：

1. 优先使用 HM-10 / JDY 常见的 `0000FFE1-0000-1000-8000-00805F9B34FB`；
2. 其次使用 Nordic UART 的 RX/TX 特征；
3. 否则使用服务中发现的首个可写特征和首个通知/指示特征。

写入类型会根据特征属性自动选择 Write 或 Write Without Response，通知通过 CCCD 开启。

## 目录结构

```text
app/src/main/java/com/lukas/bluetoothswitch/
├── MainActivity.kt
├── MainViewModel.kt
├── data/
│   ├── BlePermissions.kt
│   ├── BluetoothRepository.kt
│   ├── DelayFormat.kt
│   ├── Models.kt
│   ├── Protocol.kt
│   └── SettingsRepository.kt
└── ui/
    ├── AppRoot.kt
    ├── components/Common.kt
    ├── screens/ControlScreen.kt
    ├── screens/DevicesScreen.kt
    ├── screens/SettingsScreen.kt
    └── theme/Theme.kt
```

## 协议说明

- 发送前将 Hex 字符串转换为真实字节数组，不发送 ASCII 字符。
- 接收同时兼容真实字节帧和 ASCII Hex 文本帧。
- `A5 <6 bytes>` 解析密码，`FFFFFFFFFFFF` 显示为出厂未设置；界面同时展示 HEX 与 ASCII 两种形式。
- `A0 <channel> <A1|A2> <state> <HH> <LL>` 解析通道、控制类型、开关状态与延时。
- 延时时间计算：`T = 255 × HH + LL`，编码时 `HH = T / 255`、`LL = T % 255`，上限 `65279` 秒。
- 密码设置：最多 6 位可打印 ASCII 字符，逐字符转为单字节；数字 `0`–`9` 与 `0x00`–`0x09` 互换，其他字符使用其 ASCII 码，不足 6 字节时以 `FF` 填充。例如 `123456` → `01 02 03 04 05 06`，`A1` → `41 01 FF FF FF FF`。
- 密码重置：发送 `AA 07 A4 FF FF FF FF FF FF`。
