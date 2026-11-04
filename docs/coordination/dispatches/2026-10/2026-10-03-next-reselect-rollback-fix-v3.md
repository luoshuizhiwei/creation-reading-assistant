# CRA-RESELECT-ROLLBACK-FIX-v3 — 重选文件回滚保护的目录残留边界与精确交付

## 一、任务编号/版本

- **任务编号/版本**：`CRA-RESELECT-ROLLBACK-FIX-v3`，执行标识 `CRA-RESELECT-ROLLBACK-FIX-v3-20261003-1335`
- **执行者**：ZCode / GLM（account:zai-start-plan），开始 2026-10-03T13:35:01+08:00
- **性质**：单一路径边界收紧（两格式入口守卫 `backup.isFile` → `backup.exists()`）+ 定向用例四条。**不合流、不 ADB、不安装、不触碰正式书库、不暂存/commit/push/reset/checkout。**
- **上游依据**：`docs/coordination/reviews/2026-10/2026-10-03-next-reader-rollback-delivery-codex-review.md`（v2 已保护普通文件 backup，但入口 `backup.isFile` 漏判目录残留）
- **状态**：**已交付待 Codex 核查**

## 二、输入指纹

| 项 | 值 |
|---|---|
| 恢复仓库 HEAD | `e39a78da9c62f02614207cffb36969a32492eaea`（开工=收工，未变；本轮零 Git 写入） |
| 实施基线 | `E:\creation-reading-assistant\next-round-reselect-rollback-fix-v2\android`（v2 原始红 43/6、绿 43/0 的来源候选，见 §5） |
| 新候选 | `E:\creation-reading-assistant\next-round-reselect-rollback-fix-v3\android`（883 文件 = 父全量） |
| 输入四文件（v2 基线实测） | `ShelfImporter.kt` `e8155f3c…`、`EpubRepository.kt` `b8c921f6…`、`ShelfImporterTest.kt` `3dfcd757…`、`EpubRepositoryReplaceRollbackTest.kt` `5f0016b0…`（与任务书一致） |
| 占用核查 | v2 目录只读核对后复制；本轮零设备/零 ADB；无其他窗口写入本任务输入 |

## 三、所有权与改动

**新候选内改动恰为授权四路径**（树差 `freeze/evidence/tree-diff-v2v3.txt`：883/883 文件，changed=4、only=0；原判定脚本 False 系列表顺序笔误，集合内容 4/4 一致，已注记更正）：

| 文件 | v2 → v3 SHA-256 | 改动 |
|---|---|---|
| `…/feature/library/ShelfImporter.kt` | `e8155f3c…` → `88a1f5d5…` | 入口守卫 `backup.isFile` → `backup.exists()`（+注释修正，+1/−1 附近） |
| `…/feature/reader/EpubRepository.kt` | `b8c921f6…` → `07ed05e5…` | 同上 |
| `…/ui/viewmodel/ShelfImporterTest.kt` | `3dfcd757…` → `b38bab4f…` | +2 用例（backup 目录残留，target 存在/缺失两变体） |
| `…/feature/reader/EpubRepositoryReplaceRollbackTest.kt` | `5f0016b0…` → `7be19d8a…` | +2 用例（同上两变体） |

精确补丁：`freeze/evidence/v3-diff-ShelfImporter.patch`（17 行）、`v3-diff-EpubRepository.patch`（17 行）。**payload 不含**整个 android 目录、其他历史测试驱动、临时包后缀或配置——树差证明除四路径外零变化。

**生产语义（两格式一致）**：存在**任何同名 backup 残留**（文件或目录）时，在 `staging.delete()`、改写 target、写 DB 之前即抛 `IllegalStateException`，错误信息含「备份」并可观察；**不猜测残留归属、不自动恢复、不自动删除旧 backup**；target/backup 目录内字节保持原样。catch 分阶段回滚（v1/v2 已成立的 SWAP_UNTOUCHED/BACKED_UP/WRITTEN、suppressed 关系、restoreBackupOver）全部原样保留——`backup.exists()` 恒真于 catch 检查（残留仍在），「恢复失败保留 backup」与「主异常+suppressed」关系不变。注释已与实现同步（入口注释说明目录形态来源：中断的覆盖复制兜底不同阶段形态不同）。

## 四、环境

Windows / Git Bash；JDK 17.0.14；Gradle wrapper 8.9 `--offline`；缓存复用 `next-round-joint-j1-v2\_cache\gradle-home`（E 盘，本轮独占使用）。零 ADB/MuMu/安装/正式书库。

## 五、执行与结果

### 5.1 红（新用例 ×4 在 v2 实现上运行）

命令：`:app:testDebugUnitTest --offline --console=plain --tests EpubRepositoryReplaceRollbackTest --tests ShelfImporterTest`

**结果：47 tests / 4 failures**（EPUB 10/2、TXT 37/2）——四条新用例**全部**在 v2 实现上失败，失败原因逐条精确：

| 用例 | v2 实现的实际表现 |
|---|---|
| EPUB target 存在 + backup 非空目录 | 裸 `FileAlreadyExistsException`（copyTo 兜底直写目录路径），**无**「备份」可观察拒绝 |
| EPUB 仅 backup 目录（target 缺失） | **替换照常完成**（目录残留被无视，新副本安装、无失败） |
| TXT target 存在 + backup 非空目录 | 报「修复失败」但原因是裸文件系统异常，**无**「备份」说明 |
| TXT 仅 backup 目录 | `runInTransaction` 被**真实调用**（DB 写入）+ 替换完成——残留目录被无视 |

（XML：`freeze/evidence/red-epub.xml` 10/2、`red-txt.xml` 37/2；日志 `red-run.log`。）

### 5.2 绿（生产守卫收紧为 `backup.exists()` 后，全类重跑）

**结果：47 tests / 0 failures / 0 errors / 0 skipped**（EPUB 10/0、TXT 37/0；`freeze/evidence/green-{epub,txt}.xml`、`green-run.log`）。v2 已成立的暂存失败保护、普通文件 backup 保全、恢复失败保留 backup、主异常+suppressed 关系全部保留（既有用例零失败）。

### 5.3 与 v2 原始红/绿证据的关系

- v2 原始件在 `next-round-reselect-rollback-fix-v2\results\`：red `TEST-red-EpubRepositoryReplaceRollbackTest.xml` 8/**3** + `TEST-red-ShelfImporterTest.xml` 35/**3** = **43/6**（v2 新增 backup-**文件**用例在 v1 生产上失败）；green 8+35 = **43/0**（backup.isFile 守卫修后通过）。
- 本轮 v3 的 47/4 红 = **v2 绿 43 + 本轮新 4**（同批四文件树上叠加）；47/0 绿 = v2 守卫（文件）+ v3 守卫（文件或目录）共同成立。两轮红绿测的是不同缺陷层，来源清楚。

## 六、原始证据

`E:\creation-reading-assistant\next-round-reselect-rollback-fix-v3\freeze\evidence\`：red-run.log、red-epub.xml、red-txt.xml、green-run.log、green-epub.xml、green-txt.xml、v3-diff-*.patch ×2、tree-diff-v2v3.txt；`freeze/source-sha256-before.txt`、`source-sha256-after.txt`。命令与退出码：RED_EXIT=1（红）、GREEN_EXIT=0（绿），均在 §5。

## 七、未覆盖项

- 真实 Room/设备验收不在本轮（四路径仅 JVM 定向；MockK DAO 桩 + TemporaryFolder 真实文件系统）。
- backup 目录残留的**归属识别**与自动清理仍按 v2 合同不做（拒绝+保留+交用户）。
- journal、内容身份、UI、提交后事务机制未扩展（按任务书禁止）。
- 全量 JVM/设备测试未跑（仅两定向类，47 用例）。

## 八、恢复与副作用

原 v2 候选与全部既有候选/报告只读未动；HEAD 未变；共享仓库仅新增本报告。E 盘新增 v3 候选（883 文件）与 freeze 证据。无设备/正式书库/外部服务副作用。

## 九、交付状态与下一步

**已交付待 Codex 核查。** 四路径窄修完成：红（v2 实现 47/4，四条新用例全部按预期失败）→ 修复（`backup.isFile`→`backup.exists()`，两格式+注释同步）→ 绿（47/0，v2 全部既有保护保留）。下一步：Codex 核查本报告与 freeze 证据；通过后由集成者决定合流（本候选基于 v2，含 v1/v2 已成立保护；合流需新精确清单）。
