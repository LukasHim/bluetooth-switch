package com.lukas.bluetoothswitch.ui

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.lukas.bluetoothswitch.MainUiState
import com.lukas.bluetoothswitch.MainViewModel
import com.lukas.bluetoothswitch.R
import com.lukas.bluetoothswitch.data.BleDevice
import com.lukas.bluetoothswitch.data.BleEvent
import com.lukas.bluetoothswitch.data.BlePermissions
import com.lukas.bluetoothswitch.ui.screens.ControlScreen
import com.lukas.bluetoothswitch.ui.screens.DevicesScreen
import com.lukas.bluetoothswitch.ui.screens.SettingsScreen
import kotlinx.coroutines.launch

private enum class AppTab(
    val labelRes: Int,
    val icon: ImageVector,
) {
    DEVICES(R.string.nav_devices, Icons.Rounded.Devices),
    CONTROL(R.string.nav_control, Icons.Rounded.Tune),
    SETTINGS(R.string.nav_settings, Icons.Rounded.Settings),
}

@Composable
fun AppRoot(
    viewModel: MainViewModel,
    uiState: MainUiState,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var currentTab by rememberSaveable { mutableStateOf(AppTab.DEVICES) }
    var permissionRequested by rememberSaveable { mutableStateOf(false) }
    var permissionsGranted by remember { mutableStateOf(BlePermissions.hasAll(context)) }
    var locationEnabled by remember { mutableStateOf(BlePermissions.isLocationServiceEnabled(context)) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        permissionsGranted = BlePermissions.hasAll(context)
        locationEnabled = BlePermissions.isLocationServiceEnabled(context)
        viewModel.refreshBluetoothState()
        if (permissionsGranted && currentTab == AppTab.DEVICES) {
            viewModel.startScan()
        }
    }

    val enableBluetoothLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) {
        viewModel.refreshBluetoothState()
    }

    LaunchedEffect(Unit) {
        if (!permissionsGranted && !permissionRequested) {
            permissionRequested = true
            permissionLauncher.launch(BlePermissions.required())
        }
    }

    DisposableEffect(lifecycleOwner, currentTab) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    permissionsGranted = BlePermissions.hasAll(context)
                    locationEnabled = BlePermissions.isLocationServiceEnabled(context)
                    viewModel.refreshBluetoothState()
                    if (permissionsGranted && currentTab == AppTab.DEVICES) {
                        viewModel.startScan()
                    }
                }

                Lifecycle.Event.ON_STOP -> viewModel.stopScan()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(currentTab, permissionsGranted, uiState.bluetoothEnabled) {
        if (currentTab == AppTab.DEVICES && permissionsGranted && uiState.bluetoothEnabled) {
            viewModel.startScan()
        } else {
            viewModel.stopScan()
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is BleEvent.Connected -> {
                    viewModel.selectDevice(event.address)
                    currentTab = AppTab.CONTROL
                    snackbarHostState.showSnackbar(
                        resources.getString(R.string.message_device_connected, event.name),
                    )
                }

                is BleEvent.Disconnected -> {
                    snackbarHostState.showSnackbar(
                        resources.getString(R.string.message_device_disconnected, event.name),
                    )
                }

                is BleEvent.Message -> snackbarHostState.showSnackbar(event.text)
            }
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                tab = currentTab,
                connectedDevices = uiState.connectedDevices,
                selectedAddress = uiState.selectedAddress,
                onSelectDevice = viewModel::selectDevice,
            )
        },
        bottomBar = {
            NavigationBar {
                AppTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = currentTab == tab,
                        onClick = { currentTab = tab },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(stringResource(tab.labelRes)) },
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { contentPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
        ) {
            when (currentTab) {
                AppTab.DEVICES -> DevicesScreen(
                    uiState = uiState,
                    permissionsGranted = permissionsGranted,
                    permissionRequested = permissionRequested,
                    locationEnabled = locationEnabled,
                    onRequestPermissions = {
                        permissionLauncher.launch(BlePermissions.required())
                    },
                    onOpenAppSettings = {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", context.packageName, null),
                            ),
                        )
                    },
                    onEnableBluetooth = {
                        if (permissionsGranted) {
                            enableBluetoothLauncher.launch(
                                Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE),
                            )
                        } else {
                            permissionLauncher.launch(BlePermissions.required())
                        }
                    },
                    onOpenLocationSettings = {
                        context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                    },
                    onConnect = viewModel::connect,
                    onDisconnect = viewModel::disconnect,
                    onToggleAutoConnect = viewModel::setAutoConnect,
                    onRestartScan = viewModel::restartScan,
                )

                AppTab.CONTROL -> ControlScreen(
                    uiState = uiState,
                    onNavigateToDevices = { currentTab = AppTab.DEVICES },
                    onReadStatus = viewModel::readStatus,
                    onReadPassword = viewModel::readPassword,
                    onSetSwitch = viewModel::setSwitch,
                    onSetDelay = viewModel::setDelay,
                    onClearDelay = viewModel::clearDelay,
                    onSetPassword = { address, password ->
                        viewModel.setPassword(address, password)
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                resources.getString(R.string.password_command_sent),
                            )
                        }
                    },
                )

                AppTab.SETTINGS -> SettingsScreen(
                    settings = uiState.settings,
                    onThemeModeChange = viewModel::setThemeMode,
                    onDynamicColorChange = viewModel::setDynamicColor,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppTopBar(
    tab: AppTab,
    connectedDevices: List<BleDevice>,
    selectedAddress: String?,
    onSelectDevice: (String) -> Unit,
) {
    TopAppBar(
        title = {
            if (tab == AppTab.CONTROL && connectedDevices.isNotEmpty()) {
                ConnectedDeviceSelector(
                    devices = connectedDevices,
                    selectedAddress = selectedAddress,
                    onSelectDevice = onSelectDevice,
                )
            } else {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                )
            }
        },
    )
}

@Composable
private fun ConnectedDeviceSelector(
    devices: List<BleDevice>,
    selectedAddress: String?,
    onSelectDevice: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = devices.firstOrNull { it.address == selectedAddress }

    Box {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable(enabled = devices.isNotEmpty()) { expanded = true }
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f, fill = false)) {
                Text(
                    text = selected?.displayName ?: stringResource(R.string.control_title),
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (selected != null) {
                    Text(
                        text = selected.address,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
            Icon(
                imageVector = Icons.Rounded.ArrowDropDown,
                contentDescription = stringResource(R.string.select_device),
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            devices.forEach { device ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(
                                text = device.displayName,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                text = device.address,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    onClick = {
                        expanded = false
                        onSelectDevice(device.address)
                    },
                    leadingIcon = {
                        if (device.address == selectedAddress) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                            )
                        }
                    },
                )
            }
        }
    }
}
