# On-device Thai OCR (Tesseract)

**Date:** 2026-07-28  
**Choice:** A — Tesseract `tha+eng` on device (no cloud)

## Problem

ML Kit Text Recognition Latin cannot read Thai names/banks on e-slips, so from/to parties stay empty.

## Design

1. Replace `SlipOcr` text engine with **Tesseract 4** (`tha+eng`).
2. Bundle `tessdata_fast` for `tha` + `eng` under `assets/tessdata/`; copy to `filesDir/tessdata` on first use.
3. Keep **ML Kit barcode** for QR-first path unchanged.
4. OCR output still feeds existing `SlipParser` (parties/amount/date heuristics).
5. Also parse masked accounts (`xxx-x-x####-x`) into party lines when names exist or as fallback labels.

## Non-goals

- Cloud Vision
- Region templates per bank
- Removing ML Kit entirely (QR still uses it)
