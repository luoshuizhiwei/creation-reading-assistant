/**
 * BD-203 账本：Escape 关浮层的两宿主同构。EPUB 页此前只关选区浮层不关设置
 * 抽屉（TXT/MD 一直是抽屉优先逐层收），读屏/键盘用户的 Escape 等价性破口。
 * EpubReaderPage 整机渲染需 EPUB 引擎与 IPC，超出 jsdom 行为测试成本，
 * 按 route-status-root-source 先例用源码账本钉形状。
 */
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";

function source(rel: string): string {
  return readFileSync(fileURLToPath(new URL(rel, import.meta.url)), "utf8");
}

describe("BD-203 账本：EPUB 宿主 Escape 逐层收，与 TXT/MD 同构", () => {
  it("EPUB 页 Escape 先收设置抽屉再清选区浮层，抽屉支带 return", () => {
    const src = source("../EpubReaderPage.tsx");
    expect(
      /if \(event\.key === "Escape"\) \{\s*(\/\/[^\n]*\s*)*if \(settingsDrawerOpen\) \{\s*setSettingsDrawerOpen\(false\);\s*return;\s*\}\s*setSelectionToolbar\(null\);\s*setShowColorPicker\(false\);/.test(src),
      "回退：EPUB 侧 Escape 的抽屉优先层被拆或 return 缺失（会把选区清理一起执行）"
    ).toBe(true);
    expect(/addEventListener\("keydown", handleKeydown\);\s*return \(\) => window\.removeEventListener\("keydown", handleKeydown\);\s*\}, \[turnPage, settingsDrawerOpen\]\);/.test(src), "回退：effect 依赖没带上 settingsDrawerOpen，监听器会读陈旧开合态").toBe(true);
  });

  it("TXT/MD 宿主同形不破：抽屉支收 open 后 return", () => {
    const src = source("../reader/TxtMarkdownReader.tsx");
    expect(
      /if \(settingsDrawerOpen\) \{\s*setSettingsDrawerOpen\(false\);\s*return;\s*\}/.test(src),
      "回退：TXT/MD 侧的样板形状变了，两宿主同构断言要跟着重核"
    ).toBe(true);
  });

  it("抽屉收法保持 open=false——BD-201 的开手回交覆盖面依赖此形状", () => {
    const drawer = source("../ReaderSettingsDrawer.tsx");
    expect(/return \(\) => \{\s*const target = restoreFocusRef\.current;/.test(drawer), "回退：抽屉的开手回交 effect 被拆，Escape 收抽屉会重新开始跌焦点").toBe(true);
  });
});
