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
  icon: typeof Lightbulb;
  accent: string;
}

const modules: ModuleCard[] = [
  {
    screen: "inspiration",
    eyebrow: "捕捉与打磨",
    title: "灵感中心",
    body: "剧情火花、角色设定、台词碎片和阅读摘录都先放这里；AI 润色、扩写和平台风格化也在这里完成。",
    icon: Lightbulb,
    accent: "from-amber-200/70 via-copper/15 to-transparent"
  },
  {
    screen: "library",
    eyebrow: "输入与计时",
    title: "本地书库",
    body: "导入 TXT、Markdown、EPUB 小说，继续阅读，记录有效阅读时间，并把片段沉淀为灵感。",
    icon: BookOpen,
    accent: "from-sky-200/55 via-moss/10 to-transparent"
  }
];

const shortcuts: Array<{ screen: AppScreen; label: string; body: string; icon: typeof BarChart3 }> = [
  { screen: "stats", label: "阅读统计", body: "看最近的阅读时长、完成度和节奏。", icon: BarChart3 },
  { screen: "settings", label: "设置", body: "配置 AI、阅读偏好、书库路径、备份与日志。", icon: Settings }
];

export function StartPage() {
  const setScreen = useAppStore((state) => state.setScreen);
  const [homeQuery, setHomeQuery] = useState("");
  const results = useSearchStore((state) => state.results);
  const loading = useSearchStore((state) => state.loading);
  const { runSearch, openResult } = useSearchActions();
  const visibleResults = useMemo(() => results.slice(0, 6), [results]);

  useEffect(() => {
    runSearch(homeQuery);
  }, [homeQuery, runSearch]);

  return (
    <main className="h-full overflow-auto paper-shell">
      <div className="mx-auto flex min-h-full w-[min(1180px,calc(100vw-40px))] flex-col justify-center gap-7 py-10">
        <section className="motion-panel relative overflow-hidden rounded-[2rem] border border-paper-line/80 bg-paper-panel/95 p-8 shadow-paper">
          <div className="pointer-events-none absolute -right-20 -top-28 h-72 w-72 rounded-full bg-copper/12 blur-3xl" />
          <div className="pointer-events-none absolute bottom-0 left-1/2 h-40 w-80 -translate-x-1/2 rounded-full bg-moss/10 blur-3xl" />
          <div className="relative grid gap-8 lg:grid-cols-[0.92fr_1.08fr] lg:items-end">
            <div>
              <div className="inline-flex items-center gap-2 rounded-full border border-copper/20 bg-copper/10 px-3 py-1 text-xs font-medium text-copper">
                <Sparkles size={14} />
                本地优先 · 灵感驱动 · 阅读取材
              </div>
              <h1 className="paper-title mt-5 max-w-2xl text-4xl font-semibold tracking-tight text-paper-ink md:text-5xl">创作阅读助手</h1>
              <p className="mt-4 max-w-xl text-base leading-8 text-paper-muted">
                正文去番茄、起点、刺猬猫等平台发布；这个应用专注两件事：沉淀灵感，阅读本地小说取材。AI 打磨放在灵感中心里使用。
              </p>
            </div>

            <div className="grid gap-3 rounded-[1.5rem] border border-paper-line/70 bg-white/55 p-4 backdrop-blur">
              <div className="text-sm font-semibold text-paper-ink">今天可以从这里开始</div>
              <div className="grid gap-2 text-sm text-paper-muted">
                <div className="rounded-xl bg-paper-soft/65 px-4 py-3">1. 先把脑子里的梗丢进灵感中心。</div>
                <div className="rounded-xl bg-paper-soft/65 px-4 py-3">2. 阅读本地小说时，把触动你的片段记回灵感。</div>
                <div className="rounded-xl bg-paper-soft/65 px-4 py-3">3. 在灵感中心里用 AI 生成候选版本，不覆盖原文。</div>
              </div>
            </div>
          </div>
        </section>

        <section className="grid gap-4 lg:grid-cols-2">
          {modules.map((module) => {
            const Icon = module.icon;
            return (
              <button
                key={module.screen}
                className="motion-card group relative min-h-[220px] overflow-hidden rounded-[1.65rem] border border-paper-line/80 bg-paper-panel p-6 text-left shadow-paper transition duration-200 ease-out hover:-translate-y-1 hover:border-copper/35 hover:shadow-lift focus:outline-none focus:ring-2 focus:ring-copper/30"
                onClick={() => setScreen(module.screen)}
              >
                <div className={`absolute inset-0 bg-gradient-to-br ${module.accent} opacity-85 transition group-hover:opacity-100`} />
                <div className="relative flex h-full flex-col">
                  <div className="flex items-center justify-between gap-4">
                    <div className="rounded-2xl border border-white/80 bg-white/70 p-3 text-copper shadow-sm">
                      <Icon size={23} />
                    </div>
                    <ChevronRight className="text-paper-muted transition group-hover:translate-x-1 group-hover:text-copper" size={20} />
                  </div>
                  <div className="mt-7 text-xs font-semibold uppercase tracking-[0.2em] text-copper/80">{module.eyebrow}</div>
                  <h2 className="paper-title mt-2 text-2xl font-semibold text-paper-ink">{module.title}</h2>
                  <p className="mt-3 text-sm leading-7 text-paper-muted">{module.body}</p>
                </div>
              </button>
            );
          })}
        </section>

        <section className="motion-panel rounded-[1.5rem] border border-paper-line/80 bg-paper-panel/90 p-5 shadow-paper">
          <div className="mb-3 flex items-center gap-2 text-sm font-semibold text-paper-ink">
            <Search size={17} />
            首页搜索
          </div>
          <div className="relative">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 text-paper-muted" size={16} />
            <input
              className="paper-input h-11 w-full pl-9"
              value={homeQuery}
              onChange={(event) => setHomeQuery(event.target.value)}
              placeholder="搜索书名、作者、灵感、阅读来源"
            />
          </div>
          {homeQuery.trim() && (
            <div className="mt-3 grid gap-2">
              {loading ? (
                <div className="rounded-xl border border-paper-line bg-paper-soft/45 px-4 py-3 text-sm text-paper-muted">正在搜索...</div>
              ) : visibleResults.length === 0 ? (
                <div className="rounded-xl border border-dashed border-paper-line px-4 py-3 text-sm text-paper-muted">没有找到匹配内容。</div>
              ) : (
                visibleResults.map((result) => (
                  <button
                    key={result.id}
                    className="rounded-xl border border-paper-line bg-paper-soft/45 px-4 py-3 text-left transition duration-150 hover:-translate-y-0.5 hover:border-copper/40 hover:bg-paper-panel hover:shadow-lift"
                    onClick={() => void openResult(result)}
                  >
                    <div className="truncate text-sm font-semibold text-paper-ink">{result.title}</div>
                    <div className="mt-1 line-clamp-2 text-xs leading-5 text-paper-muted">{result.snippet}</div>
                  </button>
                ))
              )}
            </div>
          )}
        </section>

        <section className="grid gap-3 md:grid-cols-2">
          {shortcuts.map((shortcut) => {
            const Icon = shortcut.icon;
            return (
              <button
                key={shortcut.screen}
                className="motion-card group flex items-center gap-4 rounded-2xl border border-paper-line/80 bg-paper-panel/85 p-4 text-left shadow-sm transition duration-200 hover:-translate-y-0.5 hover:border-copper/30 hover:bg-white/80 focus:outline-none focus:ring-2 focus:ring-copper/25"
                onClick={() => setScreen(shortcut.screen)}
              >
                <div className="rounded-xl bg-paper-soft p-3 text-copper">
                  <Icon size={20} />
                </div>
                <div className="min-w-0 flex-1">
                  <div className="font-semibold text-paper-ink">{shortcut.label}</div>
                  <div className="mt-1 text-sm text-paper-muted">{shortcut.body}</div>
                </div>
                <ChevronRight className="text-paper-muted transition group-hover:translate-x-1 group-hover:text-copper" size={18} />
              </button>
            );
          })}
        </section>
      </div>
    </main>
  );
}

