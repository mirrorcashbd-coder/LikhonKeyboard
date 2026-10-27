#!/usr/bin/env python3
"""Builds the sticker library shipped in the APK.

Artwork: Microsoft Fluent Emoji (3D), MIT License.
https://github.com/microsoft/fluentui-emoji

Run from the project root:  python3 tools/build-stickers.py
Requires Pillow and network access (only for regenerating the assets).
"""
import io
import json
import os
import re
import sys
import urllib.parse
import urllib.request
import concurrent.futures

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "app", "src", "main", "assets", "stickers")
SIZE = 192
QUALITY = 88
BASE = "https://raw.githubusercontent.com/microsoft/fluentui-emoji/main/"

# pack id, English name, Bangla name, items
# item: folder, id, English, Bangla, extra search tags
PACKS = [
    ("moods", "Moods", "মুড", [
        ("Grinning face", "grin", "Grinning", "হাসিমুখ", ["smile", "hasi", "হাসি", "খুশি"]),
        ("Face with tears of joy", "joy", "Tears of joy", "আনন্দের কান্না", ["lol", "laugh", "হাসি"]),
        ("Rolling on the floor laughing", "rofl", "ROFL", "হেসে গড়াগড়ি", ["lol", "laugh", "হাসি"]),
        ("Winking face", "wink", "Wink", "চোখ টিপা", ["smile", "হাসি"]),
        ("Smiling face with hearts", "hearts_face", "In love", "ভালোবাসায় হাসি", ["love", "ভালোবাসা"]),
        ("Smiling face with heart-eyes", "heart_eyes", "Heart eyes", "প্রেমময় চোখ", ["love", "ভালোবাসা"]),
        ("Star-struck", "star_struck", "Star struck", "মুগ্ধ", ["wow", "মুগ্ধ"]),
        ("Thinking face", "thinking", "Thinking", "ভাবছি", ["think", "ভাবনা"]),
        ("Smirking face", "smirk", "Smirk", "দুষ্টু হাসি", ["smirk", "হাসি"]),
        ("Sleeping face", "sleeping", "Sleeping", "ঘুমাচ্ছি", ["sleep", "ঘুম"]),
        ("Crying face", "cry", "Crying", "কাঁদছি", ["sad", "কান্না", "দুঃখ"]),
        ("Loudly crying face", "loud_cry", "Sobbing", "ফুঁপিয়ে কান্না", ["sad", "কান্না"]),
        ("Angry face", "angry", "Angry", "রাগ", ["angry", "রাগ"]),
        ("Face with steam from nose", "steam", "Fuming", "ফুঁসছি", ["angry", "রাগ"]),
        ("Exploding head", "exploding", "Mind blown", "মাথা ফাটছে", ["wow", "অবাক"]),
        ("Partying face", "partying", "Party face", "পার্টি মুখ", ["party", "পার্টি"]),
        ("Smiling face with sunglasses", "cool", "Cool", "কুল", ["cool", "কুল"]),
        ("Melting face", "melt", "Melting", "গলে যাচ্ছি", ["hot", "গরম"]),
        ("Pleading face", "pleading", "Please", "অনুরোধ", ["please", "অনুরোধ"]),
        ("Face with medical mask", "mask", "Mask", "মাস্ক", ["sick", "মাস্ক"]),
    ]),
    ("love", "Love", "ভালোবাসা", [
        ("Red heart", "red_heart", "Red heart", "লাল হৃদয়", ["heart", "hridoy", "হৃদয়"]),
        ("Orange heart", "orange_heart", "Orange heart", "কমলা হৃদয়", ["heart", "হৃদয়"]),
        ("Blue heart", "blue_heart", "Blue heart", "নীল হৃদয়", ["heart", "হৃদয়"]),
        ("Purple heart", "purple_heart", "Purple heart", "বেগুনি হৃদয়", ["heart", "হৃদয়"]),
        ("Sparkling heart", "sparkling_heart", "Sparkling heart", "ঝিকিমিকি হৃদয়", ["heart", "হৃদয়"]),
        ("Growing heart", "growing_heart", "Growing heart", "বাড়ন্ত হৃদয়", ["heart", "হৃদয়"]),
        ("Beating heart", "beating_heart", "Beating heart", "ধুকধুক হৃদয়", ["heart", "হৃদয়"]),
        ("Two hearts", "two_hearts", "Two hearts", "দুই হৃদয়", ["heart", "হৃদয়"]),
        ("Heart with arrow", "heart_arrow", "Cupid's heart", "তীরবিদ্ধ হৃদয়", ["love", "ভালোবাসা"]),
        ("Heart with ribbon", "heart_ribbon", "Gift heart", "ফিতার হৃদয়", ["gift", "উপহার"]),
        ("Broken heart", "broken_heart", "Broken heart", "ভাঙা হৃদয়", ["sad", "দুঃখ"]),
        ("Love letter", "love_letter", "Love letter", "প্রেমপত্র", ["letter", "চিঠি"]),
        ("Kiss mark", "kiss", "Kiss", "চুমু", ["kiss", "চুমু"]),
        ("Rose", "rose", "Rose", "গোলাপ", ["flower", "ফুল"]),
        ("Bouquet", "bouquet", "Bouquet", "ফুলের তোড়া", ["flower", "ফুল"]),
        ("Ring", "ring", "Ring", "আংটি", ["wedding", "বিয়ে"]),
        ("Gem stone", "gem", "Diamond", "হীরা", ["diamond", "হীরা"]),
        ("People hugging", "hug", "Hug", "জড়িয়ে ধরা", ["hug", "আলিঙ্গন"]),
    ]),
    ("party", "Party", "পার্টি", [
        ("Party popper", "popper", "Party popper", "পার্টি পপার", ["party", "পার্টি"]),
        ("Confetti ball", "confetti", "Confetti", "কনফেটি", ["party", "পার্টি"]),
        ("Balloon", "balloon", "Balloon", "বেলুন", ["party", "জন্মদিন"]),
        ("Birthday cake", "cake", "Birthday cake", "জন্মদিনের কেক", ["birthday", "জন্মদিন"]),
        ("Shortcake", "shortcake", "Cake", "কেক", ["cake", "মিষ্টি"]),
        ("Wrapped gift", "gift", "Gift", "উপহার", ["gift", "উপহার"]),
        ("Bottle with popping cork", "champagne", "Champagne", "শ্যাম্পেন", ["celebrate", "উৎসব"]),
        ("Clinking glasses", "cheers", "Cheers", "চিয়ার্স", ["celebrate", "উৎসব"]),
        ("Trophy", "trophy", "Trophy", "ট্রফি", ["win", "জয়"]),
        ("1st place medal", "medal", "Gold medal", "স্বর্ণপদক", ["win", "জয়"]),
        ("Crown", "crown", "Crown", "মুকুট", ["king", "রাজা"]),
        ("Fireworks", "fireworks", "Fireworks", "আতশবাজি", ["celebrate", "উৎসব"]),
        ("Sparkler", "sparkler", "Sparkler", "ফুলঝুরি", ["celebrate", "উৎসব"]),
        ("Sparkles", "sparkles", "Sparkles", "ঝিলিক", ["shine", "চমক"]),
        ("Musical notes", "notes", "Music", "সুর", ["music", "গান"]),
        ("Microphone", "mic", "Microphone", "মাইক", ["music", "গান"]),
    ]),
    ("animals", "Animals", "প্রাণী", [
        ("Cat face", "cat", "Cat", "বিড়াল", ["cat", "বিড়াল"]),
        ("Dog face", "dog", "Dog", "কুকুর", ["dog", "কুকুর"]),
        ("Bear", "bear", "Bear", "ভালুক", ["bear", "ভালুক"]),
        ("Panda", "panda", "Panda", "পান্ডা", ["panda", "পান্ডা"]),
        ("Fox", "fox", "Fox", "শিয়াল", ["fox", "শিয়াল"]),
        ("Frog", "frog", "Frog", "ব্যাঙ", ["frog", "ব্যাঙ"]),
        ("Monkey face", "monkey", "Monkey", "বানর", ["monkey", "বানর"]),
        ("Penguin", "penguin", "Penguin", "পেঙ্গুইন", ["penguin", "পাখি"]),
        ("Koala", "koala", "Koala", "কোয়ালা", ["koala"]),
        ("Lion", "lion", "Lion", "সিংহ", ["lion", "সিংহ"]),
        ("Tiger face", "tiger", "Tiger", "বাঘ", ["tiger", "বাঘ"]),
        ("Rabbit face", "rabbit", "Rabbit", "খরগোশ", ["rabbit", "খরগোশ"]),
        ("Unicorn", "unicorn", "Unicorn", "ইউনিকর্ন", ["unicorn"]),
        ("Butterfly", "butterfly", "Butterfly", "প্রজাপতি", ["butterfly", "প্রজাপতি"]),
        ("Honeybee", "bee", "Bee", "মৌমাছি", ["bee", "মৌমাছি"]),
        ("Dolphin", "dolphin", "Dolphin", "ডলফিন", ["dolphin", "ডলফিন"]),
        ("Octopus", "octopus", "Octopus", "অক্টোপাস", ["octopus", "সমুদ্র"]),
        ("Baby chick", "chick", "Chick", "মুরগির ছানা", ["chick", "পাখি"]),
    ]),
    ("food", "Food", "খাবার", [
        ("Pizza", "pizza", "Pizza", "পিজ্জা", ["pizza", "পিজ্জা"]),
        ("Hamburger", "burger", "Burger", "বার্গার", ["burger", "বার্গার"]),
        ("French fries", "fries", "Fries", "ফ্রেঞ্চ ফ্রাই", ["fries", "আলু"]),
        ("Taco", "taco", "Taco", "টাকো", ["taco"]),
        ("Steaming bowl", "ramen", "Ramen", "রামেন", ["ramen", "নুডলস"]),
        ("Sushi", "sushi", "Sushi", "সুশি", ["sushi"]),
        ("Bento box", "bento", "Bento", "বেন্টো", ["bento", "খাবার"]),
        ("Dumpling", "dumpling", "Dumpling", "ডাম্পলিং", ["dumpling", "মোমো"]),
        ("Doughnut", "donut", "Donut", "ডোনাট", ["donut", "মিষ্টি"]),
        ("Cookie", "cookie", "Cookie", "কুকি", ["cookie", "বিস্কুট"]),
        ("Ice cream", "icecream", "Ice cream", "আইসক্রিম", ["icecream", "মিষ্টি"]),
        ("Strawberry", "strawberry", "Strawberry", "স্ট্রবেরি", ["fruit", "ফল"]),
        ("Watermelon", "watermelon", "Watermelon", "তরমুজ", ["fruit", "ফল"]),
        ("Hot beverage", "coffee", "Coffee", "কফি", ["coffee", "চা"]),
        ("Bubble tea", "boba", "Bubble tea", "বাবল টি", ["tea", "চা"]),
        ("Popcorn", "popcorn", "Popcorn", "পপকর্ন", ["popcorn", "সিনেমা"]),
        ("Avocado", "avocado", "Avocado", "অ্যাভোকাডো", ["avocado", "ফল"]),
        ("Honey pot", "honey", "Honey", "মধু", ["honey", "মধু"]),
    ]),
    ("nature", "Nature", "প্রকৃতি", [
        ("Sun", "sun", "Sun", "সূর্য", ["sun", "সূর্য"]),
        ("Crescent moon", "moon", "Moon", "চাঁদ", ["moon", "চাঁদ"]),
        ("Rainbow", "rainbow", "Rainbow", "রংধনু", ["rainbow", "রংধনু"]),
        ("Cloud", "cloud", "Cloud", "মেঘ", ["cloud", "মেঘ"]),
        ("Cloud with rain", "rain", "Rain", "বৃষ্টি", ["rain", "বৃষ্টি"]),
        ("Cloud with lightning", "thunder", "Thunder", "বজ্র", ["storm", "ঝড়"]),
        ("High voltage", "bolt", "Lightning", "বিদ্যুৎ", ["lightning", "বজ্র"]),
        ("Snowflake", "snow", "Snow", "তুষার", ["snow", "শীত"]),
        ("Snowman", "snowman", "Snowman", "তুষারমানব", ["snow", "শীত"]),
        ("Umbrella", "umbrella", "Umbrella", "ছাতা", ["umbrella", "ছাতা"]),
        ("Ringed planet", "planet", "Planet", "গ্রহ", ["space", "মহাকাশ"]),
        ("Comet", "comet", "Comet", "ধূমকেতু", ["space", "মহাকাশ"]),
        ("Tornado", "tornado", "Tornado", "টর্নেডো", ["storm", "ঝড়"]),
        ("Glowing star", "star", "Star", "তারা", ["star", "তারা"]),
    ]),
    ("work", "Work", "কাজ", [
        ("Laptop", "laptop", "Laptop", "ল্যাপটপ", ["computer", "কম্পিউটার"]),
        ("Rocket", "rocket", "Rocket", "রকেট", ["rocket", "রকেট"]),
        ("Light bulb", "idea", "Idea", "বাতি", ["idea", "ধারণা"]),
        ("Check mark button", "check", "Done", "সম্পন্ন", ["done", "ঠিক"]),
        ("Memo", "memo", "Note", "নোট", ["note", "নোট"]),
        ("Pencil", "pencil", "Pencil", "পেন্সিল", ["write", "লেখা"]),
        ("Spiral calendar", "calendar", "Calendar", "ক্যালেন্ডার", ["date", "তারিখ"]),
        ("Chart increasing", "chart_up", "Growth", "বৃদ্ধি", ["chart", "বিজনেস"]),
        ("Bar chart", "chart", "Chart", "চার্ট", ["chart", "বিজনেস"]),
        ("Money bag", "money", "Money", "টাকা", ["money", "টাকা"]),
        ("Dollar banknote", "cash", "Cash", "নোট", ["money", "টাকা"]),
        ("Credit card", "card", "Card", "কার্ড", ["card", "পেমেন্ট"]),
        ("Books", "books", "Books", "বই", ["book", "বই"]),
        ("Bullseye", "target", "Target", "লক্ষ্য", ["goal", "লক্ষ্য"]),
        ("Brain", "brain", "Brain", "মস্তিষ্ক", ["think", "ভাবনা"]),
        ("Telescope", "telescope", "Telescope", "দূরবীন", ["space", "মহাকাশ"]),
    ]),
    ("hands", "Hands", "হাত", [
        ("Thumbs up", "thumbs_up", "Thumbs up", "লাইক", ["like", "লাইক", "good"]),
        ("Thumbs down", "thumbs_down", "Thumbs down", "ডিসলাইক", ["dislike", "খারাপ"]),
        ("Clapping hands", "clap", "Clap", "তালি", ["clap", "তালি"]),
        ("Raising hands", "raise", "Raise hands", "হাত তোলা", ["hooray", "উৎসব"]),
        ("Ok hand", "ok", "OK", "ওকে", ["ok", "ঠিক"]),
        ("Victory hand", "victory", "Victory", "ভিক্টরি", ["peace", "শান্তি"]),
        ("Crossed fingers", "fingers", "Fingers crossed", "আঙুল ক্রস", ["luck", "ভাগ্য"]),
        ("Love-you gesture", "love_you", "Love you", "ভালোবাসি", ["love", "ভালোবাসা"]),
        ("Waving hand", "wave", "Wave", "হাত নাড়া", ["hello", "হ্যালো"]),
        ("Folded hands", "pray", "Pray", "প্রণাম", ["thanks", "ধন্যবাদ"]),
        ("Flexed biceps", "muscle", "Strong", "শক্তিশালী", ["strong", "শক্তি"]),
        ("Handshake", "handshake", "Handshake", "হ্যান্ডশেক", ["deal", "চুক্তি"]),
        ("Writing hand", "writing", "Writing", "লিখছি", ["write", "লেখা"]),
        ("Selfie", "selfie", "Selfie", "সেলফি", ["selfie", "ছবি"]),
    ]),
    ("reactions", "Reactions", "রিঅ্যাকশন", [
        ("Fire", "fire", "Fire", "আগুন", ["fire", "আগুন", "lit"]),
        ("Hundred points", "hundred", "100", "একশো", ["100", "perfect"]),
        ("Collision", "boom", "Boom", "ধুম", ["boom", "ধুম"]),
        ("Sweat droplets", "sweat", "Sweat", "ঘাম", ["sweat", "ঘাম"]),
        ("Dashing away", "dash", "Dash", "দৌড়", ["run", "দৌড়"]),
        ("Speech balloon", "speech", "Speech", "কথা", ["talk", "কথা"]),
        ("Thought balloon", "thought", "Thought", "ভাবনা", ["think", "ভাবনা"]),
        ("Zzz", "zzz", "Sleep", "ঘুম", ["sleep", "ঘুম"]),
        ("Check mark", "tick", "Check", "টিক", ["done", "ঠিক"]),
        ("Cross mark", "cross", "Cross", "ক্রস", ["no", "না"]),
        ("Red question mark", "question", "Question", "প্রশ্ন", ["question", "প্রশ্ন"]),
        ("Red exclamation mark", "exclamation", "Exclamation", "বিস্ময়", ["wow", "আশ্চর্য"]),
        ("Warning", "warning", "Warning", "সতর্ক", ["warning", "সতর্ক"]),
        ("Eyes", "eyes", "Eyes", "চোখ", ["eyes", "চোখ"]),
        ("Skull", "skull", "Skull", "মাথার খুলি", ["dead", "মজা"]),
        ("Pile of poo", "poo", "Poo", "পট্টি", ["poo", "মজা"]),
        ("Clown face", "clown", "Clown", "জোকার", ["clown", "জোকার"]),
        ("Ghost", "ghost", "Ghost", "ভূত", ["ghost", "ভূত"]),
    ]),
]


def tree_paths():
    url = ("https://api.github.com/repos/microsoft/fluentui-emoji/"
           "git/trees/main?recursive=1")
    req = urllib.request.Request(url, headers={"User-Agent": "likhon-build"})
    with urllib.request.urlopen(req, timeout=60) as r:
        data = json.load(r)
    return [t["path"] for t in data["tree"]
            if t["path"].endswith(".png") and "/3D/" in t["path"]]


def norm(text):
    return re.sub(r"[^a-z0-9]+", " ", text.lower()).strip()


def resolve(paths):
    table = {}
    for p in paths:
        parts = p.split("/")
        if len(parts) == 4:                       # assets/Folder/3D/file
            table[norm(parts[1])] = p
        elif len(parts) == 5 and parts[2] == "Default":
            table.setdefault(norm(parts[1]), p)   # skin-tone default
    return table


def fetch(path):
    url = BASE + urllib.parse.quote(path)
    req = urllib.request.Request(url, headers={"User-Agent": "likhon-build"})
    with urllib.request.urlopen(req, timeout=60) as r:
        return r.read()


def main():
    print("reading the Fluent Emoji tree…")
    table = resolve(tree_paths())

    jobs = []
    catalog = {"version": 1, "packs": []}
    for pack_id, pack_en, pack_bn, items in PACKS:
        pack = {"id": pack_id, "en": pack_en, "bn": pack_bn, "items": []}
        for folder, item_id, en, bn, tags in items:
            path = table.get(norm(folder))
            if not path:
                print("  MISSING:", folder)
                continue
            pack["items"].append({
                "id": item_id,
                "file": pack_id + "/" + item_id + ".webp",
                "en": en,
                "bn": bn,
                "tags": tags,
            })
            jobs.append((pack_id, item_id, path))
        catalog["packs"].append(pack)

    os.makedirs(ASSETS, exist_ok=True)
    for pack_id, *_ in PACKS:
        os.makedirs(os.path.join(ASSETS, pack_id), exist_ok=True)

    def work(job):
        pack_id, item_id, path = job
        raw = fetch(path)
        image = Image.open(io.BytesIO(raw)).convert("RGBA")
        image = image.resize((SIZE, SIZE), Image.LANCZOS)
        out = os.path.join(ASSETS, pack_id, item_id + ".webp")
        image.save(out, "WEBP", quality=QUALITY, method=6)
        return os.path.getsize(out)

    print("downloading and converting", len(jobs), "stickers…")
    total = 0
    with concurrent.futures.ThreadPoolExecutor(max_workers=6) as pool:
        for i, size in enumerate(pool.map(work, jobs), 1):
            total += size
            if i % 20 == 0:
                print("  ", i, "/", len(jobs))
    print("assets:", round(total / 1024.0), "KB")

    with open(os.path.join(ASSETS, "stickers.json"), "w", encoding="utf-8") as f:
        json.dump(catalog, f, ensure_ascii=False, separators=(",", ":"))

    with open(os.path.join(ASSETS, "LICENSE.txt"), "w", encoding="utf-8") as f:
        f.write(
            "Sticker artwork: Microsoft Fluent Emoji (3D)\n"
            "https://github.com/microsoft/fluentui-emoji\n"
            "MIT License, Copyright (c) Microsoft Corporation.\n\n"
            "The PNG files were resized to 192x192 and re-encoded as WebP.\n"
            "Some stickers got Bangla names and search tags.\n"
        )
    print("wrote", len(jobs), "stickers")


if __name__ == "__main__":
    sys.exit(main())
