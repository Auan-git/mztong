"""Generate 民职通 launcher icon foreground PNGs.

Brand: deep blue background (#1F4E79) + white 民职 glyph + gold (#C9A227) accent bar.
Adaptive icon canvas is 108dp; content is kept inside the 72dp safe zone.
"""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app" / "src" / "main" / "res"
FONT = "C:/Windows/Fonts/msyhbd.ttc"
WHITE = (255, 255, 255, 255)
GOLD = (201, 162, 39, 255)

DENSITIES = {
    "mipmap-mdpi": 108,
    "mipmap-hdpi": 162,
    "mipmap-xhdpi": 216,
    "mipmap-xxhdpi": 324,
    "mipmap-xxxhdpi": 432,
}


def render(size: int, out: Path) -> None:
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    text = "民职"
    target_width = size * 0.58
    font_size = int(target_width / 2.05)
    font = ImageFont.truetype(FONT, font_size)

    left, top, right, bottom = draw.textbbox((0, 0), text, font=font)
    text_w, text_h = right - left, bottom - top
    text_x = (size - text_w) / 2 - left
    text_y = size * 0.44 - text_h / 2 - top
    draw.text((text_x, text_y), text, font=font, fill=WHITE)

    bar_w = size * 0.34
    bar_h = max(2, int(size * 0.028))
    bar_x = (size - bar_w) / 2
    bar_y = size * 0.70
    draw.rounded_rectangle(
        [bar_x, bar_y, bar_x + bar_w, bar_y + bar_h],
        radius=bar_h / 2,
        fill=GOLD,
    )

    out.parent.mkdir(parents=True, exist_ok=True)
    img.save(out, "PNG", optimize=True)
    print(f"{out.relative_to(ROOT)}  {size}x{size}  font={font_size}px")


def main() -> None:
    for folder, size in DENSITIES.items():
        render(size, RES / folder / "ic_launcher_foreground.png")


if __name__ == "__main__":
    main()