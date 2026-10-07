package app.reporove.core.offline

import kotlinx.coroutines.ensureActive
import kotlinx.serialization.Serializable
import app.reporove.core.storage.readBounded
import java.io.File
import java.util.zip.ZipFile
import java.util.zip.CRC32
import kotlin.coroutines.coroutineContext

@Serializable data class ArchiveEntry(val path: String, val zipName: String, val size: Long, val directory: Boolean)

/** Never extract archive names into filesystem paths. Validate even entries that aren't previewed. */
object SafeArchive {
    const val MAX_ARCHIVE = 200L * 1024 * 1024
    const val MAX_EXPANDED = 500L * 1024 * 1024
    const val MAX_TEXT = 1024 * 1024
    fun validPath(path: String): Boolean = path.isNotEmpty() && path.length <= 4096 && !path.startsWith('/') && !path.contains('\\') && !path.contains('\u0000') && !path.contains(':') && path.trimEnd('/').split('/').none { it in listOf("", ".", "..") }

    suspend fun index(file: File): List<ArchiveEntry> {
        require(file.length() <= MAX_ARCHIVE) { "源码归档超过 200 MiB。" }
        return ZipFile(file).use { zip ->
            val result = linkedMapOf<String, ArchiveEntry>()
            var total = 0L
            var root: String? = null
            val buffer = ByteArray(32 * 1024)
            zip.entries().asSequence().forEachIndexed { count, entry ->
                coroutineContext.ensureActive()
                require(count < 50_000) { "归档超过 5 万个条目。" }
                require(validPath(entry.name)) { "归档包含无效路径。" }
                val first = entry.name.substringBefore('/')
                if (root == null) root = first
                require(first == root && (entry.name.contains('/') || entry.isDirectory)) { "归档目录结构无效。" }
                val path = entry.name.substringAfter('/', "").trimEnd('/')
                if (path.isEmpty()) return@forEachIndexed
                require(!result.containsKey(path)) { "归档包含重复路径。" }
                var size = 0L
                val checksum = CRC32()
                if (!entry.isDirectory) zip.getInputStream(entry).use { stream ->
                    while (true) {
                        coroutineContext.ensureActive()
                        val n = stream.read(buffer); if (n < 0) break
                        size += n; total += n; checksum.update(buffer, 0, n)
                        require(size <= 100L * 1024 * 1024 && total <= MAX_EXPANDED) { "源码展开过大，未保存。" }
                    }
                }
                require(entry.isDirectory || entry.crc < 0 || checksum.value == entry.crc) { "源码校验失败。" }
                require(entry.size < 0 || entry.size == size || entry.isDirectory) { "归档文件不完整。" }
                result[path] = ArchiveEntry(path, entry.name, size, entry.isDirectory)
            }
            // Some ZIP producers omit directory entries; build the parent structure from file paths.
            result.values.toList().forEach { entry ->
                var parent = entry.path.substringBeforeLast('/', "")
                while (parent.isNotEmpty()) {
                    require(result[parent]?.directory != false) { "归档路径冲突。" }
                    if (parent !in result) {
                        require(result.size < 50_000) { "归档目录索引超过 5 万个条目。" }
                        result[parent] = ArchiveEntry(parent, "", 0, true)
                    }
                    parent = parent.substringBeforeLast('/', "")
                }
            }
            result.values.sortedBy { it.path }
        }
    }

    fun bytes(file: File, entry: ArchiveEntry, limit: Int = MAX_TEXT): ByteArray {
        require(!entry.directory && entry.size <= limit) { "文件超过 ${limit / 1024} KiB，暂不能预览。" }
        require(validPath(entry.zipName)) { "文件路径无效。" }
        return ZipFile(file).use { zip ->
            val item = zip.getEntry(entry.zipName) ?: error("本地源码文件缺失，请重新保存。")
            zip.getInputStream(item).use { input ->
                val bytes = input.readBounded(limit)
                require(bytes.size <= limit && bytes.size.toLong() == entry.size) { "本地文件大小不一致。" }
                val checksum = CRC32().apply { update(bytes) }
                require(item.crc < 0 || item.crc == checksum.value) { "本地文件校验失败，请重新保存。" }
                bytes
            }
        }
    }
}
