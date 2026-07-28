# Rescan buttons on slip confirm

**Date:** 2026-07-28  
**Choice:** C — rescan same image + pick another image

## UI (Confirm step)

- **สแกนใหม่** — re-run `SlipIntake.process` on `pendingImageUri`; replace form fields
- **เลือกรูปอื่น** — photo picker → process new URI
- Manual entry (no image): show pick-image only
- Queue mode: same buttons; pick other replaces current item fields (does not skip queue)

## Non-goals

- Edit-dialog rescan for already-saved txs
- Partial field merge (always full replace from new scan)
