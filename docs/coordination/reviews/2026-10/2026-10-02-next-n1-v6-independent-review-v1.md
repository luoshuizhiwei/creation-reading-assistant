# N1-v6 独立审查（N1-v6-independent-review-v1）

## 一、任务编号/版本

- **任务编号/版本**：`N1-v6-independent-review-v1`
- **性质**：独立审查已有交付，**不重新实施 N1-v6**。只读候选/父候选/共享源码/既有报告；未合流、未暂存/commit/push/reset/checkout、未搬目录、未跑设备/APK/全量/Lint/Release。
- **审查对象**：[N1-repair-v6 报告](../../dispatches/2026-10/2026-10-02-next-n1-repair-v6.md)及其候选 `E:\creation-reading-assistant\next-round-n1-repair-v6\android`
- **上游依据**：[N1-v5/G1-v2 独立核查](2026-10-02-next-n1v5-g1v2-codex-review.md)（§Spec：N1-v5 仍 1P1，冲突保护未约束全部补偿分支）
- **最终结论**：**限定源码/JVM 合同审查通过，允许 Codex 准备合流，设备验收另计**（附 §五 的 2 项测试侧事项与 §七 的独立性声明）

## 二、输入指纹

| 项 | 值 |
|---|---|
| 恢复仓库 | `D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919` |
| 分支 / HEAD | `codex/recover-worktree-20260920` / **`e39a78da9c62f02614207cffb36969a32492eaea`**（实读，与预期一致；暂存区空） |
| 父候选 | `E:\creation-reading-assistant\next-round-n1-repair-v5\android` |
| 待审候选 | `E:\creation-reading-assistant\next-round-n1-repair-v6\android` |
| 原始证据根 | `E:\creation-reading-assistant\next-round-n1-repair-v6` |
| **`LocalZipBackup.kt` 实算** | `f141941a628de19660d71875bb34faa5b4833f05f6db7d0025aba774165b298e` —— **与预期一致** |
| **`LocalZipBackupTest.kt` 实算** | `4d6825b1a97da6e97d4575b4742c169681b6b6eccac7a6dddbbf35771701a707` —— **与预期一致** |
| 父候选两文件实算 | `409f380120ade73e04f9b6b610d6696db1a1065424e2fbd3a06e495b90a88ef0` / `618534dc5254ec3f4b6eafebcec0b8eb9fe5669c3eb911b5af7a9323022d99f4` —— 与 `n1_hashes` 一致 |
| D 盘脏工作树 | **未**作为父输入 |

## 三、所有权与改动

**唯一仓库写入**：本文件。未维护 `current.md`/项目页/TODO（由 Codex 集成者更新）。

**自有审查目录**（`E:\creation-reading-assistant\next-round-n1-v6-independent-review-v1\`）：`audit_v6.py`/`audit_v6.json`（实算脚本与结果）、`probe/`（自有最小探针副本）、`_cache/gradle-home`（自有缓存，由既有 E 盘缓存复制）、`independent-red|green.log/xml`（独立复现）、`probe-v5|v6.log/xml`（探针红绿）。

### 3.1 候选树逐文件比对（实算）

| 项 | 值 |
|---|---|
| 父 / 候选文件数 | 878 / 878 |
| 父独有 / 候选独有 | **0 / 0** |
| **内容不同** | **2**（`LocalZipBackup.kt`、`LocalZipBackupTest.kt`） |
| 相同 | 876 |

**排除项核实（应与父逐字节相同，实测全部相同）**：`LocalZipRestoreStateStore.kt` `167b881a…`、`JsonBridge.kt` `f8e455a4…`、`ProfileViewModel.kt` `124517d2…`、`ProfileRoute.kt` `89a793ba…`、`app/build.gradle.kts` `ae078ff0…`、`gradle.properties` `e1c184a7…`、`settings.gradle.kts` `16407902…`。**结论：确实只有上述两文件变化，StateStore/JsonBridge/Profile/构建配置均保持父版本。**

### 3.2 改动性质

- 生产文件：4 个 hunk（`+42/−17`），全部落在「事务内计划」区域与 `matchesObligation → obligationSatisfiedBy` 助手；**未触及** journal、staging 复制、finally 清理、`deleteRecursively` 检查。
- 测试文件：`+213/−0`，**纯新增**，无删除行、无 import 变化 ⇒ 既有 57 条用例是**未被改动的回归基线**。

## 四、环境

- Windows；Git Bash（MSYS）；Python 3.13；Gradle wrapper 8.9，`--offline`，`--rerun`（确保测试任务实际执行，非 `FROM-CACHE`）。
- 自有 `GRADLE_USER_HOME`，未与其他窗口共用可写缓存；探针构建在自有 `probe/` 副本内，**未改 v5/v6 正式候选**。
- **零设备、零 ADB、零安装、零正式书库访问**。

## 五、执行与结果

### 5.1 工程规范审查（Standards）

| # | 检查 | 结果 |
|---|---|---|
| S1 | 改动是否限于授权路径 | **通过**：仅 2 文件，0 增删 |
| S2 | 是否引入新文件/新依赖/架构重写 | **通过**：无新文件、无新依赖、无跨 DB/文件系统事务 |
| S3 | 补丁是否可复现候选 | **通过**：干净父副本 `git -c core.autocrlf=false apply` 后两文件**逐字节相同** |
| S4 | 行尾是否被污染 | **通过**：候选两文件 `CRLF=0`（LF） |
| S5 | 证据哈希是否自洽 | **通过**：红/绿日志与 XML、补丁、`bak` 五处 SHA 与 `execution.json` 声明**全部一致**；`bak` 等于候选生产文件 |
| S6 | 测试任务是否实际执行 | **通过**：四份日志的 `> Task :app:testDebugUnitTest` **无** `FROM-CACHE`/`UP-TO-DATE` 后缀 |
| S7 | 注释与代码是否一致 | **通过**：新注释准确描述四种授权路径同时失效与 legacy 不猜测 |

**规范发现：0 项。**

### 5.2 需求正确性审查（Spec）

**控制流逐接缝核对**（`begin → 解包/指纹 → JsonBridge 分类 → 事务内计划 → markDbCommitted → 复制/验证 → journal → finally → 导出门禁`）：

| 接缝 | 核对结论 |
|---|---|
| `begin` | 在解包前执行，坏 ZIP 会留下空指纹现场并继承前序义务（v5 既有行为，未改） |
| 指纹 | `backupFingerprint = jsonHash\|contentFingerprint`，同 JSON 不同正文判为不同备份（未改） |
| 分类 | `JsonBridge`：`local==null` 或 `shouldAcceptRemote` → accepted；`revision==local && updatedAt==local` → **equal**（同时进 `equalBookContentPaths` 与 `skippedBookContentPaths`）；否则 strictlyNewer（同时进 skipped） |
| **事务内计划** | **本次修复点**：`obligationSatisfiedBy` 单一判定 → `hasConflictingObligation` → `sourceAuthorized` 统一门控四条授权路径；冲突时**显式**把目标留在 `effectiveSkipped` |
| `markDbCommitted` | `remainingUnresolved = unresolvedTargets.filterKeys { it !in pendingTargets.keys }`；冲突目标不在 `actualTargetsToCopy` ⇒ **不被剪枝** |
| 复制/验证 | 冲突目标受 `effectiveSkipped` 保护，不进入 `actualTargetsToCopy`，验证循环不会误报 |
| `clear` | `matchesTarget` 对 legacy 与内容不符一律 false ⇒ 义务未就位时**拒绝清除** |
| 导出门禁 | 未完成状态存在即抛 `blockingExportMessage`，**早于** `buildExport` |

**四项关键判定（均为源码可复核，非仅看布尔表达式）**：

1. **统一冲突保护成立**：`sourceAuthorized = !hasConflictingObligation && (...)` —— equal、同指纹、空指纹、路径相同四条路径**同时**被门控；冲突目标既不进入复制集合（`else if` 显式加回 `effectiveSkipped`），也不被剪掉义务（不在 `pendingTargets`）。
2. **strictlyNewer 仍优先**：`if (isStrictlyNewer) return@forEach` 位于冲突逻辑**之前**且未改动；strictlyNewer 目标同时在 `skippedBookContentPaths` 中，仍受保护。匹配原 A 时（版本相等，非 strictlyNewer）不影响该铁律。
3. **legacy 边界准确**：`obligationSatisfiedBy` 与 `matchesTarget` 对 legacy 长度义务**均**返回 false ⇒ 既不能写入，也不能清除。**永久阻断属实**，报告 §七-7 已如实登记，未放宽保护。
4. **已成立行为无回归**：journal `verified=false` 先写、`finally` 清理与 suppressed 合并、`deleteRecursively` 返回 false 检查、事务内计划时序 —— 均由 4 个 hunk 的位置与既有 57 条用例的**未改动**共同证明。

### 5.3 组合重试路径（实算，含独立复现）

| 路径 | 候选声明 | 我的核实 |
|---|---|---|
| A→B→B | C1 | **通过**（独立红测中该用例在 v5 上失败、v6 上通过） |
| A→坏ZIP→B | C2 | **通过**（同上） |
| legacy→B→B | C3 | **通过**（同上；义务值 `12345` 未被 B 的 SHA 替换） |
| 阻断后重选匹配原 A | C4 | **通过**（正文补齐为 `A CONTENT`、状态清除、门禁解除） |

**红/绿独立复现（我在自有副本中重跑，非照抄候选声明）**：

| 轮次 | 输入 | 实测 |
|---|---|---|
| 红 | 父 v5 生产 `409f3801…` + 候选测试 `4d6825b1…` | **61 tests / 4 failures / 0 errors / 0 skipped**，失败项与原因与候选红 XML **逐条一致** |
| 绿 | 候选生产 `f141941a…` + 候选测试 `4d6825b1…` | **61 tests / 0 failures / 0 errors / 0 skipped** |

**候选声明的红 61/4、绿 61/0 成立。**

### 5.4 缺陷与待办（分清确认缺陷 / 推测 / 未验证）

#### F1 —— 新增分支缺少候选内回归覆盖（**确认**；测试覆盖缺口，非产品缺陷）

- **严重性**：中（不影响本次源码正确性，影响后续回归保护）
- **精确位置**：`LocalZipBackup.kt` 新增 `else if (hasConflictingObligation)` 分支（候选文件 `@@ -286,0 +300,5 @@`）
- **可达前提**：存在继承义务，且本次 ZIP 的同名正文与义务不匹配，且 `JsonBridge` 判为 **accepted**（远端 revision 较高 / 本地较低）
- **实际后果**：该分支若被将来重构移除，此路径会静默恢复为「写入冲突正文 + 剪掉旧义务」。候选 4 条 N1v6 用例**全部走 equal 分类**（本地 `revision=2` 与远端相同），**无一覆盖 accepted**
- **证据**：我的探针 `probe acceptedRemoteWithConflictingObligationIsBlocked`（本地 `revision=1`、远端 `revision=2`）——
  在 **v5 上失败**：`ComparisonFailure: accepted 不得写入与继承义务冲突的正文 expected:<[ORIGINAL]> but was:<[B CONTENT]>`（`probe-v5.xml`，61→2 tests/1 failure）；
  在 **v6 上通过**（`probe-v6.log` BUILD SUCCESSFUL）。**即：这是真实可达的第四条绕过路径，v6 已修好，但候选测试集未锁住它**
- **最小修复方向**：向 `LocalZipBackupTest.kt` 增补 1 条用例（测试侧，**无产品改动**）：复用 `realJsonBridge` 但本地 `revision=1`、远端 `revision=2`，断言 `dst` 仍为 `ORIGINAL`、义务值不变、`isComplete==false`

#### F2 —— C4 的导出断言只证明「门禁解除」，不证明「完整导出成功」（**确认**；断言强度）

- **严重性**：低
- **精确位置**：`LocalZipBackupTest.kt` 的 `N1v6_C4_reselectingMatchingAConvergesAfterBlockedRetries` 末条断言 `assertFalse(exportBlockedByUnfinishedState(...))`
- **可达前提**：任何 C4 场景
- **实际后果**：该断言在「导出成功」与「导出因**其它**原因失败」两种情况下**都为真**，不能区分二者
- **证据**：我的探针 `probe exportFailureCauseWithoutUnfinishedState`（无未完成状态、真实 JsonBridge + relaxed DAO）实测
  `PROBE_EXPORT_RESULT success=false type=java.util.NoSuchElementException message=Expected at least one element` ——
  原因是 `JsonBridge.buildExport` 调 `bookDao.observeAllActive().first()`，而测试只桩了 `noteDao`/`highlightDao` 的 `observeAllActive`，`bookDao` 等为 relaxed mock → 空流。**这是测试夹具产物，不是产品缺陷**
- **对报告的影响**：候选报告 §五.1 的措辞是「导出**不再**被未完成状态阻止」，**准确、未声称成功**；但测试本身无法区分
- **最小修复方向**（测试侧）：补桩 `bookDao.observeAllActive() returns flowOf(listOf(localBook))`（及 inspiration/progress/session），使 C4 可断言 `isSuccess`

#### F3 —— legacy 长度义务永久无解除入口（**确认**；产品边界，非本次返修项）

- **严重度**：低（保守合同）
- **位置**：`LocalZipBackup.obligationSatisfiedBy`（legacy→false）与 `LocalZipRestoreStateStore.matchesTarget`（legacy→false）
- **后果**：一旦遗留 legacy 长度义务，**任何**重新导入都无法收敛，状态永久未完成、导出永久被阻止
- **证据**：C3 用例 + `matchesTarget` 源码
- **说明**：这是本轮**有意**的保守合同（「证据不足时继续保留阻断，不猜测原来源」），报告 §七-7 已如实列为产品边界。**本审查不新增人工解除、不清空 journal、不放宽保护**；是否提供人工解除入口属产品决定

#### 未验证项（不列为缺陷）

真实 Room 数据库、真实进程 kill / 存储故障、设备 UI / SAF 重试、与永久候选的合流与新联合门禁 —— 均未运行。

## 六、原始证据

**被审交付（只读，实算）**

| 路径 | SHA-256 |
|---|---|
| `…/n1-repair-v6/logs/red-v5-final-tests.log` | `b301c39133e4239ba6effb3c7e154911240468aa9e89daf3b2fb04b73efbb243` |
| `…/n1-repair-v6/results/TEST-LocalZipBackupTest-red-v5.xml` | `51cdfdfc8e0f6948a038e20d278b2d45a52522247037dcb3cd35316adfea7597` |
| `…/n1-repair-v6/logs/green-v6-final.log` | `05fd098e689486ca27e867913288cf5a34e7e07266bb3e74784641e0ccef5011` |
| `…/n1-repair-v6/results/TEST-LocalZipBackupTest-green.xml` | `c32626fb1ccf1ea83c377385d088170df4792e0fc74719a70935727cf7e97cf8` |
| `…/n1-repair-v6/patch/n1-repair-v6.patch` | `8b5c72ea4333fb4f839c9f88f2b68cc4a955f5dda05fbfd5205e3325a8b8e91f` |
| `…/n1-repair-v6/logs/LocalZipBackup.v6.kt.bak` | 等于候选生产文件（`f141941a…`） |

**我的独立审查产物**（`E:\creation-reading-assistant\next-round-n1-v6-independent-review-v1\`）

| 路径 | 说明 | SHA-256 |
|---|---|---|
| `audit_v6.py` / `audit_v6.json` | 树比对 + 证据哈希 + XML 实算 | `10de6064…` / `65eb102b…` |
| `independent-red.log` / `.xml` | 独立红测（61/4） | `fb20fa8e…` / `017d6c9c…` |
| `independent-green.log` / `.xml` | 独立绿测（61/0） | `4f814452…` / `b8fb4759…` |
| `probe-v5.log` / `.xml` | 探针在 v5 生产代码上（accepted 路径被绕过，1 failure） | `89f7db39…` / `ff288737…` |
| `probe-v6.log` | 探针在 v6 上（全通过） | `a18af18f…` |
| `probe/…/N1v6ReviewProbeTest.kt` | 审查者自有最小探针（**不属于候选**） | `acfcfb84…` |

**探针副本来源与改动声明**：由候选 v6 完整复制（排除 `build/`、`.gradle/`、`.kotlin/`，878 文件），生产文件与候选同 SHA `f141941a…`；**唯一新增**为探针测试文件（`acfcfb84…`）。红/绿切换通过替换 `LocalZipBackup.kt` 完成，结束已还原为候选版本（`f141941a…` 已复核）。**未改动 v5/v6 正式候选。**

## 七、未覆盖项

| # | 项 | 状态 |
|---|---|---|
| 1 | 真实 Room / SQLite | 未运行（JVM + 受控 mockk DAO） |
| 2 | 真实进程 kill / 存储故障 | 未运行（JVM 接缝只证明契约） |
| 3 | 设备 UI / SAF 重试 / 导出门禁的实际用户可见路径 | 未运行 |
| 4 | `LocalZipRestoreStateStoreTest`、`JsonBridgeTest` | **未重跑**：两者输入字节与父一致，按「输入未变即复用」原则接受 v5 原始证据 |
| 5 | F1 的 accepted 路径 | 候选测试集**未覆盖**（本审查以自有探针补证） |
| 6 | F2 的完整导出成功 | 本夹具无法证明（relaxed DAO 空流） |
| 7 | 全量 JVM / Lint / Release / 联合构建 / 新联合门禁 | 未跑（不在授权范围） |

### 独立性声明（重要）

**本审查的执行者与 N1-repair-v6 候选的实施者为同一 Agent 会话。** 这构成实质的独立性限制：

- **已做到的**：全部结论建立在**实算**上（指纹、树差异、证据 SHA、XML 计数），且**红/绿两轮在自有副本中独立重跑**（非照抄候选声明），并额外构造了候选未覆盖的第四条路径探针（F1）。这些是可复核的客观证据，不依赖自我评价。
- **未做到的**：不是由**独立第三方**执行。若项目要求严格的独立复核，建议由另一窗口/模型在 F1/F2 处理后再做一次只读抽检（重点：F1 新用例是否真的在 accepted 分类下失败于 v5）。

## 八、恢复与副作用

- **仓库**：HEAD `e39a78da…` 未变；暂存区未动；未 commit/push/reset/checkout；未搬目录。**唯一仓库写入 = 本文件**。
- **v5/v6 正式候选**：只读；探针仅在自有副本内改动，结束已还原并复核 SHA。
- **共享源码 / 既有报告 / 其他窗口**：未改动、未覆盖。
- **设备**：零操作。
- **自有目录**：审查脚本、探针副本、缓存、日志与 XML 均在 `next-round-n1-v6-independent-review-v1\`；`patchcheck` 临时目录已删除。
- **未读取** Claude 独立记忆库、正式书库、实体手机。

## 九、交付状态与下一步

### 结论

**限定源码/JVM 合同审查通过，允许 Codex 准备合流，设备验收另计。**

依据：候选指纹与预期一致；树差异恰为 2 个授权文件、876 文件与父逐字节相同、StateStore/JsonBridge/Profile/构建配置均保持父版本；补丁可逐字节复现候选；红 61/4 与绿 61/0 **均由我在自有副本中独立复现**；统一冲突守卫在控制流四个环节（计划/复制/义务剪枝/清除）均成立；strictlyNewer、事务内计划时序、journal、finally 清理、`deleteRecursively` 检查、异常链保留均未受影响。

### 合流前建议处理（均为**测试侧**，无产品改动）

1. **F1（建议必须）**：向 `LocalZipBackupTest.kt` 补 1 条 accepted + 冲突义务用例（本地 `revision=1`、远端 `revision=2`），把第四条绕过路径纳入候选回归集。
2. **F2（建议）**：补桩 `bookDao.observeAllActive()` 等，使 C4 能断言**完整导出成功**，而非仅「门禁解除」。

### 其余

- **F3** 为产品边界（legacy 永久阻断），报告已如实登记，是否提供人工解除入口**待产品决定**，本审查不新增。
- **设备/真实 Room/真实 kill 验收另计**；不继承旧联合 2/2 与历史提交授权。
- **不自动修复、不合流、不暂存/提交/推送**；本报告落盘即停止，交 Codex 独立处置。
