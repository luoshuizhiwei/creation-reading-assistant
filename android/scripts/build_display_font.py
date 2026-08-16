#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
生成「纸墨」展示字体子集，落地到 android/app/src/main/res/font/。

来源：系统已安装的 NotoSerifSC-VF.ttf（SIL OFL 授权，可随 APK 再分发）。
做法：
  1. 收集应用真实用到的字符（res/values*/strings.xml 文案 + 源码中的字符串字面量）
     + ASCII/数字/常用 CJK 标点/全角形；缺失字形由 Android 字体回退兜底，绝不出现豆腐块。
  2. 从可变字体实例化 Regular(400) 与 Medium(500) 两个字重。
  3. 将 CFF2 轮廓转为 TrueType(glyf)，兼容 minSdk=24。
  4. 按字符集子集化并丢弃 OpenType 布局表以压体积。
  5. 输出 noto_serif_sc_regular.otf / noto_serif_sc_medium.otf（实际为 TTF 轮廓，扩展名取 otf 仅为区分）。

用法：py -3 android/scripts/build_display_font.py
"""
import os
import re
import sys

from fontTools import ttLib, subset
from fontTools.pens.cu2quPen import Cu2QuPen
from fontTools.pens.ttGlyphPen import TTGlyphPen
from fontTools.ttLib import newTable
from fontTools.varLib.instancer import instantiateVariableFont

ROOT = r"d:\develop\Code\Codex\creation-reading-assistant"
SRC_FONT = r"C:\Windows\Fonts\NotoSerifSC-VF.ttf"
OUT_DIR = os.path.join(ROOT, "android", "app", "src", "main", "res", "font")

# ---------------------------------------------------------------------------
# 1. 收集字符集
# ---------------------------------------------------------------------------
chars = set()


def add_text(s: str) -> None:
    chars.update(s)


# strings.xml 文案（所有语言）
for base, _, fnames in os.walk(os.path.join(ROOT, "android", "app", "src", "main", "res")):
    for fn in fnames:
        if fn.endswith(".xml"):
            with open(os.path.join(base, fn), encoding="utf-8", errors="ignore") as fh:
                txt = fh.read()
            txt = re.sub(r"<[^>]+>", " ", txt)          # 去掉 XML 标签
            txt = re.sub(r"&[a-zA-Z]+;", " ", txt)       # 去掉实体引用
            add_text(txt)

# Kotlin 源码里的字符串字面量（覆盖硬编码的展示文案，如「读毕」「收藏」）
for base, _, fnames in os.walk(os.path.join(ROOT, "android", "app", "src", "main", "java")):
    for fn in fnames:
        if fn.endswith(".kt"):
            with open(os.path.join(base, fn), encoding="utf-8", errors="ignore") as fh:
                src = fh.read()
            for m in re.finditer(r'"((?:[^"\\\n]|\\.)*)"', src):
                add_text(m.group(1))

# ASCII 可打印 + 数字
for cp in range(0x20, 0x7F):
    chars.add(chr(cp))
# 常用 CJK 标点与全角形
extra = ("　。，、；：？！“”‘’（）《》〈〉【】「」『』—…·～．’“”＂＊＆％＃＠×÷"
         "①②③④⑤⑥⑦⑧⑨⑩○◇◆△▽▶▼▲▽◀➤→←↑↓·—−")
for ch in extra:
    chars.add(ch)
for cp in range(0x3000, 0x3040):   # CJK Symbols and Punctuation
    chars.add(chr(cp))
for cp in range(0xFF01, 0xFF60):   # Fullwidth forms
    chars.add(chr(cp))
for cp in range(0x2000, 0x206F):   # General Punctuation
    chars.add(chr(cp))

charset = "".join(sorted(chars))
print(f"[charset] 共 {len(charset)} 个字符")


# ---------------------------------------------------------------------------
# 2. CFF2 -> glyf（TrueType 轮廓，兼容 minSdk=24）
# ---------------------------------------------------------------------------
def cff_to_glyf(font: ttLib.TTFont, max_err: float = 1.0) -> None:
    if "CFF2" in font:
        cff = font["CFF2"].cff
    elif "CFF " in font:
        cff = font["CFF "].cff
    else:
        return
    top_name = cff.fontNames[0]
    char_strings = cff[top_name].CharStrings
    glyph_order = font.getGlyphOrder()

    glyf = newTable("glyf")
    glyf.glyphOrder = glyph_order
    glyf.glyphs = {}
    for gname in glyph_order:
        tt_pen = TTGlyphPen(font)
        cu2qu_pen = Cu2QuPen(tt_pen, max_err, reverse_direction=True)
        char_strings[gname].draw(cu2qu_pen)
        glyf[gname] = tt_pen.glyph()

    font["glyf"] = glyf
    font["loca"] = newTable("loca")
    for t in ("CFF ", "CFF2", "VORG"):
        if t in font:
            del font[t]
    font["maxp"].version = 0x00010000
    font["maxp"].numGlyphs = len(glyph_order)
    font["head"].indexToLocFormat = 1
    for t in ("fvar", "gvar", "avar", "cvar", "STAT", "MVAR", "HVAR", "VVAR"):
        if t in font:
            del font[t]


def build(weight: int, filename: str) -> None:
    font = ttLib.TTFont(SRC_FONT)
    instantiateVariableFont(font, {"wght": weight}, inplace=True)
    cff_to_glyf(font)

    opt = subset.Options()
    opt.glyph_names = False
    opt.layout_features = []          # 丢弃 GSUB/GPOS 等布局表
    opt.name_IDs = ["*"]
    opt.notdef_outline = True
    opt.recalc_timestamp = True
    opt.drop_tables += ["GSUB", "GDEF", "GPOS", "BASE", "JSTF", "DSIG", "SVG "]

    sub = subset.Subsetter(options=opt)
    sub.populate(text=charset)
    sub.subset(font)

    os.makedirs(OUT_DIR, exist_ok=True)
    out_path = os.path.join(OUT_DIR, filename)
    font.save(out_path)
    size_kb = os.path.getsize(out_path) // 1024
    print(f"[build] {filename}: {size_kb} KB")


if __name__ == "__main__":
    build(400, "noto_serif_sc_regular.otf")
    build(500, "noto_serif_sc_medium.otf")
    print("[done] 展示字体子集已生成。")
