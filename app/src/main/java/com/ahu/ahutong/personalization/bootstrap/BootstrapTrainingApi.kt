package com.ahu.ahutong.personalization.bootstrap

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

interface BootstrapTrainingApi {
    @POST("/v1/bootstrap-training-data/credentials")
    suspend fun credential(
        @Body request: BootstrapTrainingCredentialRequest
    ): retrofit2.Response<BootstrapTrainingCredentialResponse>

    @POST("/v1/bootstrap-training-data/batches")
    suspend fun upload(
        @Header("Authorization") credential: String,
        @Header("X-Body-SHA256") bodySha256Hex: String,
        @Body exactBody: RequestBody
    ): retrofit2.Response<ResponseBody>

    @POST("/v1/bootstrap-training-data/delete")
    suspend fun delete(
        @Body request: BootstrapTrainingDeletionRequest
    ): retrofit2.Response<ResponseBody>

    companion object {
        // 服务地址集中在 SchoolConfig，详见其中的 TODO(民政职业大学) 说明。
        private const val BASE_URL = SchoolConfig.UPDATE_SERVER_BASE_URL
        private val allowedHost = SchoolConfig.UPDATE_SERVER_HOST
        private val allowedPaths = setOf(
            "/v1/bootstrap-training-data/credentials",
            "/v1/bootstrap-training-data/batches",
            "/v1/bootstrap-training-data/delete"
        )

        private val client = OkHttpClient.Builder()
            .cookieJar(CookieJar.NO_COOKIES)
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(45, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor { chain ->
                val request = chain.request()
                check(request.url.isHttps && request.url.host == allowedHost && request.url.encodedPath in allowedPaths) {
                    "bootstrap training request escaped the fixed HTTPS allowlist"
                }
                chain.proceed(
                    request.newBuilder()
                        .removeHeader("Cookie")
                        .removeHeader("Authorization")
                        .apply {
                            request.header("Authorization")?.let { header("Authorization", it) }
                        }
                        .header("User-Agent", "MZtong/${BuildConfig.VERSION_NAME} (Android bootstrap-training)")
                        .header("Accept", "application/json")
                        .build()
                )
            }
            .build()

        val API: BootstrapTrainingApi = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(BootstrapTrainingApi::class.java)
    }
}
