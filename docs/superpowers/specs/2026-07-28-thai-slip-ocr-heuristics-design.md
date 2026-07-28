# Thai e-slip OCR heuristics (sample-driven)

**Date:** 2026-07-28  
**Approach:** Label/heuristic parsing from real slips (KBank, MAKE, Krungthai, Krungsri) — no per-bank coordinate templates

## Improvements shipped in app 1.18

- Status → direction: จ่ายบิล / เติมเงิน / ชำระเงิน / โอนเงินสำเร็จ → โอนออก
- Thai month dates: `ก.ค.` / `มิ.ย.` + ปี พ.ศ. หรือย่อ `69`
- Amount: prefer `จำนวนเงิน`/`จำนวน`+บาท; penalize `ค่าธรรมเนียม`, `(1022)`, masked `xxx` accounts
- Parties: inline labels, standalone `จาก`/`ไปยัง` + next line, stacked `นาย`/`น.ส.`/`บริษัท`, payee hints (ทรูมันนี่, ไลน์เพย์, BLUEPAY)
- Reference: `เลขที่รายการ`, `ค่าอ้างอิง`, `หมายเลขที่อ้างอิง`
- Memo: `บันทึกช่วยจำ:` (same line or next line)

## Tests

`SlipParserSampleSlipTest` fixtures approximate OCR text from user-provided slips (no images in repo).
