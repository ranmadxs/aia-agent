package cl.tomi.aiaagent.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerOff
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.LocalContext
import cl.tomi.aiaagent.data.NetworkDeviceItem
import cl.tomi.aiaagent.debug.AppLog
import cl.tomi.aiaagent.viewmodel.EstanqueViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EstanqueScreen(
    selectedDevice: NetworkDeviceItem,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: EstanqueViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return EstanqueViewModel(selectedDevice, context.applicationContext) as T
            }
        }
    )
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()
    var showLogs by remember { mutableStateOf(false) }
    val logEntries by AppLog.entries.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("💧 Lectura de datos", fontWeight = FontWeight.Bold)
                        selectedDevice.let { dev ->
                            Text(
                                dev.displayName,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }
                },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("←", color = Color.White, fontSize = MaterialTheme.typography.titleLarge.fontSize)
                    }
                },
                actions = {
                    TextButton(onClick = { showLogs = true }) {
                        Text("Ver logs", color = Color(0xFF94A3B8), style = MaterialTheme.typography.bodySmall)
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
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(scrollState)
                    .padding(16.dp)
            ) {
            val isNok = uiState.estado?.estado.equals("NOK", ignoreCase = true)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(
                                when {
                                    isNok -> Color(0xFFF97316)
                                    uiState.udpConnected -> Color(0xFF4ADE80)
                                    !uiState.offNetwork -> Color(0xFF3B82F6)
                                    else -> Color(0xFFEF4444)
                                },
                                RoundedCornerShape(6.dp)
                            )
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = when {
                            isNok -> "Sensor con fallo"
                            uiState.udpConnected -> "Recibiendo datos del NodeMCU"
                            !uiState.offNetwork -> "Cargando datos..."
                            else -> "Esperando datos del NodeMCU"
                        },
                        color = when {
                            isNok -> Color(0xFFF97316)
                            else -> Color.White
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            val porcentaje = if (isNok) 0f else (uiState.estado?.porcentaje ?: 0.0).toFloat().coerceIn(0f, 100f)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        Text("Nivel del estanque", color = Color.White, fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .background(Color(0xFF0F172A), RoundedCornerShape(8.dp))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(porcentaje / 100f)
                                    .background(Color(0xFF3B82F6), RoundedCornerShape(8.dp))
                                    .align(Alignment.BottomCenter)
                            )
                            when {
                                isNok -> Icon(
                                    imageVector = Icons.Filled.PowerOff,
                                    contentDescription = "Desconectado",
                                    modifier = Modifier
                                        .size(64.dp)
                                        .alpha(0.4f)
                                        .align(Alignment.Center),
                                    tint = Color(0xFF94A3B8)
                                )
                                uiState.estado == null && !uiState.offNetwork -> CircularProgressIndicator(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .align(Alignment.Center),
                                    color = Color(0xFF3B82F6),
                                    strokeWidth = 3.dp
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (isNok) "0 L • 0%" else "${uiState.estado?.litros?.toInt() ?: "--"} L • ${uiState.estado?.porcentaje?.let { "%.1f".format(it) } ?: "--"}%",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            val estado = uiState.estado
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Lectura de datos (NodeMCU)", color = Color.White, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(12.dp))
                    MetricRow("Estado", estado?.estado ?: "--")
                    MetricRow("Distancia", "${estado?.distancia?.let { "%.1f".format(it) } ?: "--"} cm")
                    MetricRow("Altura agua", "${estado?.alturaAgua?.let { "%.1f".format(it) } ?: "--"} cm")
                    MetricRow("Última lectura", estado?.ultimaLectura?.take(19) ?: "--")
                }
            }

            uiState.error?.let { error ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(error, color = Color(0xFFEF4444))
            }
            }

            if (uiState.offNetwork) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xCC0F172A)),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "Conéctate a la red del dispositivo",
                                color = Color.White,
                                fontWeight = FontWeight.Medium,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "El móvil debe estar en la misma red WiFi que el sensor para recibir datos.",
                                color = Color(0xFF94A3B8),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(
                                onClick = onBack,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6))
                            ) {
                                Text("Volver")
                            }
                        }
                    }
                }
            }
        }
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

@Composable
fun MetricRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color(0xFF94A3B8))
        Text(value, color = Color.White, fontWeight = FontWeight.Medium)
    }
}
