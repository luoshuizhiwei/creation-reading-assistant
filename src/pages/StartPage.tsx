import { BarChart3, BookOpen, ChevronRight, Lightbulb, Search, Settings, Sparkles } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { useSearchActions } from "@/hooks/useSearchActions";
import { useSearchStore } from "@/stores/search-store";
import { useAppStore } from "@/stores/app-store";
import type { AppScreen } from "@/stores/app-store";

interface ModuleCard {
  screen: AppScreen;
  eyebrow: string;
  title: string;
  body: string;
  foot: string;
  icon: typeof Lightbulb;
  className: string;
}

const modules: ModuleCard[] = [
  {
    screen: "inspiration",
    eyebrow: "素材整理",
    title: "灵感中心",
    body: "剧情火花、阅读摘录、角色设定和 AI 候选版本集中在这里。桌面端适合长时间整理、筛选和比较。",
    foot: "适合：整理素材 / AI 打磨 / 来源追踪",
    icon: Lightbulb,
    className: "desktop-module-inspiration"
  },
  {
    screen: "library",
    eyebrow: "输入与阅读",
    title: "本地书库",
    body: "导入 TXT、Markdown、EPUB，使用桌面大屏进行深度阅读，右侧保留目录、设置和阅读状态。",
    foot: "适合：阅读取材 / 进度记录 / 书库管理",
    icon: BookOpen,
    className: "desktop-module-library"
  }
];

const workflow = [
  ["01", "读", "从本地书库打开小说，桌面端专注长时间阅读。"],
  ["02", "摘", "选中文字或当前位置，保存为结构化来源灵感。"],
  ["03", "炼", "回到灵感中心，用 AI 候选版本做润色和扩写。"]
];

const shortcuts: Array<{ screen: AppScreen; label: string; icon: typeof BarChart3 }> = [
  { screen: "stats", label: "阅读统计", icon: BarChart3 },
  { screen: "settings", label: "设置中心", icon: Settings }
];

export function StartPage() {
  const setScreen = useAppStore((state) => state.setScreen);
  const [homeQuery, setHomeQuery] = useState("");
  const results = useSearchStore((state) => state.results);
  const loading = useSearchStore((state) => state.loading);
  const { runSearch, openResult } = useSearchActions();
  const visibleResults = useMemo(() => results.slice(0, 5), [results]);

  useEffect(() => {
    runSearch(homeQuery);
  }, [homeQuery, runSearch]);

  return (
    <main className="desktop-start-page">
      <section className="desktop-start-hero motion-panel relative overflow-hidden rounded-[2rem]">
        <div>
          <div className="desktop-eyebrow-pill">
            <Sparkles size={14} />
            桌面端不是手机端放大版
          </div>
          <h2 className="paper-title">把阅读、摘录和灵感整理放在同一张桌面上。</h2>
          <p>
            手机端负责随手记录和移动阅读；桌面端负责沉淀灵感、阅读本地小说取材、来源回溯和 AI 候选版本比较。
          </p>
        </div>
        <div className="desktop-hero-stack" aria-label="桌面端结构">
          <span>左侧导航</span>
          <span>中间工作区</span>
          <span>右侧上下文</span>
        </div>
      </section>

      <section className="desktop-start-layout">
        <div className="desktop-start-main">
          <div className="desktop-module-grid">
            {modules.map((module) => {
              const Icon = module.icon;
              return (
                <button
                  key={module.screen}
                  className={`desktop-module-card hover:-translate-y-1 focus:ring-2 ${module.className}`}
                  onClick={() => setScreen(module.screen)}
                >
                  <div className="desktop-module-icon">
                    <Icon size={23} />
                  </div>
                  <div className="desktop-module-copy">
                    <p>{module.eyebrow}</p>
                    <h3 className="paper-title">{module.title}</h3>
                    <span>{module.body}</span>
                  </div>
                  <div className="desktop-module-footer">
                    <small>{module.foot}</small>
                    <ChevronRight size={18} />
                  </div>
                </button>
              );
            })}
          </div>

          <section className="desktop-search-board motion-panel">
            <div className="desktop-board-heading">
              <div>
                <p className="paper-label">首页搜索</p>
                <h3 className="paper-title">搜索工作区</h3>
              </div>
              <Search size={18} />
            </div>
            <div className="desktop-search-input-row">
              <Search size={16} />
              <input value={homeQuery} onChange={(event) => setHomeQuery(event.target.value)} placeholder="搜索书名、作者、灵感、阅读来源" />
            </div>
            {homeQuery.trim() && (
              <div className="desktop-search-results">
                {loading ? (
                  <div className="desktop-search-empty">正在搜索...</div>
                ) : visibleResults.length === 0 ? (
                  <div className="desktop-search-empty">没有找到匹配内容。</div>
                ) : (
                  visibleResults.map((result) => (
                    <button key={result.id} onClick={() => void openResult(result)}>
                      <strong>{result.title}</strong>
                      <span>{result.snippet}</span>
                    </button>
                  ))
                )}
              </div>
            )}
          </section>
        </div>

        <aside className="desktop-start-rail">
          <section className="desktop-workflow-card motion-panel">
            <div className="desktop-board-heading">
              <div>
                <p className="paper-label">Desk flow</p>
                <h3 className="paper-title">桌面端工作流</h3>
              </div>
            </div>
            <div className="desktop-workflow-list">
              {workflow.map(([index, title, body]) => (
                <article key={index}>
                  <span>{index}</span>
                  <div>
                    <strong>{title}</strong>
                    <p>{body}</p>
                  </div>
                </article>
              ))}
            </div>
          </section>

          <section className="desktop-shortcut-grid">
            {shortcuts.map((shortcut) => {
              const Icon = shortcut.icon;
              return (
                <button key={shortcut.screen} onClick={() => setScreen(shortcut.screen)}>
                  <Icon size={18} />
                  <span>{shortcut.label}</span>
                  <ChevronRight size={16} />
                </button>
              );
            })}
          </section>
        </aside>
      </section>
    </main>
  );
}
