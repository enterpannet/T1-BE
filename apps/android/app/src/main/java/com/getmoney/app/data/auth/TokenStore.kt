package com.getmoney.app.data.auth

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.authDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "auth_tokens",
)

class TokenStore(context: Context) {
    private val dataStore = context.authDataStore

    val accessToken: Flow<String?> = dataStore.data.map { it[ACCESS_TOKEN] }

    val isLoggedIn: Flow<Boolean> = accessToken.map { !it.isNullOrBlank() }

    suspend fun saveTokens(accessToken: String, refreshToken: String) {
        dataStore.edit { prefs ->
            prefs[ACCESS_TOKEN] = accessToken
            prefs[REFRESH_TOKEN] = refreshToken
        }
    }

    suspend fun saveTokens(response: com.getmoney.app.data.api.TokenResponse) {
        saveTokens(response.accessToken, response.refreshToken)
    }

    suspend fun getAccessToken(): String? = dataStore.data.first()[ACCESS_TOKEN]

    suspend fun getRefreshToken(): String? = dataStore.data.first()[REFRESH_TOKEN]

    suspend fun clear() {
        dataStore.edit { it.clear() }
    }

    companion object {
        private val ACCESS_TOKEN = stringPreferencesKey("access_token")
        private val REFRESH_TOKEN = stringPreferencesKey("refresh_token")
    }
}
