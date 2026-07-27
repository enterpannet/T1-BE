# GetMoney — Personal Daily Budget App Design

**Date:** 2026-07-27  
**Status:** Approved for implementation planning  
**Approach:** Axum + SeaORM + PostgreSQL API · Kotlin Android · on-device OCR · IBM Carbon UI

## Problem

The user tracks monthly fixed obligations and salary in a spreadsheet, derives **เหลือใช้** and **ใช้ได้วันละ**, then wants a live answer to: *how much of today’s allowance have I already spent?* Spending evidence lives as bank transfer slip images on the phone. Manual spreadsheet updates lag behind real spending.

## Goals (MVP)

- Set a monthly budget like the spreadsheet: fixed payables + salary → remaining → daily allowance.
- Add spending by picking/sharing a slip image; OCR on device; confirm; sync amount to API (no image upload).
- Home shows today’s spent amount, daily allowance, and **% used**.
- Weekly and monthly summaries; edit/delete transactions; full fixed-expense list for the month.
- Auth: email/password, Argon2id, short-lived access + DB-backed refresh tokens, logout / logout-all.

## Non-goals (MVP)

- Automatic gallery/folder slip scanning (planned Phase 2).
- Direct “set daily allowance only” mode (planned Phase 2; architecture must not block it).
- Over-budget push notifications or trend charts.
- Uploading slip images to the server.
- iOS, web client, 2FA, email verification, password reset.

## Stack

| Layer | Choice |
|---|---|
| Android | Kotlin, Jetpack Compose, ML Kit Text Recognition |
| API | Rust, Axum |
| ORM / DB | SeaORM, PostgreSQL |
| Password hashing | Argon2id |
| UI system | IBM Carbon tokens from repo root `DESIGN-ibm.md` |

## Architecture

Monorepo layout:

```
apps/android/     # Kotlin client
apps/api/         # Rust Axum + SeaORM
DESIGN-ibm.md     # Carbon design tokens (existing)
docs/superpowers/specs/
```

### Primary flow

1. User registers/logs in → receives access JWT + refresh token.
2. User creates/edits budget month (salary + fixed expenses) → server computes `fixed_total`, `remaining`, `daily_allowance`.
3. User picks a slip → ML Kit OCR → parser extracts amount / datetime / bank / reference → user confirms or edits → `POST /transactions` with metadata only.
4. Home loads `GET /summary/today` → spent, allowance, percent, today’s list.

### Auth model

- Register/login with email + password; password stored as Argon2id hash.
- Access token: short-lived JWT (Bearer).
- Refresh token: opaque random value; only hash stored in `refresh_tokens`; rotatable on refresh.
- `POST /auth/logout` revokes current refresh token; `POST /auth/logout-all` revokes all for user.
- Android refreshes on 401; if refresh fails, clear session and return to login.

## Data model

### `users`
- `id` (UUID), `email` (unique), `password_hash`, `created_at`

### `refresh_tokens`
- `id`, `user_id`, `token_hash`, `expires_at`, `revoked_at` (nullable), `device_label` (optional), `created_at`

### `budget_months`
- `id`, `user_id`, `year`, `month` (1–12), `salary` (numeric)
- Unique `(user_id, year, month)`
- Derived (computed on write or read, not necessarily stored):  
  - `fixed_total = SUM(fixed_expenses.amount)`  
  - `remaining = salary - fixed_total`  
  - `daily_allowance = remaining / days_in_month(year, month)`

### `fixed_expenses`
- `id`, `budget_month_id`, `name`, `amount`, `sort_order`

### `transactions`
- `id`, `user_id`, `amount`, `spent_at` (timestamptz), `source` (`slip` | `manual`), `bank` (nullable), `note` (nullable), `slip_fingerprint` (nullable, unique per user when present), `created_at`

### Daily percent

```
today_spent = SUM(transactions.amount WHERE spent_at::date = today AND user_id = …)
percent_used = (today_spent / daily_allowance) * 100
```

If no budget month exists for current calendar month, Home must route user to budget setup (do not invent a default allowance).

### Slip deduplication

Fingerprint = stable hash of normalized `(amount, spent_at, bank, reference_text)` scoped per `user_id`. Duplicate insert → `409 Conflict` with clear message; client does not create a second row.

## API (MVP)

All money fields: decimal string or numeric JSON consistent across endpoints (prefer string decimals to avoid float drift).

### Auth
- `POST /auth/register` `{ email, password }`
- `POST /auth/login` `{ email, password }` → `{ access_token, refresh_token, expires_in }`
- `POST /auth/refresh` `{ refresh_token }` → new pair; old refresh revoked
- `POST /auth/logout` `{ refresh_token }`
- `POST /auth/logout-all` (auth required)

### Budget
- `GET /budget/months/{yyyy-mm}`
- `PUT /budget/months/{yyyy-mm}` `{ salary }` (upsert month shell)
- `GET /budget/months/{yyyy-mm}/fixed-expenses`
- `POST /budget/months/{yyyy-mm}/fixed-expenses` `{ name, amount, sort_order? }`
- `PATCH /budget/months/{yyyy-mm}/fixed-expenses/{id}`
- `DELETE /budget/months/{yyyy-mm}/fixed-expenses/{id}`

Budget responses include computed `fixed_total`, `remaining`, `daily_allowance`, `days_in_month`.

### Transactions
- `GET /transactions?from=&to=`
- `POST /transactions` `{ amount, spent_at, source, bank?, note?, reference? }`
- `PATCH /transactions/{id}`
- `DELETE /transactions/{id}`

### Summaries
- `GET /summary/today` → `{ daily_allowance, spent, remaining_today, percent_used, items[] }`
- `GET /summary/week` → spent in the current week vs allowance budget `daily_allowance × (days in that week that fall in the active budget month)`
- `GET /summary/month/{yyyy-mm}` → `{ salary, fixed_total, remaining, daily_allowance, spent_variable, percent_of_remaining }`

## Android screens (Carbon)

Theme from `DESIGN-ibm.md`: canvas white, surface-1 `#f4f4f4`, ink `#161616`, primary `#0f62fe`, 0px corners, 1px hairlines, no drop shadows, IBM Plex Sans (display weight 300).

| Screen | Purpose |
|---|---|
| Login / Register | Email + password |
| Home (Today) | Large spent / allowance, % bar, today’s list, CTA add slip |
| Add slip | Pick image → OCR preview → confirm/edit → save |
| Budget month | Salary + fixed expenses CRUD + computed remaining/daily |
| Summary | Tabs: week / month |
| Transactions | Edit/delete |
| Account | Logout, logout all devices |

Bottom nav (square Carbon style): Today · Summary · Budget · Account.

## OCR pipeline

1. User selects image from gallery (or share intent into app).
2. ML Kit extracts raw text.
3. Heuristic parser finds amount (largest plausible THB figure), datetime, bank/app name, reference id when present.
4. Confirmation UI always shown; user can override any field or switch to full manual entry.
5. Client POSTs structured fields only; image stays on device.

**Phase 2:** MediaStore observer / folder watch for new bank screenshots → same confirm flow (not auto-save without review).

## Error handling

| Case | Behavior |
|---|---|
| OCR fails / no amount | Manual entry path + semantic error styling |
| Duplicate slip (409) | Message: already recorded; no second row |
| Access expired | Silent refresh; on failure → login |
| No current budget | Block Home metrics; force budget setup |
| Network failure | Carbon error treatment + retry |

## Testing

- **API:** budget math (incl. 28/29/30/31-day months), Argon2 verify, refresh revoke/logout-all, fingerprint dedupe.
- **Android:** parser unit tests from fixture slip texts; smoke for Home and Add Slip.
- **Manual:** 2–3 real bank slip formats the user actually uses.

## Phase 2 (explicit backlog)

- Auto-scan new images in gallery folders.
- Alternate budget mode: set `daily_allowance` directly without fixed list.
- Over-budget notifications and trend charts.
- Richer multi-bank parser packs.

## Success criteria

1. User can mirror their spreadsheet month (fixed lines + salary) and see the same daily allowance logic.
2. After confirming a slip, Home % updates without typing the amount by hand (except OCR correction).
3. Logout-all invalidates other sessions via refresh revocation.
4. UI reads as Carbon: square chrome, IBM Blue accent only, Plex Sans light display.
