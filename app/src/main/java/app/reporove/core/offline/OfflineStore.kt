package app.reporove.core.offline

import app.reporove.core.model.*
import app.reporove.core.network.AppJson

import app.reporove.data.GitHubRepository
import app.reporove.core.reader.ReaderDocument
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import org.jsoup.Jsoup
import retrofit2.HttpException
import app.reporove.core.storage.readBounded
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID

@Serializable data class OfflineSnapshot(
    val id: String, val scope: String, val repository: Repository, val ref: String, val sha: String,
    val savedAt: Long, val documents: Boolean, val code: Boolean,
    val readmePath: String? = null, val readme: String? = null, val readmeHtml: String? = null,
    val languages: Map<String, Long> = emptyMap(), val entries: List<ArchiveEntry> = emptyList(),
    val images: Map<String, String> = emptyMap(), val missingImages: Int = 0,
    val bytes: Long = 0,
)
@Serializable data class SaveRequest(val fullName: String, val ref: String, val documents: Boolean, val code: Boolean)
@Serializable data class SaveProgress(val request: SaveRequest, val stage: String, val bytes: Long = 0, val running: Boolean = false, val error: String? = null, val scope: String = "public")

/** Explicit snapshots live in filesDir; cache eviction never deletes them. */
class OfflineStore(private val root: File, private val repository: GitHubRepository) {
    val ready = CompletableDeferred<Unit>()
    private val mutex = Mutex()
    @Volatile private var generation = 0L
    private val mutable = MutableStateFlow<List<OfflineSnapshot>>(emptyList())
    val snapshots = mutable.asStateFlow()
    private val progressState = MutableStateFlow<Map<String, SaveProgress>>(emptyMap())
    val progress = progressState.asStateFlow()

    suspend fun initialize() = withContext(Dispatchers.IO) { mutex.withLock {
        root.mkdirs()
        root.listFiles()?.filter { it.name.startsWith(".partial-") }?.forEach { it.deleteRecursively() }
        // If credentials were lost or invalidated while the app wasn't running, purge orphan private files.
        root.listFiles().orEmpty().filter { it.isDirectory && !it.name.startsWith('.') }.forEach { dir ->
            val old = runCatching { AppJson.decodeFromString<OfflineSnapshot>(File(dir, "manifest.json").readText()) }.getOrNull()
            if (old?.repository?.isPrivate == true && old.scope != repository.scope) check(dir.deleteRecursively()) { "无法清理旧账号快照。" }
        }
        loadLocked()
        ready.complete(Unit)
    } }
    private fun loadLocked() {
        mutable.value = root.listFiles().orEmpty().filter { it.isDirectory && !it.name.startsWith('.') }.mapNotNull { dir ->
            runCatching { AppJson.decodeFromString<OfflineSnapshot>(File(dir, "manifest.json").readText()) }.getOrNull()?.takeIf { it.id == dir.name && (!it.repository.isPrivate || it.scope == repository.scope) }
        }.sortedByDescending { it.savedAt }
        val tasks = runCatching { AppJson.decodeFromString<Map<String, SaveProgress>>(File(root, "tasks.json").readText()) }.getOrDefault(emptyMap())
        progressState.value = tasks.filterValues { it.scope == repository.scope }.mapValues { (_, value) -> if (value.running) value.copy(running = false, stage = "保存中断", error = "上次保存中断，可重试；已有快照仍可阅读。") else value }
    }
    fun find(id: String) = mutable.value.firstOrNull { it.id == id && (!it.repository.isPrivate || it.scope == repository.scope) }
    private fun directory(snapshot: OfflineSnapshot): File {
        require(snapshot.id.matches(Regex("[a-f0-9-]{36}")))
        require(!snapshot.repository.isPrivate || snapshot.scope == repository.scope) { "当前账号不能读取这个快照。" }
        return File(root, snapshot.id)
    }
    suspend fun delete(id: String) = withContext(Dispatchers.IO) { mutex.withLock {
        val snapshot = find(id) ?: return@withLock
        check(progressState.value[snapshot.repository.fullName]?.running != true) { "请先取消这个仓库正在进行的保存。" }
        val dir = directory(snapshot)
        check(dir.deleteRecursively() && !dir.exists()) { "文件删除未完成，记录已保留。" }
        mutable.value = mutable.value.filterNot { it.id == id }
        if (mutable.value.none { it.repository.fullName == snapshot.repository.fullName }) {
            progressState.value = progressState.value - snapshot.repository.fullName
            atomic(File(root, "tasks.json"), AppJson.encodeToString(progressState.value))
        }
    } }
    suspend fun clearPrivate() = withContext(Dispatchers.IO) { mutex.withLock {
        generation++
        root.listFiles().orEmpty().filter { it.isDirectory }.forEach { dir ->
            val manifest = runCatching { AppJson.decodeFromString<OfflineSnapshot>(File(dir, "manifest.json").readText()) }.getOrNull()
            if (dir.name.startsWith(".partial-") || manifest?.repository?.isPrivate == true) check(dir.deleteRecursively()) { "私有离线文件清理失败。" }
        }
        mutable.value = mutable.value.filterNot { it.repository.isPrivate }
        progressState.value = emptyMap(); File(root, "tasks.json").delete()
    } }
    private suspend fun status(key: String, value: SaveProgress, stillActive: () -> Boolean = { true }) = withContext(Dispatchers.IO) { mutex.withLock {
        if (!stillActive()) return@withLock
        progressState.value = progressState.value + (key to value)
        atomic(File(root, "tasks.json"), AppJson.encodeToString(progressState.value))
    } }
    suspend fun dismissTask(key: String) = withContext(Dispatchers.IO) { mutex.withLock {
        progressState.value = progressState.value - key
        atomic(File(root, "tasks.json"), AppJson.encodeToString(progressState.value))
    } }

    suspend fun save(request: SaveRequest) = withContext(Dispatchers.IO) {
        require(request.documents || request.code) { "至少选择一项。" }
        val bound = repository.authenticatedSource(); val capturedGeneration = generation
        fun active() { check(bound.active() && capturedGeneration == generation) { "账号状态已变化，保存已停止。" } }
        suspend fun report(value: SaveProgress) { active(); status(request.fullName, value.copy(scope = bound.scope)) { bound.active() && generation == capturedGeneration } }
        val id = UUID.randomUUID().toString()
        val staging = File(root, ".partial-$id").apply { mkdirs() }
        val key = request.fullName
        try {
            report(SaveProgress(request, "读取仓库与提交", running = true))
            val parts = request.fullName.split('/'); require(parts.size == 2)
            val api = bound.api; val owner = parts[0]; val name = parts[1]
            val repo = api.repository(owner, name); active()
            val sha = api.commit(owner, name, request.ref).sha
            require(sha.matches(Regex("[a-fA-F0-9]{40}"))) { "无法取得固定提交。" }
            var readme: String? = null; var html: String? = null; var readmePath: String? = null
            var languages = emptyMap<String, Long>()
            val images = mutableMapOf<String, String>(); var missing = 0
            if (request.documents) {
                report(SaveProgress(request, "保存仓库资料", running = true))
                languages = api.languages(owner, name)
                val content = try { api.readme(owner, name, sha) } catch (e: HttpException) { if (e.code() == 404) null else throw e }
                content?.let {
                    readme = GitHubRepository.text(it); readmePath = it.path
                    html = try { api.markdown(MarkdownRequest(readme!!, context = repo.fullName)).use { body -> body.string() } }
                    catch (e: CancellationException) { throw e } catch (_: Exception) { ReaderDocument.localMarkdown(readme!!) }
                }
            }
            var entries = emptyList<ArchiveEntry>()
            if (request.code) {
                require(root.usableSpace > SafeArchive.MAX_ARCHIVE + 32 * 1024 * 1024) { "可用空间不足，请先腾出至少 232 MiB。" }
                report(SaveProgress(request, "下载源码", running = true))
                api.archive(owner, name, sha).use { body ->
                    require(body.contentLength() <= SafeArchive.MAX_ARCHIVE) { "源码归档超过 200 MiB。" }
                    body.byteStream().use { input -> File(staging, "source.zip").outputStream().use { output ->
                        val buffer = ByteArray(32 * 1024); var bytes = 0L; var reported = 0L
                        while (true) {
                            coroutineContext.ensureActive(); active()
                            val n = input.read(buffer); if (n < 0) break
                            bytes += n; require(bytes <= SafeArchive.MAX_ARCHIVE) { "源码归档超过 200 MiB。" }
                            output.write(buffer, 0, n)
                            if (bytes - reported > 512 * 1024) { reported = bytes; report(SaveProgress(request, "下载源码", bytes, true)) }
                        }
                    } }
                }
                report(SaveProgress(request, "校验源码与目录", running = true))
                entries = SafeArchive.index(File(staging, "source.zip")); active()
            }
            // Only same-repository relative images. Third-party hosts aren't downloaded implicitly.
            if (html != null) {
                report(SaveProgress(request, "保存 README 图片", running = true))
                val base = "https://github.com/${repo.fullName}/blob/$sha/${readmePath ?: "README.md"}"
                val paths = Jsoup.parse(html, base).select("img[src]").mapNotNull { img ->
                    val resolved = app.reporove.core.reader.MarkdownLinks.resolve(img.attr("src"), repo.fullName, sha, readmePath ?: "README.md", true)
                    val uri = java.net.URI(resolved)
                    val prefix = "/${repo.fullName}/$sha/"
                    uri.path?.takeIf { uri.host == "raw.githubusercontent.com" && it.startsWith(prefix) }?.removePrefix(prefix)
                }.distinct()
                var imageBytes = 0L
                paths.forEachIndexed { index, path ->
                    coroutineContext.ensureActive(); active()
                    if (index >= 30 || !SafeArchive.validPath(path)) { missing++; return@forEachIndexed }
                    try {
                        val data = entries.firstOrNull { it.path == path && !it.directory }?.let { SafeArchive.bytes(File(staging, "source.zip"), it, 5 * 1024 * 1024) }
                            ?: api.image(owner, name, GitHubRepository.encodePath(path), sha).use { it.byteStream().use { stream -> stream.readBounded(5 * 1024 * 1024) } }
                        require(data.size <= 5 * 1024 * 1024 && imageBytes + data.size <= 20 * 1024 * 1024)
                        imageBytes += data.size; val filename = "image-${digest(path)}"
                        File(staging, filename).writeBytes(data); images[path] = filename
                    } catch (e: CancellationException) { throw e } catch (_: Exception) { missing++ }
                }
            }
            active()
            val snapshot = OfflineSnapshot(id, if (repo.isPrivate) bound.scope else "public", repo, request.ref, sha, System.currentTimeMillis(), request.documents, request.code, readmePath, readme, html, languages, entries, images, missing, staging.walkTopDown().filter { it.isFile }.sumOf(File::length))
            mutex.withLock {
                active()
                atomic(File(staging, "manifest.json"), AppJson.encodeToString(snapshot))
                Files.move(staging.toPath(), File(root, id).toPath(), StandardCopyOption.ATOMIC_MOVE)
                val old = mutable.value.filter { it.repository.fullName == repo.fullName && it.scope == snapshot.scope }
                mutable.value = listOf(snapshot) + mutable.value.filterNot { it in old }
                val retained = old.filter { val dir = directory(it); !dir.deleteRecursively() || dir.exists() }
                mutable.value = mutable.value + retained
            }
            report(SaveProgress(request, "已保存", snapshot.bytes))
        } catch (e: CancellationException) {
            if (capturedGeneration == generation) withContext(NonCancellable) { report(SaveProgress(request, "已取消", error = "保存已取消，已有快照仍可阅读。")) }; throw e
        } catch (e: Exception) {
            if (capturedGeneration == generation) report(SaveProgress(request, "保存失败", error = app.reporove.core.network.userMessage(e)))
        } finally { staging.deleteRecursively() }
    }

    suspend fun contents(id: String, path: String): List<Content> = withContext(Dispatchers.IO) {
        val snapshot = find(id) ?: error("离线快照不存在。")
        require(path.isEmpty() || SafeArchive.validPath(path))
        val file = snapshot.entries.firstOrNull { it.path == path && !it.directory }
        if (file != null) {
            val bytes = SafeArchive.bytes(File(directory(snapshot), "source.zip"), file)
            listOf(Content(path.substringAfterLast('/'), path, "file", bytes.size.toLong(), Base64.getEncoder().encodeToString(bytes), "base64"))
        } else {
            require(path.isEmpty() || snapshot.entries.any { it.path == path && it.directory }) { "未保存这个路径。" }
            snapshot.entries.filter { it.path.substringBeforeLast('/', "") == path }.map { Content(it.path.substringAfterLast('/'), it.path, if (it.directory) "dir" else "file", it.size) }
        }
    }
    fun image(id: String, path: String): ByteArray? {
        val snapshot = find(id) ?: return null; val dir = directory(snapshot)
        snapshot.images[path]?.takeIf { it.matches(Regex("image-[a-f0-9]{64}")) }?.let { return File(dir, it).takeIf { it.length() <= 5 * 1024 * 1024 }?.readBytes() }
        return snapshot.entries.firstOrNull { it.path == path && !it.directory }?.let { runCatching { SafeArchive.bytes(File(dir, "source.zip"), it, 5 * 1024 * 1024) }.getOrNull() }
    }
    companion object {
        fun digest(text: String) = MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
        fun atomic(file: File, text: String) {
            file.parentFile?.mkdirs(); val temp = File(file.parentFile, file.name + ".tmp")
            temp.writeText(text); Files.move(temp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
