# Task 5 Report: Budget Math and Endpoints

## Status

Implemented Task 5 only: calendar-aware budget math, budget-month salary upsert,
and user-owned fixed-expense CRUD with computed figures in every response.

## Changes

- Added `budget::math::{days_in_month, compute_budget}` and string-serialized
  decimal figures.
- Added authenticated `GET`/`PUT /budget/months/{yyyy-mm}` routes.
- Added authenticated list/create/get/patch/delete fixed-expense routes.
- Budget responses include salary, ordered fixed expenses, `fixed_total`,
  `remaining`, `daily_allowance`, and `days_in_month`.
- Salary upserts use PostgreSQL conflict handling on `(user_id, year, month)`.

## TDD Evidence

### Math RED

Command:

```text
$env:CARGO_TARGET_DIR='D:\10min\getmoney\apps\api\target'; cargo test --test budget_math --manifest-path apps/api/Cargo.toml
```

Expected compile failure before the module existed:

```text
error[E0433]: cannot find `budget` in `getmoney_api`
could not compile `getmoney-api` (test "budget_math")
```

### Math GREEN

The same command passed after implementing `budget/math.rs`:

```text
running 2 tests
test february_leap ... ok
test july_31_days_daily_allowance ... ok
test result: ok. 2 passed; 0 failed
```

### API RED

Command:

```text
$env:CARGO_TARGET_DIR='D:\10min\getmoney\apps\api\target'; cargo test --test budget_api --manifest-path apps/api/Cargo.toml
```

Before mounting the handlers, the first budget PUT returned the expected RED:

```text
assertion `left == right` failed
  left: 404
 right: 200
test result: FAILED. 0 passed; 1 failed
```

### API GREEN

After implementation, the focused CRUD test passed:

```text
running 1 test
test budget_month_and_fixed_expense_crud_recomputes_decimal_figures ... ok
test result: ok. 1 passed; 0 failed
```

## Full Verification

Command:

```text
$env:CARGO_TARGET_DIR='D:\10min\getmoney\apps\api\target'; cargo test --manifest-path apps/api/Cargo.toml
```

Result: all 8 integration/unit tests passed with no failures; library, binary,
and doc-test targets also passed. Cursor diagnostics reported no linter errors
in the changed Rust files.

## Concerns

- Integration coverage requires the local PostgreSQL service and
  `apps/api/.env`.
- Money values are exact decimal strings; `daily_allowance` is intentionally
  not rounded because the task's math contract compares exact Decimal division.
