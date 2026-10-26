# লিখন কীবোর্ড · Likhon Keyboard

A small, fast, **offline** Android keyboard with an English QWERTY layout and
phonetic Bangla typing. Written in plain Java against the Android framework:
no AndroidX, no third-party libraries, no internet permission, no ads.

**APK:** `build/manual/LikhonKeyboard-1.0.apk` (also copied to
`/sdcard/Download/LikhonKeyboard-1.0.apk`)

---

## Install

1. Open `LikhonKeyboard-1.0.apk` in the Files app and allow "install unknown apps"
   for that app if asked.
2. Open **Likhon Keyboard** from the app drawer.
3. Tap **Enable keyboard** → turn on "Likhon Keyboard".
4. Come back and tap **Choose keyboard** → pick **Likhon Keyboard**.
5. Type in the "Try it here" box to check it.

The keyboard then appears in every app. Long-press the **বাংলা / EN** key inside
the keyboard to open settings at any time.

## What it does

- **English QWERTY** with shift, caps-lock (double-tap shift), symbols, emoji.
- **Phonetic Bangla**: type the sound in English letters, get Bangla as you
  type (`ami` → আমি, `kemon` → কেমন). Capitals give the retroflex / long forms
  (`T` → ট, `D` → ড, `N` → ণ, `S` → ষ, `R` → ড়, `O` → ও, `I` → ঈ, `U` → ঊ).
- **Live conversion while composing**, so context-dependent spellings work:
  `kotha` → কথা, but `bhalo` → ভালো.
- **Auto-capitalise** for English sentences.
- **Key preview** pop-up, **vibration** and **click sound** options.
- **Themes**: follow system, light, or dark.
- **Smart delete**: in Bangla it removes the romanised letter and redraws the
  word (one tap does not leave a dangling া or ্).
- **Enter key** shows Search / Send / Go / Done / Next when the app asks for it.
- **Numeric keyboard** for number and phone fields.
- **Bangla digits**, `।`, `ঁ`, `্` and `৳` in Bangla mode.
- English **and** Bangla UI (follows the phone language).
- Everything is offline and the app declares **no permissions at all**.

## Bangla typing guide

| Type | Get | Type | Get |
| --- | --- | --- | --- |
| `ami` | আমি | `kemon` | কেমন |
| `tumi` | তুমি | `bhalo` | ভালো |
| `kotha` | কথা | `korbo` | করবো |
| `desh` | দেশ | `kintu` | কিন্তু |
| `kothay` | কোথায় | `tomar` | তোমার |
| `pani` | পানি | `rasta` | রাস্তা |
| `shundor` | সুন্দর | `dhonnobad` | ধন্যবাদ |

Special keys / letters:

- `ng` → ং, `^` → ্ (hasanta), `~` → ঁ, `:` → ঃ, `.` → ।
- Doubled consonants make conjuncts: `kk` → ক্ক, `nn` → ন্ন.
- Common clusters are automatic: `nt` → ন্ত, `st` → স্ত, `pr` → প্র.
- `o` is silent between consonants (কথা) and becomes ো at the end of a word
  (ভালো).
- The most frequent irregular spellings are in a small reviewed dictionary
  (`WordDictionary.java`): tomar, kothay, jodi, jabe, kore, hobe, shob,
  dhonnobad, …

The rules live in `Phonetic.java` and are easy to extend: add a consonant or
vowel mapping, or a whole word to `WordDictionary.java`, then run
`tools/check-phonetic.sh`.

## Build

The normal Gradle project is included:

```bash
./gradlew assembleDebug        # on a regular x86_64 machine with Android SDK
```

This phone (ARM64) cannot run Google's `aapt2`, which is x86_64-only, so an
equivalent ARM64 build script is included. It uses the Ubuntu/Debian
`aapt` + `d8` + `zipalign` + `apksigner` tools:

```bash
# requirements: JDK 17, aapt, zipalign, apksigner,
# Android SDK platform 34 + build-tools 34.0.0 (for android.jar and d8.jar)
tools/build-apk-arm64.sh
```

Output: `build/manual/LikhonKeyboard-1.0.apk` (signed with the debug key in
`keystore/likhon-debug.jks`, generated on first run — keep that file if you want
future builds to install over the current one).

Compile-time checks:

```bash
tools/check-phonetic.sh     # phonetic engine tests, plain JDK
```

## Project layout

```
app/src/main/java/com/likhon/keyboard/
  LikhonIme.java        InputMethodService: state, composing, commit, prefs
  KeyboardView.java     custom Canvas keyboard: drawing, touch, repeat delete
  Layouts.java          letters / symbols / more / emoji / numeric layouts
  Key.java              immutable key model
  Phonetic.java         Roman → Bangla transliteration rules
  WordDictionary.java   curated exceptions for irregular spellings
  Palette.java          light/dark colours
  Prefs.java            typed SharedPreferences
  SettingsActivity.java setup + settings screen (code-built UI)
  Ui.java               small view/drawable helpers
app/src/main/res/       manifest resources, method.xml, strings (en + bn), icons
tools/                  icon generator, phonetic check, ARM64 APK build
```

`KeyboardView` paints every key itself instead of inflating ~40 Views. That keeps
the keyboard light, lets a finger slide between keys, and makes the pop-up
preview and repeat-delete cheap.

## Known limits

- No word suggestions, auto-correction, or swipe typing (yet).
- The custom keyboard view is not fully accessible to TalkBack; keys are not
  exposed as individual accessibility nodes.
- Phonetic Bangla is rule-based, not a full dictionary. Very irregular words can
  come out wrong; add them to `WordDictionary.java`.
- `j` types জ. For য use the dictionary words (jodi, jabe, jibon) or type `z`.
- The APK was compiled, aligned and signature-verified here, but it could not be
  launched on a device from this environment. Anything unexpected should be
  fixed with a quick follow-up build.

## License

Personal project. Do what you like with it.
