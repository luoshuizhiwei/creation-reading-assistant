// @vitest-environment node
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";

/**
 * BD-194 源码账：空态原语（EmptyState / EpubEmptyState）是「挂载即带内容」的块，
 * polite 插入即哑（BD-175/180/192 判据），且 Epub 那份容器里有按钮（BD-191：带交互
 * 的容器不该整体当 status）。两处死装饰已摘；到达播报归宿主常驻区，不许回潮。
 * 判据先剥 // 行注释——本批判据注释里就引用了这些属性名，不剥会自伤。
 */

function read(rel: string): string {
  return readFileSync(fileURLToPath(new URL(rel, import.meta.url)), "utf-8");
}

function codeOnly(rel: string): string {
  return read(rel).replace(/\/\/[^\n]*/g, "");
}

describe("BD-194 源码账：空态原语不得自带 polite 播报", () => {
  it("ui.tsx EmptyState：函数体内无 role=status / aria-live", () => {
    const body = codeOnly("../ui.tsx").match(/export function EmptyState[\s\S]*?\n\}/);
    expect(body, "EmptyState 原语不见了").toBeTruthy();
    expect(/role="status"/.test(body![0]), "回退：原语又挂回 role=status").toBe(false);
    expect(/aria-live/.test(body![0]), "回退：原语又挂回 aria-live").toBe(false);
    expect(/BD-194/.test(read("../ui.tsx")), "缺少 BD-194 判据注释").toBe(true);
  });

  it("EpubEmptyState：全文无 role=status / aria-live", () => {
    const src = codeOnly("../../features/library/epub-reader/EpubEmptyState.tsx");
    expect(/role="status"/.test(src), "回退：带按钮的容器又整体当 status").toBe(false);
    expect(/aria-live/.test(src), "回退：aria-live 又回来了").toBe(false);
    expect(/BD-194/.test(read("../../features/library/epub-reader/EpubEmptyState.tsx")), "缺少 BD-194 判据注释").toBe(true);
  });

  it("指纹防线：两份原语里不得再出现 status+aria-live 同标签双装饰", () => {
    // 挂载即带内容的 role="status" aria-live 同挂是 BD-194 案的指纹；
    // 合法常驻区是 <span/p className="sr-only" role="status">（无 aria-live 同挂、有换文本），不在此列。
    for (const rel of ["../ui.tsx", "../../features/library/epub-reader/EpubEmptyState.tsx"]) {
      expect(/role="status"[^>]*aria-live|aria-live[^>]*role="status"/.test(codeOnly(rel)), `${rel} 回退`).toBe(false);
    }
  });
});
