# S04 EPUB 回滚链 · 终审代码交付包

- **日期**：2026-09-27
- **仓库**：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`（已核实）
- **前置文档**：`2026-09-27-s04-epub-rollback-safety-fix.md`（修复实现）、`2026-09-27-s04-epub-rollback-exception-surface-review.md`（独立异常面审查）
- **所有权**：本轮仅改 `EpubRepository.kt`、`EpubRepositoryReplaceTest.kt`、safety-fix 报告与本报告；未 commit / push / reset / checkout；未碰真机、书库、原始仓库
- **结论**：**四项事项全部完成，定向 JVM 测试一次通过：`tests="21" failures="0" errors="0"`**（20 既有 + 1 新增）

---

## 1. 恢复失败接入应用诊断记录（AppLog）

`EpubRepository.restoreBackupAfterFailedPromotion` 的 `onFailure` 中，在 `addSuppressed` **之后**追加：

```kotlin
runCatching {
    AppLog.w(
        "EpubRepository",
        "EPUB 回滚恢复未完成(${restoreFailure::class.simpleName ?: "Throwable"})：" +
            "完整旧字节已保留在备份文件中，后续替换会被残留守卫拒绝",
    )
}
```

约束逐条落实：

- **不记录敏感内容**：消息为固定描述 + 失败类别（异常类简单名，非异常消息）；不含书名、文件名（含 bookId/backup 文件名）、路径、正文、异常消息。新用例有负断言钉住「不得携带 bookId」。
- **记录失败不破坏既有不变式**：先 `addSuppressed` 再写日志；日志调用包在 `runCatching` 里——即使记录动作抛出，也不顶替 DAO 主异常（catch 末尾仍 `throw error`）、不阻断 suppressed 挂载、不触碰 backup（`backup.delete()` 在此之前已完成或未执行）。
- **设施安全性**：`AppLog.push` 为纯 JVM 实现（`LocalDateTime`/`UUID`/`synchronized` + `MutableStateFlow<PersistentList>`），无 Android API、无可抛路径；`AppLog.w` 写入「日志与诊断」页可见的 WARN 环形缓冲（上限 500 条）。

## 2. 面向调用方的异常链断言（新用例 1 条）

新增 `replaceStoredEpub surfaces restore failure through the caught exception chain`（复用 `seedRestoreFailure` 骨架：DAO 异常 + 恢复 rename=false + 恢复复制半途抛 IOException）。断言全部基于**实际捕获的异常及其 cause 链**（`generateSequence(caught) { it.cause }`，限深 16 防环）：

1. 原始 DAO 异常（类型 + 本用例独有消息）在链上**可寻得**——不做对象身份/位置固定断言（同实例与「副本+cause」两种协程恢复形态下都通过）；
2. 恢复失败（IOException，按类型 + 消息匹配）在链上**某异常的 suppressed** 中寻得——不是只检查注入的原始异常实例；
3. AppLog 环形缓冲出现本次的 `EpubRepository`/`EPUB 回滚恢复未完成` 记录（按调用前快照 `drop(sizeBefore)` 取增量），且不含 bookId；
4. 数据安全不变式保持：完整 backup 逐字节保留、DB 零写入、内存缓存清空；正向证据（rename 尝试 ≥1、恢复复制恰好 1 次）防假绿。

设计依据见异常面审查报告 §1：协程 1.10.2 恢复开启时副本 suppressed 为空、原实例（挂 suppressed）位于其 cause 链；生产 Android 不复制、捕获即原实例。本用例对两种形态都成立，未来协程升级或派发器调整导致的形态翻转不会使其失效。

## 3. 过度表述修正

- **`EpubRepository.kt` 恢复段 KDoc**：删除「由调用方/用户提示层决定后续处置」，改为如实表述——suppressed 是尽力而为的附加信息；当前生产调用方（`ShelfImporter.repairFileTyped`）只读取异常 message 做任务态展示，不依据 suppressed 作出用户提示决策；异常跨 `withContext` 且协程栈回溯恢复开启（JVM 断言开启或显式 `kotlinx.coroutines.debug`，生产 Android 默认关闭）时，调用方捕获的是恢复后副本（suppressed 为空），携带 suppressed 的原实例位于其 cause 链；调用层当前对回滚状态的可靠判据是磁盘残留（入口守卫），不是异常对象。
- **safety-fix 报告 §2.1**：追加勘误块——「副本不携带 suppressed」机制在恢复激活路径属实，但对本测试自身配置（runTest + Unconfined）实测不触发复制、捕获即原实例，该环境未复现「caught.suppressed 为空」；副本以原实例为 cause，信息仍可经 cause 链取回；断言位置选择不受影响。
- **safety-fix 报告文末**新增「§7 勘误与后续」：指向异常面审查与本终审报告；明确真机、并发、低存储路径**仍未验证**，任何报告均不声称已通过。既有 §5 的真机边界声明保持不变。

## 4. 运行记录

### 4.1 命令与退出码

```bash
cd D:/develop/Code/Codex/creation-reading-assistant-recovery-20260919/android
export GRADLE_USER_HOME=D:/develop/env/gradle
./gradlew :app:testDebugUnitTest \
  --tests "com.creationreadingassistant.feature.reader.EpubRepositoryReplaceTest" \
  --init-script E:/creation-reading-assistant/codex-s04-build-20260925/redirect-build-via-junction.gradle \
  --offline > E:/creation-reading-assistant/codex-s04-build-20260925/test-final-code-gate.log 2>&1
# EXIT=0，BUILD SUCCESSFUL in 1m 27s（一次通过，无失败迭代）
```

只跑该测试类的定向 JVM；测试任务自带 main/test 编译检查，未做全量构建。init-script 沿用上一轮既有配置（junction 指向 E 盘 project-builds，与项目同盘根，规避 KSP 跨盘 `different roots`，见 safety-fix 报告 §4.2），本轮未新建、未修改任何重定向配置。

### 4.2 结果证据

| 项 | 值 |
|---|---|
| 测试数 | `tests="21" skipped="0" failures="0" errors="0"`（20 既有 + 1 新增 `…caught exception chain`，XML 中在列） |
| 测试 XML | `D:\develop\Code\Codex\cra-s04-buildlink\app\test-results\testDebugUnitTest\TEST-com.creationreadingassistant.feature.reader.EpubRepositoryReplaceTest.xml`（物理在 E 盘 project-builds，经 junction） |
| XML SHA-256 | `3b0730aec38d21514dad7f57ec1340184faa4a4d7bc8cacc18123f844456d833` |
| 运行日志 | `E:\creation-reading-assistant\codex-s04-build-20260925\test-final-code-gate.log` |
| 日志 SHA-256 | `d4d1854015b94268d9cea4463453ecac6064d71a6f80802c0b06d7e58215c053` |

### 4.3 文件清单

| 文件 | 状态 |
|---|---|
| `android/app/src/main/java/com/creationreadingassistant/feature/reader/EpubRepository.kt` | 修改（onFailure 追加 AppLog 记录 + import；恢复段 KDoc 契约表述修正） |
| `android/app/src/test/java/com/creationreadingassistant/feature/reader/EpubRepositoryReplaceTest.kt` | 修改（import；覆盖清单补一条；新增 1 个用例） |
| `docs/coordination/legacy-stages/2026-09/2026-09-27-s04-epub-rollback-safety-fix.md` | 修改（§2.1 勘误块；文末新增 §7 勘误与后续） |
| `docs/coordination/legacy-stages/2026-09/2026-09-27-s04-epub-rollback-final-code-gate.md` | 新增（本报告） |

git 状态核对：本轮触碰的仅上述 4 个文件；未执行任何 Git 写操作。

---

## 5. 未验证边界（不夸大）

1. **真机**：本片全部为 JVM 单元测试；恢复失败在真机上的实际表现（低存储/跨卷触发 `renameTo` false 的频率、诊断页记录的用户可见效果、`.backup` 残留现场）**未验证，不声称已通过**。建议的专项验证仍按 safety-fix 报告 §5 执行。
2. **并发**：`ShelfImporter` 无互斥、入口守卫存在竞态窗口（异常面审查报告 §2 范围外观察）——本轮未改动、未验证。
3. **低存储 / 磁盘异常路径**：`backup.delete()` 返回 false（L209/L255）导致残留砖住后续替换的路径**无日志、未覆盖**——本轮按任务范围仅在「恢复失败」点接入记录，未扩大；登记为后续切片候选。
4. **catch 内未受保护的三处 delete（L206/L209/L216）** 仍依赖「`File.delete()` 失败返回 false 而非抛出」的 JDK 语义（Android 应用进程无 SecurityManager，抛出路径不可达）——本片未做 `runCatching` 加固，异常面审查报告 §4/§6 的建议保持开放。
5. **AppLog 断言的执行顺序假设**：新用例按「单 worker 顺序执行 + 调用前后快照取增量」断言日志落盘；若未来 Gradle 改为并行 fork / 类内并发执行，该断言可能需要改为仅做存在性检查。
6. **协程版本演进**：异常链断言已设计为两态通用，但若未来 kotlinx.coroutines 改变复制语义（如副本携带 suppressed 或 cause 结构变化），需复核本报告 §1.2 的形态描述。
7. **「生产调用方只读 message」的契约核对**基于当前 `ShelfImporter.repairFileTyped` 实现；未来若调用方开始消费 suppressed/cause 链，`EpubRepository` KDoc 的表述需同步更新。
