package cl.tomi.aiaagent.api

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import cl.tomi.aiaagent.debug.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.Proxy
import java.util.concurrent.TimeUnit

/** IP del ESP en modo AP. Siempre 192.168.50.1:80 */
const val ESP_AP_BASE_URL = "http://192.168.50.1"

/**
 * API HTTP del WebServer del ESP para configuración WiFi.
 * Solo se usa cuando el teléfono está en la red 192.168.50.x (ESP AP).
 * Usa socketFactory de la red WiFi para evitar que use datos móviles.
 */
object EspWifiApi {

    private val defaultClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /** Cliente enlazado a la red 192.168.50.x. Sin CHANGE_NETWORK_STATE. */
    private fun clientForEspNetwork(context: Context): OkHttpClient {
        val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return defaultClient
        @Suppress("DEPRECATION")
        val espNetwork = cm.allNetworks.firstOrNull { network ->
            val caps = cm.getNetworkCapabilities(network) ?: return@firstOrNull false
            if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return@firstOrNull false
            cm.getLinkProperties(network)?.linkAddresses?.any { addr ->
                addr.address?.hostAddress?.startsWith("192.168.50.") == true
            } == true
        }
        return espNetwork?.let { network ->
            OkHttpClient.Builder()
                .socketFactory(network.socketFactory)
                .proxy(Proxy.NO_PROXY)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(90, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()
        } ?: defaultClient
    }

    data class WifiNetwork(
        val ssid: String,
        val rssi: Int,
        val encryption: String
    )

    data class WifiResponse(
        val connectedSsid: String,
        val connectedRssi: Int,
        val networks: List<WifiNetwork>
    )

    data class ConnectResult(
        val ok: Boolean,
        val message: String?,
        val error: String?
    )

    /** GET /api/wifi - Lista redes que el ESP puede ver. Reintenta 1 vez si falla por timeout. */
    suspend fun getNetworks(context: Context): Result<WifiResponse> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$ESP_AP_BASE_URL/api/wifi")
            .get()
            .build()
        var lastError: Exception? = null
        repeat(2) { attempt ->
            try {
                val httpClient = clientForEspNetwork(context)
                val response = httpClient.newCall(request).execute()
                val body = response.body?.string() ?: return@withContext Result.failure(Exception("Empty response"))
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("HTTP ${response.code}: $body"))
                }
                val json = JSONObject(body)
                val networksArray = json.optJSONArray("networks")
                val networks = (networksArray?.let { arr ->
                    (0 until arr.length()).map { i ->
                        val obj = arr.getJSONObject(i)
                        WifiNetwork(
                            ssid = obj.optString("ssid", ""),
                            rssi = obj.optInt("rssi", 0),
                            encryption = obj.optString("encryption", "encrypted")
                        )
                    }
                } ?: emptyList()).filter { it.ssid.isNotBlank() }
                return@withContext Result.success(
                    WifiResponse(
                        connectedSsid = json.optString("connected_ssid", ""),
                        connectedRssi = json.optInt("connected_rssi", 0),
                        networks = networks.sortedByDescending { it.rssi }
                    )
                )
            } catch (e: Exception) {
                lastError = e
                AppLog.w("EspWifiApi", "getNetworks attempt ${attempt + 1} failed: ${e.message}")
                if (attempt == 0) delay(2000)
            }
        }
        Result.failure(lastError ?: Exception("Timeout"))
    }

    /** POST /api/wifi - Conecta el ESP a una red. */
    suspend fun connect(ssid: String, password: String, context: Context): ConnectResult = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply {
                put("ssid", ssid)
                put("password", password)
            }.toString()
            val request = Request.Builder()
                .url("$ESP_AP_BASE_URL/api/wifi")
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()
            val response = clientForEspNetwork(context).newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            if (response.isSuccessful) {
                val json = JSONObject(responseBody)
                val ok = json.optBoolean("ok", true)
                val message = json.optString("message", "").takeIf { it.isNotBlank() }
                ConnectResult(ok = ok, message = message, error = null)
            } else {
                val json = try { JSONObject(responseBody) } catch (_: Exception) { JSONObject() }
                val error = json.optString("error", "HTTP ${response.code}")
                ConnectResult(ok = false, message = null, error = error)
            }
        } catch (e: Exception) {
            AppLog.w("EspWifiApi", "connect failed: ${e.message}")
            ConnectResult(ok = false, message = null, error = e.message ?: "Error de conexión")
        }
    }
}
