import { useEffect, useState, type ReactNode } from "react";
import { AlertCircle } from "lucide-react";
import { ConfirmDialog, PageTransition, ToastCenter } from "@/components/interaction";
import { DesktopFrame } from "@/components/layout/DesktopFrame";
import { CreationProjectsPage } from "@/features/creation/CreationProjectsPage";
import { InboxPage } from "@/features/creation/inbox/InboxPage";
import { InspirationPage } from "@/features/inspiration/InspirationPage";
import { LibraryPage } from "@/features/library/LibraryPage";
import { ReaderPage } from "@/features/library/ReaderPage";
import { ReadingStatsPage } from "@/features/library/ReadingStatsPage";
import { setReaderExcerptDestination } from "@/features/library/excerpt-destination";
import { createReaderExcerptDestination } from "@/features/library/excerpt-destination-impl";
import { SearchPanel } from "@/features/search/SearchPanel";
import { SettingsPage } from "@/features/settings/SettingsPage";
import { useSettingsActions } from "@/hooks/useSettingsActions";
import { getStartupRecovery, markStartupRecoverySeen, writeRendererLog } from "@/services/maintenance-service";
import { useSettingsStore } from "@/stores/settings-store";
import { useCreationStore } from "@/stores/creation-store";
import { useUIStore } from "@/stores/ui-store";
import { useAppStore } from "@/stores/app-store";
import type { AppScreen } from "@/stores/app-store";
import type { StartupRecoveryInfo } from "@/types/maintenance";

/**
 * AppScreen → 正文组件 的穷尽映射：TypeScript 的 Record 强制每个可设置的
 * AppScreen 都有合法渲染路径；新增屏幕而没有补充分支会在编译期直接报错，
 * 不再可能出现「有状态、无正文」的空白页。
 */
export const screenContent: Record<AppScreen, ReactNode> = {
  projects: <CreationProjectsPage />,
  inbox: <InboxPage />,
  inspiration: <InspirationPage />,
  library: <LibraryPage />,
  reader: <ReaderPage />,
  stats: <ReadingStatsPage />,
  settings: <SettingsPage />
};

function RecoveryPrompt({
  info,
  onOpenLibrary,
  onDismiss
}: {
  info: StartupRecoveryInfo;
  onOpenLibrary: () => void;
  onDismiss: () => void;
}) {
  return (
    <div className="absolute inset-0 z-50 grid place-items-center bg-paper-ink/10 px-6 backdrop-blur-[1px]">
      <section className="motion-dialog w-[min(520px,100%)] rounded-2xl border border-paper-line bg-paper-panel p-5 shadow-paper">
        <div className="flex items-start gap-3">
          <div className="rounded-full border border-copper/20 bg-copper/10 p-2 text-copper">
            <AlertCircle size={18} />
          </div>
          <div className="min-w-0 flex-1">
            <h2 className="paper-title text-base font-semibold">已恢复上次阅读会话</h2>
            <p className="mt-2 text-sm leading-6 text-paper-muted">
              检测到上次可能异常退出，已把 {info.recoveredSessionsCount} 个未结束阅读会话安全收尾。你可以直接回到书库继续阅读，
              统计不会把离线时间误算进去。
            </p>
            <div className="mt-4 flex justify-end gap-2">
              <button className="rounded-md px-3 py-2 text-sm text-paper-muted hover:bg-paper-soft/70 hover:text-paper-ink" onClick={onDismiss}>
                留在当前页
              </button>
              <button className="rounded-md bg-copper px-3 py-2 text-sm font-medium text-white shadow-lift hover:bg-copper-dark" onClick={onOpenLibrary}>
                打开书库
              </button>
            </div>
          </div>
        </div>
      </section>
    </div>
  );
}

export default function App() {
  const screen = useAppStore((state) => state.screen);
  const errors = useAppStore((state) => state.errors);
  const clearErrors = useAppStore((state) => state.clearErrors);
  const setError = useAppStore((state) => state.setError);
  const setScreen = useAppStore((state) => state.setScreen);
  const settings = useSettingsStore((state) => state.settings);
  const showToast = useUIStore((state) => state.showToast);
  const setCreationAbnormalExit = useCreationStore((state) => state.setAbnormalExit);
  const [recoveryInfo, setRecoveryInfo] = useState<StartupRecoveryInfo>();
  const { loadSettings } = useSettingsActions();

  useEffect(() => {
    void loadSettings();
    // 注入摘录目的地：映射到 inbox.create 和 card.create IPC。
    setReaderExcerptDestination(createReaderExcerptDestination());
  }, [loadSettings]);

  useEffect(() => {
    if (errors.length === 0) return;
    for (const err of errors) {
      showToast({ tone: "error", title: "发生错误", body: err.message });
    }
    clearErrors();
  }, [errors, clearErrors, showToast]);

  useEffect(() => {
    const root = document.documentElement;
    const applyTheme = () => {
      const selected = settings?.appearance.theme ?? "system";
      const resolved = selected === "system" ? (window.matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light") : selected;
      root.dataset.appTheme = resolved;
      root.style.fontSize = `${Math.round((settings?.appearance.appFontScale ?? 1) * 100)}%`;
    };
    applyTheme();
    const media = window.matchMedia("(prefers-color-scheme: dark)");
    media.addEventListener("change", applyTheme);
    return () => media.removeEventListener("change", applyTheme);
  }, [settings?.appearance.appFontScale, settings?.appearance.theme]);

  useEffect(() => {
    void getStartupRecovery()
      .then(async (info) => {
        setRecoveryInfo(info);
        setCreationAbnormalExit(info.abnormalExit);
        if (info.recoveredSessionsCount > 0) {
          return;
        }
        if (info.abnormalExit) {
          await markStartupRecoverySeen();
        }
      })
      .catch((error) => setError(error instanceof Error ? error.message : String(error)));
  }, [setCreationAbnormalExit, setError]);

  const closeRecoveryPrompt = () => {
    setRecoveryInfo(undefined);
    void markStartupRecoverySeen().catch((error) => setError(error instanceof Error ? error.message : String(error)));
  };

  const openRecoveredLibrary = () => {
    closeRecoveryPrompt();
    setScreen("library");
  };

  useEffect(() => {
    const handleError = (event: ErrorEvent) => {
      void writeRendererLog({
        level: "error",
        message: event.message,
        detail: event.error?.stack ?? `${event.filename}:${event.lineno}:${event.colno}`,
        source: "window.error"
      });
    };
    const handleRejection = (event: PromiseRejectionEvent) => {
      void writeRendererLog({
        level: "error",
        message: "Unhandled promise rejection",
        detail: event.reason instanceof Error ? event.reason.stack ?? event.reason.message : String(event.reason),
        source: "window.unhandledrejection"
      });
    };
    window.addEventListener("error", handleError);
    window.addEventListener("unhandledrejection", handleRejection);
    return () => {
      window.removeEventListener("error", handleError);
      window.removeEventListener("unhandledrejection", handleRejection);
    };
  }, []);

  return (
    <div className="relative h-screen overflow-hidden paper-shell">
      <ToastCenter />
      <ConfirmDialog />
      <SearchPanel />
      {recoveryInfo && recoveryInfo.recoveredSessionsCount > 0 && (
        <RecoveryPrompt info={recoveryInfo} onOpenLibrary={openRecoveredLibrary} onDismiss={closeRecoveryPrompt} />
      )}
      {screen === "reader" ? (
        <PageTransition screenKey={screen}>{screenContent.reader}</PageTransition>
      ) : (
        <DesktopFrame>
          <PageTransition screenKey={screen}>{screenContent[screen]}</PageTransition>
        </DesktopFrame>
      )}
    </div>
  );
}
