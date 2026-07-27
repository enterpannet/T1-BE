package com.getmoney.app.data.auth

import com.getmoney.app.data.api.ApiErrorBody
import com.getmoney.app.data.api.AuthApi
import com.getmoney.app.data.api.LoginRequest
import com.getmoney.app.data.api.RefreshRequest
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import retrofit2.HttpException

class AuthRepository(
    private val authApi: AuthApi,
    private val tokenStore: TokenStore,
) {
    val isLoggedIn: Flow<Boolean> = tokenStore.isLoggedIn

    suspend fun login(email: String, password: String): Result<Unit> =
        authenticate { authApi.login(LoginRequest(email.trim(), password)) }

    suspend fun register(email: String, password: String): Result<Unit> =
        authenticate { authApi.register(LoginRequest(email.trim(), password)) }

    suspend fun logout() {
        val refreshToken = tokenStore.getRefreshToken()
        if (refreshToken != null) {
            try {
                authApi.logout(RefreshRequest(refreshToken))
            } catch (_: Exception) {
            }
        }
        tokenStore.clear()
    }

    suspend fun logoutAll() {
        try {
            authApi.logoutAll()
        } catch (_: Exception) {
        }
        tokenStore.clear()
    }

    private suspend fun authenticate(fetch: suspend () -> com.getmoney.app.data.api.TokenResponse): Result<Unit> {
        return try {
            val tokens = fetch()
            tokenStore.saveTokens(tokens)
            Result.success(Unit)
        } catch (error: HttpException) {
            Result.failure(Exception(parseErrorMessage(error)))
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    private fun parseErrorMessage(error: HttpException): String {
        val body = error.response()?.errorBody()?.string()
        if (!body.isNullOrBlank()) {
            runCatching {
                Gson().fromJson(body, ApiErrorBody::class.java)?.error
            }.getOrNull()?.let { return it }
        }
        return when (error.code()) {
            401 -> "Invalid email or password"
            409 -> "Email already registered"
            else -> "Request failed (${error.code()})"
        }
    }
}
