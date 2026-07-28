# Share slip for OCR debug (option C)

**Date:** 2026-07-28  
**Choice:** C — share image + OCR/filename text

## Behavior

On Add-slip **Confirm**, when a slip image is present:

- Button **แชร์สลิป** opens the system share sheet (`ACTION_SEND`, `image/*`).
- Attachment is a cache copy via `FileProvider`, named with the resolved display name (e.g. `1607.jpg`).
- `EXTRA_TEXT` / `EXTRA_SUBJECT` include:
  - filename
  - parsed amount / from / to / bank / reference (if any)
  - raw OCR text (truncated if huge)

No cloud upload; share stays on-device → user’s chosen app (Line, Telegram, Files, …).
