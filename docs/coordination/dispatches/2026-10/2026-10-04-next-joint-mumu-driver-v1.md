# CRA-JOINT-MUMU-DRIVER-v1：联合候选窄范围 MuMu 验收驱动准备

## 一、任务编号/版本

- **任务编号/版本**：`CRA-JOINT-MUMU-DRIVER-v1`
- **性质**：提前准备驱动与离线门禁。**本轮不连接或操作 MuMu、不安装 APK、不跑 Gradle。**
- **上游依据**：[MD 真章验收](../../../qa/2026-10/2026-10-04-next-md-true-chapter-accept-v1.md)、[同步/Profile WIP 三方合并](2026-10-04-next-sync-wip-reconcile-v1.md)、[回滚双实现保全合并](2026-10-04-next-rollback-wip-reconcile-v1.md)
- **状态**：**已交付待 Codex 核查**。**准备完成 ≠ 设备验收通过。**

## 二、输入指纹

| 项 | 值 |
|---|---|
| 恢复仓库 / HEAD | `D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919` / `e39a78da9c62f02614207cffb36969a32492eaea`（开工/收工一致，未变） |
| 唯一实施目录 | `E:\creation-reading-assistant\next-round-joint-mumu-driver-v1` |
| 重复执行检查 | 目标报告与本任务目录开工时均不存在 → **首次执行** |
| 只读参考 | `next-round-n1-runner-hardening-v1`（runner）、`next-round-md-true-chapter-accept-v1`（MD 验收类与 mdtc 客户端）、`next-round-integration-build-accept-v2`（ui/rj 驱动）、`next-round-sync-md-join-v1`（进行中的合流候选） |

### 2.1 验收合同核对（先核已有方法是否真正覆盖目标）

| # | 本轮受影响行为 | 已有覆盖 | 判定 |
|---|---|---|---|
| 1 | EPUB 同 URI V1→V2：生产替换入口读到 V2 | **无**。已验基线 `EpubParser.parse` 仅 4 参，`requireFreshSource` 出现次数 **0**；回滚合并报告已把该修复列为**被阻塞项**（依赖 `EpubParser.kt` 所有权） | **缺口**：驱动把该项标 `requires: ["require_fresh_source"]`，能力探测不到即 **NOT RUN**，不伪造 |
| 2 | 替换失败时正文与记录保全 | **JVM 已证**：回滚合并后 74/74（`ShelfImporterTest` 63 + `EpubRepositoryReplaceRollbackTest` 11）；本轮独立复算的修复版为 33/0 | **设备侧无注入接缝** → 只记「JVM 已证、设备 NOT RUN」，**不虚构设备注入能力** |
| 3 | MD 使用实际 H1 多章 | **有**：`MdTrueChapterAcceptV1Test#mdtc_01_proveProductionChapters`（4 个真实 H1，起点 0/377/730/1107） | **复用** |
| 4 | MD 真实跨章阅读/保存/冷启动/笔记离开与回源 | **有**：`mdtc_10 / mdtc_11 / mdtc_20 / mdtc_30`（MD 报告行为分母 5/5 即 01/10/11/20/30） | **复用** |
| 5 | 合流后 Profile/同步入口仍可达 | **JVM 已证门控**（`SyncRepositoryLocalFactsTest` 25 + `WebDavCompatibilityTest` 27 + `ProfileViewModelTest` 3 = 55/0）；**设备侧无** | **缺口**：本轮补一个最小 UI 探针（`uiprobe`），按实际文案断言入口可达 |

**没有受影响且输入一致的旧场景被重跑**：MD 五项复用其已验方法与夹具，不重跑恢复历史。

## 三、所有权与改动

**唯一仓库写入**：本文件。**唯一实施目录**：`E:\creation-reading-assistant\next-round-joint-mumu-driver-v1`。

| 文件 | 性质 | SHA-256 |
|---|---|---|
| `runner.py` | **复用，字节未变**（源 `next-round-n1-runner-hardening-v1/runner.py`） | `a634a344f0d8a8c0bf8d14ddee93518551fa5577f2950b61ab77445549c2167e`（两处相同） |
| `joint_driver.py` | 新增编排层 | `a7379249fe83c8968fc55811e36beae05e3b16b7e9231bf5b04ac440f0fb26f4` |
| `offline_test.py` | 新增离线门禁 | `6f9af573e46b3248205431ec9c758e65adc1bc116ffd9aa8dfba027f0275d410` |
| `offline-results.json` | 离线结果 | `47fc0289e19bd5a85b4413ecc91f301810ea60199577f70442cf856d1a8dbfdb` |
| `config.template.json` | 参数模板 | `40910c7e3d64a6add77815c33b04b97d19892d4e516697b69f1819f4b7a019c2` |
| `runbook.md` | 运行手册 | `1b1a8c203fd4976d995b6f48a2dd9bbec1e296c408a060f78e57eaee78c9477c` |
| `fixtures/fixture-manifest.md` | 夹具来源 | `9645ddd438ad9d3d8680b3b8a79bd178190f6713c1a9d73557751ae4cda78` |
| `execution.json` | 执行记录 | `f351f85dc1c112dc29c79525edc512135236fd456d79d8808f919e947527cc1f` |

**未修改**：任何 Android 产品源码、既有驱动（`runner.py`/`mdtc.py`/`ui.py`/`rj.py`）、输入候选、公共文档。

### 3.1 为什么复用而不是重写

`runner.py` 已覆盖本轮所需的**单方法**语义：显式参数（不推断包名/serial/runner）、**拒绝覆盖既有证据**、PASS 必须同时满足 ADB 退出 0 **且**真实 `OK (1 test)` **且**无失败/崩溃标记、`arm` 模式信号门（runId/PID/case/phase/0≤年龄<60000ms，**验证前绝不 force-stop**）。它另有 43 条离线用例与 N1 设备批证据。本轮**只补它没有的编排**（前置检查/基线/恢复/UI 探针/汇总），并复制为字节相同副本，SHA 双处一致。

## 四、环境

Windows 11 / Git Bash；Python 3.13。**零 ADB、零 MuMu、零 Gradle、零安装。** 离线门禁仅用标准库（`argparse/hashlib/json/re/pathlib/subprocess/tempfile`），不发起任何设备调用（`offline-results.json` 自证 `no_adb_or_device_calls = true`）。

## 五、执行与结果

### 5.1 驱动结构（判定逻辑与 adb 分离）

```
joint_driver.py
├─ 纯函数（离线可测，不碰 adb）
│  ├─ validate_config          缺键 / 空值 / 占位值(TBD|unknown|null|N/A|?) / 空 items → 拒绝
│  ├─ check_apk                缺文件 / SHA 不符 / 占位 SHA → 拒绝（返回实算值供对账）
│  ├─ check_sandbox_isolation  host==production、production 嵌在 host 下 → 拒绝
│  ├─ evidence_guard           同一 run_id 产物已存在 → 拒绝覆盖
│  ├─ parse_instrumentation / find_instrumentation   解析并核对 target 包
│  ├─ derive_capabilities      **只读探测候选目录**得出能力，不由 config 自报
│  ├─ item_verdict             缺能力 → NOT RUN；未执行 → NOT RUN
│  ├─ check_ui_expectations    UI 探针断言（存在性）
│  └─ aggregate                全 PASS + 恢复通过 → PASS；任一 FAIL / 任一 NOT RUN / 恢复失败 → 不通过
└─ Adb 薄封装（离线测试不构造）：devices / install / uninstall / base_apk_sha /
   settings_snapshot（七项+reverse+forward）/ data_manifest
```

**能力探测是防"声明即通过"的关键**：`require_fresh_source` 由 `EpubParser.kt` 中是否含 `requireFreshSource` 判定，`md_acceptance_class` 由候选内是否存在 `acceptance/MdTrueChapterAcceptV1Test.kt` 判定——都在**只读候选目录**上做，config 无法把它们写成 true。

### 5.2 离线门禁（55 用例，0 失败）

```
cd E:/creation-reading-assistant/next-round-joint-mumu-driver-v1
python -B offline_test.py      →  {"tests": 55, "failures": 0, "no_device_calls": true, "runner_reused_byte_identical": true}
```

覆盖任务点名的实际逻辑：

| 组 | 用例 |
|---|---|
| 脚本参数 | 缺键（5 种）、占位值（6 种）、空 `items`、项缺 `method` |
| 缺 APK / 错 SHA | 文件不存在、SHA 不符（并回报实算值）、大写归一、占位 SHA |
| 错误包名 | `host == production`、`production` 嵌在 `host` 下、占位包名 |
| 失败退出 | offline precheck 无设备 → 1；错 SHA → 1；`report` 跟随 overall（PASS→0 / FAIL→1） |
| 恢复异常 | 恢复失败 → 整体 FAIL，**即使全部项 PASS**；恢复失败在同时有 FAIL/NOT RUN 时优先 |
| 重复执行保护 | 同 run_id 拒绝；不同 run_id 通过 |
| instrumentation | 解析正确、target 不符、未注册、空输出 |
| 能力探测 | 只读得出、空候选=无能力、造出 `requireFreshSource` 后探测为真 |
| 单项/汇总 | 执行 PASS/FAIL、未执行=NOT RUN、缺能力强制 NOT RUN、NOT RUN→NOT PASS |
| UI 探针 | 齐全 / 缺失（回报缺哪些）/ 空 dump |

### 5.3 尚需最终构建提供的条件（具体到文件与参数，非占位）

| # | 条件 | 精确内容 | 当前状态 |
|---|---|---|---|
| 1 | 最终联合候选 `android/` | 合流后目录 | `next-round-sync-md-join-v1/android`（886 文件，后缀 `.qahost`）**仍在变，执行前须重核** |
| 2 | 宿主 APK 路径 + SHA-256 | 构建产物 | 待构建 |
| 3 | 测试 APK 路径 + SHA-256 | 构建产物 | 待构建 |
| 4 | **MD 验收类进测试 APK** | 文件 `app/src/androidTest/java/com/creationreadingassistant/acceptance/MdTrueChapterAcceptV1Test.kt`（源 SHA `991aa720d81ce892f67bb3cb534a1e2047c12a8f53e36d860ad277adbb1d1ab7`），类名 `com.creationreadingassistant.acceptance.MdTrueChapterAcceptV1Test`，方法 `mdtc_01/10/11/20/30` | **不在 join 候选内**（该候选内该文件计数 **0**）→ 缺它则 MD 四项 NOT RUN |
| 5 | **EPUB 同 URI V1→V2 能力** | `EpubParser.kt` 的 `requireFreshSource` + `EpubRepository.replaceStoredEpub` 调用方 + 对应仪器方法 | **已验基线内出现次数 0** → 缺它则该项 NOT RUN |
| 6 | 设备 serial | 运行时 `adb devices` 核实 | 不沿用历史 serial |

### 5.4 恢复契约（finally 覆盖实际涉及项）

`baseline` 采集 / `restore` 对账逐项覆盖：**沙箱数据目录逐文件 SHA 清单**（含 DB/WAL/SHM）、**宿主与测试 APK 的设备侧读回 SHA 与路径**、**七项系统设置**（size/density/accelerometer_rotation/user_rotation/night/stay_on/screen_off_timeout）、**reverse/forward 列表**。恢复动作：force-stop → 卸载测试包 → 卸载宿主包 → 逐项对账（含"卸载后沙箱数据目录必须为空"）。**任何异常或差异 → 返回 False → 整体不通过**，不在失败后继续标总 PASS。

## 六、原始证据

| 路径（`E:\creation-reading-assistant\next-round-joint-mumu-driver-v1\`） | 说明 | SHA-256 |
|---|---|---|
| `runner.py` | 复用副本（与源字节相同） | `a634a344f0d8a8c0bf8d14ddee93518551fa5577f2950b61ab77445549c2167e` |
| `joint_driver.py` | 编排层 | `a7379249fe83c8968fc55811e36beae05e3b16b7e9231bf5b04ac440f0fb26f4` |
| `offline_test.py` / `offline-results.json` | 离线门禁与结果 | `6f9af573…` / `47fc0289…` |
| `config.template.json` | 参数模板 | `40910c7e…` |
| `runbook.md` | 运行手册 | `1b1a8c20…` |
| `fixtures/fixture-manifest.md` | 夹具来源 | `9645ddd4…` |
| `execution.json` | 执行记录 | `f351f85d…` |

**复用的既有证据（不重跑）**：MD 五项方法与其自播种夹具（`MdTrueChapterAcceptV1Test`，源 SHA `991aa720…`，含 4 个 H1 章节、目标章 index 1）；`runner.py` 的 43 条离线用例与 N1 设备批结论；回滚 JVM 74/74 与同步门控 JVM 55/55。

## 七、未覆盖项

| # | 项 | 说明 |
|---|---|---|
| 1 | **任何设备执行** | 本轮零 ADB/零安装；驱动未经真机跑过 |
| 2 | 条件 4（MD 验收类进测试 APK） | 未落地 → MD 四项将在首跑时 NOT RUN，除非构建方带上该文件 |
| 3 | 条件 5（EPUB `requireFreshSource`） | 被阻塞（依赖 `EpubParser.kt` 所有权）→ 该项 NOT RUN |
| 4 | 条件 1（最终候选冻结） | 合流候选仍在变；本轮只读核对其当前形态，未冻结 |
| 5 | 文件级故障注入的设备侧证据 | 设备无接缝，**只能**记「JVM 已证、设备 NOT RUN」 |
| 6 | 实体手机 / MIUI | 不涉及；结论只写 MuMu |
| 7 | 截图与 DB 的联合解释 | 由执行者按 runbook §4 采集；本轮不产出设备截图 |

**没有建立"永远待确认"的占位任务**：条件 4/5 已具体到文件路径、类名、方法名与 SHA；条件 1 已具体到候选目录与文件数。

## 八、恢复与副作用

- **仓库**：HEAD `e39a78da…` 未变；未 stage/commit/push/reset/checkout；未更新 `current.md` 或 Obsidian。**唯一仓库写入 = 本文件**。
- **只读输入**：`next-round-n1-runner-hardening-v1`、`next-round-md-true-chapter-accept-v1`、`next-round-integration-build-accept-v2`、`next-round-sync-md-join-v1` 全部**只读**，未修改、未覆盖。
- **设备**：未连接、未安装、未操作 MuMu；未触碰正式书库。
- **D 产品源码 / 其他窗口工件**：未触碰。
- **本轮无恢复动作需要**：只在自有 E 目录新增文件，无外部副作用。

## 九、交付状态与下一步

**已交付待 Codex 核查。**

- **交付**：`runner.py`（复用，字节未变）+ `joint_driver.py`（编排）+ `offline_test.py`/`offline-results.json`（**55 用例 0 失败，无设备调用**）+ `config.template.json` + `runbook.md`（命令模板/前置/证据清单/边界）+ `fixtures/fixture-manifest.md`。
- **结论口径**：本轮是**准备完成**，不是设备验收通过；MD 五项与替换保全均引用既有已验证据，未重跑。
- **下一步（待 Codex 与最终候选）**：
  1. 构建方带上条件 4 的 `MdTrueChapterAcceptV1Test.kt`，并就条件 5（`requireFreshSource`）给出决定——两者都**不影响驱动启动**，缺哪项就自动 NOT RUN；
  2. 最终 APK 产出后填 `config.json`（路径 + 实算 SHA），按 `runbook.md` §2 顺序执行；
  3. 执行者须为当时唯一 MuMu 所有者；`precheck` 通过前不得安装。
