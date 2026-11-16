package com.lukas.bluetoothswitch.ui.screens

import android.os.Build
import androidx.annotation.StringRes
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lukas.bluetoothswitch.BuildConfig
import com.lukas.bluetoothswitch.R
import com.lukas.bluetoothswitch.data.AppSettings
import com.lukas.bluetoothswitch.data.AutoRefreshMode
import com.lukas.bluetoothswitch.data.DelayFormat
import com.lukas.bluetoothswitch.data.DelayMemoryEntry
import com.lukas.bluetoothswitch.data.ThemeMode
import com.lukas.bluetoothswitch.ui.components.SectionHeader

private data class ThemeOption(
    val mode: ThemeMode,
    @param:StringRes val labelRes: Int,
    val icon: ImageVector,
)

private data class AutoRefreshOption(
    val mode: AutoRefreshMode,
    @param:StringRes val labelRes: Int,
    @param:StringRes val descriptionRes: Int,
)

@Composable
fun SettingsScreen(
    settings: AppSettings,
    onThemeModeChange: (ThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onAutoRefreshModeChange: (AutoRefreshMode) -> Unit,
    onAutoRefreshSecondsChange: (Int) -> Unit,
    onDeleteDelayMemory: (String) -> Unit,
    onClearDelayMemories: () -> Unit,
) {
    val themeOptions = listOf(
        ThemeOption(ThemeMode.SYSTEM, R.string.theme_system, Icons.Rounded.BrightnessAuto),
        ThemeOption(ThemeMode.LIGHT, R.string.theme_light, Icons.Rounded.LightMode),
        ThemeOption(ThemeMode.DARK, R.string.theme_dark, Icons.Rounded.DarkMode),
    )
    val dynamicColorSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "appearance_header") {
            SectionHeader(title = stringResource(R.string.settings_appearance))
        }

        item(key = "theme_mode") {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.theme_mode),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    themeOptions.forEachIndexed { index, option ->
                        if (index > 0) {
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        }
                        ThemeOptionRow(
                            option = option,
                            selected = settings.themeMode == option.mode,
                            onClick = { onThemeModeChange(option.mode) },
                        )
                    }
                }
            }
        }

        item(key = "dynamic_color") {
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(42.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.Palette,
                                contentDescription = null,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.dynamic_color),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = when {
                                !dynamicColorSupported -> {
                                    stringResource(R.string.dynamic_color_unavailable)
                                }

                                settings.dynamicColor -> {
                                    stringResource(R.string.dynamic_color_enabled)
                                }

                                else -> {
                                    stringResource(R.string.dynamic_color_disabled)
                                }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = settings.dynamicColor && dynamicColorSupported,
                        onCheckedChange = onDynamicColorChange,
                        enabled = dynamicColorSupported,
                    )
                }
            }
        }

        item(key = "general_header") {
            SectionHeader(title = stringResource(R.string.settings_general))
        }

        item(key = "auto_connect_summary") {
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(42.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.Bluetooth,
                                contentDescription = null,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.auto_connect),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = if (settings.autoConnectMacs.isEmpty()) {
                                stringResource(R.string.auto_connect_summary_none)
                            } else {
                                stringResource(
                                    R.string.auto_connect_summary,
                                    settings.autoConnectMacs.size,
                                )
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        item(key = "auto_refresh") {
            AutoRefreshCard(
                settings = settings,
                onModeChange = onAutoRefreshModeChange,
                onSecondsChange = onAutoRefreshSecondsChange,
            )
        }

        item(key = "delay_memory") {
            DelayMemoryCard(
                delayMemory = settings.delayMemory,
                onDelete = onDeleteDelayMemory,
                onClearAll = onClearDelayMemories,
            )
        }

        item(key = "about_header") {
            SectionHeader(title = stringResource(R.string.settings_about))
        }

        item(key = "about") {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(48.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.Info,
                                    contentDescription = null,
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.app_name),
                                style = MaterialTheme.typography.titleLarge,
                            )
                            Text(
                                text = stringResource(R.string.about_description),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    HorizontalDivider()

                    AboutRow(
                        label = stringResource(R.string.about_version),
                        value = BuildConfig.VERSION_NAME,
                    )
                    AboutRow(
                        label = stringResource(R.string.about_package),
                        value = BuildConfig.APPLICATION_ID,
                    )
                    AboutRow(
                        label = stringResource(R.string.about_protocol),
                        value = stringResource(R.string.about_protocol_value),
                    )
                    AboutRow(
                        label = stringResource(R.string.about_devices),
                        value = stringResource(R.string.about_devices_value),
                    )
                }
            }
        }
    }
}

@Composable
private fun AutoRefreshCard(
    settings: AppSettings,
    onModeChange: (AutoRefreshMode) -> Unit,
    onSecondsChange: (Int) -> Unit,
) {
    val options = listOf(
        AutoRefreshOption(
            AutoRefreshMode.OFF,
            R.string.auto_refresh_off,
            R.string.auto_refresh_off_description,
        ),
        AutoRefreshOption(
            AutoRefreshMode.DELAYED,
            R.string.auto_refresh_delayed,
            R.string.auto_refresh_delayed_description,
        ),
        AutoRefreshOption(
            AutoRefreshMode.ALWAYS,
            R.string.auto_refresh_always,
            R.string.auto_refresh_always_description,
        ),
    )

    var secondsText by rememberSaveable { mutableStateOf(settings.autoRefreshSeconds.toString()) }
    LaunchedEffect(settings.autoRefreshSeconds) {
        if (secondsText.toIntOrNull() != settings.autoRefreshSeconds) {
            secondsText = settings.autoRefreshSeconds.toString()
        }
    }
    val secondsValue = secondsText.toIntOrNull()
    val secondsValid = secondsValue != null &&
        secondsValue in AppSettings.MIN_AUTO_REFRESH_SECONDS..AppSettings.MAX_AUTO_REFRESH_SECONDS

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
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(42.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Autorenew,
                            contentDescription = null,
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.auto_refresh),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(R.string.auto_refresh_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            options.forEachIndexed { index, option ->
                if (index > 0) {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
                AutoRefreshOptionRow(
                    label = stringResource(option.labelRes),
                    description = stringResource(option.descriptionRes),
                    selected = settings.autoRefreshMode == option.mode,
                    onClick = { onModeChange(option.mode) },
                )
            }

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            OutlinedTextField(
                value = secondsText,
                onValueChange = { value ->
                    val filtered = value.filter(Char::isDigit).take(4)
                    secondsText = filtered
                    filtered.toIntOrNull()?.let { seconds ->
                        val inRange = seconds >= AppSettings.MIN_AUTO_REFRESH_SECONDS &&
                            seconds <= AppSettings.MAX_AUTO_REFRESH_SECONDS
                        if (inRange) {
                            onSecondsChange(seconds)
                        }
                    }                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                enabled = settings.autoRefreshMode != AutoRefreshMode.OFF,
                label = { Text(stringResource(R.string.auto_refresh_interval)) },
                supportingText = {
                    Text(stringResource(R.string.auto_refresh_interval_hint))
                },
                isError = !secondsValid,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        }
    }
}

@Composable
private fun AutoRefreshOptionRow(
    label: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        RadioButton(
            selected = selected,
            onClick = null,
        )
    }
}

@Composable
private fun DelayMemoryCard(
    delayMemory: Map<String, Int>,
    onDelete: (String) -> Unit,
    onClearAll: () -> Unit,
) {
    val entries = remember(delayMemory) {
        delayMemory.entries
            .mapNotNull { (key, seconds) -> DelayMemoryEntry.fromKey(key, seconds) }
            .sortedWith(compareBy({ it.address }, { it.channel }))
    }
    var showClearConfirm by remember { mutableStateOf(false) }

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
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.size(42.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.History,
                            contentDescription = null,
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.delay_memory),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(R.string.delay_memory_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (entries.isEmpty()) {
                Text(
                    text = stringResource(R.string.delay_memory_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                )
            } else {
                entries.forEachIndexed { index, entry ->
                    if (index > 0) {
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = entry.address,
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = FontFamily.Monospace,
                            )
                            Text(
                                text = stringResource(
                                    R.string.delay_memory_entry,
                                    entry.channel,
                                    DelayFormat.format(entry.seconds),
                                    entry.seconds,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { onDelete(entry.key) }) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteOutline,
                                contentDescription = stringResource(R.string.delete),
                            )
                        }
                    }
                }

                HorizontalDivider()

                TextButton(
                    onClick = { showClearConfirm = true },
                    modifier = Modifier.padding(horizontal = 8.dp),
                ) {
                    Text(stringResource(R.string.clear_all))
                }
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text(stringResource(R.string.delay_memory_clear_title)) },
            text = { Text(stringResource(R.string.delay_memory_clear_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearConfirm = false
                        onClearAll()
                    },
                ) {
                    Text(stringResource(R.string.clear_all))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun ThemeOptionRow(
    option: ThemeOption,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = option.icon,
            contentDescription = null,
            tint = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        Text(
            text = stringResource(option.labelRes),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        RadioButton(
            selected = selected,
            onClick = null,
        )
    }
}

@Composable
private fun AboutRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(88.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.End,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}
