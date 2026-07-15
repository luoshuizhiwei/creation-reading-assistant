import { useMemo, useState, type ReactNode } from "react";
import {
  BookOpen,
  Book,
  MoreHorizontal,
  ChevronRight,
  Cloud,
  Database,
  Shield,
  Info,
  RefreshCw,
  Moon,
  Type,
  Sparkles,
  Trash2,
  FileText,
  Activity
} from "lucide-react";
import { MOBILE_APP_VERSION } from "../../services/mobile-updates";
import { buildMobileStatsSummary } from "../../services/mobile-stats";
import { formatDuration } from "../../utils/format";
import { buildStorageBreakdown, clearReaderContentCache } from "./profile-helpers";
import type { MobileAppTheme } from "../../utils/mobile-helpers";
import type { MobileSnapshot } from "../../types/mobile";
import type { useWebDavSettings } from "./hooks/useWebDavSettings";
import type { useMobileAISettings } from "./hooks/useMobileAISettings";
import type { useUpdateCheck } from "./hooks/useUpdateCheck";
import type { ProfileSubPage } from "./ProfilePage";

export function ProfileHome({
  snapshot,
  paired,
  appTheme,
  webdav,
  ai,
  update,
  onSetActivePage,
  onConfirm,
  onMessage
}: {
  snapshot: MobileSnapshot;
  paired: boolean;
  appTheme: MobileAppTheme;
  webdav: ReturnType<typeof useWebDavSettings>;
  ai: ReturnType<typeof useMobileAISettings>;
  update: ReturnType<typeof useUpdateCheck>;
  onSetActivePage: (page: ProfileSubPage | undefined) => void;
  onConfirm: (dialog: { title: string; message: string; onConfirm: () => void } | null) => void;
  onMessage: (message: string) => void;
}) {
  const [profileMoreOpen, setProfileMoreOpen] = useState(false);
  const [storageRevision, setStorageRevision] = useState(0);

  const monthStats = useMemo(() => buildMobileStatsSummary(snapshot, "month"), [snapshot]);
  const completedCount = useMemo(
    () => snapshot.progress.filter((item) => item.completionState === "completed").length,
    [snapshot]
  );
  const storage = useMemo(() => buildStorageBreakdown(snapshot), [snapshot, storageRevision]);
  const hasUpdateBadge = update.showUpdateBanner;

  const openProfileShortcut = (page: ProfileSubPage) => {
    setProfileMoreOpen(false);
    onSetActivePage(page);
  };

  const handleClearCache = () => {
    onConfirm({
      title: "清理缓存",
      message: `确认清理阅读器正文缓存？将删除 ${storage.cacheEntryCount} 条缓存条目（约 ${Math.round(storage.cacheBytes / 1024)} KB），释放后可重新生成。不会删除正式书籍、进度、书签和笔记。`,
      onConfirm: () => {
        const result = clearReaderContentCache();
        setStorageRevision((value) => value + 1);
        onMessage(`已清理 ${result.removed} 条缓存，释放约 ${Math.round(result.freedBytes / 1024)} KB。`);
      }
    });
  };

  const appThemeLabel = appTheme === "system" ? "跟随系统" : appTheme === "dark" ? "深色" : "浅色";

  const menuGroups: Array<{
    title: string;
    items: Array<{ label: string; desc?: string; icon: ReactNode; action?: () => void; badge?: boolean; danger?: boolean }>;
  }> = [
    {
      title: "阅读与外观",
      items: [
        { label: "阅读设置", desc: "字号、行距、主题、翻页模式", icon: <Type size={20} />, action: () => onSetActivePage("reader") },
        { label: "应用外观", desc: appThemeLabel, icon: <Moon size={20} />, action: () => onSetActivePage("appearance") },
        { label: "我的阅读", desc: "进度、时长、书籍状态", icon: <BookOpen size={20} />, action: () => onSetActivePage("reading") }
      ]
    },
    {
      title: "数据与存储",
      items: [
        { label: "存储管理", desc: `${snapshot.books.length} 本 · ${storage.downloadedBooks} 本已落地`, icon: <Database size={20} />, action: () => onSetActivePage("storage") },
        { label: "清理缓存", desc: `${storage.cacheEntryCount} 条缓存 · ${Math.round(storage.cacheBytes / 1024)} KB`, icon: <Trash2 size={20} />, action: handleClearCache },
        { label: "标签管理", desc: `${snapshot.tags.length} 个`, icon: <FileText size={20} />, action: () => onSetActivePage("tags") },
        { label: "分类管理", desc: `${snapshot.categories.length} 个`, icon: <BookOpen size={20} />, action: () => onSetActivePage("categories") },
        { label: "书单管理", desc: `${snapshot.shelves.length} 个`, icon: <Book size={20} />, action: () => onSetActivePage("shelves") }
      ]
    },
    {
      title: "我的书评与笔记",
      items: [
        { label: "我的书评 / 笔记", desc: `${snapshot.notes.length} 条`, icon: <FileText size={20} />, action: () => onSetActivePage("notes") }
      ]
    },
    {
      title: "同步与工具",
      items: [
        { label: "局域网同步", desc: paired ? "已连接电脑" : "从未同步", icon: <RefreshCw size={20} />, action: () => onSetActivePage("sync") },
        { label: "WebDAV 设置", desc: webdav.webdavAccount.endpoint ? "已配置" : "未配置", icon: <Cloud size={20} />, action: () => onSetActivePage("webdav") },
        { label: "AI 助手", desc: ai.aiSettings.hasApiKey ? "已配置 Key" : "未配置", icon: <Sparkles size={20} />, action: () => onSetActivePage("ai") }
      ]
    },
    {
      title: "帮助与关于",
      items: [
        { label: "日志与诊断", desc: "运行环境与问题记录", icon: <Activity size={20} />, action: () => onSetActivePage("diagnostics") },
        { label: "隐私安全", desc: "本地优先", icon: <Shield size={20} />, action: () => onSetActivePage("privacy") },
        { label: "关于", desc: hasUpdateBadge ? `新版本 v${update.updateInfo?.latestVersion} 可更新` : `版本 ${MOBILE_APP_VERSION}`, icon: <Info size={20} />, action: () => onSetActivePage("about"), badge: hasUpdateBadge }
      ]
    }
  ];

  return (
    <div className="screen-stack">
      <header className="mobile-header row-header profile-topbar">
        <div>
          <p className="mini-label">本地档案</p>
          <h1>我的</h1>
        </div>
        <button className="round-action" aria-label="更多" aria-expanded={profileMoreOpen} onClick={() => setProfileMoreOpen((value) => !value)}>
          <MoreHorizontal size={22} />
        </button>
      </header>

      {profileMoreOpen && (
        <section className="profile-more-panel" aria-label="更多快捷入口">
          <button onClick={() => openProfileShortcut("storage")}>
            <Database size={18} />
            <span>数据备份</span>
          </button>
          <button onClick={() => openProfileShortcut("privacy")}>
            <Shield size={18} />
            <span>隐私安全</span>
          </button>
          <button onClick={() => openProfileShortcut("about")}>
            <Info size={18} />
            <span>关于应用</span>
          </button>
        </section>
      )}

      <section className="profile-card compact-profile-card app-info-card">
        <div className="app-info-row">
          <div className="app-info-icon">
            <Book size={28} />
          </div>
          <div className="app-info-text">
            <h2>创作阅读助手</h2>
            <p>版本 {MOBILE_APP_VERSION} · 本地优先</p>
          </div>
        </div>
        <div className="profile-card-summary-row">
          <article>
            <strong>{formatDuration(monthStats.totalReadingMs)}</strong>
            <span>本月阅读时长</span>
          </article>
          <article>
            <strong>{completedCount}</strong>
            <span>累计读完</span>
          </article>
          <article>
            <strong>{snapshot.inspirations.length}</strong>
            <span>灵感数量</span>
          </article>
        </div>
      </section>

      <section className="profile-grid">
        <button onClick={() => onSetActivePage("sync")}>
          <strong>同步</strong>
          <span>{paired ? "local-desktop-lan 已连接" : "从未同步"}</span>
        </button>
        <button onClick={() => onSetActivePage("notes")}>
          <strong>笔记</strong>
          <span>{snapshot.notes.length} 条</span>
        </button>
      </section>

      {menuGroups.map((group) => (
        <section className="profile-menu-group" key={group.title}>
          <h2>{group.title}</h2>
          <div className="menu-list profile-menu-list">
            {group.items.map((item) => (
              <button key={item.label} onClick={item.action} className={item.danger ? "danger-menu-item" : ""}>
                <span className="profile-menu-icon">{item.icon}</span>
                <span className="profile-menu-text">
                  <strong>{item.label}</strong>
                  {item.desc && <small>{item.desc}</small>}
                </span>
                {item.badge && <span className="profile-menu-badge" aria-label="有新内容" />}
                <ChevronRight size={18} />
              </button>
            ))}
          </div>
        </section>
      ))}
    </div>
  );
}
