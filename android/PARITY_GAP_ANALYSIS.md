# 原生版 ↔ 网页版 差距分析与 1:1 还原计划

> 真相源（只读参考，不改动）：`D:\develop\Code\Codex\创作阅读助手\mobile\src`
> 还原目标：`D:\develop\Code\Codex\创作阅读助手\android\app\src\main\java\com\creationreadingassistant`
> 约束：网页版作为参考保留不动；原生版逐屏对照还原 UI 与功能，**不得重新设计布局**。

## 结论
原生版主体框架已落地（5 Tab + Reader 全屏浮层 + 14 个 Profile 子页 + 17+17 数据层 + 设置持久化），但相比网页版仍有大量功能/UI 差距，集中在 **阅读器**、**Profile 桩化功能**、**缺失功能**。需在保持 `mobile/` 不动的前提下逐屏对照还原。

---

## 一、阅读器（最大差距）

| 功能 | 网页版 | 原生版现状 | 动作 |
|---|---|---|---|
| 书内全文搜索 | ReaderPanel search tab，当前书全文搜索+跳转 | ❌ 无 | 补齐 |
| 翻页模式 | paged（左右翻页）/ scroll | ⚠️ `readerMode=paged` 设置未被读取，始终按章滚动 | 修复或明确 |
| 高亮 | 颜色切换 + note 字段 + 转笔记/转灵感 + 按章分组 + 导出书摘 | ⚠️ 仅建色 + 删除，无 note/改色/转换/导出 | 增强 |
| 笔记 | 抽屉 notes tab + 草稿态 | ⚠️ 仅 AlertDialog 即时保存 | 增强 |
| 主题背景 | 7 种：white/warm/green/night/warm-yellow/green-bean/oled-black | ⚠️ 4 种（缺 warm-yellow/green-bean/oled-black） | 补 3 种 |
| 亮度调节 | ✅ | ❌ 无 | 补齐 |
| 阅读提醒 | 节奏提示 / 护眼提醒 | ❌ 无 | 补齐 |
| 灵感速记 | 标题/想法/摘录 + 分类选择 + 标签选择 | ⚠️ 无分类/标签 | 补 |
| AI 解读存灵感 | 带 tags / categoryIds | ⚠️ 不带 | 补 |
| 书籍信息面板 | 字数/章节数/阅读统计/删除 | ⚠️ Reader 内无 | 补 |
| 进度面板统计 | 阅读速度/预计读完/灵感数/书签数 + 滑块 | ⚠️ 仅上/下章 + 滑块 | 补统计行 |
| TTS 高级 | 音色/音调/音量/在线引擎/定时停止/媒体通知/句高亮 | ⚠️ 仅语速 + 段落切换 | 增强 |

## 二、Profile 桩化功能（标注"待接入原生"）

- WebDAV：「测试」按钮桩化；「下载恢复」无（仅提示用导入 JSON）
- AI 设置：「连接测试」桩化
- 同步：最近同步结果 / 日志明细占位文本
- 关于：检查更新 / 下载对应源码 / 查看 GPL-3.0 全部桩化
- 诊断：日志收集 / 导出 / 清空全部桩化

## 三、缺失功能

- **Onboarding 首次引导浮层**（网页 `OnboardingOverlay.tsx` 有，原生无）
- **灵感 AI 候选版本**（`InspirationVariantEntity` 数据层存在，但 UI 无入口）

## 四、结构性 re-design（功能等价，低优先，暂不强制 1:1）

- 导航：原生用 NavHost 路由（含 `reader/{bookId}`/`search`/`pairing`）vs 网页条件渲染 + 浮层 —— 功能等价
- StatsScreen 直连 DAO（`@EntryPoint`）vs 网页 services 层 —— 功能等价
- EPUB 自研引擎（ZipFile+XmlPullParser）vs 网页 epub.js —— 分页/CFI 能力差异，因沙箱无网络约束保留现状
- 灵感 source 序列化进 `payload` JSON vs 网页独立 source 模型 —— 跨端同步映射需注意

---

## 执行阶段

- **阶段一（阅读器核心，quick win）**：补 3 种主题背景 + 亮度控制；书内全文搜索；高亮增强（note/改色/转笔记灵感/导出书摘）；灵感速记补分类/标签 + AI 解读存灵感带标签分类
- **阶段二（阅读器进阶）**：翻页模式修复；进度面板统计行；书籍信息面板；阅读提醒（节奏/护眼）；TTS 高级项
- **阶段三（Profile 桩化补齐）**：WebDAV 测试+下载恢复；AI 测试；同步日志明细；关于更新/GPL/源码；诊断日志收集/导出/清空
- **阶段四（缺失功能）**：Onboarding 引导浮层；灵感 AI 候选版本 UI；修正 `AiSettingsSubPage` 误导性 `DegradedNote` 注释
