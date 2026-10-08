package app.reporove.data

import android.app.DownloadManager
import android.content.Context
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.content.ContentUris
import android.system.Os
import android.system.OsConstants
import android.system.ErrnoException
import app.reporove.core.model.Asset
import app.reporove.core.model.Release
import app.reporove.core.model.DownloadRecord
import app.reporove.core.storage.LocalStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay
import java.io.File

data class DownloadProgress(val status: Int, val bytes: Long, val total: Long, val reason: Int, val localUri: String? = null)

class Downloads(private val context: Context, private val store: LocalStore) {
    private val manager = context.getSystemService(DownloadManager::class.java)
    suspend fun enqueue(fullName: String, asset: Asset): Long {
        require(asset.id > 0) { "附件编号无效，请刷新版本。" }
        return enqueueFile(fullName, asset.name, "https://api.github.com/repos/$fullName/releases/assets/${asset.id}", asset.contentType, binaryAsset = true)
    }

    suspend fun enqueueArchive(fullName: String, release: Release, tar: Boolean): Long {
        val url = (if (tar) release.tarballUrl else release.zipballUrl) ?: throw IllegalArgumentException("此版本没有源码归档。")
        val name = "${fullName.substringAfter('/')}-${release.tag}.${if (tar) "tar.gz" else "zip"}"
        return enqueueFile(fullName, name, url, if (tar) "application/gzip" else "application/zip")
    }

    private suspend fun enqueueFile(fullName: String, name: String, url: String, mime: String, binaryAsset: Boolean = false): Long = withContext(Dispatchers.IO) {
        require(fullName.matches(Regex("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")) && fullName.split('/').none { it == "." || it == ".." }) { "仓库名称无效。" }
        val uri = Uri.parse(url)
        val archive = uri.host == "api.github.com" && (uri.path.orEmpty().startsWith("/repos/$fullName/zipball/") || uri.path.orEmpty().startsWith("/repos/$fullName/tarball/"))
        val attachment = binaryAsset && uri.host == "api.github.com" && uri.path.orEmpty().matches(Regex("/repos/${Regex.escape(fullName)}/releases/assets/[1-9][0-9]*"))
        require(uri.scheme == "https" && (archive || attachment)) { "下载地址不受支持。" }
        val safeName = name.replace(Regex("[^\\p{L}\\p{N}._-]"), "_").take(160).ifBlank { "download" }
        val createdAt = System.currentTimeMillis()
        val fileName = "$createdAt-$safeName"
        val request = DownloadManager.Request(uri)
            .setTitle(name).setDescription(fullName).setMimeType(mime)
            .addRequestHeader("User-Agent", "RepoRove-Android")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "RepoRove/$fileName")
        if (binaryAsset) request.addRequestHeader("Accept", "application/octet-stream")
        val id = manager.enqueue(request)
        try { store.addDownload(DownloadRecord(id, name, fullName, createdAt, fileName)) }
        catch (error: Exception) { manager.remove(id); throw error }
        id
    }

    suspend fun progress(id: Long): DownloadProgress? = withContext(Dispatchers.IO) {
        manager.query(DownloadManager.Query().setFilterById(id)).use { cursor ->
            if (!cursor.moveToFirst()) return@withContext null
            DownloadProgress(cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)), cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)), cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)), cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)), cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI)))
        }
    }

    fun open(id: Long) {
        val uri = manager.getUriForDownloadedFile(id) ?: throw IllegalArgumentException("下载文件已被移除。")
        try { context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, manager.getMimeTypeForDownloadedFile(id) ?: "application/octet-stream")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)) }
        catch (_: ActivityNotFoundException) { throw IllegalArgumentException("没有可以打开此文件的应用，请在 Downloads/RepoRove 中查看。") }
    }

    /** Delete only a recorded file in our download directory. Keep the record if deletion cannot be verified. */
    suspend fun remove(id: Long) = withContext(Dispatchers.IO) {
        val record = store.downloads.first().firstOrNull { it.id == id } ?: throw IllegalArgumentException("下载记录不存在。")
        require(Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED) { "下载存储暂不可用，文件和记录已保留，请稍后重试。" }
        val before = progress(id)
        val directory = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "RepoRove").canonicalFile
        // Upgrade old records before removing the system row, which holds the only known file location.
        val currentFile = before?.localUri?.let(Uri::parse)?.takeIf { it.scheme == "file" }?.path?.let { File(it).canonicalFile }
        require(currentFile == null || currentFile.parentFile == directory) { "文件已移动到其他位置，请在文件管理器中处理。记录已保留。" }
        val fileName = currentFile?.name ?: record.fileName
        val file = fileName?.let {
            require(it.isNotBlank() && it == File(it).name) { "下载文件位置无效，记录已保留。" }
            File(directory, it).canonicalFile.also { path -> require(path.parentFile == directory) { "下载文件位置无效，记录已保留。" } }
        } ?: throw IllegalArgumentException("系统已丢失文件位置，无法确认删除。请在 Downloads/RepoRove 中处理文件，再移除记录。")
        if (record.fileName != file.name) store.rememberDownloadFile(id, file.name)
        val media = ownedMedia(file.name)
        val removed = manager.remove(id)
        require(before == null || removed > 0) { "系统未能取消或移除下载，记录已保留，请重试。" }
        require(progress(id) == null) { "系统下载仍在运行，记录已保留，请重试。" }
        // A cancelled worker can create its file/MediaStore entry after the system row disappears.
        // Refresh ownership during cleanup, and require stable absence for an active task.
        // Never infer absence from File.exists() on scoped storage.
        val wasActive = before?.status in listOf(DownloadManager.STATUS_PENDING, DownloadManager.STATUS_RUNNING, DownloadManager.STATUS_PAUSED)
        var absentSince: Long? = null
        repeat(30) {
            if (filePresent(file)) {
                (ownedMedia(file.name) ?: media)?.let { context.contentResolver.delete(it, null, null) }
                if (filePresent(file)) file.delete()
            }
            if (!filePresent(file)) {
                val now = android.os.SystemClock.elapsedRealtime()
                absentSince = absentSince ?: now
                if (!wasActive || now - absentSince!! >= 1000) { store.removeDownload(id); return@withContext }
            } else absentSince = null
            delay(100)
        }
        throw IllegalArgumentException("文件仍在 Downloads/RepoRove 中，删除未完成，记录已保留。请重试或在文件管理器中删除。")
    }

    /** Explicit history cleanup; callers must tell the user that this does not delete a disk file. */
    suspend fun forget(id: Long) {
        require(progress(id) == null) { "下载仍由系统管理，请使用删除文件或取消下载。" }
        store.removeDownload(id)
    }

    private fun filePresent(file: File): Boolean = try { Os.stat(file.absolutePath); true }
    catch (error: ErrnoException) {
        if (error.errno == OsConstants.ENOENT) false
        else throw IllegalArgumentException("无法确认本机文件状态，记录已保留，请在文件管理器中检查。", error)
    }

    private fun ownedMedia(fileName: String): Uri? {
        val columns = arrayOf(MediaStore.Downloads._ID, MediaStore.Downloads.OWNER_PACKAGE_NAME)
        context.contentResolver.query(MediaStore.Downloads.EXTERNAL_CONTENT_URI, columns,
            "${MediaStore.Downloads.DISPLAY_NAME}=? AND ${MediaStore.Downloads.RELATIVE_PATH}=?", arrayOf(fileName, "Download/RepoRove/"), null)?.use { cursor ->
            while (cursor.moveToNext()) if (cursor.getString(1) == context.packageName) return ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cursor.getLong(0))
        }
        return null
    }
}
