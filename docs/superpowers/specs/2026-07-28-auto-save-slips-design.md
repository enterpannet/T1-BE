# Auto-save slips + reference dedupe

**Date:** 2026-07-28  
**Approved:** User OK

## Behavior

1. Account toggle **Auto-save slips** (default on when enabling for first-time gallery import).
2. After gallery/auto scan: save each slip immediately; do not open confirm queue for successes.
3. Deduplicate by **เลขที่รายการ** when present (`slip_fingerprint` = `ref:<normalized>`); else keep amount+spent_at+bank+ref fingerprint.
4. Slips missing amount or spent_at → remain in review queue only.
5. Snackbar summary: saved / skipped duplicate / need review.

## Scope

- API fingerprint change + Android auto-save path
- Manual Add slip confirm UI remains for pick-one / review queue
