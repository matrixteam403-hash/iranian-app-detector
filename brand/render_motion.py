#!/usr/bin/env python3
"""
Renders the Iranian App Detector motion identity (logo reveal).

Pure math + PIL: every frame is drawn from the same vector description that
lives in brand/logo.svg, so the animation and the static logo never drift apart.
Outputs: MP4 (H.264), GIF and a poster frame.
"""
import math
import os
import subprocess

from PIL import Image, ImageDraw, ImageFilter, ImageFont

OUT = os.path.dirname(os.path.abspath(__file__))
W, H = 1280, 720
SS = 2                      # supersampling factor
FPS = 30
DURATION = 7.0              # seconds
FRAMES = int(FPS * DURATION)
EMERALD = (46, 229, 157)
EMERALD_DIM = (15, 164, 111)
AMBER = (255, 183, 77)
INK = (3, 10, 8)
BG_MID = (7, 22, 17)


def clamp(v, lo=0.0, hi=1.0):
    return max(lo, min(hi, v))


def ease_out(t):
    t = clamp(t)
    return 1 - (1 - t) ** 3


def ease_in_out(t):
    t = clamp(t)
    return 3 * t * t - 2 * t * t * t


def lerp(a, b, t):
    return a + (b - a) * t


def cubic(p0, p1, p2, p3, steps=90):
    pts = []
    for i in range(steps + 1):
        t = i / steps
        mt = 1 - t
        x = (mt ** 3) * p0[0] + 3 * (mt ** 2) * t * p1[0] + 3 * mt * t * t * p2[0] + t ** 3 * p3[0]
        y = (mt ** 3) * p0[1] + 3 * (mt ** 2) * t * p1[1] + 3 * mt * t * t * p2[1] + t ** 3 * p3[1]
        pts.append((x, y))
    return pts


# --- the shield outline, sampled from the SVG path -------------------------
SHIELD = (
    [(256, 112), (356, 150), (356, 252)]
    + cubic((356, 252), (356, 320), (313, 370), (256, 398))
    + cubic((256, 398), (256, 398), (156, 320), (156, 252))
    + [(156, 252), (156, 150), (256, 112)]
)


def shield_prefix(fraction, total_len):
    """Returns the points of the shield path up to `fraction` of its length."""
    if fraction <= 0:
        return []
    target = fraction * total_len
    acc = 0.0
    out = [SHIELD[0]]
    for a, b in zip(SHIELD, SHIELD[1:]):
        seg = math.dist(a, b)
        if acc + seg >= target:
            k = (target - acc) / seg if seg else 0
            out.append((lerp(a[0], b[0], k), lerp(a[1], b[1], k)))
            return out
        acc += seg
        out.append(b)
    return out


TOTAL_LEN = sum(math.dist(a, b) for a, b in zip(SHIELD, SHIELD[1:]))

# scene transform: 512 design units -> canvas
SCALE = 1.62
CX, CY = 300, 360          # logo centre on the 1280x720 canvas
TITLE_X = 640


def to_canvas(p):
    return (CX + (p[0] - 256) * SCALE, CY + (p[1] - 266) * SCALE)


def background():
    """Static dark background, rendered once."""
    img = Image.new("RGB", (W, H), INK)
    px = img.load()
    for y in range(H):
        for x in range(0, W, 1):
            d = math.dist((x * 0.55, y), (300 * 0.55, 330)) / 900
            v = clamp(d)
            r = int(lerp(BG_MID[0], INK[0], v))
            g = int(lerp(BG_MID[1], INK[1], v))
            b = int(lerp(BG_MID[2], INK[2], v))
            px[x, y] = (r, g, b)
    # soft emerald wash behind the logo
    glow = Image.new("L", (W, H), 0)
    gd = ImageDraw.Draw(glow)
    for i in range(28):
        r = 330 - i * 10
        gd.ellipse([CX - r, CY - r, CX + r, CY + r], fill=int(1.15 * i))
    glow = glow.filter(ImageFilter.GaussianBlur(46))
    tint = Image.new("RGB", (W, H), (16, 120, 88))
    img = Image.composite(tint, img, glow.point(lambda v: int(v * 0.55)))
    # subtle grid
    gd = ImageDraw.Draw(img)
    for x in range(0, W, 40):
        gd.line([(x, 0), (x, H)], fill=(9, 34, 26), width=1)
    for y in range(0, H, 40):
        gd.line([(0, y), (W, y)], fill=(9, 34, 26), width=1)
    return img


BACKGROUND = background()


def load_font(size, bold=False):
    candidates = [
        "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf" if bold else "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
        "/usr/share/fonts/truetype/liberation/LiberationSans-Bold.ttf" if bold else "/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf",
    ]
    for c in candidates:
        if os.path.exists(c):
            return ImageFont.truetype(c, size)
    return ImageFont.load_default()


def persian_font(size):
    for c in [
        "/usr/share/fonts/truetype/noto/NotoNaskhArabic-Bold.ttf",
        "/usr/share/fonts/truetype/noto/NotoNaskhArabic-Regular.ttf",
        "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
    ]:
        if os.path.exists(c):
            return ImageFont.truetype(c, size)
    return ImageFont.load_default()


def draw_arc_sweep(layer, t_sweep, alpha):
    """Rotating radar wedge clipped to the shield shape (approximated by alpha ramp)."""
    R = 118 * SCALE
    cx, cy = to_canvas((256, 266))
    steps = 26
    for i in range(steps):
        a0 = t_sweep - i * 0.021
        a1 = a0 + 0.022
        a = int(120 * alpha * (1 - i / steps) ** 2)
        if a <= 1:
            continue
        d = ImageDraw.Draw(layer, "RGBA")
        d.pieslice(
            [cx - R, cy - R, cx + R, cy + R],
            math.degrees(-a1), math.degrees(-a0),
            fill=(46, 229, 157, a),
        )


def frame(i):
    t = i / FPS
    img = BACKGROUND.copy()
    layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))   # glow layer
    core = Image.new("RGBA", (W, H), (0, 0, 0, 0))   # crisp layer
    d = ImageDraw.Draw(core, "RGBA")

    # timeline (seconds)
    t_rings = clamp((t - 0.15) / 1.0)
    t_shield = clamp((t - 0.35) / 1.25)
    t_fill = clamp((t - 1.45) / 0.6)
    t_sweep_on = clamp((t - 1.6) / 0.5)
    t_arcs = clamp((t - 1.9) / 0.9)
    t_text = clamp((t - 2.9) / 0.9)
    t_badge = clamp((t - 3.6) / 0.8)

    cx, cy = to_canvas((256, 266))

    # 1. concentric guides
    for idx, (r, alpha) in enumerate([(150, 0.16), (104, 0.20)]):
        rr = r * SCALE * (0.86 + 0.14 * ease_out(t_rings - idx * 0.12))
        a = int(255 * alpha * clamp((t_rings - idx * 0.12) * 2.4))
        if a > 0:
            d.ellipse([cx - rr, cy - rr, cx + rr, cy + rr], outline=(46, 229, 157, a), width=2)

    # 2. shield fill
    if t_fill > 0:
        d.polygon([to_canvas(p) for p in SHIELD], fill=(46, 229, 157, int(28 * ease_in_out(t_fill))))

    # 3. radar sweep inside the shield
    if t_sweep_on > 0:
        sweep_layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        draw_arc_sweep(sweep_layer, (t * 1.5) % (2 * math.pi) + 1.2, t_sweep_on)
        mask = Image.new("L", (W, H), 0)
        ImageDraw.Draw(mask).polygon([to_canvas(p) for p in SHIELD], fill=255)
        core = Image.composite(Image.alpha_composite(core, sweep_layer), core, mask)
        d = ImageDraw.Draw(core, "RGBA")

    # 4 + 5. range arcs and crosshair ticks, clipped to the shield silhouette
    inner = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    di = ImageDraw.Draw(inner, "RGBA")
    arcs = [
        ((212, 266, 44), 0.0, 0.95),
        ((180, 266, 76), 0.16, 0.55),
        ((148, 266, 108), 0.32, 0.28),
    ]
    drew_inner = False
    for idx, (arc, delay, strength) in enumerate(arcs):
        k = clamp(t_arcs * 1.7 - delay * 2)
        if k <= 0:
            continue
        (ax, ay, r) = arc
        c = to_canvas((ax, ay))
        rr = r * SCALE
        a = int(255 * strength * min(1.0, k * 2))
        di.arc([c[0] - rr, c[1] - rr, c[0] + rr, c[1] + rr], 180, 180 + 90 * k,
               fill=(46, 229, 157, a), width=int(8 * SCALE))
        drew_inner = True
    if t_arcs > 0.3:
        a = int(120 * clamp((t_arcs - 0.3) / 0.5))
        for (p0, p1) in [((256, 306), (256, 322)), ((292, 266), (310, 266)), ((220, 266), (202, 266))]:
            A, B = to_canvas(p0), to_canvas(p1)
            di.line([A, B], fill=(46, 229, 157, a), width=int(6 * SCALE))
        drew_inner = True
    if drew_inner:
        shmask = Image.new("L", (W, H), 0)
        ImageDraw.Draw(shmask).polygon([to_canvas(p) for p in SHIELD], fill=255)
        core = Image.composite(Image.alpha_composite(core, inner), core, shmask)
        d = ImageDraw.Draw(core, "RGBA")

    # 6. shield outline drawn on (glow underneath, crisp on top)
    if 0 < t_shield:
        pts = shield_prefix(ease_in_out(t_shield), TOTAL_LEN)
        pts = [to_canvas(p) for p in pts]
        if len(pts) > 1:
            gl = Image.new("RGBA", (W, H), (0, 0, 0, 0))
            ImageDraw.Draw(gl).line(pts, fill=(46, 229, 157, 190), width=int(26 * SCALE), joint="curve")
            layer = Image.alpha_composite(layer, gl.filter(ImageFilter.GaussianBlur(14)))
            d.line(pts, fill=(124, 247, 198, 255), width=int(11 * SCALE), joint="curve")
            d.line(pts, fill=(46, 229, 157, 255), width=int(8 * SCALE), joint="curve")

    # 7. core dot with pulse
    if t_fill > 0.3:
        pulse = 1 + 0.10 * math.sin(t * 4.4)
        for r, a in [(26 * pulse, 60), (17 * pulse, 255)]:
            rr = r * SCALE
            col = (8, 27, 20, 255) if r < 20 else None
            if col:
                d.ellipse([cx - rr * 0.62, cy - rr * 0.62, cx + rr * 0.62, cy + rr * 0.62], fill=col)
        rr = 11 * SCALE * pulse
        gl = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        ImageDraw.Draw(gl).ellipse([cx - rr * 2.4, cy - rr * 2.4, cx + rr * 2.4, cy + rr * 2.4],
                                  fill=(46, 229, 157, 120))
        layer = Image.alpha_composite(layer, gl.filter(ImageFilter.GaussianBlur(18)))
        d.ellipse([cx - rr, cy - rr, cx + rr, cy + rr], fill=(46, 229, 157, 255))
        d.ellipse([cx - rr * 0.34, cy - rr * 0.34 - rr * 0.22, cx + rr * 0.1, cy + rr * 0.1],
                  fill=(234, 255, 246, 200))

    # 8. detection blips with expanding rings
    blips = [((312, 206), 2.15, EMERALD, 9), ((214, 232), 2.55, AMBER, 6), ((300, 320), 3.0, EMERALD, 6)]
    blip_layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    db = ImageDraw.Draw(blip_layer, "RGBA")
    blip_glow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    dg = ImageDraw.Draw(blip_glow, "RGBA")
    for (pos, t0, color, rr0) in blips:
        k = clamp((t - t0) / 0.55)
        if k <= 0:
            continue
        p = to_canvas(pos)
        r = rr0 * SCALE * ease_out(k)
        dg.ellipse([p[0] - r * 2.1, p[1] - r * 2.1, p[0] + r * 2.1, p[1] + r * 2.1],
                   fill=color + (110,))
        db.ellipse([p[0] - r, p[1] - r, p[0] + r, p[1] + r], fill=color + (int(240 * ease_out(k)),))
        rk = clamp((t - t0) / 1.6)
        if 0 < rk < 1:
            ring = r + 40 * SCALE * rk
            db.ellipse([p[0] - ring, p[1] - ring, p[0] + ring, p[1] + ring],
                       outline=color + (int(120 * (1 - rk)),), width=2)
    blipmask = Image.new("L", (W, H), 0)
    ImageDraw.Draw(blipmask).polygon([to_canvas(p) for p in SHIELD], fill=255)
    layer = Image.alpha_composite(layer, blip_glow.filter(ImageFilter.GaussianBlur(12)))
    core = Image.composite(Image.alpha_composite(core, blip_layer), core, blipmask)
    d = ImageDraw.Draw(core, "RGBA")

    # 9. wordmark
    if t_text > 0:
        a = ease_out(t_text)
        title_font = load_font(64, bold=True)
        sub_font = load_font(28)
        fa_font = persian_font(34)
        dx = int(30 * (1 - a))
        d.text((TITLE_X + dx, 292), "Iranian App", font=title_font, fill=(234, 255, 246, int(255 * a)))
        d.text((TITLE_X + dx, 366), "Detector", font=title_font, fill=(46, 229, 157, int(255 * a)))
        d.text((TITLE_X + dx + 2, 452), "Bulk uninstaller for Iranian apps",
               font=sub_font, fill=(148, 178, 168, int(230 * a)))
        try:
            d.text((TITLE_X + dx + 2, 490), "شناسایی و حذف برنامه‌های ایرانی",
                   font=fa_font, fill=(120, 152, 142, int(210 * a)), direction="rtl")
        except Exception:
            pass

    # 10. feature badges
    badges = ["10-layer detection", "VPN Hunter", "One-by-one uninstall"]
    if t_badge > 0:
        bf = load_font(20)
        x = TITLE_X
        for idx, label in enumerate(badges):
            k = ease_out(clamp(t_badge * 1.6 - idx * 0.28))
            if k <= 0:
                continue
            tw = d.textlength(label, font=bf)
            pad = 16
            box = [x, 556, x + tw + pad * 2, 596]
            d.rounded_rectangle(box, radius=10,
                                fill=(46, 229, 157, int(20 * k)), outline=(46, 229, 157, int(110 * k)), width=1)
            d.text((x + pad, 566), label, font=bf, fill=(160, 240, 212, int(235 * k)))
            x += tw + pad * 2 + 12

    # global fade-in / fade-out for a clean loop
    out = Image.alpha_composite(core, layer)
    # composite layers: glow (baked in `layer`) sits *under* the crisp art
    composed = Image.alpha_composite(img.convert("RGBA"), layer)
    composed = Image.alpha_composite(composed, core)
    composed = composed.convert("RGB")
    fade = min(clamp(t / 0.5), clamp((DURATION - 0.15 - t) / 0.55))
    if fade < 1:
        composed = Image.composite(composed, img, Image.new("L", (W, H), int(255 * fade)))
    return composed


def main():
    frames = []
    for i in range(FRAMES):
        frames.append(frame(i).resize((W, H), Image.LANCZOS))
        if i % 30 == 0:
            print("frame", i, "/", FRAMES, flush=True)

    frames[FRAMES - 45].save(os.path.join(OUT, "motion-poster.png"))
    frames[FRAMES - 45].save(os.path.join(OUT, "motion-poster.jpg"), quality=92)

    # GIF (optimised palette)
    step = 2
    gif_frames = [f.resize((720, 405), Image.LANCZOS).convert("P", palette=Image.ADAPTIVE, colors=128)
                  for f in frames[::step]]
    gif_frames[0].save(os.path.join(OUT, "motion-logo.gif"), save_all=True,
                       append_images=gif_frames[1:], duration=int(1000 * step / FPS),
                       loop=0, optimize=True, disposal=2)

    # MP4 via bundled ffmpeg
    import imageio_ffmpeg
    ffmpeg = imageio_ffmpeg.get_ffmpeg_exe()
    tmp = os.path.join(OUT, "_frames")
    os.makedirs(tmp, exist_ok=True)
    for i, f in enumerate(frames):
        f.save(os.path.join(tmp, f"f{i:04d}.png"))
    mp4 = os.path.join(OUT, "motion-logo.mp4")
    cmd = [ffmpeg, "-y", "-framerate", str(FPS), "-i", os.path.join(tmp, "f%04d.png"),
           "-c:v", "libx264", "-pix_fmt", "yuv420p", "-crf", "20", "-preset", "medium",
           "-movflags", "+faststart", mp4]
    subprocess.run(cmd, check=True, capture_output=True)
    webm = os.path.join(OUT, "motion-logo.webm")
    subprocess.run([ffmpeg, "-y", "-framerate", str(FPS), "-i", os.path.join(tmp, "f%04d.png"),
                    "-c:v", "libvpx-vp9", "-b:v", "0", "-crf", "38", "-row-mt", "1", webm],
                   check=False, capture_output=True)
    subprocess.run(["rm", "-rf", tmp], check=False)
    print("done")


if __name__ == "__main__":
    main()
