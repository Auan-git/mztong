package com.ahu.ahutong.data.repository

import com.ahu.ahutong.data.SchoolConfig
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

/**
 * Gitee 开放 API（v5）。
 *
 * 学习资料的目录索引使用 `git/trees` 一次拉取全仓库文件树；
 * 公开仓库无需 access_token，匿名读取有频率限制，失败时会回退到分支 SHA 再取树。
 */
interface GiteeApi {

    @GET("repos/{owner}/{repo}/git/trees/{tree}")
    suspend fun getTree(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("tree") tree: String,
        @Query("recursive") recursive: String = "1"
    ): GiteeTreeResponse

    @GET("repos/{owner}/{repo}/branches/{branch}")
    suspend fun getBranch(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("branch") branch: String
    ): GiteeBranchResponse

    companion object {
        private const val BASE_URL = SchoolConfig.GITEE_API_BASE_URL

        val instance: GiteeApi by lazy {
            val client = OkHttpClient.Builder()
                .connectTimeout(8, TimeUnit.SECONDS)
                .readTimeout(12, TimeUnit.SECONDS)
                .callTimeout(18, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .addInterceptor { chain ->
                    val request = chain.request().newBuilder()
                        .header("Accept", "application/json")
                        .header("Cache-Control", "no-cache")
                        .header("User-Agent", "MZtong-Android")
                        .build()
                    chain.proceed(request)
                }
                .build()

            Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(GiteeApi::class.java)
        }
    }
}