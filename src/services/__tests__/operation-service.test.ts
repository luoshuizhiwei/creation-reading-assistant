// @vitest-environment jsdom
/**
 * operation-service 渲染端 IPC 合同测试。
 * 验证 start / getState / cancel / subscribe 正确转发到 preload `api.operation.*`，
 * 且 subscribe 返回的退订函数即 preload 提供的 unsubscribe（无泄漏、可注销）。
 */
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { OperationState } from "../../types/operation";
import { cancelOperation, getOperationState, startOperation, subscribeOperation } from "../operation-service";

type FakeApi = {
  operation: {
    start: ReturnType<typeof vi.fn>;
    getState: ReturnType<typeof vi.fn>;
    cancel: ReturnType<typeof vi.fn>;
    subscribe: ReturnType<typeof vi.fn>;
  };
};

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

let api: FakeApi;

beforeEach(() => {
  api = {
    operation: {
      start: vi.fn(),
      getState: vi.fn(),
      cancel: vi.fn(),
      subscribe: vi.fn()
    }
  };
  (window as unknown as { api: FakeApi }).api = api;
});

afterEach(() => {
  delete (window as unknown as { api?: FakeApi }).api;
});

describe("operation-service (渲染端 IPC 合同)", () => {
  it("startOperation 转发请求并返回初始状态", async () => {
    const state = makeState();
    api.operation.start.mockResolvedValue(state);

    const result = await startOperation({ kind: "backup.create" });

    expect(api.operation.start).toHaveBeenCalledWith({ kind: "backup.create" });
    expect(result).toBe(state);
  });

  it("startOperation 在主进程取消前置步骤时返回 null", async () => {
    api.operation.start.mockResolvedValue(null);

    expect(await startOperation({ kind: "backup.create" })).toBeNull();
    expect(api.operation.start).toHaveBeenCalledTimes(1);
  });

  it("cancelOperation 转发 operationId", async () => {
    await cancelOperation("op-1");

    expect(api.operation.cancel).toHaveBeenCalledWith("op-1");
  });

  it("getOperationState 转发 operationId", async () => {
    api.operation.getState.mockResolvedValue(null);

    expect(await getOperationState("op-1")).toBeNull();
    expect(api.operation.getState).toHaveBeenCalledWith("op-1");
  });

  it("subscribeOperation 转发 operationId 与监听器，并返回退订函数", async () => {
    const unsubscribe = vi.fn();
    api.operation.subscribe.mockResolvedValue(unsubscribe);
    const listener = vi.fn();

    const result = await subscribeOperation("op-1", listener);

    expect(api.operation.subscribe).toHaveBeenCalledWith("op-1", listener);
    expect(result).toBe(unsubscribe);
  });
});
