package com.getmoney.app.data.api

import com.google.gson.annotations.SerializedName

data class TokenResponse(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("refresh_token") val refreshToken: String,
    @SerializedName("expires_in") val expiresIn: Long,
)

data class LoginRequest(
    val email: String,
    val password: String,
)

data class RefreshRequest(
    @SerializedName("refresh_token") val refreshToken: String,
)

data class ApiErrorBody(
    val error: String?,
)

data class PutMonthRequest(
    val salary: String,
)

data class CreateExpenseRequest(
    val name: String,
    val amount: String,
    @SerializedName("sort_order") val sortOrder: Int = 0,
)

data class PatchExpenseRequest(
    val name: String? = null,
    val amount: String? = null,
    @SerializedName("sort_order") val sortOrder: Int? = null,
)

data class FixedExpenseResponse(
    val id: String,
    val name: String,
    val amount: String,
    @SerializedName("sort_order") val sortOrder: Int,
)

data class BudgetMonthResponse(
    val id: String,
    val year: Int,
    val month: Int,
    val salary: String,
    @SerializedName("fixed_expenses") val fixedExpenses: List<FixedExpenseResponse>,
    @SerializedName("fixed_total") val fixedTotal: String,
    val remaining: String,
    @SerializedName("daily_allowance") val dailyAllowance: String,
    @SerializedName("days_in_month") val daysInMonth: Int,
)

data class CreateTransactionRequest(
    val amount: String,
    @SerializedName("spent_at") val spentAt: String,
    val source: String,
    val bank: String? = null,
    val note: String? = null,
    val reference: String? = null,
    @SerializedName("image_url") val imageUrl: String? = null,
)

data class PatchTransactionRequest(
    val amount: String? = null,
    @SerializedName("spent_at") val spentAt: String? = null,
    val source: String? = null,
    val bank: String? = null,
    val note: String? = null,
    @SerializedName("image_url") val imageUrl: String? = null,
)

data class TransactionResponse(
    val id: String,
    val amount: String,
    @SerializedName("spent_at") val spentAt: String,
    val source: String,
    val bank: String?,
    val note: String?,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("image_url") val imageUrl: String? = null,
)

data class TodaySummaryResponse(
    @SerializedName("daily_allowance") val dailyAllowance: String,
    val spent: String,
    @SerializedName("remaining_today") val remainingToday: String,
    @SerializedName("percent_used") val percentUsed: String,
    val items: List<TransactionResponse>,
)

data class WeekSummaryResponse(
    @SerializedName("daily_allowance") val dailyAllowance: String,
    val allowance: String,
    val spent: String,
    val remaining: String,
    @SerializedName("percent_used") val percentUsed: String,
    val items: List<TransactionResponse>,
)

data class MonthSummaryResponse(
    val salary: String,
    @SerializedName("fixed_total") val fixedTotal: String,
    val remaining: String,
    @SerializedName("daily_allowance") val dailyAllowance: String,
    @SerializedName("spent_variable") val spentVariable: String,
    @SerializedName("percent_of_remaining") val percentOfRemaining: String,
)

data class AppVersionResponse(
    @SerializedName("version_code") val versionCode: Int,
    @SerializedName("version_name") val versionName: String,
    @SerializedName("force") val force: Boolean = false,
    @SerializedName("apk_url") val apkUrl: String,
    @SerializedName("notes") val notes: String = "",
)
