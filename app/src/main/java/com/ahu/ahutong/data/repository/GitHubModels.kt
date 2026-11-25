package com.ahu.ahutong.data.repository

import com.ahu.ahutong.data.SchoolConfig

import com.google.gson.annotations.SerializedName

/**
 * 资源仓库内容项（目录或文件）。
 *
 * 类名沿用历史命名（GitHubContentItem），实际数据来源已切换为 Gitee。
 */
data class GitHubContentItem(
    val name: String,
    val path: String,
    val type: String,        // "file" or "dir"
    val size: Long = 0,
    @SerializedName("download_url")
    val downloadUrl: String? = null,
    @SerializedName("html_url")
    val htmlUrl: String? = null,
    val repositoryId: String? = null,
    val repositoryPath: String? = null,
    val containsOnlyUnsupportedFiles: Boolean = false
)

/** Gitee git/trees 响应。 */
data class GiteeTreeResponse(
    val sha: String? = null,
    val tree: List<GiteeTreeItem> = emptyList(),
    val truncated: Boolean = false
)

/** Gitee 文件树节点：type 为 "blob"（文件）或 "tree"（目录）。 */
data class GiteeTreeItem(
    val path: String,
    val type: String,
    val size: Long = 0
)

/** Gitee 分支信息（用于在树接口失败时解析最新 commit SHA）。 */
data class GiteeBranchResponse(
    val name: String? = null,
    val commit: GiteeBranchCommit? = null
)

data class GiteeBranchCommit(
    val sha: String? = null
)

/**
 * 本地下载记录
 */
data class DownloadedFile(
    val name: String,
    val path: String,         // 仓库内路径
    val localPath: String,    // 本地文件路径
    val size: Long = 0,
    val downloadTime: Long = System.currentTimeMillis(),
    val uri: String? = null
)

/**
 * 学院仓库配置
 */
data class RepoConfig(
    val id: String,
    val name: String,           // "老年福祉学院"
    val owner: String,
    val repo: String,
    val branch: String = "master"
) {
    val rawBase get() = "${SchoolConfig.GITEE_WEB_BASE_URL}/$owner/$repo/raw/$branch"
    val webUrl get() = "${SchoolConfig.GITEE_WEB_BASE_URL}/$owner/$repo"
}

data class CachedRepositoryContents(
    val items: List<GitHubContentItem>,
    val updateTime: Long
)

data class RepositoryDirectorySummary(
    val directoryCount: Int = 0,
    val fileCount: Int = 0
)

data class RepositoryMarkdownDocument(
    val title: String,
    val path: String,
    val content: String
)

data class RepositoryAccelerationSource(
    val id: String,
    val name: String,
    val description: String,
    val proxyPrefix: String? = null,
    val useJsDelivr: Boolean = false
)