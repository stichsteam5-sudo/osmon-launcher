"""Generates the Osmon icon/banner and the free theme packs (original artwork)."""
import json
import math
import os
import random
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

PROJ = sys.argv[1]          # android project dir
OUT = sys.argv[2]           # dist dir (Osmon/themes ...)
W, H = 1920, 1080
FONT = "/usr/share/fonts/opentype/inter/Inter-SemiBold.otf"


def hexc(s):
    s = s.lstrip("#")
    return np.array([int(s[i:i + 2], 16) for i in (0, 2, 4)], dtype=np.float32)


def vgrad(stops, w=W, h=H):
    """Vertical gradient from a list of (pos, hex)."""
    ys = np.linspace(0, 1, h)
    out = np.zeros((h, 3), np.float32)
    for c in range(3):
        out[:, c] = np.interp(ys, [p for p, _ in stops], [hexc(x)[c] for _, x in stops])
    return np.repeat(out[:, None, :], w, axis=1)


def to_img(arr):
    return Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8), "RGB")


def grain(img, amount=3.0, seed=0):
    rng = np.random.default_rng(seed)
    a = np.asarray(img).astype(np.float32)
    a += rng.normal(0, amount, a.shape[:2])[:, :, None]
    return to_img(a)


def glow(img, cx, cy, r, color, strength=1.0):
    a = np.asarray(img).astype(np.float32)
    yy, xx = np.mgrid[0:H, 0:W]
    d = np.sqrt((xx - cx) ** 2 + (yy - cy) ** 2) / r
    m = np.exp(-d * d * 2.2)[:, :, None] * strength
    a = a * (1 - m) + hexc(color) * m
    return to_img(a)


def ridge(seed, base, amp, smooth=True, rough=0.0):
    rng = random.Random(seed)
    xs = np.arange(W)
    y = np.full(W, float(base))
    for k in range(1, 5):
        f = rng.uniform(0.6, 1.4) * k / W * 2 * math.pi
        y += amp / k * np.sin(xs * f + rng.uniform(0, 6.28))
    if rough:
        n = np.zeros(W)
        step = 64
        while step >= 2:
            pts = np.array([rng.uniform(-1, 1) for _ in range(W // step + 2)])
            n += np.interp(xs, np.arange(len(pts)) * step, pts) * rough * step / 64
            step //= 2
        y += n
    return y


def fill_below(img, ys, color, blur=0, alpha=1.0):
    rgb = tuple(int(v) for v in hexc(color))
    layer = Image.new("RGBA", (W, H), rgb + (0,))
    d = ImageDraw.Draw(layer)
    poly = [(0, H)] + [(x, float(ys[x])) for x in range(0, W, 4)] + [(W - 1, float(ys[-1])), (W, H)]
    c = rgb + (int(255 * alpha),)
    d.polygon(poly, fill=c)
    if blur:
        layer = layer.filter(ImageFilter.GaussianBlur(blur))
    base = img.convert("RGBA")
    base.alpha_composite(layer)
    return base.convert("RGB")


def stars(img, n, seed, max_y=0.7):
    rng = random.Random(seed)
    d = ImageDraw.Draw(img)
    for _ in range(n):
        x, y = rng.uniform(0, W), rng.uniform(0, H * max_y)
        b = rng.uniform(120, 255) * (1 - y / (H * max_y)) ** 0.5
        r = rng.choice([0.6, 0.8, 1.0, 1.0, 1.4])
        d.ellipse([x - r, y - r, x + r, y + r], fill=(int(b), int(b), int(min(255, b + 15))))
    return img


# ---------------------------------------------------------------- wallpapers

def hills_sunset():
    img = to_img(vgrad([(0, "#2B1446"), (0.38, "#7A2E5C"), (0.58, "#F0805A"), (0.7, "#FFC98A"), (1, "#FFC98A")]))
    img = glow(img, W * 0.62, H * 0.6, 420, "#FFE3B0", 0.85)
    layers = [("#C76A7A", 0.60, 50, 10), ("#94456B", 0.68, 60, 6), ("#6A2D5C", 0.76, 70, 3),
              ("#43204A", 0.85, 80, 1), ("#25122E", 0.94, 70, 0)]
    for i, (c, b, a, bl) in enumerate(layers):
        img = fill_below(img, ridge(10 + i, H * b, a), c, blur=bl)
    return grain(img, 2.5, 1)


def mountains_night(pal, seed):
    sky, mid, hz, cols, moon = pal
    img = to_img(vgrad([(0, sky), (0.55, mid), (0.8, hz), (1, hz)]))
    img = stars(img, 900, seed)
    img = glow(img, W * 0.74, H * 0.24, 220, moon, 0.35)
    d = ImageDraw.Draw(img)
    mx, my, r = W * 0.74, H * 0.24, 46
    d.ellipse([mx - r, my - r, mx + r, my + r], fill=tuple(int(v) for v in hexc(moon)))
    img = img.filter(ImageFilter.GaussianBlur(0.6))
    for i, c in enumerate(cols):
        base = H * (0.55 + i * 0.1)
        img = fill_below(img, ridge(seed * 10 + i, base, 70 - i * 8, rough=55 - i * 8), c, blur=max(0, 4 - i * 1.5))
        # mist between layers
        if i < len(cols) - 1:
            hzc = tuple(int(v) for v in hexc(hz))
            mist = Image.new("RGBA", (W, H), hzc + (0,))
            md = ImageDraw.Draw(mist)
            y0 = int(base + 50)
            md.rectangle([0, y0, W, y0 + 50], fill=hzc + (38,))
            mist = mist.filter(ImageFilter.GaussianBlur(45))
            b2 = img.convert("RGBA")
            b2.alpha_composite(mist)
            img = b2.convert("RGB")
    return grain(img, 2.5, seed)


def aurora():
    img = to_img(vgrad([(0, "#020812"), (0.6, "#06172A"), (1, "#0A2236")]))
    img = stars(img, 1100, 77, 0.85)
    a = np.asarray(img).astype(np.float32)
    xs = np.arange(W, dtype=np.float32)
    yy = np.arange(H, dtype=np.float32)[:, None]
    for (col, y0, amp, L, ph, k) in [("#34D399", 0.46, 70, 230, 0.3, 1.0),
                                    ("#2DD4BF", 0.38, 60, 180, 2.0, 0.6),
                                    ("#A78BFA", 0.30, 50, 150, 4.1, 0.45)]:
        base = H * y0 + amp * np.sin(xs / W * 4.2 + ph) + 25 * np.sin(xs / W * 11 + ph * 2)
        length = L * (0.75 + 0.35 * np.sin(xs / W * 7 + ph))
        rays = 0.55 + 0.45 * np.sin(xs / 19.0 + ph) ** 2 * (0.6 + 0.4 * np.sin(xs / 71.0 + ph * 3))
        dy = base[None, :] - yy
        up = np.exp(-np.clip(dy, 0, None) / length[None, :])
        down = np.exp(-(np.clip(-dy, 0, None) / 14.0) ** 2)
        inten = np.where(dy >= 0, up, down) * rays[None, :]
        inten *= np.clip(1.0 - np.abs(xs - W * 0.5) / (W * 0.75), 0.25, 1)[None, :]
        a += inten[:, :, None] * hexc(col)[None, None, :] * 0.85 * k
    img = to_img(a).filter(ImageFilter.GaussianBlur(2.2))
    img = stars(img, 250, 78, 0.5)
    img = fill_below(img, ridge(31, H * 0.80, 40, rough=40), "#071422", blur=2)
    img = fill_below(img, ridge(32, H * 0.88, 50, rough=30), "#02070D", blur=0)
    return grain(img, 2.5, 3)


def dunes():
    img = to_img(vgrad([(0, "#3B2A6B"), (0.35, "#C46A6A"), (0.55, "#F3B07A"), (1, "#F3B07A")]))
    img = glow(img, W * 0.3, H * 0.5, 300, "#FFE6C2", 0.7)
    layers = [("#E7A26E", "#C9805A", 0.58), ("#D98A5A", "#B36A47", 0.68), ("#C2704A", "#94523A", 0.78),
              ("#A85A3C", "#7A3F2C", 0.88), ("#7E4030", "#55281E", 0.97)]
    for i, (light, dark, b) in enumerate(layers):
        ys = ridge(50 + i, H * b, 55)
        img = fill_below(img, ys, light, blur=1)
        img = fill_below(img, ys + 18 + 10 * np.sin(np.arange(W) / 140.0 + i), dark, blur=10, alpha=0.6)
    return grain(img, 2.5, 4)


def depth():
    img = to_img(vgrad([(0, "#060A1A"), (1, "#0B1230")]))
    layer = Image.new("RGBA", (W, H), (11, 18, 48, 0))
    d = ImageDraw.Draw(layer)
    for (x, y, r, c) in [(0.2, 0.3, 520, "#2563EB"), (0.75, 0.25, 460, "#7C3AED"), (0.6, 0.8, 600, "#0EA5E9"),
                         (0.15, 0.85, 380, "#DB2777"), (0.9, 0.7, 340, "#14B8A6")]:
        col = tuple(int(v) for v in hexc(c)) + (150,)
        d.ellipse([x * W - r, y * H - r * 0.7, x * W + r, y * H + r * 0.7], fill=col)
    layer = layer.filter(ImageFilter.GaussianBlur(160))
    b = img.convert("RGBA")
    b.alpha_composite(layer)
    return grain(b.convert("RGB"), 3.0, 5)


# ---------------------------------------------------------------- packs

def save_pack(name, folder, meta, images):
    d = os.path.join(OUT, "Osmon", "themes", folder)
    os.makedirs(d, exist_ok=True)
    for fn, im in images:
        im.save(os.path.join(d, fn), "JPEG", quality=92, optimize=True, progressive=True)
    meta = dict(meta, name=name)
    with open(os.path.join(d, "theme.json"), "w", encoding="utf-8") as f:
        json.dump(meta, f, ensure_ascii=False, indent=2)


def main():
    night_pals = [
        ("#030614", "#0B1A3A", "#2A3F6E", ["#1C2C55", "#14213F", "#0C152B", "#060B17"], "#F4F1E6"),
        ("#0A0518", "#24123F", "#5B3A78", ["#3A2459", "#2A1844", "#1B0F2E", "#0D0718"], "#FFE9F1"),
        ("#02100F", "#08302C", "#1E5A50", ["#124039", "#0C2E29", "#071E1B", "#030E0C"], "#EAFBF5"),
    ]
    save_pack("Холмы на закате", "01_Holmy", {"colors": ["#2B1446", "#7A2E5C", "#F0805A"],
              "glow": ["#F97316", "#EC4899", "#A855F7"], "dim": 10, "wallpaper": "wallpaper.jpg"},
              [("wallpaper.jpg", hills_sunset())])
    save_pack("Горы ночью", "02_Gory", {"colors": ["#030614", "#0B1A3A", "#2A3F6E"],
              "glow": ["#3B82F6", "#6366F1", "#0EA5E9"], "dim": 5, "wallpaper": "wallpaper.jpg"},
              [("wallpaper.jpg", mountains_night(night_pals[0], 11))])
    save_pack("Сияние", "03_Siyanie", {"colors": ["#020812", "#06172A", "#0A2236"],
              "glow": ["#10B981", "#22D3EE", "#8B5CF6"], "dim": 5, "wallpaper": "wallpaper.jpg"},
              [("wallpaper.jpg", aurora())])
    save_pack("Дюны", "04_Dyuny", {"colors": ["#3B2A6B", "#C46A6A", "#F3B07A"],
              "glow": ["#F59E0B", "#F97316", "#EC4899"], "dim": 15, "wallpaper": "wallpaper.jpg"},
              [("wallpaper.jpg", dunes())])
    save_pack("Глубина", "05_Glubina", {"colors": ["#060A1A", "#0B1230", "#060A1A"],
              "glow": ["#2563EB", "#7C3AED", "#0EA5E9"], "dim": 0, "wallpaper": "wallpaper.jpg"},
              [("wallpaper.jpg", depth())])
    save_pack("Ночные горы · слайдшоу", "06_Noch_slaidshou", {"colors": ["#030614", "#0B1A3A", "#2A3F6E"],
              "glow": ["#3B82F6", "#8B5CF6", "#14B8A6"], "dim": 5, "slideshow": True},
              [("night_%d.jpg" % (i + 1), mountains_night(p, 21 + i)) for i, p in enumerate(night_pals)])

    # app icon + banner
    for size, folder in [(96, "mipmap-xhdpi"), (144, "mipmap-xxhdpi")]:
        s = 4
        big = to_img(vgrad([(0, "#1B2A6B"), (0.55, "#7C3AED"), (1, "#F0805A")], size * s, size * s))
        mask = Image.new("L", big.size, 0)
        ImageDraw.Draw(mask).rounded_rectangle([0, 0, size * s - 1, size * s - 1], radius=size * s * 0.23, fill=255)
        d = ImageDraw.Draw(big)
        c = size * s / 2
        d.arc([c - size * s * 0.30, c - size * s * 0.30, c + size * s * 0.30, c + size * s * 0.30],
              start=180, end=360, fill=(255, 255, 255), width=int(size * s * 0.07))
        d.line([(c - size * s * 0.36, c + size * s * 0.05), (c + size * s * 0.36, c + size * s * 0.05)],
               fill=(255, 255, 255), width=int(size * s * 0.05))
        out = Image.new("RGBA", big.size, (0, 0, 0, 0))
        out.paste(big, (0, 0), mask)
        os.makedirs(os.path.join(PROJ, "res", folder), exist_ok=True)
        out.resize((size, size), Image.LANCZOS).save(os.path.join(PROJ, "res", folder, "ic_launcher.png"))

    bw, bh, s = 320, 180, 3
    ban = to_img(vgrad([(0, "#1B2A6B"), (0.6, "#5B2A86"), (1, "#C25A5A")], bw * s, bh * s))
    d = ImageDraw.Draw(ban)
    f = ImageFont.truetype(FONT, 64 * s // 2)
    txt = "Osmon"
    tw = d.textlength(txt, font=f)
    d.text(((bw * s - tw) / 2, bh * s * 0.30), txt, font=f, fill=(255, 255, 255))
    f2 = ImageFont.truetype(FONT.replace("SemiBold", "Regular"), 13 * s)
    sub = "TV launcher"
    sw = d.textlength(sub, font=f2)
    d.text(((bw * s - sw) / 2, bh * s * 0.66), sub, font=f2, fill=(230, 225, 255))
    os.makedirs(os.path.join(PROJ, "res", "drawable-xhdpi"), exist_ok=True)
    ban.resize((bw, bh), Image.LANCZOS).save(os.path.join(PROJ, "res", "drawable-xhdpi", "banner.png"))


if __name__ == "__main__":
    main()
