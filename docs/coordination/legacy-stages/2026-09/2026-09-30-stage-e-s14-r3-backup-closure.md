# 阶段 E S14+R3 最小备份联合候选：测试 APK 依赖闭包修复（2026-09-30）

- 窗口：Stage E S14+R3 联合候选构建窗口（与其他窗口并行；未触碰 G6/资产/其他窗口目录）
- 恢复仓库：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`，HEAD `513891d6a66a9b78e072ba759d34ed580d377ecf`（只读）
- 隔离目录：`E:\creation-reading-assistant\stage-e-s14-r3-backup-closed-20260930\`（新建、独占；不改变 S14-only 失败现场 `stage-e-s14-isolated-20260930\`）
- 底座：`stage-e-s14-isolated-20260930\android` 纯源码（排除 `build/`、`.gradle`、`.idea`；复制后 build 目录数 = 0）
- **本窗口未操作 MuMu 或实体手机**（`adb` 零调用）；设备验收归窗口 4
- 本窗口唯一可写共享文件：本报告

---

## 一、结论

**联合候选构建通过，host/test APK 已产出，READY.json 已写入。**

| 项 | S14-only（失败） | 本联合候选 |
|---|---|---|
| `compileQahostAndroidTestKotlin` | **FAIL，25 错**（引用被排除的 R3 API） | **EXIT 0，0 错** |
| `LocalZipBackupTest` JVM | 34/34 | **34/34**（同一文件，SHA 未变） |
| `JsonBridgeTest` JVM | 未跑 | **18/18**（52/52 合计，EXIT 0） |
| qahost host/test APK | 无产物 | **两个 APK 均产出**（SHA 见 §四） |
| 设备验收 | NOT RUN | **NOT RUN（本窗口零设备操作）**，待窗口 4 |

---

## 二、联合候选构成（与 S14-only 的区别 = 恰一个文件）

对 S14-only 候选逐字节对比后确认：**两个 androidTest 文件在 S14 候选中本就是工作树版本**（`ede20f80…`/`75e9f71e…`，与恢复仓库工作树逐字节一致）——S14 隔离报告 §1.3 "HEAD 版" 的说法确实不成立（两文件均为未跟踪工作树文件），失败报告 §2.1 的根因判定正确。因此唯一缺口是 `JsonBridge.kt` 缺 R3 备份闭包：

| 文件 | S14-only SHA | 联合候选 SHA | 变更 |
|---|---|---|---|
| `feature/sync/JsonBridge.kt` | `d90c7b12b4d014e86f374dfc7b1cbd90fc02b4dbaf1ec860b0d787fe7459a63f` | `b9d8c3612eed44419aa2e9a9c7aa7d0e93589f4ae03f5d38e33ce3a308ce515a` | **+R3/S04-5 备份闭包**（= 恢复仓库工作树版本，逐字节一致） |
| `feature/sync/LocalZipBackup.kt` | `ab78a16e8e34c50a59ada31040278569baaa013f2c1eba6054a675b89997514c` | 同左 | **不变**（安全实现无新问题，保持原 SHA） |
| `test/…/LocalZipBackupTest.kt` | `174cfbf6cbd3ff35e182efc4b956ff46d72b4ded20ae7cc910bfb01cc29a690a` | 同左 | 不变（W5/W6 判别用例保留） |
| `androidTest/…/LocalZipBackupPersistentRestoreTest.kt` | `ede20f8022ab67bd67c74c5885ca57484c2bf67d4ee41007a2637fad6d078bf0` | 同左 | 不变（**断言零删改**：来源引用/规则/纠错/持久重开全部保留） |
| `androidTest/…/LocalExportRoomRollbackTest.kt` | `75e9f71e0787e90c2fb5141f4f1cec9dc80ac2b498071da4a9d9f15d76c48320` | 同左 | 不变（**事务回滚断言零删改**） |
| `test/…/JsonBridgeTest.kt` | HEAD 版 | HEAD 版 | 不变（联合 JsonBridge 下 18/18 通过） |

**可复算补丁**：`artifacts/jsonbridge-s14-to-joint.patch`（289 行，SHA-256 `22383db80eca6ed132ed7d88adc20d9334f4f0d0d636a58252e7810f07d507c6`），= S14 候选 JsonBridge → 联合候选 JsonBridge 的完整 diff。复算方式：取 S14-only 树，应用该 patch，即得本候选。

### 2.1 纳入的 R3/S04-5 内容（逐项 = 隔离报告 §2 明确排除清单的回填）

1. **三个导出 DTO**：`SourceRefExport` / `TextRuleExport` / `CorrectionExport`（`@Serializable`，含 `toEntity()`/`fromEntity()`）。
2. **`LocalExport` 三字段**：`library_source_refs` / `reader_text_rules` / `reader_text_corrections`（`@SerialName`，默认空表——旧备份 JSON 向后兼容）。
3. **构造器接线**：`sourceRefDao: LibrarySourceRefDao? = null`、`textRuleDao: ReaderTextRuleDao? = null`、`correctionDao: ReaderCorrectionDao? = null`。
4. **导出填充**：`exportToString`/`buildExport` 中三路数据收集并填入 `LocalExport`。
5. **事务内合并（旧不覆新）**：`importFromString` 事务内按 `last_seen_at` / `updated_at` 比较后 `upsert`，不加联系方式与 S14 书籍分支一致。
6. **S14 四 hunk 全部保留**：`ImportMergeResult`、`onMergeResult` 参数、`acceptedBookIds`/`skippedBookContentPaths` 收集、事务后回调——以逐行 diff 核对无丢失。

### 2.2 依赖闭包核对（结论：无缺口，无需提取实体/DAO）

- 三个 DAO（`LibrarySourceRefDao.getAll/upsert`、`ReaderTextRuleDao.observeAll/getById/upsert`、`ReaderCorrectionDao.listForBook/getById/upsert`）与三实体在 S14 底座（HEAD `513891d6` + B1+B2+库浏览器）**已存在且方法签名逐一匹配**——G0/U/G2 提交与更早的 R3 v13/v14 迁移已携带。
- DI：`JsonBridge` 为 `@Inject constructor`，三 DAO 是 `AppDatabase` 的 `abstract fun`（v13/v14 产物），Dagger 图自动提供；可空默认参数保证既有手工构造点（测试等）不受影响。**无任何 DI 文件改动**。
- 排除项：`WebDavBackup.kt`（工作树有改，非本组，未带入）、`LegadoReadingProgressImporter.kt`（未跟踪，非本组）、`SyncOptions`/排序等其他功能（未带入）。对 `importFromString` 的新参数是带默认值的新增尾参，`SyncRepository`/`SyncMergePolicy`/`WebDavBackup` 既有调用点不受影响（编译通过即证）。

---

## 三、命令与退出码（原始日志在 `artifacts\`）

| # | 命令（工作目录 = 隔离候选 `android/`） | 退出码 | 日志 |
|---|---|---|---|
| 1 | `./gradlew :app:compileDebugKotlin :app:compileDebugUnitTestKotlin --offline --console=plain` | **0**（BUILD SUCCESSFUL in 1m 52s） | `compile-debug.log` |
| 2 | `./gradlew :app:compileQahostAndroidTestKotlin -PcraAndroidTestBuildType=qahost --offline --console=plain` | **0**（BUILD SUCCESSFUL in 1m 7s；`grep -c "^e:"` = **0**） | `compile-qahost-androidtest.log` |
| 3 | `./gradlew :app:compileDebugAndroidTestKotlin --offline --console=plain` | **0**（8s） | `compile-debug-androidtest.log` |
| 4 | `./gradlew :app:testDebugUnitTest --offline --tests …LocalZipBackupTest --tests …JsonBridgeTest` | **0**（33s） | `test-jvm.log` |
| 5 | `./gradlew :app:assembleQahost :app:assembleQahostAndroidTest -PcraAndroidTestBuildType=qahost --offline --console=plain` | **0**（34s） | `assemble-qahost.log` |

全部 `--offline`；project cache 位于本候选目录内（独占 E 盘路径，未与其他窗口共用）。

## 四、产物与验证

### 4.1 qahost APK

| APK | SHA-256 | 元数据 |
|---|---|---|
| `app/build/outputs/apk/qahost/app-qahost.apk` | `b7ce65df96c7f3732a07d3f929cc90cd77eccfc02473c8e8593b805e1f06bf37` | applicationId `com.creationreadingassistant.qahost`，versionCode 3，versionName **0.5.1** |
| `app/build/outputs/apk/androidTest/qahost/app-qahost-androidTest.apk` | `97609ba0078da7e7e6f6e5f062107b02086400417b5ade0b635f0b0d75c46771` | 包 `com.creationreadingassistant.qahost.test`，target `com.creationreadingassistant.qahost` |

**测试 APK 内容核验**（dex 解包，`artifacts/apk-dex-check/`）：`LocalZipBackupPersistentRestoreTest`（181 引用）、`LocalExportRoomRollbackTest`（26 引用）、`CraIsolatedTestRunner`、`SourceRefExport`/`TextRuleExport`/`CorrectionExport` 类引用全部在 dex 中。

### 4.2 JVM 定向测试

| 套件 | tests | failures | errors | skipped | XML SHA-256 |
|---|---|---|---|---|---|
| `LocalZipBackupTest` | 34 | 0 | 0 | 0 | `2e0c276b99244d23591a59ad18f725c331a998df3a17a0beb886870937125df8` |
| `JsonBridgeTest` | 18 | 0 | 0 | 0 | `520b1db6c4ca8ad95ec616d3171ee329f162c12ca0575ba66370e3a83fdd31fc` |
| **合计** | **52** | **0** | **0** | **0** | — |

`JsonBridgeTest`（HEAD 版、未修改）在联合 JsonBridge 下通过——`LocalExport` 新增字段带默认值，既有用例向后兼容。

## 五、源码清单与 READY.json

- 完整源码清单（842 文件，含逐文件 SHA-256）：`artifacts/source-manifest.sha256`（清单自身 SHA-256 `e624a8052f073291cb1b7f8cfd6794546538c8d237baec4265e4c6db335b64bd`）。
- **READY.json** 已写入候选目录根（构建与测试全部通过、APK 产出之后）：含源码清单指针、五文件 SHA、host/test APK 路径与 SHA、包名、runner（`com.creationreadingassistant.testing.CraIsolatedTestRunner`）、目标测试类、五条构建命令与退出码、JVM 计数。

## 六、边界与未验

1. **设备验收 NOT RUN**：本窗口零设备操作（未安装、未备份、未改设备状态）。窗口 4 须按 [S14 验收报告 §4](../../../qa/2026-09/2026-09-30-stage-e-s14-isolated-mumu-acceptance.md) 流程对**本候选 APK**（上表 SHA）重走全流程：设备前快照（serial/密度/夜间模式/原 APK/主 DB 与持久文件 SHA）→ 安装 → 运行 `LocalZipBackupPersistentRestoreTest` + `LocalExportRoomRollbackTest` → 恢复对账。旧 MuMu 6/6（09-28）对应旧 APK + 整树 JsonBridge，**不得**作为本候选证据。
2. **保留的既有登记风险**（沿 S14 隔离报告 §6）：真实进程 kill 窗口、DB/文件整包非原子、实体手机差异——本轮未新增也未消除。
3. **本候选不携带**：WebDAV 重写（`WebDavBackup.kt` 工作树改动未带入）、`SyncOptions`、新排序、`LegadoReadingProgressImporter.kt`（未跟踪）。若这些功能后续入版，需另行评估与 JsonBridge 新字段的合流。
4. **LocalZipBackup.kt 未动**：安全实现（journal/staging/fail-closed）无新问题，按任务要求保持原 SHA `ab78a16e…`；其对 `JsonBridge.LocalExport` 的消费经 S14 隔离版本验证，新增 R3 字段带默认值不改变其行为面。

## 七、纪律声明

- 恢复仓库只读：未修改任何共享源码/测试/Gradle/schema；未 stage/commit/push/reset/checkout；未搬移或删除任何目录（S14 失败现场原样保留）。
- 本窗口唯一可写共享文件：本报告；其余全部写入 `E:\creation-reading-assistant\stage-e-s14-r3-backup-closed-20260930\`。
- 未触碰原目录（非恢复仓库）、正式书库与真实手机；未使用 MuMu（零 adb 调用）。
- 测试原义保留：两个 androidTest 文件逐字节未动，未删断言、未空实现、未 `@Ignore`——绿色结果仅来自补齐 JsonBridge 的 R3 备份闭包。
