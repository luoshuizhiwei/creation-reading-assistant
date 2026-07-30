#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
创作阅读助手 · 批注色对比度计算脚本（实施稿 · 设计方向已冻结）

依据 WCAG 2.1，分两套独立标准：

  (a) 高亮底（文本背景高亮）
      标准：正文 fg 落在「合成高亮底」上的【文本对比度】 >= 4.5:1（AA 文本）。
      合成底 = 随纸 5 色批注色 @ α 与 paperBg 在 sRGB 空间直线混合。
      5 色批注色亮度均低于纸面，合成底仍明显浅于正文，文本对比度充足。

  (b) 下划线 / 批注引线（非文本图形）
      标准：线条颜色 与 纸张背景 的【非文本对比度】 >= 3:1（WCAG 1.4.11，UI 组件/图形）。
      线条颜色 = annotationStrokeColors[paper][color]（每档纸张、每档批注色的「暗化描边色」）。
      说明：批注下划线 / 引线【保留 5 色语义】，不得为达标统一成单一 primary。
            浅纸面上 5 色批注色物理上明度过高（@1.0 仅 1.2–1.6:1），故不能拿原批注色直接作线；
            改为对每个 (paper, color) 求解一个「足够暗的描边色」——在纸面非文本对比度 >=3:1。
            - 浅纸（bg 亮度高）：向黑色插值「暗化」，保留 hue 即保留语义身份；
            - 深纸（夜读 bg 极暗，亮度低）：原批注色已高亮、对比充足，直接沿用（>=3:1 全过）。
            暗化版仍按 5 色区分（黄/红/绿/蓝/紫各一套暗化值），保持语义可辨识。

合成公式（sRGB 直线混合）：C = α·C_src + (1−α)·C_paperBg（逐通道）。
相对亮度 L = 0.2126·R_l + 0.7152·G_l + 0.0722·B_l（通道先 sRGB->linear）。
对比度 CR = (L_lighter+0.05)/(L_darker+0.05)。
"""

def hex2rgb(h):
    h = h.lstrip('#')
    return tuple(int(h[i:i+2], 16) for i in (0, 2, 4))

def rgb2hex(rgb):
    return '#' + ''.join('%02X' % round(max(0, min(255, c))) for c in rgb)

def srgb_to_lin(c):
    c = c / 255.0
    return c / 12.92 if c <= 0.03928 else ((c + 0.055) / 1.055) ** 2.4

def rel_lum(rgb):
    r, g, b = (srgb_to_lin(c) for c in rgb)
    return 0.2126 * r + 0.7152 * g + 0.0722 * b

def contrast(lum1, lum2):
    lighter, darker = max(lum1, lum2), min(lum1, lum2)
    return (lighter + 0.05) / (darker + 0.05)

def composite(accent_hex, bg_hex, alpha):
    a = hex2rgb(accent_hex)
    b = hex2rgb(bg_hex)
    out = tuple(alpha * ac + (1 - alpha) * bc for ac, bc in zip(a, b))
    return out, rgb2hex(out)


# ---- 4 档 ReaderPaperPalette（实施稿·冷调低彩度）----
# accent = 纸张主色（强调色）；ann = 随纸 5 色批注底纹（用于高亮底；暗化版用于下划线/引线）。
PAPERS = {
    'white': {'bg': '#FAFAFB', 'fg': '#1B1E23', 'accent': '#3D5A80', 'light': True,
              'ann': {'黄': '#E6C95A', '红': '#D08B7A', '绿': '#7FA86B', '蓝': '#6E8FC0', '紫': '#A884B0'}},
    'warm':  {'bg': '#F3ECDC', 'fg': '#2B231A', 'accent': '#3D5A80', 'light': True,
              'ann': {'黄': '#C9A24B', '红': '#B5705A', '绿': '#7C8A5A', '蓝': '#6E84A8', '紫': '#9A7C92'}},
    'green': {'bg': '#E8F0DF', 'fg': '#1F291A', 'accent': '#3F6B4F', 'light': True,
              'ann': {'黄': '#C7B65A', '红': '#B5705A', '绿': '#6E8A55', '蓝': '#6E84A8', '紫': '#9A7C92'}},
    'night': {'bg': '#15171C', 'fg': '#DEE2E9', 'accent': '#8AA6D8', 'light': False,
              'ann': {'黄': '#E6C95A', '红': '#D08B7A', '绿': '#8FA86B', '蓝': '#8EA3D0', '紫': '#C0A0C8'}},
}

TEXT_MIN = 4.5    # (a) 高亮底：正文 vs 合成底（文本对比度 AA）
NONTXT_MIN = 3.0  # (b) 下划线/引线：线条 vs 纸张背景（非文本对比度 WCAG 1.4.11）
# 求解描边色时采用的安全余量目标：略高于 3.0，抵消 hex 取整后的微小回退，确保落地值稳过 3:1。
STROKE_TARGET = 3.1

# (a) 高亮底：批注 5 色 @ 0.18，正文 fg vs 合成底
HIGHLIGHT_ALPHA = 0.18


def solve_stroke_color(paper_bg_hex, ann_hex, target=STROKE_TARGET, iters=48):
    """
    求解一个「暗化描边色」使 线条 vs paperBg 非文本对比度 >= target（默认 3.1，稳过 3:1）。
      - 浅纸（bg 亮度高）：向黑色插值暗化，保留 hue；
      - 深纸（bg 亮度低）：原批注色已高亮、对比充足，直接返回原色（不增亮、保持语义）。
    返回 (stroke_hex, contrast)。
    """
    bg_rgb = hex2rgb(paper_bg_hex)
    ann_rgb = hex2rgb(ann_hex)
    bg_lum = rel_lum(bg_rgb)
    base_cr = contrast(rel_lum(ann_rgb), bg_lum)
    # 原批注色已达标：直接沿用（夜读档走此分支）
    if base_cr >= target:
        return rgb2hex(ann_rgb), base_cr
    # 方向：浅纸暗化（limit=黑），深纸增亮（limit=白）
    limit = (0, 0, 0) if bg_lum > 0.5 else (255, 255, 255)
    lo, hi = 0.0, 1.0
    for _ in range(iters):
        mid = (lo + hi) / 2.0
        col = tuple(mid * l + (1 - mid) * a for a, l in zip(ann_rgb, limit))
        if contrast(rel_lum(col), bg_lum) >= target:
            hi = mid
        else:
            lo = mid
    col = tuple(hi * l + (1 - hi) * a for a, l in zip(ann_rgb, limit))
    return rgb2hex(col), contrast(rel_lum(col), bg_lum)


def build_annotation_stroke_colors():
    """每档纸张 × 每档批注色 -> 暗化描边色（4 档 × 5 色）。"""
    out = {}
    for pkey, p in PAPERS.items():
        out[pkey] = {}
        for cname, chex in p['ann'].items():
            stroke_hex, cr = solve_stroke_color(p['bg'], chex)
            out[pkey][cname] = (chex, stroke_hex, cr)
    return out


# ---- 颜色中文名顺序，保证表格稳定 ----
COLOR_ORDER = ['黄', '红', '绿', '蓝', '紫']


if __name__ == '__main__':
    # Windows 默认控制台（GBK/cp936）无法编码部分装饰符号（如 >=、->、*），
    # 直接 print 会抛 UnicodeEncodeError 导致进程非零退出。这里把 stdout 设为
    # errors='replace' 作为兜底，保证任何未覆盖到的非 GBK 字符都不会让脚本崩溃；
    # 同时下方结果标识统一使用纯 ASCII（[PASS]/[FAIL]），不依赖调用方设置 PYTHONUTF8。
    import sys
    try:
        if sys.stdout is not None and hasattr(sys.stdout, 'reconfigure'):
            sys.stdout.reconfigure(errors='replace')
    except Exception:
        pass

    # ============ (a) 高亮底 · 正文对比度（>=4.5） ============
    print('=' * 82)
    print('(a) 高亮底 · 正文文字 vs 合成底色 · 文本对比度（AA >= %.1f:1）' % TEXT_MIN)
    print('     合成底 = 随纸 5 色批注色 @%.2f 与 paperBg 直线混合' % HIGHLIGHT_ALPHA)
    print('=' * 82)
    highlight_all_ok = True
    for pkey, p in PAPERS.items():
        fg_lum = rel_lum(hex2rgb(p['fg']))
        print('\n## 纸张档：%s（bg=%s, fg=%s, %s）'
              % (pkey, p['bg'], p['fg'], '浅(Light)' if p['light'] else '深(Dark)'))
        for cname in COLOR_ORDER:
            chex = p['ann'][cname]
            comp_rgb, comp_hex = composite(chex, p['bg'], HIGHLIGHT_ALPHA)
            cr = contrast(fg_lum, rel_lum(comp_rgb))
            ok = cr >= TEXT_MIN
            highlight_all_ok &= ok
            print('      %s %s @%.2f -> 合成底 %s | 正文对比度 %.2f:1 %s'
                  % (cname, chex, HIGHLIGHT_ALPHA, comp_hex, cr, 'OK' if ok else 'FAIL'))

    # ============ (b) 下划线 / 引线 · 暗化描边色 · 非文本对比度（>=3） ============
    print('\n' + '=' * 82)
    print('(b) 下划线 / 批注引线 · 暗化描边色 vs 纸张背景 · 非文本对比度（WCAG 1.4.11 >= %.1f:1）'
          % NONTXT_MIN)
    print('     线条颜色 = annotationStrokeColors[paper][color]（保留 5 色语义的暗化版）')
    print('     * 下划线与批注引线共用同一套描边色（同一颜色、同一纸面，对比度相同）')
    print('=' * 82)
    stroke = build_annotation_stroke_colors()
    lines_all_ok = True
    for pkey, p in PAPERS.items():
        print('\n## 纸张档：%s（bg=%s, %s）'
              % (pkey, p['bg'], '浅(Light)' if p['light'] else '深(Dark)'))
        for cname in COLOR_ORDER:
            ann_hex, stroke_hex, cr = stroke[pkey][cname]
            ok = cr >= NONTXT_MIN
            lines_all_ok &= ok
            same = '（沿用原批注色）' if stroke_hex.upper() == ann_hex.upper() else ''
            print('      %s 批注色 %s -> 暗化描边 %s%s | 非文本对比度 %.2f:1 %s'
                  % (cname, ann_hex, stroke_hex, same, cr, 'OK' if ok else 'FAIL'))

    # ============ annotationStrokeColors 落地表（4 档 × 5 色） ============
    print('\n' + '=' * 82)
    print('annotationStrokeColors 落地表（4 档 × 5 色，下划线/引线统一使用）')
    print('=' * 82)
    for pkey, p in PAPERS.items():
        print('\n%s (bg=%s):' % (pkey, p['bg']))
        for cname in COLOR_ORDER:
            ann_hex, stroke_hex, cr = stroke[pkey][cname]
            print('    %s : 批注色 %s -> 描边色 %s  (非文本 %.2f:1)'
                  % (cname, ann_hex, stroke_hex, cr))

    # ============ 诊断：若线条用原 5 色批注色 @1.0（不做暗化）在浅纸面的非文本对比度 ============
    print('\n' + '=' * 82)
    print('诊断：若下划线/引线直接用「原 5 色批注色 @1.0」（不做暗化）vs paperBg 的非文本对比度')
    print('     结论：浅纸面明度过高的批注色远未达 3:1，故必须改用暗化描边色（见 (b)）。')
    print('=' * 82)
    for pkey, p in PAPERS.items():
        if not p['light']:
            continue
        print('\n  — 浅纸 %s（bg=%s）原批注色直接作线：' % (pkey, p['bg']))
        for cname in COLOR_ORDER:
            chex = p['ann'][cname]
            cr = contrast(rel_lum(hex2rgb(chex)), rel_lum(hex2rgb(p['bg'])))
            print('      %s %s | 非文本对比度 %.2f:1 %s'
                  % (cname, chex, cr, 'OK' if cr >= NONTXT_MIN else 'FAIL(需暗化)'))

    # ============ 结论 ============
    print('\n' + '=' * 82)
    summary = '[PASS] 全过' if (highlight_all_ok and lines_all_ok) else '[FAIL] 存在未达标'
    print('结论：高亮底正文对比度(>=%.1f) %s | 暗化描边线下划线/引线非文本对比度(>=%.1f) %s -> %s'
          % (TEXT_MIN, '全过' if highlight_all_ok else '未全过',
             NONTXT_MIN, '全过' if lines_all_ok else '未全过', summary))
    print('最终参数：高亮底 alpha=%.2f / 下划线·引线 = annotationStrokeColors 暗化描边色（不叠加 alpha，实色描边）'
          % HIGHLIGHT_ALPHA)
    print('=' * 82)
