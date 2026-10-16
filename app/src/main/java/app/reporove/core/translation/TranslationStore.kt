package app.reporove.core.translation

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import app.reporove.core.network.AppJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class TranslationStore(context: Context, private val root: File = File(context.filesDir, "translation-v2")) {
    private val prefs = context.getSharedPreferences("bilingual-v2", Context.MODE_PRIVATE)
    private val alias = "reporove.bilingual.v2"
    private val mutex = Mutex()
    private val mutableConfig = MutableStateFlow(read())
    val config = mutableConfig.asStateFlow()
    private val mutableRevision = MutableStateFlow(0L)
    val revision = mutableRevision.asStateFlow()
    val generation: Long get() = mutableRevision.value
    suspend fun snapshot() = mutex.withLock { mutableConfig.value to generation }
    suspend fun save(raw: TranslationConfig) = withContext(Dispatchers.IO) {
        val value = raw.validated()
        mutex.withLock {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
            val encrypted = cipher.doFinal(AppJson.encodeToString(value).toByteArray())
            val blob = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(encrypted, Base64.NO_WRAP)
            check(prefs.edit().putString("config", blob).commit()) { "无法安全保存翻译配置。" }
            mutableRevision.value++; mutableConfig.value = value
        }
    }
    suspend fun remove() = withContext(Dispatchers.IO) { mutex.withLock {
        check(prefs.edit().clear().commit()) { "无法删除翻译配置。" }
        mutableRevision.value++; mutableConfig.value = null
        check(!root.exists() || root.deleteRecursively()) { "翻译缓存未能全部清除。" }
    } }
    suspend fun clearCache() = withContext(Dispatchers.IO) { mutex.withLock {
        mutableRevision.value++
        check(!root.exists() || root.deleteRecursively()) { "翻译缓存未能全部清除。" }
    } }
    fun cacheKey(config: TranslationConfig, block: TranslationBlock, context: String, scope: String): String = TranslationDocument.hash("v3|$scope|${config.engine}|${config.endpoint}|${config.model}|Chinese|${context.take(400)}|${block.text}|${block.cacheSignature()}")
    suspend fun cached(key: String): String? = withContext(Dispatchers.IO) { mutex.withLock {
        val file = File(root, key)
        runCatching { if (file.length() in 1..96000) file.readText().also { file.setLastModified(System.currentTimeMillis()) } else null }.getOrNull()
    } }
    suspend fun cache(key: String, value: String, expectedGeneration: Long, active: () -> Boolean) = withContext(Dispatchers.IO) { mutex.withLock {
        if (expectedGeneration != generation || !active()) return@withLock
        root.mkdirs()
        val file = File(root, key); val temporary = File(root, "$key.tmp")
        try { temporary.writeText(value); check(temporary.renameTo(file)); } finally { temporary.delete() }
        val files = root.listFiles().orEmpty().sortedBy { it.lastModified() }
        var bytes = files.sumOf { it.length() }; var count = files.size
        for (candidate in files) { if (bytes <= 20 * 1024 * 1024 && count <= 5000) break; val size = candidate.length(); if (candidate.delete()) { bytes -= size; count-- } }
    } }
    private fun read(): TranslationConfig? = try {
        prefs.getString("config", null)?.let { blob ->
            val parts = blob.split(':', limit = 2)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP))) }
            AppJson.decodeFromString<TranslationConfig>(String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)))).validated()
        }
    } catch (_: Exception) { prefs.edit().clear().apply(); null }
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build()); generateKey()
        }
    }
}
