# Transactions Screen + Slip Images Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a full Tx tab with slip thumbnails (local-first), limit Today to 5 latest + View all, store slip images on device after save, and wire optional Cloudinary upload behind a default-off Settings toggle (credentials later).

**Architecture:** API gains nullable `image_url`. Android copies slip images into `filesDir/slips/{id}` via `SlipImageStore`, displays local file then URL fallback with Coil. Cloudinary unsigned upload runs only when preference enabled and BuildConfig cloud name/preset are non-empty; then PATCH `image_url`.

**Tech Stack:** Rust/Axum/SeaORM migration; Kotlin Compose; DataStore; Coil; OkHttp multipart to Cloudinary; existing Retrofit `TransactionApi`.

**Spec:** `docs/superpowers/specs/2026-07-28-transactions-slip-images-design.md`

## Global Constraints

- Slip images default to **local-only**; Cloudinary toggle **default off**.
- GetMoney API does **not** accept multipart images — only optional `image_url` string.
- Money fields remain decimal **strings**.
- UI: IBM Carbon from `DESIGN-ibm.md` — primary `#0f62fe`, corners `0px`, no drop shadows.
- Today list capped at **exactly 5** latest items + View all → Tx tab.
- Nav tabs: Today | **Tx** | Summary | Budget | Account.
- Android ship version **1.8** / `versionCode` **9** when feature complete.
- Cloudinary BuildConfig may be empty strings until user supplies credentials.

---

## File Structure

```
apps/api/
  migration/src/m20260728_000002_transaction_image_url.rs
  migration/src/lib.rs
  src/entities/transactions.rs
  src/transactions/handlers.rs
  tests/transactions_api.rs          # extend or add image_url cases

apps/android/app/src/main/java/com/getmoney/app/
  data/slipimage/SlipImageStore.kt
  data/cloudinary/CloudinaryConfig.kt
  data/cloudinary/CloudinaryUploader.kt
  data/cloudinary/CloudUploadStore.kt   # DataStore toggle
  data/api/Dto.kt / ApiClient.kt        # image_url + list GET
  data/tx/TransactionRepository.kt
  ui/tx/TransactionsScreen.kt
  ui/tx/TransactionThumb.kt             # shared row thumb
  ui/home/HomeScreen.kt
  ui/slip/AddSlipScreen.kt              # save local + optional upload
  ui/account/AccountScreen.kt
  ui/nav/AppNav.kt
  MainActivity.kt
```

---

### Task 1: API `image_url` column + handlers

**Files:**
- Create: `apps/api/migration/src/m20260728_000002_transaction_image_url.rs`
- Modify: `apps/api/migration/src/lib.rs`
- Modify: `apps/api/src/entities/transactions.rs`
- Modify: `apps/api/src/transactions/handlers.rs`
- Modify or create: `apps/api/tests/transactions_api.rs` (image_url create/patch/list)

**Interfaces:**
- Produces: `transactions.image_url: Option<String>`
- Produces: `TransactionResponse.image_url: Option<String>` (serde `skip_serializing_if` optional — prefer always serialize as null)
- Produces: create/patch accept optional `image_url: Option<String>`

- [ ] **Step 1: Write failing API test** asserting create with `image_url` round-trips on GET list/response.

```rust
// In transactions_api integration test (follow existing auth+tx patterns in repo):
// POST /transactions { ..., "image_url": "https://res.cloudinary.com/demo/image/upload/v1/x.jpg" }
// assert response.image_url == Some(...)
```

- [ ] **Step 2: Run test — expect FAIL** (column/field missing)

```bash
cd apps/api
# use existing test DB env from prior MVP
cargo test --test transactions_api -- --nocapture
```

- [ ] **Step 3: Migration**

```rust
// m20260728_000002_transaction_image_url.rs
manager
    .alter_table(
        Table::alter()
            .table(Transactions::Table)
            .add_column(ColumnDef::new(Transactions::ImageUrl).text().null())
            .to_owned(),
    )
    .await?;
```

Register in `Migrator::migrations()`.

Add `pub image_url: Option<String>` to entity `Model`.

Update `CreateTransactionRequest`, `PatchTransactionRequest`, `TransactionResponse`, insert/patch `Set` for `image_url`.

- [ ] **Step 4: Tests PASS**

```bash
cargo test --test transactions_api
```

- [ ] **Step 5: Commit**

```bash
git add apps/api/migration apps/api/src/entities/transactions.rs apps/api/src/transactions/handlers.rs apps/api/tests
git commit -m "feat(api): add nullable image_url on transactions"
```

---

### Task 2: Android DTOs + list transactions API

**Files:**
- Modify: `apps/android/app/src/main/java/com/getmoney/app/data/api/Dto.kt`
- Modify: `apps/android/app/src/main/java/com/getmoney/app/data/api/ApiClient.kt`
- Modify: `apps/android/app/src/main/java/com/getmoney/app/data/tx/TransactionRepository.kt`

**Interfaces:**
- Consumes: API `GET /transactions` (already exists server-side as `list_transactions`)
- Produces:
  - `TransactionResponse.imageUrl: String?`
  - `CreateTransactionRequest.imageUrl` / `PatchTransactionRequest.imageUrl` optional
  - `TransactionRepository.listTransactions(from, to): Result<List<TransactionResponse>>`
  - `TransactionRepository.updateImageUrl(id, url): Result<TransactionResponse>`

- [ ] **Step 1: Extend DTOs**

```kotlin
data class TransactionResponse(
    val id: String,
    val amount: String,
    @SerializedName("spent_at") val spentAt: String,
    val source: String,
    val bank: String?,
    val note: String?,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("image_url") val imageUrl: String? = null,
)
```

Add `imageUrl` to create/patch requests similarly.

```kotlin
@GET("transactions")
suspend fun list(
    @Query("from") from: String? = null,
    @Query("to") to: String? = null,
): List<TransactionResponse>
```

- [ ] **Step 2: Repository methods** `listTransactions`, `updateImageUrl` (patch with only imageUrl).

- [ ] **Step 3: Compile**

```bash
cd apps/android
# JAVA_HOME = JDK 17
.\gradlew.bat :app:compileDebugKotlin
```

- [ ] **Step 4: Commit**

```bash
git add apps/android/app/src/main/java/com/getmoney/app/data
git commit -m "feat(android): transaction image_url DTO and list API"
```

---

### Task 3: SlipImageStore (local files)

**Files:**
- Create: `apps/android/app/src/main/java/com/getmoney/app/data/slipimage/SlipImageStore.kt`
- Create: `apps/android/app/src/test/java/com/getmoney/app/data/slipimage/SlipImageStoreTest.kt`

**Interfaces:**
- Produces:
  - `class SlipImageStore(context: Context)`
  - `suspend fun saveFromUri(transactionId: String, source: Uri): File?`
  - `fun fileFor(transactionId: String): File?`
  - `fun delete(transactionId: String)`
  - Directory: `context.filesDir.resolve("slips")`
  - Filename: `{transactionId}.jpg` (copy bytes regardless of source type)

- [ ] **Step 1: Failing unit test** (Robolectric or temp File dir injected)

Prefer constructor `SlipImageStore(rootDir: File)` for testability + secondary ctor from Context:

```kotlin
@Test
fun saveAndResolveRoundTrip() {
    val root = createTempDir()
    val store = SlipImageStore(root)
    // write a tiny jpeg/png bytes to a source file, saveFromUri via file URI
    assertTrue(store.fileFor(id)!!.exists())
}
```

- [ ] **Step 2: Implement store** — copy InputStream from ContentResolver or file URI into `root/slips/{id}.jpg` on `Dispatchers.IO`.

- [ ] **Step 3: Tests PASS**

```bash
.\gradlew.bat :app:testDebugUnitTest --tests com.getmoney.app.data.slipimage.SlipImageStoreTest
```

- [ ] **Step 4: Commit**

```bash
git add apps/android/app/src/main/java/com/getmoney/app/data/slipimage \
  apps/android/app/src/test/java/com/getmoney/app/data/slipimage
git commit -m "feat(android): local SlipImageStore for transaction thumbnails"
```

---

### Task 4: Cloudinary config + uploader + upload preference

**Files:**
- Modify: `apps/android/app/build.gradle.kts` — BuildConfig fields (empty default)
- Create: `apps/android/app/src/main/java/com/getmoney/app/data/cloudinary/CloudinaryConfig.kt`
- Create: `apps/android/app/src/main/java/com/getmoney/app/data/cloudinary/CloudinaryUploader.kt`
- Create: `apps/android/app/src/main/java/com/getmoney/app/data/cloudinary/CloudUploadStore.kt`
- Create: `apps/android/app/src/test/java/com/getmoney/app/data/cloudinary/CloudinaryConfigTest.kt`

**Interfaces:**
- Produces:
  - `object CloudinaryConfig { val cloudName: String; val uploadPreset: String; val isConfigured: Boolean }`
  - `class CloudinaryUploader(okHttpClient: OkHttpClient = OkHttpClient()) { suspend fun upload(file: File): Result<String> }`
  - Endpoint: `https://api.cloudinary.com/v1_1/{cloudName}/image/upload` with multipart fields `file`, `upload_preset`
  - Parse JSON `secure_url` (or `url`)
  - `CloudUploadStore`: `enabled: Flow<Boolean>`, `suspend fun setEnabled`, default **false**

- [ ] **Step 1: build.gradle.kts**

```kotlin
buildConfigField("String", "CLOUDINARY_CLOUD_NAME", "\"${project.findProperty("CLOUDINARY_CLOUD_NAME") ?: ""}\"")
buildConfigField("String", "CLOUDINARY_UPLOAD_PRESET", "\"${project.findProperty("CLOUDINARY_UPLOAD_PRESET") ?: ""}\"")
```

- [ ] **Step 2: Config test**

```kotlin
@Test
fun isConfiguredRequiresBoth() {
    assertFalse(CloudinaryConfig.isConfiguredFor("", "preset"))
    assertFalse(CloudinaryConfig.isConfiguredFor("cloud", ""))
    assertTrue(CloudinaryConfig.isConfiguredFor("cloud", "preset"))
}
```

Expose `isConfiguredFor(name, preset)` for pure test; `isConfigured` uses BuildConfig.

- [ ] **Step 3: Implement uploader + DataStore** (mirror `AutoScanStore` / `TokenStore` pattern, name `cloud_upload`).

- [ ] **Step 4: Unit tests for config PASS; compile**

- [ ] **Step 5: Commit**

```bash
git add apps/android/app/build.gradle.kts apps/android/app/src/main/java/com/getmoney/app/data/cloudinary \
  apps/android/app/src/test/java/com/getmoney/app/data/cloudinary
git commit -m "feat(android): Cloudinary config, uploader, and opt-in store"
```

---

### Task 5: Shared transaction row + TransactionsScreen + nav tab

**Files:**
- Create: `apps/android/app/src/main/java/com/getmoney/app/ui/tx/TransactionListItem.kt` (row with thumb)
- Create: `apps/android/app/src/main/java/com/getmoney/app/ui/tx/TransactionsScreen.kt`
- Modify: `apps/android/app/src/main/java/com/getmoney/app/ui/nav/AppNav.kt`
- Modify: `apps/android/app/build.gradle.kts` — add Coil `io.coil-kt:coil-compose:2.7.0`

**Interfaces:**
- Consumes: `TransactionRepository.listTransactions`, `SlipImageStore`, edit/delete dialogs (extract from Home or duplicate minimally then share)
- Produces: `TransactionsScreen(...)` full list newest-first
- Thumb resolve: `fileFor(id)` → Coil `File`; else `imageUrl`; else blank box

- [ ] **Step 1: Add Coil dependency**

- [ ] **Step 2: Implement `TransactionListItem`** with amount, spent_at, bank/note, 56dp thumb, Edit/Delete actions.

- [ ] **Step 3: `TransactionsScreen`** — load list on RESUMED; empty state; reuse edit/delete patterns from HomeScreen (prefer extracting shared dialogs into `ui/tx/TransactionDialogs.kt` if Home duplication is large).

- [ ] **Step 4: AppNav** — insert `MainTab("tx", "Tx")` after today; composable route `"tx"`.

- [ ] **Step 5: Compile**

```bash
.\gradlew.bat :app:compileDebugKotlin
```

- [ ] **Step 6: Commit**

```bash
git add apps/android/app/build.gradle.kts apps/android/app/src/main/java/com/getmoney/app/ui/tx \
  apps/android/app/src/main/java/com/getmoney/app/ui/nav/AppNav.kt
git commit -m "feat(android): Tx tab with full transaction list and thumbs"
```

---

### Task 6: Home — latest 5 + View all

**Files:**
- Modify: `apps/android/app/src/main/java/com/getmoney/app/ui/home/HomeScreen.kt`
- Create: `apps/android/app/src/test/java/com/getmoney/app/ui/home/HomeTransactionPreviewTest.kt`
- Modify: `apps/android/app/src/main/java/com/getmoney/app/ui/nav/AppNav.kt`

**Interfaces:**
- Produces: `fun <T> takeLatestTransactions(items: List<T>, limit: Int = 5): List<T> = items.take(limit)` in `ui/home/HomeTransactionPreview.kt` or util
- Home params: `onViewAllTransactions: () -> Unit`, `slipImageStore: SlipImageStore`
- Use shared `TransactionListItem` for thumbs

- [ ] **Step 1: Unit test takeLatest**

```kotlin
@Test
fun capsAtFive() {
    assertEquals(5, takeLatestTransactions((1..10).toList()).size)
}
```

- [ ] **Step 2: Home uses `data.items.let { takeLatestTransactions(it) }` + TextButton “ดูทั้งหมด”**

- [ ] **Step 3: Wire `onViewAllTransactions` → navigate `"tx"`**

- [ ] **Step 4: Tests + compile PASS**

- [ ] **Step 5: Commit**

```bash
git commit -m "feat(android): limit Today transactions to 5 with view-all"
```

---

### Task 7: Wire save path — local copy + optional Cloudinary

**Files:**
- Modify: `apps/android/app/src/main/java/com/getmoney/app/ui/slip/AddSlipScreen.kt`
- Modify: `apps/android/app/src/main/java/com/getmoney/app/MainActivity.kt` / `AppNav.kt` (pass stores)
- Modify: delete path in Home/Tx to call `slipImageStore.delete(id)`

**Interfaces:**
- After successful `createSlipTransaction`, if `sourceImageUri != null`:
  1. `slipImageStore.saveFromUri(response.id, uri)`
  2. If `cloudUploadStore.isEnabled() && CloudinaryConfig.isConfigured`:
     - `uploader.upload(file)` → on success `repository.updateImageUrl(id, url)`
     - on failure: snackbar/message non-blocking
- Queue mode: each queue item has `QueuedSlip.uri` — pass that URI into save.
- Manual entry: skip image.
- Pick/share flows: retain last processed `Uri` in AddSlipScreen state for save.

- [ ] **Step 1: Track `pendingImageUri` in AddSlipScreen** when processing image / queue item.

- [ ] **Step 2: Post-save hook as above.**

- [ ] **Step 3: Delete transaction also deletes local file.**

- [ ] **Step 4: Compile + unit tests**

- [ ] **Step 5: Commit**

```bash
git commit -m "feat(android): persist slip images locally and optional Cloudinary upload"
```

---

### Task 8: Account cloud upload toggle

**Files:**
- Modify: `apps/android/app/src/main/java/com/getmoney/app/ui/account/AccountScreen.kt`

**Interfaces:**
- Switch bound to `CloudUploadStore.enabled`
- `enabled = CloudinaryConfig.isConfigured` for the Switch interactivity; helper text when not configured: `"Cloud upload awaits Cloudinary credentials"`
- When configured: `"Upload slip images to Cloudinary after save"`

- [ ] **Step 1: UI section under Auto slip scan or new “Cloud images” block**

- [ ] **Step 2: Compile**

- [ ] **Step 3: Commit**

```bash
git commit -m "feat(android): Account toggle for Cloudinary slip upload"
```

---

### Task 9: Redeploy API + Android 1.8 APK

**Files:**
- Modify: `apps/android/app/build.gradle.kts` → `versionCode = 9`, `versionName = "1.5"` wait — use **`1.8`** / **9**
- Deploy: use existing `scripts/remote-redeploy-api.sh` pattern to `tmd.deals`

- [ ] **Step 1: Bump version**

- [ ] **Step 2: Run Android unit tests + API tests**

```bash
cd apps/api && cargo test
cd apps/android && .\gradlew.bat :app:testDebugUnitTest
```

- [ ] **Step 3: Redeploy API** (SSH + remote-redeploy) so production has `image_url` column.

- [ ] **Step 4: `assembleDebug` → copy `getmoney-v1.8-tmd.deals.apk` (do not commit APK)**

- [ ] **Step 5: Commit version bump**

```bash
git commit -m "chore(android): bump to 1.8 for transactions and slip images"
```

- [ ] **Step 6: Manual checklist**
  1. Save slip → Tx shows thumb.  
  2. Today ≤5 + View all.  
  3. Cloud toggle off → no Cloudinary traffic.  
  4. Toggle disabled until credentials in gradle properties.

---

## Spec coverage self-check

| Spec requirement | Task |
|---|---|
| Tx tab full list + thumbs | 5 |
| Today 5 + View all | 6 |
| Local slip copy after save | 3, 7 |
| Display local then image_url | 5 |
| Cloudinary toggle default off | 4, 8 |
| API image_url | 1, 2 |
| No multipart to GetMoney API | 1, 7 |
| Credentials later via BuildConfig | 4, 9 |
| Delete cleans local file | 7 |
| Carbon UI | 5, 6, 8 |
| Version 1.8 | 9 |

## Consistency notes

- `SlipImageStore.fileFor` / `saveFromUri` / `delete` names used in Tasks 3, 5, 7.
- `CloudUploadStore` + `CloudinaryConfig.isConfigured` gate Task 7–8.
- `TransactionResponse.imageUrl` from Task 2 used everywhere.
