package com.ahu.ahutong.data.server

import com.ahu.ahutong.data.SchoolConfig
import com.ahu.ahutong.data.server.model.ApkUpdateInfo
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import java.util.concurrent.TimeUnit

/**
 * 自动更新信息源：Gitee 仓库根目录的 update.json（静态文件，不消耗 API 频次）。
 *
 * 内容与 [ApkUpdateInfo] 一致：
 * ```json
 * {"update":true,"force":false,"versionCode":33,"versionName":"1.8.0",
 *  "changelog":"...","url":"https://gitee.com/.../releases/download/v1.8.0/xxx.apk",
 *  "sha256":"64位小写十六进制"}
 * ```
 * release 启动时由 MainViewModel.checkApkUpdate 自动检查；设置页「检查更新」复用同一份数据。
 */
interface GiteeUpdateApi {

    @GET("update.json")
    suspend fun getApkUpdateInfo(): ApkUpdateInfo

    companion object {
        private val baseUrl =
            "${SchoolConfig.GITEE_WEB_BASE_URL}/${SchoolConfig.RESOURCE_REPOSITORY_OWNER}/" +
                "${SchoolConfig.RESOURCE_REPOSITORY_NAME}/raw/${SchoolConfig.RESOURCE_REPOSITORY_BRANCH}/"

        private val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(60, TimeUnit.SECONDS)
            .build()

        val API: GiteeUpdateApi = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GiteeUpdateApi::class.java)
    }
}