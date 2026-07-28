# App icons + logo (Material Icons)

**Date:** 2026-07-28  
**Approach:** A — Material Icons + `logomoney.PNG` as app logo  
**Surface:** Android app chrome (launcher, bottom nav, primary actions, Account)

## Goal

Replace letter placeholders on bottom navigation and add consistent Material icons on primary actions across the app. Use `C:\Users\admin\Downloads\logomoney.PNG` as the launcher icon and as a small brand mark on Login and Account.

## Non-goals

- Custom gold/cyan icon set matching the logo art
- Adaptive icon layers / Play Store marketing assets beyond replacing the current launcher drawable
- Redesigning Carbon layout, colors, or typography
- Icons on every TextButton (e.g. dialog Cancel / ปิด) — only primary/destructive actions listed below

## Logo

1. Copy `logomoney.PNG` into the Android project as `apps/android/app/src/main/res/drawable/ic_launcher.png`.
2. Remove or stop using the solid-blue vector `ic_launcher.xml` so the PNG is the launcher asset.
3. Manifest already points at `@drawable/ic_launcher` for `android:icon` and `android:roundIcon` — keep that.
4. Show the same drawable as a small brand image:
   - **Login** screen: above the title, ~72–96dp, centered
   - **Account** screen: near the page title, ~40–48dp

No splash-screen overhaul in this change.

## Bottom navigation

Replace `Text(tab.label.take(1))` icons with Material Icons (Outlined family preferred for unselected; Filled acceptable when selected if Compose defaults handle it via tint).

| Route | Label | Icon |
|-------|-------|------|
| today | Today | `Icons.Outlined.Today` (or `CalendarToday`) |
| tx | Tx | `Icons.Outlined.ReceiptLong` (or `Receipt`) |
| summary | Summary | `Icons.Outlined.BarChart` |
| budget | Budget | `Icons.Outlined.AccountBalanceWallet` |
| account | Account | `Icons.Outlined.Person` |

Tint: theme `onSurfaceVariant` unselected, `primary` selected (NavigationBarItem defaults OK if they match Carbon).

## In-app action icons (scope C)

Add a leading Material Icon (16–20dp) beside the label on these controls. Keep existing text labels.

| Location | Control | Icon |
|----------|---------|------|
| Today | Add slip | `Add` or `PhotoCamera` |
| Today | ดูทั้งหมด | `ChevronRight` (trailing OK) |
| Tx / Today list | Edit | `Edit` |
| Tx / Today list | Delete | `Delete` (error/red tint for delete) |
| Budget | Add fixed expense | `Add` |
| Budget | Delete (fixed expense row) | `Delete` |
| Account | Scan now | `QrCodeScanner` |
| Account | สแกนย้อนหลังทั้งหมด | `History` |
| Account | Cloud upload row label | `CloudUpload` (decorative next to title) |
| Account | Sign out | `Logout` |
| Account | Sign out everywhere | `Logout` (same icon; outline button) |
| Add slip | Primary pick/camera/save CTAs | matching `Photo` / `CameraAlt` / `Check` where a single primary button exists |

Dialog confirm buttons (Save / Delete) may keep text-only to avoid crowding; list-row Edit/Delete must have icons.

## Visual rules

- Use `material-icons-extended` only if a needed glyph is missing from the default Material Icons artifact already on the classpath; prefer default set first.
- Icon color follows Material3 / Carbon theme tokens — do not paint menu icons gold/cyan from the logo.
- Spacing: 8dp between icon and label inside buttons/rows.
- Do not convert settings rows into card chrome; keep existing Account layout.

## Dependencies / wiring

- Ensure Compose Material Icons dependency is present (`androidx.compose.material:material-icons-extended` if needed).
- Centralize a tiny helper optional: `IconLabelRow` / reuse `Row`+`Icon`+`Text` inline — no large new design system.

## Testing

- Unit tests not required for pure Compose icon wiring.
- Manual: install APK → confirm launcher shows `logomoney`; bottom nav icons visible on all 5 tabs; Account scan/sign-out and list Edit/Delete show icons; Login shows logo.

## Extra folders UX (Account)

Make “โฟลเดอร์เพิ่ม” selection unmistakable:

1. Split into two labeled groups:
   - **เลือกแล้ว** — selected folders only
   - **ยังไม่เลือก** — remaining folders
2. Selected chips: primary fill + leading `Check` icon + stronger contrast text.
3. Unselected chips: outlined / muted + leading `Folder` icon (no primary fill).
4. Empty selected group shows muted helper: “ยังไม่ได้เลือกโฟลเดอร์เพิ่ม”.

Keep existing `selectedBucketIds` / `AutoScanStore` behavior; UI-only change.

## Version

Bump Android `versionName` / `versionCode` and ship APK as part of implementation (same pattern as recent builds).
