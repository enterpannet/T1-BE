package com.getmoney.app.ui.slip

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import android.content.Intent
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.getmoney.app.autoscan.AutoScanCoordinator
import com.getmoney.app.data.cloudinary.CloudUploadStore
import com.getmoney.app.data.cloudinary.CloudinaryConfig
import com.getmoney.app.data.cloudinary.CloudinaryUploader
import com.getmoney.app.data.slipimage.SlipImageStore
import com.getmoney.app.data.tx.DuplicateSlipException
import com.getmoney.app.data.tx.TransactionRepository
import com.getmoney.app.ocr.SlipDraft
import com.getmoney.app.ocr.SlipIntake
import com.getmoney.app.ocr.SlipNoteCodec
import com.getmoney.app.ocr.SlipNoteParts
import com.getmoney.app.ocr.TransferDirection
import com.getmoney.app.ui.components.IconText
import com.getmoney.app.ui.components.SlipImagePreview
import com.getmoney.app.ui.theme.CarbonButtonDefaults
import com.getmoney.app.ui.theme.ErrorRed
import com.getmoney.app.ui.theme.InkMuted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    slipImageStore: SlipImageStore,
    cloudUploadStore: CloudUploadStore,
    cloudinaryUploader: CloudinaryUploader,
    onDone: () -> Unit,
    sharedImageUri: Uri? = null,
    onShareUriConsumed: () -> Unit = {},
    autoScanCoordinator: AutoScanCoordinator? = null,
    startInQueueMode: Boolean = false,
    onUploadFailed: (String) -> Unit = {},
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
    var direction by remember { mutableStateOf(TransferDirection.OUT) }
    var fromName by remember { mutableStateOf("") }
    var toName by remember { mutableStateOf("") }
    var memo by remember { mutableStateOf("") }
    var spentAtIso by remember { mutableStateOf(defaultSpentAtIso()) }
    var readSourceHint by remember { mutableStateOf<String?>(null) }
    var pendingImageUri by remember { mutableStateOf<Uri?>(null) }
    var rawOcrText by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var sharing by remember { mutableStateOf(false) }
    var filenameCopied by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val slipFileName = remember(pendingImageUri) {
        pendingImageUri?.let { resolveUriDisplayName(context, it) }
    }

    fun applyDraft(
        draft: SlipDraft,
        source: SlipIntake.Source,
        imageUri: Uri? = null,
        ocrText: String? = null,
    ) {
        isManualEntry = false
        amount = draft.amount
        bank = draft.bank.orEmpty()
        reference = draft.reference.orEmpty()
        direction = draft.direction ?: TransferDirection.OUT
        fromName = draft.fromName.orEmpty()
        toName = draft.toName.orEmpty()
        memo = draft.note.orEmpty()
        // Slip path: keep OCR datetime only — never invent "now" (needed for history scan)
        spentAtIso = draft.spentAtIso.orEmpty()
        readSourceHint = when (source) {
            SlipIntake.Source.Qr -> "อ่านจาก QR บนสลิป"
            SlipIntake.Source.Ocr -> "อ่านจากข้อความบนสลิป (OCR)"
        }
        pendingImageUri = imageUri
        rawOcrText = ocrText
        filenameCopied = false
        step = AddSlipStep.Confirm
    }

    fun enterManually() {
        isManualEntry = true
        amount = ""
        bank = ""
        reference = ""
        direction = TransferDirection.OUT
        fromName = ""
        toName = ""
        memo = ""
        spentAtIso = defaultSpentAtIso()
        readSourceHint = null
        pendingImageUri = null
        rawOcrText = null
        error = null
        step = AddSlipStep.Confirm
    }

    fun composedNote(): String? {
        val transferLine = SlipNoteCodec.displayTransferLine(
            SlipNoteParts(
                direction = direction,
                fromName = fromName,
                toName = toName,
            ),
        )
        val memoForStore = memo.trim()
            .takeIf { it.isNotEmpty() }
            ?.takeUnless { it == transferLine }
        return SlipNoteCodec.compose(
            direction = direction,
            fromName = fromName,
            toName = toName,
            memo = memoForStore,
        )
    }

    val amountValid = isValidSlipAmount(amount)
    val spentAtValid = isValidSpentAtIso(spentAtIso)
    val canSave = amountValid && (isManualEntry || spentAtValid)

    fun advanceQueueAfterSkip() {
        val coordinator = autoScanCoordinator ?: return
        coordinator.skipCurrent()
        val next = coordinator.peekCurrent()
        if (next != null) {
            applyDraft(next.draft, next.source, imageUri = next.uri, ocrText = next.rawText)
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
            applyDraft(next.draft, next.source, imageUri = next.uri, ocrText = next.rawText)
            error = null
        } else {
            onDone()
        }
    }

    suspend fun persistSlipImage(transactionId: String, sourceUri: Uri) {
        val file = slipImageStore.saveFromUri(transactionId, sourceUri) ?: return
        if (cloudUploadStore.isEnabled() && CloudinaryConfig.isConfigured) {
            cloudinaryUploader.upload(file).fold(
                onSuccess = { url ->
                    transactionRepository.updateImageUrl(transactionId, url)
                },
                onFailure = { throwable ->
                    onUploadFailed(throwable.message ?: "Cloud upload failed")
                },
            )
        }
    }

    fun processImageUri(uri: Uri, failureStep: AddSlipStep = AddSlipStep.Pick) {
        step = AddSlipStep.Processing
        error = null
        readSourceHint = null
        scope.launch {
            try {
                val outcome = slipIntake.process(uri)
                applyDraft(
                    outcome.draft,
                    outcome.source,
                    imageUri = uri,
                    ocrText = outcome.rawText,
                )
            } catch (throwable: Throwable) {
                error = throwable.message ?: "อ่านสลิปไม่สำเร็จ"
                step = failureStep
            }
        }
    }

    fun rescanCurrentImage() {
        val uri = pendingImageUri ?: return
        processImageUri(uri, failureStep = AddSlipStep.Confirm)
    }

    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        if (uri == null) {
            if (step == AddSlipStep.Processing) {
                step = if (pendingImageUri != null) AddSlipStep.Confirm else AddSlipStep.Pick
            }
            return@rememberLauncherForActivityResult
        }
        val failureStep =
            if (pendingImageUri != null || step == AddSlipStep.Confirm) {
                AddSlipStep.Confirm
            } else {
                AddSlipStep.Pick
            }
        processImageUri(uri, failureStep = failureStep)
    }

    fun launchPickImage() {
        pickImageLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
        )
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
        coordinator.peekCurrent()?.let {
            applyDraft(it.draft, it.source, imageUri = it.uri, ocrText = it.rawText)
        } ?: onDone()
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
                    IconText(
                        imageVector = Icons.Outlined.Image,
                        text = "Pick slip image",
                    )
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
                pendingImageUri?.let { previewUri ->
                    Text(
                        text = "ตัวอย่างสลิป",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    SlipImagePreview(
                        data = previewUri,
                        showTapHint = true,
                        contentDescription = "Slip preview",
                    )
                    slipFileName?.let { fileName ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = fileName,
                            style = MaterialTheme.typography.bodySmall,
                            color = InkMuted,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            TextButton(
                                onClick = {
                                    clipboard.setText(AnnotatedString(fileName))
                                    filenameCopied = true
                                },
                                enabled = !saving && !sharing,
                            ) {
                                IconText(
                                    imageVector = Icons.Outlined.ContentCopy,
                                    text = if (filenameCopied) "คัดลอกแล้ว" else "คัดลอกชื่อไฟล์",
                                )
                            }
                            TextButton(
                                onClick = {
                                    sharing = true
                                    error = null
                                    scope.launch {
                                        val caption = SlipShareHelper.buildDebugCaption(
                                            fileName = fileName,
                                            amount = amount,
                                            bank = bank,
                                            reference = reference,
                                            fromName = fromName,
                                            toName = toName,
                                            rawOcrText = rawOcrText,
                                        )
                                        val intent = withContext(Dispatchers.IO) {
                                            SlipShareHelper.buildShareIntent(
                                                context = context,
                                                sourceUri = previewUri,
                                                fileName = fileName,
                                                caption = caption,
                                            )
                                        }
                                        if (intent == null) {
                                            error = "แชร์สลิปไม่สำเร็จ"
                                        } else {
                                            context.startActivity(
                                                Intent.createChooser(intent, "แชร์สลิป"),
                                            )
                                        }
                                        sharing = false
                                    }
                                },
                                enabled = !saving && !sharing,
                            ) {
                                IconText(
                                    imageVector = Icons.Outlined.Share,
                                    text = if (sharing) "กำลังแชร์…" else "แชร์สลิป",
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "ข้อมูลผิด? สแกนรูปเดิมซ้ำ หรือเลือกรูปอื่น",
                        style = MaterialTheme.typography.bodySmall,
                        color = InkMuted,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = { rescanCurrentImage() },
                            enabled = !saving,
                            modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.small,
                        ) {
                            IconText(
                                imageVector = Icons.Outlined.Refresh,
                                text = "สแกนใหม่",
                            )
                        }
                        OutlinedButton(
                            onClick = { launchPickImage() },
                            enabled = !saving,
                            modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.small,
                        ) {
                            IconText(
                                imageVector = Icons.Outlined.Image,
                                text = "เลือกรูปอื่น",
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                } ?: run {
                    OutlinedButton(
                        onClick = { launchPickImage() },
                        enabled = !saving,
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.small,
                    ) {
                        IconText(
                            imageVector = Icons.Outlined.Image,
                            text = "เลือกรูปเพื่อสแกน",
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
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
                    label = { Text("วันเวลาบนสลิป") },
                    placeholder = { Text("2026-06-07T07:03:00+07:00") },
                    supportingText = {
                        Text(
                            when {
                                !isManualEntry && spentAtIso.isBlank() ->
                                    "ต้องมีวันเวลาที่โอน/รับจริงจากสลิป (ไม่ใช่วันที่กดบันทึก)"
                                !isManualEntry && !spentAtValid ->
                                    "รูปแบบวันเวลาไม่ถูกต้อง"
                                else ->
                                    "ใช้เวลาที่โอนจริงบนสลิป — วันบันทึกแยกต่างหากได้"
                            },
                        )
                    },
                    isError = !isManualEntry && spentAtIso.isNotBlank() && !spentAtValid ||
                        (!isManualEntry && spentAtIso.isBlank()),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(),
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "ทิศทาง",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = direction == TransferDirection.OUT,
                        onClick = { direction = TransferDirection.OUT },
                        label = { Text("โอนออก") },
                    )
                    FilterChip(
                        selected = direction == TransferDirection.IN,
                        onClick = { direction = TransferDirection.IN },
                        label = { Text("รับเข้า") },
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = fromName,
                    onValueChange = { fromName = it },
                    label = { Text("จาก (ต้นทาง)") },
                    placeholder = { Text("คน / บริษัท / บัญชี ฯลฯ") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(),
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = toName,
                    onValueChange = { toName = it },
                    label = { Text("ถึง (ปลายทาง)") },
                    placeholder = { Text("คน / บริษัท / บัญชี ฯลฯ") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(),
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = memo,
                    onValueChange = { memo = it },
                    label = { Text("บันทึกช่วยจำ") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 5,
                    colors = OutlinedTextFieldDefaults.colors(),
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = {
                        saving = true
                        error = null
                        val noteToSave = composedNote()
                        scope.launch {
                            val saveResult = if (isManualEntry) {
                                transactionRepository.createManualTransaction(
                                    amount = amount.trim(),
                                    spentAtIso = spentAtIso.trim().ifBlank { null },
                                    bank = bank.trim().ifBlank { null },
                                    note = noteToSave,
                                )
                            } else {
                                transactionRepository.createSlipTransaction(
                                    amount = amount.trim(),
                                    spentAtIso = spentAtIso.trim().ifBlank { null },
                                    bank = bank.trim().ifBlank { null },
                                    reference = reference.trim().ifBlank { null },
                                    note = noteToSave,
                                )
                            }
                            saveResult.fold(
                                onSuccess = { response ->
                                    val imageUri = pendingImageUri
                                    if (!isManualEntry && imageUri != null) {
                                        persistSlipImage(response.id, imageUri)
                                    }
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
                    enabled = !saving && canSave,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small,
                    colors = CarbonButtonDefaults.primaryButtonColors(),
                    elevation = CarbonButtonDefaults.primaryButtonElevation(),
                ) {
                    IconText(
                        imageVector = Icons.Outlined.Check,
                        text = if (saving) "Saving…" else "Confirm & save",
                    )
                }
                if (isQueueMode) {
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = { advanceQueueAfterSkip() },
                        enabled = !saving,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("ข้าม")
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

/** Accept ISO-8601 with optional offset (slip transfer time). */
internal fun isValidSpentAtIso(raw: String): Boolean {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return false
    return runCatching {
        java.time.OffsetDateTime.parse(trimmed)
        true
    }.getOrElse {
        runCatching {
            java.time.ZonedDateTime.parse(trimmed)
            true
        }.getOrDefault(false)
    }
}

/** Best-effort display name for gallery / share / file:// URIs (for OCR debug). */
internal fun resolveUriDisplayName(context: Context, uri: Uri): String {
    val candidates = linkedSetOf<String>()

    if (uri.scheme.equals("content", ignoreCase = true)) {
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) {
                            cursor.getString(idx)?.trim()?.takeIf { it.isNotEmpty() }?.let { candidates += it }
                        }
                    }
                }
        }
    }

    uri.lastPathSegment
        ?.let { Uri.decode(it) }
        ?.substringAfterLast('/')
        ?.substringAfterLast(':')
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.let { candidates += it }

    uri.path
        ?.let { Uri.decode(it) }
        ?.substringAfterLast('/')
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.let { candidates += it }

    fun hasImageExt(name: String): Boolean {
        val ext = name.substringAfterLast('.', missingDelimiterValue = "").lowercase()
        return ext in setOf("jpg", "jpeg", "png", "webp", "heic", "heif")
    }

    val withExt = candidates.filter { hasImageExt(it) }
    var best = withExt.maxByOrNull { it.length }
        ?: candidates.maxByOrNull { it.length }
        ?: uri.toString()

    // Photo picker sometimes returns bare "1607" — attach mime extension for searchability
    if (!best.contains('.')) {
        val mime = runCatching { context.contentResolver.getType(uri) }.getOrNull().orEmpty()
        val ext = when {
            mime.equals("image/png", ignoreCase = true) -> "png"
            mime.equals("image/webp", ignoreCase = true) -> "webp"
            mime.startsWith("image/") -> "jpg"
            else -> "jpg"
        }
        best = "$best.$ext"
    }
    return best
}
