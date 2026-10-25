// @vitest-environment jsdom
import React from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { act, cleanup, render, screen, waitFor } from "@testing-library/react";
import { ProofPanel } from "@/features/creation/proof/ProofPanel";
import type { ProofView } from "@/types/creation";

// BD-187：「正在逐场景检查…」与结果摘要此前都挂在条件挂载的节点上——polite 区
// 随内容一起插入不会被播报。播报必须走一条常驻区：面板一打开就存在，状态变化
// 只是同一条节点换文本。

const runProof = vi.fn();
const ignoreProofLocation = vi.fn();
const unignoreProofLocation = vi.fn();

vi.mock("@/hooks/useCreationActions", () => ({
  useCreationActions: () => ({ runProof, ignoreProofLocation, unignoreProofLocation })
}));

function proofView(): ProofView {
  return {
    projectId: "project-1",
    scanScope: {
      kind: "project",
      label: "全书扫描：1 卷 / 2 章 / 3 场景",
      volumeCount: 1,
      chapterCount: 2,
      sceneCount: 3,
      rules: ["repeatedChar"],
      bannedWords: [],
      maxParagraphChars: 500
    },
    issues: [],
    ignoredIssues: [],
    scannedScenes: 3,
    affectedScenes: 0,
    total: 0,
    ignoredCount: 0,
    rawTotal: 0,
    truncated: false,
    ignoredTruncated: false,
    ignoreRecordCount: 0
  };
}

afterEach(() => cleanup());

describe("校对面板 · 播报走常驻区（BD-187）", () => {
  it("检查中/检查完成在同一条常驻区上切换，结果摘要不再自带 status", async () => {
    let resolve!: (view: ProofView) => void;
    runProof.mockImplementation(() => new Promise<ProofView>((done) => { resolve = done; }));

    const { container } = render(<ProofPanel projectId="project-1" onClose={() => undefined} />);
    await screen.findByRole("dialog", { name: "本地校对" });

    const region = screen.getByRole("status");
    expect(region.textContent).toBe("正在逐场景检查…");

    await act(async () => {
      resolve(proofView());
    });

    // 关键：完成播报不是新挂载的节点，而是同一条常驻区换文本。
    expect(screen.getByRole("status")).toBe(region);
    await waitFor(() => expect(region.textContent).toContain("检查完成"));

    // 可见摘要块退回纯视觉；面板里只有这一条 status 区。
    expect(container.querySelector('.creation-proof-summary[role="status"]')).toBeNull();
    expect(container.querySelectorAll('[role="status"]').length).toBe(1);
  });

  it("扫描失败不播报完成，错误仍走 alert", async () => {
    runProof.mockRejectedValue(new Error("boom"));
    render(<ProofPanel projectId="project-1" onClose={() => undefined} />);

    const alert = await screen.findByRole("alert");
    expect(alert.textContent).toContain("正文未被修改");
    expect(screen.getByRole("status").textContent).toBe("");
  });
});
