import { useEffect, useState } from "react";
import { BookMarked, Download, Plus, Replace, Search } from "lucide-react";
import { Button, EmptyState } from "@/components/ui";
import { CreateProjectWizard } from "@/features/creation/CreateProjectWizard";
import { CardsPage } from "@/features/creation/cards/CardsPage";
import { HistoryPage } from "@/features/creation/history/HistoryPage";
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
  const { loadProjects, loadNavigation, loadOutline, loadScene, loadCards, exportDraft } = useCreationActions();
  const showToast = useUIStore((state) => state.showToast);
  const [wizardOpen, setWizardOpen] = useState(false);
  const [searchOpen, setSearchOpen] = useState(false);
  const [replaceOpen, setReplaceOpen] = useState(false);
  const [view, setView] = useState<"writing" | "cards" | "history">("writing");

  useEffect(() => {
    void loadProjects();
  }, [loadProjects]);

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
            <h2>{view === "writing" ? "正文写作台" : view === "cards" ? "卡片管理" : "历史与回收站"}</h2>
            <p>
              {view === "writing"
                ? "在场景中连续写作；卷章结构在大纲树中管理，中文输入、撤销重做、粘贴清洗和自动保存都在本地完成。"
                : view === "cards"
                  ? "管理角色、地点、组织等创作卡片与它们之间的关系；字段、别名与标签都随项目保存在本地。"
                  : "误删的内容可在这里恢复，或从命名快照回到某个版本；永久删除前请确认。"}
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
          </div>
        </section>

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
            </div>
            {view === "cards" ? (
              <CardsPage project={selected} />
            ) : view === "history" ? (
              <HistoryPage project={selected} />
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
    </div>
  );
}
