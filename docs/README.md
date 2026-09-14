# 文档索引

更新日期：2026-09-13

本仓库包含桌面端、独立原生 Android（当前移动端主线 `android/`）。旧 Capacitor `mobile/` 产品线已于 2026-07-30 删除，其许可证与上游存档见 `archives/frozen-mobile/`；历史文档里提到的 `mobile/` 是指该已删除产品线，不能当作当前 `android/` 的实现依据。

## 当前执行依据

| 文档 | 作用 |
|---|---|
| [`../AGENTS.md`](../AGENTS.md) | 产品线边界、构建命令和真机验证约束 |
| [`handoff/current.md`](handoff/current.md) | 当前 Agent 单一交接入口：活动主线、工作区边界、下一步、验证命令与 desktop 停放项 |
| [`plans/2026-09-12-android-library-folder-smart-recognition-roadmap.md`](plans/2026-09-12-android-library-folder-smart-recognition-roadmap.md) | android/native 应用内书籍目录路线（第 1–3 组已接线并真机验收 PASS；第 4 组阅读器优化未启动）；含 R3 产品决定与拒绝项清单 |
| [`plans/2026-09-13-android-wip-integration-plan.md`](plans/2026-09-13-android-wip-integration-plan.md) | Android/native 当前未提交 WIP 的分组、共享 seam、集成顺序和提交前证据要求；切片 1 已审阅待拆分提交、切片 3/4 已收口待审、切片 5 调用图已审计 |
| [`plans/2026-09-13-android-r3-slice1-integration-manifest.md`](plans/2026-09-13-android-r3-slice1-integration-manifest.md) | Android/native 切片 1（R3 来源目录与索引）的文件边界、共享 Room migration 约束、审阅结论和定向验证记录 |
| [`plans/2026-09-13-android-r3-slice1-review.md`](plans/2026-09-13-android-r3-slice1-review.md) | 切片 1（R3）完整只读 diff 审阅：候选/排除文件清单、混合文件 hunk 级归属、migration 依赖顺序（12→13→14）、seam 调用链、定向测试与风险（含 `library_source_refs` 备份导出缺口待决议） |
| [`plans/2026-09-13-android-shared-seams-audit.md`](plans/2026-09-13-android-shared-seams-audit.md) | 共享设置/搜索/同步/安全的只读调用图审计：`data.settings` 主体归属 Reader，切片 5 须按新拆片设计执行，仍禁止直接提交 |
| [`plans/2026-09-10-desktop-global-card-library-requirements.md`](plans/2026-09-10-desktop-global-card-library-requirements.md) | desktop 创作助手 v2.0 当前需求：全局世界观卡片库、写作速查、v9→v10 迁移、后续写作/AI/校对切片与待决策项 |
| [`plans/2026-09-10-desktop-global-card-library-migration-preflight.md`](plans/2026-09-10-desktop-global-card-library-migration-preflight.md) | desktop v9→v10 全局卡片库的 M0.4 技术预检：已锁定基线与备份证据、迁移 Module seam 和必须确认的四项产品输入 |
| [`plans/2026-08-09-desktop-creation-workbench-phase-1-spec.md`](plans/2026-08-09-desktop-creation-workbench-phase-1-spec.md) | desktop 创作工作台第一阶段的正式功能规格、迁移要求、验收矩阵和实施切片 |
| [`architecture/desktop-creation-current.md`](architecture/desktop-creation-current.md) | desktop 当前模块边界、SQLite/IPC 调用链、关键一致性规则与验证入口 |
| [`architecture/desktop-creation-migration-audit-contract.md`](architecture/desktop-creation-migration-audit-contract.md) | desktop 旧数据只读审计、隐私边界与迁移分级 |
| [`architecture/desktop-creation-adapter-decision.md`](architecture/desktop-creation-adapter-decision.md) | desktop 创作适配层的边界与技术决策 |
| [`architecture/native-android-reader.md`](architecture/native-android-reader.md) | 当前独立原生 Android 阅读器架构与已知边界 |
| [`architecture/android-code-quality-and-evolution-guide.md`](architecture/android-code-quality-and-evolution-guide.md) | 原生 Android 代码质量、架构解耦、Compose 性能与演进规范指南 |
| [`testing/native-android-gap-audit-2026-07-29.md`](testing/native-android-gap-audit-2026-07-29.md) | 可直接分派给其他 agent 的缺口与验收标准；P0 已全部收口（2026-08-16），剩余为真机复核、EPUB 语料/基线、桌面门禁与同步冒烟 |
| [`plans/legado-feature-backlog.md`](plans/legado-feature-backlog.md) | 阅读体验增强候选；不是已承诺路线图 |
| [`plans/2026-08-16-android-followup-roadmap.md`](plans/2026-08-16-android-followup-roadmap.md) | 现行后续计划：收口与真机验证 → 首次发版 → 质量门禁 → 功能深化 |
| [`qa/android-structure-cleanup-2026-09-08.md`](qa/android-structure-cleanup-2026-09-08.md) | 当前 Android 死代码清理证据、删除测试方法与剩余结构/API 债 |
| [`plans/2026-08-16-reading-goal-streak-design.md`](plans/2026-08-16-reading-goal-streak-design.md) | P3.2 阅读目标 + 连续阅读 + 提醒设计（片 0–1 会话写入与统计口径统一已完成；片 2–3 待决策/实施） |
| [`plans/2026-08-16-toc-read-mark-manual-sort-design.md`](plans/2026-08-16-toc-read-mark-manual-sort-design.md) | P3.3 目录已读标记 + 分类/标签/书单手动排序（已完成并真机验收） |
| [`plans/replace-rules-render-integration-design.md`](plans/replace-rules-render-integration-design.md) | 替换净化规则接入正文渲染：小型 TXT + 新分页引擎及不支持路径能力边界已接线，流式/旧渲染及其他格式仍待实施与带书真机验收 |
| [`plans/2026-09-03-desktop-toc-upgrade-design.md`](plans/2026-09-03-desktop-toc-upgrade-design.md) | desktop 阅读目录升级：目录/设置分离 + 共享 ReaderSidePanel（折叠/搜索/高亮跟随/已读标记）+ TXT/MD 识别对齐 + 进度按章锚定 + TXT 目录手动修正——P0/P1 均已实施（§9/§10） |
| [`WorkBuddy/theme_visual_plan.md`](WorkBuddy/theme_visual_plan.md) | 已冻结并真机验收的主题视觉规范 |
| [`GITHUB_RELEASE_PROCESS.md`](GITHUB_RELEASE_PROCESS.md) | 现有桌面端发布流程；旧 Capacitor APK 发布已在 P0-A2 退役，原生 Android 发布（P0-A3）见 [`release/ANDROID_RELEASE.md`](release/ANDROID_RELEASE.md)（2026-08-16 已收口，`android-v*` tag 流） |
| [`release/ANDROID_RELEASE.md`](release/ANDROID_RELEASE.md) | 原生 Android 发布操作指南：签名、版本注入、GPL 源码包、检查更新 |

## 历史或参考资料

以下内容保留用于追溯设计与故障，不代表当前代码状态。**2026-09-14 起，历史存档类文档已集中移动到 [`archive/`](archive/README.md)**（按来源分四个子目录：`desktop-reports/` 根目录一次性交付报告与接力交接、`mobile-legacy/` 已删除 Capacitor 产品线的审查与回归快照、`theme-process/` 主题方案形成过程稿、`superpowers/` 2026-06 阶段计划），每类归档原因见 [`archive/README.md`](archive/README.md)。仍留在原位的历史资料：

- `plans/2026-07-26-native-android-stabilization-plan.md`、`plans/p4-epub-pager.md`：已完成的 Android 稳定化/EPUB 翻页阶段计划与实施记录，被后续阅读内核实现替代。
- `plans/backlog-04-eye-care-filter-design.md`、`plans/backlog-09-txt-toc-rules-design.md`：已实施功能的备料设计稿（护眼色温滤镜、TXT 目录内置规则），对应 `legado-feature-backlog.md` 第 4/9 项。
- `architecture/reader-engine-v2.md`：2026-07-16 的 Capacitor/epub.js V2 方案。
- `architecture/desktop-creation-workspace-contract.md`、`architecture/desktop-creation-structure-contract.md`：desktop 切片 2/6 的历史契约快照；当前完整能力面以 `desktop-creation-current.md` 为准。
- `architecture/legado-reader-adoption.md`：2026-07-19 的 Capacitor + `archives/frozen-mobile/legado-reader-core` 方案（上游来源见该存档）。
- `code-review/2026-07-21-native-reader-review.md`：旧 `mobile/` 产品线的审查快照。
- `research/novalist-desktop-adoption-2026-08-21.md`、`research/legado-with-md3-analysis.md`：desktop 取舍与上游调研资料，不是本项目实现说明，也不是已承诺路线图。
- `perf/reader-fluency-baseline-2026-09-06.md`：阅读流畅度基线存档。
- `research/novalist-desktop-adoption-2026-08-21.md`：desktop 创作雷达与 AI 上下文候选的取舍记录；不是当前 Android 任务，也不是已承诺路线图。

## 维护规则

- 新文档标题或开头必须写清产品线：`desktop`、`android/native`（`mobile/Capacitor` 仅用于历史文档标注，该产品线已删除，不指向当前实现）。
- 阶段计划完成后补“完成/被替代”状态；不要继续作为下一位 agent 的入口。
- 当前路径、命令和验收口径只在 `AGENTS.md`、本索引和当前架构文档维护，避免复制出多个真源。
- 测试记录使用“测试 EPUB”“测试 TXT”等中性名称，不记录真实测试书名。
