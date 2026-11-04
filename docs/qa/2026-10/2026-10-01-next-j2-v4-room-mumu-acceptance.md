# J2-v4 四项 Room 持久化仪器测试复验与完整恢复对账报告

## 1. 任务编号与版本

- **任务编号/版本**：J2-v4
- **任务性质**：补齐 WorkManager 一致组恢复、核对 J1-v3 候选包合同、复验四项 Room 持久化仪器测试并完成 100% 完整环境恢复对账。
- **报告日期**：2026-10-01
- **上游基准**：
  - 恢复仓库 HEAD：`e39a78da9c62f02614207cffb36969a32492eaea`。
  - 上游裁决输入：[J2-v3 / W1-v1 核查及最小闭合批次](file:///D:/develop/Code/Codex/creation-reading-assistant-recovery-20260919/docs/coordination/reviews/2026-10/2026-10-01-next-j2v3-w1-codex-review.md)。
  - 上游候选产物：[next-round-joint-j1-v3/READY.json](file:///E:/creation-reading-assistant/next-round-joint-j1-v3/READY.json)。
- **本轮验收工件库**：`E:\creation-reading-assistant\next-round-j2-v4-room-acceptance\`。

---

## 2. 输入指纹与合同核查

### 2.1 J1-v3 候选包合同核查（100% 成立）
严格按照合同条款核查 `E:\creation-reading-assistant\next-round-joint-j1-v3`：
1. **构建状态**：`READY.json` 明确 `BUILD_READY: true`。
2. **源码差异单 hunk 限制**：
   比对 J1-v2 与 J1-v3 全源码树，差异且仅有一处（`SyncRepositoryPersistenceTest.kt:356`）：
   ```diff
   - assertEquals("2025-10-09T07:46:40Z", payload["completedAt"]!!.jsonPrimitive.content)
   + assertEquals("2025-10-09T08:53:20Z", payload["completedAt"]!!.jsonPrimitive.content)
   ```
   产品源码、依赖声明及测试用例其他断言零变动。
3. **UTC 时间标准校验**：
   `1_760_000_000_000L` 毫秒 = `1,760,000,000` 秒。经过 Python datetime、JDK java.time 及纯算术三通道验算，其绝对 UTC 时间标准为 **`2025-10-09T08:53:20Z`**，断言修改完全准确。
4. **宿主 APK SHA-256**：
   本地实算为 `ea68aa16038eba81715c719bca4b9df72887faa5b4c91688419ccfe0a95b67f8`，与基线逐位完全一致。
5. **测试 APK SHA-256**：
   本地实算为 `03b2fa0d6b8ce5674ffab4e81e5d4b91ea0266bebb1e082ab92880fcebbd28a3`，与 `READY.json` 记录逐位完全一致。
6. **签名与组件合同**：
   `apksigner verify --print-certs` 退出码均为 0，证书指纹统一为 `05249d786e3d3d117bb5bd7b1f83512a0c9b8f1ee4e502112c4b2d9ccbd7b9a1`，签名完全兼容；`targetPackage` 为 `com.creationreadingassistant.qahost`，`runner` 为 `com.creationreadingassistant.testing.CraIsolatedTestRunner`。

---

## 3. 所有权与改动边界

1. **零代码与工程变更**：未修改任何产品源码、测试代码、Gradle 构建脚本或冻结目录文件。
2. **独占 MuMu 设备**：独占 MuMu 模拟器（`127.0.0.1:16416`），全命令显式带 `-s 127.0.0.1:16416`；操作前冷停，操作后冷停。
3. **零非授权应用操作**：严禁并切实未启动主 UI 界面、未配置 WebDAV、未执行任何 ZIP/JSON 导入，未访问正式书库。
4. **共享仓库唯一可写**：本报告 `docs/qa/2026-10/2026-10-01-next-j2-v4-room-mumu-acceptance.md` 为本轮在共享仓库内唯一创建的文件。

---

## 4. 第一阶段：补齐 WorkManager 一致组恢复（缺口闭合）

在开始复验前，首先对设备当前现场与 J2-v3 停止点进行核实，确认环境无新的用户活动或意外文件变动。
1. **冷停并整组恢复**：
   从 J2-v3 `pre_baseline/data/no_backup/` 将以下一致组原子恢复至设备：
   - `no_backup/androidx.work.workdb`（SHA: `8adff4f16af210efd8076ee72afc4d45407c74c12f37b0e8135f26986b05ebba`）
   - `no_backup/androidx.work.workdb-wal`（SHA: `90189b898bebea8aab38ea0a4d3058fbf76b5863f122605b00695de803a327c6`）
   - `no_backup/androidx.work.workdb-shm`（SHA: `75432009fa6cc5627c5cd13a0e2ee1466d29bdf41dc8180e4f76bef6ff870ec1`）
2. **所有权与权限补全**：
   设置所有者为 `10083:10083`，文件权限为 `600`，保留 SELinux 标签 `u:object_r:app_data_file:s0:c81,c256,c512,c768`。
3. **第一阶段对账结果**：
   设备端 71 个数据文件经实测比对，**71/71 文件 100% 逐位吻合，0 新增、0 缺失、0 变动**！上轮遗留的两文件缺口彻底清零闭合。

---

## 5. 第三阶段：复验与执行结果（4 项测试全绿）

### 5.1 本轮冷备与实装哈希逐位核验
- **系统设置完整基线记录**（含 stay_on 与屏幕设置）：
  - 分辨率：`Physical size: 1080x1920`
  - 密度：`Physical density: 280`
  - 字体缩放：`null`
  - 加速度旋转：`1` / 用户旋转：`0`
  - 充电常亮（stay_on）：`0`
  - 夜间模式：`Night mode: yes`
- **保留数据覆盖安装**：
  执行 `adb install -r -d -t` 分别安装 J1-v3 宿主与测试包，回读设备实装 APK 并计算 SHA-256：
  - 宿主实装 SHA-256：`ea68aa16038eba81715c719bca4b9df72887faa5b4c91688419ccfe0a95b67f8`（**逐位匹配**）。
  - 测试实装 SHA-256：`03b2fa0d6b8ce5674ffab4e81e5d4b91ea0266bebb1e082ab92880fcebbd28a3`（**逐位匹配**）。
  - 安装后 UID 依然为 `10083`，沙箱未重建。

### 5.2 仪器测试执行明细与分析
- **执行命令**：
  ```shell
  adb -s 127.0.0.1:16416 shell am instrument -w -r \
    -e class com.creationreadingassistant.data.repository.SyncRepositoryPersistenceTest \
    com.creationreadingassistant.qahost.test/com.creationreadingassistant.testing.CraIsolatedTestRunner
  ```
- **耗时**：0.707 秒；**退出码**：0（`INSTRUMENTATION_CODE: -1`）。
- **执行统计**：**Tests run: 4, Failures: 0, Errors: 0, Skipped: 0**（`OK (4 tests)`）。

| 测试方法名称 | 状态码 | 判定结果 | 验证要点与断言链分析 |
| :--- | :--- | :--- | :--- |
| **`acceptedRemoteBookKeepsLocalDeviceFactsAcrossReopen`** | `[1, 0]` | **PASS** | 验证远端书籍合并时保留本地事实列（`local_content_path`、`local_uri`、`content_status`），并在数据库 reopen 后保持一致。 |
| **`acceptedRemoteProgressKeepsLocalFactsAndAppliesExplicitValuesAcrossReopen`** | `[1, 0]` | **PASS** | 预置父表 `books` 记录后，外键约束通过；阅读进度本地事实保留与显式值合流逻辑闭环。 |
| **`readingAndShelvedRowsNeverCarryCompletedTimeAfterPullOrPush`** | `[1, 0]` | **PASS** | 在读与在架状态记录在 pull 与 push 双端均成功剥离已读完时间。 |
| **`pullThenLocalProgressChangeThenPushSendsCurrentFactsNotStalePayload`** | `[1, 0]` | **PASS** | **关键复验项通过**：<br>1. pull 获取初始进度成功并持久化；<br>2. 本地推进阅读进度至 0.55 / completed 成功；<br>3. push 请求成功组装并发出；<br>4. wire 反序列化断言通过；<br>5. `completedAt` 正确输出为 `2025-10-09T08:53:20Z`；<br>6. 失败点之后的对端 `filePath` 保留（`/desktop/pk-b1.epub`）及未知扩展字段保留（`customUnknown: keep-me`）断言**全部顺利通过**！ |

---

## 6. 恢复与环境副作用核查（Post-Recovery Reconciliation）

测试完成后，立即冷停应用，清理测试临时文件，并恢复操作前原始 APK 对与一致组数据。

### 6.1 持久化数据隔离性实测
测试运行期间，宿主主数据库 `creation_reading_assistant_native` SHA-256 依然为 `7bd076c6...`，未被触碰；所有 `shared_prefs/*.xml` 零污染。测试临时文件仅限于独立的 `cache/sync_persistence_test.db` 与 MockK 类加载临时 jar，沙箱隔离绝对成立。

### 6.2 最终恢复对账结果（100% 成立）
1. **宿主 APK 恢复**：设备端回读 SHA-256 为 `e82904cc9c1315466ed5c521693f93f541560feef038bf074ce7f7cf8324ef8a`（MATCH=True）。
2. **测试 APK 恢复**：设备端回读 SHA-256 为 `fdbd019011088505fa37d71565259a0d76f6927407af69148379e2ef7c27038d`（MATCH=True）。
3. **应用 UID 恢复**：保持为 `10083`（MATCH=True）。
4. **系统设置恢复**：分辨率、密度、字体缩放、旋转、充电常亮（stay_on: 0）、夜间模式与基线 100% 一致（MATCH=True）。
5. **持久化文件对账**：
   - 持久文件总分母：**71 个**；恢复后文件总数：**71 个**。
   - 逐位 100% 一致文件数：**71 / 71（100%）**。
   - 主数据库 `databases/creation_reading_assistant_native` SHA-256：`7bd076c6...`（完全一致）。
   - WorkManager 一致组三件套（`workdb`, `workdb-wal`, `workdb-shm`）：**逐位完全一致**。
   - 全部 shared_prefs（`sync_config.xml`, `cra_device_identity.xml`, `ai_secrets.xml` 等）：**逐位完全一致**。
   - 新增文件数：**0**；缺失文件数：**0**；变动文件数：**0**。
6. **加密凭据保护**：全过程严格未打开任何加密配置，未触发 `SecurePrefs.open` 的自愈删除逻辑。

---

## 7. 原始证据索引

所有原始日志、证据工件和复现脚本均已固化存储于 `E:\creation-reading-assistant\next-round-j2-v4-room-acceptance\`：
- **第一阶段（WorkManager 补齐恢复）**：
  - `phase1_wm_restore/device_current_data_sha256.log`
  - `phase1_wm_restore/current_vs_v3_stop_point.json`
  - `phase1_wm_restore/device_data_sha256_after_wm_restore.log`
  - `phase1_wm_restore/phase1_recovery_reconciliation.json`
- **第二阶段（J1-v3 合同核查）**：
  - `phase2_contract_check/j1v3_patch.txt`
  - `phase2_contract_check/contract_verification.json`
- **第三阶段（复验与恢复对账）**：
  - `phase3_pre_baseline/system_settings.json`（含 stay_on 设置）
  - `phase3_pre_baseline/device_installed_apk_sha256.log`
  - `phase3_pre_baseline/device_data_sha256.log`
  - `phase3_pre_baseline/data_backup.tar`
  - `phase3_install/device_installed_sha256.log`
  - `phase3_test_run/instrumentation_raw.log`（含 `OK (4 tests)` 控制台输出）
  - `phase3_test_run/instrumentation_logcat.log`
  - `phase3_test_run/test_evaluation.json`
  - `phase3_post_recovery/device_restored_apk_sha256.log`
  - `phase3_post_recovery/restored_system_settings.json`
  - `phase3_post_recovery/device_restored_data_sha256.log`
  - `phase3_post_recovery/final_phase3_report.json`
- **执行脚本**：
  - `scripts/check_current_state.py`
  - `scripts/phase1_restore_workmanager.py`
  - `scripts/phase2_check_contract.py`
  - `scripts/phase3_retest_and_restore.py`

---

## 8. 未覆盖项

1. 本批次限定于四项 Room 持久化仪器测试及 WorkManager 恢复闭合，**不包含 WebDAV 实际 UI 连接/拦截、Legado ZIP 导入、JSON 备份恢复或真实书库操作**。
2. 实体硬件真机、MIUI 系统、TalkBack 辅助功能、R8 混淆发布包不在本轮验收范围。
3. 历史批次的 KeyStore 凭据损失由于缺乏历史运行读写证据，本轮未予篡改或断言恢复。

---

## 9. 交付状态与下一步

- **验收结论**：**`PASS (限定四项 Room 持久化仪器测试验收通过，WorkManager 与 71/71 持久文件恢复 100% 成立)`**。
- **边界说明**：
  本批次通过**仅关闭四项 Room 持久化限定项**，不代表 WebDAV 关卡或完整联合设备关卡通过。
- **停止点确认**：
  设备环境已无瑕疵对账并恢复至初始基线，未修改产品源码或构建系统，未暂存或提交 Git 变更。当前批次任务已全量闭环交付，停止执行，等待 Codex 独立审阅及下一批次任务协调。
