import type { ReactNode } from "react";
import { BarChart3, BookMarked, BookOpen, Clock3, Home, Lightbulb, PanelRight, PenLine, Search, Settings, Sparkles } from "lucide-react";
import { useAppStore, type AppScreen } from "@/stores/app-store";
import { useCreationStore } from "@/stores/creation-store";
import { useInspirationStore } from "@/stores/inspiration-store";
import { useLibraryStore } from "@/stores/library-store";
import { useSearchStore } from "@/stores/search-store";

const navItems: Array<{ screen: AppScreen; label: string; hint: string; icon: typeof Home }> = [
  { screen: "projects", label: "项目", hint: "写作", icon: BookMarked },
  { screen: "inbox", label: "收件箱", hint: "待处理", icon: Lightbulb },
  { screen: "library", label: "资料阅读", hint: "书库", icon: BookOpen },
  { screen: "stats", label: "阅读统计", hint: "节律", icon: BarChart3 },
  { screen: "settings", label: "设置", hint: "偏好", icon: Settings }
];

const screenTitles: Record<AppScreen, { eyebrow: string; title: string; body: string }> = {
  start: {
    eyebrow: "Desk overview",
    title: "创作阅读助手",
    body: "启动页（兼容入口）。"
  },
  projects: {
    eyebrow: "Creation desk",
    title: "创作项目",
    body: "管理作品项目；项目内包含概览、写作、大纲、卡片、背景设定、统计与版本历史。"
  },
  inbox: {
    eyebrow: "Inbox",
    title: "收件箱",
    body: "旧灵感迁移与手动收集的内容；可转为创作项目的资料卡。"
  },
  inspiration: {
    eyebrow: "Inspiration desk",
    title: "灵感中心",
    body: "旧数据兼容入口：阅读摘录、灵感花和 AI 候选版本。"
  },
  library: {
    eyebrow: "Local library",
    title: "资料阅读",
    body: "导入、筛选和打开本地 TXT / Markdown / EPUB，阅读时摘录到项目。"
  },
  reader: {
    eyebrow: "Reading desk",
    title: "资料阅读",
    body: "正文、目录、阅读设置和灵感摘录，专注阅读。"
  },
  stats: {
    eyebrow: "Reading rhythm",
    title: "阅读统计",
    body: "查看阅读时长、书籍进度和节律总结。"
  },
  settings: {
    eyebrow: "Preferences",
    title: "设置",
    body: "配置 AI、外观、阅读、同步与数据维护。"
  }
};

function formatDuration(ms = 0): string {
  const minutes = Math.floor(ms / 60_000);
  if (minutes < 60) return `${minutes} 分钟`;
  const hours = Math.floor(minutes / 60);
  const rest = minutes % 60;
  return rest ? `${hours} 小时 ${rest} 分钟` : `${hours} 小时`;
}

export function DesktopFrame({ children }: { children: ReactNode }) {
  const screen = useAppStore((state) => state.screen);
  const setScreen = useAppStore((state) => state.setScreen);
  const setSearchOpen = useSearchStore((state) => state.setOpen);
  const inspirations = useInspirationStore((state) => state.items);
  const projects = useCreationStore((state) => state.projects);
  const creationLeaveGuard = useCreationStore((state) => state.leaveGuard);
  const books = useLibraryStore((state) => state.books);
  const progress = useLibraryStore((state) => state.progress);
  const progressItems = Object.values(progress);
  const totalReadingMs = progressItems.reduce((sum, item) => sum + (item?.totalReadingTimeMs ?? 0), 0);
  const readingCount = progressItems.filter((item) => (item?.progressPercent ?? 0) > 0 && (item?.progressPercent ?? 0) < 100).length;
  const title = screenTitles[screen];
  const navigate = async (target: AppScreen) => {
    if (target === screen) return;
    if (screen === "projects" && creationLeaveGuard && !(await creationLeaveGuard())) return;
    setScreen(target);
  };

  return (
    <div className="desktop-workbench paper-shell">
      <aside className="desktop-sidebar" aria-label="桌面端主导航">
        <button className="desktop-brand" onClick={() => void navigate("projects")}>
          <span className="desktop-brand-mark">阅</span>
          <span>
            <strong>创作阅读助手</strong>
            <small>Desktop studio</small>
          </span>
        </button>

        <nav className="desktop-nav">
          {navItems.map((item) => {
            const Icon = item.icon;
            const active = screen === item.screen;
            return (
              <button key={item.screen} className={active ? "active" : ""} onClick={() => void navigate(item.screen)}>
                <Icon size={18} />
                <span>
                  <strong>{item.label}</strong>
                  <small>{item.hint}</small>
                </span>
              </button>
            );
          })}
        </nav>

        <section className="desktop-sidebar-card">
          <div className="desktop-card-label">本地工作状态</div>
          <div className="desktop-sidebar-stats">
            <span>
              <strong>{books.length}</strong>
              <small>书籍</small>
            </span>
            <span>
              <strong>{inspirations.length}</strong>
              <small>灵感</small>
            </span>
            <span>
              <strong>{projects.length}</strong>
              <small>项目</small>
            </span>
          </div>
        </section>
      </aside>

      <section className="desktop-stage">
        <header className="desktop-commandbar">
          <div className="min-w-0">
            <p>{title.eyebrow}</p>
            <h1>{title.title}</h1>
            <span>{title.body}</span>
          </div>
          <button className="desktop-search-command" onClick={() => setSearchOpen(true)}>
            <Search size={17} />
            <span>搜索灵感、书库、来源</span>
            <kbd>Ctrl K</kbd>
          </button>
        </header>
        <div className="desktop-canvas">{children}</div>
      </section>

      <aside className="desktop-context-panel" aria-label="桌面端上下文信息">
        <div className="desktop-context-title">
          <PanelRight size={17} />
          <span>上下文</span>
        </div>

        <section className="desktop-context-card">
          <div className="desktop-card-label">阅读节奏</div>
          <strong>{formatDuration(totalReadingMs)}</strong>
          <p>{readingCount ? `${readingCount} 本书正在推进。` : "还没有形成稳定阅读记录。"}</p>
        </section>

        <section className="desktop-context-card">
          <div className="desktop-card-label">灵感沉淀</div>
          <strong>{inspirations.length} 条</strong>
          <p>桌面端适合筛选、编辑和比较 AI 候选版本。</p>
        </section>

        <section className="desktop-context-card desktop-context-actions">
          <button onClick={() => void navigate("projects")}>
            <PenLine size={16} />
            打开创作项目
          </button>
          <button onClick={() => void navigate("inspiration")}>
            <Sparkles size={16} />
            打开灵感中心
          </button>
          <button onClick={() => void navigate("library")}>
            <BookOpen size={16} />
            打开本地书库
          </button>
          <button onClick={() => void navigate("stats")}>
            <Clock3 size={16} />
            查看阅读统计
          </button>
        </section>
      </aside>
    </div>
  );
}
