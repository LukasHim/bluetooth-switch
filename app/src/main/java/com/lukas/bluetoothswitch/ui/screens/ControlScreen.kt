package com.lukas.bluetoothswitch.ui.screens

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BluetoothConnected
import androidx.compose.material.icons.rounded.BluetoothDisabled
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lukas.bluetoothswitch.MainUiState
import com.lukas.bluetoothswitch.R
import com.lukas.bluetoothswitch.data.ChannelStatus
import com.lukas.bluetoothswitch.data.Protocol
import com.lukas.bluetoothswitch.data.SwitchControl
import com.lukas.bluetoothswitch.ui.components.EmptyState
import com.lukas.bluetoothswitch.ui.components.SectionHeader
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun ControlScreen(
    uiState: MainUiState,
    onNavigateToDevices: () -> Unit,
    onReadStatus: (String) -> Unit,
    onReadPassword: (String) -> Unit,
    onSetSwitch: (String, Int, Boolean) -> Unit,
    onSetDelay: (String, Int, Boolean, Int) -> Unit,
    onClearDelay: (String, Int, Boolean) -> Unit,
    onSetPassword: (String, String) -> Unit,
) {
    val device = uiState.selectedDevice
    if (device == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            EmptyState(
                icon = Icons.Rounded.BluetoothDisabled,
                title = stringResource(R.string.control_no_device_title),
                message = stringResource(R.string.control_no_device_message),
                actionLabel = stringResource(R.string.go_to_devices),
                onAction = onNavigateToDevices,
            )
        }
        return
    }

    val controlState = uiState.controlState
    val channels = controlState.channelsOrDefault
    val statusTime = remember(controlState.lastStatusAt) {
        controlState.lastStatusAt?.let { timestamp ->
            Instant.ofEpochMilli(timestamp)
                .atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("HH:mm:ss"))
        }
    }
    val passwordText = when {
        controlState.passwordHex == null -> stringResource(R.string.password_not_read)
        controlState.passwordHex.equals("FFFFFFFFFFFF", ignoreCase = true) -> {
            stringResource(R.string.password_factory_default)
        }

        else -> controlState.passwordHex.chunked(2).joinToString(" ")
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "device_header") {
            DeviceHeaderCard(
                name = device.displayName,
                address = device.address,
                statusTime = statusTime,
                notificationsEnabled = controlState.notificationsEnabled,
                onReadStatus = { onReadStatus(device.address) },
            )
        }

        channels.forEach { channel ->
            item(key = "${device.address}-channel-${channel.channel}") {
                ChannelCard(
                    deviceAddress = device.address,
                    channel = channel,
                    onSetSwitch = { on -> onSetSwitch(device.address, channel.channel, on) },
                    onSetDelay = { on, seconds ->
                        onSetDelay(device.address, channel.channel, on, seconds)
                    },
                    onClearDelay = { onClearDelay(device.address, channel.channel, channel.isOn) },
                )
            }
        }

        item(key = "password_header") {
            SectionHeader(title = stringResource(R.string.password_title))
        }
        item(key = "password") {
            PasswordCard(
                deviceAddress = device.address,
                passwordText = passwordText,
                onReadPassword = { onReadPassword(device.address) },
                onSetPassword = { password -> onSetPassword(device.address, password) },
            )
        }
    }
}

@Composable
private fun DeviceHeaderCard(
    name: String,
    address: String,
    statusTime: String?,
    notificationsEnabled: Boolean,
    onReadStatus: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(44.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.BluetoothConnected,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(R.string.device_address, address),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                ) {
                    Text(
                        text = stringResource(R.string.connected),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }

            Text(
                text = statusTime?.let { stringResource(R.string.status_updated_at, it) }
                    ?: stringResource(R.string.status_not_read),
                style = MaterialTheme.typography.bodySmall,
            )

            if (!notificationsEnabled) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = stringResource(R.string.notifications_unavailable),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            FilledTonalButton(
                onClick = onReadStatus,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.read_status))
            }
        }
    }
}

@Composable
private fun ChannelCard(
    deviceAddress: String,
    channel: ChannelStatus,
    onSetSwitch: (Boolean) -> Unit,
    onSetDelay: (Boolean, Int) -> Unit,
    onClearDelay: () -> Unit,
) {
    var delayText by rememberSaveable(deviceAddress, channel.channel) {
        mutableStateOf(if (channel.delaySeconds > 0) channel.delaySeconds.toString() else "10")
    }
    val delaySeconds = delayText.toIntOrNull()
    val delayValid = delaySeconds != null && delaySeconds in 0..Protocol.MAX_DELAY_SECONDS

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.channel_title, channel.channel),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(
                            if (channel.control == SwitchControl.DELAYED) {
                                R.string.delayed_control
                            } else {
                                R.string.immediate_control
                            },
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = stringResource(
                        if (channel.isOn) R.string.channel_on else R.string.channel_off,
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (channel.isOn) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                Spacer(modifier = Modifier.width(8.dp))
                Switch(
                    checked = channel.isOn,
                    onCheckedChange = onSetSwitch,
                )
            }

            if (channel.delaySeconds > 0) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ) {
                    Text(
                        text = stringResource(R.string.current_delay, channel.delaySeconds),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }

            HorizontalDivider()

            OutlinedTextField(
                value = delayText,
                onValueChange = { value ->
                    delayText = value.filter(Char::isDigit).take(5)
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.delay_seconds)) },
                supportingText = {
                    Text(
                        stringResource(
                            if (delayValid) R.string.delay_hint else R.string.message_invalid_delay,
                        ),
                    )
                },
                isError = !delayValid,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = { delaySeconds?.let { onSetDelay(true, it) } },
                    enabled = delayValid,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.delay_open))
                }
                FilledTonalButton(
                    onClick = { delaySeconds?.let { onSetDelay(false, it) } },
                    enabled = delayValid,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.delay_close))
                }
            }

            TextButton(
                onClick = onClearDelay,
                enabled = channel.control == SwitchControl.DELAYED || channel.delaySeconds > 0,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.clear_delay))
            }
        }
    }
}

@Composable
private fun PasswordCard(
    deviceAddress: String,
    passwordText: String,
    onReadPassword: () -> Unit,
    onSetPassword: (String) -> Unit,
) {
    var passwordInput by rememberSaveable(deviceAddress) { mutableStateOf("") }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.password_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(R.string.password_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedButton(onClick = onReadPassword) {
                    Text(stringResource(R.string.read_password))
                }
            }

            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = passwordText,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                )
            }

            OutlinedTextField(
                value = passwordInput,
                onValueChange = { value ->
                    passwordInput = value.filter(Char::isDigit).take(6)
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.password_input_label)) },
                supportingText = {
                    Text(stringResource(R.string.password_input_support, passwordInput.length))
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            )

            Button(
                onClick = {
                    onSetPassword(passwordInput)
                    passwordInput = ""
                },
                enabled = passwordInput.length == 6,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.set_password))
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.password_rule),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
