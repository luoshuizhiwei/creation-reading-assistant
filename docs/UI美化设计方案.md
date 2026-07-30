# 创作阅读助手 · 移动端 UI 美化设计方案

> **角色**：UI Designer（界面设计师）
> **目标**：在不改变功能、不改动布局结构、不改变用户已习惯的「5 标签底部导航 + 阅读器全屏覆盖层」心智模型的前提下，提升视觉精致度与触感愉悦感。
> **范围**：仅视觉层（动效 / 触感 / 字重 / 纹理一致性），全部基于现有「墨韵·素笺」设计令牌体系，零新增颜色、零新增圆角、零破坏安全区与栅格。

---

## 1. 设计原则（贴合用户习惯）

1. **结构不动**：底部 5 Tab（首页 / 书架 / 灵感 / 统计 / 我的）、阅读器全屏层、顶部 AppBar、底部 Sheet 全部保留原样。
2. **功能不动**：所有按钮、手势、跳转、导入/同步逻辑原封不动，本次只改「看起来」和「按下去的感觉」。
3. **令牌复用**：所有美化只引用 `md3-base.css` 已有变量（`--md3-motion-*`、`--app-soft-shadow-*`、`--md3-surface-*` 等），不新增设计令牌。
4. **无障碍优先**：所有动效尊重 `prefers-reduced-motion: reduce`，触感反馈不替代键盘/读屏可用性。
5. **性能友好**：纯 CSS 过渡与 `@keyframes`，无 JS 动画、无重排（transform/opacity 合成层）。

---

## 2. 现状资产盘点（已做对的，保留）

| 资产 | 位置 | 评价 |
|---|---|---|
| 墨韵·素笺主题 | `md3-base.css` | 宣纸底 + 靛青印章，暖而不艳，已覆盖 light/dark/system |
| 纸质纹理层 | `styles/paper-grain.css` | 统一抽取 `::before` 噪点 + `isolation`，DRY 到位 |
| 分类色封面 | `app-foundation.css`（`data-category`） | 文学/社科/科技/历史/艺术/默认 六色梯度已落地 |
| 毛玻璃底部导航 | `interaction-polish.css` | `blur(18px)` 玻璃质感已具备 |
| 完整 MD3 动效令牌 | `md3-base.css` | `--md3-motion-*` 缓动/时长齐备 |
| 状态胶囊语义色 | `release-overrides.css` | reading/done/streak 语义色已就绪 |

> 结论：地基已经很扎实，**本次不是「重做」，而是「收口与提味」**。

---

## 3. 本轮已落地的美化（已实装，可预览）

新增文件 `mobile/src/styles/ui-beautify.css`，并在 `styles.css` 中于 `unified-layer` 之前引入（纯加法，不覆盖权威外观）。

### 3.1 入场「浮起」动效（愉悦感）
- 门面级卡片（续读 hero、灵感卡、统计卡、设置卡、对话框、底部 Sheet 等）载入时以 `translateY(10px)→0` + 淡入柔和浮起。
- 网格/多列卡片（首页指标、书架书卡、我的页摘要）加 **轻微错峰**（封顶 10 级），制造节奏而非杂乱。
- 使用 `animation-fill-mode: backwards`：动画结束后不残留 transform，确保书卡 `:active` 按压不被吃掉。

### 3.2 触感一致性（按下去有回应）
- **书架书卡整体**补 `:active` 缩放回弹（此前只有右上角 more 按钮有反馈，整卡点了"没反应"）。
- 首页横向书架小卡、菜单行、我的页菜单行补统一按压底色 + 轻缩放。
- 搜索条补统一过渡，`:active` 缩放更顺滑。

### 3.3 标题精致度（墨韵文气）
- AppBar 主标题字重 `800 → 700` + 负字距 `-0.012em`，更克制文气，不改字号与位置。
- 次级页 / 我的页大标题同步收字距。

### 3.4 死代码清理（维护性）
- 移除了 `interaction-polish.css` 中两处被截断的空 `::before` 选择器（`.home-continue-cover::before`、`.home-continue-list-cover::before`），无视觉影响，纯减负。

---

## 4. 建议的后续美化清单（待你确认，均为低风险视觉项）

> 以下每一项都可独立开关、独立提交，不影响功能。建议按顺序推进，每项都附带「回归方式」。

| # | 美化项 | 做法（基于现有令牌） | 风险 | 回归方式 |
|---|---|---|---|---|
| P1 | **统一 `:hover` 隔离审计** | 全站扫描裸 `:hover`，统一包进 `@media (hover:hover) and (pointer:fine)`，移动端不误触发 | 低 | 目测 + grep |
| P2 | **空态插画一致性** | 确保全部空态复用 `EmptyIllustration`（线条风、靛青色），避免"灰字+空白"网页感 | 低 | 逐页走查 5 Tab |
| P3 | **阅读器走纸感微调** | 正文 `line-height` 微提到 1.9、`text-indent: 2em`、段首留白，强化"读书"而非"看网页"（仅 `reader-layout.css` 内） | 低 | 开一本书对比 |
| P4 | **进度条圆角统一** | 全站进度条统一 `border-radius: 999px` 与 `--app-progress-fill`，移除个别方角 | 低 | 书架/首页/统计走查 |
| P5 | **焦点环一致性** | 确保全部可交互元素都有 `:focus-visible` 靛青环（a11y + 习惯统一） | 低 | 键盘 Tab 走查 |
| P6 | **封面微高光** | 在分类封面左上角加一条极淡的 `linear-gradient` 高光（复用 V3-cover 思路），提升"实体书"质感 | 低 | 书架走查 |
| P7 | **节标题小标（eyebrow）** | 在 AppBar 主标题上方加一行 `--md3-primary` 小字（如「今日 · 7月26日」），需少量 JSX，属最小结构改动，可选项 | 中 | 需你确认是否接受微小结构改动 |

> **说明**：P7 是唯一涉及极少量 JSX 的项（加一行小标），其余全部 CSS-only。如你倾向"绝对不改结构"，可只做 P1–P6。

---

## 5. 验收与回归

- **构建后必跑**：`npm run mobile:build` → `node scripts/verify-mobile-css-budget.mjs`（CSS ≤ 320 KiB；本轮增量约 3KB 源码，余量充足）。
- **动效护栏**：系统开启「减少动态效果」时，全部入场动效与按压缩放自动关闭，界面静态可用。
- **触控目标**：所有新增反馈均作用于既有 ≥44×44px 目标，不缩小热区。
- **主题一致**：light / dark / system 三套变量均已存在，新增规则仅引用变量，无硬编码颜色。

---

## 6. 交付文件

- `mobile/src/styles/ui-beautify.css`（本轮新增美化层）
- `mobile/src/styles.css`（新增一行 `@import`，引入美化层）
- `mobile/src/styles/interaction-polish.css`（清理两处死选择器）

**UI Designer 建议**：先 `npm run mobile:build` 在真机/模拟器预览本轮动效与触感，确认风格后再决定是否推进 P1–P7。
