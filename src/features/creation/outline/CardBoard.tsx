import { useState } from "react";
import { FileText, LayoutGrid, ListTree } from "lucide-react";
import type { CreationOutlineChapter, CreationProjectOutline } from "@/types/creation";

interface CardBoardProps {
  outline: CreationProjectOutline;
  workflow: string[];
  selectedSceneId?: string;
  onSelectScene: (sceneId: string) => void;
}

type GroupMode = "chapter" | "status";

function chapterKey(chapter: CreationOutlineChapter): string {
  return [chapter.displayNumber, chapter.title].filter(Boolean).join(" ");
}

export function CardBoard({ outline, workflow, selectedSceneId, onSelectScene }: CardBoardProps) {
  const [mode, setMode] = useState<GroupMode>("chapter");

  const flatChapters = outline.volumes.flatMap((volume) =>
    volume.chapters.map((chapter) => ({ volumeTitle: volume.title, chapter }))
  );

  const allScenes = flatChapters.flatMap(({ volumeTitle, chapter }) =>
    chapter.scenes.map((scene) => ({ volumeTitle, chapter, scene }))
  );

  const scenesByStatus = new Map<string, typeof allScenes>();
  for (const item of allScenes) {
    const list = scenesByStatus.get(item.chapter.status) ?? [];
    list.push(item);
    scenesByStatus.set(item.chapter.status, list);
  }

  const renderCard = (item: (typeof allScenes)[number]) => (
    <button
      type="button"
      key={item.scene.id}
      className={`card-board-card ${item.scene.id === selectedSceneId ? "active" : ""}`}
      onClick={() => onSelectScene(item.scene.id)}
    >
      <span className="card-board-card-title">
        <FileText size={13} />
        {item.scene.title}
      </span>
      <span className="card-board-card-meta">
        {chapterKey(item.chapter)}
        {item.volumeTitle && ` · ${item.volumeTitle}`}
        <em>{item.scene.wordCount.toLocaleString("zh-CN")}字</em>
      </span>
      <span className="card-board-card-status">{item.chapter.status}</span>
    </button>
  );

  return (
    <div className="card-board">
      <div className="card-board-toolbar">
        <span className="outline-toolbar-label">卡片板</span>
        <div className="card-board-mode" role="group" aria-label="分组方式">
          <button
            type="button"
            className={mode === "chapter" ? "active" : ""}
            onClick={() => setMode("chapter")}
          >
            <ListTree size={12} /> 按章节
          </button>
          <button
            type="button"
            className={mode === "status" ? "active" : ""}
            onClick={() => setMode("status")}
          >
            <LayoutGrid size={12} /> 按状态
          </button>
        </div>
      </div>
      <div className="card-board-scroll">
        {allScenes.length === 0 && <p className="card-board-empty">暂无场景。在左侧大纲中新建卷、章节或场景。</p>}
        {mode === "chapter"
          ? flatChapters.map(({ volumeTitle, chapter }) => (
              <section key={chapter.id} className="card-board-group">
                <header className="card-board-group-head">
                  <span className="card-board-chapter-key">{chapterKey(chapter)}</span>
                  {volumeTitle && <small>{volumeTitle}</small>}
                  <em>{chapter.scenes.length}场景 · {chapter.scenes.reduce((sum, s) => sum + s.wordCount, 0).toLocaleString("zh-CN")}字</em>
                </header>
                <div className="card-board-grid">
                  {chapter.scenes.map((scene) => renderCard({ volumeTitle, chapter, scene }))}
                  {chapter.scenes.length === 0 && <span className="card-board-empty-hint">暂无场景</span>}
                </div>
              </section>
            ))
          : workflow.map((status) => {
              const items = scenesByStatus.get(status) ?? [];
              if (items.length === 0) return null;
              return (
                <section key={status} className="card-board-group">
                  <header className="card-board-group-head">
                    <span className="card-board-status-key">{status}</span>
                    <em>{items.length}场景 · {items.reduce((sum, item) => sum + item.scene.wordCount, 0).toLocaleString("zh-CN")}字</em>
                  </header>
                  <div className="card-board-grid">{items.map(renderCard)}</div>
                </section>
              );
            })}
      </div>
    </div>
  );
}
