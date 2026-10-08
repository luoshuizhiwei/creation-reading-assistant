import type { AppScreen } from "@/stores/app-store";
import type { SettingsSection } from "@/types/settings";

/**
 * 项目内视图的联合类型。定义放在这里而不是 project-nav.ts，是因为本文件要按它建条目，
 * 而 project-nav.ts 又要从本文件派生——类型留在被依赖的那一侧，避免循环 import。
 * project-nav.ts 原样 re-export，外部 `import type { ProjectView } from ".../project-nav"`
 * 一行都不用改。
 */
export type ProjectView = "overview" | "writing" | "outline" | "preview" | "cards" | "stats" | "history";

/**
 * 规格 §4.1 第 3 条：把 app-nav.ts + project-nav.ts + search-registry.ts 合并成一条
 * `{id, group, type, label, hint, keywords, tip}`，同时喂侧栏、全局搜索、页面提示——一份真相。
 *
 * 批次 AY 只做「合并」这一段：本文件成为三份名单的唯一数据源，原先三个导出名
 * （APP_NAV_ITEMS / PROJECT_NAV_ITEMS / SETTINGS_SEARCH_REGISTRY）改为从这里派生，
 * 于是调用方与被断言的字符串一个字都没变。第 9 步的硬约束写在规格里是「不得改被断言的
 * 类名」，实测同样被断言的还有这些**文案、顺序与 id**：
 *   · desktop-frame-nav.test.tsx 逐字比对 ["项目","卡片库","收件箱","书库","设置"] 且数量 5；
 *   · settings-search-registry.test.ts 断言 id 唯一、label/group 非空、searchSettings 行为；
 *   · settings-search-dom-guard.test.tsx 把每个 id 与控件 DOM 的 data-setting-id 逐条核对。
 * 派生而不是复制，才让「一份真相」不落空：改这里一处，侧栏/搜索/提示同步变。
 *
 * ⚠ id 的命名规则（这条是本文件唯一的结构约束，别改）：
 *   · setting 条目的 id **就是** data-setting-id 原值（"reader.fontSize"），派生视图直传，
 *     中间不做任何字符串手术——它是被 DOM 测试核对的对外契约。
 *   · screen 与 project-view 的 id 带类型前缀（"screen:stats" / "project-view:stats"），
 *     因为这两个命名空间实测**会撞**：屏幕有 stats（阅读统计），项目视图也有 stats
 *     （写作统计），标签都叫「统计」类字样但功能分账。第一版草稿没加前缀，NAV_REGISTRY
 *     里出现两个裸 "stats"，「id 唯一」当场不成立，按 id 查屏 also 会捞到视图条目。
 *     前缀把这件事变成结构上不可能，而不是靠人记得别同名。
 *
 * 三类条目的形状差别如实保留（不强行抹平）：
 *   · screen —— 左栏项，label 主名 + hint 副名（组件里是 <strong>/<small> 两行）。
 *     灵感中心 / 资料阅读 / 阅读统计三个屏幕**不进左栏**（各自从项目页、书库页进入，
 *     顺序与「左栏恰好五项」被测试钉着），但仍在本表登记——页面提示位要用它们的 tip。
 *   · project-view —— 原表只有 view/label，这里补 hint 与 tip。
 *   · setting —— 原表没有 hint 列，派生视图也不给它凭空造一个。
 *
 * ⚠ 注释里不要写 Tailwind 工具类字面串：content glob 扫 src/** 且不剥注释，
 *   注释里的类名会被发射进产物 CSS（批次 AV 实测）。
 */

export type NavEntryType = "screen" | "project-view" | "setting";

export interface NavEntry {
  /** 全局唯一，命名规则见顶部注释 */
  id: string;
  type: NavEntryType;
  /** screen 恒「一级导航」；project-view 恒「项目导航」；setting 用分区中文名 */
  group: string;
  label: string;
  hint?: string;
  /** 附加检索词（别名、英文、口语说法），空格分隔 */
  keywords?: string;
  /**
   * 页面提示位（§4.1 第 2 条）。批次 AY 登记、批次 AZ 起由页头渲染：screen 的喂
   * DesktopFrame 页头标题下那行，project-view 的喂项目页 hero 那行小字。
   * ⚠ 现在这 15 条句子是**合并前界面上正在显示的原话**（逐字节搬过来的），不是新写的
   * 文案——批次 AZ 的前提是零视觉，改文字和改结构不能混在一个提交里。规格要的
   * 「这一页的规矩」那种写法（讲边界与后果，例如「本地文件不上传；阅读时的摘录落进
   * 收件箱，不直接改稿」）已另起草，稿子在规格笔记同级的 tips-copy-draft.md（仓库外）。
   * 要换得单独一批：在提交信息里逐页写清改了哪句话，并同步 nav-registry.test.ts 里
   * ORIGINAL_SCREEN_TIPS / ORIGINAL_PROJECT_VIEW_TIPS 两张锚点表。
   */
  tip?: string;
  screen?: AppScreen;
  view?: ProjectView;
  section?: SettingsSection;
}

/** 分区中文名：原 search-registry.ts 的导出搬到这里，供派生视图与搜索共用。 */
export const SETTINGS_SECTION_LABELS: Record<SettingsSection, string> = {
  appearance: "外观",
  reader: "阅读器",
  ai: "AI 助手",
  storage: "数据与存储",
  debug: "关于 / 调试"
};

/* ------------------------------------------------------------------ 应用级屏幕 */

const SCREEN_ENTRIES: NavEntry[] = [
  { id: "screen:projects", type: "screen", screen: "projects", group: "一级导航", label: "项目", hint: "写作", keywords: "创作 项目 书稿 章节 场景", tip: "管理作品项目；项目内包含概览、写作、大纲、卡片、背景设定、统计与版本历史。" },
  { id: "screen:card-library", type: "screen", screen: "card-library", group: "一级导航", label: "卡片库", hint: "世界观", keywords: "卡片 角色 地点 组织 设定 世界观", tip: "跨作品复用角色、地点、组织和世界观设定；查看它们正在服务的项目。" },
  { id: "screen:inbox", type: "screen", screen: "inbox", group: "一级导航", label: "收件箱", hint: "待处理", keywords: "收件箱 灵感 摘录 待处理 转换", tip: "旧灵感迁移与手动收集的内容；可转为创作项目的资料卡。" },
  { id: "screen:library", type: "screen", screen: "library", group: "一级导航", label: "书库", hint: "资料阅读", keywords: "书库 书籍 epub txt markdown 导入 摘录", tip: "导入、筛选和打开本地 TXT / Markdown / EPUB，阅读时摘录到项目。" },
  { id: "screen:settings", type: "screen", screen: "settings", group: "一级导航", label: "设置", hint: "偏好", keywords: "设置 偏好 主题 缩放 同步 备份 密钥", tip: "配置 AI、外观、阅读、同步与数据维护。" },
  // 以下三个屏幕可达但不进左栏；登记在此是为了页面提示位与搜索检索词有同一份来源。
  { id: "screen:inspiration", type: "screen", screen: "inspiration", group: "一级导航", label: "灵感中心", hint: "旧数据", keywords: "灵感 摘录 花 ai 候选", tip: "旧数据兼容入口：阅读摘录、灵感花和 AI 候选版本。" },
  { id: "screen:reader", type: "screen", screen: "reader", group: "一级导航", label: "资料阅读", hint: "正文", keywords: "阅读 正文 目录 摘录 字号 行距", tip: "正文、目录、阅读设置和灵感摘录，专注阅读。" },
  { id: "screen:stats", type: "screen", screen: "stats", group: "一级导航", label: "阅读统计", hint: "节律", keywords: "统计 时长 进度 节律 阅读", tip: "查看阅读时长、书籍进度和节律总结。" }
];

/**
 * 左栏实际渲染的五项 id（顺序即渲染顺序，被 desktop-frame-nav.test.tsx 逐字断言，勿改）。
 * 用 id 而不是屏幕名登记，是为了让「漏登记」「拼错」在派生时当场抛错，而不是静默少一项。
 */
const SIDEBAR_IDS = ["screen:projects", "screen:card-library", "screen:inbox", "screen:library", "screen:settings"] as const;

/* ------------------------------------------------------------------ 项目内视图 */

const PROJECT_VIEW_ENTRIES: NavEntry[] = [
  { id: "project-view:overview", type: "project-view", view: "overview", group: "项目导航", label: "概览", hint: "进度", tip: "项目概览：写作目标、最近编辑与待处理事项。" },
  { id: "project-view:writing", type: "project-view", view: "writing", group: "项目导航", label: "写作", hint: "正文", tip: "在场景中连续写作；卷章结构在大纲中管理，中文输入、撤销重做、粘贴清洗和自动保存都在本地完成。" },
  { id: "project-view:outline", type: "project-view", view: "outline", group: "项目导航", label: "大纲", hint: "结构", tip: "大纲树与场景任务卡板共享同一数据；任务卡记录视角、时间、地点、出场、目标、冲突、结果与情绪。" },
  { id: "project-view:preview", type: "project-view", view: "preview", group: "项目导航", label: "全书预览", hint: "通读", tip: "按卷、章、场景通读全书并可打印或导出打印版 PDF；本页只读，正文改动请回到写作台。" },
  { id: "project-view:cards", type: "project-view", view: "cards", group: "项目导航", label: "设定卡", hint: "本项目", tip: "管理角色、地点、组织等创作卡片与它们之间的关系；背景设定作为卡片页的二级入口。" },
  { id: "project-view:stats", type: "project-view", view: "stats", group: "项目导航", label: "写作统计", hint: "本机", tip: "项目字数、写作时长、连续写作与修订进度；会话只在输入时计时，不记录具体按键内容。" },
  { id: "project-view:history", type: "project-view", view: "history", group: "项目导航", label: "版本历史", hint: "快照", tip: "误删的内容可在这里恢复，或从命名快照回到某个版本；永久删除前请确认。" }
];

/* ------------------------------------------------------------------ 设置项 */

/**
 * setting 条目：`id` = data-setting-id 字面值（对外契约），`group` = **子分组名**。
 *
 * ⚠ group 在这里不是分区中文名：原 search-registry.ts 用的是「主题与缩放 / 排版 /
 * 字体与背景 / 格式与行为 / 阅读记录 / 预设 / 服务连接 / 生成参数 / 存储位置 /
 * 备份与恢复 / 手机同步 / 已配对设备 / 应用信息 / 诊断工具 / 应用更新」这一层，
 * SettingsSearch.tsx 把它渲染成「分区 › 子分组」，searchSettings 还按它给 2 分权重。
 * 第一版草稿误用 SETTINGS_SECTION_LABELS[section] 顶替，等于一次静默的搜索结果改版——
 * 现按原值逐条照登，派生侧不做任何字符串加工。
 */
const S = (section: SettingsSection, key: string, group: string, label: string, keywords: string): NavEntry => ({
  id: `${section}.${key}`,
  type: "setting",
  section,
  group,
  label,
  keywords
});

/** id 与控件 DOM 的 data-setting-id 一一对应（settings-search-dom-guard.test.tsx 逐条核对）。 */
const SETTING_ENTRIES: NavEntry[] = [
  S("appearance", "theme", "主题与缩放", "应用主题", "深色 浅色 暗色 theme"),
  S("appearance", "appFontScale", "主题与缩放", "应用字体缩放", "界面字号 缩放 scale"),

  S("reader", "fontSize", "排版", "字号", "字体大小 字号 size"),
  S("reader", "lineHeight", "排版", "行距", "行间距 line height"),
  S("reader", "paragraphSpacing", "排版", "段间距", "段落间距 paragraph"),
  S("reader", "letterSpacing", "排版", "字间距", "字符间距 letter spacing"),
  S("reader", "pageMargin", "排版", "页边距", "边距 margin"),
  S("reader", "fontFamily", "字体与背景", "字体", "字体选择 导入字体 font"),
  S("reader", "readerBackground", "字体与背景", "书籍背景", "背景 纸张 颜色 暖纸 护眼 夜间 background"),
  S("reader", "epubStyleMode", "格式与行为", "EPUB 样式", "原书样式 统一样式 epub"),
  S("reader", "textConversion", "格式与行为", "繁简转换", "繁体 简体 转换"),
  S("reader", "restoreLastPosition", "格式与行为", "自动恢复上次位置", "位置 进度 恢复"),
  S("reader", "trackReadingSessions", "阅读记录", "自动记录阅读会话", "阅读记录 计时 会话"),
  S("reader", "presets", "预设", "阅读预设", "预设 场景 preset"),

  S("ai", "enabled", "服务连接", "启用 AI 助手", "ai 开关 启用"),
  S("ai", "provider", "服务连接", "Provider", "服务商 openai"),
  S("ai", "baseUrl", "服务连接", "Base URL", "地址 接口 url"),
  S("ai", "model", "服务连接", "模型名", "model 模型"),
  S("ai", "apiKey", "服务连接", "API Key", "密钥 key"),
  S("ai", "temperature", "生成参数", "Temperature", "温度 随机性"),

  S("storage", "dataDirectory", "存储位置", "数据目录", "目录 路径 数据"),
  S("storage", "libraryDirectory", "存储位置", "书库目录", "书籍目录 书库 路径"),
  S("storage", "backupActions", "备份与恢复", "备份 / 恢复数据", "备份 恢复 扫描"),
  S("storage", "encryptedBackupActions", "备份与恢复", "加密备份", "加密 口令 密码 导出 恢复 crbackup 备份"),
  S("storage", "autoBackupEnabled", "备份与恢复", "自动备份", "备份 自动"),
  S("storage", "sync", "手机同步", "手机同步", "同步 局域网 配对 二维码 wifi"),
  S("storage", "syncDevices", "已配对设备", "已配对设备", "设备 断开 手机"),

  S("debug", "appVersion", "应用信息", "应用版本", "版本 version"),
  S("debug", "dataRoot", "应用信息", "数据目录（调试）", "目录 路径"),
  S("debug", "diagnostics", "诊断工具", "诊断工具", "日志 调试信息 导出"),
  S("debug", "update", "应用更新", "应用更新", "更新 升级 update")
];

/* ------------------------------------------------------------------ 合并表 */

/** 一份真相：三份名单合并后的完整注册表。顺序即原三份各自的顺序，派生视图据此保序。 */
export const NAV_REGISTRY: NavEntry[] = [...SCREEN_ENTRIES, ...PROJECT_VIEW_ENTRIES, ...SETTING_ENTRIES];

/**
 * 每条按 type 必须齐的字段。group 对 screen/project-view 是固定值，setting 的 group
 * 是分区中文名（由 SETTINGS_SECTION_LABELS 决定），所以 group 只在前两类上钉。
 */
export const REQUIRED_FIELDS: Record<NavEntryType, readonly (keyof NavEntry)[]> = {
  screen: ["screen"],
  "project-view": ["view"],
  setting: ["section"]
};

export const GROUP_BY_TYPE: Partial<Record<NavEntryType, string>> = {
  screen: "一级导航",
  "project-view": "项目导航"
};

/**
 * 结构自检（供 registry.test.ts 断言，也在派生前兜底）：
 * 重复 id、缺类型必填字段、group 与 type 不匹配、label 空——这几样都会让派生视图
 * 静默产出少一项或错一项的名单，而「侧栏少一项」正是测试全绿、界面缺一块那类静默失效。
 * 返回人话数组而不是抛错，是为了让测试一次报全部结构问题。
 */
export function registryIssues(): string[] {
  const issues: string[] = [];
  const seen = new Map<string, NavEntryType>();
  for (const e of NAV_REGISTRY) {
    if (!e.id.trim()) issues.push("存在空 id 的条目");
    if (!e.label.trim()) issues.push(`${e.id || "(空 id)"}：label 为空`);
    if (!e.group.trim()) issues.push(`${e.id}：group 为空`);
    for (const f of REQUIRED_FIELDS[e.type]) {
      if (e[f] === undefined) issues.push(`${e.id}（${e.type}）缺少必填字段 ${String(f)}`);
    }
    const wantGroup = GROUP_BY_TYPE[e.type];
    if (wantGroup && e.group !== wantGroup) issues.push(`${e.id}：group 应为「${wantGroup}」，实为「${e.group}」`);
    const dup = seen.get(e.id);
    if (dup) issues.push(`id 重复：${e.id} 同时属于 ${dup} 与 ${e.type}（id 必须带类型前缀）`);
    else seen.set(e.id, e.type);
  }
  return issues;
}

const mustFind = (id: string): NavEntry => {
  const hit = NAV_REGISTRY.filter((e) => e.id === id);
  if (hit.length !== 1) throw new Error(`navigation registry：id ${id} 匹配到 ${hit.length} 条（应为 1 条）`);
  return hit[0];
};

/* ------------------------------------------------------------------ 派生视图 */

/** 左栏项：screen 字段必然存在（由 registryIssues 的结构自检保证）。 */
export interface SidebarEntry extends NavEntry {
  screen: AppScreen;
  label: string;
  hint: string;
}
/** 项目内视图项。 */
export interface ProjectViewEntry extends NavEntry {
  view: ProjectView;
  label: string;
}
/** 设置项检索条目。 */
export interface SettingEntry extends NavEntry {
  type: "setting";
  section: SettingsSection;
  group: string;
  label: string;
  keywords: string;
}

/** 左栏用：应用级屏幕条目，顺序按 SIDEBAR_IDS（被 desktop-frame-nav.test.tsx 逐字断言）。 */
export const appNavEntries = (): SidebarEntry[] =>
  SIDEBAR_IDS.map((id) => {
    const e = mustFind(id);
    if (e.type !== "screen" || !e.screen || !e.hint) throw new Error(`navigation registry：${id} 不是合格的左栏屏幕条目`);
    return e as SidebarEntry;
  });

/** 项目页用：项目内视图条目，顺序与原 project-nav.ts 表一致。 */
export const projectViewEntries = (): ProjectViewEntry[] =>
  NAV_REGISTRY.filter((e): e is ProjectViewEntry => e.type === "project-view" && Boolean(e.view));

/** 设置搜索用：设置项条目，id 原值直传（被 DOM 测试核对，勿加前缀勿改写）。 */
export const settingEntries = (): SettingEntry[] =>
  NAV_REGISTRY.filter((e): e is SettingEntry => e.type === "setting" && Boolean(e.section) && typeof e.keywords === "string");

/**
 * 页面提示位（§4.1 第 2 条）：批次 AZ 起由 DesktopFrame 的页头那行渲染。
 * 取不到就抛，不返回 undefined——这条线现在是有真实读者的：一个屏幕在
 * APP_SCREENS 里存在、在本表里没有 tip，界面上就是静默的空行（或者 undefined），
 * 而注册表的意义正是「一份真相」，缺一项应当在开发期当场炸掉，和 mustFind 同一个道理。
 */
export function screenTip(screen: AppScreen): string {
  const hit = NAV_REGISTRY.find((e) => e.type === "screen" && e.screen === screen);
  if (!hit?.tip) throw new Error(`navigation registry：屏幕 ${screen} 没有登记 tip，页头提示位会空`);
  return hit.tip;
}

/**
 * 项目视图的提示位：批次 AZ 起由项目页 hero 那行小字渲染（原先那句住在
 * CreationProjectsPage 的 viewDescription 里，与注册表是双写）。
 * 与 screenTip 同样的理由：有真实读者了，取不到就抛，不静默给一个空行。
 */
export function projectViewTip(view: ProjectView): string {
  const hit = NAV_REGISTRY.find((e) => e.type === "project-view" && e.view === view);
  if (!hit?.tip) throw new Error(`navigation registry：项目视图 ${view} 没有登记 tip，hero 说明会空`);
  return hit.tip;
}

export type { AppScreen, SettingsSection };
