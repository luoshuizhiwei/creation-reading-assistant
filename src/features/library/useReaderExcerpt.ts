import { useCallback, useRef, useState } from "react";
import type { ExcerptResult, ExcerptSourceSnapshot, ExcerptTarget } from "@/types/library";
import { getReaderExcerptDestination } from "./excerpt-destination";
import {
  buildExcerptSource,
  capExcerpt,
  EXCERPT_DEDUP_WINDOW_MS,
  ExcerptBuildContext,
  isEmptyExcerpt,
  isDuplicateExcerpt,
  isValidExcerptTarget,
  makeExcerptSignature
} from "./excerpt-source";

interface LastExcerptRecord {
  signature: string;
  at: number;
}

export interface UseReaderExcerptResult {
  /** 是否正在提交摘录（用于禁用按钮，防止重复点击） */
  isSubmitting: boolean;
  /** 摘录选择器是否展开 */
  isPickerOpen: boolean;
  /** 可选的目标项目列表 */
  projects: Array<{ id: string; title: string }>;
  /** 当前待提交的来源快照（选择器打开时非空） */
  pendingSource: ExcerptSourceSnapshot | undefined;
  /** 打开摘录选择器：校验选文、加载项目列表 */
  openPicker: (context: ExcerptBuildContext) => Promise<void>;
  /** 关闭选择器，取消摘录 */
  closePicker: () => void;
  /**
   * 提交摘录到指定目标。内含双重提交防护和失败不假成功保证。
   * 返回的 ExcerptResult.success=false 时会附带 error。
   */
  submit: (target: ExcerptTarget) => Promise<ExcerptResult>;
}

/**
 * 资料摘录 hook。
 *
 * 职责：
 * 1. 校验选文非空（空选文给出明确处理，不调用目的地）。
 * 2. 防止快速重复摘录（签名 + 时间窗口去重）。
 * 3. 提交期间禁用按钮（isSubmitting）。
 * 4. 失败时返回 success=false + error，绝不假成功。
 * 5. 不创建旧灵感（不依赖 inspiration-service）。
 * 6. 不修改阅读器滚动/CFI 位置（位置恢复不受影响）。
 */
export function useReaderExcerpt(): UseReaderExcerptResult {
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isPickerOpen, setIsPickerOpen] = useState(false);
  const [projects, setProjects] = useState<Array<{ id: string; title: string }>>([]);
  const [pendingSource, setPendingSource] = useState<ExcerptSourceSnapshot | undefined>(undefined);
  const lastRef = useRef<LastExcerptRecord | undefined>(undefined);
  const inFlightRef = useRef(false);

  const openPicker = useCallback(async (context: ExcerptBuildContext) => {
    if (isEmptyExcerpt(context.excerpt)) {
      return;
    }
    const source = buildExcerptSource(context);
    setPendingSource(source);
    setIsPickerOpen(true);
    try {
      const destination = getReaderExcerptDestination();
      const list = await destination.listProjects();
      setProjects(list);
    } catch {
      // 列表加载失败不阻塞选择器，用户仍可选「全局收件箱」
      setProjects([]);
    }
  }, []);

  const closePicker = useCallback(() => {
    setIsPickerOpen(false);
    setPendingSource(undefined);
  }, []);

  const submit = useCallback(
    async (target: ExcerptTarget): Promise<ExcerptResult> => {
      const source = pendingSource;
      if (!source) {
        return { success: false, error: "没有待摘录的内容。" };
      }
      if (!isValidExcerptTarget(target)) {
        return { success: false, error: "摘录目标无效。" };
      }
      // 双重提交防护：窗口内相同签名直接拒绝
      if (isDuplicateExcerpt(source, lastRef.current)) {
        return { success: false, error: "已摘录同一段内容，请勿重复操作。" };
      }
      // 并发防护：提交进行中拒绝再次提交
      if (inFlightRef.current) {
        return { success: false, error: "正在提交，请稍候。" };
      }

      inFlightRef.current = true;
      setIsSubmitting(true);
      try {
        const destination = getReaderExcerptDestination();
        let result: ExcerptResult;
        if (target.kind === "inbox") {
          result = await destination.saveToInbox(source);
        } else {
          result = await destination.saveToProjectCard(target.projectId, source);
        }
        // 只有真正成功才记录签名，避免后续合法重试被误判为重复
        if (result.success) {
          lastRef.current = {
            signature: makeExcerptSignature(source),
            at: Date.now()
          };
        }
        // 防御性归一化：目的地忘记返回 success 时视为失败
        if (result.success !== true) {
          return {
            success: false,
            error: result.error ?? "摘录失败，请稍后重试。"
          };
        }
        return result;
      } catch (error) {
        return {
          success: false,
          error: error instanceof Error ? error.message : String(error)
        };
      } finally {
        inFlightRef.current = false;
        setIsSubmitting(false);
      }
    },
    [pendingSource]
  );

  return { isSubmitting, isPickerOpen, projects, pendingSource, openPicker, closePicker, submit };
}

export { buildExcerptSource, capExcerpt, isEmptyExcerpt, isDuplicateExcerpt, makeExcerptSignature, EXCERPT_DEDUP_WINDOW_MS };
export type { ExcerptBuildContext, ExcerptResult, ExcerptSourceSnapshot, ExcerptTarget };
