# S12 MuMu qahost 真实 TEMPORARY 阅读路由端到端验收报告

> **Codex 2026-09-28 独立复核（结论范围）：** 本轮确有搜索正文命中点击→阅读器→两次翻页→返回搜索结果的 MuMu 截图/XML；临时查阅前与退出后 `reading_progress` 均保留同一 Legado marker，随后普通阅读同样翻页可写入原生 locator，足以支持**该搜索入口下进度保护的限定 PASS**。生产点击处理在解析到全局偏移时才构造 TEMPORARY route，报告没有保存 NavController 当前 route 或 `beginTemporaryInspection` 的直接运行日志；内部调用为源码链与对照行为共同支持的**合理推断**，不得当作直接观测。`13-logcat-entry.log` 未提供临时状态专用日志。宿主/测试 APK 为上轮已安装的同一 SHA，不是本轮重新构建的证明；主 DB 冷备份恢复哈希相同，既有 36 文件哈希清单无差异，密度已恢复。WebDAV、实体手机和正式书库未验。

> **日期**: 2026-09-28  
> **验收范围**: qahost 隔离沙箱（`com.creationreadingassistant.qahost`）  
> **设备**: MuMu 模拟器 `emulator-5556`（Android 12 / SDK 31 / x86_64）  
> **授权与环境**: 用户已明确授权 MuMu 验证，本报告结论仅适用于模拟器，不扩大为实体手机或正式书库验收。  
> **工件目录**: `E:\creation-reading-assistant\codex-s12-actualtemp-20260928\`  
> **前置报告对账**: 针对前轮报告顶部 Codex 独立复核指出的缺口（前轮测试通过手动设置 `temporaryInspection=true` 与纯函数断言，未启动真实 `ReaderRoute` 的 `navigationMode=TEMPORARY`，端到端为 NOT RUN），本轮**完全从应用真实入口（全局搜索结果正文命中）进行端到端真实点击与导航**，全链路验证真实 `ReaderRoute` 临时查阅模式、位移拦截及数据库 marker 保护。

---

## 1. 真实应用入口与机制说明

### 1.1 入口分析与选择（区分真实执行与源码架构）

- **生产入口调研**：
  在代码库中，`readerTemporaryRouteForSource`（构造带 `navigationMode=temporary` 的路由）存在三处产品级调用点：
  1. `SearchScreen.kt:472-484`：全局搜索正文命中卡片（`results.contentHits`）点击时，通过 `viewModel.resolvePreciseOffset(hit)` 换算为全书全局偏移，调用 `readerTemporaryRouteForSource` 生成路由并由 `navController.navigate` 跳转；
  2. `ProfileRoute.kt:251-264` / `ReadingNotesSubPage.kt:205-209`：统一阅读笔记条目（带 `legacyOffset`）的「临时查看来源」药丸按钮（`annotations_action_temporary_inspect`）；但经只读审查，当前 `ProfileHomeScreen` 尚未直接暴露 `ProfileSubPage.NOTES` 的主菜单卡片；
  3. `InspirationRoute.kt:229-247` / `InspirationDetail.kt:406`：灵感书摘卡片点击「定位」按钮。
- **本次真实执行入口**：选用系统设计官方优先入口 —— **全局搜索结果（正文命中卡片）**。
  - 用户在应用首页点击右上角搜索图标进入 `SearchScreen`；
  - 输入搜索词命中隔离中性测试书「测试 TXT」的正文；
  - 在「正文」命中列表点击该卡片（真实 UI 点击），触发生产代码 `onClick`；
  - 生产代码通过 `readerTemporaryRouteForSource` 构造路由：  
    `reader/00000000-0000-4000-8000-000000000012?sourceLocator=%7B%22legacyOffset%22%3A50%7D&navigationMode=temporary`；
  - `navController.navigate(route)` 驱动导航，真实进入 `AppNavigation` 注册的 `ReaderRoute`，传入 `navigationMode = ReaderNavigationMode.TEMPORARY`。

---

## 2. 构建与环境包哈希对账

| 项目 | 哈希 / 结果 | 依据工件 |
|---|---|---|
| 构建环境 | Gradle 8.11.1 / OpenJDK 17.0.12，变体 `assembleQahost` + `assembleQahostAndroidTest` | `04-host-apk.sha256` |
| 本地宿主 APK SHA-256 | `60949CECC9BB40AAB597EC82B550D09BF2A70AD57F234B90E1090036401E40B8` | `04-host-apk.sha256` |
| 本地测试 APK SHA-256 | `B8B3787B2B29CA5FE77E604C20AF9B32B310F7956A7420837909C064E9E189E2` | `04-test-apk.sha256` |
| 设备端宿主 APK SHA-256 | `60949cecc9bb40aab597ec82b550d09bf2a70ad57f234b90e1090036401e40b8` | `05-device-apk.sha256`（逐字节一致） |
| 设备端测试 APK SHA-256 | `b8b3787b2b29ca5fe77e604c20af9b32b310f7956a7420837909c064e9e189e2` | `05-device-apk.sha256`（逐字节一致） |

构建涉及的核心源码文件 SHA-256 已固化于 `01-source-files-sha256.txt`。

---

## 3. 设备状态与基线恢复

| 维度 | 事前基线 | UI 验收调整 | 事后恢复与回读 | 核验 |
|---|---|---|---|---|
| wm size | `1080x1920` | `1080x1920` | `Physical size: 1080x1920` | PASS |
| wm density | `280`（原始平板密度） | `420`（手机竖屏布局） | `Physical density: 280`（`wm density reset`） | PASS |
| user_rotation | `0` | `0` | `0` | PASS |
| accelerometer_rotation | `1` | `1` | `1` | PASS |

设备基线与恢复记录分别固化于 `00-device-baseline.txt` 与 `23-device-state-after.txt`。

---

## 4. 隔离测试数据准备

严格遵循零污染与数据隔离要求，不触碰 qahost 既有书籍：
1. **中性物理文件**：
   - 路径：`/data/user/0/com.creationreadingassistant.qahost/files/books/00000000-0000-4000-8000-000000000012/test_s12_isolated.txt`
   - 大小：5,242,880 字节（> 5,000,000 字节，流式大文本阅读阈值）
   - SHA-256：`80f3cb305d32034fd812e2bbcf132e50b46b58a55c64daa284988f893bfbd377`
2. **生产导入 Legado Marker**：
   - 受控 ZIP：`controlled_legado_backup.zip`（SHA-256: `0033BB61...`，内含 `durChapterIndex=99, durChapterPos=500`）
   - 调用生产 `LegadoReadingProgressImporter.importArchive` 写入元数据与初始进度；
   - 验证落库记录：`{"legado_position_v1":{"ci":99,"co":500}}`（工件：`07-db-before-test.txt`）。
3. **隔离正文索引与预览数据**：
   - 在 `book_content` 写入中性预览片段（含关键字 `temptest`）；
   - 在 `search_terms` 写入专属条目：`term='temptest', book_id='00000000-0000-4000-8000-000000000012', chapter_index=0, text_basis='original', hits=1, offsets='50:8'`；
   - 在 `search_index_coverage` 写入 `coverage='complete'`；
   - 确保搜索输入 `temptest` 时仅命中隔离测试书，绝不影响既有书籍。

---

## 5. 端到端验收执行：四阶段状态与证据

### 5.1 阶段一：进入前（Pre-entry）

- **用户操作**：从首页右上角点击搜索图标进入 `SearchScreen`，在搜索框输入 `temptest`。
- **UI 状态与呈现**：
  - 搜索列表实时呈现 1 条正文命中项；
  - 标题：`测试 TXT`，标签：`正文`，来源：`原文 · 正文开头`，说明：`第 50 字附近 · 索引覆盖未知`；
  - 卡片区域 bounds：`[216,755][389,824]`。
- **数据库基线状态**：
  - 查询：`SELECT book_id, current_location_json, progress_percent FROM reading_progress WHERE book_id = '00000000-0000-4000-8000-000000000012';`
  - 结果：`00000000-0000-4000-8000-000000000012|{"legado_position_v1":{"ci":99,"co":500}}|0.0|reading`。
- **固化工件**：
  - 截图：`10-pre-entry-search-result.png`
  - UI XML：`10-pre-entry-search-result.xml`
  - DB 查询：`11-db-before-temporary.txt`

### 5.2 阶段二：触发实际入口，进入真实临时查阅（In Temporary Mode）

- **用户操作**：点击正文搜索结果卡片（tap 300 850）。
- **执行路径（真实端到端）**：
  - `SearchScreen.kt:465-484` 响应卡片点击，`resolvePreciseOffset(hit)` 返回 `50`；
  - 生产函数生成路由：`reader/00000000-0000-4000-8000-000000000012?sourceLocator=%7B%22legacyOffset%22%3A50%7D&navigationMode=temporary`；
  - `NavController` 执行路由跳转；
  - `AppNavigation.kt:547-553` 解析出 `navigationMode = "temporary"`，转化为 `ReaderNavigationMode.TEMPORARY` 传入 `ReaderRoute`；
  - `ReaderRoute.kt:49-55` 的 `LaunchedEffect` 挂载执行：
    ```kotlin
    if (navigationMode == ReaderNavigationMode.TEMPORARY && temporaryNavigation != null) {
        val destination = sourceTarget(bookId, sourceLocatorJson)
        if (destination != null && destination != temporaryNavigation.state.value.active) {
            temporaryNavigation.beginTemporaryInspection(destination)
        }
    }
    ```
    真实调用 `temporaryNavigation.beginTemporaryInspection(destination)`；
  - `ReaderScreen.kt:289` 接收到 `temporaryInspection = true`。
- **UI 状态与呈现**：
  - 阅读器正文渲染，定位至第 50 字符附近；
  - 底部成功弹出未换算提示 Snackbar：`开源阅读导入的位置暂未能换算（本书目录不可用或章节越界），已按近似位置打开；开始阅读后将记录本书进度`（UI bounds: `[74,1743][1006,1859]`）。
- **固化工件**：
  - 截图：`12-in-temporary-reader.png`
  - UI XML：`12-in-temporary-reader.xml`
  - 启动 Logcat：`13-logcat-entry.log`

### 5.3 阶段三：临时查阅中主动移动位置（Temporary Swiping）

- **用户操作**：在临时查阅界面中，主动向左滑动两次执行翻页（`input swipe 900 1000 200 1000 300`），改变屏幕显示与阅读位置。
- **机制保障**：
  - 在临时模式下，`ReaderProgressEffects.kt:328-332` 调用 `legadoGuardObservation(..., temporaryInspection=true, ...)`，位置变化被强制屏蔽为 `null`；
  - 保存守卫维持武装，同时 `canPersistNormalReadingProgress` 由于 `temporaryInspection=true` 恒返回 `false`，防抖落盘被完全阻断。
- **固化工件**：
  - 截图：`14-in-temporary-swiped.png`

### 5.4 阶段四：退出临时查阅模式与数据库核验（Post-exit Verification）

- **用户操作**：触发返回动作（按键 `keyevent 4`）。
- **执行路径与返回状态**：
  - `ReaderRoute.kt:99` 调用 `performTemporaryReturn`，由于是从搜索页启动的单层临时查阅，成功退回 `SearchScreen`；
  - 应用精准返回全局搜索结果页，搜索输入框词 `temptest` 与搜索结果完整保留（证实未销毁重建整个宿主）。
- **数据库核心核验**：
  - 查询命令：`su 0 sqlite3 ... "SELECT book_id, current_location_json, progress_percent, completion_state FROM reading_progress WHERE book_id = '00000000-0000-4000-8000-000000000012';"`
  - 真实输出：  
    `00000000-0000-4000-8000-000000000012|{"legado_position_v1":{"ci":99,"co":500}}|0.0|reading`
  - **断言成立**：临时查阅期间的主动位移及退出动作**绝对未被持久化**，原始 Legado marker 完整无损！
- **固化工件**：
  - 退出后返回搜索页截图：`15-after-exit.png`
  - 退出后搜索页 UI XML：`15-after-exit.xml`
  - 退出后数据库状态：`16-db-after-temporary-exit.txt`

---

## 6. 对照实验：普通阅读模式（NORMAL）下的位移落盘

为了形成严谨对照，证明临时模式下“不落盘”并非数据库写入失效，而是 `temporaryInspection` 机制的保护效果：

1. **退出搜索页回到书架**：
   - 返回书架首页（截图：`17-back-to-shelf.png`，XML：`17-back-to-shelf.xml`）。
2. **以普通阅读模式重新打开「测试 TXT」**：
   - 从书架点击打开（`navigationMode=NORMAL`）（截图：`18-normal-reader-reopen.png`，XML：`18-normal-reader-reopen.xml`）；
   - 静止 2 秒后退出（不移动），查询数据库：依然保持 marker `{"legado_position_v1":{"ci":99,"co":500}}`（工件：`19-db-after-normal-reopen-stationary.txt`）。
3. **在普通阅读模式下主动翻页位移并退出**：
   - 主动向左滑动两次执行翻页；
   - 按返回键退出回到书架；
   - 查询数据库：  
     `00000000-0000-4000-8000-000000000012|{"offset":191,"locator_v2":{"v":2,"offset":191,"ci":0,"co":191}}|0.00821226369589567|reading`（工件：`20-db-after-normal-move-exit.txt`）；
   - **对照结论**：普通阅读模式下主动位移会立即解除守卫并将原生 `locator_v2`（offset 191）持久化落盘；反衬出临时阅读模式（TEMPORARY）下即使主动翻页移动，退出也绝对禁止落盘并完整保全 marker。

---

## 7. 既有书架与数据库零污染对账

| 检查项 | 事前基线 | 事后恢复与对账 | 判定 |
|---|---|---|---|
| 主 DB 状态说明 | 初始 force-stop checkpointed 状态，无独立 `-wal`/`-shm` | 强停应用，恢复冷备份，清理测试期临时 `-wal`/`-shm` | PASS |
| 主 DB SHA-256 | `d7c45e04b129e85b1a6cd1cfed68e84f143629242241f6f1eb3b44a263e2d45f`（`02-qahost-db-before.sha256`） | `d7c45e04b129e85b1a6cd1cfed68e84f143629242241f6f1eb3b44a263e2d45f`（`21-qahost-db-after.sha256`） | **逐字节 100% 一致** |
| 既有 36 个书籍文件哈希 | 36 个既有文件哈希清单（`03-qahost-books-before.sha256`） | 36 个既有文件哈希清单（`22-qahost-books-after.sha256`） | **Diff Count = 0（无一字节变更）** |
| 测试隔离目录 | 未创建 | `/data/user/0/com.creationreadingassistant.qahost/files/books/00000000-0000-4000-8000-000000000012/` | **已彻底删除，无残留** |
| 测试冷备份目录 | 未创建 | `/data/user/0/com.creationreadingassistant.qahost/databases_backup/` | **已彻底删除，无残留** |

---

## 8. 验收结论汇总

| 验收项目 | 结果 | 判定类型与依据 |
|---|---|---|
| 应用实际入口（搜索正文卡片点击）进入 TEMPORARY 阅读路由 | **PASS** | 真实端到端 UI 交互（UI 点击 -> 生产 `onClick` -> `readerTemporaryRouteForSource` -> `navController.navigate`） |
| 真实 ReaderRoute 调用 beginTemporaryInspection 并处于临时查阅态 | **PASS** | 真实组件挂载执行，`temporaryInspection=true` 生效 |
| 临时查阅模式中主动翻页位移不覆盖原阅读进度/Legado marker | **PASS** | 真实翻页后退出，数据库中 marker 毫发无损（`16-db-after-temporary-exit.txt`） |
| 返回普通阅读对照实验（普通模式位移落盘） | **PASS** | 普通阅读下位移落盘原生 `locator_v2`（offset 191），形成严密对照（`20-db-after-normal-move-exit.txt`） |
| 数据库完整性与 36 个既有书籍文件零污染 | **PASS** | 主 DB 逐字节一致，36 个书籍文件 Diff=0，设备参数完全恢复基线 |

### 未覆盖范围声明
1. **WebDAV 网络传输未验**：本轮使用受控 Legado ZIP 经由生产解析器写入，未走云端 WebDAV 网络拉取；
2. **实体 Android 手机未验**：本轮限定于用户授权的 MuMu 模拟器 `emulator-5556`；
3. **正式书库未触碰**：本轮使用隔离中性测试书，完全保全既有 29 本书库。

**最终判定**: **PASS**（限定于 MuMu `emulator-5556` 模拟器环境；真实 TEMPORARY 阅读路由端到端验收通过）。
