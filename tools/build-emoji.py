#!/usr/bin/env python3
"""Builds the Emoji Studio catalogue shipped in the APK.

Artwork: OpenMoji (https://openmoji.org), CC BY-SA 4.0.
Names and keywords: OpenMoji (English) and Unicode CLDR (Bengali), Unicode
license. Skin-tone variants are not shipped as separate tiles: the base emoji
is what the studio shows, which keeps the list readable and the APK small.

Run from the project root:  python3 tools/build-emoji.py
Requires Pillow only for the optional size report; network is used once to
fetch the OpenMoji release zip and the CLDR Bengali annotations. Existing
files in /tmp/opencode/emoji are reused, so a rebuild is offline-friendly.
"""
import io
import json
import os
import re
import sys
import urllib.request
import xml.etree.ElementTree as ET
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "app", "src", "main", "assets", "emoji")
CACHE = os.path.join("/tmp", "opencode", "emoji")
OPENMOJI_ZIP = os.path.join(CACHE, "openmoji.zip")
OPENMOJI_JSON = os.path.join(CACHE, "openmoji.json")
BN_XML = os.path.join(CACHE, "bn.xml")

ZIP_URL = "https://github.com/hfg-gmuend/openmoji/releases/download/17.0.0/openmoji-72x72-color.zip"
JSON_URL = "https://raw.githubusercontent.com/hfg-gmuend/openmoji/master/data/openmoji.json"
BN_URL = "https://raw.githubusercontent.com/unicode-org/cldr/main/common/annotations/bn.xml"

# Display order and localisation keys, matching strings.xml emoji_group_*.
GROUPS = [
    ("smileys-emotion", "emoji_group_smileys"),
    ("people-body", "emoji_group_people"),
    ("animals-nature", "emoji_group_animals"),
    ("food-drink", "emoji_group_food"),
    ("travel-places", "emoji_group_travel"),
    ("activities", "emoji_group_activities"),
    ("objects", "emoji_group_objects"),
    ("symbols", "emoji_group_symbols"),
    ("flags", "emoji_group_flags"),
]


def fetch(url, path):
    if os.path.exists(path) and os.path.getsize(path) > 0:
        return
    os.makedirs(os.path.dirname(path), exist_ok=True)
    print("fetching", url)
    with urllib.request.urlopen(urllib.request.Request(
            url, headers={"User-Agent": "likhon-emoji-build"}), timeout=120) as r:
        data = r.read()
    with open(path, "wb") as f:
        f.write(data)


def norm(cp):
    """Strips variation selectors so CLDR and OpenMoji agree."""
    return cp.replace("\ufe0f", "").replace("\ufe0e", "")


def clean(text):
    if not text:
        return ""
    text = re.sub(r"[\t\r\n]+", " ", text)
    return re.sub(r"  +", " ", text).strip()


def build_bangla():
    root = ET.parse(BN_XML).getroot()
    names, tags = {}, {}
    for node in root.iter():
        if not node.tag.endswith("annotation"):
            continue
        cp = node.get("cp")
        if not cp:
            continue
        value = clean(node.text)
        key = norm(cp)
        if node.get("type") == "tts":
            names[key] = value
        elif node.get("type") is None:
            tags[key] = value
    return names, tags


def main():
    fetch(ZIP_URL, OPENMOJI_ZIP)
    fetch(JSON_URL, OPENMOJI_JSON)
    fetch(BN_URL, BN_XML)

    with open(OPENMOJI_JSON, encoding="utf-8") as f:
        entries = json.load(f)

    bn_names, bn_tags = build_bangla()
    zf = zipfile.ZipFile(OPENMOJI_ZIP)
    available = set(zf.namelist())
    os.makedirs(OUT, exist_ok=True)

    by_group = {slug: [] for slug, _ in GROUPS}
    seen = set()
    kept = 0
    missing = 0
    for entry in entries:
        slug = entry.get("group", "")
        if slug not in by_group:
            continue  # components and OpenMoji-only extras
        if entry.get("skintone"):
            continue  # the yellow base is the canonical tile
        hexcode = entry.get("hexcode", "").upper()
        emoji = entry.get("emoji", "")
        if not hexcode or not emoji or hexcode in seen:
            continue
        seen.add(hexcode)
        key = norm(emoji)
        name = clean(entry.get("annotation"))
        if not name:
            continue
        en_tags = clean(entry.get("tags"))
        bn_name = clean(bn_names.get(key, ""))
        bn_words = clean(bn_tags.get(key, ""))
        image = hexcode + ".png"
        has_image = "1" if image in available else "0"
        if has_image == "0":
            missing += 1
        else:
            with zf.open(image) as src, open(os.path.join(OUT, image), "wb") as dst:
                dst.write(src.read())
        by_group[slug].append({
            "hex": hexcode,
            "emoji": emoji,
            "image": has_image,
            "name": name,
            "tags": en_tags,
            "bn_name": bn_name,
            "bn_tags": bn_words,
            "order": int(entry.get("order") or 0),
        })
        kept += 1

    lines = []
    for slug, _ in GROUPS:
        items = sorted(by_group[slug], key=lambda item: (item["order"], item["name"]))
        for item in items:
            lines.append("\t".join([
                item["hex"], item["emoji"], slug, item["image"],
                item["name"], item["tags"], item["bn_name"], item["bn_tags"],
            ]))
    catalog = os.path.join(OUT, "catalog.tsv")
    with open(catalog, "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(lines))
        f.write("\n")

    with open(os.path.join(OUT, "LICENSE.txt"), "w", encoding="utf-8") as f:
        f.write(OPENMOJI_LICENSE)

    total = os.path.getsize(catalog) + sum(
        os.path.getsize(os.path.join(OUT, name))
        for name in os.listdir(OUT) if name.endswith(".png"))
    print(f"{kept} emoji, {missing} without artwork (system font fallback)")
    print(f"catalogue {os.path.getsize(catalog) / 1024:.0f} KB, "
          f"total {total / (1024 * 1024):.1f} MB")


OPENMOJI_LICENSE = """OpenMoji emoji artwork
https://openmoji.org

The emoji images in this folder are from OpenMoji, the open-source emoji and
icon project, version 17.0.0.

  Copyright 2024 OpenMoji (https://openmoji.org)
  Licensed under CC BY-SA 4.0 (https://creativecommons.org/licenses/by-sa/4.0/)

The images are redistributed unmodified. The Bengali names and keywords come
from the Unicode CLDR project and are used under the Unicode License v3. The
app itself is not a derivative work of the artwork; only these image files are
covered by CC BY-SA 4.0.
"""


if __name__ == "__main__":
    main()
