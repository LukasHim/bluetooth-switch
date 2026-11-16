package com.lukas.bluetoothswitch.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.BluetoothSearching
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BluetoothConnected
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lukas.bluetoothswitch.MainUiState
import com.lukas.bluetoothswitch.R
import com.lukas.bluetoothswitch.data.BleConnectionState
import com.lukas.bluetoothswitch.data.BleDevice
import com.lukas.bluetoothswitch.ui.components.EmptyState
import com.lukas.bluetoothswitch.ui.components.MessageCard
import com.lukas.bluetoothswitch.ui.components.SectionHeader

@Composable
fun DevicesScreen(
    uiState: MainUiState,
    permissionsGranted: Boolean,
    permissionRequested: Boolean,
    locationEnabled: Boolean,
    onRequestPermissions: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onEnableBluetooth: () -> Unit,
    onOpenLocationSettings: () -> Unit,
    onConnect: (String) -> Unit,
    onDisconnect: (String) -> Unit,
    onToggleAutoConnect: (String, Boolean) -> Unit,
    onRestartScan: () -> Unit,
) {
    val connectedDevices = uiState.devices.filter { it.isConnected }
    val disconnectedDevices = uiState.devices.filterNot { it.isConnected }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!permissionsGranted) {
            item(key = "permission") {
                MessageCard(
                    icon = Icons.Rounded.Warning,
                    title = stringResource(R.string.permission_title),
                    message = if (permissionRequested) {
                        stringResource(R.string.permission_denied_message)
                    } else {
                        stringResource(R.string.permission_message)
                    },
                    actionLabel = stringResource(R.string.grant_permission),
                    onAction = onRequestPermissions,
                    secondaryActionLabel = if (permissionRequested) {
                        stringResource(R.string.open_app_settings)
                    } else {
                        null
                    },
                    onSecondaryAction = if (permissionRequested) onOpenAppSettings else null,
                )
            }
        } else {
            if (!uiState.bluetoothEnabled) {
                item(key = "bluetooth_off") {
                    MessageCard(
                        icon = Icons.Rounded.Bluetooth,
                        title = stringResource(R.string.bluetooth_off_title),
                        message = stringResource(R.string.bluetooth_off_message),
                        actionLabel = stringResource(R.string.enable_bluetooth),
                        onAction = onEnableBluetooth,
                    )
                }
            }

            if (!locationEnabled) {
                item(key = "location_off") {
                    MessageCard(
                        icon = Icons.Rounded.LocationOn,
                        title = stringResource(R.string.location_off_title),
                        message = stringResource(R.string.location_off_message),
                        actionLabel = stringResource(R.string.open_location_settings),
                        onAction = onOpenLocationSettings,
                    )
                }
            }

            if (uiState.bluetoothEnabled) {
                item(key = "scan_status") {
                    ScanStatusCard(
                        isScanning = uiState.isScanning,
                        onRestartScan = onRestartScan,
                    )
                }
            }

            if (uiState.devices.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        icon = Icons.AutoMirrored.Rounded.BluetoothSearching,
                        title = stringResource(R.string.no_devices_title),
                        message = stringResource(R.string.no_devices_message),
                    )
                }
            } else {
                if (connectedDevices.isNotEmpty()) {
                    item(key = "header_connected") {
                        SectionHeader(
                            title = stringResource(R.string.section_connected),
                            count = connectedDevices.size,
                        )
                    }
                    items(connectedDevices, key = { it.address }) { device ->
                        DeviceCard(
                            device = device,
                            autoConnect = device.address in uiState.settings.autoConnectMacs,
                            onConnect = { onConnect(device.address) },
                            onDisconnect = { onDisconnect(device.address) },
                            onAutoConnectChange = { enabled ->
                                onToggleAutoConnect(device.address, enabled)
                            },
                        )
                    }
                }

                if (disconnectedDevices.isNotEmpty()) {
                    item(key = "header_disconnected") {
                        SectionHeader(
                            title = stringResource(R.string.section_disconnected),
                            count = disconnectedDevices.size,
                        )
                    }
                    items(disconnectedDevices, key = { it.address }) { device ->
                        DeviceCard(
                            device = device,
                            autoConnect = device.address in uiState.settings.autoConnectMacs,
                            onConnect = { onConnect(device.address) },
                            onDisconnect = { onDisconnect(device.address) },
                            onAutoConnectChange = { enabled ->
                                onToggleAutoConnect(device.address, enabled)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScanStatusCard(
    isScanning: Boolean,
    onRestartScan: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (isScanning) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.BluetoothSearching,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(
                        if (isScanning) R.string.scanning else R.string.scan_idle,
                    ),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = stringResource(R.string.scan_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onRestartScan) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    stringResource(
                        if (isScanning) R.string.scan_again else R.string.start_scan,
                    ),
                )
            }
        }
    }
}

@Composable
private fun DeviceCard(
    device: BleDevice,
    autoConnect: Boolean,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onAutoConnectChange: (Boolean) -> Unit,
) {
    val iconColor = if (device.isConnected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSecondaryContainer
    }
    val iconBackground = if (device.isConnected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = CircleShape,
                    color = iconBackground,
                    modifier = Modifier.size(44.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (device.isConnected) {
                                Icons.Rounded.BluetoothConnected
                            } else {
                                Icons.Rounded.Bluetooth
                            },
                            contentDescription = null,
                            tint = iconColor,
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = device.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(R.string.device_address, device.address),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                    Spacer(modifier = Modifier.size(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ConnectionLabel(device.connectionState)
                        if (device.rssi > -127) {
                            Text(
                                text = stringResource(R.string.device_rssi, device.rssi),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                DeviceActionButton(
                    state = device.connectionState,
                    onConnect = onConnect,
                    onDisconnect = onDisconnect,
                )
            }

            HorizontalDivider()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.auto_connect),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Text(
                        text = stringResource(R.string.auto_connect_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = autoConnect,
                    onCheckedChange = onAutoConnectChange,
                )
            }
        }
    }
}

@Composable
private fun ConnectionLabel(state: BleConnectionState) {
    val (textRes, color) = when (state) {
        BleConnectionState.CONNECTED -> R.string.connected to MaterialTheme.colorScheme.primary
        BleConnectionState.CONNECTING -> R.string.connecting to MaterialTheme.colorScheme.secondary
        BleConnectionState.DISCONNECTING -> R.string.disconnecting to MaterialTheme.colorScheme.secondary
        BleConnectionState.DISCONNECTED -> R.string.section_disconnected to MaterialTheme.colorScheme.outline
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(color),
        )
        Text(
            text = stringResource(textRes),
            style = MaterialTheme.typography.labelSmall,
            color = color,
        )
    }
}

@Composable
private fun DeviceActionButton(
    state: BleConnectionState,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
) {
    when (state) {
        BleConnectionState.CONNECTED -> {
            OutlinedButton(onClick = onDisconnect) {
                Text(stringResource(R.string.disconnect))
            }
        }

        BleConnectionState.CONNECTING -> {
            FilledTonalButton(onClick = {}, enabled = false) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.connecting))
            }
        }

        BleConnectionState.DISCONNECTING -> {
            FilledTonalButton(onClick = {}, enabled = false) {
                Text(stringResource(R.string.disconnecting))
            }
        }

        BleConnectionState.DISCONNECTED -> {
            Button(onClick = onConnect) {
                Text(stringResource(R.string.connect))
            }
        }
    }
}
