import { useState } from "react";
import { FileUp, FolderOpen, Loader2, X } from "lucide-react";
import { Button } from "@/components/ui";
import { useCreationActions } from "@/hooks/useCreationActions";
import { useUIStore } from "@/stores/ui-store";
import type { DraftImportPreview } from "@/types/creation";
import {
  createEditablePreview,
  isEditablePreviewValid,
  renameChapter,
  renameVolume,
  toImportInput,
  toggleChapter,
  type EditableDraftPreview
} from "./draft-preview-edits";

interface ImportDraftDialogProps {
  onClose(): void;
  onImported(): void;
}

export function ImportDraftDialog({ onClose, onImported }: ImportDraftDialogProps) {
  const { previewDraftImport, runStructure, loadProjects } = useCreationActions();
  const showToast = useUIStore((state) => state.showToast);
  const [preview, setPreview] = useState<DraftImportPreview | null>(null);
  const [editable, setEditable] = useState<EditableDraftPreview | null>(null);
  const [importing, setImporting] = useState(false);

  const chooseFile = async () => {
    const result = await previewDraftImport();
    if (!result) return;
    setPreview(result);
    setEditable(createEditablePreview(result));
  };

  const execute = async () => {
    if (!preview || !editable) return;
    setImporting(true);
    const input = toImportInput(editable);
    if (input.volumes.length === 0 || !input.title) {
      setImporting(false);
      showToast({ tone: "error", title: "无法导入", body: "请至少保留一个章节并填写项目名称。" });
      return;
    }
    const ok = await runStructure({ type: "project.importDraft", title: input.title, volumes: input.volumes });
    setImporting(false);
    if (!ok) return;
    await loadProjects();
    const totalChapters = input.volumes.reduce((sum, volume) => sum + volume.chapters.length, 0);
    showToast({
      tone: "success",
      title: "旧稿已导入",
      body: `「${input.title}」已建为创作项目，共 ${input.volumes.length} 卷 ${totalChapters} 章。`
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
                支持 TXT、Markdown 与 DOCX 旧稿：自动识别章节结构（DOCX 按标题样式，TXT 按「第X章」，Markdown 按标题层级），先预览并调整卷章，确认后创建新项目（不会改动原文件，也不会覆盖已有项目）。
              </p>
              <div className="migration-actions">
                <Button variant="secondary" onClick={onClose}>取消</Button>
                <Button onClick={() => void chooseFile()}>
                  <FolderOpen size={14} /> 选择文件
                </Button>
              </div>
            </>
          ) : editable ? (
            <>
              <label className="creation-proof-banned">
                <span>项目名称</span>
                <input
                  className="paper-input h-9"
                  value={editable.projectTitle}
                  onChange={(event) => setEditable({ ...editable, projectTitle: event.target.value })}
                  placeholder={preview.projectTitle}
                />
              </label>
              <p className="migration-note">
                识别结果：{editable.volumes.length} 卷 /{" "}
                {editable.volumes.reduce((sum, volume) => sum + volume.chapters.length, 0)} 章 / 约{" "}
                {preview.totalWords.toLocaleString("zh-CN")} 字
                {preview.warnings.length > 0 && (
                  <span className="migration-error-inline">（{preview.warnings.join("；")}）</span>
                )}
              </p>
              <div className="migration-report">
                {editable.volumes.map((volume) => (
                  <div key={volume.key} className="import-volume">
                    <p className="import-volume-title">
                      <input
                        className="paper-input h-7"
                        aria-label={`卷名 ${volume.title}`}
                        value={volume.title}
                        onChange={(event) => setEditable(renameVolume(editable, volume.key, event.target.value))}
                      />
                    </p>
                    <ul>
                      {volume.chapters.map((chapter) => (
                        <li key={chapter.key} className={chapter.included ? "" : "import-chapter--excluded"}>
                          <input
                            className="paper-input h-7"
                            aria-label={`章名 ${chapter.title}`}
                            value={chapter.title}
                            onChange={(event) =>
                              setEditable(renameChapter(editable, volume.key, chapter.key, event.target.value))
                            }
                          />
                          <em>{chapter.wordCount.toLocaleString("zh-CN")} 字</em>
                          <button
                            type="button"
                            className="import-chapter-toggle"
                            onClick={() => {
                              const result = toggleChapter(editable, volume.key, chapter.key);
                              if (!result.applied) {
                                showToast({ tone: "error", title: "至少保留一章", body: "导入需要至少保留一个章节。" });
                              }
                              setEditable(result.state);
                            }}
                          >
                            {chapter.included ? "排除" : "恢复"}
                          </button>
                        </li>
                      ))}
                    </ul>
                  </div>
                ))}
              </div>
              <div className="migration-actions">
                <Button variant="secondary" onClick={() => void chooseFile()}>重新选择</Button>
                <Button onClick={() => void execute()} disabled={importing || !isEditablePreviewValid(editable)}>
                  {importing ? <><Loader2 size={14} className="spin" /> 导入中…</> : "导入为新项目"}
                </Button>
              </div>
            </>
          ) : null}
        </div>
      </div>
    </div>
  );
}
