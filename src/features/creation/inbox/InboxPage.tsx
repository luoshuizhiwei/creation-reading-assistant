import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useCreationActions } from "@/hooks/useCreationActions";
import { useCreationStore } from "@/stores/creation-store";
import { useAppStore } from "@/stores/app-store";
import { useUIStore } from "@/stores/ui-store";
import { getAISettings, runAIAction } from "@/services/ai-service";
import { resolveTargetProject } from "@/features/creation/inbox/inbox-target";
import {
  AiSendConfirmDialog,
  rememberAiSendOptOut,
  shouldConfirmAiSend
} from "@/features/creation/inbox/ai-send-confirm";
import { isAIAvailable, type AIRunAction, type AISettings } from "@/types/ai";
import type { InspirationStatus, InspirationType } from "@/types/inspiration";
import type { InboxItem } from "@/types/creation";
import {
  InboxQuickInput,
  InboxItemList,
  InboxConvertToCardDialog,
  AI_LABELS,
  STATUS_FILTER_OPTIONS,
  EMPTY_DRAFT,
  parseList,
  formatList,
  newVariantId,
  type InboxDraft,
  type SaveStatus
} from "./components";
import "./inbox-local.css";

export interface InboxPageProps {
  /** 转资料卡的目标项目（可选；缺省用第一个项目并允许下拉切换）。 */
  projectId?: string;
}

const INBOX_PAGE_PAGE_SIZE = 200;

export function InboxPage({ projectId }: InboxPageProps) {
  const { loadInbox, deleteInbox, runStructure, updateInbox, createInbox } = useCreationActions();
  const projects = useCreationStore((state) => state.projects);
  const showToast = useUIStore((state) => state.showToast);
  const setError = useAppStore((state) => state.setError);

  const [selectedId, setSelectedId] = useState<string | undefined>();
  const [confirmingId, setConfirmingId] = useState<string | null>(null);
  const [targetProjectId, setTargetProjectId] = useState(projectId ?? "");
  const [draft, setDraft] = useState<InboxDraft>(EMPTY_DRAFT);
  const [saveStatus, setSaveStatus] = useState<SaveStatus>("idle");

  const [aiSettings, setAISettings] = useState<AISettings | null>(null);
  const [aiBusy, setAiBusy] = useState<AIRunAction | undefined>();
  const [isAIRunning, setIsAIRunning] = useState(false);

  const [convertingIds, setConvertingIds] = useState<Set<string>>(() => new Set());
  const convertingIdsRef = useRef(new Set<string>());
  const isAIRunningRef = useRef(false);
  const userEditedDraftRef = useRef(false);
  const autoSaveTimerRef = useRef<number | undefined>();

  /** 弹窗转卡目标条目（支持显式弹窗模式） */
  const [convertToCardDialogItem, setConvertToCardDialogItem] = useState<InboxItem | null>(null);

  /**
   * 分页状态（单一一致状态）：
   * - items：去重后的已加载条目（UI 显示的"已加载数量" = items.length）；
   * - nextOffset：后端已扫描到的位置（分页查询从这里继续，不等于去重后的 items.length）；
   * - hasMore：是否还有更多条目可加载。
   */
  const [items, setItems] = useState<InboxItem[]>([]);
  const [nextOffset, setNextOffset] = useState(0);
  /** 是否还有更多条目可加载。 */
  const [hasMore, setHasMore] = useState(true);
  /** 正在加载更多。 */
  const [loadingMore, setLoadingMore] = useState(false);

  /**
   * 请求序列号：用于取消旧请求，禁止旧请求覆盖新列表。
   * 每次发起 refresh/loadMore/loadAndSelect 都递增并捕获当前 seq；
   * 只有响应到达时 seq 仍匹配才写 state。
   */
  const requestSeqRef = useRef(0);

  const aiAvailable = isAIAvailable(aiSettings);

  /** 状态分组筛选：all = 全部；其余按 InspirationStatus 过滤已加载条目（纯前端过滤）。 */
  const [statusFilter, setStatusFilter] = useState<InspirationStatus | "all">("all");
  const filteredItems = useMemo(
    () => (statusFilter === "all" ? items : items.filter((item) => item.status === statusFilter)),
    [items, statusFilter]
  );

  useEffect(() => {
    setTargetProjectId((current) => resolveTargetProject(projects, projectId, current) ?? "");
  }, [projectId, projects]);

  /** 按 item.id 去重合并两个列表，保持原顺序，后来者追加在末尾。 */
  function mergeUnique(prev: InboxItem[], next: InboxItem[]): InboxItem[] {
    const seen = new Set(prev.map((item) => item.id));
    const merged = [...prev];
    for (const item of next) {
      if (!seen.has(item.id)) {
        seen.add(item.id);
        merged.push(item);
      }
    }
    return merged;
  }

  /**
   * 单一协调的初始刷新：从 offset 0 读取第一页，并在内部处理深链选中。
   *
   * - 如果同时有 pendingSelectId（来自搜索的深链），本调用负责一次读第一页，
   *   命中则选中，未命中则交给 loadAndSelect 继续翻页——避免两次并发读 offset 0。
   * - 使用 requestSeqRef 取消旧请求，禁止旧响应覆盖新列表。
   */
  const refresh = useCallback(
    async (selectAfter?: string): Promise<{ items: InboxItem[]; nextOffset: number }> => {
      const seq = ++requestSeqRef.current;
      const next = await loadInbox({ limit: INBOX_PAGE_PAGE_SIZE, offset: 0 });
      if (seq !== requestSeqRef.current) return { items: next, nextOffset: next.length }; // 旧请求被取消
      setItems(next);
      setNextOffset(next.length);
      setHasMore(next.length >= INBOX_PAGE_PAGE_SIZE);
      // 协调深链：第一页命中就直接选中，不再让 loadAndSelect 重复读 offset 0
      if (selectAfter) {
        const hit = next.find((item) => item.id === selectAfter);
        if (hit) {
          setSelectedId(selectAfter);
        }
      }
      return { items: next, nextOffset: next.length };
    },
    [loadInbox]
  );

  const loadMore = useCallback(async () => {
    if (loadingMore || !hasMore) return;
    const seq = ++requestSeqRef.current;
    setLoadingMore(true);
    try {
      const more = await loadInbox({ limit: INBOX_PAGE_PAGE_SIZE, offset: nextOffset });
      if (seq !== requestSeqRef.current) return;
      // 按 id 去重，避免重复 ID 导致 React duplicate-key 警告
      setItems((prev) => mergeUnique(prev, more));
      // nextOffset 表示后端已扫描位置：按后端返回条数推进，不受去重影响
      setNextOffset((offset) => offset + more.length);
      setHasMore(more.length >= INBOX_PAGE_PAGE_SIZE);
    } catch (error) {
      if (seq === requestSeqRef.current) {
        setError(error instanceof Error ? error.message : String(error));
      }
    } finally {
      if (seq === requestSeqRef.current) setLoadingMore(false);
    }
  }, [hasMore, nextOffset, loadingMore, loadInbox, setError]);

  /**
   * 加载并选中指定条目；目标在已加载范围之外时按页翻页加载直到找到或耗尽。
   *
   * 关键修复（深链 offset 闭包）：
   * - refresh 返回实际 nextOffset，mount effect 把它作为 startOffset 传入，
   *   避免旧 render 闭包捕获 nextOffset=0 导致 0→0→200 重复读取。
   * - 与 refresh 共用 requestSeqRef，refresh 先读 offset 0 后调用本函数时，
   *   本函数从 startOffset（或当前 nextOffset）继续翻页。
   * - 合并结果按 id 去重，避免 duplicate-key 警告；去重不掩盖错误 offset。
   * - 使用 seq 取消，旧请求不覆盖新列表。
   */
  const loadAndSelect = useCallback(
    async (itemId: string, startOffset?: number) => {
      // 1. 已加载范围内
      if (items.some((item) => item.id === itemId)) {
        setSelectedId(itemId);
        return true;
      }
      // 2. 翻页加载直到命中或耗尽；从 startOffset（深链调用方提供的真实扫描位置）或当前 nextOffset 继续
      let offset = startOffset ?? nextOffset;
      let keepSearching = hasMore;
      while (keepSearching) {
        const seq = ++requestSeqRef.current;
        const more = await loadInbox({ limit: INBOX_PAGE_PAGE_SIZE, offset });
        if (seq !== requestSeqRef.current) {
          // 被新请求取消：停止当前搜索，不写 state
          return false;
        }
        if (more.length === 0) {
          keepSearching = false;
          break;
        }
        // 按 id 去重合并
        setItems((prev) => mergeUnique(prev, more));
        // 后端扫描位置按返回条数推进
        setNextOffset((previous) => Math.max(previous, offset + more.length));
        const hit = more.find((item) => item.id === itemId);
        if (hit) {
          setHasMore(more.length >= INBOX_PAGE_PAGE_SIZE);
          setSelectedId(itemId);
          return true;
        }
        if (more.length < INBOX_PAGE_PAGE_SIZE) {
          setHasMore(false);
          keepSearching = false;
        } else {
          offset += more.length;
        }
      }
      showToast({
        tone: "warning",
        title: "目标条目不可用",
        body: "该收件箱条目可能已被删除或已转卡；请刷新后重试。"
      });
      return false;
    },
    [hasMore, items, nextOffset, loadInbox, showToast]
  );

  // 首次挂载：单一协调的刷新流程；同时消费深链选中请求。
  // 使用 useRef 防止 StrictMode 双挂载或依赖变化导致重复刷新。
  const didMountRef = useRef(false);
  useEffect(() => {
    if (didMountRef.current) return;
    didMountRef.current = true;
    const requested = useCreationStore.getState().consumeInboxSelection();
    void refresh(requested).then(async (firstPage) => {
      if (requested && !firstPage.items.some((item) => item.id === requested)) {
        // 第一页未命中：从 refresh 返回的实际 nextOffset 继续翻页，避免旧闭包 nextOffset=0 重复读第一页。
        await loadAndSelect(requested, firstPage.nextOffset);
      }
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    void getAISettings().then(setAISettings).catch(() => setAISettings(null));
  }, []);

  const selected = useMemo(() => items.find((item) => item.id === selectedId), [items, selectedId]);

  useEffect(() => {
    if (!selected) {
      userEditedDraftRef.current = false;
      setDraft(EMPTY_DRAFT);
      setSaveStatus("idle");
      return;
    }
    userEditedDraftRef.current = false;
    setDraft({
      title: selected.title,
      body: selected.body,
      type: selected.type,
      status: selected.status,
      tags: formatList(selected.tags),
      platformTags: formatList(selected.platformTags)
    });
    setSaveStatus("idle");
  }, [selected]);

  const persistSelectedDraft = useCallback(
    async (
      nextDraft: InboxDraft = draft,
      options: { toast?: boolean } = { toast: false }
    ): Promise<{ ok: boolean; latestItem?: InboxItem }> => {
      if (!selected) return { ok: false };
      setSaveStatus("saving");
      const ok = await updateInbox({
        itemId: selected.id,
        baseRevision: selected.revision,
        title: nextDraft.title,
        body: nextDraft.body,
        kind: nextDraft.type,
        status: nextDraft.status,
        tags: parseList(nextDraft.tags),
        platformTags: parseList(nextDraft.platformTags)
      });
      if (ok) {
        userEditedDraftRef.current = false;
        setSaveStatus(options.toast ? "saved" : "idle");
        const next = await refresh();
        const latestItem = next.items.find((item) => item.id === selected.id);
        return { ok: true, latestItem };
      }
      setSaveStatus("unsaved");
      return { ok: false };
    },
    [selected, draft, updateInbox, refresh]
  );

  useEffect(() => {
    if (autoSaveTimerRef.current) {
      window.clearTimeout(autoSaveTimerRef.current);
    }
    if (isAIRunningRef.current) return;
    if (!userEditedDraftRef.current) return;
    autoSaveTimerRef.current = window.setTimeout(() => {
      if (isAIRunningRef.current) return;
      if (!userEditedDraftRef.current) return;
      void persistSelectedDraft(draft, { toast: false }).then((result) => result.ok);
    }, 1500);
    return () => {
      if (autoSaveTimerRef.current) {
        window.clearTimeout(autoSaveTimerRef.current);
      }
    };
  }, [draft, selected, persistSelectedDraft]);

  useEffect(() => {
    return () => {
      if (autoSaveTimerRef.current) {
        window.clearTimeout(autoSaveTimerRef.current);
        autoSaveTimerRef.current = undefined;
      }
    };
  }, []);

  const updateDraft = (patch: Partial<InboxDraft>) => {
    userEditedDraftRef.current = true;
    setSaveStatus("unsaved");
    setDraft((prev) => ({ ...prev, ...patch }));
  };

  const handleCreate = async () => {
    const itemId = await createInbox({
      title: "新的想法",
      body: "",
      kind: "note",
      status: "inbox",
      tags: [],
      platformTags: []
    });
    if (itemId) {
      await refresh();
      setSelectedId(itemId);
      showToast({ tone: "success", title: "已新建想法", body: "先把想法放进来，之后再慢慢打磨。" });
    }
  };

  const handleCreateQuick = async (title: string, kind: InspirationType = "note") => {
    if (!title.trim()) return;
    const itemId = await createInbox({
      title: title.trim(),
      body: "",
      kind,
      status: "inbox",
      tags: [],
      platformTags: []
    });
    if (itemId) {
      await refresh();
      setSelectedId(itemId);
      showToast({ tone: "success", title: "已新建想法", body: "想法已快速记录至收件箱。" });
    }
  };

  const handleDelete = async (itemId: string) => {
    const ok = await deleteInbox({ itemId });
    if (ok) {
      showToast({ tone: "success", title: "已移出收件箱", body: "条目已软删除。" });
      if (selectedId === itemId) setSelectedId(undefined);
      await refresh();
    }
    setConfirmingId(null);
  };

  /** D-C2 lite：发送前确认门控。未勾选「记住选择」时，每次 AI 调用先展示将发送的内容。 */
  const [aiConfirm, setAiConfirm] = useState<Exclude<AIRunAction, "consistency"> | null>(null);

  const requestAI = (action: Exclude<AIRunAction, "consistency">) => {
    if (!selected) return;
    if (!aiAvailable) return;
    if (!shouldConfirmAiSend()) {
      void runAI(action);
      return;
    }
    setAiConfirm(action);
  };

  const runAI = async (action: Exclude<AIRunAction, "consistency">) => {
    if (!selected) return;
    if (!aiAvailable) return;
    if (isAIRunningRef.current) return;
    isAIRunningRef.current = true;
    setIsAIRunning(true);
    setAiBusy(action);
    if (autoSaveTimerRef.current) {
      window.clearTimeout(autoSaveTimerRef.current);
      autoSaveTimerRef.current = undefined;
    }
    try {
      // 1. 先保存当前草稿——失败时立即终止，不发起 AI 请求
      const saveResult = await persistSelectedDraft(draft, { toast: false });
      if (!saveResult.ok) {
        showToast({ tone: "error", title: "草稿保存失败", body: "AI 请求已取消，请解决保存问题后重试。" });
        return;
      }
      // 2. 使用 saveResult 返回的最新 revision，不再从旧闭包 items 读取
      const latestItem = saveResult.latestItem ?? selected;
      // 3. 发起 AI 请求
      const result = await runAIAction({
        action,
        title: draft.title,
        content: draft.body || draft.title,
        platform: parseList(draft.platformTags)[0]
      });
      // 4. 使用最新 revision 追加候选——避免 revision-mismatch
      const newVariant = {
        id: newVariantId(),
        kind: action,
        content: result.content,
        model: result.model,
        createdAt: new Date().toISOString(),
        prompt: result.prompt
      };
      const ok = await updateInbox({
        itemId: selected.id,
        baseRevision: latestItem.revision,
        variants: [...latestItem.variants, newVariant]
      });
      if (ok) {
        showToast({ tone: "success", title: `${AI_LABELS[action]}完成`, body: "结果已保存为候选版本，没有覆盖正文。" });
      } else {
        showToast({ tone: "error", title: "候选保存失败", body: "AI 已返回结果但保存失败，正文和既有候选不变。" });
      }
      await refresh();
    } catch (error) {
      setError(error instanceof Error ? error.message : String(error));
      showToast({ tone: "error", title: "AI 请求失败", body: error instanceof Error ? error.message : String(error) });
    } finally {
      isAIRunningRef.current = false;
      setIsAIRunning(false);
      setAiBusy(undefined);
    }
  };

  const adoptVariant = async (variant: Record<string, unknown>) => {
    if (!selected) return;
    const content = typeof variant.content === "string" ? variant.content : "";
    if (autoSaveTimerRef.current) {
      window.clearTimeout(autoSaveTimerRef.current);
      autoSaveTimerRef.current = undefined;
    }
    const ok = await updateInbox({
      itemId: selected.id,
      baseRevision: selected.revision,
      body: content
    });
    if (ok) {
      showToast({ tone: "success", title: "已采纳为正文", body: "候选内容已写入本地收件箱草稿。" });
      await refresh();
    }
  };

  const copyVariant = async (content: string) => {
    try {
      await navigator.clipboard.writeText(content);
      showToast({ tone: "success", title: "已复制到剪贴板" });
    } catch {
      showToast({ tone: "warning", title: "复制失败，请手动选中复制" });
    }
  };

  const removeVariant = async (variantId: string) => {
    if (!selected) return;
    const current = items.find((item) => item.id === selected.id);
    const variants = current ? current.variants : selected.variants;
    const next = variants.filter((variant) => variant.id !== variantId);
    const ok = await updateInbox({
      itemId: selected.id,
      baseRevision: current ? current.revision : selected.revision,
      variants: next
    });
    if (ok) {
      showToast({ tone: "info", title: "候选已移除" });
      await refresh();
    }
  };

  const handleToCard = async (item: InboxItem) => {
    const target = resolveTargetProject(projects, projectId, targetProjectId);
    if (!target || !projects.some((project) => project.id === target)) {
      showToast({ tone: "warning", title: "还没有创作项目", body: "请先创建项目再转资料卡。" });
      return;
    }
    if (convertingIdsRef.current.has(item.id)) return;
    convertingIdsRef.current.add(item.id);
    setConvertingIds(new Set(convertingIdsRef.current));
    try {
      const ok = await runStructure({
        type: "inbox.convertToCard",
        itemId: item.id,
        baseRevision: item.revision,
        projectId: target
      });
      if (!ok) {
        showToast({ tone: "error", title: "转卡失败", body: `「${item.title}」仍保留在收件箱中，请处理错误后重试。` });
        await refresh();
        return;
      }
      showToast({ tone: "success", title: "已转为资料卡", body: `「${item.title}」已加入创作项目的资料卡。` });
      await refresh();
    } finally {
      convertingIdsRef.current.delete(item.id);
      setConvertingIds(new Set(convertingIdsRef.current));
    }
  };

  const handleDialogConvertToCard = async (item: InboxItem, targetProjId: string) => {
    if (!targetProjId || !projects.some((p) => p.id === targetProjId)) {
      showToast({ tone: "warning", title: "还没有创作项目", body: "请先创建项目再转资料卡。" });
      return;
    }
    if (convertingIdsRef.current.has(item.id)) return;
    convertingIdsRef.current.add(item.id);
    setConvertingIds(new Set(convertingIdsRef.current));
    try {
      const ok = await runStructure({
        type: "inbox.convertToCard",
        itemId: item.id,
        baseRevision: item.revision,
        projectId: targetProjId
      });
      if (!ok) {
        showToast({ tone: "error", title: "转卡失败", body: `「${item.title}」仍保留在收件箱中，请处理错误后重试。` });
        await refresh();
        return;
      }
      showToast({ tone: "success", title: "已转为资料卡", body: `「${item.title}」已加入创作项目的资料卡。` });
      setConvertToCardDialogItem(null);
      await refresh();
    } finally {
      convertingIdsRef.current.delete(item.id);
      setConvertingIds(new Set(convertingIdsRef.current));
    }
  };

  return (
    <section className="inbox-page" aria-label="全局收件箱">
      {aiConfirm && (
        <AiSendConfirmDialog
          actionLabel={AI_LABELS[aiConfirm]}
          title={draft.title}
          content={draft.body || draft.title}
          target={
            [aiSettings?.model, aiSettings?.baseUrl]
              .filter((part) => typeof part === "string" && part.trim() !== "")
              .join(" · ") || "你配置的 AI 服务"
          }
          busy={isAIRunning}
          onConfirm={(_finalContent, remember) => {
            if (remember) rememberAiSendOptOut();
            const action = aiConfirm;
            setAiConfirm(null);
            if (action) void runAI(action);
          }}
          onCancel={() => setAiConfirm(null)}
        />
      )}

      <InboxQuickInput
        projectId={projectId}
        projects={projects}
        targetProjectId={targetProjectId}
        onTargetProjectChange={setTargetProjectId}
        onCreateNew={handleCreate}
        onQuickCreate={handleCreateQuick}
      />

      <div className="inbox-filter-row" role="group" aria-label="按状态筛选收件箱">
        {STATUS_FILTER_OPTIONS.map((option) => {
          const count =
            option.value === "all"
              ? items.length
              : items.filter((item) => item.status === option.value).length;
          const active = statusFilter === option.value;
          return (
            <button
              key={option.value}
              type="button"
              className={`inbox-filter-chip ${active ? "active" : ""}`}
              aria-pressed={active}
              onClick={() => setStatusFilter(option.value)}
            >
              {option.label}
              <em>{count}</em>
            </button>
          );
        })}
      </div>

      <InboxItemList
        items={items}
        filteredItems={filteredItems}
        selectedId={selectedId}
        onSelectId={setSelectedId}
        confirmingId={confirmingId}
        onConfirmDelete={setConfirmingId}
        onDelete={handleDelete}
        convertingIds={convertingIds}
        onConvertToCard={handleToCard}
        hasMore={hasMore}
        loadingMore={loadingMore}
        onLoadMore={() => void loadMore()}
        selectedItem={selected}
        draft={draft}
        saveStatus={saveStatus}
        onDraftChange={updateDraft}
        onSaveDraft={() => void persistSelectedDraft(draft, { toast: true }).then((r) => r.ok)}
        aiAvailable={aiAvailable}
        aiSettings={aiSettings}
        aiBusy={aiBusy}
        isAIRunning={isAIRunning}
        onRequestAI={requestAI}
        onAdoptVariant={adoptVariant}
        onCopyVariant={copyVariant}
        onRemoveVariant={removeVariant}
      />

      {convertToCardDialogItem && (
        <InboxConvertToCardDialog
          open={Boolean(convertToCardDialogItem)}
          item={convertToCardDialogItem}
          projects={projects}
          targetProjectId={targetProjectId}
          onTargetProjectChange={setTargetProjectId}
          onConfirm={handleDialogConvertToCard}
          onClose={() => setConvertToCardDialogItem(null)}
          busy={convertingIds.has(convertToCardDialogItem.id)}
        />
      )}
    </section>
  );
}

export default InboxPage;
