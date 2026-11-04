# 阶段 E 联合候选与集成方案复核（2026-09-29）

## 已确认

- ZCode 在 E 盘构造 HEAD+G0+U+G2+runner 修正的隔离候选；debug androidTest 与 qahost 主/测试构建日志均为 `BUILD SUCCESSFUL`。qahost 原始 instrumentation 日志为 G0 3/3、U 5/5，各用例最终 `STATUS_CODE=0`，无 `stack=`；日志 SHA 分别为 `3344ef1204ab652bc578e4d4f10021d908904139c0ae943d9b4a774d399f6467`、`f79b906a986f51f38dbd12244d17772fd4938883ae76c3ac6937f33eca18d82b`。
- 修正后的 `CraIsolatedTestRunner.kt` SHA 为 `cc5e1fd62fe3e2e256086b1e1f5537a8ebf6a488106e0f51386183cef05d2d09`，只对准确的 `com.creationreadingassistant.qahost` 目标包注入隔离 Application；其他包透传清单 Application。qahost 分支有运行日志，普通 debug 透传分支**设备 NOT RUN**。
- 联合候选在设备现有 v15 库上冷启动未复现此前 v14 候选的 15→14 降级崩溃。报告仅记录进程存活、前台 Activity 和限定时间内无 FATAL；不外推完整业务流。
- 本轮备份与恢复的**持久文件范围**为 53 项（`databases/files/shared_prefs`），前后 SHA 清单完全相同；当前主 DB SHA 为 `7bd076c60763d9d9a65fa3df990bcd67f2911686cdbcbf4310d37f0588499a28`，原 qahost 主/测试 APK 当前 SHA 分别为 `e82904cc9c1315466ed5c521693f93f541560feef038bf074ce7f7cf8324ef8a` / `fdbd019011088505fa37d71565259a0d76f6927407af69148379e2ef7c27038d`，密度为物理 280。tar 包含其他目录，但它们未逐文件对账；不能把“53/53 持久文件恢复”写成“全数据目录逐字节恢复”。

## 集成方案待纠正

- Antigravity 的集成报告写于 ZCode 结束前，因此“联合验收待回读 / 唯一阻塞”已经过时；应以本复核后的状态为准。
- 报告将剩余约 180 行 HOLD 归为 8 个“最小可编译批次”，但这些批次尚无各自 HEAD+精确 hunk 的隔离编译证据，且未展示 180 行到批次的一一映射；当前只能称**候选分组**，不能称已证最小可编译。
- 报告称 65 项原目录代码差异均有意替代，但其 §3.2 七类计数 `11+10+3+4+3+7+18=56`，余下 9 项未在该摘要交代。原语义报告可能包含明细；需要逐行 ID/路径映射后才能得出“65 项全覆盖”的结论。
- 报告引用的 544 条 Git porcelain 为先前时点；本轮默认 `git status --short` 为 **547 条（260 M + 287 ??）**，展开未跟踪文件后为 1519 条。不能把历史 544 或“补齐 81 个未跟踪文件”当作当前完整保护清单；增量快照应按当前实际文件与哈希重算。
- §E 只要求在 stage/commit 前展示精确候选并获明确授权。E 盘增量保护和相称构建验证可直接执行，无需报告额外发明五次逐阶段授权。当前未 stage/commit/换目录。

## 下一步

1. 将有限的 Antigravity Claude 额度用于**只读决策包反证**：核对 65 项完整映射、180 HOLD 分组覆盖与误归属，以及首批原子边界是否真正可提交；输出少量可执行更正，不再重复编译/设备测试。
2. ZCode Flash 处理机械工作：按当前 status/hash 做 E 盘增量保护，生成首批精确补丁/排除清单和当前资产计数；不暂存或提交。
3. 两者报告回读后，由 Codex 给用户展示首批文件/hunk、依赖与剩余资产去向，再按计划 §E 请求 stage/commit 授权。整体 A–E 仍 **4/5 = 80%**。
