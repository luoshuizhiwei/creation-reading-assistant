import { useEffect, useState, type ReactNode } from "react";
import { BookMarked, BookOpen, Copy, Inbox as InboxIcon, Layers3, Minus, Search, Settings, Square, X } from "lucide-react";
import { RingButton } from "@/components/interaction";
import { useAppStore, type AppScreen } from "@/stores/app-store";
import { useCreationStore } from "@/stores/creation-store";
import { useLibraryStore } from "@/stores/library-store";
import { useSearchStore } from "@/stores/search-store";
import { useCreationActions } from "@/hooks/useCreationActions";
import { APP_NAV_ITEMS } from "@/features/navigation/app-nav";
import { closeWindow, minimizeWindow, onMaximizedChange, toggleMaximize } from "@/services/window-service";

function WindowControls() {
  const [maximized, setMaximized] = useState(false);
  useEffect(() => {
    const dispose = onMaximizedChange(setMaximized);
    return () => dispose();
  }, []);
  const controls = [
    {
      key: "minimize",
      label: "最小化窗口",
      Icon: Minus,
      onClick: minimizeWindow
    },
    {
      key: "maximize",
      label: maximized ? "还原窗口" : "最大化窗口",
      Icon: maximized ? Copy : Square,
      onClick: toggleMaximize
    },
    {
      key: "close",
      label: "关闭窗口",
      Icon: X,
      onClick: closeWindow
    }
  ];
  return (
    <div className="desktop-window-controls" role="group" aria-label="窗口控制">
      {controls.map(({ key, label, Icon, onClick }) => (
        <button key={key} type="button" className={`desktop-window-control ${key}`} aria-label={label} title={label} onClick={onClick}>
          <Icon size={key === "minimize" ? 16 : key === "close" ? 15 : 13} strokeWidth={key === "close" ? 2.2 : 2} />
        </button>
      ))}
    </div>
  );
}


const navIcons: Record<string, typeof Settings> = {
  projects: BookMarked,
  "card-library": Layers3,
  inbox: InboxIcon,
  library: BookOpen,
  "global-search": Search,
  settings: Settings
};

const screenTitles: Record<AppScreen, { eyebrow: string; title: string; body: string }> = {
  projects: {
    eyebrow: "Creation desk",
    title: "创作项目",
    body: "管理作品项目；项目内包含概览、写作、大纲、卡片、背景设定、统计与版本历史。"
  },
  "card-library": {
    eyebrow: "World bible",
    title: "全局卡片库",
    body: "跨作品复用角色、地点、组织和世界观设定；查看它们正在服务的项目。"
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
    title: "书库",
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

export function DesktopFrame({ children }: { children: ReactNode }) {
  const screen = useAppStore((state) => state.screen);
  const setScreen = useAppStore((state) => state.setScreen);
  const creationFocusMode = useAppStore((state) => state.creationFocusMode);
  const setSearchOpen = useSearchStore((state) => state.setOpen);
  const projects = useCreationStore((state) => state.projects);
  const books = useLibraryStore((state) => state.books);
  const [pendingInboxCount, setPendingInboxCount] = useState<number | undefined>();
  const { loadInboxCount } = useCreationActions();

  useEffect(() => {
    void loadInboxCount().then((res) => setPendingInboxCount(res.pending)).catch(() => {});
  }, [loadInboxCount, screen]);

  const workbenchActive = useCreationStore((state) => state.selectedId != null);
  const creationLeaveGuard = useCreationStore((state) => state.leaveGuard);
  const title = screenTitles[screen];
  const navigate = async (target: AppScreen) => {
    if (target === screen) return;
    if (screen === "projects" && creationLeaveGuard && !(await creationLeaveGuard())) return;
    setScreen(target);
  };

  return (
    <div className={`desktop-root paper-shell${creationFocusMode && screen === "projects" ? " desktop-root--focus" : ""}`}>
      <header className="desktop-titlebar" aria-label="窗口标题栏" aria-hidden={creationFocusMode && screen === "projects"}>
        <div className="desktop-titlebar-title">
          <span className="desktop-titlebar-mark">阅</span>
          <span>创作阅读助手</span>
        </div>
        <WindowControls />
      </header>
      <div className="desktop-workbench">
      <aside
        className={`desktop-sidebar ${screen === "projects" && workbenchActive ? "desktop-sidebar--rail" : ""}`}
        aria-label="桌面端主导航"
        aria-hidden={creationFocusMode && screen === "projects"}
      >
        <RingButton
          className="desktop-brand nav-spine-item"
          aria-label="创作阅读助手：回到项目首页"
          aria-current={screen === "projects" ? "page" : undefined}
          onClick={() => void navigate("projects")}
        >
          <span className="desktop-brand-mark">阅</span>
          <span>
            <strong>创作阅读助手</strong>
            <small>编辑出版工作室</small>
          </span>
        </RingButton>

        <nav className="desktop-nav" aria-label="一级导航">
          {APP_NAV_ITEMS.map((item) => {
            const Icon = navIcons[item.kind === "screen" ? item.screen : item.action] ?? Settings;
            if (item.kind === "action") {
              return (
                <RingButton key={item.action} className="nav-spine-item" type="button" aria-label={item.label} onClick={() => setSearchOpen(true)}>
                  <Icon size={18} />
                  <span>
                    <strong>{item.label}</strong>
                    <small>{item.hint}</small>
                  </span>
                </RingButton>
              );
            }
            const active = screen === item.screen;
            return (
              <RingButton
                key={item.screen}
                type="button"
                className={`nav-spine-item ${active ? "active" : ""}`}
                data-active={active}
                aria-label={item.label}
                aria-current={active ? "page" : undefined}
                onClick={() => void navigate(item.screen)}
              >
                <Icon size={18} />
                <span>
                  <strong>{item.label}</strong>
                  <small>{item.hint}</small>
                </span>
              </RingButton>
            );
          })}
        </nav>

        <section className="desktop-sidebar-card" aria-label="工作区状态概览">
          <div className="desktop-card-label">工作台概览</div>
          <div className="desktop-sidebar-stats">
            <span title={`创作项目：${projects.length} 个`}>
              <strong>{projects.length}</strong>
              <small>项目</small>
            </span>
            <span title={`收件箱待处理：${pendingInboxCount ?? 0} 条`}>
              <strong>{pendingInboxCount ?? 0}</strong>
              <small>待办</small>
            </span>
            <span title={`本地书库：${books.length} 册`}>
              <strong>{books.length}</strong>
              <small>藏书</small>
            </span>
          </div>
        </section>
      </aside>

      <section className={`desktop-stage${workbenchActive && screen === "projects" ? " desktop-stage--immersive" : ""}`}>
        {(!workbenchActive || screen !== "projects") && (
          <header className="desktop-commandbar">
            <div className="min-w-0">
              <h1>{title.title}</h1>
              <span title={title.body}>{title.body}</span>
            </div>
            <RingButton className="desktop-search-command" type="button" aria-label="打开全局搜索（Ctrl K）" onClick={() => setSearchOpen(true)}>
              <Search size={17} />
              <span>搜索项目、收件箱、资料</span>
              <kbd>Ctrl K</kbd>
            </RingButton>
          </header>
        )}
        <div className="desktop-canvas">{children}</div>
      </section>
      </div>
    </div>
  );
}
