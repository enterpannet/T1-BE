# PaddleOCR hybrid (A+B) — Implementation Plan

> **For agentic workers:** Execute task-by-task. Steps use checkbox (`- [ ]`) syntax.

**Goal:** On-device PaddleOCR Thai as primary slip text OCR; keep Tesseract + SlipParser as fallback/heuristics.

**Architecture:** `SlipOcr` tries Paddle (ppocr-sdk / ONNX) → if fail/empty → Tesseract `tha+eng` → `SlipParser` unchanged. QR remains ML Kit barcode.

**Tech Stack:** Kotlin, ONNX Runtime Android, OpenCV (via ppocr-sdk), existing Tesseract4Android

## File map

- `apps/android/ppocr-sdk/` — vendored or submodule from PaddleOCR `deploy/ppocr-android`
- `apps/android/app/src/main/assets/models/{det,rec}/` — ONNX + Thai dict/yml
- `apps/android/app/.../ocr/PaddleSlipOcr.kt` — thin wrapper
- `apps/android/app/.../ocr/SlipOcr.kt` — hybrid orchestration
- `apps/android/app/build.gradle.kts` — deps + version 1.26

---

### Task 1: Vendor ppocr-sdk + Thai models

- [ ] Sparse-fetch `deploy/ppocr-android/ppocr-sdk` into `apps/android/ppocr-sdk`
- [ ] Download mobile det ONNX + Thai rec ONNX + dict/yml into app assets
- [ ] `settings.gradle.kts` include `:ppocr-sdk`; app depends on it + onnxruntime + opencv

### Task 2: Hybrid SlipOcr

- [ ] `PaddleSlipOcr.recognize(bitmap): String`
- [ ] `SlipOcr`: Paddle first, Tesseract fallback
- [ ] OpenCV init once; release on process death ok

### Task 3: Verify + ship

- [ ] Unit tests (parser) still pass
- [ ] `assembleDebug` → `getmoney-v1.26-tmd.deals.apk`
