# Library-evidence-v1 — 书库现有实现与历史验收证据归属报告

- 任务编号/版本：**Library-evidence-v1**（只读核查；依据 Codex-review-N1v2-M0v3-v1 的并行安排）。
- 开始时间：2026-10-02T09:07+08:00；应用/模型：ZCode / GLM（account:zai-start-plan）。
- 输入指纹：恢复仓库 HEAD `e39a78da9c62f02614207cffb36969a32492eaea`（本轮全程未变，暂存区动作为零）；联合候选 `next-round-joint-j1-v3\android`（六文件冻结 SHA 6/6，见 `freeze/six-file-sha256.txt`）；tracker：`E:\creation-reading-assistant\n1v2-m0v3-codex-review-20261002\functional-tracker.csv`（23 功能行）。
- 本轮零构建、零测试重跑、零 ADB/MuMu、零 Git 操作；唯一写入 = 本报告 + `E:\creation-reading-assistant\next-round-library-evidence-v1\`（核对脚本输出）。

## 1. 通用输入一致性（适用全部四组）

- **候选 == 基线**：J1-v3 候选的书库/书架源码与基线 e39a78da 逐字节一致（抽样 13 文件 SAME，含全部关键入口）。
- **D 工作树已漂移（基线后 WIP，非候选缺陷）**：`ShelfImporter.kt`（D=5120e75c… vs 候选=基线 d6bd2396…）、`LibraryRootStore.kt`（D=1410ca8d…/基线 5f08281b…）、`ShelfModels.kt`（D=ed9bb3ae…/基线 d410d700…）、`ShelfScreen.kt`（D=861e4720…/基线 7ce8e0d2…）、`BookRepository.kt`（D=74e82ed9…/基线 698172c7…）。含义：① 历史证据（2026-09-13/09-18）的输入是**当时工作树**，与候选同源但非逐字节同输入，历史真机证据只能记"有限证据（旧输入）"；② 若日后合流 D 树 WIP，这些文件的既有证据需按差异重估；③ 候选自身的证据链不受 D 树漂移影响。
- **JVM 同输入证据**：J1 联合门禁 2026-10-01/02 全量 `:app:testDebugUnitTest` **2673/2673**（287 XML，唯一用例实算核验）在 J1 候选上运行；v2/v3 树相对该候选仅 androidTest 测试文件变化，JVM 集不变 → 候选内全部书库 JVM 测试（下表列出）均属"同输入已通过"，可复用。

## 2. G1-01 授权书籍目录导入与浏览（含 G1-03 子项）

| 维度 | 归属 |
|---|---|
| 需求来源 | 路线 2026-09-12 §4.1/§4.2/§4.3/§4.5、§5.1–5.5；八组需求 2026-09-09；交付 README R3/R4 行 |
| 实现入口（候选内，SHA=基线） | `LibraryRootStore.kt`（DataStore 根目录/恢复/更换）、`SafLibrarySource.kt`（单目录 1,000 上限、UNREADABLE）、`SafBookSourceScanner.kt`（2,000 项/深度 16）、`LibraryBrowserRoute/Components/ViewModel/Policy.kt`（`shelf/library` 路由：授权/恢复/换根/面包屑/排序/搜索/多选加书架）、`ShelfImporter.kt`（批量落地） |
| JVM 同输入 | `LibraryRootStoreTest`、`SafLibrarySourceTest`、`SafBookSourceScannerTest`、`LibraryBrowserPolicyTest`、`ShelfImporterTest` 等均在 2673 集通过 |
| 设备证据 | WorkBuddy 2026-09-13 Redmi K50 Ultra/HyperOS 真机 A–E PASS（导入/已入架/>32MiB/内容更新/删书不删来源/来源失效内部副本/>1000 项/>16 层/取消/清理恢复；`reports/workbuddy-r4-library-folder.md` + 路线 §1.1）；**输入为当时工作树，非候选逐字节** |
| D vs 候选 | `SafLibrarySource/SafBookSourceScanner/SmartBookRecognizer/LibrarySourceIndex/Policy` SAME；`ShelfImporter/LibraryRootStore` D 树已漂移 |
| 结论 | **已归属且 JVM 证据可复用**；真机 A–E 记"有限证据（2026-09-13 旧输入，Redmi 真机）"；SAF 授权边界（恢复/换根的权限失效路径）无当前输入证据 |
| 最小下一步 | 无需重开发；若做联合 MuMu 批次，可复用历史 A–E 场景对候选跑一轮（归入既有批次，不新立清单） |

### G1-03 来源索引与增量更新（G1-01 子面）

| 维度 | 归属 |
|---|---|
| 实现入口 | `LibrarySourceIndex.kt`、`ShelfSourceUpdateChecker.kt`、`SourceFingerprint.kt`（>32MiB `fp1|size|headMd5|tailMd5`）、`library_source_refs` 表 + Room 13→14（候选 `app/schemas/…/14.json` 在，schema 1–15 全链）、`ShelfImporter` best-effort 落引用（rootId 不伪造）、`LibraryBrowserPolicy` 四级判定、missing 标记 |
| JVM 同输入 | `LibrarySourceIndexTest`、`ShelfSourceUpdateCheckerTest`、`SourceFingerprintTest` 在 2673 集通过 |
| 设备证据 | ① zcode 报告 2026-09-13：JVM 253/2238、13→14 迁移用例**编译未设备执行**；② WorkBuddy 2026-09-13 真机 A–E 含"内容更新/来源失效/删书不删来源"；③ 2026-09-12 记录有"设备侧 Room v13→v14 PASS"（旧输入）。**候选内（v15 全链迁移路径）的同批迁移设备证据确实没有**——tracker 该未解项成立 |
| 结论 | **已归属 + 有限证据**；缺口仅"候选 v15 全链安装迁移中 13→14 段的同批设备证据"，非实现缺口 |
| 最小下一步 | 在任一既定 MuMu 批次附一项：v15 全链安装后核对 `library_source_refs` 表结构（一条 SQL/一个截图即可），不单独立项 |

## 3. G1-02 智能识别与批量导入

| 维度 | 归属 |
|---|---|
| 实现入口 | `SmartBookRecognizer.kt`/`SafSmartBookRecognizer.kt`（事件流/进度/置信分级/可解释理由/标题作者建议）、`SafBookSourceScanner.kt`（1,000/2,000/深度 16 上限）、识别结果页四档页签与批量勾选（`LibraryBrowserRoute`）、`BatchSheet.kt`/`ShelfImporter`（进度/去重/批次摘要） |
| JVM 同输入 | `SmartBookRecognizerTest`、`SafBookSourceScannerTest`（2,000 上限按所有目录项计数的回归在候选内）等通过 |
| 设备证据 | WorkBuddy 2026-09-13 真机 A–E：>1,000 项截断、>16 层深、主动取消、清理恢复均 PASS（旧输入、隔离目录） |
| 结论 | **已归属 + 有限证据**；"真实超大目录（非隔离构造）表现"仍未验（tracker 未解项成立），属环境规模问题而非功能缺口 |
| 最小下一步 | 不立新项；如未来有真实大库设备批次顺带记录截断行为即可 |

## 4. G1-04 书架状态分组与读完归类（含 R5-L1 检查点）

| 维度 | 归属 |
|---|---|
| 需求来源 | 书架成熟化 2026-09-18（P0 落地，P1–P3 待评审）；八组 L1 |
| 实现入口（候选=基线） | `ShelfModels.kt` `bookStatus/readingStatus`（**注意：已是 S05-① 显式完成态权威语义**——只依赖进度+状态列，与 2026-09-18 版本的 `isDownloaded` 短路实现已不同）、`ShelfScreen/BookGrid`（状态条/分组/新角标）、`ShelfPrefsStore`（新书集合）、`BookRepository.markFinished/setReadingState`、**R5-L1 已实现**：`data/settings/ShelfSavedViewsStore.kt` + `ShelfViewModel/FilterSheet/ShelfRoute` 保存筛选接线 |
| JVM 同输入 | `ShelfStatusFilterTest`、`ShelfStatusCountsTest`、`BookRepositoryTest`（markFinished 5 例）、`ShelfPrefsTest`、`ShelfLongPressPanelTest`、`ShelfFilterLogicTest` 等在 2673 集通过 |
| 设备证据 | ① 2026-09-18 Redmi c49ac6cf **release APK** 真机 10 项全 ✓（旧 bookStatus 语义、Room v15 工作树）；② **E 阶段 MuMu（2026-10-01，与基线同源输入）**：S05 七站（标记已读完→归档→取消读完→DB 对账，`db/*` 证据）+ 52 项仪器内"平板书架 9"——覆盖**新的显式完成态语义**与书架 UI；实体手机边界如实（MuMu≠MIUI） |
| 后续修改影响 | **2026-09-18 真机证据对候选 `bookStatus` 不再逐字适用**（S05-① 重写了判定语义，且这是有意的授权行为变化，不是回归）；新语义的证据由 E 阶段 S05 + 平板书架 9 + JVM 承接 |
| 结论 | **已归属；P0 证据分层有效**（旧真机=旧语义有限证据；E 阶段=新语义同源证据）；R5-L1 源码归属成立、设备验证未单列 |
| 最小下一步 | P1/P2/P3 维持"待评审"（tracker 口径）；L1 的设备确认可随任一 MuMu 书架批次（保存筛选→重进→视图恢复，两条断言） |

## 5. G1-05 数据生命周期（搁置/移除正文/删除全部资料与撤销；重关联校验）

> 矩阵此前漏列不代表实现缺失——候选内实现**成体系存在**，以下是归属证据。

| 维度 | 归属 |
|---|---|
| 需求来源 | 交付 README R1-D1/R2-D2 + 统一需求契约 2（搁置/移除正文/删除全部资料三档；撤销只恢复本次影响；D2 重新关联必须校验内容） |
| 实现入口（候选=基线） | `data/repository/BookRepository.kt`：`deleteBook/deleteBookScoped/deleteBooksScoped`（`BookDeletionSnapshot` 快照驱动）、`restoreBook`（同 deleted_at 戳恢复，不复活此前删除）、`restoreDeletions`（**三条规则**：活跃行让路/时间戳不符让路/关系目标缺失跳过——正是 README "删除/撤销范围不对称"问题的修复实现）、`deleteBookContentFiles`（D2 决策 A：移除正文物理删除，`ContentFileDeletionOutcome` FullyCleared/RemainsAfterAttempt/Aborted，残留可观察）；`feature/library/deletion/`：`DeletionScope`（SHELVE/REMOVE_CONTENT/…三档语义与文案 `DeletionCopy`）、`DeletionUndoStore/Policy/ViewModel/Bar`（撤销凭证；搁置与移除正文无撤销凭证由 retention 派生）、`BookDeletionCoordinator`（残留扫描 `scanDirForResidue`）；重关联侧：`ShelfImporter` 同 `contentHash` 去重比对（578-585 行）与 `LibraryBrowserPolicy` 四级判定构成"内容校验后关联"的现有形态 |
| JVM 同输入 | `BookDeletionRestoreTest`、`BookDeletionCoordinatorRestoreTest`、`DeletionUndoCredentialTest`、`ShelfBookDeletionDelegationTest`、`BookResidueSafeCleanupTest`、`ShelfDeleteCopyConsistencyTest` 在 2673 集通过 |
| 设备证据 | 无 G1-05 专门的设备旅程验收（矩阵遗漏所致）；相关局部：E 阶段 S05 的归档/书架恢复在读入口（restoreReading）与"移除正文"残留清理的 JVM 层。**确认为"设备验证缺口"，不是实现缺口** |
| 结论 | **源码归属成立 + JVM 同输入证据成立；设备旅程级验证 = 尚未验证**（未核当前版本证据，tracker 口径一致） |
| 最小下一步 | 一个 MuMu 场景即可闭合主干（见 §7 场景 A），不需要重开发 |

## 6. 总表

| 旅程 | 源码归属 | JVM 同输入 | 设备证据 | 结论 |
|---|---|---|---|---|
| G1-01 | ✓（==基线） | ✓ 2673 集内 | 2026-09-13 真机 A–E（旧输入，有限） | 已归属且证据可复用；SAF 权限失效路径未验 |
| G1-03（子面） | ✓ | ✓ | 旧真机部分；候选 v15 全链迁移中 13→14 段无同批设备证据 | 有限证据；补一条迁移核对即可 |
| G1-02 | ✓ | ✓ | 2026-09-13 真机 A–E 含截断/取消（隔离目录，有限） | 已归属；真实超大目录表现未验（环境规模） |
| G1-04 | ✓（S05-① 新语义） | ✓ | 旧真机 10 项（旧语义）+ E 阶段 MuMu S05/平板书架 9（新语义、同源） | 已归属；L1 设备确认未单列 |
| G1-05 | ✓（成体系） | ✓（6 个专项测试类） | 无专门设备旅程 | 实现不缺；设备旅程级未验证 |

## 7. 建议的新验收场景（≤3，留待统一 MuMu 任务，不在本轮运行）

1. **G1-05 生命周期旅程一次走通**（旧证据不足原因：矩阵漏列，从未有该旅程的设备验收；JVM 只证明仓储规则）：预置一本带进度/笔记/书单关联的中性测试书 → 搁置 → 移除正文（核对 DB 指针清除、正文文件删除、书籍与进度保留、不可打开）→ 删除全部资料 → 撤销（快照恢复、不复活此前删除项、不覆盖新改动）→ 重新导入同内容文件（校验按 contentHash 关联，位置不强套）。
2. **G1-03 迁移段补证**（旧证据不足原因：13→14 迁移在候选的 v15 全链安装路径上无同批执行）：候选全链安装/升级后核对 `library_source_refs` 存在且可写入（一条 SQL/截图级证据）。
3. **G1-04 L1 保存筛选确认**（旧证据不足原因：L1 实现晚于 2026-09-18 真机批次，无设备记录）：保存一个筛选视图 → 退出重进 → 视图按保存条件恢复（两条 UI 断言，可并入任意书架批次）。

## 8. 原始证据索引

- 需求：`docs/plans/android-parallel-delivery-2026-09-09/README.md`（R1-D1/R2-D2/L1、契约 2、已知问题）、`docs/plans/android/2026-09-12-android-library-folder-smart-recognition-roadmap.md`（§1.1/§4/§5/上限合同/2026-09-13 复验结论）、`docs/plans/android/2026-09-18-android-shelf-maturity-plan.md`（§0 诊断、§8 P0 实施记录：JVM 2507/0 + Redmi c49ac6cf release 真机 10 项 ✓）。
- 历史报告：`docs/plans/android-parallel-delivery-2026-09-09/reports/workbuddy-r4-library-folder.md`（2026-09-12，未真机）、`reports/zcode-r4-library-source-index.md`（2026-09-13，JVM 253/2238）、路线文首 2026-09-13 WorkBuddy A–E PASS 记录。
- E 阶段：`docs/qa/2026-10/2026-10-01-stage-e-final-codex-mumu-acceptance.md`（S05 七站、52 项仪器含平板书架 9）、`docs/coordination/dispatches/2026-10/2026-10-01-stage-e-final-v2-codex-closure.md`（142 路径/基线同源）。
- 审查：`docs/coordination/reviews/2026-10/2026-10-02-next-n1v2-m0v3-codex-review.md` + `functional-tracker.csv`（G1-05 漏列补录、8/23 覆盖口径）。
- 候选内测试（2673 集同输入）：见各表 JVM 行；本轮输出存 `E:\creation-reading-assistant\next-round-library-evidence-v1\consistency-check.txt`（D/基线/候选三方 SHA 对照）。

## 9. 未覆盖项与边界

- 未读全部历史报告（按任务边界只读相关链接）；笔记/阅读器/桌面端不在范围。
- D 工作树漂移文件的具体 WIP 内容未审（只判方向与影响面）；合流时需按文件重估证据。
- 历史真机证据均为旧输入且 Redmi/HyperOS（实体机）；当前 MuMu 授权不能升格为实体机/MIUI 结论。
- 源码归属、JVM 通过、UI 流程通过、数据恢复通过四层分别记录（见各表），互不替代。
