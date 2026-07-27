package com.getmoney.app.autoscan

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.autoScanDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "auto_scan",
)

class AutoScanStore(context: Context) : AutoScanStoreReader {
    private val dataStore = context.autoScanDataStore

    val enabled: Flow<Boolean> = dataStore.data.map { it[ENABLED] ?: true }

    override suspend fun isEnabled(): Boolean = dataStore.data.first()[ENABLED] ?: true

    suspend fun setEnabled(value: Boolean) {
        dataStore.edit { prefs ->
            prefs[ENABLED] = value
        }
    }

    override suspend fun getLastScanCursorEpochSec(): Long? =
        dataStore.data.first()[LAST_SCAN_CURSOR_EPOCH_SEC]

    override suspend fun setLastScanCursorEpochSec(value: Long) {
        dataStore.edit { prefs ->
            prefs[LAST_SCAN_CURSOR_EPOCH_SEC] = value
        }
    }

    override suspend fun getExtraBucketIds(): Set<String> =
        dataStore.data.first()[EXTRA_BUCKET_IDS]
            ?.split(',')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.toSet()
            ?: emptySet()

    suspend fun setExtraBucketIds(ids: Set<String>) {
        dataStore.edit { prefs ->
            prefs[EXTRA_BUCKET_IDS] = ids.joinToString(",")
        }
    }

    companion object {
        private val ENABLED = booleanPreferencesKey("enabled")
        private val LAST_SCAN_CURSOR_EPOCH_SEC = longPreferencesKey("last_scan_cursor_epoch_sec")
        private val EXTRA_BUCKET_IDS = stringPreferencesKey("extra_bucket_ids")
    }
}
