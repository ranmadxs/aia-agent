package cl.tomi.aiaagent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import cl.tomi.aiaagent.data.NetworkDeviceItem
import cl.tomi.aiaagent.ui.DeviceScanScreen
import cl.tomi.aiaagent.ui.EstanqueScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF3B82F6),
                    background = Color(0xFF0F172A),
                    surface = Color(0xFF1E293B)
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AiaAgentApp()
                }
            }
        }
    }
}

@Composable
fun AiaAgentApp() {
    var selectedDevice by remember { mutableStateOf<NetworkDeviceItem?>(null) }

    if (selectedDevice == null) {
        DeviceScanScreen(
            onDeviceSelected = { device ->
                selectedDevice = device
            }
        )
    } else {
        EstanqueScreen(
            selectedDevice = selectedDevice!!,
            onBack = { selectedDevice = null }
        )
    }
}
