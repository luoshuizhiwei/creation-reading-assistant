// @vitest-environment jsdom
import React from "react";
import { beforeAll, beforeEach, afterEach, describe, expect, it, vi } from "vitest";
import { act, cleanup, render, screen } from "@testing-library/react";
import { CardImportDialog } from "../CardImportDialog";
import * as client from "../card-import-export-client";
import type { CardImportPreview, CardImportSchemaContext } from "../card-import-export-types";

vi.mock("../card-import-export-client", async (importOriginal) => {
  const actual = await importOriginal<typeof import("../card-import-export-client")>();
  return {
    ...actual,
    openAndParseImportSource: vi.fn(),
    loadImportSchema: vi.fn(),
    parseImportSource: vi.fn()
  };
});

const preview: CardImportPreview = {
  format: "csv",
  headers: ["标题", "关系", ""],
  rows: [{ index: 0, fields: { 标题: "林晚", 关系: "张三" }, notes: [] }],
  warnings: []
};

const schema: CardImportSchemaContext = {
  projectId: "p1",
  cardTypes: [{ kind: "character", name: "人物", fields: [] }],
  relationTypes: [
    {
      id: "rt1",
      name: "同学",
      forwardName: "同学",
      reverseName: "同窗",
      fromKinds: ["character"],
      toKinds: ["character"]
    }
  ],
  existingCards: []
};

beforeAll(() => {
  // jsdom 没有 rAF，Dialog 的首焦点在 rAF 回调里。
  globalThis.requestAnimationFrame = ((cb: FrameRequestCallback) =>
    setTimeout(() => cb(0), 0) as unknown as number) as typeof requestAnimationFrame;
  globalThis.cancelAnimationFrame = ((id: number) => clearTimeout(id)) as typeof cancelAnimationFrame;
});

beforeEach(() => {
  vi.mocked(client.openAndParseImportSource).mockResolvedValue({ format: "csv", text: "标题,关系\n林晚,张三" });
  vi.mocked(client.loadImportSchema).mockResolvedValue(schema);
  vi.mocked(client.parseImportSource).mockResolvedValue(preview);
});

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

async function renderInMappingPhase() {
  render(<CardImportDialog projectId="p1" onClose={vi.fn()} onImported={vi.fn()} />);
  await act(async () => {
    await Promise.resolve();
    await Promise.resolve();
    await Promise.resolve();
  });
}

describe("卡片导入 · 关系列映射下拉框", () => {  // BD-142：两个下拉框都只有当前值、没有可访问名，读屏器一律念「组合框」，
  // 多列关系时无法区分是哪一列、哪个维度。
  it("每个关系列的类型/方向下拉框都有带列名的可访问名", async () => {
    await renderInMappingPhase();

    expect(screen.getByRole("combobox", { name: "列「关系」的关系类型" })).toBeTruthy();
    expect(screen.getByRole("combobox", { name: "列「关系」的关系方向" })).toBeTruthy();
    expect(screen.getByRole("combobox", { name: "列「标题」的关系类型" })).toBeTruthy();
  });

  it("空表头列沿用可见文案「（空列）」作为名字的一部分", async () => {
    await renderInMappingPhase();

    expect(screen.getByRole("combobox", { name: "列「（空列）」的关系类型" })).toBeTruthy();
    expect(screen.getByRole("combobox", { name: "列「（空列）」的关系方向" })).toBeTruthy();
  });

  // BD-153：自动打开文件后「正在读取文件…」只是普通段落，读屏器不会播报。
  it("自动读取文件时的提示以 status 暴露", async () => {
    render(<CardImportDialog projectId="p1" onClose={vi.fn()} onImported={vi.fn()} />);

    expect(screen.getByRole("status").textContent).toContain("正在读取文件");

    await act(async () => {
      await Promise.resolve();
    });
  });
});
