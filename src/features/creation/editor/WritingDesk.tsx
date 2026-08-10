import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { ChevronDown, ChevronRight, Eye, FileText, Radio, X } from "lucide-react";
import { InlineNotice } from "@/components/interaction";
import { SceneEditor, type SceneEditorHandle } from "@/features/creation/editor/SceneEditor";
import { useCreationActions } from "@/hooks/useCreationActions";
import { useCreationStore } from "@/stores/creation-store";
import { useUIStore } from "@/stores/ui-store";
import type { CreationProjectNavigation, CreationProjectSummary } from "@/types/creation";

interface WritingDeskProps {
  projects: CreationProjectSummary[];
  project: CreationProjectSummary;
  navigation: CreationProjectNavigation;
  onSelectProject(projectId: string): void;
}

export function WritingDesk({ projects, project, navigation, onSelectProject }: WritingDeskProps) {
  const selectedSceneId = useCreationStore((state) => state.selectedSceneId);
  const sceneViews = useCreationStore((state) => state.sceneViews);
  const abnormalExit = useCreationStore((state) => state.abnormalExit);
  const recoveryNoticeDismissed = useCreationStore((state) => state.recoveryNoticeDismissed);
  const watchConnected = useCreationStore((state) => state.watchConnected);
  const selectScene = useCreationStore((state) => state.selectScene);
  const dismissRecoveryNotice = useCreationStore((state) => state.dismissRecoveryNotice);
  const setLeaveGuard = useCreationStore((state) => state.setLeaveGuard);
  const { loadScene, saveSceneBody, subscribeProject } = useCreationActions();
  const showToast = useUIStore((state) => state.showToast);
  const editorRef = useRef<SceneEditorHandle>(null);
  const [expandedChapters, setExpandedChapters] = useState<Set<string>>(() => new Set(navigation.chapters.map((chapter) => chapter.id)));
  const [focusMode, setFocusMode] = useState(() => window.matchMedia("(max-width: 920px)").matches);
  const [typewriter, setTypewriter] = useState(false);
  const [characterCount, setCharacterCount] = useState(0);

  const selectedScene = useMemo(
    () => navigation.chapters.flatMap((chapter) => chapter.scenes).find((scene) => scene.id === selectedSceneId),
    [navigation, selectedSceneId]
  );
  const selectedChapter = useMemo(
    () => navigation.chapters.find((chapter) => chapter.scenes.some((scene) => scene.id === selectedSceneId)),
    [navigation, selectedSceneId]
  );
  const sceneView = selectedSceneId ? sceneViews[selectedSceneId] : undefined;

  useEffect(() => {
    setExpandedChapters(new Set(navigation.chapters.map((chapter) => chapter.id)));
  }, [navigation.project.id]);

  useEffect(() => {
    if (!selectedSceneId || sceneViews[selectedSceneId]) return;
    void loadScene(selectedSceneId);
  }, [loadScene, sceneViews, selectedSceneId]);

  useEffect(() => subscribeProject(project.id), [project.id, subscribeProject]);

  const saveBeforeLeaving = useCallback(async (): Promise<boolean> => {
    if (!editorRef.current?.isDirty()) return true;
    const saved = await editorRef.current.saveNow();
    if (!saved) {
      showToast({ tone: "warning", title: "正文尚未保存", body: "请解决保存失败或正文冲突后再切换场景。" });
    }
    return saved;
  }, [showToast]);

  useEffect(() => {
    setLeaveGuard(saveBeforeLeaving);
    return () => {
      if (useCreationStore.getState().leaveGuard === saveBeforeLeaving) setLeaveGuard(undefined);
    };
  }, [saveBeforeLeaving, setLeaveGuard]);

  const chooseScene = async (sceneId: string) => {
    if (sceneId === selectedSceneId) return;
    if (!(await saveBeforeLeaving())) return;
    selectScene(sceneId);
    await loadScene(sceneId);
  };

  const chooseProject = async (projectId: string) => {
    if (projectId === project.id) return;
    if (!(await saveBeforeLeaving())) return;
    onSelectProject(projectId);
  };

  return (
    <section className={`writing-desk ${focusMode ? "writing-desk--focus" : ""}`} aria-label="正文写作台">
      <aside className="writing-outline" aria-label="项目与场景导航">
        <label className="writing-project-switcher">
          <span>当前项目</span>
          <select value={project.id} onChange={(event) => void chooseProject(event.target.value)}>
            {projects.map((item) => <option key={item.id} value={item.id}>{item.title}</option>)}
          </select>
        </label>
        <div className="writing-outline-scroll">
          {navigation.chapters.map((chapter) => {
            const expanded = expandedChapters.has(chapter.id);
            return (
              <section className="writing-chapter" key={chapter.id}>
                <button
                  type="button"
                  className="writing-chapter-button"
                  aria-expanded={expanded}
                  onClick={() => setExpandedChapters((current) => {
                    const next = new Set(current);
                    if (expanded) next.delete(chapter.id); else next.add(chapter.id);
                    return next;
                  })}
                >
                  {expanded ? <ChevronDown size={14} /> : <ChevronRight size={14} />}
                  <strong>{chapter.title}</strong>
                  <span>{chapter.scenes.length}</span>
                </button>
                {expanded && (
                  <div className="writing-scene-list">
                    {chapter.scenes.map((scene) => (
                      <button
                        key={scene.id}
                        type="button"
                        className={`writing-scene-button ${scene.id === selectedSceneId ? "active" : ""}`}
                        aria-pressed={scene.id === selectedSceneId}
                        onClick={() => void chooseScene(scene.id)}
                      >
                        <FileText size={13} />
                        <span>{scene.title}</span>
                        <small>r{scene.revision}</small>
                      </button>
                    ))}
                  </div>
                )}
              </section>
            );
          })}
        </div>
      </aside>

      <main className="writing-manuscript">
        <header className="writing-manuscript-head">
          <div>
            <p className="desktop-card-label">Manuscript</p>
            <h2>{selectedScene?.title ?? "选择场景"}</h2>
            <span>{selectedChapter?.title ?? project.title} · 单场景编辑</span>
          </div>
          <div className={`writing-watch ${watchConnected ? "connected" : ""}`} title={watchConnected ? "已订阅项目变更" : "正在连接项目变更"}>
            <Radio size={12} /> {watchConnected ? "变更已连接" : "连接中"}
          </div>
        </header>

        {abnormalExit && !recoveryNoticeDismissed && (
          <InlineNotice tone="warning" className="writing-recovery-notice">
            <span>已恢复最后确认保存的正文；异常退出前不足一秒的未提交输入可能未保存。</span>
            <button type="button" onClick={dismissRecoveryNotice} aria-label="关闭恢复说明"><X size={14} /></button>
          </InlineNotice>
        )}

        <div className="writing-scroll">
          {selectedSceneId && sceneView ? (
            <SceneEditor
              ref={editorRef}
              view={sceneView}
              onSave={saveSceneBody}
              onReloadScene={() => loadScene(selectedSceneId)}
              onStatsChange={setCharacterCount}
              focusMode={focusMode}
              onToggleFocusMode={() => setFocusMode((value) => !value)}
              typewriter={typewriter}
              onToggleTypewriter={() => setTypewriter((value) => !value)}
            />
          ) : (
            <div className="scene-editor-placeholder">{selectedSceneId ? "正在读取场景正文…" : "这个项目还没有可编辑场景。"}</div>
          )}
        </div>
      </main>

      <aside className="writing-margin" aria-label="场景信息">
        <p className="desktop-card-label">Margin notes</p>
        <h3>场景信息</h3>
        <dl>
          <div><dt>所属章节</dt><dd>{selectedChapter?.title ?? "—"}</dd></div>
          <div><dt>场景修订</dt><dd>r{sceneView?.revision ?? selectedScene?.revision ?? 0}</dd></div>
          <div><dt>非空白字符</dt><dd>{characterCount.toLocaleString("zh-CN")}</dd></div>
          <div><dt>保存方式</dt><dd>停止输入 800ms 后自动保存</dd></div>
        </dl>
        <div className="writing-margin-rule" />
        <p className="writing-boundary"><Eye size={14} /> 当前只编辑单一场景。多场景连续聚合、卡片引用和批注将在后续切片接入。</p>
      </aside>
    </section>
  );
}
