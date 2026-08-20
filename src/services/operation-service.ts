import { getDesktopApi } from "./ipc-client";
import type { OperationState, OperationStartRequest } from "../types/operation";

/** 启动一个长任务，返回初始运行态（含 operationId）；用户取消前置步骤时返回 null。 */
export function startOperation(request: OperationStartRequest): Promise<OperationState | null> {
  return getDesktopApi().operation.start(request);
}

export function getOperationState(operationId: string): Promise<OperationState | null> {
  return getDesktopApi().operation.getState(operationId);
}

export function cancelOperation(operationId: string): Promise<void> {
  return getDesktopApi().operation.cancel(operationId);
}

/**
 * 订阅某任务的运行态快照，返回退订函数。退订时同时通知主进程注销订阅，避免泄漏。
 * 主进程已按 subscriptionId 过滤，此处再按 operationId 双保险，忽略迟到事件。
 */
export function subscribeOperation(
  operationId: string,
  listener: (state: OperationState) => void
): Promise<() => void> {
  return getDesktopApi().operation.subscribe(operationId, listener);
}
