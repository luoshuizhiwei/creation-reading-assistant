# N1-repair-v6：统一 ZIP 恢复的冲突义务保护（一次关闭组合重试路径）

## 一、任务编号/版本

- **任务编号/版本**：`N1-repair-v6`
- **性质**：两文件源码修复 + 定向 JVM 验证。**不合流、不 ADB、不安装、不访问正式书库、不暂存/commit/push/reset/checkout。**
- **上游依据**：[N1-v5/G1-v2 独立核查](../../reviews/2026-10/2026-10-02-next-n1v5-g1v2-codex-review.md) §Spec「N1-v5 仍 1P1：冲突保护没有约束全部补偿分支」（`LocalZipBackup.kt:261–282` 的 conflict 只参与 `equalAuthorized`，`isSameFingerprintCandidate` / `isStartedEmptyFingerprint` 仍可单独授权，三条可达路径）
- **状态**：**已交付，交 Codex 独立核查。**

## 二、输入指纹

| 项 | 值 |
|---|---|
| 恢复仓库 | `D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919` |
| 分支 / HEAD | `codex/recover-worktree-20260920` / **`e39a78da9c62f02614207cffb36969a32492eaea`**（开工核对一致，收工未变） |
| 父候选 | `E:\creation-reading-assistant\next-round-n1-repair-v5\android` |
| 完整输入 SHA 来源 | `E:\creation-reading-assistant\n1v5-g1v2-codex-review-20261002\verification.json` |
| **开工核对 `n1_hashes`（4/4 实算一致）** | `LocalZipBackup.kt` `409f380120ade73e04f9b6b610d6696db1a1065424e2fbd3a06e495b90a88ef0`；`LocalZipBackupTest.kt` `618534dc5254ec3f4b6eafebcec0b8eb9fe5669c3eb911b5af7a9323022d99f4`；`LocalZipRestoreStateStore.kt` `167b881a8d5c65fb416af4820fedbf118820762c523d8c298c36e17ac7dfcca4`；`JsonBridge.kt` `f8e455a452e0182d8bad50349b26e58dbdd0dd7a942e2ea09d3d8223570ece37` |
| D 盘脏工作树 | **未**作为父输入（父输入取自 E 盘 v5 候选，逐 SHA 核对） |

## 三、所有权/改动

**唯一仓库写入**：本文件 `docs/coordination/dispatches/2026-10/2026-10-02-next-n1-repair-v6.md`。

**候选树差异（排除 `build/`、`.gradle/`、`.kotlin/`）**：

| 项 | 值 |
|---|---|
| 父文件数 / 候选文件数 | 878 / 878 |
| 父独有 / 候选独有 | 0 / 0 |
| **内容不同** | **2** |
| 相同 | 876 |

| 文件 | 父 SHA-256 | 候选 SHA-256 | 规模 |
|---|---|---|---|
| `app/src/main/java/com/creationreadingassistant/feature/sync/LocalZipBackup.kt` | `409f3801…0a88ef0` | `f141941a628de19660d71875bb34faa5b4833f05f6db7d0025aba774165b298e` | +42/−17 |
| `app/src/test/java/com/creationreadingassistant/feature/sync/LocalZipBackupTest.kt` | `618534dc…022d99f4` | `4d6825b1a97da6e97d4575b4742c169681b6b6eccac7a6dddbbf35771701a707` | +213/−0 |

**未改动抽查（与父逐字节相同）**：`LocalZipRestoreStateStore.kt` `167b881a…`、`JsonBridge.kt` `f8e455a4…`、`ProfileViewModel.kt` `124517d2…`、`app/build.gradle.kts` `ae078ff0…`。**未越界**，未扩架构或其他功能。

### 3.1 根因与统一判定

**根因**（Codex 复核 §Spec）：v5 把冲突只挂在 `equalAuthorized` 上——

```kotlin
val hasConflictingObligation = expectedObligation != null && !isMatchedObligation
val equalAuthorized = isEqualRel && !hasConflictingObligation      // ← 只有这一路受限
val needCompensate = (isMatchedObligation || equalAuthorized ||
                      isSameFingerprintCandidate || isStartedEmptyFingerprint) && ...
```

`isSameFingerprintCandidate`（最近一次指纹相同）与 `isStartedEmptyFingerprint`（空指纹）仍可**单独**授权补偿，于是三条路径可绕过：

1. **A→B→B**：第一次 B 被 equal 守卫挡住，但状态指纹已存成 B、A 义务留在 unresolved；第二次 B 指纹相同且路径属继承义务 → `isSameFingerprintCandidate` 绕过 → 复制 B、剪掉 A、放行导出。
2. **A→坏 ZIP→B**：`begin()` 在解包**之前**执行，坏 ZIP（缺 `backup.json`）留下**空指纹**现场并继承 A 义务；随后 B 经 `isStartedEmptyFingerprint` 绕过。
3. **legacy→B→B**：legacy 长度义务第一次被挡但指纹存成 B；第二次同指纹分支把长度义务替成 B 的 SHA 并清除，**未证明 B 是原来源**。

**状态表（实施前先整理，再落实统一判定）**：

| 继承义务类型 | 源正文是否满足义务 | 当前版本分类 | 是否允许复制 | 义务处置 |
|---|---|---|---|---|
| 无继承义务 | 不适用 | accepted / equal / skipped | 允许（skipped 除外） | 无义务 |
| 可靠 SHA 义务 | **匹配** | 任意 | **允许**（补齐义务） | 由 pendingTargets 接管，验证就位后收敛 |
| 可靠 SHA 义务 | **不匹配** | equal | **禁止** | 保留 unresolvedTargets |
| 可靠 SHA 义务 | **不匹配** | 空指纹（DB 提交后未推进） | **禁止** | 保留 |
| 可靠 SHA 义务 | **不匹配** | 最近一次指纹相同（重复选了最近的包） | **禁止** | 保留 |
| 可靠 SHA 义务 | **不匹配** | 路径相同 / accepted（本地版本较低） | **禁止** | 保留 |
| legacy 长度义务 | **无法证明**（证据不足） | 任意（含同指纹） | **禁止** | 保留，不猜测原来源 |
| 任意义务 | 任意 | strictlyNewer（本地严格较新） | **禁止**（铁律 1，未改动） | 保留 |

**统一判定**（不再给某一个 OR 单独打补丁）：

```kotlin
val obligationSatisfied = obligationSatisfiedBy(srcHash, expectedObligation)  // 唯一判定入口
val hasConflictingObligation = expectedObligation != null && !obligationSatisfied
val sourceAuthorized = !hasConflictingObligation &&
    (expectedObligation != null || isEqualRel ||
        isSameFingerprintCandidate || isStartedEmptyFingerprint)
val needCompensate = sourceAuthorized && (!dstFile.isFile || computeSha256(dstFile) != srcHash)

if (needCompensate) { /* 移出跳过集合，允许复制 */ }
else if (hasConflictingObligation) { /* 显式留在跳过集合：冲突目标不得进入本轮复制集合 */ }
```

四点关键语义：

1. **单一守卫**：冲突存在时，equal / 空指纹 / 同指纹 / 路径相同**四条路径同时失效**。
2. **legacy 不猜测**：`obligationSatisfiedBy` 对 legacy 长度义务恒返回 `false`（长度证据不足以证明正文身份）→ 继续阻断。
3. **显式保护**：冲突目标即使被 merge 判为 `accepted`（本地版本较低）也会被**显式**加回跳过集合，不得进入复制集合，从而不得经 `markDbCommitted` 的 `remainingUnresolved` 剪掉旧义务。
4. **正当补偿保留**：匹配 SHA 的原 A 重试仍走 `expectedObligation != null && obligationSatisfied` 分支，可补齐正文并收敛。

**保留未动**：事务内计划（`onPlanReady`/`markDbCommitted`）、strictly-newer 铁律、finally journal 修复、`deleteRecursively` 返回值检查与异常合并。

## 四、环境

| 项 | 值 |
|---|---|
| 平台 | Windows；Git Bash（MSYS） |
| Gradle | wrapper `gradle-8.9-bin`，`--offline`，`--rerun`（避免构建缓存命中导致未实际执行） |
| `GRADLE_USER_HOME` | `E:\creation-reading-assistant\next-round-n1-repair-v6\_cache\gradle-home`（**自有**，由既有 E 盘缓存 robocopy 复制，2.8 GB；核占用后复用） |
| SDK | `local.properties` → `sdk.dir=D:\develop\Android\Sdk`（自父候选复制） |
| 构建目录 | 全部在候选自有 `android/app/build` |
| 设备 | **零 ADB / 零 MuMu / 零安装 / 零正式书库访问** |

## 五、命令与结果

**统一命令**（工作目录 `E:\…\next-round-n1-repair-v6\android`）：

```
./gradlew.bat :app:testDebugUnitTest --rerun --offline --console=plain \
  --tests "com.creationreadingassistant.feature.sync.LocalZipBackupTest"
```

| 轮次 | 生产代码 | 测试代码 | 退出码 | 结果 |
|---|---|---|---|---|
| **红（v5 基线）** | v5 `409f3801…` | v6 最终版 | **1** | `61 tests completed, 4 failed` |
| **绿（v6 候选）** | v6 `f141941a…` | v6 最终版 | **0** | `BUILD SUCCESSFUL`，**61 tests / 0 failures / 0 errors / 0 skipped** |

**红测 4 条失败原因（全部为 ComparisonFailure，精确命中冲突绕过）**：

| 用例 | 失败断言 |
|---|---|
| `N1v6_C1_aToBToBDoesNotWriteConflictingContentOrDischargeObligation` | 第二次 B 同样不得写入冲突正文 expected:`ORIGINAL` but was:`B CONTENT` |
| `N1v6_C2_emptyFingerprintFromBadZipDoesNotDischargeObligation` | 空指纹不得授权写入 B 的冲突正文 expected:`ORIGINAL` but was:`B CONTENT` |
| `N1v6_C3_legacyLengthObligationIsNotReplacedByRepeatedB` | 长度义务不得授权写入 B 的正文 expected:`ORIGINAL` but was:`B CONTENT` |
| `N1v6_C4_reselectingMatchingAConvergesAfterBlockedRetries` | B 不得写入 expected:`ORIGINAL` but was:`B CONTENT` |

**只跑 `LocalZipBackupTest`**；未跑全量 / Lint / Release / 设备。

### 5.1 四条组合路径的断言强度

每条都断言**正文实际字节 + 原义务内容 + 状态 + 导出阻断**，不只断言「状态文件还在」：

| 用例 | 覆盖的组合路径 | 关键断言 |
|---|---|---|
| `N1v6_C1` | A 复制中断 → B → **再选同一 B** | 两次 B 后 `dst` 字节仍为 `ORIGINAL`；`obligationOf(state)` 与 A 中断后**逐字相等**（未被 B 的 SHA 替换）；`isComplete == false`；导出**因未完成状态**被阻止 |
| `N1v6_C2` | A 中断 → 缺 `backup.json` 的坏 ZIP → B | 坏 ZIP 后 `backupFingerprint.isEmpty()` 且 A 义务仍在；B 后 `dst` 仍 `ORIGINAL`、义务不变、状态未完成、导出被阻止 |
| `N1v6_C3` | legacy 长度义务 → B → 再选 B | 播种 `pendingTargets = {b1/content.txt: "12345"}`；两次 B 后 `dst` 仍 `ORIGINAL`、义务**仍是 `12345`**（未被 B 的 SHA 替换）、导出被阻止 |
| `N1v6_C4` | A→B→B（均被阻断）→ **重新选择匹配的原 A** | 阻断期间 `dst` 仍 `ORIGINAL` 且导出被阻止；重选 A 后 `dst == "A CONTENT"`、状态被清除、导出**不再**被未完成状态阻止 |

**导出断言的强度修正**：最初写作 `exportZip(...).isFailure`。在真实 JsonBridge + relaxed DAO 下导出本就可能因**其它**原因失败，该写法会退化成空断言（C4 的收敛断言即因此暴露）。已改为只认「**因未完成状态**被阻止」（失败信息含 `上次整包恢复未完成`），使 C1–C3 的阻断断言与 C4 的解除断言都非空。

## 六、原始证据

工件根：`E:\creation-reading-assistant\next-round-n1-repair-v6\`

| 路径 | 内容 | SHA-256 |
|---|---|---|
| `logs/red-v5-final-tests.log` | 红测完整 Gradle 控制台原日志（EXIT 1） | `b301c39133e4239ba6effb3c7e154911240468aa9e89daf3b2fb04b73efbb243` |
| `results/TEST-LocalZipBackupTest-red-v5.xml` | 红测原始 XML（61/4/0/0） | `51cdfdfc8e0f6948a038e20d278b2d45a52522247037dcb3cd35316adfea7597` |
| `logs/green-v6-final.log` | 绿测完整 Gradle 控制台原日志（EXIT 0） | `05fd098e689486ca27e867913288cf5a34e7e07266bb3e74784641e0ccef5011` |
| `results/TEST-LocalZipBackupTest-green.xml` | 绿测原始 XML（61/0/0/0） | `c32626fb1ccf1ea83c377385d088170df4792e0fc74719a70935727cf7e97cf8` |
| `patch/n1-repair-v6.patch` | 2 文件补丁（a/b 前缀，`-p1`） | `8b5c72ea4333fb4f839c9f88f2b68cc4a955f5dda05fbfd5205e3325a8b8e91f` |
| `logs/LocalZipBackup.v6.kt.bak` | 红/绿切换用的生产文件备份（还原依据） | — |
| `execution.json` | 本轮执行记录 | — |
| `android/` | 候选树（含构建产物） | — |
| `_cache/gradle-home/` | 自有 Gradle 缓存（2.8 GB） | — |

**补丁验证**：在干净父副本上 `git -c core.autocrlf=false apply --check` 通过、`apply` 后**两个文件与候选逐字节相同**（`cmp` 全 OK）。

**过程事件（如实登记）**：一次用 Python 文本模式重写测试文件，把 LF 转成了 CRLF（无功能影响）。已用二进制替换还原为 LF；最终候选测试文件 `CRLF=0 / LF=2352`，且红/绿两轮均在**还原后的最终文件**上重跑。

## 七、未覆盖项

| # | 项 | 状态 |
|---|---|---|
| 1 | **真实 Room 数据库** | **未运行**。用例用真实 `JsonBridge` + 受控 mockk DAO；未开真实 SQLite |
| 2 | **真实进程 kill / 存储故障** | **未运行**。JVM 用 `fileCopyOperation` 接缝模拟「复制中断」，只证明**契约**，不冒充真实 kill |
| 3 | **设备 / UI / SAF 重试** | **未运行**（零 ADB、零安装） |
| 4 | `LocalZipRestoreStateStoreTest` | **未重跑**：StateStore 输入字节未变（`167b881a…`），复用 v5 原始证据 |
| 5 | `JsonBridgeTest` | **未重跑**：JsonBridge 输入字节未变（`f8e455a4…`），复用 v5 原始证据 |
| 6 | 全量 JVM / Lint / Release / 联合构建 | 未跑（不在授权范围） |
| 7 | legacy 长度义务的**出路** | 按本轮要求「证据不足时继续保留阻断，不猜测原来源」，故 legacy 义务**无法**由任何重新导入解除。是否需要人工解除入口属产品决定，本轮不做 |
| 8 | 与 G0/G1 或永久候选的合流 | **未做**；N1 未合流 |

## 八、恢复/副作用

- **仓库**：HEAD `e39a78da…` 未变；暂存区未动；未 stage/commit/push/reset/checkout；未搬目录。
- **唯一仓库写入**：本文件。
- **父候选**：只读引用，未修改（父 v5 两文件 SHA 与 `n1_hashes` 一致）。
- **共享仓库产品代码**：只读；未改动。
- **设备**：零操作；未安装、未启动宿主、未改设置、未访问正式书库。
- **其他窗口 / Codex 修改**：未覆盖、未触碰。
- **构建缓存**：使用自有 `_cache/gradle-home`（由既有 E 盘缓存复制），未与其他窗口共用可写缓存。
- **临时目录**：`_patchcheck` 已在补丁验证后删除。

## 九、交付状态与下一步

**交付状态：已交付，交 Codex 独立核查。** 不自行合流、不关闭数据安全缺陷。

- 交付物：精确改动清单（§三）、父/候选完整 SHA（§二、§三）、两文件补丁（§六，已验证可逐字节复现候选）、红/绿两轮原始控制台日志与 XML（§五、§六）、未覆盖边界（§七）。
- 本轮**只**改 `LocalZipBackup.kt` 与其测试；**未**叠加另一个局部 OR，而是把冲突提升为整个复制计划/义务转移的统一守卫。
- **未新增架构**：无新文件、无新依赖、无跨 DB/文件系统事务；`StateStore`、`JsonBridge`、`Profile`、构建配置全部保持父输入字节。

**建议 Codex 下一步**：

1. 独立核查 §五的红/绿 XML 与 §六 的补丁 SHA、候选树 2 文件差异。
2. 若核查通过，再按 M2 排序安排：真实 Room / 强制终止 / 存储故障的设备或 Robolectric 类验证，以及永久候选合流与新的联合门禁（**不复用**旧联合 2/2 与 142 路径提交授权）。
3. `legacy 长度义务无出路`（§七-7）需产品侧决定是否要人工解除入口，本轮不实现。
