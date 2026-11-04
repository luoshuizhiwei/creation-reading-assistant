# S05 取消读完后残余 100% 的 MuMu UI 独立验收报告

> **Codex 2026-09-28 独立复核：MuMu qahost 手机布局范围限定通过。** 已目视核对取消读完后的书架、继续阅读 Sheet、我的阅读在读页签和统计截图；与同一测试书 `100% + reading + completed_at=NULL` 的前后 DB 回读相符。E 盘 before/after DB+设置清单及 36 个既有书籍文件哈希清单逐项比较均为 0 差异；设备结束回读 1080×1920、280 dpi、夜间 no。首页“最近读完”横排逐卡、真实阅读到书末自动完成及取消后再次阅读百分比自愈均按报告 NOT RUN，不把七站限定结果外推。测试包仍安装于 qahost，原 APK 未回退，已在报告注明。

- 日期：2026-09-28
- 验收对象：`docs/coordination/legacy-stages/2026-09/2026-09-28-s05-cross-page-finished-consumers-result.md`（跨页消费者修复）与其 Codex 复核要求的设备验收
- 验收范围：qahost 隔离沙箱（`com.creationreadingassistant.qahost`），MuMu 模拟器 `emulator-5556`（独占）
- 设备口径：**仅模拟器**；不扩大为实体手机或正式书库验收。模拟器特性（root、`su` 可用）与真机可能不同
- 工件目录：`E:\creation-reading-assistant\zcode-s05-mumu-crosspage-20260928\`（98 项：截图/UI XML/DB 快照/哈希/日志）
- 纪律：未修改任何产品源码、测试源码或构建配置；未 commit/push/reset/checkout；未触碰正式书库、真实手机与原始仓库

## 一、结论

**PASS（MuMu 模拟器限定）。** 显式 finished 与取消读完后的七个展示站点全部按修复后的契约工作，且每一处都有截图/UI XML 与同一测试书的 DB `completion_state`/`percent` 前后值关联：

- 标记读完：`percent 58.96 → 100.0`、`state reading → finished`、`completed_at` 写入，位置 JSON 未动；
- 取消读完：`percent 仍为 100.0`（残余保留，**未改成 99.4**）、`state → reading`、`completed_at → NULL`，真实位置（locator_v2 offset 1080127）与 `last_read_at` 分毫未动；
- 取消后：书架归入「在读」、首页继续阅读回扫且卡片文案为「100%」而非「已读完」、退出「最近读完/已完成归档」、我的阅读归入「在读」（标签「读到 100%」）、我的页完读数 10→9、统计分布已读完 8→7 / 在读 5→6；
- 真实 finished 对照（同一本书标记读完态）七站全部仍显示完成。

## 二、构建、源码输入与安装

| 项 | 值 | 工件 |
|---|---|---|
| HEAD | `5aed5939600e491843e3c44453921bd6273a233c`（分支 `codex/recover-worktree-20260920`） | — |
| 源码输入哈希（10 个 S05 修复文件） | 见 `01-source-files-sha256.txt`（如 `BookReadiness.kt 4aaef8b7…`、`HomeViewModel.kt ebc2020a…`、`StatsRepository.kt 25c99c8d…`） | `01-source-files-sha256.txt` |
| 构建命令 | `./gradlew :app:assembleQahost :app:assembleQahostAndroidTest -PcraAndroidTestBuildType=qahost --offline --console=plain`（构建前确认 `app/build` 4 分钟无写入，窗口 1 未在构建） | `05-build.log` |
| 构建结果 | `BUILD SUCCESSFUL in 1m 20s`，`72 actionable tasks: 12 executed, 60 up-to-date`，**EXIT 0**；`dexBuilderQahost`/`packageQahost` 本轮执行，`compileQahostKotlin` 输入于 19:59 重验 | `05-build.log` |
| 宿主 APK SHA-256 | `812d7ac60984926781875736276f9b7d174bea2d4cb78f1c14e99bb53c419c81` | `06-apks.sha256` |
| 测试 APK SHA-256 | `b8b3787b2b29ca5fe77e604c20af9b32b310f7956a7420837909c064e9e189e2`（与 S12 报告**逐字节一致**，交叉印证 androidTest 输入未变） | `06-apks.sha256` |
| 安装 | 宿主/测试 APK 各 `Success`（EXIT 0）；设备侧宿主 APK 拉回哈希 `812d7ac6…` 与构建产物**一致** | `07-install-*.log`, `08-device-apk.sha256` |
| 修复代码确在包内 | 从设备 APK 提取 21 个 dex，新符号存在：`classes10.dex` 含 `isReadingFinished` + `buildContinueBooks`；`classes15.dex` 含 `buildContinueItems`；`classes7.dex` 含 `isReadingFinishedRow` | `cra_s05_dex/` |

## 三、设备基线与恢复

| 维度 | 事前基线 | 验收期 | 事后回读 | 判定 |
|---|---|---|---|---|
| wm size | `1080x1920` | `1080x1920` | `Physical size: 1080x1920` | PASS |
| wm density | `280`（Physical） | `420`（Override，手机竖屏布局） | `Physical density: 280`（`wm density reset`，无 override） | PASS |
| user_rotation | `0` | `0` | `0` | PASS |
| accelerometer_rotation | `1` | `1` | `1` | PASS |
| 夜间模式 | `Night mode: no` | 未切换 | `Night mode: no` | PASS（基线记录，未改） |
| 主 DB SHA-256 | `d7c45e04b129e85b1a6cd1cfed68e84f143629242241f6f1eb3b44a263e2d45f`（与 S12 恢复后基线一致） | 运行期变更 | 回写后 `d7c45e04…`，与基线 **diff=0** | PASS |
| shared_prefs（7 个 xml）+ files/datastore（6 个 pb） | 13 个哈希清单 | 运行期变更 | 13 个哈希与基线 **diff=0** | PASS |
| 既有书籍文件 | 36 个文件哈希清单 | 未触碰既有书 | 36 个哈希 **diff=0** | PASS |
| 测试隔离目录 | 不存在 | `files/books/00000000-0000-4000-8000-000000000012/` | **已删除**，books 目录回到 22 项；DB 中该 id 的 books/reading_progress 行 **0 行残留** | PASS |
| /sdcard 中转 | 无 | baseline/restore/dex/ui.xml/s.png/夹具 | **已全部清理**（`grep -c cra_s05` = 0） | PASS |

基线/恢复记录：`00-device-baseline.txt`、`02-qahost-db-and-prefs-before.sha256.txt`、`03-books-before.sha256.txt`、`53-…after.sha256.txt`、`54-books-after.sha256.txt`、`55-testbook-gone.txt`、`56-device-state-after.txt`。

## 四、隔离夹具

- 测试书：书名「测试 TXT」（中性名），`id = 00000000-0000-4000-8000-000000000012`。
- 播种方式：复用仓库既有 instrumentation `LegadoMarkerRealImportTemporaryTest#testProductionImportDirectlyIntoQahostDatabaseForUiAcceptance`（生产 `LegadoReadingProgressImporter.importArchive` 真实解析受控 Legado ZIP 并经 Room DAO 落库，**非直接 SQL 伪造**），运行 `OK (1 test)`、EXIT 0（`09-instrumentation-seed.log`）。
- 正文文件：本轮用 Python 生成的中性中文 TXT，`5,261,820` 字节（>5 MB 流式阈值）、169 个「第 N 章」标题；SHA-256 `5f005c939cf25f8f9468c4117373971a552fb619308f2e3d67ec98a1452c593a`（`04-test-txt.sha256`）。因该 instrumentation 硬编码 `local_content_path=…/test_s12_isolated.txt`，设备端文件名沿用该名，内容为本轮夹具。
- **夹具规范化（唯一一次 DB 直改，作用范围仅本测试书）**：夹具写入的 `content_status = "READY"` 不是应用真实导入值，而统计页口径为 `content_status == "available"`（其余站点用「非 failed/missing/downloading」），导致测试书被统计页排除在分布外。为让统计面可验，将本书 `content_status` 规范化为 `"available"`（`38-fixture-normalize.txt`）。该改动仅作用于本轮隔离测试书，随冷备份回写一并还原。

## 五、DB 前后对账（同一测试书）

| 阶段 | percent | completion_state | completed_at | current_location_json | last_read_at |
|---|---|---|---|---|---|
| 播种后（导入器落库） | 0.0 | reading | NULL | `{"legado_position_v1":{"ci":99,"co":500}}` | 2025-09-28T09:00:00Z |
| 首次打开并翻页退出 | 58.9611 | reading | NULL | `locator_v2 offset 1080127` | 2026-09-28T12:05:11.674Z |
| **标记为已读完** | **100.0** | **finished** | **1790598141174** | 未动（offset 1080127） | 未动 |
| **取消读完** | **100.0（残余保留）** | **reading** | **NULL** | 未动（offset 1080127） | 未动 |

工件：`10-db-after-seed.txt`、`14-db-after-first-read.txt`、`25-db-after-mark-finished.txt`、`42-db-after-cancel.txt`、`22-db-all-books.txt`（全库对照）。

## 六、七站逐项验收（每项：截图/XML + DB 关联）

| # | 站点 | finished 对照（DB: 100/finished/有 completed_at） | 取消读完（DB: 100/reading/NULL） | 工件 | 判定 |
|---|---|---|---|---|---|
| 1 | 书架分组与卡片 | 在读 3 / **已完成 10**；卡片「测试 · 已读完」+ 封面「读毕」印章 | 在读 4 / **已完成 9**；卡片「测试 · 第100章 · 已读 **100%**」，无「已读完」、无「读毕」印章 | `40-shelf-finished-control.*`、`43-shelf-cancelled.*` | **PASS** |
| 2 | 首页·继续阅读（行 + 管理 Sheet） | 行内不含测试书；Sheet 列表不含 | **回扫**：行内第 4 卡位「测试 TXT」；Sheet 显示「**100%**」（非「已读完」） | `23-home-row-reverse.*`（取消前 58%）、`45-continue-sheet-cancelled.*`（取消后 100%） | **PASS** |
| 3 | 首页·本周概览（最近读完计数） | 在读 5 / **已读完 8** | 在读 6 / **已读完 7** | `26-home-finished-top.*`、`44-home-cancelled.*` | **PASS** |
| 4 | 已完成归档页（首页→全部） | 「共 **10** 本完读书籍」，首行「测试 TXT · 已完读 · 2026年9月28日读完」 | 「共 **9** 本」，测试书**消失**（首行变为 zz-s05-import-A） | `29-archive-finished.*`、`29b-archive-finished-row.*`、`47-archive-cancelled.*` | **PASS** |
| 5 | 档案·我的阅读（页签与状态标签） | 全部 17 / 在读 7 / 读完 **10**；「读完」页签含测试书，标签「已读完」 | 在读 **8** / 读完 **9**；「在读」页签含测试书，标签「**读到 100%**」 | `32/33-myreading-finished*.*`、`48-myreading-cancelled.*`、`49-myreading-reading-tab.*` | **PASS** |
| 6 | 我的页·完读数 | 「**10 本** 读完」 | 「**9 本** 读完」 | `31-profile-finished.*`、`50-profile-cancelled.*` | **PASS** |
| 7 | 统计页·书籍状态分布 | 共 34 本 / 在读 5 / **已读完 8** / 未开始 17 / 不可读 4 | 共 34 本 / **在读 6** / **已读完 7** / 未开始 17 / 不可读 4 | `39-stats-finished-control.*`、`51-stats-cancelled.*` | **PASS** |
| — | 操作面板动作切换（附加） | 长按面板提供「取消已读完」 | 长按面板提供「标记为已读完」 | `24-action-sheet-before-finish.*`、`41-action-sheet-cancel.*` | **PASS** |

## 七、过程记录与诚实口径

1. **横排继续阅读行的假阴性（方法论记录）**：首页「继续阅读」是横向 LazyRow，卡片间跳转时屏幕外的卡位不产生文字节点，最初的两次 fling 滑动**跳过了**测试书所在卡位，一度误判「首页继续阅读排除了该 58% 的书」。经按评分模型（`recency*0.6 + sessions/10*0.4`，预测测试书 0.600 排第 4、位于 006 与 012 之间）反推卡位，并以反向拖拽复现后确认：**测试书始终在行内**（`23-home-row-reverse.xml` 显示「测试 TXT · 58%」）。该结论已修正，不保留「首页曾排除」的错误表述。
2. **uiautomator/路径转换坑**：Git Bash 会把 `adb shell` 参数里的 `/sdcard/...` 转成 Windows 路径（曾导致截屏写错位置、拉回陈旧 XML）。本轮改为设备路径加引号 + `MSYS_NO_PATHCONV=1`，并在每次采集前删除设备侧旧文件，避免陈旧工件混入。
3. **一次误触**：早期使用旧 dump 坐标点击「全部」误入阅读器（`29-archive-finished.*` 被覆盖过一次），随后改为「每次点击前重新 dump 取坐标」，最终归档页证据为重新采集的干净版本。
4. **真实 finished 对照的口径**：对照组的「finished」来自 UI「标记为已读完」（`markFinished`，percent→100 + finished），并非驱动真实阅读到书末由 `mergeReaderProgress` 自动写 finished。两者在展示契约上同为显式 `finished`，但**自动完成路径未在设备上单独驱动**（见第八节）。

## 八、未验 / NOT RUN

| 项 | 状态 | 说明 |
|---|---|---|
| 真实读到书末由 merge 自动置 finished 的路径 | **NOT RUN** | 需驱动 169 章分页到最后一页；本次对照用显式「标记为已读完」，DB 终态同为 `finished`（JVM 侧已锁 merge 契约） |
| 首页「已阅读完成」横排卡片的逐卡目视 | **NOT RUN（卡级）** | 该区计数（8→7）与归档页卡片级（10→9，含「2026年9月28日读完」行）已验证；横排卡位未逐张目视 |
| 统计页非相关区块（人物拓扑/足迹/趋势） | 未验（范围外） | 本轮只验书籍状态分布 |
| 实体手机 | **NOT RUN** | 本报告限定 MuMu `emulator-5556`；模拟器 root/权限与真机不同 |
| 夜间模式下的上述页面 | 未验 | 夜间模式基线为 `no`，本轮未切换主题 |
| 取消读完后的再次真实阅读自愈（percent 回落） | **NOT RUN（UI）** | DB 语义已由 JVM 锁定（下一次 saveProgress 由 locator 重算）；设备端未再打开阅读器验证回落 |

## 九、恢复与收尾

- 应用 force-stop 后删除测试书目录、回写冷备份（DB + 7 shared_prefs + 6 datastore），属主/权限恢复为 `u0_a70:u0_a70` / `660`（DB）。
- 回读：DB 与 13 个设置文件哈希**与基线逐字节一致（diff=0）**；36 个既有书籍文件 **diff=0**；DB 中测试书 books/progress 行 **0 残留**。
- 设备参数：size/density/rotation/accelerometer/夜间模式全部回读为基线值；`/sdcard` 中转目录已清理。
- 安装状态说明（非夹具）：qahost 宿主 APK 现为本次构建 `812d7ac6…`（含 S05 修复），测试 APK 与 S12 相同 `b8b3787b…`；两包均保留安装（沿用历次会话惯例），应用当前处于停止状态。未回退为旧版 APK；如需回退请由总指挥裁决。

**最终判定：PASS（限定 MuMu `emulator-5556` 模拟器 / qahost 隔离沙箱；七站截图与 UI XML 齐全，DB 前后对账一致，基线逐字节恢复）。**
