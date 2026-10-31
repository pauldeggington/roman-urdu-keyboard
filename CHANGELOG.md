# Changelog

Every release on `main` has a version: **major.minor.build**. The major and minor numbers (`VERSION` in the repo) go up when
a set of changes is merged to `main`; the build number is the CI build, so a newer build always installs over an older one.
The app shows it as, for example, "Version 2.1 (build 121)". Work-in-progress builds are not published as releases; they are only kept as a download on the CI run.

## 2.5

- **Google Play bundle:** every release also carries a `.aab` file, the format Google Play takes. The Play version has no in-app updater, because Play updates the app itself and does not allow an app to update itself another way.

- **New app ID: `com.lanternkeys.urdukb`.** The app's ID on the phone changes (the name you see, Roman Urdu Keyboard, stays). This is needed to publish on Google Play, and it means 2.5 installs **beside** the old app, not over it. After installing it, go through the short setup again (the translator is downloaded again, about 2.6 GB), switch to the new keyboard, then uninstall the old one. The old app keeps working but will not receive updates.

## 2.4

- **Meaning, not word order:** Urdu puts the verb last and English does not, so the model is told in both directions to translate what is meant and not the word order, with examples.
- **Roman Urdu all the way:** Urdu voice notes are now written down as Roman Urdu and translated by the keyboard's own Roman Urdu to English translation (its examples and spelling handling, plus rules for text that was heard, not typed). The English is double-checked by turning it back into Roman Urdu.
- **Careful listening (switch in the Voice card):** each Urdu voice note is heard four ways: normal speed, slowed by a quarter, faster by a sixth, and written as Roman Urdu. If two of the first three agree, that wins; if not, the model compares all four and picks the most likely words. Then the English is turned back into Urdu and compared with what was heard; if it does not match, it is translated once more, closer to the Urdu, and if it still does not match the screen says the English may be off. More accurate, several times slower.
- **Cleaner audio:** before listening, low rumble is removed, silence at the start and end is trimmed, and a quiet recording is raised (never more than 8 times).
- **Also heard as:** when the listening passes disagree, the other readings are listed, and tapping Use this translates that reading instead.
- **Mixed Urdu and English:** the translation step keeps English words, names and places as they are and translates the Urdu around them, with worked examples.

- **Better Urdu voice notes:** the model now writes down what it hears first, then a separate step translates that transcript, which gave far better English than doing both at once. English names and places in the note are kept in Latin letters.

- **Voice, Urdu to English:** share an Urdu voice note from WhatsApp (Share, then Roman Urdu Keyboard) and the on-device model listens and shows Urdu text and English, both copyable. Long notes are cut into pieces at quiet moments (the model hears 30 seconds at a time).
- **Voice, English to Urdu:** speak English in the app (Voice card, Speak) and get the Urdu in script and Roman Urdu, read aloud by the phone's own Urdu voice, saved as an audio message you can send. Needs the Urdu voice installed in the phone's Text-to-speech settings.
- Everything runs on the phone; nothing is uploaded.

## 2.3

- **Tone matrix:** Roman Urdu translation now has five tones: Casual, Formal, and new Poetic, Funny and Respectful (for elders). **Auto** (the default) picks the tone from the sentence, for example "Please give my salaam to Mother" is Respectful and "Good morning, my love" is Poetic (which also covers warm words to a partner or family). Tap the tone key on the toolbar to pick one by hand. Each tone has its own rules and example bank, grouped by situation.
- **Spelling follows the Urdu letters:** parhna, barha, larka, phir, fikar, zindagi, waqt (not padhna, badha, ladka, fir, phikar, jindagi, vaqt). The model is told the rule and every answer is checked against about 190 built-in fixes; your own glossary still wins.
- **Smart tone:** in Auto, when the words alone do not show the tone, the on-device model reads the sentence first and works out the tone, who it is to (an elder always gets Respectful) and what it is about, which picks the closest examples. Clear cases stay instant. There is a switch for it in settings.
- **Words stay words:** a single word or short phrase ("friend") now comes back as that word, not as a question.
- **Instructions hidden:** the model's instructions are built in and no longer shown or editable in the app, so improvements reach everyone. The example bank and the glossary stay editable.

## 2.2

- **Easier to change:** the app screens (settings, setup guide, Translate sheet) are rewritten in Jetpack Compose, each split into a screen and a model. The plain-Kotlin logic (suggestions, glide, detection, translation prompts) moved into its own `core` module with fast tests, and the dependency versions are in one catalog. Everything looks and works as before.
- **Text in one place:** the screens' and keyboard's messages are in `strings.xml`, ready for translation.
- Contributor guide, architecture page and editor settings added.
- **Smaller download:** the main APK is now shrunk with R8 (about a third of the size). The unshrunk build is attached to each release as `-standard.apk`, in case anything misbehaves.
- **Urdu app screens:** the app and setup screens, and the keyboard's messages, are available in Urdu script when the phone's language is Urdu.
- **Easier to read and use:** every tap target is at least 48dp, switches and colour choices are labelled for TalkBack, Urdu text is drawn larger (it reads small at the same size), the back arrow mirrors in right-to-left, and line heights have room for large text. The Android-free `core` tests check the contrast of every colour swatch, in light and dark.
- **State survives:** turning the screen or changing the colour no longer resets the settings and setup screens mid-download.
- **Cleaner code:** all settings in one typed `KbSettings` class; the keyboard service is split into `TypingAssist`, `MediaSender`, `VoiceController` and `PreviewHooks`.

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
