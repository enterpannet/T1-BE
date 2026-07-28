# Full History Scan + Pause/Stop Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make “สแกนย้อนหลังทั้งหมด” scan every gallery image in continuous batches of 100 with one aggregated summary, and add Pause / Resume / Stop controls.

**Architecture:** Extend `AutoScanCoordinator` with a multi-batch loop for full-history only; cooperative pause/stop checkpoints beside existing `scanGeneration` cancel. `ScanProgress` gains overall counters + `Paused`. Banner/Account UI bind the new controls.

**Tech Stack:** Kotlin coroutines, StateFlow, Jetpack Compose, JUnit4 + Robolectric

## Global Constraints

- Only `resetAndScanAllHistory` loops to completion; `runScanNow` / `runScanIfNeeded` stay single-batch (≤100).
- `BATCH_LIMIT` remains 100; do not load all URIs at once.
- Pause is in-memory (process lifetime); cursor in DataStore still advances per completed batch.
- Stop keeps review queue; `clearQueue` still cancels and clears.
- Thai UI copy for controls: พัก / ต่อ / หยุด.
- Spec: `docs/superpowers/specs/2026-07-28-full-history-scan-pause-design.md`

---

### Task 1: ScanProgress overall + Paused

**Files:**
- Modify: `apps/android/app/src/main/java/com/getmoney/app/autoscan/ScanProgress.kt`
- Test: `apps/android/app/src/test/java/com/getmoney/app/autoscan/ScanProgressTest.kt`

**Produces:**
- `Phase.Paused`
- `overallCurrent: Int = 0`, `overallTotal: Int = 0`
- `isActive` true for Listing|Reading|Saving|Paused
- `statusLine()` uses overall when `overallTotal > 0`; Paused prefix `พักไว้`

- [ ] Extend `ScanProgress` + tests for overall reading line and paused line
- [ ] Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.getmoney.app.autoscan.ScanProgressTest"`

---

### Task 2: Coordinator multi-batch + pause/stop/resume

**Files:**
- Modify: `apps/android/app/src/main/java/com/getmoney/app/autoscan/AutoScanCoordinator.kt`
- Test: `apps/android/app/src/test/java/com/getmoney/app/autoscan/AutoScanCoordinatorTest.kt`

**Produces:**
- `fun pauseScan()`, `fun resumeScan()`, `fun stopScan()`
- `resetAndScanAllHistory` → continuous batches until empty
- Single aggregated `AutoSaveSummary` on Done or Stop
- Mid-batch pause/stop does not advance cursor for that incomplete batch

**Fake scanner:** support cursor-filtered listing so 250 images → 3 list calls when `BATCH_LIMIT=100`.

- [ ] Tests: full history multi-batch; pause+resume; stop mid-run; runScanNow still one batch
- [ ] Implement loop + pause gate + stop
- [ ] Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.getmoney.app.autoscan.*"`

---

### Task 3: Banner + Account UI

**Files:**
- Modify: `apps/android/app/src/main/java/com/getmoney/app/ui/nav/AppNav.kt`
- Modify: `apps/android/app/src/main/java/com/getmoney/app/ui/account/AccountScreen.kt`

- [ ] Banner: พัก|หยุด while active (not Paused); ต่อ|หยุด while Paused; progress uses overall when set
- [ ] Account helper text: scan all in batches of 100 continuously (no “กดซ้ำ”)
- [ ] Account row of pause/resume/stop when progress active

---

### Task 4: Version bump + APK

**Files:**
- Modify: `apps/android/app/build.gradle.kts` → versionName `1.48`, versionCode `49`
- Copy APK to `getmoney-v1.48-tmd.deals.apk`

- [ ] Assemble debug APK and copy to repo root
