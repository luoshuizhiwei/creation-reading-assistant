// @vitest-environment jsdom
/**
 * BD-217 账本：WCAG 1.3.3 感官特性（Level A）。
 *
 * 主判据是 scripts/audit-sensory-instructions.mjs 的两条腿：
 *   ① 静态：TypeScript AST 只取「用户看得见的文案」（JSX 文本 / 字符串字面量 /
 *      无插值模板），注释不在 AST 里，因此代码注释里的「左侧」不会被误判；
 *   ② 运行时：Electron 真窗口逐视图遍历可见文本节点（跳过 aria-hidden / 零尺寸 /
 *      visibility:hidden），抓 左侧/右侧/上方/下方。
 *
 * 本批真实缺陷：13 处操作说明用视线方位指代控件——「从左侧选择条目」「点击上方按钮
 * 生成」「在下方保存 API Key」「选中场景可在右侧编辑」。读屏器听不到「左侧」，窗口
 * 变窄改列后句子还会说反；1.3.3 要求点名控件或区域。修复后一律改成「想法列表」
 * 「大纲树」「卡片板」「固定类型」「超长段落阈值」「API Key」这类可听见的名字。
 *
 * 路径账（__tests__/ 起算）：../../../ = 仓库根；../../features = src/features/。
 */
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import React from "react";
import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { InboxItemList } from "@/features/creation/inbox/components/InboxItemList";
import { EMPTY_DRAFT } from "@/features/creation/inbox/components/types";

const read = (rel: string) => readFileSync(fileURLToPath(new URL(rel, import.meta.url)), "utf8");
const census = read("../../../scripts/audit-sensory-instructions.mjs");
const pkg = read("../../../package.json");

const inboxList = read("../../features/creation/inbox/components/InboxItemList.tsx");
const inboxDetail = read("../../features/creation/inbox/components/InboxItemDetail.tsx");
const outlinePage = read("../../features/creation/outline/OutlinePage.tsx");
const cardBoard = read("../../features/creation/outline/CardBoard.tsx");
const cardsPage = read("../../features/creation/cards/CardsPage.tsx");
const cardImport = read("../../features/creation/cards/import-export/CardImportDialog.tsx");
const cardTypeEditor = read("../../features/creation/cards/CardTypeEditor.tsx");
const proofPanel = read("../../features/creation/proof/ProofPanel.tsx");
const proofIgnore = read("../../features/creation/proof/proof-ignore.ts");
const writingMargin = read("../../features/creation/editor/desk/WritingDeskMargin.tsx");
const aiSection = read("../../features/settings/sections/AISection.tsx");

const sources: Array<[string, string]> = [
  ["InboxItemList", inboxList],
  ["InboxItemDetail", inboxDetail],
  ["OutlinePage", outlinePage],
  ["CardBoard", cardBoard],
  ["CardsPage", cardsPage],
  ["CardImportDialog", cardImport],
  ["CardTypeEditor", cardTypeEditor],
  ["ProofPanel", proofPanel],
  ["proof-ignore", proofIgnore],
  ["WritingDeskMargin", writingMargin],
  ["AISection", aiSection]
];

const renders = () => (
  <InboxItemList
    items={[]}
    filteredItems={[]}
    onSelectId={vi.fn()}
    confirmingId={null}
    onConfirmDelete={vi.fn()}
    onDelete={vi.fn()}
    convertingIds={new Set()}
    onConvertToCard={vi.fn()}
    hasMore={false}
    loadingMore={false}
    onLoadMore={vi.fn()}
    draft={EMPTY_DRAFT}
    saveStatus="idle"
    onDraftChange={vi.fn()}
    onSaveDraft={vi.fn()}
    aiAvailable={false}
    aiSettings={null}
    isAIRunning={false}
    onRequestAI={vi.fn()}
    onAdoptVariant={vi.fn()}
    onCopyVariant={vi.fn()}
    onRemoveVariant={vi.fn()}
  />
);

describe("BD-217 普查判据：1.3.3 感官特性", () => {
  it("红/绿结论与退出码都在，静态腿走 AST 而不是裸正则", () => {
    expect(census).toContain("SENSORY_RED");
    expect(census).toContain("SENSORY_OK");
    expect(census).toContain("process.exit(1)");
    expect(census).toContain("ts.isJsxText");
    expect(census).toContain("ts.isStringLiteral");
    expect(census).toContain("createTreeWalker");
    expect(census).toContain("aria-hidden");
    expect(pkg).toContain('"audit:sensory-instructions": "node scripts/audit-sensory-instructions.mjs"');
  });

  it("13 处方位指代都换成了可听见的控件/区域名", () => {
    expect(inboxList).toContain('aria-label="想法列表"');
    expect(inboxList).toContain("在「想法列表」中选择一个条目查看与编辑");
    expect(inboxDetail).toContain("点击「润色」「扩写」等按钮生成。");
    expect(outlinePage).toContain("先选择一个场景，再编辑它的任务卡字段。");
    expect(outlinePage).toContain("场景状态独立于章节工作流状态。");
    expect(outlinePage).toContain("选中场景后可编辑任务卡字段。");
    expect(cardBoard).toContain("在「大纲树」中新建卷、章节或场景。");
    expect(cardsPage).toContain("选择一张已关联卡片查看详情，或关联、新建一张。");
    expect(cardImport).toContain("使用「固定类型」");
    expect(cardTypeEditor).toContain("每个字段的 key 都必须在类型内唯一且创建后保持稳定。");
    expect(proofPanel).toContain("因此不计入总计。");
    expect(proofIgnore).toContain("可在「超长段落阈值」中调整");
    expect(writingMargin).toContain("卷章结构可在「大纲树」或「卡片板」中管理。");
    expect(aiSection).toContain("请先填写「API Key」并保存才能使用 AI 打磨");
  });

  it("旧的方位文案一处不剩", () => {
    const retired = [
      "从左侧选择一个条目",
      "点击上方按钮生成",
      "在左侧选择一个场景",
      "独立于左侧章节",
      "可在右侧编辑",
      "在左侧大纲中",
      "在左侧选择一张已关联卡片",
      "使用下方固定类型",
      "下方每个字段",
      "计入上方总数",
      "可在下方调整",
      "在左侧大纲树",
      "请先在下方保存 API Key"
    ];
    for (const [name, source] of sources) {
      for (const phrase of retired) expect(source, `${name} 仍含「${phrase}」`).not.toContain(phrase);
    }
  });

  it("想法列表有可访问名字，空态提示点名它", () => {
    render(renders());
    const list = screen.getByRole("list", { name: "想法列表" });
    expect(list).toBeTruthy();
    expect(screen.getByText(/在「想法列表」中选择一个条目查看与编辑/)).toBeTruthy();
  });
});
