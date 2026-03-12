package cl.tomi.aiaagent.data

/**
 * Dispositivo descubierto por UDP en la red local.
 * Spec v2: soporta id, ip, name, type, device_id, channel_id, version, mqtt_topic_in, mqtt_topic_out.
 * rawPayload guarda el JSON completo del discovery para no perder ningún dato.
 */
data class NetworkDeviceItem(
    val id: String,
    val ip: String,
    val name: String? = null,
    val type: String? = null,
    val deviceId: String? = null,
    val channelId: String? = null,
    val version: String? = null,
    val mqttTopicIn: String? = null,
    val mqttTopicOut: String? = null,
    val rawPayload: String? = null
) {
    val displayName: String
        get() {
            val base = name?.takeIf { it.isNotBlank() } ?: id
            val ch = effectiveChannelId
            return if (ch != null) "$ch - $base" else base
        }

    /** device_id o id para Forzar Guardado */
    val effectiveDeviceId: String get() = deviceId?.takeIf { it.isNotBlank() } ?: id

    /** channel_id para Forzar Guardado (8 hex) */
    val effectiveChannelId: String? get() = channelId?.takeIf { it.isNotBlank() }

    /** Llave de negocio única: channel_id@device_id. Si no hay channel_id, usa solo device_id. */
    val businessKey: String
        get() {
            val dev = effectiveDeviceId
            val ch = effectiveChannelId?.takeIf { it.isNotBlank() }
            return if (ch != null) "$ch@$dev" else dev
        }
}
