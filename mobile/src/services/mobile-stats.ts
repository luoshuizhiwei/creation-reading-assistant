/**
 * 移动端统计聚合服务。
 *
 * 历史代码通过此文件导入统计函数；新的统计实现位于
 * `features/stats/statistics-helpers.ts`，本文件仅做转发以保持向后兼容。
 */

export type { MobileSnapshot } from "../types/mobile";

export {
  buildBookStatusCount,
  buildDateRange,
  buildMobileStatsSummary,
  buildReadingStreak,
  buildReadingTrend,
  getDisplayableBooks,
  getPeriodSessions,
  getStatsPeriodTitle,
  getValidSessions,
  hasBookBeenRead,
  isBookCompleted,
  isBookReading,
  isCurrentStatsPeriod,
  shiftStatsPeriodAnchor,
  statsPeriodLabels
} from "../features/stats/statistics-helpers";

export type {
  BookStatusCount,
  DateRange,
  MobileStatsSummary,
  StatsPeriod,
  TrendItem
} from "../features/stats/statistics-helpers";
