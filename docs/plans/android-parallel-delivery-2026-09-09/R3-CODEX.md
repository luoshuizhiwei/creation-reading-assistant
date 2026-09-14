# 发给 Codex：R3 设置持久化/迁移、阅读器接线与集成

> 本文件是 **R3** 轮任务包，替代 R1 的 `CODEX.md`（R1 原文保留作记录，不要覆盖它）。
> 工作目录 `D:/develop/Code/Codex/creation-reading-assistant`。**你同时是本轮的集成者**：
> 各 agent 的 R3 交付由你冻结、集成、串行跑门禁后再交 WorkBuddy 验收。

## 先读

1. 仓库 `AGENTS.md`；
2. `docs/plans/android-parallel-delivery-2026-09-09/{README.md, COMMON.md}`；
3. 本轮任务包 `R3-TRAE.md`、`R3-QODER.md`、`R3-WORKBUDDY.md`；
4. `reports/` 下 R1/R2 全部报告（含 `workbuddy-r2-s1-7/-s1-8` 的索引终验结论）。

## 开工先做

输出 `pwd` / 分支 / `HEAD` / `git status --short`。参考基线 `a9a3bce`（主仓库当前 HEAD），
但**工作区不是干净基线**：桌面端 `electron/**`、`src/**`、`scripts/**`、`android/` 根目录 adb 取证产物、
`.workbuddy-ai/**` 都在场。**未经用户明确要求不得 stage / commit / push / reset / clean / stash**。
如 R1/R2 闭环与缺陷修复由其他 agent 并行进行，先与他们确认共享文件是否正在被改，避免互相覆盖。

## 本轮产品任务

1. **本书级设置的权威持久化**：在 `data/settings/SettingsStore.kt` 之上新增**本书级覆盖存储**，
   只保存用户**显式覆盖**的项；读取优先级 `本书覆盖 > 全局 > 结构默认`；清除覆盖回落全局。
   用 DataStore 键空间或新表皆可，但**若用表，必须带 Room 迁移 + `android/app/schemas` 快照 + 迁移测试**，
   并按既有 Room 版本策略升版（现为 v12）。给 Trae/Qoder 最小兼容接口并**通知他们同步明确提交**，
   不允许他们整文件覆盖你的文件。
2. **默认值与预设的结构化来源**：扩展 `data/settings/ReaderDefaultsMigration.kt` 一带，
   让"恢复默认"与"预设"有单一事实来源；迁移既有 legacy 键，**不破坏或静默改写用户已存设置**。
   注意：`traditionalChinese`（繁体显示）与 `ttsTimedStopMinutes`（TTS 定时停止）**已实现**，
   本轮只做覆盖与接线确认，**不要重复实现**。
3. **阅读器接线**：把本书级设置真正注入阅读渲染（字号/行距/段距/页边距/背景/繁简/分页与滚动引擎等）
   与选区工具条动作配置（`ReaderLayerBuilders.kt` 等宿主）。**不得改变 source 坐标语义与阅读进度语义**。
4. **导航**：`ui/navigation/AppNavigation.kt` 增加设置主页 / 预设 / 词典管理路由；
   不得破坏既有 `reader/...` 路由参数（`sourceLocator=` / `navigationMode=` / `highlightId=`），
   临时查阅 LIFO 返回语义保持不变。
5. **词典的共享 part**：`feature/dictionary/**` 由 Qoder 交付；你负责它需要的依赖（构建文件、公共 `strings.xml`、
   必要的 SAF/权限接线）与最终接线，不重复实现词库解析。
6. **接收 SEAM REQUEST**：优先给最小兼容接口，接口向后兼容；给完通知对应 agent，禁止整体覆盖其文件。

## 集成职责与门禁

- 全部 DAO / entity / migration / schema、`AppNavigation.kt`、`ReaderAction`、reader 宿主/投影/layout/规则接线、
  共享设置与构建文件由你唯一维护。
- 合入前**冻结三方写入**，按「数据 → 设置持久化 → reader 接线 → 词典/工具条」顺序集成，每步检查他人改动是否保留。
- 串行执行并记录**真实**结果：`:app:testDebugUnitTest`、`:app:lintDebug`、`:app:assembleDebug`、
  `:app:compileDebugAndroidTestKotlin`；记录退出码、tests/failures/errors/skipped、Lint 条目、APK SHA-256。
- 交付 WorkBuddy 时给出**冻结代码 SHA + 工作树 diff 指纹 + APK 路径与 SHA-256**，验收期间不得继续改同一快照。
- 独立真机验收仍由 WorkBuddy 负责；不在本任务里越俎代庖。
- 更新本轮报告与活动文档；**不把开发检查点当 PASS，不 push**。

## 交付

`docs/plans/android-parallel-delivery-2026-09-09/reports/codex-r3.md`：三方集成状态、基线差异、
迁移与 schema 变更、真实门禁结果、SEAM 处理记录、下一步与未完成项。
另外把 R3 的实际文件所有权冻结结果写回本目录（README 或单独的 `R3-OWNERSHIP.md`），
注明与 R1 表的差异，供 R4 复用。
