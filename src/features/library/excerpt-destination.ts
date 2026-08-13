import type { ExcerptResult, ExcerptSourceSnapshot, ReaderExcerptDestination } from "@/types/library";

/**
 * 资料摘录目的地注入点。
 *
 * library 内的阅读器组件通过 {@link getReaderExcerptDestination} 取得目的地，
 * 不直接依赖共享 IPC。集成端在启动时调用 {@link setReaderExcerptDestination}
 * 注入真实实现（映射到 inboxCreate / card.create 等共享 IPC seam）。
 *
 * 默认实现返回「未接线」错误，确保操作失败时不假成功、用户能收到明确提示。
 */

const UNAVAILABLE_ERROR = "摘录目的地尚未接线：请在集成阶段注入 ReaderExcerptDestination。";

const unavailableDestination: ReaderExcerptDestination = {
  async listProjects() {
    return [];
  },
  async saveToInbox(): Promise<ExcerptResult> {
    return { success: false, error: UNAVAILABLE_ERROR };
  },
  async saveToProjectCard(): Promise<ExcerptResult> {
    return { success: false, error: UNAVAILABLE_ERROR };
  }
};

let currentDestination: ReaderExcerptDestination = unavailableDestination;

/** 注入真实的摘录目的地实现。集成端在应用启动时调用一次。 */
export function setReaderExcerptDestination(destination: ReaderExcerptDestination): void {
  currentDestination = destination;
}

/** 重置为默认的未接线实现。测试间隔离使用。 */
export function resetReaderExcerptDestination(): void {
  currentDestination = unavailableDestination;
}

/** 取得当前摘录目的地。阅读器组件和 hook 通过此函数访问目的地。 */
export function getReaderExcerptDestination(): ReaderExcerptDestination {
  return currentDestination;
}

export type { ExcerptResult, ExcerptSourceSnapshot, ReaderExcerptDestination };
