# S14 隔离持久库备份恢复测试 · 准备报告（未编译/未执行）

- 日期：2026-09-28；角色：S14 持久库备份恢复测试窗口（总指挥 Codex；不自行领取后续任务）
- 仓库：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`；分支 `codex/recover-worktree-20260920`
- 性质：**只交付测试代码与静态核对**。本窗口未运行 Gradle、未安装 APK、未操作 MuMu/真实手机（窗口 1 是唯一 Gradle 所有者）；**一切测试结论均未取得，本文不构成任何 PASS**。
- 唯一可写文件：本报告 + `android/app/src/androidTest/java/com/creationreadingassistant/feature/sync/LocalZipBackupPersistentRestoreTest.kt`（新建，未跟踪）。未修改任何既有测试、生产代码、Room schema、Gradle 配置或共享账本。
- 输入：`docs/qa/2026-09/2026-09-27-s14-mumu-room-rollback-acceptance.md`（§5 未覆盖清单 1/3 即本棒目标）、`docs/coordination/legacy-stages/2026-09/2026-09-28-stage-b-platform-risk-delta.md`（S14 行"补持久库导入路径的最小仪器证据"）、`docs/handoff/current.md`、生产源码 `JsonBridge.kt`(499 行)/`LocalZipBackup.kt`(173 行)/相关 DAO 与 schema `15.json`、既有测试 `LocalExportRoomRollbackTest.kt`、隔离基建 `CraIsolatedTestRunner`/`CraIsolatedTestApplication`。

## 1. 任务定位：补什么缺口

既有 MuMu 2/2（`LocalExportRoomRollbackTest`）**只证明内存 Room 事务断言**：受控外键失败回滚、旧备份不覆新。按其报告 §5 明列的未覆盖项，本棒补的是：

1. **持久库导入**——`Room.databaseBuilder` 唯一命名数据库文件（非内存），导入真实落盘；
2. **关闭→重新打开**——回滚与恢复结果在 `close()` 后按同名重开仍成立（持久性证明）；
3. **完整 ZIP 备份恢复**——`LocalZipBackup.exportZip → importZip` 的文件集合（backup.json + books/**）用独立 SHA-256 对账，书籍/来源引用/进度/规则/纠错五类记录逐实体对账。

仍不在本棒范围（保持既有登记）：真实 WebDAV 网络、跨设备 SAF URI、实体手机 ROM、qahost 1.14GB 库膨胀成因。

## 2. 测试覆盖矩阵

文件：`android/app/src/androidTest/java/com/creationreadingassistant/feature/sync/LocalZipBackupPersistentRestoreTest.kt`（575 行，3 个 @Test，全部合成数据）

| 用例 | 覆盖点 | 对应既有缺口 / 判别力 |
|---|---|---|
| `zipBackupRoundTripRestoresFiveCategoriesAndFilesAcrossCloseReopen` | 种入持久库五类记录（2 书[测试 EPUB/测试 TXT]+2 进度+2 来源引用+1 规则+1 纠错）+ 2 个合成书源文件 → `exportToString`/`exportZip` → ZIP 条目集合与逐文件 SHA-256 对账 → **关闭并删除库文件**模拟整库丢失 → `importZip` 恢复到新建同名库 → 五类记录逐实体相等 + 恢复文件逐字节哈希相等 → 再 `close()`→重开 → 规范逻辑摘要（五类记录排序行哈希）三态相等（导出前=恢复后=重开后） | 内存测试没有 ZIP 文件集合、没有持久落盘、没有重开；`d1==d2` 证明导出→导入保真，`d2==d3` 证明**持久性**（这正是"不能证明退出重开"的缺口） |
| `correctionForeignKeyFailureRollsBackAllCategoriesInPersistentDbAndStaysRolledBackAfterReopen` | 注入点：`reader_text_corrections.book_id` FK→books CASCADE（schema 15.json 核实），且纠错是 `JsonBridge.importFromString` 事务内**最后一步**——构造指向不存在书籍的纠错，使书籍/进度/来源引用/规则四类写入**全部先行完成**后触发 `SQLiteConstraintException` → 断言四类全部回滚、既有书无损 → close→重开后再断言（回滚持久成立） | 比既有内存测试（bookTags 注入）覆盖面更宽：回滚范围覆盖 S14 新增的三类（来源引用/规则/纠错）且新增**重开后仍回滚**的持久性判别；失败点是生产代码真实 FK 约束，不是测试自建的镜像 |
| `zipWithoutBackupJsonIsRejectedBeforeTouchingPersistentDb` | 构造缺 `backup.json` 的 ZIP → `importZip` 必须抛 `IllegalStateException` → 断言持久库零变化 → 重开后再断言 | 生产代码真实失败路径（`LocalZipBackup.kt:100-102`），验证"先校验后入库"的隔离顺序在持久库上成立 |

对账手段（任务要求"独立哈希对账"）：① ZIP 条目名集合 == `backup.json` + 全部书源文件；② ZIP 内书源文件 SHA-256 == 落盘源文件；③ 恢复出的文件集合与逐文件 SHA-256 == 打包前；④ `backup.json` 解析回 `LocalExport` 后按类计数与导出一致；⑤ 五类记录先逐实体 `assertEquals`（导出 payload 原样入库：books/progress 直接 upsert，refs/rules/corrections 走 1:1 DTO 往返），再以规范逻辑摘要哈希做跨重开总对账。

## 3. 隔离保证（逐条对应"不得接触正式书库/真实手机/用户已有 qahost 数据"）

| 风险面 | 隔离手段（代码位置） |
|---|---|
| 正式持久库 `creation_reading_assistant_native`（AppDatabase.kt:159） | 测试库 `TEST_DB_NAME = "s14_persistent_backup_restore_test.db"` 唯一命名，永不重名；`@Before`/`@After` 删除 db+`-wal`+`-shm`+`-journal` 四件 |
| qahost 真实 `files/books/`（29 本书、7 个内部 epub） | `IsolatedSyncTestContext`（测试文件内私有类）重定向 `filesDir`/`cacheDir`/`applicationContext` 到测试唯一根；`exportZip` 遍历的是**重定向后**的 books（只含合成 2 文件），`importZip` 的临时解压目录与 books 覆盖全部落在测试根内 |
| qahost 真实加密 prefs（`SecurePrefs` → `EncryptedSharedPreferences`，`cra_device_identity` 含 WebDAV 令牌） | 覆写 `getSharedPreferences` 返回**内存实现**：ESP 的 delegate 与 `MasterKey` 的 keyset prefs 全部落在内存，deviceId 稳定可复现；**关键防线**：另覆写 `deleteSharedPreferences` 为空操作——`SecurePrefs.open`（SecurePrefs.kt:35）降级重试路径会删除同名 prefs 文件，不拦截将删掉 qahost 真实 `cra_device_identity` |
| 已登记的残留设备侧触碰（如实声明，非用户数据） | ① `MasterKey` 在 AndroidKeyStore 中 qahost 测试包主密钥别名的 get-or-create（幂等、应用自有行为、既有 qahost 已存在该别名）；② 主 APK 合并清单 `androidx.startup` 创建 `androidx.work.workdb`（`CraIsolatedTestApplication` KDoc 已登记，换 Application 无法消除，本测试不触碰它） |
| 运行入口 | 经 `CraIsolatedTestRunner`（build.gradle.kts:32 默认 runner）注入裸 `CraIsolatedTestApplication`：无 Hilt、无 `App.onCreate` 三条 cra-startup 接线，不触碰正式库与系统夜间模式 |

数据面：只有合成行与合成字节（书籍文件不做任何解析）；报告与测试内只用"测试 EPUB/测试 TXT"中性名称。

## 4. 文件清单与所有权

| 文件 | 状态 | 归属 |
|---|---|---|
| `android/app/src/androidTest/java/com/creationreadingassistant/feature/sync/LocalZipBackupPersistentRestoreTest.kt` | **新建**（本棒唯一代码产物，575 行） | 本棒 |
| `docs/coordination/2026-09-28-s14-persistent-backup-test-preparation.md` | 本报告 | 本棒 |
| `LocalExportRoomRollbackTest.kt`（同目录既有文件） | **零改动**（该文件本就未跟踪，系早前窗口产物；本棒未触碰） | 他人 |

`git status` 核对：androidTest/feature/sync 目录仅上述两个未跟踪文件，无其他工作区变化归于本棒。

## 5. 预期命令（供总指挥/Gradle 所有者串行执行，本棒未运行）

```powershell
# 1) 编译（在 android/ 目录；qahost 变体，与 09-27 MuMu 验收同一通道）
.\gradlew.bat :app:assembleQahostDebug :app:assembleQahostDebugAndroidTest

# 2) 安装（按既有 qahost runbook：docs/qa/2026-09/2026-09-25-s04-qahost-device-runbook.md；
#    只装 qahost + qahost.test，绝不安装/触碰正式包）

# 3) 运行（MuMu 端点以 adb devices 实测为准，先核对 qahost 存在、正式包不存在）
adb -s <serial> shell am instrument -w -r -e class `
  com.creationreadingassistant.feature.sync.LocalZipBackupPersistentRestoreTest `
  com.creationreadingassistant.qahost.test/com.creationreadingassistant.testing.CraIsolatedTestRunner
```

预期：`OK (3 tests)`、`INSTRUMENTATION_CODE: -1`。执行前/后建议沿用 09-27 报告的只读对账法（qahost 库三件字节拉取 + `books` 表计数 + 内部 epub MD5 清单）证明 qahost 状态零变化。

## 6. 静态核对结果（本棒实际执行过的检查）

| 检查 | 结果 |
|---|---|
| 括号/圆括号平衡 | `{` 111/111、`(` 410/410（脚本计数） |
| 生产符号逐一存在 | `JsonBridge.exportToString/importFromString`、`LocalZipBackup(jsonBridge, ioDispatcher)/exportZip/importZip`、`AppDatabase` 16 个 DAO 访问器（与既有测试逐一相同）、`BookDao.getByIds/getById/upsert/observeAllActive`、`ReadingProgressDao.getByBooks/getByBooks/upsert/observeAllActive`（ReadingDao.kt:14-24）、`ReaderTextRuleDao.observeAll/getById/upsert`、`ReaderCorrectionDao.listForBook/getById/upsert`、`LibrarySourceRefDao.getAll/upsert`、`ReaderCorrectionEntity.STATUS_ACTIVE`、`SourceRefExport/TextRuleExport/CorrectionExport.fromEntity`、`LocalExport.CURRENT_SCHEMA_VERSION=1`（构造默认值命中） |
| 实体构造器逐一比对 | `BookEntity(id,title,format,size,revision,updated_at,…)`、`ReadingProgressEntity(book_id,progress_percent,completion_state,current_location_json,revision,updated_at)`、`ReaderTextRuleEntity(11 字段)`、`ReaderCorrectionEntity(9 字段)`、`LibrarySourceRefEntity(12 字段)`——与 CatalogEntities.kt/ReadingEntities.kt/ReaderTextRuleEntity.kt/ReaderCorrectionEntity.kt 逐字段核对 |
| FK 注入可行性 | schema `15.json`：`reading_progress`/`reader_text_corrections`/`reader_text_rules`/`library_source_refs` 四表均 `FK→books CASCADE`；`BookDao.runInTransaction`（BookDao.kt:118，@Transaction suspend）与既有测试证明异常回滚语义 |
| DTO 往返无损 | 三个 Export DTO 字段与对应 Entity 1:1（含可空与默认值），逐实体相等断言成立的前提核实 |
| 既有测试零改动 | `git status` 目录级核对（§4） |
| 未越界 | 未改 Gradle/manifest/schema/生产代码/共享文档；未运行任何构建/设备命令 |

## 7. 未执行边界（不得写为通过）

1. **未编译**：Kotlin 语法/类型以人工静态核对为准，未过编译器；`getSharedPreferences`/`deleteSharedPreferences` 的平台类型覆写、`Enumeration.toList()`、`File.invariantSeparatorsPath` 等 stdlib 用法未经编译验证。
2. **未执行**：3 个用例零运行结果；`OK (3 tests)` 是预期，不是证据。
3. 运行期风险（执行窗口应知悉，失败时按此归因）：① `EncryptedSharedPreferences` 初始化在内存 delegate 上的行为未实测（若抛错会走 SecurePrefs 明文回退，仍被内存 prefs 拦截，测试不受影响但 deviceId 生成路径变化）；② `MasterKey` 的 AndroidKeyStore 访问在 MuMu 上未实测；③ `Room.databaseBuilder` 同名 close→reopen 的 WAL checkpoint 行为是标准语义但未在本机验证；④ `d1==d2` 若失败优先核对 Float 在 JSON 往返中的精度（`progress_percent` 走 kotlinx.serialization，理论上无损）。
4. 本测试通过**不**代表：真实 WebDAV 网络路径、跨设备 SAF URI、实体手机 ROM、正式库（该库从不被本测试打开）。

## 8. SEAM REQUEST

**无。** 注入失败用的是生产 schema 真实 FK 约束 + 精心构造的备份 JSON（既有测试已验证的同类技术，注入点选在事务最后一步以扩大回滚判别面）；隔离条件由唯一命名持久库 + 测试自有 `IsolatedSyncTestContext`（含 `deleteSharedPreferences` 防线）+ 默认 `CraIsolatedTestRunner` 满足，全部落在授权文件内，无需生产代码改动。若执行时发现 `EncryptedSharedPreferences` 在内存 delegate 上有不可预期行为，届时再评估是否需要"deviceId 可注入"的生产接缝，不预先申请。

## 9. 判定红线（给复核者）

- 内存 Room 2/2（09-27）与本测试 3/3（若通过）合并后，S14 才算取得"持久库导入 + 重开持久性 + ZIP 文件对账"的模拟器证据；二者各自独立，不得互相冒充。
- 任何 JVM 定向绿灯（`WebDavBackupTest`/`JsonBridgeTest` 等）仍是纯 JVM 契约，不等价于本仪器测试。
- 本测试全称与类名出现在测试 APK 之前，一切"已验证"表述须附 APK SHA-256 与 instrument 原始日志。

落盘即停，等总指挥复核与串行执行安排。不 commit/push/reset/checkout；不覆盖其他窗口修改。

## Codex 静态复核补记（2026-09-28）

- 核对新文件含 3 个 `@Test`，`Room.databaseBuilder` 使用独立库名，书源文件路径由 `IsolatedSyncTestContext` 指向测试根；生产 `LocalZipBackup.importZip` 的顺序确为先 `backup.json` 校验、再 DB 导入、后复制 books。以上为静态检查，不代表设备运行。
- 发现手工构造 `LocalExport` 时缺少必填 `inspirations`、`sessions`；已在新测试文件内补两个空集合。此为 Codex 的两行修正，原窗口交付状态仍是 **未编译、未执行**。
- 执行前还需检查隔离库四件删除是否成功，并以当前测试 APK 哈希、原始 instrumentation 日志和 qahost 数据前后对账来决定是否接受。即使 3/3 通过，也仅覆盖 MuMu 上隔离持久库与本地 ZIP，不扩展成 WebDAV/正式库/实体手机结论。
