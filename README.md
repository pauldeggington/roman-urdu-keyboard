<p align="center">
  <img src="docs/logo.png" width="128" alt="Roman Urdu Keyboard logo">
</p>

<h1 align="center">Roman Urdu Keyboard</h1>

<p align="center">
  An Android keyboard that translates <b>English ⇄ Roman Urdu</b> as you type.<br>
  It runs Google's Gemma 4 on your phone, so your messages never leave the device.
</p>

<p align="center">
  <a href="https://github.com/pauldeggington/roman-urdu-keyboard/releases/latest/download/roman-urdu-keyboard.apk"><b>⬇ Download the APK</b></a>
  &nbsp;·&nbsp;
  <a href="https://github.com/pauldeggington/roman-urdu-keyboard/releases">All releases</a>
</p>

---

Type English in any app, tap **Translate** in the keyboard's toolbar, and the text turns into natural Roman Urdu.
Type Roman Urdu and it goes the other way. Choose a casual (*tum*) or formal (*aap*) tone, say whether you're male, female or prefer neutral wording,
and use the spellings you prefer (*kaisay*, *chahtay*, *barha*).

It is a fork of [HeliBoard](https://github.com/HeliBorg/HeliBoard), so you get a full keyboard that looks and feels like
Gboard: suggestions, emoji, clipboard history, one-handed mode and Material You colours.

## Features

**Translation**
- **English → Roman Urdu** and **Roman Urdu → English**, on the phone, with no internet needed after setup.
- **Auto-detect** which way to translate, or force a direction with the direction key.
- **Casual or Formal** tone: *tum* or *aap*. The tone key shows the current choice as a word, so you always know.
- **Sounds like you:** pick Male, Female or Neutral, so "I'm tired" becomes *Main thak gaya hoon*, *Main thak gayi hoon* or *Hum thak gaye hain*. *ay* spellings throughout.
- **Tap Translate again to undo** and get your original text back. The key spins while the model is working.
- **Spelling glossary:** tell it your preferred spellings (*bada → barha*) and they are used every time.
- **Example bank:** a bank of sentence pairs. For each message the keyboard picks the closest ones to guide the model.
  Edit a translation in the app and save it as an example, and the keyboard learns your style.
- Understands texting shorthand and spelling variants (*kr, nhi, tm, bht*).

**Keyboard**
- Gboard-style layout and height, borderless keys, number hints, long-press symbols, swipe the space bar to move the cursor.
- Gboard-style pill keys, circular key popups, Material Symbols icons and the Google Sans key font. Vibration on key press.
- **20 colour swatches** or wallpaper colours, light, dark or follow the phone. Pick them in the app.
- Word suggestions, emoji, clipboard history and glide typing (glide needs Google's swipe library file, see HeliBoard's docs).

**The app**
- A **setup guide** on first launch: one step per screen, buttons that jump to the right Android setting, steps that tick themselves off,
  and a practice box. The model downloads in the background with a notification while you set up the keyboard.
- Checksum verification of the model, and import from a file if you already have it.
- A *Try it* box with direction and tone, editable style rules, the example bank and glossary, and speed numbers
  (load time and seconds per translation, CPU or GPU).

## Install

1. On your Android phone, tap **[Download the APK](https://github.com/pauldeggington/roman-urdu-keyboard/releases/latest/download/roman-urdu-keyboard.apk)**
   (or open the [Releases](https://github.com/pauldeggington/roman-urdu-keyboard/releases) page and get `roman-urdu-keyboard.apk`).
2. Open the downloaded file. If Android asks, allow installs from your browser or Files app, then tap **Install**.
3. Open **Roman Urdu Keyboard**. The setup guide walks you through everything:
   - **Download the translator** (about 2.6 GB, once, best on Wi-Fi). No account or token needed. It keeps going in the background.
   - **Turn on the keyboard** and **switch to it**. The buttons open the exact Android screens, and each step ticks itself off.
   - Choose **Male / Female / Neutral**, your usual tone and a colour, then try a translation.
4. In any app, type something and tap **Translate** in the toolbar above the keys.

New versions appear on the Releases page automatically and install over the old one, keeping your settings and the translator.

**Requirements:** Android 12 or newer, an arm64 phone, about 3 GB of free storage, and 6 GB of RAM or more recommended.
The first translation after a restart is slower while the model loads. If it is slow, try *Use the GPU* in the app.

## Privacy

- Translations run **entirely on the device**. No text you type is sent anywhere.
- The `INTERNET` permission is used only to download the model file and to read its checksum from Hugging Face.
- Your settings, glossary and examples stay on the phone.

## Build it yourself

The easiest way is GitHub Actions (see `.github/workflows/build.yml`). Fork the repo and push.

To build locally you need JDK 21, the Android SDK and NDK 28:

```bash
./gradlew :app:assembleDebug
```

To get updates that install over the old app, sign every build with the same key. Add two repository secrets,
`KEYSTORE_BASE64` (a base64-encoded keystore with the alias `urdukb`) and `KEYSTORE_PASSWORD`. Without them the build uses a random
debug key.

## Project layout

| Path | What it is |
|---|---|
| `app/src/main/java/com/paul/urdukb/` | The Roman Urdu parts: setup app, model engine, translate and tone logic, tools panel, glossary, example bank |
| `app/src/main/java/helium314/keyboard/` | HeliBoard, with small changes (toolbar keys, defaults, tools panel hooks) |
| `app/src/main/assets/roman_urdu/` | Spelling variant data (see [CREDITS.md](CREDITS.md)) |
| `tools/convert_ruwv.py` | Converts the spelling data into the file the app reads |
| `docs/HeliBoard-README.md` | The original HeliBoard README |

## Status

A personal project, young and changing quickly. Roman Urdu has no standard spelling and a small on-device model makes mistakes,
so check anything important. Bug reports and ideas are welcome as issues.

## Credits and licence

Built on HeliBoard (Helium314 and contributors), Google's Gemma 4 and LiteRT-LM, and the RUWV-NSR spelling dataset by
Mudasar Ahmed. Full credits are in [CREDITS.md](CREDITS.md).

This project is open source under the **GNU General Public License v3.0**. See [LICENSE](LICENSE).
