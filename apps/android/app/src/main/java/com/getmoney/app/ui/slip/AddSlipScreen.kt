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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.getmoney.app.autoscan.AutoScanCoordinator
import com.getmoney.app.data.tx.DuplicateSlipException
import com.getmoney.app.data.tx.TransactionRepository
import com.getmoney.app.ocr.SlipDraft
import com.getmoney.app.ocr.SlipIntake
import com.getmoney.app.ui.theme.CarbonButtonDefaults
import com.getmoney.app.ui.theme.ErrorRed
import com.getmoney.app.ui.theme.InkMuted
import kotlinx.coroutines.delay
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
    slipIntake: SlipIntake,
    transactionRepository: TransactionRepository,
    onDone: () -> Unit,
    sharedImageUri: Uri? = null,
    onShareUriConsumed: () -> Unit = {},
    autoScanCoordinator: AutoScanCoordinator? = null,
    startInQueueMode: Boolean = false,
) {
    val isQueueMode = startInQueueMode && autoScanCoordinator != null
    val queue by autoScanCoordinator?.queue?.collectAsState()
        ?: remember { mutableStateOf(emptyList()) }
    var queueInitialTotal by remember { mutableIntStateOf(0) }
    val queueIndex = if (isQueueMode && queueInitialTotal > 0) {
        queueInitialTotal - queue.size + 1
    } else {
        0
    }

    var step by remember {
        mutableStateOf(if (isQueueMode) AddSlipStep.Confirm else AddSlipStep.Pick)
    }
    var isManualEntry by remember { mutableStateOf(false) }
    var amount by remember { mutableStateOf("") }
    var bank by remember { mutableStateOf("") }
    var reference by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var spentAtIso by remember { mutableStateOf(defaultSpentAtIso()) }
    var readSourceHint by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    fun applyDraft(draft: SlipDraft, source: SlipIntake.Source) {
        isManualEntry = false
        amount = draft.amount
        bank = draft.bank.orEmpty()
        reference = draft.reference.orEmpty()
        note = draft.note.orEmpty()
        spentAtIso = draft.spentAtIso ?: defaultSpentAtIso()
        readSourceHint = when (source) {
            SlipIntake.Source.Qr -> "อ่านจาก QR บนสลิป"
            SlipIntake.Source.Ocr -> "อ่านจากข้อความบนสลิป (OCR)"
        }
        step = AddSlipStep.Confirm
    }

    fun enterManually() {
        isManualEntry = true
        amount = ""
        bank = ""
        reference = ""
        note = ""
        spentAtIso = defaultSpentAtIso()
        readSourceHint = null
        error = null
        step = AddSlipStep.Confirm
    }

    val amountValid = isValidSlipAmount(amount)

    fun advanceQueueAfterSkip() {
        val coordinator = autoScanCoordinator ?: return
        coordinator.skipCurrent()
        val next = coordinator.peekCurrent()
        if (next != null) {
            applyDraft(next.draft, next.source)
            error = null
        } else {
            onDone()
        }
    }

    fun advanceQueueAfterSave() {
        val coordinator = autoScanCoordinator ?: return
        coordinator.removeCurrentAfterSave()
        val next = coordinator.peekCurrent()
        if (next != null) {
            applyDraft(next.draft, next.source)
            error = null
        } else {
            onDone()
        }
    }

    fun processImageUri(uri: Uri) {
        step = AddSlipStep.Processing
        error = null
        readSourceHint = null
        scope.launch {
            try {
                val outcome = slipIntake.process(uri)
                applyDraft(outcome.draft, outcome.source)
            } catch (throwable: Throwable) {
                error = throwable.message ?: "อ่านสลิปไม่สำเร็จ"
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

    LaunchedEffect(isQueueMode) {
        if (!isQueueMode) return@LaunchedEffect
        val coordinator = autoScanCoordinator ?: return@LaunchedEffect
        val initial = coordinator.queue.value
        if (initial.isEmpty()) {
            onDone()
            return@LaunchedEffect
        }
        queueInitialTotal = initial.size
        coordinator.peekCurrent()?.let { applyDraft(it.draft, it.source) } ?: onDone()
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
        if (isQueueMode && queueInitialTotal > 0) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "$queueIndex/$queueInitialTotal",
                style = MaterialTheme.typography.bodyMedium,
                color = InkMuted,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "อ่าน QR บนสลิปก่อน แล้วค่อย OCR เป็น fallback — ทั้งหมดบนเครื่อง " +
                "ไม่ส่งรูปขึ้นเซิร์ฟเวอร์ โปรดยืนยันจำนวนเงินก่อนบันทึก",
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
                        text = "กำลังอ่านสลิป (QR → OCR)…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = InkMuted,
                    )
                }
            }

            AddSlipStep.Confirm -> {
                readSourceHint?.let { hint ->
                    Text(
                        text = hint,
                        style = MaterialTheme.typography.bodyMedium,
                        color = InkMuted,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
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
                                onSuccess = {
                                    if (isQueueMode) {
                                        advanceQueueAfterSave()
                                    } else {
                                        onDone()
                                    }
                                },
                                onFailure = { throwable ->
                                    if (isQueueMode && throwable is DuplicateSlipException) {
                                        error = throwable.message
                                        delay(1500)
                                        advanceQueueAfterSkip()
                                    } else {
                                        error = when (throwable) {
                                            is DuplicateSlipException -> throwable.message
                                            else -> throwable.message ?: "Save failed"
                                        }
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
                if (isQueueMode) {
                    TextButton(
                        onClick = { advanceQueueAfterSkip() },
                        enabled = !saving,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Skip")
                    }
                } else {
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
