# GetMoney MVP

Personal daily budget app: set monthly salary and fixed expenses, track today’s spend from on-device slip OCR (no image upload), and see allowance usage on Home.

**Stack:** Rust Axum API + PostgreSQL · Kotlin Jetpack Compose Android · ML Kit OCR on device · IBM Carbon UI.

**Docs:** [Design spec](docs/superpowers/specs/2026-07-27-getmoney-design.md) · [Implementation plan](docs/superpowers/plans/2026-07-27-getmoney-mvp.md)

---

## Prerequisites

| Tool | Version / notes |
|---|---|
| Docker | Postgres 16 via Compose |
| Rust | 2021 edition (`cargo`) |
| JDK | **17** (required for Android; JDK 25 breaks Gradle/Kotlin) |
| Android SDK | API 35; emulator or device |

---

## 1. Database (Postgres)

```powershell
cd apps\api
docker compose up -d
```

Default connection: `postgres://getmoney:getmoney@127.0.0.1:5432/getmoney`

---

## 2. API (Rust)

```powershell
cd apps\api
Copy-Item .env.example .env   # first run only; edit JWT_SECRET
cargo run
```

Migrations run automatically on boot. Health check:

```powershell
curl.exe http://127.0.0.1:8080/health
# {"status":"ok"}
```

### Environment variables (`apps/api/.env`)

| Variable | Default | Purpose |
|---|---|---|
| `DATABASE_URL` | `postgres://getmoney:getmoney@127.0.0.1:5432/getmoney` | Postgres connection |
| `JWT_SECRET` | *(change me)* | Access-token signing secret |
| `ACCESS_TOKEN_TTL_SECS` | `900` | Access JWT lifetime (~15 min) |
| `REFRESH_TOKEN_TTL_SECS` | `2592000` | Refresh token lifetime (~30 days) |
| `BIND_ADDR` | `0.0.0.0:8080` | Listen address |

Run tests (requires Postgres up):

```powershell
cd apps\api
cargo test
```

---

## 3. Android app

The debug build points at the **Android emulator host loopback**:

```
http://10.0.2.2:8080/
```

(`10.0.2.2` is the emulator’s alias for the host machine’s `127.0.0.1`.)

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"
cd apps\android
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:installDebug   # with emulator/device connected
```

APK output: `apps/android/app/build/outputs/apk/debug/app-debug.apk`

Unit tests:

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

For a **physical device**, change `API_BASE_URL` in `apps/android/app/build.gradle.kts` to your LAN IP (e.g. `http://192.168.1.10:8080/`).

---

## UI — IBM Carbon

Android UI follows tokens in [`DESIGN-ibm.md`](DESIGN-ibm.md): primary `#0f62fe`, ink `#161616`, canvas `#ffffff`, square corners (0–4px), IBM Plex Sans, flat surfaces (no drop shadows).

---

## End-to-end smoke checklist

Run with Postgres + API + emulator. Mark each step as you go.

| # | Step | Expected | Status |
|---|---|---|---|
| 1 | Register → Budget tab → set salary + fixed expenses for **July** | Home (Today) shows daily allowance and 0% | **Deferred** — emulator manual |
| 2 | Add manual transaction | Today % increases | **Deferred** — no dedicated manual-tx screen; use **Add slip → Enter manually** or `POST /transactions` with `"source":"manual"` via API |
| 3 | Add slip (pick image or Enter manually with ref/bank) | Today % updates | **Deferred** — emulator manual; parser unit tests pass |
| 3b | Re-add same slip (same amount/ref/bank/time) | UI shows **บันทึกไปแล้ว** (409) | **Verified** — API test `duplicate_slip_post_returns_conflict` |
| 4 | Account → **Sign out everywhere** | App returns to login | **Verified** — API test `auth_endpoints_register_login_rotate_and_logout_all`; Android UI deferred |
| 5 | Summary tab → Week / Month | Non-empty totals after transactions | **Verified** — API test `today_week_and_month_summaries_use_bangkok_calendar_boundaries`; Android UI deferred |

**Infrastructure verified this session**

- Postgres container up (`docker compose ps`)
- API health: `GET /health` → `{"status":"ok"}`
- Android `:app:assembleDebug` — BUILD SUCCESSFUL (JDK 17)

**Slip OCR fixture (unit test text, not an image file)**

Use this sample OCR text when testing `SlipParser` or **Enter manually** with matching fields:

```
โอนเงินสำเร็จ
จำนวนเงิน 1,250.50 บาท
รหัสอ้างอิง 20260727SCB001
SCB
```

---

## Repo layout

```
apps/api/       Rust Axum + SeaORM + Postgres
apps/android/   Kotlin Compose + ML Kit OCR
DESIGN-ibm.md   Carbon design tokens
docs/           Spec and implementation plan
```
