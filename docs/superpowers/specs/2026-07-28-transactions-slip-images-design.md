# GetMoney — Full Transactions + Local/Cloud Slip Images Design

**Date:** 2026-07-28  
**Status:** Approved for implementation planning  
**Surface:** Android UI + API `image_url` + Cloudinary client path (opt-in)  
**Related:** Auto gallery slip scan (`2026-07-28-auto-gallery-slip-scan-design.md`)

## Problem

Today’s Home lists all of today’s transactions without a dedicated Transactions surface, and slip images are discarded after OCR/QR. Users want a full transaction history with thumbnails, short previews (5 items) elsewhere, and an optional path to upload images to Cloudinary later without changing the default privacy model.

## Goals

- Add a **Tx** bottom tab with the full transaction list (newest first), each row showing a slip thumbnail when available.
- **Today** shows only the **latest 5** transactions plus a **View all** affordance to Tx.
- After a successful slip save, **copy the image into app-private storage** keyed by `transactionId`.
- Prefer local file for display; fall back to API `image_url` if local missing.
- Prepare **Cloudinary upload** behind an Account toggle (**default off**). Credentials arrive later via env/config; no upload until toggle on **and** config present.
- API stores nullable `image_url` only (no multipart image upload to GetMoney API in this phase).

## Non-goals

- Mandatory cloud upload for every slip.
- Multipart/image body on GetMoney API.
- Bulk backfill of historical slips to Cloudinary.
- iOS.
- Changing Summary/Budget into transaction galleries (only Today gets the 5-item preview in this phase unless already listing txs).

## Decisions

| Topic | Choice |
|---|---|
| Navigation | Today \| **Tx** \| Summary \| Budget \| Account |
| Today list | Latest **5** + View all → Tx |
| Image default | Local app storage only |
| Cloud | Settings toggle, **default off**; Cloudinary when configured |
| API | `image_url: string?` on transactions; PATCH/create can set it after client upload |

## Architecture

```
AddSlip confirm success
  → POST /transactions → id
  → SlipImageStore.save(id, sourceUri)   // always (slip source)
  → if uploadEnabled && cloudinaryConfigured:
        CloudinaryUploader.upload(file) → url
        PATCH /transactions/{id} { image_url }

Tx / Today rows
  → resolve image: local file(id) ?: image_url ?: placeholder
```

### Android components

1. **`SlipImageStore`**  
   - Path: `context.filesDir/slips/{transactionId}.jpg` (or original extension).  
   - `suspend fun save(transactionId: Uuid, source: Uri)`  
   - `fun fileFor(transactionId: Uuid): File?`  
   - `suspend fun delete(transactionId: Uuid)` on transaction delete.

2. **`CloudinaryConfig` / `CloudinaryUploader`**  
   - Reads `BuildConfig` or remote-safe constants: `CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_UPLOAD_PRESET` (unsigned preset for MVP).  
   - Empty/missing → `isConfigured == false`.  
   - Upload via HTTPS multipart to Cloudinary unsigned endpoint; return secure URL string.  
   - No secrets in APK beyond unsigned preset (acceptable for unsigned uploads); document that signed uploads can move to API later.

3. **`UploadPreferences` (DataStore)**  
   - `cloudUploadEnabled: Boolean` default `false`.

4. **`TransactionsScreen`**  
   - Loads `GET /transactions` (existing list API with optional range; default recent/all reasonable page).  
   - Row: thumbnail, amount, spent_at, bank/note; edit/delete reuse Home dialogs or shared components.  
   - Tap thumbnail → full-screen/local viewer (simple).

5. **`HomeScreen`**  
   - Cap list to 5; add TextButton/link “ดูทั้งหมด” → navigate Tx tab.

6. **`AccountScreen`**  
   - Switch “Upload slip images to cloud”.  
   - If `!CloudinaryConfig.isConfigured`, switch disabled + helper text: wait for cloud setup / credentials.

7. **Nav**  
   - Insert `tx` tab between today and summary.

### API / DB

1. Migration: `ALTER TABLE transactions ADD COLUMN image_url TEXT NULL`.  
2. `TransactionResponse.image_url: Option<String>`.  
3. `CreateTransactionRequest` / `PatchTransactionRequest`: optional `image_url`.  
4. Entity + handlers updated; no change to fingerprint rules.  
5. Redeploy API when shipping.

### Cloudinary wiring (deferred credentials)

- Document required env for Android build or `local.properties` / CI secrets:
  - `CLOUDINARY_CLOUD_NAME`
  - `CLOUDINARY_UPLOAD_PRESET`
- Until set, toggle stays disabled; local images still work.
- When user provides credentials: inject into `build.gradle.kts` `buildConfigField`s and redeploy APK; optionally store cloud name/preset in server and fetch — **YAGNI: BuildConfig for this phase**.

## Data flow (save slip)

1. User confirms slip (pick / share / auto-scan queue).  
2. App `POST /transactions` with structured fields only.  
3. On success, copy image bytes to `SlipImageStore`.  
4. If cloud enabled + configured: upload → `PATCH` `image_url`. Failure of upload: keep local file; show non-blocking snackbar (do not roll back transaction).  
5. Manual entry: no image file.

## Error handling

| Case | Behavior |
|---|---|
| Local copy fails | Transaction still saved; row shows placeholder |
| Cloud upload fails | Local kept; snackbar; retry optional later (not required MVP) |
| Toggle on, no config | Switch disabled |
| Delete transaction | Delete local slip file; API delete as today |
| Remote `image_url` but no local | Coil/Glide load URL when toggle ever produced URL |

## Testing

- Unit: `SlipImageStore` path helpers (with temp dir / Robolectric if needed).  
- Unit: Home list capping helper `take(5)`.  
- API: migration + create/patch/list include `image_url`.  
- Manual: save slip → Tx shows thumb; Today shows ≤5; cloud off → no network to Cloudinary.

## Success criteria

- Tx tab lists full history with local thumbs for new slips.  
- Today shows 5 + View all.  
- Cloud toggle default off; no upload without credentials.  
- API `image_url` present and updatable.  
- Carbon UI retained (`DESIGN-ibm.md`).

## Implementation notes

- Reuse existing `TransactionApi` list/create/patch/delete.  
- Prefer Coil for async image loading if not already present; else BitmapFactory for local files only until Coil added.  
- Version bump Android when shipping (e.g. 1.8).  
- Ship API migration to `tmd.deals` with Android release that depends on `image_url`.
