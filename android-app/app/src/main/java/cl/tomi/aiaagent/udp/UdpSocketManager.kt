package cl.tomi.aiaagent.udp

import cl.tomi.aiaagent.data.EstanqueEstado
import cl.tomi.aiaagent.debug.AppLog
import cl.tomi.aiaagent.data.NetworkDeviceItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/** Puerto UDP único para discovery y lecturas (spec NodeMCU) */
const val DISCOVERY_PORT = 9999

/** Mensaje discovery */
const val DISCOVERY_MESSAGE = "AIA-DISCOVER"

/**
 * Socket UDP compartido para discovery y datos del sensor.
 * Un solo bind al puerto 9999. Discovery y lecturas yai-mqtt/out usan el mismo socket.
 */
object UdpSocketManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _discoveredDevices = MutableSharedFlow<NetworkDeviceItem>(replay = 0, extraBufferCapacity = 64)
    val discoveredDevices: SharedFlow<NetworkDeviceItem> = _discoveredDevices

    data class SensorReading(val senderIp: String, val deviceId: String?, val estado: EstanqueEstado)
    private val _sensorReadings = MutableSharedFlow<SensorReading>(replay = 0, extraBufferCapacity = 64)
    val sensorReadings: SharedFlow<SensorReading> = _sensorReadings

    @Volatile
    private var socket: DatagramSocket? = null

    @Volatile
    private var receiverJob: kotlinx.coroutines.Job? = null

    fun ensureStarted() {
        if (socket != null) return
        synchronized(this) {
            if (socket != null) return
            try {
                val s = DatagramSocket(DISCOVERY_PORT)
                s.broadcast = true
                s.soTimeout = 2000
                socket = s
                AppLog.d("UDP", "Socket bound to port $DISCOVERY_PORT, msg=\"$DISCOVERY_MESSAGE\" (${DISCOVERY_MESSAGE.length} chars)")
                startReceiver()
            } catch (e: Exception) {
                socket = null
                throw e
            }
        }
    }

    private fun startReceiver() {
        if (receiverJob?.isActive == true) return
        receiverJob = scope.launch {
            val s = socket ?: return@launch
            val buf = ByteArray(1024)
            val recv = DatagramPacket(buf, buf.size)
            var idleCount = 0
            while (isActive) {
                try {
                    s.receive(recv)
                    idleCount = 0
                    val raw = String(recv.data, 0, recv.length, Charsets.UTF_8).trim()
                    val senderIp = recv.address?.hostAddress ?: ""
                    if (raw.startsWith("{")) AppLog.d("UdpSocket", "RX from $senderIp: ${raw.take(80)}...")
                    processMessage(raw, senderIp)
                } catch (_: Exception) {
                    idleCount++
                }
            }
        }
    }

    private fun processMessage(raw: String, senderIp: String) {
        if (raw.isBlank()) return
        if (raw.equals(DISCOVERY_MESSAGE, ignoreCase = true)) return
        if (!raw.startsWith("{")) return
        try {
            val json = JSONObject(raw)
            val topic = json.optString("topic")
            when {
                topic == "yai-mqtt/out" -> json.optJSONObject("payload")?.let { emitSensor(it, senderIp) }
                json.has("payload") -> json.optJSONObject("payload")?.takeIf { isSensorPayload(it) }?.let { emitSensor(it, senderIp) }
                isSensorPayload(json) -> emitSensor(json, senderIp)
                else -> {
                    val device = parseDiscovery(json, senderIp, raw)
                    if (device != null) {
                        scope.launch { _discoveredDevices.emit(device) }
                    } else if (raw.contains("distanceCm", ignoreCase = true) || raw.contains("litros", ignoreCase = true)) {
                        AppLog.d("UdpSocket", "JSON has sensor keys but no match - raw=${raw.take(120)}")
                    }
                }
            }
        } catch (e: Exception) {
            AppLog.w("UdpSocket", "parse error: ${e.message} raw=${raw.take(60)}")
        }
    }

    private fun isSensorPayload(json: JSONObject): Boolean {
        return json.has("distanceCm") || json.has("fillLevelPercent") || json.has("litros")
    }

    private fun emitSensor(payload: JSONObject, senderIp: String) {
        val estado = parsePayload(payload)
        val deviceId = payload.optString("deviceId").takeIf { it.isNotBlank() }
        AppLog.d("UdpSocket", "SENSOR from $senderIp deviceId=$deviceId litros=${estado.litros}")
        scope.launch { _sensorReadings.emit(SensorReading(senderIp, deviceId, estado)) }
    }

    private fun parseDiscovery(json: JSONObject, senderIp: String, rawPayload: String): NetworkDeviceItem? {
        val deviceId = json.optString("device_id").takeIf { it.isNotBlank() }
            ?: json.optString("id").takeIf { it.isNotBlank() }
            ?: return null
        fun validIp(s: String) = s.isNotBlank() && s != "null" && s != "undefined"
        val ip = senderIp.takeIf { validIp(it) }
            ?: json.optString("ip").takeIf { validIp(it) }
            ?: return null
        return NetworkDeviceItem(
            id = json.optString("id", deviceId),
            ip = ip,
            name = json.optString("name").takeIf { it.isNotBlank() },
            type = json.optString("type").takeIf { it.isNotBlank() },
            deviceId = deviceId,
            channelId = json.optString("channel_id").takeIf { it.isNotBlank() },
            version = json.optString("version").takeIf { it.isNotBlank() },
            mqttTopicIn = json.optString("mqtt_topic_in").takeIf { it.isNotBlank() },
            mqttTopicOut = json.optString("mqtt_topic_out").takeIf { it.isNotBlank() },
            rawPayload = rawPayload
        )
    }

    /** Parsea número que puede venir como number o string (NodeMCU a veces serializa como string) */
    private fun optNumber(obj: JSONObject, key: String): Double? {
        return when (val v = obj.opt(key)) {
            is Number -> v.toDouble()
            is String -> v.toDoubleOrNull()
            else -> null
        }?.takeIf { !it.isNaN() }
    }

    private fun parsePayload(payload: JSONObject): EstanqueEstado {
        val distanceCm = optNumber(payload, "distanceCm")
        val tankDepthCm = optNumber(payload, "tankDepthCm")
        val alturaAgua = if (distanceCm != null && tankDepthCm != null) tankDepthCm - distanceCm else null
        return EstanqueEstado(
            distancia = distanceCm,
            litros = optNumber(payload, "litros"),
            porcentaje = optNumber(payload, "fillLevelPercent"),
            alturaAgua = alturaAgua,
            estado = payload.optString("status").takeIf { it.isNotBlank() },
            ultimaLectura = payload.optString("timestamp").takeIf { it.isNotBlank() },
            mqttConnected = true
        )
    }

    fun sendDiscovery() {
        val s = socket ?: return
        try {
            val msg = DISCOVERY_MESSAGE.toByteArray(Charsets.UTF_8)
            AppLog.d("UDP", "Sending AIA-DISCOVER broadcast (${msg.size} bytes) to 255.255.255.255:9999")
            val packet = DatagramPacket(msg, msg.size, InetAddress.getByName("255.255.255.255"), DISCOVERY_PORT)
            s.send(packet)
            AppLog.d("UDP", "Broadcast sent OK")
        } catch (e: Exception) {
            AppLog.w("UDP", "Broadcast send failed: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    /** Envía AIA-DISCOVER unicast al dispositivo para que registre la app como subscriber.
     *  También envía al broadcast de la subred por si el ESP recibe mejor así en modo AP. */
    fun sendDiscoveryTo(ip: String?) {
        if (ip.isNullOrBlank() || ip == "null" || ip == "undefined") {
            AppLog.w("UDP", "Cannot send: device.ip is invalid (ip=$ip)")
            return
        }
        val localAddr = socket?.localAddress?.hostAddress ?: "?"
        AppLog.d("UDP", "App $localAddr:9999 → Sending AIA-DISCOVER to $ip:9999 (unicast)")
        sendTo(ip, DISCOVERY_MESSAGE.toByteArray(Charsets.UTF_8))
        sendDiscoveryToSubnetBroadcast(ip)
    }

    fun sendTo(ip: String?, data: ByteArray) {
        if (ip.isNullOrBlank() || ip == "null" || ip == "undefined") {
            AppLog.w("UDP", "Cannot send: ip is invalid (ip=$ip)")
            return
        }
        val s = socket ?: return
        try {
            val packet = DatagramPacket(data, data.size, InetAddress.getByName(ip), DISCOVERY_PORT)
            s.send(packet)
            AppLog.d("UDP", "Sent ${data.size} bytes to $ip:9999 OK")
        } catch (e: Exception) {
            AppLog.w("UDP", "Send failed to $ip: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    /** Envía también al broadcast de la subred (ej. 192.168.50.255) por si el ESP recibe mejor broadcast en modo AP. */
    fun sendDiscoveryToSubnetBroadcast(ip: String) {
        val parts = ip.split(".")
        if (parts.size == 4) {
            val subnetBroadcast = "${parts[0]}.${parts[1]}.${parts[2]}.255"
            if (subnetBroadcast != ip) {
                AppLog.d("UDP", "Sending AIA-DISCOVER to subnet broadcast $subnetBroadcast:9999")
                sendTo(subnetBroadcast, DISCOVERY_MESSAGE.toByteArray(Charsets.UTF_8))
            }
        }
    }

    fun close() {
        synchronized(this) {
            receiverJob?.cancel()
            socket?.close()
            socket = null
            receiverJob = null
        }
    }
}
