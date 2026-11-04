#!/usr/bin/env bash
# Builds the contents of the PUBLIC repo (website, README, changelog, credits, licences) into the folder given.
# Usage: scripts/export_public.sh /path/to/public-repo-checkout
set -euo pipefail
OUT="${1:?usage: export_public.sh <folder>}"
SRC="$(cd "$(dirname "$0")/.." && pwd)"
mkdir -p "$OUT"
# website and its pictures (everything in docs/ except the private architecture and publishing notes)
rm -rf "$OUT/docs"; cp -r "$SRC/docs" "$OUT/docs"; rm -f "$OUT/docs/ARCHITECTURE.md" "$OUT/docs/PUBLISHING.md"
mkdir -p "$OUT/licenses"
cp "$SRC/CHANGELOG.md" "$SRC/CREDITS.md" "$SRC/VERSION" "$OUT/"
cp "$SRC"/licenses/* "$OUT/licenses/"
cp "$SRC/public/README.md" "$SRC/public/LICENSE" "$OUT/"
[ -f "$SRC/public/TRADEMARKS.md" ] && cp "$SRC/public/TRADEMARKS.md" "$OUT/"
touch "$OUT/docs/.nojekyll"
echo "Exported public repo contents to $OUT"
