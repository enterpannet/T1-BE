package com.getmoney.app.ui.tx

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.getmoney.app.data.api.TransactionResponse
import com.getmoney.app.data.slipimage.SlipImageStore
import com.getmoney.app.ui.theme.ErrorRed
import com.getmoney.app.ui.theme.InkMuted
import com.getmoney.app.ui.util.formatMoney
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

@Composable
fun TransactionListItem(
    transaction: TransactionResponse,
    slipImageStore: SlipImageStore,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TransactionThumb(
            transactionId = transaction.id,
            imageUrl = transaction.imageUrl,
            slipImageStore = slipImageStore,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = formatMoney(transaction.amount),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = formatSpentAt(transaction.spentAt),
                style = MaterialTheme.typography.bodyMedium,
                color = InkMuted,
            )
            transactionSubtitle(transaction)?.let { subtitle ->
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkMuted,
                )
            }
        }
        TextButton(onClick = onEdit) {
            Text("Edit")
        }
        TextButton(onClick = onDelete) {
            Text("Delete", color = ErrorRed)
        }
    }
}

@Composable
private fun TransactionThumb(
    transactionId: String,
    imageUrl: String?,
    slipImageStore: SlipImageStore,
    modifier: Modifier = Modifier,
) {
    val localFile = remember(transactionId) { slipImageStore.fileFor(transactionId) }
    val context = LocalContext.current
    Box(
        modifier = modifier
            .size(56.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        when {
            localFile != null -> {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(localFile)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }

            !imageUrl.isNullOrBlank() -> {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(imageUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
        }
    }
}

private fun transactionSubtitle(transaction: TransactionResponse): String? {
    val bank = transaction.bank?.takeIf { it.isNotBlank() }
    val note = transaction.note?.takeIf { it.isNotBlank() }
    return when {
        bank != null && note != null -> "$bank · $note"
        bank != null -> bank
        note != null -> note
        else -> null
    }
}

private fun formatSpentAt(iso: String): String {
    return try {
        ZonedDateTime.parse(iso).format(DateTimeFormatter.ofPattern("d MMM yyyy HH:mm"))
    } catch (_: Exception) {
        iso
    }
}
