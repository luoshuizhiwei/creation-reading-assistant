# CRA-INTEGRATION-BUILD-ACCEPT-v2：32 路径合流候选 + 定向回归 + 限定 MuMu 验收（2026-10-03/04）

## 任务编号/版本

`CRA-INTEGRATION-BUILD-ACCEPT-v2` / v2，2026-10-03 起、2026-10-04 完成。

- **执行应用/模型**：Claude Code（桌面 Code 标签页）；实际路由标识 `hw726brokbuddy AI/global:deepseek-v4.1-flash`
- **执行标识**：`CRA-INTEGRATION-BUILD-ACCEPT-v2-20261003-2335`
- **角色**：本批唯一候选集成 / 构建 / MuMu owner
- **状态**：**已交付待 Codex 核查**

## 输入指纹

| 项 | 值 |
|---|---|
| 活动仓库 / HEAD（预期=实际） | `D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919` / `e39a78da9c62f02614207cffb36969a32492eaea` |
| 暂存区 | 空（本批前后一致） |
| 完整产品基础（只读） | `E:\creation-reading-assistant\next-round-integration-ready-v1\android`（883 文件） |
| 基础 manifest（25 路径） | `a0240a395c20251f480378ed07ba901c0c0061c79cd98ea4b43985b229ed491d` |
| **32 路径清单** | `…\mdfix-review-20261003\payload32-preparation\manifest.json` → **`cf05ea577548de034dfbef16c612c658f60c48bdc4b62d352061a70ae7107358`**（scope 32，status：source-payload-only） |
| 32 路径 payload | `…\mdfix-review-20261003\payload32-preparation\payload` |
| 上游核查 | [MD 修复与 32 路径集成准备](../../reviews/2026-10/2026-10-03-next-mdfix-codex-review.md) |

**占用核对（开工前）**：`CRA-RESELECT-ROLLBACK-FIX-v3`、`reader-rollback-review` 均为零设备/只读；设备上无运行中 instrumentation、无应用进程，`forward`/`reverse` 均为空。两个 adb serial（`127.0.0.1:16416`、`emulator-5556`）**实为同一台 MuMu**（fingerprint/uptime/`nemu*` 服务一致，唯一监听者 `MuMuVMMHeadless.exe`）；本批统一使用 `127.0.0.1:16416`，全部命令显式 `-s`。

## 所有权与改动

- **唯一工作目录**：`E:\creation-reading-assistant\next-round-integration-build-accept-v2`
- **唯一仓库报告**：本文件
- **未修改**：D 盘共享产品源码、原仓库、桌面资产、HOLD；未 stage / commit / push / reset / checkout；未迁移或清理既有资产

### 四种状态分离（本批要求）

| 状态 | 落地位置 | 内容 |
|---|---|---|
| **源码合流** | `android/`（产品候选，后缀仍 `.qahost`） | 基础 883 → **886**（覆盖 32 路径，新增 3 个 MD/回滚文件） |
| **JVM 门禁** | 同产品候选 | 10 类 **217 用例，0 失败** |
| **构建** | `device/android/`（设备副本） | 后缀改为 `.qahostbuildv2` 后构建 |
| **MuMu 合同** | 设备副本 + `device-*` 目录 | 4 项设备合同 |

**副本分离**：产品候选 `android/` **保持 `applicationIdSuffix = ".qahost"` 未改**；设备副本 `device/android/` 仅改后缀并加仪器驱动/脚本；未把历史设备驱动、临时包后缀或其他候选整目录当作产品 payload。

## 环境

Windows 11 / Git Bash；JDK 17.0.14；SDK build-tools 34.0.0；Python 3.13；独立任务缓存（复制完成后才启动 Gradle，全程 `--offline`）。MuMu 模拟器（按宿主进程 + TCP 对应 + guest 特征判定，**不凭型号字符串**；model 被模拟为 `SM-S9280`）。手机宽度竖屏 1080×1920，批中 `wm density 480`，收尾 `wm density reset` 回 280。

## 执行与结果

### 1. 源码合流（32/32）

复制基础（排除 `.gradle`/`.kotlin`/`build`/任务缓存）→ 只覆盖 manifest 中的 32 路径 → 逐项验证：

- **覆盖后 SHA 与 manifest 一致：32/32，mismatch=0，missing=0**（`freeze/final-32-paths.json`）
- **新增 3 个文件**（基础中不存在）：`EpubRepositoryReplaceRollbackTest.kt`、`ReaderAnnotationNavigation.kt`、`ReaderAnnotationNavigationTest.kt`
- 其余 29 路径为对基础 25 路径 + 回滚 4 路径的覆盖写入
- **文件总数 883 → 886**（+3，与「新增 3 个」一致；无额外产品改动）
- **无临时后缀混入**：产品源码中未出现 `qahostmergedv*`/`qahostreadingv1`/`qahostjourneyv2`/`qahostlibrary` 等他批后缀（仅有输入自带驱动内的一处 `.qahostn1` 注释提及，非本批改动）
- **无外来驱动**：未带入其他候选的 `*AcceptanceTest.kt` 设备驱动

> 执行中一度因路径前缀处理把 32 个文件写成了 `android/android/...` 重复树（32 个文件），已**立即删除该重复树**并核实基础 883 文件未被改动，随后以正确前缀重新覆盖。该过程未污染产品候选。

### 2. 定向 JVM 门禁（217/217）

命令（`--offline --console=plain --max-workers=1`，exit 0）：

```
./gradlew.bat :app:testDebugUnitTest \
  --tests …JsonBridgeTest --tests …LocalZipBackupTest --tests …LocalZipRestoreStateStoreTest \
  --tests …WebDavCompatibilityTest --tests …SyncRepositoryLocalFactsTest \
  --tests …ProfileViewModelTest --tests …ShelfImporterTest \
  --tests …EpubRepositoryReplaceRollbackTest --tests …ReaderAnnotationNavigationTest \
  --tests …ReaderProgressNavigationTest
```

| 测试类 | tests | fail | err | skip | XML SHA-256（前16） |
|---|---:|---:|---:|---:|---|
| SyncRepositoryLocalFactsTest | 25 | 0 | 0 | 0 | `bfebd2f2da0ab8a4` |
| EpubRepositoryReplaceRollbackTest | 10 | 0 | 0 | 0 | `94fc5c4502846ad0` |
| JsonBridgeTest | 20 | 0 | 0 | 0 | `edcf8c6b644fc6fa` |
| LocalZipBackupTest | 62 | 0 | 0 | 0 | `ee07b852a850524f` |
| LocalZipRestoreStateStoreTest | 16 | 0 | 0 | 0 | `c23f2333723fe067` |
| WebDavCompatibilityTest | 27 | 0 | 0 | 0 | `61b143bdd05d2b4a` |
| ReaderAnnotationNavigationTest | 10 | 0 | 0 | 0 | `3a6cbc993d48bc05` |
| ReaderProgressNavigationTest | 7 | 0 | 0 | 0 | `3a3d3b0e2960a1c9` |
| ProfileViewModelTest | 3 | 0 | 0 | 0 | `30e359b2cf31804b` |
| ShelfImporterTest | 37 | 0 | 0 | 0 | `dffaeed710499a00` |
| **合计（10 类）** | **217** | **0** | **0** | **0** | — |

**按实际测试类去重**：217 = 6 组已验集成套件（153）+ ShelfImporterTest(37) + EpubRepositoryReplaceRollbackTest(10) + ReaderAnnotationNavigationTest(10) + ReaderProgressNavigationTest(7)。**不机械相加旧报告**。未跑全量 JVM / Lint / Release。

### 3. 构建与批次关联

| 批次 | 驱动 SHA-256（前16） | 宿主 APK（前16） | 测试 APK（前16） | 安装回读 | 变更原因 |
|---|---|---|---|---|---|
| B1 | `d5ca247b638b89ee` | `4bce2b4e…` | `98a0254d…` | 一致 | 首版驱动；构造参数与真实签名不符，build-01 编译失败保留 |
| B2 | `3f0df19e210a6fe0` | `4bce2b4e…` | `f3a70c5c…` | 一致 | 改按 `ShelfImporter` / `EpubRepository` 真实构造函数装配；TXT 保护夹具 backup 命名改用所选文件名 `content.txt.backup` |
| B3 | — | `4bce2b4e…` | `f3a70c5c…` | 一致 | 补 `original_file_name=content.txt`，使流程通过格式校验真正到达 backup 守卫 |
| **B4** | **`234a0f05f9d6f9f0`** | `4bce2b4e…` | **`6d83b10915e8952d`** | **一致** | TXT 断言改为匹配产品契约（守卫被 catch 转失败消息） |

**最终冻结（当前有效）**：

| APK | 包名 | 字节 | SHA-256 |
|---|---|---:|---|
| 宿主 | `com.creationreadingassistant.qahostbuildv2` | 51,371,712 | `4bce2b4ee9ae6f6194ad56d229f30556ca168f3bbd71eae2e92ef0b9e332cd04` |
| androidTest | `com.creationreadingassistant.qahostbuildv2.test` | 9,166,635 | `6d83b10915e8952d0b1db9e4841ed2699047a69c03e5cbcfaa3deaadf0db303c` |

`apksigner verify` 两包 exit 0，证书 `05249d78…b9a1`；宿主 manifest `application android:name="com.creationreadingassistant.App"`（**生产 Application，真实 Hilt/Room/DataStore 链路**）；androidTest `instrumentation android:name="com.creationreadingassistant.testing.CraIsolatedTestRunner"`、`targetPackage="com.creationreadingassistant.qahostbuildv2"`。**不把最终 APK 追溯绑定旧运行**。

### 4. 限定 MuMu 验收（4 项合同，全部 PASS）

| 合同 | 结果 | 关键实测 |
|---|---|---|
| **1. MD 单章非起点** | **PASS** | 保存 `offset=1383`（77.0%）→ 强停 → 冷读 PID 12843→**12904** 值一致 → 移到 94.9%（标记句不可见）→ 书内笔记「跳转」→ **77.0%，标记句可见** |
| **2. MD 多章跨章** | **PASS** | 第 3 章第 6 段保存 `offset=462` → 冷读 PID 13787→**13848** → 移到**第 4 章 95.3%** → 回源 → **第 3 章 46.8%**，落地页文本第 8 行即标记句，前为「第 3 章段落 5」、后为「段落 7」 |
| **3. TXT/EPUB 回滚保护** | **PASS** | TXT：既有 `content.txt.backup`（文件）→ `repairFile` 拒绝（原样返回明确原因），**target / backup 字节保全、DB 不写**；EPUB：既有 `ib2-epub.backup`（**目录**）→ `replaceStoredEpub` 拒绝，**target 保全、backup 保留、DB 不写** |
| **4. 正常替换冒烟** | **PASS** | 无残留时 `repairFile` 正常返回「《测试 TXT 中性戊》正文已修复」，**保护逻辑未阻断正常路径** |

**执行方式**：沿用既有结论——Compose 仪器环境无法让本阅读器 readOnly `BasicTextField` 产生选区，**选区/保存/查看/回源由设备侧 uiautomator 驱动**（`ui.py`），仪器只做夹具与真实持久数据断言；**未直写 DB 制造 PASS**。合同 3/4 为纯 Kotlin 断言（经 `ShelfImporter.repairFile` 与 `EpubRepository.replaceStoredEpub` 两个**实际仓库入口**）。

**一处产品契约如实记录**：TXT 的入口守卫在 `ShelfImporter` 内抛 `IllegalStateException`，但 `repairFileTyped` 的 `catch (Throwable)` 把它转成 `RepairOutcome(false, "修复失败：<原因>")` 返回，**不再向上抛**。因此「拒绝」的可观测形态是失败结果 + 明确原因（证据 `ib2_30` 的 `reason` 字段原文含「检测到未完成的替换备份 content.txt.backup…已保留该文件并停止本次替换」）。本批据此修正断言（属仪器口径修正，非产品改动）。

### 运行计数（区分口径）

- **方法运行 20 次**：17 PASS、3 FAIL（**全部为 `ib2_30` 的夹具/断言迭代，均保留原件**）
- **功能合同 4 项**：**4 PASS / 0 FAIL**
- 失败与调试痕迹全部保留在 `device-runs/`，每个运行独立文件名

## 原始证据

根目录：`E:\creation-reading-assistant\next-round-integration-build-accept-v2\`

| 路径 | 内容 |
|---|---|
| `freeze/final-32-paths.json` | **32 路径最终清单**：期望/实际 SHA、origin，mismatch=0 |
| `logs/overlay-result.json` | 覆盖过程：verified=32、mismatch=0、added/覆盖明细 |
| `logs/jvm-gate-*.log` | 定向 JVM 命令与原始输出（exit 0） |
| `android/app/build/test-results/testDebugUnitTest/*.xml` | **10 份原始 JUnit XML**（逐类 SHA 见上表） |
| `freeze/apk/*`、`freeze/meta/*` | 冻结双 APK + badging/manifest/signature 原输出 |
| `freeze/batch-association.json` | **逐批**驱动 SHA / 冻结 APK / 安装回读 / 运行清单 / 变更说明 |
| `freeze/device-run-index.json` | 运行索引与逐文件 SHA |
| `device-runs/*.txt` | 20 份仪器原始输出（含 3 份失败原件） |
| `device-evidence/ib2_*.json` | 10 份设备侧 JSON（保存/冷读/保护/冒烟/最终状态，含 PID 与 locator） |
| `device-evidence/*.xml`、`*.png` | 27 份 UI 层级 + 37 张截图，**实测全部竖屏**（portrait 37/37；`rotation="0"` 27/27） |
| `device-baseline/` | 前后对账：8 项设置、3 个原 APK、53 个持久文件、MD1/MD2 强停前 PID、安装回读 |
| `logs/build-0{1..5}-*.log` | 5 次构建日志（含失败与迭代） |
| `ui.py`、`rj.py`、`device/android/…/IntegrationBuildV2Test.kt` | 执行器、运行器、唯一新增仪器类 |

测试书为「测试 Markdown 单章 / 测试 Markdown 多章 / 测试 TXT 中性戊 / 测试 EPUB 中性己」中性名称。

## 未覆盖项

- 本批**只验 4 项受影响接缝**；**复用**旧 N1 / G1 / Profile / 同步完整旅程证据，**未整批重跑**。
- **未验**：实体手机 / MIUI、TalkBack、R8、正式签名 / 升级、WebDAV 实连、正式书库、AI / 续读卡 / 索引工程。
- 未跑全量 JVM / Lint / Release；仅在发现具体集成风险时才扩大（本批未触发）。
- 32 路径中**回滚目录残留**（`mdfix` 核查提到的「目录等同名残留尚未入口拒绝」）本批只验了 **backup 的目录形态**（EPUB 已覆盖），**细粒度目录残留用例未在本批补**。
- 本批未把候选应用到 D 盘共享源码，也未生成/验证 29 路径候选。
- v1 的 test APK 安装对应不确定性未由本批补清（本批独立建立自己的批次关联）。

## 恢复与副作用

| 项 | 结果 |
|---|---|
| 新增宿主/测试包 | 已卸载（Success ×2）；`/data/user/0/…qahostbuildv2` 与 `/sdcard/Android/data/…qahostbuildv2` 确认**不存在** |
| 本批夹具 | 随卸载消失（4 本书行/正文/高亮/backup 目录均在沙箱包私有目录） |
| 原宿主/测试 APK | 3/3 SHA 与开工基线相同 |
| 受保护持久数据 | `.qahost` files / databases（含 WAL/SHM）/ shared_prefs **53/53 SHA 一致** |
| 系统设置（8 项） | wm_size 1080×1920、density 280、user_rotation 0、accelerometer_rotation 1、screen_off_timeout 60000、stay_on 0、**night=yes**、http_proxy null —— **8/8 与开工基线逐字节相同**（本批开工实测 night 为 `yes`，全程未漂移；仅用 `cmd uimode night` 查询，未误用设置命令修改） |
| ADB 映射 | 本批未创建 forward / reverse；收尾均空 |
| 公共诊断临时文件 | 脚本实际产生 `/sdcard/d.xml`、`/sdcard/ib2-ui.xml`（uiautomator dump 写入），**已按实际路径删除**并 `ls` 确认不存在；`/sdcard` 其他条目未动 |

**如实登记的边界**：**卸载不等于实测 KeyStore 绝对零残留**——沙箱包主密钥别名与 `androidx.work.workdb` 随卸载消失，但未做 KeyStore 直接观测。**MuMu 只能称模拟器验收，不称实体手机 / MIUI 通过**。数据未损坏，恢复证据完整。

## 交付状态与下一步

**已交付待 Codex 核查。** 本批交付：32 路径合流候选（**32/32 SHA 一致**、无额外产品改动）、定向 JVM 门禁（**217/217**）、隔离设备构建与批次关联（**安装回读一致**）、**4/4 受影响接缝 MuMu 验收**（MD 单章非起点回源、MD 多章跨章回源、TXT/EPUB 回滚保护、正常替换冒烟），以及完整恢复对账（53/53 文件、3/3 APK、8/8 设置、映射空、临时文件清理）。

建议后续（由 Codex 决定）：
1. 独立核查 32 路径清单、217 用例 XML、批次关联表与 4 项设备合同的原始证据。
2. 若核查通过，本联合候选可进入「是否应用 D 盘共享源码」的独立决策（**本批不应用、不提交**）。
3. 未启动新功能、未回灌 D、未操作 Git、未发布。

**百分比（分口径，不相加）**：源码合流 32/32=100%；定向 JVM 217/217=100%；构建与冻结 1/1=100%（含安装回读一致）；**限定 MuMu 合同 4/4=100%**。方法运行 20 次 / 功能合同 4 项 —— **不以运行次数代替合同数**，也不以构建成功代替设备通过。全产品统一分母仍未核定。
