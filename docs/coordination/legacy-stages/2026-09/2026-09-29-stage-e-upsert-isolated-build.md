# Stage E 候选 U 隔离构建与定向测试报告（父表 `@Upsert` + 版本号 0.5.1(3)）

> 窗口：Stage E 候选 U 隔离构建（只读恢复仓库 + E 盘隔离目录）。
> 恢复仓库分支 `codex/recover-worktree-20260920`，HEAD `5aed5939600e491843e3c44453921bd6273a233c`（构建前后均为该值）。
> 隔离目录：`E:\creation-reading-assistant\stage-e-u-isolated-20260929\`（基线 `repo\`、工件 `artifacts\`、独占 project cache `project-cache\`）。
> **本轮未连接或操作任何设备；未使用 MuMu；未 stage/commit/push/reset/checkout。**

---

## 一、结论（TL;DR）

1. **候选 U 可独立编译**：HEAD 基线 + 精确 hunk 的隔离候选，`:app:compileDebugKotlin` 与 `:app:compileDebugAndroidTestKotlin` 均**实际执行**且 `BUILD SUCCESSFUL`（EXIT 0）；`assembleDebug` + `assembleDebugAndroidTest` 亦成功。**未发现依赖缺失**，无需追加任何 hunk 或整文件复制。
2. **定向 JVM 测试 11 套件 / 143 tests / 0 failures / 0 errors / 0 skipped**（EXIT 0，从原始 XML 实算）。
3. **`ParentUpsertCascadeSafetyTest`（androidTest）本轮只编译、未运行**：其编译产物（含 5 个 `@Test` 的类）已生成，但**设备 5/5 属 2026-09-23 retry-01 的既有真机证据，不是本轮结果**；本候选单独的设备语义仍未验。
4. **代码级语义证据（无需设备）**：隔离候选的 KSP 生成实现中，5 个父表 DAO 全部改用 `EntityUpsertionAdapter`（`INSERT ... ON CONFLICT DO UPDATE`），`INSERT OR REPLACE` 计数为 0；被有意保留的子/连接表 DAO 仍为 `INSERT OR REPLACE`（计数 1）。
5. **新增观察（H7 行尾）**：`TaxonomyDao.kt`、`build.gradle.kts` 工作树为 CRLF，而 `.gitattributes` 的 `text=auto eol=lf` 会在入库时规范化为 LF。**"将提交的形态"是 LF**，其 SHA 与工作树 SHA 不同（下表）。

---

## 二、隔离方法与基线

| 项 | 值 |
|---|---|
| 基线导出 | `git -c core.autocrlf=false archive HEAD android \| tar -x -C repo`（**只读**） |
| 导出完整性 | `repo/android` 文件数 **825** = `git ls-tree -r HEAD android` 计数 **825**（android/ 无提取失败；复核文档提到的 5 个中文文档失败不在 android/ 子树内，不影响本次编译结论） |
| SDK 配置 | 复制恢复仓库 `android/local.properties`（`sdk.dir=D:\develop\Android\Sdk`） |
| 构建缓存 | `--project-cache-dir E:\...\stage-e-u-isolated-20260929\project-cache`（独占 E 盘，未与其他窗口共用 project cache）；`--offline` |
| hunk 应用 | 4 个裁剪后的 patch，`git -c core.autocrlf=false apply`，逐个 `Applied patch ... cleanly`（gradle patch 因丢弃 G1 有预期 -3 行偏移） |

**候选 patch（E 盘，SHA-256 见 `01-candidate-patches.sha256`）**：

| patch | SHA-256 | 保留内容 |
|---|---|---|
| `candidate-BookDao.patch` | `49fe6e878283266250abc795bfbf62513e952ad489bd8d44312f91975cc9958e` | B1 导入 + B2 `upsert`→`@Upsert`(KDoc) + B3 `softDelete` 注释 + B4 `upsertAll`→`@Upsert` |
| `candidate-InspirationDao.patch` | `6135302f0d377055dfd005dec8834726aae5439bf0a3bd2637b2d5c1d1bfe1c5` | I1 导入 + I2 `upsert`→`@Upsert`(KDoc) |
| `candidate-TaxonomyDao.patch` | `ca4932fcc15cecd6652f3eda10039f6e7ba3379e38fbc0811857b9b8a0f41c2b` | T1 导入 + T2 `TagDao.upsert` + T3 `CategoryDao.upsert` + T4 `ShelfDao.upsert` |
| `candidate-build.gradle.kts.patch` | `40bb9f28fb0e123a39bddb649d1b4d2879bdf3e47093820e1528cbecb11fbff6` | **仅 G2**：`versionCode 2→3`、`versionName 0.5.0→0.5.1` + 4 行注释（同 hunk 内的 runner 变更已降级为上下文行） |

---

## 三、逐文件 SHA 与 hunk 清单

### 3.1 逐文件 SHA（`02-file-sha-table.csv`）

| 文件 | 隔离候选 | 工作树 | HEAD | 关系 |
|---|---|---|---|---|
| `data/local/dao/BookDao.kt` | `702618183028bd04b58a9740a8d3dc37157afc7a8ea759fe7c1fe229395a1d46` | `b344af6752c9c0fe113cb589e0858d664d2d128a4e9b31462b48e748dc7358cf` | `b7500f8d2ad188faf7191c169bde53dc59f97d6d916b3fa6b1a395a0979e4ed9` | 工作树**减去 B5**（两者均 LF） |
| `data/local/dao/InspirationDao.kt` | `241a8cc5e4b2b6ade12a905fe648665cc3002f07765a7437415831feca781105` | `241a8cc5…` | `9bc753ed17015fef2190fe1643a52c9cb74bc7ab102596e66723e080621fc319` | **与工作树逐字节一致** |
| `data/local/dao/TaxonomyDao.kt` | `78c1b3da1e8d29d948599b6d84022700955bf092375b6c57da5fb1a3fe5c327a` | `9f638576203c54a23815bd2d5d577fe2d4f45ca72ddaa4798877ccb20ae38d8e` | `78e1852b4c8a5f82e7ee403f0a7485613850d9383a4cc385869ea8335045eddc` | 内容相同，**CRLF(工作树) vs LF(候选/规范形)** |
| `app/build.gradle.kts` | `731e53fcc75dad2c1795343f64b51614204753afe6371388a420859bf50ed957` | `aac93f05a51d801759bfd0a6678a1267f5d34dfb2b64132c0bf4fbeb63439606` | `2cb816d0b778a2186bd31eb147596191709fb714ab6ef25d20ee2e537d49f479` | HEAD **+ 仅 G2**（LF）；工作树另含 G1/G3/G4/G5 与 CRLF |
| `androidTest/…/ParentUpsertCascadeSafetyTest.kt` | `8f26eebc8c4204d052bfe562575e3148e47510202a2351c6576c97758cd8adbe` | `8f26eebc…` | （HEAD 无此文件） | **与工作树逐字节一致**（未跟踪新文件） |

### 3.2 hunk 核对：隔离候选 vs HEAD（`hunk-listing-vs-HEAD.txt`）

**恰为候选 hunk，无多余内容**：

- `BookDao.kt`：`7a8 +import androidx.room.Upsert`；`16c17,38` `@Insert(REPLACE)`→KDoc+`@Upsert`；`22c44` `softDelete` 注释措辞；`27c49,50` `@Insert(REPLACE)`→注释+`@Upsert`。**无 `getAllActive`/`activeIds`**。
- `InspirationDao.kt`：`6a7` 导入；`15c16,24` upsert→KDoc+`@Upsert`。
- `TaxonomyDao.kt`：`7a8` 导入；`18c19,26` TagDao；`51c59,60` CategoryDao；`87c96,97` ShelfDao —— **行号与报告 T2/T3/T4 完全吻合**。
- `build.gradle.kts`：`22,23c22,27` 仅版本号两行 + 4 行注释。

### 3.3 hunk 核对：隔离候选 vs 工作树（`hunk-listing-vs-worktree.txt`）

**恰为报告要求排除的项**：

- `BookDao.kt`：仅 `61a62,81`（B5 = `getAllActive()` + `activeIds()`，20 行）。
- `build.gradle.kts`：`13a14,16`（G1 `testBuildType`）、`29c32`（G3 runner）、`101a105,114`（G4 `create("qahost")`）、`229a243,245`（G5 `qahostImplementation`）。
- `TaxonomyDao.kt`：忽略行尾后**无任何差异**。

---

## 四、编译证据

| 项 | 值 |
|---|---|
| 命令 | `./gradlew :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin --offline --console=plain --project-cache-dir E:\...\project-cache`（工作目录 `repo/android`） |
| 结果 | `BUILD SUCCESSFUL in 3m 17s`，`30 actionable tasks: 18 executed, 12 from cache`，**EXIT 0** |
| 关键任务实际执行 | `kspDebugKotlin`、`compileDebugKotlin`、`kspDebugAndroidTestKotlin`、`compileDebugAndroidTestKotlin`（均非 UP-TO-DATE/缓存命中） |
| 日志 | `artifacts/compile-u-candidate.log`，SHA-256 `1403bea533d81de431cb5f950b542dbf1867c6c6e24a994e901f9ebaf4fe0673` |
| 唯一警告 | `LibraryBrowserComponents.kt:498` `Icons.Outlined.HelpOutline` 弃用（HEAD 既有，与本组无关） |
| androidTest 编译产物 | `build/tmp/kotlin-classes/debugAndroidTest/…/ParentUpsertCascadeSafetyTest*.class`（含 `softDeletePreservesChildRows` 等用例 lambda 类）存在 |
| 打包 | `:app:assembleDebug :app:assembleDebugAndroidTest` → `BUILD SUCCESSFUL in 32s`，EXIT 0（`assemble-u-candidate.log`，SHA-256 `1c9bad40dbc8f60947ebe5ca53c4588b7731fdc7767e4e76f11fbc03475de2ce`） |
| debug APK SHA-256 | `1e7aa4d1565befbdcb195f0c5d635ea2acdb80fd2bb6fc29edf296efe535f5a1` |
| androidTest APK SHA-256 | `73547e350b16b970a4750eff7ebf1c2e78be53188168f7ee0eec3cc50268907b` |

**依赖缺失结论：无。** androidTest 全量编译通过即证明 `ParentUpsertCascadeSafetyTest.kt` 所依赖的 entity/`AppDatabase` 在 HEAD + 候选内齐备；**未使用任何整文件复制**来掩盖缺失 hunk。

### 4.1 生成代码语义证据（无设备）

KSP 生成实现（`build/generated/ksp/debug/java/.../dao/`）：

| 生成实现 | `EntityUpsertionAdapter` 出现次数 | `INSERT OR REPLACE` 出现次数 | 说明 |
|---|---|---|---|
| `BookDao_Impl.java` | 3 | **0** | `books` 父表已走 upsert |
| `InspirationDao_Impl.java` | 3 | **0** | `inspirations` 父表已走 upsert |
| `TagDao_Impl.java` / `CategoryDao_Impl.java` / `ShelfDao_Impl.java` | 3 / 3 / 3 | **0 / 0 / 0** | 三个 taxonomy 父表已走 upsert |
| `BookContentDao_Impl.java` | 0 | 1 | 子表，按报告保持 REPLACE |
| `BookTagDao_Impl.java` / `BookCategoryDao_Impl.java` / `ShelfBookDao_Impl.java` | 0 / 0 / 0 | 1 / 1 / 1 | 连接/子表，保持 REPLACE |

即：候选的注解改动确实改变了 Room 生成代码的冲突策略，且改动范围与报告声明的「父表全覆盖、子/连接表不动」一致。

---

## 五、定向 JVM 测试

| 项 | 值 |
|---|---|
| 命令 | `./gradlew :app:testDebugUnitTest --offline --console=plain --project-cache-dir E:\...\project-cache --tests …`（11 个类过滤，见下） |
| 结果 | `BUILD SUCCESSFUL in 1m 15s`，**EXIT 0** |
| XML 实算 | **11 套件 / 143 tests / 0 failures / 0 errors / 0 skipped** |
| 日志 | `artifacts/test-u-candidate-jvm.log`，SHA-256 `4e029a49f5f8a6d3c57727850abcdef4f3f5689e62af5997a99b3aaeb6f496c9` |

套件（均位于 `app/src/test/`，HEAD 源集）：

1. `data.repository.BookRepositoryTest`
2. `data.repository.InspirationRepositoryTest`
3. `data.repository.TaxonomyRepositoryTest`
4. `data.repository.NoteRepositoryTest`
5. `data.repository.NoteRepositoryRestoreTest`
6. `data.repository.SyncRepositoryTest`
7. `data.repository.ChapterReadRepositoryTest`
8. `feature.library.deletion.BookDeletionRestoreTest`
9. `feature.sync.JsonBridgeTest`
10. `feature.search.SearchIndexRepositorySearchTest`
11. `ui.viewmodel.ShelfViewModelTest`

**失败归因：无失败。** 需说明口径：这些 JVM 用例通过 mock/fake DAO 覆盖仓储与删除/备份/同步路径的**接口级**行为；`@Upsert` 的**运行期 SQL 语义**（不触发级联删除）不在 JVM 覆盖范围内——它只能由 androidTest（本轮未运行）或设备验证。

---

## 六、未验 / HOLD

| # | 项 | 状态 |
|---|---|---|
| U-A | `ParentUpsertCascadeSafetyTest` **设备运行**（5 个用例） | **NOT RUN（本轮）**。既有 5/5 是 2026-09-23 00:06 真机 `c49ac6cf` 的 `retry-01` 结果（整棵工作树产物），**不能记为本轮或本候选单独通过**。本窗口不使用 MuMu、不操作设备 |
| U-B | `@Upsert` 运行期级联语义（真实 SQLite / Room 打开 `foreign_keys=ON`） | 需设备或 Robolectric 类环境；本轮仅有生成代码证据（§4.1） |
| U-C | 版本号 0.5.1(3) 的界面显示 | 未验（需设备；G2 注释自述在「我的 → 日志与诊断 → 应用版本」显示 0.5.1） |
| U-D | 行尾规范化（H7） | **本轮新增观察**：工作树 `TaxonomyDao.kt`（CRLF）、`build.gradle.kts`（CRLF/LF 混合）与 `.gitattributes`（`text=auto eol=lf`）冲突；按规范形（LF）计算的候选 SHA 见 §3.1。提交时实际入库内容将是 LF 形态 |
| U-E | 5 个用例未覆盖的路径 | 沿用候选报告 §9 H4：`reader_text_rules`/`reader_text_corrections` 未播种、tags/categories/shelves 的 `softDelete` 未测、同步合并路径未测 |
| U-F | 与 G0（Room v15）的联合 | 未做；报告判定两者文件不相交、无硬依赖顺序 |

---

## 七、只读性与纪律声明

- 恢复仓库**源码与 Git 状态未改动**：五个相关文件工作树 SHA 与开工时逐字节一致（`BookDao b344af67…`、`InspirationDao 241a8cc5…`、`TaxonomyDao 9f638576…`、`build.gradle.kts aac93f05…`、`ParentUpsertCascadeSafetyTest 8f26eebc…`）；`git status --short` 行数与开工前同量级，无新增改动。
- 只读操作仅用 `git archive` / `git show` / `git diff`（全程 `GIT_OPTIONAL_LOCKS=0`，避免索引写入）。
- 未 stage/commit/push/reset/checkout；未操作设备；未进入原始仓库。
- 唯一写入的仓库文件：**本文件**。
- 大工件（基线树、patch、日志、XML、APK、SHA 表）全部位于 `E:\creation-reading-assistant\stage-e-u-isolated-20260929\`。

## 八、交接回执

- 隔离目录：`E:\creation-reading-assistant\stage-e-u-isolated-20260929\`（`repo\` 基线+候选、`artifacts\` 工件、`project-cache\` 独占缓存）
- 关键工件：`01-candidate-patches.sha256`、`02-file-sha-table.csv`、`03-logs.sha256`、`hunk-listing-vs-HEAD.txt`、`hunk-listing-vs-worktree.txt`、`compile-u-candidate.log`、`test-u-candidate-jvm.log`、`assemble-u-candidate.log`、`07-apks-u.sha256`
- 供后续设备步骤的现成产物：`app-debug.apk` `1e7aa4d1…`、`app-debug-androidTest.apk` `73547e35…`
- 仍需 MuMu/真机验证：`ParentUpsertCascadeSafetyTest` 5 个用例的设备运行（U-A）、版本号界面显示（U-C）
