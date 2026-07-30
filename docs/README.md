# 文档索引

更新日期：2026-07-29

本仓库包含桌面端、独立原生 Android（当前移动端主线 `android/`）。旧 Capacitor `mobile/` 产品线已于 2026-07-30 删除，其许可证与上游存档见 `archives/frozen-mobile/`；历史文档里提到的 `mobile/` 是指该已删除产品线，不能当作当前 `android/` 的实现依据。

## 当前执行依据

| 文档 | 作用 |
|---|---|
| [`../AGENTS.md`](../AGENTS.md) | 产品线边界、构建命令和真机验证约束 |
| [`architecture/native-android-reader.md`](architecture/native-android-reader.md) | 当前独立原生 Android 阅读器架构与已知边界 |
| [`testing/native-android-gap-audit-2026-07-29.md`](testing/native-android-gap-audit-2026-07-29.md) | 可直接分派给其他 agent 的缺口与验收标准 |
| [`plans/legado-feature-backlog.md`](plans/legado-feature-backlog.md) | 阅读体验增强候选；不是已承诺路线图 |
| [`WorkBuddy/theme_visual_plan.md`](WorkBuddy/theme_visual_plan.md) | 已冻结并真机验收的主题视觉规范 |
| [`GITHUB_RELEASE_PROCESS.md`](GITHUB_RELEASE_PROCESS.md) | 现有桌面端发布流程；旧 Capacitor APK 发布已在 P0-A2 退役，原生 Android 发布迁移（P0-A3）尚未完成 |

## 历史或参考资料

以下内容保留用于追溯设计与故障，不代表当前代码状态：

- `architecture/reader-engine-v2.md`：2026-07-16 的 Capacitor/epub.js V2 方案。
- `architecture/legado-reader-adoption.md`：2026-07-19 的 Capacitor + `archives/frozen-mobile/legado-reader-core` 方案（上游来源见该存档）。
- `superpowers/plans/`、`superpowers/specs/`：已执行或被替代的阶段计划。
- `code-review/2026-07-21-native-reader-review.md`、`testing/mobile-*.md`、`testing/native-reader-lazy-epub-regression-2026-07-19.md`、`testing/reader-bug-matrix.md`：旧 `mobile/` 产品线的审查与回归快照。
- 根目录 `BETA_CHECKLIST.md`：v0.1.2 桌面/Capacitor Beta 的历史验收记录。
- `移动端*.md`、`前端优化项目说明文档.md`：旧前端/Capacitor 审查与设计资料。
- `theme_visual_audit.md`、`PRD_add_four_visual_themes.md`、`system_design.md`：主题方案形成过程；最终视觉以 `WorkBuddy/theme_visual_plan.md` 为准。
- `legado-with-md3-analysis.md`、`research/`：上游调研资料，不是本项目实现说明。

## 维护规则

- 新文档标题或开头必须写清产品线：`desktop`、`mobile/Capacitor` 或 `android/native`。
- 阶段计划完成后补“完成/被替代”状态；不要继续作为下一位 agent 的入口。
- 当前路径、命令和验收口径只在 `AGENTS.md`、本索引和当前架构文档维护，避免复制出多个真源。
- 测试记录使用“测试 EPUB”“测试 TXT”等中性名称，不记录真实测试书名。
