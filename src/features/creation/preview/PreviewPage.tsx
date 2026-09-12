import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { BookOpen, FileDown, Printer, RefreshCw } from "lucide-react";
import { Button } from "@/components/ui";
import { printProject, projectPreview } from "@/services/creation-service";
import { useUIStore } from "@/stores/ui-store";
import type { ProjectExportView, ProjectPrintMode } from "@/types/creation";
import { buildPreviewModel, type PreviewBlock } from "./preview-model";
import "./preview-local.css";

interface PreviewPageProps {
  projectId: string;
}

function PreviewBlockView({ block }: { block: PreviewBlock }) {
  if (block.role === "break") {
    return <hr className="preview-block preview-block--break" aria-hidden="true" />;
  }
  if (block.role === "note") {
    return <aside className="preview-block preview-block--note" role="note">{`作者按：${block.text}`}</aside>;
  }
  if (block.role === "letter") {
    return <blockquote className="preview-block preview-block--letter">{block.text}</blockquote>;
  }
  if (block.role === "centered") {
    return <p className="preview-block preview-block--centered">{block.text}</p>;
  }
  return <p className="preview-block">{block.text}</p>;
}

/**
 * 全书只读通读页（阶段 4-B）。
 *
 * 只读边界：本页只调用读取与打印通道，不提供任何编辑入口，也不持有正文写入能力；
 * 正文改动仍必须回到写作台。数据来源复用 `project.export` 读通道（含块级视图），
 * 删除态章节/场景的过滤与成稿导出完全一致，不另开读源。
 *
 * 这里直接调用 service 而不是走 `useCreationActions`：`executeAction` 会把错误
 * 折叠成 `undefined`，无法区分「作品不存在/读取失败」与「项目还没有章节」，
 * 而这两种情况在本页需要给出完全不同的界面。
 */
export function PreviewPage({ projectId }: PreviewPageProps) {
  const showToast = useUIStore((state) => state.showToast);
  const [view, setView] = useState<ProjectExportView | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [printing, setPrinting] = useState<ProjectPrintMode | null>(null);
  const [jumpId, setJumpId] = useState("");
  const chapterRefs = useRef(new Map<string, HTMLElement | null>());

  const refresh = useCallback(async () => {
    setLoading(true);
    setLoadError(null);
    try {
      const next = await projectPreview(projectId);
      if (next) {
        setView(next);
      } else {
        setView(null);
        setLoadError("作品不存在或已被删除。");
      }
    } catch (error) {
      setView(null);
      setLoadError(error instanceof Error ? error.message : String(error));
    } finally {
      setLoading(false);
    }
  }, [projectId]);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  const model = useMemo(() => (view ? buildPreviewModel(view) : null), [view]);

  const handleJump = useCallback((chapterId: string) => {
    setJumpId(chapterId);
    if (!chapterId) return;
    // jsdom 不实现 scrollIntoView，因此按可选调用处理，避免测试环境抛错。
    chapterRefs.current.get(chapterId)?.scrollIntoView?.({ block: "start" });
  }, []);

  const handlePrint = useCallback(
    async (mode: ProjectPrintMode) => {
      if (printing) return;
      setPrinting(mode);
      try {
        const result = await printProject(projectId, mode);
        if (result.failureReason) {
          showToast({ tone: "error", title: "打印未完成", body: result.failureReason });
        } else if (mode === "pdf" && !result.canceled && result.filePath) {
          showToast({ tone: "success", title: "已导出打印版 PDF", body: result.filePath });
        }
      } catch (error) {
        showToast({
          tone: "error",
          title: "打印失败",
          body: error instanceof Error ? error.message : String(error)
        });
      } finally {
        setPrinting(null);
      }
    },
    [printing, projectId, showToast]
  );

  if (loading) {
    return (
      <section className="creation-writing-loading" role="status">
        <BookOpen size={24} />
        <span>正在读取全书预览…</span>
      </section>
    );
  }

  if (loadError) {
    return (
      <section className="preview-page preview-page--failed" aria-label="全书预览">
        <div className="preview-failure" role="alert">
          <h2>无法读取全书预览</h2>
          <p>{loadError}</p>
          <Button onClick={() => void refresh()}>
            <RefreshCw size={15} /> 重试
          </Button>
        </div>
      </section>
    );
  }

  if (!model || model.chapters.length === 0) {
    return (
      <section className="preview-page preview-page--empty" aria-label="全书预览">
        <div className="preview-empty">
          <BookOpen size={22} aria-hidden="true" />
          <h2>{model?.title ?? "全书预览"}</h2>
          <p>这个项目还没有章节，先在大纲里建立卷、章与场景，再回来通读与打印。</p>
        </div>
      </section>
    );
  }

  return (
    <section className="preview-page" aria-label="全书预览">
      <header className="preview-toolbar">
        <div className="preview-toolbar-meta">
          <h2>{model.title}</h2>
          <p>{`共 ${model.volumeCount} 卷 · ${model.chapterCount} 章 · ${model.sceneCount} 个场景 · ${model.wordCount.toLocaleString("zh-CN")} 字`}</p>
          {model.emptySceneCount > 0 ? (
            <p className="preview-toolbar-warn">{`其中 ${model.emptySceneCount} 个场景尚无正文，通读页会逐处标注。`}</p>
          ) : null}
        </div>
        <div className="preview-toolbar-actions">
          <label className="preview-jump">
            <span>跳转到章节</span>
            <select
              value={jumpId}
              aria-label="跳转到章节"
              onChange={(event) => handleJump(event.target.value)}
            >
              <option value="">选择章节</option>
              {model.chapters.map((chapter, index) => (
                <option key={chapter.id} value={chapter.id}>{`${index + 1}. ${chapter.heading}`}</option>
              ))}
            </select>
          </label>
          <Button onClick={() => void handlePrint("print")} disabled={printing !== null}>
            <Printer size={15} /> {printing === "print" ? "正在打印…" : "打印…"}
          </Button>
          <Button variant="secondary" onClick={() => void handlePrint("pdf")} disabled={printing !== null}>
            <FileDown size={15} /> {printing === "pdf" ? "正在导出…" : "导出打印版 PDF"}
          </Button>
        </div>
      </header>

      <p className="preview-readonly-note" role="note">
        只读通读页：本页没有任何编辑入口，正文改动请回到写作台。
      </p>

      <article className="preview-document" aria-label={`${model.title} 全书正文`}>
        {model.chapters.map((chapter) => (
          <section
            key={chapter.id}
            id={chapter.anchorId}
            className="preview-chapter"
            aria-label={chapter.heading}
            ref={(node) => {
              chapterRefs.current.set(chapter.id, node);
            }}
          >
            {chapter.startsVolume ? <div className="preview-volume">{chapter.volumeTitle}</div> : null}
            <h3 className="preview-chapter-heading">
              <span>{chapter.heading}</span>
              <span className="preview-chapter-words">{`${chapter.wordCount.toLocaleString("zh-CN")} 字`}</span>
            </h3>
            {chapter.scenes.map((scene) => (
              <div key={scene.id} className="preview-scene">
                <div className="preview-scene-mark">{scene.title}</div>
                {scene.empty ? (
                  <p className="preview-scene-empty">本场景暂无正文。</p>
                ) : (
                  scene.blocks.map((block) => <PreviewBlockView key={block.key} block={block} />)
                )}
              </div>
            ))}
          </section>
        ))}
      </article>
    </section>
  );
}
