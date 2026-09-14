# 原生 ↔ Web 移动端对齐规范（框架 v1）

> 目标：把独立原生 `android/`（Kotlin/Compose）的每一个页面、二级页、按钮位置/样式/动画，与 web 版 `mobile/`（React）逐屏对齐到「一模一样」。
> 真源：`git HEAD` 中的 `mobile/src/features/*`、`mobile/src/md3-base.css`、`styles/app-pages.css`、`styles/interaction-polish.css`、`styles/reader.css` 等。**注意：工作树 `mobile/src/features/` 已被删（仅 git HEAD 有），对齐时一律从 `git HEAD` 取源码，勿用工作树。**
> 阅读器：web 实际用 `epub.js`（非 Legado）。原生保留自研引擎，仅对齐外观/功能（暂不改内核，见末节）。

## 设计令牌（web → Compose）
| 维度 | web 变量 | 值 | Compose 落地 |
|---|---|---|---|
| 主表面 | --paper | #FAF8F2 | colorScheme.surface / background |
| 次级表面 | --paper-2 | #F3EFE6 | colorScheme.surfaceVariant |
| 三级表面 | --paper-3 | #E8E2D7 | 图标盒/分区可用 |
| 墨色正文 | --ink | #1A1917 | colorScheme.onSurface |
| 次级文字 | --muted | #76726A | colorScheme.onSurfaceVariant |
| 主色 | --indigo-seal | #3A5670 | colorScheme.primary |
| 发丝线 | --line / --md3-outline-variant | rgba(26,25,23,0.07) | colorScheme.outline（作 1dp 描边） |
| 卡片圆角 | --app-card-radius | 22dp | RoundedCornerShape(22.dp) |
| 控件圆角 | --app-control-radius | 18dp | RoundedCornerShape(18.dp) |
| 抽屉圆角 | --app-sheet-radius | 24dp | RoundedCornerShape(24.dp) |
| 小圆角 | --md3-shape-small | 8dp | RoundedCornerShape(8.dp)（图标盒/封面/徽标） |
| 中圆角 | --md3-shape-medium | 12dp | — |
| 大圆角 | --md3-shape-large | 16dp | — |
| 容器边距 | --md3-container-padding | 16dp | 屏幕/区块 padding 16dp |
| 间距 | spacing-m/l | 16/24dp | 区块间 16dp、组间 24dp |
| 动效时长 | short2/medium2 | 100/200/300ms | animate* duration |
| 动效缓动 | --md3-motion-easing-standard | cubic-bezier(0.2,0,0,1) | CubicBezierEasing(0.2f,0f,0f,1f)；按压用 scale 0.985 而非 elevation |

## 关键视觉规则（与 web 一致）
- **扁平 + 发丝线**：web 卡片用 `box-shadow:none` + `border:1px solid outline-variant`，**不用投影**。原生卡片改为 `elevation=0` + `BorderStroke(1.dp, outline)`。
- **按压反馈**：web 用 `:active { transform: scale(0.985); }`；原生用 M3 Card 自带 ripple 即可（去掉 pressedElevation）。
- **封面微高光**：`Brush.linearGradient` 左上→右下极淡白色光泽，保留。
- **暗色**：已严格对齐 md3-base.css dark，无需改。

## 导航/路由对齐
- 5 主 Tab（首页/书架/灵感/统计/我的）底部栏；web 在进入「我的」子页时**隐藏底部栏** → 原生需在 Profile 子页激活时隐藏（待 T7 处理）。
- reader/search/pairing 全屏覆盖、隐藏底部栏（原生已如此）。
- 顶部全局搜索入口：5 Tab 右上角均有（原生已如此）。

## 逐屏清单（任务映射）
- 首页+底部导航框架：T9（本轮，作为样板）✅
- 书架 T10 / 灵感 T12 / 统计 T11 / 我的+14子页 T15 / 阅读器 T14 / 搜索·引导·扫码 T13
- 每屏验收：`clean assembleDebug --no-daemon` → `adb -s c49ac6cf install -r` → 复验。

## 阅读器内核决策（待确认）
web 用 epub.js（非 Legado）。原生 `mobile/android/legado-reader-core` 是 GPL-3.0，且只让 app 与「Capacitor 壳」一致、不与 web 一致，还有分发许可红线。**当前决策：保留自研引擎，仅外观/功能对齐 web(epub.js)；若要换 Legado 需另走 GPL 路线。**
