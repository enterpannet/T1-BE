# Task 6 Report: Transactions + slip fingerprint

## Status

Complete on `feat/getmoney-mvp`.

## Commit

- `38bec1c feat(api): transactions CRUD with slip dedupe fingerprint`

## TDD evidence

- RED: `cargo test --test transactions_api` failed because
  `getmoney_api::transactions` did not exist.
- GREEN: the transaction test target passed all 3 tests after implementation.

## Implemented

- Stable SHA-256 slip fingerprint with trimmed fields and lowercase bank.
- Authenticated, user-scoped list/create/patch/delete transaction routes.
- Optional `from`/`to` filtering and decimal-string JSON responses.
- Database-enforced per-user slip deduplication mapped to
  `409 {"error":"slip already recorded"}`.

## Verification

- `cargo test --all`: 11 passed, 0 failed.
- IDE lint diagnostics: no errors.

## Concerns

- Repository-wide `cargo fmt --check` still reports pre-existing formatting
  differences in auth files outside Task 6; Task 6 files were formatted directly.

## Review follow-up

- PATCH now maps only database unique-constraint violations to
  `409 {"error":"slip already recorded"}`; all other database errors remain
  `AppError::Db` responses.
- PATCH leaves the original `slip_fingerprint` unchanged so transaction edits
  cannot silently alter the slip identity used for deduplication.
- Added endpoint-level regressions for a forced PATCH unique violation and for
  fingerprint preservation after editing slip fields.
- RED: both regressions failed against `38bec1c` (500 instead of 409; fingerprint
  changed).
- GREEN: `cargo test --test transactions_api --manifest-path apps/api/Cargo.toml`
  passed 5 tests, 0 failed.
