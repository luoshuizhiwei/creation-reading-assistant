# CRA-INTEGRATION-MUMU-ACCEPT-v2：progress/inspirations 门控补验与恢复收尾（2026-10-03）

## 任务编号/版本

`CRA-INTEGRATION-MUMU-ACCEPT-v2` / v2，日期 2026-10-03。

- **执行应用**：ZCode（桌面 CLI）
- **实际模型**：`account:zai-start-plan`（GLM）
- **开始时间**：2026-10-03T09:32:51+08:00
- **角色**：本批唯一 MuMu 操作者；P1 修复窗口独立并行，其改动未混入本批输入
- **状态**：**已交付待 Codex 核查**（本轮仅补 v1 缺口，不重跑 A1–A5/S1/S3/153 JVM，不判断 N1/整批验收）

## 输入指纹

| 项 | 值 |
|---|---|
| 工作仓库 / HEAD | `D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919` / `e39a78da9c62f02614207cffb36969a32492eaea`（开工=收工，未暂存/commit/push/reset/checkout） |
| 冻结产品输入 | `E:\creation-reading-assistant\next-round-integration-ready-v1\android` |
| **25 文件指纹** | **25/25 与 `final-manifest.json` 的 `candidate_sha256` 一致**（本轮实算；manifest head 一致） |
| 输入侧 153 JVM | 未重跑（输入未变，按复用规则引用 `final-manifest.json` tests 列表：25+20+62+16+27+3=153） |
| v1 参考 | `next-round-integration-mumu-accept-v1`（只读；`execution.json`、`device-baseline/`、`device-runs/`、`mock-service/` 均未改动） |
| v2 工作目录 | `E:\creation-reading-assistant\next-round-integration-mumu-accept-v2\`（本轮新建，唯一写入点） |

**占用与设备身份**：Root 侧 `adb devices` 为空 → 本轮 `MuMuManager.exe api -v 1 launch_player` 启动 MuMu（index 1）+ `adb connect 127.0.0.1:16416` 成功。占用核验（`_evidence/occupancy.txt`）：无运行中 instrumentation/应用进程；既有包仅 `qahost`/`qahost.test`（原 qahost，全程未触碰）。MuMu 判定依据同 v1（宿主进程 + guest 特征），不构成实体手机证据。

## 所有权与改动

- 唯一可写任务目录：`E:\…\next-round-integration-mumu-accept-v2\`（本轮新建）。
- 唯一仓库可写报告：本文件。
- 本任务副本内改动（均在 E 目录副本，未回灌）：① qahost build type `applicationIdSuffix` 由 `.qahost` 改为 **`.qahostmergedv2`**（本任务隔离后缀单 hunk）；② **新增** `app/src/androidTest/java/com/creationreadingassistant/feature/sync/IntegrationSyncGatingV2Test.kt`（本轮唯一新增仪器类，6 方法）。
- 未改：产品源码、既有测试、runner、依赖、原候选/原报告/原日志、current/TODO/项目页、HOLD、桌面资产、原损坏仓库。
- **测试包预存在检查**：安装前 `pm path com.creationreadingassistant.qahostmergedv2(.test)` 均为空（新包首装，非复用资产）；卸载后再次确认为空。

## 环境

Windows / Git Bash；JDK 17.0.14；SDK build-tools 34.0.0；Python 3.13。缓存复用 `next-round-joint-j1-v2\_cache\gradle-home`（E 盘，本轮独占）。Mock 服务 `mock_sync.py`（v1 同版）仅监听 `127.0.0.1:18101`，经 `adb reverse tcp:18101` 映射；token 为本地字面量。**runner 透传生产 App**（`.qahostmergedv2` 不匹配隔离分支）——本轮验的是真实 Hilt/Room/DataStore 链路，不声称裸 Application 隔离。

## 执行与结果

### 构建、冻结、安装（版本对应补证）

| APK | 包名 | SHA-256 |
|---|---|---|
| 宿主（冻结=安装=回读） | `com.creationreadingassistant.qahostmergedv2` | `7de243c08287fa168634e88611a12b0b90ad0ec34e594589f1b108b4e6725411` |
| androidTest（冻结=安装=回读） | `com.creationreadingassistant.qahostmergedv2.test` | `b29f858d4c983996f7df20a5c17508094fe6f35ae87f5dd492cadd215af6b1f5` |

`pm path` 安装回读 SHA 与冻结件**逐位一致**（`freeze/meta/installed-paths.txt`）——本轮新用例全部运行在此已回读的 test APK 上，版本对应成立。instrumentation runner/targetPackage 按 manifest 实测：`CraIsolatedTestRunner` / `com.creationreadingassistant.qahostmergedv2`；签名 exit 0（证书 `05249d78…`）。

### 补测 1（S2-P）：progress 门控非空关/开正反对照 — **PASS**

夹具（`mrgv2_40`，DAO 种子=夹具准备）：book `mrg-b1`（rev2/available/真实正文）+ progress `mrg-b1`（rev2/10%/reading）+ inspiration `mrg-i1`（rev2）；mock 远端配置**非空**：books/progress/inspirations 全部 rev99（`mock-service-remote-config.json`）。

- **关闭**（执行者真实 UI 把三开关全关 → 点「立即双向同步」）→ `mrgv2_41_assertClosedGating` **PASS**：
  - DataStore 落盘 `syncBooks/syncProgress/syncInspirations = false×3`（门控状态证据，非"没有数据"）；
  - 真实 Room：book/progress/inspiration 三行**逐字段未变**（远端 rev99 未被 pull 应用）；
  - mock 请求日志 `requests-v2-off.jsonl`：`POST /sync/push bodyKeys=["device"]`——**progress/inspirations/books 均未上传**。
- **开启**（执行者真实 UI 开「阅读进度与阅读历史」「灵感火花与随笔批注」，books 保持关 → 再同步）→ `mrgv2_42_assertOpenGatingApplied` **PASS**：
  - **pull 正向**：progress 行 `revision=99`、`progress_percent=99.0`（桌面 0.99×100）、state=reading，且 `current_location_json` 本机定位**保留**（R1 合并语义）；inspiration `revision=99`、title=`MRG REMOTE INSP TITLE`；
  - **books 反向对照**：book 行仍 `revision=2`/`测试 TXT`（同一同步内 books 关闭不被应用）；
  - **push 正向**：`requests-v2-on.jsonl` 的 `POST /sync/push bodyKeys=["device","inspirations","progress"]`，progress/inspirations 条目 `revision=99` 上传；books 键缺席。
- 证据：`device-evidence/mrgv2_40/41/42.json`、`mock-service/requests-v2-off.jsonl`、`requests-v2-on.jsonl`、`device-runs/s2-*.txt`、`screens/`。

### 补测 2（S2-I）：inspirations 门控非空关/开对照 — **PASS**

与 progress 同轮执行（上表）：关闭时 inspiration 行逐字段未变 + push 无 inspirations 键；开启后远端 rev99 应用且 push 含 inspirations 条目。**不是**用"没有数据"证明门控。

### 补测 3：v1 test APK 版本对应调查 — **结论落盘**（`_evidence/v1-test-apk-timeline.md`）

| 时间 | 原件 | 内容 |
|---|---|---|
| 00:46→00:52 | `build-qahostmergedv1-20261003-004609.log`（SUCCESS，全量） | 产出 **test `cd1e2e99…`** |
| **00:53:20** | `device-baseline/installed-apk-sha.txt`（mtime 实测） | 记录 test=`cd1e2e99…`（首装回读，覆盖 00:53–01:14 的运行） |
| 01:18:36 | `build-02-011819.log`（SUCCESS，增量） | 产出 build-02 test（v1 报告自报 `caae407e…`，**无独立回读原件**）；覆盖 01:18:48–01:30:35 的运行 |
| 01:32:49→01:32:50 | `build-03-013240.log`（SUCCESS）+ freeze 拷贝（mtime 实测） | 冻结件 = build-03 产物 = `11686013…` ✓ |
| 01:33:01–01:38:34 | device-runs 末 5 份（mrg_34 两次 PASS、mrg_90 等） | 按时间线在 build-03 安装后 → **可引用为最终 test 版本运行（推断，非回读实证）** |

**结论**：`installed-apk-sha.txt` 的 `cd1e2e99…` 是 **build-01 的 test APK**（首装回读，mtime 00:53:20），不是最终版；最终冻结 test（11686013）的**安装回读原件缺失**；`freeze/freeze-manifest.json` 确不存在（v1 报告引用有误）。A1–A5/S1/S2 运行在更早 test 构建（驱动代码差异、产品宿主同）；其结论按 v1 原件各自成立，**不自动转嫁**给最终 test SHA。本轮新门控证据无此缺口。

### 补测 4：恢复收尾原件 — **PASS**（原始输出落盘 `device-baseline/`）

| 项 | 原始输出 |
|---|---|
| 卸载沙箱双包 | `uninstall-output.txt`：`Success` ×2 |
| 新目录消失 | `dirs-after-uninstall.txt`：`/data/data/…qahostmergedv2` 与 `/sdcard/Android/data/…qahostmergedv2` grep 均空 |
| reverse/forward | `forward-reverse-cleanup.txt`：before 列表含 `tcp:18101`，`--remove` 后 after **空**；forward 全程空 |
| 受控服务停止 | `service-stop.txt`：`taskkill //PID 6888 //F` 成功（首查 `$!` 误杀子壳 PID 219 已如实留痕并更正为实际监听 PID）；stop 后 18101 无监听 |
| 既有三包 APK | `existing-apks-readback.txt`（新鲜只读回读）：qahost `e82904cc…`、qahost.test `fdbd0190…`、mobile `d647f0f7…` — **3/3 与 v1 基线一致** |
| qahost 持久文件 | `qahost-files-readback.txt`：**53/53** 全部一致（v1 `qahost-files-sha-before.txt` 的同一路径集合；46 files/databases 项 + 7 shared_prefs 项；root 后 python 通道对账——直连 shell 对 shared_prefs 返回空属解析问题，已注明） |
| 系统设置 | `settings-after.txt`：1080×1920 / 280dpi / rotation 0 / accel 1 / stay_on 0 / 无代理（本轮未改任何设置，fresh 只读回读） |
| KeyStore | **不据卸载自述声称直接观测到无遗留**——KeyStore 为 UID 级私有数据，卸载后按平台语义消失，本轮无直接观测手段（如实声明） |

### 补测 5：v1 计数更正

| 项 | v1 报告口径 | 更正后 |
|---|---|---|
| 设备方法运行次数 | 31 次 = 28 PASS + 1 TERMINATED + 1 FAIL + 1 早期无效 | **30 次**（`device-runs/*.txt` 实数）= **28 OK + 1 FAIL（mrg_34 首版判据缺陷）+ 1 TERMINATED（mrg_01 arm）**；早期 `mrg_04`（01:02:40）是 28 个 OK 中的 1 个，**判无效**（SAF 未真正打开即断言假通过，已被 01:06:33 有效运行取代）→ **有效验收运行 = 27 OK** |
| test APK 字节 | 9,166,635 | 实际 9,327,009（Codex 复核一致；v1 报告字节为笔误/旧值） |
| 冻结清单 | `freeze/freeze-manifest.json` | **不存在**（v1 报告引用有误）；以 `device-run-index.json`（30 条）+ `freeze/meta/*` 为准 |

## 原始证据

`E:\creation-reading-assistant\next-round-integration-mumu-accept-v2\`：`execution.json`、`freeze/`（双 APK + sha256 + badging/instrumentation/signature/installed-paths）、`device-evidence/`（mrgv2_30/40/41/42/90 JSON）、`mock-service/`（requests-v2-off/on.jsonl、remote-config、service.log）、`device-runs/`（7 份 am instrument 原始输出）、`device-baseline/`（settings/packages/forward/reverse/uninstall/dirs/service-stop/apks-readback/qahost-readback）、`_evidence/`（占用检查、树差待归档、v1 时间线调查）、`ui2.py`、`logs-build.log`。

## 未覆盖项

- Mock 服务只证明受控协议与接线，不证明真实跨设备互通/真实桌面端/真实网络。
- A1–A5、S1、S3、153 JVM 未重跑（按复用规则引用 v1 原件 + 本轮时间线调查的运行→APK 对应）。
- books 门控的关/开对照沿用 v1 S2 原件（本轮 books 仅作开启阶段的反向对照）。
- KeyStore 无遗留为平台语义推断，非直接观测；真实跨端、实体硬件/MIUI、R8/签名/升级/F 阶段维持原边界。
- progress 的 push 体为 R1 修复后形态（promoted 列回填）；桌面端真实实现行为不在本包范围。

## 恢复与副作用

见补测 4（全部原始输出落盘）。本轮零产品源码写入（改动均在 E 副本）；原 v1 工件只读未动；HEAD 未变；未暂存/commit/push/reset/checkout；未触碰正式书库/实体手机/其他窗口输入。

## 交付状态与下一步

**已交付待 Codex 核查。** 本轮补验 2/2（progress/inspirations 门控 PASS）+ 版本对应（本轮冻结=安装=回读成立；v1 时间线调查结论落盘）+ 恢复收尾原件（PASS）+ 计数更正（30=28+1+1，有效 27）。
v1 九合同的 **S2 由部分转 PASS（books/progress/inspirations 三类齐备）**；R1 由部分转 **PASS（补原件 + 新鲜只读回读；KeyStore 保留平台语义声明）**；最终 test 版本对应：**v1 批部分（时间线可引用，回读缺失如实登记）**、**v2 批成立**。
下一步（Codex 决定）：① 独立核查本轮 `device-evidence/mrgv2_41/42` 与 `requests-v2-off/on.jsonl` 的正反对照；② 决定 v1 早期运行（cd1e2e99/build-02）证据的采纳口径；③ 合流与永久应用仍需新精确清单授权。**不自动启动阅读旅程或应用/提交。**

**百分比**：本轮补验 2/2 = 100%（限 progress/inspirations 门控）；v2 批合同 2/2 = 100%（限本轮新增范围）；v1+v2 合计 9/9 合同全部有可采纳证据（其中 S2/R1 由本轮补齐、最终 test 版本对应限 v2 批成立）；全产品分母仍未核定，不给全产品完成率。
