package com.lukas.bluetoothswitch.data

enum class BleConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    DISCONNECTING,
}

data class BleDevice(
    val address: String,
    val name: String,
    val rssi: Int,
    val connectionState: BleConnectionState,
) {
    val isConnected: Boolean
        get() = connectionState == BleConnectionState.CONNECTED

    val isBusy: Boolean
        get() = connectionState == BleConnectionState.CONNECTING ||
            connectionState == BleConnectionState.DISCONNECTING

    val displayName: String
        get() = name.ifBlank { address }
}

data class ChannelStatus(
    val channel: Int,
    val control: SwitchControl = SwitchControl.IMMEDIATE,
    val isOn: Boolean = false,
    val delaySeconds: Int = 0,
)

data class DeviceControlState(
    val channels: Map<Int, ChannelStatus> = emptyMap(),
    val passwordHex: String? = null,
    val lastStatusAt: Long? = null,
    val lastPasswordAt: Long? = null,
    val notificationsEnabled: Boolean = true,
) {
    val channelsOrDefault: List<ChannelStatus>
        get() = (channels.values.ifEmpty { listOf(ChannelStatus(channel = 1)) })
            .sortedBy { it.channel }
}

sealed interface BleEvent {
    data class Connected(val address: String, val name: String) : BleEvent
    data class Disconnected(val address: String, val name: String) : BleEvent
    data class Message(val text: String, val isError: Boolean = false) : BleEvent
}

enum class ThemeMode(val storageValue: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark"),
    ;

    companion object {
        fun fromStorage(value: String?): ThemeMode =
            entries.firstOrNull { it.storageValue == value } ?: SYSTEM
    }
}

enum class AutoRefreshMode(val storageValue: String) {
    OFF("off"),
    DELAYED("delayed"),
    ALWAYS("always"),
    ;

    companion object {
        fun fromStorage(value: String?): AutoRefreshMode =
            entries.firstOrNull { it.storageValue == value } ?: OFF
    }
}

data class DelayMemoryEntry(
    val address: String,
    val channel: Int,
    val seconds: Int,
) {
    val key: String
        get() = key(address, channel)

    companion object {
        fun key(address: String, channel: Int): String = "$address#$channel"

        fun fromKey(key: String, seconds: Int): DelayMemoryEntry? {
            val separator = key.lastIndexOf('#')
            if (separator <= 0) return null
            val channel = key.substring(separator + 1).toIntOrNull() ?: return null
            if (channel !in 0..255) return null
            return DelayMemoryEntry(
                address = key.substring(0, separator),
                channel = channel,
                seconds = seconds,
            )
        }
    }
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val autoConnectMacs: Set<String> = emptySet(),
    val autoRefreshMode: AutoRefreshMode = AutoRefreshMode.OFF,
    val autoRefreshSeconds: Int = DEFAULT_AUTO_REFRESH_SECONDS,
    val delayMemory: Map<String, Int> = emptyMap(),
) {
    companion object {
        const val DEFAULT_AUTO_REFRESH_SECONDS = 5
        const val MIN_AUTO_REFRESH_SECONDS = 1
        const val MAX_AUTO_REFRESH_SECONDS = 3600
    }
}
