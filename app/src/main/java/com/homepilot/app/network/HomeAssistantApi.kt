package com.homepilot.app.network

import com.homepilot.app.model.Entity
import retrofit2.Response
import retrofit2.http.*

interface HomeAssistantApi {

    @GET("states")
    suspend fun getAllStates(): Response<List<Entity>>

    @GET("states/{entityId}")
    suspend fun getEntityState(
        @Path("entityId") entityId: String
    ): Response<Entity>

    @POST("services/{domain}/{service}")
    suspend fun callService(
        @Path("domain") domain: String,
        @Path("service") service: String,
        @Body body: Map<String, @JvmSuppressWildcards Any>
    ): Response<List<Entity>>

    @GET("config")
    suspend fun getConfig(): Response<Map<String, @JvmSuppressWildcards Any>>

    @GET("services")
    suspend fun getServices(): Response<Map<String, @JvmSuppressWildcards Any>>
}
