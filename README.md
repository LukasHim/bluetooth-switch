# BT Switch

基于 Kotlin、Jetpack Compose 与 Material 3 的 BLE 蓝牙开关控制应用。包名为
`com.lukas.bluetoothswitch`，应用名为 **BT Switch**。

## 功能

- **设备**：扫描名称以 `YX_` 或 `BK_` 开头的 BLE 设备；支持连接、断开、按设备保存“自动连接”；已连接设备排在最上方，其余设备按名称字母升序排列。
- **自动跳转**：连接成功并完成服务发现后，自动切换到“控制”页并选中该设备。
- **控制**：顶部可在多个已连接设备之间切换；读取状态、即时开关、延时动作、清除延时、读取/设置 6 位密码。
- **设置**：主题模式（跟随系统 / 浅色 / 深色）、动态取色（Android 12+）、关于信息。
- **协议**：按 `protocol.md` 收发明文 Hex 字节，支持 `AA 01 A0`、`AA 01 A5`、`AA 03 A1`、`AA 05 A2`、`AA 07 A4` 等指令。

## 构建

- Android Studio / Android SDK 36
- JDK 17+
- Gradle 8.13（项目自带 wrapper）
- minSdk 26，targetSdk 36

```bash
./gradlew assembleDebug
```

APK 输出：`app/build/outputs/apk/debug/app-debug.apk`

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
- `A5 <6 bytes>` 解析密码，`FFFFFFFFFFFF` 显示为出厂未设置。
- `A0 <channel> <A1|A2> <state> <HH> <LL>` 解析通道、控制类型、开关状态与延时。
- 延时时间计算：`T = 255 × HH + LL`，编码时 `HH = T / 255`、`LL = T % 255`，上限 `65279` 秒。
- 密码设置：6 位数字逐位转为 `0x00`–`0x09` 的单字节数据，例如 `123456` → `01 02 03 04 05 06`。
