# 阶段 E：B1 隔离结果与 HOLD 16 行回读（2026-09-29）

- B1 报告纠正了旧报告的 S1–S4 HEAD 状态、并发测试文件位置，并补出 `SearchHitRanker.kt`、`TxtChapterSplitter.kt` 两个编译依赖。隔离目录 `E:\creation-reading-assistant\stage-e-b1-isolated-20260929` 存在，`stage-e-b1-modified.patch` 为 72,623 字节、SHA-256 `9d0c272fb9f114f09b85ea9867d678049244effe743685cba7760c3420de20cb`，与报告一致。抽核隔离 `SearchTermDao.kt`、`BookRepository.kt` SHA 与报告一致。
- 独立读取隔离目录 5 份 `testDebugUnitTest` XML：4 + 13 + 19 + 12 + 27 = **75 tests，0 failures，0 errors**。这支持 B1 在所述隔离候选的定向 JVM 通过；设备/WorkManager 实跑、完整构建及共享仓库精确暂存尚未验证。报告所列 11 文件全量边界本轮只抽核，提交前还需完整 diff/清单复算。
- HOLD 报告列出的 16 个 ID 均在更新矩阵中且 `mapping=保留 HOLD`。其分类为 2 个归属已裁决、8 个仍待拆分/编译证据、6 个涉及 3 组产品决定；「已裁决」不代表已编译。报告为只读，B1 `ShelfImporter` 接缝暂留后续批次。
- 恢复仓库 HEAD 仍为 `513891d6a66a9b78e072ba759d34ed580d377ecf`，暂存区空。B1 后续本地提交未获授权。
- 下一步可并行做 B2 cache-clear 组与库浏览器搜索/选择边界组的 E 盘隔离候选，各自限定文件/hunk 与测试，优先复用 DeepSeek harness / WorkBuddy DeepSeek。Q1–Q3 产品选择在触及相应功能组前再提出。A–E 阶段进度仍为 **4/5 = 80%**。
