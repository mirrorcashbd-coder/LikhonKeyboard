#!/usr/bin/env bash
# Compiles and runs the phonetic, voice text and suggestion checks.
# Needs a JDK (17+) only.
set -euo pipefail
cd "$(dirname "$0")/.."
out="$(mktemp -d)"
trap 'rm -rf "$out"' EXIT
javac -encoding UTF-8 -d "$out" \
  app/src/main/java/com/likhon/keyboard/Phonetic.java \
  app/src/main/java/com/likhon/keyboard/WordDictionary.java \
  app/src/main/java/com/likhon/keyboard/Romanize.java \
  app/src/main/java/com/likhon/keyboard/VoiceText.java \
  tools/PhoneticCheck.java \
  tools/VoiceCheck.java \
  tools/SuggestionCheck.java
java -cp "$out" com.likhon.keyboard.PhoneticCheck
java -cp "$out" com.likhon.keyboard.VoiceCheck
java -cp "$out" com.likhon.keyboard.SuggestionCheck
