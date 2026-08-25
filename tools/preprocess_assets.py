"""Convert WebP assets, slice composite sheets, generate adaptive icon."""
from __future__ import annotations

import shutil
from collections import deque
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "assets"
OUT = ROOT / "android" / "assets"
ICON_RES = ROOT / "android" / "res"

# RGB backgrounds used full-frame (do not slice into objects).
FULL_BACKGROUNDS = {
    "olympus_background_asset",
    "sky_islands_background_asset",
    "storm_sky_background_asset",
    "zeus_temple_background_asset",
    "olympus_marble_arena_asset",
    "sky_platform_asset",
    "Horizontal_Loading_Screen",
    "Vertical_Loading_Screen",
    "Game_Name",
    "example_gameplay_1",
    "Icon",
}

GEM_SET_1 = ["sapphire", "ruby", "emerald", "amethyst"]
GEM_SET_2 = ["diamond", "zeus_bolt", "poseidon_wave", "hades_shard"]
ENEMY_SET_1 = ["hoplite", "minotaur", "harpy", "spider"]
ENEMY_SET_2 = ["eagle", "golem", "storm_wolf", "spearman"]
ELITE_SET = ["elite_spear", "elite_winged", "elite_crystal", "elite_trident"]
BOSS_SET = ["titan_earth", "titan_sea", "titan_void", "titan_storm"]


def ensure_dir(p: Path) -> None:
    p.mkdir(parents=True, exist_ok=True)


def extract_blobs(im: Image.Image, min_pixels: int = 400) -> list[Image.Image]:
    """Connected-component crop of non-transparent, non-near-black pixels."""
    rgba = im.convert("RGBA")
    w, h = rgba.size
    px = rgba.load()
    visited = bytearray(w * h)
    blobs: list[tuple[int, int, int, int]] = []

    def idx(x: int, y: int) -> int:
        return y * w + x

    def is_solid(x: int, y: int) -> bool:
        r, g, b, a = px[x, y]
        if a < 24:
            return False
        # treat near-black filler as empty
        if r < 12 and g < 12 and b < 12 and a < 250:
            return False
        if r < 8 and g < 8 and b < 8:
            return False
        return True

    for y in range(h):
        for x in range(w):
            if visited[idx(x, y)]:
                continue
            if not is_solid(x, y):
                visited[idx(x, y)] = 1
                continue
            q = deque([(x, y)])
            visited[idx(x, y)] = 1
            minx = maxx = x
            miny = maxy = y
            count = 0
            while q:
                cx, cy = q.popleft()
                count += 1
                if cx < minx:
                    minx = cx
                if cx > maxx:
                    maxx = cx
                if cy < miny:
                    miny = cy
                if cy > maxy:
                    maxy = cy
                for nx, ny in ((cx - 1, cy), (cx + 1, cy), (cx, cy - 1), (cx, cy + 1)):
                    if nx < 0 or ny < 0 or nx >= w or ny >= h:
                        continue
                    i = idx(nx, ny)
                    if visited[i]:
                        continue
                    if is_solid(nx, ny):
                        visited[i] = 1
                        q.append((nx, ny))
                    else:
                        visited[i] = 1
            if count >= min_pixels:
                pad = 4
                minx = max(0, minx - pad)
                miny = max(0, miny - pad)
                maxx = min(w - 1, maxx + pad)
                maxy = min(h - 1, maxy + pad)
                blobs.append((minx, miny, maxx + 1, maxy + 1))

    blobs.sort(key=lambda b: (b[1] // 80, b[0]))
    crops = []
    for box in blobs:
        crop = rgba.crop(box)
        # drop leftover black
        cpx = crop.load()
        cw, ch = crop.size
        for yy in range(ch):
            for xx in range(cw):
                r, g, b, a = cpx[xx, yy]
                if r < 10 and g < 10 and b < 10:
                    cpx[xx, yy] = (0, 0, 0, 0)
        crops.append(crop)
    return crops


def save_named(crops: list[Image.Image], names: list[str], dest: Path) -> None:
    ensure_dir(dest)
    for i, crop in enumerate(crops):
        name = names[i] if i < len(names) else f"extra_{i}"
        crop.save(dest / f"{name}.png")
        print(f"  slice {name}.png {crop.size}")


def convert_full(src: Path, dest: Path) -> None:
    ensure_dir(dest.parent)
    Image.open(src).convert("RGBA").save(dest)
    print(f"copy {dest.name} {Image.open(dest).size}")


def adaptive_icon() -> None:
    icon = Image.open(SRC / "Icon.png").convert("RGBA")
    # 108dp canvas equivalent at 1024px; safe zone ~66%
    size = 1024
    fg = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    inner = int(size * 0.62)
    scaled = icon.resize((inner, inner), Image.Resampling.LANCZOS)
    off = (size - inner) // 2
    fg.paste(scaled, (off, off), scaled)
    bg = Image.new("RGB", (size, size), (18, 24, 48))
    draw = ImageDraw.Draw(bg)
    draw.ellipse((80, 80, size - 80, size - 80), fill=(42, 32, 12))

    mip = ICON_RES / "mipmap-xxxhdpi"
    anydpi = ICON_RES / "mipmap-anydpi-v26"
    ensure_dir(mip)
    ensure_dir(anydpi)
    fg.save(mip / "ic_launcher_foreground.png")
    bg.save(mip / "ic_launcher_background.png")
    # fallback round/legacy
    legacy = Image.new("RGBA", (size, size), (18, 24, 48, 255))
    legacy.paste(scaled, (off, off), scaled)
    legacy.save(mip / "ic_launcher.png")
    legacy.save(mip / "ic_launcher_round.png")
    (anydpi / "ic_launcher.xml").write_text(
        """<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@mipmap/ic_launcher_background"/>
    <foreground android:drawable="@mipmap/ic_launcher_foreground"/>
</adaptive-icon>
""",
        encoding="utf-8",
    )
    (anydpi / "ic_launcher_round.xml").write_text(
        (anydpi / "ic_launcher.xml").read_text(encoding="utf-8"), encoding="utf-8"
    )
    print("adaptive icon written")


def main() -> None:
    if OUT.exists():
        shutil.rmtree(OUT)
    ensure_dir(OUT)
    ensure_dir(OUT / "sprites")
    ensure_dir(OUT / "bg")
    ensure_dir(OUT / "ui")
    ensure_dir(OUT / "audio")

    named_sets = {
        "combat_gems_set_1_asset": (GEM_SET_1, "sprites"),
        "combat_gems_set_2_asset": (GEM_SET_2, "sprites"),
        "common_enemies_set_1_asset": (ENEMY_SET_1, "sprites"),
        "common_enemies_set_2_asset": (ENEMY_SET_2, "sprites"),
        "elite_enemies_set_asset": (ELITE_SET, "sprites"),
        "titan_bosses_set_asset": (BOSS_SET, "sprites"),
    }

    for f in sorted(SRC.iterdir()):
        if f.suffix.lower() == ".mp3":
            shutil.copy2(f, OUT / "audio" / f.name)
            print(f"audio {f.name}")
            continue
        if f.suffix.lower() not in {".webp", ".png"}:
            continue
        stem = f.stem
        if stem in FULL_BACKGROUNDS or stem in {"Horizontal_Loading_Screen", "Vertical_Loading_Screen"}:
            folder = "ui" if stem in {"Horizontal_Loading_Screen", "Vertical_Loading_Screen", "Game_Name"} else "bg"
            if stem == "example_gameplay_1":
                continue
            if stem == "Icon":
                continue
            convert_full(f, OUT / folder / f"{stem}.png")
            continue
        im = Image.open(f)
        crops = extract_blobs(im)
        print(f"{f.name}: {len(crops)} blobs")
        if stem in named_sets:
            names, folder = named_sets[stem]
            save_named(crops, names, OUT / folder)
        elif stem == "young_olympian_god_hero_asset":
            save_named(crops[:1] if crops else [im.convert("RGBA")], ["hero"], OUT / "sprites")
        else:
            # environment props
            names = [f"{stem}_{i}" for i in range(len(crops))]
            if not crops:
                convert_full(f, OUT / "sprites" / f"{stem}.png")
            else:
                save_named(crops, names, OUT / "sprites")

    adaptive_icon()
    print("done")


if __name__ == "__main__":
    main()
