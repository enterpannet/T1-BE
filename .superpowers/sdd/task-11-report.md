# Task 11 Report: Slip OCR + parser + add-slip flow

## Status
Complete.

## Deliverables
- `SlipParser` + `SlipParserTest` (TDD): extracts amount, reference, bank, optional datetime from Thai slip OCR text.
- `SlipOcr`: on-device ML Kit text recognition; images never leave the device.
- `TransactionRepository`: `POST /transactions` with `source=slip`; maps HTTP 409 → `DuplicateSlipException` ("บันทึกไปแล้ว").
- `AddSlipScreen`: Photo Picker → OCR → editable confirm → save.
- Home "Add slip" CTA wired via `add_slip` nav route; bottom bar hidden on add-slip screen.
- Manifest: `READ_MEDIA_IMAGES` (Photo Picker fallback on older devices).

## Verification
- `./gradlew :app:testDebugUnitTest --tests com.getmoney.app.ocr.SlipParserTest` — PASS (JDK 17)
- `./gradlew :app:assembleDebug` — PASS (JDK 17)

## Commit
```
feat(android): on-device slip OCR, parser, and confirm save
```

## Notes
- Build requires JDK 17; default system JDK 25 breaks Kotlin Gradle plugin (`IllegalArgumentException: 25.0.1`).
- ML Kit Latin recognizer used; Thai numerals/labels in parser regex handle common SCB/KBank/PromptPay slips.
- Manual smoke (real slip screenshot → confirm → Home % update) not run in CI; recommended on emulator with API running at `10.0.2.2:8080`.

---

## Task 11 follow-up (Important findings)

### Status
Complete.

### Changes
- **Enter manually** on Pick step: opens Confirm with blank amount and default spent-at (no OCR required).
- **OCR failure**: error message retained on Pick; **Enter manually** offered alongside retry via Pick image.
- **Amount validation**: Confirm disabled when amount blank or ≤ 0; field shows “Enter an amount greater than 0”.
- **UI note**: OCR hint that Latin digits (0–9) work best (ML Kit has no Thai script model).

### Verification
- `./gradlew :app:testDebugUnitTest --tests com.getmoney.app.ocr.SlipParserTest --tests com.getmoney.app.ui.slip.SlipAmountValidationTest` — PASS (JDK 17)
- `./gradlew :app:assembleDebug` — PASS (JDK 17)

### Commit
```
fix(android): add-slip manual entry and amount validation
```
