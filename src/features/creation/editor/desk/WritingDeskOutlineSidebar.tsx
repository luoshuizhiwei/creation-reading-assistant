import { useCallback, useEffect, useState } from "react";
import { X } from "lucide-react";
import { Select } from "@/components/ui";
import { CardBoard } from "@/features/creation/outline/CardBoard";
import { OutlineTree } from "@/features/creation/outline/OutlineTree";
import { useCreationActions } from "@/hooks/useCreationActions";
import type {
  CreationProjectOutline,
  CreationProjectSummary,
  StructureApplyResult,
  StructureCommand
} from "@/types/creation";

export interface WritingDeskOutlineSidebarProps {
  projects: CreationProjectSummary[];
  project: CreationProjectSummary;
  outline: CreationProjectOutline | undefined;
  workflow: string[];
  selectedSceneId: string | undefined;
  onSelectProject: (projectId: string) => void;
  onSelectScene: (sceneId: string) => void;
}

export function WritingDeskOutlineSidebar({
  projects,
  project,
  outline,
  workflow,
  selectedSceneId,
  onSelectProject,
  onSelectScene
}: WritingDeskOutlineSidebarProps) {
  const {
    loadOutline,
    runStructure,
    previewStructure,
    applyStructureWithProtection,
    revertStructure
  } = useCreationActions();

  const [outlineView, setOutlineView] = useState<"tree" | "board">("tree");
  const [lastProtectedApply, setLastProtectedApply] = useState<StructureApplyResult | null>(null);
  const [revertBusy, setRevertBusy] = useState(false);
  const [revertError, setRevertError] = useState<string | null>(null);

  useEffect(() => {
    // 保护快照只属于创建它的项目；项目切换后不得携带旧项目撤回入口。
    setLastProtectedApply(null);
    setRevertError(null);
  }, [project.id]);

  const runStructureForTree = useCallback(
    async (command: StructureCommand): Promise<boolean> => {
      const ok = await runStructure(command);
      if (ok) void loadOutline(project.id);
      return ok;
    },
    [loadOutline, project.id, runStructure]
  );

  const applyStructureForOutline = useCallback(
    async (command: Parameters<typeof applyStructureWithProtection>[0]) => {
      const result = await applyStructureWithProtection(command);
      if (result) await loadOutline(project.id);
      return result;
    },
    [applyStructureWithProtection, loadOutline, project.id]
  );

  const handleProtectedApplied = useCallback((result: StructureApplyResult) => {
    setLastProtectedApply(result);
    setRevertError(null);
  }, []);

  const revertLastProtectedApply = useCallback(async () => {
    if (!lastProtectedApply || revertBusy) return;
    setRevertBusy(true);
    setRevertError(null);
    try {
      const result = await revertStructure({
        type: "structure.revert",
        projectId: project.id,
        protectionSnapshotId: lastProtectedApply.protectionSnapshotId,
        expectedAppliedRevisions: lastProtectedApply.affected
      });
      if (!result) {
        setRevertError("无法撤回：大纲可能已被后续修改。请保留当前内容并重新检查。");
        return;
      }
      setLastProtectedApply(null);
      await loadOutline(project.id);
    } catch (error) {
      setRevertError(error instanceof Error ? error.message : "无法撤回本次重组。");
    } finally {
      setRevertBusy(false);
    }
  }, [lastProtectedApply, loadOutline, project.id, revertBusy, revertStructure]);

  return (
    <aside className="writing-outline" aria-label="项目大纲">
      <div className="writing-project-switcher">
        <Select
          label="当前项目"
          value={project.id}
          onChange={(event) => onSelectProject(event.target.value)}
          options={projects.map((item) => ({ value: item.id, label: item.title }))}
        />
      </div>
      <div className="writing-outline-view-switch" role="group" aria-label="大纲视图">
        <button
          type="button"
          className={outlineView === "tree" ? "active" : ""}
          onClick={() => setOutlineView("tree")}
        >
          大纲树
        </button>
        <button
          type="button"
          className={outlineView === "board" ? "active" : ""}
          onClick={() => setOutlineView("board")}
        >
          卡片板
        </button>
      </div>
      {lastProtectedApply && (
        <div className="writing-outline-revert" role="status">
          <span>安全重组已应用，并已创建保护快照。</span>
          <div className="writing-outline-revert-actions">
            <button
              type="button"
              aria-label="撤回本次重组"
              disabled={revertBusy}
              onClick={() => void revertLastProtectedApply()}
            >
              {revertBusy ? "撤回中…" : "撤回"}
            </button>
            <button
              type="button"
              aria-label="关闭撤回提示"
              disabled={revertBusy}
              onClick={() => {
                setLastProtectedApply(null);
                setRevertError(null);
              }}
            >
              <X size={12} />
            </button>
          </div>
          {revertError && (
            <span className="writing-outline-revert-error" role="alert">
              {revertError}
            </span>
          )}
        </div>
      )}
      <div className="writing-outline-scroll">
        {outline ? (
          outlineView === "tree" ? (
            <OutlineTree
              outline={outline}
              workflow={workflow}
              selectedSceneId={selectedSceneId}
              onSelectScene={(sceneId) => onSelectScene(sceneId)}
              runStructure={runStructureForTree}
              previewStructure={previewStructure}
              applyStructureWithProtection={applyStructureForOutline}
              onProtectedApplied={handleProtectedApplied}
            />
          ) : (
            <CardBoard
              outline={outline}
              workflow={workflow}
              selectedSceneId={selectedSceneId}
              onSelectScene={(sceneId) => onSelectScene(sceneId)}
              previewStructure={previewStructure}
              applyStructureWithProtection={applyStructureForOutline}
              onProtectedApplied={handleProtectedApplied}
            />
          )
        ) : (
          <p className="outline-loading" role="status">
            正在读取大纲…
          </p>
        )}
      </div>
    </aside>
  );
}
