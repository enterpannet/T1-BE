"""Label slips with Claude vision to create ground truth we can't derive for free.

Filenames give us spentAt and the slip QR gives us the reference, but amount,
direction, and party names have no free source — they need real labels. This
buys them once, so the accuracy harness can score those fields too.

This is a labelling tool, not a runtime path: run it on a sample, commit the
labels, and keep scoring against them for free afterwards.

    export ANTHROPIC_API_KEY=...
    python tools/label_slips_with_claude.py --sample 300         # ~$8 on Opus 5
    python tools/label_slips_with_claude.py --sample 50 --model claude-haiku-4-5

Writes tools/slip_labels.json, merged into the fixture by dump_slip_ocr.py.
"""
from __future__ import annotations

import argparse
import base64
import json
import os
import random
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from slip_filename_truth import iter_slips  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "tools" / "slip_labels.json"
API_URL = "https://api.anthropic.com/v1/messages"

SYSTEM = """You transcribe Thai bank transfer slips (K PLUS, Krungsri, Krungthai NEXT).

Report only what is printed on the slip. Never infer, never compute, never
fill a field from context. If a value is not legible, return null for it —
a null is far more useful here than a guess, because these labels are used as
ground truth to grade another extractor.

Conventions:
- amount: the transferred amount only. Exclude any fee line.
- direction: OUT when the slip's account holder sent the money, IN when they
  received it. Thai slips put the sender block above the recipient block.
- from_name / to_name: copy the names exactly as printed, keeping the Thai
  title (นาย, น.ส., นาง, บริษัท). Do not transliterate or expand abbreviations.
- reference: the เลขที่รายการ / รหัสอ้างอิง value.
- spent_at: ISO-8601 with +07:00. Thai slips use Buddhist years (2569 = 2026);
  a bare two-digit year like 69 means 2569 BE.
"""

SCHEMA = {
    "type": "object",
    "properties": {
        "amount": {"type": ["number", "null"]},
        "fee": {"type": ["number", "null"]},
        "direction": {"type": ["string", "null"], "enum": ["OUT", "IN", None]},
        "from_name": {"type": ["string", "null"]},
        "to_name": {"type": ["string", "null"]},
        "bank": {"type": ["string", "null"]},
        "reference": {"type": ["string", "null"]},
        "spent_at": {"type": ["string", "null"]},
        "note": {"type": ["string", "null"]},
        "legible": {"type": "boolean"},
    },
    "required": ["amount", "direction", "from_name", "to_name", "reference", "spent_at", "legible"],
    "additionalProperties": False,
}

MEDIA_TYPES = {".jpg": "image/jpeg", ".jpeg": "image/jpeg", ".png": "image/png"}


def call_api(api_key: str, model: str, image: bytes, media_type: str, ocr_text: str) -> dict:
    body = {
        "model": model,
        "max_tokens": 2000,
        # The instructions are identical on every call, so cache them and pay
        # the ~0.1x read rate for the rest of the run.
        "system": [{"type": "text", "text": SYSTEM, "cache_control": {"type": "ephemeral"}}],
        "output_config": {"format": {"type": "json_schema", "schema": SCHEMA}},
        "messages": [
            {
                "role": "user",
                "content": [
                    {
                        "type": "image",
                        "source": {
                            "type": "base64",
                            "media_type": media_type,
                            "data": base64.standard_b64encode(image).decode(),
                        },
                    },
                    {
                        "type": "text",
                        "text": (
                            "Transcribe this slip. On-device OCR read the text below; "
                            "it is often garbled, so trust the image and use this only "
                            "as a hint for characters you cannot make out.\n\n" + ocr_text
                        ),
                    },
                ],
            }
        ],
    }
    req = urllib.request.Request(
        API_URL,
        data=json.dumps(body).encode(),
        headers={
            "content-type": "application/json",
            "x-api-key": api_key,
            "anthropic-version": "2023-06-01",
        },
    )
    with urllib.request.urlopen(req, timeout=180) as resp:
        payload = json.load(resp)

    if payload.get("stop_reason") == "refusal":
        raise RuntimeError(f"refused: {payload.get('stop_details')}")
    text = next(b["text"] for b in payload["content"] if b["type"] == "text")
    return {"label": json.loads(text), "usage": payload.get("usage", {})}


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--sample", type=int, default=50, help="how many slips to label")
    ap.add_argument("--model", default="claude-opus-5")
    ap.add_argument("--seed", type=int, default=42)
    args = ap.parse_args()

    api_key = os.environ.get("ANTHROPIC_API_KEY")
    if not api_key:
        print("ANTHROPIC_API_KEY is not set", file=sys.stderr)
        return 1

    ocr_by_file: dict[str, str] = {}
    dump = ROOT / "apps/android/app/src/test/resources/slip_ocr_dump.json"
    if dump.exists():
        for s in json.loads(dump.read_text(encoding="utf-8"))["slips"]:
            ocr_by_file[s["file"]] = s.get("text", "")

    slips = list(iter_slips())
    random.Random(args.seed).shuffle(slips)
    slips = slips[: args.sample]

    existing = {}
    if OUT.exists():
        existing = json.loads(OUT.read_text(encoding="utf-8")).get("labels", {})
        print(f"resuming: {len(existing)} already labelled")

    labels = dict(existing)
    in_tok = out_tok = 0
    started = time.time()

    for i, (bank, path) in enumerate(slips, 1):
        if path.name in labels:
            continue
        media_type = MEDIA_TYPES.get(path.suffix.lower(), "image/jpeg")
        try:
            got = call_api(
                api_key, args.model, path.read_bytes(), media_type,
                ocr_by_file.get(path.name, ""),
            )
        except (urllib.error.HTTPError, urllib.error.URLError, RuntimeError) as exc:
            detail = exc.read().decode()[:300] if isinstance(exc, urllib.error.HTTPError) else exc
            print(f"  !! {path.name}: {detail}", file=sys.stderr)
            continue

        labels[path.name] = {"bank": bank, **got["label"]}
        usage = got["usage"]
        in_tok += usage.get("input_tokens", 0) + usage.get("cache_read_input_tokens", 0)
        out_tok += usage.get("output_tokens", 0)

        # Checkpoint every slip: labels cost money, losing them to a crash hurts.
        OUT.write_text(
            json.dumps({"model": args.model, "labels": labels}, ensure_ascii=False, indent=1),
            encoding="utf-8",
        )
        if i % 10 == 0:
            print(f"  {i}/{len(slips)}  in={in_tok} out={out_tok}  {time.time() - started:.0f}s")

    print(f"\nwrote {OUT}  ({len(labels)} labels)")
    print(f"tokens: in={in_tok} out={out_tok}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
