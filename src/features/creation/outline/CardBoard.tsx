import { useEffect, useId, useRef, useState } from "react";
import { ArrowDown, ArrowUp, FileText } from "lucide-react";
import { Button, Tabs } from "@/components/ui";
import type {
  CreationOutlineChapter,
  CreationProjectOutline,
  ProtectedStructureCommand,
  StructureApplyResult,
  StructureApplyWithProtectionCommand,
  StructurePreviewCommand,
  StructurePreviewView
} from "@/types/creation";
import { computeBatchStatusImpact, computeSceneMoveImpact } from "./outline-impact";
import { reorderTarget } from "./outline-reorder";
import { StructureImpactDialog } from "./StructureImpactDialog";
import { useEditFocusHandoff } from "./use-edit-focus-handoff";
import { SCENE_STATUS_OPTIONS } from "@/features/creation/scene-status";
import "./outline-reorg.css";

interface CardBoardProps {
  outline: CreationProjectOutline;
  workflow: string[];
  selectedSceneId?: string;
  onSelectScene: (sceneId: string) => void;
  /**
   * 可选编辑能力：独立大纲页接线后启用；缺省时卡板保持只读。
   * 所有编辑回调返回 Promise<boolean>：调用方据此进入 busy、显式报错、避免重复提交，
   * 不采用乐观成功；失败（false / 抛错）时调用方保留对话框并展示错误。
   */
  onSceneReorder?: (sceneId: string, beforeSceneId?: string) => Promise<boolean>;
  onSceneMove?: (sceneId: string, targetChapterId: string, beforeSceneId?: string) => Promise<boolean>;
  onChapterSetStatus?: (chapterId: string, status: string) => Promise<boolean>;
  onChaptersSetStatus?: (chapterIds: string[], status: string) => Promise<boolean>;
  previewStructure?: (command: StructurePreviewCommand) => Promise<StructurePreviewView | null>;
  applyStructureWithProtection?: (command: StructureApplyWithProtectionCommand) => Promise<StructureApplyResult | null>;
  onProtectedApplied?: (result: StructureApplyResult) => void;
  selectedChapterIds?: ReadonlyArray<string> | ReadonlySet<string>;
  onToggleChapterSelected?: (chapterId: string) => void;
}

type GroupMode = "chapter" | "status";

function chapterKey(chapter: CreationOutlineChapter): string {
  return [chapter.displayNumber, chapter.title].filter(Boolean).join(" ");
}

export function CardBoard({
  outline,
  workflow,
  selectedSceneId,
  onSelectScene,
  onSceneReorder,
  onSceneMove,
  onChapterSetStatus,
  onChaptersSetStatus,
  previewStructure,
  applyStructureWithProtection,
  onProtectedApplied,
  selectedChapterIds,
  onToggleChapterSelected
}: CardBoardProps) {
  const [mode, setMode] = useState<GroupMode>("chapter");
  const [pendingOp, setPendingOp] = useState<
    | { type: "sceneMove"; sceneId: string; targetChapterId: string }
    | { type: "batchStatus"; status: string }
    | null
  >(null);
  const [batchStatus, setBatchStatus] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [preview, setPreview] = useState<StructurePreviewView | null>(null);
  const [previewBusy, setPreviewBusy] = useState(false);
  const submittingRef = useRef(false);
  // BD-246（WCAG 3.2.2 On Input）：与 OutlineTree.tsx:211 同族——卡板每枚卡上的「移章」
  // 下拉框一改值就挂影响预览弹层（原生 select 键盘下每按一次方向键就 change 一次，第一次
  // 按就被弹层接走）。弹层是「不确认不写入」的安全确认，不能拆；按 F36 走「事先说明」这条
  // 出路：常驻可见提示 + aria-describedby。旁边那颗「批量状态」下拉框本来就有显式的
  // 「应用」钮（本文件 :325），它不是这一族的形状，不动。
  const adviceId = useId();
  // BD-251（2.4.3）：runEdit 的 busy 会把状态下拉与重排钮整片禁用，握在上面的焦点被浏览器
  // 吐回 body；事务完成后没人接回来（实测 /tmp/bd251-red2.log：卡板状态下拉／上移／下移场景）。
  // 「落定」信号取 [outline, busy]：busy 进出各翻一次，之后还有一次结构刷新会搬动卡片
  // DOM 节点、把刚接回去的焦点再撞掉一次，两拍都得重试（见钩子里的重试阶梯注释）。
  const boardRef = useRef<HTMLDivElement>(null);
  const editFocusHandoff = useEditFocusHandoff(boardRef, [outline, busy], ".card-board-card, .card-board-group");

  /**
   * 统一的编辑事务包装：进入 busy、清除旧错误、执行回调。
   * 返回 false 或抛错时设置错误且不关闭调用方对话框（避免乐观成功）。
   * submittingRef 防止并发重复提交（与按钮 disabled 双保险）。
   */
  const runEdit = async (action: () => Promise<boolean>): Promise<boolean> => {
    if (submittingRef.current) return false;
    submittingRef.current = true;
    setBusy(true);
    setError(null);
    try {
      const ok = await action();
      if (!ok) setError("操作未成功，请重试。");
      return ok;
    } catch (e) {
      setError(e instanceof Error ? e.message : "操作失败。");
      return false;
    } finally {
      submittingRef.current = false;
      setBusy(false);
    }
  };

  const selectedSet = selectedChapterIds ? new Set(selectedChapterIds) : null;
  const editable = Boolean(onSceneReorder || onSceneMove || onChapterSetStatus || onChaptersSetStatus);

  const flatChapters = outline.volumes.flatMap((volume) =>
    volume.chapters.map((chapter) => ({ volumeTitle: volume.title, chapter }))
  ).concat((outline.looseChapters ?? []).map((chapter) => ({ volumeTitle: "未分卷", chapter })));

  const allScenes = flatChapters.flatMap(({ volumeTitle, chapter }) =>
    chapter.scenes.map((scene) => ({ volumeTitle, chapter, scene }))
  );

  const scenesByStatus = new Map<string, typeof allScenes>();
  for (const item of allScenes) {
    const list = scenesByStatus.get(item.scene.status ?? "planned") ?? [];
    list.push(item);
    scenesByStatus.set(item.scene.status ?? "planned", list);
  }

  const pendingCommand = (): ProtectedStructureCommand | null => {
    if (!pendingOp) return null;
    if (pendingOp.type === "sceneMove") {
      return { type: "scene.move", sceneId: pendingOp.sceneId, targetChapterId: pendingOp.targetChapterId };
    }
    const ids = selectedSet ? Array.from(selectedSet) : [];
    return { type: "chapters.setStatus", chapterIds: ids, status: pendingOp.status };
  };

  useEffect(() => {
    setPreview(null);
    setError(null);
    if (!pendingOp || !previewStructure) return;
    const command = pendingCommand();
    if (!command) return;
    let active = true;
    setPreviewBusy(true);
    void previewStructure({ type: "structure.preview", projectId: outline.project.id, command })
      .then((result) => {
        if (!active) return;
        if (result) setPreview(result);
        else setError("无法取得权威影响预览，请重试。");
      })
      .catch((reason: unknown) => {
        if (active) setError(reason instanceof Error ? reason.message : "无法取得权威影响预览，请重试。");
      })
      .finally(() => {
        if (active) setPreviewBusy(false);
      });
    return () => { active = false; };
    // selectedSet 每次渲染都是新 Set；选中项变化会通过 pendingOp 的重新发起获得新预览。
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [outline.project.id, pendingOp, previewStructure]);

  const applyPending = async (): Promise<boolean> => {
    if (!pendingOp) return false;
    if (previewStructure && (!preview || preview.stale)) return false;
    if (applyStructureWithProtection) {
      const result = await applyStructureWithProtection({
        type: "structure.applyWithProtection",
        projectId: outline.project.id,
        planId: preview?.planId ?? "",
        protectionReason: `卡板安全重组：${pendingOp.type}`
      });
      if (!result) return false;
      onProtectedApplied?.(result);
      return true;
    }
    if (pendingOp.type === "sceneMove") {
      return onSceneMove!(pendingOp.sceneId, pendingOp.targetChapterId);
    }
    const ids = selectedSet ? Array.from(selectedSet) : [];
    return onChaptersSetStatus!(ids, pendingOp.status);
  };

  const renderSceneActions = (chapter: CreationOutlineChapter, sceneId: string, sceneTitle: string) => {
    if (!editable) return null;
    const sceneIds = chapter.scenes.map((item) => item.id);
    const upTarget = reorderTarget(sceneIds, sceneId, "up");
    const downTarget = reorderTarget(sceneIds, sceneId, "down");
    const upBefore = upTarget.canMove ? upTarget.beforeId : undefined;
    const downBefore = downTarget.canMove ? downTarget.beforeId : undefined;
    return (
      <span className="card-board-card-actions">
        {/* BD-171（续 BD-169）：卡板上每张卡都有一组同名的「上移」「下移」「移章」，
            读屏器列表里分不清动的是哪一场；aria-label 带上场景名，title 保留原样。 */}
            {/* BD-226：卡板的重排钮原本是第二套画法（ChevronUp/ChevronDown），
                与大纲树、卡片类型编辑器各不相同，且这两个尖括号跟「收起」的展开尖括号是同一份美术。
                统一为 ArrowUp/ArrowDown：重排=箭头，展开/收起=尖括号，全应用一套。 */}
        {onSceneReorder && (
          <span className="card-board-reorder">
            <button type="button" title="上移" aria-label={`上移场景「${sceneTitle}」`} disabled={!upTarget.canMove || busy} onClick={() => void runEdit(() => onSceneReorder(sceneId, upBefore))}>
              <ArrowUp size={12} />
            </button>
            <button type="button" title="下移" aria-label={`下移场景「${sceneTitle}」`} disabled={!downTarget.canMove || busy} onClick={() => void runEdit(() => onSceneReorder(sceneId, downBefore))}>
              <ArrowDown size={12} />
            </button>
          </span>
        )}
        {onSceneMove && (
          <select
            className="card-board-move-select"
            aria-label={`把场景「${sceneTitle}」移动到章节`}
            aria-describedby={adviceId}
            value=""
            onChange={(event) => {
              if (event.target.value) setPendingOp({ type: "sceneMove", sceneId, targetChapterId: event.target.value });
            }}
          >
            <option value="" disabled>移章</option>
            {flatChapters
              .filter((item) => item.chapter.id !== chapter.id)
              .map((item) => (
                <option key={item.chapter.id} value={item.chapter.id}>
                  {chapterKey(item.chapter)}
                </option>
              ))}
          </select>
        )}
      </span>
    );
  };

  const renderCard = (item: (typeof allScenes)[number]) => (
    <div key={item.scene.id} className={`card-board-card ${item.scene.id === selectedSceneId ? "active" : ""}`}>
      <button
        type="button"
        className="card-board-card-main"
        aria-current={item.scene.id === selectedSceneId ? "true" : undefined}
        onClick={() => onSelectScene(item.scene.id)}
      >
        <span className="card-board-card-title">
          <FileText size={13} />
          {item.scene.title}
        </span>
        {item.scene.summary && <span className="card-board-card-summary">{item.scene.summary}</span>}
        <span className="card-board-card-meta">
          {chapterKey(item.chapter)}
          {item.volumeTitle && ` · ${item.volumeTitle}`}
          <em>{item.scene.wordCount.toLocaleString("zh-CN")}字</em>
        </span>
        <span className={`card-board-card-status card-board-card-status--${item.scene.status ?? "planned"}`}>
          {SCENE_STATUS_OPTIONS.find((entry) => entry.value === (item.scene.status ?? "planned"))?.label ?? "待规划"}
        </span>
      </button>
      {renderSceneActions(item.chapter, item.scene.id, item.scene.title)}
    </div>
  );

  const renderPendingImpact = () => {
    if (!pendingOp) return null;
    if (pendingOp.type === "sceneMove") {
      const impact = computeSceneMoveImpact(outline, pendingOp.sceneId, pendingOp.targetChapterId);
      if (!impact) return null;
      return (
        <StructureImpactDialog
          title="场景跨章移动影响预览"
          description="场景将从源章移动到目标章。确认后才会写入。"
          rows={preview?.rows ?? impact.rows}
          busy={busy || previewBusy}
          error={error}
          notice={preview?.stale ? "预览已过期，请取消后重新发起操作。" : undefined}
          confirmDisabled={Boolean(previewStructure && (!preview || preview.stale))}
          confirmLabel="确认移动"
          onCancel={() => {
            setError(null);
            setPendingOp(null);
          }}
          onConfirm={() => void runEdit(applyPending).then((ok) => {
            if (ok) setPendingOp(null);
          })}
        />
      );
    }
    const ids = selectedSet ? Array.from(selectedSet) : [];
    const impact = computeBatchStatusImpact(outline, ids, pendingOp.status);
    return (
      <StructureImpactDialog
        title="批量状态影响预览"
        description="以下章节的状态将被统一修改。确认后才会写入。"
        rows={preview?.rows ?? impact.rows}
        busy={busy || previewBusy}
        error={error}
        notice={preview?.stale ? "预览已过期，请取消后重新发起操作。" : undefined}
        confirmDisabled={Boolean(previewStructure && (!preview || preview.stale))}
        confirmLabel="确认修改"
        onCancel={() => {
          setError(null);
          setPendingOp(null);
        }}
        onConfirm={() => void runEdit(applyPending).then((ok) => {
          if (ok) setPendingOp(null);
        })}
      />
    );
  };

  return (
    <div className="card-board" ref={boardRef} {...editFocusHandoff}>
      <div className="card-board-toolbar">
        {/* BD-248（WCAG 3.2.4 判据④）：名牌原先写「卡片板」，通往它的两把切换都叫「场景卡板」
            （BD-247 刚把侧栏那一把统一到这个说法），这里别再留第三种称呼。*/}
        <span className="outline-toolbar-label">场景卡板</span>
        {/* BD-246：常驻可见的「事先说明」，就是下面每枚卡上那颗「移章」下拉框的
            aria-describedby 目标。复用现成的 .outline-page-hint 声明组，不新增间距值。 */}
        <p className="outline-page-hint" id={adviceId}>移章会先弹影响预览，确认后才写入。</p>
        <Tabs<GroupMode>
          variant="pill"
          value={mode}
          onChange={setMode}
          items={[
            { id: "chapter", label: "按章节" },
            { id: "status", label: "按状态" }
          ]}
          ariaLabel="场景卡板分组方式"
        />
      </div>
      {error && (
        <div className="card-board-error" role="alert">
          <span>{error}</span>
          <button type="button" className="card-board-error-close" aria-label="关闭错误提示" onClick={() => setError(null)}>
            ×
          </button>
        </div>
      )}
      {onChaptersSetStatus && selectedSet && selectedSet.size > 0 && (
        <div className="card-board-batch-bar">
          <span className="card-board-batch-count">已选 {selectedSet.size} 章</span>
          <select aria-label="批量状态" value={batchStatus} onChange={(event) => setBatchStatus(event.target.value)}>
            <option value="" disabled>设置状态</option>
            {workflow.map((step) => (
              <option key={step} value={step}>
                {step}
              </option>
            ))}
          </select>
          <Button size="sm" variant="tonal" disabled={!batchStatus} onClick={() => setPendingOp({ type: "batchStatus", status: batchStatus })}>
            应用
          </Button>
        </div>
      )}
      <div className="card-board-scroll">
        {allScenes.length === 0 && <p className="card-board-empty">暂无场景。在「大纲树」中新建卷、章节或场景。</p>}
        {mode === "chapter"
          ? flatChapters.map(({ volumeTitle, chapter }) => (
              <section key={chapter.id} className="card-board-group">
                <header className="card-board-group-head">
                  {onToggleChapterSelected && selectedSet && (
                    <label className="card-board-select">
                      <input
                        type="checkbox"
                        checked={selectedSet.has(chapter.id)}
                        onChange={() => onToggleChapterSelected(chapter.id)}
                        aria-label={`选择 ${chapterKey(chapter)}`}
                      />
                    </label>
                  )}
                  <span className="card-board-chapter-key">{chapterKey(chapter)}</span>
                  {volumeTitle && <small>{volumeTitle}</small>}
                  <em>
                    {chapter.scenes.length}场景 ·{" "}
                    {chapter.scenes.reduce((sum, s) => sum + s.wordCount, 0).toLocaleString("zh-CN")}字
                  </em>
                  {onChapterSetStatus && (
                    <select
                      className="outline-status-select"
                      aria-label={`章节「${chapterKey(chapter)}」的状态`}
                      value={chapter.status}
                      disabled={busy}
                      onChange={(event) => void runEdit(() => onChapterSetStatus(chapter.id, event.target.value))}
                    >
                      {workflow.map((step) => (
                        <option key={step} value={step}>
                          {step}
                        </option>
                      ))}
                    </select>
                  )}
                </header>
                <div className="card-board-grid">
                  {chapter.scenes.map((scene) => renderCard({ volumeTitle, chapter, scene }))}
                  {chapter.scenes.length === 0 && <span className="card-board-empty-hint">暂无场景</span>}
                </div>
              </section>
            ))
          : SCENE_STATUS_OPTIONS.map(({ value, label }) => {
              const items = scenesByStatus.get(value) ?? [];
              if (items.length === 0) return null;
              return (
                <section key={value} className="card-board-group">
                  <header className="card-board-group-head">
                    <span className="card-board-status-key">{label}</span>
                    <em>
                      {items.length}场景 ·{" "}
                      {items.reduce((sum, item) => sum + item.scene.wordCount, 0).toLocaleString("zh-CN")}字
                    </em>
                  </header>
                  <div className="card-board-grid">{items.map(renderCard)}</div>
                </section>
              );
            })}
      </div>
      {pendingOp && renderPendingImpact()}
    </div>
  );
}
