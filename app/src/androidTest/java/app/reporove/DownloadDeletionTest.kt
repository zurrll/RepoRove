package app.reporove

import android.app.DownloadManager
import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import app.reporove.core.model.DownloadRecord
import app.reporove.core.storage.LocalStore
import app.reporove.data.Downloads
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit

/** Verifies disk files as well as history against the actual Android storage/download providers. */
class DownloadDeletionTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val store = LocalStore(context)
    private val downloads = Downloads(context, store)
    private val directory = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "RepoRove")

    @Test fun missingSystemRowWithOwnedFileDeletesDiskAndHistory() = runBlocking {
        val name = "reporove-orphan-${System.nanoTime()}.txt"
        val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name); put(MediaStore.Downloads.RELATIVE_PATH, "Download/RepoRove/"); put(MediaStore.Downloads.MIME_TYPE, "text/plain")
        })!!
        val id = 900000001L
        try {
            context.contentResolver.openOutputStream(uri)!!.use { it.write("owned test file".toByteArray()) }
            assertTrue(File(directory, name).isFile)
            store.addDownload(DownloadRecord(id, name, "test/fixture", 0, name))
            assertNull(downloads.progress(id))
            downloads.remove(id)
            assertFalse(File(directory, name).exists())
            assertTrue(store.downloads.first().none { it.id == id })
        } finally { context.contentResolver.delete(uri, null, null); store.removeDownload(id) }
    }

    @Test fun deletedFileCanClearItsRecordWithoutTouchingOthers() = runBlocking {
        val id = 900000002L
        store.addDownload(DownloadRecord(id, "missing.txt", "test/fixture", 0, "reporove-absent-${System.nanoTime()}.txt"))
        try { downloads.remove(id); assertTrue(store.downloads.first().none { it.id == id }) }
        finally { store.removeDownload(id) }
    }

    @Test fun missingFileLocationAndInvalidPathKeepHistoryOnFailure() = runBlocking {
        for (record in listOf(DownloadRecord(900000003L, "legacy.txt", "test/fixture", 0), DownloadRecord(900000004L, "outside.txt", "test/fixture", 0, "../outside.txt"))) {
            store.addDownload(record)
            try {
                assertTrue(runCatching { downloads.remove(record.id) }.isFailure)
                assertTrue(store.downloads.first().any { it.id == record.id })
                downloads.forget(record.id)
                assertTrue(store.downloads.first().none { it.id == record.id })
            } finally { store.removeDownload(record.id) }
        }
    }

    @Test fun cancellingActiveDownloadRemovesPartialFileAndHistory() = runBlocking {
        repeat(3) { attempt -> MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("x".repeat(100000)).throttleBody(1024, 1, TimeUnit.SECONDS))
            server.start()
            val name = "reporove-cancel-${System.nanoTime()}.txt"
            val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val id = manager.enqueue(DownloadManager.Request(Uri.parse(server.url("/slow").toString())).setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "RepoRove/$name"))
            store.addDownload(DownloadRecord(id, name, "test/fixture", 0, name))
            try {
                withTimeout(15000) {
                    while (true) {
                        val progress = downloads.progress(id)
                        if (progress?.status == DownloadManager.STATUS_RUNNING && (attempt == 0 || File(directory, name).length() > 0)) break
                        delay(100)
                    }
                }
                downloads.remove(id)
                delay(500)
                assertNull(downloads.progress(id))
                assertFalse(File(directory, name).exists())
                assertTrue(store.downloads.first().none { it.id == id })
            } finally { manager.remove(id); File(directory, name).delete(); store.removeDownload(id) }
        } }
    }
}
