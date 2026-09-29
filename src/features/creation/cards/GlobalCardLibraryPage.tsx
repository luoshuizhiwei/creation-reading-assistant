import { useEffect, useMemo, useState } from "react";
import { ArchiveRestore, BookOpenCheck, Layers3, Paperclip, Pencil, Plus, Search, Trash2, UsersRound } from "lucide-react";
import { Button, Dialog, Select } from "@/components/ui";
import { CardEditorForm } from "@/features/creation/cards/components/CardEditorForm";
import { CardCoverImage } from "@/features/creation/cards/components/CardCoverImage";
import { CardListSidebar } from "@/features/creation/cards/components/CardListSidebar";
import { displayFieldValue } from "@/features/creation/cards/components/CardDynamicFields";
import { useCreationActions } from "@/hooks/useCreationActions";
import { useCreationStore } from "@/stores/creation-store";
import { useUIStore } from "@/stores/ui-store";
import type { CardSummary, ResourceInfo, TrashImpactView, TrashItem } from "@/types/creation";
import "./cards-local.css";

function newGlobalCard(kind: string): CardSummary {
  return {
    id: "new",
    projectId: null,
    linkedProjectIds: [],
    usageCount: 0,
    kind,
    title: "",
    aliases: [],
    fields: {},
    tags: [],
    createdAt: "",
    updatedAt: "",
    revision: 0
  };
}

export function GlobalCardLibraryPage() {
  const cardTypes = useCreationStore((state) => state.cardTypes);
  const cards = useCreationStore((state) => state.cards);
  const projects = useCreationStore((state) => state.projects);
  const selectedCardId = useCreationStore((state) => state.selectedCardId);
  const cardsLoading = useCreationStore((state) => state.cardsLoading);
  const selectCard = useCreationStore((state) => state.selectCard);
  const showToast = useUIStore((state) => state.showToast);
  const { loadCardTypes, loadCards, runStructure, loadResources, attachResource, detachResource, loadTrash, loadTrashImpact } = useCreationActions();
  const [filterKind, setFilterKind] = useState("");
  const [search, setSearch] = useState("");
  const [draft, setDraft] = useState<CardSummary | null>(null);
  const [resources, setResources] = useState<ResourceInfo[]>([]);
  const [confirmingResource, setConfirmingResource] = useState<string | null>(null);
  const [trashItems, setTrashItems] = useState<TrashItem[]>([]);
  const [trashOpen, setTrashOpen] = useState(false);
  const [deleteImpact, setDeleteImpact] = useState<TrashImpactView | null>(null);
  const [deleteImpactBusy, setDeleteImpactBusy] = useState(false);

  useEffect(() => {
    void loadCardTypes();
    void loadTrash().then(setTrashItems);
  }, [loadCardTypes, loadTrash]);

  useEffect(() => {
    void loadCards({ cardKind: filterKind || undefined, search: search || undefined });
  }, [filterKind, loadCards, search]);

  const selectedCard = useMemo(
    () => cards.find((card) => card.id === selectedCardId),
    [cards, selectedCardId]
  );
  const selectedType = useMemo(
    () => cardTypes.find((type) => type.kind === selectedCard?.kind),
    [cardTypes, selectedCard]
  );
  const typeNameMap = useMemo(
    () => new Map(cardTypes.map((type) => [type.kind, type.name])),
    [cardTypes]
  );
  const projectTitleById = useMemo(
    () => new Map(projects.map((project) => [project.id, project.title])),
    [projects]
  );

  useEffect(() => {
    if (selectedCardId && !selectedCard) selectCard(undefined);
  }, [selectCard, selectedCard, selectedCardId]);

  useEffect(() => {
    let cancelled = false;
    if (!selectedCard) {
      setResources([]);
      return undefined;
    }
    void loadResources({ cardId: selectedCard.id }).then((result) => {
      if (!cancelled) setResources(result);
    });
    return () => { cancelled = true; };
  }, [loadResources, selectedCard]);

  const refresh = () =>
    void loadCards({ cardKind: filterKind || undefined, search: search || undefined });

  const refreshTrash = async () => setTrashItems(await loadTrash());

  const openDeleteImpact = async () => {
    if (!selectedCard) return;
    setDeleteImpactBusy(true);
    const impact = await loadTrashImpact({ kind: "trash.impact", entity: "card", entityId: selectedCard.id });
    setDeleteImpact(impact);
    setDeleteImpactBusy(false);
  };

  const deleteSelectedCard = async () => {
    if (!selectedCard) return;
    const ok = await runStructure({ type: "card.delete", cardId: selectedCard.id });
    if (!ok) return;
    setDeleteImpact(null);
    selectCard(undefined);
    refresh();
    await refreshTrash();
    showToast({ tone: "success", title: "全局卡片已移入回收站", body: "30 天内可恢复，原项目关联已保存在恢复状态中。" });
  };

  const restoreCard = async (item: TrashItem) => {
    const ok = await runStructure({ type: "trash.restore", entity: "card", entityId: item.id });
    if (!ok) return;
    await refreshTrash();
    refresh();
    showToast({ tone: "success", title: "全局卡片已恢复", body: "原项目关联、关系、引用和附件仍然保留。" });
  };

  const saveDraft = async () => {
    if (!draft) return;
    const type = cardTypes.find((item) => item.kind === draft.kind);
    const fields = { ...draft.fields };
    for (const field of type?.fields ?? []) {
      if (fields[field.key] === undefined && field.defaultValue !== undefined) fields[field.key] = field.defaultValue;
    }
    const missing = (type?.fields ?? []).filter((field) => {
      if (!field.required) return false;
      const value = fields[field.key];
      return Array.isArray(value) ? value.length === 0 : value === undefined || value === null || value === "";
    });
    if (!draft.title.trim() || missing.length > 0) {
      showToast({
        tone: "error",
        title: "卡片尚未填写完整",
        body: !draft.title.trim() ? "请填写卡片名称。" : missing.map((field) => field.label).join("、")
      });
      return;
    }
    const ok = draft.id === "new"
      ? await runStructure({ type: "card.create", kind: draft.kind, title: draft.title.trim(), aliases: draft.aliases, fields, tags: draft.tags })
      : await runStructure({ type: "card.update", cardId: draft.id, title: draft.title.trim(), aliases: draft.aliases, fields, tags: draft.tags, baseRevision: draft.revision });
    if (!ok) return;
    showToast({ tone: "success", title: draft.id === "new" ? "全局卡片已创建" : "全局卡片已保存" });
    setDraft(null);
    refresh();
  };

  const addResource = async (role: "attachment" | "cover" = "attachment") => {
    if (!selectedCard) return;
    const result = await attachResource(undefined, selectedCard.id, role);
    if (result.canceled || !result.resource) return;
    setResources(await loadResources({ cardId: selectedCard.id }));
    showToast({ tone: "success", title: role === "cover" ? "卡片封面已设置" : "全局附件已添加", body: "所有关联项目都可使用这份卡片资产。" });
  };

  const removeResource = async (resource: ResourceInfo) => {
    if (!selectedCard) return;
    const ok = await detachResource(resource.id);
    if (!ok) return;
    setResources(await loadResources({ cardId: selectedCard.id }));
    setConfirmingResource(null);
    showToast({ tone: "success", title: "全局附件已移除" });
  };

  const detail = draft ? (
    <CardEditorForm
      draft={draft}
      cardTypes={cardTypes}
      projectCards={cards}
      onChangeDraft={setDraft}
      onSave={() => void saveDraft()}
      onCancel={() => setDraft(null)}
    />
  ) : selectedCard ? (
    <article className="cards-detail-card global-card-library-detail">
      <header className="cards-detail-head">
        <div>
          <span className="cards-detail-kind">{typeNameMap.get(selectedCard.kind) ?? selectedCard.kind}</span>
          <h2>{selectedCard.title}</h2>
        </div>
        <Button variant="outline" onClick={() => setDraft(selectedCard)}>
          <Pencil size={14} /> 编辑
        </Button>
        <Button variant="danger-outline" onClick={() => void openDeleteImpact()}>
          <Trash2 size={14} /> 删除全局卡片
        </Button>
      </header>
      {/* 封面在阶段 1 只做到数据层持久化，界面从未渲染；这里补上真实展示。 */}
      <CardCoverImage cardId={selectedCard.id} resources={resources} title={selectedCard.title} />
      {selectedCard.aliases.length > 0 && <p className="cards-aliases">别名：{selectedCard.aliases.join("、")}</p>}
      {selectedCard.tags.length > 0 && <p className="cards-tags">{selectedCard.tags.map((tag) => `#${tag}`).join(" ")}</p>}
      {selectedType && selectedType.fields.length > 0 && (
        <dl className="cards-fields-list">
          {selectedType.fields.map((field) => (
            <div key={field.key} className="cards-field-row">
              <dt>{field.label}</dt>
              <dd>{displayFieldValue(field, selectedCard.fields[field.key], cards)}</dd>
            </div>
          ))}
        </dl>
      )}
      <section className="global-card-library-usage" aria-label="项目使用情况">
        <header>
          <span><UsersRound size={15} /> 使用项目</span>
          <strong>{selectedCard.usageCount}</strong>
        </header>
        {selectedCard.linkedProjectIds.length === 0 ? (
          <p>这张卡片还没有加入任何作品；它会保留在世界观卡片库中。</p>
        ) : (
          <ul>
            {selectedCard.linkedProjectIds.map((projectId) => (
              <li key={projectId}>{projectTitleById.get(projectId) ?? `项目 ${projectId.slice(0, 8)}`}</li>
            ))}
          </ul>
        )}
      </section>
      <section className="global-card-library-assets" aria-label="全局附件">
        <header><span><Paperclip size={15} /> 全局附件</span><div className="global-card-library-asset-actions">
          {!resources.some((resource) => resource.role === "cover") && <Button size="sm" variant="outline" onClick={() => void addResource("cover")}><Plus size={13} /> 设置封面</Button>}
          <Button size="sm" variant="outline" onClick={() => void addResource()}><Plus size={13} /> 添加附件</Button>
        </div></header>
        {resources.length === 0 ? <p>暂无附件。文件归这张全局卡片所有，不依附于任何单一项目。</p> : (
          <ul>{resources.map((resource) => <li key={resource.id}>
            <div><strong>{resource.role === "cover" ? "封面 · " : ""}{resource.originalName ?? resource.relativePath.split("/").pop()}</strong><span>{(resource.size / 1024).toFixed(1)} KB · {resource.sha256.slice(0, 12)}…</span></div>
            <Button size="sm" variant={confirmingResource === resource.id ? "danger-filled" : "danger-outline"} onClick={() => {
              if (confirmingResource === resource.id) void removeResource(resource);
              else setConfirmingResource(resource.id);
            }}><Trash2 size={12} /> {confirmingResource === resource.id ? "确认移除" : "移除"}</Button>
          </li>)}</ul>
        )}
      </section>
      <p className="global-card-library-policy">附件归全局卡片所有；项目解除关联或删除不会删除这些文件。移除附件会让所有关联项目同时失去该文件，因此需要再次点击确认。删除卡片会先进入全局回收站，30 天内可恢复原项目关联、关系、引用和附件。</p>
    </article>
  ) : (
    <div className="cards-detail-empty global-card-library-empty">
      <BookOpenCheck size={30} />
      <h2>从一张可复用的设定开始</h2>
      <p>卡片属于你的世界观，而不是某一个项目。选中卡片可查看它正在服务的作品。</p>
      {/* 规格 §3.3：空状态主 CTA 用 lg */}
      <Button size="lg" onClick={() => setDraft(newGlobalCard(filterKind || cardTypes[0]?.kind || "character"))}>
        <Plus size={15} /> 新建全局卡片
      </Button>
    </div>
  );

  return (
    <>
    <section className="cards-page global-card-library" aria-label="全局卡片库">
      <header className="cards-toolbar global-card-library-toolbar">
        <div className="global-card-library-heading">
          <span className="desktop-card-label">World bible</span>
          <p><Layers3 size={15} /> {cards.length} 张全局卡片 · 改动会反映到所有关联作品</p>
        </div>
        <div className="cards-toolbar-right">
          <Select className="cards-input cards-kind-filter" value={filterKind} onChange={(event) => setFilterKind(event.target.value)} aria-label="按类型筛选">
            <option value="">全部类型</option>
            {cardTypes.map((type) => <option key={type.id} value={type.kind}>{type.name}</option>)}
          </Select>
          <label className="cards-search" aria-label="搜索卡片名称、别名或字段">
            <Search size={14} />
            <input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="搜索名称、别名或字段" />
          </label>
          <Button className="shrink-0 whitespace-nowrap" onClick={() => setDraft(newGlobalCard(filterKind || cardTypes[0]?.kind || "character"))}>
            <Plus size={15} /> 新建全局卡片
          </Button>
          <Button className="shrink-0 whitespace-nowrap" variant="outline" onClick={() => setTrashOpen(true)}>
            <ArchiveRestore size={15} /> 回收站 {trashItems.length}
          </Button>
        </div>
      </header>
      <div className="cards-layout global-card-library-layout">
        <CardListSidebar
          cards={cards}
          selectedCardId={selectedCardId}
          cardsLoading={cardsLoading}
          onSelectCard={selectCard}
          typeNameMap={typeNameMap}
        />
        <main className="cards-detail-pane">{detail}</main>
      </div>
    </section>
    {deleteImpactBusy && (
      <Dialog open title="删除全局卡片" ariaLabel="删除全局卡片" onClose={() => setDeleteImpactBusy(false)}>
        <p>正在统计关联项目和引用影响…</p>
      </Dialog>
    )}
    {deleteImpact && selectedCard && (
      <Dialog
        open
        title="删除全局卡片"
        ariaLabel="删除全局卡片"
        onClose={() => setDeleteImpact(null)}
        footer={(
          <>
            {/* 规格 §3.3 / §4：对话框页脚用 lg，取消在确认左边，破坏性操作仍放最右 */}
            <Button size="lg" variant="ghost" onClick={() => setDeleteImpact(null)}>取消</Button>
            <Button size="lg" variant="danger-outline" onClick={() => void deleteSelectedCard()}>移入回收站</Button>
          </>
        )}
      >
        <p><strong>{deleteImpact.title}</strong> 将从所有项目中暂时隐藏，并保留 30 天。</p>
        <ul className="global-card-delete-impact">
          <li>{deleteImpact.linkedProjectCount ?? 0} 个关联项目</li>
          <li>{deleteImpact.relatedCardCount} 条卡片关系</li>
          <li>{deleteImpact.sceneReferenceCount ?? 0} 个场景引用</li>
          <li>{deleteImpact.annotationCount ?? 0} 条批注</li>
          <li>{deleteImpact.resourceCount} 个附件/封面</li>
        </ul>
        <p>恢复时会重建原项目关联；超过 30 天后才永久清理关系、引用和文件。</p>
      </Dialog>
    )}
    {trashOpen && (
      <Dialog open title="全局卡片回收站" ariaLabel="全局卡片回收站" onClose={() => setTrashOpen(false)}>
        {trashItems.length === 0 ? <p>回收站为空。</p> : (
          <ul className="global-card-trash-list">
            {trashItems.map((item) => (
              <li key={item.id}>
                <div><strong>{item.title}</strong><span>{new Date(item.deletedAt).toLocaleString("zh-CN")} · 30 天内可恢复</span></div>
                {/* 行内右侧按钮不参与收缩，沿用被删除的 .global-card-trash-list button 的 flex: 0 0 auto */}
                <Button size="sm" className="shrink-0" variant="outline" onClick={() => void restoreCard(item)}><ArchiveRestore size={13} /> 恢复</Button>
              </li>
            ))}
          </ul>
        )}
      </Dialog>
    )}
    </>
  );
}

export default GlobalCardLibraryPage;
