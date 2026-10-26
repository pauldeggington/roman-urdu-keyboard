# Credits

Roman Urdu Keyboard is open source under the GNU General Public License v3.0 (see `LICENSE`).

## Keyboard

Built on **HeliBoard** by Helium314 and contributors (GPL-3.0), which is based on the **AOSP Keyboard**
(Apache 2.0) and **OpenBoard**. https://github.com/HeliBorg/HeliBoard
The original licence files (`LICENSE`, `LICENSE-Apache-2.0`, `LICENSE-CC-BY-SA-4.0`) are kept in this repository.
Changes in this fork: the Roman Urdu translate, tone and direction toolbar keys, the tools panel, a Gboard-like default
look and height, and the setup app (`app/src/main/java/com/paul/urdukb`).

## Translation

**Gemma 4** by Google, running on the phone through **LiteRT-LM** (Apache 2.0). https://github.com/google-ai-edge/LiteRT-LM
The model file is downloaded by the user from Hugging Face and is not part of this repository.

## Data

**RUWV-NSR**: Roman Urdu Word Variations and Normalized Sentiment Review Dataset.
Mudasar Ahmed, Quaid-e-Awam University of Engineering, Science and Technology (2024).
Mendeley Data, V5, doi:10.17632/v5jfhsvtmd.5. Licensed under **CC BY 4.0**. https://data.mendeley.com/datasets/v5jfhsvtmd/5
Changes: the sheet of word variations is flattened into one "variant -> most common spelling" line per variant
(lower-cased, duplicates and ambiguous variants removed) by `tools/convert_ruwv.py`, and stored as
`app/src/main/assets/roman_urdu/variants.tsv`. The app uses it to understand messy spellings in the text you type and to
spell-check Roman Urdu output (never touching your own glossary spellings).

Until `variants.tsv` is added to the repository, those two features use only the app's small built-in shorthand list.

## Icons

Material Design icons by Google (Apache 2.0), plus a few simple icons drawn for this app.
