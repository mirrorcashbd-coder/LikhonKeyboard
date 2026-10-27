# Optional libraries

Any `*.jar` in this folder is dexed into the APK by
`tools/build-apk-arm64.sh`. Nothing is here by default: Likhon is built only
against the Android framework (org.json, HttpURLConnection, VectorDrawable,
AudioRecord, SpeechRecognizer), which keeps the APK small and the build fast on
a phone.

## Adding a library

1. Drop a plain Java/Android `.jar` (classes only, no resources) here.
2. Run `bash tools/build-apk-arm64.sh`; the script prints every jar it compiles.

## Why not AndroidX, Material Components or an AAR

Those ship resources and need `aapt2` + resource merging, which the ARM64
`aapt` tool-chain used here cannot run. The Gradle build in the project root
can use them on a normal computer, but they would multiply the APK size and
replace code that already works (vector icons, ripples, cards). Everything the
keyboard needs is in the framework, so none are required.
