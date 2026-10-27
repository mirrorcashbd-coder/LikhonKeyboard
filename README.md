# লিখন কীবোর্ড · Likhon Keyboard

A small, fast, **offline** Android keyboard with an English QWERTY layout and
phonetic Bangla typing. Written in plain Java against the Android framework:
no AndroidX, no third-party libraries, no ads, and nothing of its own ever goes
online (the `INTERNET` permission is declared only for speech services that run
inside the calling app).

**APK:** `build/manual/LikhonKeyboard-1.10.apk` (also copied to
`/sdcard/Download/LikhonKeyboard-1.10.apk`)

---

## Install

1. Open `LikhonKeyboard-1.10.apk` in the Files app and allow "install unknown apps"
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

The keyboard stays exactly where it is and a strip appears above the keys:
microphone, language chip, level bar and **Stop**. Words appear as you speak,
like Gboard's Rambler. There is deliberately no separate speech screen: every
step happens inside the keyboard.

The keyboard never abandons a working microphone because of silence. The other
Bangla model (bn-IN) is only tried after the recogniser has actually caught
speech and still missed it, and Android's on-device recogniser only after real
failures. If a service refuses with CLIENT/BUSY the next engine on the phone is
tried, and an engine that never reports a voice or even a sound level (a stub
some phones ship as the default) is skipped after six completely silent turns. Every
voice event (start, ready, speech started, error name, result, insert, which
engine) is written to an in-memory **voice log** at the bottom of the voice
settings, with a copy button, because there is no logcat on the phone.

Settings → Voice also lists **every recognition engine on the phone** and lets
one be pinned instead of the default, and it says so (with an install button)
when the Google app is missing — the recogniser Gboard itself uses.

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

## Editing toolbar

A thin row above the suggestions holds the editing tools:

- **↩ / ↪ Undo / redo.** Snapshots of the field are taken before every edit;
  fast typing is merged, so undo works a word at a time. Apps that do not hand
  their text to input methods (some browsers and WebViews) simply leave the
  buttons dim.
- **📋 Clipboard history.** Every text copied while the keyboard is on screen is
  stored, newest first, in `files/clipboard.txt` inside the app's private
  storage. Nothing is ever removed automatically: the list survives restarts
  and grows until a single clip or the whole list is deleted from the panel. Tap
  a clip to insert it, `✕` to delete that one. Password fields are never
  captured.
- **📄 Copy / ⬇ Paste.** Copy uses the selection, or the whole field when
  nothing is selected; paste inserts the system clipboard, falling back to the
  newest saved clip.
- **🌐 Translate.** Translates the selection, the word, or a short field:
  Bangla → English when the text is Bangla, English → Bangla otherwise. The
  result can be inserted, used to replace the source, or copied.
- **⚙ Settings.**

The clipboard panel and the translator share one strip under the suggestions,
so the keyboard never leaves the screen for them either.

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

On phones that refuse to give an input method any audio, even the system
recogniser stays silent, so 1.5 also offers the phone's own voice screen — the
same speech screen other apps use — as the default path.

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

Output: `build/manual/LikhonKeyboard-1.10.apk` (signed with the debug key in
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

## Voice typing

Voice has three modes, chosen in Settings → Voice → **Voice method**:

- **Automatic** (default). The keyboard asks the phone's own recogniser first,
  inside the keyboard, and only falls back to the phone's speech screen when the
  phone stays silent for seven seconds or the recogniser refuses the keyboard.
- **Inside the keyboard only.** Nothing else ever opens; on a phone whose
  recognisers ignore third-party callers this mode simply stays silent.
- **Google voice screen.** Always use the phone's speech screen (the one Gboard
  opens). It recognises reliably, but Android hides the keyboard while a speech
  activity is in front; the keyboard returns with the text as soon as it closes.

The fallback matters on phones like the vivo that prompted it: their
`CopilotRecognitionService` answers `CLIENT` (refuses) and the Google app's
`GoogleRecognitionService` answers `TOO_MANY_REQUESTS` to anything but its own
screen. The screen talks to Google inside the Google app, so it works; the
keyboard cannot keep itself visible under an activity started by another app —
that is the platform's rule, not a choice here.

## Settings

Everything lives in one settings screen, grouped into cards.

**Appearance** — theme (system / light / dark), accent colour (6 swatches), key
shape (square / rounded / pill), key label size (small / normal / large), key
borders, and keyboard height (drag the grip above the keys, or pick compact /
normal / tall).

**Typing** — vibration, key sound, auto-capitalise, Bangla hints, key preview,
number row, double-space period, suggestion bar on/off, how many suggestions
(4 / 6 / 8), incognito mode, learned words (count and forget-all), and the
height reset.

**Text shortcuts** — type a short word, press space, get the full text: emails,
addresses, greetings. Add and delete them from the settings card.

**Clipboard** — unlimited history, pinning with a pinned-only filter, one-by-one
delete and a guarded delete-all. Stored in the app's private storage; password fields are
never captured.

**Voice** — language, method (automatic / inline / Google screen), recognition
engine, Banglish output, continuous dictation, spoken-punctuation cleanup,
trailing space, mic test, speech test and the in-memory voice log.

**Editing** — undo, redo, copy, paste, clipboard, translate, more tools (select
all, cut, clear field, UPPER / lower / Title, incognito) and settings, all in
the toolbar above the suggestions.

**Privacy** — nothing leaves the phone except the text you explicitly
translate; incognito stops learning and clipboard capture; reset-all returns
every setting to its default without touching the clipboard.

## Design

The keyboard itself is glass too: every key is a translucent cap with a top
sheen, a specular rim, a soft drop shadow and a live press animation — the key
springs down under the finger, a wave of accent light spreads out from the
touch point, and a slow sheen sweeps across the whole keyboard. Behind
everything sits the same four-blob colour mesh, so the toolbar, the suggestion
strip, the panels and the keys all read as clear glass over colour.

Every settings screen is built from **liquid glass**: translucent panels with a
diagonal specular rim, a top sheen, a hairline bright edge and a soft elevation
shadow, floating over a backdrop made of four huge, smooth colour blobs. The
blobs are already soft, so whatever shows through a panel reads as frosted
glass without a blur pass.

The screens are separate, each with its own floating glass top bar, back button,
slide transition and back stack:

| Screen | What lives there |
| --- | --- |
| Home | keyboard status, six glass quick tiles, guide and system links |
| Appearance | glass theme swatches, live preview, accent beads, key shape, size, spacing |
| Typing | feedback, suggestions, number row, keyboard height, privacy |
| Languages | writing mode and all 39 languages |
| Voice | microphone, recognition engine, live tests and the log |
| Tools | shortcuts, clipboard, learned words |
| Backup | export, import and reset |
| About | version, libraries and the guide |

Buttons, segmented controls, chips, fields and switches are all glass too: a
raised pill marks the selected segment, the switch has a floating white bead
with its own shadow, and every control has a bright rim that catches the light
from the top-left. All of it is derived from the palette, so the six designed
skins (Aurora, Midnight, Paper, Glacier, Neon, Graphite), the plain light/dark
pair and all accent colours work on every screen.

## Languages

39 keyboard languages ship in the APK, all built from the same code-driven
layout engine:

- **Bangla** — phonetic (avro-style: `ami` → আমি) and a direct script layout
  with the full alphabet, all matras, ৎ ং ঃ ঁ ্ ড় ঢ় য় and ৳.
- **Script keyboards** — Hindi, Arabic, Urdu, Persian, Russian, Ukrainian,
  Greek, Hebrew, Thai, Tamil, Telugu, Kannada, Malayalam, Gujarati, Punjabi,
  Odia, Sinhala. Each has its own three rows plus a shifted row for matras,
  signs and extra letters.
- **Latin keyboards** — English, Spanish, French (AZERTY), German (QWERTZ),
  Portuguese, Italian, Dutch, Turkish, Polish, Czech, Romanian, Swedish,
  Norwegian, Danish, Finnish, Vietnamese, Indonesian, Malay, Filipino,
  Kiswahili, each with its own accented letters.

Enable any number of them in **Settings → Languages**; the globe key on the
keyboard cycles through the enabled ones and shows the language name. Chinese,
Japanese and Korean *character* input needs a dictionary engine (pinyin,
romaji, hangul assembly) and is not claimed: those languages need a real IME
engine, not a key map.

## Libraries

Third-party code is installed from Maven Central with `tools/add-lib.sh` and
dexed straight into the APK by both build paths:

| Library | Version | Used for |
| --- | --- | --- |
| OkHttp | 3.14.9 | translation requests: timeouts, retries, one HTTP client |
| Okio | 1.17.5 | OkHttp's I/O layer |
| Gson | 2.10.1 | backup and restore of settings, shortcuts and clipboard (JSON) |

```bash
bash tools/add-lib.sh com.squareup.okhttp3:okhttp:3.14.9
```

A plain JAR is copied as is; an AAR is unpacked and its `classes.jar` is kept.
AAR **resources** need the aapt2 pipeline (aapt2 2.19 runs on this phone), so a
specific resource-carrying library can be wired in on request. AndroidX and
Material Components are deliberately not used: they would add several megabytes
and a theme rewrite for widgets this keyboard already draws itself, and the
whole settings/backup stack would still depend on Google's support libraries
for very little gain.

## Toolbar icons## Toolbar icons

The toolbar uses Material Design line icons as vector drawables
(`res/drawable/ic_undo.xml` and friends, Apache 2.0) instead of emoji, tinted
with the current theme. Every tap plays a short squash-and-settle animation and
undo/redo pivot to show the way the history moved.

## Changes

**1.18**

- Keyboard rebuilt as clear glass: translucent key caps with a top sheen, a
  specular rim and a real drop shadow, for letters, specials, space and enter.
- Live press animation: the key springs down, a wave of accent light spreads
  from the finger inside the key, and a sheen sweeps across the keyboard.
- The keyboard background is the same four-blob colour mesh; toolbar,
  suggestion strip, grip and panels are transparent or translucent glass.
- Settings glass made clearer so the colour mesh shows through.

**1.17**

- Liquid glass redesign across every settings screen: translucent panels,
  diagonal specular rims, top sheen, bright hairline edges and soft shadows.
- Floating glass top bar with a glass back button; glass tiles, chips,
  segmented controls, fields and buttons.
- Four-blob colour mesh backdrop behind the glass, derived from the accent.
- Theme picker rebuilt as glass cards with gradient swatches.
- Switch redrawn with a floating white bead and a glass track.

**1.16**

- Settings split into eight separate screens with a real back stack and slide
  transitions: Home, Appearance, Typing, Languages, Voice, Tools, Backup and
  About.
- New Material 3 component set drawn in code: top-lit surfaces, hairline
  outlines, elevation shadows, large top app bars, tonal and filled buttons,
  segmented controls, list rows and quick tiles.
- Hand-drawn switch with a floating thumb, spring animation and pressed halo.
- Theme picker redesigned as gradient swatches; accent picker as raised colour
  dots; live keyboard preview kept.
- Home grid of six quick tiles plus guide/keyboard/about rows.

**1.15**

- Six designed skins: Aurora, Midnight, Paper, Glacier, Neon and Graphite,
  with gradients, soft key shadows and accent glow.
- Live theme preview in the appearance card, painted with the keyboard's own
  drawing code.
- Gradient hero header in the settings, gradient keyboard background/toolbar,
  accent-highlighted first suggestion.
- New key-spacing setting (compact / normal / airy) next to shape and label
  size.

**1.14**

- Real third-party libraries installed from Maven Central and dexed into the
  APK: OkHttp 3.14.9 + Okio 1.17.5 (translation) and Gson 2.10.1 (backup).
- New backup and restore: every setting, text shortcut and clipboard entry to
  one JSON file through the system file picker, and back on another phone.
- `tools/add-lib.sh group:artifact:version` installs any Maven JAR or AAR
  classes; the About card lists what is inside the build.

**1.13**

- 39 keyboard languages: phonetic and direct-script Bangla, 17 more script
  keyboards (Hindi, Arabic, Urdu, Persian, Russian, Ukrainian, Greek, Hebrew,
  Thai, Tamil, Telugu, Kannada, Malayalam, Gujarati, Punjabi, Odia, Sinhala)
  and 20 Latin keyboards with their own accented letters (AZERTY, QWERTZ and
  Turkish layouts included).
- Language manager in the settings; the globe key cycles the enabled
  languages and shows the name; suggestions stay on where a dictionary exists.
- Shift switches to the shifted script row (matras, signs) and to capitals on
  Latin keyboards.

**1.12**

- 20+ new settings and tools: accent colours, key shape, key label size, key
  borders, keyboard height presets, suggestion bar on/off, suggestion count,
  incognito mode, learned-words manager, text shortcuts, voice trailing space,
  reset-all, plus a "more" panel with select all, cut, clear field and
  UPPER/lower/Title conversion.
- Clipboard history gained pinning (pinned copies stay on top) and a filter
  for pinned entries.
- `libs/` support in the ARM64 build script for plain-JAR libraries.

**1.11**

- Voice typing got the phone's own speech screen back as a fallback, because
  several phones refuse inline recognition to every app but their own. Default
  is automatic: inline first, then the Google voice screen if the phone stays
  silent. Settings → Voice → Voice method switches between automatic, inline
  only and screen only.
- The keyboard toolbar now uses Material vector icons (undo, redo, clipboard,
  copy, paste, translate, settings) with a tap animation, instead of emoji, and
  a hairline separates it from the suggestions.
- The speech-screen hand-off is a one-shot bridge, so text can never be inserted
  twice.

**1.10**

- New editing toolbar above the suggestions: undo, redo, copy, paste, clipboard
  history, translate and settings.
- **Clipboard history** stores every copy in a private file, newest first, and
  never deletes anything on its own; entries can be removed one by one or all at
  once. It is filled while the keyboard is visible, skips password fields, and
  survives restarts and updates.
- **Undo/redo** by text snapshots (a word per step), working in any editor that
  exposes its text through `ExtractedText`.
- **Translate** Bangla ↔ English with the public Google Translate endpoint,
  inserting, replacing or copying the result. Only the chosen text is sent, and
  only when the button is pressed.
- Settings gained a clipboard card (count and delete-all) and typing options:
  key preview, number row, double-space period, and a height reset.

**1.9**

- Voice no longer gives up on a good engine because nobody spoke yet: the
  bn-IN fallback and the on-device switch now require the recogniser to have
  actually heard speech, so a quiet first few seconds can no longer throw a
  working Bangla session away.
- An engine that answers with CLIENT/BUSY on a Bangla tag gets one retry with
  the other Bangla tag (bn-BD ↔ bn-IN) before the next engine is used, because
  several phone services report "language not supported" as CLIENT.
- Engine order is the phone's default first, then the Google app's recogniser
  (added by component even when package visibility hides it), then the rest.
  The old build could switch to a stub and stay there for ever; an engine that
  never reports a voice or a sound level is now left after three silent turns.
- Settings → Voice has a **Recognition engine** chooser (Automatic, or one of
  the services actually installed) and warns with an install button when the
  Google app is missing.
- If no engine answers at all, the strip now stops and shows the error instead
  of restarting the microphone for ever.

**1.8**

- The voice wrapper now lists every recognition service installed on the phone
  (the default one plus the others it can see) and moves to the next engine
  when one answers with CLIENT/BUSY instead of retrying it forever. Phones whose
  default service is a stub can therefore still reach a working engine.
- Android's on-device recogniser is tried after two engine errors, not only
  after four silent phrases, so a service that refuses an input method is
  detected quickly.
- Each engine, its package and its enabled state are written to the voice log.

**1.7**

- The phone's own speech screen is gone completely: no tap or long-press can
  open it, and the settings switch that enabled it was removed. Voice typing
  happens only inside the keyboard strip.
- Settings gained a **voice log** (last 60 events with timestamps, copy button)
  so the speech service's own answer can be read on the phone.
- The voice tips now point at the log instead of at the removed screen.

**1.6**

- Voice typing is **inline again by default**: the keyboard stays visible and a
  strip above the keys shows the level and the words as they arrive. The phone's
  speech screen is now only on long-press of the mic key (or the setting).
- If Bangla (bn-BD) hears nothing twice in automatic mode, the strip switches to
  Bangla (India, bn-IN) and says so, instead of retrying the same silent model.
- Failures are no longer silent: the strip shows “nothing was heard (NO_MATCH)”
  and a fatal error leaves the strip on screen with its name, so the reason can
  be read without a computer.
- The Banglish choice is visible in the strip: the chip cycles বাংলা → Banglish
  → English, and the same switch is in settings.

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
- Speech recognition still depends on the phone's service accepting an input
  method as its caller. If no installed engine accepts it, the strip says so and
  the voice log shows the error name; there is no second screen to fall back to
  by design.
- Some phones (vivo/Funtouch, for example) ship only their own assistant as a
  recognition service and answer CLIENT to other apps. The Google app provides
  the recogniser Gboard uses; with it installed the keyboard picks it up
  automatically, without it the phone's engine decides.
- Voice punctuation only recognises the listed words, and `period`/`colon`
  count as punctuation only at the end of a phrase (they are common nouns).
- The custom keyboard view is not fully accessible to TalkBack; keys are not
  exposed as individual accessibility nodes.
- Undo needs the editor to return its text to input methods. Where it does not,
  the undo button stays dim instead of half-working.
- Clipboard history only captures what is copied while the keyboard is open
  (Android hides the clipboard from background apps otherwise).
- Translate needs a connection and uses the public Google endpoint; the text you
  translate leaves the phone, nothing else does.
- Phonetic Bangla is rule-based, not a full dictionary. Very irregular words can
  come out wrong; add them to `WordDictionary.java`.
- `j` types জ. For য use the dictionary words (jodi, jabe, jibon) or type `z`.
- The APK was compiled, aligned and signature-verified here, but it could not be
  launched on a device from this environment. Anything unexpected should be
  fixed with a quick follow-up build.

## License

Personal project. Do what you like with it.
