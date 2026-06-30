# 交互质感重构 Implementation Plan

日期：2026-06-30

## Summary

基于“方案 C”做全局交互系统重构：统一 Toast、确认框、页面过渡、卡片动效、阅读提示和搜索弹窗动效，移除原生弹窗，提升应用整体流畅度。

## Phases

### 1. 验证守门脚本

- 新增 `scripts/verify-interaction-polish.mjs`。
- 检查原生弹窗残留、全局交互组件、关键动效类、App 接入点。
- 将 `verify:interaction-polish` 加入 `package.json` 和 `scripts/beta-check.mjs`。

### 2. 全局交互基础设施

- 新增 `src/stores/ui-store.ts`。
- 新增 `src/components/interaction.tsx`，包含：
  - `ToastCenter`
  - `ConfirmDialog`
  - `PageTransition`
  - `AnimatedPanel`
  - `InlineNotice`
- 在 `src/styles.css` 中添加动效 keyframes、motion 类、reduced-motion 兜底。

### 3. App 根级接入

- 在 `App.tsx` 挂载 Toast / Confirm。
- 全局错误从顶部红条改为 Toast。
- 所有 screen 内容包裹 `PageTransition`。
- 启动恢复提示使用应用内 motion dialog。

### 4. 替换原生弹窗

- `useLibraryActions.ts`：移除书籍确认改为 `confirmAction`。
- `SettingsPage.tsx`：数据目录迁移、书籍目录迁移、恢复数据改为 `confirmAction`。
- 灵感删除改为应用内确认。

### 5. 页面质感打磨

- 首页主模块、搜索区、快捷入口使用 motion-card。
- 书库列表容器和书行增加 hover/transition。
- 灵感详情和 AI 候选版本面板动画化；保存/AI/删除提供 Toast。
- 设置页 Section 使用 `AnimatedPanel`；消息提示统一 `InlineNotice`。
- 搜索面板加 `motion-dialog`；结果项增加 hover 位移和阴影。
- 阅读器“记为灵感”增加 Toast；EPUB 设置抽屉加 `motion-drawer`。

### 6. 验证与发布

- 运行：
  - `npm run verify:interaction-polish`
  - `npm run build`
  - `npm run verify:beta`
  - `npm run dist:beta`
  - `npm run verify:beta:release`
- 对打包 exe 做启动烟测。
- 更新 `BETA_CHECKLIST.md` 和 Obsidian Codex 记忆库。

## Acceptance Criteria

- 应用内没有原生确认/警告/输入弹窗。
- 危险操作和迁移恢复都走统一确认框。
- 成功、失败、AI、导入、阅读记录等反馈统一走 Toast 或 InlineNotice。
- 页面切换、搜索弹窗、设置抽屉、面板入场有一致动效。
- `prefers-reduced-motion` 下动画会被压缩或禁用。
- `npm run verify:interaction-polish` 通过，并纳入 beta 总闸门。
