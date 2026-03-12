package cl.tomi.aiaagent.data

import com.google.gson.annotations.SerializedName

data class ForzarGuardadoResponse(
    @SerializedName("mensaje") val mensaje: String,
    @SerializedName("guardado") val guardado: Boolean,
    @SerializedName("db") val db: String? = null
)
