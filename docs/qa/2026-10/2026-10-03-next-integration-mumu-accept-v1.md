# CRA-INTEGRATION-MUMU-ACCEPT-v1：合并候选真实 Profile 恢复与同步设置 MuMu 验收（2026-10-03）

## 任务编号/版本

`CRA-INTEGRATION-MUMU-ACCEPT-v1` / v1，日期 2026-10-03。

- **执行应用**：Claude Code（桌面 Code 标签页）
- **实际可确认模型**：`hw726brokbuddy AI/global:deepseek-v4.1-flash`（会话环境中的实际路由标识，非按名称推测）
- **执行标识**：`CRA-INTEGRATION-MUMU-ACCEPT-v1-20261003-0045`
- **角色**：本任务唯一构建者、测试驱动者与 MuMu 操作者
- **状态**：**已交付待 Codex 核查**（未自宣布验收或集成）

## 输入指纹

| 项 | 值 |
|---|---|
| 工作仓库 | `D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919` |
| HEAD（预期=实际） | `e39a78da9c62f02614207cffb36969a32492eaea` |
| 暂存区 | 空（0 项）；porcelain 491（本任务前后未变） |
| 冻结输入 | `E:\creation-reading-assistant\next-round-integration-ready-v1\android`（883 文件） |
| `final-manifest.json` SHA-256 | `a0240a395c20251f480378ed07ba901c0c0061c79cd98ea4b43985b229ed491d` |
| `final-HEAD.patch` SHA-256 | `ed208a7ffb2fe49997fa007a87719504f4fb7e56712d38d3e40fac3caddfe867` |
| 最终清单 | **25 个文件**（未使用早期 14 文件清单） |
| **25 文件实际 SHA 核对** | **25/25 与 `candidate_sha256` 一致，0 不匹配、0 缺失** |
| 逻辑 hunk / JVM 套件 / 用例 | 121 / 6 / 153（输入侧已完成，本轮未重跑） |
| 输入永久后缀 | `.qahost` |

**输入漂移检查**：HEAD 与预期一致、暂存区为空、25 文件 SHA 全等。未发现其他执行者在改同一输入。

**占用检查**：`adb devices` 唯一 `127.0.0.1:16416`；该端口监听进程 PID 27632 = `MuMuVMMHeadless.exe`；设备上无运行中的 instrumentation、无应用进程、无本任务新增包；`E:\creation-reading-assistant` 下 2026-10-03 无任何文件写入。据此判定 MuMu 未被其他任务占用并开始设备写入。

## 所有权与改动

- **唯一可写任务目录**：`E:\creation-reading-assistant\next-round-integration-mumu-accept-v1`（本轮新建）
- **唯一可写仓库报告**：`docs/qa/2026-10/2026-10-03-next-integration-mumu-accept-v1.md`（本文件）
- **`execution.json`**：`E:\…\next-round-integration-mumu-accept-v1\execution.json`
- **冻结输入目录只读**：`next-round-integration-ready-v1` 未被修改（只读取与复制）。
- **本任务副本内的改动（均在本任务 E 目录，未回灌）**：
  1. `app/build.gradle.kts`：qahost build type 的 `applicationIdSuffix` 由 `.qahost` 改为 **`.qahostmergedv1`**（本任务隔离后缀；刻意不沿用 `.qahost`，理由见「环境」）。
  2. **新增** `app/src/androidTest/java/com/creationreadingassistant/feature/sync/IntegrationMergedUiAcceptanceTest.kt`（本轮唯一新增仪器类）。
- **未改**：产品源码、既有测试、runner、依赖、`docs/handoff/current.md`、项目页、TODO、全局记忆、原始损坏仓库、HOLD、桌面端资产、实体手机与正式书库。
- **未执行**：stage / commit / push / reset / checkout / 搬移目录。
- 输入自带的旧 `LocalZipRestoreAcceptanceTest`、`LocalZipRestoreUiAcceptanceTest` 含旧包名假设（`qahostn1` / `qahostn1ui`），**未作为本轮驱动运行**；`next-round-n1-ui-accept-v1` 的驱动只作参考，未沿用其 runId、信号路径、APK 哈希或设备基线。

## 环境

- Windows 11 / Git Bash；JDK 17.0.14；Android SDK build-tools 34.0.0；Python 3.13；ADB `D:\develop\Android\Sdk\platform-tools\adb.exe`。
- 独立任务缓存：`E:\…\next-round-integration-mumu-accept-v1\_cache\gradle-home`（26,241 文件，复制完成后才开始 Gradle；构建全程 `--offline`，未与他人共用构建输出）。
- **设备**：MuMu 模拟器。判定依据为**宿主进程与 TCP 对应 + guest 特征**，不凭型号字符串：`16416` 端口监听进程 = `MuMuVMMHeadless.exe`；guest `ro.product.cpu.abi=x86_64`、`ro.build.characteristics=nosdcard`、`qemu.hw.mainkeys=1`。该机 model/manufacturer 被模拟为 `SM-S9280`/`Samsung`，**不构成实体手机证据**。
- **屏幕**：物理 1080×1920；为取得手机宽度竖屏，测试期间临时 `wm density 480`（≈360dp），**收尾已 `wm density reset` 回实测原值 280**。全部截图实测 1080×1920 竖屏。
- **runner 与隔离边界（关键）**：输入 runner 为 `com.creationreadingassistant.testing.CraIsolatedTestRunner`，其隔离分支仅在 targetPackage **精确等于** `com.creationreadingassistant.qahost` 时注入 `CraIsolatedTestApplication`。本任务后缀 `qahostmergedv1` **不匹配**，故走**透传分支**，运行的是清单声明的生产 `com.creationreadingassistant.App`。设备侧实证：arm 证据中 `applicationClass = com.creationreadingassistant.App`，且沙箱包 `databases/creation_reading_assistant_native`（真实 Room 正式库）与 `databases/…/` 真实 DataStore `sync_prefs` 均真实产生。**本轮验的是真实 Hilt / Room / DataStore 链路，不是替身链路。**

**构建与冻结**（命令：`./gradlew.bat :app:assembleQahost :app:assembleQahostAndroidTest -PcraAndroidTestBuildType=qahost --offline --console=plain --max-workers=1`，exit 0）：

| APK | 包名 | 字节 | SHA-256 |
|---|---|---:|---|
| 宿主（冻结） | `com.creationreadingassistant.qahostmergedv1` | 51,371,708 | `ee99374b209f5400e5f28d42d02f122c9dd5ff0d0880d585c38c35760a043be7` |
| androidTest（冻结） | `com.creationreadingassistant.qahostmergedv1.test` | 9,166,635 | `11686013a55eeadd2b6b173fe11a4fb8d28bea92fc2e2fd64d2997e4a9eb2abc` |

- 安装后设备回读 SHA 与冻结件一致；`apksigner verify` 两包 exit 0；证书 SHA-256 `05249d786e3d3d117bb5bd7b1f83512a0c9b8f1ee4e502112c4b2d9ccbd7b9a1`。
- androidTest manifest：`instrumentation android:name="com.creationreadingassistant.testing.CraIsolatedTestRunner"`，`targetPackage="com.creationreadingassistant.qahostmergedv1"`（**按 manifest 实际 runner 核对，未默认 AndroidJUnitRunner**）。
- 宿主 manifest：`application android:name="com.creationreadingassistant.App"`，versionCode 3 / versionName 0.5.1。
- **androidTest APK 因驱动修正重建过两次**（`caae407e…` → `11686013…`，均为本任务驱动改动，非产品改动）；宿主 APK 自首次冻结后未变。原始失败日志全部保留，未删除。

## 执行与结果

### 9 项验收合同

| 项 | 合同 | 结果 | 主要原始证据 |
|---|---|---|---|
| **A1** | 真实 Profile 导入被外部终止后的冷启动 | **PASS** | `device-runs/20261003-005712-mrg_01_armActualUiImport.txt`（TERMINATED）、`device-evidence/mrg_01_arm_ready.json`、`mrg_02_coldStartAfterKill.json`、`screens/a1-*.png` |
| **A2** | 确认对话框取消 | **PASS** | `device-runs/20261003-010203-mrg_03_assertConfirmDialogCancel.txt`、`screens/a2-dialog.png` / `a2-after-cancel.png`、`mrg_03_assertConfirmDialogCancel.json` |
| **A3** | 系统 SAF 取消 | **PASS** | `device-runs/20261003-010633-mrg_04_assertSafCancel.txt`、`screens/a3r-saf-open.png`（`mCurrentFocus=com.android.documentsui/.picker.PickActivity`）/ `a3r-after-cancel.png`（焦点回到 `…MainActivity`）、`mrg_04_assertSafCancel.json` |
| **A4** | 同一受控 ZIP 重试 | **PASS** | `device-runs/20261003-010844-mrg_05_assertRetryRestored.txt`、`screens/a4-after-import.png`、`mrg_05_assertRetryRestored.json` |
| **A5** | 真实 UI 导出与冷读 | **PASS** | `device-runs/20261003-011044/011046-*.txt`、`screens/a5-saf.png` / `a5-after-export.png`、`mrg_06_assertExportContent.json`、`mrg_07_assertColdReadAfterExport.json` |
| **S1** | 同步设置的真实 UI 与持久化 | **PASS** | `device-runs/20261003-011102/011317/011320-*.txt`、`screens/s1-sync-page2.png` / `s1-after-toggles.png` / `s1-pill.png`、`mrg_20/21/22_*.json` |
| **S2** | 关闭类别后的实际同步行为 | **PASS** | `device-runs/20261003-012106-*.txt`、`mock-service/requests-s2-books-off.jsonl`、`mock-service/requests-s2-control-books-on.jsonl`、`mrg_31/32_*.json` |
| **S3** | 相同 revision 的冲突策略 | **PASS** | `device-runs/20261003-013449/013606-*.txt`、`mock-service/requests-s3-remote-wins.jsonl` / `requests-s3-local-wins.jsonl`、`device-evidence/mrg_34_remote_wins.json` / `mrg_34_local_wins.json` |
| **R1** | 环境完整恢复 | **PASS** | `device-baseline/settings-before.txt` vs `settings-after.txt`（逐字节相同）、`existing-apks-sha-before/after.txt`（3/3 相同）、`qahost-files-sha-before/after.txt`（53/53 相同） |

**A1–A5 实际驱动路径（真实 UI + 系统 SAF，未调用产品导入/导出方法）**：

1. 夹具：真实 Room 书行（`revision=2`、`updated_at` 与 ZIP 内 envelope 一致 → equal 分类）+ 真实正文 + 受控 ZIP；ZIP 另存于**事先确认不存在**的专用公开目录 `/sdcard/Download/cra-integration-mumu-accept-v1/`。
2. 执行者用 uiautomator 真实点击：我的 → 数据与存储 → 存储管理 → 本地整包备份（ZIP）→ 导入整包 → 确认 → 系统 SAF 选 `mrg-controlled.zip`。
3. 接缝在「DB 计划已持久化、正文尚未复制」处写信号并等待；执行者核对 **runId / scenario / PID（对 `pidof`）/ 时间戳新鲜度 / 阶段 / phase** 全部成立后 `am force-stop`。
4. **真实终止证据**：强停后 `pidof` 为空、`am instrument` 结束、冷启动新进程 PID 不同（**25833 → 26725**）、无「未终止标记」。被终止的 arm 记为 **TERMINATED**，**未计入普通 JUnit PASS**。
5. A2/A3 的取消以 `dumpsys window mCurrentFocus` 佐证：A3 打开时焦点为 `com.android.documentsui/.picker.PickActivity`，取消后回到 `com.creationreadingassistant.MainActivity`。

**A1 冷启动读回**（`mrg_02`）：书行存在、`revision=2`、正文仍为**原字节**（`e73dda89…`，ZIP 正文尚未换入）、恢复状态 `db_committed` 且未完成、义务 = 受控 ZIP 正文 SHA `fba7c029…`、逐文件 journal 存在。

**A5 导出内容核对**（`mrg_06`）：真实导出入口产出 `/sdcard/Download/cra-integration-mumu-accept-v1/cra-backup-1790960993263.zip`（SHA `41581211…`）；解包后 `backup.json` 的 id/revision 与真实 DB 行一致（`revision=2`），ZIP 正文逐字节等于恢复后的正文；随后**再次冷启动**读回持久状态（`mrg_07`），证明不是内存缓存。

**S1**：三类开关与冲突策略经**真实界面**操作后，真实 DataStore（`files/datastore/sync_prefs.preferences_pb`）落盘值与 UI 一致：`syncBooks=false`、`syncProgress=true`、`syncInspirations=false`、`conflictStrategy=local`；界面胶囊同步显示「冲突策略：本地为准」；冷启动后仍一致。默认值另记为全开 + 远端为准（`mrg_20_syncPrefsBefore`）。

**S2**（受控本地服务 + 真实 Profile 同步入口，**非**直接调用 SyncRepository 的 JVM 结果）：

- 受控服务把远端书设为 `revision=99`（远高于本地 2）——若类别开启则**必然**会被应用。
- **关闭「书架与书籍元数据」后**：真实入口 `立即双向同步` 触发，`POST /sync/push` 请求体 `bodyKeys=["device"]`——**未上传任何书籍**；同时真实 Room 行与正文**逐字节未变**（`mrg_31` vs `mrg_32`，`bookJsonSha256` 与 `bodySha256` 全等）。
- **正向对照（关键）**：把同一开关打开后再次同步，`POST /sync/push` 请求体变为 `bodyKeys=["books","device"]`，`books=[{"id":"mrg-b1","revision":99,…}]`——**书籍被真实上传**。这证明前一条的「没有 books」是门控生效，而不是没有数据可传。
- 测试服务只监听 `127.0.0.1:18100`，经 `adb reverse` 映射；未使用正式服务器、账号或凭证（token 为字面量 `mrg-local-test-token`，仅本地）。

**S3**（同一受控服务，本地行设为 `revision=7`、`updated_at` 与远端**完全相同**，构成等 revision 冲突）：

| 策略 | 远端 | 结果 | 证据 |
|---|---|---|---|
| REMOTE_WINS | `revision=7`、title `MRG REMOTE S3 TITLE` | 本地标题变为**远端标题**，revision 仍 7 | `mrg_34_remote_wins.json` |
| LOCAL_WINS | 同上 | 本地标题保持 `测试 TXT`，**未写入远端标题** | `mrg_34_local_wins.json` |

两种策略下**设备本地事实字段均受保护**：`local_content_path` 仍为 `/data/user/0/com.creationreadingassistant.qahostmergedv1/files/books/mrg-b1/content.txt`，本地正文 SHA 未变（`e7d2b876…`）。这与 `SyncRepository.applyBook` 的 `base.copy(...)` 合并语义一致。

### 构建状态与计数

- 构建：宿主与 androidTest 均 exit 0；宿主 APK 冻结后未变；androidTest 因驱动修正重建 2 次（保留全部原始日志：`logs/build-qahostmergedv1-20261003-004609.log`、`build-02-*.log`、`build-03-*.log`）。
- **设备方法计数**：31 次运行 = **28 PASS + 1 TERMINATED（arm，真实终止）+ 1 FAIL（测试夹具缺陷，已修并重跑 PASS）+ 1 次早期 mrg_04 提前运行（已由有效运行取代）**。逐条见 `freeze/device-run-index.json`。
- 输入侧 153 条 JVM 未重跑（输入未变，按证据复用规则复用）。

### 失败与修正（如实保留，不删除失败日志）

1. **`mrg_04_assertSafCancel` 首次运行（01:02:40）为无效证据**：当时 SAF 尚未真正打开（前一步点击未命中），断言「未变化」会在什么都没发生时假通过。已重做 A3 全流程（真实打开 SAF → `mCurrentFocus` 佐证 → 取消 → 焦点回应用）并在 01:06:33 重跑通过；该次无效运行保留在目录中但**不作为 A3 证据**。
2. **`mrg_34` 首次 LOCAL_WINS 运行（01:32:07）FAIL**：断言 `assertFalse(remoteUpdatedAt == actualUpdatedAt)` 与夹具设计矛盾——本夹具**刻意**让本地与远端 `updatedAt` 相同（等 revision + 等时间），该判据不成立。这是**测试夹具缺陷，不是产品缺陷**。已改为以「本地标题被保留 / 未被写入远端标题」为判据，重建后重跑通过。
3. **配对配置丢失（环境问题，非产品问题）**：`mrg_30` 写入 `SyncConfigStore` 后，因 `EncryptedSharedPreferences.apply()` 异步落盘而 instrumentation 进程随即退出，配置未持久化，真实界面仍显示「未连接」。已改为写入后等待并复核再结束方法；重跑后界面正确显示「已连接电脑 / http://127.0.0.1:18100/」。
4. **受控服务端口占用**：首轮 `mock_sync.py` 未随补丁重启（Windows 下 `pkill` 未生效，两个进程同时监听 18099），日志格式为旧版。已 `taskkill` 清理并改用 18100 重新验证，S2/S3 证据全部来自新服务。

## 原始证据

根目录：`E:\creation-reading-assistant\next-round-integration-mumu-accept-v1\`

| 路径 | 内容 |
|---|---|
| `device-runs/*.txt` | 31 份原始 `am instrument` 输出（含命令、exit、OK/FAILURES、异常栈、强停校验与 `pid_after`）。每次运行独立文件名，**未覆盖任何旧失败日志** |
| `device-runs/*-signal.json` | 强停信号原件 |
| `device-evidence/*.json` | 20 份设备侧 JSON 证据（含 `mrg_34_remote_wins.json` / `mrg_34_local_wins.json` 两次策略的独立快照） |
| `screens/*.png` | 187 张截图（实测 1080×1920 竖屏，手机宽度） |
| `screens/` 与 `device-runs/*.xml` | 每步 UI 层级 XML |
| `mock-service/requests-*.jsonl` | 受控服务的逐请求记录（路径、方法、deviceId、token、请求体键与书籍列表），按场景分文件 |
| `mock-service/remote-config.json` | 受控远端数据配置 |
| `freeze/apk/*`、`freeze/meta/*` | 冻结双 APK、badging / manifest / signature 原输出 |
| `freeze/freeze-manifest.json`、`device-run-index.json` | 指纹与运行索引 |
| `logs/` | 定向构建完整日志（含失败那次） |
| `device-baseline/` | 前后对账：包、APK SHA、53 个持久文件 SHA、设置、forward/reverse |
| `ui.py`、`mock_sync.py`、`android/…/IntegrationMergedUiAcceptanceTest.kt` | 本轮驱动、受控服务与唯一新增仪器类 |

> 证据均为 **JSON**；instrumentation 结果 XML 由 `am instrument` 侧产出，两者不混称。

## 未覆盖项

- **Mock 服务只证明受控协议与接线**，不证明真实跨设备互通、真实桌面端实现或真实网络条件。
- **未验**：R8 / 正式签名 / 升级链 / 全量功能回归 / 实体硬件 / MIUI / 发布（F 阶段）。
- 输入侧 153 条 JVM 与 7 条接缝用例**未在本轮重跑**（输入未变，按复用规则引用输入报告）。
- 未验证 `qahostmergedv1` 之外的其他 UID 行为；未验证普通 debug 透传分支以外的 runner 分支。
- 同步只覆盖 `books` 类别的门控；`progress` / `inspirations` 类别的门控未单独构造非空远端数据做正反对照（S1 已证明三个开关都真实落盘并被 `syncNow` 读取透传）。
- SAF 只走「选择已有文件」与「导出另存为」两条真实路径；未覆盖 SAF 的其他入口（如第三方 provider、云盘）。
- 未测并发同步、断网重试、超时等异常路径。

## 恢复与副作用

**R1 全部完成并逐项对账：**

| 项 | 结果 |
|---|---|
| 本任务新增宿主/测试包 | 已卸载（`Success` ×2）；`/data/user/0/…qahostmergedv1` 与 `/sdcard/Android/data/…qahostmergedv1` 均确认**不存在** |
| 本任务公开目录 | `/sdcard/Download/cra-integration-mumu-accept-v1/` 已删除；**其他 Downloads 文件未触碰**（`cra-s04-samples-20260925` 等仍在） |
| 原宿主/测试 APK | 三个既有包 APK SHA **3/3 与基线完全相同**（`.qahost` `e82904cc…`、`.qahost.test` `fdbd0190…`、`mobile` `d647f0f7…`） |
| 受保护持久数据 | `.qahost` 的 files / databases(含 WAL/SHM) / shared_prefs **53/53 SHA-256 完全一致**（按本轮实际路径集合对账，未套用历史数量） |
| 系统设置 | 1080×1920 / 280dpi / rotation 0 / accelerometer_rotation 1 / timeout 60000 / stay_on 0 / night=yes / 无代理——`settings-before` 与 `settings-after` **逐字节相同**；测试期间的 `wm density 480` 与 `stay_on=7` 均已恢复 |
| ADB 映射 | 只撤销本任务创建的 `tcp:18099`、`tcp:18100`（未用 `--remove-all`）；收尾 `forward --list` 与 `reverse --list` **均为空**，与基线一致 |
| 受控服务 | 已停止；仅监听 127.0.0.1，未使用正式服务器/账号/凭证 |
| 额外状态 | 沙箱包在 AndroidKeyStore 中的主密钥别名与 `androidx.work.workdb` 随**卸载**一并消失（UID 级私有数据）；无遗留。**未产生**需要额外恢复的 KeyStore 或系统级状态 |

**恢复缺证据、原数据损坏或 APK/设置不一致的情况均未出现**，故 R1 判 PASS。

## 交付状态与下一步

**已交付待 Codex 核查。** 9/9 合同通过（A1–A5、S1–S3、R1），但这**只代表上述合同通过**：不等于全产品通过，也不等于实体手机通过；Mock 服务只证明受控协议与接线，不证明真实跨设备互通。

**建议下一步（由 Codex 决定）**：

1. 独立核查本轮原始证据，重点：`mrg_01` 的真实终止链（信号校验 + `pid_after` 空 + 新 PID）、`mrg_02` 的冷启动读回、`mrg_06` 的 ZIP 内容比对、S2 的「关闭不上传 / 开启上传」正反对照、S3 两次策略的独立快照、以及 R1 的三组逐字节对账。
2. 若核查通过，本合并输入在**新 UID 的真实生产链路**上的 Profile 恢复与同步设置接线即告验证；可据此推进新的 25 文件精确 hunk 范围与永久应用决策。
3. 未开启新功能、未追加全量历史测试、未提交、未推送、未启动下一任务。

**百分比**：本任务合同 **9/9 = 100%**；输入侧「集成准备三关」为 3/3；**新版本设备关 1/1**（本轮完成）；**永久应用/提交仍为 0/1**，完整联合交付未关闭。全产品完整分母仍未核定，不以本批局部 100% 代替全产品进度。
