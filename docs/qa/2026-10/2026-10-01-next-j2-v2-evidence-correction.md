# J2-v2 设备验收证据勘误与恢复边界核查报告

## 1. 任务编号与版本

- **任务版本**：J2-v2
- **任务性质**：设备验收证据只读勘误、物证冲突复核与恢复边界审查。严格执行零 ADB 设备交互、零产品代码修改、零环境配置变更。
- **报告日期**：2026-10-01
- **上游裁决基准**：[Codex review 2026-10-01-next-j1-j2-codex-review.md](file:///D:/develop/Code/Codex/creation-reading-assistant-recovery-20260919/docs/coordination/reviews/2026-10/2026-10-01-next-j1-j2-codex-review.md) 及 [verification.json](file:///E:/creation-reading-assistant/next-j1-j2-codex-review-20261001/verification.json)。
- **归档保留原报告**：[2026-10-01-next-j2-joint-mumu-acceptance.md](file:///D:/develop/Code/Codex/creation-reading-assistant-recovery-20260919/docs/qa/2026-10/2026-10-01-next-j2-joint-mumu-acceptance.md)。
- **本轮核查矩阵输出**：[evidence_correction_matrix.json](file:///E:/creation-reading-assistant/next-round-j2-v2-evidence-review/evidence_correction_matrix.json)。

---

## 2. 所有权与改动边界

1. **零代码与工程修改**：严禁修改任何 Android 源码（`android/app/src/`）、构建脚本、Gradle 配置、J1 冻结产物或工作树已有 WIP 文件。
2. **零设备交互与侵入**：严禁向 MuMu 模拟器（`127.0.0.1:16416`）发送任何 `adb` 命令；不执行应用安装、卸载、拉起、冷备回灌、数据拉取，不修改系统屏幕密度、夜间模式、保活或旋转状态。
3. **共享仓库唯一可写**：本报告 `docs/qa/2026-10/2026-10-01-next-j2-v2-evidence-correction.md` 为本轮在共享仓库内唯一允许创建并写入的文件。
4. **证据与工作区隔离**：所有审查衍生的结构化数据写入 `E:\creation-reading-assistant\next-round-j2-v2-evidence-review\`；原 `E:\creation-reading-assistant\next-round-j2-acceptance\` 下的所有原始日志、截图、XML 倾倒和冷备数据保持严格只读，绝不进行就地修改、事后补造或覆盖。

---

## 3. 核查环境与审计对象

- **操作系统**：Windows 11
- **目标设备声明**：MuMu 模拟器 Android 12（宿主序列号声明为 `127.0.0.1:16416`）；本轮未连接设备，未刷新设备 serial；报告严格遵循用户规则，绝不将 MuMu 模拟器称作实体手机或 MIUI。
- **审计对象**：
  - 恢复仓库 HEAD：`e39a78da9c62f02614207cffb36969a32492eaea`。
  - 冻结 APK 元数据：[READY.json](file:///E:/creation-reading-assistant/next-round-joint-20261001/READY.json) 与五个冻结 APK 文件。
  - JVM 单元测试结果：`android/app/build/test-results/testDebugUnitTest/` 下全部 287 份 XML 报告。
  - 真 Room 仪器测试日志：`E:\creation-reading-assistant\next-round-j2-acceptance\logs\instrumentation_persistence_test.log`。
  - WebDAV 自动化套件汇总：`E:\creation-reading-assistant\next-round-j2-acceptance\logs\acceptance_suite_summary.json`。
  - WebDAV 服务请求流水：`E:\creation-reading-assistant\next-round-j2-acceptance\logs\webdav_requests.jsonl` 及受控服务端代码 `controlled_webdav_server.py`。
  - UI 证据文件：`E:\creation-reading-assistant\next-round-j2-acceptance\evidence\` 下的全部 XML 层次倾倒与截屏。
  - 宿主冷备数据：`E:\creation-reading-assistant\next-round-j2-acceptance\cold_backup\data\` 下的 67 份文件。

---

## 4. 执行与核查结果

### 4.1 Standards 轴：哈希勘误、用例计数与恢复边界

#### 1. APK 哈希勘误与实装身份断裂
- **冻结 APK 真实哈希复核**：
  对 `E:\creation-reading-assistant\next-round-joint-20261001\` 下的冻结 APK 重新实算 SHA-256，核对结果如下：
  - **宿主 APK (`app-qahost-debug.apk`)**：
    - 实际计算 SHA-256：`ea68aa16038eba81715c719bca4b9df72887faa5b4c91688419ccfe0a95b67f8`
    - J2 原报告记录：`ea68aa168f69eb070014aa24683056c70281b315ebdbffae37eb64cfb39e2ec8`
    - **差异分析**：仅前 8 位十六进制字符一致，后 56 位完全错误！
  - **测试 APK (`app-qahost-debug-androidTest.apk`)**：
    - 实际计算 SHA-256：`0deb20074495d831a387a41a7c7d2f51ed8fb7897aeed68fb2206b89d0fa28cb`
    - J2 原报告记录：`0deb2007e0c4f82631525287f394200636cf4fe5df1efc8f38d0113d09a5df7c`
    - **差异分析**：同样仅前 8 位字符一致，后 56 位完全错误！
- **实装 APK 身份断裂（未验证）**：
  J2 原报告声称设备实装 APK 与冻结 APK “逐位匹配（Bit-for-bit matched）”。但经对整个证据目录的全面检索，**未发现任何包含 `adb shell sha256sum` 完整命令执行标准输出的原始终端回读日志**。原报告仅展示了截断的前 8 位短前缀。在缺乏原始全量回读物证的情况下，无法证明当时设备上实际运行的正是此冻结构建对。根据严格审计原则，实装 APK 哈希核验状态必须更正为 **`NOT VERIFIED`（未验证）**，严禁以短前缀假定输入身份合规。

#### 2. JVM 专项用例数勘误
原 J2 报告直接沿用了旧批次用例数，与实际测试套件严重脱节：
- **R1 专项用例数**：原报告写为 14 项；经逐份解析原始 XML，实际为 **21 项**（`SyncRepositoryLocalFactsTest` 12 项 + `WebDavSecurityTest` 9 项）。
- **R2 专项用例数**：原报告写为 18 项；实际 `WebDavCompatibilityTest` XML 包含 **24 项**。
- **全量 JVM 统计复核**：287 份 XML 报告，累计运行 **2673 项**测试，Failures: 0, Errors: 0, Skipped: 0。全量通过数据真实无误。

#### 3. 宿主数据恢复边界未满足（KeyStore 与 SecurePrefs 自愈擦除灾难）
- **代码隐患分析**：
  审查 `com.creationreadingassistant.data.security.SecurePrefs.kt`：
  ```kotlin
  fun open(context: Context, name: String = "secure_prefs"): SharedPreferences {
      return try {
          val masterKey = MasterKey.Builder(context)
              .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
              .build()
          EncryptedSharedPreferences.create(...)
      } catch (t: Throwable) {
          Log.e(TAG, "Failed to open EncryptedSharedPreferences, resetting...", t)
          context.deleteSharedPreferences(name) // 致命隐患：解密失败静默物理删除！
          ...
      }
  }
  ```
- **灾难性机制**：
  J2 原执行流程中采用了“卸载应用 -> 安装新 APK -> 将备份 data 推回设备”的操作路径。
  在 Android 系统中，**卸载应用（`pm uninstall`）会彻底抹除 AndroidKeyStore 中由系统保管的主密钥（MasterKey）**。当备份的 `shared_prefs/secure_prefs.xml` 和 `sync_config.xml` 被推回新安装的应用沙箱后，应用首次拉起调用 `SecurePrefs.open` 时，由于旧主密钥已丢失，必然抛出解密异常。
  此时，`catch (t: Throwable)` 代码块被触发，**静默执行 `context.deleteSharedPreferences(name)` 将旧加密数据物理删除**！
- **虚假安全感**：
  由于书籍表、历史记录等 Room SQLite 数据库是非加密存储，冷备回灌后书架列表能正常展示图书，给操作者造成了“数据与环境已完整恢复”的**严重假象**。而实际上，用户的 WebDAV 访问令牌、密码、配对密钥及 AI 密钥已在拉起瞬间被自愈代码静默清空。原 J2 未提供任何恢复后加密凭据可解密读取的验证证据，恢复边界宣告失败。

---

### 4.2 Spec 轴：真 Room 仪器测试与 WebDAV 自动化物证冲突

#### 1. 真 Room 仪器测试 4 项中 3 失败，不可支持产品全绿
原日志 `E:\creation-reading-assistant\next-round-j2-acceptance\logs\instrumentation_persistence_test.log` 明确记录：`Tests run: 4, Failures: 3, Errors: 0`。
四项用例的真实执行情况与定性如下：
1. **`acceptedRemoteBookKeepsLocalDeviceFactsAcrossReopen`**：**PASS**
   - **可支持结论**：验证了 `books` 表在合流远端书籍时，能正确保留本设备的本地事实列（`local_content_path`、`local_uri`、`content_status`），并在 DB reopen 后保持一致。
   - **边界限制**：该用例仅涉及 `books` 表单表操作，不能外推证明整个同步持久化机制正确。
2. **`acceptedRemoteProgressKeepsLocalFactsAndAppliesExplicitValuesAcrossReopen`**：**FAIL**
   - **失败根因**：抛出 `android.database.sqlite.SQLiteConstraintException: FOREIGN KEY constraint failed (code 787)`。
   - **机制分析**：`ReadingProgressEntity` 定义了外键约束 `foreignKeys = [ForeignKey(entity = BookEntity::class, parentColumns = ["id"], childColumns = ["book_id"])]`。测试夹具直接向 `reading_progress` 插入记录，未事先在父表 `books` 插入对应的图书主键，被 SQLite 外键约束严格拦截。
   - **定性**：属于测试代码夹具编写缺陷，导致阅读进度的本地事实保留与显式值合流逻辑**根本未经验证（NOT VERIFIED）**。
3. **`readingAndShelvedRowsNeverCarryCompletedTimeAfterPullOrPush`**：**FAIL**
   - **失败根因**：抛出相同的 `SQLiteConstraintException: FOREIGN KEY constraint failed (code 787)`。
   - **定性**：在读/在架记录在 pull/push 流程中清除已读完时间的过滤逻辑**根本未经验证（NOT VERIFIED）**。
4. **`pullThenLocalProgressChangeThenPushSendsCurrentFactsNotStalePayload`**：**FAIL**
   - **失败根因**：在测试代码第 249 行抛出 `java.lang.NullPointerException`。
   - **机制分析**：由于初始 `pull()` 阶段因缺少父表图书导致 progress 插入失败，后续从本地按 `book_id` 查询进度对象时返回了 `null`。测试日志缺少 `failedItems` 的明细打印。
   - **定性**：pull 成功后推进本地事实再 push 的全链路回归**根本未经验证（NOT VERIFIED）**。

> **勘误结论**：原 J2 报告以“夹具缺乏预置数据，产品逻辑无需修复”为由将仪器测试判定为全部通过，属于严重违规认定。未通过的 3 项业务路径在真实 Room 数据库上完全缺乏有效证据，必须纠正为 **`NOT VERIFIED`**。

#### 2. WebDAV 自动化与原物证严重冲突（Spec 4 虚假拦截与请求泄漏）
在 Spec 4（安全合规拦截）核查中，发现了严重的事实冲突：
- **物证冲突事实**：
  1. 自动化汇总文件 `acceptance_suite_summary.json` 中明确记录：
     ```json
     "pass_userinfo": false,
     "pass_cleartext": false,
     "spec4_requests_count": 2
     ```
     两项安全用例的自动化判定**均为 false**，且均有 1 次外发 HTTP 请求打到了受控服务器！
  2. UI 层次转储文件 `spec4_userinfo_blocked.xml`（及对应截图 `spec4_userinfo_blocked.png`）中，赫然显示连接结果文本为：
     **`WebDAV 连接成功（HTTP 200）`**！
- **成因深挖（Compose 状态与 ADB 输入脱节）**：
  - 界面采用 Jetpack Compose 构建，URL 输入框为 `OutlinedTextField`。
  - 自动化脚本使用盲打的 `adb shell input text` 灌入包含 userinfo 的测试 URL（`http://user:pass@127.0.0.1:18080/dav/`）。
  - 但由于输入框未获得焦点或未触发 Compose 的 `onValueChange` 事件，Compose 内部的 `url` 状态变量**根本没有更新**！
  - 随后脚本点击“测试连接”按钮，应用实际上读取的是上一测试用例留存在配置中的**合法 Mock 服务 URL**（`http://127.0.0.1:18080/dav/`），向受控服务器发起了真实的 HEAD/PROPFIND 请求并成功返回 HTTP 200！
- **后续单步补测的有效性缺失**：
  - 虽然原执行者在事后编写了单步脚本 `test_userinfo.py` 和 `test_cleartext.py`，通过显式聚焦输入实现了 0 请求，但由于后续操作调用了受控服务器的 reset 接口（见下文），单步补测期间的受控服务端日志已被物理抹除。
  - 缺乏连续完整的请求日志流水支撑，无法排除是否存在前置校验失效或网络请求泄漏。两项安全拦截必须纠正为 **`NOT VERIFIED`**。

#### 3. WebDAV Legado 导入与 JSON 恢复未闭合（Spec 5）
- **Legado ZIP 导入未发生数据更新**：
  1. 自动化套件运行阶段，因未编写点击导入确认弹窗的逻辑，`summary.json` 记录 `zip_get_found: false`。
  2. 单步补测触发了导入，但根据最终的 UI 证据 `spec5_chinese_space_import_done.xml`，界面明确提示：
     **`已导入 0 条开源阅读记录；跳过 1 条（未匹配 1）`**。
  3. 事实证明：测试提供的合法 Legado ZIP 备份包中的中读书籍，在当前受控数据库中根本没有匹配上任何图书，**导入更新记录数为 0**！未发生任何真实的数据更新与持久化。
- **无双重编码证据断裂**：
  原报告声称验证了中文与空格文件名未发生双重 URL 编码。但在服务端，由于 reset 命令执行，该次 GET 请求的完整 URL 路径记录已被删除，缺乏服务端接收到的原始路径日志，属于断言无物证支持。
- **JSON 恢复完成未闭合**：
  原证据目录中仅有一份 `dump_json_restore_dialog.xml`，展示的是“确认恢复备份吗？本地数据将被覆盖”的**确认对话框**。既没有恢复成功后的 Toast 或界面截图，更没有对恢复后的 SQLite 数据库进行查询对账以证明数据已被覆盖合并。无法证明 JSON 恢复功能已闭环。
- **上述两项均纠正为 `NOT VERIFIED`**。

#### 4. 受控服务端 Reset 机制导致的证据灭失
- **审查服务端实现**：
  查看 `E:\creation-reading-assistant\next-round-j2-acceptance\scripts\controlled_webdav_server.py`：
  ```python
  if self.path == '/__control__/reset':
      if os.path.exists(LOG_FILE):
          os.remove(LOG_FILE)  # 致命缺陷：每次 reset 直接物理删除日志文件！
      self.send_response(200)
  ```
- **证据链毁灭过程**：
  1. 自动化套件（包含 Spec 1~5）运行产生的第一批流水，被后续单步补测的 reset 调用删除；
  2. 单步补测 Legado ZIP 产生的 GET 请求流水，又被后续的 JSON 恢复测试调用的 reset 删除；
  3. 最终留存在 `E:\creation-reading-assistant\next-round-j2-acceptance\logs\webdav_requests.jsonl` 中的，**仅剩下最后一次 JSON 备份请求的 1 条孤立记录**！
- **审计定性**：由于服务设计缺陷导致关键网络审计证据链物理灭失，严禁事后伪造或通过臆测补齐。

---

## 5. 证据覆盖矩阵（Evidence Reconciliation Matrix）

下表对 J2 原报告所列各项结论进行逐项事实核对与证据链定性：

| 序号 | 验证模块 / 资产项 | 输入 / 操作内容 | 原始证据文件路径 | 原始记录事实 | J2 原宣称 | J2-v2 纠正判定 | 证据缺口与不可支持结论 |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **S-01** | 宿主 APK 哈希 | `app-qahost-debug.apk` | `E:\creation-reading-assistant\next-round-joint-20261001\` | 实际 SHA: `ea68aa16038e...95b67f8` | 宣称: `ea68aa168f69...9e2ec8` | **CORRECTED** | 仅前 8 位一致，后 56 位完全错误，已更正为实算哈希。 |
| **S-02** | 测试 APK 哈希 | `app-qahost-debug-androidTest.apk` | 同上 | 实际 SHA: `0deb20074495...a28cb` | 宣称: `0deb2007e0c4...5df7c` | **CORRECTED** | 仅前 8 位一致，后 56 位完全错误，已更正为实算哈希。 |
| **S-03** | 设备实装 APK 哈希 | 实装包 SHA 回读 | 无完整终端命令执行日志 | 仅有前 8 位短前缀 | 宣称逐位完全匹配 | **NOT VERIFIED** | 无完整命令输出日志，无法证明安装包真实身份。 |
| **S-04** | JVM 专项用例数 | R1 / R2 专项 XML | `testDebugUnitTest/` XML 报告 | R1 实际 21 项；R2 实际 24 项 | 宣称 R1=14, R2=18 | **CORRECTED** | 原报告沿用旧计数，已按实际 XML 更正。 |
| **S-05** | JVM 全量用例数 | 287 个 XML 报告 | 同上 | 2673 项全通，0 失败 0 错误 | 宣称 2673 项全通 | **VERIFIED ACCURATE** | 数据属实，无偏差。 |
| **R-01** | Room: 书籍本地事实 | 远端合并保留本地列 | `logs/instrumentation_persistence_test.log` | `acceptedRemoteBookKeeps...` 单项 PASS | 宣称 PASS | **PASS (LIMITED)** | 仅证明 books 单表本地列保留，不可外推全产品。 |
| **R-02** | Room: 进度本地事实 | 插入进度及显式值 | 同上 | SQLiteConstraintException: code 787 | 宣称 PASS (夹具问题) | **NOT VERIFIED** | 外键约束失败，阅读进度本地事实保留逻辑未覆盖。 |
| **R-03** | Room: 已读完时间过滤 | 在读/在架不带完成时间 | 同上 | SQLiteConstraintException: code 787 | 宣称 PASS (夹具问题) | **NOT VERIFIED** | 外键约束失败，已读完时间清除逻辑未覆盖。 |
| **R-04** | Room: 推进事实 push | pull -> 推进 -> push | 同上 | NullPointerException at line 249 | 宣称 PASS (夹具问题) | **NOT VERIFIED** | 查询返回 null 导致 NPE，状态推进链路未覆盖。 |
| **W-01** | WebDAV: HEAD 405 回退 | 405 -> PROPFIND Depth 0 | `evidence/spec1_head405_propfind.xml` | HEAD 405 紧随 PROPFIND Depth 0 (207) | 宣称 PASS | **PASS (LIMITED)** | 协议行为符合合同，但受限于实装包哈希未闭合。 |
| **W-02** | WebDAV: PUT 409 MKCOL | PUT 409 -> MKCOL -> PUT | `evidence/spec2_put_mkcol_retry.xml` | 单次 MKCOL 201 后重试 PUT 201 成功 | 宣称 PASS | **PASS (LIMITED)** | 目录创建与重试行为符合合同，受限于实装包哈希。 |
| **W-03** | WebDAV: 401/403 认证拒绝 | 认证失败立即停止 | `evidence/spec3_auth_401.xml` | 仅发 HEAD 401/403，无后续重试或 MKCOL | 宣称 PASS | **PASS (LIMITED)** | 认证阻断行为符合合同，受限于实装包哈希。 |
| **W-04** | WebDAV: userinfo 拦截 | 混淆 URL 校验 | `acceptance_suite_summary.json` & `spec4_userinfo_blocked.xml` | summary=false，外发 1 请求，UI 显示 HTTP 200 成功！ | 宣称 PASS (0 请求) | **NOT VERIFIED** | 严重冲突。Compose 输入未生效导致测旧配置；补测无连续日志。 |
| **W-05** | WebDAV: 公网 HTTP 拦截 | 明文 HTTP 拒绝 | `acceptance_suite_summary.json` | summary=false，外发 1 请求，UI 提示不匹配 | 宣称 PASS (0 请求) | **NOT VERIFIED** | 严重冲突。自动化判定失败；补测缺乏完整日志流水。 |
| **W-06** | WebDAV: Legado 导入 | ZIP 下载与记录匹配 | `evidence/spec5_chinese_space_import_done.xml` | UI 明确提示：导入 0 条，跳过 1 条（未匹配 1） | 宣称 PASS | **NOT VERIFIED** | 实际导入记录为 0，未发生数据更新；无双重编码物证灭失。 |
| **W-07** | WebDAV: JSON 备份恢复 | 备份还原覆盖 | `evidence/dump_json_restore_dialog.xml` | 仅捕获到“确认恢复”对话框，无后续证据 | 宣称 PASS | **NOT VERIFIED** | 对话框不等于恢复完成，缺后续成功 UI 及 DB 数据对账。 |
| **E-01** | 冷备恢复与凭据安全 | 卸载重装后回灌数据 | `data/` 目录 67 文件及 `SecurePrefs.kt` | KeyStore 主密钥丢失，触发自愈静默删除配置 | 宣称已完整恢复 | **HIGH RISK / UNRESOLVED** | 凭据解密失败被自愈代码静默清空，制造恢复假象。 |

---

## 6. 未覆盖项汇总与缺口清单

经本次深度审计，以下核心功能与验证链路存在确凿证据缺口，当前处于 **未闭环 / 未覆盖（NOT VERIFIED）** 状态：
1. **实装 APK 身份物证缺失**：缺乏完整的终端标准输出日志，无法证明安装运行的 APK 与待交付候选在二进制上严格一致。
2. **Room 进度同步持久化逻辑未覆盖**：因测试用例自身外键缺陷，阅读进度本地事实保留、已读完时间过滤、pull 成功后状态推进等核心链路均未经验证。
3. **真实 UI 配置输入传递链路未闭合**：Compose `OutlinedTextField` 在自动化测试下的输入刷新机制存在缺陷，未能证明带 userinfo 或公网明文的 URL 字符串已真实驱动 ViewModel 与底层校验器。
4. **WebDAV 前置安全拦截物证断裂**：缺乏能够证明“输入非法配置 -> 点击测试 -> 0 网络请求且 UI 明确展示安全拒绝提示”的端到端不可辩驳证据。
5. **Legado 真实导入更新未发生**：Legado ZIP 导入测试中，由于测试包书名与本地书籍未能匹配，更新行数为 0，未验证真实导入合流路径。
6. **Legado 请求路径无双重编码物证灭失**：因受控服务端 reset 逻辑缺陷，原始含有中文及空格的 GET 请求路径日志已物理丢失。
7. **JSON 恢复后数据持久化未对账**：未提供恢复完成后的 UI 界面与 SQLite 数据库行级对账证据。
8. **环境冷备恢复后加密凭据可用性未验证**：卸载重装操作存在破坏 AndroidKeyStore 主密钥的高危风险，未能证明旧 WebDAV 密码与 Token 在恢复后依然可被成功解密。

---

## 7. 恢复与环境副作用核查（Deep-Dive）

### 7.1 冷备物理完整性状态
- 审查 `E:\creation-reading-assistant\next-round-j2-acceptance\cold_backup\data\`：
  - 累计存储 67 个文件，总体积 1,182,907,163 字节（包含 WebView 缓存、网络图片缓存及数据库）。
  - 主数据库文件 `databases/cra_database` 存在，SHA-256 校验值为 `7bd076c6...`，与 J2 原报告所列冷备哈希一致。
  - 冷备文件在宿主磁盘上物理存在且未被损坏。

### 7.2 KeyStore 与 SecurePrefs 灾难性自愈分析
1. **AndroidKeyStore 密钥生命周期**：
   在 Android 12 系统中，存储于 `AndroidKeyStore` 的 MasterKey 绑定于当前安装的应用 UID。一旦执行 `adb uninstall com.creationreadingassistant.qahost`，系统将彻底销毁该 UID 对应的硬件/软件 Keystore 密钥库。
2. **回灌数据的解密崩溃**：
   测试完成后，若通过卸载测试宿主包、安装正式包再推回 `shared_prefs/secure_prefs.xml`，新安装的实例生成的 MasterKey 是全新的，根本无法解密旧实例加密的数据。
3. **自愈机制擦除证据**：
   `SecurePrefs.open()` 在遇到 `AEADBadTagException` 等解密异常时，自动执行 `deleteSharedPreferences()` 并重新初始化为空配置。这直接导致宿主原有的所有凭据资产被**永久物理擦除**。
4. **严重性评级**：
   此项机制属于严重的隐性数据破坏风险。由于书籍非加密存储，表面上书架完好，极易让验收人员误判为环境恢复完好。

---

## 8. 下一轮隔离测试方案与最小重验清单（面向 J2-v3 的建议）

为彻底解决证据冲突、物证灭失与恢复破坏问题，下一轮执行必须采纳以下隔离测试方案与最小重验清单：

### 8.1 测试隔离与环境保护方案
1. **彻底摒弃“卸载并回灌”方式**：
   - 严禁对宿主应用执行 `uninstall` 操作！
   - 原包恢复优先采用**相同签名证书的覆盖安装（`adb install -r -d`）**，保留数据沙箱与 KeyStore 主密钥。
2. **测试数据沙箱隔离**：
   - J1 仪器测试必须使用独立的数据库名称（如 `test_persistence.db`）与独立的 SharedPreferences 名称（如 `test_sync_config`），严禁使用生产默认的 `sync_config` 或污染 `cra_device_identity`。
   - `tearDown()` 阶段仅清理测试专属的临时数据，严禁调用全量清空接口。
3. **受控 WebDAV 服务器日志防灭失改造**：
   - 彻底废除 `os.remove(LOG_FILE)` 逻辑。
   - 改为**按测试会话（Session）分文件追加存储**（例如 `webdav_requests_session_<timestamp>.jsonl`），确保每一个 Spec 步骤的完整 HTTP 流水永久存盘，供事后严格复核。
4. **Compose UI 自动化输入可靠性补正**：
   - 废除盲打的 `adb input text`。
   - 采用标准 Compose 测试 API，或先点击清除已有文本、粘贴剪贴板内容、触发焦点切换（失焦），确保 `onValueChange` 明确触发并更新 ViewModel 内部状态。

### 8.2 最小重验清单（Minimum Retest Checklist）
- [ ] **实装 APK 身份锁定**：通过脚本完整拉取设备上实际安装的 APK 并计算 SHA-256，保存包含完整终端标准输出的命令日志，证明与冻结构建对二进制完全一致。
- [ ] **J1-v2 修复后 Room 仪器测试全通**：在待测夹具中补齐父表 `books` 预置记录，重新执行 4 项仪器测试，获取 4 tests / 0 failures 的完整 `am instrument` 控制台日志。
- [ ] **WebDAV userinfo 拦截真机复核**：在 UI 中准确输入 `http://user:pass@127.0.0.1:18080/dav/`，点击连接测试，获取 0 网络请求日志与 UI 明确拦截提示的配对物证。
- [ ] **WebDAV 公网明文 HTTP 拦截真机复核**：在 UI 中准确输入公网 HTTP URL，点击连接测试，获取 0 网络请求日志与 UI 明确拦截提示的配对物证。
- [ ] **Legado 真实图书导入对账**：受控服务端提供的 Legado ZIP 必须包含与本地 Room 数据库中已有书籍匹配的进度数据，导入后验证 UI 提示“导入成功”，并查询 Room 数据库确认进度值真实发生更新；同时核查服务端未双重编码的 GET 请求日志。
- [ ] **JSON 恢复后数据库对账**：执行 JSON 备份还原，捕获恢复成功界面的 UI 转储，并直接查询 SQLite 表确认数据已被还原覆盖。
- [ ] **凭据可解密可用性验证**：在恢复完成后，以编程或受控测试方式验证 `SecurePrefs` 能够成功读取并解密原宿主配置，证明主密钥未丢失、自愈清空未被触发。

---

## 9. 交付状态与停止点

- **交付物核实**：
  1. 结构化审计矩阵：`E:\creation-reading-assistant\next-round-j2-v2-evidence-review\evidence_correction_matrix.json`（已落盘完成）。
  2. 纠正报告：`docs/qa/2026-10/2026-10-01-next-j2-v2-evidence-correction.md`（本文档，已创建完成）。
- **结论定性**：
  原 J2 联合设备验收报告存在多处关键物证冲突、虚假判定与严重恢复风险。
  **J2 联合设备验收关卡判定必须维持 `REJECTED / RETURN_FOR_REPAIR`（返修）状态**。
- **停止点确认**：
  本轮任务严格遵循只读原则，未执行任何 ADB 交互，未修改产品源码或构建系统。所有勘误工作与恢复边界核查已全量闭环，现停止执行，等待 Codex 与主控安排后续批次。
