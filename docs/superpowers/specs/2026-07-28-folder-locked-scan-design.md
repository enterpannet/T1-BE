# Folder-locked gallery scan

**Date:** 2026-07-28  
**Approach:** A — scan only user-selected MediaStore buckets; no wide/all-folder scan  
**Empty selection:** do not scan until ≥1 folder selected

## Goal

Lock auto-scan / Scan now / history scan to **only** folders the user picks in Account. Remove wide “scan every folder” behavior and related copy.

## Behavior

1. Persist selected bucket ids (existing `extra_bucket_ids` key; treat as **scan folders**, not optional extras).
2. `GallerySlipScanner.listNewImages`: if `bucketIds` empty → return empty list. Else MediaStore query with `DATE_ADDED > ? AND BUCKET_ID IN (?,…)`.
3. `AutoScanCoordinator.performScan`: if no selected buckets, skip intake and do not advance cursor (or advance only when images were inspected — prefer **no cursor advance** when skipped for empty folders so first selection isn’t stuck).
4. Account UI:
   - Title: **โฟลเดอร์ที่สแกน** (required)
   - Helper: สแกนเฉพาะโฟลเดอร์ที่เลือกเท่านั้น เลือกอย่างน้อย 1 โฟลเดอร์
   - Empty selected: “ยังไม่ได้เลือก — จะยังไม่สแกน”
   - Disable Scan now / สแกนย้อนหลัง when none selected (or allow click → snackbar/message)
5. Remove copy: “Wide scan already covers all folders…”

## Non-goals

- Renaming DataStore key (keep `extra_bucket_ids` to avoid migration unless trivial alias)
- Changing batch size / OCR filter
- Picking folders outside MediaStore albums

## Tests

- Scanner with empty buckets → empty list
- Scanner with bucket filter → selection includes `BUCKET_ID IN`
- Coordinator with empty buckets → queue empty, cursor unchanged

## Version

Bump Android app + ship APK.
