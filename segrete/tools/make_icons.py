#!/usr/bin/env python3
"""Genera le icone Android dallo sprite di Mattia definito in web/js/gfx.js."""
import json, os, re, sys
from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
src = open(os.path.join(ROOT, 'web', 'js', 'gfx.js'), encoding='utf-8').read()
pal = dict(re.findall(r"\b([a-zA-Z]): '(#[0-9a-fA-F]{6})'", src.split('const IPAL')[0]))
def grid(name):
    block = re.search(name + r': \[(.*?)\n  \]', src, re.S).group(1)
    return re.findall(r'"([^"]+)"', block)
head = grid('headD')
body = re.search(r'bodyD: \[\s*\[(.*?)\]', src, re.S).group(1)
body = re.findall(r'"([^"]+)"', body)

def draw(g, img, ox, oy):
    for y, row in enumerate(g):
        for x, ch in enumerate(row):
            if ch != '.' and ch in pal:
                img.putpixel((ox + x, oy + y), tuple(int(pal[ch][i:i + 2], 16) for i in (1, 3, 5)) + (255,))

sprite = Image.new('RGBA', (18, 27), (0, 0, 0, 0))
draw(body, sprite, 2, 17)
draw(head, sprite, 0, 0)
BG = (42, 30, 36, 255)

def legacy(size):
    s = 8
    im = Image.new('RGBA', (32 * s, 32 * s), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.rounded_rectangle((0, 0, 32 * s - 1, 32 * s - 1), radius=7 * s, fill=BG)
    big = sprite.resize((18 * s, 27 * s), Image.NEAREST)
    im.alpha_composite(big, ((32 * s - 18 * s) // 2, 3 * s))
    return im.resize((size, size), Image.LANCZOS)

def foreground(size):
    # 108dp con zona sicura di 66dp al centro
    s = 6
    im = Image.new('RGBA', (54 * s, 54 * s), (0, 0, 0, 0))
    big = sprite.resize((18 * s * 1, 27 * s * 1), Image.NEAREST)
    im.alpha_composite(big, ((54 * s - big.width) // 2, (54 * s - big.height) // 2))
    return im.resize((size, size), Image.NEAREST if size % 54 == 0 else Image.LANCZOS)

out = os.path.join(ROOT, 'android', 'res')
for dpi, mul in [('mdpi', 1), ('hdpi', 1.5), ('xhdpi', 2), ('xxhdpi', 3), ('xxxhdpi', 4)]:
    d = os.path.join(out, 'mipmap-' + dpi)
    os.makedirs(d, exist_ok=True)
    legacy(int(48 * mul)).save(os.path.join(d, 'ic_launcher.png'))
    foreground(int(108 * mul)).save(os.path.join(d, 'ic_launcher_fg.png'))
legacy(512).save(os.path.join(ROOT, 'icona.png'))
print('icone generate')
