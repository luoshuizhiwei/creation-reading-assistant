> **2026-10-01 最终收口（当前权威状态，覆盖下方历史快照）：** A–E恢复计划5/5=100%，阶段E限定退出。本地提交 `e39a78da9c62f02614207cffb36969a32492eaea`（142文件/433语义hunk），JVM2628/2628、Lint0错误/6警告、MuMu52/52及三组冷启动190帧核对完成；51持久文件/原APK对/密度与系统设置恢复一致。用户明确授权MuMu与精确本地提交；未push、未搬目录、未改原损坏仓库。提交前后3004路径字节变化0，既有HOLD/桌面/混合WIP均保护，暂存区空、fsck退出0。简单只读核查使用GPT-6 Luna，未使用重置卡。本轮已停止，E heartbeat保持PAUSED。实体手机/MIUI、TalkBack语音、真kill、整包DB+文件原子性与F发布仍未验；100%仅指已约定的A–E恢复计划，不是整个产品功能或发布完成率。

## 2026-10-01 最终验收与本地提交（权威结论）

- 当前健康版本入口：恢复仓库 `D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919` 的提交 `e39a78da9c62f02614207cffb36969a32492eaea`。原损坏仓库继续保留；不迁移目录。共享工作树仍有混合用户资产，不能直接将它当作已验候选。
- 提交父版本：`513891d6a66a9b78e072ba759d34ed580d377ecf`；精确142文件/433hunk，未纳入报告、截图、缓存或HOLD。`precise-commit/result.json`：3004路径保护、工作树字节变化0、暂存区空、Git fsck退出0、未push。
- 受验输入/可复现入口：`E:\creation-reading-assistant\stage-e-final-v2-20260930\android`。冻结manifest与HEAD对应源码一致；`final-freeze-accepted`保留精确补丁/冻结源码，`precise-commit/protected-assets`保留原共享用户资产副本。回退基线保留为父提交，不在脏工作树执行reset/checkout。
- 构建环境：JDK17 `D:\develop\Java\jdk-17.0.14`、SDK `D:\develop\Android\Sdk`、GRADLE_USER_HOME `E:\creation-reading-assistant\stage-e-final-v2-20260930\_cache\gradle-home`；在上述E盘android执行 `gradlew.bat --offline --max-workers=1 :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleQahost :app:assembleQahostAndroidTest -PcraAndroidTestBuildType=qahost`。现有最终门禁日志成功；不在共享脏工作树重复构建。
- JVM2628/2628；最终Lint XML实算0错误/6警告（纠正旧READY7警告）；MuMu手机43+平板9=52/52。实际S12钳制/不移动保marker/翻页变native，S05取消读完保位置时长，S07选区保存与native定位，G6单位重启持久化及S06三模式190帧均有证据。
- 两处既有EOF警告保留。初轮仪器选择器/布局/夹具状态问题及修复链保留在QA报告，不抹去失败日志。所有最终修正仍在原142路径集合。
- 恢复51冷备持久文件0 mismatch；原APK对SHA及density280/night yes/stay0/rotation0/accelerometer1一致；专属临时WebDAV与ADB reverse已停止。正式包不存在，未触碰正式书库/真实手机。
- 资产归属继承`2026-09-30-stage-e-freeze-scope-and-asset-corrections.md`的逐项账本与`candidate-vs-shared-assets.csv`；122 HOLD维持保护，不代表通过或应清除。旧55+122+3计数以已勘误122/54/4及H007混合说明为准。既有65/67资产集合差继续有记录，未被吞并进本提交。
- E退出条件限于健康版本入口、资产去向明确、可复现构建及保留回退材料；上述条件已满足。实体手机/MIUI、实际TalkBack语音、真process-kill及整包原子性仍NOT RUN，不扩展F。
- [最终MuMu验收](../../../qa/2026-10/2026-10-01-stage-e-final-codex-mumu-acceptance.md)。全部大证据在 `E:\creation-reading-assistant\stage-e-final-v2-codex-acceptance-20261001`；`acceptance.json` / `precise-commit/result.json`可机读复核。

### 后续独立范围（本轮停止）

HOLD产品选择、真实硬件兼容性、TalkBack语音、真kill及整包原子性和F发布须以后单独安排；当前用户没有授权本轮继续F。E heartbeat已PAUSED；没有消耗重置卡。

### 下方为本轮早期历史快照

# 阶段 E：Codex 接手最终 V2 收口（2026-10-01）

## 结论与范围

- 外部三窗不是全完成：Reader READY=false，联合 JVM 原 2623/11，设备报告仅 WAITING。Codex 已补真正缺口。
- 最终 V2 **源码/JVM/构建就绪**，READY=true **只代表可以进入限定设备验收**，不代表设备通过、阶段 E 退出或已提交。
- 总恢复计划 A–E 仍 **4/5=80%**；它是恢复阶段比例，不是整个产品功能比例。
- 最新本轮 AGENTS.md 要求真实手机、不使用 MuMu；`adb devices` 仅 `127.0.0.1:16416`。因此本轮设备 **NOT RUN**，未安装、未改 DB/文件/密度、未接触正式书库。
- HEAD `513891d6a66a9b78e072ba759d34ed580d377ecf`；暂存区空。新 stage/commit 尚无精确授权，未提交、推送、迁移或修改原仓库。所有产品修改仅在 E 盘隔离候选。

## 根因与修复

1. 6 项快照守卫失败是真闭包缺失：捕获了 selectionSnapshot，规则/AI 助读/AI 解读/灵感却读活动选区；保存回调回读实时文字和 locator。正文选区清除会丢摘录、丢坐标，规则草稿可能重建。
2. 5 项 TOC 守卫失败主要是常量/旧回调形态耦合，不代表 5 个实际错跳。统一 typed target 接口以防再次误用。
3. 仅改四个生产文件：ReaderSheetHost（所有消费者用同一 VM 快照、文字与 locator 显式传入保存）、ReaderLayerBuilders（消费同源参数、统一 typed 回源并关 sheet）、ReaderViewModel（CloseSheet 释放快照）、ReaderRulesSheet（钉住本次弹层预填）。没有引入日夜视觉、系统栏或路由重构。
4. 新增 ReaderInspirationSnapshotSaveTest 四条运行期测试：AI 解读与灵感在实时选区清除后保存原摘录/locator；typed note 回源并关闭；无选区重开不继承旧快照。
5. 一个只读子智能体分别定位与复核限定变更，未发现具体回归。未派发额外实现窗口、未重做充分验证的旧候选。

## 新鲜验证

| 检查 | 本轮结果 |
|---|---|
| 原失败组 + ReaderNoteSnapshotSaveTest + ReaderViewModelTest 定向 | EXIT 0，`targeted.log` |
| 新增 4 条运行期回归 | EXIT 0，4/4，`snapshot-regression.log` |
| 一次完整 `testDebugUnitTest` | EXIT 0，285 XML，2627 tests，0 failure/error/skip |
| `lintDebug` | EXIT 0，0 errors / 7 warnings（既有报告结果，任务重新成功执行） |
| `assembleDebug` + `assembleDebugAndroidTest` | EXIT 0 |
| `assembleQahost` + `assembleQahostAndroidTest -PcraAndroidTestBuildType=qahost` | EXIT 0 |
| APK 内 Manifest | qahost.test → qahost，CraIsolatedTestRunner |
| 恢复仓库 `git fsck --full --no-reflogs` | EXIT 0，通过 |
| 原仓库同项只读诊断 | EXIT 10，仍有无效 cache-tree / 缺失对象；保留原目录 |
| 字节补丁 HEAD 复算 | 872 files，mismatch 0 |
| 语义补丁 HEAD 复算 | 872 files，CRLF→LF 后 mismatch 0；142 files / 424 hunks |
| 设备 UI / TalkBack / DB 恢复 | NOT RUN，不将旧 MuMu 结果外推新 APK |

证据目录：`E:\creation-reading-assistant\stage-e-final-v2-20260930\_codex_20261001`。完整门禁日志 `full-debug-gates.log`；qahost 日志 `qahost-gates.log`。构建与缓存均在 E 盘。

## 冻结产物与精确资产边界

- 候选：`E:\creation-reading-assistant\stage-e-final-v2-20260930\android`；入口：`E:\creation-reading-assistant\stage-e-final-v2-20260930\READY.json`。
- 对 HEAD 共 **142 文件：42 ADD + 100 MOD**。其中规则文件原不在 140 表，本轮新增为 MOD；运行期测试新增为 ADD。
- 精确逐文件内容 SHA：`E:\creation-reading-assistant\stage-e-final-v2-20260930\_codex_20261001\final-source-manifest.csv`。
- 与共享恢复工作树逐文件归属对照：`E:\creation-reading-assistant\stage-e-final-v2-20260930\_codex_20261001\candidate-vs-shared-assets.csv`。未把候选整文件覆盖共享资产；未清理 HOLD、桌面或其他既有 WIP。
- 字节级补丁：`E:\creation-reading-assistant\stage-e-final-v2-20260930\_codex_20261001\final-from-head.patch`，SHA `53f6079d8767956c3170fdfe232479370bab8b2801145a53144a9c9703466614`。
- 精确语义 hunk 补丁：`E:\creation-reading-assistant\stage-e-final-v2-20260930\_codex_20261001\final-semantic-from-head.patch`，SHA `45980367cb86767c0b2368257a6f5b94bc74bc70509a6ae3d019fb639135bc99`。仅补丁统一换行，候选原字节保留；两处既有 EOF 空行警告登记在 `semantic-apply-check.log`，不能宣称 diff-check 全清。
- 不按旧六组提交建议直接拆：分组的中间态未编译，未证明独立闭包。当前验证支持完整 142 文件候选；正式暂存/提交必须按精确 hunk 与既有资产再确认。

### APK 哈希

| 产物 | SHA-256 |
|---|---|
| debug | `0e0d995a8901feaa3af0fbcf9e7e52ecd14edf7f56e9e80de6c71aed23a15adf` |
| debug_test | `20c69748f95b42f298ddcd12b830df6775db0d1c2eb3510e550c4fc7769f9d4f` |
| qahost | `ed93b16ae202af17014659af998d70274d2f573ba4e005da33123925f2d72587` |
| qahost_test | `caee6df0747f7eaab08b39bf8ff4dac651c8891d1969ab1d5af490235e9e7835` |

## 下一步与停止边界

1. 对这份最终冻结 qahost host/test 做独立设备验收；当前只有 MuMu，最新约束下不执行。
2. 设备结果与产品边界核对后，以精确 142 文件/424 hunk 提案取得新本地提交授权；当前未 stage/commit，原 A/B 授权不能扩展到本批。
3. 旧 Reader READY=false 与三窗历史报告保留；新候选事实以本报告和最终 READY 为准，旧失败/WAITING 不是当前构建状态。
4. 用户授权最多使用一张重置卡，限实际额度耗尽后；本轮额度未耗尽、未使用重置卡。只读工具能看到卡余额，当前无可调用的消耗卡接口，不能虚称已自动重置。
5. 当前可自主完成的源码、构建、精确冻结与证据整理已完成。受设备与精确提交授权边界限制，阶段 E 尚未退出；不扩展 F 发布、不制造耗额度任务。
