"""Run the app's on-device OCR models over slip/ and dump lines + ground truth.

The output feeds SlipDateAccuracyTest on the JVM side, which replays each dump
through the real SlipParser. Keeping OCR here and parsing there means we measure
the actual shipped parser rather than a Python re-implementation of it.

Image preprocessing mirrors SlipOcr.loadBitmap / upscaleIfSmall, and line
geometry mirrors PaddleSlipOcr.recognizeDocument, so the dump is as close to
what the app sees on-device as we can get off-device.

    python tools/dump_slip_ocr.py [--limit N]
"""
from __future__ import annotations

import argparse
import json
import re
import sys
import time
from pathlib import Path

import cv2
from PIL import Image
from rapidocr import RapidOCR
from rapidocr.utils.typings import LangRec, ModelType, OCRVersion

sys.path.insert(0, str(Path(__file__).resolve().parent))
from probe_slip_qr import decode_qr  # noqa: E402
from slip_filename_truth import derive, iter_slips  # noqa: E402

# K+ slip-verification QRs embed the transaction id verbatim. That is an
# independent source for the reference field — unlike the filename, which the
# parser itself consumes, so scoring against it isn't circular.
KPLUS_REF_IN_QR = re.compile(r"016\d{9}(?:[A-Z]{3}\d{5}|\d{6})")


def reference_from_qr(path: Path, detector: cv2.QRCodeDetector) -> str | None:
    for payload in decode_qr(path, detector):
        m = KPLUS_REF_IN_QR.search(payload)
        if m:
            return m.group(0)
    return None

ROOT = Path(__file__).resolve().parent.parent
MODELS = ROOT / "apps" / "android" / "app" / "src" / "main" / "assets" / "models"
DET = MODELS / "det" / "inference.onnx"
REC = MODELS / "rec" / "inference.onnx"
DICT = MODELS / "rec" / "dict.txt"

OUT = ROOT / "apps" / "android" / "app" / "src" / "test" / "resources" / "slip_ocr_dump.json"

# SlipOcr.kt companion values.
MAX_SIDE_PX = 2200
UPSCALE_BELOW = 900


def preprocess(path: Path) -> Image.Image:
    """Replicate SlipOcr.loadBitmap inSampleSize + upscaleIfSmall."""
    img = Image.open(path).convert("RGB")
    max_side = max(img.size)

    sample = 1
    while max_side / sample > MAX_SIDE_PX:
        sample *= 2
    if sample > 1:
        img = img.resize((img.width // sample, img.height // sample), Image.BILINEAR)

    if max(img.size) < UPSCALE_BELOW:
        img = img.resize((img.width * 2, img.height * 2), Image.BILINEAR)
    return img


def build_ocr() -> RapidOCR:
    return RapidOCR(
        params={
            "Det.model_path": str(DET),
            "Det.ocr_version": OCRVersion.PPOCRV5,
            "Det.model_type": ModelType.MOBILE,
            "Rec.model_path": str(REC),
            "Rec.rec_keys_path": str(DICT),
            "Rec.ocr_version": OCRVersion.PPOCRV5,
            "Rec.model_type": ModelType.MOBILE,
            "Rec.lang_type": LangRec.TH,
            "Global.use_cls": False,
            "Global.text_score": 0.3,
        }
    )


def to_lines(result) -> list[dict]:
    """Mirror PaddleSlipOcr: yCenter = mean(box.y), xLeft = min(box.x)."""
    if result is None or getattr(result, "txts", None) is None:
        return []
    boxes = getattr(result, "boxes", None)
    scores = getattr(result, "scores", None) or [1.0] * len(result.txts)

    lines = []
    for i, txt in enumerate(result.txts):
        text = (txt or "").strip()
        if not text:
            continue
        if boxes is not None and i < len(boxes):
            pts = boxes[i]
            ys = [float(p[1]) for p in pts]
            xs = [float(p[0]) for p in pts]
            y_center, x_left = sum(ys) / len(ys), min(xs)
        else:
            y_center, x_left = float(i * 10), 0.0
        lines.append(
            {
                "text": text,
                "yCenter": round(y_center, 2),
                "xLeft": round(x_left, 2),
                "confidence": round(float(scores[i]), 4),
            }
        )
    lines.sort(key=lambda ln: (ln["yCenter"], ln["xLeft"]))
    return lines


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--limit", type=int, default=0, help="stop after N slips")
    args = ap.parse_args()

    for p in (DET, REC, DICT):
        if not p.exists():
            print(f"missing model asset: {p}", file=sys.stderr)
            return 1

    print("loading RapidOCR with app Thai ONNX models...")
    ocr = build_ocr()

    slips = list(iter_slips())
    if args.limit:
        slips = slips[: args.limit]
    print(f"slips: {len(slips)}\n")

    detector = cv2.QRCodeDetector()
    records, started, no_truth, no_text, with_ref = [], time.time(), 0, 0, 0
    for i, (bank, path) in enumerate(slips, 1):
        truth = derive(path)
        if truth is None:
            no_truth += 1

        try:
            truth_ref = reference_from_qr(path, detector)
        except Exception:
            truth_ref = None
        if truth_ref:
            with_ref += 1

        try:
            lines = to_lines(ocr(preprocess(path)))
        except Exception as exc:  # keep going; a bad file shouldn't kill the run
            print(f"  !! {path.name}: {exc}", file=sys.stderr)
            lines = []
        if not lines:
            no_text += 1

        records.append(
            {
                "bank": bank,
                "file": path.name,
                "truth": None
                if truth is None
                else {
                    "iso": truth.iso,
                    "source": truth.source,
                    "yearIsExact": truth.year_is_exact,
                    "hourAmbiguous": truth.hour_ambiguous,
                },
                "truthReference": truth_ref,
                "lines": lines,
                "text": "\n".join(ln["text"] for ln in lines),
            }
        )

        if i % 25 == 0 or i == len(slips):
            rate = i / max(time.time() - started, 1e-6)
            eta = (len(slips) - i) / max(rate, 1e-6)
            print(f"  {i}/{len(slips)}  {rate:.2f} slip/s  eta {eta:.0f}s")

    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(
        json.dumps({"slips": records}, ensure_ascii=False, indent=1), encoding="utf-8"
    )

    elapsed = time.time() - started
    print(f"\nwrote {OUT}")
    print(f"  slips={len(records)}  no_truth={no_truth}  no_text={no_text}  qr_ref={with_ref}")
    print(f"  {elapsed:.0f}s total, {elapsed / max(len(records), 1):.2f}s/slip")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
