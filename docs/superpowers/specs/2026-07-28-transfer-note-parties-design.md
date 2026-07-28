# Transfer parties + readable notes

**Date:** 2026-07-28  
**Approach:** Confirm-screen fields + OCR best-effort; persist structured summary inside existing `note` (no API migration this round)  
**Surface:** Android OCR (`SlipParser` / `SlipDraft`), Add Slip confirm, Edit dialog, Transaction list

## Goal

1. Make **บันทึกช่วยจำ** easy to read in full (own line(s), multi-line input).
2. Detect and show whether a slip is **โอนออก** or **รับเข้า**, plus **จาก** / **ถึง** names when OCR can find them.
3. Let the user correct those fields before save.

## Non-goals

- New API columns (`direction`, `from_name`, `to_name`) — deferred
- Changing budget math / treating รับเข้า as income in summary (still one transaction amount; display-only direction for now)
- Perfect OCR for every bank layout
- Cloudinary / image pipeline changes

## Data model (client)

Extend `SlipDraft`:

```kotlin
data class SlipDraft(
    val amount: String,
    val spentAtIso: String? = null,
    val bank: String? = null,
    val reference: String? = null,
    val note: String? = null,           // user memo only when composing UI
    val direction: TransferDirection? = null, // OUT | IN | null
    val fromName: String? = null,
    val toName: String? = null,
)

enum class TransferDirection { OUT, IN }
```

`enrichSlipDraftFromOcr` fills blank `direction` / `fromName` / `toName` / `note` the same way as bank/ref.

## OCR extraction (best-effort)

In `SlipParser.parse`:

**Direction**
- Prefer **IN** if text matches (case-insensitive / Thai): `รับเงิน`, `รับโอน`, `เงินเข้า`, `Received`, `Incoming`, `Credit`
- Prefer **OUT** if matches: `โอนเงิน`, `โอนสำเร็จ`, `โอนออก`, `Transfer`, `Paid`, `Payment successful`
- If both or neither → default **OUT** (personal expense app)

**Names** (first non-empty capture wins per role)
- From labels: `จาก`, `จากบัญชี`, `ผู้โอน`, `From`, `Sender`, `Payer`
- To labels: `ถึง`, `ไปยัง`, `ผู้รับ`, `เข้าบัญชี`, `To`, `Recipient`, `Payee`
- Pattern: label + optional `:` + rest of line (trim; strip masked account tails like `x1234` kept if present)
- Ignore lines that are only bank keywords or pure account numbers longer than ~12 digits with no letters

QR path: if merchant name exists and `toName` blank, set `toName = merchantName`; direction default OUT; do not overwrite OCR names when enriching.

## Persist into `note` (no API change)

On save (Add Slip / Edit), build stored `note` as:

```
[DIRECTION_LINE]
[ROUTE_LINE]
[MEMO]

DIRECTION_LINE = "โอนออก" | "รับเข้า"  (omit if user cleared direction — always keep one of the two when using confirm UI; default โอนออก)
ROUTE_LINE = "จาก: {from} → ถึง: {to}"  (omit missing side: "จาก: X" or "ถึง: Y" alone OK; omit line if both blank)
MEMO = user บันทึกช่วยจำ (may be multi-line); omit if blank
```

Join non-blank parts with `\n`. Store `null` if everything blank.

**Parse for display** (`NoteParts` helper):
- If first line is exactly `โอนออก` or `รับเข้า` → direction
- If a following line matches `จาก:` / `ถึง:` / `→` route pattern → route
- Remaining lines → memo

Legacy notes (free text / `QR: …`) → treat entire string as memo (no direction/route).

## UI

### Add Slip confirm
- Separate fields (editable):
  - Direction: segmented / two choices — **โอนออก** | **รับเข้า** (default from OCR or OUT)
  - จาก (from name)
  - ถึง (to name)
  - **บันทึกช่วยจำ** — multi-line (`minLines = 2`, `maxLines = 5`), not single-line
- Pre-fill from draft; user edits win on save

### Edit transaction dialog
- Same four fields; parse existing `note` into parts on open; re-compose on save
- Amount field unchanged

### Transaction list (`TransactionListItem`)
Separate lines (no `bank · note` mash):
1. Amount  
2. Date/time  
3. Bank (if any)  
4. Direction + route (if parsed), e.g. `โอนออก · จาก: A → ถึง: B` — **wrap fully**, no ellipsis  
5. Memo under label or plain text — **wrap fully** (`maxLines` unset / high)

Thai label on inputs: **บันทึกช่วยจำ** (not "Note").

## Testing

Unit tests:
- Direction OUT/IN keyword fixtures
- From/To line extraction
- `composeNote` / `parseNote` round-trip
- Legacy free-text note still displays as memo

Manual: scan a transfer slip → confirm shows direction/names → save → list shows full wrapped text.

## Version

Bump Android version and ship APK with the change.
