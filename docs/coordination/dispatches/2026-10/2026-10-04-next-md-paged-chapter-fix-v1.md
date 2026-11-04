# CRA-MD-PAGED-CHAPTER-FIX-v1 —— Markdown 分页模式目录选章正文不动：定位与最小修复

- 任务编号/版本：`CRA-MD-PAGED-CHAPTER-FIX-v1`；2026-10-04。
- 执行者：ZCode / GLM（account:zai-start-plan）。本批唯一 MuMu owner（serial `127.0.0.1:16416`，全程仅此 serial）。
- 性质：定位 → 最小修复 → 限定设备验收。未改 D 产品源码、原损坏仓库、HOLD、共享交接入口与他人报告；未暂存/commit/push/reset/checkout。
- 状态：**已交付待 Codex 核查**。分页 TOC 缺陷在本任务候选内已定位、修复并按 A–F 限定验收通过；滚动模式语义回归通过；永久合流未做。

## 输入指纹

| 项 | 值 |
|---|---|
| 恢复仓库 HEAD（开工=收工） | `e39a78da9c62f02614207cffb36969a32492eaea`；暂存区条目 0（开工/收工均核） |
| 产品输入（唯一） | `E:\creation-reading-assistant\next-round-joint-final-v3\android`（25217 文件全量复制，关键件 SHA 逐字节核对一致） |
| 上游失败证据 | `next-round-joint-final-v3\device-md-r3\`：选第2章后 toolbar「MD · 第 2 章 · 0%」而正文 TextView「第 1 章…」、底栏「第 1 章 0.0%」（XML/截图原件未动） |
| 复用驱动 | `E:\creation-reading-assistant\next-round-joint-mumu-driver-v4`（未改动）；final-v3 的配置/恢复合同/MD 证据模式 |
| 新候选 | `E:\creation-reading-assistant\next-round-md-paged-chapter-fix-v1\`（开工前确认不存在） |
| 机器可读 SHA 清单 | `freeze/source-sha256.json`（before/diag/after_final 三态 + APK 冻结对） |

## 所有权与精确改动

在册七文件全部核对，最终**产品改动只落在 1 个文件 + 1 个单 hunk**（其余六个文件诊断期临时日志已全部移除，最终版与冻结输入逐字节相同）：

| 文件 | before（=final-v3 冻结） | after_final | 改动 |
|---|---|---|---|
| `feature/reader/pager/MarkdownChapterSource.kt` | `42934a08…` | `baab0fbc…` | **修复**：`loadChapter` 对整本解析章视图执行 `shiftedToChapterLocal`（块/行内 canonicalRange 减去章全局起点），使块偏移满足分页契约「相对 text」 |
| `app/build.gradle.kts` | `3a8bb6f8…` | `9ea7b5cf…` | 单 hunk：后缀 `.qahostjoint20261004v3` → `.qahostmdpagedv1`（隔离新 UID；生产 App 透传与既有 runner 不变） |
| 其余六个在册文件 | — | = before | 无改动（诊断日志零残留，`grep MdPagedSeam app/src/main` = 0） |

新增测试：`test/…/pager/MarkdownChapterSourceTest.kt`（JVM 定向回归）；`androidTest/…/acceptance/MdPagedChapterFixV1Test.kt`（设备夹具+持久断言）；`scripts/mdpc_ui.py` + `scripts/run_mdpc_diag.py`（真实 UI 旅程与批驱动，import 期零设备操作）。

## 根因证据（A：原输入失败 + 运行时确认）

诊断批（诊断版源码 + 临时日志，仅记章号/偏移/身份/顺序，无正文/密钥/用户数据）：

1. `joint-mdpaged-diag-v1`：UI 旅程首版揭显失败（分页左缘是翻页区非菜单区），失败原件保留。
2. `joint-mdpaged-diag2-v1`（候选=final-v3 源码+日志）：**完整复现**——UI 状态记录 `paged-after-第 2 章 body_first_line=第 1 章`；接缝日志：`S1 goToChapter written=377`（starts=[0,377,730,1107] 正确）→ `S2 jumpConsume j=377`（消费正常、controller 身份不变）→ `S3 open req=377 -> chIdx=1`（章查找正确）→ `S3 loadChapter chIdx=1` 之后再无任何 S4 applyChapter。
3. `joint-mdpaged-diag3-v1`（补 jobStart/mutex/chapterLoaded/CANCELLED/FAILED/close 日志）：`S3 jobStart → mutexAcquired → S3 FAILED chIdx=1 err=StringIndexOutOfBoundsException:length=353; index=382`。382 = 377（第2章全局起点）+ 5（章内偏移）——**全局与章内坐标混切**。

三个候选假设的裁决：请求写入✓、消费✓、source/controller 身份稳定✓；真因是**排版期异常**：整本解析（`MarkdownDocument.isWholeDocumentParse=true`，本夹具 parsePath=wholeDocumentText）的章视图 `canonicalText` 是章局部切片，而块/单元 `canonicalRange` 保持全书全局坐标（`MarkdownDocument.blocks` 既有约定，滚动端以 blocksGlobal 基准消费）；`MarkdownChapterSource.loadChapter` 把两者原样交给按章内坐标切片的分页器，`MarkdownPageSource.sliceRange` 直接 `substring` 越界。第1章起点=0 侥幸不炸，故只表现为"切第2章起失败"；滚动模式不走该 source，其 5 项绿不受影响也不构成证明。

## 修复（最小面）

`MarkdownChapterSource.loadChapter`：整本解析时把章视图块/行内 canonicalRange **减去**章全局起点（`shiftedToChapterLocal`），canonicalText/sourceRange 不变；流式逐章解析章视图本就是章内坐标（delta=0 原样返回）。`MarkdownChapterSource` 是该章视图进入分页引擎的唯一入口，滚动端不经过它——改动不外溢。实施中红测试先抓到一版方向错误（加法），由红→绿循环修正为减法，过程留痕于日志。

## 定向测试（修复前红 / 修复后绿）

- `MarkdownChapterSourceTest`（新增，2 用例）：锁定失配机制——各章 `loadChapter` 的块偏移必须 ∈ text 长度、段落文本必须是 text 在自身偏移处的原样切片（可逆）、首块必须从 0（全局基址不得泄漏）、文本长度与生产章节表 charCount 一致、`chapterIndexFor`/`chapterStartAbs` 与全局坐标一致。**修复前**：`整本解析的章视图…` 用例以生产同一异常失败（`StringIndexOutOfBoundsException @ test:51`，红日志 jvm-red.log）；**修复后** 2/2 绿。
- 受影响 JVM 回归（改了共享分页 source，按风险选 16 套件）：**111/111，0 失败**（jvm-final.log；含 MarkdownDocumentTest/MarkdownRenderModelTest/MarkdownScrollHighlightTest/MarkdownVisibleSourceOffsetTest/MarkdownSelectionPositionTest/TxtPagerTest/分页 controller 三套件等，覆盖滚动语义与渲染模型）。未全量重跑 303 项。

## 最终 APK

| 版本 | 宿主 SHA-256 | 测试 SHA-256 |
|---|---|---|
| 诊断版（含临时日志，仅 diag 批运行） | `ca2a933a68fcea53…`（全值见 freeze/source-sha256.json） | `448e7bf0db2ee34f…` |
| **最终版（无诊断，验收运行件）** | `eeb6b4bbf4b737adf1283e973d6494a56326edb67922511134a5c6538b593319` | `448e7bf0db2ee34f543ba3e935dfef59b902795af5fc49ba69d0a5ec6a238ed5` |

## 真实 UI 证据（B/C/D/E；全部 MuMu 1080×1920 竖屏宽布局，非实体手机）

驱动 v4 全程编排（precheck→baseline→install→postinstall→items→finally restore），真实 UI 由 `mdpc_ui.py` 从当前 XML 选择控件，无坐标盲点、无直写 DB：

| 验收项 | 批次 | 结果 | 关键证据 |
|---|---|---|---|
| B：分页目录 1→2 | final-b `joint-mdpaged-final-b-v1` | **PASS** | 持久进度 offset=377 落第2章区间 [377,730)，`chapter=1 第 2 章`，space=canonical；UI 正文首行=「第 2 章」 |
| C1：2→3 | final-b | **PASS** | offset=730、chapter=2 第 3 章；正文首行=「第 3 章」 |
| C2：3→2（settle 8s 等待不回拉） | final-b | **PASS** | offset=377、chapter=1 第 2 章；正文首行=「第 2 章」 |
| D：强停冷启动恢复 | final-b | **PASS** | force-stop→重启→重开书，进度仍 offset=377/第 2 章；正文首行=「第 2 章」 |
| E：滚动模式目录选章回归 | final-f `joint-mdpaged-final-f-v1` | **PASS** | 真实 UI 切「上下滚动」（设置→翻页与按键）后目录选章，offset=377/第 2 章，restore 通过 |
| A | diag2/diag3 | 已证 | 见上节 |

批次历史（失败原件全部保留）：final-a（配置 APK 路径笔误，precheck 拒绝）、final-c/d（引导页重置+书架空=夹具缺失，教训：分项重跑必须带 seed）、final-e（设置岛群需下滑）。最终有效验收批 = final-b（A–D+恢复）与 final-f（E+恢复）。

## 恢复与副作用（F）

- 每批 driver finally 恢复 + 对账：`restore.json` 全部 ok/verified=true、problems=[]（final-b/final-c/d/e/f 逐一核对）。
- 最终人工对账（收工）：沙箱包 `qahostmdpagedv1(.test)` 已卸载（pm list 无）；`/data/local/tmp` 无 `cra_mdpc*` 临时文件；forward/reverse 空；屏幕 1080×1920/280dpi、timeout=60000、accel=1、stay_on=0。
- 原宿主保护：各批 baseline protected 清单记录原 qahost=71 文件、原 APK 对 SHA；收工实读原 APK 对 SHA=`e82904cc…`/`fdbd0190…` 与历史值逐位一致。
- **如实登记**：收工时发现夜间模式=yes（本批各批基线/恢复账内无漂移记录，无法归因本批动作；不伪称本批前后账完整覆盖该项）——已执行 `cmd uimode night no` 恢复并复核。

## 未覆盖项

- EPUB 分页、SE4、同步等未触碰（修复面为 MD source 单文件，其路径不经过 EPUB/TXT source；未重跑其绿证据，也不据此宣称其状态变化）。
- 手机窄布局未验（MuMu 1080×1920 宽布局口径）；实体手机/MIUI/TalkBack/R8/签名发布维持既有边界。
- 流式大 MD 文件（>60k 源字符走 fromFileIndex）的设备验证未做（该路径块坐标本就是章内，JVM 层由同源测试覆盖；真机仅验了整本解析路径）。
- 全产品分母未定；本报告局部完成率（A–F 6/6、JVM 111/111）不代表全模式联合退出——永久合流与"全模式退出"仍属 Codex/集成者下一决策。

## 原始证据路径

- 候选与冻结：`E:\creation-reading-assistant\next-round-md-paged-chapter-fix-v1\`（`freeze/` APK 对与 source-sha256.json、`freeze/diag/` 诊断 APK 副本）。
- 日志：`logs/mdpaged-seam-diag2|diag3.log`（接缝）、`logs/mdpaged-full-diag3.log`（全量）、`logs/jvm-red|green|final.log`、`logs/diag-build.log`。
- 设备批：`device-mdpc-diag|diag2|diag3|final-a..f\`（summary/restore/baseline/items/raw/db-results/ui 全套；失败批原件保留未覆盖成绿）。

## 交付状态与下一步

**已交付待 Codex 核查。** 建议核查顺序：source-sha256.json 三态 SHA → diag3 接缝日志的 FAILED 行 → jvm-red/green → final-b/final-f 的 db-results 与 UI XML → 恢复对账。通过后，"MD 分页 TOC"缺口可由 Codex 记为限定闭合（绑定最终宿主 SHA eeb6b4bb…）；永久精确集成清单、全模式联合退出与 Git 操作按既定流程另批决定。本任务就此停止，不启动后续实施。
