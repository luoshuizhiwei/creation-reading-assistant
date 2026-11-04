# 阶段 E：M-BUG-03 来源更新确认隔离候选收尾（2026-09-30）

> 本报告接手已在 E 盘建立但未落报告的候选。共享恢复仓库 HEAD `513891d6a66a9b78e072ba759d34ed580d377ecf`，暂存区空。产品源码、测试与公共配置均未在共享仓库修改；所有补丁、夹具修正和构建在 `E:\creation-reading-assistant\stage-e-mbug03-isolated-20260929\`。

## 三个候选的真实结果

| 候选 | 基线与差异 | 主/测试编译 | 测试原始证据 | 结论 |
|---|---|---|---|---|
| A `A-head-baseline` | HEAD + 仅 M-BUG-03 精确提取 | `A-01-compile.log` EXIT 0 | `_evidence/A-test-xml` 5 份 XML，68 tests、0 fail/error；`A-02-tests.log` EXIT 0 | M-BUG-03 在 HEAD 上独立定向通过 |
| B `B-joint-baseline` | 已冻结 B1+B2+库浏览器联合候选 + 同一 M-BUG-03 增量 | `B-01-compile.log` EXIT 0 | `_evidence/B-test-xml` 15 份 XML，221 tests、0 fail/error；`B-02-tests.log` EXIT 0 | 与上一联合候选在此定向集合兼容 |
| C `C-joint-plus-b1-coordinator` | B + `ShelfImporter` 的 B1 coordinator 接缝及两处测试构造参数 | 初次 `C-01-compile.log` EXIT 1（两个测试构造器缺参）；修正后 `C-05-compile-codex.log` EXIT 0 | `_evidence/C-04-tests-codex-retry.log` EXIT 0；候选内 5 份 XML，68 tests、0 fail/error/skip | 接缝在 5 套件上通过；未重跑 B 的全部 221 项 |

C 的最小测试修正仅在两个 `ShelfImporter(...)` 构造中加入 `indexRefreshCoordinator = mockk(relaxed = true)`，不改断言；修正后 `ShelfImporterTest.kt` SHA-256 `afc60d289368ea04da4e83c949ebfb950bcc04fc6c247285fa409de2083d46e9`，`ShelfViewModelTest.kt` SHA-256 `bc1c9e7b377685482b2c55e5f2780a0e4ab5d99e076884dea84fc4674cb77612`。`_evidence/C-b1-coordinator-seam-codex.patch` 为 3,414 字节、SHA-256 `7501d51bf7022ead6e31a41a48197c8336364a70507bf5537`，对 B 候选 `git apply --check` EXIT 0。M-BUG-03 最小增量补丁 `_evidence/stage-e-mbug03-minimal-delta.patch` 为 89,588 字节、SHA-256 `9adf5025115fb1d949a06195d2e74bde5e2a053506ec4345cb616eff9dea7241`；新增文件另由 `_evidence/candidateA-delta.csv` 与 `mbug03-closure-sha256.csv` 列出。

## 资源故障与处理

第一次 C 重跑在测试进程启动时遭 Windows 虚拟内存不足（`Native memory allocation ... failed`），第二次甚至在 JVM 启动时失败；两次均不能解释为产品测试失败。核对 PID、命令行和 CPU 空闲后，只结束本轮**已完成**的 S06 与联合候选设备验证所留下的四个 E 盘 Gradle/Kotlin 进程，未结束 D 盘或其他应用进程。释放后单 worker、E 盘 `GRADLE_USER_HOME` 重跑，C 的 5 套件 68/68，编译确认 EXIT 0。失败和成功日志分别保留为 `C-02-compile-and-tests-codex.log`、`C-03-tests-codex-lowmem.log`、`C-04-tests-codex-retry.log`、`C-05-compile-codex.log`。

## 归属与未验边界

- `ShelfSourceUpdateChecker.kt` 及其测试在 376 行总矩阵中标 `EXCLUDED`，但本候选必须纳入；180 行 HOLD 表未列这两个路径。H180 `ShelfSourceRefreshFeedbackTest.kt` 标 B2，其测试实际调用 M-BUG-03 来源刷新汇总函数，归属应更正。此处只记录勘误，**没有改写 CSV**。
- 2026-09-30 已另存三行勘误清单 `E:\creation-reading-assistant\stage-e-incremental-20260929\matrix-corrections-20260930.csv`，SHA-256 `dd3597e33d61b0ffc51fa7ce4e93c21b413536cb12fd9d168645436ffa4af8ad`；原 376/180 行矩阵均未覆盖写入，供下一版权威矩阵逐项合并。
- A/B/C 的证据限于编译和 JVM。来源 SAF 授权、实际来源文件更新、UI 确认与 MuMu 端到端行为均 **NOT RUN**。C 只有 68 项定向结果，不能宣称 221 项全量合流通过。后续提交未获授权。
- A/B 原窗口构建复用了 D 盘 Gradle 缓存；Codex 的 C 补验改用 E 盘现有缓存。新构建继续优先 E 盘。

## 交接

本轮要求的 S06 报告与联合候选 MuMu 报告均已落盘，Codex 将另写总复核。M-BUG-03 若要进入提交批次，先复算完整文件/hunk 清单与 H180/两个 `EXCLUDED` 勘误，再对 C 完整联合范围做相称回归和 MuMu 限定行为验收；目前只可称隔离编译/JVM 定向通过。
