import { lazy, Suspense, useCallback, useEffect, useMemo, useState } from "react";
import { createDemoProject } from "@/features/creation/demo/create-demo-project";
import {
  ArchiveRestore,
  BookMarked,
  BookOpen,
  ChevronLeft,
  Download,
  FileUp,
  FileWarning,
  FolderInput,
  FolderOutput,
  Layers,
  LayoutDashboard,
  ListTree,
  PenLine,
  Replace,
  Search,
  X
} from "lucide-react";
import { Button } from "@/components/ui";
import { RingButton } from "@/components/interaction";
import { ScreenFallback } from "@/components/ScreenFallback";
// creation 子系统子页面：tab 级 / dialog 级 lazy 分割（C2 轨道）。
// 均为 named export，用 .then(m => ({ default: m.X })) 适配 React.lazy 的 default 形状。
// 默认视图 overview（OverviewPage）属高频路径仍 lazy（打开项目时短暂骨架可接受）；
// 初始首页 ProjectHomePage 保持静态，避开启动双骨架（见下方渲染处）。
const LazyCommandPalette = lazy(() => import("@/features/creation/command/CommandPalette").then((m) => ({ default: m.CommandPalette })));
const LazyCreateProjectWizard = lazy(() => import("@/features/creation/CreateProjectWizard").then((m) => ({ default: m.CreateProjectWizard })));
const LazyCardsPage = lazy(() => import("@/features/creation/cards/CardsPage").then((m) => ({ default: m.CardsPage })));
const LazyHistoryPage = lazy(() => import("@/features/creation/history/HistoryPage").then((m) => ({ default: m.HistoryPage })));
const LazyImportDraftDialog = lazy(() => import("@/features/creation/import/ImportDraftDialog").then((m) => ({ default: m.ImportDraftDialog })));
const LazyExportDraftDialog = lazy(() => import("@/features/creation/export/ExportDraftDialog").then((m) => ({ default: m.ExportDraftDialog })));
const LazyMigrationDialog = lazy(() => import("@/features/creation/migration/MigrationDialog").then((m) => ({ default: m.MigrationDialog })));
const LazyOutlinePage = lazy(() => import("@/features/creation/outline/OutlinePage").then((m) => ({ default: m.OutlinePage })));
const LazyOverviewPage = lazy(() => import("@/features/creation/overview/OverviewPage").then((m) => ({ default: m.OverviewPage })));
const LazyProofPanel = lazy(() => import("@/features/creation/proof/ProofPanel").then((m) => ({ default: m.ProofPanel })));
const LazyPreviewPage = lazy(() => import("@/features/creation/preview/PreviewPage").then((m) => ({ default: m.PreviewPage })));
const LazyStatsPage = lazy(() => import("@/features/creation/stats/StatsPage").then((m) => ({ default: m.StatsPage })));
// 编辑器栈（prosemirror / tiptap）占项目屏闭包约 68%，但默认视图是 overview，
// 写界面并非首屏必需。改为 lazy 拆出独立 chunk，并在浏览器空闲时预加载，
// 消除「继续写作」的感知延迟。WritingDesk 是 named export，需适配 default 包装。
const LazyWritingDesk = lazy(() =>
  import("@/features/creation/editor/WritingDesk").then((m) => ({ default: m.WritingDesk }))
);
const preloadWritingDesk = () => import("@/features/creation/editor/WritingDesk");
const LazyReplacePanel = lazy(() => import("@/features/creation/replace/ReplacePanel").then((m) => ({ default: m.ReplacePanel })));
import { ProjectHomePage } from "@/features/creation/home/ProjectHomePage";
import { useCreationActions } from "@/hooks/useCreationActions";
import { useOperation } from "@/hooks/useOperation";
import { OperationProgressDialog } from "@/features/creation/operation/OperationProgressDialog";
import { useAppStore } from "@/stores/app-store";
import { useCreationStore } from "@/stores/creation-store";
import { useSearchStore } from "@/stores/search-store";
import { useUIStore } from "@/stores/ui-store";
import { PROJECT_NAV_ITEMS, type ProjectView } from "@/features/navigation/project-nav";

const PROJECT_NAV_ICONS: Record<ProjectView, typeof Layers> = {
  overview: LayoutDashboard,
  writing: PenLine,
  outline: ListTree,
  preview: BookOpen,
  cards: Layers,
  stats: BookMarked,
  history: ArchiveRestore
};

function viewDescription(view: ProjectView): string {
  switch (view) {
    case "overview":
      return "项目概览：写作目标、最近编辑与待处理事项。";
    case "writing":
      return "在场景中连续写作；卷章结构在大纲中管理，中文输入、撤销重做、粘贴清洗和自动保存都在本地完成。";
    case "outline":
      return "大纲树与场景任务卡板共享同一数据；任务卡记录视角、时间、地点、出场、目标、冲突、结果与情绪。";
    case "preview":
      return "按卷、章、场景通读全书并可打印或导出打印版 PDF；本页只读，正文改动请回到写作台。";
    case "cards":
      return "管理角色、地点、组织等创作卡片与它们之间的关系；背景设定作为卡片页的二级入口。";
    case "stats":
      return "项目字数、写作时长、连续写作与修订进度；会话只在输入时计时，不记录具体按键内容。";
    case "history":
      return "误删的内容可在这里恢复，或从命名快照回到某个版本；永久删除前请确认。";
  }
}

export function CreationProjectsPage() {
  const projects = useCreationStore((state) => state.projects);
  const selectedId = useCreationStore((state) => state.selectedId);
  const navigations = useCreationStore((state) => state.navigations);
  const projectNavigationRequests = useCreationStore((state) => state.projectNavigationRequests);
  const loading = useCreationStore((state) => state.loading);
  const setSelectedId = useCreationStore((state) => state.setSelectedId);
  const selectScene = useCreationStore((state) => state.selectScene);
  const selectCard = useCreationStore((state) => state.selectCard);
  const { loadProjects, loadNavigation, loadOutline, loadScene, loadCards, loadMigrationStatus, createProject, saveSceneBody } = useCreationActions();
  const operation = useOperation();
  const showToast = useUIStore((state) => state.showToast);
  const [wizardOpen, setWizardOpen] = useState(false);
  const [replaceOpen, setReplaceOpen] = useState(false);
  const [proofOpen, setProofOpen] = useState(false);
  const [paletteOpen, setPaletteOpen] = useState(false);
  const [migrationOpen, setMigrationOpen] = useState(false);
  const [migrationNotice, setMigrationNotice] = useState(false);
  const [importOpen, setImportOpen] = useState(false);
  const [exportOpen, setExportOpen] = useState(false);
  const [view, setView] = useState<ProjectView>("writing");
  /** 导入成功后递增，通知项目首页重新读取 project.home。 */
  const [homeRefreshKey, setHomeRefreshKey] = useState(0);
  const [creatingDemo, setCreatingDemo] = useState(false);

  /** 演示项目：一键载入预置正文/任务卡/卡片/伏笔的迷你项目（新用户引导）。 */
  const handleCreateDemoProject = useCallback(async (): Promise<string | undefined> => {
    if (creatingDemo) return undefined;
    setCreatingDemo(true);
    try {
      const projectId = await createDemoProject({ createProject, saveSceneBody });
      if (projectId) {
        await loadProjects();
        setHomeRefreshKey((key) => key + 1);
        showToast({
          tone: "success",
          title: "演示项目已载入",
          body: "已预置场景正文、任务卡、卡片与伏笔，可在写作台查看场景雷达、在大纲页编辑任务卡。"
        });
      }
      return projectId;
    } finally {
      setCreatingDemo(false);
    }
  }, [createProject, creatingDemo, loadProjects, saveSceneBody, showToast]);

  useEffect(() => {
    void loadProjects();
  }, [loadProjects]);

  useEffect(() => {
    void loadMigrationStatus().then((status) => {
      setMigrationNotice(status !== null && !status.activated && status.canProceed);
    });
  }, [loadMigrationStatus]);

  // 编辑器栈是项目屏最重的部分，默认视图却是 overview，写界面非首屏必需。
  // 组件挂载（即用户已进入创作屏）后，借浏览器空闲窗口预加载编辑器 chunk，
  // 缩短后续「继续写作」的感知延迟。带 cancelIdleCallback 清理与降级回退。
  useEffect(() => {
    let idleHandle: number | undefined;
    let timer: ReturnType<typeof setTimeout> | undefined;
    const warmUp = () => {
      void preloadWritingDesk();
    };
    const ric = typeof window !== "undefined" ? window.requestIdleCallback : undefined;
    if (typeof ric === "function") {
      idleHandle = ric(warmUp, { timeout: 2000 });
    } else {
      timer = setTimeout(warmUp, 1200);
    }
    return () => {
      if (idleHandle !== undefined && typeof window.cancelIdleCallback === "function") {
        window.cancelIdleCallback(idleHandle);
      }
      if (timer !== undefined) clearTimeout(timer);
    };
  }, []);

  useEffect(() => {
    const handleKeydown = (event: KeyboardEvent) => {
      // IME 组合输入期间（中文拼音上屏等）不触发全局快捷键。
      if (event.isComposing || event.keyCode === 229) return;
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

  const openProject = (projectId: string, targetView: ProjectView = "writing") => {
    setSelectedId(projectId);
    setView(targetView);
    if (!navigations[projectId]) void loadNavigation(projectId);
  };

  const backToProjectHome = () => {
    setSelectedId(undefined);
    setView("writing");
  };

  const handleExport = async () => {
    if (!selected) return;
    setExportOpen(true);
  };

  const handleExportBundle = async () => {
    if (!selected) return;
    try {
      await operation.start({ kind: "bundle.export", projectId: selected.id });
    } catch (error) {
      showToast({ tone: "error", title: "导出项目包失败", body: error instanceof Error ? error.message : String(error) });
    }
  };

  const handleImportBundle = async () => {
    try {
      await operation.start({ kind: "bundle.import" });
    } catch (error) {
      showToast({ tone: "error", title: "导入项目包失败", body: error instanceof Error ? error.message : String(error) });
    }
  };

  // 导入完成后刷新项目列表与首页（替代旧 importBundle 的同步回调）。
  useEffect(() => {
    if (
      operation.state?.status === "completed" &&
      (operation.state.kind === "bundle.import" || operation.state.kind === "bundle.import-encrypted")
    ) {
      void loadProjects();
      setHomeRefreshKey((key) => key + 1);
    }
  }, [operation.state?.status, operation.state?.kind, loadProjects]);

  const paletteCommands = useMemo(() => {
    const commands: Array<{ id: string; label: string; group: string; keywords?: string[]; shortcut?: string; run(): void }> = [
      { id: "view.writing", label: "正文写作台", group: "视图", keywords: ["写作", "manuscript"], run: () => setView("writing") },
      { id: "view.cards", label: "设定卡管理", group: "视图", keywords: ["卡片", "设定卡", "cards"], run: () => setView("cards") },
      { id: "view.history", label: "历史与回收站", group: "视图", keywords: ["回收站", "快照", "history"], run: () => setView("history") },
      { id: "view.stats", label: "写作统计", group: "视图", keywords: ["字数", "统计", "创作目标", "stats"], run: () => setView("stats") },
      { id: "action.search", label: "搜索", group: "操作", keywords: ["查找", "search"], run: () => openProjectSearch() },
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
          openProject(project.id);
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

  /** 项目内打开搜索：调用统一全局 SearchPanel 并设置项目上下文。 */
  const openProjectSearch = useCallback(() => {
    if (!selectedId) return;
    useSearchStore.getState().openSearch({ projectId: selectedId });
  }, [selectedId]);

  /**
   * 消费来自统一搜索的导航请求。
   *
   * 关键不变量：
   * 1. 请求只有在真正完成目标定位（找到目标章节/场景/卡片）后才被消费清除。
   * 2. 只有 chapter 请求依赖 project navigation；navigation 缺失时先加载，
   *    请求保留到 navigation 加载完成。
   * 3. card 请求不依赖章节树，独立执行：activate → loadCards → 确认存在 → selectCard → 切视图 → 消费。
   *    不得因为 navigation 缺失而消费 card 请求。
   * 4. navigation 加载失败时：给出明确提示并清除请求（用户可重新搜索）；
   *    失败与清除规则在注释、代码和测试中保持一致。
   * 5. 目标章节/卡片不存在时给出明确提示，并安全清除无效请求。
   * 6. 竞态保护：异步执行期间项目切换或请求被替换时放弃，绝不选择错误卡片。
   */
  useEffect(() => {
    if (!selectedId) return;
    // 使用订阅的 projectNavigationRequests：selectedId 不变时新请求也必须触发消费
    // （例如连续两次搜索跳转都指向同一项目）。
    const pending = projectNavigationRequests[selectedId];
    if (!pending) return;
    const target = pending.target;
    // 仅处理指向当前 selectedId 的请求；其它项目的请求等切换后再消费
    if (target.projectId !== selectedId) return;

    // 场景请求：不需要 navigation，直接定位场景并切视图。
    if (target.sceneId) {
      selectScene(target.sceneId);
      void loadScene(target.sceneId);
      void loadOutline(selectedId);
      setView("writing");
      useCreationStore.getState().consumeProjectNavigation(selectedId);
      return;
    }

    // 卡片请求：不依赖章节树，独立执行；navigation 缺失/加载失败都不影响卡片导航。
    if (target.cardId) {
      void (async () => {
        // 竞态守卫：执行期间项目切换或请求被替换（新 createdAt）则放弃。
        const isStillCurrent = () => {
          const state = useCreationStore.getState();
          return state.selectedId === selectedId && state.projectNavigationRequests[selectedId]?.createdAt === pending.createdAt;
        };
        // 1. 激活目标项目
        useCreationStore.getState().activateCardProject(selectedId);
        // 2. 加载目标项目卡片
        const cards = await loadCards({ projectId: selectedId });
        // 3. 竞态检查：stale/项目切换/请求被替换时不得选择错误卡片
        if (!isStillCurrent()) return;
        // 4. 确认返回值不是 undefined（失败或过期）且目标卡片存在
        const exists = Array.isArray(cards) && cards.some((c) => c.id === target.cardId);
        if (!exists) {
          showToast({
            tone: "warning",
            title: "目标卡片不存在",
            body: "该卡片可能已被删除或移动；请重新搜索。"
          });
          // 切到 cards 视图让用户看到结果，但 selectedCardId 保持空（不伪装成功）
          setView("cards");
          useCreationStore.getState().consumeProjectNavigation(selectedId);
          return;
        }
        // 5. 选中目标卡片（loadCards 内部 activateCardProject 会清空选择，因此后置）
        selectCard(target.cardId);
        // 6. 切到 cards 视图
        setView("cards");
        // 7. 消费导航请求
        useCreationStore.getState().consumeProjectNavigation(selectedId);
      })();
      return;
    }

    // chapter 请求：依赖 project navigation；未加载时先加载，加载完本 effect 重新执行。
    const navigationForTarget = navigations[selectedId];
    if (!navigationForTarget) {
      void (async () => {
        const loaded = await loadNavigation(selectedId);
        if (!loaded) {
          // navigation 加载失败：给出明确提示，并清除无效请求（避免重复触发）；
          // 用户可重新搜索发起新请求。
          showToast({
            tone: "error",
            title: "无法加载项目结构",
            body: "项目导航树加载失败，请稍后重试。"
          });
          useCreationStore.getState().consumeProjectNavigation(selectedId);
        }
        // loaded 存在时，store 已更新 navigations[selectedId]，本 effect 会在 navigations 变化时重新执行
      })();
      return;
    }

    if (target.chapterId) {
      // 章节结果：定位到目标章节的第一个场景
      const chapter = navigationForTarget.chapters.find((c) => c.id === target.chapterId);
      if (!chapter) {
        // 目标章节不存在：明确提示，安全清除无效请求
        showToast({
          tone: "warning",
          title: "目标章节不存在",
          body: "该章节可能已被删除或移动；请重新搜索。"
        });
        useCreationStore.getState().consumeProjectNavigation(selectedId);
        return;
      }
      const firstScene = chapter.scenes[0];
      if (firstScene) {
        selectScene(firstScene.id);
        void loadScene(firstScene.id);
        void loadOutline(selectedId);
      }
      setView("writing");
      useCreationStore.getState().consumeProjectNavigation(selectedId);
      return;
    }

    // project 意图（无具体 chapter/scene/card）：只切视图
    setView(target.view);
    useCreationStore.getState().consumeProjectNavigation(selectedId);
  }, [loadCards, loadNavigation, loadOutline, loadScene, navigations, projectNavigationRequests, selectCard, selectScene, selectedId, showToast]);

  return (
    <div className={`desktop-page-scroll paper-shell creation-writing-page ${selected ? "creation-writing-page--active" : ""}`}>
      <div className={`desktop-page-stack creation-writing-stack ${selected ? "creation-writing-stack--active" : ""}`}>
        {selected ? (
          <section className="desktop-page-hero motion-panel creation-writing-hero">
            <div className="creation-writing-hero-title-group">
              <h2 className="creation-writing-hero-title">{selected.title}</h2>
              <p className="creation-writing-hero-desc">{viewDescription(view)}</p>
            </div>
            <div className="desktop-page-actions">
                <Button className="project-action project-action--primary" aria-label="搜索项目" title="搜索项目" onClick={openProjectSearch}>
                  <Search size={16} />
                  <span>搜索</span>
                </Button>
                <Button className="project-action project-action--utility" aria-label="查找替换" title="查找替换" onClick={() => setReplaceOpen(true)}>
                  <Replace size={16} />
                  <span>查找替换</span>
                </Button>
                <Button className="project-action project-action--utility" aria-label="本地校对" title="本地校对" onClick={() => setProofOpen(true)}>
                  <FileWarning size={16} />
                  <span>校对</span>
                </Button>
                <Button className="project-action project-action--utility" aria-label="导出成稿" title="导出成稿" onClick={() => void handleExport()}>
                  <Download size={16} />
                  <span>导出成稿</span>
                </Button>
                <Button className="project-action project-action--utility" aria-label="导出项目包" title="导出项目包" variant="secondary" disabled={operation.isActive} onClick={() => void handleExportBundle()}>
                  <FolderOutput size={16} />
                  <span>导出包</span>
                </Button>
              <Button className="project-action project-action--utility" aria-label="导入项目包" title="导入项目包" variant="secondary" disabled={operation.isActive} onClick={() => void handleImportBundle()}>
                <FolderInput size={16} />
                <span>导入包</span>
              </Button>
              <Button className="project-action project-action--utility" aria-label="导入旧稿" title="导入旧稿" variant="secondary" onClick={() => setImportOpen(true)}>
                <FileUp size={16} />
                <span>导入旧稿</span>
              </Button>
            </div>
          </section>
        ) : null}

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
          <div className="project-workbench">
            <nav className="project-nav" aria-label="项目导航">
              <RingButton type="button" className="project-nav-back" onClick={backToProjectHome}>
                <ChevronLeft size={14} /> 项目列表
              </RingButton>
              {PROJECT_NAV_ITEMS.map(({ view: itemView, label }) => {
                const Icon = PROJECT_NAV_ICONS[itemView];
                return (
                  <RingButton
                    key={itemView}
                    type="button"
                    className={view === itemView ? "active" : ""}
                    aria-label={label}
                    aria-current={view === itemView ? "page" : undefined}
                    onClick={() => setView(itemView)}
                  >
                    <Icon size={15} /> {label}
                  </RingButton>
                );
              })}
            </nav>
            <div className="project-workbench-main">
              {view === "cards" ? (
                <Suspense fallback={<ScreenFallback />}>
                  <LazyCardsPage project={selected} />
                </Suspense>
              ) : view === "history" ? (
                <Suspense fallback={<ScreenFallback />}>
                  <LazyHistoryPage project={selected} />
                </Suspense>
              ) : view === "stats" ? (
                <Suspense fallback={<ScreenFallback />}>
                  <LazyStatsPage projectId={selected.id} />
                </Suspense>
              ) : view === "outline" ? (
                <Suspense fallback={<ScreenFallback />}>
                  <LazyOutlinePage project={selected} />
                </Suspense>
              ) : view === "preview" ? (
                <Suspense fallback={<ScreenFallback />}>
                  <LazyPreviewPage projectId={selected.id} />
                </Suspense>
              ) : view === "overview" ? (
                <Suspense fallback={<ScreenFallback />}>
                  <LazyOverviewPage
                    projectId={selected.id}
                    onContinueWriting={() => setView("writing")}
                    onOpenInbox={() => useAppStore.getState().setScreen("inbox")}
                  />
                </Suspense>
              ) : navigation ? (
                <Suspense fallback={<ScreenFallback />}>
                  <LazyWritingDesk
                    projects={projects}
                    project={selected}
                    navigation={navigation}
                    onSelectProject={setSelectedId}
                    onOpenOutline={() => setView("outline")}
                  />
                </Suspense>
              ) : (
                <section className="creation-writing-loading" role="status">
                  <BookMarked size={24} />
                  <span>正在打开项目写作台…</span>
                </section>
              )}
            </div>
          </div>
        ) : loading ? (
          <section className="creation-writing-loading" role="status">
            <BookMarked size={24} />
            <span>正在打开项目写作台…</span>
          </section>
        ) : (
          <ProjectHomePage
            onOpenProject={(projectId) => openProject(projectId, "writing")}
            onContinueWriting={(projectId) => openProject(projectId, "writing")}
            onOpenInbox={() => useAppStore.getState().setScreen("inbox")}
            onCreateProject={() => setWizardOpen(true)}
            onImportBundle={() => void handleImportBundle()}
            onImportDraft={() => setImportOpen(true)}
            onCreateDemoProject={handleCreateDemoProject}
            refreshKey={homeRefreshKey}
          />
        )}
      </div>

      <Suspense fallback={<ScreenFallback />}>
        <LazyCreateProjectWizard open={wizardOpen} onClose={() => setWizardOpen(false)} />
      </Suspense>
      {replaceOpen && selected && (
        <Suspense fallback={<ScreenFallback />}>
          <LazyReplacePanel
            projectId={selected.id}
            chapterId={selectedChapter?.id}
            sceneId={selectedScene?.id}
            onClose={() => setReplaceOpen(false)}
          />
        </Suspense>
      )}
      {proofOpen && selected && (
        <Suspense fallback={<ScreenFallback />}>
          <LazyProofPanel
            projectId={selected.id}
            onClose={() => setProofOpen(false)}
          />
        </Suspense>
      )}
      {paletteOpen && (
        <Suspense fallback={<ScreenFallback />}>
          <LazyCommandPalette
            commands={paletteCommands}
            onClose={() => setPaletteOpen(false)}
          />
        </Suspense>
      )}
      {migrationOpen && (
        <Suspense fallback={<ScreenFallback />}>
          <LazyMigrationDialog
            onClose={() => setMigrationOpen(false)}
            onMigrated={() => {
              setMigrationNotice(false);
              void loadMigrationStatus();
            }}
          />
        </Suspense>
      )}
      {importOpen && (
        <Suspense fallback={<ScreenFallback />}>
          <LazyImportDraftDialog
            onClose={() => setImportOpen(false)}
            onImported={() => {
              void loadProjects();
              setHomeRefreshKey((key) => key + 1);
            }}
          />
        </Suspense>
      )}
      {exportOpen && selected && (
        <Suspense fallback={<ScreenFallback />}>
          <LazyExportDraftDialog
            projectId={selected.id}
            projectTitle={selected.title}
            onClose={() => setExportOpen(false)}
          />
        </Suspense>
      )}
      {operation.state && (
        <OperationProgressDialog
          state={operation.state}
          isCommitting={operation.isCommitting}
          onCancel={operation.cancel}
          onClose={operation.reset}
          onReset={operation.retry}
        />
      )}
    </div>
  );
}
