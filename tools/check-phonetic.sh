#!/usr/bin/env bash
# Compiles and runs the phonetic, voice, suggestion, learned-word and layout checks.
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
  app/src/main/java/com/likhon/keyboard/LearnedIndex.java \
  app/src/main/java/com/likhon/keyboard/Key.java \
  app/src/main/java/com/likhon/keyboard/Language.java \
  app/src/main/java/com/likhon/keyboard/Languages.java \
  app/src/main/java/com/likhon/keyboard/Layouts.java \
  tools/SuggestionCheck.java \
  tools/LearnedCheck.java \
  tools/LayoutCheck.java
java -cp "$out" com.likhon.keyboard.PhoneticCheck
java -cp "$out" com.likhon.keyboard.VoiceCheck
java -cp "$out" com.likhon.keyboard.SuggestionCheck
java -cp "$out" com.likhon.keyboard.LearnedCheck
java -cp "$out" com.likhon.keyboard.LayoutCheck
