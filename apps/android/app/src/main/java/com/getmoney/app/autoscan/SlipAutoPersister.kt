package com.getmoney.app.autoscan

import com.getmoney.app.data.slipimage.SlipImageStore
import com.getmoney.app.data.tx.DuplicateSlipException
import com.getmoney.app.data.tx.TransactionRepository
import com.getmoney.app.ui.slip.isValidSlipAmount
import com.getmoney.app.ui.slip.isValidSpentAtIso

enum class AutoSaveItemResult {
    Saved,
    Duplicate,
    NeedsReview,
    Failed,
}

data class AutoSaveSummary(
    val folderFileCount: Int = 0,
    val saved: Int = 0,
    val duplicates: Int = 0,
    val needReview: Int = 0,
    val failed: Int = 0,
) {
    fun totalHandled(): Int = saved + duplicates + needReview + failed

    /** Saved this run + already in DB (duplicate). */
    fun alreadySavedCount(): Int = saved + duplicates

    fun snackbarMessage(): String {
        val parts = mutableListOf<String>()
        if (folderFileCount > 0) parts += "ในโฟลเดอร์มี $folderFileCount ไฟล์"

        val hasSlipOutcomes = saved > 0 || duplicates > 0 || needReview > 0 || failed > 0
        if (!hasSlipOutcomes) {
            parts += "ไม่พบสลิปใหม่"
            return parts.joinToString(" · ")
        }

        if (saved > 0) parts += "สำเร็จ $saved"
        if (duplicates > 0) parts += "ข้าม $duplicates"
        if (needReview > 0) parts += "ต้องตรวจ $needReview"
        if (failed > 0) parts += "error $failed"
        return parts.joinToString(" · ")
    }
}

fun interface SlipAutoPersister {
    suspend fun persist(item: QueuedSlip): AutoSaveItemResult
}

class TransactionSlipAutoPersister(
    private val transactionRepository: TransactionRepository,
    private val slipImageStore: SlipImageStore,
) : SlipAutoPersister {
    override suspend fun persist(item: QueuedSlip): AutoSaveItemResult {
        val draft = item.draft
        if (!isValidSlipAmount(draft.amount) || !isValidSpentAtIso(draft.spentAtIso.orEmpty())) {
            return AutoSaveItemResult.NeedsReview
        }
        return transactionRepository.createSlipTransaction(
            amount = draft.amount.trim(),
            spentAtIso = draft.spentAtIso,
            bank = draft.bank,
            reference = draft.reference,
            note = draft.note,
        ).fold(
            onSuccess = { response ->
                runCatching { slipImageStore.saveFromUri(response.id, item.uri) }
                AutoSaveItemResult.Saved
            },
            onFailure = { err ->
                when (err) {
                    is DuplicateSlipException -> AutoSaveItemResult.Duplicate
                    else -> AutoSaveItemResult.Failed
                }
            },
        )
    }
}
