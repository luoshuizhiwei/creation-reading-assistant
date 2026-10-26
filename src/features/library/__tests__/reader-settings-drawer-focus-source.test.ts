/**
 * BD-201 账本：阅读设置抽屉（ReaderSettingsDrawer）的焦点回交接线。
 * 抽屉不是 Dialog，宿主是 EpubReaderPage 与 TxtMarkdownReader（EpubSettingsDrawer
 * 只是 format="epub" 的委托壳）——回交全部长在抽屉自己身上，宿主零改动，
 * Escape/遮罩/关闭钮三条路径因此共享同一出口契约。
 */
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";

function source(rel: string): string {
  return readFileSync(fileURLToPath(new URL(rel, import.meta.url)), "utf8");
}

const drawerSrc = source("../ReaderSettingsDrawer.tsx");

describe("BD-201 账本：抽屉关闭与视图切换的焦点回交", () => {
  it("开手回交契约与 BD-183 同式：挂载记开手、cleanup 里 isConnected 才还", () => {
    expect(/const opener = document\.activeElement;/.test(drawerSrc), "回退：开手捕捉被拆了").toBe(true);
    expect(
      /opener instanceof HTMLElement && opener !== document\.body \? opener : null/.test(drawerSrc),
      "回退：body 开手要排除，否则把焦点抢给 body 是假回交"
    ).toBe(true);
    expect(
      /if \(target\?\.isConnected\) target\.focus\(\{ preventScroll: true \}\);/.test(drawerSrc),
      "回退：开手已卸载（整页退场）时必须静默"
    ).toBe(true);
    expect(/useEffect\(\(\) => \{\s*if \(!open\) return;[\s\S]*?\}, \[open\]\);/.test(drawerSrc), "回退：回交没有挂在 [open] 的 effect 上，宿主 Escape 直接翻 open 的路径会漏").toBe(true);
  });

  it("三条关闭路径都汇入 open=false：关闭钮与遮罩走 onClose，宿主常驻挂载", () => {
    expect(/<button[\s\S]*?aria-label="关闭"[\s\S]*?onClick=\{onClose\}/.test(drawerSrc), "回退：关闭钮不再调 onClose").toBe(true);
    expect(/onMouseDown=\{onClose\}/.test(drawerSrc), "回退：遮罩关闭路径被拆").toBe(true);
    const epubHost = source("../EpubReaderPage.tsx");
    const txtHost = source("../reader/TxtMarkdownReader.tsx");
    expect(/onClose=\{\(\) => setSettingsDrawerOpen\(false\)\}/.test(epubHost), "回退：EPUB 宿主仍要常驻挂抽屉、只翻 open").toBe(true);
    expect(/onClose=\{\(\) => setSettingsDrawerOpen\(false\)\}/.test(txtHost), "回退：TXT/MD 宿主同上").toBe(true);
    expect(/if \(settingsDrawerOpen\) \{\s*setSettingsDrawerOpen\(false\);/.test(txtHost), "回退：Escape 快捷键翻 open=false 的路径是回交的覆盖面，别改成别的收法").toBe(true);
  });

  it("视图切换回交给常驻标题：两处 setView 都带 focus，标题带 tabIndex=-1", () => {
    const handoffs = drawerSrc.match(/setView\("(?:more|main)"\);\s*\/\/[^\n]*\n\s*titleRef\.current\?\.focus\(\{ preventScroll: true \}\);/g) ?? [];
    expect(handoffs.length, "回退：「更多设置」或「返回阅读设置」的标题回交少了一处").toBe(2);
    expect(/ref=\{titleRef\} tabIndex=\{-1\}/.test(drawerSrc), "回退：常驻标题不再是可回交的焦点落点").toBe(true);
  });

  it("EpubSettingsDrawer 保持纯委托，不各自长一份回交", () => {
    const delegate = source("../epub-reader/EpubSettingsDrawer.tsx");
    expect(delegate.includes("ReaderSettingsDrawer"), "回退：委托壳拆了会让 EPUB 侧丢掉回交").toBe(true);
    expect(!/activeElement|focus\(/.test(delegate), "回退：委托里重复实现回交会造成双写").toBe(true);
  });
});
