# My identity → transfer direction

**Date:** 2026-07-28  
**Approved:** User asked to add “ชื่อเริ่มต้น” for IN/OUT inference

## Behavior

- Account settings: multi-line **ชื่อของฉัน** + **บัญชีของฉัน** (masked ok).
- On slip intake, if จาก matches me and ถึง does not → **โอนออก**.
- If ถึง matches me and จาก does not → **รับเข้า**.
- If unclear / unset → keep OCR status heuristic (default OUT).
- Match: substring on normalized names; account mask / last-4 digits.

## Storage

Local DataStore only (`my_identity`).
