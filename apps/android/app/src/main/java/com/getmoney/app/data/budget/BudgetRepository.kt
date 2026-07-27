package com.getmoney.app.data.budget

import com.getmoney.app.data.api.ApiErrorBody
import com.getmoney.app.data.api.BudgetApi
import com.getmoney.app.data.api.BudgetMonthResponse
import com.getmoney.app.data.api.CreateExpenseRequest
import com.getmoney.app.data.api.MonthSummaryResponse
import com.getmoney.app.data.api.PatchExpenseRequest
import com.getmoney.app.data.api.PutMonthRequest
import com.getmoney.app.data.api.SummaryApi
import com.getmoney.app.data.api.TodaySummaryResponse
import com.getmoney.app.data.api.WeekSummaryResponse
import com.google.gson.Gson
import retrofit2.HttpException
import java.time.ZoneId
import java.time.ZonedDateTime

class BudgetRequiredException : Exception("budget month required")

class BudgetRepository(
    private val budgetApi: BudgetApi,
    private val summaryApi: SummaryApi,
) {
    fun currentMonthKey(): String {
        val now = ZonedDateTime.now(BANGKOK)
        return "%04d-%02d".format(now.year, now.monthValue)
    }

    suspend fun getTodaySummary(): Result<TodaySummaryResponse> =
        runCatching { summaryApi.today() }.recoverCatching { error ->
            if (error is HttpException && error.code() == 404) {
                val message = parseErrorMessage(error)
                if (message == "budget month required") {
                    throw BudgetRequiredException()
                }
            }
            throw error
        }

    suspend fun getWeekSummary(): Result<WeekSummaryResponse> =
        apiCall { summaryApi.week() }

    suspend fun getMonthSummary(monthKey: String = currentMonthKey()): Result<MonthSummaryResponse> =
        apiCall { summaryApi.month(monthKey) }

    suspend fun getBudgetMonth(monthKey: String = currentMonthKey()): Result<BudgetMonthResponse> =
        apiCall { budgetApi.getMonth(monthKey) }

    suspend fun updateSalary(monthKey: String, salary: String): Result<BudgetMonthResponse> =
        apiCall { budgetApi.putMonth(monthKey, PutMonthRequest(salary)) }

    suspend fun createFixedExpense(
        monthKey: String,
        name: String,
        amount: String,
        sortOrder: Int = 0,
    ): Result<BudgetMonthResponse> =
        apiCall {
            budgetApi.createFixedExpense(
                monthKey,
                CreateExpenseRequest(name = name, amount = amount, sortOrder = sortOrder),
            )
        }

    suspend fun updateFixedExpense(
        monthKey: String,
        expenseId: String,
        name: String? = null,
        amount: String? = null,
    ): Result<BudgetMonthResponse> =
        apiCall {
            budgetApi.patchFixedExpense(
                monthKey,
                expenseId,
                PatchExpenseRequest(name = name, amount = amount),
            )
        }

    suspend fun deleteFixedExpense(monthKey: String, expenseId: String): Result<BudgetMonthResponse> =
        apiCall { budgetApi.deleteFixedExpense(monthKey, expenseId) }

    private inline fun <T> apiCall(block: () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (error: HttpException) {
            Result.failure(Exception(parseErrorMessage(error)))
        } catch (error: Exception) {
            Result.failure(error)
        }

    private fun parseErrorMessage(error: HttpException): String {
        val body = error.response()?.errorBody()?.string()
        if (!body.isNullOrBlank()) {
            runCatching {
                Gson().fromJson(body, ApiErrorBody::class.java)?.error
            }.getOrNull()?.let { return it }
        }
        return "Request failed (${error.code()})"
    }

    companion object {
        private val BANGKOK = ZoneId.of("Asia/Bangkok")
    }
}
