import { useCallback, useEffect, useState } from "react";
import { ArrowRight, BookOpen, FileUp, FolderInput, Inbox as InboxIcon, PenLine, Plus } from "lucide-react";
import { useCreationActions } from "@/hooks/useCreationActions";
import { Button } from "@/components/ui";
import type { ProjectHomeEntry } from "@/types/creation";

interface ProjectHomePageProps {
  onOpenProject(projectId: string): void;
  onContinueWriting(projectId: string): void;
  onOpenInbox(): void;
  onCreateProject(): void;
  onImportBundle(): void;
  onImportDraft(): void;
  /** 载入预置演示项目（新用户引导），返回项目 ID。 */
  onCreateDemoProject?(): Promise<string | undefined>;
  /** 外部数据变化（如项目包/旧稿导入成功）后递增，触发重新读取 project.home。 */
  refreshKey?: number;
}

function formatUpdatedAt(value?: string): string {
  if (!value) return "—";
  const date = new Date(value);
  const now = new Date();
  const sameDay = date.toDateString() === now.toDateString();
  return sameDay
    ? `今天 ${date.toLocaleTimeString("zh-CN", { hour: "2-digit", minute: "2-digit" })}`
    : date.toLocaleDateString("zh-CN");
}

function formatChars(count: number): string {
  if (count < 1000) return `${count} 字`;
  return `${(count / 1000).toFixed(1)}k 字`;
}

/** 项目首页（规格 §3.1）：最近项目（含真实字数进度）、继续写作、待处理收件箱。 */
export function ProjectHomePage({
  onOpenProject,
  onContinueWriting,
  onOpenInbox,
  onCreateProject,
  onImportBundle,
  onImportDraft,
  onCreateDemoProject,
  refreshKey = 0
}: ProjectHomePageProps) {
  const { loadProjectHome, loadInboxCount } = useCreationActions();
  const [entries, setEntries] = useState<ProjectHomeEntry[]>([]);
  const [pendingCount, setPendingCount] = useState(0);
  const [loaded, setLoaded] = useState(false);

  const refresh = useCallback(async () => {
    const [home, inbox] = await Promise.all([loadProjectHome(), loadInboxCount()]);
    setEntries(home.projects);
    setPendingCount(inbox.pending);
    setLoaded(true);
  }, [loadProjectHome, loadInboxCount]);

  useEffect(() => {
    void refresh();
  }, [refresh, refreshKey]);

  return (
    <section className="desktop-page-scroll paper-shell">
      <div className="desktop-page-stack project-home">
        <div className="project-home-toolbar">
          <div className="desktop-page-actions flex flex-wrap items-center gap-2">
            <Button onClick={onCreateProject}>
              <Plus size={16} />
              新建项目
            </Button>
            <Button variant="secondary" onClick={onOpenInbox}>
              <InboxIcon size={16} />
              待处理收件箱
              {pendingCount > 0 && <em className="desktop-home-inbox-count">{pendingCount}</em>}
            </Button>
            <Button variant="secondary" onClick={onImportBundle}>
              <FolderInput size={16} />
              导入项目包
            </Button>
            <Button variant="secondary" onClick={onImportDraft}>
              <FileUp size={16} />
              导入旧稿
            </Button>
          </div>
        </div>

        <section className="stats-card">
          <h3>最近项目</h3>
          {!loaded ? (
            <p className="stats-note">加载中…</p>
          ) : entries.length === 0 ? (
            <div className="project-home-empty">
              <p className="stats-note">还没有创作项目。新建一个项目后，会自动生成第一章与默认场景，你可以直接开始写作。</p>
              <div className="project-home-create-row flex items-center gap-2 mt-3">
                <Button onClick={onCreateProject}>
                  <Plus size={15} />
                  新建第一个项目
                </Button>
                {onCreateDemoProject && (
                  <Button
                    variant="secondary"
                    onClick={() => {
                      void onCreateDemoProject().then((projectId) => {
                        if (projectId) onOpenProject(projectId);
                      });
                    }}
                  >
                    <BookOpen size={15} />
                    载入演示项目
                  </Button>
                )}
              </div>
            </div>
          ) : (
            <ul className="project-home-list">
              {entries.map((project) => {
                const goal = project.setup?.totalWordGoal;
                const percent = goal && goal > 0 ? Math.min(100, Math.round((project.currentChars / goal) * 100)) : 0;
                return (
                  <li key={project.id} className="project-home-item">
                    <button
                      type="button"
                      className="project-home-item-main hover:-translate-y-0.5 focus:ring-2 focus:outline-none"
                      onClick={() => onOpenProject(project.id)}
                      aria-label={`打开项目：${project.title}`}
                    >
                      <strong>{project.title}</strong>
                      <span className="project-home-meta">
                        更新于 {formatUpdatedAt(project.updatedAt)} · {project.chapterCount} 章 · {project.sceneCount} 场景
                      </span>
                      <span className="project-home-progress">
                        <span className="project-home-chars">
                          {formatChars(project.currentChars)}
                          {goal ? ` / ${goal.toLocaleString("zh-CN")} 字` : ""}
                        </span>
                        {goal && goal > 0 ? (
                          <span
                            className="project-home-progressbar"
                            role="progressbar"
                            aria-valuenow={percent}
                            aria-valuemin={0}
                            aria-valuemax={100}
                            aria-label={`进度 ${percent}%`}
                          >
                            <span className="project-home-progressbar-fill" style={{ width: `${percent}%` }} />
                          </span>
                        ) : (
                          <span className="project-home-no-goal">未设字数目标</span>
                        )}
                      </span>
                    </button>
                    <Button
                      variant="outline"
                      className="shrink-0"
                      onClick={() => onContinueWriting(project.id)}
                      aria-label={`继续写作：${project.title}`}
                    >
                      <PenLine size={14} />
                      继续写作
                    </Button>
                    <ArrowRight size={16} className="project-home-arrow" />
                  </li>
                );
              })}
            </ul>
          )}
        </section>
      </div>
    </section>
  );
}
