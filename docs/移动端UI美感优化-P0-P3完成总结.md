# 移动端 UI 美感优化 · P0–P3 实施完成总结

> 项目：创作阅读助手（Capacitor 6 + React 18 + 自研 MD3 暖铜设计系统，真安卓应用）
> 范围：把原型稿的精致视觉语言（手写体标题、分类色封面、语义胶囊、装饰光晕、统一圆角）移植进真机组件，去除"网页感"，并补齐暗色模式封面统一。
> 原则：纯 CSS / 设计令牌落地，未引入运行时依赖；改动代码可以，构建与真机验证由用户自己执行。

## 已完成阶段一览

| 阶段 | 内容 | 关键文件 | 视觉变化 |
|---|---|---|---|
| C | 去网页感：字符箭头→lucide 图标；移除 `min-width:320px` 妥协 | 全站 tsx / `md3-base.css` `app-foundation.css` | 无（仅清理反模式） |
| A | 设计令牌 + 组件规范蓝图（单一事实来源） | `docs/移动端设计令牌与组件规范.md` | 无（文档） |
| P0 | 令牌层：补 `--font-display` / `--cover-*` / `--app-ambient` / `--app-page-bg` / `--app-glow` + 暗色块 + 分类色/状态胶囊 CSS 骨架 | `md3-base.css` `app-foundation.css` | 零（靠 `data-*` 触发，未接属性） |
| P1 | 门面页提质感 | 见下 | 5 项可见变化 |
| P2 | 细节打磨 | 见下 | 5 项可见/收口变化 |
| P3 | 深色封面变体（收尾） | `app-foundation.css` | 暗色模式兜底封面统一 |

## P1 门面页提质感（可见）
- **P1-1 页面纸感背景**：`.mobile-shell` 末层改 `var(--app-ambient), var(--app-page-bg)`（`release-overrides.css`）。
- **P1-2 动态分类色封面（核心）**：`CoverTone` 类型 + `MobileCategory.coverTone`；分类管理页 6 色点选择器；`BookTile`/`HomePage` 传 `coverTone` → `data-category` + `.cover-progress`。
- **P1-3 手写体 display**：统计大数字 + 区块标题 `h2` 用 `--font-display`（`stats-page.css` `release-overrides.css`）。
- **P1-4 状态胶囊语义化**：`.status-chip[data-tone=ready|cloud|error]`（`BookTile` 接入）。
- **P1-5 统计铜渐变**：`.stats-trend-bar` 暖铜纵向渐变。

## P2 细节打磨
- **P2-1 底部导航毛玻璃**：核查末层已实现，无需改动。
- **P2-2 Chip 统一**：抽 `.chip` 基类 + `data-tone` 分类色；`filter-chip`/`shelf-chip` 精简为差异。
- **P2-3 进度条令牌化**：`--app-progress-*` 全局令牌；`cover-progress`/`stats-status-track` 引用令牌。
- **P2-4 空态插画**：`EmptyIllustration.tsx` 暖铜线条书+星；接入书架/灵感/统计空态。
- **P2-5 阅读器走纸感**：章节标题间距加大 + 章节首段首字缩进 2em；默认暖光模式加暖纸底（排除所有护眼/深色，避免误伤可读性）。

## P3 深色封面变体（收尾）
- **现状**：§5 暗色 `--cover-*` 令牌（P0 已建，6 类全齐）与 `.book-cover[data-category]` 映射早已就位 → 带属性的封面暗色模式自动正确。
- **缺口**：2 个渲染点未传 `data-category`，吃基础 `.book-cover` 的硬编码浅米色兜底 → 暗色下是浅色方块：
  - `BookDetailSheet.tsx` `.detail-cover`（无图兜底）
  - `ReadingAndNotesPage.tsx` `.mini-cover`（阅读笔记 mini 封面）
- **修复**：`app-foundation.css` 新增
  ```css
  :root[data-mobile-theme="dark"] .book-cover:not([data-category]) {
    background: var(--cover-default);
  }
  ```
  `:not([data-category])` 限定，绝不影响 6 个分类色封面；浅色模式零改动。

## 验证方式（用户执行）
```bash
cd mobile && npm run dev        # 或 npx capacitor sync android
```
核对清单：门面/书架/统计/详情/阅读器/空态在浅色与暗色模式下封面统一，无浅色方块、无闪白；底部导航毛玻璃；空态插画；阅读器章节呼吸感。

## 可选后续（未做）
- 把真实 `coverTone` 透传到 `BookDetailSheet` 与 `ReadingAndNotesPage` 兜底封面，使其按分类上色而非统一 default 铜。
- 首页 hero 文案 / 阅读器沉浸质感（原 P2 候选，本轮未做）。
