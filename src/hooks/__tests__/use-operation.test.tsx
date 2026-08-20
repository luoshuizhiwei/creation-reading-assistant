// @vitest-environment jsdom
/**
 * useOperation 行为测试：
 *  - 重复启动被阻止（进行中 / 异步前置阶段均拦截）
 *  - operationId 隔离（其它任务的事件不污染当前 UI）
 *  - cancel 只取消自身任务
 *  - 卸载只退订、绝不强制 kill 提交阶段
 *  - retry 以最后一次请求重新执行
 *  - 已完成任务的迟到事件不覆盖新任务 UI
 */
import { act, renderHook } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import type { OperationState } from "../../types/operation";
import { useOperation } from "../useOperation";

const harness = vi.hoisted(() => ({
  startOperation: vi.fn(),
  getOperationState: vi.fn(),
  cancelOperation: vi.fn(),
  subscribeOperation: vi.fn(),
  listener: null as null | ((state: OperationState) => void),
  unsubscribe: vi.fn()
}));

vi.mock("@/services/operation-service", () => ({
  startOperation: (request: Parameters<typeof harness.startOperation>[0]) => harness.startOperation(request),
  getOperationState: (id: string) => harness.getOperationState(id),
  cancelOperation: (id: string) => harness.cancelOperation(id),
  subscribeOperation: (id: string, listener: (state: OperationState) => void) => harness.subscribeOperation(id, listener)
}));

function makeState(overrides: Partial<OperationState> = {}): OperationState {
  return {
    operationId: "op-1",
    kind: "backup.create",
    status: "running",
    progress: { phase: "validating", completed: 0, total: 1, bytesCompleted: null, bytesTotal: null, indeterminate: false },
    startedAt: 0,
    deferredCancel: false,
    ...overrides
  } as OperationState;
}

beforeEach(() => {
  harness.unsubscribe = vi.fn();
  harness.listener = null;
  harness.cancelOperation = vi.fn().mockResolvedValue(undefined);
  harness.getOperationState = vi.fn().mockResolvedValue(null);
  harness.startOperation = vi.fn().mockResolvedValue(makeState());
  harness.subscribeOperation = vi.fn((_id: string, listener: (state: OperationState) => void) => {
    harness.listener = listener;
    return harness.unsubscribe;
  });
});

describe("useOperation", () => {
  it("进行中的任务再次启动会被阻止", async () => {
    const { result } = renderHook(() => useOperation());
    await act(async () => {
      await result.current.start({ kind: "backup.create" });
    });
    await expect(
      act(async () => {
        await result.current.start({ kind: "backup.create" });
      })
    ).rejects.toThrow("已有进行中的任务");
    expect(harness.startOperation).toHaveBeenCalledTimes(1);
  });

  it("异步前置阶段（首事件到达前）的重复启动同样被阻止", async () => {
    const { result } = renderHook(() => useOperation());
    harness.startOperation = vi.fn(() => new Promise<OperationState>(() => {}));
    act(() => {
      void result.current.start({ kind: "backup.create" });
    });
    await expect(result.current.start({ kind: "backup.create" })).rejects.toThrow("已有进行中的任务");
    expect(harness.startOperation).toHaveBeenCalledTimes(1);
  });

  it("仅接收与当前 operationId 匹配的事件", async () => {
    const { result } = renderHook(() => useOperation());
    await act(async () => {
      await result.current.start({ kind: "backup.create" });
    });
    act(() => {
      harness.listener!(makeState({ operationId: "op-1", progress: { phase: "copying", completed: 3, total: 10, bytesCompleted: null, bytesTotal: null, indeterminate: false } }));
    });
    expect(result.current.state?.operationId).toBe("op-1");
    act(() => {
      harness.listener!(makeState({ operationId: "op-2", kind: "backup.restore" }));
    });
    expect(result.current.state?.operationId).toBe("op-1");
  });

  it("cancel 只取消自身 operationId", async () => {
    const { result } = renderHook(() => useOperation());
    await act(async () => {
      await result.current.start({ kind: "backup.create" });
    });
    act(() => {
      result.current.cancel();
    });
    expect(harness.cancelOperation).toHaveBeenCalledTimes(1);
    expect(harness.cancelOperation).toHaveBeenCalledWith("op-1");
  });

  it("卸载只退订订阅，绝不强制取消进行中的任务", async () => {
    const { result, unmount } = renderHook(() => useOperation());
    await act(async () => {
      await result.current.start({ kind: "backup.create" });
    });
    unmount();
    expect(harness.cancelOperation).not.toHaveBeenCalled();
    expect(harness.unsubscribe).toHaveBeenCalledTimes(1);
  });

  it("retry 以最后一次请求重新执行", async () => {
    const { result } = renderHook(() => useOperation());
    await act(async () => {
      await result.current.start({ kind: "backup.create" });
    });
    act(() => {
      harness.listener!(makeState({ operationId: "op-1", status: "failed", error: { message: "boom" } }));
    });
    expect(result.current.state?.status).toBe("failed");
    harness.startOperation.mockResolvedValueOnce(makeState({ operationId: "op-2" }));
    await act(async () => {
      await result.current.retry();
    });
    expect(harness.startOperation).toHaveBeenCalledTimes(2);
    expect(harness.startOperation).toHaveBeenNthCalledWith(2, { kind: "backup.create" });
    expect(result.current.state?.operationId).toBe("op-2");
  });

  it("已完成任务的迟到事件不会覆盖新任务的 UI", async () => {
    const { result } = renderHook(() => useOperation());
    await act(async () => {
      await result.current.start({ kind: "backup.create" });
    });
    act(() => {
      harness.listener!(makeState({ operationId: "op-1", status: "completed" }));
    });
    expect(result.current.state?.status).toBe("completed");
    harness.startOperation.mockResolvedValueOnce(makeState({ operationId: "op-2", kind: "backup.restore" }));
    await act(async () => {
      await result.current.start({ kind: "backup.restore" });
    });
    expect(result.current.state?.operationId).toBe("op-2");
    act(() => {
      harness.listener!(makeState({ operationId: "op-1", status: "running" }));
    });
    expect(result.current.state?.operationId).toBe("op-2");
    expect(result.current.state?.status).toBe("running");
  });

  it("快速任务在 start→subscribe 间完成：补偿拉取终态，UI 不卡在运行中", async () => {
    const { result } = renderHook(() => useOperation());
    harness.startOperation = vi.fn().mockResolvedValue(makeState({ operationId: "op-fast", status: "running" }));
    harness.subscribeOperation = vi.fn((_id: string, listener: (state: OperationState) => void) => {
      harness.listener = listener;
      return harness.unsubscribe;
    });
    // 任务在订阅注册前已完成：补偿拉取返回终态。
    harness.getOperationState = vi.fn().mockResolvedValue(
      makeState({ operationId: "op-fast", status: "completed" })
    );
    await act(async () => {
      await result.current.start({ kind: "resource.scan" });
    });
    expect(result.current.state?.status).toBe("completed");
    expect(result.current.isActive).toBe(false);
    // 终态已清理订阅，无需再拉取。
    expect(harness.unsubscribe).toHaveBeenCalledTimes(1);
  });

  it("补偿拉取返回进行中状态时不覆盖当前运行态", async () => {
    const { result } = renderHook(() => useOperation());
    harness.getOperationState = vi.fn().mockResolvedValue(
      makeState({ operationId: "op-1", status: "running", progress: { phase: "copying", completed: 5, total: 10, bytesCompleted: null, bytesTotal: null, indeterminate: false } })
    );
    await act(async () => {
      await result.current.start({ kind: "backup.create" });
    });
    // 非终态的补偿快照不覆盖（可能滞后于事件流的最新状态），UI 保持初始运行态。
    expect(result.current.state?.status).toBe("running");
    expect(result.current.state?.progress.phase).toBe("validating");
    expect(result.current.isActive).toBe(true);
  });
});
