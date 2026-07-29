"""Pick a stratified slip sample and downscale it for hand-labelling.

Ground truth for amount, direction, and party names can't be derived from
filenames or QR codes the way spentAt and reference can — someone has to read
the slips. This prepares a batch small enough to read in one sitting.

Sampling is stratified by bank and deliberately overweights the smaller banks,
so Krungsri and Krungthai get enough samples to say anything about. Accuracy
must therefore be read per bank, never pooled into one headline number.

    python tools/pick_label_batch.py --out <dir> [--per-bank "K PLUS=24,..."]
"""
from __future__ import annotations

import argparse
import json
import random
import sys
from pathlib import Path

from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
from slip_filename_truth import derive, iter_slips  # noqa: E402

DEFAULT_QUOTA = {"K PLUS": 24, "Krungsri": 10, "Krungthai NEXT": 6}

# Slip text is large relative to the page, so this stays legible while keeping
# each image cheap enough to read a batch of them.
MAX_SIDE = 1100


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", required=True)
    ap.add_argument("--seed", type=int, default=7)
    ap.add_argument("--per-bank", default="")
    args = ap.parse_args()

    quota = dict(DEFAULT_QUOTA)
    if args.per_bank:
        for part in args.per_bank.split(","):
            bank, _, n = part.partition("=")
            quota[bank.strip()] = int(n)

    by_bank: dict[str, list[Path]] = {}
    for bank, path in iter_slips():
        by_bank.setdefault(bank, []).append(path)

    out_dir = Path(args.out)
    out_dir.mkdir(parents=True, exist_ok=True)

    rng = random.Random(args.seed)
    manifest = []
    for bank, paths in by_bank.items():
        n = min(quota.get(bank, 0), len(paths))
        for path in rng.sample(paths, n):
            img = Image.open(path).convert("RGB")
            if max(img.size) > MAX_SIDE:
                scale = MAX_SIDE / max(img.size)
                img = img.resize(
                    (round(img.width * scale), round(img.height * scale)),
                    Image.LANCZOS,
                )
            dest = out_dir / f"{len(manifest):02d}_{path.stem}.jpg"
            img.save(dest, quality=88)

            truth = derive(path)
            manifest.append(
                {
                    "index": len(manifest),
                    "bank": bank,
                    "file": path.name,
                    "scaled": dest.name,
                    "filenameSpentAt": None if truth is None else truth.iso,
                }
            )

    (out_dir / "manifest.json").write_text(
        json.dumps(manifest, ensure_ascii=False, indent=1), encoding="utf-8"
    )
    for bank in by_bank:
        got = sum(1 for m in manifest if m["bank"] == bank)
        print(f"{bank:18s} {got:3d}")
    print(f"\n{len(manifest)} images -> {out_dir}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
