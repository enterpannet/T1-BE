# In-App Update Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans or implement inline task-by-task.

**Goal:** Let the Android app discover, download, and install newer APKs published via `latest.json` + Caddy `/releases/*`.

**Architecture:** Public `GET /app/version` reads a JSON manifest from disk. Caddy serves APK files statically. Android compares `versionCode`, shows AppDialog, downloads to cache, installs via FileProvider.

**Tech Stack:** Axum/Rust, Caddy, Retrofit/OkHttp, Jetpack Compose

## Global Constraints

- Spec: `docs/superpowers/specs/2026-07-28-in-app-update-design.md`
- No admin upload API; manual scp + JSON edit
- APK not streamed through Axum
- Same signing key required for updates
- Thai UI: อัปเดต / ภายหลัง

---

### Task 1: API `/app/version`

**Files:** `apps/api/src/app_release.rs`, `config.rs`, `lib.rs`, `tests/app_version_api.rs`, deploy script

### Task 2: Android updater

**Files:** `ApiClient` + models, `AppUpdateChecker`, download/install helper, `AppNav` dialog, Manifest + `file_paths.xml`

### Task 3: Version bump + sample `latest.json` template in docs/scripts
