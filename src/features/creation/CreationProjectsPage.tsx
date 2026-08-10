import { useCallback, useEffect, useMemo, useState } from "react";
import { ArchiveRestore, BookMarked, Download, FileUp, FileWarning, Plus, Replace, Search, X } from "lucide-react";
import { Button, EmptyState } from "@/components/ui";
import { CommandPalette } from "@/features/creation/command/CommandPalette";
import { CreateProjectWizard } from "@/features/creation/CreateProjectWizard";
import { CardsPage } from "@/features/creation/cards/CardsPage";
import { HistoryPage } from "@/features/creation/history/HistoryPage";
import { ImportDraftDialog } from "@/features/creation/import/ImportDraftDialog";
import { InboxPage } from "@/features/creation/inbox/InboxPage";
import { MigrationDialog } from "@/features/creation/migration/MigrationDialog";
import { ProofPanel } from "@/features/creation/proof/ProofPanel";
import { StatsPage } from "@/features/creation/stats/StatsPage";
import { WritingDesk } from "@/features/creation/editor/WritingDesk";
import { ReplacePanel } from "@/features/creation/replace/ReplacePanel";
import { SearchPanel } from "@/features/creation/search/SearchPanel";
import { useCreationActions } from "@/hooks/useCreationActions";
import { useCreationStore } from "@/stores/creation-store";
import { useUIStore } from "@/stores/ui-store";
import type { CreationSearchHit } from "@/types/creation";

export function CreationProjectsPage() {
  const projects = useCreationStore((state) => state.projects);
  const selectedId = useCreationStore((state) => state.selectedId);
  const navigations = useCreationStore((state) => state.navigations);
  const loading = useCreationStore((state) => state.loading);
  const setSelectedId = useCreationStore((state) => state.setSelectedId);
  const selectScene = useCreationStore((state) => state.selectScene);
  const selectCard = useCreationStore((state) => state.selectCard);
  const { loadProjects, loadNavigation, loadOutline, loadScene, loadCards, exportDraft, loadMigrationStatus } = useCreationActions();
  const showToast = useUIStore((state) => state.showToast);
  const [wizardOpen, setWizardOpen] = useState(false);
  const [searchOpen, setSearchOpen] = useState(false);
  const [replaceOpen, setReplaceOpen] = useState(false);
  const [proofOpen, setProofOpen] = useState(false);
  const [paletteOpen, setPaletteOpen] = useState(false);
  const [migrationOpen, setMigrationOpen] = useState(false);
  const [migrationNotice, setMigrationNotice] = useState(false);
  const [importOpen, setImportOpen] = useState(false);
  const [view, setView] = useState<"writing" | "cards" | "history" | "stats" | "inbox">("writing");

  useEffect(() => {
    void loadProjects();
  }, [loadProjects]);

  useEffect(() => {
    void loadMigrationStatus().then((status) => {
      setMigrationNotice(status !== null && !status.activated && status.canProceed);
    });
  }, [loadMigrationStatus]);

  useEffect(() => {
    const handleKeydown = (event: KeyboardEvent) => {
      const modifier = event.ctrlKey || event.metaKey;
      if (modifier && !event.altKey && event.key.toLowerCase() === "p") {
        event.preventDefault();
        setPaletteOpen((open) => !open);
      }
    };
    window.addEventListener("keydown", handleKeydown);
    return () => window.removeEventListener("keydown", handleKeydown);
  }, []);

  useEffect(() => {
    if (!selectedId || navigations[selectedId]) return;
    void loadNavigation(selectedId);
  }, [loadNavigation, navigations, selectedId]);

  const selected = projects.find((project) => project.id === selectedId);
  const navigation = selectedId ? navigations[selectedId] : undefined;
  const selectedSceneId = useCreationStore((state) => state.selectedSceneId);
  const selectedScene = navigation?.chapters.flatMap((chapter) => chapter.scenes).find(
    (scene) => scene.id === selectedSceneId
  );
  const selectedChapter = selectedScene
    ? navigation?.chapters.find((chapter) => chapter.scenes.some((scene) => scene.id === selectedScene.id))
    : undefined;

  const handleExport = async () => {
    if (!selected) return;
    const result = await exportDraft(selected.id);
    if (!result.canceled && result.filePath) {
      showToast({ tone: "success", title: "已导出成稿", body: result.filePath });
    }
  };

  const paletteCommands = useMemo(() => {
    const commands: Array<{ id: string; label: string; group: string; keywords?: string[]; shortcut?: string; run(): void }> = [
      { id: "view.writing", label: "正文写作台", group: "视图", keywords: ["写作", "manuscript"], run: () => setView("writing") },
      { id: "view.cards", label: "卡片管理", group: "视图", keywords: ["卡片", "cards"], run: () => setView("cards") },
      { id: "view.history", label: "历史与回收站", group: "视图", keywords: ["回收站", "快照", "history"], run: () => setView("history") },
      { id: "view.stats", label: "统计与创作目标", group: "视图", keywords: ["字数", "统计", "stats"], run: () => setView("stats") },
      { id: "action.search", label: "搜索", group: "操作", keywords: ["查找", "search"], run: () => setSearchOpen(true) },
      { id: "action.replace", label: "查找替换", group: "操作", keywords: ["替换", "replace"], run: () => setReplaceOpen(true) },
      { id: "action.proof", label: "本地校对", group: "操作", keywords: ["校对", "proof", "错别字"], run: () => setProofOpen(true) },
      { id: "action.export", label: "导出成稿", group: "操作", keywords: ["导出", "export"], run: () => void handleExport() },
      { id: "project.create", label: "新建项目", group: "项目", keywords: ["向导", "wizard"], run: () => setWizardOpen(true) }
    ];
    for (const project of projects) {
      commands.push({
        id: `project.open.${project.id}`,
        label: `打开项目：${project.title}`,
        group: "项目",
        keywords: [project.title],
        run: () => {
          setSelectedId(project.id);
          if (!navigations[project.id]) void loadNavigation(project.id);
        }
      });
    }
    for (const item of Object.values(navigations)) {
      for (const chapter of item.chapters) {
        for (const scene of chapter.scenes) {
          commands.push({
            id: `scene.goto.${scene.id}`,
            label: `跳转场景：${scene.title}`,
            group: "大纲",
            keywords: [scene.title, chapter.title],
            run: () => {
              setSelectedId(item.project.id);
              selectScene(scene.id);
              void loadScene(scene.id);
              void loadOutline(item.project.id);
              setView("writing");
            }
          });
        }
      }
    }
    return commands;
  }, [handleExport, loadNavigation, loadOutline, loadScene, navigations, projects, selectScene, setSelectedId]);

  const navigateToHit = async (hit: CreationSearchHit) => {
    setSearchOpen(false);
    if (hit.kind === "project") {
      setSelectedId(hit.id);
      if (!navigations[hit.id]) await loadNavigation(hit.id);
      setView("writing");
      return;
    }
    if (hit.kind === "scene") {
      if (hit.projectId !== selectedId) {
        setSelectedId(hit.projectId);
        await loadNavigation(hit.projectId);
      }
      selectScene(hit.id);
      void loadScene(hit.id);
      void loadOutline(hit.projectId);
      setView("writing");
      return;
    }
    if (hit.kind === "card") {
      if (hit.projectId !== selectedId) {
        setSelectedId(hit.projectId);
        await loadNavigation(hit.projectId);
      }
      selectCard(hit.id);
      void loadCards({ projectId: hit.projectId });
      setView("cards");
      return;
    }
    if (hit.kind === "chapter") {
      if (hit.projectId !== selectedId) {
        setSelectedId(hit.projectId);
        await loadNavigation(hit.projectId);
      }
      const current = navigations[hit.projectId];
      const firstScene = current?.chapters.find((chapter) => chapter.id === hit.id)?.scenes[0];
      if (firstScene) {
        selectScene(firstScene.id);
        void loadScene(firstScene.id);
      }
      void loadOutline(hit.projectId);
      setView("writing");
    }
  };

  return (
    <div className="desktop-page-scroll paper-shell creation-writing-page">
      <div className="desktop-page-stack creation-writing-stack">
        <section className="desktop-page-hero motion-panel creation-writing-hero">
          <div>
            <div className="desktop-card-label">Creation desk</div>
            <h2>{view === "writing" ? "正文写作台" : view === "cards" ? "卡片管理" : view === "history" ? "历史与回收站" : view === "stats" ? "统计与创作目标" : "全局收件箱"}</h2>
            <p>
              {view === "writing"
                ? "在场景中连续写作；卷章结构在大纲树中管理，中文输入、撤销重做、粘贴清洗和自动保存都在本地完成。"
                : view === "cards"
                  ? "管理角色、地点、组织等创作卡片与它们之间的关系；字段、别名与标签都随项目保存在本地。"
                  : view === "history"
                    ? "误删的内容可在这里恢复，或从命名快照回到某个版本；永久删除前请确认。"
                    : view === "stats"
                      ? "项目字数、写作时长、连续写作与修订进度；会话只在输入时计时，不记录具体按键内容。"
                      : "旧灵感迁移后的存放位置；可转为当前项目的资料卡，旧书库与阅读记录保持只读继续使用。"}
            </p>
          </div>
          <div className="desktop-page-actions">
            {selected && (
              <>
                <Button onClick={() => setSearchOpen(true)}>
                  <Search size={16} />
                  搜索
                </Button>
                <Button onClick={() => setReplaceOpen(true)}>
                  <Replace size={16} />
                  查找替换
                </Button>
                <Button onClick={() => setProofOpen(true)}>
                  <FileWarning size={16} />
                  校对
                </Button>
                <Button onClick={() => void handleExport()}>
                  <Download size={16} />
                  导出成稿
                </Button>
              </>
            )}
            <Button onClick={() => setWizardOpen(true)}>
              <Plus size={16} />
              新建项目
            </Button>
            <Button variant="secondary" onClick={() => setImportOpen(true)}>
              <FileUp size={16} />
              导入旧稿
            </Button>
          </div>
        </section>

        {migrationNotice && (
          <section className="migration-banner" role="status">
            <ArchiveRestore size={15} />
            <span>检测到旧数据（灵感/书库）尚未迁移，迁移后旧灵感会进入全局收件箱，书库保持只读继续使用。</span>
            <Button onClick={() => setMigrationOpen(true)}>查看迁移</Button>
            <button type="button" className="migration-banner-dismiss" onClick={() => setMigrationNotice(false)} aria-label="关闭迁移提示">
              <X size={13} />
            </button>
          </section>
        )}
        {selected ? (
          <>
            <div className="creation-project-tabs" role="tablist" aria-label="项目视图">
              <button
                type="button"
                role="tab"
                aria-selected={view === "writing"}
                className={view === "writing" ? "active" : ""}
                onClick={() => setView("writing")}
              >
                写作
              </button>
              <button
                type="button"
                role="tab"
                aria-selected={view === "cards"}
                className={view === "cards" ? "active" : ""}
                onClick={() => setView("cards")}
              >
                卡片
              </button>
              <button
                type="button"
                role="tab"
                aria-selected={view === "history"}
                className={view === "history" ? "active" : ""}
                onClick={() => setView("history")}
              >
                历史
              </button>
              <button
                type="button"
                role="tab"
                aria-selected={view === "stats"}
                className={view === "stats" ? "active" : ""}
                onClick={() => setView("stats")}
              >
                统计
              </button>
              <button
                type="button"
                role="tab"
                aria-selected={view === "inbox"}
                className={view === "inbox" ? "active" : ""}
                onClick={() => setView("inbox")}
              >
                收件箱
              </button>
            </div>
            {view === "cards" ? (
              <CardsPage project={selected} />
            ) : view === "history" ? (
              <HistoryPage project={selected} />
            ) : view === "stats" ? (
              <StatsPage projectId={selected.id} />
            ) : view === "inbox" ? (
              <InboxPage projectId={selected.id} />
            ) : navigation ? (
              <WritingDesk
                projects={projects}
                project={selected}
                navigation={navigation}
                onSelectProject={setSelectedId}
              />
            ) : (
              <section className="creation-writing-loading" role="status">
                <BookMarked size={24} />
                <span>正在打开项目写作台…</span>
              </section>
            )}
          </>
        ) : loading ? (
          <section className="creation-writing-loading" role="status">
            <BookMarked size={24} />
            <span>正在打开项目写作台…</span>
          </section>
        ) : (
          <section className="creation-desk creation-desk-empty creation-writing-empty">
            <EmptyState title="还没有创作项目" body="新建一个项目后，会自动生成第一章与默认场景，你可以直接开始写作。" />
            <Button onClick={() => setWizardOpen(true)}><Plus size={16} />新建第一个项目</Button>
          </section>
        )}
      </div>

      <CreateProjectWizard open={wizardOpen} onClose={() => setWizardOpen(false)} />
      {searchOpen && selected && (
        <SearchPanel
          projectId={selected.id}
          onNavigate={(hit) => void navigateToHit(hit)}
          onClose={() => setSearchOpen(false)}
        />
      )}
      {replaceOpen && selected && (
        <ReplacePanel
          projectId={selected.id}
          chapterId={selectedChapter?.id}
          sceneId={selectedScene?.id}
          onClose={() => setReplaceOpen(false)}
        />
      )}
      {proofOpen && selected && (
        <ProofPanel
          projectId={selected.id}
          onClose={() => setProofOpen(false)}
        />
      )}
      {paletteOpen && (
        <CommandPalette
          commands={paletteCommands}
          onClose={() => setPaletteOpen(false)}
        />
      )}
      {migrationOpen && (
        <MigrationDialog
          onClose={() => setMigrationOpen(false)}
          onMigrated={() => {
            setMigrationNotice(false);
            void loadMigrationStatus();
          }}
        />
      )}
      {importOpen && (
        <ImportDraftDialog
          onClose={() => setImportOpen(false)}
          onImported={() => void loadProjects()}
        />
      )}
    </div>
  );
}
