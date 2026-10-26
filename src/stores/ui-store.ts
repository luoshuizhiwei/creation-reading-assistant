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

export const useUIStore = create<UIState>((set, get) => ({
  toasts: [],
  routeStatus: "",
  setRouteStatus: (status) => set({ routeStatus: status }),
  showToast: (input) => {
    const id = makeUiId("toast");
    const toast: ToastItem = { id, createdAt: Date.now(), ...input };
    set((state) => ({ toasts: [toast, ...state.toasts].slice(0, 4) }));
    window.setTimeout(() => get().dismissToast(id), input.tone === "error" ? 6500 : 4200);
    return id;
  },
  dismissToast: (id) => set((state) => ({ toasts: state.toasts.filter((toast) => toast.id !== id) })),
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
