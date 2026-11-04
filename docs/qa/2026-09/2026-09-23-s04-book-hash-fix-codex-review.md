# S04 TXT/MD 主书哈希修复 Codex 回读（2026-09-23）

来源：[Zcode 实现报告](../../coordination/legacy-stages/undated/agent-stage-o-s04-book-hash-fix.md)及当前工作区 `ShelfImporter.kt`、`ShelfImporterTest.kt`。固定 Git 基线为 HEAD `5aed5939600e491843e3c44453921bd6273a233c`；工作区有大量既有未提交改动，`HEAD..HEAD` 无新提交，故本轮按报告与两文件局部 hunk 审查，不把整份 `git diff HEAD` 归因给 Zcode。未操作手机、未改产品源码。

## Standards

- 当前新增代码在 `reselectPlainText` 的内部副本落盘后计算 `newBookHash`，在既有 `bookDao.runInTransaction` 的 `existing.copy(...)` 中写入 `content_hash`。复用 `computeFileHash` 与 32 MiB 阈值，没有新增 DAO/schema/Worker 或跨文件产品改动。`git diff --check HEAD --` 两个目标文件退出码 0。
- 新增测试以执行 mock 的事务回调捕获 `bookDao.upsert` 参数；这一点弥补了原有 mock 不执行回调导致的虚假覆盖。未见本片新增的明确规范阻断。**Standards：0 项发现。**

## Spec

- 当前代码对 TXT/MD 小文件把内部新副本 MD5 写入 `books.content_hash`；大于 32 MiB 时清除旧 Book hash。与 `refreshSourceBaseline` 的小文件阈值一致。新增 TXT、MD、Book/source 一致性和格式失败测试，共 4 项。
- 新鲜回读当前 `ShelfImporterTest` XML：36 tests / 0 failures / 0 errors / 0 skipped，产物时间 19:27:43，晚于目标源码和测试文件修改时间；本轮没有重新运行 Gradle。报告明确承认修复与所谓 Red Phase 构建同期发生，因此**不存在旧代码失败的运行证据**；当前断言从代码看能区分旧哈希与新哈希，但不能表述为已完成实测红—绿循环。
- 代码/JVM 证据支持 **TXT/MD 正常小文件刷新候选修复**。大文件置 null 缺专门断言；EPUB `replaceStoredEpub` 的旧哈希风险未修、真实设备上的新 APK 尚未复验。`refreshSourceBaseline` 仍是后续 best-effort 写入，极端失败下 Book/source 两表原子一致性不由本片保证。**Spec：0 项本片新阻断；上述均为未覆盖边界。**

## 裁决

**本片代码/JVM 范围接受，S04 整体仍 PARTIAL。** 下一步由独立 WorkBuddy 仅用现存隔离测试 TXT 做新 APK 的 V4 定向真机复验，比较 `books.content_hash`、`library_source_refs.content_hash` 与 V4 文件 MD5，并检查原有 11 本选定字段及设备设置；不要触碰用户原有书籍。EPUB 与大文件留独立后续片，不混作本次通过。

汇总：Standards 0 项（无最严重项）；Spec 0 项本片新阻断（未覆盖边界：红灯实测、大文件、EPUB、真机与故障原子性）。
