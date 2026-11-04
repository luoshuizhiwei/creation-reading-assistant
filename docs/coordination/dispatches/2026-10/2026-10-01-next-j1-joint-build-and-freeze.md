# J1 — 联合候选集成门禁与冻结报告（2026-10-01）

- 输入：`E:\creation-reading-assistant\next-round-joint-20261001\android`（总指挥准备的联合候选：基线 `git archive e39a78da` + 5 精确文件），`source-manifest.csv` + `READY.json`（SOURCE PREPARED）已先行读取。
- 所有权：本轮独占 `next-round-joint-20261001` 的源码、构建与冻结元数据；独占 Gradle 缓存 `next-round-joint-20261001/_cache/gradle-home`（自 R1 缓存副本派生，2.6G，仅 3 个在用 `.lock` 跳过——cp 继续复制其余全部文件）。未操作 MuMu、未 commit/push/reset/checkout、未改共享产品源码与原损坏仓库。
- 结论先行：**联合门禁全部通过（BUILD_READY=true）；设备验收未做（DEVICE_ACCEPTED=false，属窗口 2）。** 完整 JVM 2673/2673（唯一用例实算核验，未拼接 R1/R2 重叠计数）、Lint 0 errors/7 warnings、debug/debug-androidTest/qahost/qahost-androidTest/release-unsigned 五产物全部构建成功并冻结。无集成失败，无产品源码修复。

## 1. 输入核验（任务项 1）

- **5 文件 SHA 与清单一致**：`_evidence/step1-sha-verify.txt` — SyncRepository.kt（3e346793…）、WebDavBackup.kt（4b1e0d3a…）、app/build.gradle.kts（e135f62e…，R3 版）、SyncRepositoryLocalFactsTest.kt（e2c9d515…）、WebDavCompatibilityTest.kt（fc167ff8…）全部 OK。
- **来源 HEAD 全树核验**：对 `git archive e39a78da` 新鲜解包逐文件 SHA 对比——候选 874 文件 = 基线 872 + 恰好 2 新增 + 3 修改（即 5 清单文件），0 缺失、无其他差异（`step1-head-diff.txt`；脚本末行布尔输出 False 为对照前缀笔误，diff 清单本身即为判定依据，已加注说明）。

## 2. 集成新增：R1 持久化仪器验证（任务项 4）

R1 原专项测试用 mock DAO 只断言"DAO 收到的实体"；本轮补真 Room 持久化事实：

- **新增** `android/app/src/androidTest/java/com/creationreadingassistant/data/repository/SyncRepositoryPersistenceTest.kt`（4 用例，SHA f14bc703…）：
  1. `acceptedRemoteBookKeepsLocalDeviceFactsAcrossReopen` — 接受远端新版书籍后，本机 local_content_path/local_uri/content_status/size/cover_data_url/original_file_name 同连接与**重开数据库**后均保持，远端标题/revision 合并落库；
  2. `acceptedRemoteProgressKeepsLocalFactsAndAppliesExplicitValuesAcrossReopen` — 本机进度/时长/完成态/定位缺键保持 + 远端显式值（0.32→32%、completedAt ISO→millis）采纳，跨重开仍在；
  3. `pullThenLocalProgressChangeThenPushSendsCurrentFactsNotStalePayload` — pull 旧 payload → 本地推进提升列 → push 经**真实 Retrofit/OkHttp/kotlinx.serialization 协议栈**（进程内 MockWebServer 受控桌面端）发出 0.55/completed/9000/当前 metadata/未知键保留/对端 filePath 保留；
  4. `readingAndShelvedRowsNeverCarryCompletedTimeAfterPullOrPush` — 远端只带 completedAt 时 reading/shelved 两端（落库与 wire payload）都不带完成时间。
- **隔离纪律**：私有库 `sync_persistence_test.db`（永不重名正式库，@Before/@After 删四件）；中性合成数据，pull/push 不触碰书库文件；设备侧触碰如实登记——真实 `SyncConfigStore`/`DeviceInfoProvider`（final 类，androidTest 无 mockk）读写 qahost 测试应用 `sync_config` 加密 prefs（@After `clear()`）与 AndroidKeyStore 实例别名（幂等非用户数据），窗口 2 按协议对 qahost 冷备/恢复；MockWebServer 为进程内 127.0.0.1 明文（network_security_config 有意放行 cleartext）。
- **最小依赖（已记录）**：`app/build.gradle.kts` 增加 `androidTestImplementation(libs.okhttp.mockwebserver)`（版本随主 okhttp 4.12.0），J1 新增 diff 见 `freeze/patches/joint-diff-build.gradle.patch`。**最终范围为 6 文件，不再是 5**：更新清单 `source-manifest-j1.csv`（build.gradle.kts 新 SHA 4c7b7a78… + 新增 androidTest 文件），原始 5 文件清单保留为输入记录。

## 3. 联合门禁结果（任务项 2/3/5）

| 门禁 | 命令 | 退出码 | 结果 |
|---|---|---|---|
| 完整 JVM | `:app:testDebugUnitTest` | 0 | **2673 tests / 0 failures / 0 errors / 0 skipped**（287 个 XML 类文件；唯一 (class,testcase) 对实算=2673，与 SUM 一致——基线 2628 + R1 新增 21 + R2 新增 24，逐项吻合，未拼接旧计数；`evidence/jvm-xml-count.txt`） |
| Lint | `:app:lintDebug` | 0 | **0 errors / 7 warnings**（与阶段 E 收口基线一致；`evidence/lint-results-debug.txt`） |
| debug 对 | `:app:assembleDebug` + `:app:assembleDebugAndroidTest` | 0 / 0 | `app-debug.apk` + `app-debug-androidTest.apk` |
| qahost 对 | `:app:assembleQahost` + `:app:assembleQahostAndroidTest -PcraAndroidTestBuildType=qahost` | 0 / 0 | `app-qahost.apk` + `app-qahost-androidTest.apk` |
| Release 未签名 | `:app:assembleRelease` | 0 | `app-release-unsigned.apk`；apksigner verify → **DOES NOT VERIFY（Missing META-INF/MANIFEST.MF），exit 1**——未签名实证 |

- **测试目标与 runner 核验**（aapt dump，`evidence/test-targets.txt`）：debug 测试 APK `com.creationreadingassistant.test` → runner `com.creationreadingassistant.testing.CraIsolatedTestRunner`，targetPackage `com.creationreadingassistant`；qahost 测试 APK `com.creationreadingassistant.qahost.test` → 同 runner，targetPackage `com.creationreadingassistant.qahost`。主包 `com.creationreadingassistant` / `com.creationreadingassistant.qahost`，versionCode 3 / versionName 0.5.1。
- **签名回退配置未因合流失效**（任务项 5）：`joint-diff-build.gradle.patch` 显示 R3 逻辑原样——release 默认 `signingConfig = null`（未签名），仅 `-PcraAllowDebugSigningFallback=true` 显式启用 debug 兜底；本轮实测走默认分支（configure 日志输出未签名提示），未生成正式密钥、未用 fallback 构建、未发布未安装。
- 如实记录的执行注记：首次联合调用把 debug/qahost androidTest 任务合并进一条命令失败（`assembleDebugAndroidTest` 在 `-PcraAndroidTestBuildType=qahost` 下不存在——该属性通过 `testBuildType` 全局切换 androidTest 变体），随即改为两次调用完成；失败日志保留于 `evidence/gate-main.log`，属驱动命令错误而非候选集成缺陷。

## 4. 冻结（任务项 7/8）

冻结目录 `E:\creation-reading-assistant\next-round-joint-20261001\freeze\`（独立于 android/ 构建输出，后续构建不覆盖）：

- `apk/`（5 个，SHA-256 见 `apk/sha256.txt`）：
  - `app-debug.apk` `dfd52cf4297a078db0469e8e4632f607be8f0dbf7063d46385f06d0c477b0408`
  - `app-debug-androidTest.apk` `1e01424c4e518347e99ee5429c27f810c5c17de6cfcefa50bc5548e2d42008e6`
  - `app-qahost.apk` `ea68aa16038eba81715c719bca4b9df72887faa5b4c91688419ccfe0a95b67f8`
  - `app-qahost-androidTest.apk` `0deb20074495d831a387a41a7c7d2f51ed8fb7897aeed68fb2206b89d0fa28cb`
  - `app-release-unsigned.apk` `383fbd8a7039e20ed532ba8c744bbdd25ca6f8f2d010fd329a9f48c2a08bf3cd`
- `patches/`：`joint-diff-SyncRepository.patch`（430 行，与 R1 二轮一致）、`joint-diff-WebDavBackup.patch`（318 行，R2 原样）、`joint-diff-build.gradle.patch`（31 行，R3 + J1 依赖追加）。
- `source-manifest-j1.csv`（最终 6 文件清单）+ 原始 `source-manifest.csv`；`READY.json`（BUILD_READY=true / DEVICE_ACCEPTED=false）+ 原始 SOURCE PREPARED 备份。
- `evidence/`：门禁日志 ×4、JVM XML 实算、Lint 报告、输入核验 ×2、测试目标 aapt 输出。
- 源码 SHA（最终态）：SyncRepository.kt `3e346793…`（未改）、WebDavBackup.kt `4b1e0d3a…`（未改）、build.gradle.kts `4c7b7a78…`、SyncRepositoryLocalFactsTest.kt `e2c9d515…`（未改）、WebDavCompatibilityTest.kt `fc167ff8…`（未改）、SyncRepositoryPersistenceTest.kt `f14bc703…`（新增）。

`READY.json` 已更新：**BUILD_READY=true、DEVICE_ACCEPTED=false**，含冻结清单、APK 对、SHA 与新设备测试类名 `com.creationreadingassistant.data.repository.SyncRepositoryPersistenceTest`。

## 5. 设备验收入口（交窗口 2）

1. 安装对（二选一，按验收范围）：
   - qahost：`adb install -r -t freeze/apk/app-qahost.apk` + `freeze/apk/app-qahost-androidTest.apk`
   - debug：`freeze/apk/app-debug.apk` + `freeze/apk/app-debug-androidTest.apk`
2. 全量仪器：`adb shell am instrument -w com.creationreadingassistant.qahost.test/com.creationreadingassistant.testing.CraIsolatedTestRunner`（debug 对把包名换为 `com.creationreadingassistant.test`）。
3. R1 持久化专项：`adb shell am instrument -w -e class com.creationreadingassistant.data.repository.SyncRepositoryPersistenceTest com.creationreadingassistant.qahost.test/com.creationreadingassistant.testing.CraIsolatedTestRunner`。
4. 按协议：先 `adb devices` 核对 serial、测试前冷备 qahost 持久数据/APK/设置（本测试登记的 `sync_config` prefs 与 KeyStore 别名触碰在冷备范围内）、结束后逐项恢复；不安装正式包、不访问正式服务。

## 6. 边界与未验项

- DEVICE_ACCEPTED=false：MuMu/设备行为（WebDAV 真实入口与请求边界、同步本地事实与持久状态、恢复协议）由窗口 2 执行，本轮不外推。
- 新仪器测试本轮只编译打包、未执行（无设备任务）；其运行期行为（SecurePrefs/KeyStore/MockWebServer 端口绑定）在设备上的实际表现由窗口 2 首跑确认，失败时按范围回修。
- N1 进程中断证据不外推本联合 APK；R8 运行、实际 TalkBack、实体硬件、正式签名/发布仍未验。
- 已登记接缝不变：无 filePath 记录/墓碑的桌面投递、shelved 跨桌面词汇——不通过伪造 filePath 或删除资产关闭。
