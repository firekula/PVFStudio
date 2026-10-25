#!/usr/bin/env python3
"""Package the generated master as native ICO and platform SVG-wrapped PNGs.

Requires Pillow. This changes size/format only and preserves the source design.
The desktop build consumes checked-in outputs, so Pillow is not required there.
"""
import base64
import io
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
SIZES = (16, 20, 24, 32, 40, 48, 64, 128, 256)


def svg_png(master, size, target):
    image = master.resize((size, size), Image.Resampling.LANCZOS)
    png = io.BytesIO()
    image.save(png, 'PNG', optimize=True)
    data = base64.b64encode(png.getvalue()).decode('ascii')
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(
        f'<svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" '
        f'width="{size}" height="{size}" viewBox="0 0 {size} {size}">\n'
        f'  <image width="{size}" height="{size}" xlink:href="data:image/png;base64,{data}"/>\n'
        '</svg>\n', encoding='utf-8')


def main():
    master = Image.open(ROOT / 'assets/branding/pvf-studio-icon.png').convert('RGBA')
    if master.width != master.height or master.width < 512:
        raise ValueError('The icon master must be square and at least 512px')
    if master.getextrema()[3] != (0, 255) or master.getpixel((0, 0))[3] != 0:
        raise ValueError('The icon master must retain its transparent outer corners')
    ico = ROOT / 'tools/windows/launcher/PVFStudio.ico'
    ico.parent.mkdir(parents=True, exist_ok=True)
    master.resize((256, 256), Image.Resampling.LANCZOS).save(ico, sizes=[(s, s) for s in SIZES])
    with Image.open(ico) as icon:
        if icon.ico.sizes() != {(s, s) for s in SIZES}:
            raise ValueError('ICO is missing one or more sizes')
    master.resize((256, 256), Image.Resampling.LANCZOS).save(ROOT / 'assets/branding/pvf-studio-icon-256.png', optimize=True)
    resources = ROOT / 'pvf-product/src/main/resources/icons/app'
    svg_png(master, 512, resources / 'pvf-studio.svg')
    svg_png(master, 64, resources / 'pvf-studio-small.svg')
    print(f'Packaged icon: {master.width}px master; ICO {", ".join(map(str, SIZES))}px; platform 512/64px')


if __name__ == '__main__':
    main()
