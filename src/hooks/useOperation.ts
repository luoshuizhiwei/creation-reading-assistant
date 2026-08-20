import { useCallback, useEffect, useRef, useState } from "react";
import type { OperationState, OperationStartRequest, OperationStatus } from "../types/operation";
import { cancelOperation, getOperationState, startOperation, subscribeOperation } from "../services/operation-service";

const TERMINAL_STATUSES: ReadonlyArray<OperationStatus> = ["completed", "cancelled", "failed"];

export interface UseOperationResult {
  state: OperationState | null;
  operationId: string | null;
  /** 是否仍在运行（未达终态），用于禁用触发按钮、显示关闭态等。 */
  isActive: boolean;
  /** 当前是否为不可取消的提交阶段（UI 据此禁用取消）。 */
  isCommitting: boolean;
  error: { code: string; message: string } | null;
  /** 启动任务；若已有进行中任务则拒绝（防止重复启动危险任务）。 */
  start: (request: OperationStartRequest) => Promise<void>;
  /** 请求取消（仅可中断阶段有效，不可中断阶段会被延迟到安全边界）。 */
  cancel: () => void;
  /** 清空调试态，便于重试；不取消进行中的任务。 */
  reset: () => void;
  /** 以最后一次启动的请求重新执行（仅终态可用，避免打断运行中的任务）。 */
  retry: () => Promise<void>;
}

/**
 * 管理单个长任务的生命周期：
 * - operationId 贯穿 start → subscribe → cancel；
 * - 订阅按 operationId 过滤，忽略迟到事件（双保险于主进程 subscriptionId 过滤）；
 * - 卸载时只退订，不取消任务（不可中断提交阶段不能被强杀）。
 */
export function useOperation(): UseOperationResult {
  const [state, setState] = useState<OperationState | null>(null);
  const [operationId, setOperationId] = useState<string | null>(null);

  const unsubscribeRef = useRef<(() => void) | null>(null);
  const activeOpIdRef = useRef<string | null>(null);
  const statusRef = useRef<OperationStatus | null>(null);
  const startingRef = useRef(false);
  const lastRequestRef = useRef<OperationStartRequest | null>(null);

  const cleanupSubscription = useCallback(() => {
    if (unsubscribeRef.current) {
      unsubscribeRef.current();
      unsubscribeRef.current = null;
    }
  }, []);

  // 卸载时只退订，不取消任务（不可中断提交阶段不能被强杀）。
  useEffect(() => cleanupSubscription, [cleanupSubscription]);

  const start = useCallback(
    async (request: OperationStartRequest) => {
      if (startingRef.current) {
        throw new Error("已有进行中的任务，不能重复启动。");
      }
      if (activeOpIdRef.current && statusRef.current && !TERMINAL_STATUSES.includes(statusRef.current)) {
        throw new Error("已有进行中的任务，不能重复启动。");
      }
      startingRef.current = true;
      try {
        cleanupSubscription();
        setState(null);
        lastRequestRef.current = request;
        const initial = await startOperation(request);
        if (!initial) return; // 用户取消目录选择等前置步骤
        activeOpIdRef.current = initial.operationId;
        statusRef.current = initial.status;
        setOperationId(initial.operationId);
        setState(initial);
        const unsubscribe = await subscribeOperation(initial.operationId, (next) => {
          if (next.operationId !== activeOpIdRef.current) return; // operationId 隔离
          statusRef.current = next.status;
          setState(next);
          if (TERMINAL_STATUSES.includes(next.status)) cleanupSubscription();
        });
        unsubscribeRef.current = unsubscribe;
        // 兜底：快速任务可能在 start → subscribe 两次 IPC 之间完成，终态事件已先于
        // listener 注册发出；主进程终态缓存 30 秒，此处补偿拉取一次，避免 UI 卡在运行中。
        const latest = await getOperationState(initial.operationId);
        if (latest && latest.operationId === activeOpIdRef.current && TERMINAL_STATUSES.includes(latest.status)) {
          statusRef.current = latest.status;
          setState(latest);
          cleanupSubscription();
        }
      } finally {
        startingRef.current = false;
      }
    },
    [cleanupSubscription]
  );

  const cancel = useCallback(() => {
    if (activeOpIdRef.current) void cancelOperation(activeOpIdRef.current);
  }, []);

  const reset = useCallback(() => {
    cleanupSubscription();
    activeOpIdRef.current = null;
    statusRef.current = null;
    lastRequestRef.current = null;
    setOperationId(null);
    setState(null);
  }, [cleanupSubscription]);

  const retry = useCallback(async () => {
    if (!lastRequestRef.current) return;
    if (activeOpIdRef.current && statusRef.current && !TERMINAL_STATUSES.includes(statusRef.current)) return;
    await start(lastRequestRef.current);
  }, [start]);

  const isActive = operationId !== null && state !== null && !TERMINAL_STATUSES.includes(state.status);
  const isCommitting =
    state !== null &&
    state.status === "running" &&
    (state.progress.phase === "database" || state.progress.phase === "committing");

  return { state, operationId, isActive, isCommitting, error: state?.error ?? null, start, cancel, reset, retry };
}
