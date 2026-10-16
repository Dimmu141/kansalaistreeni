"""Generates the launcher icon and the Play Store icon from one geometry.

Run: python scripts/generate-icon.py
Writes the adaptive-icon vector drawables under app/src/main/res and play/icon-512.png.
Pure Python (no Pillow): polygons are rasterised by scanline with 4x4 supersampling.
"""
import math
import struct
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / 'app/src/main/res'

BACKGROUND = '#164E63'  # the app's primary teal
PAGE = '#FFFFFF'
CHECK = '#2E9E6E'

# 108 x 108 adaptive-icon canvas; everything stays inside the 66 dp safe zone.
LEFT_PAGE = 'M29,38 Q41,33 53,39 L53,74 Q41,68 29,73 Z'
RIGHT_PAGE = 'M55,39 Q67,33 79,38 L79,73 Q67,68 55,74 Z'
CHECK_POINTS = [(38, 56), (49, 65), (71, 44)]
CHECK_WIDTH = 7


def vector(fill_page, fill_check):
    check = 'M' + ' L'.join(f'{x},{y}' for x, y in CHECK_POINTS)
    return f'''<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">
    <path android:fillColor="{fill_page}" android:pathData="{LEFT_PAGE}" />
    <path android:fillColor="{fill_page}" android:pathData="{RIGHT_PAGE}" />
    <path android:strokeColor="{fill_check}" android:strokeWidth="{CHECK_WIDTH}" android:strokeLineCap="round"
        android:strokeLineJoin="round" android:fillColor="#00000000" android:pathData="{check}" />
</vector>
'''


ADAPTIVE = '''<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />
</adaptive-icon>
'''


def write_resources():
    (RES / 'drawable/ic_launcher_foreground.xml').write_text(vector(PAGE, CHECK), encoding='utf-8')
    # Themed icons draw the monochrome layer in one colour; the check is cut out so it stays visible.
    (RES / 'drawable/ic_launcher_monochrome.xml').write_text(vector('#FFFFFFFF', '#00000000'), encoding='utf-8')
    anydpi = RES / 'mipmap-anydpi-v26'
    anydpi.mkdir(exist_ok=True)
    for name in ('ic_launcher.xml', 'ic_launcher_round.xml'):
        (anydpi / name).write_text(ADAPTIVE, encoding='utf-8')
    (RES / 'values/ic_launcher_colors.xml').write_text(
        '<?xml version="1.0" encoding="utf-8"?>\n<resources>\n'
        f'    <color name="ic_launcher_background">{BACKGROUND}</color>\n</resources>\n', encoding='utf-8')


def parse_path(data):
    """Flattens an M/L/Q/Z path into a polygon."""
    tokens = data.replace(',', ' ').replace('M', ' M ').replace('L', ' L ').replace('Q', ' Q ').replace('Z', ' Z ').split()
    points, i, current = [], 0, (0.0, 0.0)
    while i < len(tokens):
        command = tokens[i]; i += 1
        if command in 'ML':
            current = (float(tokens[i]), float(tokens[i + 1])); i += 2
            points.append(current)
        elif command == 'Q':
            cx, cy, x, y = map(float, tokens[i:i + 4]); i += 4
            x0, y0 = current
            for step in range(1, 25):
                t = step / 24
                points.append(((1 - t) ** 2 * x0 + 2 * (1 - t) * t * cx + t * t * x,
                               (1 - t) ** 2 * y0 + 2 * (1 - t) * t * cy + t * t * y))
            current = (x, y)
    return points


def stroke_polygons(points, width):
    half, shapes = width / 2, []
    for (x0, y0), (x1, y1) in zip(points, points[1:]):
        length = math.hypot(x1 - x0, y1 - y0)
        nx, ny = -(y1 - y0) / length * half, (x1 - x0) / length * half
        shapes.append([(x0 + nx, y0 + ny), (x1 + nx, y1 + ny), (x1 - nx, y1 - ny), (x0 - nx, y0 - ny)])
    for x, y in points:  # round caps and joins
        shapes.append([(x + half * math.cos(a * math.pi / 24), y + half * math.sin(a * math.pi / 24)) for a in range(48)])
    return shapes


def coverage(shapes, size, transform, samples=4):
    """Fraction of each pixel covered by the union of the polygons."""
    width = size * samples
    grid = bytearray(width * width)
    ones = b'\x01' * width
    for shape in shapes:
        poly = [transform(x, y) for x, y in shape]
        poly = [(x * samples, y * samples) for x, y in poly]
        ys = [y for _, y in poly]
        for row in range(max(0, int(min(ys))), min(width, int(max(ys)) + 1)):
            sy = row + 0.5
            xs = []
            for (x0, y0), (x1, y1) in zip(poly, poly[1:] + poly[:1]):
                if (y0 <= sy < y1) or (y1 <= sy < y0):
                    xs.append(x0 + (sy - y0) * (x1 - x0) / (y1 - y0))
            xs.sort()
            for a, b in zip(xs[::2], xs[1::2]):
                start, end = max(0, int(round(a))), min(width, int(round(b)))
                if end > start:
                    grid[row * width + start:row * width + end] = ones[:end - start]
    result = []
    for y in range(size):
        row = [0] * size
        for sub in range(samples):
            base = (y * samples + sub) * width
            line = grid[base:base + width]
            for x in range(size):
                row[x] += sum(line[x * samples:(x + 1) * samples])
        result.append([value / (samples * samples) for value in row])
    return result


def rgb(color):
    return tuple(int(color[i:i + 2], 16) for i in (1, 3, 5))


def write_png(path, size=512, zoom=1.25, width=None):
    """A square icon, or with [width] a wider canvas (the feature graphic) with the mark centred."""
    width = width or size
    side = max(width, size)
    scale = size / 108 * zoom
    transform = lambda x, y: (width / 2 + (x - 54) * scale, size / 2 + (y - 54) * scale)
    pages = coverage([parse_path(LEFT_PAGE), parse_path(RIGHT_PAGE)], side, transform)
    check = coverage(stroke_polygons(CHECK_POINTS, CHECK_WIDTH), side, transform)
    background, page, mark = rgb(BACKGROUND), rgb(PAGE), rgb(CHECK)
    raw = bytearray()
    for y in range(size):
        raw.append(0)
        for x in range(width):
            color = background
            for layer, (alpha) in ((page, pages[y][x]), (mark, check[y][x])):
                color = tuple(round(c * (1 - alpha) + l * alpha) for c, l in zip(color, layer))
            raw.extend(color)

    def chunk(kind, data):
        return struct.pack('>I', len(data)) + kind + data + struct.pack('>I', zlib.crc32(kind + data) & 0xFFFFFFFF)
    png = b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', width, size, 8, 2, 0, 0, 0))
    png += chunk(b'IDAT', zlib.compress(bytes(raw), 9)) + chunk(b'IEND', b'')
    path.parent.mkdir(exist_ok=True)
    path.write_bytes(png)


if __name__ == '__main__':
    write_resources()
    write_png(ROOT / 'play/icon-512.png')
    write_png(ROOT / 'play/feature-graphic.png', size=500, zoom=1.1, width=1024)
    print('Wrote launcher icon resources, play/icon-512.png and play/feature-graphic.png')
