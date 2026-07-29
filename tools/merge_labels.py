"""Merge hand-read labels into the OCR fixture so the JVM harness can score them.

Labels grow independently of OCR — you read a few more slips, you don't re-run
the models — so this patches the existing fixture in place instead of forcing a
full regeneration.

    python tools/merge_labels.py
"""
from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DUMP = ROOT / "apps/android/app/src/test/resources/slip_ocr_dump.json"
LABELS = ROOT / "tools/slip_labels.json"


def main() -> int:
    if not DUMP.exists():
        print(f"missing {DUMP} — run tools/dump_slip_ocr.py first")
        return 1
    if not LABELS.exists():
        print(f"missing {LABELS} — nothing to merge")
        return 1

    dump = json.loads(DUMP.read_text(encoding="utf-8"))
    labels = json.loads(LABELS.read_text(encoding="utf-8"))["labels"]

    attached = 0
    for slip in dump["slips"]:
        label = labels.get(slip["file"])
        slip["label"] = label
        if label is not None:
            attached += 1

    DUMP.write_text(json.dumps(dump, ensure_ascii=False, indent=1), encoding="utf-8")

    unmatched = set(labels) - {s["file"] for s in dump["slips"]}
    print(f"attached {attached}/{len(dump['slips'])} labels")
    if unmatched:
        print(f"warning: {len(unmatched)} labels match no slip: {sorted(unmatched)[:5]}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
