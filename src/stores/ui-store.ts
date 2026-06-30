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
  showToast: (input: Omit<ToastItem, "id" | "createdAt">) => string;
  dismissToast: (id: string) => void;
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
