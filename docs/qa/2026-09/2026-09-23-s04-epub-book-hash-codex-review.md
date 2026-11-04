# S04 EPUB Book hash 修复候选 Codex 双轴回读（2026-09-23）

来源：[Zcode stage-p 报告](../../coordination/legacy-stages/undated/agent-stage-p-s04-epub-book-hash-fix.md)、当前 `EpubRepository.kt` 与新增 `EpubRepositoryReplaceTest.kt`。固定 Git 基线 HEAD 为 `5aed5939600e491843e3c44453921bd6273a233c`，本轮没有新提交；工作区既有改动很多，故只审查报告标识的 EPUB 局部 hunk，不把 `git diff HEAD` 全部归因给本轮。规范来源为仓库 AGENTS.md；需求来源为 S04 总账与上轮 Codex 回读。

## Standards

- **P2 测试可误通过**：`replaceStoredEpub preserves id and reading records and does not write new hash on failure` 在 `try` 内调用 `fail(...)`，却用 `catch (Throwable)` 捕获它；`AssertionError` 带有消息，可使后续 `assertTrue(expected.message != null ...)` 成立，即使待测方法意外成功也可能通过。旧副本检查仅比较 `oldTargetBytes.size` 与新读取字节数，不能证明逐字节不变；`readingProgressDao` 虽构造但没有任何查询或断言。因此测试名和报告中的“回滚保护/关联阅读记录均已验证”超出测试实质。
- `git diff --check HEAD -- EpubRepository.kt EpubRepositoryReplaceTest.kt` 退出码 0；无其他本片明确格式问题。**Standards：1 项 P2。**

## Spec

- 正常路径在新内部副本通过解析后计算 MD5，再写入 `stored.copy(content_hash = ...)`；>32 MiB 返回 null 清旧 hash。新增小/大文件测试对旧 hash 有辨别力。Codex 新鲜运行 `:app:testDebugUnitTest --tests com.creationreadingassistant.feature.reader.EpubRepositoryReplaceTest --no-parallel`：退出码 0，XML 为 3 tests / 0 failures / 0 errors / 0 skipped；当前 `ShelfImporterTest` XML 36/36 为 Zcode 较早产物，本轮未再重跑。Zcode 报告的旧代码红灯 2/3 因后续 Gradle 运行覆盖 XML，当前无法独立回读原始红灯产物，仅作报告记录。
- **P2 小文件哈希失败仍可写库**：新增 `computeFileHash` 对所有读取异常使用 `runCatching(...).getOrNull()`。合法、已替换的 ≤32 MiB EPUB 如果哈希读取失败，方法返回 null 而不是抛错；`replaceStoredEpub` 随后仍以 `content_hash=null` 更新 Book、删除备份并返回成功。这违反本片“小文件成功后 Book hash 等于内部副本 MD5；失败时保持旧副本/元数据”的合同。大文件按策略返回 null 是另一条合法分支，应与小文件 I/O 失败区分。
- 损坏 EPUB 用例在首次解析时就失败，旧副本尚未进入备份/替换阶段；它不能证明**替换后**异常时 backup 回滚。真实 Room、设备 EPUB 刷新和崩溃/断电原子性也不在当前证据内。**Spec：1 项 P2，另有未覆盖边界。**

## 裁决

**EPUB 正常哈希路径已有可运行候选，但 stage-p 不放行真机验收，S04 仍 PARTIAL。** 下一步只修小文件哈希 I/O 失败语义与上述失效测试，补真正发生在替换后的可控失败/旧副本逐字节校验；定向 JVM 新鲜通过后再交独立真机验收。不要重复 TXT V4 或碰用户真实 EPUB。

汇总：Standards 1 项，最严重 P2；Spec 1 项，最严重 P2。
