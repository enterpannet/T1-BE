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
import re
import sys
from pathlib import Path

from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
from slip_filename_truth import derive, iter_slips  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
LABELS = ROOT / "tools" / "slip_labels.json"

DEFAULT_QUOTA = {"K PLUS": 24, "Krungsri": 10, "Krungthai NEXT": 6}

# K+ encodes a three-letter transaction type in its id (…APM…, …BTF…, …BQR…),
# and the type drives the recipient block's shape: a person, a biller with
# reference lines, a merchant. Sampling across unseen types finds new layouts
# far faster than sampling at random.
KPLUS_TYPE = re.compile(r"^016\d{9}([A-Z]{3})\d{5}$")


def slip_variant(path: Path) -> str:
    m = KPLUS_TYPE.match(path.stem)
    return m.group(1) if m else "-"


def load_labelled() -> set[str]:
    if not LABELS.exists():
        return set()
    return set(json.loads(LABELS.read_text(encoding="utf-8"))["labels"])

# Slip text is large relative to the page, so this stays legible while keeping
# each image cheap enough to read a batch of them.
MAX_SIDE = 1100


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", required=True)
    ap.add_argument("--seed", type=int, default=7)
    ap.add_argument("--per-bank", default="")
    ap.add_argument(
        "--skip-labelled",
        action="store_true",
        help="leave out slips already in tools/slip_labels.json",
    )
    args = ap.parse_args()

    quota = dict(DEFAULT_QUOTA)
    if args.per_bank:
        for part in args.per_bank.split(","):
            bank, _, n = part.partition("=")
            quota[bank.strip()] = int(n)

    labelled = load_labelled() if args.skip_labelled else set()
    seen_variants = {slip_variant(Path(name)) for name in labelled}

    by_bank: dict[str, list[Path]] = {}
    for bank, path in iter_slips():
        if path.name in labelled:
            continue
        by_bank.setdefault(bank, []).append(path)

    out_dir = Path(args.out)
    out_dir.mkdir(parents=True, exist_ok=True)

    rng = random.Random(args.seed)
    manifest = []
    for bank, paths in by_bank.items():
        n = min(quota.get(bank, 0), len(paths))
        rng.shuffle(paths)
        # Unseen transaction types first, then fill from the rest.
        fresh = [p for p in paths if slip_variant(p) not in seen_variants]
        chosen: list[Path] = []
        for path in fresh:
            if len(chosen) >= n:
                break
            if slip_variant(path) in seen_variants:
                continue
            seen_variants.add(slip_variant(path))
            chosen.append(path)
        for path in paths:
            if len(chosen) >= n:
                break
            if path not in chosen:
                chosen.append(path)

        for path in chosen:
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
                    "variant": slip_variant(path),
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
