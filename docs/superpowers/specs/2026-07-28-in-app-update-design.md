# In-app update via API + Caddy-hosted APK

**Date:** 2026-07-28  
**Status:** Implemented (v1.49 / API `/app/version`)  
**Surfaces:** Rust API (`apps/api`), Caddy on tmd.deals, Android app (`apps/android`)

## Problem

GetMoney is distributed as sideloaded APKs (`getmoney-v*-tmd.deals.apk`). Users must receive files manually. There is no version check, download, or install prompt in the app.

## Goals

1. App learns when a newer `versionCode` is available.
2. User can download the APK from `tmd.deals` and open the system installer.
3. Optional **force** update (dialog cannot be dismissed).
4. Operators publish by copying an APK + editing a JSON file on the server (no admin upload API).

## Non-goals

- Play Store / Google Play In-App Updates.
- Admin multipart upload endpoint.
- Staged rollouts, A/B channels, or multiple tracks (prod/beta).
- Serving multi-hundred-MB APKs through the Axum process.
- Changing the app signing key (updates must use the same signing key as installed builds).

## Decisions

| Topic | Choice |
|-------|--------|
| Scope | Full in-app flow: check + download + install prompt + force |
| Publishing | Files on disk + `latest.json` (manual / scp) |
| Architecture | API returns metadata; Caddy serves APK static files |

## Server layout

```
/var/getmoney/releases/
  latest.json
  getmoney-1.49.apk
```

### `latest.json`

```json
{
  "version_code": 50,
  "version_name": "1.49",
  "force": false,
  "apk_url": "https://tmd.deals/releases/getmoney-1.49.apk",
  "notes": "สแกนย้อนหลังครบ + พัก/หยุด"
}
```

| Field | Type | Notes |
|-------|------|--------|
| `version_code` | int | Compared to `BuildConfig.VERSION_CODE` |
| `version_name` | string | Display only |
| `force` | bool | If true, user cannot dismiss update UI |
| `apk_url` | string | Absolute HTTPS URL to APK |
| `notes` | string | Optional changelog shown in dialog |

### Caddy

Add a static file handler (alongside existing reverse_proxy to `:8080`):

- `https://tmd.deals/releases/*` → `file_server` rooted at `/var/getmoney/releases/`
- Do **not** expose directory listing if avoidable; clients use exact filenames from JSON.

Update `scripts/remote-deploy-api.sh` (or companion Caddyfile snippet) so deploys keep this block.

### Publish steps (operator)

1. Build APK (`assembleDebug` or release as used today).
2. `scp` to `/var/getmoney/releases/getmoney-{versionName}.apk`.
3. Edit `/var/getmoney/releases/latest.json` (`versionCode`, `versionName`, `apkUrl`, `force`, `notes`).
4. No API restart required if the API re-reads JSON on each request (preferred) or on a short TTL cache (≤60s).

## API

### `GET /app/version` (public, no JWT)

Reads `/var/getmoney/releases/latest.json` (path configurable via env, e.g. `APP_RELEASE_MANIFEST=/var/getmoney/releases/latest.json`).

**200 response** — same shape as `latest.json` (camelCase JSON).

**404 / 503** — manifest missing or unreadable; app treats as “no update info” (do not crash).

Config in `apps/api`:

- `APP_RELEASE_MANIFEST` — absolute path to JSON (optional; if unset, endpoint returns 404 and feature is inert).

No database table for v1.

## Android

### Check timing

After successful login / when `MainShell` becomes visible (once per process session is enough; optional pull-to-refresh later).

### Compare

```text
if (remote.versionCode > BuildConfig.VERSION_CODE) → offer update
```

### UI

- Dialog (reuse `AppDialog` patterns): title “มีเวอร์ชันใหม่ {versionName}”, body = `notes`, actions:
  - **อัปเดต** → start download
  - **ภายหลัง** — hidden when `force == true`
- While downloading: progress (bytes or indeterminate) on dialog or top banner; cancel allowed only when not force.
- On download complete: fire install intent via `FileProvider` (`file_paths.xml` already exists).
- Permission: `REQUEST_INSTALL_PACKAGES` / guide user to enable “ติดตั้งแอปที่ไม่รู้จัก” if install blocked.

### Networking

- Version check: Retrofit against `API_BASE_URL` → `GET app/version`.
- APK download: OkHttp/DownloadManager to `apkUrl` (may be same host `/releases/...`). Save under app cache/files; not world-readable except via FileProvider.

### Failure handling

- Network / 404 on version: silent skip (or soft log).
- Download fail: snackbar + retry; if force, keep dialog open.
- Install cancelled by user: if force, show dialog again on next resume.

## Security / ops notes

- APK must be signed with the **same** keystore as currently installed apps or Android will refuse update.
- Prefer HTTPS only (`tmd.deals`).
- Manifest is public (version + URL are not secrets). Restricting APK URL with a long random filename is optional hardening for v1.1.
- Disk size: remove old APKs periodically by hand.

## Testing

**API**

- Manifest present → 200 + fields.
- Manifest missing → 404.
- Invalid JSON → 503/500 without panic.

**Android (unit / instrumentation where practical)**

- `versionCode` equal / lower → no dialog.
- Higher + `force=false` → dialog with ภายหลัง.
- Higher + `force=true` → no dismiss.
- Fake download URL → progress then install intent constructed (mock FileProvider in test if needed).

**Manual**

- Publish test APK with bumped `versionCode`, confirm dialog → install → app reports new `versionName`.

## Rollout

1. Ship API + Caddy changes; place an initial `latest.json` matching current production `versionCode` so nothing prompts.
2. Ship Android with updater; bump to next version when ready to announce.
3. Document publish recipe in README or `docs/superpowers/specs` companion note.

## Open follow-ups (out of scope)

- `sha256` in manifest + verify before install.
- Admin upload API.
- “Check for updates” button on Account screen (easy add once core exists).
