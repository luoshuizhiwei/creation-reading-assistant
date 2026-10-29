import { create } from "zustand";

export type ToastTone = "success" | "info" | "warning" | "error";
export type ConfirmTone = "default" | "danger" | "warning";

export interface ToastItem {
  id: string;
  tone: ToastTone;
  title: string;
  body?: string;
  createdAt: number;
}

export interface ConfirmRequest {
  id: string;
  title: string;
  body: string;
  confirmLabel: string;
  cancelLabel: string;
  tone: ConfirmTone;
  resolve: (confirmed: boolean) => void;
}

interface UIState {
  toasts: ToastItem[];
  confirmRequest?: ConfirmRequest;
  /**
   * BD-198：路由级到达播报的总线。到页动作（开书、进统计）的忙态住在「正在挂载
   * 的页面」内部，页面自己插区即插入即哑（BD-175 判据）——播报必须写进 App 根
   * 这枚先于一切屏幕存在的常驻区。约定：每条播报都要有对应的清账（完成/失败/
   * 卸载），文本相同则不换、不换则不重声（BD-196 单一谓词同族）。
   */
  routeStatus: string;
  showToast: (input: Omit<ToastItem, "id" | "createdAt">) => string;
  dismissToast: (id: string) => void;
  /** BD-215（WCAG 2.2.1）：暂停/恢复单条提示的自动消失倒计时（悬停或焦点停留即延长时限）。 */
  pauseToast: (id: string) => void;
  resumeToast: (id: string) => void;
  setRouteStatus: (status: string) => void;
  confirmAction: (input: {
    title: string;
    body: string;
    confirmLabel?: string;
    cancelLabel?: string;
    tone?: ConfirmTone;
  }) => Promise<boolean>;
  resolveConfirm: (confirmed: boolean) => void;
}

const makeUiId = (prefix: string): string => `${prefix}-${Date.now().toString(36)}-${Math.random().toString(16).slice(2, 8)}`;

/**
 * BD-215（WCAG 2.2.1 时限可调）：toast 到点自动消失原是一条无条件 setTimeout，
 * 错误提示里的失败原因 6.5 秒后连读屏带眼睛一起没了，用户既不能关停也不能延长。
 * 这里把倒计时句柄与「还剩多久」留在模块里，供悬停/焦点暂停；恢复时按**剩余时长**
 * 续计——停留期间时钟不走，停留多久就延长多久（2.2.1 的「延长」；关闭键照旧保留）。
 * 用剩余时长而不是重挂完整时长，是为了让键盘焦点只是「路过」提示时，提示不会
 * 因此在页面上多赖一整轮、盖住随后获得焦点的控件（2.4.11 焦点不被遮挡）。
 */
const toastTimers = new Map<string, number>();
const toastRemaining = new Map<string, number>();
const toastDeadlines = new Map<string, number>();

export const useUIStore = create<UIState>((set, get) => ({
  toasts: [],
  routeStatus: "",
  setRouteStatus: (status) => set({ routeStatus: status }),
  showToast: (input) => {
    const id = makeUiId("toast");
    const toast: ToastItem = { id, createdAt: Date.now(), ...input };
    set((state) => ({ toasts: [toast, ...state.toasts].slice(0, 4) }));
    const duration = input.tone === "error" ? 6500 : 4200;
    toastRemaining.set(id, duration);
    toastDeadlines.set(id, Date.now() + duration);
    toastTimers.set(id, window.setTimeout(() => get().dismissToast(id), duration));
    return id;
  },
  pauseToast: (id) => {
    const timer = toastTimers.get(id);
    if (timer === undefined) return;
    window.clearTimeout(timer);
    toastTimers.delete(id);
    const deadline = toastDeadlines.get(id);
    if (deadline === undefined) return;
    toastRemaining.set(id, Math.max(0, deadline - Date.now()));
  },
  resumeToast: (id) => {
    if (toastTimers.has(id)) return;
    if (!get().toasts.some((toast) => toast.id === id)) return;
    const remaining = toastRemaining.get(id);
    if (remaining === undefined) return;
    toastDeadlines.set(id, Date.now() + remaining);
    toastTimers.set(id, window.setTimeout(() => get().dismissToast(id), remaining));
  },
  dismissToast: (id) => {
    const timer = toastTimers.get(id);
    if (timer !== undefined) window.clearTimeout(timer);
    toastTimers.delete(id);
    toastRemaining.delete(id);
    toastDeadlines.delete(id);
    set((state) => ({ toasts: state.toasts.filter((toast) => toast.id !== id) }));
  },
  confirmAction: (input) =>
    new Promise<boolean>((resolve) => {
      const id = makeUiId("confirm");
      set({
        confirmRequest: {
          id,
          title: input.title,
          body: input.body,
          confirmLabel: input.confirmLabel ?? "确认",
          cancelLabel: input.cancelLabel ?? "取消",
          tone: input.tone ?? "default",
          resolve
        }
      });
    }),
  resolveConfirm: (confirmed) => {
    const request = get().confirmRequest;
    if (!request) return;
    request.resolve(confirmed);
    set({ confirmRequest: undefined });
  }
}));
