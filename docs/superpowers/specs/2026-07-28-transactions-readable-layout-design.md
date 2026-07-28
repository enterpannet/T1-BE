# Transactions list readability

**Date:** 2026-07-28  
**Surface:** Android `TransactionsScreen` + shared `TransactionListItem` (Home preview uses same row)

## Layout

1. Group rows by calendar day (Bangkok): headers **วันนี้** / **เมื่อวาน** / `d MMM yyyy`
2. Compact row (tap whole row):
   - Left: 56dp slip thumb (reuse `SlipImagePreview` size override or smaller)
   - Primary: amount (bold)
   - Secondary: `โอนออก`/`รับเข้า` · `จาก → ถึง` (or bank if no parties)
   - Tertiary: time `HH:mm` + optional memo (1 line ellipsis)
3. No inline Edit/Delete buttons
4. Tap → detail dialog/sheet: full fields + Edit + Delete

## Non-goals

- Search/filter this round
- Per-file scan history (separate topic)
