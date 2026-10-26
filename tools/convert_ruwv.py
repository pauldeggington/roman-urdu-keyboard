#!/usr/bin/env python3
"""
Converts the RUWV-NSR word-variation sheet into the small file the app reads.

Source: Ahmed, M. (2024). Roman Urdu Word Variations and Normalized Sentiment Review Dataset (RUWV-NSR).
        Mendeley Data, V5, doi:10.17632/v5jfhsvtmd.5. Licence: CC BY 4.0.
        https://data.mendeley.com/datasets/v5jfhsvtmd/5

Input : "Roman Urdu words and English Translation with spelling variation.csv" (or the same sheet as .xlsx),
        with columns var-1 .. var-5 and Common.
Output: app/src/main/assets/roman_urdu/variants.tsv   (variant <TAB> most common spelling)

Changes made to the original data (this is an adaptation under CC BY 4.0):
  * the sheet is flattened into one "variant -> common spelling" line per variant, everything lower-cased;
  * identity lines are dropped;
  * a variant that is itself the common spelling of any word is dropped (it is a real spelling, not a variant);
  * a variant that maps to two different common spellings is dropped;
  * variants shorter than 3 letters are dropped;
  * a variant is kept only if it is a spelling variant and not another form of the word: it must end with the same
    letter as the common spelling, and if that letter is a vowel the last two letters must match too (so karte ->
    karti and kaisi -> kaise, which change gender or number, are dropped), and it must be within 3 edits of it;
  * variants that are ordinary English words (wordfreq Zipf >= 3.0, e.g. "garden", "main", "phone") are dropped, so
    English words mixed into a message are never rewritten;
  * the English translation column is not used.

Usage: python3 tools/convert_ruwv.py path/to/words.csv   (needs: pip install wordfreq; openpyxl only for .xlsx)
"""
import csv
import re
import sys
from collections import defaultdict

from wordfreq import zipf_frequency

OUT = "app/src/main/assets/roman_urdu/variants.tsv"
WORD = re.compile(r"^[a-z']{3,30}$")
ENGLISH_ZIPF = 3.0
VOWELS = set("aeiou")


def same_word(variant, common):
    """True if 'variant' looks like a spelling variant of 'common', not a different inflection of the word."""
    if variant[-1] != common[-1]:
        return False
    if variant[-1] in VOWELS and variant[-2:] != common[-2:]:
        return False
    return edit_distance(variant, common) <= 3


def edit_distance(a, b):
    prev = list(range(len(b) + 1))
    for i, ca in enumerate(a, 1):
        cur = [i]
        for j, cb in enumerate(b, 1):
            cur.append(min(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + (ca != cb)))
        prev = cur
    return prev[-1]


def norm(v):
    return "" if v is None else str(v).strip().lower()


def read_rows(path):
    if path.lower().endswith((".xlsx", ".xlsm")):
        import openpyxl
        wb = openpyxl.load_workbook(path, read_only=True, data_only=True)
        for ws in wb.worksheets:
            rows = [list(r) for r in ws.iter_rows(values_only=True)]
            if rows and any(norm(c) == "common" for c in rows[0]):
                return rows
        sys.exit("No sheet with a 'Common' column found.")
    for enc in ("utf-8-sig", "utf-8", "cp1252"):
        try:
            with open(path, encoding=enc, newline="") as f:
                return list(csv.reader(f))
        except UnicodeDecodeError:
            continue
    sys.exit("Could not read the file as text.")


def main(path):
    rows = read_rows(path)
    header = [norm(c) for c in rows[0]]
    if "common" not in header:
        sys.exit("No 'Common' column found.")
    common_i = header.index("common")
    var_is = [i for i, h in enumerate(header) if h.startswith("var")]

    commons = set()
    candidates = defaultdict(set)  # variant -> set of common spellings
    words = 0
    for row in rows[1:]:
        if len(row) <= common_i:
            continue
        common = norm(row[common_i])
        if not WORD.match(common):
            continue
        words += 1
        commons.add(common)
        for i in var_is:
            if i < len(row):
                v = norm(row[i])
                if WORD.match(v) and v != common:
                    candidates[v].add(common)

    kept = {}
    stats = defaultdict(int)
    for v, cs in candidates.items():
        if len(cs) != 1:
            stats["ambiguous"] += 1
        elif v in commons:
            stats["is a common spelling itself"] += 1
        elif zipf_frequency(v, "en") >= ENGLISH_ZIPF:
            stats["English word"] += 1
        elif not same_word(v, next(iter(cs))):
            stats["different ending or too far from the common spelling"] += 1
        else:
            kept[v] = next(iter(cs))

    with open(OUT, "w", encoding="utf-8") as f:
        for v in sorted(kept):
            f.write(f"{v}\t{kept[v]}\n")
    print(f"{words} words; {len(candidates)} candidate variants; wrote {len(kept)} to {OUT}")
    for k, n in sorted(stats.items()):
        print(f"  dropped {n}: {k}")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    main(sys.argv[1])
