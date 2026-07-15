import { useEffect, useRef } from "react";
import { App as CapacitorApp } from "@capacitor/app";
import { Capacitor } from "@capacitor/core";

interface AppBackState {
  activeProfilePage: string | undefined;
  confirmDialog: { title: string; message: string; onConfirm: () => void } | null;
  readerBook: unknown;
  showGlobalSearch: boolean;
  showQrScanner: boolean;
  tab: string;
}

interface UseAppBackHandlerOptions {
  /**
   * 持有最新 UI 状态的 ref，避免监听器闭包陈旧。
   * App.tsx 每次渲染后写入此 ref。
   */
  appBackStateRef: React.MutableRefObject<AppBackState>;
  /** 关闭阅读器（与 UI 返回按钮走同一路径，保存进度） */
  closeMobileReader: () => void;
  /** 退出 profile 子页面 */
  onProfileBack: () => void;
  /** 切回首页 tab */
  onGoHome: () => void;
  /** 清理 confirmDialog */
  onConfirmClose: () => void;
  /** 清理 QR 扫码层 */
  onQrClose: () => void;
  /** 关闭全局搜索 overlay */
  onGlobalSearchClose: () => void;
  /** 显示提示文案 */
  setMessage: (message: string) => void;
}

/**
 * Android 硬件返回键优先级处理。
 * 优先级（从高到低）：
 * 1. confirmDialog → 关闭对话框
 * 2. QR 扫码层 → 关闭扫码层
 * 3. readerBook → 派发 mobile-reader-back 事件（由 MobileReaderView 自行处理），未处理则 closeMobileReader
 * 4. profile 子页面 → 退到 profile 主页
 * 5. 非 home tab → 给页面一次机会处理二级面板（mobile-tab-back），未处理则切回首页
 * 6. home → 1.8s 内再按一次退出应用
 *
 * 同时监听自定义事件 mobile-native-back（兼容非 Capacitor 环境）。
 * 用 lastBackHandledAtRef 做 250ms 防抖，避免某些设备重复触发。
 */
export function useAppBackHandler({
  appBackStateRef,
  closeMobileReader,
  onProfileBack,
  onGoHome,
  onConfirmClose,
  onQrClose,
  onGlobalSearchClose,
  setMessage
}: UseAppBackHandlerOptions) {
  const lastBackAtRef = useRef(0);
  const lastBackHandledAtRef = useRef(0);

  useEffect(() => {
    if (!Capacitor.isNativePlatform()) return undefined;
    let removeListener: (() => void) | undefined;

    const handleBackByPriority = () => {
      const state = appBackStateRef.current;
      if (state.confirmDialog) {
        onConfirmClose();
        return;
      }
      if (state.showGlobalSearch) {
        onGlobalSearchClose();
        return;
      }
      if (state.showQrScanner) {
        onQrClose();
        return;
      }
      if (state.readerBook) {
        const readerBackEvent = new Event("mobile-reader-back", { cancelable: true });
        window.dispatchEvent(readerBackEvent);
        if (!readerBackEvent.defaultPrevented) closeMobileReader();
        return;
      }
      if (state.tab === "profile" && state.activeProfilePage) {
        onProfileBack();
        return;
      }
      // 所有 tab 都先给页面一次机会处理二级面板（如书架详情、首页 Bottom Sheet），未处理再执行默认行为
      const tabBackEvent = new Event("mobile-tab-back", { cancelable: true });
      window.dispatchEvent(tabBackEvent);
      if (tabBackEvent.defaultPrevented) return;
      if (state.tab !== "home") {
        onGoHome();
        return;
      }
      const now = Date.now();
      if (now - lastBackAtRef.current < 1800) {
        void CapacitorApp.exitApp();
        return;
      }
      lastBackAtRef.current = now;
      setMessage("再按一次返回键退出应用。");
    };

    const handleMobileBack = (source: "capacitor" | "native") => {
      const now = Date.now();
      if (now - lastBackHandledAtRef.current < 250) return;
      lastBackHandledAtRef.current = now;
      void source;
      handleBackByPriority();
    };

    const handleNativeBack = () => handleMobileBack("native");
    window.addEventListener("mobile-native-back", handleNativeBack);
    void CapacitorApp.addListener("backButton", () => handleMobileBack("capacitor")).then((handle) => {
      removeListener = () => {
        void handle.remove();
      };
    });
    return () => {
      window.removeEventListener("mobile-native-back", handleNativeBack);
      removeListener?.();
    };
  }, [appBackStateRef, closeMobileReader, onProfileBack, onGoHome, onConfirmClose, onQrClose, onGlobalSearchClose, setMessage]);
}
