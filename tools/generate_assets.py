#!/usr/bin/env python3
"""Generates every texture, sound, model, recipe, loot table, job and translation of CapitalCraft.
Run from the repo root:  python3 tools/generate_assets.py   (needs pillow, numpy, scipy, ffmpeg)"""
import json, math, os, subprocess, tempfile
import numpy as np
from PIL import Image, ImageDraw, ImageFont
from scipy.io import wavfile

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources")
A = os.path.join(ROOT, "assets", "capitalcraft")
D = os.path.join(ROOT, "data", "capitalcraft")

def w(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        if isinstance(data, str): f.write(data)
        else: json.dump(data, f, ensure_ascii=False, indent=2)

# ------------------------------------------------------------------ textures
def chip():
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0)); px = im.load(); c = 7.5
    dollar = {(7, 4), (6, 5), (7, 5), (8, 5), (6, 6), (7, 7), (8, 8), (6, 9), (7, 9), (8, 9), (7, 10), (7, 6), (7, 8)}
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - c, y - c)
            if d > 7.6: continue
            if d > 6.6: col = (74, 10, 18, 255)
            elif d > 4.7:
                a = (math.atan2(y - c, x - c) + math.pi) / (math.pi / 4)
                col = (245, 245, 240, 255) if (a % 1) < 0.42 else (200, 32, 44, 255)
            elif d > 3.9: col = (160, 112, 24, 255)
            else: col = (246, 196, 58, 255)
            px[x, y] = col
    for p in dollar:
        if math.hypot(p[0] - c, p[1] - c) <= 4: px[p] = (96, 58, 8, 255)
    im.save(f"{A}/textures/item/casino_chip.png")

def terminal():
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0)); d = ImageDraw.Draw(im)
    d.rectangle([2, 1, 13, 14], fill=(40, 46, 62, 255), outline=(18, 20, 28, 255))
    d.rectangle([3, 2, 12, 2], fill=(86, 96, 120, 255))
    d.rectangle([4, 3, 11, 9], fill=(8, 16, 24, 255), outline=(60, 70, 90, 255))
    pts = [(5, 8), (6, 6), (7, 7), (8, 5), (9, 6), (10, 4)]
    for a, b in zip(pts, pts[1:]): d.line([a, b], fill=(74, 222, 128, 255))
    d.point((5, 5), fill=(239, 83, 80, 255)); d.point((5, 6), fill=(239, 83, 80, 255))
    for i, col in enumerate([(233, 185, 73), (74, 222, 128), (239, 83, 80)]):
        d.rectangle([4 + i * 3, 11, 5 + i * 3, 12], fill=col + (255,))
    im.save(f"{A}/textures/item/stock_terminal.png")

TIERS = {"gpu_basic": ((36, 120, 70), (120, 230, 160)),
         "gpu_advanced": ((40, 92, 176), (110, 190, 255)),
         "gpu_elite": ((112, 52, 176), (255, 204, 84))}

def gpu_front(name, pcb, acc, on):
    im = Image.new("RGBA", (16, 16), (42, 44, 54, 255)); d = ImageDraw.Draw(im)
    d.rectangle([0, 0, 15, 15], outline=(18, 20, 26, 255))
    led = acc + (255,) if on else (24, 28, 36, 255)
    d.rectangle([1, 1, 14, 2], fill=led)
    for cx in (4.5, 11.5):
        for y in range(16):
            for x in range(16):
                dist = math.hypot(x - cx, y - 7.5)
                if dist <= 3.6:
                    im.putpixel((x, y), (12, 13, 18, 255))
        blade = acc + (255,) if on else (66, 72, 88, 255)
        for dx, dy in [(0, -2), (0, 2), (-2, 0), (2, 0)]:
            im.putpixel((int(cx - .5) + dx + (1 if cx > 8 else 0) - (1 if cx > 8 else 0), 7 + dy), blade)
        im.putpixel((int(cx), 7), (200, 170, 70, 255)); im.putpixel((int(cx), 8), (200, 170, 70, 255))
    d.rectangle([1, 12, 14, 14], fill=pcb + (255,))
    for x in range(2, 14, 2): d.point((x, 14), fill=(222, 178, 70, 255))
    im.save(f"{A}/textures/block/{name}_front_{'on' if on else 'off'}.png")

def gpu_side(name, pcb, acc):
    im = Image.new("RGBA", (16, 16), (60, 64, 76, 255)); d = ImageDraw.Draw(im)
    for x in range(0, 16, 2): d.line([(x, 1), (x, 12)], fill=(112, 118, 130, 255))
    d.rectangle([0, 13, 15, 15], fill=pcb + (255,))
    d.rectangle([0, 0, 15, 0], fill=(30, 32, 40, 255))
    d.point((7, 14), fill=acc + (255,))
    im.save(f"{A}/textures/block/{name}_side.png")

def gpu_top(name, pcb, acc):
    im = Image.new("RGBA", (16, 16), (92, 98, 112, 255)); d = ImageDraw.Draw(im)
    d.rectangle([0, 0, 15, 15], outline=(30, 32, 40, 255))
    for y in (3, 12): d.line([(2, y), (13, y)], fill=(70, 74, 88, 255))
    d.rectangle([5, 5, 10, 10], fill=pcb + (255,), outline=acc + (255,))
    d.rectangle([7, 7, 8, 8], fill=acc + (255,))
    im.save(f"{A}/textures/block/{name}_top.png")

def hex_tile():
    s, tw, th = 10, 30, 16
    big = Image.new("RGBA", (tw * 5, th * 5), (0, 0, 0, 0)); d = ImageDraw.Draw(big)
    col = (92, 76, 44, 255)
    for j in range(-1, 7):
        for i in range(-1, 7):
            for ox, oy in ((0, 0), (15, 8)):
                cx, cy = tw * i + ox, th * j + oy
                v = [(cx + 10, cy), (cx + 5, cy + 8), (cx - 5, cy + 8), (cx - 10, cy), (cx - 5, cy - 8), (cx + 5, cy - 8)]
                for a, b in zip(v, v[1:] + v[:1]): d.line([a, b], fill=col)
    big.crop((tw * 2, th * 2, tw * 3, th * 3)).save(f"{A}/textures/gui/hex.png")

def icon():
    im = Image.new("RGBA", (128, 128), (14, 18, 26, 255)); d = ImageDraw.Draw(im)
    d.rounded_rectangle([4, 4, 123, 123], radius=18, outline=(58, 69, 96, 255), width=3)
    d.ellipse([22, 22, 106, 106], fill=(160, 112, 24, 255)); d.ellipse([28, 28, 100, 100], fill=(246, 196, 58, 255))
    d.ellipse([38, 38, 90, 90], outline=(190, 140, 30, 255), width=3)
    try: f = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 52)
    except Exception: f = ImageFont.load_default()
    d.text((64, 62), "$", font=f, fill=(96, 58, 8, 255), anchor="mm")
    im.save(f"{A}/icon.png")

# ------------------------------------------------------------------ sounds
SR = 44100
def T(d): return np.linspace(0, d, int(SR * d), endpoint=False)
def fade(x, a=0.004, r=0.02):
    n = len(x); na, nr = max(1, int(SR * a)), max(1, int(SR * r))
    x = x.copy(); x[:na] *= np.linspace(0, 1, na); x[-nr:] *= np.linspace(1, 0, nr); return x
def bell(f, d, decay, amp=1.0):
    t = T(d); x = sum(a * np.sin(2 * np.pi * f * m * t) * np.exp(-t * decay * (1 + .4 * i)) for i, (m, a) in enumerate([(1, 1), (2.76, .5), (5.4, .25), (8.9, .1)]))
    return fade(x * amp * .5)
def seq(parts, total):
    out = np.zeros(int(SR * total))
    for start, snd in parts:
        i = int(SR * start); out[i:i + len(snd)] += snd[:max(0, len(out) - i)]
    return out
def sweep(f0, f1, d, wave="sine"):
    t = T(d); f = np.linspace(f0, f1, len(t)); ph = 2 * np.pi * np.cumsum(f) / SR
    return np.sin(ph) if wave == "sine" else (np.sign(np.sin(ph)) if wave == "square" else 2 * ((ph / (2 * np.pi)) % 1) - 1)
rng = np.random.default_rng(7)
def noise(d): return rng.uniform(-1, 1, int(SR * d))
def lowpass(x, k): return np.convolve(x, np.ones(k) / k, mode="same")

def make_sounds():
    S = {}
    t = T(.06); S["ui_click"] = fade(np.sin(2 * np.pi * 1800 * t) * np.exp(-t * 90) + .3 * noise(.06) * np.exp(-t * 120))
    t = T(.035); S["spin_tick"] = fade((np.sin(2 * np.pi * 1250 * t) + .5 * np.sin(2 * np.pi * 2500 * t)) * np.exp(-t * 130))
    t = T(.6); S["spin_start"] = fade(sweep(180, 900, .6, "saw") * .35 * (0.7 + .3 * np.sin(2 * np.pi * 18 * t)) * np.linspace(.4, 1, len(t)), .01, .1)
    notes = [523.25, 659.25, 783.99, 1046.5]
    S["win"] = seq([(i * .11, bell(f, .8, 5)) for i, f in enumerate(notes)] + [(.5, bell(523.25, 1.0, 3) + bell(659.25, 1.0, 3) + bell(783.99, 1.0, 3))], 1.6)
    jp = [(i * .07, bell(f * m, .7, 5, .8)) for m in (1, 2) for i, f in enumerate(notes + notes[::-1])]
    jp += [(.9 + i * .09, bell(1568 + (i % 3) * 300, .35, 9, .6)) for i in range(10)]
    jp += [(.55, bell(1046.5, 1.6, 2.5) + bell(1318.5, 1.6, 2.5) + bell(1568, 1.6, 2.5))]
    S["jackpot"] = seq(jp, 2.4)
    lose = []
    for i, f in enumerate([392, 330, 262, 196]):
        t = T(.28); lose.append((i * .24, fade(sweep(f * 1.02, f * .96, .28, "saw") * .35 * np.exp(-t * 3), .01, .08)))
    S["lose"] = seq(lose, 1.3)
    S["coin"] = seq([(0, bell(1568, .4, 10)), (.07, bell(2093, .5, 9))], .6)
    n = lowpass(noise(.08), 4) * np.exp(-T(.08) * 50)
    S["cash"] = seq([(0, fade(n * .9)), (.05, bell(2637, .9, 5)), (.13, bell(3136, .7, 6, .7)), (.0, bell(180, .15, 30, 1.2))], 1.0)
    t = T(1.4); hum = np.sin(2 * np.pi * 92 * t) * .5 + np.sin(2 * np.pi * 184 * t) * .25 + lowpass(noise(1.4), 12) * .5
    S["gpu_hum"] = fade(hum * .5, .15, .15)
    S["shift_start"] = seq([(0, bell(330, 1.6, 2.2)), (.55, bell(330, 1.4, 2.2) + bell(495, 1.4, 2.5, .6))], 2.1)
    t = T(1.1); S["shift_end"] = fade((sweep(720, 320, 1.1, "saw") * .3 + lowpass(noise(1.1), 6) * .15) * np.exp(-t * 1.3), .02, .2)
    S["trade_buy"] = seq([(0, bell(660, .25, 12)), (.07, bell(880, .35, 10))], .45)
    S["trade_sell"] = seq([(0, bell(880, .25, 12)), (.07, bell(660, .35, 10))], .45)
    t = T(.28); S["error"] = fade(sweep(150, 130, .28, "square") * .3 * np.exp(-t * 4), .005, .05)
    S["alert"] = seq([(i * .16, fade(np.sin(2 * np.pi * 1000 * T(.09)) * .5, .005, .02)) for i in range(3)], .6)
    os.makedirs(f"{A}/sounds", exist_ok=True)
    events = {}
    for name, x in S.items():
        x = x / max(1e-6, np.max(np.abs(x))) * 0.85
        tmp = os.path.join(tempfile.gettempdir(), name + ".wav")
        wavfile.write(tmp, SR, (x * 32767).astype(np.int16))
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", tmp, "-ac", "1", "-c:a", "libvorbis", "-q:a", "4", f"{A}/sounds/{name}.ogg"], check=True)
        events[name] = {"sounds": [f"capitalcraft:{name}"], "subtitle": f"subtitles.capitalcraft.{name}"}
    w(f"{A}/sounds.json", events)
    return list(S)

# ------------------------------------------------------------------ models, recipes, loot
def blocks_and_items():
    for name, (pcb, acc) in TIERS.items():
        gpu_front(name, pcb, acc, False); gpu_front(name, pcb, acc, True); gpu_side(name, pcb, acc); gpu_top(name, pcb, acc)
        for st in ("off", "on"):
            w(f"{A}/models/block/{name}_{st}.json", {"parent": "minecraft:block/orientable", "textures": {
                "top": f"capitalcraft:block/{name}_top", "front": f"capitalcraft:block/{name}_front_{st}", "side": f"capitalcraft:block/{name}_side"}})
        variants = {}
        for lit in ("false", "true"):
            for face, rot in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
                v = {"model": f"capitalcraft:block/{name}_{'on' if lit == 'true' else 'off'}"}
                if rot: v["y"] = rot
                variants[f"facing={face},lit={lit}"] = v
        w(f"{A}/blockstates/{name}.json", {"variants": variants})
        w(f"{A}/models/item/{name}.json", {"parent": f"capitalcraft:block/{name}_off"})
        w(f"{D}/loot_tables/blocks/{name}.json", {"type": "minecraft:block", "pools": [{"rolls": 1, "bonus_rolls": 0,
            "entries": [{"type": "minecraft:item", "name": f"capitalcraft:{name}"}], "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
    for it in ("casino_chip", "stock_terminal"):
        w(f"{A}/models/item/{it}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"capitalcraft:item/{it}"}})
    w(os.path.join(ROOT, "data", "minecraft", "tags", "blocks", "mineable", "pickaxe.json"),
      {"replace": False, "values": [f"capitalcraft:{n}" for n in TIERS]})

    def shaped(name, pattern, key, result):
        w(f"{D}/recipes/{name}.json", {"type": "minecraft:crafting_shaped", "category": "misc", "pattern": pattern,
            "key": {k: ({"item": v} if not v.startswith("#") else {"tag": v[1:]}) for k, v in key.items()}, "result": {"item": f"capitalcraft:{result}", "count": 1}})
    shaped("casino_chip", [" G ", "GRG", " G "], {"G": "minecraft:gold_ingot", "R": "minecraft:redstone"}, "casino_chip")
    shaped("stock_terminal", ["IGI", "RPR", "III"], {"I": "minecraft:iron_ingot", "G": "minecraft:gold_ingot", "R": "minecraft:redstone", "P": "minecraft:glass_pane"}, "stock_terminal")
    shaped("gpu_basic", ["III", "RGR", "ICI"], {"I": "minecraft:iron_ingot", "R": "minecraft:redstone", "G": "minecraft:gold_ingot", "C": "minecraft:copper_ingot"}, "gpu_basic")
    shaped("gpu_advanced", ["DGD", "RXR", "DGD"], {"D": "minecraft:diamond", "G": "minecraft:gold_ingot", "R": "minecraft:redstone_block", "X": "capitalcraft:gpu_basic"}, "gpu_advanced")
    shaped("gpu_elite", ["ENE", "DXD", "ENE"], {"E": "minecraft:emerald", "N": "minecraft:netherite_ingot", "D": "minecraft:diamond", "X": "capitalcraft:gpu_advanced"}, "gpu_elite")

# ------------------------------------------------------------------ jobs
HOSTILE = ["zombie", "skeleton", "spider", "creeper", "cave_spider", "witch", "drowned", "husk", "stray", "slime", "zombie_villager"]
FARM = ["cow", "pig", "sheep", "chicken", "rabbit"]
STONE = ["stone", "deepslate", "granite", "diorite", "andesite", "tuff", "cobblestone"]
DIRT = ["dirt", "grass_block", "coarse_dirt", "podzol", "rooted_dirt", "mycelium"]
BUILD = ["stone_bricks", "bricks", "cobblestone", "stone", "deepslate_bricks", "smooth_stone", "sandstone", "andesite"]
FOODS = ["cooked_beef", "cooked_porkchop", "cooked_chicken", "cooked_mutton", "cooked_rabbit", "cooked_cod", "cooked_salmon", "baked_potato", "bread"]
M, C, U, K, S, N = "mine", "craft", "use", "kill", "stat", "counter"
JOBS = []
def J(id, tier, wage, es, en, kit, objs, gpu=False):
    JOBS.append(dict(id=id, tier=tier, wage=wage, es=es, en=en, kit=kit, objs=objs, gpu=gpu))
def O(t, targets, n, es, en): return (t, targets, n, es, en)
PICK = [("iron_pickaxe", 1), ("torch", 16)]
SHOVEL = [("iron_shovel", 1)]; AXE = [("iron_axe", 1)]; HOE = [("iron_hoe", 1)]; SWORD = [("iron_sword", 1), ("shield", 1)]
FURN = [("furnace", 2), ("coal", 24)]

J("minero_carbon", 1, 1100, "Minero de carbón", "Coal Miner", PICK, [O(M, ["coal_ore", "deepslate_coal_ore"], 40, "Mena de carbón picada", "Coal ore mined")])
J("minero_cobre", 1, 1150, "Minero de cobre", "Copper Miner", PICK, [O(M, ["copper_ore", "deepslate_copper_ore"], 48, "Mena de cobre picada", "Copper ore mined")])
J("cantero", 1, 1000, "Cantero", "Quarryman", PICK, [O(M, STONE, 128, "Piedra extraída", "Stone quarried")])
J("excavador", 1, 900, "Excavador", "Excavator", SHOVEL, [O(M, DIRT, 160, "Tierra excavada", "Dirt dug")])
J("arenero", 1, 1000, "Arenero", "Sand Hauler", SHOVEL, [O(M, ["sand", "red_sand"], 96, "Arena recogida", "Sand collected"), O(M, ["gravel"], 64, "Grava recogida", "Gravel collected")])
J("arcillero", 1, 1100, "Arcillero", "Clay Digger", SHOVEL, [O(M, ["clay"], 48, "Bloques de arcilla", "Clay blocks")])
J("lenador", 1, 1000, "Leñador", "Lumberjack", AXE, [O(M, ["#minecraft:logs"], 48, "Troncos talados", "Logs felled")])
J("aserrador", 2, 1700, "Aserrador industrial", "Industrial Sawyer", AXE, [O(M, ["#minecraft:logs"], 128, "Troncos talados", "Logs felled")])
J("minero_hierro", 2, 1500, "Minero de hierro", "Iron Miner", PICK, [O(M, ["iron_ore", "deepslate_iron_ore"], 32, "Mena de hierro picada", "Iron ore mined")])
J("minero_redstone", 2, 1700, "Minero de redstone", "Redstone Miner", PICK, [O(M, ["redstone_ore", "deepslate_redstone_ore"], 40, "Mena de redstone picada", "Redstone ore mined")])
J("minero_lapis", 2, 1700, "Minero de lapislázuli", "Lapis Miner", PICK, [O(M, ["lapis_ore", "deepslate_lapis_ore"], 30, "Mena de lapislázuli picada", "Lapis ore mined")])
J("minero_oro", 2, 2200, "Minero de oro", "Gold Miner", PICK, [O(M, ["gold_ore", "deepslate_gold_ore", "nether_gold_ore"], 20, "Mena de oro picada", "Gold ore mined")])
J("minero_cuarzo", 2, 2300, "Minero del Nether", "Nether Miner", PICK, [O(M, ["nether_quartz_ore"], 24, "Cuarzo extraído", "Quartz mined")])
J("minero_diamante", 3, 3600, "Minero de diamantes", "Diamond Miner", PICK, [O(M, ["diamond_ore", "deepslate_diamond_ore"], 8, "Diamantes picados", "Diamond ore mined")])
J("buscador_antiguo", 3, 4500, "Buscador de escombros antiguos", "Ancient Debris Hunter", [("diamond_pickaxe", 1), ("torch", 16)], [O(M, ["ancient_debris"], 2, "Escombros antiguos", "Ancient debris")])
J("granjero_trigo", 1, 1100, "Granjero de trigo", "Wheat Farmer", HOE + [("wheat_seeds", 40)], [O(U, ["wheat_seeds"], 32, "Semillas plantadas", "Seeds planted"), O(M, ["wheat"], 48, "Trigo cosechado", "Wheat harvested")])
J("granjero_zanahoria", 1, 1100, "Granjero de zanahorias", "Carrot Farmer", HOE + [("carrot", 36)], [O(U, ["carrot"], 32, "Zanahorias plantadas", "Carrots planted"), O(M, ["carrots"], 48, "Zanahorias cosechadas", "Carrots harvested")])
J("patatero", 1, 1100, "Patatero", "Potato Farmer", HOE + [("potato", 36)], [O(U, ["potato"], 32, "Patatas plantadas", "Potatoes planted"), O(M, ["potatoes"], 48, "Patatas cosechadas", "Potatoes harvested")])
J("remolachero", 1, 1000, "Remolachero", "Beetroot Farmer", HOE + [("beetroot_seeds", 36)], [O(U, ["beetroot_seeds"], 32, "Semillas plantadas", "Seeds planted"), O(M, ["beetroots"], 32, "Remolachas cosechadas", "Beetroots harvested")])
J("melonero", 1, 1000, "Melonero", "Melon Picker", AXE, [O(M, ["melon"], 24, "Melones cosechados", "Melons harvested"), O(M, ["pumpkin"], 24, "Calabazas cosechadas", "Pumpkins harvested")])
J("canero", 1, 1000, "Cañero", "Cane Cutter", AXE, [O(M, ["sugar_cane"], 64, "Caña cortada", "Cane cut")])
J("cactero", 1, 900, "Recolector de cactus", "Cactus Harvester", AXE, [O(M, ["cactus"], 32, "Cactus cortados", "Cacti cut")])
J("reforestador", 1, 900, "Reforestador", "Reforester", AXE + [("oak_sapling", 24)], [O(U, ["#minecraft:saplings"], 24, "Árboles plantados", "Trees planted")])
J("ganadero", 1, 1000, "Ganadero", "Rancher", SWORD, [O(K, FARM, 16, "Animales sacrificados", "Animals slaughtered")])
J("criador", 2, 1500, "Criador", "Breeder", [("wheat", 16), ("carrot", 16)], [O(S, ["animals_bred"], 8, "Crías nacidas", "Animals bred")])
J("criador_intensivo", 2, 2100, "Criador intensivo", "Intensive Breeder", [("wheat", 24), ("carrot", 24)], [O(S, ["animals_bred"], 14, "Crías nacidas", "Animals bred")])
J("pescador", 1, 1400, "Pescador", "Fisher", [("fishing_rod", 1)], [O(S, ["fish_caught"], 8, "Peces pescados", "Fish caught")])
J("pescador_altura", 2, 2400, "Pescador de altura", "Deep-Sea Fisher", [("fishing_rod", 1)], [O(S, ["fish_caught"], 18, "Peces pescados", "Fish caught")])
J("exterminador", 2, 1800, "Exterminador de plagas", "Pest Exterminator", SWORD, [O(K, HOSTILE, 15, "Monstruos eliminados", "Monsters eliminated")])
J("cazador_creepers", 3, 2800, "Desactivador de creepers", "Creeper Defuser", SWORD + [("bow", 1), ("arrow", 32)], [O(K, ["creeper"], 6, "Creepers eliminados", "Creepers eliminated")])
J("cazador_esqueletos", 2, 1900, "Cazador de esqueletos", "Skeleton Hunter", SWORD, [O(K, ["skeleton", "stray"], 10, "Esqueletos eliminados", "Skeletons eliminated")])
J("cazador_zombis", 2, 1700, "Cazador de zombis", "Zombie Hunter", SWORD, [O(K, ["zombie", "husk", "drowned", "zombie_villager"], 12, "Zombis eliminados", "Zombies eliminated")])
J("cazador_aranas", 2, 1800, "Cazador de arañas", "Spider Hunter", SWORD, [O(K, ["spider", "cave_spider"], 10, "Arañas eliminadas", "Spiders eliminated")])
J("cazador_endermen", 3, 3400, "Cazador de endermen", "Enderman Hunter", SWORD, [O(K, ["enderman"], 4, "Endermen eliminados", "Endermen eliminated")])
J("fundidor_hierro", 2, 1500, "Fundidor de hierro", "Iron Smelter", FURN, [O(C, ["iron_ingot"], 32, "Lingotes de hierro", "Iron ingots")])
J("fundidor_cobre", 1, 1100, "Fundidor de cobre", "Copper Smelter", FURN, [O(C, ["copper_ingot"], 48, "Lingotes de cobre", "Copper ingots")])
J("fundidor_oro", 2, 2000, "Fundidor de oro", "Gold Smelter", FURN, [O(C, ["gold_ingot"], 16, "Lingotes de oro", "Gold ingots")])
J("vidriero", 1, 1000, "Vidriero", "Glassmaker", FURN, [O(C, ["glass"], 64, "Bloques de cristal", "Glass blocks")])
J("ladrillero", 2, 1500, "Ladrillero", "Brickmaker", FURN, [O(C, ["brick"], 48, "Ladrillos cocidos", "Bricks fired")])
J("cocinero", 1, 1100, "Cocinero", "Cook", FURN, [O(C, FOODS, 40, "Platos cocinados", "Dishes cooked")])
J("panadero", 1, 1000, "Panadero", "Baker", [("wheat", 48)], [O(C, ["bread"], 16, "Panes horneados", "Loaves baked")])
J("carpintero", 1, 1000, "Carpintero", "Carpenter", [("crafting_table", 1)], [O(C, ["#minecraft:planks"], 64, "Tablones aserrados", "Planks cut"), O(C, ["stick"], 32, "Palos fabricados", "Sticks made")])
J("mueblista", 2, 1500, "Mueblista", "Furniture Maker", [("crafting_table", 1)], [O(C, ["chest"], 6, "Cofres fabricados", "Chests built"), O(C, ["ladder"], 16, "Escaleras fabricadas", "Ladders built")])
J("herrero", 2, 2000, "Herrero", "Blacksmith", [("furnace", 1), ("coal", 16)], [O(C, ["iron_sword", "iron_pickaxe", "iron_axe", "iron_shovel", "iron_hoe"], 4, "Herramientas forjadas", "Tools forged")])
J("armero", 2, 2300, "Armero", "Armorer", [("furnace", 1), ("coal", 16)], [O(C, ["iron_helmet", "iron_chestplate", "iron_leggings", "iron_boots"], 4, "Piezas de armadura", "Armor pieces")])
J("artificiero", 2, 1800, "Artificiero", "Pyrotechnician", [("crafting_table", 1)], [O(C, ["firework_rocket"], 16, "Cohetes fabricados", "Rockets crafted")])
J("electricista", 2, 1600, "Electricista", "Electrician", [("crafting_table", 1)], [O(C, ["repeater"], 6, "Repetidores", "Repeaters"), O(C, ["observer"], 4, "Observadores", "Observers")])
J("maquinista", 2, 2000, "Maquinista", "Machinist", [("crafting_table", 1)], [O(C, ["piston", "sticky_piston"], 6, "Pistones", "Pistons"), O(C, ["hopper"], 4, "Embudos", "Hoppers")])
J("papelero", 1, 1000, "Papelero", "Papermaker", [("sugar_cane", 48)], [O(C, ["paper"], 48, "Papel fabricado", "Paper made")])
J("encuadernador", 2, 1300, "Encuadernador", "Bookbinder", [("paper", 24), ("leather", 8)], [O(C, ["book"], 8, "Libros encuadernados", "Books bound")])
J("albanil", 1, 1300, "Albañil", "Bricklayer", PICK, [O(U, BUILD, 128, "Bloques colocados", "Blocks laid")])
J("constructor_madera", 1, 1100, "Constructor de madera", "Timber Builder", AXE, [O(U, ["#minecraft:planks"], 128, "Tablones colocados", "Planks placed")])
J("vidriero_instalador", 1, 1100, "Instalador de cristales", "Glazier", SHOVEL, [O(U, ["glass", "glass_pane"], 48, "Cristales instalados", "Panes installed")])
J("alumbrado", 1, 1000, "Operario de alumbrado", "Lighting Technician", [("coal", 12), ("stick", 12)], [O(U, ["torch"], 64, "Antorchas colocadas", "Torches placed")])
J("mensajero", 1, 1300, "Mensajero", "Courier", [("leather_boots", 1)], [O(S, ["walk_one_cm"], 150000, "Centímetros recorridos", "Centimeters walked")])
J("explorador", 2, 2200, "Explorador", "Surveyor", [("leather_boots", 1)], [O(S, ["walk_one_cm"], 200000, "Centímetros caminados", "Centimeters walked"), O(S, ["sprint_one_cm"], 150000, "Centímetros corridos", "Centimeters sprinted")])
J("buzo", 2, 1600, "Buzo industrial", "Industrial Diver", [("turtle_helmet", 1)], [O(S, ["swim_one_cm"], 30000, "Centímetros nadados", "Centimeters swum")])
J("remero", 2, 1500, "Remero", "Rower", [("oak_boat", 1)], [O(S, ["boat_one_cm"], 50000, "Centímetros navegados", "Centimeters sailed")])
J("maquinista_tren", 2, 1500, "Maquinista de tren", "Train Driver", [("minecart", 1), ("rail", 64)], [O(S, ["minecart_one_cm"], 60000, "Centímetros en vagoneta", "Centimeters by minecart")])
J("escalador", 1, 1000, "Escalador", "Climber", [("ladder", 16)], [O(S, ["climb_one_cm"], 8000, "Centímetros escalados", "Centimeters climbed")])
J("comerciante", 2, 1900, "Comerciante", "Trader", [("emerald", 4)], [O(S, ["traded_with_villager"], 8, "Tratos cerrados", "Trades closed")])
J("encantador", 2, 1600, "Encantador", "Enchanter", [("lapis_lazuli", 16)], [O(S, ["enchant_item"], 2, "Objetos encantados", "Items enchanted")])
J("cliente_casino", 1, 900, "Cliente VIP del casino", "Casino VIP", [], [O(N, ["bets"], 10, "Apuestas realizadas", "Bets placed")])
J("ludopata", 1, 1500, "Jugador compulsivo", "High Roller", [], [O(N, ["wagered"], 5000, "Monedas apostadas", "Coins wagered")])
J("racha_ganadora", 2, 2000, "Racha de suerte", "Lucky Streak", [], [O(N, ["bets_won"], 3, "Apuestas ganadas", "Bets won")])
J("corredor_bolsa", 1, 1300, "Corredor de bolsa", "Stockbroker", [], [O(N, ["trades"], 8, "Operaciones en bolsa", "Trades made")])
J("inversor", 2, 2000, "Gran inversor", "Big Investor", [], [O(N, ["volume"], 20000, "Volumen negociado", "Volume traded")])
J("especulador", 3, 3000, "Especulador", "Speculator", [], [O(N, ["realized"], 800, "Beneficio realizado", "Realized profit")])
J("minero_cripto", 2, 1800, "Minero de criptomonedas", "Crypto Miner", [], [O(N, ["mined_usd"], 2500, "Valor minado", "Value mined")], True)
J("granja_cripto", 3, 3600, "Dueño de granja cripto", "Crypto Farm Owner", [], [O(N, ["mined_usd"], 12000, "Valor minado", "Value mined")], True)

def jobs():
    lang_es, lang_en = {}, {}
    for j in JOBS:
        objs = []
        for i, (t, targets, n, les, len_) in enumerate(j["objs"]):
            if t in (M, C, U, K):
                tg = [x if ":" in x else (("#minecraft:" + x[1:]) if x.startswith("#") else "minecraft:" + x) for x in targets]
            elif t == S:
                tg = ["minecraft:" + x for x in targets]
            else:
                tg = targets
            objs.append({"type": t, "targets": tg, "count": n})
            lang_es[f"capitalcraft.job.{j['id']}.o{i}"] = les; lang_en[f"capitalcraft.job.{j['id']}.o{i}"] = len_
        data = {"tier": j["tier"], "wage": j["wage"], "kit": [{"item": a, "count": b} for a, b in j["kit"]], "objectives": objs}
        if j["gpu"]: data["requires_gpu"] = True
        w(f"{D}/capitalcraft_jobs/{j['id']}.json", data)
        lang_es[f"capitalcraft.job.{j['id']}"] = j["es"]; lang_en[f"capitalcraft.job.{j['id']}"] = j["en"]
    return lang_es, lang_en

# ------------------------------------------------------------------ language
ES = {
 "itemGroup.capitalcraft": "CapitalCraft", "capitalcraft.group": "CapitalCraft",
 "item.capitalcraft.casino_chip": "Ficha de Casino", "item.capitalcraft.stock_terminal": "Terminal Bursátil",
 "block.capitalcraft.gpu_basic": "GPU Básica", "block.capitalcraft.gpu_advanced": "GPU Avanzada", "block.capitalcraft.gpu_elite": "GPU Élite",
 "key.capitalcraft.hud": "Mostrar/ocultar panel de jornada", "key.categories.capitalcraft": "CapitalCraft",
 "capitalcraft.welcome.1": "§6§lBienvenido al capitalismo.§r §7Tienes §e$1.500§7, una Ficha de Casino y un Terminal Bursátil.",
 "capitalcraft.welcome.2": "§7Cada día te asignan un trabajo con su kit. Al terminar la jornada la empresa se queda con casi todo.",
 "capitalcraft.welcome.3": "§7Lo que guardes en cofres es propiedad privada. Escribe §e/capital ayuda§7 para más información.",
 "capitalcraft.tip.casino": "Clic derecho: abre el Mejorador de Ítems", "capitalcraft.tip.terminal": "Clic derecho: abre la bolsa y el mercado cripto",
 "capitalcraft.tip.gpu.1": "Minería básica. Consume electricidad.", "capitalcraft.tip.gpu.2": "Minería avanzada, mayor rendimiento.", "capitalcraft.tip.gpu.3": "Granja de minería de élite.",
 "capitalcraft.tip.gpu.use": "Clic derecho: abre el panel de minería", "capitalcraft.gpu.not_owner": "§cEsta GPU pertenece a otro minero.",
 "capitalcraft.tip.value": "Valor: $%s",
 "capitalcraft.casino.title": "MEJORADOR DE ÍTEMS", "capitalcraft.casino.value": "Valor: %s", "capitalcraft.casino.tab.item": "ÍTEM", "capitalcraft.casino.tab.coins": "MONEDAS",
 "capitalcraft.casino.upgrade": "MEJORAR", "capitalcraft.casino.chance": "probabilidad", "capitalcraft.casino.win": "¡GANASTE!", "capitalcraft.casino.win.coins": "+$%s", "capitalcraft.casino.lose": "PERDISTE",
 "capitalcraft.casino.mult": "Objetivo %s del valor apostado", "capitalcraft.casino.pct": "Objetivo con %s de probabilidad",
 "capitalcraft.casino.balance": "SALDO", "capitalcraft.casino.bets": "GANADAS", "capitalcraft.casino.inventory": "Inventario", "capitalcraft.casino.search": "Buscar objeto...",
 "capitalcraft.casino.coins.stake": "Monedas a apostar", "capitalcraft.casino.coins.target": "Monedas objetivo", "capitalcraft.casino.coins.hint": "Cantidad (ej. 5k)",
 "capitalcraft.casino.p.stake": "Elige qué apostar", "capitalcraft.casino.p.target": "Elige el objetivo", "capitalcraft.casino.p.same": "Apuesta y objetivo son el mismo objeto",
 "capitalcraft.casino.err.funds": "No tienes tantas monedas", "capitalcraft.casino.err.chance": "Probabilidad demasiado baja", "capitalcraft.casino.err.need_chip": "Necesitas tu Ficha de Casino",
 "capitalcraft.casino.err.slot": "Objeto no válido", "capitalcraft.casino.err.protected": "Ese objeto es propiedad de la empresa", "capitalcraft.casino.err.target": "Objetivo no válido",
 "capitalcraft.trade.title": "TERMINAL BURSÁTIL", "capitalcraft.trade.connecting": "Conectando con el mercado...", "capitalcraft.trade.portfolio": "Cartera: $%s",
 "capitalcraft.trade.tab.stocks": "ACCIONES", "capitalcraft.trade.tab.crypto": "CRIPTO", "capitalcraft.trade.tab.portfolio": "CARTERA",
 "capitalcraft.trade.mood.bull": "Alcista", "capitalcraft.trade.mood.bear": "Bajista", "capitalcraft.trade.mood.flat": "Lateral",
 "capitalcraft.trade.pos": "Posición: %s %s ($%s)", "capitalcraft.trade.avg": "Coste medio: $%s", "capitalcraft.trade.nopos": "Sin posición abierta", "capitalcraft.trade.amount": "Monto ($)",
 "capitalcraft.trade.buy": "COMPRAR", "capitalcraft.trade.sell": "VENDER", "capitalcraft.trade.sell_all": "VENDER TODO",
 "capitalcraft.trade.col.asset": "Activo", "capitalcraft.trade.col.qty": "Cant.", "capitalcraft.trade.col.value": "Valor", "capitalcraft.trade.col.pnl": "P&G",
 "capitalcraft.trade.empty": "Aún no tienes inversiones", "capitalcraft.trade.total": "Valor total: $%s  ·  Invertido: $%s", "capitalcraft.trade.realized": "Beneficio realizado: $%s  ·  Dividendos: $%s",
 "capitalcraft.trade.bought": "Comprado %s %s por $%s", "capitalcraft.trade.sold": "Vendido %s %s por $%s", "capitalcraft.trade.sold_all": "Toda tu cripto ha sido vendida",
 "capitalcraft.trade.err.invalid": "Activo no válido", "capitalcraft.trade.err.small": "Cantidad demasiado pequeña", "capitalcraft.trade.err.funds": "Fondos insuficientes",
 "capitalcraft.trade.err.nopos": "No tienes ese activo", "capitalcraft.trade.err.need_terminal": "Necesitas tu Terminal Bursátil",
 "capitalcraft.dividends": "§aDividendos del día: +$%s",
 "capitalcraft.sector.energy": "energético", "capitalcraft.sector.mining": "minero", "capitalcraft.sector.tech": "tecnológico", "capitalcraft.sector.logistics": "logístico",
 "capitalcraft.sector.finance": "financiero", "capitalcraft.sector.defense": "de defensa", "capitalcraft.sector.commodities": "de materias primas", "capitalcraft.sector.agro": "agrícola",
 "capitalcraft.sector.health": "farmacéutico", "capitalcraft.sector.construction": "de la construcción",
 "capitalcraft.news.t0": "%s anuncia beneficios récord", "capitalcraft.news.t1": "%s, investigada por fraude contable", "capitalcraft.news.t2": "Gran demanda en el sector %s",
 "capitalcraft.news.t3": "Crisis en el sector %s", "capitalcraft.news.t4": "Los aldeanos adoptan %s en masa", "capitalcraft.news.t5": "Hackeo a un exchange: %s se desploma",
 "capitalcraft.news.t6": "Una regulación estricta amenaza a las criptomonedas", "capitalcraft.news.t7": "La adopción institucional dispara las criptomonedas",
 "capitalcraft.news.t8": "Mercado alcista: vuelve el apetito por el riesgo", "capitalcraft.news.t9": "Pánico bursátil: ventas masivas en todo el mercado",
 "capitalcraft.news.t10": "¡Rug pull! %s pierde la mitad de su valor", "capitalcraft.news.t11": "Fiebre meme: %s se dispara",
 "capitalcraft.job.warn60": "Queda 1 minuto de jornada. Guarda lo tuyo en un cofre.", "capitalcraft.job.warn10": "¡10 SEGUNDOS PARA EL FIN DE LA JORNADA!",
 "capitalcraft.job.new_shift": "NUEVA JORNADA", "capitalcraft.job.assigned": "Trabajo de hoy: %s (salario base $%s)", "capitalcraft.kit.lore": "Propiedad de la Empresa",
 "capitalcraft.payroll.header": "━━━━━━ NÓMINA ━━━━━━", "capitalcraft.payroll.job": "Trabajo: %s (%s%% completado)", "capitalcraft.payroll.wage": "Salario: +$%s",
 "capitalcraft.payroll.bonus": "Bonificación por objetivos: +$%s (racha de %s)", "capitalcraft.payroll.confiscated": "La empresa se queda con %s objetos (valor $%s)",
 "capitalcraft.payroll.surplus": "Plusvalía cedida (%2$s%%): +$%1$s", "capitalcraft.payroll.total": "TOTAL COBRADO: $%s", "capitalcraft.payroll.tip": "Solo conservas lo necesario para vivir. Lo guardado en cofres no se toca.",
 "capitalcraft.gpu.title": "Panel de minería", "capitalcraft.gpu.name.1": "GPU BÁSICA", "capitalcraft.gpu.name.2": "GPU AVANZADA", "capitalcraft.gpu.name.3": "GPU ÉLITE",
 "capitalcraft.gpu.status.off": "APAGADA", "capitalcraft.gpu.status.on": "MINANDO", "capitalcraft.gpu.status.hot": "SOBRECALENTADA", "capitalcraft.gpu.status.nofunds": "SIN FONDOS",
 "capitalcraft.gpu.temp": "Temperatura: %s°C", "capitalcraft.gpu.cooled": "Refrigeración líquida activa", "capitalcraft.gpu.nocool": "Sin refrigeración (pon agua o hielo al lado)",
 "capitalcraft.gpu.oc": "Overclock", "capitalcraft.gpu.oc.tip.0": "Rendimiento normal", "capitalcraft.gpu.oc.tip.1": "+40% de minado, más calor y consumo", "capitalcraft.gpu.oc.tip.2": "+80% de minado, mucho calor y consumo",
 "capitalcraft.gpu.power.on": "ENCENDER", "capitalcraft.gpu.power.off": "APAGAR", "capitalcraft.gpu.coin": "Moneda a minar", "capitalcraft.gpu.perhour": "$/hora",
 "capitalcraft.gpu.diff": "Dificultad de red: x%s", "capitalcraft.gpu.stats1": "Minado: $%s/s  ·  Luz: $%s/s", "capitalcraft.gpu.stats2": "Sesión: $%s  ·  Total: $%s",
 "capitalcraft.gpu.hold": "En cartera: %s %s (≈$%s)", "capitalcraft.gpu.sell": "VENDER %s", "capitalcraft.gpu.sell_all": "VENDER TODO",
 "capitalcraft.cmd.news": "Noticia de mercado generada", "capitalcraft.cmd.balance": "Saldo: $%s", "capitalcraft.cmd.nojob": "Hoy no tienes trabajo asignado", "capitalcraft.cmd.badjob": "Ese trabajo no existe",
 "capitalcraft.help.1": "§6§lCapitalCraft§r §7— el capital nunca duerme", "capitalcraft.help.2": "§e/capital saldo§7, §e/capital jornada§7: tu dinero y tu trabajo de hoy",
 "capitalcraft.help.3": "§7Cada día recibes un trabajo y su kit. Al acabar la jornada (atardecer) te pagan y la empresa confisca casi todo; solo conservas comida, antorchas y una cama.",
 "capitalcraft.help.4": "§7Ficha de Casino: apuesta cualquier objeto o monedas contra cualquier objeto del juego. La casa se queda un 10%.",
 "capitalcraft.help.5": "§7Terminal Bursátil: 12 acciones con noticias y dividendos, y 12 criptomonedas. Comisión del 0,5%.",
 "capitalcraft.help.6": "§7Bloques GPU: mina criptomonedas, pero pagas la luz. Refrígeralos con agua para hacer overclock sin quemarlos.",
}
EN = {
 "itemGroup.capitalcraft": "CapitalCraft", "capitalcraft.group": "CapitalCraft",
 "item.capitalcraft.casino_chip": "Casino Chip", "item.capitalcraft.stock_terminal": "Stock Terminal",
 "block.capitalcraft.gpu_basic": "Basic GPU", "block.capitalcraft.gpu_advanced": "Advanced GPU", "block.capitalcraft.gpu_elite": "Elite GPU",
 "key.capitalcraft.hud": "Toggle shift panel", "key.categories.capitalcraft": "CapitalCraft",
 "capitalcraft.welcome.1": "§6§lWelcome to capitalism.§r §7You have §e$1,500§7, a Casino Chip and a Stock Terminal.",
 "capitalcraft.welcome.2": "§7Every day you get a job and its tool kit. When the shift ends the company keeps almost everything.",
 "capitalcraft.welcome.3": "§7Whatever you store in chests is private property. Type §e/capital ayuda§7 for more info.",
 "capitalcraft.tip.casino": "Right click: opens the Item Upgrader", "capitalcraft.tip.terminal": "Right click: opens the stock & crypto exchange",
 "capitalcraft.tip.gpu.1": "Basic mining rig. Uses electricity.", "capitalcraft.tip.gpu.2": "Advanced rig, higher yield.", "capitalcraft.tip.gpu.3": "Elite mining farm.",
 "capitalcraft.tip.gpu.use": "Right click: opens the mining panel", "capitalcraft.gpu.not_owner": "§cThis GPU belongs to another miner.",
 "capitalcraft.tip.value": "Value: $%s",
 "capitalcraft.casino.title": "ITEM UPGRADER", "capitalcraft.casino.value": "Value: %s", "capitalcraft.casino.tab.item": "ITEM", "capitalcraft.casino.tab.coins": "COINS",
 "capitalcraft.casino.upgrade": "UPGRADE", "capitalcraft.casino.chance": "chance", "capitalcraft.casino.win": "YOU WIN!", "capitalcraft.casino.win.coins": "+$%s", "capitalcraft.casino.lose": "YOU LOSE",
 "capitalcraft.casino.mult": "Target %s of the stake value", "capitalcraft.casino.pct": "Target with %s chance",
 "capitalcraft.casino.balance": "BALANCE", "capitalcraft.casino.bets": "WON", "capitalcraft.casino.inventory": "Inventory", "capitalcraft.casino.search": "Search item...",
 "capitalcraft.casino.coins.stake": "Coins to stake", "capitalcraft.casino.coins.target": "Target coins", "capitalcraft.casino.coins.hint": "Amount (e.g. 5k)",
 "capitalcraft.casino.p.stake": "Pick what to stake", "capitalcraft.casino.p.target": "Pick a target", "capitalcraft.casino.p.same": "Stake and target are the same item",
 "capitalcraft.casino.err.funds": "You don't have that many coins", "capitalcraft.casino.err.chance": "Chance too low", "capitalcraft.casino.err.need_chip": "You need your Casino Chip",
 "capitalcraft.casino.err.slot": "Invalid item", "capitalcraft.casino.err.protected": "That item belongs to the company", "capitalcraft.casino.err.target": "Invalid target",
 "capitalcraft.trade.title": "STOCK TERMINAL", "capitalcraft.trade.connecting": "Connecting to the market...", "capitalcraft.trade.portfolio": "Portfolio: $%s",
 "capitalcraft.trade.tab.stocks": "STOCKS", "capitalcraft.trade.tab.crypto": "CRYPTO", "capitalcraft.trade.tab.portfolio": "PORTFOLIO",
 "capitalcraft.trade.mood.bull": "Bullish", "capitalcraft.trade.mood.bear": "Bearish", "capitalcraft.trade.mood.flat": "Sideways",
 "capitalcraft.trade.pos": "Position: %s %s ($%s)", "capitalcraft.trade.avg": "Avg. cost: $%s", "capitalcraft.trade.nopos": "No open position", "capitalcraft.trade.amount": "Amount ($)",
 "capitalcraft.trade.buy": "BUY", "capitalcraft.trade.sell": "SELL", "capitalcraft.trade.sell_all": "SELL ALL",
 "capitalcraft.trade.col.asset": "Asset", "capitalcraft.trade.col.qty": "Qty", "capitalcraft.trade.col.value": "Value", "capitalcraft.trade.col.pnl": "P&L",
 "capitalcraft.trade.empty": "You have no investments yet", "capitalcraft.trade.total": "Total value: $%s  ·  Invested: $%s", "capitalcraft.trade.realized": "Realized profit: $%s  ·  Dividends: $%s",
 "capitalcraft.trade.bought": "Bought %s %s for $%s", "capitalcraft.trade.sold": "Sold %s %s for $%s", "capitalcraft.trade.sold_all": "All your crypto has been sold",
 "capitalcraft.trade.err.invalid": "Invalid asset", "capitalcraft.trade.err.small": "Amount too small", "capitalcraft.trade.err.funds": "Insufficient funds",
 "capitalcraft.trade.err.nopos": "You don't own that asset", "capitalcraft.trade.err.need_terminal": "You need your Stock Terminal",
 "capitalcraft.dividends": "§aDaily dividends: +$%s",
 "capitalcraft.sector.energy": "energy", "capitalcraft.sector.mining": "mining", "capitalcraft.sector.tech": "tech", "capitalcraft.sector.logistics": "logistics",
 "capitalcraft.sector.finance": "finance", "capitalcraft.sector.defense": "defense", "capitalcraft.sector.commodities": "commodities", "capitalcraft.sector.agro": "agriculture",
 "capitalcraft.sector.health": "pharma", "capitalcraft.sector.construction": "construction",
 "capitalcraft.news.t0": "%s reports record profits", "capitalcraft.news.t1": "%s under investigation for accounting fraud", "capitalcraft.news.t2": "Strong demand lifts the %s sector",
 "capitalcraft.news.t3": "Crisis hits the %s sector", "capitalcraft.news.t4": "Villagers adopt %s en masse", "capitalcraft.news.t5": "Exchange hacked: %s plunges",
 "capitalcraft.news.t6": "Strict regulation threatens crypto", "capitalcraft.news.t7": "Institutional adoption sends crypto soaring",
 "capitalcraft.news.t8": "Bull market: risk appetite returns", "capitalcraft.news.t9": "Market panic: sell-off across the board",
 "capitalcraft.news.t10": "Rug pull! %s loses half its value", "capitalcraft.news.t11": "Meme frenzy: %s skyrockets",
 "capitalcraft.job.warn60": "1 minute of shift left. Stash your stuff in a chest.", "capitalcraft.job.warn10": "10 SECONDS UNTIL THE END OF THE SHIFT!",
 "capitalcraft.job.new_shift": "NEW SHIFT", "capitalcraft.job.assigned": "Today's job: %s (base wage $%s)", "capitalcraft.kit.lore": "Company property",
 "capitalcraft.payroll.header": "━━━━━━ PAYROLL ━━━━━━", "capitalcraft.payroll.job": "Job: %s (%s%% done)", "capitalcraft.payroll.wage": "Wage: +$%s",
 "capitalcraft.payroll.bonus": "Quota bonus: +$%s (streak of %s)", "capitalcraft.payroll.confiscated": "The company keeps %s items (worth $%s)",
 "capitalcraft.payroll.surplus": "Surplus handed back (%2$s%%): +$%1$s", "capitalcraft.payroll.total": "TOTAL PAID: $%s", "capitalcraft.payroll.tip": "You only keep what you need to live. Chests are never touched.",
 "capitalcraft.gpu.title": "Mining panel", "capitalcraft.gpu.name.1": "BASIC GPU", "capitalcraft.gpu.name.2": "ADVANCED GPU", "capitalcraft.gpu.name.3": "ELITE GPU",
 "capitalcraft.gpu.status.off": "OFF", "capitalcraft.gpu.status.on": "MINING", "capitalcraft.gpu.status.hot": "OVERHEATED", "capitalcraft.gpu.status.nofunds": "NO FUNDS",
 "capitalcraft.gpu.temp": "Temperature: %s°C", "capitalcraft.gpu.cooled": "Liquid cooling active", "capitalcraft.gpu.nocool": "No cooling (put water or ice next to it)",
 "capitalcraft.gpu.oc": "Overclock", "capitalcraft.gpu.oc.tip.0": "Stock performance", "capitalcraft.gpu.oc.tip.1": "+40% yield, more heat and power", "capitalcraft.gpu.oc.tip.2": "+80% yield, lots of heat and power",
 "capitalcraft.gpu.power.on": "POWER ON", "capitalcraft.gpu.power.off": "POWER OFF", "capitalcraft.gpu.coin": "Coin to mine", "capitalcraft.gpu.perhour": "$/hour",
 "capitalcraft.gpu.diff": "Network difficulty: x%s", "capitalcraft.gpu.stats1": "Mined: $%s/s  ·  Power: $%s/s", "capitalcraft.gpu.stats2": "Session: $%s  ·  Total: $%s",
 "capitalcraft.gpu.hold": "Holding: %s %s (≈$%s)", "capitalcraft.gpu.sell": "SELL %s", "capitalcraft.gpu.sell_all": "SELL ALL",
 "capitalcraft.cmd.news": "Market news generated", "capitalcraft.cmd.balance": "Balance: $%s", "capitalcraft.cmd.nojob": "You have no job today", "capitalcraft.cmd.badjob": "That job does not exist",
 "capitalcraft.help.1": "§6§lCapitalCraft§r §7— capital never sleeps", "capitalcraft.help.2": "§e/capital saldo§7, §e/capital jornada§7: your money and today's job",
 "capitalcraft.help.3": "§7Every day you get a job and its kit. At dusk you get paid and the company confiscates almost everything; you only keep food, torches and a bed.",
 "capitalcraft.help.4": "§7Casino Chip: stake any item or coins against any item in the game. The house keeps 10%.",
 "capitalcraft.help.5": "§7Stock Terminal: 12 stocks with news and dividends, plus 12 cryptocurrencies. 0.5% fee.",
 "capitalcraft.help.6": "§7GPU blocks: mine crypto, but you pay the power bill. Cool them with water to overclock safely.",
}

if __name__ == "__main__":
    chip(); terminal(); hex_tile(); icon()
    blocks_and_items()
    names = make_sounds()
    jes, jen = jobs()
    ES.update(jes); EN.update(jen)
    for n in names:
        ES[f"subtitles.capitalcraft.{n}"] = "CapitalCraft: " + n.replace("_", " "); EN[f"subtitles.capitalcraft.{n}"] = "CapitalCraft: " + n.replace("_", " ")
    w(f"{A}/lang/es_es.json", ES); w(f"{A}/lang/en_us.json", EN)
    print("jobs:", len(JOBS), "sounds:", len(names), "lang keys:", len(ES))
