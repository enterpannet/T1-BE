# Task 6 Report: Home banner + AddSlip queue mode

## Status

Complete. Home shows Thai pending-slip banner when queue non-empty and not dismissed; ดู opens AddSlip queue mode; ภายหลัง dismisses banner and clears in-memory queue. AddSlip skips Pick in queue mode, shows i/N progress, Skip/Confirm/Cancel, advances on save/409/skip, exits when empty.

## Changes

- **HomeScreen.kt**: Added `pendingSlipCount`, `onReviewPendingSlips`, `onDismissPendingBanner`; Carbon banner with primary ดู + TextButton ภายหลัง.
- **AddSlipScreen.kt**: Added `autoScanCoordinator`, `startInQueueMode`; prefills from `peekCurrent()`, tracks `queueIndex/queueInitialTotal`, Skip advances via `skipCurrent()`, save success via `removeCurrentAfterSave()`, 409 shows message then `skipCurrent()` and next.
- **AppNav.kt**: Collects `queue` + `bannerDismissed`; wires Home callbacks; route `add_slip?queue={queue}` with bool nav arg.

## Tests

```text
$env:JAVA_HOME='C:\Program Files\Java\jdk-17'; .\gradlew.bat :app:testDebugUnitTest
BUILD SUCCESSFUL — all unit tests pass
```

## Commit

```text
bf2753b feat(android): auto-scan Home banner and confirm queue UX
```

## Concerns

- ~~409 duplicate message may flash briefly before next slip loads (spec: message then next).~~ Fixed: 1.5s delay before queue advance on 409.
- Queue mode uses `createSlipTransaction` only (no manual entry in queue flow).
- Banner reappears on next scan session when new slips found (`bannerDismissed` reset in coordinator `performScan`).

## Fix (Task 6 review — 409 duplicate message)

**Problem:** On `DuplicateSlipException` (409) in AddSlip queue mode, `error` was set then `advanceQueueAfterSkip()` cleared it in the same coroutine turn — user never saw the duplicate message.

**Fix:** In `AddSlipScreen.kt` save `onFailure`, after setting `error` for queue-mode 409, `delay(1500)` then call `advanceQueueAfterSkip()`. Error stays visible for ~1.5s before advancing to next slip or `onDone`.

**Tests:** No AddSlipScreen unit test exists; logic fix only.

```text
$env:JAVA_HOME='C:\Program Files\Java\jdk-17'; cd apps/android; .\gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest
BUILD SUCCESSFUL in 5s — compile + all unit tests pass
```

**Commit:** `fix(android): show duplicate-slip message before queue advance`
