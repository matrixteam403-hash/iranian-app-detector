#!/usr/bin/env python3
"""
Full motion identity for Iranian App Detector.

Three scenes, 18 s total, 1080p, 30 fps:

  Scene 1 (0.0 - 5.0 s)  Identity  -- logo draws on: guide rings, shield stroke,
                                     radar sweep inside a clipped shield, sonar blips,
                                     wordmark (EN + FA) and feature pills.
  Scene 2 (5.0 - 11.0 s) Detection -- a phone-shaped sweep: package rows slide in,
                                     a scanning band sweeps over them, rows resolve to
                                     Verified / Probable / Uncertain with bars, and the
                                     dashboard counters count up.
  Scene 3 (11.0- 18.0 s) Protection -- VPN Hunter: shield + tunnel, five method chips
                                     light up one by one, a "Remove" card confirms per app,
                                     end card with logo, url and "one app at a time".

Everything is drawn from code (Pillow math), so the film is reproducible byte-for-byte
from this file: python3 brand/render_motion_full.py
Outputs: motion-full.mp4 (H.264), motion-full.webm, motion-full.gif, scene stills.
"""
import math
import os
import subprocess

from PIL import Image, ImageDraw, ImageFilter, ImageFont

OUT = os.path.dirname(os.path.abspath(__file__))
W, H = 1920, 1080
FPS = 30
SCENES = [(0.0, 5.0), (5.0, 11.0), (11.0, 18.0)]
DURATION = SCENES[-1][1]
FRAMES = int(FPS * DURATION)

EMERALD = (46, 229, 157)
EMERALD_SOFT = (124, 247, 198)
EMERALD_DEEP = (15, 164, 111)
AMBER = (245, 184, 61)
SKY = (90, 200, 250)
DANGER = (255, 107, 107)
INK = (3, 10, 8)
INK_MID = (7, 22, 17)
TEXT = (234, 246, 240)
TEXT_DIM = (155, 179, 168)


# --------------------------------------------------------------------- helpers
def clamp(v, lo=0.0, hi=1.0):
    return max(lo, min(hi, v))


def ease_out(t):
    t = clamp(t)
    return 1 - (1 - t) ** 3


def ease_in_out(t):
    t = clamp(t)
    return 3 * t * t - 2 * t * t * t


def back_out(t, s=1.4):
    t = clamp(t) - 1
    return 1 + (s + 1) * t ** 3 + s * t ** 2


def lerp(a, b, t):
    return a + (b - a) * t


def cubic(p0, p1, p2, p3, steps=160):
    pts = []
    for i in range(steps + 1):
        t = i / steps
        mt = 1 - t
        x = (mt ** 3) * p0[0] + 3 * (mt ** 2) * t * p1[0] + 3 * mt * t * t * p2[0] + t ** 3 * p3[0]
        y = (mt ** 3) * p0[1] + 3 * (mt ** 2) * t * p1[1] + 3 * mt * t * t * p2[1] + t ** 3 * p3[1]
        pts.append((x, y))
    return pts


def font(size, bold=False):
    path = (
        "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
        if bold
        else "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
    )
    if os.path.exists(path):
        return ImageFont.truetype(path, size)
    return ImageFont.load_default()


def fa_font(size):
    for p in (
        "/usr/share/fonts/truetype/noto/NotoNaskhArabic-Bold.ttf",
        "/usr/share/fonts/truetype/noto/NotoNaskhArabic-Regular.ttf",
        "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
    ):
        if os.path.exists(p):
            return ImageFont.truetype(p, size)
    return ImageFont.load_default()


# ------------------------------------------------------------------ the shield
SHIELD_UNIT = (
    [(256, 112), (356, 150), (356, 252)]
    + cubic((356, 252), (356, 320), (313, 370), (256, 398))
    + cubic((256, 398), (256, 398), (156, 320), (156, 252))
    + [(156, 252), (156, 150), (256, 112)]
)
TOTAL_LEN = sum(math.dist(a, b) for a, b in zip(SHIELD_UNIT, SHIELD_UNIT[1:]))


def shield_prefix(fraction):
    if fraction <= 0:
        return []
    target = fraction * TOTAL_LEN
    acc = 0.0
    out = [SHIELD_UNIT[0]]
    for a, b in zip(SHIELD_UNIT, SHIELD_UNIT[1:]):
        seg = math.dist(a, b)
        if acc + seg >= target:
            k = (target - acc) / seg if seg else 0
            out.append((lerp(a[0], b[0], k), lerp(a[1], b[1], k)))
            return out
        acc += seg
        out.append(b)
    return out


def shield_points(cx, cy, scale):
    return [(cx + (x - 256) * scale, cy + (y - 266) * scale) for x, y in SHIELD_UNIT]


# ---------------------------------------------------------------- backgrounds
def radial_background(seed_cx=320, seed_cy=470, radius=760, glow=0.55):
    img = Image.new("RGB", (W, H), INK)
    px = img.load()
    for y in range(0, H):
        for x in range(0, W, 2):
            d = math.dist((x, y), (seed_cx, seed_cy)) / radius
            v = clamp(d)
            col = (
                int(lerp(INK_MID[0], INK[0], v)),
                int(lerp(INK_MID[1], INK[1], v)),
                int(lerp(INK_MID[2], INK[2], v)),
            )
            px[x, y] = col
            if x + 1 < W:
                px[x + 1, y] = col
    glow_mask = Image.new("L", (W, H), 0)
    gd = ImageDraw.Draw(glow_mask)
    for i in range(34):
        r = int(radius * 0.62) - i * 12
        if r <= 0:
            continue
        gd.ellipse(
            [seed_cx - r, seed_cy - r, seed_cx + r, seed_cy + r],
            fill=int(glow * (8 + i * 3)),
        )
    glow_mask = glow_mask.filter(ImageFilter.GaussianBlur(90))
    tint = Image.new("RGB", (W, H), (14, 108, 78))
    img = Image.composite(tint, img, glow_mask)

    grid = ImageDraw.Draw(img)
    for x in range(0, W, 60):
        grid.line([(x, 0), (x, H)], fill=(9, 32, 25), width=1)
    for y in range(0, H, 60):
        grid.line([(0, y), (W, y)], fill=(9, 32, 25), width=1)
    return img


BG_LEFT = radial_background(330, 480)
BG_RIGHT = radial_background(1500, 520)
BG_CENTER = radial_background(960, 470, radius=900, glow=0.5)


def vignette(img, strength=0.55):
    mask = Image.new("L", (W, H), 0)
    md = ImageDraw.Draw(mask)
    md.ellipse([-W * 0.15, -H * 0.28, W * 1.15, H * 1.28], fill=255)
    mask = mask.filter(ImageFilter.GaussianBlur(180))
    dark = Image.new("RGB", (W, H), (0, 0, 0))
    return Image.composite(img, Image.blend(img, dark, strength), mask)


# --------------------------------------------------------------- shared pieces
def glow_blob(size, color, blur, alpha):
    layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    d.ellipse([size[0], size[1], size[2], size[3]], fill=color + (alpha,))
    return layer.filter(ImageFilter.GaussianBlur(blur))


def draw_shield(d, cx, cy, scale, progress=1.0, sweep_angle=None, sweep_alpha=0.0,
                arcs=1.0, blips=1.0, core=1.0, pulse=0.0, width_scale=1.0):
    """The brand mark, drawn into an RGBA ImageDraw."""
    pts = shield_points(cx, cy, scale)

    # interior fill
    if progress > 0.85:
        a = int(30 * clamp((progress - 0.85) / 0.15))
        d.polygon(pts, fill=EMERALD + (a,))

    # sweeps and radar content are clipped to the shield silhouette by the caller
    if sweep_angle is not None and sweep_alpha > 0:
        r = 118 * scale
        steps = 30
        for i in range(steps):
            a0 = sweep_angle - i * 0.019
            a1 = a0 + 0.02
            alpha = int(115 * sweep_alpha * (1 - i / steps) ** 2)
            if alpha <= 1:
                continue
            d.pieslice(
                [cx - r, cy - r, cx + r, cy + r],
                math.degrees(-a1), math.degrees(-a0),
                fill=EMERALD + (alpha,),
            )

    if arcs > 0:
        for idx, (r, strength) in enumerate([(44, 0.95), (76, 0.55), (108, 0.28)]):
            k = clamp(arcs * 1.7 - idx * 0.32)
            if k <= 0:
                continue
            rr = r * scale
            ox, oy = cx, cy + (266 - 266) * scale
            d.arc(
                [ox - rr, oy - rr, ox + rr, oy + rr],
                180, 180 + 90 * k,
                fill=EMERALD + (int(255 * strength * min(1.0, k * 2)),),
                width=int(8 * scale * width_scale),
            )
        tick_a = int(120 * clamp((arcs - 0.3) / 0.5))
        if tick_a > 0:
            for (p0, p1) in [((256, 306), (256, 322)), ((292, 266), (310, 266)), ((220, 266), (202, 266))]:
                A = (cx + (p0[0] - 256) * scale, cy + (p0[1] - 266) * scale)
                B = (cx + (p1[0] - 256) * scale, cy + (p1[1] - 266) * scale)
                d.line([A, B], fill=EMERALD + (tick_a,), width=int(6 * scale * width_scale))

    # outline (drawn on)
    if progress > 0:
        prefix = shield_prefix(ease_in_out(progress))
        if len(prefix) > 1:
            p = [(cx + (x - 256) * scale, cy + (y - 266) * scale) for x, y in prefix]
            d.line(p, fill=EMERALD + (255,), width=int(11 * scale * width_scale), joint="curve")

    # core
    if core > 0:
        r = 11 * scale * (1 + 0.10 * pulse)
        d.ellipse([cx - r * 2.4, cy - r * 2.4, cx + r * 2.4, cy + r * 2.4], fill=EMERALD + (55,))
        d.ellipse([cx - r * 1.55, cy - r * 1.55, cx + r * 1.55, cy + r * 1.55], fill=(8, 27, 20, 255))
        d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=EMERALD + (255,))
        d.ellipse([cx - r * 0.34, cy - r * 0.56, cx + r * 0.1, cy + r * 0.1], fill=(234, 255, 246, 210))

    # blips
    if blips > 0:
        specs = [
            ((312, 206), EMERALD, 9, 0.0),
            ((214, 232), AMBER, 6, 0.18),
            ((300, 320), EMERALD, 6, 0.34),
        ]
        for (pos, color, rr, delay) in specs:
            k = clamp(back_out((blips - delay) / 0.8))
            if k <= 0:
                continue
            bx = cx + (pos[0] - 256) * scale
            by = cy + (pos[1] - 266) * scale
            r = rr * scale * k
            d.ellipse([bx - r * 2, by - r * 2, bx + r * 2, by + r * 2], fill=color + (60,))
            d.ellipse([bx - r, by - r, bx + r, by + r], fill=color + (245,))


def wordmark(d, x, y, alpha, title_size=100, subtitle=True):
    """Title + one-line pitch + Persian line. Returns the y of the next free row."""
    if alpha <= 0:
        return y
    a255 = int(255 * clamp(alpha))
    t_font = font(title_size, bold=True)
    d.text((x, y), "Iranian App", font=t_font, fill=TEXT + (a255,))
    d.text((x, y + title_size + 4), "Detector", font=t_font, fill=EMERALD + (a255,))
    cursor = y + 2 * title_size + 34
    if subtitle:
        d.text(
            (x + 2, cursor),
            "Know what you installed.",
            font=font(34),
            fill=TEXT + (int(225 * clamp(alpha)),),
        )
        d.text(
            (x + 2, cursor + 44),
            "Remove what you do not want.",
            font=font(34),
            fill=TEXT + (int(225 * clamp(alpha)),),
        )
        try:
            d.text(
                (x + 2, cursor + 104),
                "بدانید چه نصب کرده‌اید؛ هرچه نمی‌خواهید حذف کنید",
                font=fa_font(32),
                fill=(132, 162, 152, int(220 * clamp(alpha))),
                direction="rtl",
            )
        except Exception:
            pass
        cursor += 160
    return cursor


def pill(d, x, y, label, alpha, color=EMERALD, fsize=23):
    if alpha <= 0:
        return 0
    f = font(fsize)
    tw = d.textlength(label, font=f)
    pad = 18
    h = 46
    a = clamp(alpha)
    d.rounded_rectangle(
        [x, y, x + tw + pad * 2, y + h],
        radius=12,
        fill=color + (int(20 * a),),
        outline=color + (int(115 * a),),
        width=1,
    )
    d.text((x + pad, y + 11), label, font=f, fill=(170, 240, 214, int(240 * a)))
    return tw + pad * 2 + 14


def count_up(d, x, y, value, width, alpha, color=EMERALD):
    """Big dashboard-style counter."""
    if alpha <= 0:
        return
    f = font(96, bold=True)
    d.text((x, y), f"{value}", font=f, fill=color + (int(255 * clamp(alpha)),))
    d.text((x, y + 104), width, font=font(24), fill=TEXT_DIM + (int(220 * clamp(alpha)),))


# ------------------------------------------------------------------ scene one
def scene_identity(t):
    local = t
    img = BG_LEFT.copy()
    layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    core = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(core, "RGBA")
    cx, cy, scale = 560, 520, 3.05

    rings = clamp((local - 0.10) / 1.1)
    for idx, (r, alpha) in enumerate([(150, 0.15), (104, 0.19)]):
        rr = r * scale * (0.84 + 0.16 * ease_out(rings - idx * 0.12))
        a = int(255 * alpha * clamp((rings - idx * 0.12) * 2.2))
        if a > 0:
            d.ellipse([cx - rr, cy - rr, cx + rr, cy + rr], outline=EMERALD + (a,), width=3)

    progress = clamp((local - 0.30) / 1.35)
    sweep_on = clamp((local - 1.45) / 0.55)
    arcs = clamp((local - 1.55) / 1.0)
    blips = clamp((local - 1.85) / 1.1)

    # radar content clipped to the shield (fill first: ImageDraw overwrites alpha)
    inner = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    di = ImageDraw.Draw(inner, "RGBA")
    di.polygon(shield_points(cx, cy, scale), fill=EMERALD + (20,))
    if sweep_on > 0:
        r = 118 * scale
        for i in range(30):
            a0 = (local * 1.35) % (2 * math.pi) - i * 0.019
            a1 = a0 + 0.02
            alpha = int(175 * sweep_on * (1 - i / 30) ** 2)
            if alpha > 1:
                di.pieslice([cx - r, cy - r, cx + r, cy + r],
                            math.degrees(-a1), math.degrees(-a0), fill=EMERALD + (alpha,))
    for idx, (r, strength) in enumerate([(52, 1.0), (92, 0.78), (132, 0.5)]):
        k = clamp(arcs * 1.7 - idx * 0.3)
        if k <= 0:
            continue
        rr = r * scale
        di.arc([cx - rr, cy - rr, cx + rr, cy + rr], 180, 180 + 90 * k,
               fill=EMERALD + (int(255 * strength * min(1, k * 2)),), width=int(14))
    if arcs > 0.3:
        ta = int(190 * clamp((arcs - 0.3) / 0.5))
        for (p0, p1) in [((256, 306), (256, 322)), ((292, 266), (310, 266)), ((220, 266), (202, 266))]:
            A = (cx + (p0[0] - 256) * scale, cy + (p0[1] - 266) * scale)
            B = (cx + (p1[0] - 256) * scale, cy + (p1[1] - 266) * scale)
            di.line([A, B], fill=EMERALD + (ta,), width=9)
    mask = Image.new("L", (W, H), 0)
    ImageDraw.Draw(mask).polygon(shield_points(cx, cy, scale), fill=255)
    core = Image.composite(Image.alpha_composite(core, inner), core, mask)
    d = ImageDraw.Draw(core, "RGBA")

    # shield outline with glow
    if progress > 0:
        prefix = shield_prefix(ease_in_out(progress))
        if len(prefix) > 1:
            p = [(cx + (x - 256) * scale, cy + (y - 266) * scale) for x, y in prefix]
            gl = Image.new("RGBA", (W, H), (0, 0, 0, 0))
            ImageDraw.Draw(gl).line(p, fill=EMERALD + (200,), width=int(30), joint="curve")
            layer = Image.alpha_composite(layer, gl.filter(ImageFilter.GaussianBlur(26)))
            d.line(p, fill=EMERALD + (95,), width=int(11 * scale * 0.66), joint="curve")
            d.line(p, fill=EMERALD_SOFT + (255,), width=int(11 * scale * 0.40), joint="curve")
    # core + blips
    fill_k = clamp((local - 1.25) / 0.6)
    if fill_k > 0:
        pulse = 1 + 0.11 * math.sin(local * 5.0)
        r = 11 * scale * pulse
        gl = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        ImageDraw.Draw(gl).ellipse([cx - r * 3, cy - r * 3, cx + r * 3, cy + r * 3], fill=EMERALD + (110,))
        layer = Image.alpha_composite(layer, gl.filter(ImageFilter.GaussianBlur(26)))
        d.ellipse([cx - r * 1.6, cy - r * 1.6, cx + r * 1.6, cy + r * 1.6], fill=(8, 27, 20, int(255 * fill_k)))
        d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=EMERALD + (int(255 * fill_k),))
        d.ellipse([cx - r * 0.34, cy - r * 0.56, cx + r * 0.1, cy + r * 0.1], fill=(234, 255, 246, int(210 * fill_k)))

    for (pos, color, rr, t0) in [
        ((312, 206), EMERALD, 9, 1.75),
        ((214, 232), AMBER, 6, 2.05),
        ((300, 320), EMERALD, 6, 2.30),
    ]:
        k = clamp((local - t0) / 0.5)
        if k <= 0:
            continue
        bx = cx + (pos[0] - 256) * scale
        by = cy + (pos[1] - 266) * scale
        r = rr * scale * back_out(k)
        gl = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        ImageDraw.Draw(gl).ellipse([bx - r * 3, by - r * 3, bx + r * 3, by + r * 3], fill=color + (120,))
        layer = Image.alpha_composite(layer, gl.filter(ImageFilter.GaussianBlur(20)))
        d.ellipse([bx - r, by - r, bx + r, by + r], fill=color + (int(245 * k),))
        ring_k = clamp((local - t0) / 1.5)
        if 0 < ring_k < 1:
            ring = r + 52 * scale * ring_k
            d.ellipse([bx - ring, by - ring, bx + ring, by + ring],
                      outline=color + (int(130 * (1 - ring_k)),), width=3)

    # text block
    text_k = ease_out(clamp((local - 2.35) / 0.9))
    next_y = wordmark(d, 1000, 300, text_k, title_size=100)
    if text_k > 0:
        x = 1002
        pills1 = ["10-layer detection", "VPN Hunter"]
        pills2 = ["One-by-one uninstall", "Per-app removal"]
        for row, labels in enumerate((pills1, pills2)):
            px = 1002
            for idx, label in enumerate(labels):
                k = ease_out(clamp((local - 3.15 - row * 0.18 - idx * 0.12) / 0.45))
                if k > 0:
                    color = SKY if "VPN" in label else EMERALD
                    px += pill(d, px, next_y + 10 + row * 58, label, k, color)

    out = Image.alpha_composite(img.convert("RGBA"), layer)
    out = Image.alpha_composite(out, core).convert("RGB")
    return out


# ------------------------------------------------------------------ scene two
def scene_detection(t):
    local = t
    img = BG_RIGHT.copy()
    core = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(core, "RGBA")

    # header
    head = ease_out(clamp(local / 0.7))
    d.text((150, 118), "Detection", font=font(74, bold=True), fill=TEXT + (int(255 * head),))
    d.text(
        (152, 208),
        "Ten independent signals, every result with its own evidence.",
        font=font(31), fill=TEXT_DIM + (int(235 * head),),
    )

    # phone-ish panel with package rows
    px0, py0, px1, py1 = 150, 300, 1120, 900
    panel_a = int(255 * ease_out(clamp(local / 0.5)))
    d.rounded_rectangle([px0, py0, px1, py1], radius=34,
                        fill=(11, 26, 21, int(200 * panel_a / 255)),
                        outline=(30, 51, 43, panel_a), width=2)

    rows = [
        ("Digikala", "com.digikala", 96, "Verified", EMERALD),
        ("Snapp", "cab.snapp.passenger", 88, "Verified", EMERALD),
        ("Bale", "com.ghsoft.bale", 71, "Probable", SKY),
        ("Rubika", "ir.resaneh1.iptv", 63, "Probable", SKY),
        ("Neshan", "org.rajman.neshan", 34, "Uncertain", AMBER),
    ]
    sweep_x = lerp(px0 - 120, px1 + 120, clamp((local - 0.8) / 2.6))
    for i, (label, pkg, conf, tier, color) in enumerate(rows):
        rk = ease_out(clamp((local - 0.35 - i * 0.16) / 0.55))
        if rk <= 0:
            continue
        y = py0 + 42 + i * 108 - (1 - rk) * 40
        a = int(255 * rk)
        d.rounded_rectangle([px0 + 26, y, px1 - 26, y + 92], radius=20,
                            fill=(18, 33, 27, int(215 * rk)), outline=(38, 63, 53, a), width=1)
        # icon
        d.rounded_rectangle([px0 + 44, y + 18, px0 + 100, y + 74], radius=16, fill=color + (40,),
                            outline=color + (110,), width=2)
        d.text((px0 + 58, y + 30), label[0], font=font(30, bold=True), fill=color + (255,))
        d.text((px0 + 120, y + 20), label, font=font(34, bold=True), fill=TEXT + (a,))
        d.text((px0 + 120, y + 58), pkg, font=font(24), fill=TEXT_DIM + (a,))

        # score + tier, revealed only after the sweep passes this row
        revealed = clamp((sweep_x - (px0 + 640)) / 90)
        if revealed > 0:
            d.text((px0 + 690, y + 16), f"{conf}%", font=font(38, bold=True),
                   fill=color + (int(255 * revealed),))
            tw = d.textlength(tier, font=font(22))
            d.rounded_rectangle([px1 - 190, y + 30, px1 - 190 + tw + 30, y + 66], radius=10,
                                fill=color + (int(30 * revealed),), outline=color + (int(120 * revealed),), width=1)
            d.text((px1 - 176, y + 37), tier, font=font(22), fill=color + (int(240 * revealed),))
            bar_w = 300
            d.rounded_rectangle([px1 - 190, y + 74, px1 - 190 + bar_w, y + 82], radius=4,
                                fill=(40, 62, 53, int(200 * revealed)))
            d.rounded_rectangle([px1 - 190, y + 74, px1 - 190 + bar_w * conf / 100, y + 82], radius=4,
                                fill=color + (int(240 * revealed),))

    # scanning band
    if 0.8 < local < 3.6:
        band = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        bd = ImageDraw.Draw(band)
        bd.rectangle([sweep_x - 26, py0 + 14, sweep_x + 26, py1 - 14], fill=EMERALD + (46,))
        bd.rectangle([sweep_x - 3, py0 + 14, sweep_x + 3, py1 - 14], fill=EMERALD_SOFT + (200,))
        core = Image.alpha_composite(core, band.filter(ImageFilter.GaussianBlur(6)))
        d = ImageDraw.Draw(core, "RGBA")
        d.text((px0 + 34, py1 + 18), "scanning packages…", font=font(24), fill=TEXT_DIM + (220,))
    elif local >= 3.6:
        d.text((px0 + 34, py1 + 18), "scan complete — 5 of 5 matched", font=font(24),
               fill=EMERALD + (230,))

    # dashboard counters
    counters = [(96, "installed"), (88, "detected"), None, None]
    cx0 = 1260
    cy0 = 330
    d.text((cx0, cy0 - 70), "Session", font=font(38, bold=True), fill=TEXT + (int(255 * head),))
    values = [
        ("Installed", 214, TEXT),
        ("Detected", 96, EMERALD),
        ("Verified", 41, EMERALD_SOFT),
        ("Probable", 33, SKY),
        ("Uncertain", 22, AMBER),
    ]
    for i, (label, target, color) in enumerate(values):
        k = clamp((local - 1.1 - i * 0.14) / 1.5)
        if k <= 0:
            continue
        shown = int(target * ease_in_out(k))
        y = cy0 + i * 108
        d.text((cx0, y), f"{shown}", font=font(72, bold=True), fill=color + (int(255 * min(1, k * 3)),))
        d.text((cx0 + 190, y + 26), label, font=font(28), fill=TEXT_DIM + (int(230 * min(1, k * 3)),))
        d.rounded_rectangle([cx0, y + 84, cx0 + 520, y + 90], radius=3, fill=(30, 51, 43, 220))
        d.rounded_rectangle([cx0, y + 84, cx0 + 520 * (shown / max(target, 1)), y + 90],
                            radius=3, fill=color + (235,))

    out = Image.alpha_composite(img.convert("RGBA"), core).convert("RGB")
    return out


# ---------------------------------------------------------------- scene three
def scene_protection(t):
    local = t
    img = BG_CENTER.copy()
    layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    core = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(core, "RGBA")

    head = ease_out(clamp(local / 0.6))
    d.text((1000, 130), "VPN Hunter", font=font(74, bold=True), fill=TEXT + (int(255 * head),))
    d.text((1002, 220), "Five independent methods. Every finding shows its evidence.",
           font=font(29), fill=TEXT_DIM + (int(230 * head),))

    # shield on the left: the same radar mark, with a tunnel running through it
    sx, sy, scale = 470, 520, 2.15
    prog = ease_out(clamp(local / 0.8))
    mask = Image.new("L", (W, H), 0)
    ImageDraw.Draw(mask).polygon(shield_points(sx, sy, scale), fill=255)
    inner = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    di = ImageDraw.Draw(inner, "RGBA")
    di.polygon(shield_points(sx, sy, scale), fill=EMERALD + (22,))

    # radar sweep
    sweep_r = 118 * scale
    for i in range(30):
        a0 = (local * 1.5) % (2 * math.pi) - i * 0.019
        a1 = a0 + 0.02
        alpha = int(82 * (1 - i / 30) ** 2)
        if alpha > 1:
            di.pieslice([sx - sweep_r, sy - sweep_r, sx + sweep_r, sy + sweep_r],
                        math.degrees(-a1), math.degrees(-a0), fill=EMERALD + (alpha,))
    # range arcs
    for idx, (r, strength) in enumerate([(52, 1.0), (92, 0.78), (132, 0.5)]):
        k = clamp(prog * 1.8 - idx * 0.3)
        if k <= 0:
            continue
        rr = r * scale
        di.arc([sx - rr, sy - rr, sx + rr, sy + rr], 180, 180 + 90 * k,
               fill=EMERALD + (int(255 * strength * min(1, k * 2)),), width=int(14))
    # crosshair ticks
    for (p0, p1) in [((256, 306), (256, 322)), ((292, 266), (310, 266)), ((220, 266), (202, 266))]:
        A = (sx + (p0[0] - 256) * scale, sy + (p0[1] - 266) * scale)
        B = (sx + (p1[0] - 256) * scale, sy + (p1[1] - 266) * scale)
        di.line([A, B], fill=EMERALD + (120,), width=int(6 * scale))
    # tunnel: two packets travelling outward along the shield's spine
    for i in range(2):
        k = clamp(((local * 0.55) + i / 2) % 1)
        spread = lerp(16, 96, k)
        alpha = int(225 * (1 - k) ** 1.1)
        for sgn in (-1, 1):
            px_ = sx + sgn * spread * scale * 0.34
            py_ = sy + 128 * scale * 0.34 * (1 - k)
            r_ = (8 - 4 * k) * scale * 0.34
            di.ellipse([px_ - r_, py_ - r_ * 2.4, px_ + r_, py_ + r_ * 2.4],
                       fill=EMERALD_SOFT + (alpha,))
    core = Image.composite(Image.alpha_composite(core, inner), core, mask)
    d = ImageDraw.Draw(core, "RGBA")

    pts = shield_points(sx, sy, scale)
    outline = pts + [pts[0]]
    gl = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    ImageDraw.Draw(gl).line(outline, fill=EMERALD + (200,), width=24, joint="curve")
    layer = Image.alpha_composite(layer, gl.filter(ImageFilter.GaussianBlur(26)))
    d.line(outline, fill=EMERALD + (95,), width=int(11 * scale * 0.62), joint="curve")
    d.line(outline, fill=EMERALD_SOFT + (255,), width=int(11 * scale * 0.36), joint="curve")

    # core with pulse
    pulse = 1 + 0.11 * math.sin(local * 5.0)
    r = 11 * scale * pulse
    gl2 = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    ImageDraw.Draw(gl2).ellipse([sx - r * 2.8, sy - r * 2.8, sx + r * 2.8, sy + r * 2.8], fill=EMERALD + (120,))
    layer = Image.alpha_composite(layer, gl2.filter(ImageFilter.GaussianBlur(22)))
    d.ellipse([sx - r * 1.55, sy - r * 1.55, sx + r * 1.55, sy + r * 1.55], fill=(8, 27, 20, 255))
    d.ellipse([sx - r, sy - r, sx + r, sy + r], fill=EMERALD + (255,))
    d.ellipse([sx - r * 0.34, sy - r * 0.56, sx + r * 0.1, sy + r * 0.1], fill=(234, 255, 246, 210))

    # five method chips lighting up
    methods = [
        ("Known package id", "82 signatures, incl. Iranian clients"),
        ("VPN service + transport", "BIND_VPN_SERVICE, wireguard/openvpn/xray cores"),
        ("System VPN accounting", "tun/ppp/utun interfaces, TRANSPORT_VPN, TrafficStats"),
        ("Routing profile", "Android always-on VPN + lock-down"),
        ("Name & developer", "فیلترشکن / پروکسی / tunnel keywords"),
    ]
    for i, (title, sub) in enumerate(methods):
        k = ease_out(clamp((local - 0.7 - i * 0.28) / 0.5))
        if k <= 0:
            continue
        x = 900 + (1 - k) * 40
        y = 330 + i * 98
        a = int(255 * k)
        d.rounded_rectangle([x, y, x + 860, y + 76], radius=18,
                            fill=(17, 32, 26, int(210 * k)), outline=EMERALD + (int(90 * k),), width=1)
        d.ellipse([x + 20, y + 24, x + 48, y + 52], fill=EMERALD + (int(60 * k),),
                  outline=EMERALD + (int(200 * k),), width=2)
        d.text((x + 26, y + 29), f"{i + 1}", font=font(19, bold=True), fill=EMERALD + (a,))
        d.text((x + 66, y + 12), title, font=font(28, bold=True), fill=TEXT + (a,))
        d.text((x + 66, y + 46), sub, font=font(20), fill=TEXT_DIM + (a,))
        d.rounded_rectangle([x + 820, y + 30, x + 840, y + 46], radius=3, fill=EMERALD + (int(220 * k),))

    # per-app remove card
    rk = ease_out(clamp((local - 2.5) / 0.6))
    if rk > 0:
        x, y = 900, 812
        d.rounded_rectangle([x, y, x + 860, y + 150], radius=26,
                            fill=(20, 36, 30, int(230 * rk)), outline=EMERALD + (int(110 * rk),), width=2)
        d.rounded_rectangle([x + 26, y + 34, x + 106, y + 114], radius=22, fill=EMERALD + (int(45 * rk),),
                            outline=EMERALD + (int(130 * rk),), width=2)
        d.text((x + 52, y + 54), "V", font=font(38, bold=True), fill=EMERALD + (int(255 * rk),))
        d.text((x + 128, y + 36), "V2Ray client", font=font(32, bold=True), fill=TEXT + (int(255 * rk),))
        d.text((x + 128, y + 78), "com.example.client · 71% Likely", font=font(22),
               fill=TEXT_DIM + (int(235 * rk),))
        d.rounded_rectangle([x + 606, y + 40, x + 828, y + 110], radius=16,
                            fill=DANGER + (int(38 * rk),), outline=DANGER + (int(160 * rk),), width=2)
        d.text((x + 646, y + 60), "Remove", font=font(28, bold=True), fill=DANGER + (int(255 * rk),))

    # confirmation flash
    if 3.2 < local < 5.2:
        k = clamp((local - 3.2) / 0.35) * clamp((5.2 - local) / 0.4)
        d.rounded_rectangle([900, 812, 1760, 962], radius=26, outline=EMERALD_SOFT + (int(220 * k),), width=3)
        d.text((1218, 990), "one request · one confirmation · one app",
               font=font(24), fill=EMERALD + (int(230 * k),))

    # end card
    ek = ease_out(clamp((local - 5.0) / 0.7))
    if ek > 0:
        veil = Image.new("RGBA", (W, H), (3, 10, 8, int(255 * ek)))
        core = Image.alpha_composite(core, veil)
        d = ImageDraw.Draw(core, "RGBA")
        a = int(255 * ek)
        wm_scale = lerp(0.86, 1.0, ek)
        swx, swy = 960, 380
        draw_shield(d, swx, swy, 1.5 * wm_scale, progress=1.0, arcs=1.0, blips=1.0, core=1.0,
                    pulse=math.sin(local * 5))
        d.text((630, 700), "Iranian App Detector", font=font(64, bold=True), fill=TEXT + (a,))
        d.text((714, 786), "github.com/matrixteam403-hash/iranian-app-detector",
               font=font(26), fill=EMERALD + (int(240 * ek),))
        try:
            d.text((700, 836), "یکی‌یکی، با تأیید خودتان",
                   font=fa_font(34), fill=TEXT_DIM + (int(220 * ek),), direction="rtl")
        except Exception:
            pass

    out = Image.alpha_composite(img.convert("RGBA"), layer)
    out = Image.alpha_composite(out, core).convert("RGB")
    return out


# ------------------------------------------------------------------- timeline
def frame(i):
    t = i / FPS
    if t < SCENES[0][1]:
        img = scene_identity(t)
        local = t
        span = SCENES[0][1]
    elif t < SCENES[1][1]:
        img = scene_detection(t - SCENES[1][0])
        local = t - SCENES[1][0]
        span = SCENES[1][1] - SCENES[1][0]
    else:
        img = scene_protection(t - SCENES[2][0])
        local = t - SCENES[2][0]
        span = SCENES[2][1] - SCENES[2][0]

    # cross-dissolve between scenes
    fade_in = clamp(local / 0.45)
    fade_out = clamp((span - local) / 0.45)
    k = min(fade_in, fade_out)
    if k < 1:
        black = Image.new("RGB", (W, H), (3, 10, 8))
        img = Image.blend(black, img, ease_in_out(k))
    return vignette(img)


def main():
    os.makedirs(os.path.join(OUT, "_frames"), exist_ok=True)
    tmp = os.path.join(OUT, "_frames")
    for name in os.listdir(tmp):
        os.remove(os.path.join(tmp, name))

    for i in range(FRAMES):
        f = frame(i)
        f.save(os.path.join(tmp, f"f{i:04d}.png"))
        if i % 60 == 0:
            print("frame", i, "/", FRAMES, flush=True)

    # stills
    for label, idx in [("identity", int(3.4 * FPS)), ("detection", int(9.2 * FPS)),
                       ("protection", int(15.6 * FPS))]:
        frame(idx).save(os.path.join(OUT, f"motion-full-{label}.png"))
    frame(int(16.6 * FPS)).save(os.path.join(OUT, "motion-full-poster.png"))

    import imageio_ffmpeg
    ffmpeg = imageio_ffmpeg.get_ffmpeg_exe()
    mp4 = os.path.join(OUT, "motion-full.mp4")
    subprocess.run(
        [ffmpeg, "-y", "-framerate", str(FPS), "-i", os.path.join(tmp, "f%04d.png"),
         "-c:v", "libx264", "-pix_fmt", "yuv420p", "-crf", "20", "-preset", "medium",
         "-movflags", "+faststart", mp4], check=True, capture_output=True)
    subprocess.run(
        [ffmpeg, "-y", "-framerate", str(FPS), "-i", os.path.join(tmp, "f%04d.png"),
         "-c:v", "libvpx-vp9", "-b:v", "0", "-crf", "40", "-row-mt", "1",
         os.path.join(OUT, "motion-full.webm")], check=False, capture_output=True)

    # gif: every 2nd frame, 960 px wide, 128 colours
    step = 2
    gif_frames = []
    for i in range(0, FRAMES, step):
        im = Image.open(os.path.join(tmp, f"f{i:04d}.png")).resize((960, 540), Image.LANCZOS)
        gif_frames.append(im.convert("P", palette=Image.ADAPTIVE, colors=128))
    gif_frames[0].save(
        os.path.join(OUT, "motion-full.gif"), save_all=True,
        append_images=gif_frames[1:], duration=int(1000 * step / FPS),
        loop=0, optimize=True, disposal=2,
    )
    for name in os.listdir(tmp):
        os.remove(os.path.join(tmp, name))
    os.rmdir(tmp)
    print("done")


if __name__ == "__main__":
    main()
