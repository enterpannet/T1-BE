package com.getmoney.app.ui.account

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import com.getmoney.app.autoscan.AutoScanCoordinator
import com.getmoney.app.autoscan.AutoScanStore
import com.getmoney.app.autoscan.GallerySlipScanner
import com.getmoney.app.data.auth.AuthRepository
import com.getmoney.app.ui.theme.CarbonButtonDefaults
import com.getmoney.app.ui.theme.InkMuted
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AccountScreen(
    authRepository: AuthRepository,
    autoScanStore: AutoScanStore,
    autoScanCoordinator: AutoScanCoordinator,
    hasPhotoPermission: Boolean,
    onRequestPhotoPermission: () -> Unit,
    onScanNowComplete: (foundCount: Int) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val scanner = remember { GallerySlipScanner(context) }

    val autoScanEnabled by autoScanStore.enabled.collectAsState(initial = true)
    var scanning by remember { mutableStateOf(false) }
    var buckets by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var selectedBucketIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var bucketsLoading by remember { mutableStateOf(false) }

    LaunchedEffect(hasPhotoPermission) {
        if (!hasPhotoPermission) {
            buckets = emptyList()
            return@LaunchedEffect
        }
        bucketsLoading = true
        buckets = scanner.listBuckets()
        selectedBucketIds = autoScanStore.getExtraBucketIds()
        bucketsLoading = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.Top,
    ) {
        Text(
            text = "Account",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Manage your session on this device.",
            style = MaterialTheme.typography.bodyMedium,
            color = InkMuted,
        )
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
                    text = "Scan new gallery images when you open the app.",
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

        Button(
            onClick = {
                scope.launch {
                    if (scanning) return@launch
                    if (!hasPhotoPermission) {
                        onRequestPhotoPermission()
                        return@launch
                    }
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
            enabled = autoScanEnabled && !scanning,
            shape = MaterialTheme.shapes.small,
            colors = CarbonButtonDefaults.primaryButtonColors(),
            elevation = CarbonButtonDefaults.primaryButtonElevation(),
        ) {
            if (scanning) {
                CircularProgressIndicator(
                    modifier = Modifier.height(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Text("Scan now")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "โฟลเดอร์เพิ่ม (optional)",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Wide scan already covers all folders. Selection is saved for preference.",
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
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    buckets.forEach { (bucketId, displayName) ->
                        FilterChip(
                            selected = bucketId in selectedBucketIds,
                            onClick = {
                                val updated = if (bucketId in selectedBucketIds) {
                                    selectedBucketIds - bucketId
                                } else {
                                    selectedBucketIds + bucketId
                                }
                                selectedBucketIds = updated
                                scope.launch {
                                    autoScanStore.setExtraBucketIds(updated)
                                }
                            },
                            label = { Text(displayName) },
                        )
                    }
                }
            }
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
            Text("Sign out")
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
            Text("Sign out everywhere")
        }
    }
}
