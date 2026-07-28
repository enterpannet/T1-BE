# KBank / K+ slip completeness first

**Date:** 2026-07-28  
**Scope:** Make Kasikorn (K PLUS / KBank) e-slips reliable before other banks.

## Approach

1. Keep PaddleOCR Thai; retain line **bounding boxes** (Y order).
2. Detect K+ slips via keywords (`K+`, `ธ.กสิกรไทย`, `โอนเงินสำเร็จ` / `จ่ายบิลสำเร็จ` / …).
3. Split mid-slip into **from block → to block** using:
   - vertical gap / first masked account boundary, and
   - existing party heuristics inside each block.
4. Footer (จำนวน / ค่าธรรมเนียม / เลขที่รายการ / บันทึก) stays global text parse.
5. Non-KBank slips keep generic `SlipParser` path.

## Non-goals (this round)

- Perfect Krungsri / Krungthai layout
- Cloud / LLM
