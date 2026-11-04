# CRA-RESTORE-REAL-INTERRUPTION-ACCEPT-v1 —— J5 真实恢复中断、断言补强与严格恢复

- 任务编号/版本：`CRA-RESTORE-REAL-INTERRUPTION-ACCEPT-v1`；2026-10-05。
- 执行者：Claude Code（桌面应用 Code 标签页）；客户端上报模型串 `anthropic/claude-ccr-h776f726b62756464792041492f676c6f62616c3a646565707365656b2d76342e312d666c617368`（解码为 `workbuddy AI/global:deepseek-v4.1-flash`；未做界面核验）。
- 开始 09:35 / 结束 09:59（本地）；本批**唯一** MuMu、ADB 与设备 APK 构建负责人。
- 性质：设备验收。**未改生产 Kotlin 源码、D 源码、原仓库、current/TODO 及旧报告。**
- 状态：**已交付待 Codex 核查**。

## 一、输入指纹

| 项 | 值 |
|---|---|
| 恢复仓库 HEAD（开工=收工） | `e39a78da9c62f02614207cffb36969a32492eaea`（与预期一致） |
| 暂存区 | 条目 0（与预期一致） |
| 只读候选 | `E:\creation-reading-assistant\unified-mumu-accept-v1\android`（979 文件冻结，实算 979/979 匹配） |
| 本轮工作副本 | `E:\creation-reading-assistant\restore-real-interruption-accept-v1\android`（982 文件） |
| 冻结驱动 | `runner.py` `a634a344…`、`joint_driver.py` `013ca2f9…`，与 `unified-offline-binding-v1/patched-driver` **逐字节相同** |
| 设备 serial | `127.0.0.1:16416`（运行时核实；`emulator-5556` 为同设备别名） |

**本批新冻结完整清单**（不套用旧 978/979）：`freeze/source-sha256.json`（982 项）。相对上游 979 冻结的差异**仅**：

- 改：`app/build.gradle.kts`（后缀 `.qahostunifiedv1` → `.qahostrestorev1` 单 hunk）；`…/acceptance/UnifiedMumuAcceptV1Test.kt`（断言补强）
- 增：`…/acceptance/AcceptancePredicates.kt`、`…/acceptance/RestoreRealInterruptionAcceptV1Test.kt`、`local.properties`（机器本地）
- 删：无

**生产 Kotlin 源码零改动。**

## 二、所有权与改动

- 只写：`E:\creation-reading-assistant\restore-real-interruption-accept-v1\` 与本报告。
- 允许范围内的改动：**本副本**的隔离 qahost 后缀、androidTest 验收源、E 侧测试脚本。
- 未改生产 Kotlin、D 源码、原仓库、`current.md`/TODO、旧报告、旧候选。无 `commit/push/reset/checkout`。
- 同编号任务/设备占用核查：开工时 `E:\…\restore-real-interruption-accept-v1` 与本报告均不存在（首次执行）；设备上无本批包（见三）。

## 三、环境

- MuMu 模拟器 `127.0.0.1:16416`；Android 12（SDK 32）；1080×1920 / density 280；`uptime` 2 天。
- **本批新双包开工前不存在**：`com.creationreadingassistant.qahostrestorev1(.test)` 的 `pm path` 均 rc≠0（见 `baseline_pre.json`）。
- 保护包（预先存在、全程只读保全）：`com.creationreadingassistant.qahost`（71 文件）、`com.creationreadingassistant.qahost.test`（0 文件）。
- 结果口径：**MuMu 模拟器**，**不是**实体手机 / MIUI；1080×1920 宽布局，**不称手机窄布局验收**（本轮无需窄布局，设置保持不动）。

## 四、执行与结果

### 4.1 构建与冻结

| 项 | 值 |
|---|---|
| 命令 | `./gradlew :app:assembleQahost :app:assembleQahostAndroidTest -PcraAndroidTestBuildType=qahost --offline --console=plain --max-workers=1` |
| 退出码 | **0**（`build-logs/build-exit.txt`、`build-logs/build.log`，`BUILD SUCCESSFUL in 3m 6s`） |
| 宿主 APK | `dbe197fb8160d21350435341289d65e350e233399f1d11c0e49ae1f1216777fe`（51,764,964 B） |
| 测试 APK | `e822972e5c110f9520edbd1f6f6af19becd69b6c45604bec36e23805a6b002be`（9,281,382 B） |
| 宿主 manifest | package `…qahostrestorev1`、Application `com.creationreadingassistant.App`（**生产 App**） |
| 测试 manifest | package `…qahostrestorev1.test`、runner `…testing.CraIsolatedTestRunner`、targetPackage `…qahostrestorev1` |
| 安装读回 | 双包 SHA 与冻结**逐位一致**（见 `freeze/final-freeze.json`） |

> `CraIsolatedTestRunner` 只对**精确** `com.creationreadingassistant.qahost` 替换裸 Application；本批后缀不同，故走**生产 App + Hilt 图**——这正是真实 Profile/恢复链所必需，已在 manifest 与运行结果中确认。

### 4.2 先修验收逻辑（断言补强）

新增 `AcceptancePredicates.kt`：把判定提取为**显式谓词**，`UnifiedMumuAcceptV1Test` 的 umu_12/umu_31 改为消费谓词。

| 方法 | 旧行为 | 新行为 |
|---|---|---|
| `umu_12_verifyJ1Reentered` | 只 `require(json != null)` → **offset=0 也 pass=true** | 绑定 `umu_11` 落盘的**退出保存期望位置** + 重进 UI dump（期望段落标记）；期望缺失/为 0、重进偏移不等、缺 UI 证据 → **FAIL** |
| `umu_31_verifyJ3State` | 只把 `inChapter2` 写进 JSON → **越界也 pass=true** | `require(j3ChapterRangeAccepted(...))`：越界/缺偏移/无有效范围 → **FAIL** |
| `umu_42`/`umu_90` 等纯记录方法 | 写 pass=true | 本轮**未运行**，不作为旅程 PASS 计入 |

**负向验证（本轮实际运行，3/3 PASS）**，直接证明谓词拒绝坏输入：

| 方法 | 结果 | 覆盖的拒绝场景 |
|---|---|---|
| `umu_51_negative_zeroOffsetRejected` | **PASS** | 期望=0、期望=2553/重进=0（回归）、期望缺失、重进缺失、负值、UI dump 缺失、UI 不含期望段落 → 全部判假；**仅「期望=重进=非零且 UI 命中」一条正例为真** |
| `umu_52_negative_wrongChapterRangeRejected` | **PASS** | 偏移=0、越上界、越下界、偏移缺失、范围为空 → 全部判假；仅范围内一条正例为真 |
| `umu_53_negative_missingRecoveryEvidenceRejected` | **PASS** | 状态缺失、阶段=started、阶段=files_copied、待修目标为空、信号 pid 缺失、**信号 pid==当前进程（未真杀）**、runId 缺失、phase 不符 → 全部判假；仅齐全一条正例为真 |

**fail-closed 实证**：本轮**未重跑 J1/J3 设备旅程**（按要求不重复已知产品失败），在缺证据条件下运行补强后的两个方法：

| 方法 | 结果 | 输出 |
|---|---|---|
| `umu_12_verifyJ1Reentered` | **FAIL**（runner exit 1） | `J1：缺少退出保存期望位置证据（umu_11_verifyJ1Saved.json）——缺证据不得判通过` |
| `umu_31_verifyJ3State` | **FAIL**（runner exit 1） | `J3：MD 分页切章保存后必须存在进度行` |

→ 旧实现在同一条件下输出 `pass=true`；**补强后缺证据不再能伪装成旅程 PASS**。这两个 FAIL 是**预期的 fail-closed 行为**，**不是**产品结论，也不代表 J1/J3 旅程本轮被判定失败（本轮未执行该两段旅程）。

### 4.3 J5 真实恢复中断（本轮核心）

**采用入口与调用链（全部为生产代码，未直写状态文件、未直写 DB、未改 UI）**：

1. `rj5_00_seedFixture`：新 UID 私有目录写中性正文 `RJ5 ORIGINAL CONTENT`，真实 Room 行（revision 2），并生成中性测试 ZIP（`backup.json` + `books/rj5-b1/content.txt` = `RJ5 RECOVERED CONTENT`）。ZIP SHA `ad589063…`；前置断言**状态文件不存在**（保证「无中断时无横幅」可判）。
2. 真实 UI 路径：**我的 → 存储管理 → 导入整包 → 确认 → 系统 SAF → 下载 → rj5-fixture.zip**。
3. 受控中断：`rj5_01_armRealUiImport` 用**既有生产测试接缝** `LocalZipBackup.fileCopyOperation`（`LocalZipBackup.kt:450`）在生产复制点停住，并断言此刻 `LocalZipRestoreStateStore.read(context).stage == "db_committed"` 且 `pendingTargets` 非空，再落信号；由外部 runner 核验信号后执行**真实 `am force-stop`**。
4. 冷启动 ×2：Profile 横幅（阶段文案 + 重试入口）与跨实例持久。
5. 真实重试：横幅「重新选择同一备份重试」→ 确认 → SAF → 同一 ZIP。

**阶段确认方法**：信号内容（`stateStage="db_committed"`、`phase="after_db_plan_before_content_copy"`）+ 设备侧独立读取的 `zip-restore-state.json`（`stage=db_committed`、`pendingTargets` 非空）+ 正文 SHA 仍为 ORIGINAL。三者同时成立才认为中断落在「DB 已提交、文件未复制完」窗口。

**结果**：

| 步骤 | 结果 | 关键证据 |
|---|---|---|
| `rj5_00_seedFixture` | **PASS** | `evidence/raw/rj5_00_seedFixture.*` |
| `rj5_01_armRealUiImport` | **TERMINATED** | 信号 gate=`verified`、`stop_exit=0`、`pid_after=""`、`instrument_ended=true`；`ordinary_junit_pass=false`（**真实强停**，非 JUnit 通过） |
| `rj5_02_verifyInterruptedPersisted` | **PASS** | 新进程；状态 `db_committed`+待修目标；正文仍 ORIGINAL；`ui_cold1.xml`/`ui_cold2.xml` 均含「上次整包恢复未完成」「数据库已更新、书籍文件尚未复制完」「导出已被阻止」「重新选择同一备份重试」 |
| `rj5_03_verifyRetryConverged` | **PASS** | 状态文件清除、正文 SHA `30c8607f…`=RECOVERED、`ui_retry.xml` 横幅消失 |

**生产阶段与进程证据**：

- 中断信号（设备原始）：`runId=rj5-1791165079723-173863760757704`、`pid=10444`、`stateStage=db_committed`、`pendingTargets=rj5-b1/content.txt=30c8607f…`、`oldContentSha256=19f29582…`
- 设备侧状态文件（独立读取）：`stage="db_committed"`、`pendingTargets={"rj5-b1/content.txt":"30c8607f…"}`、`sourceLabel=content://com.android.providers.downloads.documents/document/raw%3A…rj5-fixture.zip`（**证明来自真实 SAF 选择**）
- 中断瞬间正文 SHA = `19f29582…`（ORIGINAL，未被覆盖）
- 冷启动进程 pid 依次 11803 → 11966 → 12180（**跨实例**，与中断进程 10444 不同）
- 重试后正文 SHA = `30c8607f…`（RECOVERED），状态文件不存在

**J5 判定：4/4 PASS（真实 ZIP 导入中途受控强停级中断）。** 这与旧批「仅 `run-as` 注入状态文件」有本质区别——旧批只验横幅组件与读取链，本轮验完整生产恢复流程的中断与收敛。

### 4.4 三项判定分别给出（不相互代替）

| 判定项 | 结论 |
|---|---|
| **J5 真实恢复中断** | **PASS 4/4**（真实 UI+SAF+真实 `am force-stop`+真实重试收敛） |
| **断言补强** | **PASS**：负向验证 3/3；补强后的 umu_12/umu_31 在缺证据下 fail-closed（2/2 不再假通过） |
| **恢复合同** | **PASS**：见第六节逐项对账 |

## 五、原始证据

| 证据 | 路径 |
|---|---|
| 构建日志/退出码 | `build-logs/build.log`、`build-logs/build-exit.txt` |
| 新完整源码清单（982） | `freeze/source-sha256.json` |
| 冻结记录 | `freeze/final-freeze.json`（双 APK SHA/manifest/runner/安装读回/驱动 SHA） |
| 双 APK | `freeze/app-qahost.apk`、`freeze/app-qahost-androidTest.apk` |
| 基线（前/后） | `evidence/baseline_pre.json`、`evidence/baseline_post.json` |
| 逐方法原始输出与判定 | `evidence/raw/<method>.txt` + `.json`（runner 产出，拒绝覆盖） |
| J5 中断信号与状态 | `evidence/raw/rj5_01_armRealUiImport.json`；设备状态文件原文见报告 4.3 |
| 真实 UI 证据 | `evidence/ui/ui_*.xml` + `.png`（含 `ui_profile_nointerrupt`、`ui_cold1`、`ui_cold2`、`ui_retry`、SAF 全程） |
| 驱动身份 | `driver/runner.py`、`driver/joint_driver.py`（与 patched-driver 逐字节相同） |

## 六、恢复与副作用

设备写入仅限：安装/卸载本批双包、本批私有目录与 SAF 夹具 ZIP（`/sdcard/Download/rj5-fixture.zip`，已删）。

| 对账项 | 结果 |
|---|---|
| 七项设置（前后） | **完全一致**：wm_size 1080x1920、density 280、accel 1、user_rotation 0、**night yes**、stay_on 0、timeout 60000 |
| 保护包 APK 字节 SHA | **2/2 一致**：`e82904cc…`、`fdbd0190…` |
| 保护包持久文件清单 | **逐文件哈希一致**：qahost 71/71（差异 0）、qahost.test 0/0 |
| forward / reverse | 前后均为空 |
| 本批双包 | 已卸载；`pm path` rc≠0；应用外部目录已随卸载消失 |
| 设备残留 | `/data/local/tmp` 无本批残留；`/sdcard/rj5-window.xml` 等本批临时文件已清理；`pm list` 仅剩保护包 |

**恢复失败或缺证据即整体不通过**——本轮无失败项。night 基线为开工实读 `yes`（与 Root 补恢复一致），**未由本轮改动**。

## 七、未覆盖项

- **J1/J3 设备旅程本轮未重跑**（按要求不重复已知产品失败）；补强后的 umu_12/umu_31 因此 fail-closed。J1 产品缺陷由窗口一在独立 E 树修复，本批**不**等待、**不**绑定其修复。
- 未执行 TXT 实时偏移、MD canonical、EPUB 回源等既有旅程；旧批 J2–J4 限定结论**不**因本轮而变，也不冒充最终修复 APK 联合通过。
- 手机窄布局、实体手机/MIUI/TalkBack/R8/正式签名发布维持既有边界。
- 中断窗口为「DB 已提交、文件未复制完」单点；`started` 阶段与 `files_copied` 阶段的强停未单独覆盖。
- 恢复横幅的「重新选择同一备份重试」已验收敛；**选择不同 ZIP 的路径**未覆盖。
- 本批 J5 结论**仅**对本批冻结双 APK（`dbe197fb…` / `e822972e…`）成立。

## 八、交付状态与下一步

**已交付待 Codex 核查。** MuMu 所有权已释放。

- J5 真实恢复中断：**PASS 4/4**（含真实 `am force-stop` 的 TERMINATED 与真实重试收敛）。
- 断言补强：**PASS**（负向 3/3 + 缺证据 fail-closed 2/2）。
- 恢复合同：**PASS**（七设置/APK 对/71 文件/转发/清理逐项一致）。
- 下一步建议：① Codex 复核 `freeze/final-freeze.json` 与 `evidence/raw/*.json`；② 待窗口一 TXT 修复交付后，由单一 owner 将其并入**新冻结候选**再做 J1 回归，**不得**用本轮前账追溯证明旧批恢复成立，也不得把本轮 J5 结果写成最终修复 APK 联合通过；③ 本批新后缀与仪器类须随新候选**重新冻结**，不套用 978/979 旧清单。
