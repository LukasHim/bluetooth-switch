package com.lukas.bluetoothswitch.data

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.lukas.bluetoothswitch.R
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Owns BLE scanning, multiple simultaneous GATT connections and the state cache
 * that backs the Compose UI.
 */
@SuppressLint("MissingPermission")
class BluetoothRepository(context: Context) {
    private val appContext = context.applicationContext
    private val bluetoothManager = appContext.getSystemService(BluetoothManager::class.java)
    private val adapter: BluetoothAdapter?
        get() = bluetoothManager?.adapter
    private val scanner
        get() = runCatching { adapter?.bluetoothLeScanner }.getOrNull()
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _devices = MutableStateFlow<List<BleDevice>>(emptyList())
    val devices: StateFlow<List<BleDevice>> = _devices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _isBluetoothEnabled = MutableStateFlow(true)
    val isBluetoothEnabled: StateFlow<Boolean> = _isBluetoothEnabled.asStateFlow()

    private val _controlStates = MutableStateFlow<Map<String, DeviceControlState>>(emptyMap())
    val controlStates: StateFlow<Map<String, DeviceControlState>> = _controlStates.asStateFlow()

    private val _events = MutableSharedFlow<BleEvent>(extraBufferCapacity = 32)
    val events = _events.asSharedFlow()

    private val autoConnectMacs = MutableStateFlow<Set<String>>(emptySet())
    private val knownDevices = LinkedHashMap<String, KnownDevice>()
    private val connections = HashMap<String, GattConnection>()
    private val autoConnectAttempted = mutableSetOf<String>()

    private val adapterStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                refreshBluetoothState()
                if (!_isBluetoothEnabled.value) stopScan()
            }
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            handleScanResult(result)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            results.orEmpty().forEach(::handleScanResult)
        }

        override fun onScanFailed(errorCode: Int) {
            _isScanning.value = false
            emitMessage(appContext.getString(R.string.message_scan_failed, errorCode), isError = true)
        }
    }

    init {
        refreshBluetoothState()
        runCatching {
            ContextCompat.registerReceiver(
                appContext,
                adapterStateReceiver,
                IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        }
    }

    fun updateAutoConnectMacs(macs: Set<String>) {
        autoConnectMacs.value = macs
        if (_isScanning.value) {
            macs.forEach { address ->
                if (knownDevices.containsKey(address)) {
                    maybeAutoConnect(address)
                }
            }
        }
    }

    fun refreshBluetoothState() {
        val enabled = if (BlePermissions.hasConnectPermission(appContext)) {
            runCatching { adapter?.isEnabled == true }.getOrDefault(false)
        } else {
            true
        }
        _isBluetoothEnabled.value = enabled
    }

    fun startScan() {
        refreshBluetoothState()
        val adapter = adapter ?: run {
            emitMessage(appContext.getString(R.string.message_bluetooth_unavailable), isError = true)
            return
        }
        if (!BlePermissions.hasScanPermission(appContext)) return
        if (!_isBluetoothEnabled.value) return
        if (_isScanning.value) return

        val bleScanner = runCatching { adapter.bluetoothLeScanner }.getOrNull() ?: return
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0L)
            .build()

        autoConnectAttempted.clear()
        _isScanning.value = true
        try {
            bleScanner.startScan(null, settings, scanCallback)
        } catch (error: SecurityException) {
            _isScanning.value = false
            emitMessage(appContext.getString(R.string.message_permission_missing), isError = true)
        } catch (error: IllegalStateException) {
            _isScanning.value = false
        }
    }

    fun stopScan() {
        if (!_isScanning.value) return
        _isScanning.value = false
        runCatching { scanner?.stopScan(scanCallback) }
    }

    fun restartScan() {
        stopScan()
        mainHandler.postDelayed({ startScan() }, 350L)
    }

    fun connect(address: String) {
        if (!BlePermissions.hasConnectPermission(appContext)) {
            emitMessage(appContext.getString(R.string.message_permission_missing), isError = true)
            return
        }
        refreshBluetoothState()
        if (!_isBluetoothEnabled.value) {
            emitMessage(appContext.getString(R.string.message_bluetooth_disabled), isError = true)
            return
        }

        val existing = connections[address]
        if (existing != null &&
            existing.state != BleConnectionState.DISCONNECTED
        ) {
            return
        }
        existing?.let {
            runCatching { it.gatt?.close() }
            connections.remove(address)
        }

        val adapter = adapter ?: run {
            emitMessage(appContext.getString(R.string.message_bluetooth_unavailable), isError = true)
            return
        }
        val remoteDevice = runCatching { adapter.getRemoteDevice(address) }.getOrNull() ?: run {
            emitMessage(appContext.getString(R.string.message_connect_failed, 0), isError = true)
            return
        }
        val name = knownDevices[address]?.name
            ?: runCatching { remoteDevice.name?.trim() }.getOrNull()?.takeUnless { it.isNullOrBlank() }
            ?: address

        val connection = GattConnection(address = address, name = name)
        connections[address] = connection
        publishDevices()

        try {
            val gatt = remoteDevice.connectGatt(
                appContext,
                false,
                createGattCallback(address),
                BluetoothDevice.TRANSPORT_LE,
            )
            if (gatt == null) {
                connections.remove(address)
                publishDevices()
                emitMessage(appContext.getString(R.string.message_connect_failed, 0), isError = true)
            } else {
                connection.gatt = gatt
            }
        } catch (error: SecurityException) {
            connections.remove(address)
            publishDevices()
            emitMessage(appContext.getString(R.string.message_permission_missing), isError = true)
        }
    }

    fun requestAutoConnect(address: String) {
        if (address !in autoConnectMacs.value) return
        if (!knownDevices.containsKey(address) && !connections.containsKey(address)) return
        autoConnectAttempted.remove(address)
        maybeAutoConnect(address)
    }

    fun disconnect(address: String) {
        val connection = connections[address] ?: return
        if (connection.state == BleConnectionState.DISCONNECTING) return

        connection.suppressDisconnectEvent = false
        connection.state = BleConnectionState.DISCONNECTING
        connection.ready = false
        connection.writeInFlight = false
        connection.queue.clear()
        publishDevices()

        val gatt = connection.gatt
        if (gatt == null) {
            handleDisconnected(connection, null, BluetoothGatt.GATT_SUCCESS)
            return
        }
        runCatching { gatt.disconnect() }
        mainHandler.postDelayed({
            if (connections[address] === connection &&
                connection.state == BleConnectionState.DISCONNECTING
            ) {
                handleDisconnected(connection, connection.gatt, BluetoothGatt.GATT_SUCCESS)
            }
        }, 2_000L)
    }

    fun send(address: String, payload: ByteArray) {
        val connection = connections[address]
        if (connection == null || !connection.ready) {
            emitMessage(appContext.getString(R.string.message_not_connected), isError = true)
            return
        }
        if (connection.writeCharacteristic == null) {
            emitMessage(appContext.getString(R.string.message_no_write_characteristic), isError = true)
            return
        }
        if (connection.queue.size >= MAX_WRITE_QUEUE) {
            connection.queue.removeFirst()
        }
        connection.queue.addLast(payload)
        drainWrites(connection)
    }

    fun readStatus(address: String) {
        send(address, Protocol.readStatus())
    }

    fun readPassword(address: String) {
        send(address, Protocol.readPassword())
    }

    fun close() {
        stopScan()
        runCatching { appContext.unregisterReceiver(adapterStateReceiver) }
        connections.values.toList().forEach { connection ->
            runCatching {
                connection.gatt?.disconnect()
                connection.gatt?.close()
            }
        }
        connections.clear()
        mainHandler.removeCallbacksAndMessages(null)
    }

    private fun handleScanResult(result: ScanResult?) {
        val device = result?.device ?: return
        val advertisedName = result.scanRecord?.deviceName?.trim()
        val name = advertisedName?.takeUnless { it.isNullOrBlank() }
            ?: runCatching { device.name?.trim() }.getOrNull()?.takeUnless { it.isNullOrBlank() }
            ?: return
        if (!isTargetDeviceName(name)) return

        val address = device.address ?: return
        knownDevices[address] = KnownDevice(
            address = address,
            name = name,
            rssi = result.rssi,
            lastSeen = System.currentTimeMillis(),
        )
        publishDevices()
        maybeAutoConnect(address)
    }

    private fun maybeAutoConnect(address: String) {
        if (address !in autoConnectMacs.value) return
        val state = connections[address]?.state
        if (state == BleConnectionState.CONNECTING ||
            state == BleConnectionState.CONNECTED ||
            state == BleConnectionState.DISCONNECTING
        ) {
            return
        }
        if (!autoConnectAttempted.add(address)) return
        connect(address)
    }

    private fun publishDevices() {
        val addresses = LinkedHashSet<String>().apply {
            addAll(knownDevices.keys)
            addAll(connections.keys)
        }
        val list = addresses.mapNotNull { address ->
            val known = knownDevices[address]
            val connection = connections[address]
            val name = known?.name ?: connection?.name ?: return@mapNotNull null
            BleDevice(
                address = address,
                name = name,
                rssi = known?.rssi ?: UNKNOWN_RSSI,
                connectionState = connection?.state ?: BleConnectionState.DISCONNECTED,
            )
        }.sortedWith(
            compareByDescending<BleDevice> { it.connectionState == BleConnectionState.CONNECTED }
                .thenBy { it.displayName.lowercase(Locale.getDefault()) }
                .thenBy { it.address },
        )
        _devices.value = list
    }

    private fun createGattCallback(address: String): BluetoothGattCallback =
        object : BluetoothGattCallback() {
            override fun onConnectionStateChange(
                gatt: BluetoothGatt,
                status: Int,
                newState: Int,
            ) {
                val connection = connections[address]
                if (connection == null) {
                    runCatching { gatt.close() }
                    return
                }
                connection.gatt = gatt

                if (status != BluetoothGatt.GATT_SUCCESS) {
                    handleDisconnected(connection, gatt, status)
                    return
                }

                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        connection.state = BleConnectionState.CONNECTING
                        publishDevices()
                        val started = runCatching { gatt.discoverServices() }.getOrDefault(false)
                        if (!started) {
                            connection.suppressDisconnectEvent = true
                            connection.state = BleConnectionState.DISCONNECTING
                            emitMessage(
                                appContext.getString(R.string.message_services_failed, 0),
                                isError = true,
                            )
                            runCatching { gatt.disconnect() }
                            handleDisconnected(connection, gatt, status)
                        }
                    }

                    BluetoothProfile.STATE_DISCONNECTED -> {
                        handleDisconnected(connection, gatt, status)
                    }
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                val connection = connections[address] ?: return
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    connection.suppressDisconnectEvent = true
                    connection.state = BleConnectionState.DISCONNECTING
                    emitMessage(
                        appContext.getString(R.string.message_services_failed, status),
                        isError = true,
                    )
                    runCatching { gatt.disconnect() }
                    handleDisconnected(connection, gatt, status)
                    return
                }
                configureCharacteristics(gatt, connection)
            }

            override fun onCharacteristicWrite(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int,
            ) {
                val connection = connections[address] ?: return
                if (connection.writeType == BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE) {
                    return
                }
                connection.writeInFlight = false
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    emitMessage(appContext.getString(R.string.message_command_failed), isError = true)
                }
                drainWrites(connection)
            }

            override fun onDescriptorWrite(
                gatt: BluetoothGatt,
                descriptor: BluetoothGattDescriptor,
                status: Int,
            ) {
                val connection = connections[address] ?: return
                if (!connection.awaitingDescriptor) return
                connection.awaitingDescriptor = false
                connection.notificationsEnabled = status == BluetoothGatt.GATT_SUCCESS
                if (!connection.notificationsEnabled) {
                    emitMessage(
                        appContext.getString(R.string.message_notifications_unavailable),
                        isError = false,
                    )
                }
                markReady(connection)
            }

            @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
            ) {
                handleNotification(address, characteristic.value)
            }

            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray,
            ) {
                handleNotification(address, value)
            }
        }

    private fun configureCharacteristics(gatt: BluetoothGatt, connection: GattConnection) {
        val characteristics = runCatching {
            gatt.services.orEmpty().flatMap { service -> service.characteristics.orEmpty() }
        }.getOrElse { emptyList() }

        fun isWritable(characteristic: BluetoothGattCharacteristic): Boolean {
            val properties = characteristic.properties
            return properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0 ||
                properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0
        }

        fun isNotifiable(characteristic: BluetoothGattCharacteristic): Boolean {
            val properties = characteristic.properties
            return properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0 ||
                properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0
        }

        val writeCharacteristic = characteristics.firstOrNull {
            it.uuid == HM10_CHARACTERISTIC && isWritable(it)
        } ?: characteristics.firstOrNull {
            it.uuid == NORDIC_RX_CHARACTERISTIC && isWritable(it)
        } ?: characteristics.firstOrNull(::isWritable)

        val notifyCharacteristic = characteristics.firstOrNull {
            it.uuid == HM10_CHARACTERISTIC && isNotifiable(it)
        } ?: characteristics.firstOrNull {
            it.uuid == NORDIC_TX_CHARACTERISTIC && isNotifiable(it)
        } ?: writeCharacteristic?.takeIf(::isNotifiable)
        ?: characteristics.firstOrNull(::isNotifiable)

        connection.writeCharacteristic = writeCharacteristic
        connection.notifyCharacteristic = notifyCharacteristic
        connection.writeType = if (writeCharacteristic != null &&
            writeCharacteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0
        ) {
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        } else {
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        }

        if (writeCharacteristic == null) {
            connection.suppressDisconnectEvent = true
            connection.state = BleConnectionState.DISCONNECTING
            emitMessage(appContext.getString(R.string.message_no_write_characteristic), isError = true)
            publishDevices()
            runCatching { gatt.disconnect() }
            handleDisconnected(connection, gatt, BluetoothGatt.GATT_SUCCESS)
            return
        }

        if (notifyCharacteristic == null) {
            connection.notificationsEnabled = false
            emitMessage(
                appContext.getString(R.string.message_notifications_unavailable),
                isError = false,
            )
            markReady(connection)
            return
        }

        configureNotifications(gatt, connection, notifyCharacteristic)
    }

    private fun configureNotifications(
        gatt: BluetoothGatt,
        connection: GattConnection,
        characteristic: BluetoothGattCharacteristic,
    ) {
        val localEnabled = runCatching {
            gatt.setCharacteristicNotification(characteristic, true)
        }.getOrDefault(false)

        if (!localEnabled) {
            connection.notificationsEnabled = false
            emitMessage(
                appContext.getString(R.string.message_notifications_unavailable),
                isError = false,
            )
            markReady(connection)
            return
        }

        val descriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_UUID)
        if (descriptor == null) {
            connection.notificationsEnabled = true
            markReady(connection)
            return
        }

        val value = if (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) {
            BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        } else {
            BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
        }

        connection.awaitingDescriptor = true
        connection.notificationsEnabled = true
        val started = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeDescriptor(descriptor, value) == BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                descriptor.value = value
                @Suppress("DEPRECATION")
                gatt.writeDescriptor(descriptor)
            }
        }.getOrDefault(false)

        if (!started) {
            connection.awaitingDescriptor = false
            connection.notificationsEnabled = false
            emitMessage(
                appContext.getString(R.string.message_notifications_unavailable),
                isError = false,
            )
            markReady(connection)
            return
        }

        // A few BLE stacks never deliver onDescriptorWrite. Do not leave the UI
        // stuck in "connecting" forever.
        mainHandler.postDelayed({
            if (connections[connection.address] === connection && !connection.ready) {
                connection.awaitingDescriptor = false
                markReady(connection)
            }
        }, DESCRIPTOR_TIMEOUT_MS)
    }

    private fun markReady(connection: GattConnection) {
        if (connection.ready) return
        val gatt = connection.gatt ?: return
        connection.ready = true
        connection.state = BleConnectionState.CONNECTED
        publishDevices()
        _controlStates.update { states ->
            val current = states[connection.address] ?: DeviceControlState()
            states + (
                connection.address to current.copy(
                    notificationsEnabled = connection.notificationsEnabled,
                )
                )
        }
        _events.tryEmit(BleEvent.Connected(connection.address, connection.name))
        runCatching {
            gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
        }
        readStatus(connection.address)
    }

    private fun handleNotification(address: String, value: ByteArray?) {
        if (value == null || value.isEmpty()) return
        val messages = Protocol.parseNotification(value)
        if (messages.isEmpty()) return
        val now = System.currentTimeMillis()
        _controlStates.update { states ->
            var current = states[address] ?: DeviceControlState()
            messages.forEach { message ->
                current = when (message) {
                    is IncomingMessage.Password -> current.copy(
                        passwordHex = message.hex,
                        lastPasswordAt = now,
                    )

                    is IncomingMessage.ChannelStatus -> current.copy(
                        channels = current.channels + (
                            message.channel to ChannelStatus(
                                channel = message.channel,
                                control = message.control,
                                isOn = message.isOn,
                                delaySeconds = message.delaySeconds,
                            )
                            ),
                        lastStatusAt = now,
                    )
                }
            }
            states + (address to current)
        }
    }

    private fun drainWrites(connection: GattConnection) {
        if (connection.writeInFlight || connection.queue.isEmpty()) return
        val gatt = connection.gatt ?: return
        val characteristic = connection.writeCharacteristic ?: return
        val payload = connection.queue.removeFirst()
        connection.writeInFlight = true
        connection.writeToken += 1
        val token = connection.writeToken

        val started = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeCharacteristic(characteristic, payload, connection.writeType) ==
                    BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                characteristic.writeType = connection.writeType
                @Suppress("DEPRECATION")
                characteristic.value = payload
                @Suppress("DEPRECATION")
                gatt.writeCharacteristic(characteristic)
            }
        }.getOrDefault(false)

        if (!started) {
            connection.writeInFlight = false
            emitMessage(appContext.getString(R.string.message_command_failed), isError = true)
            return
        }

        val timeout = if (connection.writeType == BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE) {
            NO_RESPONSE_WRITE_GAP_MS
        } else {
            WRITE_TIMEOUT_MS
        }
        mainHandler.postDelayed({
            if (connections[connection.address] === connection &&
                connection.writeInFlight &&
                connection.writeToken == token
            ) {
                connection.writeInFlight = false
                drainWrites(connection)
            }
        }, timeout)
    }

    private fun handleDisconnected(
        connection: GattConnection,
        gatt: BluetoothGatt?,
        status: Int,
    ) {
        if (connections[connection.address] !== connection) {
            runCatching { gatt?.close() }
            return
        }
        val wasManual = connection.state == BleConnectionState.DISCONNECTING
        connections.remove(connection.address)
        connection.state = BleConnectionState.DISCONNECTED
        connection.ready = false
        connection.writeInFlight = false
        connection.queue.clear()
        runCatching { gatt?.close() }
        publishDevices()

        when {
            wasManual -> {
                if (!connection.suppressDisconnectEvent) {
                    _events.tryEmit(BleEvent.Disconnected(connection.address, connection.name))
                }
            }

            status != BluetoothGatt.GATT_SUCCESS -> {
                emitMessage(
                    appContext.getString(R.string.message_connect_failed, status),
                    isError = true,
                )
            }

            else -> {
                _events.tryEmit(BleEvent.Disconnected(connection.address, connection.name))
            }
        }
    }

    private fun emitMessage(text: String, isError: Boolean) {
        _events.tryEmit(BleEvent.Message(text = text, isError = isError))
    }

    private fun isTargetDeviceName(name: String): Boolean =
        name.startsWith("YX_", ignoreCase = true) || name.startsWith("BK_", ignoreCase = true)

    private data class KnownDevice(
        val address: String,
        val name: String,
        val rssi: Int,
        val lastSeen: Long,
    )

    private class GattConnection(
        val address: String,
        val name: String,
    ) {
        var gatt: BluetoothGatt? = null
        var writeCharacteristic: BluetoothGattCharacteristic? = null
        var notifyCharacteristic: BluetoothGattCharacteristic? = null
        var state: BleConnectionState = BleConnectionState.CONNECTING
        var ready: Boolean = false
        var writeInFlight: Boolean = false
        var writeToken: Int = 0
        var writeType: Int = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        var notificationsEnabled: Boolean = true
        var awaitingDescriptor: Boolean = false
        var suppressDisconnectEvent: Boolean = false
        val queue: ArrayDeque<ByteArray> = ArrayDeque()
    }

    private companion object {
        const val UNKNOWN_RSSI = -127
        const val MAX_WRITE_QUEUE = 32
        const val WRITE_TIMEOUT_MS = 1_500L
        const val NO_RESPONSE_WRITE_GAP_MS = 120L
        const val DESCRIPTOR_TIMEOUT_MS = 1_500L

        val HM10_CHARACTERISTIC: UUID =
            UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb")
        val NORDIC_RX_CHARACTERISTIC: UUID =
            UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e")
        val NORDIC_TX_CHARACTERISTIC: UUID =
            UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e")
        val CLIENT_CHARACTERISTIC_CONFIG_UUID: UUID =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
}
