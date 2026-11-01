"""Frames raw 1080x2160 captures into Play phone screenshots (1080x2160) with a caption."""
import os, sys
from PIL import Image, ImageDraw, ImageFilter, ImageFont

FONT, SHOTS, OUT = sys.argv[1], sys.argv[2], sys.argv[3]
W, H = 1080, 2160
BRAND, BIRCH, SOFT = (22, 78, 99), (227, 179, 65), (220, 237, 243)

SLIDES = [
    ("raw-home", "Opi Suomen yhteiskunta", "muutama minuutti päivässä"),
    ("raw-path", "Etene aihepolkua", "8 aihetta demokratiasta turvallisuuteen"),
    ("raw-explain", "Selitys jokaiseen vastaukseen", "myös helpolla suomella"),
    ("raw-results", "Näe, miten edistyt", "tarkkuus, putki ja oma viikko"),
    ("raw-exam", "Testaa itsesi", "harjoituskoe, valinnainen aikaraja"),
    ("raw-translate", "Apua omalla kielelläsi", "selitykset kahdeksalla tukikielellä"),
]


def font(size, weight):
    f = ImageFont.truetype(FONT, size)
    f.set_variation_by_name(weight)
    return f


def fit(draw, text, weight, size, width):
    while draw.textlength(text, font=font(size, weight)) > width:
        size -= 2
    return font(size, weight)


os.makedirs(OUT, exist_ok=True)
for i, (raw, title, sub) in enumerate(SLIDES, 1):
    img = Image.new("RGB", (W, H), BRAND)
    d = ImageDraw.Draw(img)
    # A soft lighter glow behind the phone.
    glow = Image.new("L", (W, H), 0)
    ImageDraw.Draw(glow).ellipse((-200, 700, W + 200, H + 600), fill=60)
    img.paste(Image.new("RGB", (W, H), (40, 104, 126)), mask=glow.filter(ImageFilter.GaussianBlur(120)))
    d = ImageDraw.Draw(img)
    tf = fit(d, title, b"ExtraBold", 84, W - 120)
    d.text((W // 2, 150), title, font=tf, fill="white", anchor="mm")
    sf = fit(d, sub, b"SemiBold", 46, W - 160)
    d.text((W // 2, 245), sub, font=sf, fill=SOFT, anchor="mm")
    d.rounded_rectangle((W // 2 - 40, 300, W // 2 + 40, 310), 5, fill=BIRCH)

    # The screen, scaled, with rounded corners, a dark bezel and a shadow; it runs off the bottom edge.
    shot = Image.open(os.path.join(SHOTS, raw + ".png")).convert("RGB")
    sw = 840
    sh = int(shot.height * sw / shot.width)
    shot = shot.resize((sw, sh), Image.LANCZOS)
    x, y, r, bez = (W - sw) // 2, 390, 56, 14
    shadow = Image.new("L", (W, H), 0)
    ImageDraw.Draw(shadow).rounded_rectangle((x - bez, y - bez + 24, x + sw + bez, y + sh + bez + 24), r + bez, fill=150)
    img.paste((8, 34, 44), mask=shadow.filter(ImageFilter.GaussianBlur(30)))
    d = ImageDraw.Draw(img)
    d.rounded_rectangle((x - bez, y - bez, x + sw + bez, y + sh + bez), r + bez, fill=(15, 23, 28))
    mask = Image.new("L", (sw, sh), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, sw, sh), r, fill=255)
    img.paste(shot, (x, y), mask)
    img.save(os.path.join(OUT, f"{i}-{raw[4:]}-fi.png"), optimize=True)
    print("wrote", i, raw)
