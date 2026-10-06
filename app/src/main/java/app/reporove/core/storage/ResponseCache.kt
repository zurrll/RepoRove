package app.reporove.core.storage

import app.reporove.core.model.Loaded
import app.reporove.core.network.AppJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonElement
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

@Serializable private data class Envelope(val savedAt: Long, val payload: JsonElement)

class ResponseCache(private val directory: File, private val maxBytes: Long = 40L * 1024 * 1024) {
    private val mutex = Mutex()

    suspend fun <T> read(scope: String, key: String, serializer: KSerializer<T>): Loaded<T>? = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                val file = file(scope, key)
                if (!file.exists() || file.length() > 4L * 1024 * 1024) return@withLock null
                val envelope = AppJson.decodeFromString<Envelope>(file.readText())
                Loaded(AppJson.decodeFromJsonElement(serializer, envelope.payload), envelope.savedAt)
            } catch (_: IOException) { null } catch (_: kotlinx.serialization.SerializationException) { null }
        }
    }

    suspend fun <T> write(scope: String, key: String, serializer: KSerializer<T>, value: T, stillActive: () -> Boolean = { true }) = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!stillActive()) return@withLock
            try {
                val target = file(scope, key)
                target.parentFile?.mkdirs()
                val temporary = File(target.parentFile, target.name + ".tmp")
                temporary.writeText(AppJson.encodeToString(Envelope(System.currentTimeMillis(), AppJson.encodeToJsonElement(serializer, value))))
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
                val files = directory.walkTopDown().filter { it.isFile }.toList().sortedBy(File::lastModified)
                var size = files.sumOf(File::length)
                for (old in files) {
                    if (size <= maxBytes) break
                    val length = old.length()
                    if (old.delete()) size -= length
                }
            } catch (_: IOException) { /* Caching failure must not discard a successful response. */ }
        }
    }

    suspend fun clear() = withContext(Dispatchers.IO) { mutex.withLock { directory.deleteRecursively(); Unit } }
    private fun file(scope: String, key: String) = File(File(directory, digest(scope)), digest(key) + ".json")
    private fun digest(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
}
