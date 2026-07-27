# GetMoney MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship a Kotlin Android + Rust API app that computes daily allowance from salary/fixed expenses and tracks today’s spend % from on-device OCR slip confirms (no image upload).

**Architecture:** Monorepo `apps/api` (Axum + SeaORM + PostgreSQL + Argon2id + JWT access / DB refresh tokens) and `apps/android` (Compose + ML Kit OCR → JSON to API). UI follows Carbon tokens in `DESIGN-ibm.md`.

**Tech Stack:** Rust 2021, Axum 0.8, SeaORM 1.x, PostgreSQL 16, argon2, jsonwebtoken, Kotlin 2.x, Jetpack Compose, ML Kit Text Recognition, OkHttp/Retrofit, DataStore.

**Spec:** `docs/superpowers/specs/2026-07-27-getmoney-design.md`

## Global Constraints

- Money fields in JSON: decimal **strings** (e.g. `"6100.00"`), never IEEE floats.
- Slip images never leave the device; API accepts structured transaction fields only.
- Password hashing: Argon2id only.
- Access JWT short-lived (~15m); refresh tokens opaque, only SHA-256 hash stored in DB.
- UI: IBM Carbon from `DESIGN-ibm.md` — primary `#0f62fe`, ink `#161616`, canvas `#ffffff`, corners `0px`, IBM Plex Sans, no drop shadows.
- MVP excludes: auto gallery scan, direct daily-allowance-only mode, 2FA, email verify, push alerts, charts, image upload.
- Week summary allowance = `daily_allowance × (count of days in that week that fall in the active budget month)`.
- No current budget month → Home must not invent allowance; force budget setup.

---

## File Structure

```
apps/api/
  Cargo.toml
  .env.example
  docker-compose.yml          # postgres only
  migration/                  # sea-orm migrations
  src/
    main.rs
    lib.rs
    config.rs
    db.rs
    error.rs
    state.rs
    auth/
      mod.rs
      password.rs
      jwt.rs
      refresh.rs
      extractor.rs
      handlers.rs
    budget/
      mod.rs
      math.rs
      handlers.rs
    transactions/
      mod.rs
      fingerprint.rs
      handlers.rs
    summary/
      mod.rs
      handlers.rs
    entities/                 # SeaORM entities
      mod.rs
      users.rs
      refresh_tokens.rs
      budget_months.rs
      fixed_expenses.rs
      transactions.rs
  tests/
    auth_api.rs
    budget_math.rs
    transactions_api.rs
    summary_api.rs

apps/android/
  settings.gradle.kts
  build.gradle.kts
  app/
    build.gradle.kts
    src/main/
      AndroidManifest.xml
      java/com/getmoney/app/
        GetMoneyApp.kt
        MainActivity.kt
        ui/theme/CarbonTheme.kt
        ui/theme/Color.kt
        ui/theme/Type.kt
        ui/nav/AppNav.kt
        ui/auth/AuthScreens.kt
        ui/home/HomeScreen.kt
        ui/budget/BudgetScreen.kt
        ui/summary/SummaryScreen.kt
        ui/slip/AddSlipScreen.kt
        ui/account/AccountScreen.kt
        data/api/ApiClient.kt
        data/api/Dto.kt
        data/auth/TokenStore.kt
        data/auth/AuthRepository.kt
        data/budget/BudgetRepository.kt
        data/tx/TransactionRepository.kt
        ocr/SlipOcr.kt
        ocr/SlipParser.kt
    src/test/java/com/getmoney/app/ocr/SlipParserTest.kt
```

---

### Task 1: API scaffold + Postgres + health

**Files:**
- Create: `apps/api/Cargo.toml`
- Create: `apps/api/docker-compose.yml`
- Create: `apps/api/.env.example`
- Create: `apps/api/src/main.rs`
- Create: `apps/api/src/lib.rs`
- Create: `apps/api/src/config.rs`
- Create: `apps/api/src/error.rs`
- Create: `apps/api/src/state.rs`
- Create: `apps/api/src/db.rs`

**Interfaces:**
- Produces: `AppState { db: DatabaseConnection, config: Config }`, `Config` from env, `GET /health` → `{"status":"ok"}`

- [ ] **Step 1: Create docker-compose for Postgres**

```yaml
# apps/api/docker-compose.yml
services:
  db:
    image: postgres:16-alpine
    environment:
      POSTGRES_USER: getmoney
      POSTGRES_PASSWORD: getmoney
      POSTGRES_DB: getmoney
    ports:
      - "5432:5432"
    volumes:
      - pgdata:/var/lib/postgresql/data
volumes:
  pgdata:
```

- [ ] **Step 2: Create Cargo.toml + .env.example**

```toml
# apps/api/Cargo.toml
[package]
name = "getmoney-api"
version = "0.1.0"
edition = "2021"

[dependencies]
axum = "0.8"
tokio = { version = "1", features = ["full"] }
serde = { version = "1", features = ["derive"] }
serde_json = "1"
sea-orm = { version = "1", features = ["sqlx-postgres", "runtime-tokio-rustls", "macros", "with-uuid", "with-chrono", "with-rust_decimal"] }
sea-orm-migration = "1"
uuid = { version = "1", features = ["v4", "serde"] }
chrono = { version = "0.4", features = ["serde"] }
rust_decimal = { version = "1", features = ["serde-with-str"] }
argon2 = "0.5"
password-hash = "0.5"
jsonwebtoken = "9"
sha2 = "0.10"
hex = "0.4"
rand = "0.8"
thiserror = "2"
anyhow = "1"
tower-http = { version = "0.6", features = ["cors", "trace"] }
tracing = "0.1"
tracing-subscriber = { version = "0.3", features = ["env-filter"] }
dotenvy = "0.15"

[dev-dependencies]
tower = { version = "0.5", features = ["util"] }
http-body-util = "0.1"
```

```env
# apps/api/.env.example
DATABASE_URL=postgres://getmoney:getmoney@127.0.0.1:5432/getmoney
JWT_SECRET=change-me-to-a-long-random-string
ACCESS_TOKEN_TTL_SECS=900
REFRESH_TOKEN_TTL_SECS=2592000
BIND_ADDR=0.0.0.0:8080
```

- [ ] **Step 3: Implement config, error, state, db, health**

```rust
// apps/api/src/config.rs
#[derive(Clone)]
pub struct Config {
    pub database_url: String,
    pub jwt_secret: String,
    pub access_token_ttl_secs: i64,
    pub refresh_token_ttl_secs: i64,
    pub bind_addr: String,
}

impl Config {
    pub fn from_env() -> Self {
        dotenvy::dotenv().ok();
        Self {
            database_url: std::env::var("DATABASE_URL").expect("DATABASE_URL"),
            jwt_secret: std::env::var("JWT_SECRET").expect("JWT_SECRET"),
            access_token_ttl_secs: std::env::var("ACCESS_TOKEN_TTL_SECS")
                .unwrap_or_else(|_| "900".into())
                .parse()
                .expect("ACCESS_TOKEN_TTL_SECS"),
            refresh_token_ttl_secs: std::env::var("REFRESH_TOKEN_TTL_SECS")
                .unwrap_or_else(|_| "2592000".into())
                .parse()
                .expect("REFRESH_TOKEN_TTL_SECS"),
            bind_addr: std::env::var("BIND_ADDR").unwrap_or_else(|_| "0.0.0.0:8080".into()),
        }
    }
}
```

```rust
// apps/api/src/error.rs
use axum::{
    Json,
    http::StatusCode,
    response::{IntoResponse, Response},
};
use serde_json::json;

#[derive(Debug, thiserror::Error)]
pub enum AppError {
    #[error("unauthorized")]
    Unauthorized,
    #[error("forbidden")]
    Forbidden,
    #[error("not found")]
    NotFound,
    #[error("conflict: {0}")]
    Conflict(String),
    #[error("bad request: {0}")]
    BadRequest(String),
    #[error(transparent)]
    Db(#[from] sea_orm::DbErr),
    #[error(transparent)]
    Other(#[from] anyhow::Error),
}

impl IntoResponse for AppError {
    fn into_response(self) -> Response {
        let (status, msg) = match &self {
            AppError::Unauthorized => (StatusCode::UNAUTHORIZED, self.to_string()),
            AppError::Forbidden => (StatusCode::FORBIDDEN, self.to_string()),
            AppError::NotFound => (StatusCode::NOT_FOUND, self.to_string()),
            AppError::Conflict(m) => (StatusCode::CONFLICT, m.clone()),
            AppError::BadRequest(m) => (StatusCode::BAD_REQUEST, m.clone()),
            AppError::Db(e) => (StatusCode::INTERNAL_SERVER_ERROR, e.to_string()),
            AppError::Other(e) => (StatusCode::INTERNAL_SERVER_ERROR, e.to_string()),
        };
        (status, Json(json!({ "error": msg }))).into_response()
    }
}

pub type AppResult<T> = Result<T, AppError>;
```

```rust
// apps/api/src/state.rs
use crate::config::Config;
use sea_orm::DatabaseConnection;

#[derive(Clone)]
pub struct AppState {
    pub db: DatabaseConnection,
    pub config: Config,
}
```

```rust
// apps/api/src/db.rs
use sea_orm::{Database, DatabaseConnection};

pub async fn connect(database_url: &str) -> DatabaseConnection {
    Database::connect(database_url)
        .await
        .expect("failed to connect to database")
}
```

```rust
// apps/api/src/lib.rs
pub mod config;
pub mod db;
pub mod error;
pub mod state;

use axum::{Router, routing::get};
use state::AppState;

pub fn app(state: AppState) -> Router {
    Router::new()
        .route("/health", get(|| async { axum::Json(serde_json::json!({"status":"ok"})) }))
        .with_state(state)
}
```

```rust
// apps/api/src/main.rs
use getmoney_api::{app, config::Config, db, state::AppState};

#[tokio::main]
async fn main() {
    tracing_subscriber::fmt()
        .with_env_filter("getmoney_api=debug,tower_http=info")
        .init();
    let config = Config::from_env();
    let db = db::connect(&config.database_url).await;
    let bind = config.bind_addr.clone();
    let state = AppState { db, config };
    let listener = tokio::net::TcpListener::bind(&bind).await.unwrap();
    tracing::info!("listening on {bind}");
    axum::serve(listener, app(state)).await.unwrap();
}
```

- [ ] **Step 4: Start DB and verify health**

Run:
```powershell
cd d:\10min\getmoney\apps\api
docker compose up -d
Copy-Item .env.example .env
cargo run
```
In another shell:
```powershell
curl http://127.0.0.1:8080/health
```
Expected: `{"status":"ok"}`

- [ ] **Step 5: Commit**

```bash
git add apps/api
git commit -m "feat(api): scaffold Axum app with Postgres and health endpoint"
```

---

### Task 2: Migrations + SeaORM entities

**Files:**
- Create: `apps/api/migration/Cargo.toml`
- Create: `apps/api/migration/src/lib.rs`
- Create: `apps/api/migration/src/m20260727_000001_init.rs`
- Create: `apps/api/src/entities/*.rs`

**Interfaces:**
- Produces: tables `users`, `refresh_tokens`, `budget_months`, `fixed_expenses`, `transactions` matching the spec
- Produces: SeaORM entity modules under `crate::entities`

- [ ] **Step 1: Write init migration SQL via sea-orm-migration**

Create migration crate following SeaORM CLI layout. Core SQL (must match exactly):

```sql
CREATE TABLE users (
  id UUID PRIMARY KEY,
  email TEXT NOT NULL UNIQUE,
  password_hash TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE refresh_tokens (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  token_hash TEXT NOT NULL UNIQUE,
  expires_at TIMESTAMPTZ NOT NULL,
  revoked_at TIMESTAMPTZ NULL,
  device_label TEXT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE budget_months (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  year INT NOT NULL,
  month INT NOT NULL CHECK (month BETWEEN 1 AND 12),
  salary NUMERIC(18,2) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE (user_id, year, month)
);

CREATE TABLE fixed_expenses (
  id UUID PRIMARY KEY,
  budget_month_id UUID NOT NULL REFERENCES budget_months(id) ON DELETE CASCADE,
  name TEXT NOT NULL,
  amount NUMERIC(18,2) NOT NULL,
  sort_order INT NOT NULL DEFAULT 0
);

CREATE TABLE transactions (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  amount NUMERIC(18,2) NOT NULL,
  spent_at TIMESTAMPTZ NOT NULL,
  source TEXT NOT NULL CHECK (source IN ('slip','manual')),
  bank TEXT NULL,
  note TEXT NULL,
  slip_fingerprint TEXT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX transactions_user_fingerprint_uidx
  ON transactions (user_id, slip_fingerprint)
  WHERE slip_fingerprint IS NOT NULL;
```

- [ ] **Step 2: Generate/write SeaORM entities** for all five tables (`Uuid` PKs, `Decimal` for money, `DateTimeWithTimeZone` for timestamps). Export via `entities/mod.rs`.

- [ ] **Step 3: Wire migration runner** in `main` or a `cargo run --bin migrate` so `cargo run` applies pending migrations before serve (or document `sea-orm-cli migrate up`). Prefer auto-migrate on boot for local MVP:

```rust
// in main before serve — call Migrator::up(&db, None).await.unwrap();
```

- [ ] **Step 4: Verify tables exist**

Run:
```powershell
docker compose exec db psql -U getmoney -d getmoney -c "\dt"
```
Expected: lists `users`, `refresh_tokens`, `budget_months`, `fixed_expenses`, `transactions`

- [ ] **Step 5: Commit**

```bash
git add apps/api
git commit -m "feat(api): add Postgres schema and SeaORM entities"
```

---

### Task 3: Password + JWT + refresh helpers

**Files:**
- Create: `apps/api/src/auth/mod.rs`
- Create: `apps/api/src/auth/password.rs`
- Create: `apps/api/src/auth/jwt.rs`
- Create: `apps/api/src/auth/refresh.rs`
- Create: `apps/api/tests/budget_math.rs` (placeholder skip — math in Task 5; put password tests here instead as `tests/auth_unit.rs`)
- Create: `apps/api/tests/auth_unit.rs`

**Interfaces:**
- Produces:
  - `hash_password(password: &str) -> AppResult<String>`
  - `verify_password(password: &str, hash: &str) -> AppResult<bool>`
  - `issue_access_token(user_id: Uuid, config: &Config) -> AppResult<String>`
  - `decode_access_token(token: &str, config: &Config) -> AppResult<Uuid>`
  - `generate_refresh_token() -> (raw: String, hash: String)`
  - `hash_refresh_token(raw: &str) -> String` (SHA-256 hex)

- [ ] **Step 1: Write failing unit tests**

```rust
// apps/api/tests/auth_unit.rs
use getmoney_api::auth::password::{hash_password, verify_password};

#[test]
fn argon2_hash_and_verify() {
    let hash = hash_password("correct horse").unwrap();
    assert!(verify_password("correct horse", &hash).unwrap());
    assert!(!verify_password("wrong", &hash).unwrap());
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cargo test --test auth_unit --manifest-path apps/api/Cargo.toml`
Expected: FAIL (module not found / unresolved)

- [ ] **Step 3: Implement password + jwt + refresh**

```rust
// apps/api/src/auth/password.rs
use argon2::{
    Argon2,
    password_hash::{PasswordHash, PasswordHasher, PasswordVerifier, SaltString, rand_core::OsRng},
};
use crate::error::{AppError, AppResult};

pub fn hash_password(password: &str) -> AppResult<String> {
    let salt = SaltString::generate(&mut OsRng);
    let hash = Argon2::default()
        .hash_password(password.as_bytes(), &salt)
        .map_err(|e| AppError::Other(anyhow::anyhow!(e)))?
        .to_string();
    Ok(hash)
}

pub fn verify_password(password: &str, hash: &str) -> AppResult<bool> {
    let parsed = PasswordHash::new(hash).map_err(|e| AppError::Other(anyhow::anyhow!(e)))?;
    Ok(Argon2::default()
        .verify_password(password.as_bytes(), &parsed)
        .is_ok())
}
```

```rust
// apps/api/src/auth/jwt.rs
use chrono::{Duration, Utc};
use jsonwebtoken::{DecodingKey, EncodingKey, Header, Validation, decode, encode};
use serde::{Deserialize, Serialize};
use uuid::Uuid;
use crate::config::Config;
use crate::error::{AppError, AppResult};

#[derive(Serialize, Deserialize)]
struct Claims {
    sub: String,
    exp: i64,
    iat: i64,
}

pub fn issue_access_token(user_id: Uuid, config: &Config) -> AppResult<String> {
    let now = Utc::now();
    let claims = Claims {
        sub: user_id.to_string(),
        iat: now.timestamp(),
        exp: (now + Duration::seconds(config.access_token_ttl_secs)).timestamp(),
    };
    encode(
        &Header::default(),
        &claims,
        &EncodingKey::from_secret(config.jwt_secret.as_bytes()),
    )
    .map_err(|e| AppError::Other(anyhow::anyhow!(e)))
}

pub fn decode_access_token(token: &str, config: &Config) -> AppResult<Uuid> {
    let data = decode::<Claims>(
        token,
        &DecodingKey::from_secret(config.jwt_secret.as_bytes()),
        &Validation::default(),
    )
    .map_err(|_| AppError::Unauthorized)?;
    Uuid::parse_str(&data.claims.sub).map_err(|_| AppError::Unauthorized)
}
```

```rust
// apps/api/src/auth/refresh.rs
use rand::{RngCore, rngs::OsRng};
use sha2::{Digest, Sha256};

pub fn hash_refresh_token(raw: &str) -> String {
    let mut hasher = Sha256::new();
    hasher.update(raw.as_bytes());
    hex::encode(hasher.finalize())
}

pub fn generate_refresh_token() -> (String, String) {
    let mut bytes = [0u8; 32];
    OsRng.fill_bytes(&mut bytes);
    let raw = hex::encode(bytes);
    let hash = hash_refresh_token(&raw);
    (raw, hash)
}
```

Export from `auth/mod.rs`. Add `pub mod auth;` to `lib.rs`.

- [ ] **Step 4: Run tests — expect PASS**

Run: `cargo test --test auth_unit --manifest-path apps/api/Cargo.toml`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add apps/api
git commit -m "feat(api): Argon2id password hashing and JWT/refresh helpers"
```

---

### Task 4: Auth HTTP endpoints

**Files:**
- Create: `apps/api/src/auth/handlers.rs`
- Create: `apps/api/src/auth/extractor.rs`
- Modify: `apps/api/src/lib.rs` (mount routes)
- Create: `apps/api/tests/auth_api.rs`

**Interfaces:**
- Produces routes:
  - `POST /auth/register` `{email,password}` → token pair
  - `POST /auth/login` `{email,password}` → token pair
  - `POST /auth/refresh` `{refresh_token}` → new pair (rotate)
  - `POST /auth/logout` `{refresh_token}`
  - `POST /auth/logout-all` Bearer required
- Produces: `AuthUser(Uuid)` extractor from `Authorization: Bearer`

- [ ] **Step 1: Write failing integration test skeleton** using `axum::Router` + `oneshot` (or httptest```rust
// apps/api/tests/auth_api.rs — assert register → 200 with access_token+refresh_token;
// login wrong password → 401; refresh rotates; logout-all makes old refresh fail
```

Use a test DB URL `DATABASE_URL` from env; skip if unset with `#[ignore]` only if CI lacks DB — prefer always-on local docker.

Minimal assertions:
1. Register `user@example.com` / `password123` → 200, keys present
2. Login bad password → 401
3. Refresh with returned token → 200, old refresh → 401
4. Login → logout-all with access → previous refresh → 401

- [ ] **Step 2: Run tests — expect FAIL**

Run: `cargo test --test auth_api --manifest-path apps/api/Cargo.toml`
Expected: FAIL (routes missing)

- [ ] **Step 3: Implement handlers + extractor + mount**

Response DTO:
```rust
#[derive(Serialize)]
struct TokenResponse {
    access_token: String,
    refresh_token: String,
    expires_in: i64,
}
```

On register: reject duplicate email with `409`. On login: verify Argon2. Persist refresh row with `expires_at = now + refresh_ttl`. On refresh: lookup by hash, reject if revoked/expired, set `revoked_at`, issue new pair. Logout sets `revoked_at`. Logout-all revokes all rows for `user_id`.

- [ ] **Step 4: Run tests — expect PASS**

- [ ] **Step 5: Commit**

```bash
git add apps/api
git commit -m "feat(api): register/login/refresh/logout auth endpoints"
```

---

### Task 5: Budget math + budget endpoints

**Files:**
- Create: `apps/api/src/budget/mod.rs`
- Create: `apps/api/src/budget/math.rs`
- Create: `apps/api/src/budget/handlers.rs`
- Create: `apps/api/tests/budget_math.rs`
- Modify: `apps/api/src/lib.rs`

**Interfaces:**
- Produces:
  - `days_in_month(year: i32, month: u32) -> u32`
  - `compute_budget(salary: Decimal, fixed_total: Decimal, year: i32, month: u32) -> BudgetFigures { fixed_total, remaining, daily_allowance, days_in_month }`
- Routes:
  - `GET|PUT /budget/months/{yyyy-mm}`
  - CRUD `/budget/months/{yyyy-mm}/fixed-expenses[/{id}]`

- [ ] **Step 1: Write failing math tests**

```rust
// apps/api/tests/budget_math.rs
use getmoney_api::budget::math::{compute_budget, days_in_month};
use rust_decimal::Decimal;
use std::str::FromStr;

#[test]
fn july_31_days_daily_allowance() {
    // salary 27000, fixed 20836.17 → remaining 6163.83 → /31 ≈ 198.833...
    // Use spreadsheet-shaped numbers from design intent:
    let salary = Decimal::from_str("27000.00").unwrap();
    let fixed = Decimal::from_str("20836.17").unwrap();
    let figs = compute_budget(salary, fixed, 2026, 7);
    assert_eq!(figs.days_in_month, 31);
    assert_eq!(figs.remaining, Decimal::from_str("6163.83").unwrap());
    assert_eq!(
        figs.daily_allowance,
        figs.remaining / Decimal::from(31)
    );
}

#[test]
fn february_leap() {
    assert_eq!(days_in_month(2024, 2), 29);
    assert_eq!(days_in_month(2025, 2), 28);
}
```

- [ ] **Step 2: Run — expect FAIL**

- [ ] **Step 3: Implement math**

```rust
// apps/api/src/budget/math.rs
use chrono::NaiveDate;
use rust_decimal::Decimal;
use serde::Serialize;

#[derive(Debug, Clone, Serialize)]
pub struct BudgetFigures {
    pub fixed_total: Decimal,
    pub remaining: Decimal,
    pub daily_allowance: Decimal,
    pub days_in_month: u32,
}

pub fn days_in_month(year: i32, month: u32) -> u32 {
    let first = NaiveDate::from_ymd_opt(year, month, 1).expect("valid month");
    let next = if month == 12 {
        NaiveDate::from_ymd_opt(year + 1, 1, 1).unwrap()
    } else {
        NaiveDate::from_ymd_opt(year, month + 1, 1).unwrap()
    };
    (next - first).num_days() as u32
}

pub fn compute_budget(salary: Decimal, fixed_total: Decimal, year: i32, month: u32) -> BudgetFigures {
    let days = days_in_month(year, month);
    let remaining = salary - fixed_total;
    let daily_allowance = if days == 0 {
        Decimal::ZERO
    } else {
        remaining / Decimal::from(days)
    };
    BudgetFigures {
        fixed_total,
        remaining,
        daily_allowance,
        days_in_month: days,
    }
}
```

Serialize decimals with `rust_decimal::serde::str` on response DTOs.

- [ ] **Step 4: Implement handlers** — parse `{yyyy-mm}`, upsert salary, list/create/patch/delete fixed expenses owned by current user only. Response always includes computed figures.

- [ ] **Step 5: Run math + a small API test for PUT+POST fixed+GET figures — PASS**

- [ ] **Step 6: Commit**

```bash
git add apps/api
git commit -m "feat(api): budget month math and fixed-expense CRUD"
```

---

### Task 6: Transactions + slip fingerprint

**Files:**
- Create: `apps/api/src/transactions/mod.rs`
- Create: `apps/api/src/transactions/fingerprint.rs`
- Create: `apps/api/src/transactions/handlers.rs`
- Create: `apps/api/tests/transactions_api.rs`
- Modify: `apps/api/src/lib.rs`

**Interfaces:**
- Produces: `slip_fingerprint(amount: &str, spent_at: &str, bank: &str, reference: &str) -> String` (SHA-256 of normalized pipe-joined fields)
- Routes: `GET /transactions?from&to`, `POST /transactions`, `PATCH|DELETE /transactions/{id}`
- Duplicate fingerprint for same user → `409` `"slip already recorded"`

- [ ] **Step 1: Write failing tests** for fingerprint stability + duplicate POST → 409

```rust
#[test]
fn fingerprint_is_stable() {
    use getmoney_api::transactions::fingerprint::slip_fingerprint;
    let a = slip_fingerprint("100.00", "2026-07-27T10:00:00+07:00", "scb", "REF1");
    let b = slip_fingerprint("100.00", "2026-07-27T10:00:00+07:00", "scb", "REF1");
    assert_eq!(a, b);
}
```

- [ ] **Step 2: Run — FAIL**

- [ ] **Step 3: Implement**

```rust
// apps/api/src/transactions/fingerprint.rs
use sha2::{Digest, Sha256};

pub fn slip_fingerprint(amount: &str, spent_at: &str, bank: &str, reference: &str) -> String {
    let normalized = format!(
        "{}|{}|{}|{}",
        amount.trim(),
        spent_at.trim(),
        bank.trim().to_lowercase(),
        reference.trim()
    );
    let mut hasher = Sha256::new();
    hasher.update(normalized.as_bytes());
    hex::encode(hasher.finalize())
}
```

`POST` body: `{ amount: String, spent_at: DateTime, source: "slip"|"manual", bank?, note?, reference? }`. If `source=="slip"`, compute fingerprint from amount/spent_at/bank/reference; on unique violation map to `AppError::Conflict`.

- [ ] **Step 4: Tests PASS**

- [ ] **Step 5: Commit**

```bash
git add apps/api
git commit -m "feat(api): transactions CRUD with slip dedupe fingerprint"
```

---

### Task 7: Summary endpoints

**Files:**
- Create: `apps/api/src/summary/mod.rs`
- Create: `apps/api/src/summary/handlers.rs`
- Create: `apps/api/tests/summary_api.rs`
- Modify: `apps/api/src/lib.rs`

**Interfaces:**
- `GET /summary/today` → `{ daily_allowance, spent, remaining_today, percent_used, items[] }`
- `GET /summary/week` → week spent vs `daily_allowance * days_in_week_in_budget_month`
- `GET /summary/month/{yyyy-mm}` → `{ salary, fixed_total, remaining, daily_allowance, spent_variable, percent_of_remaining }`
- If no budget for current month on `/summary/today` → `404` with `"budget month required"` (client forces setup)

- [ ] **Step 1: Write failing API test** — seed user, budget July salary 27000 + fixed totaling known, two txs today, assert percent.

- [ ] **Step 2: Run — FAIL**

- [ ] **Step 3: Implement handlers** using `chrono` for local calendar day boundaries in `+07:00` (document timezone: Asia/Bangkok fixed for MVP).

```rust
// percent_used = if daily_allowance == 0 { 0 } else { (spent / daily_allowance) * 100 }
// remaining_today = daily_allowance - spent
// spent_variable for month = sum(transactions in that calendar month)
// percent_of_remaining = spent_variable / remaining * 100 (0 if remaining==0)
```

Week: Monday–Sunday containing “today” (ISO); count days that have `(year,month)==budget month`; multiply by that month’s `daily_allowance`.

- [ ] **Step 4: Tests PASS**

- [ ] **Step 5: Commit**

```bash
git add apps/api
git commit -m "feat(api): today/week/month summary endpoints"
```

---

### Task 8: Android project + Carbon theme

**Files:**
- Create: `apps/android/` Gradle project (`settings.gradle.kts`, root + `app` build files)
- Create: `apps/android/app/src/main/java/com/getmoney/app/ui/theme/{Color,Type,CarbonTheme}.kt`
- Create: `apps/android/app/src/main/java/com/getmoney/app/MainActivity.kt`
- Create: `apps/android/app/src/main/AndroidManifest.xml`

**Interfaces:**
- Produces: `GetMoneyTheme` applying Carbon colors/typography; empty scaffold Activity

- [ ] **Step 1: Create Android app module** (minSdk 26, target 35, Compose BOM). Add dependency for IBM Plex Sans via downloadable font or bundled `res/font`.

- [ ] **Step 2: Map tokens**

```kotlin
// Color.kt
val IbmBlue = Color(0xFF0F62FE)
val Ink = Color(0xFF161616)
val InkMuted = Color(0xFF525252)
val Canvas = Color(0xFFFFFFFF)
val Surface1 = Color(0xFFF4F4F4)
val Hairline = Color(0xFFE0E0E0)
val ErrorRed = Color(0xFFDA1E28)
val SuccessGreen = Color(0xFF24A148)
```

Corner shapes: `RoundedCornerShape(0.dp)` everywhere. Primary button: blue fill, white text, no elevation.

- [ ] **Step 3: Run app on emulator** — blank white screen with “GetMoney” in Plex weight 300. Manual check.

- [ ] **Step 4: Commit**

```bash
git add apps/android
git commit -m "feat(android): Compose app scaffold with Carbon theme"
```

---

### Task 9: Android auth + token store + API client

**Files:**
- Create: `.../data/api/Dto.kt`, `ApiClient.kt`
- Create: `.../data/auth/TokenStore.kt`, `AuthRepository.kt`
- Create: `.../ui/auth/AuthScreens.kt`
- Create: `.../ui/nav/AppNav.kt`
- Modify: `MainActivity.kt`

**Interfaces:**
- Produces: Retrofit/OkHttp client with base URL from BuildConfig; interceptor attaches Bearer; authenticator refreshes on 401 once
- `TokenStore` DataStore for access+refresh
- Login/Register screens → navigate to Home on success

- [ ] **Step 1: Define DTOs matching API** (decimal strings)

```kotlin
data class TokenResponse(
    val access_token: String,
    val refresh_token: String,
    val expires_in: Long,
)
data class LoginRequest(val email: String, val password: String)
```

- [ ] **Step 2: Implement TokenStore + AuthRepository + Auth screens (Carbon inputs)**

- [ ] **Step 3: Manual test against local API** — register, kill app, reopen still logged in (refresh works).

- [ ] **Step 4: Commit**

```bash
git add apps/android
git commit -m "feat(android): auth screens with token refresh"
```

---

### Task 10: Budget + Home summary UI

**Files:**
- Create: `.../data/budget/BudgetRepository.kt`
- Create: `.../ui/budget/BudgetScreen.kt`
- Create: `.../ui/home/HomeScreen.kt`
- Create: `.../ui/summary/SummaryScreen.kt`
- Create: `.../ui/account/AccountScreen.kt`
- Modify: `AppNav.kt` — bottom nav Today · Summary · Budget · Account

**Interfaces:**
- Home calls `GET /summary/today`; on 404 budget required → navigate Budget
- Budget: edit salary, CRUD fixed lines, show remaining + daily_allowance
- Summary tabs week/month
- Account: logout / logout-all

- [ ] **Step 1: Implement repositories + screens** with Carbon progress for % (Rectangle bar, primary fill width = percent capped 100%, error color if >100%).

- [ ] **Step 2: Manual test** — enter spreadsheet-like July fixed lines + salary 27000; Home shows daily allowance and 0% until txs exist.

- [ ] **Step 3: Commit**

```bash
git add apps/android
git commit -m "feat(android): budget, home percent, summary, account"
```

---

### Task 11: Slip OCR + parser + add-slip flow

**Files:**
- Create: `.../ocr/SlipOcr.kt`
- Create: `.../ocr/SlipParser.kt`
- Create: `.../ui/slip/AddSlipScreen.kt`
- Create: `.../data/tx/TransactionRepository.kt`
- Create: `apps/android/app/src/test/java/com/getmoney/app/ocr/SlipParserTest.kt`
- Modify: Home CTA → AddSlip; Manifest `READ_MEDIA_IMAGES` / picker Photo Picker

**Interfaces:**
- `SlipParser.parse(raw: String): SlipDraft(amount, spentAtIso?, bank?, reference?, note?)`
- `SlipOcr.recognize(uri): String`
- AddSlip: pick image → OCR → editable confirm → `POST /transactions` source=`slip`
- On 409: show “บันทึกไปแล้ว” Carbon error

- [ ] **Step 1: Write failing parser unit tests** with fixture Thai slip text:

```kotlin
@Test
fun extractsAmountAndRef() {
    val raw = """
        โอนเงินสำเร็จ
        จำนวนเงิน 1,250.50 บาท
        รหัสอ้างอิง 20260727SCB001
        SCB
    """.trimIndent()
    val draft = SlipParser.parse(raw)
    assertEquals("1250.50", draft.amount)
    assertEquals("20260727SCB001", draft.reference)
}
```

- [ ] **Step 2: Run unit test — FAIL**

Run: `./gradlew :app:testDebugUnitTest --tests com.getmoney.app.ocr.SlipParserTest`

- [ ] **Step 3: Implement parser** — regex for amounts (`\d{1,3}(,\d{3})*(\.\d{2})?`), pick largest; bank keywords (SCB, KBank, Krungthai, PromptPay, etc.); reference lines.

- [ ] **Step 4: Wire ML Kit + confirm UI + POST**

- [ ] **Step 5: Unit tests PASS; manual: pick real slip screenshot, confirm, Home % updates**

- [ ] **Step 6: Commit**

```bash
git add apps/android
git commit -m "feat(android): on-device slip OCR, parser, and confirm save"
```

---

### Task 12: README + end-to-end smoke checklist

**Files:**
- Create: `README.md`
- Create: `docs/superpowers/plans/2026-07-27-getmoney-mvp-smoke.md` (optional short checklist) — prefer single root README only

**Interfaces:**
- Produces: how to run docker, api, android against `10.0.2.2:8080`

- [ ] **Step 1: Write README** with setup, env vars, emulator API base URL, Carbon note, link to spec/plan.

- [ ] **Step 2: Run smoke checklist manually**
  1. Register → set July budget → Home shows allowance
  2. Add manual tx → % updates
  3. Add slip (fixture image) → % updates; re-add same → 409 message
  4. Logout-all → app requires login
  5. Week/month summary non-empty

- [ ] **Step 3: Commit**

```bash
git add README.md
git commit -m "docs: README and local run instructions for GetMoney MVP"
```

---

## Self-Review (plan vs spec)

| Spec requirement | Task |
|---|---|
| Android Kotlin + ML Kit OCR | 8, 11 |
| Rust Axum + SeaORM + Postgres | 1–2 |
| Argon2id + access/refresh + logout-all | 3–4 |
| Budget salary + fixed → daily allowance | 5, 10 |
| Today % + week/month summary | 7, 10 |
| Edit/delete transactions | 6, 10–11 |
| No image upload; OCR on device; confirm | 11 |
| Slip dedupe 409 | 6, 11 |
| Carbon UI | 8–11 |
| Phase 2 backlog not built | (excluded) |

**Placeholder scan:** none intentional.  
**Type consistency:** decimal strings in API DTOs; `TokenResponse` fields match Android; fingerprint inputs amount/spent_at/bank/reference.

---

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-07-27-getmoney-mvp.md`.

**Two execution options:**

1. **Subagent-Driven (recommended)** — fresh subagent per task, review between tasks  
2. **Inline Execution** — run tasks in this session with executing-plans checkpoints  

Which approach?
