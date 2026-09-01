/**
 * 统计计算器（纯 module，可独立测试）。
 *
 * 从主进程 stats.view（ProjectStatsView）与项目目标（CreationProjectSetup）
 * 派生出展示数据：
 * - 三种字数指标切换：汉字 / 非空白字符 / 含标点字符；
 * - 目标进度：当前字数 / 目标字数 / 百分比 / 剩余；
 * - 日净增与周净增（本地日历语义：日界 = 本地 0 点，周界 = 本地周一 0 点，
 *   与主进程 stats.view 的 daily / week 口径一致，不使用 UTC 字符串截断）；
 * - 目标日期倒计时：剩余字数与所需每日速度；
 * - 修订量 / 活动时长 / 连续写作 / 章节状态 / 里程碑直接映射 stats.view。
 *
 * 主指标（项目级设置）由 SEAM 提供前，展示默认使用 nonWhitespace
 * （与 project.home 的 currentChars 同口径）；指标切换在 UI 侧是纯展示预览。
 */

import type { CreationProjectSetup, ProjectStatsView } from "@/types/creation";

export type WordMetric = "han" | "nonWhitespace" | "withPunctuation";

export const DEFAULT_WORD_METRIC: WordMetric = "nonWhitespace";

export const WORD_METRIC_LABELS: Record<WordMetric, string> = {
  han: "汉字",
  nonWhitespace: "字",
  withPunctuation: "字（含标点）"
};

/** 本地日历日期键 YYYY-MM-DD（不依赖 UTC）。 */
export function localDateKey(date: Date): string {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

/** 本地日历周一 0 点（与主进程 stats.view 的 startOfWeek 同口径）。 */
export function startOfLocalWeek(date: Date): Date {
  const monday = new Date(date.getFullYear(), date.getMonth(), date.getDate() - ((date.getDay() + 6) % 7));
  return monday;
}

export function wordsForMetric(stats: ProjectStatsView, metric: WordMetric): number {
  switch (metric) {
    case "han":
      return stats.words.han;
    case "withPunctuation":
      return stats.words.withPunctuation;
    case "nonWhitespace":
    default:
      return stats.words.nonWhitespace;
  }
}

export interface GoalProgress {
  metric: WordMetric;
  current: number;
  target: number;
  percent: number;
  remaining: number;
  reached: boolean;
}

/** 总字数目标进度；未设置总字数目标时返回 null。 */
export function computeGoalProgress(
  stats: ProjectStatsView,
  setup: CreationProjectSetup,
  metric: WordMetric = DEFAULT_WORD_METRIC
): GoalProgress | null {
  const target = setup.totalWordGoal;
  if (target === undefined || target <= 0) return null;
  const current = wordsForMetric(stats, metric);
  return {
    metric,
    current,
    target,
    remaining: Math.max(0, target - current),
    reached: current >= target,
    percent: Math.min(100, Math.round((current / target) * 100))
  };
}

/** 今日（本地日历）净增字数：stats.daily 最后一项即今日。 */
export function computeDailyNetChars(stats: ProjectStatsView, now = new Date()): number {
  const todayKey = localDateKey(now);
  const entry = stats.daily.find((item) => item.date === todayKey);
  return entry?.netChars ?? 0;
}

/** 今日（本地日历）活动时长（秒）。 */
export function computeDailyActiveSeconds(stats: ProjectStatsView, now = new Date()): number {
  const todayKey = localDateKey(now);
  const entry = stats.daily.find((item) => item.date === todayKey);
  return entry?.activeSeconds ?? 0;
}

/** 本周（本地周一 0 点起，含今天）净增字数与活动时长。 */
export function computeWeekSummary(
  stats: ProjectStatsView,
  now = new Date()
): { netChars: number; activeSeconds: number; mondayKey: string; todayKey: string } {
  const todayKey = localDateKey(now);
  const monday = startOfLocalWeek(now);
  const mondayKey = localDateKey(monday);
  let netChars = 0;
  let activeSeconds = 0;
  for (const item of stats.daily) {
    if (item.date < mondayKey || item.date > todayKey) continue;
    netChars += item.netChars;
    activeSeconds += item.activeSeconds;
  }
  return { netChars, activeSeconds, mondayKey, todayKey };
}

export interface GoalDeadline {
  remaining: number;
  daysLeft: number;
  perDayNeeded: number;
}

/** 目标日期倒计时：剩余字数、剩余天数（含今天）与每日所需速度；条件不足返回 null。 */
export function computeGoalDeadline(
  stats: ProjectStatsView,
  setup: CreationProjectSetup,
  metric: WordMetric = DEFAULT_WORD_METRIC,
  now = new Date()
): GoalDeadline | null {
  const progress = computeGoalProgress(stats, setup, metric);
  if (!progress || progress.reached || !setup.targetDate) return null;
  const target = Date.parse(setup.targetDate);
  if (Number.isNaN(target)) return null;
  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime();
  const targetDay = new Date(new Date(target).getFullYear(), new Date(target).getMonth(), new Date(target).getDate()).getTime();
  const daysLeft = Math.ceil((targetDay - today) / 86_400_000);
  if (daysLeft < 0) return { remaining: progress.remaining, daysLeft: 0, perDayNeeded: progress.remaining };
  return {
    remaining: progress.remaining,
    daysLeft,
    perDayNeeded: daysLeft > 0 ? Math.ceil(progress.remaining / daysLeft) : progress.remaining
  };
}

/** 每周更新日平均目标：每周目标字数 ÷ 每周更新日数（未设置时返回 null）。 */
export function computeWeeklyUpdateDayTarget(
  setup: CreationProjectSetup,
  _metric: WordMetric = DEFAULT_WORD_METRIC
): { weeklyGoal: number; days: number; perUpdateDay: number } | null {
  if (setup.weeklyWordGoal === undefined || setup.weeklyWordGoal <= 0) return null;
  const days = setup.weeklyUpdateDays.length;
  if (days === 0) return null;
  return {
    weeklyGoal: setup.weeklyWordGoal,
    days,
    perUpdateDay: Math.ceil(setup.weeklyWordGoal / days)
  };
}
