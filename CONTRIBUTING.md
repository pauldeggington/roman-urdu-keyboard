# Contributing

Thanks for wanting to improve the keyboard. This page is the short version of how the project fits together and how to
make a change safely. The longer picture is in [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Quick start

You need JDK 17 or newer and the Android SDK (Android Studio installs both).

```bash
git clone https://github.com/pauldeggington/roman-urdu-keyboard
cd roman-urdu-keyboard
./gradlew :core:test            # the plain-Kotlin logic and its tests: seconds, no phone or SDK needed
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug    # the APK is in app/build/outputs/apk/debug/
```

An emulator build without the AI model (so it installs on any CPU) is `./gradlew :app:assembleDebug -PnoNative`.
It has hooks that let CI drive the keyboard and take screenshots (see `scripts/shots.sh`).

## Where things live

| Folder | What goes there |
|---|---|
| `core/` | Plain Kotlin with **no Android calls**: word suggestions, autocorrect, glide decoding, language detection, translation prompt logic. Anything new that can be written without Android belongs here, because its tests run in seconds. |
| `app/src/main/java/com/paul/urdukb/ime/` | The keyboard: input service, key view, toolbar, emoji, GIFs, clipboard, voice. |
| `app/src/main/java/com/paul/urdukb/ui/` | The app screens in Jetpack Compose. Each screen is a `...Screen.kt` (what it shows) and a `...Model.kt` (what it does). Shared pieces are in `Components.kt`. |
| `app/src/main/java/com/paul/urdukb/` | The rest of the app: model download, the engine that talks to Gemma, preferences, the activities that start the screens. |
| `app/src/main/assets/` | Word lists, emoji data and spelling data (see [CREDITS.md](CREDITS.md)). |
| `app/src/main/res/values/strings.xml` | Text shown to people. New text goes here, not in the code, so it can be translated. |
| `scripts/` | CI screenshots, README images and the showcase films. |

## Making a change

1. Branch from `main`.
2. If the logic can live in `core/`, write it there with a test. Tests that need the word list read it from `app/src/main/assets/dict/`.
3. Run `./gradlew :core:test :app:testDebugUnitTest`. If you changed how a screen looks, the screenshot test will say which picture moved; see [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md#screenshot-and-accessibility-tests-no-emulator).
4. Open a pull request. CI builds the APK, runs the tests and boots the keyboard on an emulator for screenshots.
5. Keep one change per pull request, and say what you could and could not test on a real phone.

## Style

- A new app screen is a composable in `ui/` plus a model class for its logic. The keyboard itself stays on Canvas views.
- Kotlin, 4-space indent, no wildcard imports (`.editorconfig` sets this up in most editors).
- Prefer small files. If a file passes about 500 lines, ask whether part of it should be its own class.
- User-visible text lives in `strings.xml`.
- Do not add a dependency without a reason; the app has three on purpose. Versions live in `gradle/libs.versions.toml`.

## Translating the app

All screen text is in `app/src/main/res/values/strings.xml`. To add a language, copy it to `values-xx/strings.xml` (see `values-ur/` for Urdu) and translate the values; keep `%1$s`-style placeholders as they are. Android picks the file by the phone's language.

## Versions and releases

`VERSION` holds major.minor. CI adds the build number (for example 2.1.122). Merging to `main` publishes a release; other
other branches publish no release (the APK is a download on the CI run). The main APK is the R8-shrunk build; `-standard.apk` beside it is the same app unshrunk. See [CHANGELOG.md](CHANGELOG.md).

## Privacy rules for contributors

Nothing the person types may leave the phone. No analytics, no crash reporting that includes text, no new permission
without a note in the README privacy section.
