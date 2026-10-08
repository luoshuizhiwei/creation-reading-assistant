import type { SettingsSection } from "@/types/settings";
import { settingEntries, SETTINGS_SECTION_LABELS } from "@/features/navigation/registry";

export interface SettingsSearchEntry {
  /** 与控件 DOM 上的 data-setting-id 一致，用于跳转定位 */
  id: string;
  section: SettingsSection;
  group: string;
  label: string;
  /** 附加检索词（别名、英文、口语说法），空格分隔 */
  keywords?: string;
}

/**
 * 设置项搜索注册表。
 * 约定：新增设置控件时必须登记一行，并给控件外层加同名 data-setting-id。
 * （__tests__/settings-search-registry.test.ts 校验 id 唯一性与检索函数；
 *  __tests__/settings-search-dom-guard.test.tsx 逐条核对 data-setting-id 真的在 DOM 上。）
 *
 * 规格 §4.1 第 3 条起，本文件不再持有数据：唯一数据源是 navigation/registry.ts 的
 * NAV_REGISTRY（一条 {id, group, type, label, hint, keywords, tip} 同时喂侧栏、搜索、
 * 页面提示）。批次 AY 只做合并这一段——导出名、条目顺序、字段值全部照旧，
 * SETTINGS_SEARCH_REGISTRY 由 settingEntries() 派生，所以调用方与被断言的字符串零改动。
 *
 * ⚠ 派生时只取 setting 类型条目，且 id 原值直传（不带 "setting:" 前缀）：DOM 测试拿的
 * 就是 data-setting-id 的字面值。前缀只加在 screen / project-view 两类上，原因见
 * registry.ts 顶部——屏幕的 stats（阅读统计）与项目视图的 stats（写作统计）本来就会撞名。
 */
export const SETTINGS_SEARCH_REGISTRY: SettingsSearchEntry[] = settingEntries().map((e) => ({
  id: e.id,
  section: e.section,
  group: e.group,
  label: e.label,
  keywords: e.keywords
}));

export { SETTINGS_SECTION_LABELS };

/** 按查询词过滤注册表：匹配项名 / 组名 / 检索词 / 分区名，最多返回 limit 条 */
export function searchSettings(query: string, limit = 8): SettingsSearchEntry[] {
  const q = query.trim().toLowerCase();
  if (!q) return [];
  const terms = q.split(/\s+/);
  const scored: Array<{ entry: SettingsSearchEntry; score: number }> = [];
  for (const entry of SETTINGS_SEARCH_REGISTRY) {
    const haystacks: Array<[string, number]> = [
      [entry.label.toLowerCase(), 3],
      [entry.group.toLowerCase(), 2],
      [(entry.keywords ?? "").toLowerCase(), 1],
      [SETTINGS_SECTION_LABELS[entry.section].toLowerCase(), 1]
    ];
    let score = 0;
    let matchedAll = true;
    for (const term of terms) {
      const hit = haystacks.find(([text]) => text.includes(term));
      if (!hit) {
        matchedAll = false;
        break;
      }
      score += hit[1];
    }
    if (matchedAll) scored.push({ entry, score });
  }
  scored.sort((a, b) => b.score - a.score);
  return scored.slice(0, limit).map((item) => item.entry);
}
