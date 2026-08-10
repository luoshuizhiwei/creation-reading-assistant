import { useCallback, useEffect, useRef, useState } from "react";
import { Replace, ShieldCheck, X } from "lucide-react";
import { Button } from "@/components/ui";
import { useCreationActions } from "@/hooks/useCreationActions";
import { useUIStore } from "@/stores/ui-store";
import type { ReplacePreviewHit, ReplacePreviewView, ReplaceScope } from "@/types/creation";

interface ReplacePanelProps {
  projectId: string;
  chapterId?: string;
  sceneId?: string;
  onClose(): void;
}

const SCOPE_OPTIONS: Array<{ scope: ReplaceScope; label: string; needs: "none" | "chapter" | "scene" }> = [
  { scope: "project", label: "整个项目", needs: "none" },
  { scope: "chapter", label: "当前章节", needs: "chapter" },
  { scope: "scene", label: "当前场景", needs: "scene" }
];

export function ReplacePanel({ projectId, chapterId, sceneId, onClose }: ReplacePanelProps) {
  const { replacePreview, replaceApply } = useCreationActions();
  const showToast = useUIStore((state) => state.showToast);
  const findRef = useRef<HTMLInputElement>(null);
  const [find, setFind] = useState("");
  const [replaceWith, setReplaceWith] = useState("");
  const [scope, setScope] = useState<ReplaceScope>("project");
  const [regex, setRegex] = useState(false);
  const [preview, setPreview] = useState<ReplacePreviewView | null>(null);
  const [previewing, setPreviewing] = useState(false);
  const [excluded, setExcluded] = useState<Set<string>>(new Set());
  const [applying, setApplying] = useState(false);

  useEffect(() => {
    findRef.current?.focus();
  }, []);

  const runPreview = useCallback(async () => {
    const trimmed = find.trim();
    if (!trimmed) {
      setPreview(null);
      return;
    }
    setPreviewing(true);
    const view = await replacePreview({
      projectId,
      find: trimmed,
      replaceWith,
      scope,
      scopeId: scope === "scene" ? sceneId : scope === "chapter" ? chapterId : undefined,
      regex
    });
    setPreview(view);
    setExcluded(new Set());
    setPreviewing(false);
  }, [chapterId, find, projectId, regex, replacePreview, replaceWith, sceneId, scope]);

  const runApply = async () => {
    if (!preview || preview.totalHits === 0) return;
    setApplying(true);
    const ok = await replaceApply({
      type: "replace.apply",
      projectId,
      find: preview.find,
      replaceWith: preview.replaceWith,
      scope: preview.scope,
      scopeId: preview.scope === "scene" ? sceneId : preview.scope === "chapter" ? chapterId : undefined,
      regex,
      excludeSceneIds: [...excluded]
    });
    setApplying(false);
    if (!ok) return;
    const included = preview.matchedScenes - excluded.size;
    showToast({
      tone: "success",
      title: `已替换 ${preview.totalHits} 处命中`,
      body: `修改 ${included} 个场景（排除 ${excluded.size} 个），每个场景已自动创建保护快照，可在历史页恢复。`
    });
    onClose();
  };

  const toggleExcluded = (sceneId: string) => {
    setExcluded((current) => {
      const next = new Set(current);
      if (next.has(sceneId)) next.delete(sceneId);
      else next.add(sceneId);
      return next;
    });
  };

  return (
    <div className="creation-search-overlay" role="dialog" aria-label="查找替换" aria-modal="true">
      <div className="creation-search-shell creation-replace-shell" role="search">
        <div className="creation-search-head">
          <Replace size={16} className="creation-search-head-icon" />
          <input
            ref={findRef}
            className="creation-search-input"
            placeholder="查找文本…"
            value={find}
            onChange={(event) => setFind(event.target.value)}
            onKeyDown={(event) => {
              if (event.key === "Enter") void runPreview();
              if (event.key === "Escape") onClose();
            }}
          />
          <button type="button" className="creation-search-close" onClick={onClose} aria-label="关闭查找替换">
            <X size={16} />
          </button>
        </div>

        <div className="creation-replace-row">
          <input
            className="paper-input h-9"
            placeholder="替换为…"
            value={replaceWith}
            onChange={(event) => setReplaceWith(event.target.value)}
            onKeyDown={(event) => {
              if (event.key === "Enter") void runPreview();
            }}
          />
          <Button onClick={() => void runPreview()} disabled={previewing}>
            预览
          </Button>
        </div>

        <div className="creation-search-filters creation-replace-filters">
          <div className="creation-search-scopes" role="group" aria-label="替换范围">
            {SCOPE_OPTIONS.map(({ scope: option, label, needs }) => (
              <button
                key={option}
                type="button"
                disabled={needs === "chapter" && !chapterId}
                className={scope === option ? "active" : ""}
                onClick={() => setScope(option)}
              >
                {label}
              </button>
            ))}
          </div>
          <label className="creation-replace-regex">
            <input
              type="checkbox"
              checked={regex}
              onChange={(event) => setRegex(event.target.checked)}
            />
            正则（受限）
          </label>
        </div>

        <div className="creation-search-results">
          {previewing && <p className="creation-search-state" role="status">正在扫描场景正文…</p>}
          {!previewing && preview && preview.totalHits === 0 && (
            <p className="creation-search-state">没有找到与「{preview.find}」匹配的内容。</p>
          )}
          {!previewing && !preview && (
            <p className="creation-search-state">
              输入查找与替换文本后点「预览」；项目级替换会自动为每个修改场景创建保护快照。
            </p>
          )}
          {!previewing && preview && preview.totalHits > 0 && (
            <>
              <div className="creation-replace-summary">
                共 {preview.matchedScenes} 个场景命中 {preview.totalHits} 处；可勾选排除不想修改的场景。
                {excluded.size > 0 && <> 已排除 {excluded.size} 个。</>}
              </div>
              <div className="creation-replace-list">
                {preview.sceneHits.map((hit: ReplacePreviewHit) => (
                  <label key={hit.sceneId} className="creation-replace-item">
                    <input
                      type="checkbox"
                      checked={!excluded.has(hit.sceneId)}
                      onChange={() => toggleExcluded(hit.sceneId)}
                    />
                    <span className="creation-replace-item-main">
                      <span className="creation-replace-item-title">
                        {hit.sceneTitle} <em>{hit.chapterTitle}</em>
                      </span>
                      {hit.snippets.map((snippet, index) => (
                        <span key={index} className="creation-replace-item-snippet">{snippet}</span>
                      ))}
                    </span>
                    <span className="creation-replace-item-count">{hit.count}</span>
                  </label>
                ))}
              </div>
            </>
          )}
        </div>

        {preview && preview.totalHits > 0 && (
          <div className="creation-replace-actions">
            <span className="creation-replace-hint"><ShieldCheck size={13} /> 替换前自动快照，可在历史页恢复</span>
            <Button
              onClick={() => void runApply()}
              disabled={applying || excluded.size === preview.matchedScenes}
            >
              {applying ? "替换中…" : `替换 ${preview.totalHits} 处`}
            </Button>
          </div>
        )}
      </div>
    </div>
  );
}
