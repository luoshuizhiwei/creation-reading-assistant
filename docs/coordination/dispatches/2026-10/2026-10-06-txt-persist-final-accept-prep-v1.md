# CRA-TXT-PERSIST-FINAL-ACCEPT-PREP-v1 —— 恢复后移动保存最终TXT验收工件准备报告

> 任务编号：`CRA-TXT-PERSIST-FINAL-ACCEPT-PREP-v1`
> 日期：2026-10-06
> 执行者：Antigravity（Gemini）
> 状态：**READY_FOR_BINDING / 离线 9/9 通过 / 接缝 5/5 通过 / 测试源定向编译通过（0 error）**
> 设备结论：**NOT_RUN（本任务不包含任何设备 PASS 结论，第一批不操作设备）**

---

## 1. 目标与背景

针对此前冷启对照与审阅中暴露的核心缺陷：
> “冷启对照本批非零 5054 保留，但重进后 UI 29%→87% 且退出行/updated_at 冻结……这是重进后保存链冻结的独立方向……不能再次只证明旧进度没有清零，却遗漏保存永久冻结。”

本任务为独立的**最终 TXT 验收工件准备窗口**（第一批），与另一并行产品小修窗口互斥并行：
- 不修改产品源码、其候选或进行中工件；
- 第一批不使用 ADB/MuMu，不产出设备 PASS；
- 准备能彻底验证“恢复后继续移动确实保存”、覆盖“UI 有位移但 DB 仍旧 S / updated_at 不变”反例的最终验收测试源码、驱动、配置模板、夹具与门禁；
- 单独准备短末项 clamp 独立补充场景，不污染原 J1 基准。

---

## 2. 输入指纹与所有权

### 2.1 只读输入与编译基线
- 既有工件输入（只读）：
  - `E:\creation-reading-assistant\txt-final-acceptance-prep-v2`
  - `E:\creation-reading-assistant\txt-failure-evidence-repair-v1`
  - `E:\creation-reading-assistant\j1-post-item-harvest-v2`
- 产品编译基线（只读）：
  - `E:\creation-reading-assistant\txt-reentry-fix-v4\android`（稳定 TXT-v4 基线）
- 仓库基线：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`，HEAD `e39a78da9c62f02614207cffb36969a32492eaea`。

### 2.2 所有权与写入范围
- 本任务唯一写入目录：`E:\creation-reading-assistant\txt-persist-final-accept-prep-v1\`
- 唯一 D 报告：`docs/coordination/dispatches/2026-10/2026-10-06-txt-persist-final-accept-prep-v1.md`（本报告）
- D 仓库产品源码、Git 树、公共配置、其他窗口候选：**零修改、零提交、零争用**；
- 私有 Gradle 构建缓存：`E:\creation-reading-assistant\txt-persist-final-accept-prep-v1\gradle-home\`，不与另一窗口共用可写缓存。

---

## 3. 工作闭合说明（逐项对应）

| 工作项 | 要求 | 本任务实施与闭合证明 |
|---|---|---|
| **1. 复用采集 v2** | 复用已验采集 v2，不重写 runner 或降低完整 checker；config 逐项绑定 `after_item.expect` 与实际 `writeResult`，核齐身份口径 | 复用 `j1-post-item-harvest-v2` 的 `joint_driver.py`、`j1_harvest.py`、`runner.py`。配置模板中 11 项主测试全部逐项绑定 `after_item.expect = <method>.json`，与测试落盘文件名 `$step.json` 完全一致；统一任务编号 `CRA-TXT-PERSIST-FINAL-ACCEPT-PREP-v1`，核齐 `step`、`runId`、`packageName`、`taskId`、`configSha256`、`fixtureSha256` 六项身份字段。接缝测试 5/5 通过。 |
| **2. 正确放置测试源码** | 冻结路径必须对应 `app/src/androidTest/java/...`；双 APK 保持同一最终身份 | payload 目录层级规范为 `payload/app/src/androidTest/java/com/creationreadingassistant/acceptance/...`，包含三处测试源；`APPLY.md` 与配置中严格约束构建后、安装前与安装读回使用同一 APK 身份。 |
| **3. 加强恢复后移动保存** | 记录恢复基准 R 和完整进度行；真实 UI 移到明确不同位置证明位移；等待有上限，核 locator/percent/updated_at 实际变化；back 后核行并冷读回；不能仅断言 offset>0 | 纯合同 `J1RecoveryContract.newPositionAccepted` 与测试 `j1_60_newPositionSaved` 核心强化：不仅断言 `S2 > 0`，且必须 `S2 != R`，更**必须 `S2 != S`（绝不能等于旧保存值 S！若相等立即判定为保存冻结反例）**；核验 `newUpdatedAt != savedUpdatedAt`；UI dump 命中新位置正文 marker；UI 回调中执行前置阅读器校验、滑动后正文 marker 位移校验与上限等待落盘。 |
| **4. 页面前置检查** | 实际页面不是阅读器、位移未达成或观测缺失时，明确拒绝/NOT_RUN，不在启动器上 swipe 冒充移动 | `j1_ui_callback.py` 新增 `assert_in_reader` 守卫：执行移动前 dump UI，若当前处于桌面 Launcher 或书架导航区而非阅读器正文，立即抛错拒绝（RuntimeError），绝不在启动器上滑动冒充；滑动前后比对正文 marker 集合，未产生实质位移时立即抛错拒绝。接缝测试验证通过。 |
| **5. 普通恢复单元合同** | 普通恢复仍按阅读单元合同：允许原单元起点或精确 S，配真实 UI 标记；不把段内像素恢复作为不存在的要求 | 严格保持生产阅读单元合同（`PLAIN_TEXT_CHUNK_CHARS=3000` 换行切块）：恢复落点允许 `R == savedUnitStart`（单元起点）或 `R == S`（精确恢复），配合 UI dump 包含目标可见 marker，不凭空要求段内像素恢复。 |
| **6. 短末项 clamp 补充场景** | 中性独立夹具/记录，与普通 J1 原始保存合同分开，验证合法落位后能继续保存，不污染原 J1 基准 | 单独生成短末项 clamp 夹具 `fixture_short_clamp.txt`（2 单元 / 3840 字符，末单元仅 771 字符）；独立图书 ID `j1-clamp-b1`；独立测试类 `TxtShortClampSupplementTest.kt`（4 方法）；独立配置模板 `j1-clamp-config.template.json`。验证书尾短项触发 clamp 落位后能继续正常保存，不污染原 J1 基准。 |
| **7. 卸载前归档与失败保护** | 每个方法失败/最后一项都在卸载前归档，缺本步骤或身份错误拒绝，失败判定不被采集成功改成 PASS | 时序保持：方法结束 -> `_record_item`（原始判定先于采集）-> `after_item`（采集 `expect` 文件并核验身份）-> 下一项。原始方法失败时，即使采集成功仍保持 FAIL；采集失败时 runner PASS 降级为 FAIL；全部项执行完毕后才进入 finally 卸载与恢复。 |
| **8. 产品修复中不猜填** | 只绑定稳定 TXT-v4 做测试编译准备；最终产品 SHA/APK/serial 保持“待合流实测”，不猜填或冒充冻结 | 产品编译基线基于 `txt-reentry-fix-v4\android`；配置模板中 candidateDir、APK 路径、APK SHA、serial 均显式保留占位并标注“待合流实测”，不猜填、不冒充冻结。 |

---

## 4. 离线验证与接缝结果

### 4.1 离线正反例测试（`offline/j1_offline_tests.py`，9/9 PASS）
- 执行命令：`python offline/j1_offline_tests.py`
- 结果总账：`offline/results/SUMMARY.json`（`all_passed=True, total=9`）

| 用例名称 | 注入条件 | 期望与实际退出码 | 驱动批次 | J1 行为 | 核心判别点 |
|---|---|---|---|---|---|
| `good_unit_start` | 合法单元起点恢复 + 恢复后移动合法保存 | 0 | PASS | ACCEPTED | 整批接受（J1_BATCH_ACCEPTED） |
| **`reject_freeze_db_old_s`** | **UI 发生位移，但 DB offset 仍等于旧 S (3525)** | 1 | PASS | **REJECTED** | **成功捕获保存冻结核心反例：原因报“检测到保存冻结：UI 已移动但 DB 进度依然为旧保存值 S”** |
| **`reject_freeze_timestamp_same`** | **UI 发生位移，但 updated_at 仍为旧保存时间戳** | 1 | PASS | **REJECTED** | **成功捕获时间戳冻结反例：原因报“检测到保存冻结：UI 已移动但 updated_at 未更新”** |
| `reject_new_position_same_as_r` | 新位置偏移等于重进落点 R（未移动） | 1 | PASS | REJECTED | 捕获未移动反例 |
| `reject_zero_reentry` | 重进落点偏移为 0（清零） | 1 | PASS | REJECTED | 捕获清零反例 |
| `reject_wrong_unit` | 重进落点落在错误阅读单元 | 1 | PASS | REJECTED | 捕获错单元反例 |
| `reject_restore_missing` | 缺失恢复账 `restore.json` | 1 | NOT_PASS | ACCEPTED | 驱动层门禁拒绝，缺恢复账绝不通过整批 |
| `reject_restore_fail` | 恢复账记录 `ok=false` | 1 | NOT_PASS | ACCEPTED | 驱动层门禁拒绝 |
| `reject_empty_manifest` | 只有 run_id 的空 manifest | 1 | NOT_PASS | ACCEPTED | 驱动层门禁拒绝 |

### 4.2 离线接缝检查（`offline/j1_seam_tests.py`，5/5 PASS）
- 执行命令：`python offline/j1_seam_tests.py`
- 结果总账：`offline/seam-results/SUMMARY.json`（`all_ok=True`）
  1. `test_j1_config_template_after_items`: PASS（11 项 items 全部绑定 after_item.expect）
  2. `test_clamp_config_template_after_items`: PASS（短末项 clamp 配置模板逐项绑定）
  3. `test_ui_callback_reader_guard`: PASS（验证在 Launcher 页面与书架导航页面时抛错拒绝）
  4. `test_fixture_hashes`: PASS（两个中性夹具实际 SHA-256 与声明一致）
  5. `test_run_entry_serial_check`: PASS（`j1_run.py` 严格校验 serial 参数）

---

## 5. 定向编译验证

在独立 E 编译副本（`compile-copy/android`）内执行：
```bash
cmd.exe /c "set GRADLE_USER_HOME=E:\creation-reading-assistant\txt-persist-final-accept-prep-v1\gradle-home&& gradlew.bat :app:compileQahostAndroidTestKotlin -PcraAndroidTestBuildType=qahost --offline --console=plain --no-daemon > ..\logs\compile-qahost-androidtest.log 2>&1"
```

- 编译结果：**EXIT=0，BUILD SUCCESSFUL in 1m 33s**；
- 错误计数：`^e: ` 错误计数为 0；
- 产物核实：三个新类均成功产出 `.class` 文件于 `compile-copy/android/app/build/tmp/kotlin-classes/qahostAndroidTest/com/creationreadingassistant/acceptance/`：
  - `J1RecoveryContract.class` (23981 bytes)
  - `TxtFinalAcceptanceJ1Test.class` (37916 bytes)
  - `TxtShortClampSupplementTest.class` (23800 bytes)
- 原始日志：`compile-copy/logs/compile-qahost-androidtest.log`（SHA-256：`b17585ccbda45395c52f2757a2b3a2ff9bd72cfec9d9762604544d1c06cd6342`）。
- **口径说明**：此编译仅证明新增测试源成功接入产品 API，不代表最终 APK 已冻结，更不代表设备通过。

---

## 6. 精确交付 SHA-256 清单（`manifest/payload-sha256.txt`）

```
fda7c12e30e40971339704a6fa99d7c7de852fd886c3afb1b852114203e92e00  payload/app/src/androidTest/java/com/creationreadingassistant/acceptance/J1RecoveryContract.kt
c9f6a982315e9481f80237b50a70103df831eb2414ab1762c6c2a7b2c3c79883  payload/app/src/androidTest/java/com/creationreadingassistant/acceptance/TxtFinalAcceptanceJ1Test.kt
8882226c6f47cdb1064d237fd31294a221923f3b6de3436e624edef916b078f9  payload/app/src/androidTest/java/com/creationreadingassistant/acceptance/TxtShortClampSupplementTest.kt
15e6f96577c6e97362dfd3c5bc3d5ccace7696d39f7b2b417b462a5398ce5fa7  payload/fixture/fixture.txt
477fd489c59aa3c0abecf58d03631e49ab1c7d457f9154abf1711b5c5ceb8199  payload/fixture/fixture.sha256.txt
359ad2c6336debb6e1cfd89162f51d3652e9bb46881424d1cc31301b6aa12361  payload/fixture/j1_fixture.py
fe66a99f6f1a76bee806e86aeadedd60731ffd3ae751f45fdb8458624e4bb260  payload/fixture/fixture_short_clamp.txt
e4603f0b5645f20a4ebdb52d8b473de6c779b22b87ff72410b593a6e0d8449ff  payload/fixture/fixture_short_clamp.sha256.txt
4a2ab36e3c980332e448cb82c3f1dee30669c855ce69951fb1608a82451af94b  payload/fixture/j1_clamp_fixture.py
936386f38e3ce0082082a052b86d3fb6a38eb15e0e1b4bb560be7836c48c4361  payload/APPLY.md
f11fa77877db88741774ef6c3c816cc4bf6d3f4cb09dd58f01aedb7ff3a35dd2  driver/joint_driver.py
b598ee05215055ca984ea7118723ce11ebf6155ea1d707fa2644242995fc8fcc  driver/j1_harvest.py
a634a344f0d8a8c0bf8d14ddee93518551fa5577f2950b61ab77445549c2167e  driver/runner.py
4c86dfcdd051a4f754df37cd6ed7c2f54511d61ec746ac43210b07f7209dc878  driver/j1_ui_callback.py
1ec512ea8fef7f28446c4a38f2fc8d1193b425214120cd1df87ea0395743bb34  driver/j1_evidence_check.py
599f88ad70eaaa54dc54deae659d55e8072cbd6eab9b5251919457817fcfd4e3  driver/j1_run.py
44f1df7751c631719176177c9556005c6639215347e33761f39d7980b3f6b4e7  config/j1-config.template.json
9a2d3647d458e47a4723920937f25ddaab5791d4b656469637b67b93cdecb250  config/j1-clamp-config.template.json
c21c58567ff38a6af15d08f06b6b1c82eedac59e6f30c55b02db475f9d6fe8fa  config/binding-fields.md
1975f8f8a55e650f950a3d434b1dc3fc88b590ef04ef11bc929d15336c2780f4  offline/j1_offline_tests.py
0e10972e65d485465c405397ffd681e2482edd58508ba1db433ecc7cab6824a7  offline/j1_seam_tests.py
14b9876fc686709194ab416cbae83135848441587a91efe737120a86f0b7447f  README.md
```

---

## 7. 边界与诚实声明

1. **第一批未操作设备（NOT_RUN）**：Kotlin 验收测试源未在设备运行，本任务不产出任何设备 PASS，状态为 `READY_FOR_BINDING`；
2. **待合流实测**：最终产品源码清单、双 APK 路径与 SHA-256、实际设备 serial 待合流实测，绝不提前猜填；
3. **互斥守卫**：未修改产品源码、未触动另一窗口的 JVM 小修工作、未动用 MuMu 设备、未修改 Git 树；
4. **阅读器恢复等待阶段**：无法安全观测，显式记录为 `NOT_RUN`，绝不以首页/书架停留冒充恢复等待阶段。

---

## 8. 下一步动作

本工件准备完成后停止，交给 Codex 核查；
待另一窗口产品小修完成并通过核查后，由指定的唯一 owner 串行执行第二批合流构建与 MuMu 验收。
