# Task 8 Report: Version bump, full tests, APK

**Branch:** `feat/auto-gallery-slip-scan`  
**Date:** 2026-07-28

## Status: Complete

## Version bump

- `apps/android/app/build.gradle.kts`: `versionCode = 6`, `versionName = "1.5"`

## Unit tests

Command: `.\gradlew.bat :app:testDebugUnitTest` (JAVA_HOME=jdk-17)

| Suite | Tests | Failures |
|---|---:|---:|
| AutoScanCoordinatorTest | 6 | 0 |
| AutoScanCursorLogicTest | 2 | 0 |
| SlipCandidateFilterTest | 4 | 0 |
| EmvQrParserTest | 4 | 0 |
| SlipParserTest | 6 | 0 |
| SlipAmountValidationTest | 2 | 0 |
| **Total** | **24** | **0** |

Result: **BUILD SUCCESSFUL**

## APK

Command: `.\gradlew.bat :app:assembleDebug` → **BUILD SUCCESSFUL**

Copied to: `d:\10min\getmoney\getmoney-v1.5-tmd.deals.apk` (~73.5 MB)

APK **not** committed (per brief).

## Commit

```
chore(android): bump to 1.5 for auto gallery slip scan
```

File: `apps/android/app/build.gradle.kts` only.

## Manual checklist (device) — deferred

No physical device available in this agent session. Verify on device before release:

1. Fresh install / clear app data → open → grant photos → no huge backlog.
2. Save a new slip screenshot → kill/reopen app → banner “พบสลิปใหม่”.
3. Confirm → Home % updates; Skip → does not return next open.
4. Deny permission → no crash; Pick slip still works.
5. Toggle auto-scan off → no scan on open.

## Concerns

None from automated steps. Manual device checklist remains outstanding.

---

## Review fixes (2026-07-28)

**Status:** Complete

### Changes

1. **AddSlipScreen** — Skip button `enabled = !saving` in queue mode; blocks double-advance during 409 duplicate delay.
2. **AutoScanCoordinator** — `scanGeneration` (`AtomicInteger`) incremented on `clearQueue()`; in-flight `performScan` discards stale results before queue/banner writes.
3. **AccountScreen** — `runScanNow` wrapped in `try/finally` so `scanning = false` always.

### Tests

Command: `.\gradlew.bat :app:testDebugUnitTest :app:compileDebugKotlin` (JAVA_HOME=jdk-17)

| Suite | Tests | Failures |
|---|---:|---:|
| AutoScanCoordinatorTest | 7 | 0 |
| (all others unchanged) | 17 | 0 |
| **Total** | **25** | **0** |

Result: **BUILD SUCCESSFUL**

New test: `clearQueueDuringScanPreventsStaleQueueRefill`

### Commit

```
fix(android): guard queue advance during 409 and logout scan race
```

### Concerns

None. Cursor may still advance on stale scan completion (intentional scope limit).
