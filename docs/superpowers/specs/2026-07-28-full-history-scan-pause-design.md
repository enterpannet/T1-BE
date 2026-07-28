# Full history scan (continuous batches) + pause / stop / resume

**Date:** 2026-07-28  
**Status:** Approved; implemented in v1.48  
**App:** GetMoney Android (`apps/android`)

## Problem

“สแกนย้อนหลังทั้งหมด” today resets the cursor then runs **one** batch of `BATCH_LIMIT` (100). Users with hundreds of slips (e.g. 513) must reopen/tap repeatedly. There is no pause or stop UI — only cancel via clearing the queue.

## Goals

1. **สแกนย้อนหลังทั้งหมด** scans **every** image in the selected folders, in continuous batches of 100 (e.g. 100×5 + 13).
2. **Aggregate** saved / skipped / need-review / failed across all batches; show **one** summary when the run finishes or is stopped.
3. **Pause** and **Stop** as separate controls; **Resume** continues the same run.
4. **สแกนตอนนี้** stays a **single** batch of up to 100 from the current cursor (unchanged product behavior).

## Non-goals

- Surviving process death with WorkManager (pause only while the app process is alive).
- Changing OCR/parser quality.
- Removing `BATCH_LIMIT` or loading all URIs into memory at once.
- Changing ZIP export of review slips beyond “include everything accumulated in this run.”

## Decisions (from user)

| Topic | Choice |
|-------|--------|
| Controls | Both **Pause** and **Stop** |
| Which button loops to completion | Only **สแกนย้อนหลังทั้งหมด** |
| Approach | In-process loop in `AutoScanCoordinator` (not WorkManager) |

## Behavior

### สแกนย้อนหลังทั้งหมด

1. Reset cursor to beginning of history (`0`).
2. Count images in selected buckets → `overallTotal` (e.g. 513).
3. Loop:
   - List next ≤100 images after cursor.
   - OCR/QR + optional auto-save (same as today’s batch).
   - Advance cursor after each **completed** batch.
   - Accumulate counters + append review/failed queue items.
   - Continue while the listed batch is non-empty (typically until a batch returns fewer than 100, then one more empty → stop).
4. On natural completion: phase `Done`, publish aggregated `AutoSaveSummary` once, keep exportable review list for the whole run.

### สแกนตอนนี้ / session scan

- Still one `performScan` batch (≤100). No multi-batch loop.
- Progress remains batch-scoped (`current/total` for that batch).
- Pause/Stop may still apply if a long single batch is running (same controls on the banner).

### Pause

- Cooperative: checked between images (and between persist items), same places as today’s generation cancel.
- Phase becomes `Paused`.
- Cursor: last **fully completed** batch already advanced; mid-batch images not yet processed stay after cursor (do **not** advance cursor for partial batch on pause).
- Queue / counters / exportable review list retained in memory.
- UI: **ต่อ** + **หยุด**.

### Resume

- Continues the **same** full-history (or single-batch) run from the next unprocessed image.
- Does not reset counters or clear the accumulated queue.
- If the run was full-history, remaining work still loops batches until done.

### Stop

- Ends the run immediately (after current image checkpoint).
- Publishes aggregated summary for work done so far.
- Cursor remains at last completed batch advance (partial batch not committed) — next **สแกนตอนนี้** continues from there.
- Does **not** clear the review queue (user may still review / ZIP).
- Distinct from logout/`clearQueue()` which cancels **and** clears the queue.

### Progress UI

Banner (and Account status line) for a full-history run:

- `กำลังอ่าน 237/513 · สำเร็จ N · ข้าม N · ต้องตรวจ N` (overall indices).
- Optional subtle batch hint is **not** required for v1.
- Actions while running: **พัก** | **หยุด**
- While paused: **ต่อ** | **หยุด**
- While Done: existing dismiss / ZIP entry points unchanged.

Copy on Account for full history: remove “เปิดแอปหรือกดซ้ำเพื่อชุดถัดไป”; say it will scan all files in continuous batches of 100.

## API / model changes

### `ScanProgress`

Add:

- `Phase.Paused`
- `overallCurrent: Int` — images finished in this run (OCR inspected)
- `overallTotal: Int` — from `countImages` at run start (0 if unknown / single-batch without count)
- `paused: Boolean` or rely on `phase == Paused`
- `isActive`: true for Listing | Reading | Saving | **Paused** (banner stays visible while paused)

`statusLine()`:

- Reading/Saving: prefer `overallCurrent/overallTotal` when `overallTotal > 0`, else batch `current/total`.
- Paused: `พักไว้ · อ่านแล้ว overallCurrent/overallTotal · …counters`

### `AutoScanCoordinator`

| Method | Role |
|--------|------|
| `resetAndScanAllHistory` | Reset cursor → run **multi-batch** loop with overall totals |
| `runScanNow` / `runScanIfNeeded` | Single batch (existing) |
| `pauseScan()` | Request pause |
| `resumeScan()` | Clear pause; continue if a run is waiting |
| `stopScan()` | End run, emit aggregated summary, keep queue |

Implementation sketch:

- `performScan` refactored to process one batch and return batch stats + whether more may exist (`images.size == BATCH_LIMIT`).
- `performFullHistoryScan` loops, accumulates, honors pause/stop/generation.
- Pause via `MutableStateFlow` / `Mutex` + suspend until resume/stop (not only `scanGeneration`, which remains **cancel/clear**).

### Aggregation

Running totals: `saved`, `skipped` (duplicates + non-slip), `needReview`, `failed`.  
Single `AutoSaveSummary` written to `_lastAutoSaveSummary` on Done or Stop (not after every batch).  
`_exportableReviewSlips` / `_queue`: append across batches for the run (replace-at-end-of-run is OK if mid-run UI reads the accumulating list).

## UI touch points

- `AppNav` progress banner: Pause / Resume / Stop.
- `AccountScreen`: update full-history helper text; disable conflicting actions while `isActive` (including Paused); show Pause/Resume/Stop if banner is not enough on Account.
- Snackbar: still one shot via `consumeAutoSaveSummary()` after Done/Stop.

## Persistence

- Cursor in DataStore only (unchanged).
- Pause state is **in-memory**; killing the app drops pause — next open uses cursor (safe resume of *progress through gallery*, not of an in-flight “Paused” UI state).

## Testing

- Full-history with 250 mock images → 3 batches; one aggregated summary; cursor at end.
- Pause mid-batch → no cursor advance for partial batch; resume completes; counters additive.
- Stop mid-run → summary for partial work; queue retained; generation not required.
- `runScanNow` still processes at most one batch.
- `clearQueue` still aborts and clears (regression).
- `ScanProgress.statusLine` for Paused / overall totals.

## Rollout

- Bump Android `versionName` / `versionCode` when shipping APK after implementation.
- No API/backend changes.
