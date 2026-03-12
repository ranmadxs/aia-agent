package cl.tomi.aiaagent.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import cl.tomi.aiaagent.api.EspWifiApi
import cl.tomi.aiaagent.cache.DeviceCache
import cl.tomi.aiaagent.data.NetworkDeviceItem
import cl.tomi.aiaagent.debug.AppLog
import cl.tomi.aiaagent.viewmodel.DeviceScanViewModel

@Composable
private fun WifiNetworkCard(
    ssid: String,
    rssi: Int,
    onClick: () -> Unit,
    isConnectedOnEsp: Boolean = false
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (isConnectedOnEsp) Modifier.border(1.dp, Color(0xFF22C55E), RoundedCornerShape(8.dp)) else Modifier)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isConnectedOnEsp) Color(0xFF1E3A2F) else Color(0xFF1E293B)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(ssid, color = Color.White, fontWeight = FontWeight.Medium)
                    if (isConnectedOnEsp) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Device conectado",
                            color = Color(0xFF22C55E),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Text(
                    signalBars(rssi),
                    color = Color(0xFF94A3B8),
                    style = MaterialTheme.typography.labelSmall
                )
            }
            IconButton(onClick = onClick) {
                Icon(Icons.Filled.Edit, contentDescription = "Configurar", tint = Color(0xFF3B82F6))
            }
        }
    }
}

@Composable
private fun signalBars(level: Int): String {
    val bars = when {
        level >= -50 -> "████"
        level >= -60 -> "███"
        level >= -70 -> "██"
        level >= -80 -> "█"
        else -> "○"
    }
    return "$bars ${level} dBm"
}

@Composable
private fun DeviceCard(
    device: NetworkDeviceItem,
    icon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Filled.Router,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = Color(0xFF3B82F6)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    device.displayName,
                    color = Color.White,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    "${device.ip} • ${device.id}",
                    color = Color(0xFF94A3B8),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Text("→", color = Color(0xFF3B82F6))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceScanScreen(
    onDeviceSelected: (NetworkDeviceItem) -> Unit
) {
    val context = LocalContext.current
    val deviceCache = remember { DeviceCache(context.applicationContext) }
    val viewModel: DeviceScanViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return DeviceScanViewModel(context.applicationContext, deviceCache) as T
            }
        }
    )
    val uiState by viewModel.uiState.collectAsState()
    var showLogs by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showWifiList by remember { mutableStateOf(false) }
    var showNetworkError by remember { mutableStateOf(false) }
    var wifiConfigNetwork by remember { mutableStateOf<android.net.wifi.ScanResult?>(null) }
    var wifiConfigEspNetwork by remember { mutableStateOf<EspWifiApi.WifiNetwork?>(null) }
    val logEntries by AppLog.entries.collectAsState()

    fun onDeviceClick(device: NetworkDeviceItem) {
        if (viewModel.isOnSameNetworkAs(device)) {
            viewModel.onDeviceSelected(device)
            onDeviceSelected(device)
        } else {
            showNetworkError = true
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.refreshWifiSsid()
    }

    LaunchedEffect(Unit) {
        viewModel.startScan()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            when {
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ->
                    viewModel.refreshWifiSsid()
                else -> permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        } else {
            viewModel.refreshWifiSsid()
        }
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.stopScan() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "📡 Devices",
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    TextButton(onClick = { showLogs = true }) {
                        Text("Ver logs", color = Color(0xFF94A3B8), style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton(
                        onClick = {
                            if (uiState.isOnDeviceNetwork) {
                                showWifiList = true
                                viewModel.startWifiScan()
                            }
                        },
                        enabled = uiState.isOnDeviceNetwork
                    ) {
                        Icon(
                            Icons.Filled.Wifi,
                            contentDescription = "Redes WiFi",
                            tint = if (uiState.isOnDeviceNetwork) Color(0xFF3B82F6) else Color(0xFF64748B),
                            modifier = if (uiState.isOnDeviceNetwork) Modifier.shadow(
                                elevation = 8.dp,
                                shape = CircleShape,
                                ambientColor = Color(0xFF3B82F6).copy(alpha = 0.5f),
                                spotColor = Color(0xFF3B82F6).copy(alpha = 0.4f)
                            ) else Modifier
                        )
                    }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "Más opciones", tint = Color.White)
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            if (uiState.isOnDeviceNetwork) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            "Configurar WiFi",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    },
                                    onClick = {
                                        showWifiList = true
                                        viewModel.startWifiScan()
                                        showMenu = false
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Filled.Settings,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "Limpiar dispositivos guardados",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                },
                                onClick = {
                                    viewModel.clearCache()
                                    showMenu = false
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Filled.DeleteSweep,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            )
                        }
                    }
                    Text(
                        "v${context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"}",
                        color = Color(0xFF94A3B8),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(end = 16.dp)
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1E293B),
                    titleContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            // Sonar 360° con sensores en caché como puntos; brillan cuando el sweep pasa por encima
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                SonarAnimation(
                    size = 200.dp,
                    sweepColor = Color(0xFF3B82F6),
                    backgroundColor = Color(0xFF0F172A),
                    centerColor = Color(0xFF1E293B),
                    cachedDevices = uiState.cachedDevices
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (uiState.scanning) "Buscando dispositivos..." else "Listo",
                    color = Color.White,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = if (uiState.isOnDeviceNetwork) "Conectado a la red del dispositivo" else "Conecta a la red WiFi del dispositivo",
                    color = if (uiState.isOnDeviceNetwork) Color(0xFF22C55E) else Color(0xFF94A3B8),
                    style = MaterialTheme.typography.bodySmall
                )
                uiState.currentWifiSsid?.let { ssid ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Filled.Wifi,
                            contentDescription = null,
                            tint = Color(0xFF22C55E),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "WiFi: $ssid",
                            color = Color(0xFF22C55E),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "AIA Agent v${context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"}",
                    color = Color(0xFF64748B),
                    style = MaterialTheme.typography.labelMedium
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = { viewModel.startScan() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.scanning,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(if (uiState.scanning) "Escaneando..." else "Escanear de nuevo")
            }

            uiState.error?.let { error ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(error, color = Color(0xFFEF4444))
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Listas: guardados (caché) y encontrados (con spinner)
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                if (uiState.cachedDevices.isNotEmpty()) {
                    item {
                        Text(
                            "Dispositivos guardados (${uiState.cachedDevices.size})",
                            color = Color(0xFF94A3B8),
                            fontWeight = FontWeight.Medium
                        )
                    }
                    items(uiState.cachedDevices) { device ->
                        DeviceCard(
                            device = device,
                            icon = Icons.Filled.Router,
                            onClick = { onDeviceClick(device) }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(8.dp)) }
                }

                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (uiState.scanning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color(0xFF3B82F6),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            "Dispositivos encontrados (${uiState.discoveredDevices.size})",
                            color = Color(0xFF94A3B8),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                items(uiState.discoveredDevices) { device ->
                    DeviceCard(
                        device = device,
                        icon = Icons.Filled.Sensors,
                        onClick = { onDeviceClick(device) }
                    )
                }

                if (uiState.cachedDevices.isEmpty() && uiState.discoveredDevices.isEmpty() && !uiState.scanning) {
                    item {
                        Spacer(modifier = Modifier.height(32.dp))
                        Text(
                            "Conecta el móvil a la misma red WiFi del dispositivo\ny pulsa Escanear",
                            color = Color(0xFF64748B),
                            modifier = Modifier.padding(32.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }

    if (showNetworkError) {
        AlertDialog(
            onDismissRequest = { showNetworkError = false },
            title = { Text("Red incorrecta", color = Color.White) },
            text = {
                Text(
                    "Conéctate a la red WiFi del dispositivo para acceder al monitor.",
                    color = Color.White
                )
            },
            confirmButton = {
                TextButton(onClick = { showNetworkError = false }) {
                    Text("Entendido", color = Color(0xFF3B82F6))
                }
            },
            containerColor = Color(0xFF1E293B),
            titleContentColor = Color.White,
            textContentColor = Color.White
        )
    }

    if (showWifiList) {
        AlertDialog(
            onDismissRequest = { showWifiList = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Wifi, contentDescription = null, tint = Color(0xFF3B82F6))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Redes WiFi", color = Color.White)
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (uiState.wifiSourceEsp) {
                        Text(
                            "Red del ESP (192.168.50.x)",
                            color = Color(0xFF22C55E),
                            style = MaterialTheme.typography.labelSmall
                        )
                        uiState.espConnectedSsid?.let { ssid ->
                            Text("Conectado a: $ssid", color = Color(0xFF94A3B8), style = MaterialTheme.typography.bodySmall)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (uiState.wifiScanning) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color(0xFF3B82F6), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        TextButton(onClick = { viewModel.startWifiScan() }) {
                            Text(if (uiState.wifiScanning) "Escaneando..." else "Escanear", color = Color(0xFF3B82F6))
                        }
                    }
                    uiState.wifiScanError?.let { err ->
                        Text(err, color = Color(0xFFEF4444), style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    uiState.wifiConnectResult?.let { msg ->
                        Text(msg, color = if (msg.startsWith("Error") || msg.startsWith("No")) Color(0xFFEF4444) else Color(0xFF22C55E), style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (uiState.wifiSourceEsp) {
                            val connectedSsid = uiState.espConnectedSsid?.trim()?.takeIf { it.isNotBlank() }
                            items(uiState.espWifiNetworks) { net ->
                                val isDeviceConnected = connectedSsid != null && net.ssid.trim().equals(connectedSsid, ignoreCase = true)
                                WifiNetworkCard(
                                    ssid = net.ssid,
                                    rssi = net.rssi,
                                    onClick = { wifiConfigEspNetwork = net },
                                    isConnectedOnEsp = isDeviceConnected
                                )
                            }
                        } else {
                            items(uiState.wifiNetworks) { scanResult ->
                                val ssid = scanResult.SSID ?: ""
                                WifiNetworkCard(
                                    ssid = ssid,
                                    rssi = scanResult.level,
                                    onClick = { wifiConfigNetwork = scanResult }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showWifiList = false }) {
                    Text("Cerrar", color = Color(0xFF3B82F6))
                }
            },
            containerColor = Color(0xFF1E293B),
            titleContentColor = Color.White,
            textContentColor = Color.White
        )
    }

    wifiConfigNetwork?.let { scanResult ->
        var password by remember(scanResult.SSID) { mutableStateOf("") }
        val targetDevice = uiState.cachedDevices.firstOrNull() ?: uiState.discoveredDevices.firstOrNull()
        val canConnect = viewModel.isOnEspApNetwork() || targetDevice != null
        AlertDialog(
            onDismissRequest = { wifiConfigNetwork = null },
            title = { Text("Conectar a ${scanResult.SSID}", color = Color.White) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (!canConnect) {
                        Text(
                            "Conéctate a la red del dispositivo (192.168.50.x) o selecciona uno",
                            color = Color(0xFFEF4444),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Contraseña", color = Color(0xFF94A3B8)) },
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = PasswordVisualTransformation(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF3B82F6),
                            unfocusedBorderColor = Color(0xFF64748B),
                            focusedLabelColor = Color(0xFF3B82F6),
                            unfocusedLabelColor = Color(0xFF94A3B8)
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.sendWifiConfig(targetDevice, scanResult.SSID ?: "", password)
                        wifiConfigNetwork = null
                    },
                    enabled = canConnect
                ) {
                    Text("Conectar", color = Color(0xFF3B82F6))
                }
            },
            dismissButton = {
                TextButton(onClick = { wifiConfigNetwork = null }) {
                    Text("Cancelar", color = Color(0xFF94A3B8))
                }
            },
            containerColor = Color(0xFF1E293B),
            titleContentColor = Color.White,
            textContentColor = Color.White
        )
    }

    wifiConfigEspNetwork?.let { net ->
        var password by remember(net.ssid) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { wifiConfigEspNetwork = null },
            title = { Text("Conectar a ${net.ssid}", color = Color.White) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("API HTTP del ESP (192.168.50.1)", color = Color(0xFF22C55E), style = MaterialTheme.typography.labelSmall)
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Contraseña", color = Color(0xFF94A3B8)) },
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = PasswordVisualTransformation(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF3B82F6),
                            unfocusedBorderColor = Color(0xFF64748B),
                            focusedLabelColor = Color(0xFF3B82F6),
                            unfocusedLabelColor = Color(0xFF94A3B8)
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.sendWifiConfig(null, net.ssid, password)
                        wifiConfigEspNetwork = null
                    }
                ) {
                    Text("Conectar", color = Color(0xFF3B82F6))
                }
            },
            dismissButton = {
                TextButton(onClick = { wifiConfigEspNetwork = null }) {
                    Text("Cancelar", color = Color(0xFF94A3B8))
                }
            },
            containerColor = Color(0xFF1E293B),
            titleContentColor = Color.White,
            textContentColor = Color.White
        )
    }

    if (showLogs) {
        AlertDialog(
            onDismissRequest = { showLogs = false },
            title = { Text("Logs de depuración", color = Color.White) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = { AppLog.clear() }) {
                        Text("Limpiar", color = Color(0xFF94A3B8))
                    }
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 350.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(logEntries.reversed()) { line ->
                            Text(
                                line,
                                color = Color(0xFFE2E8F0),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF0F172A), RoundedCornerShape(4.dp))
                                    .padding(8.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLogs = false }) {
                    Text("Cerrar", color = Color(0xFF3B82F6))
                }
            },
            containerColor = Color(0xFF1E293B),
            titleContentColor = Color.White,
            textContentColor = Color.White
        )
    }
}
