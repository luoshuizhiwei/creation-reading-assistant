import React, { forwardRef, useCallback, useImperativeHandle, useMemo, useRef } from "react";
import { SceneEditor, type SceneEditorHandle } from "@/features/creation/editor/SceneEditor";
import type { SceneSelection } from "@/features/creation/editor/annotation-selection";
import type { CreationDocument, SceneBodyView, SceneSaveResponse } from "@/types/creation";

export interface ContinuousSceneRef {
  id: string;
  title: string;
  view: SceneBodyView | undefined;
}

export interface ContinuousChapterEditorHandle {
  /** 保存所有脏场景；任一失败时返回 false（但不阻塞其它场景）。 */
  saveAllDirty: () => Promise<boolean>;
  getSelection: () => SceneSelection | null;
  isComposing: () => boolean;
}

export interface ContinuousChapterEditorProps {
  chapterTitle: string;
  scenes: ContinuousSceneRef[];
  onSave: (sceneId: string, baseRevision: number, body: CreationDocument) => Promise<SceneSaveResponse | undefined>;
  /** 重新加载场景正文；与 SceneEditor.onReloadScene 契约一致，返回最新 view。 */
  onReloadScene?: (sceneId: string) => Promise<SceneBodyView | null | undefined>;
  onStatsChange?: (sceneId: string, chars: number) => void;
  /** 选区变化：selection.sceneId 由子编辑器各自解析，保证连续模式选区归属正确场景。 */
  onSelectionChange?: (selection: SceneSelection | null) => void;
  /** 非 IME 状态下输入 @ 触发卡片引用（透传给各场景编辑器）。 */
  onMentionTrigger?: (selection: SceneSelection) => void;
  focusMode: boolean;
  onToggleFocusMode: () => void;
  typewriter: boolean;
  onToggleTypewriter: () => void;
}

export const ContinuousChapterEditor = forwardRef<ContinuousChapterEditorHandle, ContinuousChapterEditorProps>(
  function ContinuousChapterEditor(props, ref) {
    const {
      chapterTitle,
      scenes,
      onSave,
      onReloadScene,
      onStatsChange,
      onSelectionChange,
      onMentionTrigger,
      focusMode,
      onToggleFocusMode,
      typewriter,
      onToggleTypewriter
    } = props;

    // 每个场景一个独立句柄（各自持有 session，保证自动保存 / IME / 冲突互不影响）。
    const handles = useRef(new Map<string, SceneEditorHandle>());
    const activeSceneId = useRef<string | null>(null);

    const register = useCallback((sceneId: string, handle: SceneEditorHandle | null) => {
      if (handle) {
        handles.current.set(sceneId, handle);
      } else {
        handles.current.delete(sceneId);
      }
    }, []);

    useImperativeHandle(
      ref,
      () => ({
        saveAllDirty: async () => {
          let allOk = true;
          for (const handle of handles.current.values()) {
            try {
              if (handle.isDirty()) {
                const ok = await handle.saveNow();
                if (!ok) allOk = false;
              }
            } catch {
              allOk = false;
            }
          }
          return allOk;
        },
        getSelection: () => activeSceneId.current ? handles.current.get(activeSceneId.current)?.getSelection() ?? null : null,
        isComposing: () => activeSceneId.current ? handles.current.get(activeSceneId.current)?.isComposing() ?? false : false
      }),
      []
    );

    const ordered = useMemo(() => scenes, [scenes]);

    return (
      <div className="chapter-continuous-editor">
        <h2 className="chapter-continuous-title">{chapterTitle}</h2>
        {ordered.map((scene, index) => (
          <section key={scene.id} className="chapter-continuous-scene" data-scene-id={scene.id}>
            <div className="chapter-continuous-scene-head">
              <span className="chapter-continuous-scene-index">{index + 1}</span>
              <h3 className="chapter-continuous-scene-title">{scene.title}</h3>
            </div>
            {scene.view ? (
              <SceneEditor
                ref={(handle) => register(scene.id, handle)}
                view={scene.view}
                onSave={onSave}
                onReloadScene={onReloadScene ? () => onReloadScene(scene.id) : undefined}
                onStatsChange={onStatsChange ? (chars) => onStatsChange(scene.id, chars) : undefined}
                onSelectionChange={(selection) => {
                  if (selection) activeSceneId.current = selection.sceneId;
                  onSelectionChange?.(selection);
                }}
                onMentionTrigger={(selection) => {
                  activeSceneId.current = selection.sceneId;
                  onMentionTrigger?.(selection);
                }}
                focusMode={focusMode}
                onToggleFocusMode={onToggleFocusMode}
                typewriter={typewriter}
                onToggleTypewriter={onToggleTypewriter}
              />

            ) : (
              <div className="scene-editor-placeholder">正在读取场景正文…</div>
            )}
            {index < ordered.length - 1 && (
              <div className="chapter-continuous-break" role="separator" aria-label="场景分隔">
                ＊ ＊ ＊
              </div>
            )}
          </section>
        ))}
      </div>
    );
  }
);
