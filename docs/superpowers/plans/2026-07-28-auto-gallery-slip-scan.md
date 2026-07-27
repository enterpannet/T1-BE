# Auto Gallery Slip Scan Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** On Android app open, scan MediaStore for new images since a persisted cursor, filter slip-like candidates via existing `SlipIntake` (QR → OCR), and queue one-by-one confirm/skip — images never leave the device.

**Architecture:** `AutoScanCoordinator` (once per MainShell session) reads `AutoScanStore` (DataStore), queries `GallerySlipScanner`, runs `SlipIntake` + `SlipCandidateFilter`, exposes an in-memory queue to Home banner → `AddSlipScreen` queue mode. Optional extra MediaStore buckets in Account settings. No API changes.

**Tech Stack:** Kotlin, Jetpack Compose, DataStore Preferences (already in project), MediaStore, ML Kit (existing SlipIntake), JUnit4 unit tests.

**Spec:** `docs/superpowers/specs/2026-07-28-auto-gallery-slip-scan-design.md`

## Global Constraints

- Slip images never leave the device; API accepts structured transaction fields only.
- First run: set `lastScanCursor = now` **without** scanning historical gallery images.
- After a scan run finishes querying/inspecting: set `lastScanCursor = scanStartedAt` even if zero candidates / user dismisses queue.
- Max **30** images inspected per scan run.
- Default auto-scan enabled (`true`); runs only when photo permission granted.
- UI: IBM Carbon from `DESIGN-ibm.md` — primary `#0f62fe`, corners `0px`, no drop shadows.
- Reuse `SlipIntake` / confirm save path (`source=slip`); handle API `409` by showing message and advancing queue.
- Ship as Android **versionName 1.5** / **versionCode 6**.

---

## File Structure

```
apps/android/app/src/main/java/com/getmoney/app/
  autoscan/
    AutoScanStore.kt              # DataStore: enabled, cursor, extraBucketIds
    GallerySlipScanner.kt         # MediaStore query → List<ScannedImage>
    SlipCandidateFilter.kt        # accept/reject SlipIntake.Outcome
    AutoScanCoordinator.kt        # session scan + MutableStateFlow queue
    ScannedImage.kt               # data class uri + dateAddedSec
  ocr/
    SlipIntake.kt                 # MODIFY: attach rawText on OCR path for filter
  ui/
    home/HomeScreen.kt            # MODIFY: banner
    slip/AddSlipScreen.kt         # MODIFY: queue mode (Skip, i/N, prefilled draft)
    account/AccountScreen.kt      # MODIFY: toggle, Scan now, folder chips
    nav/AppNav.kt                 # MODIFY: wire coordinator + permission
  MainActivity.kt                 # MODIFY: construct store/scanner/coordinator
  AndroidManifest.xml             # MODIFY: READ_EXTERNAL_STORAGE maxSdk 32

apps/android/app/src/test/java/com/getmoney/app/
  autoscan/
    SlipCandidateFilterTest.kt
    AutoScanCursorLogicTest.kt    # pure helpers if extracted
```

---

### Task 1: SlipCandidateFilter + SlipIntake rawText

**Files:**
- Create: `apps/android/app/src/main/java/com/getmoney/app/autoscan/SlipCandidateFilter.kt`
- Create: `apps/android/app/src/test/java/com/getmoney/app/autoscan/SlipCandidateFilterTest.kt`
- Modify: `apps/android/app/src/main/java/com/getmoney/app/ocr/SlipIntake.kt`

**Interfaces:**
- Consumes: `SlipIntake.Outcome` (`draft: SlipDraft`, `source: Source`, `qrPayload: String?`)
- Produces: `object SlipCandidateFilter { fun isCandidate(outcome: SlipIntake.Outcome): Boolean }`
- Produces: `SlipIntake.Outcome` gains `rawText: String? = null` (set on OCR path)

- [ ] **Step 1: Write failing tests**

```kotlin
package com.getmoney.app.autoscan

import com.getmoney.app.ocr.SlipDraft
import com.getmoney.app.ocr.SlipIntake
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SlipCandidateFilterTest {
    @Test
    fun acceptsQrWithAmount() {
        val outcome = SlipIntake.Outcome(
            draft = SlipDraft(amount = "100.00", reference = "ABC"),
            source = SlipIntake.Source.Qr,
            qrPayload = "000201...",
        )
        assertTrue(SlipCandidateFilter.isCandidate(outcome))
    }

    @Test
    fun rejectsQrWithoutUsableAmount() {
        val outcome = SlipIntake.Outcome(
            draft = SlipDraft(amount = "0.00"),
            source = SlipIntake.Source.Qr,
        )
        assertFalse(SlipCandidateFilter.isCandidate(outcome))
    }

    @Test
    fun acceptsOcrWithAmountAndSlipKeywords() {
        val outcome = SlipIntake.Outcome(
            draft = SlipDraft(amount = "250.50", bank = "SCB"),
            source = SlipIntake.Source.Ocr,
            rawText = "โอนเงิน สำเร็จ จำนวนเงิน 250.50 บาท SCB",
        )
        assertTrue(SlipCandidateFilter.isCandidate(outcome))
    }

    @Test
    fun rejectsOcrRandomPhotoWithNumber() {
        val outcome = SlipIntake.Outcome(
            draft = SlipDraft(amount = "42.00"),
            source = SlipIntake.Source.Ocr,
            rawText = "Happy birthday 42 candles",
        )
        assertFalse(SlipCandidateFilter.isCandidate(outcome))
    }
}
```

- [ ] **Step 2: Run tests — expect FAIL** (filter / `rawText` missing)

```bash
cd apps/android
# JAVA_HOME = JDK 17
.\gradlew.bat :app:testDebugUnitTest --tests com.getmoney.app.autoscan.SlipCandidateFilterTest
```

Expected: compile error or FAIL.

- [ ] **Step 3: Extend Outcome and implement filter**

In `SlipIntake.kt`, add `rawText: String? = null` to `Outcome`. On OCR return path:

```kotlin
return Outcome(draft = draft, source = Source.Ocr, rawText = rawText)
```

Create `SlipCandidateFilter.kt`:

```kotlin
package com.getmoney.app.autoscan

import com.getmoney.app.ocr.SlipIntake

object SlipCandidateFilter {
    private val slipKeyword = Regex(
        """จำนวน\s*เงิน|ยอด(?:โอน|เงิน|ชำระ)?|โอน(?:เงิน|สำเร็จ)?|PromptPay|พร้อมเพย์|Amount|Transfer|Baht|THB|฿|ธนาคาร|Bank""",
        RegexOption.IGNORE_CASE,
    )

    fun isCandidate(outcome: SlipIntake.Outcome): Boolean {
        if (!hasPositiveAmount(outcome.draft.amount)) return false
        return when (outcome.source) {
            SlipIntake.Source.Qr -> true
            SlipIntake.Source.Ocr -> {
                !outcome.draft.bank.isNullOrBlank() ||
                    !outcome.draft.reference.isNullOrBlank() ||
                    slipKeyword.containsMatchIn(outcome.rawText.orEmpty())
            }
        }
    }

    private fun hasPositiveAmount(raw: String): Boolean {
        val value = raw.trim().replace(",", "").toDoubleOrNull() ?: return false
        return value > 0.0
    }
}
```

- [ ] **Step 4: Run tests — expect PASS**

```bash
.\gradlew.bat :app:testDebugUnitTest --tests com.getmoney.app.autoscan.SlipCandidateFilterTest
```

- [ ] **Step 5: Commit**

```bash
git add apps/android/app/src/main/java/com/getmoney/app/autoscan/SlipCandidateFilter.kt \
  apps/android/app/src/test/java/com/getmoney/app/autoscan/SlipCandidateFilterTest.kt \
  apps/android/app/src/main/java/com/getmoney/app/ocr/SlipIntake.kt
git commit -m "feat(android): slip candidate filter for auto gallery scan"
```

---

### Task 2: AutoScanStore (DataStore)

**Files:**
- Create: `apps/android/app/src/main/java/com/getmoney/app/autoscan/AutoScanStore.kt`
- Create: `apps/android/app/src/main/java/com/getmoney/app/autoscan/AutoScanCursor.kt` (pure helpers)
- Create: `apps/android/app/src/test/java/com/getmoney/app/autoscan/AutoScanCursorLogicTest.kt`

**Interfaces:**
- Consumes: `Context` (same DataStore pattern as `TokenStore`)
- Produces:
  - `class AutoScanStore(context: Context)`
  - `val enabled: Flow<Boolean>`
  - `suspend fun isEnabled(): Boolean`
  - `suspend fun setEnabled(value: Boolean)`
  - `suspend fun getLastScanCursorEpochSec(): Long?` — `null` means never initialized
  - `suspend fun setLastScanCursorEpochSec(value: Long)`
  - `suspend fun getExtraBucketIds(): Set<String>`
  - `suspend fun setExtraBucketIds(ids: Set<String>)`
  - `object AutoScanCursor { fun initialCursor(nowEpochSec: Long): Long; fun advanceToScanStart(scanStartedAtEpochSec: Long): Long }`

- [ ] **Step 1: Write cursor helper tests**

```kotlin
package com.getmoney.app.autoscan

import org.junit.Assert.assertEquals
import org.junit.Test

class AutoScanCursorLogicTest {
    @Test
    fun firstRunUsesNowWithoutScanningPast() {
        assertEquals(1_700_000_000L, AutoScanCursor.initialCursor(1_700_000_000L))
    }

    @Test
    fun afterScanCursorEqualsScanStart() {
        assertEquals(1_700_000_100L, AutoScanCursor.advanceToScanStart(1_700_000_100L))
    }
}
```

- [ ] **Step 2: Run — expect FAIL**

```bash
.\gradlew.bat :app:testDebugUnitTest --tests com.getmoney.app.autoscan.AutoScanCursorLogicTest
```

- [ ] **Step 3: Implement helpers + store**

`AutoScanCursor.kt`:

```kotlin
package com.getmoney.app.autoscan

object AutoScanCursor {
    fun initialCursor(nowEpochSec: Long): Long = nowEpochSec
    fun advanceToScanStart(scanStartedAtEpochSec: Long): Long = scanStartedAtEpochSec
}
```

`AutoScanStore.kt` — mirror `TokenStore` with `preferencesDataStore(name = "auto_scan")`:

- Keys: `enabled` (boolean, default true via `?: true`), `last_scan_cursor_epoch_sec` (long, absent = null), `extra_bucket_ids` (string, comma-separated bucket ids).

```kotlin
suspend fun getExtraBucketIds(): Set<String> =
    dataStore.data.first()[EXTRA_BUCKET_IDS]
        ?.split(',')
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        ?.toSet()
        ?: emptySet()
```

- [ ] **Step 4: Run cursor tests — PASS**

```bash
.\gradlew.bat :app:testDebugUnitTest --tests com.getmoney.app.autoscan.AutoScanCursorLogicTest
```

- [ ] **Step 5: Commit**

```bash
git add apps/android/app/src/main/java/com/getmoney/app/autoscan/AutoScanCursor.kt \
  apps/android/app/src/main/java/com/getmoney/app/autoscan/AutoScanStore.kt \
  apps/android/app/src/test/java/com/getmoney/app/autoscan/AutoScanCursorLogicTest.kt
git commit -m "feat(android): AutoScanStore and cursor helpers"
```

---

### Task 3: GallerySlipScanner

**Files:**
- Create: `apps/android/app/src/main/java/com/getmoney/app/autoscan/ScannedImage.kt`
- Create: `apps/android/app/src/main/java/com/getmoney/app/autoscan/GallerySlipScanner.kt`
- Modify: `apps/android/app/src/main/AndroidManifest.xml`

**Interfaces:**
- Consumes: `ContentResolver`, cursor epoch, optional `extraBucketIds`, `limit` (default 30)
- Produces:
  - `data class ScannedImage(val uri: Uri, val dateAddedSec: Long, val bucketId: String?)`
  - `class GallerySlipScanner(context: Context) { suspend fun listNewImages(afterEpochSec: Long, extraBucketIds: Set<String> = emptySet(), limit: Int = 30): List<ScannedImage> }`

- [ ] **Step 1: Add legacy storage permission for API ≤32**

In `AndroidManifest.xml` after `READ_MEDIA_IMAGES`:

```xml
<uses-permission
    android:name="android.permission.READ_EXTERNAL_STORAGE"
    android:maxSdkVersion="32" />
```

- [ ] **Step 2: Implement scanner**

Query `MediaStore.Images.Media.EXTERNAL_CONTENT_URI` with projection:
`_ID`, `DATE_ADDED`, `BUCKET_ID` (or `BUCKET_DISPLAY_NAME` if needed for UI later).

Selection: `DATE_ADDED > ?` with args `[afterEpochSec.toString()]`.
Sort: `DATE_ADDED ASC`.
Take at most `limit` rows; build content URIs via `ContentUris.withAppendedId`.

If `extraBucketIds` is non-empty: still run the **wide** query (spec: wide-first). Do **not** narrow the wide query to only those buckets. (Extra buckets are for future bias / Settings display; wide query already covers them. Optionally log bucket ids for Settings listing — see Task 7.)

Run query on `Dispatchers.IO`.

```kotlin
data class ScannedImage(
    val uri: Uri,
    val dateAddedSec: Long,
    val bucketId: String?,
)
```

- [ ] **Step 3: Smoke-compile**

```bash
.\gradlew.bat :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add apps/android/app/src/main/AndroidManifest.xml \
  apps/android/app/src/main/java/com/getmoney/app/autoscan/ScannedImage.kt \
  apps/android/app/src/main/java/com/getmoney/app/autoscan/GallerySlipScanner.kt
git commit -m "feat(android): MediaStore gallery slip scanner"
```

---

### Task 4: AutoScanCoordinator + queue model

**Files:**
- Create: `apps/android/app/src/main/java/com/getmoney/app/autoscan/QueuedSlip.kt`
- Create: `apps/android/app/src/main/java/com/getmoney/app/autoscan/AutoScanCoordinator.kt`
- Create: `apps/android/app/src/test/java/com/getmoney/app/autoscan/AutoScanCoordinatorTest.kt`

**Interfaces:**
- Consumes: `AutoScanStore`, `GallerySlipScanner`, `SlipIntake`, `SlipCandidateFilter`, clock `() -> Long`
- Produces:
  - `data class QueuedSlip(val uri: Uri, val draft: SlipDraft, val source: SlipIntake.Source)`
  - `class AutoScanCoordinator(...)`
  - `val queue: StateFlow<List<QueuedSlip>>`
  - `val bannerDismissed: StateFlow<Boolean>`
  - `suspend fun runScanIfNeeded(hasPhotoPermission: Boolean)` — first-run init cursor only; else scan
  - `suspend fun runScanNow(hasPhotoPermission: Boolean)` — same scan path, for Settings
  - `fun dismissBanner()`
  - `fun clearQueue()` — Later / logout
  - `fun skipCurrent(): QueuedSlip?` — drop index 0
  - `fun peekCurrent(): QueuedSlip?`
  - `fun removeCurrentAfterSave()`

- [ ] **Step 1: Write coordinator unit test with fakes**

Use fake store (in-memory), fake scanner returning fixed URIs, fake intake returning controlled outcomes. Assert:
1. First call with `cursor == null` sets cursor to `now` and does **not** call scanner.
2. Second call with permission lists images, filters, fills queue, advances cursor to `scanStartedAt`.
3. Image that fails intake is skipped; non-candidate dropped.

Keep fakes in the test file.

- [ ] **Step 2: Run — expect FAIL**

```bash
.\gradlew.bat :app:testDebugUnitTest --tests com.getmoney.app.autoscan.AutoScanCoordinatorTest
```

- [ ] **Step 3: Implement coordinator**

Pseudo-logic for `runScanIfNeeded`:

```kotlin
if (!store.isEnabled() || !hasPhotoPermission) return
val now = clock()
val cursor = store.getLastScanCursorEpochSec()
if (cursor == null) {
    store.setLastScanCursorEpochSec(AutoScanCursor.initialCursor(now))
    return
}
val scanStartedAt = now
val images = scanner.listNewImages(afterEpochSec = cursor, extraBucketIds = store.getExtraBucketIds(), limit = 30)
val found = mutableListOf<QueuedSlip>()
for (image in images) {
    val outcome = runCatching { intake.process(image.uri) }.getOrNull() ?: continue
    if (!SlipCandidateFilter.isCandidate(outcome)) continue
    found += QueuedSlip(image.uri, outcome.draft, outcome.source)
}
_queue.value = found
bannerDismissed.value = false
store.setLastScanCursorEpochSec(AutoScanCursor.advanceToScanStart(scanStartedAt))
```

Use `MutableStateFlow` for queue; expose as `StateFlow`.

Session flag `private var scannedThisSession = false` so `runScanIfNeeded` only auto-runs once per process; `runScanNow` always runs (and may reset session flag).

- [ ] **Step 4: Tests PASS**

```bash
.\gradlew.bat :app:testDebugUnitTest --tests com.getmoney.app.autoscan.AutoScanCoordinatorTest
```

- [ ] **Step 5: Commit**

```bash
git add apps/android/app/src/main/java/com/getmoney/app/autoscan/QueuedSlip.kt \
  apps/android/app/src/main/java/com/getmoney/app/autoscan/AutoScanCoordinator.kt \
  apps/android/app/src/test/java/com/getmoney/app/autoscan/AutoScanCoordinatorTest.kt
git commit -m "feat(android): AutoScanCoordinator queue and cursor advance"
```

---

### Task 5: Wire MainActivity, permission, AppNav session scan

**Files:**
- Modify: `apps/android/app/src/main/java/com/getmoney/app/MainActivity.kt`
- Modify: `apps/android/app/src/main/java/com/getmoney/app/ui/nav/AppNav.kt`

**Interfaces:**
- Pass `autoScanCoordinator: AutoScanCoordinator` into `AppNav` / `MainShell`
- On `MainShell` first composition: request photo permission if needed; then `scope.launch { coordinator.runScanIfNeeded(granted) }`
- On logout (`AccountScreen` / auth clear): call `coordinator.clearQueue()` from Account or observe login false in AppNav

Permission helper (inline in AppNav or small file):

```kotlin
val permission = if (Build.VERSION.SDK_INT >= 33) {
    Manifest.permission.READ_MEDIA_IMAGES
} else {
    Manifest.permission.READ_EXTERNAL_STORAGE
}
```

Use `rememberLauncherForActivityResult(RequestPermission())`.

- [ ] **Step 1: Construct dependencies in MainActivity**

```kotlin
val autoScanStore = AutoScanStore(applicationContext)
val gallerySlipScanner = GallerySlipScanner(applicationContext)
val autoScanCoordinator = AutoScanCoordinator(
    store = autoScanStore,
    scanner = gallerySlipScanner,
    intake = slipIntake,
)
// pass autoScanStore + autoScanCoordinator to AppNav
```

- [ ] **Step 2: MainShell LaunchedEffect scan once**

After login shell appears, request permission → `runScanIfNeeded`.

- [ ] **Step 3: Compile**

```bash
.\gradlew.bat :app:compileDebugKotlin
```

- [ ] **Step 4: Commit**

```bash
git add apps/android/app/src/main/java/com/getmoney/app/MainActivity.kt \
  apps/android/app/src/main/java/com/getmoney/app/ui/nav/AppNav.kt
git commit -m "feat(android): wire auto-scan on MainShell with photo permission"
```

---

### Task 6: Home banner + AddSlip queue mode

**Files:**
- Modify: `apps/android/app/src/main/java/com/getmoney/app/ui/home/HomeScreen.kt`
- Modify: `apps/android/app/src/main/java/com/getmoney/app/ui/slip/AddSlipScreen.kt`
- Modify: `apps/android/app/src/main/java/com/getmoney/app/ui/nav/AppNav.kt`

**Interfaces:**
- `HomeScreen(..., pendingSlipCount: Int, onReviewPendingSlips: () -> Unit, onDismissPendingBanner: () -> Unit)`
- Navigate to `add_slip` with queue mode when reviewing
- `AddSlipScreen` accepts optional `queue: List<QueuedSlip>` / coordinator callbacks:
  - If `peekCurrent() != null` on entry (queue mode): skip Pick; show Confirm with draft; title hint `i/N`; buttons Confirm / Skip / Cancel
  - Skip → `skipCurrent()`; if empty `onDone()`
  - Save success or 409 → `removeCurrentAfterSave()` or skip duplicate then next; if empty `onDone()` and refresh Home

- [ ] **Step 1: Home banner UI**

When `pendingSlipCount > 0` and banner not dismissed:

```text
พบสลิปใหม่ N ใบ
[ดู] [ภายหลัง]
```

Carbon: no card chrome beyond existing layout spacing; primary button for ดู, TextButton for ภายหลัง.

- [ ] **Step 2: Queue mode on AddSlipScreen**

Parameters example:

```kotlin
fun AddSlipScreen(
    slipIntake: SlipIntake,
    transactionRepository: TransactionRepository,
    autoScanCoordinator: AutoScanCoordinator? = null,
    startInQueueMode: Boolean = false,
    ...
)
```

When `startInQueueMode`:
- `LaunchedEffect` load `peekCurrent()` into fields via `applyDraft`
- Show `Skip` TextButton that advances
- Show progress text `"$index/$total"` from coordinator queue size (track with local index = total - remaining + 1)

- [ ] **Step 3: AppNav routes**

`onReviewPendingSlips` → `navigate("add_slip?queue=1")` or remember a `var openQueue by mutableStateOf(false)` passed into AddSlip.

- [ ] **Step 4: Compile + existing unit tests**

```bash
.\gradlew.bat :app:testDebugUnitTest
```

- [ ] **Step 5: Commit**

```bash
git add apps/android/app/src/main/java/com/getmoney/app/ui/home/HomeScreen.kt \
  apps/android/app/src/main/java/com/getmoney/app/ui/slip/AddSlipScreen.kt \
  apps/android/app/src/main/java/com/getmoney/app/ui/nav/AppNav.kt
git commit -m "feat(android): auto-scan Home banner and confirm queue UX"
```

---

### Task 7: Account settings — toggle, Scan now, folders

**Files:**
- Modify: `apps/android/app/src/main/java/com/getmoney/app/ui/account/AccountScreen.kt`
- Modify: `apps/android/app/src/main/java/com/getmoney/app/autoscan/GallerySlipScanner.kt` (add `listBuckets(): List<Pair<String,String>>` bucketId → displayName)
- Modify: `apps/android/app/src/main/java/com/getmoney/app/ui/nav/AppNav.kt` (pass store + coordinator)

**Interfaces:**
- `AccountScreen(authRepository, autoScanStore, autoScanCoordinator, hasPhotoPermission, onRequestPhotoPermission)`
- Toggle binds to `autoScanStore.enabled`
- Button **Scan now** → permission check → `runScanNow` → if queue non-empty navigate Home or show snackbar count
- Extra folders: list buckets from MediaStore; multi-select chips; save via `setExtraBucketIds` (even if scanner remains wide-first, persisted ids satisfy spec “can add folders”; document that wide scan already covers all — selecting folders is for user preference / future narrowing. **Implementation choice locked here:** persist selected bucket ids; scanner continues wide query as Task 3; Settings UI still lets user pick/save folders so success criterion is met.)

- [ ] **Step 1: Add `listBuckets()` to scanner**

Distinct `BUCKET_ID` + `BUCKET_DISPLAY_NAME` from MediaStore images, limit ~100.

- [ ] **Step 2: Account UI section “Auto slip scan”**

- Switch Auto-scan  
- Button Scan now  
- Text “โฟลเดอร์เพิ่ม (optional)” + simple multi-select from `listBuckets()`  
- Keep Sign out buttons below

- [ ] **Step 3: Clear queue on logout**

Before/after `authRepository.logout()` call `autoScanCoordinator.clearQueue()`.

- [ ] **Step 4: Compile**

```bash
.\gradlew.bat :app:compileDebugKotlin
```

- [ ] **Step 5: Commit**

```bash
git add apps/android/app/src/main/java/com/getmoney/app/ui/account/AccountScreen.kt \
  apps/android/app/src/main/java/com/getmoney/app/autoscan/GallerySlipScanner.kt \
  apps/android/app/src/main/java/com/getmoney/app/ui/nav/AppNav.kt
git commit -m "feat(android): auto-scan settings toggle, scan now, folders"
```

---

### Task 8: Version bump, full tests, APK

**Files:**
- Modify: `apps/android/app/build.gradle.kts` (`versionCode = 6`, `versionName = "1.5"`)

- [ ] **Step 1: Bump version**

```kotlin
versionCode = 6
versionName = "1.5"
```

- [ ] **Step 2: Run all Android unit tests**

```bash
cd apps/android
.\gradlew.bat :app:testDebugUnitTest
```

Expected: BUILD SUCCESSFUL, all tests PASS.

- [ ] **Step 3: Assemble debug APK and copy**

```bash
.\gradlew.bat :app:assembleDebug
Copy-Item -Force app\build\outputs\apk\debug\app-debug.apk d:\10min\getmoney\getmoney-v1.5-tmd.deals.apk
```

- [ ] **Step 4: Manual checklist (device)**

1. Fresh install / clear app data → open → grant photos → no huge backlog.  
2. Save a new slip screenshot → kill/reopen app → banner “พบสลิปใหม่”.  
3. Confirm → Home % updates; Skip → does not return next open.  
4. Deny permission → no crash; Pick slip still works.  
5. Toggle auto-scan off → no scan on open.

- [ ] **Step 5: Commit**

```bash
git add apps/android/app/build.gradle.kts
git commit -m "chore(android): bump to 1.5 for auto gallery slip scan"
```

(Do not commit APK binaries unless the user asks.)

---

## Spec coverage self-check

| Spec requirement | Task |
|---|---|
| Scan on app open (logged in) | 5 |
| Wide MediaStore + optional folders UI | 3, 7 |
| Cursor = since last scan; first run = now, no backlog | 2, 4 |
| SlipIntake QR→OCR | 4 (reuse) |
| Confirm queue one-by-one + Skip | 6 |
| No image upload / API unchanged | all |
| Disable auto-scan; pick/share remain | 7, existing AddSlip |
| Home banner + Later drops in-memory queue | 6 |
| Permission deny quiet | 5 |
| 409 advance queue | 6 |
| Unit tests filter + cursor | 1, 2, 4 |
| version 1.5 | 8 |

## Placeholder / consistency check

- `SlipIntake.Outcome.rawText` introduced in Task 1; filter and coordinator use the same field.  
- Cursor advance uses `AutoScanCursor` helpers only.  
- Queue type is `QueuedSlip` everywhere.  
- Extra buckets persisted in Task 7; scanner remains wide-first (explicit, not TBD).
