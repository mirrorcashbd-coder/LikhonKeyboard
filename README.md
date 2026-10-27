# লিখন কীবোর্ড · Likhon Keyboard

A small, fast, **offline** Android keyboard with an English QWERTY layout and
phonetic Bangla typing. Written in plain Java against the Android framework:
no AndroidX, no third-party libraries, no ads, and nothing of its own ever goes
online (the `INTERNET` permission is declared only for speech services that run
inside the calling app).

**APK:** `build/manual/LikhonKeyboard-1.5.apk` (also copied to
`/sdcard/Download/LikhonKeyboard-1.5.apk`)

---

## Install

1. Open `LikhonKeyboard-1.5.apk` in the Files app and allow "install unknown apps"
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
- **Multi-touch**: every finger is tracked separately, so two thumbs can type
  at the same time (hold shift with one hand, type with the other) and fast
  alternating taps are not dropped.
- **Forgiving keys**: taps that land in the narrow gap between two keys go to
  the nearest key instead of being ignored.
- **Auto-capitalise** for English sentences.
- **Key preview** pop-up, **vibration** and **click sound** options.
- **Themes**: follow system, light, or dark.
- **Smart delete**: in Bangla it removes the romanised letter and redraws the
  word (one tap does not leave a dangling া or ্).
- **Enter key** shows Search / Send / Go / Done / Next when the app asks for it.
- **Numeric keyboard** for number and phone fields.
- **Bangla digits**, `।`, `ঁ`, `্` and `৳` in Bangla mode.
- **Voice typing** in Bangla, Banglish and English (see below).
- **Word suggestions, always on**: a scrollable strip above the keys with up
  to 18 completions, visible even before the first letter. English uses the
  10,000 most common words, Bangla uses 18,000 words matched against what you
  type in roman letters, so `bhal` offers ভালো, ভালোবাসা, ভালোবাসি…
- **Learns your words**: every word you finish typing, pick or dictate is
  remembered locally and comes back in the strip, even when it is not in the
  bundled lists.
- **Resizable keyboard**: drag the grip above the keys up or down to make the
  keyboard taller or shorter; long-press the grip to reset it.
- English **and** Bangla UI (follows the phone language).
- Typing and suggestions are fully offline. The only runtime permission is the
  microphone, and it is used only while voice typing is running.

## Voice typing

Tap the **microphone** key (bottom row of the letters and symbols pages) once.

There are two ways to dictate, switched in settings:

- **Phone voice screen** (default): the speech screen your phone already has
  opens — the same one other apps use. Speak, then the sentence is inserted
  when you return to the app. The recogniser records from a foreground screen,
  which is the path phones are most likely to allow.
- **Inline strip**: turn the switch off. A strip appears above the keyboard
  with the microphone, the language chip, a level bar and a **Stop** button,
  and words appear as you speak.

Settings also has a **Microphone check** (records 1.5 s and reports the level)
and a **Speech service check** (runs one recognition and prints the result or
the service's error name), so a silent microphone can be told apart from a
speech service that refuses to answer.

In inline mode:

- Each tap on the language chip cycles **বাংলা → Banglish → English**.
  Bangla uses `bn-BD`, English uses `en-US`; Banglish listens in Bangla and then
  writes what you said in roman letters (`আমি ভালো আছি` → `ami bhalo achhi`).
- **Continuous** mode keeps the microphone open after each phrase and simply
  carries on, like Gboard's Rambler dictation. Turn it off in settings if your
  phone's speech service prefers one phrase per tap.
- **Clean up** removes fillers and stutters (`উম`, `মানে`, `আঃ`, repeated words)
  and tidies punctuation.
- **Spoken punctuation** like Rambler: say `comma`, `period` / `full stop`,
  `question mark`, `exclamation mark`, `colon`, `semicolon` or `new line`
  (Bangla: `কমা`, `দাঁড়ি`, `প্রশ্নচিহ্ন`, `বিস্ময়চিহ্ন`, `কোলন`,
  `সেমিকোলন`, `নতুন লাইন`) and the mark is inserted instead of the word.
- **Voice editing**: saying `delete that` (or `মুছে ফেলো`) deletes the word
  before the cursor.
- A small level bar in the voice strip shows how loudly you are speaking.
- Partial results appear in the composing region and are replaced as the
  recogniser refines them; nothing is committed twice.

Speech recognition is done by the speech service already on the phone (usually
Google) — the same service Gboard uses for its Rambler voice typing. Bangla may
need the Bangla language pack downloaded in that app, and some phones only do
recognition online. The keyboard itself never sends anything anywhere: the
`INTERNET` permission exists only because some speech services run in the
calling app and need it to reach their own servers.

## Word suggestions

The strip above the keys is always there on the letters and symbols pages.

- **Nothing typed yet**: your recently used words come first, then the most
  common words of the current language, so the strip is never empty.
- **In English**, the 10,000 most frequent words are searched by prefix, so
  `hel` gives hello, help, held, helpful… in frequency order.
- **In Bangla**, suggestions follow the *roman* letters you type, not the
  converted text: `kemon` → কেমন, `bhal` → ভালো, ভালোবাসা… This list is built
  from a public-domain word list and romanised once at build time
  (`tools/build-suggestions.sh`), so there is no work to do while typing.
- **New words are learned**: when you finish a word (space, enter, punctuation,
  a suggestion or dictation), it is stored in
  `files/learned_words.txt` and appears at the front of the strip from then on.
  Bangla words remember the roman letters that produced them, so typing `amar`
  suggests আপনার নিজের লেখা `আমার` again.
- Tap a suggestion to replace the word being typed and add a space.
- Long-press a suggestion to forget a learned word.
- Suggestions are hidden for password and number fields, and on the emoji page.

## Keyboard size

The thin grip between the suggestion strip and the keys changes the keyboard
height:

- drag it up to make the keys taller, down to make them shorter;
- the text on the keys scales with them;
- long-press the grip to go back to the default size.

The chosen size is remembered across restarts (`Prefs.KEY_HEIGHT`), from 26 to
92 dp per row.

## PixelBoard

[PixelBoard](https://github.com/Akshayykadam/PixelBoard) is a **GPL-3.0 mod of
Gboard**: it is a set of Morphe/ReVanced patches that rewrite Google's Gboard
APK, not a keyboard implementation. It cannot be merged into Likhon (different
app, GPL-3.0, and no source for the keyboard itself). Its ideas that fit here —
always-on suggestions, learning what you type, and a resizable keyboard — were
implemented from scratch in this project instead.

The same is true of its voice typing: PixelBoard has no recogniser code. The
Rambler patch changes methods in Gboard's own
`VoiceSettingsFragment` (`aB`, `ac`, `f`, `aD`) so Gboard's hidden Rambler
selector becomes available; the microphone, streaming, partials and punctuation
all live inside Gboard. Because Android's `SpeechRecognizer` talks to the same
speech service, Likhon can offer the same kind of dictation, and this version
matches Rambler's behaviour (continuous session, live partials, filler cleanup,
spoken punctuation, delete command, level meter) without copying GPL code.

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

Output: `build/manual/LikhonKeyboard-1.5.apk` (signed with the debug key in
`keystore/likhon-debug.jks`, generated on first run — keep that file if you want
future builds to install over the current one).

Compile-time checks:

```bash
tools/check-phonetic.sh       # phonetic engine + voice text tests, plain JDK
tools/build-suggestions.sh    # rebuild the Bangla suggestion index
```

The APK embeds two word lists in `app/src/main/assets/`:
`words_en.txt` (frequency order) and `words_bn_index.txt`
(roman, Bangla, rank — generated, do not edit by hand). The sources live in
`tools/data/`.

## Project layout

```
app/src/main/java/com/likhon/keyboard/
  LikhonIme.java        InputMethodService: state, composing, commit, prefs
  KeyboardView.java     custom Canvas keyboard: drawing, touch, repeat delete
  Layouts.java          letters / symbols / more / emoji / numeric layouts
  Key.java              immutable key model
  Phonetic.java         Roman → Bangla transliteration rules
  WordDictionary.java   curated exceptions for irregular spellings
  Romanize.java         Bangla → Banglish, used by voice and by suggestions
  VoiceInput.java       SpeechRecognizer wrapper, continuous dictation
  VoiceText.java        filler/stutter cleanup for dictated text
  Suggestions.java      prefix search over the two bundled word lists
  LearnedWords.java     persistence for words the person actually types
  LearnedIndex.java     the pure add/suggest/remove logic behind it
  Palette.java          light/dark colours
  Prefs.java            typed SharedPreferences
  SettingsActivity.java setup + settings screen (code-built UI)
  Ui.java               small view/drawable helpers
app/src/main/assets/    English + Bangla word lists for suggestions
app/src/main/res/       manifest resources, method.xml, strings (en + bn), icons
tools/                  icon generator, phonetic/voice checks, suggestion index,
                        ARM64 APK build
```

`KeyboardView` paints every key itself instead of inflating ~40 Views. That keeps
the keyboard light, lets a finger slide between keys, and makes the pop-up
preview and repeat-delete cheap. Touch handling keeps one small state object per
finger (key, long-press timer, delete-repeat timer), which is what allows
simultaneous presses.

## Changes

**1.5**

- Voice typing now works even on phones whose speech service ignores an input
  method: the default path opens the phone's own voice screen and inserts the
  sentence afterwards. The inline strip is still there as an option.
- Fixed the Android 11+ package-visibility declaration for speech services
  (`<queries>`), added `INTERNET` for recognisers that run in-process, shortened
  the end-of-phrase wait, and recreate a recogniser that reports itself busy or
  broken instead of retrying it forever.
- The strip now says “nothing was heard — speak again” instead of silently
  restarting, stops after three failed starts with a real error message, and
  points to the voice-screen switch when nothing is recognised.
- Settings gained **Microphone check** and **Speech service check**, which show
  the peak microphone level and the speech service's own answer, so a blocked
  microphone and a refusing recogniser can be told apart without a computer.
- Dictated sentences are learned as suggestible words in one place, including
  the sentences that arrive through the voice screen.

**1.4**

- Voice typing now understands spoken punctuation (`comma`, `দাঁড়ি`,
  `new line`, …), the `delete that` / `মুছে ফেলো` command, and shows a live
  microphone level bar in the voice strip.
- Continuous dictation restarts faster after each finished phrase (140 ms).
- Documented why PixelBoard's voice patch cannot be copied: it only flips
  Gboard's internal Rambler switch, it contains no speech recognition code.

**1.3**

- The suggestion strip is always visible: recent/learned words first, then the
  most common words of the current language, even before the first letter.
- New words are learned automatically when finished (space, enter, punctuation,
  suggestion tap or dictation), kept on disk and offered again later. Bangla
  words remember the roman spelling that produced them; long-press a chip to
  forget one.
- Drag the grip above the keys to resize the keyboard; long-press it to reset.
  The size is remembered.
- `tools/LearnedCheck.java` checks the learning logic on a plain JDK.

**1.2**

- Voice typing with three modes (বাংলা, Banglish, English), a live partial
  result in the composing region, continuous dictation and filler cleanup.
- The microphone key and the voice strip; the only permission is `RECORD_AUDIO`.
- Word suggestions with a scrollable strip above the keyboard: 10,000 English
  words and 18,000 Bangla words matched by the roman letters you type.
- Bundled word lists are loaded lazily, so the keyboard still starts instantly.

**1.1**

- Multi-touch typing: each finger now has its own key, highlight, long-press
  and delete-repeat timer. Two letters can be pressed at the same time, and a
  finger resting on shift no longer blocks the other hand.
- Gaps between keys are no longer dead space: a tap there goes to the nearest
  key, so slightly off-centre taps are not dropped.
- Faster key repeat for delete and a shorter long-press delay.

**1.0** — first release.

## Known limits

- Suggestions are prefix-only: no next-word prediction and no auto-correction.
  Learner words are matched by prefix too, and only within the current language.
- Learned words are stored unencrypted in the app's private files directory;
  they never leave the phone.
- Voice typing depends on the phone's speech service; it does nothing if none
  is installed, and Bangla needs a Bangla language pack. It cannot use Gboard's
  private Rambler implementation directly.
- The phone's voice screen is a separate screen: it hides the keyboard for a
  moment and the text appears when you come back. The inline strip avoids that
  but depends on the speech service accepting an input method as its caller.
- Voice punctuation only recognises the listed words, and `period`/`colon`
  count as punctuation only at the end of a phrase (they are common nouns).
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
