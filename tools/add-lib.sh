#!/usr/bin/env bash
#
# Installs a library from Maven Central into libs/, where the ARM64 build
# script picks it up automatically.
#
#   tools/add-lib.sh com.squareup.okhttp3:okhttp:3.14.9
#   tools/add-lib.sh com.google.code.gson:gson:2.10.1
#
# Plain JARs are copied as they are. AARs (the usual Android library format)
# are unpacked and their classes.jar is kept; an AAR that ships resources is
# reported, because resources need the aapt2 pipeline (see libs/README.md).
set -euo pipefail
cd "$(dirname "$0")/.."

coord="${1:-}"
if [ -z "$coord" ]; then
    echo "usage: tools/add-lib.sh group:artifact:version" >&2
    exit 1
fi

IFS=: read -r group artifact version <<< "$coord"
if [ -z "${group:-}" ] || [ -z "${artifact:-}" ] || [ -z "${version:-}" ]; then
    echo "usage: tools/add-lib.sh group:artifact:version" >&2
    exit 1
fi

base="https://repo1.maven.org/maven2/${group//./\/}/$artifact/$version"
mkdir -p libs
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

if curl -fsSL --retry 3 -o "libs/$artifact-$version.jar" "$base/$artifact-$version.jar"; then
    echo "installed libs/$artifact-$version.jar"
    exit 0
fi

if curl -fsSL --retry 3 -o "$tmp/lib.aar" "$base/$artifact-$version.aar"; then
    unzip -q "$tmp/lib.aar" -d "$tmp/aar"
    if [ ! -f "$tmp/aar/classes.jar" ]; then
        echo "$artifact is an AAR with no classes.jar" >&2
        exit 1
    fi
    cp "$tmp/aar/classes.jar" "libs/$artifact-$version.jar"
    echo "installed libs/$artifact-$version.jar (classes from AAR)"
    if [ -d "$tmp/aar/res" ] && [ -n "$(ls -A "$tmp/aar/res")" ]; then
        echo "note: $artifact also ships resources in src/main/res;"
        echo "      those need the aapt2 resource pipeline (libs/README.md)."
    fi
    exit 0
fi

echo "could not download $coord from Maven Central" >&2
exit 1
