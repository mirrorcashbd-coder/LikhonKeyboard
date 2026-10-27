#!/usr/bin/env bash
# Rebuilds assets/words_bn_index.txt from tools/data/words_bn.txt.
# Only needed when the Bangla word list or the romanisation rules change.
set -euo pipefail
cd "$(dirname "$0")/.."
out="$(mktemp -d)"
trap 'rm -rf "$out"' EXIT
javac -encoding UTF-8 -d "$out" \
  app/src/main/java/com/likhon/keyboard/Romanize.java \
  app/src/main/java/com/likhon/keyboard/WordDictionary.java \
  tools/SuggestionIndex.java
java -cp "$out" com.likhon.keyboard.SuggestionIndex
