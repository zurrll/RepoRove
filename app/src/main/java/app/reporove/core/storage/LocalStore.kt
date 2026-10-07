package app.reporove.core.storage

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.reporove.core.model.*
import app.reporove.core.network.AppJson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import java.io.IOException

private val Context.localData by preferencesDataStore("reporove")

class LocalStore(context: Context) {
    private val data = context.applicationContext.localData
    private val preferencesKey = stringPreferencesKey("preferences-v1")
    private val collectionsKey = stringPreferencesKey("collections-v1")
    private val downloadsKey = stringPreferencesKey("downloads-v1")
    private val readingsKey = stringPreferencesKey("reading-history-v1")
    val readings: Flow<List<ReadingRecord>> get() = values.map { decode(it[readingsKey], emptyList()) }
    suspend fun recordReading(record: ReadingRecord, active: () -> Boolean = { true }) { data.edit { values -> if (!active()) return@edit; values[readingsKey] = AppJson.encodeToString((listOf(record) + decode<List<ReadingRecord>>(values[readingsKey], emptyList()).filterNot { it.scope == record.scope && it.fullName == record.fullName && it.path == record.path && it.ref == record.ref }).take(100)) } }
    suspend fun clearReadings() { data.edit { it.remove(readingsKey) } }
    private val values = data.data.catch { if (it is IOException) emit(androidx.datastore.preferences.core.emptyPreferences()) else throw it }

    val preferences: Flow<Preferences> = values.map { value ->
        decode(value[preferencesKey], Preferences()).normalized()
    }
    val collections: Flow<CollectionState> = values.map { decode(it[collectionsKey], CollectionState()) }
    val downloads: Flow<List<DownloadRecord>> = values.map { decode(it[downloadsKey], emptyList()) }

    suspend fun updatePreferences(update: (Preferences) -> Preferences) {
        data.edit { it[preferencesKey] = AppJson.encodeToString(update(decode(it[preferencesKey], Preferences()).normalized()).normalized()) }
    }

    suspend fun toggleLater(repo: Repository) = updateCollections { value ->
        value.copy(later = if (value.later.any { it.id == repo.id }) value.later.filterNot { it.id == repo.id } else listOf(repo) + value.later)
    }

    suspend fun toggleFollowing(repo: Repository) = updateCollections { value ->
        value.copy(following = if (value.following.any { it.id == repo.id }) value.following.filterNot { it.id == repo.id } else listOf(repo) + value.following)
    }

    private suspend fun updateCollections(update: (CollectionState) -> CollectionState) {
        data.edit { it[collectionsKey] = AppJson.encodeToString(update(decode(it[collectionsKey], CollectionState()))) }
    }

    suspend fun addDownload(record: DownloadRecord) {
        data.edit { it[downloadsKey] = AppJson.encodeToString(listOf(record) + decode<List<DownloadRecord>>(it[downloadsKey], emptyList()).filterNot { it.id == record.id }) }
    }

    suspend fun removeDownload(id: Long) {
        data.edit { it[downloadsKey] = AppJson.encodeToString(decode<List<DownloadRecord>>(it[downloadsKey], emptyList()).filterNot { item -> item.id == id }) }
    }

    suspend fun rememberDownloadFile(id: Long, fileName: String) {
        data.edit { it[downloadsKey] = AppJson.encodeToString(decode<List<DownloadRecord>>(it[downloadsKey], emptyList()).map { record -> if (record.id == id) record.copy(fileName = fileName) else record }) }
    }

    // Private local collections belong to the active account and are cleared on logout.
    suspend fun removePrivateCollections() = updateCollections { it.copy(later = it.later.filterNot(Repository::isPrivate), following = it.following.filterNot(Repository::isPrivate)) }

    private inline fun <reified T> decode(raw: String?, default: T): T = if (raw == null) default else try { AppJson.decodeFromString<T>(raw) } catch (_: kotlinx.serialization.SerializationException) { default } catch (_: IllegalArgumentException) { default }
}
