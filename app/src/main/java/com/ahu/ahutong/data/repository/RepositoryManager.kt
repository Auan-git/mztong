package com.ahu.ahutong.data.repository

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Base64
import com.ahu.ahutong.AHUApplication
import com.ahu.ahutong.data.SchoolConfig
import com.ahu.ahutong.data.dao.PreferencesManager
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import com.tencent.mmkv.MMKV
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

object RepositoryManager {
    private const val GITEE_HOST = SchoolConfig.GITEE_WEB_BASE_URL
    private const val CONTENT_CACHE_PREFIX = "content_cache_"
    private const val CONTENT_CACHE_TIME_PREFIX = "content_cache_time_"
    private const val CONTENT_TREE_CACHE_TIME_KEY = "content_tree_cache_time"
    private const val CONTENT_TREE_CACHE_VERSION_KEY = "content_tree_cache_version"
    private const val CONTENT_UNSUPPORTED_PATHS_KEY = "content_unsupported_paths"
    private const val CONTENT_CACHE_VERSION = 9
    private const val DOWNLOAD_RECORDS_KEY = "downloaded_files"
    private const val DOWNLOAD_RELATIVE_ROOT = "ahutong"
    private const val GIT_LFS_POINTER_PREFIX = "version https://git-lfs.github.com/spec/v1"
    private const val GIT_LFS_POINTER_MAX_BYTES = 1024
    private val GIT_LFS_BATCH_MEDIA_TYPE = "application/vnd.git-lfs+json".toMediaType()

    /**
     * 学习资料与软件源码放在同一个 Gitee 仓库里，按「学习资料/<学院名>/」区分。
     *
     * 学院 id 保持不变以兼容本地缓存与导航；实际资料目录由 pathPrefix 决定。
     */
    private val repositorySources = listOf(
        RepositorySource(
            id = "cs",
            title = "老年福祉学院",
            pathPrefix = "${SchoolConfig.RESOURCE_MATERIALS_ROOT}/老年福祉学院"
        ),
        RepositorySource(
            id = "ai",
            title = "社会工作学院",
            pathPrefix = "${SchoolConfig.RESOURCE_MATERIALS_ROOT}/社会工作学院"
        ),
        RepositorySource(
            id = "ic",
            title = "康复工程学院",
            pathPrefix = "${SchoolConfig.RESOURCE_MATERIALS_ROOT}/康复工程学院"
        ),
        RepositorySource(
            id = "ee",
            title = "生命文化学院",
            pathPrefix = "${SchoolConfig.RESOURCE_MATERIALS_ROOT}/生命文化学院"
        ),
        RepositorySource(
            id = "internet",
            title = "婚礼文化与传媒艺术学院",
            pathPrefix = "${SchoolConfig.RESOURCE_MATERIALS_ROOT}/婚礼文化与传媒艺术学院"
        ),
        RepositorySource(
            id = "sbi",
            title = "儿童教育与发展学院",
            pathPrefix = "${SchoolConfig.RESOURCE_MATERIALS_ROOT}/儿童教育与发展学院"
        )
    )
    private val repositorySourceById = repositorySources.associateBy { it.id }
    private val repositoryTitleById = repositorySources.associate { it.id to it.title }

    /**
     * 下载线路。资源仓库已迁移到国内 Gitee，直连即可，无需再使用 GitHub 加速源。
     * 旧版本保存的加速源 id（jsdelivr / moeyy / gh-proxy / direct）会自动回退到这里的第一项。
     */
    val accelerationSources = listOf(
        RepositoryAccelerationSource(
            id = "gitee",
            name = "Gitee 直连（推荐）",
            description = "国内直连 Gitee，无需额外加速"
        )
    )

    private val gson = Gson()
    private val kv: MMKV by lazy {
        MMKV.initialize(AHUApplication.getApp())
        MMKV.mmkvWithID("repository_downloads")
    }
    private val warmUpMutex = Mutex()

    private val downloadClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .followRedirects(true)
        .build()

    // === Gitee API ===

    suspend fun getContents(path: String = "", forceRefresh: Boolean = false): List<GitHubContentItem> {
        return withContext(Dispatchers.IO) {
            val fallbackRootItems = if (path.isBlank()) repositoryRootItems() else null

            if (!forceRefresh) {
                getCachedContents(path)?.items?.let { return@withContext it }
            }

            runCatching {
                warmUpAllContentCaches(
                    forceRefresh = forceRefresh || getCachedContents(path) == null
                )
            }.onSuccess {
                getCachedContents(path)?.items?.let { return@withContext it }
            }.onFailure {
                fallbackRootItems?.let { return@withContext it }
            }

            getCachedContents(path)?.items?.let { return@withContext it }
            fallbackRootItems?.let { return@withContext it }

            throw IllegalStateException("目录缓存不可用")
        }
    }

    suspend fun warmUpAllContentCaches(
        forceRefresh: Boolean = false,
        onProgress: ((Int) -> Unit)? = null
    ): Long {
        return withContext(Dispatchers.IO) {
            warmUpMutex.withLock {
                val cachedUpdateTime = kv.decodeLong(CONTENT_TREE_CACHE_TIME_KEY, 0L)
                val cachedVersion = kv.decodeInt(CONTENT_TREE_CACHE_VERSION_KEY, 0)
                // UI warm-up passes onProgress and should refresh remote trees even when cache exists.
                if (!forceRefresh &&
                    onProgress == null &&
                    cachedUpdateTime > 0L &&
                    cachedVersion == CONTENT_CACHE_VERSION &&
                    getCachedContents("") != null
                ) {
                    return@withLock cachedUpdateTime
                }

                val grouped = buildAllDirectoryCaches(onProgress)
                val updateTime = System.currentTimeMillis()
                grouped.forEach { (path, items) ->
                    saveContentCache(path, items, updateTime)
                }
                kv.encode(CONTENT_TREE_CACHE_TIME_KEY, updateTime)
                kv.encode(CONTENT_TREE_CACHE_VERSION_KEY, CONTENT_CACHE_VERSION)
                updateTime
            }
        }
    }

    fun getCachedContents(path: String = ""): CachedRepositoryContents? {
        if (kv.decodeInt(CONTENT_TREE_CACHE_VERSION_KEY, 0) != CONTENT_CACHE_VERSION) {
            return null
        }
        val key = contentCacheKey(path)
        val json = kv.decodeString("$CONTENT_CACHE_PREFIX$key", null) ?: return null
        val cached = try {
            val type = object : TypeToken<List<GitHubContentItem>>() {}.type
            val items: List<GitHubContentItem> = gson.fromJson(json, type)
            CachedRepositoryContents(
                items = items,
                updateTime = kv.decodeLong("$CONTENT_CACHE_TIME_PREFIX$key", 0L)
            )
        } catch (e: Exception) {
            null
        } ?: return null

        if (normalizeRepositoryPath(path).isEmpty() && !matchesCurrentRepositorySources(cached.items)) {
            return null
        }

        if (cached.items.any { it.name.contains('/') || it.name.contains('\\') }) {
            return null
        }

        return cached
    }

    fun shouldShowUnsupportedDirectoryMessage(path: String): Boolean {
        if (path.isBlank()) return false
        return getUnsupportedDirectoryPaths().contains(normalizeRepositoryPath(path))
    }

    fun getDirectorySummaries(items: List<GitHubContentItem>): Map<String, RepositoryDirectorySummary> {
        return items
            .filter { it.type == "dir" }
            .associate { item ->
                val cachedChildren = getCachedContents(item.path)?.items.orEmpty()
                item.path to RepositoryDirectorySummary(
                    directoryCount = cachedChildren.count { it.type == "dir" },
                    fileCount = cachedChildren.count { it.type == "file" }
                )
            }
    }

    fun getRawUrl(path: String): String {
        val resolved = resolveVirtualPath(path) ?: return ""
        return resolved.source.rawUrl(resolved.repositoryPath)
    }

    fun getRepositoryUrl(path: String): String {
        val resolved = resolveVirtualPath(path) ?: return repositoryRootUrl()
        return resolved.source.webUrl(resolved.repositoryPath, tree = true)
    }

    /** 资源仓库首页链接（用于「向 Gitee 仓库提 PR」入口）。 */
    fun getRepositoryRootUrl(): String = repositoryRootUrl()

    fun getRepositoryTitle(repoId: String): String {
        return repositoryTitleById[repoId] ?: repoId
    }

    fun getRepositoryOrder(path: String): Int {
        val repositoryId = normalizeRepositoryPath(path).substringBefore('/', "")
        val index = repositorySources.indexOfFirst { it.id == repositoryId }
        return if (index >= 0) index else Int.MAX_VALUE
    }

    fun formatDisplayPath(path: String): String {
        val normalized = normalizeRepositoryPath(path)
        if (normalized.isBlank()) return "学习资料"
        val segments = normalized.split('/').filter { it.isNotBlank() }
        if (segments.isEmpty()) return "学习资料"
        val repositoryId = segments.first()
        val repositoryTitle = getRepositoryTitle(repositoryId)
        val displaySegments = buildList {
            add(repositoryTitle)
            val relativeSegments = segments.drop(1)
            val trimmedRelativeSegments = if (relativeSegments.firstOrNull() == repositoryTitle) {
                relativeSegments.drop(1)
            } else {
                relativeSegments
            }
            addAll(trimmedRelativeSegments)
        }
        return displaySegments.joinToString("/")
    }

    suspend fun getMarkdownDocument(path: String): RepositoryMarkdownDocument {
        return withContext(Dispatchers.IO) {
            val resolved = resolveVirtualPath(path)
                ?: throw IllegalArgumentException("无效的 Markdown 路径")
            val urls = getDownloadUrls(path, AHUApplication.getApp())
            val selectedAccelerationSource = getSelectedAccelerationSource(AHUApplication.getApp())
            var lastError: Exception? = null
            for (url in urls) {
                try {
                    val request = Request.Builder().url(url).build()
                    downloadClient.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            lastError = IllegalStateException("HTTP ${response.code}")
                            return@use
                        }
                        val content = response.readGitLfsMarkdown(
                            source = resolved.source,
                            accelerationSource = selectedAccelerationSource
                        )
                            ?: response.body?.string().orEmpty()
                        return@withContext RepositoryMarkdownDocument(
                            title = File(resolved.repositoryPath).name.ifBlank { "Markdown" },
                            path = path,
                            content = content
                        )
                    }
                } catch (e: Exception) {
                    lastError = e
                }
            }
            throw lastError ?: IllegalStateException("无法读取 Markdown")
        }
    }

    private suspend fun getDownloadUrls(path: String, context: Context): List<String> {
        val resolved = resolveVirtualPath(path) ?: return emptyList()
        getSelectedAccelerationSource(context)
        return listOf(resolved.source.rawUrl(resolved.repositoryPath))
    }

    // === 下载管理 ===

    fun getDownloadedFiles(context: Context): List<DownloadedFile> {
        val records = getDownloadRecords()
        val files = records.mapNotNull { record ->
            record.toDownloadedFile(context) ?: run {
                removeDownloadRecord(record.path)
                null
            }
        }
        return files.sortedByDescending { it.downloadTime }
    }

    fun getDownloadedFile(path: String, context: Context): DownloadedFile? {
        val record = getDownloadRecords().firstOrNull { it.path == path } ?: return null
        return record.toDownloadedFile(context) ?: run {
            removeDownloadRecord(path)
            null
        }
    }

    fun isDownloaded(path: String, context: Context): Boolean {
        return getDownloadedFile(path, context) != null
    }

    suspend fun downloadFile(
        path: String,
        context: Context,
        onProgress: (Float) -> Unit = {}
    ): DownloadedFile? = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val resolved = resolveVirtualPath(path) ?: return@withContext null
        val urls = getDownloadUrls(path, appContext)
        val selectedAccelerationSource = getSelectedAccelerationSource(appContext)
        val previousRecord = getDownloadRecord(path)
        val target = createDownloadTarget(path, appContext)

        for ((index, url) in urls.withIndex()) {
            try {
                val request = Request.Builder().url(url).build()
                var gitLfsPointer: GitLfsPointer? = null
                var gitLfsDownload: GitLfsDownloadAction? = null
                downloadClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@use
                    }
                    val pointer = response.readGitLfsPointer()
                    if (pointer != null) {
                        gitLfsPointer = pointer
                        gitLfsDownload = resolveGitLfsDownload(resolved.source, pointer)
                        return@use
                    }

                    val body = response.body ?: return@use
                    val total = body.contentLength()
                    var downloadedBytes = 0L

                    body.byteStream().use { input ->
                        target.openOutputStream().use { output ->
                            val buffer = ByteArray(8 * 1024)
                            var completed: Long = 0
                            var read = input.read(buffer)
                            while (read >= 0) {
                                output.write(buffer, 0, read)
                                completed += read
                                downloadedBytes = completed
                                if (total > 0) {
                                    onProgress(completed.toFloat() / total.toFloat())
                                }
                                read = input.read(buffer)
                            }
                            output.flush()
                        }
                    }

                    target.markCompleted()
                    if (target is DownloadTarget.MediaStoreTarget || previousRecord?.uri != null) {
                        previousRecord?.delete(appContext)
                    }
                    removeDownloadRecord(path)
                    val record = DownloadRecord(
                        path = path,
                        localName = target.relativePath,
                        uri = target.uri?.toString(),
                        localPath = target.displayPath,
                        downloadTime = System.currentTimeMillis(),
                        size = downloadedBytes
                    )
                    saveDownloadRecord(record)
                    return@withContext record.toDownloadedFile(appContext)
                }

                if (gitLfsPointer != null) {
                    val lfsDownload = gitLfsDownload
                    if (lfsDownload != null) {
                        val lfsResult = downloadGitLfsFile(
                            urls = buildLfsDownloadUrls(
                                href = lfsDownload.href,
                                accelerationSource = selectedAccelerationSource
                            ),
                            headers = lfsDownload.header,
                            path = path,
                            context = appContext,
                            target = target,
                            previousRecord = previousRecord,
                            pointer = gitLfsPointer,
                            onProgress = onProgress
                        )
                        if (lfsResult != null) return@withContext lfsResult
                    }
                }
            } catch (e: Exception) {
                if (index == urls.lastIndex) {
                    target.delete()
                    return@withContext null
                }
            }
        }
        target.delete()
        null
    }

    fun getLocalFile(path: String, context: Context): File? {
        val file = getDownloadedFile(path, context) ?: return null
        val localFile = File(file.localPath)
        return if (localFile.exists()) localFile else null
    }

    fun deleteFile(path: String, context: Context): Boolean {
        val record = getDownloadRecords().firstOrNull { it.path == path } ?: return false
        val deleted = record.delete(context)
        if (deleted) {
            removeDownloadRecord(path)
        }
        return deleted
    }

    // === 内部方法 ===

    private suspend fun buildAllDirectoryCaches(
        onProgress: ((Int) -> Unit)? = null
    ): Map<String, List<GitHubContentItem>> {
        val progressCounter = AtomicInteger(0)
        // 六个学院都在同一个仓库里：按仓库分组，树只拉一次，再按学院前缀切分。
        val groupedSources = repositorySources.groupBy { Triple(it.owner, it.repo, it.branch) }
        val snapshotTree = loadMaterialsTreeSnapshot()
        val repositoryCaches: List<Result<Pair<RepositorySource, RepositoryIndexCache>>> = supervisorScope {
            groupedSources.values.map { sources ->
                async {
                    val first = sources.first()
                    val treeResult = runCatching {
                        runCatching {
                            GiteeApi.instance.getTree(
                                owner = first.owner,
                                repo = first.repo,
                                tree = first.branch
                            ).tree
                        }.recoverCatching {
                            val treeSha = resolveRepositoryTreeSha(first)
                            GiteeApi.instance.getTree(
                                owner = first.owner,
                                repo = first.repo,
                                tree = treeSha
                            ).tree
                        }.getOrThrow()
                    }.recoverCatching {
                        // Gitee 匿名 API 有频率限制（403 Rate Limit），此时用 App 内置快照兜底，
                        // 文件本身仍可通过 raw 直链下载，不影响浏览已收录的资料。
                        snapshotTree.takeIf { it.isNotEmpty() }
                            ?: throw IllegalStateException("无法获取资料目录")
                    }
                    sources.map { source ->
                        treeResult.mapCatching { tree ->
                            source to buildDirectoryCachesFromGitHubTree(source, tree)
                        }.also { result ->
                            if (result.isSuccess) {
                                val fileCount = result.getOrNull()?.second?.documentFileCount ?: 0
                                onProgress?.invoke(progressCounter.addAndGet(fileCount))
                            }
                        }
                    }
                }
            }.awaitAll().flatten()
        }

        val grouped = mutableMapOf("" to repositoryRootItems())
        val unsupportedPaths = mutableSetOf<String>()
        repositoryCaches.mapNotNull { it.getOrNull() }.forEach { (_, cache) ->
            grouped.putAll(cache.grouped)
            unsupportedPaths += cache.unsupportedDirectoryPaths
        }
        if (grouped.size == 1 && repositoryCaches.all { it.isFailure }) {
            throw repositoryCaches.firstNotNullOfOrNull { it.exceptionOrNull() }
                ?: IllegalStateException("无法构建目录索引")
        }
        saveUnsupportedDirectoryPaths(unsupportedPaths)
        return grouped
    }

    private fun repositoryRootItems(): List<GitHubContentItem> {
        return repositorySources.map { source ->
            GitHubContentItem(
                name = source.title,
                path = source.id,
                type = "dir",
                htmlUrl = source.webUrl("", tree = true),
                repositoryId = source.id,
                repositoryPath = ""
            )
        }
    }

    private fun buildDirectoryCachesFromGitHubTree(
        source: RepositorySource,
        tree: List<GiteeTreeItem>
    ): RepositoryIndexCache {
        val sourceTree = source.filterTree(tree)
        val resolvedLfsSizes = resolveGitLfsDisplaySizes(source, sourceTree)
        val allChildren = mutableMapOf<String, MutableList<GiteeTreeItem>>()
        val allDirectories = mutableSetOf("")

        sourceTree.forEach { treeItem ->
            val repositoryPath = normalizeRepositoryPath(treeItem.path)
            if (repositoryPath.isEmpty()) return@forEach

            val parent = repositoryPath.substringBeforeLast('/', "")
            allChildren.getOrPut(parent) { mutableListOf() }.add(
                treeItem.copy(path = repositoryPath)
            )
            allDirectories += parent
            if (treeItem.type == "tree") {
                allDirectories += repositoryPath
            }
        }

        val displayChildren = mutableMapOf<String, MutableList<GitHubContentItem>>()
        val displayableDirectories = mutableSetOf<String>()
        val unsupportedDirectoryPaths = mutableSetOf<String>()

        val directoriesByDepth = allDirectories.sortedByDescending { path ->
            if (path.isEmpty()) 0 else path.count { it == '/' } + 1
        }
        directoriesByDepth.forEach { repositoryPath ->
            val children = allChildren[repositoryPath].orEmpty()
            val displayed = children.mapNotNull { child ->
                val childPath = normalizeRepositoryPath(child.path)
                val childName = childPath.substringAfterLast('/')
                when {
                    child.type == "blob" && isDocumentFile(childName) -> {
                        GitHubContentItem(
                            name = childName,
                            path = virtualPath(source, childPath),
                            type = "file",
                            size = resolvedLfsSizes[childPath] ?: child.size,
                            downloadUrl = source.rawUrl(childPath),
                            htmlUrl = source.webUrl(childPath, tree = false),
                            repositoryId = source.id,
                            repositoryPath = childPath
                        )
                    }
                    child.type == "tree" && childPath in displayableDirectories -> {
                        GitHubContentItem(
                            name = childName,
                            path = virtualPath(source, childPath),
                            type = "dir",
                            htmlUrl = source.webUrl(childPath, tree = true),
                            repositoryId = source.id,
                            repositoryPath = childPath
                        )
                    }
                    else -> null
                }
            }

            val virtualDirectoryPath = virtualPath(source, repositoryPath)
            displayChildren[virtualDirectoryPath] = displayed.toMutableList()
            if (displayed.isNotEmpty()) {
                displayableDirectories += repositoryPath
            } else if (children.isNotEmpty()) {
                unsupportedDirectoryPaths += virtualDirectoryPath
            }
        }

        displayChildren.forEach { (virtualDirectoryPath, children) ->
            val resolved = resolveVirtualPath(virtualDirectoryPath) ?: return@forEach
            children.addAll(
                allChildren[resolved.repositoryPath].orEmpty().mapNotNull { child ->
                    val childPath = normalizeRepositoryPath(child.path)
                    if (child.type != "tree" || childPath in displayableDirectories) {
                        return@mapNotNull null
                    }
                    val childVirtualPath = virtualPath(source, childPath)
                    if (childVirtualPath !in unsupportedDirectoryPaths) return@mapNotNull null
                    GitHubContentItem(
                        name = childPath.substringAfterLast('/'),
                        path = childVirtualPath,
                        type = "dir",
                        htmlUrl = source.webUrl(childPath, tree = true),
                        repositoryId = source.id,
                        repositoryPath = childPath,
                        containsOnlyUnsupportedFiles = true
                    )
                }
            )
        }

        return RepositoryIndexCache(
            grouped = displayChildren.mapValues { (_, items) ->
                sortRepositoryItems(items.distinctBy { it.path })
            },
            unsupportedDirectoryPaths = unsupportedDirectoryPaths,
            documentFileCount = tree.count { treeItem ->
                treeItem.type == "blob" && isDocumentFile(treeItem.path.substringAfterLast('/'))
            }
        )
    }

    private fun getDownloadRecords(): List<DownloadRecord> {
        val json = kv.decodeString(DOWNLOAD_RECORDS_KEY, "[]") ?: "[]"
        return try {
            val element = JsonParser.parseString(json)
            if (!element.isJsonArray) return emptyList()
            val array = element.asJsonArray
            if (array.size() == 0) {
                emptyList()
            } else if (array.firstOrNull()?.isJsonArray == true) {
                parseLegacyDownloadRecords(array)
            } else {
                val type = object : TypeToken<List<DownloadRecord>>() {}.type
                gson.fromJson<List<DownloadRecord>>(array, type).filter { it.path.isNotEmpty() }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun getDownloadRecord(path: String): DownloadRecord? {
        return getDownloadRecords().firstOrNull { it.path == path }
    }

    private fun parseLegacyDownloadRecords(array: JsonArray): List<DownloadRecord> {
        return array.mapNotNull { element ->
            val item = element.takeIf { it.isJsonArray }?.asJsonArray ?: return@mapNotNull null
            if (item.size() < 2) return@mapNotNull null
            val path = item[0].asString
            val localName = item[1].asString
            DownloadRecord(
                path = path,
                localName = localName,
                localPath = File(getLegacyDownloadDir(AHUApplication.getApp()), localName).absolutePath
            )
        }
    }

    private fun saveDownloadRecord(record: DownloadRecord) {
        val files = getDownloadRecords().toMutableList()
        files.removeAll { it.path == record.path }
        files.add(record)
        kv.encode(DOWNLOAD_RECORDS_KEY, gson.toJson(files))
    }

    private fun removeDownloadRecord(path: String) {
        val files = getDownloadRecords().toMutableList()
        files.removeAll { it.path == path }
        kv.encode(DOWNLOAD_RECORDS_KEY, gson.toJson(files))
    }

    private fun saveContentCache(
        path: String,
        items: List<GitHubContentItem>,
        updateTime: Long = System.currentTimeMillis()
    ) {
        val key = contentCacheKey(path)
        kv.encode("$CONTENT_CACHE_PREFIX$key", gson.toJson(items))
        kv.encode("$CONTENT_CACHE_TIME_PREFIX$key", updateTime)
    }

    private fun createDownloadTarget(path: String, context: Context): DownloadTarget {
        val relativePath = safeRelativeFilePath(path)
        val parent = relativePath.substringBeforeLast('/', "")
        val displayName = relativePath.substringAfterLast('/').ifEmpty { "repository_file" }
        val relativeDirectory = listOf(
            Environment.DIRECTORY_DOWNLOADS,
            DOWNLOAD_RELATIVE_ROOT,
            parent
        )
            .filter { it.isNotEmpty() }
            .joinToString("/")
            .let { "$it/" }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativeDirectory)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("无法创建下载文件")
            val displayPath = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "$DOWNLOAD_RELATIVE_ROOT/$relativePath"
            ).absolutePath
            DownloadTarget.MediaStoreTarget(context, uri, relativePath, displayPath)
        } else {
            val file = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "$DOWNLOAD_RELATIVE_ROOT/$relativePath"
            )
            file.parentFile?.mkdirs()
            DownloadTarget.FileTarget(file, relativePath)
        }
    }

    private fun getLegacyDownloadDir(context: Context): File {
        val root = context.getExternalFilesDir(null) ?: context.filesDir
        val dir = File(root, "repository")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun safeRelativeFilePath(path: String): String {
        val segments = path
            .replace('\\', '/')
            .split('/')
            .map { it.trim() }
            .filter { it.isNotEmpty() && it != "." && it != ".." }
        return segments.joinToString("/").ifEmpty { "repository_file" }
    }

    private fun sortRepositoryItems(items: List<GitHubContentItem>): List<GitHubContentItem> {
        val dirs = items.filter { it.type == "dir" }.sortedBy { it.name.lowercase() }
        val files = items.filter { it.type == "file" }.sortedBy { it.name.lowercase() }
        return dirs + files
    }

    private suspend fun getSelectedAccelerationSource(context: Context): RepositoryAccelerationSource {
        val selectedId = PreferencesManager(context.applicationContext)
            .repositoryAccelerationSource
            .first()
        return accelerationSources.firstOrNull { it.id == selectedId } ?: accelerationSources.first()
    }

    private fun resolveGitLfsDisplaySizes(
        source: RepositorySource,
        tree: List<GiteeTreeItem>
    ): Map<String, Long> {
        val candidatePaths = tree.asSequence()
            .filter { it.type == "blob" }
            .filter { it.size in 1..GIT_LFS_POINTER_MAX_BYTES.toLong() }
            .map { normalizeRepositoryPath(it.path) }
            .filter { it.isNotEmpty() && isDocumentFile(it.substringAfterLast('/')) }
            .toList()

        if (candidatePaths.isEmpty()) return emptyMap()

        return candidatePaths.mapNotNull { repositoryPath ->
            val request = Request.Builder()
                .url(source.rawUrl(repositoryPath))
                .header("User-Agent", "MZtong-Android")
                .build()
            val size = runCatching {
                downloadClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    response.readGitLfsPointer()?.size
                }
            }.getOrNull()
            size?.let { repositoryPath to it }
        }.toMap()
    }

    private fun isDocumentFile(name: String): Boolean {
        val lower = name.lowercase()
        return lower.endsWith(".pdf") || lower.endsWith(".doc") ||
            lower.endsWith(".docx") || lower.endsWith(".ppt") ||
            lower.endsWith(".pptx") || lower.endsWith(".xls") ||
            lower.endsWith(".xlsx") || lower.endsWith(".txt") ||
            lower.endsWith(".md")
    }

    private fun Response.readGitLfsPointer(): GitLfsPointer? {
        val preview = runCatching {
            peekBody(GIT_LFS_POINTER_MAX_BYTES.toLong()).string()
        }.getOrNull() ?: return null
        return parseGitLfsPointer(preview)
    }

    private fun Response.readGitLfsMarkdown(
        source: RepositorySource,
        accelerationSource: RepositoryAccelerationSource
    ): String? {
        val pointer = readGitLfsPointer() ?: return null
        val download = resolveGitLfsDownload(source, pointer) ?: return null
        buildLfsDownloadUrls(download.href, accelerationSource).forEach { url ->
            val request = Request.Builder()
                .url(url)
                .apply {
                    download.header?.forEach { (key, value) -> header(key, value) }
                }
                .build()
            val result = downloadClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                response.body?.string()
            }
            if (result != null) return result
        }
        return null
    }

    private fun parseGitLfsPointer(content: String): GitLfsPointer? {
        val lines = content.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()
        if (lines.firstOrNull() != GIT_LFS_POINTER_PREFIX) return null

        val oid = lines.firstOrNull { it.startsWith("oid sha256:") }
            ?.removePrefix("oid sha256:")
            ?.trim()
            ?.takeIf { it.matches(Regex("^[0-9a-fA-F]{64}$")) }
            ?: return null
        val size = lines.firstOrNull { it.startsWith("size ") }
            ?.removePrefix("size ")
            ?.trim()
            ?.toLongOrNull()
            ?: return null
        return GitLfsPointer(oid = oid, size = size)
    }

    private fun resolveGitLfsDownload(
        source: RepositorySource,
        pointer: GitLfsPointer
    ): GitLfsDownloadAction? {
        val requestBody = Gson().toJson(
            GitLfsBatchRequest(
                objects = listOf(
                    GitLfsBatchObjectRequest(
                        oid = pointer.oid,
                        size = pointer.size
                    )
                )
            )
        ).toRequestBody(GIT_LFS_BATCH_MEDIA_TYPE)

        val request = Request.Builder()
            .url(source.lfsBatchUrl())
            .header("Accept", "application/vnd.git-lfs+json")
            .header("User-Agent", "MZtong-Android")
            .post(requestBody)
            .build()

        return downloadClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val batchResponse = runCatching {
                gson.fromJson(response.body?.string().orEmpty(), GitLfsBatchResponse::class.java)
            }.getOrNull()
            batchResponse?.objects?.firstOrNull { it.oid.equals(pointer.oid, ignoreCase = true) }
                ?.actions?.download
        }
    }

    private suspend fun downloadGitLfsFile(
        urls: List<String>,
        headers: Map<String, String>?,
        path: String,
        context: Context,
        target: DownloadTarget,
        previousRecord: DownloadRecord?,
        pointer: GitLfsPointer,
        onProgress: (Float) -> Unit
    ): DownloadedFile? = withContext(Dispatchers.IO) {
        urls.forEachIndexed { index, url ->
            runCatching {
                val request = Request.Builder().url(url).apply {
                    headers?.forEach { (key, value) -> header(key, value) }
                }.build()
                downloadClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    val body = response.body ?: return@use null
                    val total = body.contentLength().takeIf { it > 0L } ?: pointer.size
                    var downloadedBytes = 0L
                    body.byteStream().use { input ->
                        target.openOutputStream().use { output ->
                            val buffer = ByteArray(8 * 1024)
                            var read = input.read(buffer)
                            while (read >= 0) {
                                output.write(buffer, 0, read)
                                downloadedBytes += read
                                if (total > 0L) {
                                    onProgress(downloadedBytes.toFloat() / total.toFloat())
                                }
                                read = input.read(buffer)
                            }
                            output.flush()
                        }
                    }
                    target.markCompleted()
                    if (target is DownloadTarget.MediaStoreTarget || previousRecord?.uri != null) {
                        previousRecord?.delete(context)
                    }
                    removeDownloadRecord(path)
                    val record = DownloadRecord(
                        path = path,
                        localName = target.relativePath,
                        uri = target.uri?.toString(),
                        localPath = target.displayPath,
                        downloadTime = System.currentTimeMillis(),
                        size = downloadedBytes.takeIf { it > 0L } ?: pointer.size
                    )
                    saveDownloadRecord(record)
                    return@withContext record.toDownloadedFile(context)
                }
            }.getOrNull()?.let { return@withContext it }

            if (index == urls.lastIndex) {
                return@withContext null
            }
        }
        null
    }

    private fun buildLfsDownloadUrls(
        href: String,
        accelerationSource: RepositoryAccelerationSource
    ): List<String> {
        val proxied = accelerationSource.proxyPrefix?.let { it + href }
        return listOfNotNull(proxied, href).distinct()
    }

    private fun matchesCurrentRepositorySources(items: List<GitHubContentItem>): Boolean {
        val cachedRoots = items.map { listOf(it.path, it.name, it.htmlUrl.orEmpty()) }
        val expectedRoots = repositoryRootItems().map { listOf(it.path, it.name, it.htmlUrl.orEmpty()) }
        return cachedRoots == expectedRoots
    }

    private fun virtualPath(source: RepositorySource, repositoryPath: String): String {
        val normalizedPath = normalizeRepositoryPath(repositoryPath)
        return listOf(source.id, normalizedPath)
            .filter { it.isNotEmpty() }
            .joinToString("/")
    }

    private fun resolveVirtualPath(path: String): ResolvedRepositoryPath? {
        val normalizedPath = normalizeRepositoryPath(path)
        if (normalizedPath.isEmpty()) return null
        val repositoryId = normalizedPath.substringBefore('/')
        val source = repositorySourceById[repositoryId] ?: return null
        val repositoryPath = normalizedPath.substringAfter('/', "")
        return ResolvedRepositoryPath(source, repositoryPath)
    }

    private fun repositoryRootUrl(): String {
        val rootSource = repositorySources.first()
        return "$GITEE_HOST/${rootSource.owner}/${rootSource.repo}"
    }

    private suspend fun resolveRepositoryTreeSha(source: RepositorySource): String {
        val branch = GiteeApi.instance.getBranch(
            owner = source.owner,
            repo = source.repo,
            branch = source.branch
        )
        return branch.commit?.sha?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("无法解析仓库树")
    }

    private fun getUnsupportedDirectoryPaths(): Set<String> {
        val json = kv.decodeString(CONTENT_UNSUPPORTED_PATHS_KEY, "[]") ?: "[]"
        return try {
            val type = object : TypeToken<List<String>>() {}.type
            gson.fromJson<List<String>>(json, type).toSet()
        } catch (e: Exception) {
            emptySet()
        }
    }

    private fun saveUnsupportedDirectoryPaths(paths: Set<String>) {
        kv.encode(CONTENT_UNSUPPORTED_PATHS_KEY, gson.toJson(paths.sorted()))
    }

    private fun normalizeRepositoryPath(path: String): String {
        return path.replace('\\', '/').trim('/')
    }

    private data class MaterialsTreeSnapshot(
        val generatedAt: String? = null,
        val branch: String? = null,
        val entries: List<GiteeTreeItem> = emptyList()
    )

    /**
     * 读取 App 内置的学习资料目录快照（assets/materials_tree_snapshot.json）。
     *
     * 快照由 tools/generate_materials_snapshot.ps1 生成，只用于 Gitee API 被限流或断网时兜底。
     */
    private fun loadMaterialsTreeSnapshot(): List<GiteeTreeItem> {
        return runCatching {
            val json = AHUApplication.getApp().assets
                .open("materials_tree_snapshot.json")
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }
            val type = object : TypeToken<MaterialsTreeSnapshot>() {}.type
            val snapshot = gson.fromJson<MaterialsTreeSnapshot>(json, type)
            snapshot?.entries.orEmpty().mapNotNull { item ->
                val path = item.path.trim('/')
                if (path.isEmpty()) null else GiteeTreeItem(path = path, type = item.type, size = item.size)
            }
        }.getOrDefault(emptyList())
    }

    private fun encodePath(path: String): String {
        if (path.isEmpty()) return ""
        return path.split('/').joinToString("/") { Uri.encode(it) }
    }

    private fun contentCacheKey(path: String): String {
        return Base64.encodeToString(
            path.toByteArray(Charsets.UTF_8),
            Base64.NO_WRAP or Base64.URL_SAFE
        )
    }

    private data class DownloadRecord(
        val path: String,
        val localName: String,
        val uri: String? = null,
        val localPath: String? = null,
        val downloadTime: Long = 0L,
        val size: Long = 0L
    ) {
        fun toDownloadedFile(context: Context): DownloadedFile? {
            val uriValue = uri?.let { Uri.parse(it) }
            if (uriValue != null) {
                return DownloadedFile(
                    name = File(path).name,
                    path = path,
                    localPath = localPath ?: localName,
                    size = size.takeIf { it > 0L } ?: querySize(uriValue),
                    downloadTime = downloadTime,
                    uri = uri
                )
            }

            val file = File(localPath ?: File(getLegacyDownloadDir(context), localName).absolutePath)
            if (!file.exists()) return null
            return DownloadedFile(
                name = File(path).name,
                path = path,
                localPath = file.absolutePath,
                size = size.takeIf { it > 0L } ?: file.length(),
                downloadTime = if (downloadTime > 0) downloadTime else file.lastModified()
            )
        }

        fun delete(context: Context): Boolean {
            val uriValue = uri?.let { Uri.parse(it) }
            if (uriValue != null) {
                return context.contentResolver.delete(uriValue, null, null) > 0
            }

            val file = File(localPath ?: File(getLegacyDownloadDir(context), localName).absolutePath)
            return !file.exists() || file.delete()
        }

        private fun querySize(uri: Uri): Long {
            return runCatching {
                val context = AHUApplication.getApp()
                context.contentResolver.query(
                    uri,
                    arrayOf(OpenableColumns.SIZE),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (sizeIndex >= 0) cursor.getLong(sizeIndex) else 0L
                    } else {
                        0L
                    }
                } ?: 0L
            }.getOrDefault(0L)
        }
    }

    private sealed class DownloadTarget(
        val relativePath: String,
        val displayPath: String,
        val uri: Uri?
    ) {
        abstract fun openOutputStream(): OutputStream
        open fun markCompleted() = Unit
        abstract fun delete()

        class MediaStoreTarget(
            private val context: Context,
            uri: Uri,
            relativePath: String,
            displayPath: String
        ) : DownloadTarget(relativePath, displayPath, uri) {
            override fun openOutputStream(): OutputStream {
                return context.contentResolver.openOutputStream(uri!!)
                    ?: throw IllegalStateException("无法写入下载文件")
            }

            override fun markCompleted() {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.IS_PENDING, 0)
                    }
                    context.contentResolver.update(uri!!, values, null, null)
                }
            }

            override fun delete() {
                context.contentResolver.delete(uri!!, null, null)
            }
        }

        class FileTarget(
            private val file: File,
            relativePath: String
        ) : DownloadTarget(relativePath, file.absolutePath, null) {
            private val tempFile = File(file.parentFile, "${file.name}.downloading")

            override fun openOutputStream(): OutputStream {
                tempFile.parentFile?.mkdirs()
                return FileOutputStream(tempFile)
            }

            override fun markCompleted() {
                if (file.exists()) {
                    file.delete()
                }
                if (!tempFile.renameTo(file)) {
                    tempFile.copyTo(file, overwrite = true)
                    tempFile.delete()
                }
            }

            override fun delete() {
                tempFile.delete()
            }
        }
    }

    private data class RepositorySource(
        val id: String,
        val title: String,
        val pathPrefix: String = "",
        val owner: String = SchoolConfig.RESOURCE_REPOSITORY_OWNER,
        val repo: String = SchoolConfig.RESOURCE_REPOSITORY_NAME,
        val branch: String = SchoolConfig.RESOURCE_REPOSITORY_BRANCH
    ) {
        /** 拼出仓库内完整路径（含学院目录前缀），并逐段做 URL 编码。 */
        private fun fullRepoPath(repositoryPath: String): String {
            val normalizedPrefix = pathPrefix.trim('/')
            val normalizedPath = repositoryPath.trim('/')
            return encodePath(
                listOf(normalizedPrefix, normalizedPath)
                    .filter { it.isNotEmpty() }
                    .joinToString("/")
            )
        }

        /** Gitee 原始文件直链：https://gitee.com/{owner}/{repo}/raw/{branch}/{path} */
        fun rawUrl(repositoryPath: String): String {
            return "$GITEE_HOST/$owner/$repo/raw/$branch/${fullRepoPath(repositoryPath)}"
        }

        /** Gitee 网页链接：目录用 tree，文件用 blob。 */
        fun webUrl(repositoryPath: String, tree: Boolean): String {
            val kind = if (tree) "tree" else "blob"
            val encodedPath = fullRepoPath(repositoryPath)
            return if (encodedPath.isEmpty()) {
                "$GITEE_HOST/$owner/$repo/tree/$branch"
            } else {
                "$GITEE_HOST/$owner/$repo/$kind/$branch/$encodedPath"
            }
        }

        fun lfsBatchUrl(): String {
            return "$GITEE_HOST/$owner/$repo.git/info/lfs/objects/batch"
        }

        /** 只保留本学院目录下的节点，并把路径裁剪成相对本学院的路径。 */
        fun filterTree(tree: List<GiteeTreeItem>): List<GiteeTreeItem> {
            val prefix = pathPrefix.trim('/')
            if (prefix.isEmpty()) return tree
            return tree.mapNotNull { item ->
                val path = item.path.trim('/')
                when {
                    path == prefix -> null
                    path.startsWith("$prefix/") -> item.copy(path = path.removePrefix("$prefix/"))
                    else -> null
                }
            }
        }
    }

    private data class ResolvedRepositoryPath(
        val source: RepositorySource,
        val repositoryPath: String
    )

    private data class RepositoryIndexCache(
        val grouped: Map<String, List<GitHubContentItem>>,
        val unsupportedDirectoryPaths: Set<String>,
        val documentFileCount: Int
    )

    private data class GitLfsPointer(
        val oid: String,
        val size: Long
    )

    private data class GitLfsBatchRequest(
        val operation: String = "download",
        val transfers: List<String> = listOf("basic"),
        val objects: List<GitLfsBatchObjectRequest>
    )

    private data class GitLfsBatchObjectRequest(
        val oid: String,
        val size: Long
    )

    private data class GitLfsBatchResponse(
        val objects: List<GitLfsBatchObjectResponse> = emptyList()
    )

    private data class GitLfsBatchObjectResponse(
        val oid: String,
        val size: Long = 0,
        val actions: GitLfsBatchActions? = null
    )

    private data class GitLfsBatchActions(
        val download: GitLfsDownloadAction? = null
    )

    private data class GitLfsDownloadAction(
        val href: String,
        val header: Map<String, String>? = null
    )
}
