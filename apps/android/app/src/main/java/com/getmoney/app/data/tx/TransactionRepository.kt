package com.getmoney.app.data.tx

import com.getmoney.app.data.api.ApiErrorBody
import com.getmoney.app.data.api.CreateTransactionRequest
import com.getmoney.app.data.api.TransactionApi
import com.getmoney.app.data.api.TransactionResponse
import com.google.gson.Gson
import retrofit2.HttpException
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class DuplicateSlipException : Exception("บันทึกไปแล้ว")

class TransactionRepository(
    private val transactionApi: TransactionApi,
) {
    suspend fun createSlipTransaction(
        amount: String,
        spentAtIso: String?,
        bank: String?,
        reference: String?,
        note: String?,
    ): Result<TransactionResponse> =
        try {
            Result.success(
                transactionApi.create(
                    CreateTransactionRequest(
                        amount = amount,
                        spentAt = spentAtIso ?: nowBangkokIso(),
                        source = "slip",
                        bank = bank?.takeIf { it.isNotBlank() },
                        note = note?.takeIf { it.isNotBlank() },
                        reference = reference?.takeIf { it.isNotBlank() },
                    ),
                ),
            )
        } catch (error: HttpException) {
            if (error.code() == 409) {
                Result.failure(DuplicateSlipException())
            } else {
                Result.failure(Exception(parseErrorMessage(error)))
            }
        } catch (error: Exception) {
            Result.failure(error)
        }

    private fun nowBangkokIso(): String {
        val now = ZonedDateTime.now(BANGKOK)
        return now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
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
