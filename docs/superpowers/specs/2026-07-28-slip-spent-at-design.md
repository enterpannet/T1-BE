# Slip transfer datetime (spent_at)

**Date:** 2026-07-28  
**Requirement:** Persist the real transfer/receive datetime printed on the slip. Save/install time (`created_at`) may differ — especially when scanning gallery history after first install.

## Rules

1. Prefer OCR/QR-extracted slip datetime → `spent_at`.
2. Never silently substitute “now” for slip intake when datetime is missing.
3. Confirm UI labels field as วันเวลาบนสลิป; manual entry may default to now.
4. Today/week summaries already filter by `spent_at` (unchanged).
