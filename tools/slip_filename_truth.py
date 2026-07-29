"""Derive slip transaction datetime from gallery filenames.

Bank apps encode the transaction timestamp in the saved filename. That gives us
free ground truth for the one field the OCR pipeline fails on most (spentAt),
across every sample in slip/ — no manual labelling.

Formats found in slip/ (310 files):

  K PLUS   016DDDHHMMSS<TYPE><SEQ>        016209023213BOR02751  -> Jul 28, 02:32:13
           ^^^ day-of-year, no year        (year must be inferred)
  K PLUS   <account>_YYYYMMDD_HHMMSS      004999006314305_20260604_182602
  Krungsri receipt_YYYYMMDDHHMMSS         receipt_20260607044746
  KTB      <epoch-millis>                 1780401364698

Run standalone to print a coverage report:
    python tools/slip_filename_truth.py
"""
from __future__ import annotations

import re
from dataclasses import dataclass
from datetime import date, datetime, timedelta, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SLIP_ROOT = ROOT / "slip"

TZ_BANGKOK = timezone(timedelta(hours=7))

# Dataset was collected mid-2026; used to bound the day-of-year year inference.
# Override if you re-run this on a differently-aged corpus.
CORPUS_END = date(2026, 7, 29)

IMAGE_SUFFIXES = {".jpg", ".jpeg", ".png"}


@dataclass(frozen=True)
class Truth:
    iso: str
    source: str
    # False when the year had to be inferred rather than read from the name.
    year_is_exact: bool
    # Krungsri writes a 12-hour clock with no AM/PM marker, so the hour is
    # ambiguous: 07:27 in the filename is either 07:27 or 19:27 on the slip.
    # Verified over all 45 Krungsri samples — hours 1..12 only, never 13..23 —
    # and against two slips whose OCR read the PM variant.
    hour_ambiguous: bool = False

    @property
    def dt(self) -> datetime:
        return datetime.fromisoformat(self.iso)

    @property
    def candidates(self) -> tuple[datetime, ...]:
        """Every datetime this filename could legitimately mean."""
        if not self.hour_ambiguous:
            return (self.dt,)
        return (self.dt, self.dt + timedelta(hours=12))

    def matches(self, other: datetime, tolerance_s: int = 90) -> bool:
        """True if `other` is within tolerance of any candidate.

        The filename records the save time, which trails the transaction time
        on the slip by a few seconds, hence the tolerance.
        """
        return any(
            abs((other - c).total_seconds()) <= tolerance_s for c in self.candidates
        )


def _iso(y: int, mo: int, d: int, h: int, mi: int, s: int) -> str:
    return f"{y:04d}-{mo:02d}-{d:02d}T{h:02d}:{mi:02d}:{s:02d}+07:00"


# --- K PLUS: 016 + DDD(day-of-year) + HHMMSS + type/seq ----------------------
# Mirrors kbankTxnIdPattern in SlipParser.kt so both agree on what a txn id is.
_KPLUS_TXN = re.compile(
    r"(?<![0-9A-Za-z])0?16(\d{3})(\d{6})(?:[A-Za-z]{2,4}\d{3,}|\d{6,})"
)


def _from_kplus_txn(stem: str) -> Truth | None:
    m = _KPLUS_TXN.search(stem)
    if not m:
        return None
    doy = int(m.group(1))
    hh, mm, ss = (int(m.group(2)[i : i + 2]) for i in (0, 2, 4))
    if not (1 <= doy <= 366 and hh <= 23 and mm <= 59 and ss <= 59):
        return None

    # No year in the filename. Pick the most recent year in which this
    # day-of-year has already occurred, relative to the corpus end date.
    for year in (CORPUS_END.year, CORPUS_END.year - 1):
        try:
            d = date(year, 1, 1) + timedelta(days=doy - 1)
        except ValueError:
            continue
        if d.year != year:  # doy 366 in a non-leap year
            continue
        if d <= CORPUS_END:
            return Truth(
                _iso(d.year, d.month, d.day, hh, mm, ss),
                "kplus_txn_doy",
                year_is_exact=False,
            )
    return None


# --- Krungsri: receipt_YYYYMMDDhhMMSS, hh on a 12-hour clock ----------------
_KRUNGSRI = re.compile(
    r"receipt[_\-]?(20[2-3]\d)(0[1-9]|1[0-2])(0[1-9]|[12]\d|3[01])"
    r"(0[1-9]|1[0-2])([0-5]\d)([0-5]\d)(?!\d)",
    re.IGNORECASE,
)


def _from_krungsri(stem: str) -> Truth | None:
    m = _KRUNGSRI.search(stem)
    if not m:
        return None
    y, mo, d, h, mi, s = (int(g) for g in m.groups())
    try:
        date(y, mo, d)
    except ValueError:
        return None
    # 12 means midnight on a 12-hour clock; the PM variant is handled by
    # Truth.candidates, which adds 12 hours.
    return Truth(
        _iso(y, mo, d, h % 12, mi, s),
        "krungsri_receipt_12h",
        year_is_exact=True,
        hour_ambiguous=True,
    )


# --- K PLUS alt: <account>_YYYYMMDD_HHMMSS, 24-hour ------------------------
_YMD_HMS = re.compile(
    r"(?<!\d)(20[2-3]\d)(0[1-9]|1[0-2])(0[1-9]|[12]\d|3[01])"
    r"[_\-]([01]\d|2[0-3])([0-5]\d)([0-5]\d)(?!\d)"
)


def _from_explicit_ymd(stem: str) -> Truth | None:
    m = _YMD_HMS.search(stem)
    if not m:
        return None
    y, mo, d, h, mi, s = (int(g) for g in m.groups())
    try:
        date(y, mo, d)
    except ValueError:
        return None
    return Truth(_iso(y, mo, d, h, mi, s), "explicit_ymd_hms", year_is_exact=True)


# --- Krungthai NEXT: bare unix epoch milliseconds ---------------------------
_EPOCH_MS = re.compile(r"^(1[5-9]\d{11})$")

_EPOCH_MIN = datetime(2020, 1, 1, tzinfo=timezone.utc)
_EPOCH_MAX = datetime(2030, 1, 1, tzinfo=timezone.utc)


def _from_epoch_ms(stem: str) -> Truth | None:
    m = _EPOCH_MS.match(stem)
    if not m:
        return None
    dt_utc = datetime.fromtimestamp(int(m.group(1)) / 1000, tz=timezone.utc)
    if not (_EPOCH_MIN <= dt_utc < _EPOCH_MAX):
        return None
    local = dt_utc.astimezone(TZ_BANGKOK)
    return Truth(
        _iso(local.year, local.month, local.day, local.hour, local.minute, local.second),
        "epoch_ms",
        year_is_exact=True,
    )


# Order matters: the K+ txn id carries the real transaction time, while an
# explicit YYYYMMDD in a K+ filename is the save time. Try the txn id first.
_STRATEGIES = (_from_kplus_txn, _from_krungsri, _from_explicit_ymd, _from_epoch_ms)


def derive(path: Path) -> Truth | None:
    """Best-effort transaction timestamp from a slip's filename."""
    stem = path.stem
    for strategy in _STRATEGIES:
        truth = strategy(stem)
        if truth is not None:
            return truth
    return None


def iter_slips(root: Path = SLIP_ROOT):
    for bank_dir in sorted(p for p in root.iterdir() if p.is_dir()):
        for f in sorted(bank_dir.iterdir()):
            if f.suffix.lower() in IMAGE_SUFFIXES:
                yield bank_dir.name, f


def main() -> int:
    if not SLIP_ROOT.exists():
        print(f"missing {SLIP_ROOT}")
        return 1

    by_bank: dict[str, list[tuple[Path, Truth | None]]] = {}
    for bank, path in iter_slips():
        by_bank.setdefault(bank, []).append((path, derive(path)))

    print("=== filename ground-truth coverage ===\n")
    total = covered = 0
    for bank, rows in by_bank.items():
        n = len(rows)
        ok = [t for _, t in rows if t is not None]
        total += n
        covered += len(ok)
        sources: dict[str, int] = {}
        for t in ok:
            sources[t.source] = sources.get(t.source, 0) + 1
        src = ", ".join(f"{k}={v}" for k, v in sorted(sources.items())) or "-"
        print(f"{bank:18s} {len(ok):3d}/{n:3d}   {src}")
        for path, t in rows:
            if t is None:
                print(f"                   MISS  {path.name}")
    print(f"\n{'TOTAL':18s} {covered:3d}/{total:3d} ({100 * covered / total:.1f}%)")

    print("\n=== decoded samples (eyeball these) ===\n")
    for bank, rows in by_bank.items():
        for path, t in rows[:3]:
            got = f"{t.iso}  [{t.source}]" if t else "—"
            exact = "" if not t or t.year_is_exact else "  (year inferred)"
            print(f"{bank:18s} {path.name:42s} -> {got}{exact}")

    # Sanity: dates should cluster in a plausible window, not scatter randomly.
    stamps = sorted(t.dt for _, rows in by_bank.items() for _, t in rows if t)
    if stamps:
        print(f"\nrange: {stamps[0].date()} .. {stamps[-1].date()}  (n={len(stamps)})")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
