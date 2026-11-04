# 32路径集成范围预检（合并前只读预案）

- 任务编号/版本：CRA-INTEGRATION-SCOPE-PRECHECK-v1；2026-10-03 指定，实际执行 2026-10-04T00:14–01:05+08:00，应用 ZCode/GLM。
- 输入指纹：HEAD `e39a78da9c62f02614207cffb36969a32492eaea`（开始/结束一致）；暂存区空（前后均 0 条 staged）；工作树状态条目 1569（开始）→1570（结束），唯一新增为另一窗口报告 `docs/coordination/reviews/2026-10/2026-10-03-next-reading-coverage-review-v1.md`（untracked，未读取）。固定输入 [manifest.json](<E:/creation-reading-assistant/mdfix-review-20261003/payload32-preparation/manifest.json>) SHA256 `cf05ea577548de034dfbef16c612c658f60c48bdc4b62d352061a70ae7107358` + `payload\` 32 文件；32 路径 D 侧 SHA 开始/结束零漂移。已有同任务结果占用检查：`next-round-integration-scope-precheck-v1` 此前不存在，本轮新建。
- 所有权与改动：只读 HEAD/暂存区/D 盘 32 路径与状态清单、E 盘固定输入、mdfix-review 审查报告；写入仅自有目录 `E:\creation-reading-assistant\next-round-integration-scope-precheck-v1`（scripts×7、verify/threeway/hunk/wip/final JSON×5）与本唯一报告。未应用 payload、未构建、未操作设备、未执行任何 Git 写操作。
- 环境：Windows Git Bash + Python 3.13（difflib 行区间 + 逐函数人工阅读）。关键事实：`core.autocrlf=true` 且 `.gitattributes` 为 `* text=auto eol=lf`——HEAD blob 为 LF、多数 D 工作树文件为 CRLF，原始字节 SHA 会把「仅 EOL 不同」误判成改动；本预检分类一律用 CRLF→LF 规范化内容比较，原始字节 SHA 另列存证。
- 执行与结果：
  1. **manifest 结构**：scope=32、32 条唯一相对路径、全部在 `android/` 下、无 `..`/绝对路径/反斜杠；payload 目录恰好 32 文件与 manifest 一一对应；**32/32 `candidate_sha256` 与 payload 实测字节一致**。
  2. **污染检查**：通过。无临时批次后缀（仅既有 `.qahost` 变体定义）、无设备驱动/日志/APK/生成物混入 payload；命中项均为良性（`library_source_refs` 产品 schema、URL-userio 安全用例中的 `127.0.0.1`、LocalZipBackupTest 的 `N1v6_*` 证据可溯性测试命名）。
  3. **manifest 记录质量**（3 项缺陷，不影响 payload 完整性）：(a) rollback-v3×3 与 md-annotation×1 共 4 条 `head_sha256`/`WIP_sha256` 为 null，本预检已补算（见清单）；(b) WIP 记录用原始字节、head 记录用 blob，基准不一致——build.gradle.kts 因此看似「D 已改」，实际 D 与 HEAD 内容一致（仅 EOL 混合）；(c) 4 个 payload 文件为 CRLF（SyncRepository.kt、WebDavBackup.kt、SyncRepositoryLocalFactsTest.kt、WebDavCompatibilityTest.kt），与仓库 eol=lf 标准不一致，建议落盘前归一为 LF（git 提交时 text=auto 也会归一）。
  4. **五类分类**（32 = 10 + 13 + 9）：A 无需再写 10；B 可直接应用 13（5 个 D==HEAD + 8 个新文件）；C 必须合并 9。逐路径清单见下表（类别 A1=D 与 payload 已一致(tracked)、A2=untracked 新文件且内容已一致、B1=D==HEAD 覆写即得、B2=新增落盘、M=必须合并；SHA 为 12 位缩写，H/D/P = HEAD/D 工作树/payload）。

| 路径（android/ 前缀省略） | 类别 | H / D / P | 归属 | 建议应用方式与边界 |
|---|---|---|---|---|
| app/build.gradle.kts | B1 | 94213a46·aac93f05·ae078ff0 | accepted_plus_merge (hunks=2) | D 内容==HEAD（仅 EOL 混合）；授权后直接以 payload 覆写，顺带归一 EOL；确认无批次后缀 |
| …/data/repository/SyncOptions.kt | A2 | —(不在HEAD)·D==P·lf | explicit_WIP_dependency | D 已有同内容（untracked 用户资产）；无需写 |
| …/data/settings/SyncPrefsStore.kt | A2 | —·D==P·lf | explicit_WIP_dependency | 同上 |
| …/ui/viewmodel/ProfileUpdateChecker.kt | A2 | —·D==P·lf | explicit_WIP_dependency | 同上 |
| …/ui/components/SettingsDensity.kt | A2 | —·D==P·lf | explicit_WIP_dependency | 同上 |
| …/ui/viewmodel/ProfileSyncEngine.kt | A1 | ≠·D==P·lf | explicit_WIP_dependency | D 工作树已含接受内容；无需写 |
| …/ui/screen/profile/ProfileUiState.kt | A1 | ≠·D==P·lf | explicit_WIP_dependency | 同上 |
| …/ui/screen/profile/WebDavSubPage.kt | A1 | ≠·D==P·lf | explicit_WIP_dependency | 同上 |
| …/ui/screen/profile/SyncSubPage.kt | A1 | ≠·D==P·lf | explicit_WIP_dependency | 同上 |
| …/ui/components/AppLayout.kt | A1 | ≠·D==P·lf | explicit_WIP_dependency | 同上 |
| …/ui/layout/LayoutTokens.kt | A1 | ≠·D==P·lf | explicit_WIP_dependency | 同上 |
| …/feature/sync/JsonBridge.kt | B1 | b9d8c3612eed·b9d8c3612eed·f8e455a452e0 | accepted_plus_merge | D 内容==HEAD；授权后覆写 payload（N1 接缝） |
| …/feature/sync/LocalZipBackup.kt | B1 | ab78a16e8e34·ab78a16e8e34·f141941a628d | accepted_plus_merge | 同上（v1→v6） |
| …/feature/sync/JsonBridgeTest.kt | B1 | d6548b124894·d6548b124894·bcc27a4d0635 | accepted_plus_merge | 同上 |
| …/feature/sync/LocalZipBackupTest.kt | B1 | 174cfbf6cbd3·174cfbf6cbd3·000b7c811abc | accepted_plus_merge | 同上 |
| …/androidTest/…/SyncRepositoryPersistenceTest.kt | B2 | 不在 HEAD、不在 D | accepted_plus_merge | 新文件落盘（LF） |
| …/feature/sync/LocalZipRestoreStateStore.kt | B2 | 不在 HEAD、不在 D | accepted_plus_merge | 新文件落盘（LF，N1 新增生产类） |
| …/test/…/SyncRepositoryLocalFactsTest.kt | B2 | 不在 HEAD、不在 D | accepted_plus_merge | 新文件落盘（**CRLF→建议归一**） |
| …/test/…/LocalZipRestoreStateStoreTest.kt | B2 | 不在 HEAD、不在 D | accepted_plus_merge | 新文件落盘（LF） |
| …/test/…/WebDavCompatibilityTest.kt | B2 | 不在 HEAD、不在 D | accepted_plus_merge | 新文件落盘（**CRLF→建议归一**） |
| …/test/…/EpubRepositoryReplaceRollbackTest.kt | B2 | 不在 HEAD、不在 D | rollback-v3 | 新文件落盘（LF）；断言兼容性见接缝 4 |
| …/ui/screen/reader/ReaderAnnotationNavigation.kt | B2 | 不在 HEAD、不在 D | md-annotation-fix-v1 | 新文件落盘（LF）；必须与 M 类 ReaderProgressEffects 同批 |
| …/test/…/ReaderAnnotationNavigationTest.kt | B2 | 不在 HEAD、不在 D | md-annotation-fix-v1 | 新文件落盘（LF） |
| …/data/repository/SyncRepository.kt | M | 529/616/805 行 | accepted_plus_merge | 同函数双侧编辑（见分析①） |
| …/feature/sync/WebDavBackup.kt | M | 314/471/558 行 | accepted_plus_merge | 同函数双侧编辑（见分析②） |
| …/ui/screen/profile/ProfileRoute.kt | M | 513/571/644 行 | accepted_plus_merge | 授权边界：legado 备份删留（见分析③） |
| …/ui/viewmodel/ProfileViewModel.kt | M | 674/757/777 行 | accepted_plus_merge | 授权边界：legado 备份删留（见分析④） |
| …/test/…/ProfileViewModelTest.kt | M | 206/243/210 行 | merged_test_fixture | 重复符号去重（见分析⑤） |
| …/feature/library/ShelfImporter.kt | M | 1093/1213/1150 行 | rollback-v3 | 平行实现二选一（见分析⑥） |
| …/feature/reader/EpubRepository.kt | M | 351/538/408 行 | rollback-v3 | 平行实现二选一（见分析⑥） |
| …/test/…/ShelfImporterTest.kt | M | 686/1527/943 行 | rollback-v3 | 文本可合、断言随⑥所选实现（见分析⑦） |
| …/ui/screen/reader/ReaderProgressEffects.kt | M | 858/881/888 行 | md-annotation-fix-v1 | 文本干净 0 重叠，接缝验证（见分析⑧） |

  5. **需要合并路径的 hunk 交集分析**（只读，未执行合并；完整行区间与片段在 `hunk_intersection_result.json`）：
     - ① SyncRepository：payload 19 hunks 中 12 与 D 重叠。D=用户 WIP 的 SyncOptions 门控（pull/push 按 `syncBooks/syncInspirations/syncProgress` 跳空列表、`conflictStrategy` 参数化 shouldAcceptRemote）；payload=已核定 R1 本地事实/提升列合并重写（applyBook/applyProgress/applySession 及 push 信封映射）。**已核定修复 hunks 与既有用户修改咬合在同一批函数体内**，必须人工合流：payload 的信封合并语义装进 D 的门控骨架，两者都保留。
     - ② WebDavBackup：10/13 重叠。D 侧重构了请求构建/PROPFIND/鉴权头（471 行）；payload=W1 可追溯服务（558 行）。同区域双侧改写，人工合流。
     - ③ ProfileRoute：5/21 重叠，其中**授权边界**：D 已删除 `ProfileAction.RefreshLegadoBackups` 处理器（H391-398 delete）并改两处恢复/导入确认文案；payload 仍保留并修改该处理器。应用前 owner 必须决策「D 的删除是否为最终意图」——不得用 payload 静默复活已删除功能，反之则 D 的删除作废。
     - ④ ProfileViewModel：9/19 重叠，同样含 legado 决策（D 删除 `legadoImporter`/`_legadoBackups`/`loadLegadoBackups` 相关；payload 保留并修改），另双侧都加了 `syncPrefsStore` 门控；`testWebDav`/`runSync` 调用点双侧都改。
     - ⑤ ProfileViewModelTest：文本区间不重叠，但 payload 两处插入（`syncPrefsStore` mock 声明 + 构造参数）在 D 已存在（D114-117、D141），naive 应用会**重复声明编译错**；D 还重排了构造参数顺序。合流=跳过 payload 这两处插入、保留 D 版本。
     - ⑥ ShelfImporter / EpubRepository：**同一缺陷的平行实现**。D 工作树已含用户自己的崩溃残留守卫（注释援引 2026-09-24 设计稿；phase 标记 `oldCopyMovedToBackup`/`newCopyPromoted` 与 `backupReady`/`promoteStarted`；Epub 侧另有 fileOps 注入与 S04 content_hash），payload rollback-v3 是独立再实现（`swapStage`/`oldBytesParkedInBackup`，文案「检测到未完成的替换备份…」vs D 的「检测到未完成的替换残留…」）。三个重叠区间正是守卫/回滚本体。建议默认保留 D 实现、弃用 payload 的 5+5 hunks（D 更新且为用户资产），由 owner 拍板。
     - ⑦ ShelfImporterTest：payload 3 hunks（2 处 import + 253 行 backup 残留测试块）与 D 的 12 处改动仅空行邻接，文本三方可合；但断言文案（「修复失败」「备份」）必须按⑥所选实现核对——D 的 `repairFileTyped`/`RepairOutcome` 包装后的 facade 文案若不含这些词，测试断言需改。
     - ⑧ ReaderProgressEffects：0 重叠；payload 3 hunks 与 D 的 6 处改动（4 处空行 + 2 处 `chapterIndex = 0`）完全分离，干净三方合并即可；应用后确认 `ReaderAnnotationNavigation` 导入解析（依赖 B2 新文件同批落盘）。
  6. **32 路径外共享 WIP 边界**：工作树共 1570 条状态（0 staged）；32 路径内 19 条（15 tracked 修改 + 4 untracked）；**范围外 1550 条（212 tracked 修改 + 1338 untracked）全部未触碰**，其中包括用户资产（BookRepository、ReaderScreen 系列 androidTest、PerBookSettingsStore 等）、docs/qa/evidence 历史证据 bulk、桌面 `src/`、`.opencode`/`.workbuddy-ai`/`.scratch-shots` 等。本预检未读取范围外文件内容（仅路径清单）。
- 原始证据：`E:\creation-reading-assistant\next-round-integration-scope-precheck-v1\`：`verify_payload_result.json`（首轮原始字节比对）、`threeway_eol_result.json`（规范化三方全 SHA + EOL 指纹）、`hunk_intersection_result.json`（9 路径逐 hunk 区间/片段/关系）、`wip_inventory.json`（开始时全量状态快照）、`final_inventory.json`（收尾指纹 + 32 路径清单）、`scripts\`×7 可复跑。
- 未覆盖项：未执行 `git merge-file`/编译/测试验证（交集结论是 difflib 行区间近似 + 函数级人工阅读，「干净三方」是预期而非证明）；未读取另一窗口正在生成的候选与 `2026-10-03-next-reading-coverage-review-v1.md`；未验证 payload 测试断言与 D 实现文案的最终一致性（列为接缝）；hunks 字段仅 manifest 记录（build.gradle.kts=2），其余 origin 的 hunk 数以本预检实测为准。
- 恢复与副作用：无。纯只读预检，无设备、无设置、无临时文件产生于共享树；E 盘写入全部在自有目录。
- 交付状态与下一步：32 路径三方分类与逐路径预案完成；应用后验收锚点=32 路径 D 侧内容与 payload 一致（其中 9 条按上述合流决策后一致）。本清单是**固定输入（HEAD e39a78da + manifest cf05ea57）下的预案，不是已授权回灌或提交清单**。下一步由合流 owner 按「分析①-⑧」执行：先定③④ legado 删留与⑥守卫实现二选一两项授权决策，再按 B1 覆写→B2 新增→M 人工合流顺序落盘，定向回归（SyncRepositoryLocalFacts/LocalZipBackup/RestoreStateStore/WebDavCompatibility/ProfileViewModel/ShelfImporter/ReaderAnnotationNavigation 套件）后再进入 CRA-INTEGRATION-BUILD-ACCEPT-v2 的构建与 MuMu 验证。完成本预检即停止；未应用 payload、未操作 Git 写入、未启动下一任务。
