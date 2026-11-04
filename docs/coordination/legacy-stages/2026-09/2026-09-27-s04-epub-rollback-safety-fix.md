# S04 EPUB 替换「回滚恢复失败」修复

- **日期**：2026-09-27
- **仓库**：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`
- **前置文档**：`docs/coordination/reviews/2026-09/2026-09-27-s04-epub-rollback-failure-audit.md`（同日只读审计，两处静态可证风险：恢复段异常顶替原异常、恢复不完整仍无条件删 backup）
- **所有权**：本轮仅改 3 个 Kotlin 文件 + 本报告；未 commit / push / reset / checkout；未碰设备、模拟器、正式书库、原始仓库
- **结论**：**已实现并跑通**。定向 JVM `EpubRepositoryReplaceTest`：`tests="20" failures="0" errors="0"`，含 2 个新故障注入用例 + 1 个成功回滚回归

---

## 1. 修复内容

### 1.1 `EpubRepository.kt`（catch 块内 backupReady=true 分支）

回滚恢复从内联代码（原 L205-212）抽为独立私有函数 `restoreBackupAfterFailedPromotion(target, backup, primaryError)`，两条不变式：

1. **原异常不被顶替**：恢复动作整体包在 `runCatching` 里，任何恢复异常经 `primaryError.addSuppressed(...)` 挂载，catch 末尾仍 `throw error`；
2. **完整 backup 只在验证通过后才删除**：
   - `fileOps.renameTo(backup, target)` 成功 ⇒ 直接完成（rename 原子换名，内容不变）；
   - 失败则退回 `fileOps.copyTo(backup, target)`，返回后**先流式核对** target 与 backup 逐字节一致（新增 `contentEquals`，64 KiB 缓冲、遇差异即停、长度不等直接判否、不做整文件驻留内存）；
   - 核对通过才 `backup.delete()`；
   - 核对失败**保留 backup**（完整旧字节仍在磁盘），失败原因挂 suppressed。

辅助：`contentEquals(a, b)` 流式逐字节比较；`companion` 新增 `BUFFER_BYTES = 64 * 1024`。

**未改动**：正常替换路径、backupReady=false 分支、CancellationException 语义（取消仍走回滚后原样重抛，既有注释与行为保留）。

### 1.2 `FileOps.kt`

仅更新两处过时注释：接缝范围从「只路由建立备份阶段」改为「路由建立备份与回滚恢复两个阶段，四处调用共用同一 `renameTo`/`copyTo`，测试按 source/dest 区分注入」。**未新增接口方法、未改 public API**（按审计报告 §4 与用户约束）。`DefaultFileOps` 实现逐字未动。

### 1.3 `EpubRepositoryReplaceTest.kt`

- `FakeFileOps` 增加 `restoreRenameResult` / `restoreCopyBehavior`，按 `source.name.endsWith(".backup")` 区分阶段；默认值 `null` 表示跟随正向行为。
- 新增 3 个用例（§3）。
- 文件头覆盖清单同步更新（原「回滚自身失败未覆盖」条目移入已覆盖；仍保留「长度相同内容不同的损坏备份」与「进程被杀无 fsync」两条未覆盖声明）。

---

## 2. 设计取舍（两个实测发现，均已验证）

### 2.1 恢复异常的 suppressed 断言位置

**不能断言在冒泡异常上**。实测（第一次运行，XML failure `恢复段异常必须以 suppressed 形式保留…实际=` 空列表）证实：`kotlinx.coroutines` 的 stack-trace recovery 跨 `withContext` 复制异常对象时，**副本不携带 suppressed**。这与本测试文件既有的「不能用 `caught === injected` 做身份判定」注释同源、更进一步。

因此 suppressed 断言写在**注入的同一实例**（`daoError.suppressed`）上：`addSuppressed` 发生在 catch 块内、重抛之前，MockK 抛出的正是该实例，所以它上面的 suppressed 就是生产代码写入的结果。冒泡异常仍按既有惯例用「类型 + 本用例独有消息」断言主异常身份。

> **勘误（2026-09-27 终审，详见 `2026-09-27-s04-epub-rollback-exception-surface-review.md`）**：本节「副本不携带 suppressed」的机制在恢复激活路径属实，但 (a) 对本测试自身配置（runTest 外层 + Unconfined 内层）实测**不触发复制**，捕获的即原实例——「caught.suppressed 为空」在本环境未复现；(b) 副本以原实例为 cause，恢复失败信息仍可经 `caught.cause.suppressed` 取回。断言位置的选择不受影响。另需注意：suppressed 只是尽力而为的附加信息，**当前生产调用方只读取异常 message，并不依据 suppressed 作出用户提示决策**。

### 2.2 FakeFileOps 「成功必须真 rename」

第一版 `renameTo` 在恢复阶段直接 `return restoreRenameResult ?: renameResult`——当值为 true 时**返回 true 却不执行真实 rename**。后果：所有成功回滚用例静默失效（`旧 target 必须依然存在` 等 4 个既有/回归用例失败）。修正后无论哪个阶段，「成功」都执行 `source.renameTo(dest)`，`false` 才短路。此 bug 由本轮失败暴露、已修复并由回归用例钉住。

---

## 3. 新增测试用例

| 用例 | 注入 | 断言 |
|---|---|---|
| `keeps full backup when restore copy throws after partial write` | DAO 异常 + 恢复 rename=false + 恢复 copy 写一半后抛 IOException | 主异常 = 原始 DAO 异常（类型+消息）；`daoError.suppressed` 含该 IOException；恢复复制恰好调用 1 次；**backup 存在且逐字节等于旧字节**；DB 零写入；缓存无残留 |
| `keeps full backup when restore copy returns truncated` | 同上，恢复 copy 少写 1 字节后正常返回 | 主异常 = 原始 DAO 异常；`daoError.suppressed` 含「回滚恢复不完整」；**backup 未被删、逐字节完好**（内容核对闸门生效） |
| `still restores target via rename when restore rename succeeds` | DAO 异常，恢复走默认直通 rename | 旧 target 逐字节还原；backup 被消费；恢复复制 0 次（未走 copy 分支） |

公共骨架 `seedRestoreFailure(bookId, partialThen)`：seed 旧 target → 注入 DAO 异常 → 恢复 rename=false → 按 source 注入恢复 copy 故障。

---

## 4. 运行记录

### 4.1 命令与退出码

```bash
cd D:/develop/Code/Codex/creation-reading-assistant-recovery-20260919/android
export GRADLE_USER_HOME=D:/develop/env/gradle
./gradlew :app:testDebugUnitTest \
  --tests "com.creationreadingassistant.feature.reader.EpubRepositoryReplaceTest" \
  --init-script E:/creation-reading-assistant/codex-s04-build-20260925/redirect-build-via-junction.gradle \
  --offline > E:/creation-reading-assistant/codex-s04-build-20260925/test-rollback-safety-fix.log 2>&1
# 最终 EXIT=0，BUILD SUCCESSFUL in 25s
```

只跑该类定向 JVM，未做全量构建。

### 4.2 init-script 说明（偏离与原因）

**用的是 `redirect-build-via-junction.gradle`，不是审计报告里上一轮用过的 `redirect-build.gradle`。**
本轮第一次运行直接用后者即失败（EXIT=1）：`kspDebugKotlin` 内 Hilt 的 `CodeGeneratorImpl.associate`
对生成文件做 `relativeTo(projectDir)`，构建输出在 `E:` 而项目在 `D:` ⇒
`this and base files have different roots`（上一轮能过纯属侥幸——当时 `kspDebugKotlin` 恰好 UP-TO-DATE，
本轮改了 `EpubRepository.kt` 触发真实重跑）。
`-via-junction` 变体把输出指向既有 junction `D:\develop\Code\Codex\cra-s04-buildlink`
（目标 `E:\creation-reading-assistant\codex-s04-build-20260925\project-builds`），与项目同盘根，KSP 正常。
两者都是既有 E 盘配置文件，本轮未新建、未修改任何重定向配置。

### 4.3 中间失败（如实记录，均已修复后复跑）

第一次测试运行 EXIT=1，20 例中 6 失败，归因两处：

- `FakeFileOps` 成功路径不执行真实 rename（§2.2）→ 4 个既有/回归用例失败；
- suppressed 断言写错位置（§2.1）→ 2 个新用例失败。

修复后复跑 EXIT=0、全绿。两次失败日志均已被最终日志覆盖（同一 `$LOG` 路径），
失败现场以上文引用的 XML failure 消息为准。

### 4.4 证据（绝对路径 + SHA-256）

| 项 | 值 |
|---|---|
| 日志 | `E:\creation-reading-assistant\codex-s04-build-20260925\test-rollback-safety-fix.log` |
| 日志 SHA-256 | `a29450813934de590733a2e052f0f096b1e44273b504592356b174871159819b` |
| 测试 XML | `D:\develop\Code\Codex\cra-s04-buildlink\app\test-results\testDebugUnitTest\TEST-com.creationreadingassistant.feature.reader.EpubRepositoryReplaceTest.xml`（物理位于 E 盘 project-builds，经 junction） |
| XML SHA-256 | `262e011978f33af48810e6f6474662498d30ebf1f5af30934f8dc7701946aacd` |
| XML 结果 | `tests="20" skipped="0" failures="0" errors="0"`（03 个新用例见 §3，全部 PASS） |

注：首次尝试的 plain-`redirect-build.gradle` 运行（KSP 报错）与第一次测试运行（6 失败）的原始日志
已被最终成功日志覆盖；失败细节以 §4.2/§4.3 与当轮 XML 摘要为准。

---

## 5. 覆盖边界（不夸大）

**已证明（JVM 定向，确定性注入）**

- 恢复段任何异常不再顶替主异常，主异常（DAO RuntimeException）类型与消息原样冒泡；
- 恢复复制抛错 ⇒ backup 完整保留（逐字节断言）；
- 恢复复制静默截断 ⇒ 内容核对拦下、backup 不被删（逐字节断言）；
- 恢复 rename 成功路径行为不变（回归）；取消路径与既有成功回滚用例全部仍绿（20/20 含全部既有用例）。

**仍未覆盖（不得声称已防护）**

- 「长度相同但内容不同」的损坏：`contentEquals` 逐字节核对**已能拦下**此形态于恢复侧，
  但**建立备份侧**的 `require(长度)` 闸门仍未升级（超出本片范围，保持审计报告登记）；
- `require` 通过后、`target.delete()` 之前进程被杀（无 fsync）；
- 生产数据库在异常边界的真实终态（测试断言仅覆盖 mock 语义：注入点零写入）；
- 真机上 `renameTo` 返回 false 的实际触发条件与频率——**未验证**，本修复不改变其概率，
  只保证失败时不再丢唯一副本、不再吞主异常。

**真机边界**：本片全部为 JVM 单元测试；结论不可替代真机验证。建议后续在真机低存储/跨卷场景
做一次专项验证，重点观察恢复失败时 `.backup` 残留与用户提示。

---

## 6. 文件清单

| 文件 | 状态 |
|---|---|
| `android/app/src/main/java/com/creationreadingassistant/feature/reader/EpubRepository.kt` | 修改（catch 内恢复段抽为 `restoreBackupAfterFailedPromotion` + `contentEquals` + `BUFFER_BYTES`；constructor 注释同步） |
| `android/app/src/main/java/com/creationreadingassistant/feature/reader/FileOps.kt` | 修改（仅两处过时注释；接口与实现零改动） |
| `android/app/src/test/java/com/creationreadingassistant/feature/reader/EpubRepositoryReplaceTest.kt` | 修改（FakeFileOps 按阶段区分注入 + 3 个新用例 + 头注释） |
| `docs/coordination/legacy-stages/2026-09/2026-09-27-s04-epub-rollback-safety-fix.md` | 新增（本报告） |

工作区其他人的修改原样保留；未做任何 Git 写操作。
