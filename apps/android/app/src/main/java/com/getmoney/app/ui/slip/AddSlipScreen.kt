package com.getmoney.app.ui.slip

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.getmoney.app.data.tx.DuplicateSlipException
import com.getmoney.app.data.tx.TransactionRepository
import com.getmoney.app.ocr.SlipDraft
import com.getmoney.app.ocr.SlipOcr
import com.getmoney.app.ocr.SlipParser
import com.getmoney.app.ui.theme.CarbonButtonDefaults
import com.getmoney.app.ui.theme.ErrorRed
import com.getmoney.app.ui.theme.InkMuted
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

private enum class AddSlipStep {
    Pick,
    Processing,
    Confirm,
}

@Composable
fun AddSlipScreen(
    slipOcr: SlipOcr,
    transactionRepository: TransactionRepository,
    onDone: () -> Unit,
    sharedImageUri: Uri? = null,
    onShareUriConsumed: () -> Unit = {},
) {
    var step by remember { mutableStateOf(AddSlipStep.Pick) }
    var isManualEntry by remember { mutableStateOf(false) }
    var amount by remember { mutableStateOf("") }
    var bank by remember { mutableStateOf("") }
    var reference by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var spentAtIso by remember { mutableStateOf(defaultSpentAtIso()) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    fun applyDraft(draft: SlipDraft) {
        isManualEntry = false
        amount = draft.amount
        bank = draft.bank.orEmpty()
        reference = draft.reference.orEmpty()
        note = draft.note.orEmpty()
        spentAtIso = draft.spentAtIso ?: defaultSpentAtIso()
        step = AddSlipStep.Confirm
    }

    fun enterManually() {
        isManualEntry = true
        amount = ""
        bank = ""
        reference = ""
        note = ""
        spentAtIso = defaultSpentAtIso()
        error = null
        step = AddSlipStep.Confirm
    }

    val amountValid = isValidSlipAmount(amount)

    fun processImageUri(uri: Uri) {
        step = AddSlipStep.Processing
        error = null
        scope.launch {
            try {
                val raw = slipOcr.recognize(uri)
                applyDraft(SlipParser.parse(raw))
            } catch (throwable: Throwable) {
                error = throwable.message ?: "OCR failed"
                step = AddSlipStep.Pick
            }
        }
    }

    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        if (uri == null) {
            if (step == AddSlipStep.Processing) {
                step = AddSlipStep.Pick
            }
            return@rememberLauncherForActivityResult
        }
        processImageUri(uri)
    }

    LaunchedEffect(sharedImageUri) {
        val uri = sharedImageUri ?: return@LaunchedEffect
        onShareUriConsumed()
        processImageUri(uri)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.Top,
    ) {
        Text(
            text = "Add slip",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "OCR runs on your device. Images are never uploaded. " +
                "Works best when amounts and references use Latin digits (0–9).",
            style = MaterialTheme.typography.bodyMedium,
            color = InkMuted,
        )
        Spacer(modifier = Modifier.height(24.dp))

        when (step) {
            AddSlipStep.Pick -> {
                Button(
                    onClick = {
                        pickImageLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small,
                    colors = CarbonButtonDefaults.primaryButtonColors(),
                    elevation = CarbonButtonDefaults.primaryButtonElevation(),
                ) {
                    Text("Pick slip image")
                }
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = { enterManually() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Enter manually")
                }
            }

            AddSlipStep.Processing -> {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Reading slip…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = InkMuted,
                    )
                }
            }

            AddSlipStep.Confirm -> {
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Amount") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = amount.isNotBlank() && !amountValid,
                    supportingText = if (amount.isNotBlank() && !amountValid) {
                        { Text("Enter an amount greater than 0") }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = OutlinedTextFieldDefaults.colors(),
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = bank,
                    onValueChange = { bank = it },
                    label = { Text("Bank") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(),
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = reference,
                    onValueChange = { reference = it },
                    label = { Text("Reference") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(),
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = spentAtIso,
                    onValueChange = { spentAtIso = it },
                    label = { Text("Spent at (ISO)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(),
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(),
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = {
                        saving = true
                        error = null
                        scope.launch {
                            val saveResult = if (isManualEntry) {
                                transactionRepository.createManualTransaction(
                                    amount = amount.trim(),
                                    spentAtIso = spentAtIso.trim().ifBlank { null },
                                    bank = bank.trim().ifBlank { null },
                                    note = note.trim().ifBlank { null },
                                )
                            } else {
                                transactionRepository.createSlipTransaction(
                                    amount = amount.trim(),
                                    spentAtIso = spentAtIso.trim().ifBlank { null },
                                    bank = bank.trim().ifBlank { null },
                                    reference = reference.trim().ifBlank { null },
                                    note = note.trim().ifBlank { null },
                                )
                            }
                            saveResult.fold(
                                onSuccess = { onDone() },
                                onFailure = { throwable ->
                                    error = when (throwable) {
                                        is DuplicateSlipException -> throwable.message
                                        else -> throwable.message ?: "Save failed"
                                    }
                                },
                            )
                            saving = false
                        }
                    },
                    enabled = !saving && amountValid,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small,
                    colors = CarbonButtonDefaults.primaryButtonColors(),
                    elevation = CarbonButtonDefaults.primaryButtonElevation(),
                ) {
                    Text(if (saving) "Saving…" else "Confirm & save")
                }
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = {
                        step = AddSlipStep.Pick
                        pickImageLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Pick another image")
                }
            }
        }

        error?.let { message ->
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = ErrorRed,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        TextButton(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Cancel")
        }
    }
}

private fun defaultSpentAtIso(): String {
    val now = ZonedDateTime.now(ZoneId.of("Asia/Bangkok"))
    return now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
}

internal fun isValidSlipAmount(raw: String): Boolean {
    val trimmed = raw.trim()
    if (trimmed.isBlank()) return false
    val normalized = trimmed.replace(",", "")
    val value = normalized.toDoubleOrNull() ?: return false
    return value > 0.0
}
