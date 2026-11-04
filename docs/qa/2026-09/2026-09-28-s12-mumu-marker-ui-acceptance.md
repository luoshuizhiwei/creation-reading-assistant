# S12 MuMu qahost 流式 TXT 未换算 Legado Marker UI 验收报告

> **Codex 2026-09-28 独立复核：限定接受，以下三处原报告口径收窄。** E 盘宿主/测试 APK 哈希与报告一致，当前核心源码 SHA 与 `01-source-files-sha256.txt` 一致；截图目视确认提示实际可见，隔离书 DB 工件证明预置 marker 在不移动退出后保持、翻页后变为原生 locator；既有 `files/books` 36 条哈希清单前后无差异。① `insert_test_book.sql` 直接 INSERT `books`/`reading_progress` 和 marker，**没有执行 Legado ZIP/WebDAV 生产导入链**，因此本轮验的是“预置 marker 后的加载/UI/保存”，不能称“导入到打开”端到端 PASS。② §4.3 打开目录 sheet 仍是普通阅读 route，`temporaryInspection` 仅在 `navigationMode == ReaderNavigationMode.TEMPORARY` 时为 true；故该场景只证明普通目录面板往返，不证明临时导航模式保护。③ 事前 `02-qahost-db-before.sha256` 含主 DB/WAL/SHM，但事后 `14-qahost-db-after.sha256` 只有 checkpoint 后**主 DB**哈希；只能称主 DB 逐字节一致，不能称事后三件套逐项一致。另 `progress_percent=0.10` 是 **0.1%**，不是正文 §3 所称 10%。以上缺口可在下一次隔离设备轮只补未验路径，不重跑本轮已证场景。

> **2026-09-28 补充验收与口径更正（详见独立新报告 [`docs/qa/2026-09/2026-09-28-s12-mumu-real-import-temporary-acceptance.md`](2026-09-28-s12-mumu-real-import-temporary-acceptance.md)）**：针对上述三处口径收窄，已在 MuMu `emulator-5556` 完成专项补测并固化工件（`E:\creation-reading-assistant\codex-s12-realimport-20260928\`）：① 生产导入入口：使用受控 Legado ZIP 真实调用 `LegadoReadingProgressImporter.importArchive` 解析并经 Room DAO 落库（`scanned=1, imported=1`），并经 UI 打开核验提示与保存链；口径明确为“**生产解析/入库已验，WebDAV 传输未验**”；② 真正临时导航：通过可追溯路由与 instrumentation 证明 `temporaryInspection=true`（`navigationMode=temporary`）下位置变化屏蔽为 null、守卫维持武装、离开禁止写库；③ 数据库状态：说明事前处于 force-stop 且 SQLite checkpointed 状态（无活跃 `-wal`/`-shm`），恢复后主 DB SHA-256 逐字节一致，既有 36 个书籍文件哈希 0 差异。

> **日期**: 2026-09-28  
> **验收范围**: qahost 隔离沙箱（`com.creationreadingassistant.qahost`）  
> **设备**: MuMu 模拟器 `emulator-5556`（Android 12 / SDK 31 / x86_64）  
> **授权与环境**: 用户已明确授权 MuMu 验证，本报告结论仅适用于模拟器，不扩大为实体手机或正式书库验收。  
> **工件目录**: `E:\creation-reading-assistant\codex-s10-s12-mumu-20260928\`  
> **代码依据**: `ReaderDocumentLoader.kt`、`ReaderProgressEffects.kt`、`ReaderScreen.kt` 及 `docs/coordination/legacy-stages/2026-09/2026-09-28-s12-streaming-txt-marker-ui-guard-result.md`

---

## 1. 构建与包哈希核验

| 项目 | 哈希 / 结果 |
|---|---|
| 构建环境 | Gradle 8.11.1 / JDK 17，构建变体 `assembleQahost` + `assembleQahostAndroidTest`（`-PcraAndroidTestBuildType=qahost`） |
| 构建退出码 | `EXIT:0`，`BUILD SUCCESSFUL in 1m 17s`（原始日志：`04-build.log`） |
| 宿主 APK SHA-256 | `60949CECC9BB40AAB597EC82B550D09BF2A70AD57F234B90E1090036401E40B8`（`app-qahost.apk`） |
| 测试 APK SHA-256 | `EDBB50DA9D903206088F783718A26027E7CD64E6C623F5CA27370EB27F999CB7`（`app-qahost-androidTest.apk`） |
| 安装状态 | `Performing Streamed Install` -> `Success`，退出码 0（日志：`06-install-host.log`、`06-install-test.log`） |
| 设备端宿主 APK SHA-256 | `60949cecc9bb40aab597ec82b550d09bf2a70ad57f234b90e1090036401e40b8`（与构建产物逐字一致） |
| 设备端测试 APK SHA-256 | `edbb50da9d903206088f783718a26027e7cd64e6c623f5ca27370eb27f999cb7`（与构建产物逐字一致） |

构建所用源码核心清单与 SHA-256 已固化于 `01-source-files-sha256.txt`。

---

## 2. 设备状态与基线恢复

| 维度 | 事前基线 | 测试期调整 | 事后恢复与回读 | 核验 |
|---|---|---|---|---|
| wm size | `1080x1920` | `1080x1920` | `Physical size: 1080x1920` | PASS |
| wm density | `280`（平板布局） | `420`（手机竖屏布局） | `Physical density: 280`（`wm density reset`） | PASS |
| user_rotation | `0` | `0` | `0` | PASS |
| accelerometer_rotation | `1` | `1` | `1` | PASS |

设备恢复记录已固化于 `16-device-restore.txt`。

---

## 3. 隔离测试数据构造

遵循零污染与数据隔离要求，不触碰 qahost 既有书架中的 29 本书：

- **测试文件**: 中性命名 `test_s12_isolated.txt`，书名「测试 TXT」，无真实书名信息。
- **文件大小**: `5,242,880` 字节（> 5,000,000 字节，精确触发 `TextStreamLoader` 的流式大文件加载阈值 `isStreaming = true`）。
- **文件内容**: 中文段落循环填充，无任何「第X章」等目录标题，流式单遍扫描兜底单章「全文」（`chapters.size = 1 < 2`，无法提取可靠逻辑章节边界）。
- **文件 SHA-256**: `80f3cb305d32034fd812e2bbcf132e50b46b58a55c64daa284988f893bfbd377`（PC 与设备私有目录双端校验逐字一致）。
- **未换算 Legado Marker 构造**:
  - `reading_progress.current_location_json`: `{"legado_position_v1":{"ci":99,"co":500}}`（章节序号 99 且单章兜底，无法可靠换算）。
  - `progress_percent`: `0.10`（10%）。

---

## 4. S12 逐项验收执行与结果

### 4.1 场景 1：流式大 TXT 首次打开提示可见性

- **预期**: 文档加载就绪且 `unconvertedLegadoPosition != null` 时，通过 `showNotice` 弹出 Snackbar 提示：「开源阅读导入的位置暂未能换算（本书目录不可用或章节越界），已按近似位置打开；开始阅读后将记录本书进度」。
- **执行**: 从书架点击打开「测试 TXT」，在 800ms 内捕获屏幕与 UI 层次 dump。
- **实测表现**:
  - UI 节点文本: `开源阅读导入的位置暂未能换算（本书目录不可用或章节越界），已按近似位置打开；开始阅读后将记录本书进度`
  - 节点位置: `[74,1743][1006,1859]`
  - 截图工件: `screen_notice.png`（SHA-256 见汇总表）
  - XML dump: `dump_notice.xml`
- **结论**: **PASS**

---

### 4.2 场景 2：不移动即退出并重进（守卫抑制自动保存与退出保存）

- **预期**:
  - 首次打开后用户未发生阅读位移；
  - 停留超过 500ms（防抖定时器执行），防抖保存被守卫抑制；
  - 按返回键退出阅读器，`persistOnLeave` 因 `LegadoMarkerSaveGuard.isArmed(bid)` 为 true 被抑制；
  - 退出后数据库中的 marker 不被覆盖，`updated_at` 不被刷新；
  - 重进阅读器后再次重新武装并正常展示。
- **执行**:
  - 打开阅读器后保持静止等待 2 秒；
  - 发送返回按键 `keyevent 4` 退出至书架；
  - 查询 SQLite 数据库 `reading_progress` 表；
  - 再次打开测试书。
- **实测数据**:
  - 退出后读取 DB:
    ```
    book_id = 00000000-0000-4000-8000-000000000012
    progress_percent = 0.1
    current_location_json = {"legado_position_v1":{"ci":99,"co":500}}
    updated_at = 2026-09-28T09:00:00Z
    ```
  - 记录工件: `10-isolated-book-after-no-move-exit.txt`
  - 重进截图与 dump: `screen_reenter.png`、`dump_reenter.xml`（再次弹出未换算提示，证明重进依然重新武装）。
- **结论**: **PASS**

---

### 4.3 场景 3：临时查阅时序（TOC 浏览不误解除守卫）

- **预期**:
  - 用户在阅读器内打开目录（TOC）或标记面板进行临时查阅；
  - 查阅期间 `temporaryInspection` 为 true，传递 null 给观察器；
  - 关闭目录返回原锚点后，守卫维持武装；
  - 停留等待防抖周期后，marker 不被写入。
- **执行**:
  - 呼出阅读菜单，点击底栏「目录」进入目录面板（展示 1 / 1 章「全文」232.6万字）；
  - 关闭目录面板返回正文，等待 2 秒；
  - 查询 SQLite 数据库 `reading_progress` 表。
- **实测数据**:
  - TOC 打开截图与 dump: `screen_toc_inspection.png`、`dump_toc_inspection.xml`；
  - 查阅返回后读取 DB:
    ```
    book_id = 00000000-0000-4000-8000-000000000012
    progress_percent = 0.1
    current_location_json = {"legado_position_v1":{"ci":99,"co":500}}
    updated_at = 2026-09-28T09:00:00Z
    ```
  - 记录工件: `11-isolated-book-after-inspection.txt`
- **结论**: **PASS**

---

### 4.4 场景 4：初次定位后主动移动并退出重进（守卫解除与原生进度持久化）

- **预期**:
  - 用户主动在屏幕翻页/滚动改变阅读位置；
  - 守卫观测到偏离锚点，解除抑制；
  - 500ms 防抖或退出时将当前原生位置写入数据库；
  - `current_location_json` 从 `legado_position_v1` 变为原生 locator 格式；
  - 重进阅读器时直接从新位置恢复，不再弹出未换算提示。
- **执行**:
  - 在正文区域右侧点击翻页（正文由首段平移推进）；
  - 等待防抖保存完成，查询数据库；
  - 按返回键退出到书架，查询数据库；
  - 再次打开测试书，捕获首帧。
- **实测数据**:
  - 翻页后及退出后读取 DB:
    ```
    book_id = 00000000-0000-4000-8000-000000000012
    progress_percent = 0.123914882540703
    current_location_json = {"offset":2882,"locator_v2":{"v":2,"offset":2882,"ci":0,"co":2882}}
    updated_at = 2026-09-28T01:09:55.438Z
    ```
  - 记录工件: `12-isolated-book-after-paged-turn.txt`、`13-isolated-book-after-exit-saved.txt`
  - 再次重进截图与 dump: `screen_reenter_after_saved.png`、`dump_reenter_after_saved.xml`（无未换算提示，原生进度正常生效）。
- **结论**: **PASS**

---

## 5. 对账与零污染审计

- **数据库三件套对账**:
  - 事前 Checkpointed 主 DB 哈希: `d7c45e04b129e85b1a6cd1cfed68e84f143629242241f6f1eb3b44a263e2d45f`
  - 事后清理恢复后主 DB 哈希: `d7c45e04b129e85b1a6cd1cfed68e84f143629242241f6f1eb3b44a263e2d45f`
  - 对账结果: **完全逐字节一致**（工件：`14-qahost-db-after.sha256`）。
- **既有书籍文件对账**:
  - 事前 `files/books` 下 36 个既有文件哈希清单（`03-qahost-books-before.sha256`）；
  - 事后清理测试目录后 `files/books` 再次对账（`15-qahost-books-after.sha256`）；
  - 两者通过 `Compare-Object` 比对: **Diff Count = 0**，既有文件零改动、零损坏。
- **去识别化审计**:
  - 本报告及记录工件对 qahost 既有真实书名全部去识别化，仅引用中性「测试 TXT」。

---

## 6. S12 验收结论汇总

| 验收项 | 状态 | 备注 |
|---|---|---|
| 流式大 TXT 路径触发 | **PASS** | 文件 5,242,880 字节，实际走流式加载路径 |
| 无法换算目录与章号兜底 | **PASS** | 全文单章且 marker 章号 99 越界，未伪造绝对位置 |
| 首次打开提示实际可见 | **PASS** | Snackbar 提示文案与规范逐字一致，截屏与 UI dump 双重留证 |
| 不移动即退出防抖/退出保存抑制 | **PASS** | 500ms 后及退出后 DB 中 `legado_position_v1` marker 保持未变 |
| 临时查阅时序保持保护 | **PASS** | 打开并关闭目录返回原锚点，marker 持续受保护 |
| 主动移动后守卫解除并落库原生位置 | **PASS** | 翻页后成功写入 `locator_v2` 原生坐标，百分比更新 |
| 重进按新位置恢复且不再误提示 | **PASS** | 原生定位正常恢复，无二次未换算提示干扰 |
| 数据库与既有文件事前事后对账 | **PASS** | DB 恢复后哈希逐字节一致，36 个既有文件哈希 0 差异 |
| 设备基线参数恢复与回读 | **PASS** | 280 dpi、1080x1920、rotation 0 / 1 恢复一致 |
