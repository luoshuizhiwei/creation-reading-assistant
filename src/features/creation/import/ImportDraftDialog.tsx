import { useState } from "react";
import { FileUp, FolderOpen, Loader2, X } from "lucide-react";
import { Button } from "@/components/ui";
import { useCreationActions } from "@/hooks/useCreationActions";
import { useUIStore } from "@/stores/ui-store";
import type { DraftImportPreview } from "@/types/creation";

interface ImportDraftDialogProps {
  onClose(): void;
  onImported(): void;
}

export function ImportDraftDialog({ onClose, onImported }: ImportDraftDialogProps) {
  const { previewDraftImport, runStructure, loadProjects } = useCreationActions();
  const showToast = useUIStore((state) => state.showToast);
  const [preview, setPreview] = useState<DraftImportPreview | null>(null);
  const [projectTitle, setProjectTitle] = useState("");
  const [importing, setImporting] = useState(false);

  const chooseFile = async () => {
    const result = await previewDraftImport();
    if (!result) return;
    setPreview(result);
    setProjectTitle(result.projectTitle);
  };

  const execute = async () => {
    if (!preview) return;
    setImporting(true);
    const ok = await runStructure({
      type: "project.importDraft",
      title: projectTitle.trim() || preview.projectTitle,
      volumes: preview.volumes
    });
    setImporting(false);
    if (!ok) return;
    await loadProjects();
    showToast({
      tone: "success",
      title: "旧稿已导入",
      body: `「${projectTitle.trim() || preview.projectTitle}」已建为创作项目，共 ${preview.volumes.length} 卷 ${preview.totalChapters} 章。`
    });
    onImported();
    onClose();
  };

  return (
    <div className="creation-search-overlay" role="dialog" aria-label="导入旧稿" aria-modal="true">
      <div className="creation-search-shell migration-dialog" role="search">
        <div className="creation-search-head">
          <FileUp size={16} className="creation-search-head-icon" />
          <span className="creation-proof-title">导入旧稿</span>
          <button type="button" className="creation-search-close" onClick={onClose} aria-label="关闭导入对话框">
            <X size={16} />
          </button>
        </div>

        <div className="migration-body">
          {!preview ? (
            <>
              <p className="migration-note">
                支持 TXT 与 Markdown 旧稿：自动识别「第X章」或 Markdown 标题结构，先预览卷章识别结果，确认后创建新项目（不会改动原文件，也不会覆盖已有项目）。
              </p>
              <div className="migration-actions">
                <Button variant="secondary" onClick={onClose}>取消</Button>
                <Button onClick={() => void chooseFile()}>
                  <FolderOpen size={14} /> 选择文件
                </Button>
              </div>
            </>
          ) : (
            <>
              <label className="creation-proof-banned">
                <span>项目名称</span>
                <input
                  className="paper-input h-9"
                  value={projectTitle}
                  onChange={(event) => setProjectTitle(event.target.value)}
                  placeholder={preview.projectTitle}
                />
              </label>
              <p className="migration-note">
                识别结果：{preview.volumes.length} 卷 / {preview.totalChapters} 章 / 约 {preview.totalWords.toLocaleString("zh-CN")} 字
                {preview.warnings.length > 0 && (
                  <span className="migration-error-inline">（{preview.warnings.join("；")}）</span>
                )}
              </p>
              <div className="migration-report">
                {preview.volumes.map((volume, volumeIndex) => (
                  <div key={volumeIndex} className="import-volume">
                    <p className="import-volume-title">卷：{volume.title}</p>
                    <ul>
                      {volume.chapters.map((chapter, chapterIndex) => (
                        <li key={chapterIndex}>
                          {chapter.title}
                          <em>{chapter.wordCount.toLocaleString("zh-CN")} 字</em>
                        </li>
                      ))}
                    </ul>
                  </div>
                ))}
              </div>
              <div className="migration-actions">
                <Button variant="secondary" onClick={() => void chooseFile()}>重新选择</Button>
                <Button onClick={() => void execute()} disabled={importing || !projectTitle.trim()}>
                  {importing ? <><Loader2 size={14} className="spin" /> 导入中…</> : "导入为新项目"}
                </Button>
              </div>
            </>
          )}
        </div>
      </div>
    </div>
  );
}
