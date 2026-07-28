# Transfer parties + readable notes — Implementation Plan

> **For agentic workers:** Execute task-by-task. Steps use checkbox (`- [ ]`) syntax.

**Goal:** OCR direction/from/to into editable confirm fields; persist structured text in `note`; show full wrapped lines in lists.

**Architecture:** `SlipDraft` gains direction/from/to; `SlipNoteCodec` compose/parse; `SlipParser` extracts parties; UI binds fields and recomposes note on save. No API migration.

**Tech Stack:** Kotlin, JUnit, Jetpack Compose Material3

## Global Constraints

- Spec: `docs/superpowers/specs/2026-07-28-transfer-note-parties-design.md`
- Labels: โอนออก / รับเข้า / จาก / ถึง / บันทึกช่วยจำ
- versionName `1.15` / versionCode `16` → `getmoney-v1.15-tmd.deals.apk`
- Do not commit unless asked

---

### Task 1: SlipNoteCodec + TransferDirection

**Files:**
- Create: `.../ocr/TransferDirection.kt`
- Create: `.../ocr/SlipNoteCodec.kt`
- Create: `.../test/.../SlipNoteCodecTest.kt`

- [ ] Tests for compose/parse round-trip + legacy memo
- [ ] Implement codec

### Task 2: SlipDraft + SlipParser parties + enrich + QR

**Files:** SlipParser.kt, SlipIntake.kt, EmvQrParser.kt, SlipParserTest.kt, SlipIntakeEnrichTest.kt

- [ ] Extend SlipDraft; extract direction/names; enrich blanks; QR toName from merchant

### Task 3: UI confirm / edit / list

**Files:** AddSlipScreen.kt, TransactionDialogs.kt, TransactionListItem.kt

- [ ] Direction toggle + from/to + multi-line บันทึกช่วยจำ; list separate lines

### Task 4: Version + APK

- [ ] 1.15 / 16; assembleDebug; copy APK
