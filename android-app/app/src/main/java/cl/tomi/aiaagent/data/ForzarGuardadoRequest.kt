package cl.tomi.aiaagent.data

import com.google.gson.annotations.SerializedName

data class ForzarGuardadoRequest(
    @SerializedName("channelId") val channelId: String? = null,
    @SerializedName("deviceId") val deviceId: String? = null
)
