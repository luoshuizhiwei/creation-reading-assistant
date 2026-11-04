# 阶段 E：S06、M-BUG-03 与联合 MuMu 三项任务核对（2026-09-30）

## 实际完成状态

| 任务 | 报告 | Codex 独立回读 | 边界 |
|---|---|---|---|
| S06 启动主题最小候选 | [隔离报告](../../legacy-stages/2026-09/2026-09-29-stage-e-s06-boot-isolated-result.md) | E 盘补丁 SHA-256 `238274ef6aae4e7a03ceb8aea318222b3c4279fe56df8cc4b5dbe51719d8df4d` 与报告一致；编译日志 `BUILD SUCCESSFUL`；原始 XML **13/13**、0 fail/error/skip，XML 哈希与报告一致 | 冷启动第一帧颜色、系统深浅模式实际行为 NOT RUN |
| M-BUG-03 来源更新确认 | [补全报告](../../legacy-stages/2026-09/2026-09-29-stage-e-mbug03-isolated-result.md) | 原窗口 A（HEAD 基线）编译/JVM **68/68**，B（冻结联合基线）编译/15 XML **221/221**；Codex 修 C 的两个测试构造参数，修后编译 EXIT 0、5 XML **68/68**。补丁/接缝哈希与原始日志保留 | C 的完整 221 项未重跑；SAF/UI/MuMu 来源更新 NOT RUN；矩阵勘误未回写 |
| B1+B2+库浏览器联合 MuMu | [设备报告](../../../qa/2026-09/2026-09-29-stage-e-b1-b2-library-mumu-acceptance.md) | 原始 instrumentation 日志为 3+5+1+2 = **11/11**；E 盘冷备 tar SHA 与报告一致，53 行前后文件 SHA 清单差异 0；当前 `adb devices` 仅 `emulator-5556`，密度 280，仅 qahost 主/测试包，主 DB SHA `7bd076c60763d9d9a65fa3df990bcd67f2911686cdbcbf4310d37f0588499a28` 与基线一致 | B1 WorkManager、B2 Storage 用户入口、库浏览器 SAF 端到端 NOT RUN；MuMu 不等于实体手机 |

M-BUG-03 额外 C 变体最初因 `ShelfImporterTest` 与 `ShelfViewModelTest` 少传 `indexRefreshCoordinator` 编译失败。最小夹具修复后，首次测试尝试遭 Windows 虚拟内存不足；只结束本轮已完成的 E 盘空闲 Gradle/Kotlin 进程，保留两份故障日志，再以 E 盘缓存、单 worker 重跑通过。没有停止 D 盘或其他应用进程。

## Git 与阶段口径

恢复仓库 HEAD 仍为 `513891d6a66a9b78e072ba759d34ed580d377ecf`，暂存区空。除本轮报告/交接文档，未改共享产品源码与测试，未执行后续 stage/commit/push/reset/checkout，原始仓库未触碰。用户此前仅授权首批 A/B 两个本地提交。阶段 A–E 仍为 **4/5=80%**：本轮三项任务已完成到各自限定验收，但 E 的全量资产/剩余 HOLD 处置、产品选择、最终提交与健康入口尚未收口。

## 接下来

先复核 M-BUG-03 的矩阵勘误与完整合流边界；S06 第一帧可在 qahost 受控环境做限定 MuMu 验收，但 qahost runner 使用隔离 Application，不能冒充生产 `App.kt` 启动链路证据。后续提交须先给出完整精确纳入/排除清单并取得新授权。
