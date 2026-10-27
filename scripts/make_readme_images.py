#!/usr/bin/env python3
"""Builds the README images in docs/ from the emulator screenshots the preview job pushes to the ci-shots branch.

    git fetch origin ci-shots && mkdir -p /tmp/cis && git archive origin/ci-shots | tar -x -C /tmp/cis
    python3 scripts/make_readme_images.py /tmp/cis docs
"""
import glob
import os
import sys

from PIL import Image, ImageDraw, ImageFilter

SRC = sys.argv[1] if len(sys.argv) > 1 else "/tmp/cis"
OUT = sys.argv[2] if len(sys.argv) > 2 else "docs"
os.makedirs(OUT, exist_ok=True)


def load(name):
    return Image.open(os.path.join(SRC, name + ".png")).convert("RGB")


def labelled(label):
    """A demo frame the preview job labelled (typed, emoji)."""
    f = sorted(glob.glob(os.path.join(SRC, "frames", "*_" + label + ".png")))
    return Image.open(f[0]).convert("RGB")


def rounded(im, r):
    mask = Image.new("L", im.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, im.size[0] - 1, im.size[1] - 1], r, fill=255)
    out = Image.new("RGBA", im.size)
    out.paste(im, (0, 0), mask)
    return out


def phone(im, width, bezel=10, dark=True):
    """A screenshot inside a simple phone body."""
    h = round(im.size[1] * width / im.size[0])
    scr = rounded(im.resize((width, h), Image.LANCZOS), 34)
    body = Image.new("RGBA", (width + 2 * bezel, h + 2 * bezel), (0, 0, 0, 0))
    d = ImageDraw.Draw(body)
    d.rounded_rectangle([0, 0, body.size[0] - 1, body.size[1] - 1], 34 + bezel, fill=(32, 33, 36) if dark else (60, 64, 67))
    body.alpha_composite(scr, (bezel, bezel))
    return body


def card(im, width):
    """A cropped screenshot as a rounded card."""
    h = round(im.size[1] * width / im.size[0])
    return rounded(im.resize((width, h), Image.LANCZOS), 26)


def shadow(img, blur=22, offset=(0, 14), alpha=110):
    pad = blur * 3
    sh = Image.new("RGBA", (img.size[0] + pad * 2, img.size[1] + pad * 2), (0, 0, 0, 0))
    a = img.split()[3].point(lambda v: alpha if v > 0 else 0)
    sh.paste((0, 0, 0, 255), (pad + offset[0], pad + offset[1]), a)
    sh = sh.filter(ImageFilter.GaussianBlur(blur))
    sh.alpha_composite(img, (pad, pad))
    return sh


def gradient(size, c1, c2):
    w, h = size
    base = Image.new("RGB", size, c1)
    top = Image.new("RGB", size, c2)
    mask = Image.linear_gradient("L").resize(size)
    return Image.composite(top, base, mask)


def row(items, bg, gap=36, pad=48, anchor="top"):
    shs = [shadow(i) for i in items]
    w = sum(s.size[0] for s in shs) + gap * (len(shs) - 1) + pad * 2
    h = max(s.size[1] for s in shs) + pad * 2
    canvas = bg(w, h).convert("RGBA")
    x = pad
    for s in shs:
        y = pad if anchor == "top" else (h - s.size[1]) // 2
        canvas.alpha_composite(s, (x - 0, y - 0))
        x += s.size[0] + gap - 0
    return canvas.convert("RGB")


def bg_dark(w, h):
    return gradient((w, h), (22, 24, 38), (46, 40, 74))


def bg_light(w, h):
    return gradient((w, h), (236, 240, 252), (222, 214, 244))


def keyboard_crop(name, top=1020):
    im = load(name)
    return im.crop((0, top, im.size[0], im.size[1]))


# --- hero: setup, typing, emoji
hero = row(
    [
        phone(load("app_setup_WELCOME"), 330),
        phone(labelled("typed"), 330),
        phone(labelled("emoji"), 330),
        phone(load("app_setup_PERSONAL"), 330),
    ],
    bg_dark,
    gap=30,
    pad=56,
    anchor="center",
)
hero.save(os.path.join(OUT, "hero.png"), optimize=True)

# --- keyboard states
kb = row(
    [
        card(keyboard_crop("01_letters_dark"), 360),
        card(keyboard_crop("03_popup_e", 900), 360),
        card(keyboard_crop("10_tools_row"), 360),
        card(keyboard_crop("06_symbols1"), 360),
    ],
    bg_dark,
    gap=24,
    pad=40,
    anchor="center",
)
kb.save(os.path.join(OUT, "keyboard-dark.png"), optimize=True)

kb2 = row(
    [
        card(keyboard_crop("11_letters_light"), 360),
        card(keyboard_crop("12_emoji_light", 1020), 360),
        card(keyboard_crop("09_emoticons", 1020), 360),
    ],
    bg_light,
    gap=24,
    pad=40,
    anchor="center",
)
kb2.save(os.path.join(OUT, "keyboard-light.png"), optimize=True)

# --- emoji screens
emo = row(
    [
        card(keyboard_crop("08_emoji", 1000), 360),
        card(keyboard_crop("23_search_emoji", 1000), 360),
        card(keyboard_crop("22_emoticons", 1000), 360),
    ],
    bg_dark,
    gap=24,
    pad=40,
    anchor="center",
)
emo.save(os.path.join(OUT, "emoji-screens.png"), optimize=True)

# --- setup guide and app
setup = row(
    [phone(load(n), 300) for n in ("app_setup_WELCOME", "app_setup_MODEL", "app_setup_ENABLE", "app_setup_PERSONAL", "app_setup_PRACTICE", "app_setup_DONE")],
    bg_dark,
    gap=22,
    pad=44,
    anchor="center",
)
setup.save(os.path.join(OUT, "setup.png"), optimize=True)

app = row(
    [phone(load(n), 300) for n in ("app_main_1", "app_main_2", "app_main_3", "app_setup_WELCOME_light")],
    bg_dark,
    gap=24,
    pad=44,
    anchor="center",
)
app.save(os.path.join(OUT, "app.png"), optimize=True)

# --- animated demo from the frames the preview job took
frames = sorted(glob.glob(os.path.join(SRC, "frames", "*.png")))
if frames:
    W = 300
    imgs = []
    for f in frames:
        im = Image.open(f).convert("RGB")
        # status bar off the top, then the screen as a phone
        im = im.crop((0, 90, im.size[0], im.size[1]))
        imgs.append(phone(im, W, bezel=8).convert("RGB"))
    # a dark backdrop so the GIF edges look clean
    pad = 18
    canvas_size = (imgs[0].size[0] + pad * 2, imgs[0].size[1] + pad * 2)
    out = []
    for im in imgs:
        c = Image.new("RGB", canvas_size, (22, 24, 38))
        c.paste(im, (pad, pad))
        out.append(c.quantize(colors=96, method=Image.MEDIANCUT, dither=Image.NONE))
    durations = []
    for i in range(len(out)):
        # repeated frames (the pauses) are merged by the same-image check below
        durations.append(330)
    # merge identical neighbouring frames into one longer one
    merged, mdur = [out[0]], [durations[0]]
    for im, d in zip(out[1:], durations[1:]):
        if im.tobytes() == merged[-1].tobytes():
            mdur[-1] += d
        else:
            merged.append(im)
            mdur.append(d)
    mdur = [max(d, 450) if d > 330 else 380 for d in mdur]
    mdur[-1] += 1200
    merged[0].save(
        os.path.join(OUT, "demo.gif"),
        save_all=True,
        append_images=merged[1:],
        duration=mdur,
        loop=0,
        optimize=True,
        disposal=1,
    )
    print("gif frames", len(merged), "size", os.path.getsize(os.path.join(OUT, "demo.gif")) // 1024, "KB")

for f in sorted(os.listdir(OUT)):
    p = os.path.join(OUT, f)
    if os.path.isfile(p):
        print(f, os.path.getsize(p) // 1024, "KB")
