package com.homepilot.app.network

import com.homepilot.app.model.ServerConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {

    private var retrofit: Retrofit? = null
    private var currentConfig: ServerConfig? = null

    fun getApi(config: ServerConfig): HomeAssistantApi {
        if (retrofit == null || currentConfig != config) {
            currentConfig = config

            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }

            val client = OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val request = chain.request().newBuilder()
                        .addHeader("Authorization", "Bearer ${config.accessToken}")
                        .addHeader("Content-Type", "application/json")
                        .build()
                    chain.proceed(request)
                }
                .addInterceptor(logging)
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()

            retrofit = Retrofit.Builder()
                .baseUrl(config.baseUrl)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
        }
        return retrofit!!.create(HomeAssistantApi::class.java)
    }

    fun reset() {
        retrofit = null
        currentConfig = null
    }
}
