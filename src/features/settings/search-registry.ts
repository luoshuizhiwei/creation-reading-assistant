import type { SettingsSection } from "@/types/settings";

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
 * 约定：新增设置控件时必须在此登记一行，并给控件外层加同名 data-setting-id。
 * （__tests__/settings-search-registry.test.ts 会校验 id 唯一性与检索函数。）
 */
export const SETTINGS_SEARCH_REGISTRY: SettingsSearchEntry[] = [
  // 外观
  { id: "appearance.theme", section: "appearance", group: "主题与缩放", label: "应用主题", keywords: "深色 浅色 暗色 theme" },
  { id: "appearance.appFontScale", section: "appearance", group: "主题与缩放", label: "应用字体缩放", keywords: "界面字号 缩放 scale" },

  // 阅读器
  { id: "reader.fontSize", section: "reader", group: "排版", label: "字号", keywords: "字体大小 字号 size" },
  { id: "reader.lineHeight", section: "reader", group: "排版", label: "行距", keywords: "行间距 line height" },
  { id: "reader.paragraphSpacing", section: "reader", group: "排版", label: "段间距", keywords: "段落间距 paragraph" },
  { id: "reader.letterSpacing", section: "reader", group: "排版", label: "字间距", keywords: "字符间距 letter spacing" },
  { id: "reader.pageMargin", section: "reader", group: "排版", label: "页边距", keywords: "边距 margin" },
  { id: "reader.fontFamily", section: "reader", group: "字体与背景", label: "字体", keywords: "字体选择 导入字体 font" },
  { id: "reader.readerBackground", section: "reader", group: "字体与背景", label: "书籍背景", keywords: "背景 纸张 颜色 暖纸 护眼 夜间 background" },
  { id: "reader.epubStyleMode", section: "reader", group: "格式与行为", label: "EPUB 样式", keywords: "原书样式 统一样式 epub" },
  { id: "reader.textConversion", section: "reader", group: "格式与行为", label: "繁简转换", keywords: "繁体 简体 转换" },
  { id: "reader.restoreLastPosition", section: "reader", group: "格式与行为", label: "自动恢复上次位置", keywords: "位置 进度 恢复" },
  { id: "reader.trackReadingSessions", section: "reader", group: "阅读记录", label: "自动记录阅读会话", keywords: "阅读记录 计时 会话" },
  { id: "reader.presets", section: "reader", group: "预设", label: "阅读预设", keywords: "预设 场景 preset" },

  // AI 助手
  { id: "ai.enabled", section: "ai", group: "服务连接", label: "启用 AI 助手", keywords: "ai 开关 启用" },
  { id: "ai.provider", section: "ai", group: "服务连接", label: "Provider", keywords: "服务商 openai" },
  { id: "ai.baseUrl", section: "ai", group: "服务连接", label: "Base URL", keywords: "地址 接口 url" },
  { id: "ai.model", section: "ai", group: "服务连接", label: "模型名", keywords: "model 模型" },
  { id: "ai.apiKey", section: "ai", group: "服务连接", label: "API Key", keywords: "密钥 key" },
  { id: "ai.temperature", section: "ai", group: "生成参数", label: "Temperature", keywords: "温度 随机性" },

  // 数据与存储
  { id: "storage.dataDirectory", section: "storage", group: "存储位置", label: "数据目录", keywords: "目录 路径 数据" },
  { id: "storage.libraryDirectory", section: "storage", group: "存储位置", label: "书库目录", keywords: "书籍目录 书库 路径" },
  { id: "storage.backupActions", section: "storage", group: "备份与恢复", label: "备份 / 恢复数据", keywords: "备份 恢复 扫描" },
  { id: "storage.encryptedBackupActions", section: "storage", group: "备份与恢复", label: "加密备份", keywords: "加密 口令 密码 导出 恢复 crbackup 备份" },
  { id: "storage.autoBackupEnabled", section: "storage", group: "备份与恢复", label: "自动备份", keywords: "备份 自动" },
  { id: "storage.sync", section: "storage", group: "手机同步", label: "手机同步", keywords: "同步 局域网 配对 二维码 wifi" },
  { id: "storage.syncDevices", section: "storage", group: "已配对设备", label: "已配对设备", keywords: "设备 断开 手机" },

  // 关于 / 调试
  { id: "debug.appVersion", section: "debug", group: "应用信息", label: "应用版本", keywords: "版本 version" },
  { id: "debug.dataRoot", section: "debug", group: "应用信息", label: "数据目录（调试）", keywords: "目录 路径" },
  { id: "debug.diagnostics", section: "debug", group: "诊断工具", label: "诊断工具", keywords: "日志 调试信息 导出" },
  { id: "debug.update", section: "debug", group: "应用更新", label: "应用更新", keywords: "更新 升级 update" }
];

export const SETTINGS_SECTION_LABELS: Record<SettingsSection, string> = {
  appearance: "外观",
  reader: "阅读器",
  ai: "AI 助手",
  storage: "数据与存储",
  debug: "关于 / 调试"
};

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
