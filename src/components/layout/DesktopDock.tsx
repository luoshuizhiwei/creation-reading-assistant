import { FolderOpen, MonitorSmartphone, Palette, Save, Smartphone } from "lucide-react";
import { RingButton } from "@/components/interaction";
import { useSettingsActions } from "@/hooks/useSettingsActions";
import { openDataDirectory } from "@/services/maintenance-service";
import {
  mostAttentiveSceneSaveStatus,
  SCENE_SAVE_STATUS_LABEL,
  type SceneSessionStatus
} from "@/features/creation/editor/scene-document-session";
import { useAppStore } from "@/stores/app-store";
import { useCreationStore } from "@/stores/creation-store";
import { useSettingsStore } from "@/stores/settings-store";
import type { AppearanceSettings } from "@/types/settings";

/**
 * 侧栏底部常驻 dock（规格 §4.1 第 1 条）：主题、界面缩放、保存状态、数据路径、同步入口
 * 放在零层级可达的位置，主题切换不必进设置页。
 *
 * dock 与设置页读的是同一份 settings（useSettingsStore），写的是同一条
 * patchSettings → updateSettings 通道；不是 dock 自己存一套偏好。所以在这里
 * 换主题、改缩放，设置页「外观 › 主题与缩放」里的值同步变，反之也一样。
 *
 * 保存状态（第五项，批次 BB 补上）读 creation store 里的 sceneSaveStatuses ——
 * 那是场景编辑器上报的同一份信号，不是 dock 自己猜的：编辑器行内那条状态与这里
 * 必然同进同退。整章连续模式一屏 N 个场景会话，dock 只有一个状态位，取的是
 * 「最需要处理的那一个」（次序见 scene-document-session 的 SCENE_SAVE_STATUS_ATTENTION）。
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

/**
 * 保存态那一行挂的修饰类。写成 7 条字面量而不是 `--${status}` 拼接：
 * 守卫的「宿主谓词」按类名逐字查源码，拼接串只有撞上动态前缀才算活 ——
 * 这里没必要赌那条豁免，字面量是最硬的证据。
 */
const STATUS_CLASS: Record<SceneSessionStatus, string> = {
  idle: "desktop-dock-status--idle",
  composing: "desktop-dock-status--composing",
  dirty: "desktop-dock-status--dirty",
  saving: "desktop-dock-status--saving",
  saved: "desktop-dock-status--saved",
  error: "desktop-dock-status--error",
  conflict: "desktop-dock-status--conflict"
};

export function DesktopDock() {
  const settings = useSettingsStore((state) => state.settings);
  const setScreen = useAppStore((state) => state.setScreen);
  const sceneSaveStatuses = useCreationStore((state) => state.sceneSaveStatuses);
  const { patchSettings } = useSettingsActions();
  const theme = settings?.appearance.theme ?? "system";
  const scale = settings?.appearance.appFontScale ?? 1;
  const dataDirectory = settings?.storage.dataDirectory ?? "";
  const saveStatus = mostAttentiveSceneSaveStatus(Object.values(sceneSaveStatuses));

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
        <span className="desktop-dock-label" title="正文保存状态（§4.1：与编辑器行内那条同一个信号）">
          <Save size={12} aria-hidden="true" />
          保存
        </span>
        {/* 刻意不做 aria-live：编辑器行内那条状态本来就是 live region，
            这里再挂一层会在每次状态变化时重复播报同一句话。dock 的这盏灯是
            「扫一眼」用的常驻读数，读屏用户到侧栏导航时能读到它，不打断输入。 */}
        <span
          className={`desktop-dock-status${saveStatus ? ` ${STATUS_CLASS[saveStatus]}` : " desktop-dock-status--none"}`}
          title={saveStatus ? `正文保存状态：${SCENE_SAVE_STATUS_LABEL[saveStatus]}` : "正文保存状态：当前没有在编辑场景"}
        >
          <span className="desktop-dock-status-dot" aria-hidden="true" />
          {/* 文案只有一处真相：SCENE_SAVE_STATUS_LABEL（编辑器行内那条读的就是它）。
              「未在编辑」是 dock 独有的第 8 种取值，不进那张表，因为它不对应任何会话状态。 */}
          <span className="desktop-dock-status-text">{saveStatus ? SCENE_SAVE_STATUS_LABEL[saveStatus] : "未在编辑"}</span>
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
