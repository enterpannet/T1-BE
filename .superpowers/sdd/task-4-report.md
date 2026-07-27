# Task 4 Report: Auth HTTP Endpoints

## Status

Implemented Task 4 only: registration, login, refresh rotation, single-token
logout, logout-all, the `AuthUser` bearer extractor, route mounting, and auth API
integration coverage.

## Changes

- Added `apps/api/src/auth/handlers.rs` with token-pair persistence and all five
  auth handlers.
- Added `apps/api/src/auth/extractor.rs` to decode bearer access tokens into
  `AuthUser(Uuid)`.
- Mounted `/auth/register`, `/auth/login`, `/auth/refresh`, `/auth/logout`, and
  `/auth/logout-all` in `apps/api/src/lib.rs`.
- Added `apps/api/tests/auth_api.rs` covering successful registration,
  duplicate-email conflict, bad-password rejection, refresh-token rotation, and
  logout-all revocation.

## TDD Evidence

### RED

Command:

```text
$env:CARGO_TARGET_DIR='D:\10min\getmoney\apps\api\target'; cargo test --test auth_api --manifest-path apps/api/Cargo.toml
```

The test compiled and failed at the first registration assertion because the
route did not exist:

```text
assertion `left == right` failed
  left: 404
 right: 200
test result: FAILED. 0 passed; 1 failed
```

An earlier run using Cursor's shared temporary target directory hit Windows
`Access is denied` while replacing the test executable. Setting
`CARGO_TARGET_DIR` to the repository-local target produced the expected RED
failure above.

### GREEN

After implementing the endpoints and extractor, the focused test passed:

```text
running 1 test
test auth_endpoints_register_login_rotate_and_logout_all ... ok
test result: ok. 1 passed; 0 failed
```

### Full verification

Command:

```text
$env:CARGO_TARGET_DIR='D:\10min\getmoney\apps\api\target'; cargo test --manifest-path apps/api/Cargo.toml
```

Result: all 3 integration tests passed (`auth_api`, `auth_unit`, and
`entities_contract`), with no failures; library, binary, and doc-test targets
also passed. Cursor diagnostics reported no linter errors in changed Rust files.

## Behavioral Results

- Register returns `200` with access and refresh tokens.
- Duplicate email returns `409`.
- Incorrect password returns `401`.
- Refresh returns a new token pair and revokes the old refresh token.
- Logout revokes the supplied refresh token.
- Logout-all requires a valid bearer access token and revokes all active
  refresh tokens for that user.

## Concerns

- Integration tests require the local Postgres service and `apps/api/.env`.
- Expired/revoked refresh tokens are retained for later cleanup rather than
  deleted; cleanup is outside Task 4.

## Important Review Fixes

- Registration now translates a database unique-constraint violation into
  `AppError::Conflict`, so concurrent requests for the same email return one
  `200` and one `409` instead of exposing a database `500`.
- Refresh rotation now conditionally revokes the old token and inserts its
  replacement in one SeaORM transaction. An insertion failure explicitly rolls
  back the revocation, leaving the old refresh token usable.
- Added regression coverage for concurrent duplicate registration and forced
  replacement-token insertion failure. Before the fixes, these tests observed
  `[200, 500]` and an unusable old token (`401`), respectively.

## Review Fix Verification

Command:

```text
cargo test --test auth_api --manifest-path apps/api/Cargo.toml
```

Result:

```text
running 3 tests
test failed_refresh_insert_rolls_back_old_token_revocation ... ok
test concurrent_registration_returns_conflict_instead_of_server_error ... ok
test auth_endpoints_register_login_rotate_and_logout_all ... ok
test result: ok. 3 passed; 0 failed
```
