import type { ReactNode } from "react";
import { BarChart3, BookOpen, Clock3, Home, Lightbulb, PanelRight, Search, Settings, Sparkles } from "lucide-react";
import { useAppStore, type AppScreen } from "@/stores/app-store";
import { useInspirationStore } from "@/stores/inspiration-store";
import { useLibraryStore } from "@/stores/library-store";
import { useSearchStore } from "@/stores/search-store";

const navItems: Array<{ screen: AppScreen; label: string; hint: string; icon: typeof Home }> = [
  { screen: "start", label: "工作台", hint: "总览", icon: Home },
  { screen: "inspiration", label: "灵感中心", hint: "素材/AI", icon: Lightbulb },
  { screen: "library", label: "本地书库", hint: "阅读", icon: BookOpen },
  { screen: "stats", label: "阅读统计", hint: "节奏", icon: BarChart3 },
  { screen: "settings", label: "设置", hint: "偏好", icon: Settings }
];

const screenTitles: Record<AppScreen, { eyebrow: string; title: string; body: string }> = {
  start: {
    eyebrow: "Desk overview",
    title: "创作阅读工作台",
    body: "桌面端用于深度整理：左侧导航，中间处理，右侧查看上下文。"
  },
  inspiration: {
    eyebrow: "Inspiration desk",
    title: "灵感中心",
    body: "把阅读摘录、剧情火花和 AI 候选版本集中管理。"
  },
  library: {
    eyebrow: "Local library",
    title: "本地书库",
    body: "导入、筛选和打开本地 TXT / Markdown / EPUB。"
  },
  reader: {
    eyebrow: "Reading desk",
    title: "深度阅读",
    body: "正文、目录和阅读设置在桌面端并排工作。"
  },
  stats: {
    eyebrow: "Reading rhythm",
    title: "阅读统计",
    body: "查看阅读时长、书籍进度和灵感沉淀。"
  },
  settings: {
    eyebrow: "Preferences",
    title: "设置",
    body: "配置 AI、主题、书库路径、同步和数据维护。"
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
  const books = useLibraryStore((state) => state.books);
  const progress = useLibraryStore((state) => state.progress);
  const progressItems = Object.values(progress);
  const totalReadingMs = progressItems.reduce((sum, item) => sum + (item?.totalReadingTimeMs ?? 0), 0);
  const readingCount = progressItems.filter((item) => (item?.progressPercent ?? 0) > 0 && (item?.progressPercent ?? 0) < 100).length;
  const title = screenTitles[screen];

  return (
    <div className="desktop-workbench paper-shell">
      <aside className="desktop-sidebar" aria-label="桌面端主导航">
        <button className="desktop-brand" onClick={() => setScreen("start")}>
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
              <button key={item.screen} className={active ? "active" : ""} onClick={() => setScreen(item.screen)}>
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
          <button onClick={() => setScreen("inspiration")}>
            <Sparkles size={16} />
            打开灵感中心
          </button>
          <button onClick={() => setScreen("library")}>
            <BookOpen size={16} />
            打开本地书库
          </button>
          <button onClick={() => setScreen("stats")}>
            <Clock3 size={16} />
            查看阅读统计
          </button>
        </section>
      </aside>
    </div>
  );
}
