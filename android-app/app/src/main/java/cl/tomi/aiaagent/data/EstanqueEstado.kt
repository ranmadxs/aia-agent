package cl.tomi.aiaagent.data

import com.google.gson.annotations.SerializedName

data class EstanqueEstado(
    @SerializedName("distancia") val distancia: Double?,
    @SerializedName("litros") val litros: Double?,
    @SerializedName("porcentaje") val porcentaje: Double?,
    @SerializedName("altura_agua") val alturaAgua: Double?,
    @SerializedName("estado") val estado: String?,
    @SerializedName("ultima_lectura") val ultimaLectura: String?,
    @SerializedName("mqtt_connected") val mqttConnected: Boolean = false
)
