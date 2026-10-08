import { describe, expect, it } from "vitest";
import { APP_NAV_ITEMS } from "../app-nav";
import { PROJECT_NAV_ITEMS } from "../project-nav";
import { SETTINGS_SEARCH_REGISTRY } from "@/features/settings/search-registry";
import { NAV_REGISTRY, registryIssues, screenTip } from "../registry";

/**
 * 批次 AY：三份名单合并成一条注册表（规格 §4.1 第 3 条）。
 * 本文件的存在理由不是「测一下函数返回什么」，而是钉住合并的这一条性质：
 * **合并必须零改动**——派生出来的三份，与原三份逐字段相等。
 *
 * 期望值原样抄自合并前的三份表（git HEAD 的 app-nav.ts / project-nav.ts /
 * search-registry.ts）。之所以把期望写死而不是再算一遍：派生代码若写错（漏字段、
 * group 用成分区名、顺序变了），拿派生验派生永远绿。抄下来的旧值是一次性的锚，
 * 它红了只有两种解释：要么合并真的改了对外行为（本批的立项前提被破坏），
 * 要么有人**有意**改了导航/设置项的文案与分组——那必须在同一个提交里更新这些
 * 期望值，并在提交信息里写明改了哪个界面的哪句话。
 */

const ORIGINAL_APP_NAV = [
  { kind: "screen", screen: "projects", label: "项目", hint: "写作" },
  { kind: "screen", screen: "card-library", label: "卡片库", hint: "世界观" },
  { kind: "screen", screen: "inbox", label: "收件箱", hint: "待处理" },
  { kind: "screen", screen: "library", label: "书库", hint: "资料阅读" },
  { kind: "screen", screen: "settings", label: "设置", hint: "偏好" }
];

const ORIGINAL_PROJECT_NAV = [
  { view: "overview", label: "概览" },
  { view: "writing", label: "写作" },
  { view: "outline", label: "大纲" },
  { view: "preview", label: "全书预览" },
  { view: "cards", label: "设定卡" },
  { view: "stats", label: "写作统计" },
  { view: "history", label: "版本历史" }
];

const ORIGINAL_SETTINGS = [
  { id: "appearance.theme", section: "appearance", group: "主题与缩放", label: "应用主题", keywords: "深色 浅色 暗色 theme" },
  { id: "appearance.appFontScale", section: "appearance", group: "主题与缩放", label: "应用字体缩放", keywords: "界面字号 缩放 scale" },
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
  { id: "ai.enabled", section: "ai", group: "服务连接", label: "启用 AI 助手", keywords: "ai 开关 启用" },
  { id: "ai.provider", section: "ai", group: "服务连接", label: "Provider", keywords: "服务商 openai" },
  { id: "ai.baseUrl", section: "ai", group: "服务连接", label: "Base URL", keywords: "地址 接口 url" },
  { id: "ai.model", section: "ai", group: "服务连接", label: "模型名", keywords: "model 模型" },
  { id: "ai.apiKey", section: "ai", group: "服务连接", label: "API Key", keywords: "密钥 key" },
  { id: "ai.temperature", section: "ai", group: "生成参数", label: "Temperature", keywords: "温度 随机性" },
  { id: "storage.dataDirectory", section: "storage", group: "存储位置", label: "数据目录", keywords: "目录 路径 数据" },
  { id: "storage.libraryDirectory", section: "storage", group: "存储位置", label: "书库目录", keywords: "书籍目录 书库 路径" },
  { id: "storage.backupActions", section: "storage", group: "备份与恢复", label: "备份 / 恢复数据", keywords: "备份 恢复 扫描" },
  { id: "storage.encryptedBackupActions", section: "storage", group: "备份与恢复", label: "加密备份", keywords: "加密 口令 密码 导出 恢复 crbackup 备份" },
  { id: "storage.autoBackupEnabled", section: "storage", group: "备份与恢复", label: "自动备份", keywords: "备份 自动" },
  { id: "storage.sync", section: "storage", group: "手机同步", label: "手机同步", keywords: "同步 局域网 配对 二维码 wifi" },
  { id: "storage.syncDevices", section: "storage", group: "已配对设备", label: "已配对设备", keywords: "设备 断开 手机" },
  { id: "debug.appVersion", section: "debug", group: "应用信息", label: "应用版本", keywords: "版本 version" },
  { id: "debug.dataRoot", section: "debug", group: "应用信息", label: "数据目录（调试）", keywords: "目录 路径" },
  { id: "debug.diagnostics", section: "debug", group: "诊断工具", label: "诊断工具", keywords: "日志 调试信息 导出" },
  { id: "debug.update", section: "debug", group: "应用更新", label: "应用更新", keywords: "更新 升级 update" }
];

describe("合并注册表的结构自检", () => {
  it("registryIssues() 为空：无重复 id、无缺字段、group 与 type 相符", () => {
    expect(registryIssues()).toEqual([]);
  });

  it("screen 与 project-view 的 id 带类型前缀（两者的 stats 本来会撞名）", () => {
    // 这条不是形式主义：屏幕有 stats（阅读统计）、项目视图也有 stats（写作统计）。
    // 去掉前缀后 registryIssues() 会报重复，而按裸 id 查条目会捞到错的那一条。
    expect(NAV_REGISTRY.filter((e) => e.type === "screen").every((e) => e.id.startsWith("screen:"))).toBe(true);
    expect(NAV_REGISTRY.filter((e) => e.type === "project-view").every((e) => e.id.startsWith("project-view:"))).toBe(true);
    expect(NAV_REGISTRY.some((e) => e.id === "screen:stats" && e.screen === "stats")).toBe(true);
    expect(NAV_REGISTRY.some((e) => e.id === "project-view:stats" && e.view === "stats")).toBe(true);
  });

  it("setting 的 id 不带前缀（它是 data-setting-id 的对外契约字面值）", () => {
    for (const e of NAV_REGISTRY.filter((x) => x.type === "setting")) {
      expect(e.id.startsWith("setting:")).toBe(false);
      expect(e.id).toMatch(/^[a-z]+\.[A-Za-z]+$/);
    }
  });
});

describe("派生视图与原三份名单逐字段相等（合并 = 零改动）", () => {
  it("APP_NAV_ITEMS 与合并前的左栏五项完全一致", () => {
    expect(APP_NAV_ITEMS).toEqual(ORIGINAL_APP_NAV);
  });

  it("PROJECT_NAV_ITEMS 与合并前的七个项目视图完全一致", () => {
    expect(PROJECT_NAV_ITEMS).toEqual(ORIGINAL_PROJECT_NAV);
  });

  it("SETTINGS_SEARCH_REGISTRY 与合并前的 31 条设置项完全一致（含 group 子分组名与 keywords）", () => {
    expect(SETTINGS_SEARCH_REGISTRY).toEqual(ORIGINAL_SETTINGS);
  });
});

describe("提示位数据已登记（批次 AZ 才渲染，本批只验数据面）", () => {
  it("八个屏幕都有 tip", () => {
    const screens = NAV_REGISTRY.filter((e) => e.type === "screen");
    expect(screens.length).toBe(8);
    for (const e of screens) expect((e.tip ?? "").length).toBeGreaterThan(0);
  });

  it("screenTip 按屏幕取到对应文案，未登记的取不到", () => {
    expect(screenTip("inbox")).toBe(NAV_REGISTRY.find((e) => e.id === "screen:inbox")!.tip);
    // 覆盖 APP_SCREENS 全集：每个屏幕都要有提示位，否则页头那行会空。
    for (const s of ["projects", "card-library", "inbox", "inspiration", "library", "reader", "stats", "settings"] as const) {
      expect(typeof screenTip(s)).toBe("string");
    }
  });
});
