# S14 本地 ZIP 恢复中途失败的数据一致性 · 只读调查（2026-09-28）

> Codex 2026-09-28 只读复核：独立回读 `SyncMergePolicy.shouldAcceptRemote`、`JsonBridge.importFromString` 与 `LocalZipBackup.importZip`，确认「较新本地书籍行被保留、同路径旧 ZIP 文件仍覆盖」的成功路径版本偏斜，以及 DB 提交后文件直接覆盖失败可留下中间态。下文 T1 夹具描述需修订：同一路径不能既是旧文件又是目录障碍；T2 的 chmod 只读是否会在 MuMu 实际拒绝写入也未经验证。T1–T4 尚未运行，任何修复尚未实施；先用自洽的隔离失败测试保护唯一旧文件，再修文件覆盖，版本偏斜需另设 DB 合并结果到文件复制的接缝。

- 仓库：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`；分支 `codex/recover-worktree-20260920`。未进入/未修改原仓库。
- 性质：**只读调查**。未运行 Gradle/ADB/MuMu，未接触真实手机、正式书库或 qahost 既有数据；未实现修复、未新增测试；唯一写入 = 本报告。
- 输入：`AGENTS.md`、`docs/handoff/current.md`、[持久库备份测试准备报告](../../2026-09-28-s14-persistent-backup-test-preparation.md)（本窗口上一棒）；生产源码逐行核对（file:line 均为当前工作树）。
- 文件时效自证：`LocalZipBackup.kt`（mtime 2026-09-19，tracked-clean）、`SyncMergePolicy.kt`（09-19）、`ProfileViewModel.kt`（09-18）、`ProfileRoute.kt` 读取于本轮；`JsonBridge.kt` mtime 2026-09-23 02:46，早于本窗口今天的完整读取——结论以这些版本为准，若他窗口此后改动需重新核对。

## 1. 真实执行顺序（源码已确认事实）

`UI` → `ProfileViewModel.zipImport`（ProfileViewModel.kt:415-421）→ `LocalZipBackup.importZip`（LocalZipBackup.kt:68-128）：

| 步 | 位置 | 内容 | 失败后果 |
|---|---|---|---|
| 1 | :69 | 建 `cacheDir/zip-restore-<millis>` 临时目录 | — |
| 2 | :77-98 | 流式解压全部 entry 到临时目录（含 ZipSlip 过滤） | 抛异常 → DB 未动、旧文件未动 → finally 清临时目录 |
| 3 | :100-102 | 校验 `backup.json` 存在 | 抛 `IllegalStateException` → 同上，DB 未动 |
| 4 | :105 | `JsonBridge.importFromString` —— **单个 DB 事务**（JsonBridge.kt:361 `bookDao.runInTransaction`）：books/灵感/进度/会话/笔记/高亮/标签/分类/书单按 revision 合并 → 关联表 clearAll+重建（:410-421）→ 来源引用/规则/纠错"旧不覆新"（:424-456，纠错是**最后一步**） | 事务整体回滚 → DB 未动 → 清临时目录 |
| 5 | :110-123 | **DB 已提交后**，把临时 `books/**` 逐文件复制到真实 `filesDir/books/`：`realBooksDir.mkdirs()`（:112，返回值忽略）→ `walkTopDown().forEach`：目录 `dst.mkdirs()`、文件 `dst.parentFile?.mkdirs()`（返回值忽略）+ `dst.outputStream()`（**FileOutputStream 直接截断覆盖既有文件**）+ `copyTo` | 任何异常沿 `forEach` 上抛 → **循环中止，已复制的保留、未复制的放弃、半写入的留在原地** → finally 清临时目录。**该段无任何回滚、无 staging、无备份副本** |
| 6 | :124-127 | finally `tmp.deleteRecursively()` | — |

类注释（:30-32）声称"中途失败整体回滚（临时目录清理）"——**只对 DB 段成立**；文件覆盖段（步骤 5）既不在事务内也没有旧文件保护。注释中"失败时不影响已入库数据——DB 仍引用原路径，缺失会弹提示，属可恢复错误"（:107-109）陈述的是**有意设计**（DB 原子 + 文件尽力），但漏掉了"覆盖损坏"这一档（见 §2.3）。

## 2. 失败窗口状态矩阵

前提约定：备份 ZIP 含书 B0/B1/B2 的源文件；B1 在本地已有旧版本文件。临时目录在所有窗口都会被 finally 清理（若 ENOSPC，删除本身释放空间，预期成功——此点属推测，见 §2.4）。

### 2.1 DB 提交前失败（解压失败 / 空间不足 / ZIP 损坏 / 缺 backup.json / DB 事务失败）——【源码确认：安全】

DB 未动（或已回滚）、旧文件未动、无新文件、临时目录清理。用户看到错误后可先释放空间再重试。**这一段与注释承诺一致。**

### 2.2 DB 提交后、文件复制失败的各窗口——【失败点分类是源码确认；实际可达性与半写入损坏需故障注入证明】

| 失败点 | DB | 旧文件（不在备份中的书） | 备份中的书的文件 | 结果分类 |
|---|---|---|---|---|
| `mkdirs` 失败（:112/:117 返回值被忽略，父目录缺失） | 已提交为备份态 | 未动 | 抛 `FileNotFoundException`，B0 已复制=备份版、B1/B2 未复制 | DB 与文件不一致（可重导入收敛） |
| **覆盖既有文件时打开/写入失败**（空间不足、权限） | 已提交为备份态 | 未动 | **dst 已被截断或半写入=损坏**（`FileOutputStream` 先截断）；B0 备份版、B1 损坏、B2 未复制 | DB 与文件不一致 + **旧文件损坏**（不可自动恢复，除非重导入） |
| 已有文件被完整覆盖（成功部分） | 已提交 | 未动 | 无条件变为备份版本 | 见 §2.3 版本偏斜 |

关键点：**"旧数据保持"不是任何文件段失败后的结果**——DB 在复制开始前就已整体切换（含关联表清空重建：不在备份中的旧书将失去标签/书单/分类/已读关联，这是 JsonBridge.kt:409 注释声明的设计"备份为准"）。文件段失败后系统处于"DB=备份世界（∪ 保留的旧书行）、文件=新旧混合"的中间态。

### 2.3 成功路径本身的版本偏斜——【源码确认的事实缺口，非故障才出现】

文件复制对 DB 合并结果**无感知**：即使 B1 的本地行因 revision 更新被 JsonBridge 跳过（SyncMergePolicy.kt:21-25，"旧不覆新"），其**文件仍会被备份里的旧版本无条件覆盖**（LocalZipBackup.kt:113-122 不查询任何合并结果）。结果：行=新内容（content_hash/进度引用新版本），文件=旧内容。恢复**成功**也会造成 DB 与文件不一致。而恢复确认弹窗（ProfileRoute.kt:539）对用户承诺"覆盖当前书籍源文件与数据库（**按版本规则合并**）"——文件段不满足这句承诺。

### 2.4 分类小结

- **源码已确认**：§1 顺序；§2.1 安全窗口；§2.2 的失败传播路径（mkdirs 返回值忽略、`FileOutputStream` 截断语义、`walkTopDown` 首异常中止）；§2.3 偏斜；DB 幂等（§3）。
- **需故障注入才能证明**：半写入损坏在真实 FS/ENOSPC 下的实际表现（截断后残留字节数、后续解析行为）；`tmp.deleteRecursively()` 在 ENOSPC 下的成功率；`walkTopDown` 的实际中止集合；重导入端到端收敛（静态推导见 §3，未实测）。
- **产品可接受性判断**："DB 原子 + 文件尽力 + 缺失可修复"作为设计意图本身可辩护（与"重新定位文件"修复路径衔接）；**不可辩护**的是①覆盖损坏无任何保护（唯一副本可被半写入毁掉，比修复路径 `reselectPlainText` 的 staging/backup 守卫倒退）；②失败后用户只得到一条原始 OS 消息，没有任何"当前处于中间态、重新导入可恢复"的指引；③成功路径违背确认弹窗的版本合并承诺。

## 3. 再次导入同一 ZIP 能否安全恢复

**结论：ZIP 仍在手时，重导入收敛到完整恢复态（源码确认的静态推导；端到端待注入验证）。**

- DB 幂等：同一备份的 envelope revision 与本地已导入行相等且 updated_at 相等 → `shouldAcceptRemote` 返回 false（SyncMergePolicy.kt:25）→ 行跳过不变；不在备份中的书行不受影响；关联表重建为同一集合；来源引用/规则/纠错 `>=` 判定重写同值（JsonBridge.kt:429/440/451）。
- 文件自愈：备份内每个文件都会被完整重写——半写入的 dst 被 `FileOutputStream` 重新截断后完整覆盖，缺失的被复制（LocalZipBackup.kt:113-122）。
- 附条件：① 失败与重试之间用户产生的新数据行受 revision 合并保护，但**其旧文件若与备份内路径同名仍会被备份版本覆盖**（同 §2.3 偏斜）；② ZIP 丢失后，半写入损坏的文件无法自愈——DB 行 `content_status` 仍为 available，打开时才失败，用户的既有出路是长按书籍"重新定位文件"（`ShelfImporter.repairFile` 路径），前提是用户仍持有原始来源文件。

## 4. 用户可见反馈（源码确认）

- 导入前：确认弹窗"导入将解包并覆盖当前书籍源文件与数据库（按版本规则合并）…建议先导出备份"（ProfileRoute.kt:539）。
- 失败时：仅一条 snackbar「整包导入失败：`<原始异常 message>`」（ProfileViewModel.kt:419 → ProfileRoute.kt:190 `showMsg`）——ENOSPC/权限等场景是 OS 原文，无中文解释、无"数据处于什么状态"、无"重新导入即可恢复"或"先释放空间"的可执行指引。
- 成功时：「整包导入成功」——即便存在 §2.3 偏斜也不提示。

## 5. 最小隔离故障注入测试设计（本次不实现）

沿用上一棒准备报告的基建（唯一命名持久库、`IsolatedSyncTestContext` 文件重定向、合成"测试 EPUB/测试 TXT"、默认 `CraIsolatedTestRunner`），全部注入手段为**文件系统预置障碍**，不需要 hook 生产代码：

| # | 注入 | 步骤 | 判别哪种结果 |
|---|---|---|---|
| T1 目录占位 | 预置 `files/books/b1/content.txt` 位置为**同名目录** → 复制到该文件时打开必然抛异常 | 备份含 B0/B1/B2；本地预置 B1 旧文件 + 障碍；恢复 → 捕获失败 | **DB 与文件不一致**：DB 三书行已提交、B0 文件=备份版、B1 旧文件字节未动（SHA-256 前后一致）、B2 文件未复制；同时证明 walkTopDown 中止集合 |
| T2 打开失败（只读目标） | 预置 B1 文件为 mode 444（owner 无写位）→ `FileOutputStream` 打开抛 EACCES | 同上结构；对账 B1 旧文件 SHA-256 前后一致 | **打开失败不损坏旧文件**（把"截断发生在 open 时还是 write 时"从推测中剥离）；DB 与文件不一致。注：**写中途损坏（真 ENOSPC）不在最小集**——需专用隔离分区逼真注入，登记为设备窗口可选增强 |
| T3 重导入收敛 | T1 失败后移除目录障碍，再次 `importZip` 同一 ZIP | 全量对账五类记录 + 文件集合与哈希 | **新数据完整恢复**（验证 §3 的收敛推导） |
| T4 版本偏斜（可 JVM） | 本地 B0 行 revision 7 + 新文件内容；备份含 B0 revision 6 行 + 旧文件 → 恢复**成功** | 断言 DB 行=rev7、文件=旧内容 | **DB 与文件不一致**（成功路径即发生；可先在 JVM 隔离目录证实，不需要设备） |

每条用例 @Before/@After 清理唯一库与测试根；不触碰 qahost 既有数据。T1–T3 为 androidTest，T4 可为 JVM（`LocalZipBackup`/`JsonBridge` 纯逻辑可用隔离目录驱动——现 JVM 测试 LocalZipBackupTest.kt:118-151 已有 exportZip/importZip 的 mock 框架可扩展）。

## 6. 修复方案对比（至多两个，本次均不实施）

| | 方案 A：逐文件 staging + rename | 方案 B：版本感知文件复制 |
|---|---|---|
| 做法 | 每个目标文件先写 `dst.<staging>`，**复制完整成功后** rename 覆盖 dst（Linux/Android rename 同目录原子替换既有文件）；失败时清理本批 staging，旧文件原样 | `JsonBridge.importFromString` 报告"哪些书籍行本次实际被接受"，`LocalZipBackup` 只对被接受的书复制文件（被跳过的书保留本地文件，与 DB 行对齐） |
| 文件所有权 | `LocalZipBackup.kt` + `LocalZipBackupTest.kt`（JVM）+（可选）本窗口 androidTest 扩展 | `JsonBridge.kt`（Codex 共享 owner，S04-5 曾多轮改动）+ `LocalZipBackup.kt` + `JsonBridgeTest.kt` + `LocalZipBackupTest.kt` |
| 回滚复杂度 | 低：staging 删除即净；rename 原子 | 中：需先落地 A（B 不解决半写入损坏），再加接口回传 |
| 备份格式影响 | **零** | **零** |
| 解决 | 覆盖损坏（§2.2 最严重档）、旧文件丢失 | §2.3 成功路径偏斜 + 弹窗承诺兑现；**不解决**写中途损坏 |
| 残留 | 失败后仍是"DB=备份态+文件混合"（与设计意图一致，靠重导入收敛） | 同 A 的残留 |

**推荐：先验后修。** 先执行 §5 的 T1–T4 取得隔离证据（当前全部失败分类是静态推导，且类注释与实现已被证实存在差距，正式动手前应有可复现的失败现场）；随后实施 **A**（改动面最小、无论是否做 B 都必须做、且不动备份格式与 JsonBridge 所有权）。**B 单独立项交产品裁决**：版本偏斜是否要修复本质是"文件是否必须跟随行版本"的产品语义，同时应一并修正确认弹窗文案（ProfileRoute.kt:539）与失败 snackbar 的可执行指引（两者归属 UI 文案，改动面小，可与 A 同轮）。

## 7. SEAM REQUEST

本轮只读，无现行 SEAM REQUEST。**条件性预告**：若方案 B 立项，需 `JsonBridge.kt` + `JsonBridgeTest.kt` 的 hunk 所有权（现由 Codex 共享 owner 保管），请届时由总指挥分配；方案 A 与 §5 测试所需文件（`LocalZipBackup.kt`、`LocalZipBackupTest.kt`、本窗口 androidTest 文件）不与他人冲突。

## 8. 边界声明

本轮仅新增本报告一个文件；未修改产品代码、测试、Gradle、schema、账本或他窗口报告；未运行 Gradle/ADB/MuMu；未接触真实手机、正式书库、qahost 既有数据；未 commit/push/reset/checkout。所有 file:line 为 2026-09-28 工作树事实；"推测"均已在 §2.4 显式标注，未把 `LocalZipBackup.kt:30-32` 的"原子恢复"注释当作已实现保证（该注释与 ：110-123 的实际行为不符，已列为注释-实现差距）。报告不含真实书名。落盘即停，交总指挥复核。
