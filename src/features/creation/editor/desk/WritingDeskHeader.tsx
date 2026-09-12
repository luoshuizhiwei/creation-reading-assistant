import { PanelRightOpen, Radio } from "lucide-react";
import { useCreationStore } from "@/stores/creation-store";
import type { CreationProjectSummary } from "@/types/creation";

export interface WritingDeskHeaderProps {
  editMode: "scene" | "continuous";
  selectedChapter?: { title: string };
  selectedScene?: { title: string };
  project: CreationProjectSummary;
  onToggleEditMode: () => void;
  quickReferenceOpen: boolean;
  onToggleQuickReference: () => void;
}

export function WritingDeskHeader({
  editMode,
  selectedChapter,
  selectedScene,
  project,
  onToggleEditMode,
  quickReferenceOpen,
  onToggleQuickReference
}: WritingDeskHeaderProps) {
  const watchConnected = useCreationStore((state) => state.watchConnected);

  const title =
    editMode === "continuous"
      ? selectedChapter
        ? selectedChapter.title
        : "整章连续编辑"
      : selectedScene?.title ?? "选择场景";

  const subtitle = `${selectedChapter?.title ?? project.title} · ${
    editMode === "continuous" ? "整章连续编辑（可逐场景编辑）" : "逐场景编辑"
  }`;

  return (
    <header className="writing-manuscript-head">
      <div>
        <p className="desktop-card-label">Manuscript</p>
        <h2>{title}</h2>
        <span>{subtitle}</span>
      </div>
      <div className="writing-head-actions">
        <button
          type="button"
          className={`writing-quick-toggle ${quickReferenceOpen ? "active" : ""}`}
          aria-pressed={quickReferenceOpen}
          onClick={onToggleQuickReference}
          title="打开写作速查（Ctrl+Shift+K）"
        >
          <PanelRightOpen size={14} /> 速查 <kbd>Ctrl⇧K</kbd>
        </button>
        <div className="writing-mode-switch" role="group" aria-label="写作模式切换">
          <button
            type="button"
            className={editMode === "scene" ? "active" : ""}
            aria-pressed={editMode === "scene"}
            onClick={() => {
              if (editMode !== "scene") void onToggleEditMode();
            }}
            title="逐场景编辑：一次编辑一个场景"
          >
            逐场景
          </button>
          <button
            type="button"
            className={editMode === "continuous" ? "active" : ""}
            aria-pressed={editMode === "continuous"}
            onClick={() => {
              if (editMode !== "continuous") void onToggleEditMode();
            }}
            title="整章连续编辑：当前章节所有场景连续排列、各自可编辑"
          >
            整章连续
          </button>
        </div>
        <div
          className={`writing-watch ${watchConnected ? "connected" : ""}`}
          title={watchConnected ? "已订阅项目变更" : "正在连接项目变更"}
        >
          <Radio size={12} /> {watchConnected ? "变更已连接" : "连接中"}
        </div>
      </div>
    </header>
  );
}
