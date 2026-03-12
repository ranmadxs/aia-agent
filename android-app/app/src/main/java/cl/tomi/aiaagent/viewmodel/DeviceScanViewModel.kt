package cl.tomi.aiaagent.viewmodel

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cl.tomi.aiaagent.api.EspWifiApi
import cl.tomi.aiaagent.cache.DeviceCache
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
import org.json.JSONObject

data class DeviceScanUiState(
    val cachedDevices: List<NetworkDeviceItem> = emptyList(),
    val discoveredDevices: List<NetworkDeviceItem> = emptyList(),
    val scanning: Boolean = false,
    val error: String? = null,
    val currentWifiSsid: String? = null,
    val isOnDeviceNetwork: Boolean = false,
    val wifiNetworks: List<ScanResult> = emptyList(),
    val espWifiNetworks: List<EspWifiApi.WifiNetwork> = emptyList(),
    val espConnectedSsid: String? = null,
    val wifiSourceEsp: Boolean = false,
    val wifiScanning: Boolean = false,
    val wifiScanError: String? = null,
    val networkError: String? = null,
    val wifiConnectResult: String? = null
)

class DeviceScanViewModel(
    private val context: Context,
    private val deviceCache: DeviceCache
) : ViewModel() {

    private val _uiState = MutableStateFlow(DeviceScanUiState())
    val uiState: StateFlow<DeviceScanUiState> = _uiState.asStateFlow()

    private var scanJob: Job? = null
    private var collectJob: Job? = null
    private var cleanupJob: Job? = null
    private var networkRefreshJob: Job? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    /** Tiempo sin recibir discovery para considerar dispositivo apagado (ms) */
    private val discoveredTimeoutMs = 15_000L

    fun startScan() {
        scanJob?.cancel()
        collectJob?.cancel()
        cleanupJob?.cancel()
        networkRefreshJob?.cancel()
        val cached = deviceCache.loadDevices()
        val now = System.currentTimeMillis()
        _uiState.update { state ->
            state.discoveredDevices.forEach { lastSeenByBusinessKey[it.businessKey] = now }
            state.copy(scanning = true, cachedDevices = cached, error = null)
            // No limpiar discoveredDevices: mantener visibles hasta que se apaguen (timeout 15s)
        }
        try {
            UdpSocketManager.ensureStarted()
        } catch (e: Exception) {
            _uiState.update {
                it.copy(scanning = false, error = e.message ?: "Error de red")
            }
            return
        }
        // Registrar como subscriber de inmediato si estamos en la misma red (para que al entrar al monitor los datos lleguen rápido)
        viewModelScope.launch(Dispatchers.IO) {
            cached.filter { isOnSameNetworkAs(it) }.forEach { device ->
                repeat(5) {
                    UdpSocketManager.sendDiscoveryTo(device.ip)
                    if (it < 4) delay(50)
                }
            }
        }
        collectJob = viewModelScope.launch {
            UdpSocketManager.discoveredDevices.collect { device ->
                deviceCache.saveDevice(device)
                lastSeenByBusinessKey[device.businessKey] = System.currentTimeMillis()
                _uiState.update { state ->
                    val newDiscovered = state.discoveredDevices.filter { it.businessKey != device.businessKey } + device
                    val newCached = deviceCache.loadDevices()
                    state.copy(discoveredDevices = newDiscovered, cachedDevices = newCached)
                }
            }
        }
        scanJob = viewModelScope.launch(Dispatchers.IO) {
            repeat(10) {
                if (!isActive) return@launch
                UdpSocketManager.sendDiscovery()
                delay(300)
            }
            _uiState.update { it.copy(scanning = false) }
        }
        cleanupJob = viewModelScope.launch {
            while (isActive) {
                delay(5000)
                val now = System.currentTimeMillis()
                _uiState.update { state ->
                    val stillAlive = state.discoveredDevices.filter { d ->
                        val recentlySeen = (now - (lastSeenByBusinessKey[d.businessKey] ?: 0)) < discoveredTimeoutMs
                        val onSameNetwork = isOnSameNetworkAs(d)
                        recentlySeen || onSameNetwork
                    }
                    if (stillAlive.size != state.discoveredDevices.size) {
                        state.copy(discoveredDevices = stillAlive)
                    } else state
                }
            }
        }
        refreshNetworkStatus()
        ensureNetworkOwnerInDiscovered()
        networkRefreshJob = viewModelScope.launch {
            while (isActive) {
                delay(2000)
                refreshNetworkStatus()
                ensureNetworkOwnerInDiscovered()
            }
        }
        registerNetworkCallback()
    }

    private fun registerNetworkCallback() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
        val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        networkCallback?.let { cm.unregisterNetworkCallback(it) }
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                refreshNetworkStatus()
            }
            override fun onLost(network: Network) {
                refreshNetworkStatus()
            }
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                refreshNetworkStatus()
            }
        }
        cm.registerDefaultNetworkCallback(networkCallback!!)
    }

    private val lastSeenByBusinessKey = mutableMapOf<String, Long>()

    /** Asegura que el dueño de la red (dispositivos en caché de la misma subred) aparezca siempre en encontrados. */
    private fun ensureNetworkOwnerInDiscovered() {
        _uiState.update { state ->
            val onNetwork = state.cachedDevices.filter { isOnSameNetworkAs(it) }
            val missing = onNetwork.filter { cached ->
                state.discoveredDevices.none { it.businessKey == cached.businessKey }
            }
            if (missing.isEmpty()) state
            else {
                missing.forEach { lastSeenByBusinessKey[it.businessKey] = System.currentTimeMillis() }
                state.copy(discoveredDevices = state.discoveredDevices + missing)
            }
        }
    }

    fun stopScan() {
        scanJob?.cancel()
        collectJob?.cancel()
        cleanupJob?.cancel()
        networkRefreshJob?.cancel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            networkCallback?.let { cm?.unregisterNetworkCallback(it) }
            networkCallback = null
        }
        _uiState.update { it.copy(scanning = false) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    /** Verifica si el móvil está en la misma red que el dispositivo (mismo subnet). */
    fun isOnSameNetworkAs(device: NetworkDeviceItem): Boolean {
        val myIp = getActiveNetworkIp() ?: getCurrentWifiIp() ?: return false
        return device.ip.isNotBlank() && sameSubnet(myIp, device.ip)
    }

    fun clearNetworkError() {
        _uiState.update { it.copy(networkError = null) }
    }

    /** Guarda el dispositivo en caché al seleccionarlo (queda primero en la lista al volver). */
    fun onDeviceSelected(device: NetworkDeviceItem) {
        deviceCache.saveDevice(device)
    }

    /** Limpia la caché de dispositivos y vacía la lista en pantalla. */
    fun clearCache() {
        deviceCache.clear()
        _uiState.update { it.copy(cachedDevices = emptyList()) }
    }

    /** Envía credenciales WiFi al ESP. Si está en 192.168.50.x usa HTTP API, sino UDP. */
    fun sendWifiConfig(device: NetworkDeviceItem?, ssid: String, password: String) {
        _uiState.update { it.copy(wifiConnectResult = null) }
        if (isOnEspApNetwork()) {
            sendWifiConfigViaHttp(ssid, password)
        } else if (device != null) {
            sendWifiConfigViaUdp(device, ssid, password)
        } else {
            _uiState.update { it.copy(wifiConnectResult = "Conéctate a la red del dispositivo o selecciona uno") }
        }
    }

    private fun sendWifiConfigViaHttp(ssid: String, password: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = EspWifiApi.connect(ssid, password, context)
            _uiState.update {
                it.copy(
                    wifiConnectResult = if (result.ok) result.message ?: "Conectado" else result.error ?: "Error"
                )
            }
        }
    }

    private fun sendWifiConfigViaUdp(device: NetworkDeviceItem, ssid: String, password: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                UdpSocketManager.ensureStarted()
                val payload = JSONObject().apply {
                    put("cmd", "WIFI_CONFIG")
                    put("ssid", ssid)
                    put("password", password)
                }
                val msg = JSONObject().apply {
                    put("topic", device.mqttTopicIn ?: "yai-mqtt/in")
                    put("payload", payload)
                }
                UdpSocketManager.sendTo(device.ip, msg.toString().toByteArray(Charsets.UTF_8))
                _uiState.update { it.copy(wifiConnectResult = "Enviado por UDP") }
            } catch (e: Exception) {
                _uiState.update { it.copy(wifiConnectResult = "Error: ${e.message}") }
            }
        }
    }

    /** Actualiza el SSID de la red WiFi actual. */
    fun refreshWifiSsid() {
        refreshNetworkStatus()
    }

    /** Actualiza SSID y si estamos en la red del dispositivo (NodeMCU AP o misma subred que un dispositivo guardado). */
    private fun refreshNetworkStatus() {
        val ssid = getCurrentWifiSsid()
        _uiState.update { state ->
            val onDeviceNetwork = isOnDeviceNetwork(state.cachedDevices)
            state.copy(currentWifiSsid = ssid, isOnDeviceNetwork = onDeviceNetwork)
        }
    }

    /**
     * True si estamos en la red del dispositivo.
     * - 192.168.50.x: usa WiFi (getCurrentWifiIp) porque el ESP no tiene internet y la red activa puede ser cellular.
     * - Misma subred que dispositivo: usa red activa para detectar cuando se cambia a datos móviles.
     */
    private fun isOnDeviceNetwork(cachedDevices: List<NetworkDeviceItem>): Boolean {
        val wifiIp = getCurrentWifiIp()
        if (wifiIp?.startsWith("192.168.50.") == true) return true
        val activeIp = getActiveNetworkIp()
        if (activeIp == null) return false
        return cachedDevices.any { device ->
            device.ip.isNotBlank() && sameSubnet(activeIp, device.ip)
        }
    }

    private fun sameSubnet(ip1: String, ip2: String): Boolean {
        val p1 = ip1.split(".")
        val p2 = ip2.split(".")
        if (p1.size != 4 || p2.size != 4) return false
        return p1[0] == p2[0] && p1[1] == p2[1] && p1[2] == p2[2]
    }

    /** IP de la red activa (default route). Null si es cellular. */
    private fun getActiveNetworkIp(): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return getCurrentWifiIp()
        val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return getCurrentWifiIp()
        val network = cm.activeNetwork ?: return getCurrentWifiIp()
        val caps = cm.getNetworkCapabilities(network) ?: return getCurrentWifiIp()
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) return null
        val links = cm.getLinkProperties(network)?.linkAddresses ?: return getCurrentWifiIp()
        val ipv4 = links.firstOrNull { it.address?.hostAddress?.contains('.') == true }
        return ipv4?.address?.hostAddress ?: getCurrentWifiIp()
    }

    /** True si el móvil está en 192.168.50.x (red del NodeMCU en modo AP). */
    fun isOnEspApNetwork(): Boolean {
        return getCurrentWifiIp()?.startsWith("192.168.50.") == true
    }

    /** Escanea redes WiFi disponibles. Si está en 192.168.50.x usa API HTTP del ESP. */
    fun startWifiScan() {
        _uiState.update { it.copy(wifiScanning = true, wifiScanError = null, wifiConnectResult = null) }
        if (isOnEspApNetwork()) {
            startEspWifiScan()
        } else {
            startAndroidWifiScan()
        }
    }

    private fun startEspWifiScan() {
        viewModelScope.launch(Dispatchers.IO) {
            val result = EspWifiApi.getNetworks(context)
            if (result.isSuccess) {
                val data = result.getOrNull()
                _uiState.update {
                    it.copy(
                        espWifiNetworks = data?.networks ?: emptyList(),
                        espConnectedSsid = data?.connectedSsid?.trim()?.takeIf { s -> s.isNotBlank() },
                        wifiSourceEsp = true,
                        wifiNetworks = emptyList(),
                        wifiScanning = false,
                        wifiScanError = null
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        wifiScanning = false,
                        wifiScanError = result.exceptionOrNull()?.message ?: "No se pudo conectar al ESP"
                    )
                }
            }
        }
    }

    private fun startAndroidWifiScan() {
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: run {
            _uiState.update { it.copy(wifiScanning = false, wifiScanError = "WiFi no disponible") }
            return
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                try {
                    ctx.unregisterReceiver(this)
                } catch (_: Exception) {}
                val success = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false)
                val results = wm.scanResults
                    .filter { !it.SSID.isNullOrBlank() }
                    .distinctBy { it.SSID }
                    .sortedByDescending { it.level }
                _uiState.update {
                    it.copy(
                        wifiNetworks = results,
                        espWifiNetworks = emptyList(),
                        espConnectedSsid = null,
                        wifiSourceEsp = false,
                        wifiScanning = false,
                        wifiScanError = if (success || results.isNotEmpty()) null else "Escanear de nuevo"
                    )
                }
            }
        }
        val filter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.applicationContext.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                context.applicationContext.registerReceiver(receiver, filter)
            }
            val ok = wm.startScan()
            if (!ok) {
                context.applicationContext.unregisterReceiver(receiver)
                _uiState.update {
                    it.copy(wifiScanning = false, wifiScanError = "No se pudo iniciar el escaneo")
                }
            }
        } catch (e: Exception) {
            _uiState.update {
                it.copy(wifiScanning = false, wifiScanError = e.message ?: "Error al escanear")
            }
        }
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

    private fun getCurrentWifiSsid(): String? {
        return try {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return null
            val info = wm.connectionInfo
            val ssid = info.ssid?.replace("\"", "")?.trim()
            when {
                ssid.isNullOrBlank() -> null
                ssid == "<unknown ssid>" -> null
                else -> ssid
            }
        } catch (_: Exception) {
            null
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopScan()
    }
}
