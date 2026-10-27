# Credits

Roman Urdu Keyboard is open source under the GNU General Public License v3.0 (see `LICENSE`).

## Keyboard

Written from scratch for this app (`app/src/main/java/com/paul/urdukb`). The look follows Gboard's proportions
(key sizes, pill keys, popups) using the fonts and icons credited below.

## Translation

**Gemma 4** by Google, running on the phone through **LiteRT-LM** (Apache 2.0). https://github.com/google-ai-edge/LiteRT-LM
The model file is downloaded by the user from Hugging Face and is not part of this repository.

## Data

**RUWV-NSR**: Roman Urdu Word Variations and Normalized Sentiment Review Dataset.
Mudasar Ahmed, Quaid-e-Awam University of Engineering, Science and Technology (2024).
Mendeley Data, V5, doi:10.17632/v5jfhsvtmd.5. Licensed under **CC BY 4.0**. https://data.mendeley.com/datasets/v5jfhsvtmd/5
Changes: the sheet of word variations is flattened into one "variant -> common spelling" line per variant
(lower-cased; English words, identity lines, ambiguous variants and variants that end differently from the common
spelling are removed) by `tools/convert_ruwv.py`, and stored as `app/src/main/assets/roman_urdu/variants.tsv`.
The app uses it only to recognise different spellings of the same word: when picking examples for a message, when
detecting the language, and when applying the user's spelling glossary. The "common" spelling is never written into
a translation. The English translation column is not used.

## Icons

Material Symbols Rounded by Google (Apache 2.0; licence text in `LICENSE-Apache-2.0`), plus a few simple icons drawn for this app.

Key labels use Google Sans (text optical size, regular), Copyright 2017 Google Inc., licensed under the SIL Open Font License 1.1. Taken from the Fontsource package of Google Fonts and cut to a static Latin font; license text in docs/licenses/GoogleSans-OFL.txt.

## Glide typing word list

`app/src/main/assets/dict/en.tsv`: the 50,000 most common English words with a frequency score, taken from **wordfreq**
by Robyn Speer (https://github.com/rspeer/wordfreq, data licensed **CC BY-SA 4.0**; it includes data from Google Books
Ngrams, OpenSubtitles, Wikipedia, and other public sources listed in the wordfreq README). Changes: cut to the top 50,000
words made of letters (and common contractions), a few offensive words removed, frequency stored as an integer.
This file stays under CC BY-SA 4.0.
