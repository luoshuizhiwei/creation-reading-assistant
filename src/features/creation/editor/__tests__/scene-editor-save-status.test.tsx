// @vitest-environment jsdom
/**
 * 批次 BB：场景编辑器把会话保存态上报进 creation store，侧栏 dock 的第五行读的就是这份信号。
 *
 * 这里测的是「上报这条通道本身」，不是 dock 的显示（显示在 desktop-dock.test.tsx）：
 *   · 打开场景后 store 里必须有它那条，dock 才有东西可读；
 *   · 真人敲字（DOM 净变化被 prosemirror-view 的 DOMObserver 读回来）会把状态推到 dirty，
 *     防抖提交期间是 saving，失败是 error —— 这几档必须原样进 store；
 *   · 编辑器换场景（组件不卸载，写作台在两个已缓存场景之间切换就是这样）时旧条目必须撤掉，
 *     否则上一场最后一次读到的状态会冒充「正在编辑的场景」；
 *   · 卸载时条目撤销，且随后异步补的那次落盘不许把它写回来 ——
 *     漏掉这一条，dock 会永远挂着一个再也不会消失的「正在保存」；
 *   · StrictMode 的假卸载不许把信号永久打死（旧代码那道只会被置 false 的 mountedRef 就死在这里）。
 *
 * 用真实 SceneEditor（Tiptap 在 jsdom 下由既有测试证明可渲染），不拿替身测自己的接线。
 */
import React from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { act, cleanup, render } from "@testing-library/react";
import { SceneEditor } from "@/features/creation/editor/SceneEditor";
import { useCreationStore } from "@/stores/creation-store";
import type { CreationDocument, SceneBodyView, SceneSaveResponse } from "@/types/creation";

function sceneOf(sceneId: string, text: string): SceneBodyView {
  return {
    sceneId,
    projectId: "p1",
    chapterId: "ch1",
    title: sceneId,
    body: { type: "doc", content: [{ type: "paragraph", content: [{ type: "text", text }] }] },
    revision: 1,
    updatedAt: ""
  } as unknown as SceneBodyView;
}

const noop = () => undefined;
const baseProps = {
  focusMode: false,
  onToggleFocusMode: noop,
  typewriter: false,
  onToggleTypewriter: noop
};

function statuses(): Record<string, string> {
  return useCreationStore.getState().sceneSaveStatuses;
}

/** 会话状态由 effect / 事件驱动，测试里推进一次微任务与帧再读 store。 */
async function settle(ms = 0): Promise<void> {
  await act(async () => {
    if (ms > 0) await new Promise((resolve) => setTimeout(resolve, ms));
    await Promise.resolve();
  });
}

/**
 * 在真实编辑器里「敲一个字」：改 DOM 后由 prosemirror-view 的 DOMObserver 读回事务，
 * 走的是 session.edit → dirty → 防抖 → saving → saved/error 这条完整链路。
 * jsdom 下派发 beforeinput / 直接取 pmViewDesc 都不通（实测），改 DOM 是唯一可用的入口。
 */
async function typeOneCharacter(container: HTMLElement): Promise<void> {
  const paragraph = container.querySelector(".scene-editor-content p") as HTMLElement;
  paragraph.appendChild(document.createTextNode("写"));
  await settle();
}

beforeEach(() => {
  useCreationStore.setState({ sceneSaveStatuses: {} });
});
afterEach(() => {
  cleanup();
  vi.useRealTimers();
});

describe("SceneEditor → store 的保存态上报", () => {
  it("打开场景后 store 里有这一场的状态位（dock 据此显示，不再靠猜）", async () => {
    await act(async () => {
      render(<SceneEditor view={sceneOf("scene-a", "第一场正文")} onSave={async () => undefined} {...baseProps} />);
    });
    await settle();
    expect(statuses()).toEqual({ "scene-a": "saved" });
  });

  it("没有 view（正文尚未落地）时不编造状态位", async () => {
    await act(async () => {
      render(<SceneEditor view={undefined} onSave={async () => undefined} {...baseProps} />);
    });
    await settle();
    expect(statuses()).toEqual({});
  });

  it("IME 组合输入期间上报 composing，组合结束回到 saved", async () => {
    let container!: HTMLElement;
    await act(async () => {
      ({ container } = render(
        <SceneEditor view={sceneOf("scene-a", "第一场正文")} onSave={async () => undefined} {...baseProps} />
      ));
    });
    await settle();
    const host = container.querySelector(".scene-editor-content") as HTMLElement;

    await act(async () => {
      host.dispatchEvent(new CompositionEvent("compositionstart", { bubbles: true }));
    });
    expect(statuses()).toEqual({ "scene-a": "composing" });

    await act(async () => {
      host.dispatchEvent(new CompositionEvent("compositionend", { bubbles: true }));
    });
    expect(statuses()).toEqual({ "scene-a": "saved" });
  });

  it("敲一个字：dirty → 防抖到点提交 → 成功落回 saved，三档都进 store", async () => {
    vi.useFakeTimers({ toFake: ["setTimeout", "clearTimeout"] });
    const save = vi.fn(async (_sceneId: string, _revision: number, _body: CreationDocument): Promise<SceneSaveResponse> => ({
      ok: true,
      result: {
        commandType: "scene.updateBody",
        sequence: 1,
        projectId: "p1",
        sceneId: "scene-a",
        revision: 2,
        updatedAt: "2026-10-08T00:00:00.000Z"
      }
    }));
    let container!: HTMLElement;
    await act(async () => {
      ({ container } = render(<SceneEditor view={sceneOf("scene-a", "第一场正文")} onSave={save} {...baseProps} />));
    });
    await settle();
    expect(statuses()).toEqual({ "scene-a": "saved" });

    const paragraph = container.querySelector(".scene-editor-content p") as HTMLElement;
    await act(async () => {
      paragraph.appendChild(document.createTextNode("写"));
      await vi.advanceTimersByTimeAsync(0);
    });
    // 停止输入后先挂「未保存」，防抖 800ms 到点才提交。
    expect(statuses()).toEqual({ "scene-a": "dirty" });

    await act(async () => {
      await vi.advanceTimersByTimeAsync(800);
    });
    expect(save).toHaveBeenCalledTimes(1);
    expect(statuses()).toEqual({ "scene-a": "saved" });
    vi.useRealTimers();
  });

  it("保存失败上报 error，不会假装已保存", async () => {
    vi.useFakeTimers({ toFake: ["setTimeout", "clearTimeout"] });
    const save = vi.fn(async (): Promise<SceneSaveResponse> => ({
      ok: false,
      error: { code: "integrity", message: "数据校验失败，保存已中止。" }
    }));
    let container!: HTMLElement;
    await act(async () => {
      ({ container } = render(<SceneEditor view={sceneOf("scene-a", "第一场正文")} onSave={save} {...baseProps} />));
    });
    await settle();
    const paragraph = container.querySelector(".scene-editor-content p") as HTMLElement;
    await act(async () => {
      paragraph.appendChild(document.createTextNode("写"));
      await vi.advanceTimersByTimeAsync(0);
    });
    expect(statuses()).toEqual({ "scene-a": "dirty" });
    await act(async () => {
      await vi.advanceTimersByTimeAsync(800);
    });
    expect(statuses()).toEqual({ "scene-a": "error" });
    vi.useRealTimers();
  });

  it("同一实例内切换场景：旧场景条目撤掉，只剩当前这一场", async () => {
    let view!: ReturnType<typeof render>;
    await act(async () => {
      view = render(<SceneEditor view={sceneOf("scene-a", "第一场正文")} onSave={async () => undefined} {...baseProps} />);
    });
    await settle();
    expect(statuses()).toEqual({ "scene-a": "saved" });

    // 写作台在两个已缓存场景之间切换时 SceneEditor 不卸载，只换 view.sceneId。
    await act(async () => {
      view.rerender(<SceneEditor view={sceneOf("scene-b", "第三场正文")} onSave={async () => undefined} {...baseProps} />);
    });
    await settle();
    expect(statuses()).toEqual({ "scene-b": "saved" });
  });

  it("带着未保存的改动卸载：条目撤销，随后异步补的落盘不许把它写回来", async () => {
    vi.useFakeTimers({ toFake: ["setTimeout", "clearTimeout"] });
    let resolveSave!: () => void;
    const gating = new Promise<void>((resolve) => {
      resolveSave = resolve;
    });
    const save = vi.fn(async (_sceneId: string, _revision: number, _body: CreationDocument): Promise<SceneSaveResponse> => {
      await gating;
      return {
        ok: true,
        result: {
          commandType: "scene.updateBody",
          sequence: 1,
          projectId: "p1",
          sceneId: "scene-a",
          revision: 2,
          updatedAt: "2026-10-08T00:00:00.000Z"
        }
      };
    });
    let container!: HTMLElement;
    let unmount!: () => void;
    await act(async () => {
      const rendered = render(<SceneEditor view={sceneOf("scene-a", "第一场正文")} onSave={save} {...baseProps} />);
      container = rendered.container;
      unmount = rendered.unmount;
    });
    await settle();
    await typeOneCharacter(container);
    expect(statuses()).toEqual({ "scene-a": "dirty" });

    // 卸载：编辑器先撤销这一场那条，再异步补一次落盘。补盘过程中会话仍会回调
    // saving / saved 两次，若放行，dock 上就永远留下一个「正在保存」。
    await act(async () => {
      unmount();
      await vi.advanceTimersByTimeAsync(0);
    });
    expect(statuses()).toEqual({});
    resolveSave();
    await act(async () => {
      await vi.advanceTimersByTimeAsync(10);
    });
    expect(statuses()).toEqual({});
    expect(save).toHaveBeenCalledTimes(1);
    vi.useRealTimers();
  });

  it("StrictMode 双跑后信号仍然活着（旧 mountedRef 一次性置 false 会永久打死它）", async () => {
    await act(async () => {
      render(
        <React.StrictMode>
          <SceneEditor view={sceneOf("scene-a", "第一场正文")} onSave={async () => undefined} {...baseProps} />
        </React.StrictMode>
      );
    });
    await settle();
    expect(statuses()).toEqual({ "scene-a": "saved" });

    // 双跑之后还要能继续上报：真人敲字这一档必须仍然进 store。
    const host = document.querySelector(".scene-editor-content") as HTMLElement;
    const paragraph = host.querySelector("p") as HTMLElement;
    paragraph.appendChild(document.createTextNode("写"));
    // DOMObserver 读回 MutationObserver 回调要等一个真实 timer 轮次（本用例用真定时器）。
    await settle(10);
    expect(statuses()).toEqual({ "scene-a": "dirty" });
  });
});
