# App icons, logo, and folder selection UX — Implementation Plan

> **For agentic workers:** Execute task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Material Icons across nav/actions, `logomoney.PNG` as launcher + Login/Account brand mark, and Account extra-folder chips split into clear selected vs unselected groups.

**Architecture:** Add `material-icons-extended`, replace bottom-nav letter placeholders and key action labels with `Icon`+text, swap launcher drawable to resized PNG, restyle Account folder chips into two sections.

**Tech Stack:** Jetpack Compose Material3, Material Icons Extended, Android drawable resources.

## Global Constraints

- Approach A from `docs/superpowers/specs/2026-07-28-app-icons-and-logo-design.md`
- Icon tint follows theme (no gold/cyan menu icons)
- Do not commit unless user asks
- Bump to versionName `1.14` / versionCode `15` and copy APK `getmoney-v1.14-tmd.deals.apk`

---

### Task 1: Launcher logo asset

**Files:**
- Create: `apps/android/app/src/main/res/drawable/ic_launcher.png` (resized from Downloads)
- Delete: `apps/android/app/src/main/res/drawable/ic_launcher.xml`

- [ ] **Step 1:** Resize `C:\Users\admin\Downloads\logomoney.PNG` to 512×512 PNG and write to `drawable/ic_launcher.png`; remove vector `ic_launcher.xml`.
- [ ] **Step 2:** Confirm `AndroidManifest.xml` still uses `@drawable/ic_launcher`.

### Task 2: Icons dependency + bottom nav

**Files:**
- Modify: `apps/android/app/build.gradle.kts`
- Modify: `apps/android/app/src/main/java/com/getmoney/app/ui/nav/AppNav.kt`

- [ ] **Step 1:** Add `implementation("androidx.compose.material:material-icons-extended")`.
- [ ] **Step 2:** Extend `MainTab` with `imageVector`; wire NavigationBarItem `icon = { Icon(...) }` for Today/Tx/Summary/Budget/Account per spec.

### Task 3: Brand mark on Login + Account

**Files:**
- Modify: `apps/android/app/src/main/java/com/getmoney/app/ui/auth/AuthScreens.kt`
- Modify: `apps/android/app/src/main/java/com/getmoney/app/ui/account/AccountScreen.kt`

- [ ] **Step 1:** Login (`AuthForm` when title is Sign in, or always on AuthForm): show `Image(painterResource(R.drawable.ic_launcher))` ~80dp above title.
- [ ] **Step 2:** Account header: logo ~48dp beside/above “Account” title.

### Task 4: Action icons (scope C)

**Files:**
- Modify: HomeScreen, TransactionListItem, BudgetScreen, AccountScreen, AddSlipScreen (primary CTAs)

- [ ] **Step 1:** Add leading/trailing Icons per spec table (Add slip, ดูทั้งหมด, Edit, Delete, Scan now, history scan, cloud row, sign out, budget add/delete).

### Task 5: Extra folders clear selection UX

**Files:**
- Modify: `apps/android/app/src/main/java/com/getmoney/app/ui/account/AccountScreen.kt`

- [ ] **Step 1:** Split FlowRow into “เลือกแล้ว” / “ยังไม่เลือก” groups.
- [ ] **Step 2:** Selected FilterChip: check leadingIcon + primary emphasis; unselected: Folder leadingIcon + muted outline.
- [ ] **Step 3:** Empty selected helper text.

### Task 6: Version bump + APK

- [ ] **Step 1:** versionCode 15, versionName 1.14; `assembleDebug`; copy `getmoney-v1.14-tmd.deals.apk`.
