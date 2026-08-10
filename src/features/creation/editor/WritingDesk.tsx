import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Eye, MessageSquarePlus, Radio, Trash2, X } from "lucide-react";
import { InlineNotice } from "@/components/interaction";
import { SceneEditor, type SceneEditorHandle } from "@/features/creation/editor/SceneEditor";
import { CardBoard } from "@/features/creation/outline/CardBoard";
import { OutlineTree } from "@/features/creation/outline/OutlineTree";
import { useCreationActions } from "@/hooks/useCreationActions";
import { useCreationStore } from "@/stores/creation-store";
import { useUIStore } from "@/stores/ui-store";
import type { Annotation, CreationProjectNavigation, CreationProjectSummary, StructureCommand } from "@/types/creation";

/** 写作会话：空闲超过该时长（毫秒）即结算并上报当前段。 */
const SESSION_IDLE_TIMEOUT_MS = 5 * 60 * 1000;

interface WritingDeskProps {
  projects: CreationProjectSummary[];
  project: CreationProjectSummary;
  navigation: CreationProjectNavigation;
  onSelectProject(projectId: string): void;
}

export function WritingDesk({ projects, project, navigation, onSelectProject }: WritingDeskProps) {
  const selectedSceneId = useCreationStore((state) => state.selectedSceneId);
  const sceneViews = useCreationStore((state) => state.sceneViews);
  const outlines = useCreationStore((state) => state.outlines);
  const abnormalExit = useCreationStore((state) => state.abnormalExit);
  const recoveryNoticeDismissed = useCreationStore((state) => state.recoveryNoticeDismissed);
  const watchConnected = useCreationStore((state) => state.watchConnected);
  const selectScene = useCreationStore((state) => state.selectScene);
  const dismissRecoveryNotice = useCreationStore((state) => state.dismissRecoveryNotice);
  const setLeaveGuard = useCreationStore((state) => state.setLeaveGuard);
  const { loadOutline, loadScene, runStructure, saveSceneBody, subscribeProject, reportSession, loadAnnotations, createAnnotation, updateAnnotation, deleteAnnotation, loadCards, loadProjectExport } = useCreationActions();
  const showToast = useUIStore((state) => state.showToast);
  const editorRef = useRef<SceneEditorHandle>(null);
  const [outlineView, setOutlineView] = useState<"tree" | "board">("tree");
  const [focusMode, setFocusMode] = useState(() => window.matchMedia("(max-width: 920px)").matches);
  const [typewriter, setTypewriter] = useState(false);
  const [characterCount, setCharacterCount] = useState(0);
  const [annotations, setAnnotations] = useState<Annotation[]>([]);
  const [annotationDraft, setAnnotationDraft] = useState("");
  const [annotationBlock, setAnnotationBlock] = useState(0);
  const [annotationCardId, setAnnotationCardId] = useState("");
  const [confirmingAnnotation, setConfirmingAnnotation] = useState<string | null>(null);
  const [continuousPreview, setContinuousPreview] = useState(false);
  const [exportView, setExportView] = useState<Awaited<ReturnType<typeof loadProjectExport>>>(null);

  const cardList = useCreationStore((state) => state.cards);

  const refreshAnnotations = useCallback(async () => {
    if (!selectedSceneId) {
      setAnnotations([]);
      return;
    }
    setAnnotations(await loadAnnotations({ projectId: project.id, sceneId: selectedSceneId }));
  }, [loadAnnotations, project.id, selectedSceneId]);

  useEffect(() => {
    void refreshAnnotations();
  }, [refreshAnnotations]);

  const sessionRef = useRef<{
    sceneId: string | null;
    startedAt: number;
    startChars: number;
    lastActivity: number;
  } | null>(null);

  const settleSession = useCallback(() => {
    const session = sessionRef.current;
    if (!session) return;
    sessionRef.current = null;
    const activeMs = Math.max(0, session.lastActivity - session.startedAt);
    const activeSeconds = Math.round(activeMs / 1000);
    if (activeSeconds < 1) return;
    void reportSession({
      projectId: project.id,
      sceneId: session.sceneId ?? undefined,
      startedAt: new Date(session.startedAt).toISOString(),
      activeSeconds,
      netChars: characterCountRef.current - session.startChars
    });
  }, [project.id, reportSession]);

  const characterCountRef = useRef(0);
  characterCountRef.current = characterCount;

  const handleStatsChange = useCallback((count: number) => {
    setCharacterCount(count);
    const now = Date.now();
    const session = sessionRef.current;
    if (session && session.sceneId === selectedSceneIdRef.current) {
      session.lastActivity = now;
      return;
    }
    if (session) settleSession();
    sessionRef.current = {
      sceneId: selectedSceneIdRef.current ?? null,
      startedAt: now,
      startChars: count,
      lastActivity: now
    };
  }, [settleSession]);

  const selectedSceneIdRef = useRef<string | undefined>(undefined);
  selectedSceneIdRef.current = selectedSceneId;

  useEffect(() => {
    const timer = setInterval(() => {
      const session = sessionRef.current;
      if (session && Date.now() - session.lastActivity > SESSION_IDLE_TIMEOUT_MS) settleSession();
    }, 30_000);
    const handleVisibility = () => {
      if (document.visibilityState === "hidden") settleSession();
    };
    document.addEventListener("visibilitychange", handleVisibility);
    return () => {
      clearInterval(timer);
      document.removeEventListener("visibilitychange", handleVisibility);
      settleSession();
    };
  }, [settleSession]);

  const outline = outlines[project.id];
  const workflow = project.setup.chapterWorkflow;

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
    void loadOutline(project.id);
  }, [loadOutline, project.id]);

  useEffect(() => {
    void loadCards({ projectId: project.id });
  }, [loadCards, project.id]);

  useEffect(() => {
    if (continuousPreview) void loadProjectExport(project.id).then(setExportView);
  }, [continuousPreview, loadProjectExport, project.id]);

  const currentChapterScenes = useMemo(() => {
    if (!continuousPreview || !exportView) return null;
    const chapter = selectedChapter ? exportView.volumes.flatMap((volume) => volume.chapters).find((item) => item.id === selectedChapter.id) : undefined;
    return chapter ?? null;
  }, [continuousPreview, exportView, selectedChapter]);

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

  const submitAnnotation = async () => {
    if (!selectedSceneId || !annotationDraft.trim()) return;
    const body = sceneView?.body;
    const block = body?.content?.[annotationBlock] as { content?: Array<{ text?: string }> } | undefined;
    const text = block?.content?.[0]?.text as string | undefined;
    const ok = await createAnnotation({
      projectId: project.id,
      sceneId: selectedSceneId,
      cardId: annotationCardId || undefined,
      anchor: { blockIndex: annotationBlock, textOffset: 0, textLength: Math.min(text?.length ?? 1, 40) || 1 },
      note: annotationDraft.trim()
    });
    if (ok) {
      setAnnotationDraft("");
      await refreshAnnotations();
    }
  };

  const chooseProject = async (projectId: string) => {
    if (projectId === project.id) return;
    if (!(await saveBeforeLeaving())) return;
    onSelectProject(projectId);
  };

  const runStructureForTree = useCallback(
    async (command: StructureCommand): Promise<boolean> => {
      const ok = await runStructure(command);
      if (ok) void loadOutline(project.id);
      return ok;
    },
    [loadOutline, project.id, runStructure]
  );

  return (
    <section className={`writing-desk ${focusMode ? "writing-desk--focus" : ""}`} aria-label="正文写作台">
      <aside className="writing-outline" aria-label="项目大纲">
        <label className="writing-project-switcher">
          <span>当前项目</span>
          <select value={project.id} onChange={(event) => void chooseProject(event.target.value)}>
            {projects.map((item) => <option key={item.id} value={item.id}>{item.title}</option>)}
          </select>
        </label>
        <div className="writing-outline-view-switch" role="group" aria-label="大纲视图">
          <button type="button" className={outlineView === "tree" ? "active" : ""} onClick={() => setOutlineView("tree")}>大纲树</button>
          <button type="button" className={outlineView === "board" ? "active" : ""} onClick={() => setOutlineView("board")}>卡片板</button>
        </div>
        <div className="writing-outline-scroll">
          {outline ? (
            outlineView === "tree" ? (
              <OutlineTree
                outline={outline}
                workflow={workflow}
                selectedSceneId={selectedSceneId}
                onSelectScene={(sceneId) => void chooseScene(sceneId)}
                runStructure={runStructureForTree}
              />
            ) : (
              <CardBoard
                outline={outline}
                workflow={workflow}
                selectedSceneId={selectedSceneId}
                onSelectScene={(sceneId) => void chooseScene(sceneId)}
              />
            )
          ) : (
            <p className="outline-loading" role="status">正在读取大纲…</p>
          )}
        </div>
      </aside>

      <main className="writing-manuscript">
        <header className="writing-manuscript-head">
          <div>
            <p className="desktop-card-label">Manuscript</p>
            <h2>{continuousPreview ? (currentChapterScenes ? `${currentChapterScenes.displayNumber ?? ""} ${currentChapterScenes.title}`.trim() : "章内连续预览") : (selectedScene?.title ?? "选择场景")}</h2>
            <span>{selectedChapter?.title ?? project.title} · {continuousPreview ? "多场景连续预览" : "单场景编辑"}</span>
          </div>
          <div className="writing-head-actions">
            <button
              type="button"
              className={`writing-preview-toggle ${continuousPreview ? "active" : ""}`}
              onClick={() => setContinuousPreview((value) => !value)}
              title="切换章内多场景连续预览"
            >
              <Eye size={13} /> {continuousPreview ? "返回编辑" : "连续预览"}
            </button>
            <div className={`writing-watch ${watchConnected ? "connected" : ""}`} title={watchConnected ? "已订阅项目变更" : "正在连接项目变更"}>
              <Radio size={12} /> {watchConnected ? "变更已连接" : "连接中"}
            </div>
          </div>
        </header>

        {abnormalExit && !recoveryNoticeDismissed && (
          <InlineNotice tone="warning" className="writing-recovery-notice">
            <span>已恢复最后确认保存的正文；异常退出前不足一秒的未提交输入可能未保存。</span>
            <button type="button" onClick={dismissRecoveryNotice} aria-label="关闭恢复说明"><X size={14} /></button>
          </InlineNotice>
        )}

        <div className="writing-scroll">
          {continuousPreview ? (
            currentChapterScenes ? (
              <div className="writing-continuous-preview">
                <h2 className="writing-continuous-chapter">
                  {currentChapterScenes.displayNumber ? `${currentChapterScenes.displayNumber} ` : ""}{currentChapterScenes.title}
                </h2>
                {currentChapterScenes.scenes.map((scene, index) => (
                  <section key={scene.id} className="writing-continuous-scene">
                    {scene.title && scene.title !== "默认场景" && <h3>{scene.title}</h3>}
                    {scene.text ? (
                      scene.text.split(/\n{2,}/).map((paragraph, paragraphIndex) => (
                        <p key={paragraphIndex} className="writing-continuous-paragraph">{paragraph}</p>
                      ))
                    ) : (
                      <p className="writing-continuous-empty">（本场景暂无正文）</p>
                    )}
                    {index < currentChapterScenes.scenes.length - 1 && <div className="writing-continuous-break" aria-hidden="true">＊ ＊ ＊</div>}
                  </section>
                ))}
              </div>
            ) : (
              <div className="scene-editor-placeholder">正在读取连续预览…</div>
            )
          ) : selectedSceneId && sceneView ? (
            <SceneEditor
              ref={editorRef}
              view={sceneView}
              onSave={saveSceneBody}
              onReloadScene={() => loadScene(selectedSceneId)}
              onStatsChange={handleStatsChange}
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
        <div className="writing-annotations">
          <p className="desktop-card-label">批注与引用</p>
          {annotations.length === 0 && <p className="writing-annotation-empty">暂无批注。可关联卡片标记引用，正文编辑后失效的锚点会显示「待重新定位」。</p>}
          <ul>
            {annotations.map((annotation) => (
              <li key={annotation.id} className={`writing-annotation ${annotation.anchorInvalid ? "invalid" : ""} ${annotation.status === "resolved" ? "resolved" : ""}`}>
                <span className="writing-annotation-main">
                  {annotation.anchorInvalid && <em className="writing-annotation-relocate">待重新定位</em>}
                  <span className="writing-annotation-text">{annotation.anchoredText || "（锚点失效）"}</span>
                  <span className="writing-annotation-note">{annotation.note}</span>
                  <span className="writing-annotation-meta">
                    {annotation.cardId ? "已关联卡片" : "未关联"} · {annotation.status === "resolved" ? "已解决" : "待处理"}
                  </span>
                </span>
                <span className="writing-annotation-actions">
                  <button
                    type="button"
                    onClick={() => {
                      void updateAnnotation({
                        annotationId: annotation.id,
                        baseRevision: 1,
                        status: annotation.status === "resolved" ? "open" : "resolved"
                      }).then(() => refreshAnnotations());
                    }}
                  >
                    {annotation.status === "resolved" ? "重开" : "解决"}
                  </button>
                  <button
                    type="button"
                    className={confirmingAnnotation === annotation.id ? "confirming" : ""}
                    onClick={() => {
                      if (confirmingAnnotation === annotation.id) {
                        void deleteAnnotation({ annotationId: annotation.id }).then(() => refreshAnnotations());
                        setConfirmingAnnotation(null);
                      } else {
                        setConfirmingAnnotation(annotation.id);
                      }
                    }}
                  >
                    <Trash2 size={12} />
                    {confirmingAnnotation === annotation.id ? "确认" : "删除"}
                  </button>
                </span>
              </li>
            ))}
          </ul>
          <div className="writing-annotation-form">
            <select
              className="paper-input h-8 text-xs"
              value={annotationBlock}
              onChange={(event) => setAnnotationBlock(Number(event.target.value))}
              aria-label="批注段落"
            >
              {Array.from({ length: Math.max(sceneView?.body?.content?.length ?? 1, 1) }, (_, index) => (
                <option key={index} value={index}>第 {index + 1} 段</option>
              ))}
            </select>
            <select
              className="paper-input h-8 text-xs"
              value={annotationCardId}
              onChange={(event) => setAnnotationCardId(event.target.value)}
              aria-label="关联卡片"
            >
              <option value="">不关联卡片</option>
              {cardList.map((card) => (
                <option key={card.id} value={card.id}>{card.title}</option>
              ))}
            </select>
            <textarea
              className="paper-input min-h-[64px] resize-y text-xs"
              placeholder="批注内容（引用锚点会记录该段开头文本）…"
              value={annotationDraft}
              onChange={(event) => setAnnotationDraft(event.target.value)}
            />
            <button type="button" className="writing-annotation-add" onClick={() => void submitAnnotation()} disabled={!annotationDraft.trim()}>
              <MessageSquarePlus size={13} /> 添加批注
            </button>
          </div>
        </div>
        <div className="writing-margin-rule" />
        <p className="writing-boundary"><Eye size={14} /> 卷章结构可在左侧大纲树或卡片板中管理。批注锚定段落开头文本，正文改动后失效会进入待重新定位，不会静默丢失。</p>
      </aside>
    </section>
  );
}
