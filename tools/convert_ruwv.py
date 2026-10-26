#!/usr/bin/env python3
"""
Converts the RUWV-NSR word-variation sheet into the small file the app reads.

Source: Ahmed, M. (2024). Roman Urdu Word Variations and Normalized Sentiment Review Dataset (RUWV-NSR).
        Mendeley Data, V5, doi:10.17632/v5jfhsvtmd.5. Licence: CC BY 4.0.
        https://data.mendeley.com/datasets/v5jfhsvtmd/5

Input : the Excel file with columns "Var-1" ... "Var-5" and "common".
Output: app/src/main/assets/roman_urdu/variants.tsv   (variant <TAB> most common spelling)

Change made to the original data: the sheet is flattened into one "variant -> common spelling" line per variant,
lower-cased, with duplicates and identity lines removed. A variant that maps to two different common spellings is dropped.

Usage: python3 tools/convert_ruwv.py path/to/RUWV.xlsx
"""
import re
import sys
from collections import defaultdict

import openpyxl

OUT = "app/src/main/assets/roman_urdu/variants.tsv"
WORD = re.compile(r"^[a-z']{1,30}$")


def norm(v):
    if v is None:
        return ""
    return str(v).strip().lower()


def main(path):
    wb = openpyxl.load_workbook(path, read_only=True, data_only=True)
    sheet = None
    for ws in wb.worksheets:
        header = [norm(c) for c in next(ws.iter_rows(min_row=1, max_row=1, values_only=True))]
        if "common" in header and any(h.startswith("var") for h in header):
            sheet = ws
            break
    if sheet is None:
        sys.exit("Could not find a sheet with 'Var-1..Var-5' and 'common' columns.")

    header = [norm(c) for c in next(sheet.iter_rows(min_row=1, max_row=1, values_only=True))]
    common_i = header.index("common")
    var_is = [i for i, h in enumerate(header) if h.startswith("var")]

    mapping = defaultdict(set)
    rows = 0
    for row in sheet.iter_rows(min_row=2, values_only=True):
        common = norm(row[common_i])
        if not WORD.match(common):
            continue
        rows += 1
        for i in var_is:
            variant = norm(row[i])
            if WORD.match(variant) and variant != common:
                mapping[variant].add(common)

    kept = {v: next(iter(c)) for v, c in mapping.items() if len(c) == 1}
    dropped = len(mapping) - len(kept)
    with open(OUT, "w", encoding="utf-8") as f:
        for v in sorted(kept):
            f.write(f"{v}\t{kept[v]}\n")
    print(f"{rows} words, {len(kept)} variants written to {OUT} ({dropped} ambiguous variants dropped)")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    main(sys.argv[1])
