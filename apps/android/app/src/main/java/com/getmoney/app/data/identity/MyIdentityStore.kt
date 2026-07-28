package com.getmoney.app.data.identity

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.myIdentityDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "my_identity",
)

data class MyIdentity(
    val nameAliases: List<String> = emptyList(),
    val accountHints: List<String> = emptyList(),
) {
    fun hasAny(): Boolean = nameAliases.isNotEmpty() || accountHints.isNotEmpty()

    fun namesText(): String = nameAliases.joinToString("\n")

    fun accountsText(): String = accountHints.joinToString("\n")

    companion object {
        fun fromTexts(namesText: String, accountsText: String): MyIdentity =
            MyIdentity(
                nameAliases = splitLines(namesText),
                accountHints = splitLines(accountsText),
            )

        private fun splitLines(raw: String): List<String> =
            raw.lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
    }
}

class MyIdentityStore(context: Context) {
    private val dataStore = context.myIdentityDataStore

    val identity: Flow<MyIdentity> = dataStore.data.map { prefs ->
        MyIdentity.fromTexts(
            namesText = prefs[NAMES] ?: "",
            accountsText = prefs[ACCOUNTS] ?: "",
        )
    }

    suspend fun getIdentity(): MyIdentity = identity.first()

    suspend fun setIdentity(value: MyIdentity) {
        dataStore.edit { prefs ->
            prefs[NAMES] = value.namesText()
            prefs[ACCOUNTS] = value.accountsText()
        }
    }

    companion object {
        private val NAMES = stringPreferencesKey("name_aliases")
        private val ACCOUNTS = stringPreferencesKey("account_hints")
    }
}
