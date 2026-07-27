package com.getmoney.app.data.cloudinary

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.cloudUploadDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "cloud_upload",
)

class CloudUploadStore(context: Context) {
    private val dataStore = context.cloudUploadDataStore

    val enabled: Flow<Boolean> = dataStore.data.map { it[ENABLED] ?: false }

    suspend fun isEnabled(): Boolean = dataStore.data.first()[ENABLED] ?: false

    suspend fun setEnabled(value: Boolean) {
        dataStore.edit { prefs ->
            prefs[ENABLED] = value
        }
    }

    companion object {
        private val ENABLED = booleanPreferencesKey("enabled")
    }
}
