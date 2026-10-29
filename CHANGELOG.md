# Changelog

Every release on `main` has a version: **major.minor.build**. The major and minor numbers (`VERSION` in the repo) go up when
a set of changes is merged to `main`; the build number is the CI build, so a newer build always installs over an older one.
The app shows it as, for example, "Version 2.1 (build 121)". Test builds are tagged `-test` and are never offered by the in-app update.

## 2.2

- **Easier to change:** the app screens (settings, setup guide, Translate sheet) are rewritten in Jetpack Compose, each split into a screen and a model. The plain-Kotlin logic (suggestions, glide, detection, translation prompts) moved into its own `core` module with fast tests, and the dependency versions are in one catalog. Everything looks and works as before.
- **Text in one place:** the screens' and keyboard's messages are in `strings.xml`, ready for translation.
- Contributor guide, architecture page and editor settings added.
- The APK is larger (Compose adds about 15 MB); the experimental R8 test build shows how much smaller it can get.

## 2.1

- **Typing help:** word suggestions, automatic correction (with Backspace to undo), next-word suggestions, glide typing improvements.
- **Voice typing in the keyboard:** the mic listens inside the keyboard and shows words as you speak.
- **Emoji:** search understands Roman Urdu words (*khushi, pyaar, shukriya*) and forgives typos; skin tones are remembered; the screen opens faster.
- **GIFs and stickers:** a Recent chip, endless scrolling, quicker search, more categories.
- **Clipboard:** a Paste chip above the keys for what you just copied, ages on each clip, and a setting to keep clips for an hour, a day or a week.
- **Speed and fixes:** idle model unload and warm-up, caches, encrypted GIPHY key, the transparent keyboard background bug.
- **README:** new banner and layout, setup and showcase videos, FAQ, permissions table and the signing fingerprint.

## 2.0

- The rewritten keyboard: on-device Gemma translation, Gboard-style layout, emoji, GIFs, clipboard history, setup guide.
