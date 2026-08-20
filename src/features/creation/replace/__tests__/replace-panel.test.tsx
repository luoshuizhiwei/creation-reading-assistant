// @vitest-environment jsdom
import React from "react";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { ReplacePanel } from "../ReplacePanel";
import type { ReplaceErrorView, ReplaceHit, ReplacePlanService, ReplacePlanView } from "../types";

function makeHit(sceneId: string, index: number, before = "旧", after = "新"): ReplaceHit {
  return {
    hitId: `h-${sceneId}-${index}`,
    sceneId,
    blockIndex: 0,
    range: { start: index, end: index + 1 },
    before,
    after,
    context: `…${before}…`
  };
}

function planWith(hits: ReplaceHit[]): ReplacePlanView {
  const byScene = new Map<string, ReplaceHit[]>();
  for (const hit of hits) {
    const list = byScene.get(hit.sceneId) ?? [];
    list.push(hit);
    byScene.set(hit.sceneId, list);
  }
  const scenes = [...byScene.entries()].map(([sceneId, list]) => ({
    sceneId,
    chapterId: "c1",
    chapterTitle: "第一章",
    title: `场景${sceneId}`,
    hitCount: list.length
  }));
  return {
    planId: "plan-1",
    projectId: "p1",
    scope: "all",
    find: "旧",
    replaceWith: "新",
    mode: "plain",
    scenes,
    hits,
    totalHits: hits.length,
    limit: 200,
    truncated: false,
    sealedAt: new Date().toISOString(),
    expiresAt: new Date(Date.now() + 100000).toISOString()
  };
}

interface Controllable {
  createPlan: ReturnType<typeof vi.fn>;
  applyPlan: ReturnType<typeof vi.fn>;
  cancel: ReturnType<typeof vi.fn>;
  ctrl: () => { resolve: (p: ReplacePlanView) => void; reject: (e: unknown) => void } | null;
}

function controllableService(): Controllable {
  let ctrl: { resolve: (p: ReplacePlanView) => void; reject: (e: unknown) => void } | null = null;
  const createPlan = vi.fn((_q: unknown, handlers: { onProgress: (p: never) => void; signal?: AbortSignal }) => {
    return new Promise<ReplacePlanView>((resolve, reject) => {
      ctrl = { resolve, reject };
      handlers.onProgress({
        phase: "scanning",
        completedScenes: 0,
        totalScenes: 3,
        completedHits: 0,
        totalHits: 0
      } as never);
      handlers.signal?.addEventListener("abort", () => reject({ code: "cancelled", message: "已取消" }));
    });
  });
  return {
    createPlan,
    applyPlan: vi.fn(() =>
      Promise.resolve({
        planId: "plan-1",
        appliedHitCount: 1,
        modifiedSceneIds: [],
        snapshotIds: [],
        sequence: 1,
        committedAt: ""
      })
    ),
    cancel: vi.fn(),
    ctrl: () => ctrl
  } as unknown as Controllable;
}

function rejectingService(plan: ReplacePlanView, error: ReplaceErrorView): ReplacePlanService {
  return {
    createPlan: vi.fn(() => Promise.resolve(plan)),
    applyPlan: vi.fn(() => Promise.reject(error)),
    cancel: vi.fn()
  };
}

function renderPanel(service: ReplacePlanService) {
  return render(
    <ReplacePanel projectId="p1" chapterId="c1" onClose={() => {}} service={service} />
  );
}

describe("ReplacePanel 逐命中排除与替换计划", () => {
  it("同场景多命中只排除其中一个", async () => {
    const plan = planWith([makeHit("s1", 0), makeHit("s1", 1)]);
    const service = controllableService();
    renderPanel(service);
    fireEvent.change(screen.getByTestId("replace-find"), { target: { value: "旧" } });
    fireEvent.click(screen.getByTestId("replace-preview"));
    await waitFor(() => expect(service.ctrl()).not.toBeNull());
    service.ctrl()!.resolve(plan);
    await screen.findByTestId("replace-plan-view");
    fireEvent.click(screen.getByLabelText("排除命中 h-s1-0"));
    fireEvent.click(screen.getByTestId("replace-apply"));
    await waitFor(() =>
      expect(service.applyPlan).toHaveBeenCalledWith("plan-1", ["h-s1-0"])
    );
  });

  it("跨场景逐命中排除", async () => {
    const plan = planWith([makeHit("s1", 0), makeHit("s1", 1), makeHit("s2", 0)]);
    const service = controllableService();
    renderPanel(service);
    fireEvent.change(screen.getByTestId("replace-find"), { target: { value: "旧" } });
    fireEvent.click(screen.getByTestId("replace-preview"));
    await waitFor(() => expect(service.ctrl()).not.toBeNull());
    service.ctrl()!.resolve(plan);
    await screen.findByTestId("replace-plan-view");
    fireEvent.click(screen.getByLabelText("排除命中 h-s1-1"));
    fireEvent.click(screen.getByLabelText("排除命中 h-s2-0"));
    fireEvent.click(screen.getByTestId("replace-apply"));
    await waitFor(() =>
      expect(service.applyPlan).toHaveBeenCalledWith("plan-1", ["h-s1-1", "h-s2-0"])
    );
  });

  it("按场景全选/全不选", async () => {
    const plan = planWith([makeHit("s1", 0), makeHit("s1", 1)]);
    const service = controllableService();
    renderPanel(service);
    fireEvent.change(screen.getByTestId("replace-find"), { target: { value: "旧" } });
    fireEvent.click(screen.getByTestId("replace-preview"));
    await waitFor(() => expect(service.ctrl()).not.toBeNull());
    service.ctrl()!.resolve(plan);
    await screen.findByTestId("replace-plan-view");
    const apply = screen.getByTestId("replace-apply") as HTMLButtonElement;
    // 全选排除：应用按钮显示 0 处且禁用
    fireEvent.click(screen.getByLabelText("排除场景 场景s1"));
    await waitFor(() => expect(apply.textContent).toContain("（0 处）"));
    expect(apply.disabled).toBe(true);
    // 全不选：恢复为 2 处且可用
    fireEvent.click(screen.getByLabelText("排除场景 场景s1"));
    await waitFor(() => expect(apply.textContent).toContain("（2 处）"));
    expect(apply.disabled).toBe(false);
  });

  it("普通文本与受限正则都能传递到预览请求", async () => {
    const plan = planWith([makeHit("s1", 0)]);
    const service = controllableService();
    renderPanel(service);
    fireEvent.change(screen.getByTestId("replace-find"), { target: { value: "旧" } });
    fireEvent.click(screen.getByTestId("replace-mode-regex"));
    fireEvent.click(screen.getByTestId("replace-preview"));
    await waitFor(() => expect(service.ctrl()).not.toBeNull());
    service.ctrl()!.resolve(plan);
    await waitFor(() => {
      const call = (service.createPlan as ReturnType<typeof vi.fn>).mock.calls[0]![0] as { mode: string };
      expect(call.mode).toBe("regex");
    });
  });

  it("捕获组替换在计划视图中展示 after", async () => {
    const plan = planWith([{ ...makeHit("s1", 0, "第1章", "卷1章") }]);
    const service = controllableService();
    renderPanel(service);
    fireEvent.change(screen.getByTestId("replace-find"), { target: { value: "第1章" } });
    fireEvent.click(screen.getByTestId("replace-preview"));
    await waitFor(() => expect(service.ctrl()).not.toBeNull());
    service.ctrl()!.resolve(plan);
    const view = await screen.findByTestId("replace-plan-view");
    expect(view.textContent).toContain("卷1章");
  });

  it("stale：正文变化后应用被拒并展示失效提示", async () => {
    const plan = planWith([makeHit("s1", 0)]);
    const service = rejectingService(plan, { code: "stale", message: "场景正文自预览后已变更" });
    renderPanel(service);
    fireEvent.change(screen.getByTestId("replace-find"), { target: { value: "旧" } });
    fireEvent.click(screen.getByTestId("replace-preview"));
    await screen.findByTestId("replace-plan-view");
    fireEvent.click(screen.getByTestId("replace-apply"));
    await screen.findByTestId("replace-error-banner");
    expect(screen.getByText("场景正文自预览后已变更")).toBeTruthy();
  });

  it("重叠命中：稳定拒绝并展示提示", async () => {
    const plan = planWith([makeHit("s1", 0)]);
    const service = rejectingService(plan, { code: "overlap", message: "存在重叠的替换命中" });
    renderPanel(service);
    fireEvent.change(screen.getByTestId("replace-find"), { target: { value: "旧" } });
    fireEvent.click(screen.getByTestId("replace-preview"));
    await screen.findByTestId("replace-plan-view");
    fireEvent.click(screen.getByTestId("replace-apply"));
    await screen.findByTestId("replace-error-banner");
    expect(screen.getByText("存在重叠的替换命中")).toBeTruthy();
  });

  it("planId 伪造/重复/过期均被拒并展示对应提示", async () => {
    const cases: Array<[ReplaceErrorView, string]> = [
      [{ code: "plan-forbidden", message: "替换计划不存在或已失效" }, "替换计划不存在或已失效"],
      [{ code: "plan-used", message: "该替换计划已使用" }, "该替换计划已使用"],
      [{ code: "plan-expired", message: "替换计划已过期" }, "替换计划已过期"]
    ];
    for (const [error, text] of cases) {
      const plan = planWith([makeHit("s1", 0)]);
      const service = rejectingService(plan, error);
      const { unmount } = renderPanel(service);
      fireEvent.change(screen.getByTestId("replace-find"), { target: { value: "旧" } });
      fireEvent.click(screen.getByTestId("replace-preview"));
      await screen.findByTestId("replace-plan-view");
      fireEvent.click(screen.getByTestId("replace-apply"));
      await screen.findByTestId("replace-error-banner");
      expect(screen.getByText(text)).toBeTruthy();
      unmount();
    }
  });

  it("preview 中途取消：回到空闲且不留下计划", async () => {
    const service = controllableService();
    renderPanel(service);
    fireEvent.change(screen.getByTestId("replace-find"), { target: { value: "旧" } });
    fireEvent.click(screen.getByTestId("replace-preview"));
    const cancel = await screen.findByTestId("replace-cancel");
    fireEvent.click(cancel); // 触发 abort → createPlan reject cancelled
    await waitFor(() => expect(screen.queryByTestId("replace-plan-view")).toBeNull());
    // 取消后回到空闲：预览按钮可用，无错误横幅
    expect((screen.getByTestId("replace-preview") as HTMLButtonElement).disabled).toBe(false);
    expect(screen.queryByTestId("replace-error-banner")).toBeNull();
  });

  it("apply 前取消：未点击应用则不调用 applyPlan，无写入", async () => {
    const plan = planWith([makeHit("s1", 0)]);
    const service = controllableService();
    renderPanel(service);
    fireEvent.change(screen.getByTestId("replace-find"), { target: { value: "旧" } });
    fireEvent.click(screen.getByTestId("replace-preview"));
    await waitFor(() => expect(service.ctrl()).not.toBeNull());
    service.ctrl()!.resolve(plan);
    await screen.findByTestId("replace-plan-view");
    expect(service.applyPlan).not.toHaveBeenCalled();
  });

  it("apply 事务失败：回滚提示且 applyPlan 已调用一次", async () => {
    const plan = planWith([makeHit("s1", 0)]);
    const service = rejectingService(plan, { code: "transaction-failed", message: "应用替换失败，已全部回滚" });
    renderPanel(service);
    fireEvent.change(screen.getByTestId("replace-find"), { target: { value: "旧" } });
    fireEvent.click(screen.getByTestId("replace-preview"));
    await screen.findByTestId("replace-plan-view");
    fireEvent.click(screen.getByTestId("replace-apply"));
    await screen.findByTestId("replace-error-banner");
    expect(screen.getByText("应用替换失败，已全部回滚")).toBeTruthy();
    expect(service.applyPlan).toHaveBeenCalledTimes(1);
  });

  it("保护快照和 change_log 一致：完成横幅报告命中与场景数并提示保护快照", async () => {
    const plan = planWith([makeHit("s1", 0), makeHit("s2", 0)]);
    const service: ReplacePlanService = {
      createPlan: vi.fn(() => Promise.resolve(plan)),
      applyPlan: vi.fn(() =>
        Promise.resolve({
          planId: "plan-1",
          appliedHitCount: 2,
          modifiedSceneIds: ["s1", "s2"],
          snapshotIds: ["sn1", "sn2"],
          sequence: 5,
          committedAt: ""
        })
      ),
      cancel: vi.fn()
    };
    renderPanel(service);
    fireEvent.change(screen.getByTestId("replace-find"), { target: { value: "旧" } });
    fireEvent.click(screen.getByTestId("replace-preview"));
    await screen.findByTestId("replace-plan-view");
    fireEvent.click(screen.getByTestId("replace-apply"));
    const banner = await screen.findByTestId("replace-done-banner");
    expect(banner.textContent).toContain("应用 2 处命中");
    expect(banner.textContent).toContain("影响 2 个场景");
    expect(banner.textContent).toContain("保护快照");
  });

  it("大项目预览：跨场景上报进度（渲染端据此更新，不阻塞）", async () => {
    const plan = planWith(Array.from({ length: 300 }, (_, i) => makeHit(`s${i}`, 0)));
    plan.truncated = true;
    plan.totalHits = 200;
    plan.limit = 200;
    const service = controllableService();
    renderPanel(service);
    fireEvent.change(screen.getByTestId("replace-find"), { target: { value: "旧" } });
    fireEvent.click(screen.getByTestId("replace-preview"));
    // 预览中：进度对话框打开，显示真实百分比，且取消按钮可用（未进入事务）
    const dialog = await screen.findByTestId("replace-progress-dialog");
    expect(dialog.textContent).toContain("0%");
    const cancel = screen.getByTestId("replace-cancel") as HTMLButtonElement;
    expect(cancel.disabled).toBe(false);
    // 模拟进度推进
    service.ctrl()!.resolve(plan);
    await screen.findByTestId("replace-plan-view");
    expect(screen.getByTestId("replace-total-hits").textContent).toContain("共 200 处命中");
    expect(screen.getByTestId("replace-truncated")).toBeTruthy();
  });
});
