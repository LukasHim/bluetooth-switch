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
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import com.lukas.bluetoothswitch.data.DelayFormat
import com.lukas.bluetoothswitch.data.DelayMemoryEntry
import com.lukas.bluetoothswitch.data.Protocol
import com.lukas.bluetoothswitch.data.SwitchControl
import com.lukas.bluetoothswitch.ui.components.EmptyState
import kotlinx.coroutines.delay

private const val DEFAULT_DELAY_SECONDS = 10

@Composable
fun ControlScreen(
    uiState: MainUiState,
    onNavigateToDevices: () -> Unit,
    onReadStatus: (String) -> Unit,
    onReadPassword: (String) -> Unit,
    onSetSwitch: (String, Int, Boolean) -> Unit,
    onSetDelay: (String, Int, Boolean, Int) -> Unit,
    onRememberDelay: (String, Int, Int) -> Unit,
    onClearDelay: (String, Int, Boolean) -> Unit,
    onSetPassword: (String, String) -> Unit,
    onResetPassword: (String) -> Unit,
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
    val lastStatusAt = controlState.lastStatusAt

    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(lastStatusAt) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            delay(1_000L)
        }
    }

    val statusText = if (lastStatusAt == null) {
        stringResource(R.string.status_not_read)
    } else {
        stringResource(
            R.string.status_updated_relative,
            formatRelativeTime(nowMillis - lastStatusAt),
        )
    }
    val passwordHex = controlState.passwordHex
    val passwordAscii = passwordHex?.let(Protocol::decodePasswordAscii).orEmpty()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "device_header") {
            DeviceHeaderCard(
                name = device.displayName,
                address = device.address,
                statusText = statusText,
                notificationsEnabled = controlState.notificationsEnabled,
                onReadStatus = { onReadStatus(device.address) },
            )
        }

        channels.forEach { channel ->
            item(key = "${device.address}-channel-${channel.channel}") {
                ChannelCard(
                    deviceAddress = device.address,
                    channel = channel,
                    rememberedSeconds = uiState.settings.delayMemory[
                        DelayMemoryEntry.key(device.address, channel.channel)
                    ],
                    onSetSwitch = { on -> onSetSwitch(device.address, channel.channel, on) },
                    onSetDelay = { on, seconds ->
                        onSetDelay(device.address, channel.channel, on, seconds)
                    },
                    onRememberDelay = { seconds ->
                        onRememberDelay(device.address, channel.channel, seconds)
                    },
                    onClearDelay = { onClearDelay(device.address, channel.channel, channel.isOn) },
                )
            }
        }

        item(key = "password") {
            PasswordCard(
                deviceAddress = device.address,
                passwordHex = passwordHex,
                passwordAscii = passwordAscii,
                onReadPassword = { onReadPassword(device.address) },
                onSetPassword = { password -> onSetPassword(device.address, password) },
                onResetPassword = { onResetPassword(device.address) },
            )
        }
    }
}

private fun formatRelativeTime(elapsedMillis: Long): String {
    val totalSeconds = elapsedMillis.coerceAtLeast(0L) / 1_000L
    if (totalSeconds > 86_400L) return ">24h"

    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return buildString {
        if (hours > 0) append(hours).append('h')
        if (hours > 0 || minutes > 0) append(minutes).append('m')
        append(seconds).append('s')
    }
}

@Composable
private fun DeviceHeaderCard(
    name: String,
    address: String,
    statusText: String,
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
                IconButton(onClick = onReadStatus) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = stringResource(R.string.read_status),
                    )
                }
            }

            Text(
                text = statusText,
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
        }
    }
}

@Composable
private fun ChannelCard(
    deviceAddress: String,
    channel: ChannelStatus,
    rememberedSeconds: Int?,
    onSetSwitch: (Boolean) -> Unit,
    onSetDelay: (Boolean, Int) -> Unit,
    onRememberDelay: (Int) -> Unit,
    onClearDelay: () -> Unit,
) {
    val initialSeconds = rememberedSeconds
        ?: channel.delaySeconds.takeIf { seconds -> seconds > 0 }
        ?: DEFAULT_DELAY_SECONDS
    var delayText by remember(deviceAddress, channel.channel) {
        mutableStateOf(DelayFormat.format(initialSeconds))
    }
    var edited by remember(deviceAddress, channel.channel) { mutableStateOf(false) }
    var showPicker by remember(deviceAddress, channel.channel) { mutableStateOf(false) }

    LaunchedEffect(rememberedSeconds) {
        if (rememberedSeconds != null && DelayFormat.parse(delayText) != rememberedSeconds) {
            delayText = DelayFormat.format(rememberedSeconds)
        }
    }
    LaunchedEffect(channel.delaySeconds) {
        if (!edited && rememberedSeconds == null && channel.delaySeconds > 0) {
            val current = DelayFormat.parse(delayText)
            if (current == null || current == DEFAULT_DELAY_SECONDS) {
                delayText = DelayFormat.format(channel.delaySeconds)
            }
        }
    }

    val delaySeconds = DelayFormat.parse(delayText)
    val delayValid = delaySeconds != null

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

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = delayText,
                    onValueChange = { value ->
                        delayText = DelayFormat.sanitizeInput(value)
                        edited = true
                    },
                    modifier = Modifier.weight(1f),
                    label = { Text(stringResource(R.string.delay_time)) },
                    supportingText = if (!delayValid) {
                        { Text(stringResource(R.string.message_invalid_delay)) }
                    } else {
                        null
                    },
                    isError = !delayValid,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                FilledTonalIconButton(
                    onClick = { showPicker = true },
                    modifier = Modifier.size(56.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Schedule,
                        contentDescription = stringResource(R.string.pick_duration),
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = {
                        delaySeconds?.let { seconds ->
                            edited = true
                            onSetDelay(true, seconds)
                        }
                    },
                    enabled = delayValid,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.delay_open))
                }
                FilledTonalButton(
                    onClick = {
                        delaySeconds?.let { seconds ->
                            edited = true
                            onSetDelay(false, seconds)
                        }
                    },
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

    if (showPicker) {
        DurationPickerDialog(
            initialSeconds = delaySeconds ?: 0,
            onDismiss = { showPicker = false },
            onConfirm = { seconds ->
                delayText = DelayFormat.format(seconds)
                edited = true
                onRememberDelay(seconds)
                showPicker = false
            },
        )
    }
}

@Composable
private fun DurationPickerDialog(
    initialSeconds: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var hoursText by rememberSaveable { mutableStateOf((initialSeconds / 3600).toString()) }
    var minutesText by rememberSaveable { mutableStateOf(((initialSeconds % 3600) / 60).toString()) }
    var secondsText by rememberSaveable { mutableStateOf((initialSeconds % 60).toString()) }

    val hours = hoursText.toIntOrNull() ?: 0
    val minutes = minutesText.toIntOrNull()
    val seconds = secondsText.toIntOrNull()
    val total = if (minutes != null && seconds != null &&
        minutes in 0..59 && seconds in 0..59
    ) {
        hours * 3600 + minutes * 60 + seconds
    } else {
        null
    }
    val valid = total != null && total in 0..Protocol.MAX_DELAY_SECONDS

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pick_duration)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DurationField(
                        value = hoursText,
                        onValueChange = { value ->
                            hoursText = value.filter(Char::isDigit).take(2)
                        },
                        label = stringResource(R.string.duration_hours),
                        modifier = Modifier.weight(1f),
                    )
                    DurationField(
                        value = minutesText,
                        onValueChange = { value ->
                            minutesText = value.filter(Char::isDigit).take(2)
                        },
                        label = stringResource(R.string.duration_minutes),
                        isError = !valid,
                        modifier = Modifier.weight(1f),
                    )
                    DurationField(
                        value = secondsText,
                        onValueChange = { value ->
                            secondsText = value.filter(Char::isDigit).take(2)
                        },
                        label = stringResource(R.string.duration_seconds),
                        isError = !valid,
                        modifier = Modifier.weight(1f),
                    )
                }
                Text(
                    text = if (valid) {
                        stringResource(R.string.duration_total, DelayFormat.format(total ?: 0))
                    } else {
                        stringResource(R.string.message_invalid_delay)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (valid) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { total?.let(onConfirm) },
                enabled = valid,
            ) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun DurationField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = { Text(label) },
        isError = isError,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
}

@Composable
private fun PasswordCard(
    deviceAddress: String,
    passwordHex: String?,
    passwordAscii: String,
    onReadPassword: () -> Unit,
    onSetPassword: (String) -> Unit,
    onResetPassword: () -> Unit,
) {
    var passwordInput by rememberSaveable(deviceAddress) { mutableStateOf("") }
    var showResetConfirm by remember { mutableStateOf(false) }

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
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (passwordHex == null) {
                        Text(
                            text = stringResource(R.string.password_not_read),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    } else {
                        PasswordValueRow(
                            label = stringResource(R.string.password_hex_label),
                            value = passwordHex.chunked(2).joinToString(" "),
                        )
                        PasswordValueRow(
                            label = stringResource(R.string.password_ascii_label),
                            value = passwordAscii.ifEmpty {
                                stringResource(R.string.password_ascii_empty)
                            },
                        )
                    }
                }
            }

            OutlinedTextField(
                value = passwordInput,
                onValueChange = { value ->
                    passwordInput = value
                        .filter { char -> char.code in 0x20..0x7E }
                        .take(6)
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.password_input_label)) },
                supportingText = {
                    Text(stringResource(R.string.password_input_support, passwordInput.length))
                },
                singleLine = true,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = {
                        onSetPassword(passwordInput)
                        passwordInput = ""
                    },
                    enabled = passwordInput.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.set_password))
                }
                OutlinedButton(onClick = { showResetConfirm = true }) {
                    Text(stringResource(R.string.password_reset))
                }
            }
        }
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text(stringResource(R.string.password_reset_confirm_title)) },
            text = { Text(stringResource(R.string.password_reset_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetConfirm = false
                        onResetPassword()
                    },
                ) {
                    Text(stringResource(R.string.reset))
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun PasswordValueRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(44.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1f),
        )
    }
}
