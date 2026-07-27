# GetMoney — Auto Gallery Slip Scan Design

**Date:** 2026-07-28  
**Status:** Approved for implementation planning  
**Parent:** Phase 2 of `2026-07-27-getmoney-design.md`  
**Surface:** Android only (API unchanged; images never leave the device)

## Problem

Users save bank transfer slips into different gallery folders depending on bank/app (Screenshots, bank-named albums, Downloads, etc.). Manually picking each slip is friction. After QR-first + OCR on-device intake exists, the app should discover new slip-like images when opened and queue them for confirmation.

## Goals

- On app open (logged in), optionally auto-scan for **new** gallery images since the last successful scan cursor.
- Default scan is **wide** (MediaStore images), not bank-folder-specific; user may **add extra folders** later to bias/include specific buckets.
- Candidates run through existing `SlipIntake` (QR → OCR fallback) on device.
- Present matches as a **confirm queue, one slip at a time** (confirm or skip).
- Never upload images; API still receives structured transaction fields only.
- User can disable auto-scan and still use pick/share manually.

## Non-goals

- Background scan while the app is killed; notification-driven intake.
- Auto-save without user confirm.
- Server-side OCR / PaddleOCR / VLM; Bank/PromptPay verification APIs.
- iOS.
- First-run backlog of the entire gallery history.

## Decisions (from brainstorming)

| Topic | Choice |
|---|---|
| Trigger | Scan when opening the app (logged-in MainShell), not continuous background |
| Folder scope | Hybrid: wide MediaStore default + optional user-added folders |
| “New” window | Images with `DATE_ADDED` (or equivalent) **greater than** `lastScanCursor` |
| Multi-match UX | Queue: confirm / skip one-by-one |
| First launch | Set `lastScanCursor = now` **without** scanning historical images |
| Dedup already saved | Existing API `409` slip fingerprint; show message and advance queue |

## Architecture

```
MainShell (logged in)
  └─ AutoScanCoordinator
        ├─ AutoScanStore (DataStore): enabled, lastScanCursor, extraFolderIds
        ├─ GallerySlipScanner → List<Uri> (MediaStore query, max N/run)
        ├─ SlipIntake (existing QR → OCR)
        ├─ SlipCandidateFilter → queue of SlipDraft + Uri
        └─ UI: Home banner → AddSlip confirm queue
```

No API schema changes. Reuse `POST /transactions` with `source=slip`.

### Components

1. **`AutoScanStore`**  
   - `autoScanEnabled: Boolean` — default `true`. Scan runs only when photo permission is granted; if denied or toggled off in Settings, coordinator no-ops.  
   - `lastScanCursorEpochSec: Long`  
   - `extraBucketIds: Set<String>` (MediaStore bucket/folder ids or relative paths the OS exposes)

2. **`GallerySlipScanner`**  
   - Query `MediaStore.Images` where `DATE_ADDED > lastScanCursor`, order ascending, **limit 30 per run**.  
   - If `extraBucketIds` non-empty: still include wide results **or** union with folder-filtered queries (implementation: run one wide query; optionally also query those buckets for the same cursor window and merge/dedupe by URI). Default behavior remains wide-first.  
   - Requires `READ_MEDIA_IMAGES` (already in manifest) / legacy storage as needed by `minSdk`.

3. **`SlipCandidateFilter`**  
   Accept if any of:
   - `SlipIntake.Source.Qr` with a parseable amount, or
   - OCR draft has amount **and** slip-like signals (EMV/PromptPay QR payload present, or text keywords already used by `SlipParser` / bank labels).  
   Reject selfies, random screenshots without amount+slip signals.

4. **`AutoScanCoordinator`**  
   - On MainShell start (once per process session, or each resume if cursor advanced — prefer **once per cold/warm start** + manual “Scan now”).  
   - If permission missing → skip quietly (no crash); pick/share still work.  
   - Advance **`lastScanCursor` to scan-start time** when the scan **query finishes** (not when the user finishes the confirm queue), so skipped images do not reappear. Optionally keep an in-session skip set; not required across restarts if cursor advanced.  
   - Cap work: max 30 images inspected; show progress only if lasting > ~1s.

5. **UI**  
   - **Home banner:** “พบสลิปใหม่ N ใบ” → Start queue / Later (Later dismisses banner for session; items remain until cursor already advanced — if cursor advanced at scan end, “Later” means drop the in-memory queue only).  
   - **Confirm queue:** reuse `AddSlipScreen` / confirm fields; add **Skip**, show `i/N`, preserve source hint (QR vs OCR).  
   - **Account/Settings:** toggle Auto-scan, **Scan now**, manage extra folders (picker for album/bucket).

### Cursor semantics (explicit)

- **First run / no cursor:** write `lastScanCursor = now`; do **not** scan the past.  
- **After a scan run:** set `lastScanCursor = scanStartedAt` even if zero candidates, even if user never opens the queue.  
- **Manual pick/share:** does not move the gallery cursor (unless the same URI was also a candidate).

### Permissions

- Request `READ_MEDIA_IMAGES` when enabling auto-scan or on first MainShell if auto-scan enabled.  
- Denial → auto-scan off for session; settings can retry.

## Data flow

1. User opens app → coordinator checks enabled + permission.  
2. Scanner returns up to 30 new URIs.  
3. For each URI: `SlipIntake.process` → filter → append to queue.  
4. Home shows banner if queue non-empty.  
5. User confirms → existing create-slip API; on success next item; on 409 show duplicate message then next.  
6. Skip → next item.  
7. Queue empty → dismiss banner.

## Error handling

| Case | Behavior |
|---|---|
| Permission denied | No auto-scan; manual flows unchanged |
| MediaStore / OCR failure on one image | Skip that URI; continue |
| Empty candidates | Update cursor; no banner |
| Duplicate fingerprint (409) | Message; advance queue |
| User logs out | Cancel in-memory queue; store persists on device |

## Testing

- Unit: `SlipCandidateFilter` with QR-only, OCR-with-keywords, random text (reject).  
- Unit: cursor advance rules (first run, after scan, skip does not rewind).  
- Manual: save a new bank slip screenshot → open app → appears in queue; skip → does not return next open; confirm → Home % updates.  
- Manual: deny photos permission → no crash; pick image still works.

## Success criteria

- New slip image after last cursor → queued without manual Pick.  
- Confirm/Skip one-by-one works; Home updates after save.  
- Images never uploaded.  
- Auto-scan can be disabled; pick/share remain.  
- Extra folders can be added in settings (MediaStore bucket filter).

## Implementation notes

- Prefer Kotlin coroutines + DataStore.  
- Reuse `SlipIntake`, `EmvQrParser`, `SlipParser`, confirm UI.  
- Version bump Android when shipping (e.g. 1.5).  
- Keep Carbon UI patterns from `DESIGN-ibm.md`.
