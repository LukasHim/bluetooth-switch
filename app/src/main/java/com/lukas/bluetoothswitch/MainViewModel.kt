package com.lukas.bluetoothswitch

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lukas.bluetoothswitch.data.AppSettings
import com.lukas.bluetoothswitch.data.BleDevice
import com.lukas.bluetoothswitch.data.BleEvent
import com.lukas.bluetoothswitch.data.BluetoothRepository
import com.lukas.bluetoothswitch.data.DeviceControlState
import com.lukas.bluetoothswitch.data.Protocol
import com.lukas.bluetoothswitch.data.SettingsRepository
import com.lukas.bluetoothswitch.data.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MainUiState(
    val settings: AppSettings = AppSettings(),
    val devices: List<BleDevice> = emptyList(),
    val connectedDevices: List<BleDevice> = emptyList(),
    val selectedAddress: String? = null,
    val selectedDevice: BleDevice? = null,
    val controlState: DeviceControlState = DeviceControlState(),
    val isScanning: Boolean = false,
    val bluetoothEnabled: Boolean = true,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val settingsRepository = SettingsRepository(application)
    private val bluetoothRepository = BluetoothRepository(application)

    val events = bluetoothRepository.events

    private val selectedAddress = MutableStateFlow<String?>(null)
    private val latestSettings = MutableStateFlow(AppSettings())

    private data class PartialState(
        val settings: AppSettings,
        val devices: List<BleDevice>,
        val isScanning: Boolean,
        val bluetoothEnabled: Boolean,
        val controlStates: Map<String, DeviceControlState>,
    )

    private val partialState = combine(
        settingsRepository.settings,
        bluetoothRepository.devices,
        bluetoothRepository.isScanning,
        bluetoothRepository.isBluetoothEnabled,
    ) { settings, devices, scanning, bluetoothEnabled ->
        PartialState(
            settings = settings,
            devices = devices,
            isScanning = scanning,
            bluetoothEnabled = bluetoothEnabled,
            controlStates = emptyMap(),
        )
    }.combine(bluetoothRepository.controlStates) { partial, controlStates ->
        partial.copy(controlStates = controlStates)
    }

    val uiState: StateFlow<MainUiState> = combine(partialState, selectedAddress) { partial, selected ->
        val connectedDevices = partial.devices.filter { it.isConnected }
        val effectiveAddress = selected
            ?.takeIf { address -> connectedDevices.any { it.address == address } }
            ?: connectedDevices.firstOrNull()?.address
        MainUiState(
            settings = partial.settings,
            devices = partial.devices,
            connectedDevices = connectedDevices,
            selectedAddress = effectiveAddress,
            selectedDevice = partial.devices.firstOrNull { it.address == effectiveAddress },
            controlState = effectiveAddress
                ?.let { address -> partial.controlStates[address] }
                ?: DeviceControlState(),
            isScanning = partial.isScanning,
            bluetoothEnabled = partial.bluetoothEnabled,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MainUiState(),
    )

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                latestSettings.value = settings
                bluetoothRepository.updateAutoConnectMacs(settings.autoConnectMacs)
            }
        }
        viewModelScope.launch {
            bluetoothRepository.events.collect { event ->
                if (event is BleEvent.Connected) {
                    selectedAddress.value = event.address
                }
            }
        }
    }

    fun startScan() = bluetoothRepository.startScan()

    fun stopScan() = bluetoothRepository.stopScan()

    fun restartScan() = bluetoothRepository.restartScan()

    fun refreshBluetoothState() = bluetoothRepository.refreshBluetoothState()

    fun connect(address: String) = bluetoothRepository.connect(address)

    fun disconnect(address: String) = bluetoothRepository.disconnect(address)

    fun selectDevice(address: String) {
        selectedAddress.value = address
    }

    fun setAutoConnect(address: String, enabled: Boolean) {
        val current = latestSettings.value.autoConnectMacs
        val updated = if (enabled) current + address else current - address
        latestSettings.value = latestSettings.value.copy(autoConnectMacs = updated)
        bluetoothRepository.updateAutoConnectMacs(updated)
        if (enabled) bluetoothRepository.requestAutoConnect(address)
        viewModelScope.launch {
            settingsRepository.setAutoConnect(address, enabled)
        }
    }

    fun setSwitch(address: String, channel: Int, on: Boolean) {
        bluetoothRepository.send(address, Protocol.setChannel(channel, on))
    }

    fun setDelay(address: String, channel: Int, on: Boolean, seconds: Int) {
        if (seconds !in 0..Protocol.MAX_DELAY_SECONDS) return
        bluetoothRepository.send(address, Protocol.setDelay(channel, on, seconds))
    }

    fun clearDelay(address: String, channel: Int, actionOn: Boolean) {
        bluetoothRepository.send(address, Protocol.clearDelay(channel, actionOn))
    }

    fun readStatus(address: String) = bluetoothRepository.readStatus(address)

    fun readPassword(address: String) = bluetoothRepository.readPassword(address)

    fun setPassword(address: String, password: String) {
        if (password.length != 6 || password.any { !it.isDigit() }) return
        bluetoothRepository.send(address, Protocol.setPassword(password))
    }

    fun setThemeMode(themeMode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(themeMode) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setDynamicColor(enabled) }
    }

    override fun onCleared() {
        bluetoothRepository.close()
        super.onCleared()
    }
}
