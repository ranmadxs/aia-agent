package cl.tomi.aiaagent.viewmodel

import android.content.Context
import android.net.wifi.WifiManager
import cl.tomi.aiaagent.debug.AppLog
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cl.tomi.aiaagent.data.EstanqueEstado
import cl.tomi.aiaagent.data.NetworkDeviceItem
import cl.tomi.aiaagent.udp.UdpSocketManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class EstanqueUiState(
    val estado: EstanqueEstado? = null,
    val error: String? = null,
    val udpConnected: Boolean = false,
    val offNetwork: Boolean = false
)

class EstanqueViewModel(
    private val device: NetworkDeviceItem,
    private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(EstanqueUiState())
    val uiState: StateFlow<EstanqueUiState> = _uiState.asStateFlow()

    private var collectJob: Job? = null
    private var discoveryJob: Job? = null
    private var networkCheckJob: Job? = null

    init {
        checkNetwork()
        startListening()
    }

    private fun checkNetwork() {
        val onSame = isOnSameNetworkAs(device)
        _uiState.update { it.copy(offNetwork = !onSame) }
    }

    private fun isOnSameNetworkAs(device: NetworkDeviceItem): Boolean {
        val myIp = getCurrentWifiIp() ?: return false
        val deviceIp = device.ip
        if (deviceIp.isBlank()) return false
        val myParts = myIp.split(".")
        val deviceParts = deviceIp.split(".")
        if (myParts.size != 4 || deviceParts.size != 4) return false
        return myParts[0] == deviceParts[0] && myParts[1] == deviceParts[1] && myParts[2] == deviceParts[2]
    }

    private fun getCurrentWifiIp(): String? {
        return try {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return null
            val ip = wm.connectionInfo.ipAddress
            if (ip == 0) return null
            "${ip and 0xFF}.${(ip shr 8) and 0xFF}.${(ip shr 16) and 0xFF}.${(ip shr 24) and 0xFF}"
        } catch (_: Exception) {
            null
        }
    }

    fun refreshNetworkCheck() {
        checkNetwork()
    }

    private fun startListening() {
        collectJob?.cancel()
        discoveryJob?.cancel()
        AppLog.d("EstanqueVM", "startListening device.ip=${device.ip} deviceId=${device.effectiveDeviceId}")
        try {
            UdpSocketManager.ensureStarted()
        } catch (_: Exception) {
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            // Ráfaga inicial agresiva: el ESP solo envía lecturas cuando tiene subscriber.
            // Unicast primero (más fiable en modo AP), luego broadcast.
            repeat(15) {
                UdpSocketManager.sendDiscoveryTo(device.ip)
                UdpSocketManager.sendDiscovery()
                if (it < 14) delay(50)
            }
        }
        collectJob = viewModelScope.launch {
            UdpSocketManager.sensorReadings.collect { reading ->
                val matches = reading.senderIp == device.ip ||
                    (reading.deviceId != null && reading.deviceId.equals(device.effectiveDeviceId, ignoreCase = true)) ||
                    (reading.deviceId != null && device.effectiveDeviceId.contains(reading.deviceId, ignoreCase = true)) ||
                    (reading.deviceId != null && reading.deviceId.contains(device.effectiveDeviceId, ignoreCase = true))
                AppLog.d("EstanqueVM", "reading: senderIp=${reading.senderIp} deviceId=${reading.deviceId} | device.ip=${device.ip} effectiveId=${device.effectiveDeviceId} | matches=$matches")
                _uiState.update {
                    it.copy(estado = reading.estado, udpConnected = true)
                }
            }
        }
        discoveryJob = viewModelScope.launch(Dispatchers.IO) {
            var count = 0
            while (isActive) {
                delay(if (count < 40) 250 else 1500)
                count++
                if (count < 80) {
                    UdpSocketManager.sendDiscoveryTo(device.ip)
                } else {
                    UdpSocketManager.sendDiscovery()
                    UdpSocketManager.sendDiscoveryTo(device.ip)
                    count = 0
                }
            }
        }
        networkCheckJob = viewModelScope.launch {
            while (isActive) {
                delay(5000)
                checkNetwork()
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        collectJob?.cancel()
        discoveryJob?.cancel()
        networkCheckJob?.cancel()
    }
}
