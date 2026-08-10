import { useEffect, useMemo, useRef, useState } from "react";
import { ArrowLeft, Copy, Library, Plus, Save, Sparkles, Trash2, X } from "lucide-react";
import { AnimatedPanel } from "@/components/interaction";
import { Button, EmptyState, Field, ShellPanel, TextArea, TextInput } from "@/components/ui";
import { useCreationActions } from "@/hooks/useCreationActions";
import { useInspirationActions } from "@/hooks/useInspirationActions";
import { useLibraryActions } from "@/hooks/useLibraryActions";
import { runAIAction } from "@/services/ai-service";
import { useInspirationStore } from "@/stores/inspiration-store";
import { useCreationStore } from "@/stores/creation-store";
import { useLibraryStore } from "@/stores/library-store";
import { useUIStore } from "@/stores/ui-store";
import { useAppStore } from "@/stores/app-store";
import type { AIRunAction } from "@/types/ai";
import type { InspirationStatus, InspirationType } from "@/types/inspiration";

const typeLabels: Record<InspirationType, string> = {
  plot: "剧情点子",
  character: "人设",
  world: "世界观",
  scene: "桥段",
  line: "台词/金句",
  trope: "套路",
  conflict: "冲突点",
  note: "札记"
};

const statusLabels: Record<InspirationStatus, string> = {
  inbox: "未整理",
  reviewing: "待整理",
  usable: "可用",
  polished: "已润色",
  used: "已使用",
  archived: "归档"
};

const aiLabels: Record<AIRunAction, string> = {
  polish: "润色",
  expand: "扩写",
  "platform-style": "平台风格化",
  conflict: "生成冲突点",
  humanize: "去 AI 味"
};

function parseList(value: string): string[] {
  return value
    .split(",")
    .map((item) => item.trim())
    .filter(Boolean);
}

function formatList(value: string[]): string {
  return value.join(", ");
}

interface InspirationDraft {
  title: string;
  body: string;
  type: InspirationType;
  status: InspirationStatus;
  tags: string;
  platformTags: string;
}

const EMPTY_DRAFT: InspirationDraft = {
  title: "",
  body: "",
  type: "note",
  status: "inbox",
  tags: "",
  platformTags: ""
};

export function InspirationPage() {
  const setScreen = useAppStore((state) => state.setScreen);
  const setError = useAppStore((state) => state.setError);
  const readerReturn = useAppStore((state) => state.readerReturn);
  const clearReaderReturn = useAppStore((state) => state.clearReaderReturn);
  const confirmAction = useUIStore((state) => state.confirmAction);
  const showToast = useUIStore((state) => state.showToast);
  const items = useInspirationStore((state) => state.items);
  const books = useLibraryStore((state) => state.books);
  const selectedId = useInspirationStore((state) => state.selectedId);
  const setSelectedId = useInspirationStore((state) => state.setSelectedId);
  const loading = useInspirationStore((state) => state.loading);
  const { loadInspirations, createItem, updateItem, deleteItem, addVariant } = useInspirationActions();
  const { runStructure } = useCreationActions();
  const creationProjects = useCreationStore((state) => state.projects);
  const creationSelectedId = useCreationStore((state) => state.selectedId);
  const { openReader } = useLibraryActions();
  const [filter, setFilter] = useState("");
  const [draft, setDraft] = useState<InspirationDraft>(EMPTY_DRAFT);
  const [aiBusy, setAiBusy] = useState<AIRunAction | undefined>();
  const [isAIRunning, setIsAIRunning] = useState(false);
  const [targetProjectId, setTargetProjectId] = useState<string>("");
  const isAIRunningRef = useRef(false);
  const userEditedDraftRef = useRef(false);
  const autoSaveTimerRef = useRef<number | undefined>();

  useEffect(() => {
    void loadInspirations();
  }, [loadInspirations]);

  const selected = useMemo(() => items.find((item) => item.id === selectedId), [items, selectedId]);

  useEffect(() => {
    if (!selected) {
      userEditedDraftRef.current = false;
      setDraft(EMPTY_DRAFT);
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
  }, [selected]);

  useEffect(() => {
    if (!selected) return;

    if (autoSaveTimerRef.current) {
      window.clearTimeout(autoSaveTimerRef.current);
    }
    if (isAIRunningRef.current) return;
    if (!userEditedDraftRef.current) return;

    autoSaveTimerRef.current = window.setTimeout(async () => {
      if (isAIRunningRef.current) return;
      if (!userEditedDraftRef.current) return;
      const hasChanges =
        draft.title !== selected.title ||
        draft.body !== selected.body ||
        draft.type !== selected.type ||
        draft.status !== selected.status ||
        draft.tags !== formatList(selected.tags) ||
        draft.platformTags !== formatList(selected.platformTags);

      if (hasChanges) {
        await updateItem(selected.id, {
          title: draft.title,
          body: draft.body,
          type: draft.type,
          status: draft.status,
          tags: parseList(draft.tags),
          platformTags: parseList(draft.platformTags)
        });
        userEditedDraftRef.current = false;
      }
    }, 1500);

    return () => {
      if (autoSaveTimerRef.current) {
        window.clearTimeout(autoSaveTimerRef.current);
      }
    };
  }, [draft, selected, updateItem]);

  useEffect(() => {
    return () => {
      if (autoSaveTimerRef.current) {
        window.clearTimeout(autoSaveTimerRef.current);
        autoSaveTimerRef.current = undefined;
      }
    };
  }, []);

  const filtered = useMemo(() => {
    const keyword = filter.trim().toLowerCase();
    if (!keyword) return items;
    return items.filter((item) =>
      [
        item.title,
        item.body,
        item.type,
        item.status,
        item.tags.join(" "),
        item.platformTags.join(" "),
        item.source?.bookTitle ?? "",
        item.source?.bookAuthor ?? "",
        item.source?.locationLabel ?? "",
        item.source?.excerpt ?? ""
      ]
        .join("\n")
        .toLowerCase()
        .includes(keyword)
    );
  }, [filter, items]);

  const source = selected?.source;
  const updateDraft = (patch: Partial<InspirationDraft>) => {
    userEditedDraftRef.current = true;
    setDraft((prev) => ({ ...prev, ...patch }));
  };
  const legacySourceBook = selected?.sourceBookId ? books.find((book) => book.id === selected.sourceBookId) : undefined;
  const legacySourceLabel =
    !source && selected?.sourceBookId
      ? `来源书籍：${legacySourceBook?.title ?? "未知书籍"}${selected.sourceLocation?.progressPercent !== undefined ? ` · ${selected.sourceLocation.progressPercent.toFixed(1)}%` : ""}`
      : undefined;

  const returnToReading = async () => {
    if (!readerReturn) return;
    await openReader(readerReturn.bookId);
    clearReaderReturn();
  };

  const persistSelectedDraft = async (nextDraft: InspirationDraft = draft, options: { toast?: boolean } = { toast: true }) => {
    if (!selected) return;
    const item = await updateItem(selected.id, {
      title: nextDraft.title,
      body: nextDraft.body,
      type: nextDraft.type,
      status: nextDraft.status,
      tags: parseList(nextDraft.tags),
      platformTags: parseList(nextDraft.platformTags)
    });
    if (item) userEditedDraftRef.current = false;
    if (item && options.toast) showToast({ tone: "success", title: "灵感已保存", body: "正文、标签和状态已写入本地灵感箱。" });
    return item;
  };

  const saveSelected = async () => {
    await persistSelectedDraft();
  };

  const createNew = async () => {
    const item = await createItem({
      title: "新的灵感",
      body: "",
      type: "note",
      status: "inbox",
      tags: [],
      platformTags: []
    });
    if (item) {
      setSelectedId(item.id);
      showToast({ tone: "success", title: "已新建灵感", body: "先把想法放进来，之后再慢慢打磨。" });
    }
  };

  const runAI = async (action: AIRunAction) => {
    if (!selected) return;
    setAiBusy(action);
    setIsAIRunning(true);
    isAIRunningRef.current = true;
    if (autoSaveTimerRef.current) {
      window.clearTimeout(autoSaveTimerRef.current);
      autoSaveTimerRef.current = undefined;
    }
    try {
      await persistSelectedDraft(draft, { toast: false });
      const result = await runAIAction({
        action,
        title: draft.title,
        content: draft.body || draft.title,
        platform: parseList(draft.platformTags)[0]
      });
      const item = await addVariant(selected.id, result);
      if (item) showToast({ tone: "success", title: `${aiLabels[action]}完成`, body: "结果已保存为候选版本，没有覆盖原文。" });
    } catch (error) {
      setError(error instanceof Error ? error.message : String(error));
    } finally {
      isAIRunningRef.current = false;
      setIsAIRunning(false);
      setAiBusy(undefined);
    }
  };

  const deleteSelected = async () => {
    if (!selected) return;
    const confirmed = await confirmAction({
      title: "删除这条灵感？",
      body: "删除后会从本地灵感箱移除这条记录和它的 AI 候选版本。",
      confirmLabel: "删除",
      tone: "danger"
    });
    if (!confirmed) return;
    const deleted = await deleteItem(selected.id);
    if (deleted) showToast({ tone: "success", title: "灵感已删除" });
  };

  const copyVariantToClipboard = async (content: string) => {
    try {
      await navigator.clipboard.writeText(content);
      showToast({ tone: "success", title: "已复制到剪贴板" });
    } catch {
      showToast({ tone: "warning", title: "复制失败，请手动选中复制" });
    }
  };

  const excerptToProject = async () => {
    if (!selected) return;
    const projectId = targetProjectId || creationSelectedId || creationProjects[0]?.id;
    if (!projectId || creationProjects.length === 0) {
      showToast({ tone: "warning", title: "还没有创作项目", body: "请先在创作工作台新建项目，再摘录灵感。", });
      return;
    }
    const source = selected.source
      ? {
          bookId: selected.source.bookId ?? selected.sourceBookId,
          bookTitle: selected.source.bookTitle,
          bookAuthor: selected.source.bookAuthor,
          format: selected.source.format,
          chapterTitle: selected.source.chapterTitle,
          locationLabel: selected.source.locationLabel,
          progressPercent: selected.source.progressPercent,
          excerpt: selected.source.excerpt,
          href: selected.source.href,
          cfi: selected.source.cfi,
          scrollTop: selected.source.scrollTop,
          createdFrom: selected.source.createdFrom,
          locator: selected.source.locator,
          createdAt: selected.source.createdAt
        }
      : null;
    const ok = await runStructure({
      type: "card.create",
      projectId,
      kind: "reference",
      title: selected.title,
      tags: selected.tags,
      fields: { note: selected.body.slice(0, 2000) },
      content: {
        excerpt: selected.body,
        source,
        inspirationId: selected.id,
        excerptedAt: selected.updatedAt
      }
    });
    if (ok) {
      showToast({
        tone: "success",
        title: "已摘录到项目资料卡",
        body: `「${selected.title}」已作为资料卡加入创作项目，来源快照（含书籍与阅读位置）随卡保存。`
      });
    }
  };

  const adoptVariant = async (variantContent: string) => {
    if (!selected) return;
    const nextDraft = { ...draft, body: variantContent };
    if (autoSaveTimerRef.current) {
      window.clearTimeout(autoSaveTimerRef.current);
      autoSaveTimerRef.current = undefined;
    }
    setDraft(nextDraft);
    const item = await persistSelectedDraft(nextDraft, { toast: false });
    if (item) showToast({ tone: "success", title: "已采纳为正文", body: "候选内容已立即写入本地灵感箱。" });
  };

  const removeVariant = async (variantId: string) => {
    if (!selected) return;
    const nextVariants = selected.variants.filter((v) => v.id !== variantId);
    await updateItem(selected.id, { variants: nextVariants });
    showToast({ tone: "info", title: "候选已移除" });
  };

  return (
    <div className="desktop-inspiration-page paper-shell">
        <ShellPanel className="desktop-inspiration-list desktop-panel-card motion-panel border-0 bg-transparent p-0 shadow-none">
          <div className="grid gap-3">
            <div>
              <div className="desktop-card-label">Inspiration index</div>
              <h2 className="paper-title mt-1 text-xl font-semibold">灵感中心索引</h2>
              <p className="mt-1 text-xs leading-5 text-paper-muted">先收进箱子，再在桌面端筛选、编辑和打磨。</p>
            </div>
            <div className="flex flex-wrap gap-2">
              {readerReturn && (
                <Button variant="secondary" className="h-8 px-2 text-xs" onClick={() => void returnToReading()}>
                  <ArrowLeft size={14} />
                  返回阅读
                </Button>
              )}
              <Button variant="secondary" className="h-8 px-2 text-xs" onClick={() => setScreen("library")}>
                <Library size={14} />
                书库
              </Button>
            </div>
          </div>
          <div className="mt-3 flex gap-2">
            <TextInput value={filter} onChange={(event) => setFilter(event.target.value)} placeholder="搜索标题、标签、平台标签" />
            <Button onClick={createNew} disabled={loading}>
              <Plus size={16} />
            </Button>
          </div>
          <div className="desktop-inspiration-list-scroll mt-3 grid gap-2 pr-1">
            {filtered.map((item) => (
              <button
                key={item.id}
                className={`desktop-inspiration-card ${item.id === selectedId ? "active" : ""}`}
                onClick={() => setSelectedId(item.id)}
              >
                <div className="paper-title truncate text-sm font-semibold">{item.title}</div>
                <div className="mt-1 flex flex-wrap gap-1 text-[11px] text-paper-muted">
                  <span>{typeLabels[item.type]}</span>
                  <span>·</span>
                  <span>{statusLabels[item.status]}</span>
                  {item.platformTags.slice(0, 2).map((tag) => (
                    <span key={tag} className="rounded-full bg-copper/10 px-2 py-0.5 text-copper">
                      {tag}
                    </span>
                  ))}
                </div>
                <p className="mt-2 line-clamp-2 text-xs leading-5 text-paper-muted">{item.body || "还没有正文。"}</p>
              </button>
            ))}
            {filtered.length === 0 && <EmptyState title="没有匹配灵感" body="换个关键词，或者先新建一条灵感。" />}
          </div>
        </ShellPanel>

        <ShellPanel className="desktop-inspiration-editor desktop-panel-card motion-panel border-0 bg-transparent p-0 shadow-none">
          <div className="desktop-inspiration-editor-scroll">
          {!selected ? (
            <EmptyState title="灵感箱还是空的" body="点击左侧加号，把剧情点子、人设、桥段或阅读札记先收进来。" />
          ) : (
            <div className="grid gap-4">
              <AnimatedPanel className="desktop-editor-card rounded-2xl border border-paper-line bg-paper-panel shadow-paper">
                <div className="mb-4 flex items-center justify-between gap-3">
                  <div>
                    <div className="desktop-card-label">Editor</div>
                    <h2 className="paper-title mt-1 text-xl font-semibold">素材正文</h2>
                  </div>
                  <div className="flex gap-2">
                    {creationProjects.length > 0 && (
                      <>
                        <select
                          className="paper-input h-9 max-w-[160px]"
                          value={targetProjectId || creationSelectedId || creationProjects[0]?.id || ""}
                          onChange={(event) => setTargetProjectId(event.target.value)}
                          aria-label="摘录目标项目"
                          title="摘录到哪个创作项目"
                        >
                          {creationProjects.map((project) => (
                            <option key={project.id} value={project.id}>{project.title}</option>
                          ))}
                        </select>
                        <Button variant="secondary" onClick={() => void excerptToProject()}>
                          <Library size={16} />
                          摘录到项目
                        </Button>
                      </>
                    )}
                    <Button variant="secondary" onClick={() => void deleteSelected()}>
                      <Trash2 size={16} />
                      删除
                    </Button>
                    <Button onClick={() => void saveSelected()}>
                      <Save size={16} />
                      保存
                    </Button>
                  </div>
                </div>
                <div className="grid grid-cols-2 gap-4">
                  <Field label="标题">
                    <TextInput value={draft.title} onChange={(event) => updateDraft({ title: event.target.value })} />
                  </Field>
                  <div className="grid grid-cols-2 gap-3">
                    <label className="grid gap-1.5 text-sm text-paper-muted">
                      <span className="font-medium text-paper-ink">类型</span>
                      <select className="paper-input h-9" value={draft.type} onChange={(event) => updateDraft({ type: event.target.value as InspirationType })}>
                        {Object.entries(typeLabels).map(([value, label]) => (
                          <option key={value} value={value}>
                            {label}
                          </option>
                        ))}
                      </select>
                    </label>
                    <label className="grid gap-1.5 text-sm text-paper-muted">
                      <span className="font-medium text-paper-ink">状态</span>
                      <select className="paper-input h-9" value={draft.status} onChange={(event) => updateDraft({ status: event.target.value as InspirationStatus })}>
                        {Object.entries(statusLabels).map(([value, label]) => (
                          <option key={value} value={value}>
                            {label}
                          </option>
                        ))}
                      </select>
                    </label>
                  </div>
                  <Field label="标签，逗号分隔">
                    <TextInput value={draft.tags} onChange={(event) => updateDraft({ tags: event.target.value })} placeholder="修罗场, 系统流, 反差" />
                  </Field>
                  <Field label="平台标签，逗号分隔">
                    <TextInput value={draft.platformTags} onChange={(event) => updateDraft({ platformTags: event.target.value })} placeholder="番茄, 起点, 刺猬猫" />
                  </Field>
                </div>
                <Field label="正文">
                  <TextArea className="mt-2 min-h-[260px] resize-y" value={draft.body} onChange={(event) => updateDraft({ body: event.target.value })} />
                </Field>
                {(source || legacySourceLabel) && (
                  <div className="mt-3 rounded-xl border border-paper-line bg-paper-soft/45 p-4 text-sm text-paper-muted">
                    <div className="mb-2 flex items-center justify-between gap-3">
                      <div className="font-semibold text-paper-ink">来源卡片</div>
                      {source?.format && <span className="paper-chip uppercase">{source.format}</span>}
                    </div>
                    {source ? (
                      <div className="grid gap-1.5 text-xs leading-5">
                        <div>
                          来源书籍：<span className="text-paper-ink">{source?.bookTitle ?? source.bookId ?? "未知书籍"}</span>
                          {source.bookAuthor ? ` · 作者：${source.bookAuthor}` : ""}
                        </div>
                        <div>位置：{source.locationLabel ?? source.chapterTitle ?? (source.progressPercent !== undefined ? `${source.progressPercent.toFixed(1)}%` : "未记录")}</div>
                        {source.href && <div className="break-all">EPUB 路径：{source.href}</div>}
                        {source.excerpt && (
                          <blockquote className="mt-2 rounded-lg border border-paper-line bg-paper-panel/75 p-3 text-paper-ink">
                            <div className="mb-1 text-[11px] font-semibold text-paper-muted">来源摘录</div>
                            <div className="whitespace-pre-wrap leading-6">{source.excerpt}</div>
                          </blockquote>
                        )}
                      </div>
                    ) : (
                      <div className="text-xs">{legacySourceLabel}</div>
                    )}
                  </div>
                )}
              </AnimatedPanel>
            </div>
          )}
          </div>
        </ShellPanel>

        <aside className="desktop-inspiration-aside desktop-panel-card motion-panel">
          <div className="desktop-inspiration-aside-scroll">
            <section className="desktop-ai-card rounded-2xl border border-paper-line bg-paper-panel">
              <div className="desktop-card-label">AI polish</div>
              <h2 className="paper-title mt-1 text-lg font-semibold">AI 打磨</h2>
              <p className="mt-2 text-xs leading-5 text-paper-muted">所有输出都进入候选版本，不会覆盖正文。</p>
              <div className="mt-3 grid gap-2">
                {Object.entries(aiLabels).map(([action, label]) => (
                  <Button key={action} variant="secondary" disabled={!selected || isAIRunning || Boolean(aiBusy)} onClick={() => void runAI(action as AIRunAction)}>
                    <Sparkles size={15} />
                    {aiBusy === action ? "生成中..." : label}
                  </Button>
                ))}
              </div>
            </section>

            <section className="desktop-ai-card rounded-2xl border border-paper-line bg-paper-panel">
              <div className="paper-title text-base font-semibold">AI 候选版本</div>
              <div className="mt-3 grid gap-3">
                {!selected ? (
                  <div className="rounded-lg border border-dashed border-paper-line p-4 text-sm text-paper-muted">先从左侧选择一条灵感。</div>
                ) : selected.variants.length === 0 ? (
                  <div className="rounded-lg border border-dashed border-paper-line p-4 text-sm text-paper-muted">
                    还没有候选版本。点击上方“润色 / 扩写 / 平台风格化”后，结果会保存在这里。
                  </div>
                ) : (
                  selected.variants.map((variant) => (
                    <article key={variant.id} className="rounded-xl border border-paper-line bg-paper-soft/40 p-4">
                      <div className="mb-2 flex items-start justify-between gap-2">
                        <div className="text-xs font-medium text-copper">
                          {aiLabels[variant.kind]} · {variant.model || "AI"}
                        </div>
                        <button
                          className="shrink-0 rounded-full p-0.5 text-paper-muted transition hover:bg-red-50 hover:text-red-700"
                          title="移除候选"
                          onClick={() => void removeVariant(variant.id)}
                        >
                          <X size={14} />
                        </button>
                      </div>
                      <div className="line-clamp-[8] whitespace-pre-wrap text-sm leading-7 text-paper-ink">{variant.content}</div>
                      <div className="mt-3 flex gap-2">
                        <Button
                          variant="quiet"
                          className="px-2 text-xs"
                          onClick={() => void copyVariantToClipboard(variant.content)}
                        >
                          <Copy size={12} />
                          复制
                        </Button>
                        <Button
                          variant="quiet"
                          className="px-2 text-xs"
                          onClick={() => void adoptVariant(variant.content)}
                        >
                          <Sparkles size={12} />
                          采纳
                        </Button>
                      </div>
                    </article>
                  ))
                )}
              </div>
            </section>
          </div>
        </aside>
    </div>
  );
}
