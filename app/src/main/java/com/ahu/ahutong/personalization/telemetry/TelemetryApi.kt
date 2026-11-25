package com.ahu.ahutong.personalization.telemetry

import com.ahu.ahutong.BuildConfig
import com.ahu.ahutong.data.SchoolConfig
import java.util.concurrent.TimeUnit
import okhttp3.CookieJar
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface TelemetryApi {
    @POST("/v1/on-device-model-evaluations/credentials")
    suspend fun credential(
        @Body request: TelemetryCredentialRequest
    ): retrofit2.Response<TelemetryCredentialResponse>

    @POST("/v1/on-device-model-evaluations/batch")
    suspend fun upload(
        @Header("Authorization") telemetryCredential: String,
        @Header("X-Body-SHA256") bodySha256Hex: String,
        @Body exactBody: RequestBody
    ): retrofit2.Response<ResponseBody>

    @POST("/v1/on-device-model-evaluations/delete")
    suspend fun delete(
        @Body request: TelemetryDeletionRequest
    ): retrofit2.Response<ResponseBody>

    companion object {
        // 服务地址集中在 SchoolConfig，详见其中的 TODO(民政职业大学) 说明。
        private const val BASE_URL = SchoolConfig.UPDATE_SERVER_BASE_URL
        private val allowedHost = SchoolConfig.UPDATE_SERVER_HOST
        private val allowedPaths = setOf(
            "/v1/on-device-model-evaluations/credentials",
            "/v1/on-device-model-evaluations/batch",
            "/v1/on-device-model-evaluations/delete"
        )

        private val client = OkHttpClient.Builder()
            .cookieJar(CookieJar.NO_COOKIES)
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor { chain ->
                val request = chain.request()
                check(request.url.isHttps && request.url.host == allowedHost && request.url.encodedPath in allowedPaths) {
                    "telemetry request escaped the fixed HTTPS allowlist"
                }
                chain.proceed(
                    request.newBuilder()
                        .removeHeader("Cookie")
                        .header("User-Agent", "MZtong/${BuildConfig.VERSION_NAME} (Android telemetry)")
                        .header("Accept", "application/json")
                        .build()
                )
            }
            .build()

        val API: TelemetryApi = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(TelemetryApi::class.java)
    }
}
