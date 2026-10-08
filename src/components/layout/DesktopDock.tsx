import { FolderOpen, MonitorSmartphone, Palette, Smartphone } from "lucide-react";
import { RingButton } from "@/components/interaction";
import { useSettingsActions } from "@/hooks/useSettingsActions";
import { openDataDirectory } from "@/services/maintenance-service";
import { useAppStore } from "@/stores/app-store";
import { useSettingsStore } from "@/stores/settings-store";
import type { AppearanceSettings } from "@/types/settings";

/**
 * 侧栏底部常驻 dock（规格 §4.1 第 1 条）：主题、界面缩放、数据路径、同步入口
 * 放在零层级可达的位置，主题切换不必进设置页。
 *
 * 五条里这一批只做四条。「保存状态」刻意缺席，原因记在这里免得被当成漏做：
 * 正文的保存态（已保存 / 未保存 / 正在保存 / 失败 / 冲突）现在是
 * SceneEditor 内部 useState 里的 session.status，既不在 store 也没有事件广播，
 * 应用壳拿不到它。dock 要显示的就得先把这个信号提升到 store —— 那是结构改动，
 * 不该夹在一批加界面的提交里。
 *
 * dock 与设置页读的是同一份 settings（useSettingsStore），写的是同一条
 * patchSettings → updateSettings 通道；不是 dock 自己存一套偏好。所以在这里
 * 换主题、改缩放，设置页「外观 › 主题与缩放」里的值同步变，反之也一样。
 *
 * ⚠ 类名新增不改名：desktop-sidebar / desktop-nav / nav-spine-item 等被断言的
 *   类名一个字没动，dock 是往侧栏末尾**追加**的一节（第 9 步的硬约束）。
 */

const THEME_DOTS: Array<{ value: AppearanceSettings["theme"]; label: string; swatch: string }> = [
  // swatch 是色点本身的填色，走语义令牌而非字面量：浅色/深色两套主题下都要能看清。
  { value: "light", label: "浅色", swatch: "var(--surface-1)" },
  { value: "dark", label: "深色", swatch: "var(--text-primary)" },
  { value: "system", label: "跟随系统", swatch: "var(--separator)" }
];

const SCALE_MIN = 0.85;
const SCALE_MAX = 1.4;
const SCALE_STEP = 0.05;
/** 步进范围与设置页 AppearanceSection 那只滑杆一致（min/max/step 同源，别各自记一套）。 */

const clampScale = (value: number): number => Math.min(SCALE_MAX, Math.max(SCALE_MIN, Math.round(value * 100) / 100));

export function DesktopDock() {
  const settings = useSettingsStore((state) => state.settings);
  const setScreen = useAppStore((state) => state.setScreen);
  const { patchSettings } = useSettingsActions();
  const theme = settings?.appearance.theme ?? "system";
  const scale = settings?.appearance.appFontScale ?? 1;
  const dataDirectory = settings?.storage.dataDirectory ?? "";

  const stepScale = (direction: -1 | 1) => {
    const next = clampScale(scale + direction * SCALE_STEP);
    // 已到档位上下限时不写盘：夹完还是原值，发出去就是一次无意义的设置更新
    // （updateSettings 会整份重写并刷新 updatedAt，还会让设置页的滑杆闪一下）。
    // 顺带把浮点脏值归位：store 里若存着 0.8999999999，next 与它不等，会被写成 0.9。
    if (next === scale) return;
    void patchSettings({ appearance: { appFontScale: next } });
  };

  return (
    <section className="desktop-dock" aria-label="常驻工具坞">
      <div className="desktop-dock-row">
        <span className="desktop-dock-label" title="主题（§4.1：主题切换不进设置页）">
          <Palette size={12} aria-hidden="true" />
          主题
        </span>
        <span className="desktop-dock-themes" role="group" aria-label="应用主题">
          {THEME_DOTS.map((dot) => (
            <RingButton
              key={dot.value}
              type="button"
              className={`desktop-dock-dot${theme === dot.value ? " active" : ""}`}
              aria-label={`主题：${dot.label}`}
              title={dot.label}
              aria-pressed={theme === dot.value}
              onClick={() => void patchSettings({ appearance: { theme: dot.value } })}
            >
              <span className="desktop-dock-swatch" style={{ background: dot.swatch }} />
            </RingButton>
          ))}
        </span>
      </div>

      <div className="desktop-dock-row">
        <span className="desktop-dock-label" title="界面缩放：整档界面字号，0.85–1.4">
          <MonitorSmartphone size={12} aria-hidden="true" />
          缩放
        </span>
        <span className="desktop-dock-scale" role="group" aria-label="界面缩放">
          <RingButton type="button" className="desktop-dock-step" aria-label="缩小界面" title="缩小界面" onClick={() => stepScale(-1)}>
            −
          </RingButton>
          <span className="desktop-dock-value" aria-live="polite">
            {Math.round(scale * 100)}%
          </span>
          <RingButton type="button" className="desktop-dock-step" aria-label="放大界面" title="放大界面" onClick={() => stepScale(1)}>
            +
          </RingButton>
        </span>
      </div>

      <div className="desktop-dock-row">
        <RingButton
          type="button"
          className="desktop-dock-entry"
          aria-label={`打开数据目录：${dataDirectory || "未设置"}`}
          title={dataDirectory || "数据目录尚未就绪"}
          onClick={() => {
            void openDataDirectory().catch(() => {
              /* 非 Electron 环境（单测里的 jsdom）没有 window.api：静默，不打断界面。 */
            });
          }}
        >
          <FolderOpen size={12} aria-hidden="true" />
          <span className="desktop-dock-entry-text">数据目录</span>
        </RingButton>
        <RingButton
          type="button"
          className="desktop-dock-entry"
          aria-label="同步设置"
          title="局域网同步：去设置的「数据与存储」"
          onClick={() => setScreen("settings")}
        >
          <Smartphone size={12} aria-hidden="true" />
          <span className="desktop-dock-entry-text">同步</span>
        </RingButton>
      </div>
    </section>
  );
}
