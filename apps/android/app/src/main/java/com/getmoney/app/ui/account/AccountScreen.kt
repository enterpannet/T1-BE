package com.getmoney.app.ui.account

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.getmoney.app.R
import com.getmoney.app.autoscan.AutoScanCoordinator
import com.getmoney.app.autoscan.AutoScanCursor
import com.getmoney.app.autoscan.AutoScanStore
import com.getmoney.app.autoscan.GallerySlipScanner
import com.getmoney.app.autoscan.ScanProgress
import com.getmoney.app.data.auth.AuthRepository
import com.getmoney.app.data.cloudinary.CloudUploadStore
import com.getmoney.app.data.cloudinary.CloudinaryConfig
import com.getmoney.app.data.identity.MyIdentity
import com.getmoney.app.data.identity.MyIdentityStore
import com.getmoney.app.ui.components.IconText
import com.getmoney.app.ui.theme.CarbonButtonDefaults
import com.getmoney.app.ui.theme.InkMuted
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AccountScreen(
    authRepository: AuthRepository,
    autoScanStore: AutoScanStore,
    autoScanCoordinator: AutoScanCoordinator,
    cloudUploadStore: CloudUploadStore,
    myIdentityStore: MyIdentityStore,
    hasPhotoPermission: Boolean,
    onRequestPhotoPermission: () -> Unit,
    onScanNowComplete: (foundCount: Int) -> Unit = {},
    onExportReviewZip: () -> Unit = {},
    zipExporting: Boolean = false,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val scanner = remember { GallerySlipScanner(context) }

    val autoScanEnabled by autoScanStore.enabled.collectAsState(initial = true)
    val autoSaveEnabled by autoScanStore.autoSaveEnabled.collectAsState(initial = true)
    val cloudUploadEnabled by cloudUploadStore.enabled.collectAsState(initial = false)
    val savedIdentity by myIdentityStore.identity.collectAsState(initial = MyIdentity())
    val scanProgress by autoScanCoordinator.scanProgress.collectAsState()
    val reviewQueue by autoScanCoordinator.queue.collectAsState()
    val exportableReview by autoScanCoordinator.exportableReviewSlips.collectAsState()
    val cloudinaryConfigured = CloudinaryConfig.isConfigured
    var scanning by remember { mutableStateOf(false) }
    var buckets by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var selectedBucketIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var bucketsLoading by remember { mutableStateOf(false) }
    var selectedFolderFileCount by remember { mutableStateOf(0) }
    var myNamesText by remember { mutableStateOf("") }
    var myAccountsText by remember { mutableStateOf("") }
    var identitySaving by remember { mutableStateOf(false) }
    var identitySavedHint by remember { mutableStateOf(false) }

    LaunchedEffect(savedIdentity) {
        myNamesText = savedIdentity.namesText()
        myAccountsText = savedIdentity.accountsText()
        identitySavedHint = false
    }

    LaunchedEffect(hasPhotoPermission) {
        if (!hasPhotoPermission) {
            buckets = emptyList()
            selectedFolderFileCount = 0
            return@LaunchedEffect
        }
        bucketsLoading = true
        buckets = scanner.listBuckets()
        selectedBucketIds = autoScanStore.getExtraBucketIds()
        bucketsLoading = false
    }

    LaunchedEffect(selectedBucketIds, hasPhotoPermission) {
        if (!hasPhotoPermission || selectedBucketIds.isEmpty()) {
            selectedFolderFileCount = 0
            return@LaunchedEffect
        }
        selectedFolderFileCount = scanner.countImages(selectedBucketIds)
    }

    val selectedBuckets = buckets.filter { it.first in selectedBucketIds }
    val unselectedBuckets = buckets.filter { it.first !in selectedBucketIds }

    fun toggleBucket(bucketId: String) {
        val updated = if (bucketId in selectedBucketIds) {
            selectedBucketIds - bucketId
        } else {
            selectedBucketIds + bucketId
        }
        selectedBucketIds = updated
        scope.launch {
            autoScanStore.setExtraBucketIds(updated)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.Top,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher),
                contentDescription = "GetMoney logo",
                modifier = Modifier.size(48.dp),
            )
            Text(
                text = "Account",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Manage your session on this device.",
            style = MaterialTheme.typography.bodyMedium,
            color = InkMuted,
        )
        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "ชื่อของฉัน (ต้นทาง)",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "ใส่ชื่อ/บัญชีที่ใช้บนสลิป ระบบจะเทียบกับ จาก–ถึง เพื่อเลือกโอนออกหรือรับเข้า",
            style = MaterialTheme.typography.bodySmall,
            color = InkMuted,
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = myNamesText,
            onValueChange = {
                myNamesText = it
                identitySavedHint = false
            },
            label = { Text("ชื่อ (บรรทัดละหนึ่งชื่อ)") },
            placeholder = { Text("นาย เกียรติศักดิ์ พ") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            maxLines = 4,
            shape = MaterialTheme.shapes.small,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            ),
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = myAccountsText,
            onValueChange = {
                myAccountsText = it
                identitySavedHint = false
            },
            label = { Text("บัญชี (บรรทัดละหนึ่งรายการ)") },
            placeholder = { Text("xxx-x-x3523-x") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            maxLines = 4,
            shape = MaterialTheme.shapes.small,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            ),
        )
        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = {
                identitySaving = true
                scope.launch {
                    myIdentityStore.setIdentity(
                        MyIdentity.fromTexts(myNamesText, myAccountsText),
                    )
                    identitySaving = false
                    identitySavedHint = true
                }
            },
            enabled = !identitySaving,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.small,
            colors = CarbonButtonDefaults.primaryButtonColors(),
            elevation = CarbonButtonDefaults.primaryButtonElevation(),
        ) {
            Text(if (identitySaving) "กำลังบันทึก…" else if (identitySavedHint) "บันทึกแล้ว" else "บันทึกชื่อของฉัน")
        }

        Spacer(modifier = Modifier.height(32.dp))
        HorizontalDivider(color = InkMuted.copy(alpha = 0.2f))
        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Auto slip scan",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Auto-scan",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = "Scan gallery history in batches when you open the app.",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkMuted,
                )
            }
            Switch(
                checked = autoScanEnabled,
                onCheckedChange = { enabled ->
                    scope.launch { autoScanStore.setEnabled(enabled) }
                },
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Auto-save",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = "บันทึกทันทีหลังสแกน ไม่ต้องยืนยันทีละใบ (ซ้ำถ้าเลขที่รายการตรงกัน)",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkMuted,
                )
            }
            Switch(
                checked = autoSaveEnabled,
                onCheckedChange = { enabled ->
                    scope.launch { autoScanStore.setAutoSaveEnabled(enabled) }
                },
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "โฟลเดอร์ที่สแกน",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "สแกนเฉพาะโฟลเดอร์ที่เลือกเท่านั้น ต้องเลือกอย่างน้อย 1 โฟลเดอร์",
            style = MaterialTheme.typography.bodySmall,
            color = InkMuted,
        )
        Spacer(modifier = Modifier.height(12.dp))

        when {
            !hasPhotoPermission -> {
                Text(
                    text = "Grant photo access to choose folders.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkMuted,
                )
            }
            bucketsLoading -> {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            buckets.isEmpty() -> {
                Text(
                    text = "No folders found.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkMuted,
                )
            }
            else -> {
                Text(
                    text = "เลือกแล้ว (${selectedBuckets.size})",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (selectedBuckets.isEmpty()) {
                    Text(
                        text = "ยังไม่ได้เลือก — จะยังไม่สแกน",
                        style = MaterialTheme.typography.bodyMedium,
                        color = InkMuted,
                    )
                } else {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        selectedBuckets.forEach { (bucketId, displayName) ->
                            FilterChip(
                                selected = true,
                                onClick = { toggleBucket(bucketId) },
                                label = { Text(displayName) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = "เลือกแล้ว",
                                        modifier = Modifier.size(18.dp),
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
                                ),
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "ในโฟลเดอร์ที่เลือกมี $selectedFolderFileCount ไฟล์",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "ยังไม่เลือก (${unselectedBuckets.size})",
                    style = MaterialTheme.typography.labelLarge,
                    color = InkMuted,
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (unselectedBuckets.isEmpty()) {
                    Text(
                        text = "เลือกครบทุกโฟลเดอร์แล้ว",
                        style = MaterialTheme.typography.bodyMedium,
                        color = InkMuted,
                    )
                } else {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        unselectedBuckets.forEach { (bucketId, displayName) ->
                            FilterChip(
                                selected = false,
                                onClick = { toggleBucket(bucketId) },
                                label = { Text(displayName) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.Folder,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    labelColor = MaterialTheme.colorScheme.onSurface,
                                    iconColor = InkMuted,
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = false,
                                    borderColor = InkMuted.copy(alpha = 0.45f),
                                ),
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        val canScan = autoScanEnabled && !scanning && !scanProgress.isActive && selectedBucketIds.isNotEmpty()

        Button(
            onClick = {
                scope.launch {
                    if (scanning) return@launch
                    if (!hasPhotoPermission) {
                        onRequestPhotoPermission()
                        return@launch
                    }
                    if (selectedBucketIds.isEmpty()) return@launch
                    scanning = true
                    try {
                        autoScanCoordinator.runScanNow(true)
                        onScanNowComplete(autoScanCoordinator.queue.value.size)
                    } finally {
                        scanning = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = canScan,
            shape = MaterialTheme.shapes.small,
            colors = CarbonButtonDefaults.primaryButtonColors(),
            elevation = CarbonButtonDefaults.primaryButtonElevation(),
        ) {
            if (scanning || scanProgress.isActive) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Text(
                        text = scanProgress.statusLine().ifBlank { "กำลังสแกน…" },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimary,
                        maxLines = 2,
                    )
                }
            } else {
                IconText(
                    imageVector = Icons.Outlined.QrCodeScanner,
                    text = "Scan now",
                )
            }
        }

        if (scanProgress.phase != ScanProgress.Phase.Idle) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = scanProgress.statusLine(),
                style = MaterialTheme.typography.bodySmall,
                color = InkMuted,
            )
            if (scanProgress.isActive) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (scanProgress.phase == ScanProgress.Phase.Paused) {
                        TextButton(onClick = { autoScanCoordinator.resumeScan() }) {
                            Text("ต่อ")
                        }
                    } else {
                        TextButton(onClick = { autoScanCoordinator.pauseScan() }) {
                            Text("พัก")
                        }
                    }
                    TextButton(onClick = { autoScanCoordinator.stopScan() }) {
                        Text("หยุด")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            onClick = {
                scope.launch {
                    if (scanning) return@launch
                    if (!hasPhotoPermission) {
                        onRequestPhotoPermission()
                        return@launch
                    }
                    if (selectedBucketIds.isEmpty()) return@launch
                    scanning = true
                    try {
                        autoScanCoordinator.resetAndScanAllHistory(true)
                        onScanNowComplete(autoScanCoordinator.queue.value.size)
                    } finally {
                        scanning = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = canScan,
            shape = MaterialTheme.shapes.small,
        ) {
            IconText(
                imageVector = Icons.Outlined.History,
                text = "สแกนย้อนหลังทั้งหมด",
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = if (selectedBucketIds.isEmpty()) {
                "เลือกโฟลเดอร์ก่อน แล้วค่อยสแกน"
            } else {
                "ในโฟลเดอร์มี $selectedFolderFileCount ไฟล์ · " +
                    "สแกนต่อเนื่องทีละชุด (สูงสุด ${AutoScanCursor.BATCH_LIMIT} รูป) จนครบ · " +
                    "พัก / หยุด ได้ระหว่างทาง"
            },
            style = MaterialTheme.typography.bodySmall,
            color = InkMuted,
        )

        val zipCount = maxOf(reviewQueue.size, exportableReview.size)
        if (zipCount > 0) {
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = onExportReviewZip,
                modifier = Modifier.fillMaxWidth(),
                enabled = !zipExporting && !scanning,
                shape = MaterialTheme.shapes.small,
            ) {
                Text(
                    if (zipExporting) "กำลังสร้าง ZIP…"
                    else "ส่งออก ZIP สลิปที่ต้องตรวจ ($zipCount)",
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.CloudUpload,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "Cloud images",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Cloud upload",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = if (cloudinaryConfigured) {
                        "Upload slip images to Cloudinary after save"
                    } else {
                        "Cloud upload awaits Cloudinary credentials"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = InkMuted,
                )
            }
            Switch(
                checked = cloudUploadEnabled,
                onCheckedChange = { enabled ->
                    scope.launch { cloudUploadStore.setEnabled(enabled) }
                },
                enabled = cloudinaryConfigured,
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
        HorizontalDivider(color = InkMuted.copy(alpha = 0.3f))
        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = {
                scope.launch {
                    autoScanCoordinator.clearQueue()
                    authRepository.logout()
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.small,
            colors = CarbonButtonDefaults.primaryButtonColors(),
            elevation = CarbonButtonDefaults.primaryButtonElevation(),
        ) {
            IconText(
                imageVector = Icons.AutoMirrored.Outlined.Logout,
                text = "Sign out",
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = {
                scope.launch {
                    autoScanCoordinator.clearQueue()
                    authRepository.logoutAll()
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.small,
        ) {
            IconText(
                imageVector = Icons.AutoMirrored.Outlined.Logout,
                text = "Sign out everywhere",
            )
        }
    }
}
