# A+B: PaddleOCR primary + parser/Tesseract fallback

**Date:** 2026-07-28  
**Approved:** OK

## Goal

Better Thai + biller text on e-slips, still 100% on-device.

## Design

1. **Primary OCR:** PaddleOCR Thai (ONNX / Paddle-Lite) in `SlipOcr`
2. **Fallback:** existing Tesseract `tha+eng` if Paddle init/infer fails or empty text
3. **Unchanged:** ML Kit barcode (QR-first), `SlipParser` party heuristics (A)
4. Images never leave the device

## Non-goals

- Cloud Vision / server OCR
- LLM field extraction (later)
- Removing Tesseract in v1 (keep as safety net)

## Success

- APK builds; slip intake still works offline
- Thai names / billers improve vs Tesseract-only on sample slips
- Parser fixtures still pass
