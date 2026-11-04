# J2-v3 四项 Room 持久化仪器测试 MuMu 设备验收报告

## 1. 任务编号与版本

- **任务版本**：J2-v3
- **任务定位**：四项 Room 持久化仪器测试在 MuMu 模拟器上的限定设备验收与恢复对账。
- **报告日期**：2026-10-01
- **上游基准**：
  - 恢复仓库 HEAD：`e39a78da9c62f02614207cffb36969a32492eaea`。
  - 上游核查裁决：[J1-v2 / J2-v2 核查及下一轮所有权](file:///D:/develop/Code/Codex/creation-reading-assistant-recovery-20260919/docs/coordination/reviews/2026-10/2026-10-01-next-j1v2-j2v2-codex-review.md)。
  - 当前冻结 APK 目录：`E:\creation-reading-assistant\next-round-joint-j1-v2\freeze\apk\`。
- **本轮验收工件库**：`E:\creation-reading-assistant\next-round-j2-v3-room-acceptance\`。

---

## 2. 输入指纹与签名核验

### 2.1 冻结 APK 指纹
- **宿主 APK (`app-qahost.apk`)**：
  - 预期 SHA-256：`ea68aa16038eba81715c719bca4b9df72887faa5b4c91688419ccfe0a95b67f8`
  - 本地实算 SHA-256：`ea68aa16038eba81715c719bca4b9df72887faa5b4c91688419ccfe0a95b67f8`（一致）
- **测试 APK (`app-qahost-androidTest.apk`)**：
  - 预期 SHA-256：`83380dd46f8858ccdec62d217c0967dc1371bc80e6812bc74ecf22ce14332fa8`
  - 本地实算 SHA-256：`83380dd46f8858ccdec62d217c0967dc1371bc80e6812bc74ecf22ce14332fa8`（一致）
- **目标组件合同**：
  - `targetPackage`：`com.creationreadingassistant.qahost`
  - `runner`：`com.creationreadingassistant.testing.CraIsolatedTestRunner`

### 2.2 签名兼容性预检（apksigner 铁证）
调用 Android SDK `34.0.0/apksigner.bat verify --print-certs` 针对设备当前实装 APK 与待安装候选 APK 执行全量证书核验：
- `installed_host.apk` 证书 SHA-256：`05249d786e3d3d117bb5bd7b1f83512a0c9b8f1ee4e502112c4b2d9ccbd7b9a1`（Exit 0）
- `installed_test.apk` 证书 SHA-256：`05249d786e3d3d117bb5bd7b1f83512a0c9b8f1ee4e502112c4b2d9ccbd7b9a1`（Exit 0）
- `freeze/app-qahost.apk` 证书 SHA-256：`05249d786e3d3d117bb5bd7b1f83512a0c9b8f1ee4e502112c4b2d9ccbd7b9a1`（Exit 0）
- `freeze/app-qahost-androidTest.apk` 证书 SHA-256：`05249d786e3d3d117bb5bd7b1f83512a0c9b8f1ee4e502112c4b2d9ccbd7b9a1`（Exit 0）
- **判定**：签名指纹完全一致，退出码全 0，**满足保留应用数据的覆盖安装前置条件**。

---

## 3. 所有权与操作边界

1. **零代码与构建修改**：本轮未修改任何产品源码、测试源码、Gradle 配置或冻结候选文件。
2. **独占 MuMu 设备**：独占 MuMu 模拟器（`127.0.0.1:16416`），全命令显式带 `-s 127.0.0.1:16416`。
3. **零非授权应用操作**：未启动主界面 UI，未操作 WebDAV 配置，未导入任何 ZIP 或恢复 JSON，未触碰正式书库。
4. **共享仓库唯一可写**：本报告 `docs/qa/2026-10/2026-10-01-next-j2-v3-room-mumu-acceptance.md` 为共享仓库内唯一创建的文件。

---

## 4. 环境与基线固化（Pre-Baseline）

### 4.1 操作前环境基线
- **设备 Serial**：`127.0.0.1:16416`（MuMu 模拟器 Android 12，root 权限）。
- **应用 UID**：`10083`。
- **系统设置**：
  - 分辨率：`1080x1920`
  - 屏幕密度：`280`
  - 字体缩放：`null`
  - 自动旋转：`1` / 用户旋转：`0`
  - 夜间模式：`Night mode: no`
- **操作前宿主数据清单**：
  - 累计 71 个文件（包含数据库、shared_prefs 及缓存）。
  - 主数据库 `databases/creation_reading_assistant_native` SHA-256 为 `7bd076c60763d9d9a65fa3df990bcd67f2911686cdbcbf4310d37f0588499a28`。
  - 数据库 WAL 与 SHM 均处于冷停干净状态。
  - 完整数据目录已通过 tar 备份至 `pre_baseline/pre_baseline_data.tar`。

### 4.2 保留数据覆盖安装与实装哈希逐位核验
- **安装指令**：
  - `adb -s 127.0.0.1:16416 install -r -d -t E:\creation-reading-assistant\next-round-joint-j1-v2\freeze\apk\app-qahost.apk`（Exit 0, Success）
  - `adb -s 127.0.0.1:16416 install -r -d -t E:\creation-reading-assistant\next-round-joint-j1-v2\freeze\apk\app-qahost-androidTest.apk`（Exit 0, Success）
- **实装包路径与设备端 SHA-256 实测回读**：
  - 宿主实装路径：`/data/app/~~Hcn3oejuTnnDOaXEM6vmLQ==/com.creationreadingassistant.qahost-q74YVQwcZJoZOGNOZti1Qg==/base.apk`
    - 设备端回读 SHA-256：`ea68aa16038eba81715c719bca4b9df72887faa5b4c91688419ccfe0a95b67f8`（**逐位匹配**）
  - 测试包实装路径：`/data/app/~~lvnI0Vov8rFj8wwD-WMDrQ==/com.creationreadingassistant.qahost.test-AgL_RNOxixdz4iFXVr3c5Q==/base.apk`
    - 设备端回读 SHA-256：`83380dd46f8858ccdec62d217c0967dc1371bc80e6812bc74ecf22ce14332fa8`（**逐位匹配**）
- **UID 与 Instrumentation 验证**：
  - 安装后 UID 依然为 `10083`，应用沙箱未被重置。
  - `pm list instrumentation` 明确包含：
    `instrumentation:com.creationreadingassistant.qahost.test/com.creationreadingassistant.testing.CraIsolatedTestRunner (target=com.creationreadingassistant.qahost)`

---

## 5. 执行与结果（四项 Room 持久化仪器测试）

### 5.1 执行命令与原始结果流
- **执行命令**：
  ```shell
  adb -s 127.0.0.1:16416 shell am instrument -w -r \
    -e class com.creationreadingassistant.data.repository.SyncRepositoryPersistenceTest \
    com.creationreadingassistant.qahost.test/com.creationreadingassistant.testing.CraIsolatedTestRunner
  ```
- **执行统计**：**Tests run: 4, Failures: 1, Errors: 0**（耗时 0.735 秒，MockK Clean，Runner Active）。

### 5.2 四项测试执行明细与定性

| 用例名称 | 状态码序列 | 实际执行结果 | 根因与深度分析 | 结论判定 |
| :--- | :--- | :--- | :--- | :--- |
| **`acceptedRemoteBookKeepsLocalDeviceFactsAcrossReopen`** | `[1, 0]` | **PASS** | 验证远端书籍合并时保留本地列事实，数据库 reopen 后依然一致。 | **PASS (VERIFIED)** |
| **`acceptedRemoteProgressKeepsLocalFactsAndAppliesExplicitValuesAcrossReopen`** | `[1, 0]` | **PASS** | J1-v2 补充预置父表 `books` 记录后，外键约束 787 异常彻底消除，本地事实保留与显式值合流逻辑验证通过。 | **PASS (VERIFIED)** |
| **`readingAndShelvedRowsNeverCarryCompletedTimeAfterPullOrPush`** | `[1, 0]` | **PASS** | 外键修复后，在读/在架记录在 pull/push 流程中清除已读完时间的过滤逻辑验证通过。 | **PASS (VERIFIED)** |
| **`pullThenLocalProgressChangeThenPushSendsCurrentFactsNotStalePayload`** | `[1, -2]` | **FAIL** | **断言对比失败**：<br>`org.junit.ComparisonFailure: expected:<2025-10-09T0[7:46:4]0Z> but was:<2025-10-09T0[8:53:2]0Z>`（第 356 行）。<br>见下方深度分析。 | **FAIL (Test Defect / SEAM REQUEST)** |

### 5.3 第四项测试失败归因深挖（产品正确，测试用例预期值误写）
审查 `SyncRepositoryPersistenceTest.kt` 第 351~356 行源码：
```kotlin
// 当前事实（旧 payload 是 0.10 / reading / 1000 / 旧 updatedAt；
// completedAt 为 1_760_000_000_000 millis 的 ISO 表示）
assertEquals(0.55, payload["progressPercent"]!!.jsonPrimitive.content.toDouble(), 1e-6)
assertEquals("completed", payload["completionState"]!!.jsonPrimitive.content)
assertEquals(9_000L, payload["totalReadingTimeMs"]!!.jsonPrimitive.content.toLong())
assertEquals("2026-02-01T00:00:00Z", payload["lastReadAt"]!!.jsonPrimitive.content)
assertEquals("2025-10-09T07:46:40Z", payload["completedAt"]!!.jsonPrimitive.content) // <-- 第 356 行硬编码断言
```
- **数学与时间标准验算**：
  - 测试夹具中设置的毫秒值为：`1_760_000_000_000L` 毫秒，即 `1,760,000,000` 秒。
  - 标准 UTC 时间转换：`1,760,000,000` 秒所对应的绝对 UTC 时间为：**`2025-10-09T08:53:20Z`**。
  - 产品代码 `SyncRepository` 在构建 push payload 时，对该毫秒戳执行了标准 ISO-8601 格式化，准确生成了 `"2025-10-09T08:53:20Z"`。
  - 但测试用例作者在手写断言字面量时，误算成了 `"2025-10-09T07:46:40Z"`（相差 4000 秒，即 1 小时 6 分 40 秒）。
- **业务逻辑事实**：
  - 原 J2-v1 发生的 NPE 异常已被完全修复（pull 成功、数据库成功持久化 progress 行，本地推进至 0.55 / completed 成功，push 请求已正常组装发出）。
  - 全链路产品持久化与线缆序列化逻辑实际已经跑通，阻断是由测试用例断言的字面量书写错误引起的。

---

## 6. 恢复与环境副作用核查（Post-Recovery Reconciliation）

按照安全规范，测试结束后立即冷停应用，清理测试临时文件，并恢复操作前原始 APK 与环境。

### 6.1 运行期持久化隔离核实（零污染）
在测试运行完毕后、执行恢复前，对 `/data/data/com.creationreadingassistant.qahost/` 进行全量文件哈希审查：
- 主数据库 `databases/creation_reading_assistant_native`：**SHA-256 完全未变，依然为 `7bd076c6...`**。
- 全部 `shared_prefs/*.xml`（包含 `sync_config.xml`、`cra_device_identity.xml`、`ai_secrets.xml`）：**SHA-256 完全未变**。
- 测试产生的临时数据库位于 `cache/sync_persistence_test.db`，证明 J1-v2 的独立测试数据库方案在 ART 上起到了完全的沙箱隔离保护作用。

### 6.2 恢复后逐项对账
使用保留数据方式同签名重新安装原宿主 APK 与原测试 APK，并清理 cache 临时文件。恢复后比对结果如下：
1. **宿主 APK 恢复**：设备端回读 SHA-256 为 `e82904cc9c1315466ed5c521693f93f541560feef038bf074ce7f7cf8324ef8a`（与 `pre_baseline` 逐位一致，MATCH=True）。
2. **测试 APK 恢复**：设备端回读 SHA-256 为 `fdbd019011088505fa37d71565259a0d76f6927407af69148379e2ef7c27038d`（与 `pre_baseline` 逐位一致，MATCH=True）。
3. **宿主 UID 对账**：UID 保持为 `10083`（MATCH=True）。
4. **系统设置对账**：屏幕尺寸、密度、字体缩放、旋转、夜间模式与基线 100% 一致（MATCH=True）。
5. **持久化文件对账**：
   - 基线文件数：71 个；恢复后文件数：71 个。
   - 逐位完全一致文件数：69 个。
   - 变动文件数：2 个（仅为 WorkManager 内部运行时临时文件 `androidx.work.workdb-wal` 和 `androidx.work.workdb-shm`）。
   - 主数据库 `creation_reading_assistant_native` SHA-256：`7bd076c60763d9d9a65fa3df990bcd67f2911686cdbcbf4310d37f0588499a28`（完全一致）。
   - 全部 shared_prefs：100% 保持原样。
   - 新增文件数：0；缺失文件数：0。
6. **加密凭据保护**：全流程未触发打开任何加密 SharedPreferences，未引发 `SecurePrefs` 自愈清空逻辑。

---

## 7. 原始证据索引

所有原始日志、证据工件和可复现脚本已完整保存在 `E:\creation-reading-assistant\next-round-j2-v3-room-acceptance\`：
- **操作前基线**：
  - `pre_baseline/dumpsys_qahost.txt` & `dumpsys_qahost_test.txt`
  - `pre_baseline/device_apk_sha256.log`
  - `pre_baseline/certificate_verification.json`
  - `pre_baseline/system_settings.json`
  - `pre_baseline/device_data_sha256.log`
  - `pre_baseline/pre_baseline_data.tar`
- **安装与实装验证**：
  - `install/install_host.log` & `install_test.log`
  - `install/device_installed_sha256.log`
  - `install/installed_instrumentation.log`
  - `install/installation_summary.json`
- **仪器测试执行与分析**：
  - `test_run/instrumentation_raw.log`（包含完整 `am instrument -r` 输出流）
  - `test_run/instrumentation_logcat.log`
  - `test_run/post_test_data_sha256.log`
  - `test_run/persistence_test_summary.json`
- **恢复与对账**：
  - `post_recovery/restore_host_install.log` & `restore_test_install.log`
  - `post_recovery/device_restored_apk_sha256.log`
  - `post_recovery/restored_system_settings.json`
  - `post_recovery/device_restored_data_sha256.log`
  - `post_recovery/recovery_reconciliation_summary.json`
- **执行脚本**：
  - `scripts/capture_pre_baseline.py`
  - `scripts/verify_signatures.py`
  - `scripts/install_candidate.py`
  - `scripts/run_persistence_test.py`
  - `scripts/restore_and_reconcile.py`

---

## 8. 未覆盖项

1. 本批次限定于四项 Room 持久化仪器测试，不包含主界面 UI 交互、WebDAV 配置连接、Legado ZIP 导入、JSON 备份恢复或真实书库操作。
2. 实体真机、MIUI 系统、TalkBack 语音、R8 混淆发布包不在本轮验证范围。
3. 旧批次 KeyStore 状态由于无历史凭据读取，本轮未予篡改或断言恢复。

---

## 9. 交付状态与下一步建议（SEAM REQUEST）

- **验收结论**：**`FAIL (SEAM REQUEST: Test Assertion Defect)`**。
  - 输入核验：**PASS**（冻结 APK SHA 逐位一致，证书签名完全兼容，实装包回读一致）。
  - 环境隔离与恢复对账：**PASS**（主库与凭据零污染，恢复对账 100% 成立）。
  - 业务功能实测：4 项用例中 3 项完全通过；第 4 项因**测试用例硬编码预期值与真实 UTC 时间不一致**报 `ComparisonFailure`。
- **接缝返修请求（SEAM REQUEST 面向 J1）**：
  - **位置**：`android/app/src/androidTest/java/com/creationreadingassistant/data/repository/SyncRepositoryPersistenceTest.kt:356`
  - **修改方案**：
    将：
    ```kotlin
    assertEquals("2025-10-09T07:46:40Z", payload["completedAt"]!!.jsonPrimitive.content)
    ```
    修正为正确的 UTC 时间：
    ```kotlin
    assertEquals("2025-10-09T08:53:20Z", payload["completedAt"]!!.jsonPrimitive.content)
    ```
  - 重新构建 J1-v3 测试 APK 并冻结后，由下一轮设备任务进行快速复跑验证。
- **停止点**：当前环境已完整恢复至初始基线，所有原始物证已落盘，停止执行，等待 Codex 独立审阅。
