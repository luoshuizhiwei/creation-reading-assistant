# S04 QA host 真机只读预检（新恢复点可行性）

- 日期：2026-09-25
- 仓库：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`
- 性质：**真机只读预检**，仅为决定"是否有条件建立新恢复点"提供实测输入。不是安装，不是验收，不构成对设备阶段的放行。
- 输入：
  - `docs/code-review/2026-09/2026-09-25-s04-qahost-predevice-review.md`（验机前独立审查，§5/§6/§7 为本预检的判据来源）
  - `docs/coordination/legacy-stages/2026-09/2026-09-25-s04-qahost-offline-integration.md`（集成者离线复验）
  - 本仓库 `AGENTS.md`（真机约束：仅真实手机、先 `adb devices`、逐条 `adb -s <serial>`、中性书籍称呼）
- 本窗口唯一写入 = 本报告。设备侧零写操作：未安装/卸载、未运行 instrumentation 或 Gradle connected 任务、未创建备份、未复制数据库或书籍、未打开应用界面、未 force-stop、未清理数据、未改动常亮/方向/权限、未取消 WorkManager、未使用模拟器；主机侧未触碰原始仓库，未 commit / push / stage / reset / checkout / clean。

---

## 0. 结论

**只读预检全程完成，未触发"命令需修改状态才能继续"的中途停止条款；新恢复点判定为"有条件可行"。**

1. 设备为唯一连接的真实手机（serial 脱敏 `c49***6cf`），Redmi K50 Ultra / Android 15（SDK 35），当前竖屏。非模拟器。
2. 正式主包已安装：versionCode 3 / versionName 0.5.1，DEBUGGABLE；**两个 qahost 包（`com.creationreadingassistant.qahost` / `.qahost.test`）均未安装**——前置审查 §7-B8（既有沙箱数据处置）在当前设备状态下不触发。
3. `run-as` 对正式包**可用**；设备上 `sqlite3` **不存在**——这是唯一实质性的只读能力阻塞项：前置审查 §6 首选的"`run-as` + `sqlite3` 在线一致快照"通道在当前设备上不可用，恢复点建立时若采用任何替代（推送静态 sqlite3 二进制属设备写、复制 db+wal+shm 三件套属"临时副本"路径）均属**用户决策**，本窗口不做、不预授权。
4. 正式包三类数据实测聚合合计 **2,293,468,585 字节 ≈ 2.14 GiB（约 2.29 GB）**。据此估算：完整可恢复备份档约 **2.6–3.0 GB**（建议预留 ≥ 4 GB）；仅对账基线档约数十 MB、最坏数百 MB。主机 E 盘空闲 345 GB，空间充裕；D 盘空闲 36 GB、C 盘空闲 46 GB。
5. **强调（诚实登记）**：以上全部为**实测聚合 + 估算**。估算不是已完成的备份；新恢复点目前**不存在**。在用户确认空间/位置/保留期限、恢复点实际建成且一致性核验通过之前，维持前置审查 §0 的 BLOCKED，本预检不改变该状态。

---

## 1. 执行记录（命令 → 目的 → 只读性质）

先 `adb devices`：仅一台设备 `c49***6cf`（状态 `device`）。以下全部命令显式 `adb -s c49***6cf`。

| # | 命令（脱敏） | 目的 | 性质 |
|---|---|---|---|
| 1 | `getprop ro.product.* / ro.build.version.*` | 型号 / 系统版本 / API | 只读属性 |
| 2 | `wm size` / `wm density` / `settings get system accelerometer_rotation` / `settings get system user_rotation` / `dumpsys window displays` | 屏幕、当前方向与旋转设置 | 只读查询 |
| 3 | `pm list packages`（过滤）、`pm list instrumentation`（过滤）、`pm path` ×3 | 三个目标包安装状态 | 只读查询 |
| 4 | `dumpsys package com.creationreadingassistant` | 正式包版本 / flags / 时间戳 | 只读查询 |
| 5 | `sha256sum <base.apk>`（设备端执行） | 正式包 base.apk SHA-256（§5-D-10 基线首项） | 只读计算 |
| 6 | `stat -c %s <base.apk>` | base.apk 字节数 | 只读查询 |
| 7 | `run-as com.creationreadingassistant id` | run-as 可用性 | 只读执行 |
| 8 | `run-as … sh -c 'find <dir> -type f -exec stat -c %s {} +'` ×3（databases / files / shared_prefs） | 三目录逐文件字节求和；输出仅数字，聚合后即弃 | 只读统计，未列任何文件名 |
| 9 | `run-as … sh -c 'du -sk databases files shared_prefs'` | 同上交叉验证（块分配口径） | 只读统计 |
| 10 | `which sqlite3`（系统与 run-as 环境） | 对账/快照通道能力探测 | 只读探测 |
| 11 | 主机 `df -h` | 各磁盘可用空间 | 主机只读 |

无任何一步需要写入、安装或状态变更才能继续；无中途 BLOCKED。

---

## 2. 设备与系统【已证实·实测】

| 项 | 值 |
|---|---|
| 设备 | Redmi K50 Ultra（brand Redmi，device diting，model 22081212C）——真实手机，非 MuMu |
| 系统 | Android 15，SDK 35，OS3.0.3.0.VLFCNXM |
| 屏幕 | 物理 1220×2712，density 480 |
| 当前方向 | **竖屏**（rotation 0） |
| 自动旋转 | 开启（`accelerometer_rotation=1`，`user_rotation=0`）——仅登记，未改动；执行窗口若需保持竖屏，遵守 AGENTS.md"记录原值并恢复"的约束 |

## 3. 包安装状态【已证实·实测】

| 包 | 状态 | 明细 |
|---|---|---|
| `com.creationreadingassistant`（正式主包） | **已安装** | versionCode 3，versionName 0.5.1；minSdk 24 / targetSdk 35；pkgFlags 含 `DEBUGGABLE`；firstInstall 2026-09-19 18:38:36，lastUpdateTime 2026-09-25 02:12:17 |
| `com.creationreadingassistant.qahost`（沙箱主包） | **未安装** | `pm path` 无返回 |
| `com.creationreadingassistant.qahost.test`（沙箱测试包） | **未安装** | `pm path` 无返回 |

**正式包 base.apk 对账基线（§5-D-10 首项，供执行窗口复测比对）：**

- 包路径：`/data/app/~~45-MCWVuuLZU1Eobo-BmNA==/com.creationreadingassistant-7ZDlMHrB76lHnpdeytbAFw==/base.apk`（单一 base.apk，无 split）
- SHA-256（设备端 `sha256sum` 实测）：`53d0c01b0d36dc19690dc1f62834f19fc61634a2dd394de2b23efc9da85359ac`
- 字节数：53,532,616（≈ 51.05 MiB）

**顺带观察（仅登记，未处理、不构成阻塞也不构成放行）：**

- 设备上仍安装着旧同包 debug 测试包 `com.creationreadingassistant.test`（instrumentation target=正式包，runner 为标准 `AndroidJUnitRunner`）——它是 qahost 方案之前的遗留物，其保留/清理属用户决策。
- 设备上仍安装着旧 Capacitor 产品线包 `local.creationReadingAssistant.mobile`——与本轮无关，仅登记。
- `dumpsys window displays` 输出中可见正式包 `MainActivity` 的近期活动记录，说明正式包近期有使用痕迹；执行窗口安装前仍需按 §5-B-6 自行确认正式包未在前台（本窗口未做前台态判定，未做任何干预）。

## 4. 只读通道能力【已证实·实测】

| 通道 | 结果 | 影响 |
|---|---|---|
| `run-as com.creationreadingassistant` | **可用**（uid=10949 u0_a949，DEBUGGABLE） | §5-D 逐表对账、§6 数据副本通道的基础前提成立 |
| 设备端 `sqlite3` | **不存在**（系统 PATH 与 run-as 环境均 "inaccessible or not found"） | **唯一实质阻塞项**：§6 首选"在线一致快照（`.backup` / `VACUUM INTO`）"通道不可用。替代方案均为用户决策：推送静态 sqlite3 二进制是设备写操作；复制 db+wal+shm 三件套按 §6 只能称"临时副本"且必须对副本跑 `PRAGMA integrity_check`。本窗口两者皆未执行 |
| 设备端 `stat` / `find` / `du` / `sha256sum` | 均可用 | 聚合统计与哈希对账可执行 |
| `dumpsys` / `getprop` / `pm` / `settings get` | 均可用 | 状态查询可执行 |

**只读能力阻塞项汇总：仅 1 项 —— 设备无 `sqlite3`。** 该项不阻塞本预检（预检已完成），阻塞的是**后续建立恢复点时的首选捕获方法**，需用户在建立恢复点前决策替代通道；决策与执行均不在本窗口授权范围内。

## 5. 正式包数据聚合【已证实·实测（仅聚合）】

统计方式：`run-as` 下 `find <dir> -type f -exec stat -c %s {} +` 逐文件字节求和（ apparent bytes ）；`du -sk` 为块分配口径交叉验证。**未列出任何文件名、未读取任何行或值、未复制任何数据。**

| 目录 | 实测字节（apparent） | du 口径（KiB） | 折算 |
|---|---:|---:|---|
| `databases/`（含正式 Room 库、workdb 及其 wal/shm） | 1,126,281,728 | 1,100,972 | ≈ 1.049 GiB |
| `files/`（含书库文件等） | 1,167,181,726 | 1,141,135 | ≈ 1.087 GiB |
| `shared_prefs/` | 5,131 | 32 | ≈ 5 KiB |
| **合计** | **2,293,468,585** | — | **≈ 2.136 GiB（约 2.29 GB 十进制）** |

隐私人声明：本报告只出现上述聚合数字；书籍一律中性称呼（"书库文件"），不出现真实书名、文件名、数据库行、偏好值或逐文件清单。逐文件清单属后续阶段仓库外本地私有证据。

## 6. 主机本地磁盘可用空间【已证实·实测】

| 盘 | 总量 | 已用 | 可用 | 使用率 |
|---|---:|---:|---:|---:|
| C: | 201 GB | 155 GB | **46 GB** | 78% |
| D: | 276 GB | 240 GB | **36 GB** | 87% |
| E: | 932 GB | 588 GB | **345 GB** | 64% |

## 7. 新恢复点：空间需求估算与位置候选

**前提声明：以下全部为估算。估算 ≠ 已建立备份；新恢复点目前不存在。** 旧恢复点已由用户删除以释放空间，本报告不引用、不比照其体量、更不重新生成；估算全部来自 §5/§6 实测值。

### 7.1 空间需求（按 §5-D 清单逐项估算）

| 组成 | 估算依据 | 体量 |
|---|---|---:|
| 设备数据副本（databases + files + shared_prefs） | §5 实测聚合 2.294 GB；打包含/副本开销留 ±10% 余量 | ≈ 2.3–2.6 GB |
| 正式包 base.apk 留档（版本锚） | §3 实测 0.053 GB | ≈ 0.06 GB |
| 内容级对账清单（Room 逐行哈希、workdb 逐任务字段、书库逐文件 SHA-256、prefs 哈希） | 文本清单；量级取决于 Room 行数（含 search_terms 索引表），无行数实测（行统计超出本窗口授权聚合口径，未做） | 数十 MB 量级，最坏数百 MB |
| manifest / 书面恢复步骤 / 捕获记录 | 文本 | 可忽略 |
| **完整可恢复备份档合计** | — | **≈ 2.6–3.0 GB；建议目标盘预留 ≥ 4 GB** |
| 仅对账基线档合计（无原始数据副本） | 仅清单与聚合记录 | **数十 MB，最坏数百 MB** |

### 7.2 位置候选（均为仓库外用户本地目录；不进 git/docs/任何同步上传渠道）

| 候选 | 可用空间 | 评估 |
|---|---:|---|
| **E:** | 345 GB | **首选候选**：对完整档（≤ 4 GB）余量最充裕，且不挤占用户刚清理过的盘 |
| D: | 36 GB | 可行但紧张：完整档约占其 8%；用户刚为释放该盘空间删除旧快照，**不建议默认选 D** |
| C: | 46 GB | 系统盘且 78% 已用，不推荐 |

命名建议沿用前置审查口径如 `<日期>-s04-qahost-round1`；保留期限与到期销毁方式必须由用户确认并记入快照 manifest（§6）。

### 7.3 "仅对账基线" 与 "完整可恢复备份" 的区别（用户分档决策依据）

| 维度 | 仅对账基线 | 完整可恢复备份 |
|---|---|---|
| 内容 | 只有指纹与清单：Room 逐表逐行规范化哈希、workdb 逐任务关键字段、书库逐文件相对路径+字节数+SHA-256、prefs 文件清单+哈希、正式包 APK 路径/版本/SHA-256、聚合大小 | 上述全部 + **原始数据副本**（databases 三件套、files 树、shared_prefs）+ base.apk 留档 + manifest + 书面恢复步骤 |
| 用途 | 证明验收前后**零变化**（内容级对账） | 零变化证明 **+ 数据可还原** |
| 事故时的能力 | **不能还原任何数据**——数据丢失/损坏时无物可恢复 | 可按书面步骤还原（**恢复能力在实机演练完成或用户明确接受"未经演练"之前一律标 UNVERIFIED**，§6/P1 复核第 5 条） |
| 体量 | 数十 MB，最坏数百 MB | ≈ 2.6–3.0 GB（预留 ≥ 4 GB） |
| **命名纪律** | **不得称为"恢复点"**——它只是对账证据 | 达成 §6 全部要求（空间/位置/保留期确认、副本一致性核验、manifest、恢复步骤）前，也只能称"临时副本"，不得称"恢复点" |

两档共同约束：存放仓库外用户本地私有目录；不进入 git 仓库、docs/ 或任何同步/上传渠道；敏感行内容与逐文件清单只留本地私有证据；明确不备份 Keystore 条目；凭据 prefs（`sync_config`/`webdav_config`/`ai_secrets`）可备但不入仓库。

## 8. 对前置审查 BLOCKED 清单的映射（只读预检视角）

| 项 | 本预检结果 |
|---|---|
| B2/B9（恢复点未建立/未确认） | **仍成立**——本预检只提供空间与通道实测，恢复点仍不存在；维持 BLOCKED |
| B8（设备已存在 qahost 包且无处置决定） | **当前不触发**——两包均未安装；若执行窗口复测发现已存在，仍按 §5-D-11 先取证再等用户决定 |
| §5-D-10 正式包基线 | 已捕获首项：包路径 / versionCode 3 / 0.5.1 / base.apk SHA-256 `53d0c01b…85359ac`（执行窗口测试后逐项复测） |
| §5-D-16 对账通道 | `run-as` 可用 ✅；`sqlite3` 不可用 ❌ → 恢复点首选捕获通道与"run-as + sqlite3"逐表导出路径需用户决策替代方案 |
| §5-B-5（真机/serial/竖屏） | 已证实：唯一真实手机、竖屏、非模拟器 |

## 9. 窗口限制与隐私声明

- 设备侧仅 §1 所列只读查询；零安装、零卸载、零 instrumentation、零 Gradle connected、零备份创建、零数据复制、零界面打开、零 force-stop、零清理、零设置改动、零 WorkManager 操作、零模拟器使用。
- 主机侧唯一写入 = 本报告；未操作原始仓库；未 commit / push / stage / reset / checkout / clean。
- 设备 serial 已脱敏（`c49***6cf`）；报告只含包名、聚合数字与哈希，不含真实书名、文件名、数据库行或偏好值；未读取任何书籍内容。
