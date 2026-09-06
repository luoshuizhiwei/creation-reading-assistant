import { useEffect, useMemo, useState } from "react";
import {
  Columns,
  Download,
  Flag,
  GitBranch,
  Globe2,
  Layers,
  LayoutGrid,
  Pencil,
  Plus,
  Search,
  Trash2,
  Upload
} from "lucide-react";
import { Select, Tabs } from "@/components/ui";
import { BoardView } from "@/features/creation/cards/BoardView";
import { CardTypeEditor } from "@/features/creation/cards/CardTypeEditor";
import { RelationTypeEditor } from "@/features/creation/cards/RelationTypeEditor";
import { BackgroundPage } from "@/features/creation/background/BackgroundPage";
import { CardImportDialog } from "@/features/creation/cards/import-export/CardImportDialog";
import { CardExportDialog } from "@/features/creation/cards/import-export/CardExportDialog";
import { CardListSidebar } from "@/features/creation/cards/components/CardListSidebar";
import { CardEditorForm } from "@/features/creation/cards/components/CardEditorForm";
import { CardRelationsManager } from "@/features/creation/cards/components/CardRelationsManager";
import { displayFieldValue } from "@/features/creation/cards/components/CardDynamicFields";
import "./cards-local.css";
import { useCreationActions } from "@/hooks/useCreationActions";
import { useCreationStore } from "@/stores/creation-store";
import { useUIStore } from "@/stores/ui-store";
import type {
  CardSummary,
  CreationProjectSummary,
  ResourceInfo
} from "@/types/creation";

export interface CardsPageProps {
  project: CreationProjectSummary;
}

export function CardsPage({ project }: CardsPageProps) {
  const cardTypes = useCreationStore((state) => state.cardTypes);
  const relationTypes = useCreationStore((state) => state.relationTypes);
  const cards = useCreationStore((state) => state.cards);
  const cardRelations = useCreationStore((state) => state.cardRelations);
  const selectedCardId = useCreationStore((state) => state.selectedCardId);
  const cardsLoading = useCreationStore((state) => state.cardsLoading);
  const selectCard = useCreationStore((state) => state.selectCard);
  const {
    loadCardTypes,
    loadRelationTypes,
    loadCards,
    loadCardRelations,
    runStructure,
    subscribeProject,
    loadResources,
    attachResource,
    detachResource
  } = useCreationActions();
  const showToast = useUIStore((state) => state.showToast);

  const [view, setView] = useState<"board" | "list">("board");
  const [mode, setMode] = useState<"cards" | "background">("cards");
  const [filterKind, setFilterKind] = useState("");
  const [search, setSearch] = useState("");
  const [draft, setDraft] = useState<CardSummary | null>(null);
  const [confirmDelete, setConfirmDelete] = useState(false);
  const [resources, setResources] = useState<ResourceInfo[]>([]);
  const [confirmingResource, setConfirmingResource] = useState<string | null>(null);
  const [showCardTypeEditor, setShowCardTypeEditor] = useState(false);
  const [showRelationTypeEditor, setShowRelationTypeEditor] = useState(false);
  const [showMilestone, setShowMilestone] = useState(false);
  const [milestoneReason, setMilestoneReason] = useState("");
  const [milestoneBusy, setMilestoneBusy] = useState(false);
  const [showImportDialog, setShowImportDialog] = useState(false);
  const [showExportDialog, setShowExportDialog] = useState(false);

  useEffect(() => {
    setDraft(null);
    setConfirmDelete(false);
    setResources([]);
    setShowCardTypeEditor(false);
    setShowRelationTypeEditor(false);
    setShowMilestone(false);
    setMilestoneReason("");
  }, [project.id]);

  useEffect(() => {
    if (mode !== "cards") return;
    void loadCardTypes(project.id);
    void loadRelationTypes(project.id);
  }, [loadCardTypes, loadRelationTypes, project.id, mode]);

  // 订阅项目已提交事件：卡片/关系变更（含看板拖拽改类型）实时刷新列表，跨视图保持一致。
  useEffect(() => {
    if (mode !== "cards") return undefined;
    return subscribeProject(project.id);
  }, [project.id, subscribeProject, mode]);

  useEffect(() => {
    if (mode !== "cards") return;
    void loadCards({ projectId: project.id, cardKind: filterKind || undefined, search: search || undefined });
  }, [loadCards, project.id, filterKind, search, mode]);

  const projectCards = useMemo(
    () => cards.filter((card) => card.projectId === project.id),
    [cards, project.id]
  );

  // 项目切换时 store 会先清空旧项目卡片；搜索深链则会先加载目标项目卡片再选中。
  // 这里只清理确实不属于当前项目结果集的旧选择，避免挂载时抹掉合法的深链选择。
  useEffect(() => {
    if (selectedCardId && !projectCards.some((card) => card.id === selectedCardId)) {
      selectCard(undefined);
    }
  }, [projectCards, selectCard, selectedCardId]);

  const selectedCard = useMemo(
    () => projectCards.find((card) => card.id === selectedCardId),
    [projectCards, selectedCardId]
  );
  const selectedType = useMemo(
    () => cardTypes.find((type) => type.kind === selectedCard?.kind),
    [cardTypes, selectedCard]
  );
  const typeNameMap = useMemo(
    () => new Map(cardTypes.map((type) => [type.kind, type.name])),
    [cardTypes]
  );
  const relations = selectedCard ? cardRelations[selectedCard.id] : undefined;

  useEffect(() => {
    if (selectedCard) void loadCardRelations(project.id, selectedCard.id);
  }, [loadCardRelations, project.id, selectedCard]);

  useEffect(() => {
    let cancelled = false;
    void loadResources({ projectId: project.id, cardId: selectedCard?.id }).then((list) => {
      if (!cancelled) setResources(list);
    });
    return () => {
      cancelled = true;
    };
  }, [loadResources, project.id, selectedCard?.id]);

  const handleAttach = async () => {
    if (!selectedCard) return;
    const result = await attachResource(project.id, selectedCard.id);
    if (result.canceled || !result.resource) return;
    showToast({ tone: "success", title: "附件已添加", body: "文件保存在项目工作区内。" });
    void loadResources({ projectId: project.id, cardId: selectedCard.id }).then(setResources);
  };

  const handleDetach = async (resource: ResourceInfo) => {
    const ok = await detachResource(resource.id);
    if (ok) {
      showToast({ tone: "success", title: "附件已移除", body: `${resource.originalName ?? resource.relativePath}` });
      void loadResources({ projectId: project.id, cardId: selectedCard?.id }).then(setResources);
    }
  };

  const typeById = useMemo(() => new Map(cardTypes.map((type) => [type.kind, type])), [cardTypes]);

  const refreshCards = () =>
    void loadCards({ projectId: project.id, cardKind: filterKind || undefined, search: search || undefined });

  const newCardInKind = (kind: string) => {
    setDraft({
      id: "new",
      projectId: project.id,
      kind: kind || cardTypes[0]?.kind || "character",
      title: "",
      aliases: [],
      fields: {},
      tags: [],
      createdAt: "",
      updatedAt: "",
      revision: 0
    });
  };

  const saveDraft = async () => {
    if (!draft || draft.projectId !== project.id) return;
    const fields = { ...draft.fields };
    for (const field of typeById.get(draft.kind)?.fields ?? []) {
      if (fields[field.key] === undefined && field.defaultValue !== undefined) {
        fields[field.key] = field.defaultValue;
      }
    }
    // 必填字段未完成（且无默认值）时，禁止保存，避免把半成品写入库。
    const missingRequired = (typeById.get(draft.kind)?.fields ?? []).filter((field) => {
      if (!field.required) return false;
      const value = fields[field.key];
      if (Array.isArray(value)) return value.length === 0;
      return value === undefined || value === null || value === "";
    });
    if (missingRequired.length > 0) {
      showToast({
        tone: "error",
        title: "必填字段未完成",
        body: missingRequired.map((field) => field.label).join("、")
      });
      return;
    }
    if (draft.id === "new") {
      const ok = await runStructure({
        type: "card.create",
        projectId: project.id,
        kind: draft.kind,
        title: draft.title.trim(),
        aliases: draft.aliases,
        fields,
        tags: draft.tags
      });
      if (ok) {
        showToast({ tone: "success", title: "卡片已创建" });
        setDraft(null);
        refreshCards();
      }
    } else {
      const ok = await runStructure({
        type: "card.update",
        cardId: draft.id,
        title: draft.title.trim(),
        aliases: draft.aliases,
        fields,
        tags: draft.tags,
        baseRevision: draft.revision
      });
      if (ok) {
        showToast({ tone: "success", title: "卡片已保存" });
        setDraft(null);
        refreshCards();
      }
    }
  };

  const moveCardKind = async (cardId: string, kind: string) => {
    const card = projectCards.find((item) => item.id === cardId);
    if (!card || card.kind === kind) return;
    const ok = await runStructure({
      type: "card.update",
      cardId,
      kind,
      baseRevision: card.revision
    });
    if (ok) {
      showToast({ tone: "success", title: `已移动到「${typeNameMap.get(kind) ?? kind}」` });
      refreshCards();
    }
  };

  const removeCard = async () => {
    if (!selectedCard || !confirmDelete) return;
    const ok = await runStructure({ type: "card.delete", cardId: selectedCard.id });
    if (ok) {
      showToast({ tone: "success", title: "卡片已删除" });
      selectCard(undefined);
      setConfirmDelete(false);
      refreshCards();
    }
  };

  const handleCreateRelation = async (payload: {
    relationTypeId: string;
    targetCardId: string;
    note?: string;
  }): Promise<boolean> => {
    if (!selectedCard) return false;
    const ok = await runStructure({
      type: "cardRelation.create",
      projectId: project.id,
      fromCardId: selectedCard.id,
      toCardId: payload.targetCardId,
      relationTypeId: payload.relationTypeId,
      note: payload.note
    });
    if (ok) {
      showToast({ tone: "success", title: "关系已建立" });
      void loadCardRelations(project.id, selectedCard.id);
      return true;
    }
    return false;
  };

  const handleRemoveRelation = async (relationId: string): Promise<boolean> => {
    const ok = await runStructure({ type: "cardRelation.delete", relationId });
    if (ok) {
      showToast({ tone: "success", title: "关系已删除" });
      if (selectedCard) void loadCardRelations(project.id, selectedCard.id);
      return true;
    }
    // 失败时不重新加载：UI 仍保留原关系，不先从本地消失。
    return false;
  };

  const createMilestone = async () => {
    if (!selectedCard) return;
    const reason = milestoneReason.trim();
    if (!reason) {
      showToast({ tone: "warning", title: "请填写里程碑说明" });
      return;
    }
    setMilestoneBusy(true);
    const ok = await runStructure({
      type: "snapshot.create",
      projectId: project.id,
      subjectType: "card",
      subjectId: selectedCard.id,
      reason
    });
    setMilestoneBusy(false);
    if (ok) {
      showToast({ tone: "success", title: "已创建命名里程碑" });
      setShowMilestone(false);
      setMilestoneReason("");
    }
  };

  const detailPane = draft ? (
    <CardEditorForm
      draft={draft}
      cardTypes={cardTypes}
      projectCards={projectCards}
      onChangeDraft={setDraft}
      onSave={() => void saveDraft()}
      onCancel={() => setDraft(null)}
    />
  ) : selectedCard ? (
    <div className="cards-detail-card">
      <header className="cards-detail-head">
        <div>
          <span className="cards-detail-kind">{typeNameMap.get(selectedCard.kind) ?? selectedCard.kind}</span>
          <h3>{selectedCard.title}</h3>
        </div>
        <div className="cards-detail-actions">
          <button type="button" onClick={() => setDraft(selectedCard)} title="编辑"><Pencil size={15} /></button>
          <button
            type="button"
            className={confirmDelete ? "confirming" : ""}
            onClick={() => {
              if (confirmDelete) void removeCard();
              else setConfirmDelete(true);
            }}
            title="删除卡片"
          >
            <Trash2 size={15} />
          </button>
        </div>
      </header>

      {selectedCard.aliases.length > 0 && (
        <p className="cards-aliases">别名：{selectedCard.aliases.join("、")}</p>
      )}
      {selectedCard.tags.length > 0 && (
        <p className="cards-tags">{selectedCard.tags.map((tag) => `#${tag}`).join(" ")}</p>
      )}

      {selectedType && selectedType.fields.length > 0 && (
        <dl className="cards-fields-list">
          {selectedType.fields.map((field) => (
            <div key={field.key} className="cards-field-row">
              <dt>{field.label}</dt>
              <dd>{displayFieldValue(field, selectedCard.fields[field.key], projectCards)}</dd>
            </div>
          ))}
        </dl>
      )}

      <CardRelationsManager
        selectedCard={selectedCard}
        projectCards={projectCards}
        relationTypes={relationTypes}
        relations={relations}
        typeNameMap={typeNameMap}
        onCreateRelation={handleCreateRelation}
        onDeleteRelation={handleRemoveRelation}
      />

      <section className="cards-relations">
        <header className="cards-relations-head">
          <h4>附件</h4>
          <button type="button" onClick={() => void handleAttach()}>
            <Plus size={13} /> 添加附件
          </button>
        </header>
        {resources.length === 0 ? (
          <p className="cards-relations-empty">暂无附件。文件保存在项目工作区内，随项目包一起导出。</p>
        ) : (
          <ul className="cards-relation-list">
            {resources.map((resource) => (
              <li key={resource.id}>
                <span>{resource.originalName ?? resource.relativePath.split("/").pop()}</span>
                <em>{(resource.size / 1024).toFixed(1)} KB</em>
                <small>{resource.sha256.slice(0, 12)}…</small>
                <button
                  type="button"
                  className={confirmingResource === resource.id ? "confirming" : ""}
                  onClick={() => {
                    if (confirmingResource === resource.id) {
                      void handleDetach(resource);
                      setConfirmingResource(null);
                    } else {
                      setConfirmingResource(resource.id);
                    }
                  }}
                >
                  <Trash2 size={12} />
                  {confirmingResource === resource.id ? "确认移除" : "移除"}
                </button>
              </li>
            ))}
          </ul>
        )}
      </section>

      <section className="cards-relations">
        <header className="cards-relations-head">
          <h4>里程碑</h4>
          <button type="button" onClick={() => { setShowMilestone((value) => !value); setMilestoneReason(""); }}>
            <Flag size={13} /> 创建命名里程碑
          </button>
        </header>
        {showMilestone && (
          <div className="cards-milestone-form">
            <input
              className="cards-input"
              value={milestoneReason}
              onChange={(event) => setMilestoneReason(event.target.value)}
              placeholder="里程碑说明，例如：角色设定定稿 v1"
            />
            <div className="cards-form-actions">
              <button type="button" className="cards-save" disabled={milestoneBusy} onClick={() => void createMilestone()}>保存里程碑</button>
              <button type="button" className="cards-cancel" onClick={() => { setShowMilestone(false); setMilestoneReason(""); }}>取消</button>
            </div>
          </div>
        )}
        <p className="cards-relations-empty">命名里程碑会为当前卡片创建永久保留的快照，用于版本对比与恢复。</p>
      </section>
    </div>
  ) : (
    <div className="cards-detail-empty">
      <Layers size={28} />
      <p>在左侧选择一张卡片查看详情，或新建一张。</p>
    </div>
  );

  return (
    <section className="cards-page" aria-label="设定卡管理">
      <header className="cards-toolbar">
        <div className="cards-toolbar-left">
          <span className="desktop-card-label">Cards</span>
          {mode === "cards" && (
            <>
              <Tabs<"board" | "list">
                variant="pill"
                value={view}
                onChange={setView}
                items={[
                  { id: "board", label: <span className="inline-flex items-center gap-1.5"><LayoutGrid size={13} /> 看板</span> },
                  { id: "list", label: <span className="inline-flex items-center gap-1.5"><Columns size={13} /> 列表</span> }
                ]}
                ariaLabel="卡片视图"
              />
              {view === "list" && (
                <Select
                  className="cards-input cards-kind-filter"
                  value={filterKind}
                  onChange={(event) => setFilterKind(event.target.value)}
                  aria-label="按类型筛选"
                >
                  <option value="">全部类型</option>
                  {cardTypes.map((type) => (
                    <option key={type.id} value={type.kind}>{type.name}</option>
                  ))}
                </Select>
              )}
            </>
          )}
          <button
            type="button"
            className={`cards-view-switch-button ${mode === "background" ? "active" : ""}`}
            onClick={() => setMode((current) => (current === "cards" ? "background" : "cards"))}
            aria-pressed={mode === "background"}
          >
            <Globe2 size={13} /> 背景设定
          </button>
        </div>
        {mode === "cards" && (
          <div className="cards-toolbar-right">
            <span className="cards-search">
              <Search size={14} />
              <input
                value={search}
                onChange={(event) => setSearch(event.target.value)}
                placeholder="搜索名称或别名"
              />
            </span>
            <button type="button" className="cards-manage" onClick={() => setShowCardTypeEditor(true)}>
              <Layers size={14} /> 卡片类型
            </button>
            <button type="button" className="cards-manage" onClick={() => setShowRelationTypeEditor(true)}>
              <GitBranch size={14} /> 关系类型
            </button>
            <button type="button" className="cards-manage" onClick={() => setShowImportDialog(true)}>
              <Upload size={14} /> 导入
            </button>
            <button type="button" className="cards-manage" onClick={() => setShowExportDialog(true)}>
              <Download size={14} /> 导出
            </button>
            <button type="button" className="cards-add" onClick={() => newCardInKind(filterKind)}>
              <Plus size={15} /> 新建卡片
            </button>
          </div>
        )}
      </header>

      {mode === "background" ? (
        <BackgroundPage projectId={project.id} />
      ) : cardsLoading && view === "board" ? (
        <p className="cards-empty" role="status">正在读取卡片…</p>
      ) : view === "board" ? (
        <div className="cards-board-layout">
          <div className="cards-board-pane">
            <BoardView
              cardTypes={cardTypes}
              cards={projectCards}
              selectedCardId={selectedCardId}
              onSelectCard={(cardId) => selectCard(cardId)}
              onMoveKind={(cardId, kind) => void moveCardKind(cardId, kind)}
              onNewCard={(kind) => newCardInKind(kind)}
            />
          </div>
          <aside className="cards-detail-pane">{detailPane}</aside>
        </div>
      ) : (
        <div className="cards-layout">
          <CardListSidebar
            cards={projectCards}
            selectedCardId={selectedCardId}
            cardsLoading={cardsLoading}
            onSelectCard={(cardId) => selectCard(cardId)}
            typeNameMap={typeNameMap}
            cardTypes={cardTypes}
            filterKind={filterKind}
            onFilterKindChange={setFilterKind}
            search={search}
            onSearchChange={setSearch}
            onNewCard={() => newCardInKind(filterKind)}
          />
          <main className="cards-detail-pane">{detailPane}</main>
        </div>
      )}
      {showCardTypeEditor && (
        <CardTypeEditor
          projectId={project.id}
          cardTypes={cardTypes}
          cards={projectCards}
          relationTypes={relationTypes}
          onClose={() => setShowCardTypeEditor(false)}
        />
      )}
      {showRelationTypeEditor && (
        <RelationTypeEditor
          projectId={project.id}
          cardTypes={cardTypes}
          relationTypes={relationTypes}
          relations={Object.values(cardRelations).flatMap((value) => [...value.outgoing, ...value.incoming])}
          onClose={() => setShowRelationTypeEditor(false)}
        />
      )}
      {showImportDialog && (
        <CardImportDialog
          projectId={project.id}
          onClose={() => setShowImportDialog(false)}
          onImported={() => {
            setShowImportDialog(false);
            refreshCards();
          }}
        />
      )}
      {showExportDialog && (
        <CardExportDialog
          projectId={project.id}
          filter={{ cardKind: filterKind || undefined, search: search || undefined }}
          onClose={() => setShowExportDialog(false)}
        />
      )}
    </section>
  );
}

export default CardsPage;
