# 阶段 E：HOLD 映射与 B1 静态报告回读（2026-09-29）

## 已核实

- `hold-180-row-mapping-updated.csv` 的 SHA-256 为 `ffb5fc2309ccd39d52dcc0488e049c153b61a4a9802c7fad12abfa8e18ad7bf7`；180 行、H001–H180 唯一连续，路径与原 180 行矩阵逐序一致。原表 86 行「未映射」已给出处置；当前 122 行保留 HOLD，58 行为候选或首批已验。候选只是归属假设，未隔离构建。
- 恢复仓库 HEAD 仍为首批 B 提交 `513891d6a66a9b78e072ba759d34ed580d377ecf`；后续 B1 未提交。

## B1 报告勘误与阻塞

- B1 审查 §1.1/§3.1 误称 G0 已包含 `SearchTermDao.kt` 的 S1–S4。实测 `git diff -U0 HEAD -- .../SearchTermDao.kt` 仍有删除 `deleteByChapter`、`countTerms`、`rowsByTerm` 及 KDoc 改写的四组差异，另有 S6 `SearchIndexCoverageDao.distinctBookIds()`。B1 精确候选不能以「此文件只剩 S6」作为隔离依据。
- B1 审查 §4 问题 3 误称 `SearchIndexRepositoryRebuildConcurrencyTest.kt` 不存在。实测文件位于 `android/app/src/test/java/com/creationreadingassistant/data/repository/`，Git 状态为未跟踪；先前检查用了错误目录。须检查测试内容与候选依赖，再决定纳入/排除。
- B1 审查 §2.2 写「3 个 hunk」却列出四个，属于计数笔误。
- B1 所列 `BookDao.activeIds()` 为源码级硬依赖，但所列最小候选尚未实际隔离编译；`ShelfImporter.kt` 的混合 hunk 也未证明可安全分割。不得据静态报告宣称 B1 可提交。

## 下一步

由一个 Claude 窗口按上述勘误重做 B1 精确补丁与依赖审查；在 E 盘独立隔离候选执行定向编译和有判别力的 JVM 测试，保留日志、补丁和 SHA，并将报告落在 `docs/coordination/`。不在共享恢复仓库暂存/提交；失败如实记录并停止。另一个窗口可只读核查 122 HOLD 中的 16 个待裁决条目，不并行改 B1 文件。阶段 A–E 仍为 4/5 = 80%。
