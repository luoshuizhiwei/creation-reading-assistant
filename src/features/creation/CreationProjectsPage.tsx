import { useEffect, useState } from "react";
import { BookMarked, Plus } from "lucide-react";
import { Button, EmptyState } from "@/components/ui";
import { CreateProjectWizard } from "@/features/creation/CreateProjectWizard";
import { CardsPage } from "@/features/creation/cards/CardsPage";
import { WritingDesk } from "@/features/creation/editor/WritingDesk";
import { useCreationActions } from "@/hooks/useCreationActions";
import { useCreationStore } from "@/stores/creation-store";

export function CreationProjectsPage() {
  const projects = useCreationStore((state) => state.projects);
  const selectedId = useCreationStore((state) => state.selectedId);
  const navigations = useCreationStore((state) => state.navigations);
  const loading = useCreationStore((state) => state.loading);
  const setSelectedId = useCreationStore((state) => state.setSelectedId);
  const { loadProjects, loadNavigation } = useCreationActions();
  const [wizardOpen, setWizardOpen] = useState(false);
  const [view, setView] = useState<"writing" | "cards">("writing");

  useEffect(() => {
    void loadProjects();
  }, [loadProjects]);

  useEffect(() => {
    if (!selectedId || navigations[selectedId]) return;
    void loadNavigation(selectedId);
  }, [loadNavigation, navigations, selectedId]);

  const selected = projects.find((project) => project.id === selectedId);
  const navigation = selectedId ? navigations[selectedId] : undefined;

  return (
    <div className="desktop-page-scroll paper-shell creation-writing-page">
      <div className="desktop-page-stack creation-writing-stack">
        <section className="desktop-page-hero motion-panel creation-writing-hero">
          <div>
            <div className="desktop-card-label">Creation desk</div>
            <h2>{view === "writing" ? "正文写作台" : "卡片管理"}</h2>
            <p>
              {view === "writing"
                ? "在场景中连续写作；卷章结构在大纲树中管理，中文输入、撤销重做、粘贴清洗和自动保存都在本地完成。"
                : "管理角色、地点、组织等创作卡片与它们之间的关系；字段、别名与标签都随项目保存在本地。"}
            </p>
          </div>
          <div className="desktop-page-actions">
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
            </div>
            {view === "cards" ? (
              <CardsPage project={selected} />
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
    </div>
  );
}
