package cl.tomi.aiaagent.api

import cl.tomi.aiaagent.data.EstanqueEstado
import cl.tomi.aiaagent.data.ForzarGuardadoRequest
import cl.tomi.aiaagent.data.ForzarGuardadoResponse
import retrofit2.Response
import retrofit2.http.*

interface EstanqueApi {

    @GET("monitor/api/estado")
    suspend fun getEstado(): Response<EstanqueEstado>

    @POST("monitor/api/historial/forzar-guardado")
    @Headers("Content-Type: application/json")
    suspend fun forzarGuardado(
        @Header("X-Aia-Origin") aiaOrigin: String,
        @Body body: ForzarGuardadoRequest?
    ): Response<ForzarGuardadoResponse>
}
