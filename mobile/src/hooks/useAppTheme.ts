import { useEffect, useState } from "react";
import { loadMobileAppTheme, type MobileAppTheme } from "../utils/mobile-helpers";

/**
 * 应用主题（浅色/深色/跟随系统）。
 * - 挂载到 document.documentElement.dataset.mobileTheme
 * - 持久化到 localStorage
 * - setAppTheme 切换时通过 onMessage 反馈给用户
 */
export function useAppTheme(onMessage: (message: string) => void) {
  const [appTheme, setAppThemeState] = useState<MobileAppTheme>(() => loadMobileAppTheme());

  useEffect(() => {
    document.documentElement.dataset.mobileTheme = appTheme;
    localStorage.setItem("creation-reading-assistant-mobile-theme", appTheme);
  }, [appTheme]);

  const setAppTheme = (theme: MobileAppTheme) => {
    setAppThemeState(theme);
    onMessage(theme === "system" ? "已切换为跟随系统外观。" : theme === "dark" ? "已切换为深色外观。" : "已切换为浅色外观。");
  };

  return { appTheme, setAppTheme };
}
