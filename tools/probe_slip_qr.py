"""Probe how much ground truth the slip QR codes give us for free.

Dates came free from filenames. The open question is whether amount can come
free from the EMV/PromptPay QR that Thai banks print on e-slips — if it can, we
can score SlipParser's amount extraction without paying for manual or LLM
labels.

Prints, per bank: how many slips carry a decodable QR, and how many of those
expose an amount (EMV tag 54) or a reference (tag 62/05).

    python tools/probe_slip_qr.py
"""
from __future__ import annotations

import sys
from pathlib import Path

import cv2
import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent))
from slip_filename_truth import iter_slips  # noqa: E402


def parse_emv(payload: str) -> dict[str, str]:
    """Flatten an EMVCo TLV string into {tag: value}, one level deep.

    Nested templates (26-51, 62, 80-99) are recursed into with dotted tags so a
    PromptPay reference shows up as e.g. "62.05".
    """
    out: dict[str, str] = {}

    def walk(s: str, prefix: str = "") -> None:
        i = 0
        while i + 4 <= len(s):
            tag = s[i : i + 2]
            length_raw = s[i + 2 : i + 4]
            if not tag.isdigit() or not length_raw.isdigit():
                return
            length = int(length_raw)
            value = s[i + 4 : i + 4 + length]
            if len(value) < length:
                return
            key = f"{prefix}{tag}"
            out[key] = value
            tag_num = int(tag)
            if 26 <= tag_num <= 51 or tag_num == 62 or 80 <= tag_num <= 99:
                walk(value, prefix=f"{key}.")
            i += 4 + length

    walk(payload)
    return out


def decode_qr(path: Path, detector: cv2.QRCodeDetector) -> list[str]:
    img = cv2.imread(str(path))
    if img is None:
        return []

    payloads: list[str] = []
    # Slip QRs are small relative to the page; upscaling markedly improves the
    # detector's hit rate, so try progressively larger renders before giving up.
    for scale in (1.0, 1.5, 2.0):
        frame = img
        if scale != 1.0:
            frame = cv2.resize(img, None, fx=scale, fy=scale, interpolation=cv2.INTER_CUBIC)
        try:
            ok, decoded, _, _ = detector.detectAndDecodeMulti(frame)
        except cv2.error:
            ok, decoded = False, []
        if ok:
            payloads = [d for d in decoded if d]
            if payloads:
                break
    return payloads


def main() -> int:
    detector = cv2.QRCodeDetector()

    stats: dict[str, dict[str, int]] = {}
    samples: list[str] = []

    for bank, path in iter_slips():
        s = stats.setdefault(bank, {"n": 0, "qr": 0, "amount": 0, "ref": 0})
        s["n"] += 1

        payloads = decode_qr(path, detector)
        if not payloads:
            continue
        s["qr"] += 1

        for payload in payloads:
            tags = parse_emv(payload)
            amount = tags.get("54")
            ref = tags.get("62.05") or tags.get("62.01")
            if amount:
                s["amount"] += 1
            if ref:
                s["ref"] += 1
            if len(samples) < 8:
                shown = payload if len(payload) <= 90 else payload[:90] + "…"
                samples.append(
                    f"{bank:16s} {path.name:34s} amount={amount!r} ref={ref!r}\n"
                    f"                 {shown}"
                )
            break

    print(f"{'bank':18s} {'n':>4s} {'qr':>5s} {'amount':>7s} {'ref':>5s}")
    tot = {"n": 0, "qr": 0, "amount": 0, "ref": 0}
    for bank, s in stats.items():
        print(f"{bank:18s} {s['n']:4d} {s['qr']:5d} {s['amount']:7d} {s['ref']:5d}")
        for k in tot:
            tot[k] += s[k]
    print(f"{'TOTAL':18s} {tot['n']:4d} {tot['qr']:5d} {tot['amount']:7d} {tot['ref']:5d}")
    if tot["n"]:
        print(
            f"\nQR decodable: {100 * tot['qr'] / tot['n']:.1f}%   "
            f"with amount: {100 * tot['amount'] / tot['n']:.1f}%"
        )

    if samples:
        print("\n=== sample payloads ===")
        for line in samples:
            print(line)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
