# 清样工作台 · 细节迭代第三轮计划（Layout & Craft Pass）

> 用途：交给执行 Agent 的下一轮细节规格。**只规划，未实施。**
> 仓库：`D:\develop\Code\Codex\creation-reading-assistant`　基线：分支 `codex/workspace-backup-2026-08-20`，HEAD `a6ccec8`
> 前置：Round 1-4 已完成（语义令牌 `src/styles/tokens.css`、壳层扁平化、图标栏、检查器折叠、
> hero 压平、设置左分类右表单、大纲树三列）。本轮只处理「细节质感」——布局比例、对齐、截断、语义色残留。
> 截图证据：`scripts/visual-evidence-r4-pages/`（本轮执行前先重拍）。

---

## 0. 硬性边界（沿用主规格 §0）

- 不 commit/push/stage；不动 `android/**`、`archives/**`、主进程契约、`package.json`。
- 保留 ARIA、测试选择器、键盘命令；调整必须同步所属测试。
- 每个修复项完成后重拍对应页截图并人工判图，再进下一项。

---

## 1. 问题清单（截图 + 代码定位，均已核实）

### P1 写作台正文过窄（最严重）
- **现象**：1440px 下纸面有效宽约 **465px**，规格要求正文有效宽 680–760px；纸面两侧
  各有 ~100px 死底色，正文被「挤在中间一小条」。
- **代码定位**：
  - `src/styles/editorial-studio.css:736` `.writing-desk { grid-template-columns: 192px minmax(470px, 1fr) 204px; }`
  - `src/styles/editorial-studio.css:884` `.scene-editor, .writing-continuous { max-width: 920px; }`
  - `.writing-scroll` 的 `padding: 24px clamp(18px, 4vw, 60px) 44px` 在窄中列里双向挤压
- **修复**：
  1. 列宽改 `224px minmax(560px, 1fr) 288px`（288px = 规格 §4.2 检查器宽）。
  2. `.scene-editor`/`.writing-continuous` 的 `max-width: 920px` 改为 `none`（纸面铺满中列）；
     正文有效宽度改由 `.scene-editor-content, .writing-continuous-scene .ProseMirror` 内层控制：
     `max-width: 760px; margin-inline: auto; padding-inline: 48px`。
  3. `.writing-scroll` 横向 padding 收敛为 `clamp(16px, 2.5vw, 36px)`。

### P2 工具栏仍是「浮块」且折两行
- **现象**：工具栏呈圆角白块悬于纸面内，撤销/段落/B/I 一行，行聚焦/打字机/专注/已保存
  挤到第二行右侧；与 Round 2「停靠通栏」意图不符。
- **代码定位**：
  - `src/styles.css:2330` `.scene-editor-toolbar { position: sticky; top: 12px; ... }`（top 12px + 圆角）
  - `src/styles/editorial-studio.css:930` 与文件末尾追加段各有一条 `.scene-editor-toolbar` 规则，
    三处叠加（含 `!important`），最终形态取决于优先级而非意图。
- **修复**：三处规则合并为一条真源——删除 `editorial-studio.css:930` 区块与追加段中的重复，
  在 `styles.css:2330` 原地改为：`position: sticky; top: 0; width: 100%; border: 0;
  border-bottom: 1px solid var(--separator); border-radius: 0; background: var(--surface-paper);
  box-shadow: none;`；按钮间距收紧；「已保存」状态固定行尾。允许换行但两行均通栏。

### P3 侧栏「项目」激活项左缘残留 2px 朱红条
- **现象**：图标栏/宽侧栏下，激活导航项左缘仍是旧主题朱红。
- **代码定位**：`src/styles/editorial-studio.css:93` `.nav-spine-item.active` 的
  `background: linear-gradient(... var(--studio-seal) ...)`（seal 现映射校样红），
  带 `!important`；此前追加的无 `!important` 覆盖未生效。
- **修复**：该规则原地改 `var(--action-primary)`（印刷蓝=交互主色语义正确）；
  同文件内所有 `--studio-seal` 的「位置标记」用途逐一复核（§5 校样红外语义迁移）。

### P4 稿件树截断与行密度
- **现象**：卷名「正文 ¹章」截断为「正…」；场景行「默认场景 15,000字」在 192px 栏内拥挤。
- **代码定位**：`.writing-chapter-button`/`.writing-scene-button`（`styles.css:2194` 附近）
  与 `.outline-scene-main`（本轮已加三列 grid）。
- **修复**：随 P1 列宽 224px 一并缓解；`.outline-volume-title`/`.outline-chapter-title`
  允许 `text-overflow: ellipsis` + `title` 属性；场景行 `.outline-scene-goal` 在无目标时
  不渲染（已实现）；行高统一 32px。

### P5 校样边栏（右栏）密度与截断
- **现象**：AI 按钮行贴底被裁；雷达空态卡与「字数/批注概览/AI 助手」块间距失衡；
  页签行「»」钮与两个页签比例不匀。
- **代码定位**：`.writing-margin`（`editorial-studio.css:782`）与 `SceneRadar.tsx` 的
  blocks 间距（`scene-radar.css` gap 14px）；`.writing-margin-tabs`（追加段）。
- **修复**：`.writing-margin` 增加 `padding-bottom: 24px`；`.scene-radar-ai-buttons`
  改两列 grid；页签行按钮 `min-width: 0` + 文字 `truncate`；`»` 钮固定 22px 方形。

### P6 概览「章节状态」卡空态悬浮条
- **现象**：全部章节「未设置」时，卡内只有一条孤立灰条 + 数字 1，观感像渲染故障。
- **代码定位**：`src/features/creation/overview/OverviewPage.tsx:153`（statusTotal>0 分支
  渲染单行 list）；`.overview-status-bar`（`editorial-studio.css:1716`）。
- **修复**：当 `chapterStatusCounts.length === 1 && !status` 时改渲染
  `.overview-status-empty`（「全部章节尚未设置状态」文案，样式已在追加段）；
  多状态时保持分段条。

### P7 资料库书封字形与行高
- **现象**：书封墨色已统一，但三行书封（书/文/M）字形基线略偏；进度格「0% + 空条 + 时长」
  三元素横向间距松散。
- **代码定位**：`.library-cover`（追加段）与 `LibraryPage.tsx:275`；
  `.desktop-library-row` 进度格（`LibraryPage.tsx:290` 附近）。
- **修复**：封面字改 `font-family: var(--font-content)`（衬线单字更像书脊）；
  进度格改 `grid-template-columns: auto 1fr auto`（百分比｜条｜时长），gap 10px。

### P8 迁移横幅右缘折叠钮形态
- **现象**：横幅右端「关闭」渲染为实心蓝方块（X 不可辨），是 Button primary 类污染。
- **代码定位**：`CreationProjectsPage.tsx:394` `.migration-banner-dismiss`；
  追加段已有幽灵样式但被 Button 基类的 `bg-copper` 顶掉（同特异性、加载序在前）。
- **修复**：追加段选择器升级为 `.migration-banner .migration-banner-dismiss`（提高特异性），
  并加 `display: grid; place-items: center; width: 30px; height: 30px;`。

---

## 2. 执行顺序与验证

| 步 | 内容 | 验证 |
|---|------|------|
| 1 | P1+P2（写作台布局与工具栏，同文件联动） | 重拍 `light/dark-writing`，量纸面宽 ≥680px |
| 2 | P3+P4（侧栏语义色与树密度） | 重拍 `light-writing`，目检左缘 |
| 3 | P5+P6+P7+P8（右栏/概览/资料库/横幅细节） | 重拍对应页 |
| 4 | 全量门禁：`npx vitest run`（719/0）、16+1 verify、`npm run build`、<br>`verify:beta --scope=desktop`、`electron-smoke` | 全绿 |
| 5 | 提交（用户放行后）：单 commit `fix(desktop): 细节迭代第三轮——布局比例与质感` |

## 3. 验收清单
- [ ] 1440px 写作页正文有效宽 680–760px（DOM 量测 `.ProseMirror` 内容盒）
- [ ] 工具栏单/双行均通栏贴纸面顶，无独立浮影
- [ ] 全应用无校样红用于非校对位置（grep `--studio-seal` 逐一核对用途）
- [ ] 无文字截断无 `title` 提示（卷名/场景名/页签）
- [ ] 概览四卡等高，章节状态空态有文案
- [ ] 双主题逐页目检通过

## 4. 不做项
- 大纲「摘要」列（无数据源，§5 禁伪造）；styles/ 目录拆分；专注模式全局隐藏（跨层状态）。
