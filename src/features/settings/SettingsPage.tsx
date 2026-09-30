import { useEffect, useState } from "react";
import { Button } from "@/components/ui";
import { OperationProgressDialog } from "@/features/creation/operation/OperationProgressDialog";
import { useOperation } from "@/hooks/useOperation";
import { useSettingsActions } from "@/hooks/useSettingsActions";
import { useAppStore } from "@/stores/app-store";
import { useSettingsStore } from "@/stores/settings-store";
import { useUIStore } from "@/stores/ui-store";
import type { SettingsSection } from "@/types/settings";
import { SettingsSearch } from "./SettingsSearch";
import { AISection } from "./sections/AISection";
import { AppearanceSection } from "./sections/AppearanceSection";
import { DebugSection } from "./sections/DebugSection";
import { ReaderSection } from "./sections/ReaderSection";
import { SETTINGS_SECTION_LABELS } from "./search-registry";
import { StorageSection } from "./sections/StorageSection";

const CATEGORIES: Array<[SettingsSection, string]> = [
  ["appearance", SETTINGS_SECTION_LABELS.appearance],
  ["reader", SETTINGS_SECTION_LABELS.reader],
  ["ai", SETTINGS_SECTION_LABELS.ai],
  ["storage", SETTINGS_SECTION_LABELS.storage],
  ["debug", SETTINGS_SECTION_LABELS.debug]
];

export function SettingsPage() {
  const [activeCategory, setActiveCategory] = useState<SettingsSection>("appearance");
  const [highlightId, setHighlightId] = useState<string>();
  const settings = useSettingsStore((state) => state.settings);
  const settingsLoading = useSettingsStore((state) => state.loading);
  const settingsError = useSettingsStore((state) => state.error);
  const setError = useAppStore((state) => state.setError);
  const confirmAction = useUIStore((state) => state.confirmAction);
  const showToast = useUIStore((state) => state.showToast);
  const { applySettings, loadSettings, patchSettings, resetSection } = useSettingsActions();
  const operation = useOperation();

  useEffect(() => {
    void loadSettings();
  }, [loadSettings]);

  // 搜索跳转：分区切换渲染完成后，滚动到目标控件并短暂高亮。
  useEffect(() => {
    if (!highlightId) return;
    const element = document.querySelector(`[data-setting-id="${CSS.escape(highlightId)}"]`);
    element?.scrollIntoView({ behavior: "smooth", block: "center" });
    element?.classList.add("settings-search-hit");
    const timer = window.setTimeout(() => {
      element?.classList.remove("settings-search-hit");
      setHighlightId(undefined);
    }, 2200);
    return () => {
      window.clearTimeout(timer);
      element?.classList.remove("settings-search-hit");
    };
  }, [highlightId, activeCategory]);

  if (!settings) {
    return (
      <div className="desktop-panel-card desktop-empty-wrap h-full">
        <div className="grid h-full place-items-center text-sm text-paper-muted">
          {/* 批次 C：与 ErrorBoundary 的兜底岛同一处收口。Tailwind 的 red-* 不随主题
              翻转（夜校把晨校的浅岛原样画在深色面板上），换成 --proof-mark / --proof-tint
              令牌族，岛内文字实算晨 4.85 / 夜 5.11。边框不能写 border-[color:var(--x)]/30
              ——Tailwind 给 var() 套 alpha 修饰符时整条声明会被静默丢弃，所以用 color-mix。 */}
          {settingsError ? (
            <div className="max-w-md rounded-2xl border border-[color:color-mix(in_srgb,var(--proof-mark)_30%,var(--separator))] bg-[color:var(--proof-tint)] p-5 text-center text-[color:var(--proof-mark)]">
              <div className="text-base font-semibold">设置读取失败</div>
              <p className="mt-2 text-sm leading-6">{settingsError}</p>
              <Button className="mt-4" disabled={settingsLoading} onClick={() => void loadSettings()}>
                {settingsLoading ? "正在重试..." : "重试读取设置"}
              </Button>
            </div>
          ) : (
            <div>{settingsLoading ? "正在读取设置..." : "设置尚未加载。"}</div>
          )}
        </div>
      </div>
    );
  }

  return (
    <div className="desktop-page-scroll paper-shell">
      <div className="desktop-page-stack">
        <div className="settings-layout">
          <nav className="settings-nav" aria-label="设置分类">
            <SettingsSearch
              onSelect={(entry) => {
                setActiveCategory(entry.section);
                setHighlightId(entry.id);
              }}
            />
            {CATEGORIES.map(([section, label]) => (
              <button
                key={section}
                type="button"
                className={activeCategory === section ? "active" : ""}
                aria-current={activeCategory === section ? "true" : undefined}
                onClick={() => setActiveCategory(section)}
              >
                {label}
              </button>
            ))}
          </nav>
          <div className="settings-forms">
            {activeCategory === "appearance" && (
              <AppearanceSection settings={settings} patchSettings={patchSettings} resetSection={resetSection} />
            )}

            {activeCategory === "reader" && (
              <ReaderSection
                settings={settings}
                patchSettings={patchSettings}
                applySettings={applySettings}
                loadSettings={loadSettings}
                resetSection={resetSection}
                showToast={showToast}
                setError={setError}
              />
            )}

            {activeCategory === "ai" && (
              <AISection
                settings={settings}
                patchSettings={patchSettings}
                loadSettings={loadSettings}
                resetSection={resetSection}
                showToast={showToast}
                setError={setError}
              />
            )}

            {activeCategory === "storage" && (
              <StorageSection
                settings={settings}
                patchSettings={patchSettings}
                loadSettings={loadSettings}
                resetSection={resetSection}
                operation={operation}
                confirmAction={confirmAction}
                showToast={showToast}
                setError={setError}
              />
            )}

            {activeCategory === "debug" && (
              <div className="settings-wide">
                <DebugSection settings={settings} resetSection={resetSection} showToast={showToast} setError={setError} />
              </div>
            )}

            {operation.state && (
              <OperationProgressDialog
                state={operation.state}
                isCommitting={operation.isCommitting}
                onCancel={operation.cancel}
                onClose={operation.reset}
                onReset={operation.retry}
              />
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
