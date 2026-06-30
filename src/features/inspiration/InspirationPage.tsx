import { useEffect, useMemo, useRef, useState } from "react";
import { ArrowLeft, Library, Plus, Save, Sparkles, Trash2 } from "lucide-react";
import { AnimatedPanel } from "@/components/interaction";
import { Button, EmptyState, Field, ShellPanel, TextArea, TextInput } from "@/components/ui";
import { useInspirationActions } from "@/hooks/useInspirationActions";
import { useLibraryActions } from "@/hooks/useLibraryActions";
import { runAIAction } from "@/services/ai-service";
import { useInspirationStore } from "@/stores/inspiration-store";
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
  note: "札记"
};

const statusLabels: Record<InspirationStatus, string> = {
  inbox: "未整理",
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
  const selectedId = useInspirationStore((state) => state.selectedId);
  const setSelectedId = useInspirationStore((state) => state.setSelectedId);
  const loading = useInspirationStore((state) => state.loading);
  const { loadInspirations, createItem, updateItem, deleteItem, addVariant } = useInspirationActions();
  const { openReader } = useLibraryActions();
  const [filter, setFilter] = useState("");
  const [draft, setDraft] = useState<InspirationDraft>(EMPTY_DRAFT);
  const [aiBusy, setAiBusy] = useState<AIRunAction | undefined>();
  const autoSaveTimerRef = useRef<number | undefined>();

  useEffect(() => {
    void loadInspirations();
  }, [loadInspirations]);

  const selected = useMemo(() => items.find((item) => item.id === selectedId), [items, selectedId]);

  useEffect(() => {
    if (!selected) {
      setDraft(EMPTY_DRAFT);
      return;
    }
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

    autoSaveTimerRef.current = window.setTimeout(async () => {
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
      }
    }, 1500);

    return () => {
      if (autoSaveTimerRef.current) {
        window.clearTimeout(autoSaveTimerRef.current);
      }
    };
  }, [draft, selected, updateItem]);

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
  const legacySourceLabel =
    !source && selected?.sourceBookId
      ? `来源书籍：${selected.sourceBookId}${selected.sourceLocation?.progressPercent !== undefined ? ` · ${selected.sourceLocation.progressPercent.toFixed(1)}%` : ""}`
      : undefined;

  const returnToReading = async () => {
    if (!readerReturn) return;
    await openReader(readerReturn.bookId);
    clearReaderReturn();
  };

  const saveSelected = async () => {
    if (!selected) return;
    const item = await updateItem(selected.id, {
      title: draft.title,
      body: draft.body,
      type: draft.type,
      status: draft.status,
      tags: parseList(draft.tags),
      platformTags: parseList(draft.platformTags)
    });
    if (item) showToast({ tone: "success", title: "灵感已保存", body: "正文、标签和状态已写入本地灵感箱。" });
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
    try {
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

  return (
    <div className="grid h-full grid-rows-[60px_1fr] overflow-hidden paper-shell">
      <header className="paper-topbar flex items-center gap-3 px-5">
        <Sparkles size={18} />
        <div className="paper-title text-xl font-semibold text-copper">灵感中心</div>
        <div className="min-w-0 flex-1 text-sm text-paper-muted">先收进箱子，再用 AI 打磨成可写素材</div>
        {readerReturn && (
          <Button variant="secondary" onClick={() => void returnToReading()}>
            <ArrowLeft size={16} />
            返回阅读
          </Button>
        )}
        <Button variant="secondary" onClick={() => setScreen("library")}>
          <Library size={16} />
          本地书库
        </Button>
        <Button variant="quiet" onClick={() => setScreen("start")}>
          <ArrowLeft size={16} />
          首页
        </Button>
      </header>

      <div className="grid min-h-0 grid-cols-[340px_1fr] gap-0 bg-paper-bg">
        <ShellPanel className="min-h-0 overflow-hidden border-y-0 border-l-0 bg-paper-soft/55 p-4 shadow-none">
          <div className="mb-3 flex gap-2">
            <TextInput value={filter} onChange={(event) => setFilter(event.target.value)} placeholder="搜索标题、标签、平台标签" />
            <Button onClick={createNew} disabled={loading}>
              <Plus size={16} />
            </Button>
          </div>
          <div className="grid max-h-[calc(100%-52px)] gap-2 overflow-auto pr-1">
            {filtered.map((item) => (
              <button
                key={item.id}
                className={`rounded-xl border p-3 text-left transition duration-150 hover:-translate-y-0.5 ${
                  item.id === selectedId ? "border-copper bg-paper-panel shadow-lift" : "border-paper-line bg-white/60 hover:border-copper/40"
                }`}
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

        <ShellPanel className="min-h-0 overflow-auto border-y-0 border-r-0 bg-transparent p-5 shadow-none">
          {!selected ? (
            <EmptyState title="灵感箱还是空的" body="点击左侧加号，把剧情点子、人设、桥段或阅读札记先收进来。" />
          ) : (
            <div className="mx-auto grid max-w-4xl gap-4">
              <AnimatedPanel className="rounded-2xl border border-paper-line bg-paper-panel p-5 shadow-paper">
                <div className="grid grid-cols-2 gap-4">
                  <Field label="标题">
                    <TextInput value={draft.title} onChange={(event) => setDraft((prev) => ({ ...prev, title: event.target.value }))} />
                  </Field>
                  <div className="grid grid-cols-2 gap-3">
                    <label className="grid gap-1.5 text-sm text-paper-muted">
                      <span className="font-medium text-paper-ink">类型</span>
                      <select className="paper-input h-9" value={draft.type} onChange={(event) => setDraft((prev) => ({ ...prev, type: event.target.value as InspirationType }))}>
                        {Object.entries(typeLabels).map(([value, label]) => (
                          <option key={value} value={value}>
                            {label}
                          </option>
                        ))}
                      </select>
                    </label>
                    <label className="grid gap-1.5 text-sm text-paper-muted">
                      <span className="font-medium text-paper-ink">状态</span>
                      <select className="paper-input h-9" value={draft.status} onChange={(event) => setDraft((prev) => ({ ...prev, status: event.target.value as InspirationStatus }))}>
                        {Object.entries(statusLabels).map(([value, label]) => (
                          <option key={value} value={value}>
                            {label}
                          </option>
                        ))}
                      </select>
                    </label>
                  </div>
                  <Field label="标签，逗号分隔">
                    <TextInput value={draft.tags} onChange={(event) => setDraft((prev) => ({ ...prev, tags: event.target.value }))} placeholder="修罗场, 系统流, 反差" />
                  </Field>
                  <Field label="平台标签，逗号分隔">
                    <TextInput value={draft.platformTags} onChange={(event) => setDraft((prev) => ({ ...prev, platformTags: event.target.value }))} placeholder="番茄, 起点, 刺猬猫" />
                  </Field>
                </div>
                <Field label="正文">
                  <TextArea className="mt-2 min-h-[260px] resize-y" value={draft.body} onChange={(event) => setDraft((prev) => ({ ...prev, body: event.target.value }))} />
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
                <div className="mt-4 flex flex-wrap justify-between gap-2">
                  <div className="flex flex-wrap gap-2">
                    {Object.entries(aiLabels).map(([action, label]) => (
                      <Button key={action} variant="secondary" disabled={Boolean(aiBusy)} onClick={() => void runAI(action as AIRunAction)}>
                        <Sparkles size={15} />
                        {aiBusy === action ? "生成中..." : label}
                      </Button>
                    ))}
                  </div>
                  <div className="flex gap-2">
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
              </AnimatedPanel>

              <AnimatedPanel className="rounded-2xl border border-paper-line bg-paper-panel p-5 shadow-paper">
                <div className="paper-title text-base font-semibold">AI 候选版本</div>
                <div className="mt-3 grid gap-3">
                  {selected.variants.length === 0 ? (
                    <div className="rounded-lg border border-dashed border-paper-line p-4 text-sm text-paper-muted">
                      还没有候选版本。点击上方“润色 / 扩写 / 平台风格化”后，结果会保存在这里，不会覆盖原文。
                    </div>
                  ) : (
                    selected.variants.map((variant) => (
                      <article key={variant.id} className="rounded-xl border border-paper-line bg-paper-soft/40 p-4">
                        <div className="mb-2 text-xs font-medium text-copper">
                          {aiLabels[variant.kind]} · {variant.model || "AI"}
                        </div>
                        <div className="whitespace-pre-wrap text-sm leading-7 text-paper-ink">{variant.content}</div>
                      </article>
                    ))
                  )}
                </div>
              </AnimatedPanel>
            </div>
          )}
        </ShellPanel>
      </div>
    </div>
  );
}

