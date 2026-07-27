# Task 3 Report: SlipImageStore (local files)

## Status

**Complete.** `SlipImageStore` persists slip images under `{rootDir}/slips/{transactionId}.jpg` with injectable `File` root for tests and `Context` constructor for production. All unit tests PASS.

## Commits

| SHA | Message |
|-----|---------|
| `fb058ea` | `feat(android): local SlipImageStore for transaction thumbnails` |

## Changes

### SlipImageStore (`SlipImageStore.kt`)

- `SlipImageStore(rootDir: File)` — testable constructor (no ContentResolver)
- `SlipImageStore(context: Context)` — uses `context.filesDir` + `contentResolver`
- `suspend fun saveFromUri(transactionId, source): File?` — copies bytes on `Dispatchers.IO`; handles `file://` and `content://` URIs
- `fun fileFor(transactionId): File?` — returns existing `{id}.jpg` or null
- `fun delete(transactionId)` — removes stored file if present

### Tests (`SlipImageStoreTest.kt`)

- `saveAndResolveRoundTrip` — save via file URI, resolve with `fileFor`
- `fileForReturnsNullWhenMissing`
- `deleteRemovesStoredFile`
- `saveOverwritesExistingFile`

Robolectric required for `android.net.Uri` in JVM unit tests.

## Tests

```text
cd apps/android
JAVA_HOME=C:\Program Files\Java\jdk-17
.\gradlew.bat :app:testDebugUnitTest --tests com.getmoney.app.data.slipimage.SlipImageStoreTest
BUILD SUCCESSFUL — 4 tests, 0 failures
```

## Interfaces Delivered

| Interface | Status |
|-----------|--------|
| `SlipImageStore(context: Context)` | ✅ |
| `SlipImageStore(rootDir: File)` | ✅ |
| `saveFromUri(transactionId, source): File?` | ✅ |
| `fileFor(transactionId): File?` | ✅ |
| `delete(transactionId)` | ✅ |
| Directory `filesDir/slips/` | ✅ |
| Filename `{transactionId}.jpg` | ✅ |

## Concerns

1. **`saveFromUri` returns null on failure** — callers (Task 7) should treat null as non-fatal; no exception surface.
2. **No ContentResolver in File ctor** — tests use `file://` URIs only; production uses Context ctor for gallery/camera `content://` URIs.
3. **Always `.jpg` extension** — source PNG/WebP bytes are copied as-is; Coil accepts arbitrary image bytes in a `.jpg` file.

## Next Task

Task 4: Cloudinary config, uploader, and opt-in `CloudUploadStore`.

---

## Hardening (2026-07-28)

**Commit:** `fix(android): harden SlipImageStore cancel and failed-copy cleanup`

### Fixes

1. **CancellationException** — rethrown from `saveFromUri`; only other exceptions return null.
2. **Atomic save** — copy to temp file in `slips/`, then `renameTo` destination; temp deleted on failure so `fileFor` never sees a truncated `.jpg`.

### Tests

```text
cd apps/android
JAVA_HOME=C:\Program Files\Java\jdk-17
.\gradlew.bat :app:testDebugUnitTest --tests com.getmoney.app.data.slipimage.SlipImageStoreTest
BUILD SUCCESSFUL — 4 tests, 0 failures
```
