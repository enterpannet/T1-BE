package com.getmoney.app.data.tx

import com.getmoney.app.data.api.ApiErrorBody
import com.getmoney.app.data.api.CreateTransactionRequest
import com.getmoney.app.data.api.PatchTransactionRequest
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
        createTransaction(
            amount = amount,
            spentAtIso = spentAtIso,
            source = "slip",
            bank = bank,
            reference = reference,
            note = note,
        )

    suspend fun createManualTransaction(
        amount: String,
        spentAtIso: String?,
        bank: String?,
        note: String?,
    ): Result<TransactionResponse> =
        createTransaction(
            amount = amount,
            spentAtIso = spentAtIso,
            source = "manual",
            bank = bank,
            reference = null,
            note = note,
        )

    suspend fun listTransactions(
        from: String? = null,
        to: String? = null,
    ): Result<List<TransactionResponse>> =
        try {
            Result.success(transactionApi.list(from = from, to = to))
        } catch (error: HttpException) {
            Result.failure(Exception(parseErrorMessage(error)))
        } catch (error: Exception) {
            Result.failure(error)
        }

    suspend fun updateImageUrl(
        id: String,
        url: String,
    ): Result<TransactionResponse> =
        try {
            Result.success(
                transactionApi.patch(
                    transactionId = id,
                    body = PatchTransactionRequest(imageUrl = url),
                ),
            )
        } catch (error: HttpException) {
            Result.failure(Exception(parseErrorMessage(error)))
        } catch (error: Exception) {
            Result.failure(error)
        }

    suspend fun updateTransaction(
        transactionId: String,
        amount: String? = null,
        note: String? = null,
    ): Result<TransactionResponse> =
        try {
            Result.success(
                transactionApi.patch(
                    transactionId = transactionId,
                    body = PatchTransactionRequest(
                        amount = amount,
                        note = note,
                    ),
                ),
            )
        } catch (error: HttpException) {
            Result.failure(Exception(parseErrorMessage(error)))
        } catch (error: Exception) {
            Result.failure(error)
        }

    suspend fun deleteTransaction(transactionId: String): Result<Unit> =
        try {
            transactionApi.delete(transactionId)
            Result.success(Unit)
        } catch (error: HttpException) {
            Result.failure(Exception(parseErrorMessage(error)))
        } catch (error: Exception) {
            Result.failure(error)
        }

    private suspend fun createTransaction(
        amount: String,
        spentAtIso: String?,
        source: String,
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
                        source = source,
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
