package com.getmoney.app.data.api

import com.getmoney.app.BuildConfig
import com.getmoney.app.data.auth.TokenStore
import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

interface AuthApi {
    @POST("auth/register")
    suspend fun register(@Body body: LoginRequest): TokenResponse

    @POST("auth/login")
    suspend fun login(@Body body: LoginRequest): TokenResponse

    @POST("auth/refresh")
    suspend fun refresh(@Body body: RefreshRequest): TokenResponse

    @POST("auth/logout")
    suspend fun logout(@Body body: RefreshRequest)

    @POST("auth/logout-all")
    suspend fun logoutAll()
}

interface BudgetApi {
    @GET("budget/months/{month}")
    suspend fun getMonth(@Path("month") month: String): BudgetMonthResponse

    @PUT("budget/months/{month}")
    suspend fun putMonth(
        @Path("month") month: String,
        @Body body: PutMonthRequest,
    ): BudgetMonthResponse

    @POST("budget/months/{month}/fixed-expenses")
    suspend fun createFixedExpense(
        @Path("month") month: String,
        @Body body: CreateExpenseRequest,
    ): BudgetMonthResponse

    @PATCH("budget/months/{month}/fixed-expenses/{expenseId}")
    suspend fun patchFixedExpense(
        @Path("month") month: String,
        @Path("expenseId") expenseId: String,
        @Body body: PatchExpenseRequest,
    ): BudgetMonthResponse

    @DELETE("budget/months/{month}/fixed-expenses/{expenseId}")
    suspend fun deleteFixedExpense(
        @Path("month") month: String,
        @Path("expenseId") expenseId: String,
    ): BudgetMonthResponse
}

interface TransactionApi {
    @POST("transactions")
    suspend fun create(@Body body: CreateTransactionRequest): TransactionResponse

    @PATCH("transactions/{transactionId}")
    suspend fun patch(
        @Path("transactionId") transactionId: String,
        @Body body: PatchTransactionRequest,
    ): TransactionResponse

    @DELETE("transactions/{transactionId}")
    suspend fun delete(@Path("transactionId") transactionId: String)
}

interface SummaryApi {
    @GET("summary/today")
    suspend fun today(): TodaySummaryResponse

    @GET("summary/week")
    suspend fun week(): WeekSummaryResponse

    @GET("summary/month/{month}")
    suspend fun month(@Path("month") month: String): MonthSummaryResponse
}

class ApiClient(tokenStore: TokenStore) {
    private val gson = Gson()

    private val refreshAuthApi: AuthApi = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(OkHttpClient.Builder().build())
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()
        .create(AuthApi::class.java)

    private val okHttpClient = OkHttpClient.Builder()
        .authenticator(TokenAuthenticator(tokenStore, refreshAuthApi))
        .addInterceptor(AuthInterceptor(tokenStore))
        .apply {
            if (BuildConfig.DEBUG) {
                addInterceptor(
                    HttpLoggingInterceptor().apply {
                        level = HttpLoggingInterceptor.Level.BODY
                    },
                )
            }
        }
        .build()

    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()

    val authApi: AuthApi = retrofit.create(AuthApi::class.java)

    fun <T> createService(service: Class<T>): T = retrofit.create(service)
}

private class AuthInterceptor(
    private val tokenStore: TokenStore,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.url.encodedPath.endsWith("/auth/login") ||
            request.url.encodedPath.endsWith("/auth/register") ||
            request.url.encodedPath.endsWith("/auth/refresh")
        ) {
            return chain.proceed(request)
        }

        val token = runBlocking { tokenStore.getAccessToken() } ?: return chain.proceed(request)
        return chain.proceed(
            request.newBuilder()
                .header("Authorization", "Bearer $token")
                .build(),
        )
    }
}

private class TokenAuthenticator(
    private val tokenStore: TokenStore,
    private val refreshAuthApi: AuthApi,
) : Authenticator {
    private val lock = Any()

    override fun authenticate(route: Route?, response: Response): Request? {
        if (response.code != 401) return null
        if (response.request.header("Authorization") == null) return null
        if (responseCount(response) >= 2) return null

        synchronized(lock) {
            val currentAccess = runBlocking { tokenStore.getAccessToken() }
            val requestToken = response.request.header("Authorization")
                ?.removePrefix("Bearer ")
                ?.trim()

            if (currentAccess != null && currentAccess != requestToken) {
                return response.request.newBuilder()
                    .header("Authorization", "Bearer $currentAccess")
                    .build()
            }

            val refreshToken = runBlocking { tokenStore.getRefreshToken() } ?: run {
                runBlocking { tokenStore.clear() }
                return null
            }

            val newTokens = try {
                runBlocking {
                    refreshAuthApi.refresh(RefreshRequest(refreshToken))
                }
            } catch (_: Exception) {
                runBlocking { tokenStore.clear() }
                return null
            }

            runBlocking {
                tokenStore.saveTokens(
                    accessToken = newTokens.accessToken,
                    refreshToken = newTokens.refreshToken,
                )
            }

            return response.request.newBuilder()
                .header("Authorization", "Bearer ${newTokens.accessToken}")
                .build()
        }
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }
}
