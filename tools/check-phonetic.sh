#!/usr/bin/env bash
# Compiles and runs the phonetic engine check. Needs a JDK (17+) only.
set -euo pipefail
cd "$(dirname "$0")/.."
out="$(mktemp -d)"
trap 'rm -rf "$out"' EXIT
javac -d "$out" \
  app/src/main/java/com/likhon/keyboard/Phonetic.java \
  app/src/main/java/com/likhon/keyboard/WordDictionary.java \
  tools/PhoneticCheck.java
java -cp "$out" com.likhon.keyboard.PhoneticCheck
