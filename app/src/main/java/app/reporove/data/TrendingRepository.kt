package app.reporove.data

import app.reporove.core.model.*
import app.reporove.core.network.AppJson
import app.reporove.core.storage.readBounded
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Separate anonymous client and public cache: GitHub account tokens never reach Trending. */
class TrendingRepository(
    private val directory: File,
    private val client: OkHttpClient = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(25, TimeUnit.SECONDS).callTimeout(35, TimeUnit.SECONDS).followRedirects(false).build(),
    private val baseUrl: HttpUrl = "https://github.com/".toHttpUrl(),
    private val now: () -> Long = System::currentTimeMillis,
) {
    val mutex = Mutex()

    suspend fun board(period: TrendingPeriod, language: String = "", refresh: Boolean = false): Loaded<TrendingBoard> = withContext(Dispatchers.IO) {
        require(TrendingRules.validLanguage(language)) { "热榜语言条件无效。" }
        mutex.withLock {
            val file = boardFile(period, language)
            val cached = decode<TrendingBoard>(file)?.takeIf { it.period == period && it.language == language }
            if (!refresh && cached != null && now() - cached.fetchedAt in 0..TrendingRules.FRESH_MILLIS) return@withLock Loaded(cached, cached.fetchedAt)
            try {
                val url = baseUrl.newBuilder().addPathSegment("trending").apply { if (language.isNotEmpty()) addPathSegment(language) }.addQueryParameter("since", period.query).build()
                val html = download(url)
                val board = TrendingParser.parse(html, period, language, now())
                try { write(file, board) } catch (_: IOException) { /* The source response remains usable if optional caching fails. */ }
                directory.listFiles { _, name -> name.startsWith("board-") }?.sortedByDescending(File::lastModified)?.drop(24)?.forEach(File::delete)
                Loaded(board, board.fetchedAt)
            } catch (error: CancellationException) { throw error }
            catch (error: IOException) { cached?.let { Loaded(it, it.fetchedAt, offline = true) } ?: throw error }
            catch (error: TrendingSourceException) { cached?.let { Loaded(it, it.fetchedAt, offline = true) } ?: throw error }
        }
    }

    suspend fun history(): TrendingHistory = withContext(Dispatchers.IO) { mutex.withLock { decode<TrendingHistory>(File(directory, "history.json")) ?: TrendingHistory() } }
    suspend fun opened(fullName: String) = withContext(Dispatchers.IO) {
        if (!TrendingRules.validName(fullName)) return@withContext
        mutex.withLock {
            val file = File(directory, "history.json")
            val history = decode<TrendingHistory>(file) ?: TrendingHistory()
            try { write(file, history.copy(opened = (listOf(TrendingSeen(fullName.lowercase(), now())) + history.opened.filterNot { it.fullName.equals(fullName, true) }).take(300))) }
            catch (_: IOException) { /* Optional recommendation history must not interrupt opening a project. */ }
        }
    }

    suspend fun savedDigest(day: String): TrendingDigest? = withContext(Dispatchers.IO) {
        if (!validDay(day)) null else mutex.withLock { decode<TrendingDigest>(File(directory, "digest-$day.json")) }
    }

    /** Persist first, then post without cancellation between them. A failed post restores the claim. */
    suspend fun notifyOnce(digest: TrendingDigest, allowed: suspend () -> Boolean, post: () -> Boolean): Boolean = withContext(Dispatchers.IO) {
        require(validDay(digest.day))
        mutex.withLock {
            val file = File(directory, "history.json")
            val history = decode<TrendingHistory>(file) ?: TrendingHistory()
            if (history.lastNotificationDay == digest.day || !allowed()) return@withLock false
            withContext(NonCancellable) {
                write(File(directory, "digest-${digest.day}.json"), digest)
                write(file, history.copy(lastNotificationDay = digest.day, notified = (digest.picks.map { TrendingSeen(it.entry.fullName.lowercase(), now()) } + history.notified).take(300)))
                val posted = try { post() } catch (_: Exception) { false }
                if (!posted) write(file, history)
                directory.listFiles { _, name -> name.startsWith("digest-") }?.sortedByDescending(File::lastModified)?.drop(30)?.forEach(File::delete)
                posted
            }
        }
    }

    private fun boardFile(period: TrendingPeriod, language: String): File {
        val key = MessageDigest.getInstance("SHA-256").digest(language.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(directory, "board-${period.query}-$key.json")
    }
    private inline fun <reified T> decode(file: File): T? = runCatching { if (file.length() > 2_000_000) null else AppJson.decodeFromString<T>(file.readText()) }.getOrNull()
    private inline fun <reified T> write(file: File, value: T) {
        directory.mkdirs()
        val temporary = File(directory, "${file.name}.tmp")
        temporary.writeText(AppJson.encodeToString(value))
        Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }
    private suspend fun download(url: HttpUrl): String = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(Request.Builder().url(url).header("User-Agent", "RepoRove-Android").header("Accept-Language", "en").header("Accept", "text/html").build())
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { if (continuation.isActive) continuation.resumeWithException(e) }
            override fun onResponse(call: Call, response: Response) {
                try {
                    val html = response.use {
                        if (it.code == 429) throw TrendingSourceException("GitHub 热榜访问过于频繁，请稍后再试。")
                        if (it.code in 400..499) throw TrendingSourceException("GitHub 暂时无法提供热榜（HTTP ${it.code}），请稍后重试。")
                        if (!it.isSuccessful) throw IOException("GitHub Trending HTTP ${it.code}")
                        val bytes = it.body?.byteStream()?.use { stream -> stream.readBounded(2_000_000) } ?: throw IOException("Empty Trending response")
                        if (bytes.size > 2_000_000) throw TrendingSourceException("GitHub 热榜内容过大，暂时无法读取。")
                        bytes.toString(Charsets.UTF_8)
                    }
                    if (continuation.isActive) continuation.resume(html)
                } catch (error: Exception) { if (continuation.isActive) continuation.resumeWithException(error) }
            }
        })
    }

    companion object {
        fun validDay(day: String) = day.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) && runCatching { LocalDate.parse(day) }.isSuccess
    }
}
