# S12 结果：EPUB 阅读位置（current_location_json）单一契约 — 2026-09-28

- 仓库 `D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`｜分支 `codex/recover-worktree-20260920`｜本轮仅 JVM Gradle，**未运行 MuMu/ADB/真机**，未进原仓库。
- 输入：`docs/coordination/investigations/agent-investigation-s12-qoder.md`（4 写入点/3 量纲调查）、`docs/coordination/legacy-stages/2026-09/2026-09-28-stage-b-reader-risk-delta.md` §1-S12/§4-R2、`docs/handoff/current.md` §26/§27。
- 授权文件使用情况：`LegadoReadingProgressImporter.kt`、`EpubRepository.kt`、`ReaderChapterLoaderCoordinator.kt`（仅注释）、locator 纯函数 `LocatorCodec.kt`/`LocatorBuilder.kt`（确有必要：契约本体在此）。**`ReaderProgressEffects.kt`、`ReaderProgressActions.kt` 零改动**（其 EPUB 写入路径本就自洽）。未触碰 Room schema、Gradle、共享文档与他人文件。

## 1. 字段契约（本轮确立并落地的单一契约）

EPUB 的 `reading_progress.current_location_json` 形态：

```json
{"chapter":ci,"offset":co,"locator_v2":{"v":2,"offset":<全局>?,"ci":ci,"co":co}}
```

| 字段 | 量纲 | 契约 |
|---|---|---|
| `locator_v2.ci` | 章节索引（权威） | 必填 |
| `locator_v2.co` | **章内**字符偏移（权威） | 必填，与 `ci` 成对 |
| `locator_v2.offset` | **全书**字符偏移 | **可选**。写入方给出时必须满足不变式 **`offset == chapterStart[ci] + co`**；给不出（未解析正文/不知章起点表）时**必须缺省**，由读侧按 `chapterStart[ci] + co` 派生。**绝不以章内偏移冒充全局** |
| 顶层 `chapter` / `offset` | 旧版镜像：`chapter=ci`、`offset=co`（EPUB 为章内口径） | 保留，供旧正则解析（`EpubRepository.loadProgress/Offset` 回落、旧 App）继续工作 |

- TXT / Markdown 是本契约的 `ci=0` 单一坐标空间特例（chapterStart[0]=0 ⇒ 全书==章内），三个既有写入点不改即自洽。
- 消费语义（读侧本轮**零改动**，均为既有行为，已用测试钉住）：
  - 首开恢复：`EpubRepository.loadProgress/loadProgressOffset` 读 v2 `ci`/`co`（无 v2 回落顶层正则）；
  - 回源跳转（搜索结果/临时检视返回/深链，`pendingSourceLocatorJson` 链）：`SourceNavigationContract.resolveChapteredPosition`——带全局且与章元组矛盾 ⇒ **安全拒绝**（宁可不跳不落错位）；全局缺省 ⇒ 按章元组派生，天然自洽；
  - 标注定位：`AnchorResolver` 优先 `ci`，仅越界回落全局；
  - 书架章号：读 v2 `ci`。

## 2. 写入点对照（修复前 → 修复后）

| 写入点 | 修复前 v2.offset | 修复前判定 | 修复后 |
|---|---|---|---|
| `LegadoReadingProgressImporter.locationFor`（EPUB 导入） | `co`（章内冒充全局） | ❌ 除首章外违反不变式 | **v2 缺省 offset**（ci/co 权威）✅ |
| `EpubRepository.saveProgress` ← `ReaderChapterLoaderCoordinator:65`（章变更自动保存，3 参缺省路径） | `0`（伪造） | ❌ 除首章外违反不变式 | **v2 缺省 offset** ✅ |
| `EpubRepository.saveProgress` ← 分页防抖（`ReaderProgressEffects:267`）/退出保存（`ReaderProgressActions:253`），调用方传 `absoluteOffset≥0` | 真全局 | ✅ | ✅（不变；调用方负责不变式） |
| `ReaderProgressEffects:228/280`、`ReaderProgressActions:303`（TXT/Markdown） | 全书偏移 | ✅（单一空间特例） | ✅（不变） |

观察（未改，不属本契约缺口）：TXT/MD 写入点的 v2 `co` 实为全书偏移而非章内值；今天无消费者按章元组解释 TXT/MD 记录（TXT 走 `resolvePlainPosition`、MD 走 `preferGlobalOffset=true`），功能无害，登记为已知怪癖。

## 3. 读取点对照（零改动，均已测试钉住）

| 读取点 | 消费字段 | 行为 |
|---|---|---|
| `EpubRepository.loadProgress/loadProgressOffset`（`ReaderDocumentLoader:67/76` 首开） | v2 `ci`/`co`，无 v2 回落顶层 `"chapter"`/`"offset"` 正则 | 新旧格式都正确恢复 |
| `SourceNavigationContract.targetFromStoredLocation` → `resolveChapteredPosition`（回源跳转） | `ci/co` 权威 + 可选全局校验 | 新格式派生可跳；旧伪造格式安全拒绝 |
| `AnchorResolver.resolve`（高亮/笔记定位） | 优先 `ci`，越界回落全局 | 不受影响 |
| `ShelfModels` `"ci"` 正则（书架章号） | v2 `ci` | 不受影响 |
| `LegadoReadingProgressImporter.shouldApply`（重复导入比较） | 本地 `ci/co`（v2 或顶层正则） | 新旧本地格式均可比较；TXT 标记路径不变 |
| `JsonBridge` 备份/恢复 | JSON 原样往返 | 不受影响 |

## 4. 改动文件（5 生产 + 3 新测试；git 状态见证据 00）

| 文件 | 改动 |
|---|---|
| `feature/reader/locator/LocatorCodec.kt` | `encode` 的 `legacyOffset` 改为 `Int?`：null 时**省略** v2 `"offset"` 字段（decode 本就支持该形状）；类注释写明契约与不变式 |
| `feature/reader/locator/LocatorBuilder.kt` | `progressJson` 的 `globalOffset` 改为 `Int?`，默认仍 `= legacyOffset`（TXT/MD 调用点零变化）；KDoc 写明「要么真实、要么缺省，绝不伪造」 |
| `feature/sync/LegadoReadingProgressImporter.kt` | EPUB 分支 `globalOffset = null` + 契约注释（原 `globalOffset = remote.charOffset` 即 S12-① 根因） |
| `feature/reader/EpubRepository.kt` | `saveProgress`：`globalOffset = absoluteOffset.takeIf { it >= 0 }`（原 else 分支用章内偏移冒充全局）+ 契约注释 |
| `ui/viewmodel/ReaderChapterLoaderCoordinator.kt` | 仅更新注释（其 3 参调用经 EpubRepository 收口后自动变为自洽） |
| 新增 `app/src/test/.../feature/sync/LegadoImportEpubLocatorContractTest.kt` | 导入产物精确 JSON + 旧格式重复导入比较 |
| 新增 `app/src/test/.../feature/reader/EpubRepositorySaveProgressLocatorTest.kt` | 仓库写入/读回/自愈 |
| 新增 `app/src/test/.../feature/reader/navigation/StoredProgressLocatorNavigationTest.kt` | 存储 JSON → 回源跳转契约五形态 |

## 5. 定向测试（任务书四场景全覆盖）

命令（`android/` 下，共两轮）：

```bash
./gradlew.bat :app:testDebugUnitTest \
  --tests "...feature.sync.LegadoImportEpubLocatorContractTest" \
  --tests "...feature.sync.LegadoReadingProgressImporterTest" \
  --tests "...feature.reader.EpubRepositorySaveProgressLocatorTest" \
  --tests "...feature.reader.navigation.StoredProgressLocatorNavigationTest" \
  --tests "...feature.reader.navigation.SourceNavigationContractTest" \
  --tests "...feature.reader.locator.LocatorCodecTest" \
  --tests "...ui.viewmodel.ReaderViewModelTest" \
  --tests "...ui.viewmodel.ReaderDocumentLoaderLegadoProgressTest"
```

| 轮次 | 退出码 | 结果 |
|---|---|---|
| run1 | 1 | 108 项，**1 失败**：`StoredProgressLocatorNavigationTest` self-consistent 用例——**测试数据错误**（均匀 1000 字章表下把第 3 章全局写成 1250；生产不变式校验正确拒绝），非生产缺陷 |
| run2 | **0** | **108 项 / 0 失败 / 0 错误 / 0 跳过**（`BUILD SUCCESSFUL in 46s`），XML 逐类汇总：契约测试 2+3+5、既有邻居回归 6+11+4+5+72 全绿 |

四场景映射：

| 场景 | 用例 |
|---|---|
| 导入后首开 | 导入产物精确断言 `{"chapter":8,"offset":321,"locator_v2":{"v":2,"ci":8,"co":321}}`；`loadProgress/Offset` 读回 8/321；`resolveChapteredPosition` 立即可跳（absolute=8321, ch=8, co=321） |
| 章节切换后再保存 | 3 参 `saveProgress(ci=5)` → `{"chapter":5,"offset":0,"locator_v2":{"v":2,"ci":5,"co":0}}`（无伪造全局）；回源跳转解析到本章起点 |
| 退出重进 | 带 `absoluteOffset=1250` 退出保存 → JSON 含真全局且过不变式；`loadProgress/Offset` 读回 3/250 |
| 旧 locator 兼容或安全回退 | 旧伪造全局记录：首开读回 8/321（兼容）+ 回源跳转被安全拒绝（回退）+ 一次章变更保存后自愈（merge 采信新契约产物，跳转恢复）；无 v2 裸 legacy 记录走顶层正则回退；自洽原生记录不受影响；重复导入等值不覆盖 |

**边界声明**：以上全部为 JVM 定向证据。未做设备/端到端验收，不把本轮绿灯写成设备验收；S12 的真机链验收（调查报告 R-1/S12-F5）仍待设备通道。

## 6. 测试原始日志与证据路径

`E:\creation-reading-assistant\codex-s12-locator-20260928\`：

| 工件 | 内容 |
|---|---|
| `00-changed-files-git-status.txt` | 8 个改动/新增文件的 git 状态（3 M + 2 ?? 生产 + 3 ?? 测试；`??` 两件是前轮未提交的新文件，本轮在其上追加改动） |
| `10-targeted-jvm-run1.log` | 第一轮原始输出（含 1 失败，EXIT=1） |
| `11-targeted-jvm-run2.log` | 第二轮原始输出（`BUILD SUCCESSFUL`，EXIT=0） |
| `12-run2-xml-summary.txt` | 逐类 XML 汇总（108/0/0/0） |
| `TEST-*.xml`（8 份） | Gradle 原始 XML 结果 |

Gradle 缓存/构建沿用既有 `D:\develop\env\gradle`（用户既定 GRADLE_USER_HOME）；仅日志与 XML 工件落 E 盘。构建目录重定向需改 Gradle 配置，不属本轮授权，未动。

## 7. 旧数据边界

1. **存量伪造全局记录**（历史导入器写入的 EPUB 进度）：按任务书**不批量改写**。行为边界：首开恢复正确（读 ci/co）；回源跳转被一致性校验安全拒绝并提示「此阅读位置与当前正文不一致」；**任意一次原生保存**（翻页防抖/章切换/退出）即被新契约产物覆盖而自愈。拒绝语义是既有安全设计，本轮未放宽读侧（放宽=改 `SourceNavigationContract.kt`，不在授权文件内，且会削弱「矛盾数据不冒充精确」防线）。
2. **前契约时代裸 legacy 记录**（无 `locator_v2`）：顶层正则回退路径不变，已测。
3. **旧 App 读新数据**：新导入产物保留全部旧字段（顶层 `chapter`/`offset`、v2 `ci`/`co`），仅省略 v2 `offset`；旧正则解析不受缺失字段影响。
4. **TXT/Markdown 数据**：本轮写入路径零改动，存量记录形态不变。

## 8. 未解决项（均不在本轮授权内，维持调查报告登记）

1. **P2-2** 流式大 TXT 导入回书首无提示（TXT 换算域，未动）；
2. **P2-3** 导入经 `mergeReaderProgress` 的 `percent>=99.5 → FINISHED` 静默提升完成态（`ReadingEntities.kt` 数据层语义 + D-3 产品裁决未定，未动；本轮改动未改变该行为）;
3. **P2-4** 导入中状态/`unchanged` 等值措辞（ProfileViewModel/UI 域，未动）；
4. **真机/端到端验收**（S12-F5）待设备通道；
5. 本轮发现的登记项：TXT/MD 写入点 v2 `co`=全书偏移的怪癖（§2 观察，功能无害）。

## 9. SEAM REQUEST（本轮零跨界修改，以下为建议项）

| # | 请求 | 理由 |
|---|---|---|
| SEAM-1 | **可选加固**：在 `EpubRepository.saveProgress` 写入时断言不变式（`absoluteOffset` 与 `chapterStart[ci]+co` 不符则拒绝或降级为缺省全局），需要把章起点表传入仓储或注入校验回调——涉及 `ReaderViewModel`（`SaveEpubProgress` 携带 `chapterStartOffsets`）或 PagedChapterSource 接缝，属 S06/S04 阅读内容 owner。当前调用方只有两处且已自洽，此为纵深防御，非必需 | 防未来新增调用方再次写出不自洽 locator |
| SEAM-2 | `SourceNavigationContract.kt`（navigation 包）本轮**未修改**；若总指挥希望对存量伪造记录放宽回源跳转（用章元组而非拒绝），属产品语义决定 + 该文件归属窗口，不默认执行 | 维持既有安全拒绝语义 |

---

本轮未 commit/push/stage/reset/checkout/clean；未清理他人未提交文件；书籍仅用「测试EPUB」等中性名称。交总指挥复核，本棒停止。

## Codex 回读（2026-09-28）

- 独立回读 `E:\creation-reading-assistant\codex-s12-locator-20260928\12-run2-xml-summary.txt` 与 `11-targeted-jvm-run2.log`：8 类累计 108 项，失败/错误/跳过均 0，`BUILD SUCCESSFUL`、EXIT=0。第一轮 1 失败由测试章起点表与预期全局偏移矛盾引起，第二轮测试数据改为自洽的 3250，未见生产逻辑因该失败而改动。
- 定向对照 `LocatorCodec.encode` 的可选 v2 全局字段、`LocatorBuilder.progressJson` 的 nullable 参数、Legado EPUB 导入传 null、`EpubRepository.saveProgress` 缺省全局时传 null，以及新测试的导入产物/章节跳转断言，关键链路与报告一致。此次复核未重跑 JVM，未做设备级 S12 导入验收。
- 接受 **S12-① EPUB locator 写入契约的代码与定向 JVM 范围**；存量伪造全局记录仍可能使回源跳转被安全拒绝，首次打开按 ci/co 恢复与后续原生保存自愈只在单测/代码路径得到证明，设备端到端仍未验证。TXT/完读状态与 UI 预览不在本次关闭范围。
