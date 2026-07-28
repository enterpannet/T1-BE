package com.getmoney.app.ui.tx

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.getmoney.app.data.api.TransactionResponse
import com.getmoney.app.data.slipimage.SlipImageStore
import com.getmoney.app.ocr.SlipNoteCodec
import com.getmoney.app.ocr.SlipNoteParts
import com.getmoney.app.ui.components.SlipImagePreview
import com.getmoney.app.ui.theme.InkMuted
import com.getmoney.app.ui.util.formatMoney
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

private val Bangkok = ZoneId.of("Asia/Bangkok")

@Composable
fun TransactionListItem(
    transaction: TransactionResponse,
    slipImageStore: SlipImageStore,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val localFile = remember(transaction.id) { slipImageStore.fileFor(transaction.id) }
    val previewData: Any? = localFile ?: transaction.imageUrl?.takeIf { it.isNotBlank() }
    val noteParts = remember(transaction.note) { SlipNoteCodec.parse(transaction.note) }
    val secondary = remember(noteParts, transaction.bank) {
        transactionSecondaryLine(noteParts, transaction.bank)
    }
    val timeLabel = remember(transaction.spentAt) { formatSpentAtTime(transaction.spentAt) }
    val memo = noteParts.memo?.takeIf { it.isNotBlank() }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SlipImagePreview(
            data = previewData,
            thumbSize = 56.dp,
            showTapHint = false,
            enableFullscreenOnTap = false,
            contentDescription = "Slip thumbnail",
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = formatMoney(transaction.amount),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            if (secondary != null) {
                Text(
                    text = secondary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val meta = buildString {
                append(timeLabel)
                if (!memo.isNullOrBlank()) {
                    append(" · ")
                    append(memo)
                }
            }
            Text(
                text = meta,
                style = MaterialTheme.typography.bodySmall,
                color = InkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

internal fun transactionSecondaryLine(parts: SlipNoteParts, bank: String?): String? {
    val direction = parts.direction?.labelTh()
    val route = when {
        !parts.fromName.isNullOrBlank() && !parts.toName.isNullOrBlank() ->
            "${parts.fromName} → ${parts.toName}"
        !parts.fromName.isNullOrBlank() -> "จาก ${parts.fromName}"
        !parts.toName.isNullOrBlank() -> "ถึง ${parts.toName}"
        else -> null
    }
    return when {
        direction != null && route != null -> "$direction · $route"
        direction != null -> direction
        route != null -> route
        !bank.isNullOrBlank() -> bank
        else -> null
    }
}

internal fun formatSpentAtTime(iso: String): String {
    return try {
        ZonedDateTime.parse(iso).withZoneSameInstant(Bangkok)
            .format(DateTimeFormatter.ofPattern("HH:mm"))
    } catch (_: Exception) {
        iso
    }
}

internal fun formatSpentAtFull(iso: String): String {
    return try {
        ZonedDateTime.parse(iso).withZoneSameInstant(Bangkok)
            .format(DateTimeFormatter.ofPattern("d MMM yyyy HH:mm"))
    } catch (_: Exception) {
        iso
    }
}

internal fun dayKeyForTransaction(iso: String): java.time.LocalDate? {
    return try {
        ZonedDateTime.parse(iso).withZoneSameInstant(Bangkok).toLocalDate()
    } catch (_: Exception) {
        null
    }
}

internal fun dayHeaderLabel(date: java.time.LocalDate, today: java.time.LocalDate): String {
    return when (date) {
        today -> "วันนี้"
        today.minusDays(1) -> "เมื่อวาน"
        else -> date.format(DateTimeFormatter.ofPattern("d MMM yyyy"))
    }
}
