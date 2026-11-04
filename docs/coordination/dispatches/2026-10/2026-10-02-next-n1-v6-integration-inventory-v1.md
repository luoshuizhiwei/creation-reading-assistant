# N1-v6-integration-inventory-v1 — 合流准备清单（只读）

- 任务编号/版本：**N1-v6-integration-inventory-v1**。角色：只读合流准备者；输出文件归属与冲突清单，不判断 N1 验收、不实际合流。
- 开始时间：2026-10-02T17:56+08:00；应用/模型：ZCode / GLM（account:zai-start-plan）。
- 自有工件：`E:\creation-reading-assistant\next-round-n1-v6-integration-inventory-v1\`（树差、分文件 diff、拟合流补丁）。唯一仓库写入 = 本报告。**零构建、零 ADB、零设备、零 Git 写入**。

## 1. 输入指纹

| 项 | 值 |
|---|---|
| 恢复仓库 HEAD | `e39a78da9c62f02614207cffb36969a32492eaea`（开工/收工两次核验一致）；暂存区空；工作树含既有未提交资产（非本任务产物，未触碰） |
| 已验收联合候选 | `E:\creation-reading-assistant\next-round-joint-j1-v3\android`（六文件冻结清单 6/6 重算一致） |
| N1-v6 待审候选 | `E:\creation-reading-assistant\next-round-n1-repair-v6\android` |
| N1-v6 自报指纹核验 | LocalZipBackup `f141941a…`、LocalZipBackupTest `4d6825b1…` 与报告一致；父 v5 四文件 `n1_hashes` 与 `n1v5-g1v2-codex-review-20261002\verification.json` 引用一致（LocalZipRestoreStateStore `167b881a…`、JsonBridge `f8e455a4…`） |
| 依据报告 | `docs/coordination/dispatches/2026-10/2026-10-02-next-n1-repair-v6.md`（v6=两文件修复：LocalZipBackup +42/−17 统一义务判定 + LocalZipBackupTest +213 四条组合路径；红 4→绿 61/0 定向 JVM） |

## 2. 排除规则（本轮统一口径，两次树差同规则）

- 排除目录前缀：`app/build`、`build`、`.gradle`、`.kotlin`、`benchmark/build`（构建/缓存/生成物）。
- 排除文件：`local.properties`（机器配置）。
- 其余全部纳入。已知口径瑕疵：对 HEAD 的辅助遍历中文件名前缀 `build` 曾误排 `*/build.gradle.kts`（已用第二次定向核验更正，见 §3 分类——三份 build.gradle.kts 实为双候选同字节）。桌面线（`src/`、`electron/`）不在 android 候选树内，天然排除。

## 3. 树差总纲（两条权威 diff）

**① N1-v6 ↔ J1-v3：差异恰为 8 文件**（6 改 + 2 新增；877 vs 875，无其他差异、无缺失）——即 N1-v6 已包含 J1-v3 的全部 R1/R2/J1 状态（SyncRepository/WebDavBackup/build.gradle.kts/两测试文件逐字节同）。

**② N1-v6 ↔ HEAD（继承总账）**：J1 链贡献 = SyncRepository、WebDavBackup（改）+ SyncRepositoryLocalFactsTest、WebDavCompatibilityTest、SyncRepositoryPersistenceTest（新增测试）+ app/build.gradle.kts（mockwebserver/mockk-android hunk）；N1 链贡献 = §4 的 8 文件 + 5 个 EOL 变体文件（见 §5 排除项）。HEAD-only = 0。

## 4. 拟合流文件清单（8 项，全部 SHAs 实算）

| # | 完整相对路径（android/ 前缀省略） | J1-v3 | N1-v6 | HEAD | 共享 WIP（D 树） | 来源任务与目的 | 交集 | 建议 |
|---|---|---|---|---|---|---|---|---|
| 1 | `app/src/main/java/…/feature/sync/LocalZipBackup.kt` | ab78a16e… | f141941a… | ab78a16e… | ab78a16e…（无漂移） | N1-v1 起累积（中断计划/身份义务），v3 事务接缝，v4 身份/legacy，v5 冲突分支，**v6 统一义务判定**（+42/−17） | 无 | **完整文件替换**（采 N1-v6） |
| 2 | `app/src/main/java/…/feature/sync/LocalZipRestoreStateStore.kt` | —（不存在） | 167b881a… | —（不存在） | 不存在 | N1-v1 新增（磁盘持久中断状态：阶段/指纹/义务/pending），v5 定形 | 无 | **完整新增** |
| 3 | `app/src/main/java/…/feature/sync/JsonBridge.kt` | b9d8c361… | f8e455a4… | b9d8c361… | b9d8c361…（无漂移） | **N1-v4 接缝**：导入合并结果回报（含 equal 分支，`JsonBridge.kt:410-414`），供恢复义务判定消费 | 无 | **完整文件替换** |
| 4 | `app/src/main/java/…/ui/screen/profile/ProfileRoute.kt` | 99eb2aaf… | 89a793ba… | 99eb2aaf… | **a1cad555…（漂移）** | N1-v1 引入持久中断横幅（`zipRestoreState` 收集 + 进入「我的」页刷新 + `ZipRestoreInterruptedBanner` 重试入口），v2 定形 | **与 D-WIP 同文件**（D：设置搜索 focusAnchor/LazyList 状态/syncPrefs·aboutUpdate 收集；与 N1 改动不同区域，但 `ProfileScreen(` 调用点两侧均有增改） | **精确 hunk 合并**（集成者执行；两套改动区域不同、无语义冲突证据，但需逐 hunk 核） |
| 5 | `app/src/main/java/…/ui/viewmodel/ProfileViewModel.kt` | 9ef25b8f… | 124517d2… | 9ef25b8f… | **f538cbb3…（漂移）** | N1-v1 定形：`zipRestoreState` StateFlow + `refreshZipRestoreState(context)`（导入/恢复成功后刷新） | **与 D-WIP 同文件**（D 漂移内容待用户/集成者定性） | **精确 hunk 合并** |
| 6 | `app/src/test/java/…/feature/sync/JsonBridgeTest.kt` | d6548b12… | bcc27a4d… | d6548b12… | d6548b12…（无漂移） | N1-v4：等值回报契约测试 | 无 | **完整文件替换** |
| 7 | `app/src/test/java/…/feature/sync/LocalZipBackupTest.kt` | 174cfbf6… | 4d6825b1… | 174cfbf6… | 174cfbf6…（无漂移） | N1-v1 起累积；**v6 +213 行四条组合路径**（C1 A→B→B / C2 坏 ZIP 空指纹 / C3 legacy 长度 / C4 重选 A 收敛；每条断言正文字节+义务+状态+导出阻断） | 无 | **完整文件替换** |
| 8 | `app/src/test/java/…/feature/sync/LocalZipRestoreStateStoreTest.kt` | —（不存在） | 9eba0a91… | —（不存在） | 不存在 | N1-v1 新增状态存储测试，v5 定形 | 无 | **完整新增** |

继承链核验（各轮候选实测 SHA）：JsonBridge 不变至 **v4**（f8e455a4 引入接缝）；ProfileRoute 于 **v2** 定形（89a793ba）；ProfileViewModel 于 **v1** 定形（124517d2）；StateStore v1→v5 五个形态（167b881a 定形）；LocalZipBackup v1→v6 六个形态；三个测试文件随对应轮次演进。**"v6 只改两文件"不等于合流只需两文件——继承集=8 文件。**

## 5. 分类与排除项

- **保持 J1-v3（已在 N1-v6 中同字节，零动作）**：`SyncRepository.kt`、`WebDavBackup.kt`、`app/build.gradle.kts`（R3 签名 hunk + J1 mockwebserver/mockk-android；**不含 G1 的 .qahostlibrary 后缀** ✓）、`SyncRepositoryLocalFactsTest.kt`、`WebDavCompatibilityTest.kt`；G1 的 `LibraryLifecycleAcceptanceTest.kt`/后缀不在 N1-v6 亦不复制 ✓。
- **测试宿主配置**：`app/build.gradle.kts` 属测试宿主配置范畴（androidTest 依赖 hunk），J1-v3 版本即为合流态。
- **必须排除**：`build/`、`.gradle/`、`.kotlin/`、`benchmark/build`（构建产物）；`local.properties`（机器配置）；桌面线文件（不在 android 树）；**N1-v6 候选内 5 个 EOL 变体文件**（`gradlew.bat` + `scripts/*.ps1` ×4——与 HEAD 逐字节差异仅 CRLF/LF 行尾，实算核验；**保持 J1-v3/HEAD 形态，不带入 N1 的行尾变体**）。
- **待处理（交 Codex）**：ProfileRoute.kt / ProfileViewModel.kt 的 **D-WIP 交集**（漂移内容：设置搜索 focusAnchor 滚动定位、syncPrefs/aboutUpdate 接线——属未提交用户资产，归属与先后需 Codex/用户定性）。

## 6. 拟合流补丁（自有 E 目录，未应用）

`E:\creation-reading-assistant\next-round-n1-v6-integration-inventory-v1\patch\n1v6-onto-j1v3.patch`（3195 行，8 个文件 delta：6 替换 + 2 新增；**基线 = `next-round-joint-j1-v3\android` 六文件冻结态**，目标 = N1-v6 同 8 文件；`patch/baseline|target/` 留有两侧副本与 README-baseline.txt）。**未应用到共享仓库或任何既有候选。**

## 7. 后续最小顺序建议（草案；N1 独立审查未通过前不生效）

1. **Codex 定性**两处 D-WIP 交集（ProfileRoute/ProfileViewModel）：WIP 先行合入或 N1 先行 + WIP 重放，二选一后确定合流树的这两文件形态。
2. N1-v6 独立审查通过（并行审查窗口结论）后，集成者以 §6 补丁为起点在**新合流候选**构建：`assembleDebug`/`assembleQahost` + 定向 `testDebugUnitTest --tests LocalZipBackupTest,JsonBridgeTest,LocalZipRestoreStateStoreTest`（61+ 若干+若干，红绿合同已备）+ `lintDebug`。
3. 隔离 MuMu：N1 真实 kill 中断验收（复用 Library-evidence 前的 N1 四组合路径场景 + 重选 A 收敛 + 恢复对账 + 导出阻断），**区分证据层级**——v6 的 61/0 是 JVM 定向证据（mock 状态存储文件系统语义），真实 Room/真实 kill/设备重试/恢复对账均**尚未执行**。
4. 设备批次按现行协议冷备/恢复；通过后并入永久集成清单（新精确范围）。

## 8. 未覆盖项

- 未审 N1 全部算法语义（属并行独立审查窗口）；未审 D-WIP 两 Profile 文件的漂移内容语义（只登记交集与区域特征）；未核 N1-v1..v5 各轮报告全文（继承以候选实测 SHA 链为准）。
- 暂存区/工作树资产归属、合流时点、D-WIP 合入授权均超出本任务。

## 9. 恢复与副作用

零构建/设备/Git 写入；原候选、freeze、报告、HOLD 全部只读。E 盘新增：本轮核对目录（树差 ×2、分文件 diff ×4、拟合流补丁 + 双侧副本 + README）。

## 10. 交付状态

**交付成立：清单 8 项（6 替换/新增建议 + 2 项 hunk 合并待 Codex 处理）+ 排除项 + EOL 噪声 5 文件 + 拟合流补丁（未应用）。** 需 Codex 处理的冲突：① ProfileRoute.kt / ProfileViewModel.kt 的 D-WIP 交集定性；② N1 独立审查结论回填后本清单方可从草案转正式。报告路径：`docs/coordination/dispatches/2026-10/2026-10-02-next-n1-v6-integration-inventory-v1.md`（本文件）。
