# 联合验收仪器测试源码补齐（不运行设备）

- 任务编号/版本：`CRA-JOINT-ACCEPTANCE-TESTS-v1`，2026-10-04 指定，实际执行 2026-10-04T17:20–18:05+08:00，应用 ZCode / GLM（model: new-provider/glm-5.2）。
- 输入指纹：HEAD `e39a78da9c62f02614207cffb36969a32492eaea`（开工实读一致，暂存区 0 条，D 工作树 496 脏 WIP 路径保持原样）。只读输入三个候选：`sync-md-join-v1`（SE4 合流版 ReaderProgressEffects 核对）、`rollback-wip-reconcile-v2`（编译核对基树）、`root-md-render-carrier-fix`（隔离宿主基建与依赖核对）；字节复用来源为 Root MD 设备副本 `…\next-round-md-true-chapter-accept-v1\device\android\…\acceptance\MdTrueChapterAcceptV1Test.kt`。
- 所有权与改动：写入仅 `E:\creation-reading-assistant\next-round-joint-acceptance-tests-v1`（payload 恰 3 个 androidTest 源文件、编译核对树、TESTING-NOTES.md、freeze/evidence、execution.json）与唯一报告 `docs/coordination/dispatches/2026-10/2026-10-04-next-joint-acceptance-tests-v1.md`。产品源码、公共配置、已有 runner、其他候选零改动；无设备；无 Git 操作。

## 1. 已验 MdTrueChapterAcceptV1Test 字节复用

- 复用源 = Root MD 批次实际构建进测试 APK（`21489a7c…`）的 device 副本同名文件；逐字节一致（复用前后 SHA 均 `991aa720d81ce892f67bb3cb534a1e2047c12a8f53e36d860ad277adbb1d1ab7`）。
- 核对结论：包名 `com.creationreadingassistant.acceptance`；依赖仅 `androidx.test.{core,ext.junit,platform}`、`androidx.room.Room`、`org.json`、kotlinx-coroutines——全部为 androidTest 既有依赖（`app/build.gradle.kts` L243–248 已具备），无新增库需求；7 方法（00 夹具 / 01 生产章节表 / 10 真实保存 / 11 冷读 / 20 离章 / 30 回源 / 90 终态）与 Root MD 报告 7/7 一致；夹具书 id `mdtc-` 前缀、`mdtc_00` 内置 `cleanRows` 清理。
- **不绑定旧 APK/旧通过**：旧测试 APK 与其 7/7 设备通过不迁移到新候选；新候选必须重新构建、重新运行，运行前一律 NOT RUN。

## 2. 新增 EPUB 同 URI V1→V2 仪器测试（`EpubReplaceFreshSourceAcceptTest`）

- `jera_10_replaceSameUriReadsFreshV2Bytes`：隔离宿主私有 filesDir 写真实 EPUB（ZIP 结构）V1 → **生产** `openEpub` 导入（真实 Parser + 真实 Room 登记，零直写）→ 同一 URI 覆盖 V2 字节 → **生产** `replaceStoredEpub` 替换。断言：内部副本字节含 V2 标记、不含 V1 标记、逐字节确实改变（no-op 即失败）；Room 记录由生产代码更新（标题、`content_hash` = 新内部副本 MD5、id 不变、`local_content_path` 指向派生 target）；`.importing`/`.repairing`/`.backup` 零残留。
- `jera_20_unreadableSourceFailsAndKeepsOldCopy`：同 URI 来源消失后替换必须失败；旧 target 逐字节保留；DB 行与替换前快照逐字段一致（零写）；临时文件零残留。
- 不 mock 生产替换或 Parser、不直写 DB 冒充成功；`FileOps` 用测试自有直通实现（生产 `DefaultFileOps` 为 private）。数据全部 `jera-src/` 与生产派生 id，@After 精确清理。
- **绑定要求**：`jera_10` 在未合入 fresh-source 修复的树上会 FAIL（替换退化为 V1→V1 no-op），`jera_20` 同样 FAIL（旧 Parser 早退分支不读来源）——该红是 v3 绑定判据，不是缺陷。**最终运行必须绑定 EPUB v3。**

## 3. ReaderProgressEffects key/pending 时序最小补验（`ReaderAnnotationPendingConsumptionTest`）

- 方式：Compose 仪器环境**真实组合**生产 `ReaderProgressEffects`（internal，androidTest friend 可见，编译核对已验证），注入记录 lambda 与内存 state 驱动时序；断言 effect 的实际执行结果，**不是源码检查**。
- `se4_10`：`pendingHighlightId` 在 `isLoading=true` 时就位 → 文档就绪（isLoading→false）后，SE4 effect 因 key 含 `isLoading`（M-BUG-10 嫁接）立即消费：`jumpToPlainOffset` 恰一次、偏移等于摘录锚定位置、pending 清空——**证明"文档就绪后批注请求及时消费"**。
- `se4_20`：消费完成后向 highlights 追加无关高亮（SE4 key 含 highlights，effect 重启）→ 不得再次跳转——**证明"随后不会因无关更新再次跳转"**。
- `se4_30`：源定位请求在场（`pendingSourceLocatorJson != null`）时 SE4 让位，不清空 pending——两通道互斥合同。
- 边界如实声明：本类只证明 SE4 effect 的组合时序，不证明 ReaderScreen 完整接线与端到端 UI 滚动；后者由已验 MD 批次（mdtc_10/11/20/30 + 设备侧 uiautomator 驱动）覆盖，不重复。Compose 测试时钟为测试驱动，断言"有限次重组内消费"，不断言真实毫秒数。
- MD 跨章保存/冷启动/回源复用已验方法（mdtc_10/11/20/30），不新增重复方法。

## 4. 数据清理与隔离

两个新类只在隔离测试宿主（新 applicationId 后缀 + 新 UID）运行，读写范围 = 宿主私有 filesDir 与宿主私有 Room 库。EPUB 测试按生产派生 id 精确删除 7 张表行 + 内部副本四态文件 + 来源目录；SE4 测试 DB 只经真实 `reader_anchor_cache` DAO 写 `jse4-` 前缀 key 并在 tearDown 清理；MD 类沿用原 `mdtc-` 清理。不读写正式书库。

## 5. 交付物与执行顺序

- **payload 恰 3 文件**（payload == 编译树程序化核对一致）：
  - `acceptance/MdTrueChapterAcceptV1Test.kt` `991aa720…`（字节复用）
  - `acceptance/EpubReplaceFreshSourceAcceptTest.kt` `7784bff2c503b1439e511a4b5b73fb1026d8b8dac5341f06eaa58ba887cb7ccc`（新增）
  - `ui/screen/reader/ReaderAnnotationPendingConsumptionTest.kt` `12cfd95cdedf77c2d2909d07a3df1c6604bfb1f52f9b3929ca9ae28166eb3e9f`（新增）
- **执行顺序**（12 仪器方法）：jera_10 → jera_20 → se4_10 → se4_20 → se4_30 → mdtc_00 → mdtc_01 →（ui.py 驱动保存）mdtc_10 →（cold）mdtc_11 →（离章）mdtc_20 →（回源）mdtc_30 → mdtc_90；行为分母口径沿用各原批合同（MD 行为分母 01/10/11/20/30，夹具/终态不计）。
- **依赖与 runner**：androidTest 既有库零新增；instrumentation = `<HOST>.test/com.creationreadingassistant.testing.CraIsolatedTestRunner`；宿主后缀建议按 Root MD 模式在设备副本树绑定（如 `.qahostjointv1`），透传生产 App（结果 JSON `applicationClass` 可核对）。**本批不改任何构建配置**。
- 详细清单见 [TESTING-NOTES.md](<E:/creation-reading-assistant/next-round-joint-acceptance-tests-v1/TESTING-NOTES.md>) 与 `execution.json`。

## 6. 编译核对（非运行证据）

`./gradlew --offline --no-daemon :app:compileDebugAndroidTestKotlin`（JDK 17.0.14；独立缓存 `_cache/gradle-home` 仅本候选使用；基树 = rollback-wip-reconcile-v2 产品树 + 本批 3 文件）——**BUILD SUCCESSFUL**（39 个 androidTest 文件 = 36 既有 + 3 本批全部编译通过；唯一警告来自既有文件）。原始日志 `freeze/evidence/compile-androidtest.log`、exit 0。未构建最终联合 APK，未占用设备。

## 未覆盖项与 NOT RUN 声明

- **全部 12 个仪器方法 NOT RUN**（未运行 MuMu）：jera_10/jera_20、se4_10/se4_20/se4_30、mdtc 7 方法——本批不写任何"验收通过"。
- SE4 测试不覆盖 ReaderScreen 全接线（端到端由已验 MD 批次覆盖）；EPUB v3 生产树内容未知，若 v3 合同变化需绑定前核对（当前按 fresh-source 修复后合同编写）；最终包后缀、APK 构建、设备批执行由 Codex 集成窗口串行完成。
- 驱动侧（`mdtc.py`/`ui.py`）不在本批所有权内，执行顺序说明中引用 Root MD 既有驱动模式。

## 副作用与停止

无设备、无系统设置、无正式书库触碰；三只读输入零改动；D 产品源码零写入、零 Git 操作；共享仓库仅新增本报告。交付测试源码与核对证据后停止：不运行 MuMu、不修改 D 产品源码、不操作 Git、不更新 current.md/Obsidian。
