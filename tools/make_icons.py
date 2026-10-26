#!/usr/bin/env python3
"""Generate legacy launcher PNGs for Likhon Keyboard.

The adaptive icon (mipmap-anydpi-v26) covers Android 8+. These PNGs are the
fallback for Android 6 and 7, which cannot inflate vector launcher icons.
Run: python3 tools/make_icons.py
"""

from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "app" / "src" / "main" / "res"

SIZES = {
    "mdpi": 48,
    "hdpi": 72,
    "xhdpi": 96,
    "xxhdpi": 144,
    "xxxhdpi": 192,
}

TOP = (74, 143, 255)
BOTTOM = (36, 92, 214)
S = 4  # supersampling factor


def rounded(draw, box, radius, fill):
    draw.rounded_rectangle(box, radius=radius, fill=fill)


def render(size: int) -> Image.Image:
    n = size * S
    img = Image.new("RGBA", (n, n), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    # Vertical gradient inside a rounded square.
    radius = int(n * 0.22)
    mask = Image.new("L", (n, n), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, n - 1, n - 1), radius=radius, fill=255)

    grad = Image.new("RGBA", (n, n))
    gd = ImageDraw.Draw(grad)
    for y in range(n):
        t = y / max(1, n - 1)
        gd.line(
            [(0, y), (n, y)],
            fill=(
                int(TOP[0] + (BOTTOM[0] - TOP[0]) * t),
                int(TOP[1] + (BOTTOM[1] - TOP[1]) * t),
                int(TOP[2] + (BOTTOM[2] - TOP[2]) * t),
                255,
            ),
        )
    img.paste(grad, (0, 0), mask)

    # Soft highlight at the top.
    glow = Image.new("RGBA", (n, n), (0, 0, 0, 0))
    ImageDraw.Draw(glow).ellipse(
        (-n * 0.35, -n * 0.75, n * 1.35, n * 0.45), fill=(255, 255, 255, 26)
    )
    img.alpha_composite(Image.composite(glow, Image.new("RGBA", (n, n)), mask))

    # Key grid, proportionally the same as the adaptive icon.
    unit = n / 108.0

    def key(x, y, w, h, r=3.0, alpha=255):
        rounded(
            draw,
            (x * unit, y * unit, (x + w) * unit, (y + h) * unit),
            r * unit,
            (255, 255, 255, alpha),
        )

    for x in (25, 46, 67):
        key(x, 33, 16, 12)
        key(x, 48, 16, 12)
    key(25, 63, 17, 12)
    key(44, 63, 16, 12)
    key(65, 63, 20, 12)
    key(34, 78, 40, 11, r=5.5, alpha=235)

    return img.resize((size, size), Image.LANCZOS)


for folder, px in SIZES.items():
    out = RES / f"mipmap-{folder}"
    out.mkdir(parents=True, exist_ok=True)
    icon = render(px)
    icon.save(out / "ic_launcher.png")
    icon.save(out / "ic_launcher_round.png")

print("icons written")
