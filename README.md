# Roman Urdu Keyboard (private)

Android keyboard that translates English to Roman Urdu on the phone, offline, with Gemma. Package `com.lanternkeys.urdukb`.

This repository is private. Copyright © 2026 Paul Eggington, all rights reserved (see `LICENSE`).
Third-party credits and licences: `CREDITS.md` and `licenses/`.

The public face of the app (website, privacy policy, release downloads, issues) lives in a separate public repo,
`pauldeggington/roman-urdu-keyboard`. See [docs/PUBLISHING.md](docs/PUBLISHING.md).

## Build

```bash
./gradlew :core:test :app:testDebugUnitTest   # plain-Kotlin logic and screenshot tests
./gradlew :app:assembleDebug -PnoNative       # emulator build without the AI model
```

CI (`.github/workflows/build.yml`) builds the APK and the Play bundle on every push to `main`, tags `v<VERSION>.<build>`
and publishes the release to the public repo. Notes on the layout are in [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

| Folder | What it holds |
|---|---|
| `core/` | Plain Kotlin, no Android calls: suggestions, autocorrect, glide, language detection, translation prompts |
| `app/` | The keyboard, the app screens (Compose), model handling and resources |
| `gemma3pack/` | Play Asset Delivery pack for the built-in Gemma 3 model |
| `docs/` | Website source and images (exported to the public repo by `scripts/export_public.sh`) |
| `scripts/`, `tools/` | CI screenshots, README/site images, data conversion |
