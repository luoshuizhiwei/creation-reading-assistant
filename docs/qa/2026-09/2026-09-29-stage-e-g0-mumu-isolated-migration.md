# 阶段 E — G0 Room v15 迁移 MuMu 隔离宿主验收（G0+G2 组合态）（2026-09-29）

> 执行角色：ZCode (GLM 5.3 Flash)，本轮唯一 MuMu/ADB 所有者
> 恢复仓库：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`（本轮对仓库零写入源码、零 index/工作树改动；唯一仓库写入 = 本报告）
> 隔离构建目录：`E:\creation-reading-assistant\stage-e-g0g2-mumu-20260929\`（本轮新建，独占）
> 输入复核：[首批精确候选复核](../../coordination/reviews/2026-09/2026-09-29-stage-e-first-candidates-review.md)、[G0 隔离候选](../../coordination/legacy-stages/2026-09/2026-09-29-stage-e-g0-isolated-candidate.md)、[G2 qahost 候选](../../coordination/legacy-stages/2026-09/2026-09-29-stage-e-qahost-candidate.md)

---

## 0. 结论先行

1. **三个 v15 迁移用例在 MuMu 隔离宿主全部通过**（每例独立 `am instrument`，`OK (1 test)`、退出码 0）：
   `migrate_14_to_15_drops_redundant_term_index`、`migrate_13_to_15_full_chain_keeps_old_data_and_creates_source_refs`、`openingDatabaseRebuildsPartialIndexesAndDoesNotResurrectTermIndex`。
2. **口径声明：本轮设备测试 = HEAD + G0 精确 hunks + G2 精确 hunks 的组合态（G0+G2），不是 G0 单独态。** G0 单独编译态证据在 `stage-e-g0-isolated-20260929`（compile + 2286 JVM 绿）；本轮借用 G2 的 qahost 宿主与 `CraIsolatedTestRunner` 承载 G0 用例（两候选文件集零交集，G2 报告 §5 已证）。
3. **qahost 宿主数据逐字节复原**：`cold-norm == data-sha-final2 == data-sha-after-restore`（同一 SHA `05c4ee3f…`，77 文件）；两 APK 已重装回原件（设备侧 SHA 与备份一致）。无 BLOCKED 项；两次操作事故（合并语法误用、一次 adb push 被 MSYS 路径改写吞掉）均已当场发现并重做，如实记录于 §6。
4. **未覆盖边界**：仅验证 G0+G2 组合态下的 3 个用例；G0 单独态设备行为、U/G2 其余候选、普通 debug 变体 runner 冒烟、真实生产库均不在本轮（§8）。

---

## 1. 候选输入清单（设备测试前输出，全部与候选报告逐项核对一致）

### 1.1 文件与 hunk（隔离树 vs HEAD `5aed5939` 的全部差异 = 以下 9 项）

| # | 文件 | 组 | 选取 hunk | 候选来源 | 候选 SHA-256 |
|---|------|----|-----------|----------|--------------|
| 1 | `android/app/src/main/java/com/creationreadingassistant/data/local/AppDatabase.kt` | G0 | 全 2 hunk（v15 + MIGRATION_14_15） | 工作树（=候选） | `dabb1be7…baf1dd` |
| 2 | `android/app/src/main/java/com/creationreadingassistant/data/local/DatabaseModule.kt` | G0 | 全 3 hunk（注释+注册） | 工作树（=候选） | `ec18fd06…410ca1` |
| 3 | `android/app/src/main/java/com/creationreadingassistant/data/local/dao/SearchTermDao.kt` | G0 | **仅 hunk 5/6** | G0 隔离目录候选 | `038adc4e…8721ce` |
| 4 | `android/app/src/androidTest/java/com/creationreadingassistant/data/local/AppDatabaseMigrationTest.kt` | G0 | 全 8 hunk（3 个 v15 用例） | 工作树（=候选） | `9dd237e4…b571f` |
| 5 | `android/app/schemas/com.creationreadingassistant.data.local.AppDatabase/15.json` | G0 | 新文件 | 工作树（未跟踪） | `c94ca0e5…85b407` |
| 6 | `android/app/build.gradle.kts` | G2 | hunk A/C/D/E，**排除 B** | 工作树回退 hunk B | `92723863…ffee810` |
| 7 | `android/app/src/androidTest/java/com/creationreadingassistant/testing/CraIsolatedTestRunner.kt` | G2 | 新文件 | 工作树（未跟踪） | `343b7fb1…a6b074` |
| 8 | `android/app/src/androidTest/java/com/creationreadingassistant/testing/CraIsolatedTestApplication.kt` | G2 | 新文件 | 工作树（未跟踪） | `286b5250…ac9856` |
| 9 | `android/app/src/androidTest/java/com/creationreadingassistant/feature/reader/IsolatedTestContext.kt` | G2 | 新文件 | 工作树（未跟踪） | `7d97e13b…f099c038` |

### 1.2 候选树构造与验证（只读恢复仓库）

- `git archive --format=tar HEAD`（1693 文件）→ 解包至隔离目录；**本轮 9 个中文文档全部提取成功**（上一版 G0 报告的 5 个失败未复现），全树 diff 确认提取完整。
- G0 的 4 个"全 hunk"文件直接取工作树（工作树=候选，SHA 同 §1.1）；`SearchTermDao.kt` 取 G0 隔离目录已核候选（SHA `038adc4e…`）；`15.json` 与 3 个 G2 新文件取工作树未跟踪原件。
- `build.gradle.kts`：取工作树后**手工回退 hunk B**（versionCode 3→2、versionName 0.5.1→0.5.0、删 4 行注释）。验证：`--strip-trailing-cr` diff vs 工作树 = **恰好 hunk B 区域**；vs HEAD = 恰好 A/C/D/E。字节级另含 7 行 EOL 归一（工作树第 11–16/241 行为 LF、隔离树统一 CRLF；Kotlin DSL 编译无影响，如实登记）。
- 全树 `diff -rq` vs 第二次 HEAD 提取：内容差异**恰好** 5 个候选文件 + 6 个新增（15.json、3 G2 文件、local.properties、archive-stderr.txt），零意外差异。
- hunk 级复核：`SearchTermDao.kt` 隔离 vs 工作树 diff = 恰好 hunk 1/2/3/4/6（候选保留 HEAD 的 `deleteByChapter`/`countTerms`/`rowsByTerm` 与 SearchTermDao 内既有的 `rowsByTermForBasis`/`distinctBookIds`——后者为 HEAD 既有方法，与 hunk 6 新增的 `SearchIndexCoverageDao.distinctBookIds` 是不同接口的同名方法，候选后者不存在）；`index_search_terms_term` 的 `Index(...)` 声明已删（现第 87 行注释文本提及索引名属替换注释内容，非声明）；`index_search_terms_book` 保留。

---

## 2. 隔离构建（E 盘）

| 项 | 值 |
|----|-----|
| 命令 | `JAVA_HOME=D:\develop\Java\jdk-17.0.14 ./gradlew.bat :app:assembleQahost :app:assembleQahostAndroidTest -PcraAndroidTestBuildType=qahost --offline --no-daemon --console=plain --project-cache-dir=<ISO>/android/.gradle-project-cache` |
| 退出码 | **0**，`BUILD SUCCESSFUL in 2m 4s`（72 tasks: 43 executed, 29 from cache） |
| 主 APK | `app/build/outputs/apk/qahost/app-qahost.apk`，SHA-256 `dea430fefbf3b6a6a80ee3691521c5e76e7906e83d3a3badbe7c67c447a0831f` |
| 测试 APK | `app/build/outputs/apk/androidTest/qahost/app-qahost-androidTest.apk`，SHA-256 `0de4ccc4fac3d47b349c31cac51f2ebbeceedea8a4398537b559483b1549bbb7` |
| 测试 APK manifest（aapt 核验） | runner=`com.creationreadingassistant.testing.CraIsolatedTestRunner`（G2 hunk C 生效）、targetPackage=`com.creationreadingassistant.qahost`（hunk D 生效） |
| 构建日志 | `<ISO>/evidence/build-g0g2-qahost.log`，SHA-256 `10838ba0…0c046` |
| 首次构建失败（已重跑） | 首次命令因 `evidence/` 目录不存在导致日志重定向失败、构建未启动（EXIT=1）；`mkdir -p evidence` 后重跑成功。属操作事故，非候选问题。 |

版本口径：候选 APK versionCode=2 / versionName=0.5.0（G1 版本号 hunk 已排除），与已装 0.5.1(3) 构成降级安装，需 `-r -d -t`。

---

## 3. 设备与 qahost 冷备（测试前）

| 项 | 值 |
|----|-----|
| 设备 | MuMu `emulator-5556`（`adb devices` 唯一设备；全程命令显式 `-s emulator-5556`） |
| root | `adb root`（已 root；用于数据目录访问） |
| 已装包 | 仅 `com.creationreadingassistant.qahost`（0.5.1，lastUpdateTime 2026-09-28 11:53:06）+ `.qahost.test`；**正式包 `com.creationreadingassistant` 未安装** |
| 原 APK 留档 | 主 `e82904cc9c1315466ed5c521693f93f541560feef038bf074ce7f7cf8324ef8a`、测试 `fdbd019011088505fa37d71565259a0d76f6927407af69148379e2ef7c27038d`（与 G2 报告记录的 D 阶段产物一致） |
| 冷备 | `am force-stop` 后整目录 pull `/data/data/com.creationreadingassistant.qahost` → **77 文件**，逐文件 SHA-256 清单（`data-cold-sha256.txt`） |
| 主 DB | `databases/creation_reading_assistant_native` SHA `7bd076c6…99a28`（shm `fd4c9fda…`、wal 空 `e3b0c442…`） |
| 设置/文件 | shared_prefs 7 个、datastore 6 个、books 36+8、cache/txt_index 等全部入清单 |

---

## 4. 安装与定向测试

### 4.1 安装

`adb install -r -d -t` 两 APK 均 `Success`；设备侧 SHA-256 与本地构建**逐字节一致**（主 `dea430fe…`、测试 `0de4ccc4…`）。

### 4.2 首次合并语法尝试（操作事故，如实记录）

`-e class Class#m1,m2,m3`（逗号连写三方法）不被该 runner 支持：逗号后被解析为独立类名 → 2 个 `initializationError`（ClassNotFoundException），`Tests run: 3, Failures: 2`，退出码 0。**第 1 个方法 `migrate_14_to_15_drops_redundant_term_index` 在该次已实际执行并通过**。日志原件 `instrument-g0-v15-three-cases.log`（SHA `9a76ca73…298f`）。随后改为逐用例独立调用（§4.3），该事故不构成候选缺陷。

### 4.3 三个 v15 用例定向结果（每例独立 `am instrument -w -e class <Class>#<method>`）

| # | 用例 | 退出码 | 结果 | 耗时 | 日志（SHA-256） |
|---|------|--------|------|------|-----------------|
| 1 | `migrate_14_to_15_drops_redundant_term_index` | 0 | **OK (1 test)** | 0.084s | `instrument-case1-migrate-14-15.log`（`84828bfc…5f0b`） |
| 2 | `migrate_13_to_15_full_chain_keeps_old_data_and_creates_source_refs` | 0 | **OK (1 test)** | 0.085s | `instrument-case2-migrate-13-15.log`（`a012bb53…d23c`） |
| 3 | `openingDatabaseRebuildsPartialIndexesAndDoesNotResurrectTermIndex` | 0 | **OK (1 test)** | 0.093s | `instrument-case3-open-callback.log`（`da464c49…003b`） |

runner=`CraIsolatedTestRunner`（裸 `CraIsolatedTestApplication`，无 Hilt/无 `App.onCreate` 接线）；测试库为独立名 `migration-test` / `migration-open-callback-test`（schema 资产经 `build.gradle.kts:258` 进 androidTest APK）。

---

## 5. 测试后逐项对账与恢复

### 5.1 对账（归一化逐项 diff：冷备 vs 测试后）

| # | 差异 | 定性 | 处置 |
|---|------|------|------|
| 1 | `databases/migration-test` + `migration-test-journal` 新增 | 用例 1/2 的 `MigrationTestHelper` 测试库遗留（用例 3 的 `migration-open-callback-test` 已被 tearDown 清净） | `rm` 删除（root），已记录 `databases-after-test.txt` |
| 2 | `cache/migration-test.lck` + `cache/migration-open-callback-test.lck` 新增（0 字节） | 测试开库的 `.lck` 工件（与既往 S12/S14 测试同类残余） | `rm` 删除 |
| 3 | `no_backup/androidx.work.workdb-shm`/`-wal` 变化（主 `workdb` **未变**） | **G2 报告已登记的已知残余**：`WorkManagerInitializer` 经 ContentProvider 在 instrumentation 进程初始化（裸 Application 也拦不住），WAL 追加帧未 checkpoint 进主库 | 从冷备推回原字节（root `cp` + `chown u0_a70:u0_a70` + `chmod 660`） |
| 4 | `cache/data/user/0/…/androidx.work.workdb.lck`（冷备中的历史嵌套残留）消失 | 测试开库时被系统清理，机制未查 | 从冷备推回原字节（0 字节） |
| — | 其余 75 文件（主 DB、shm/wal、books 44、datastore 6、shared_prefs 7、no_backup workdb 主库等） | **逐字节一致** | 无需处置 |

主 DB `creation_reading_assistant_native` SHA 测试前后均为 `7bd076c6…99a28`——**宿主正式数据零接触**。

### 5.2 恢复验证（三重闭环）

- 清理/推回后全量重哈希：`data-sha-final2.txt` 与 `cold-norm.txt` **完全一致**（`FINAL_DATA_IDENTICAL_TO_COLD`）。
- 重装原 APK（`adb install -r -t`，0.5.1(3) 对候选 0.5.0(2) 为升级）后再次全量重哈希：`data-sha-after-restore.txt` 与冷备**完全一致**（同一 SHA `05c4ee3f…`）。
- 恢复后设备侧包 SHA：主 `e82904cc…4ef8a`、测试 `fdbd0190…27038d`——**与测试前留档完全一致**（`installed-apks-after-restore.txt`）。
- 恢复后 `databases/` 仅剩主库三件；`cache/` 无任何 migration 残留。
- 操作事故记录：第一次恢复推送中 workdb-shm/-wal 的 `adb push` 被 MSYS 路径改写吞掉（目标被改写为 `D:/develop/Git/Git/...`，COPY 实际失败但链上后续 chown 仍回显成功）——最终对账暴露后以 `MSYS_NO_PATHCONV=1` + push 到 `/data/local/tmp` + shell `cp` 重做，复验通过。**恢复以最终哈希对账为准，不以命令回显为准。**

### 5.3 设备其他状态

`accelerometer_rotation=1`、`user_rotation=0` 未动；**密度未改**；未跑 D 门禁整套；未触碰正式包/真实手机/正式书库。

---

## 6. 输入/输出哈希总表

| 项 | SHA-256 |
|----|---------|
| HEAD（构建基线） | `5aed5939600e491843e3c44453921bd6273a233c`（git） |
| 候选 9 文件 | 见 §1.1（`<ISO>/candidate-input-shas.txt`） |
| 主 APK（候选构建） | `dea430fefbf3b6a6a80ee3691521c5e76e7906e83d3a3badbe7c67c447a0831f` |
| 测试 APK（候选构建） | `0de4ccc4fac3d47b349c31cac51f2ebbeceedea8a4398537b559483b1549bbb7` |
| 原 APK（备份/恢复后一致） | 主 `e82904cc…4ef8a` / 测试 `fdbd0190…27038d` |
| qahost 主 DB（前后一致） | `7bd076c60763d9d9a65fa3df990bcd67f2911686cdbcbf4310d37f0588499a28` |
| 冷备清单 = 恢复后清单 = 重装后清单 | `05c4ee3f212066faa7cd640f7c55b35b60789456214406e8b65bc475b9f49489` |
| 构建日志 | `10838ba0…0c046` |
| 三用例日志 | `84828bfc…` / `a012bb53…` / `da464c49…` |
| 合并语法事故日志 | `9a76ca73…298f` |

---

## 7. 未覆盖边界

1. **G0 单独态设备行为未验**：本轮为 G0+G2 组合态（G2 提供 qahost 宿主与隔离 runner）。G0-only 的设备运行需另一个宿主方案，不在本轮。
2. **既有迁移用例未重跑**：仅定向 3 个新增 v15 用例；`AppDatabaseMigrationTest` 既有用例与整套 D 门禁按任务要求未重跑。
3. **workdb WAL 侧文件恢复的机制边界**：`workdb-shm/-wal` 按冷备字节推回；主 `workdb` 未变，恢复语义等价于回到测试前逻辑状态，但未用原 App 冷启动复验 workdb 可打开（避免触碰宿主业务启动路径）。
4. **`cache/…workdb.lck` 消失机制未查**：冷备中的历史嵌套残留在本轮测试中消失，已按字节推回；成因未深究（0 字节 `.lck`，无数据含义）。
5. **真实生产库/真实手机**：全部结论仅适用于 MuMu `emulator-5556` 的隔离 qahost；正式包与真实书库未触碰。
6. **U 候选、G2 普通 debug runner 冒烟**：属其他窗口/后续轮次（首批复核 §15 条 2）。

---

## 8. 证据索引（`E:\creation-reading-assistant\stage-e-g0g2-mumu-20260929\`）

| 类 | 路径 |
|----|------|
| 候选清单 | `g0g2-combined-inventory.md`、`candidate-input-shas.txt` |
| 隔离树 | `android/`（HEAD + G0 + G2；`archive-stderr.txt`、`head-files*.txt` 为验证辅助） |
| 构建 | `evidence/build-g0g2-qahost.log`、`android/app/build/outputs/apk/qahost/`、`android/app/build/outputs/apk/androidTest/qahost/` |
| 冷备 | `qahost-backup/`（`data-cold/` 77 文件、`data-cold-sha256.txt`、`qahost-original-*.apk`、`original-apk-sha256.txt`、`installed-packages-before.txt`） |
| 测试 | `evidence/instrument-case{1,2,3}-*.log`、`instrument-g0-v15-three-cases.log` |
| 对账 | `evidence/cold-norm.txt`、`data-sha-after-test.txt`、`data-sha-final2.txt`、`data-sha-after-restore.txt`、`databases-after-test.txt`、`installed-apks-after-restore.txt` |

落盘即停，交总指挥复核。
