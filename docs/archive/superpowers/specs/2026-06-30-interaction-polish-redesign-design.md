# 交互质感重构设计文档

日期：2026-06-30

## 背景

在本地书库与阅读体验修复后，应用的功能主线已经转向“灵感中心 + 本地书库”。但部分页面仍显得生硬：页面切换直接替换、确认操作使用原生弹窗、成功/失败提示分散在页面内部，阅读器和设置页的反馈方式不一致。

本阶段采用方案 C：做一次全局交互系统重构，而不是只给单个页面打补丁。

## 目标

- 所有用户可见的确认、提示、反馈都使用应用内 UI，不再使用 `window.confirm`、`window.alert`、`window.prompt`。
- 首页、书库、灵感中心、设置、统计、搜索、阅读器使用统一的“纸张卡片”动效语言。
- 阅读体验优先：阅读页记录灵感不强制跳转，保存后通过 Toast 和阅读页浮层反馈。
- 为动效提供 `prefers-reduced-motion` 兜底，避免过度动画影响可访问性。
- 新增自动验证，防止后续改动重新引入原生弹窗或绕开统一交互组件。

## 交互组件

新增 `src/stores/ui-store.ts` 和 `src/components/interaction.tsx`：

- `ToastCenter`：集中展示成功、信息、警告、错误。
- `ConfirmDialog`：危险操作和迁移/恢复操作统一确认。
- `PageTransition`：全局页面切换过渡。
- `AnimatedPanel`：设置页、灵感详情等纸张面板入场动画。
- `InlineNotice`：页面内辅助说明和任务状态提示。

## 动效原则

- 动画短、轻、服务状态变化，不制造等待感。
- 页面切换用 180-260ms 的纸张浮入；Toast 用右上滑入；确认框用轻弹入；抽屉用右侧滑入。
- hover 微交互只用于明确“可点击”的卡片、搜索结果、书库行。
- 阅读器主体不做持续动画，避免干扰阅读。

## 页面范围

- `App.tsx`：全局挂载 Toast / Confirm / PageTransition；错误条改为 Toast。
- `StartPage.tsx`：首页主卡片、搜索卡片、快捷入口统一 motion-card。
- `LibraryPage.tsx`：书库容器和书行增加点击反馈。
- `InspirationPage.tsx`：保存、删除、AI 生成增加 Toast；删除走 ConfirmDialog；主面板和候选面板动画化。
- `SettingsPage.tsx`：迁移、恢复改 ConfirmDialog；维护/AI/存储消息改 InlineNotice；设置卡片动画化。
- `ReaderPage.tsx` / `EpubReaderPage.tsx`：记录灵感 Toast + 原位置浮层；EPUB 设置抽屉滑入。
- `SearchPanel.tsx`：搜索弹窗弹入；搜索结果 hover 更明确。

## 验证

新增 `npm run verify:interaction-polish`，检查：

- `src` 中没有原生 `window.confirm/alert/prompt` 或裸 `confirm/alert/prompt`。
- 全局交互组件存在并被 `App.tsx` 接入。
- 动效 CSS 关键帧和类名存在。
- 搜索弹窗、EPUB 抽屉、阅读页 Toast、设置页/书库确认动作存在。

该脚本纳入 `scripts/beta-check.mjs`。
