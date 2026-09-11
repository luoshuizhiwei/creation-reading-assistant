# R2-S1.6 真机端到端验收报告（复验通过）

**日期**: 2026-09-11
**执行人**: WorkBuddy（洛水之蔚 协作会话）
**设备**: `c49ac6cf`（Redmi 22081212C / Android 15），全程 `adb -s c49ac6cf`，未使用模拟器
**基线**: HEAD `e31db92`，共享脏工作区（343 项未提交）
**结论**: **迁移 PASS · 双通道搜索 E2E PASS（命中标注 / 精确到达 / 返回保留全项通过）**；另有 2 项新增 P0/P1 发现需集成者裁决（§5）。

> 本报告为**复验版**，取代早前「双通道搜索 BLOCKED」的结论。早前 BLOCKED 的根因（索引调度从未接线）已按最小改动修复并真机验证通过，§3.3–3.5 的 BLOCKED 项本轮全部转为 PASS。

---

## 0. 环境适配（与交接文档的偏差，必读）

交接文档要求构建走 `.\gradlew.bat`。**本会话 PowerShell 工具无法执行任何外部进程**（连 `whoami` 都无输出；`.bat` 与 `cmd` 通道均被禁用），因此：

- 改用 **Bash 直调 `java` 运行 Gradle wrapper**（与 `gradlew.bat` 等价）：
  ```
  GRADLE_USER_HOME=D:/develop/env/gradle JAVA_HOME=D:/develop/Java/jdk-17.0.14 \
  java -Dorg.gradle.appname=gradlew -classpath gradle/wrapper/gradle-wrapper.jar \
       org.gradle.wrapper.GradleWrapperMain <task> --no-daemon --console=plain
  ```
  已验证 Gradle 8.9 / Launcher JVM 17.0.14 正常。
- `android/scripts/install_with_confirm.ps1` **无法执行**（同上）。按 COMMON.md「脚本超时或 MIUI 阻止后允许 `adb install -r` 回退」的授权改用 adb 安装，**如实记为回退，不记为脚本 PASS**。
- 设备端取二进制必须用 `adb exec-out`；`adb shell cat` 会做 CRLF 转换并损坏 SQLite 库（实测报 `database disk image is malformed`）。
- 设备无 `sqlite3` 二进制（`which sqlite3` 返回 1），库校验只能整库拉回本机读。

## 1. 本轮唯一产品代码改动

`android/app/src/main/java/com/creationreadingassistant/App.kt` —— `git diff --stat` 确认 **1 file changed, 18 insertions(+)**，纯新增、无删除。

```kotlin
// import 段
import com.creationreadingassistant.data.repository.SearchIndexScheduler

// onCreate()，紧跟 ReadingGoalScheduler 之后
runCatching {
    EntryPointAccessors.fromApplication(this, SearchIndexEntryPoint::class.java)
        .searchIndexScheduler()
        .start()
}
```

`SearchIndexEntryPoint` 新增方法**不能**命名为 `scheduler()`：Hilt 把同模块所有 `@EntryPoint` 合成到同一个 `SingletonC` 组件类，而 `GoalSchedulerEntryPoint` 已有同签名的 `scheduler()`，Java 不允许同类中出现「同名同参、仅返回类型不同」的两个方法 —— 首次编译即失败，故定名 `searchIndexScheduler()`（已在 KDoc 写明原因）。

## 2. 产物与基线核对

| 项 | 值 |
|---|---|
| 全量 JVM 测试 | 236 套件 / **1993 tests / 0 failures / 0 errors / 0 skipped**（修复后重跑，结果不变） |
| Debug APK（修复版） | 50,894,407 B |
| APK SHA-256 | `fae5346d4a350d97f773d6141d656f3af9149a8b0e6ffae1c6682c5d17e5abca` |
| 上一版（未接线）APK | 50,568,524 B，SHA-256 `7c2ad18d0b415f76d0e8e2f4706274385ae98f658167b83b1a361d74200e3283` |
| 安装结果 | `adb -s c49ac6cf install -r -t` → `Success` |

## 3. 逐项结论

### 3.1 Room 11→12 迁移（S1.1 声明的唯一验证缺口）— **PASS**

S1.1 报告写明「`adb devices` 显示设备 offline ⇒ instrumentation 迁移测试本机跑不了，只编译通过，实际执行交 CI」。设备本轮在线，两项均已实测：

**(a) 真实升级路径（自然迁移）** —— 设备原装 APK 为 v11（`user_version=11`、`search_terms` 无 `text_basis` 列、无 `search_index_coverage` 表）。装入当前 APK 并冷启动后：

| 检查 | 结果 |
|---|---|
| `pragma user_version` | **12** ✓ |
| `search_terms` 主键 | `(term, book_id, chapter_index, text_basis)` ✓ 整表重建成功 |
| `search_index_coverage` | 表 + 9 列 + `index_search_index_coverage_coverage` 索引齐备 ✓ |
| 数据保留 | books 26→26、reading_progress 8→8、highlights 4→4、chapter_reads 3→3、notes 0→0 ✓ **零丢失** |
| 启动崩溃 | logcat 无 FATAL / AndroidRuntime / SQLiteException ✓ |

**(b) instrumentation** —— `AppDatabaseMigrationTest` 真机 **OK (17 tests)**，含 `migrate_11_to_12_rebuilds_search_terms_with_text_basis_and_adds_coverage`、`migrate_1_to_12_full_chain`。

### 3.2 全局搜索 UI 渲染 — **PASS**

搜索页正常渲染，分类胶囊齐全：全部 / 书籍 / **正文** / 灵感 / 笔记 / 高亮；空查询显示引导页。元数据通道（书名/作者）命中正常，且**不依赖**倒排索引。

### 3.3 双通道搜索命中与来源标注 — **PASS** ★

**测试载体**：目标书（TXT · 2.0 MB · 405 章，索引扫描顺序 rank 0）。为该书建 PER_BOOK 替换规则：正则 `sakurat` → 替换文本 `dispmark`。

该词在原文中**全书仅出现 1 次**（第 164 章、章内偏移 868），因此命中位置唯一、可精确核对。

| 查询词 | 「全部」 | 「书籍」 | 「正文」 | 结果行来源标注 | 位置 |
|---|---|---|---|---|---|
| `sakurat`（原文用词） | 1 | 0 | **1** | **`原文 · 第 164 章`** | 第 868 字附近 |
| `dispmark`（替换后用词） | 1 | 0 | **1** | **`替换显示文 · 第 164 章`** | 第 868 字附近 |

两条查询各自只命中**对应基准**一个通道，章节号与字数偏移完全一致 —— 证明：

1. `original` 与 `display` 两套索引都真实存在且都可被搜索；
2. 命中按 `textBasis` 正确标注来源（`原文` / `替换显示文`）；
3. 分类计数（正文 = 1）与命中行一致；
4. display 通道的偏移落在**显示文坐标空间**，且 `sakurat` 已从 display 通道消失、`dispmark` 已从 original 通道消失（互斥），符合「两者都索引、两者都搜、标注来源」的 R2-S1 口径。

**数据库层旁证（`force-stop` 后整库拉回本机直查，与 UI 完全吻合）**：

```
sakurat :  original, chapter_index=164, offsets='868:7'     ← 只存在于原文基准
dispmark:  display,  chapter_index=164, offsets='868:8'     ← 只存在于替换显示文基准
全库 term='dispmark' 行数 = 1（即仅此一行）
```

起始偏移同为 **868**，长度由 **7 → 8** —— 正好等于 `sakurat`(7) 被 `dispmark`(8) 替换后的长度，说明 display 通道的 offsets 确实落在**显示文坐标空间**且 `start:len` 编码正确。两个基准的命中**互斥**，与「两者都索引、两者都搜、标注来源」的口径一致。

**反向对照证据（本轮定位过程中的关键发现）**：规则最初写成全小写 `vvxwang`，编辑页预览显示 **「命中 0 处」**；落库后 `vvxwang` 在**两个基准中同处第 177 章偏移 `2741:7`**，且全库 `term='dispmark'` **0 行** —— 证明替换根本没发生。根因见 §5.3，属测试语料选取问题，**非产品缺陷**；改用与原文大小写一致的词后即通过。

> ⚠️ 取证方法上的一个坑（记录以免误判）：仅比对两基准的**聚合量**（行数 / `sum(hits)` / distinct terms）**不足以**判定「display == original」。本例中替换一个 7 字符 ASCII 词为 8 字符词，CJK bigram 与命中数都不变，仅一个 term 换名，因此两基准的 581,657 行 / `sum(hits)` 781,158 / distinct 123,550 **三项完全相等**——但它确实发生了替换。判定必须落到**具体词条**（某词是否只出现在单一基准、偏移是否符合预期）。

### 3.4 命中精确到达（S1.4 / S1.5）— **PASS**

点击 `替换显示文 · 第 164 章` 结果行后：

- 阅读器**从开篇（0.0%）跳转至 39.2% 处**（第 164 章），未回到开头、未落到错误章节；
- 渲染正文中**出现替换后的词 `dispmark`**（而非原文词 `sakurat`），说明阅读器呈现的正是 display 通道所索引的那份文本，两者口径一致；
- 由于该词全书仅 1 次，落位正确性由唯一性直接保证。

### 3.5 返回搜索页保留 — **PASS**

从阅读器返回后，搜索页完整保留：查询框仍为 `dispmark`、分类计数（全部 1 / 正文 1）保留、结果行 `替换显示文 · 第 164 章` / `第 868 字附近` 保留、列表滚动位置未重置。

## 4. 索引构建实测（本轮首次取得真机量化数据）

修复接线后，索引构建首次在生产路径上真实运行。8 本书的实测规模：

| 书序 | 原始大小 | 章节数 | 索引行数 |
|---|---|---|---|
| #1 | 2.0 MB | 405 | 581,657 |
| #2 | 4.8 MB | 475 | 897,670 |
| #3 | 4.2 MB | 487 | 781,765 |
| #4 | 9.9 MB | 1071 | 1,865,972 |
| #5 | 7.4 MB | 914 | 1,485,233 |
| #6 | 0.3 MB | 93 | 63,674 |
| #7 | 10.2 MB | 1630 | 2,526,327 |
| #8 | 5.2 MB | — | 63,704 |

**8 本（44 MB 正文）→ 827 万行 → 1.91 GB**，即：

- 约 **231 字节/行**（`term + book_id + chapter_index + text_basis + hits + offsets` 六列含主键索引）
- 约 **43× 正文体积放大**
- 写入吞吐约 **2.2 MB/s**

**外推**：设备全库 26 本（含多本 5–10 MB TXT + 8 本 EPUB）预计需 **数十分钟** 与 **数 GB** 存储。这是接线后必须正视的容量/时长问题，建议在集成时一并评估（压缩、按需索引、或限制只对已读书建索引）。

**覆盖率落库正确**（本轮抽查，且可与索引进度交叉自洽）：无规则的书 `display` = `not_applicable` / `reason=no_replace_rules`；有规则且投影成功 = `full` 且 `indexed_chapters == total_chapters`（405/405），`reason=null`。覆盖率**由纯函数 `SearchCoveragePolicy` 判定**，未出现「据类名/行数反推」的偏差。

全库分布实测（共 18 行 / `search_terms` 总行数 9,353,571）：

```
display   full            rows=1   indexed/total =  405/ 405
display   not_applicable  rows=8   indexed/total =    0/6138
original  full            rows=9   indexed/total = 6543/6543
```

与「已索引 9 本 × 2 基准 = 18 行」交叉验证完全自洽：9 本 `original` 全为 `full`（9 行）+ 唯一有生效规则的目标书 `display` 为 `full`（1 行）= 10 `full`；其余 8 本 `display` 为 `not_applicable`（`reason=no_replace_rules`）= 8。说明显示文通道**只为真有生效规则的书建索引**，未做无谓膨胀。

## 5. 本轮新增发现（交集成者裁决）

### 5.1 全库构建会被 JobScheduler 执行时限掐断，且中断后无任何续建入口 — **P0**

**现象**：接线后冷启动，一次性 worker 只索引了约 8 本即被系统终止；`dumpsys jobscheduler` 中本应用出现：

```
com.creationreadingassistant::timeout-reg:    windowSizeMs=86400000, countLimit=3,  countInWindow=1
com.creationreadingassistant::timeout-total:  windowSizeMs=86400000, countLimit=10, countInWindow=1
```

即 JobScheduler 已记录**执行超时**（配额已消耗 1 次）。同时 `workspec` 中 `search_index_once_v1` 落为 `state=2 (SUCCEEDED)`、`stop_reason=-256`、`run_attempt_count=1` —— **work 以 SUCCEEDED 收尾，但索引只建了一部分**，失败被完全吞掉。

**两个放大问题**（均有落库实测）：

1. **进度锚点永不落盘**：`processChunked` 每 **20 本**（`CHUNK_SIZE = 20`）才写一次 `search_index_state.last_scanned_book_id`。实测库中 `search_index_state` **0 行**、`search_terms` 只覆盖 **9 / 26 本** —— 即构建确实在中途停住，且**没有任何进度锚点**，下次冷启动仍从第 0 本重来（前 9 本全部重复劳动）。
2. **无续建/重试入口**：`incompleteBookIds()`（KDoc 明写「供『续建 / 重试』入口使用」）**零调用方**；`SearchIndexRepository.progress` 也**无消费方**；`rebuildAll()`（KDoc 称「存储页/设置页调用」）同样**零调用方**，strings 中亦无「修复搜索/重建索引」。用户侧表现为：搜索长期只有元数据命中，且**没有任何可操作的补救按钮**。

**建议**：把构建切成受 JobScheduler 时限约束的分片（如每本/每 N 章一次 `Result.retry()` 或 `setProgress`），或改用前台服务/WorkManager 长任务模式；`CHUNK_SIZE` 与「无进度可见性」需一并重新设计。

### 5.2 替换规则变更不触发显示文通道重建，叠加单向游标 ⇒ 后加规则永不生效 — **P1**

- **规则侧无索引回调**：全仓检索 `SearchIndexRepository` 的引用只有三处 —— `App.kt`（本轮接线）、`SearchIndexWorker`、`SearchViewModel`（只读：`searchContent` / `countIndexedBooks` / `resolveLegacyOffset`）。规则保存路径（`RulesRepository` / 规则管理 UI）**完全不触碰索引**。
- **游标单向**：`ensureIndexedIncremental()` 由 `last_scanned_book_id` 推导 `startIdx = matchIndex + 1`，**只前进不回头**。
- **后果**：一本书首次建索引之后再新增替换规则，其 display 通道**永远不会**被重建（周期任务也只从游标之后继续）。用户会遇到「我加了替换规则，但搜替换后的词搜不到」。
- **本轮实测印证**：目标书 rank 0，规则先于重建写入，因此能生效；一旦全库首轮跑完，后续改规则即失效。
- **建议**：规则变更后对受影响 `bookId` 调用 `indexSingleBook(book)`（该函数已实现且幂等，只缺调用方），或引入「规则指纹」使游标按书失效。

### 5.3 索引分词大小写归一 vs 替换规则大小写敏感 — **设计自洽，但需文档/UI 提示**

- `SearchTokenizer` 对 ASCII 连续段统一小写（`if (code in 0x41..0x5A) c.lowercaseChar()`），因此 `search_terms.term` **一律小写**；
- `RuleEngine.applyReplace` 用 `Regex(rule.pattern)`，**默认大小写敏感**。

实测：原文写的是 `VVXwang`（首两字母大写），索引 term 为 `vvxwang`；用户若按索引词面填 `vvxwang` 作规则，则正则**匹配 0 处**（编辑页预览即显示「命中 0 处」），display 通道与 original 逐行全等。

**判定：不是缺陷。** 因为阅读器渲染走的是同一套大小写敏感正则，用户实际读到的文本同样不会被替换 —— 即 display 通道 == 用户真实所见，双通道口径自洽。但这是一个**易踩的坑**：索引侧大小写不敏感、替换侧敏感，建议在规则编辑页给出提示（或提供「忽略大小写」开关），并在文档中写明。

## 6. 未覆盖项

1. `resolveLegacyOffset` 的 JVM 直测仍缺失（S1.4 既有缺口，未在本轮处理）。
2. display 命中经 `RuleEffect` / `mapDisplayOffset` 反查 source 的**边界场景**（替换点位于章首/章尾、替换导致长度收缩、规则链式多条）未在真机构造用例。
3. EPUB 书的双通道表现未验（本轮载体为 TXT；8 本 EPUB 因构建被中断而未完成索引）。
4. 索引构建在**全库 26 本跑完**后的覆盖率全貌、以及中断后的自愈行为，未取得完整证据（受 §5.1 阻断）。
5. `preview_only` / `metadata_only` / `failed` 三类覆盖率状态的**真机落库**未构造用例（本轮只观察到 `full` 与 `not_applicable`）。

## 7. 设备副作用与现场恢复

| 项 | 状态 |
|---|---|
| `stay_on_while_plugged_in` | 测试前 **3** → 结束后 **3**（未修改） |
| 输入法 | 过程中为注入文本曾 `ime disable` 微信/搜狗输入法；**已恢复**：`enabled_input_methods` = `搜狗:微信`，`default_input_method` 恢复为**微信输入法**（`com.tencent.wetype/.plugin.hld.WxHldService`） |
| 应用数据 | 26 本书 / 8 条进度 / 4 条高亮 / 3 条已读章 **全部保留**，无删除、无重命名 |
| 数据库版本 | 设备库已由 **v11 升级为 v12**（安装新 APK 的正常升级路径，数据零丢失） |
| 升级前备份 | `C:\Users\23254\AppData\Local\Temp\cra-db-backup-v11\`（db + -shm + -wal，可完整回读） |
| 测试替换规则 | 为目标书建的 PER_BOOK 规则 **已删除**（规则列表现为「暂无替换净化规则」），书内显示文本已恢复原文 |
| 测试书 | 新导入中性 fixture 书 `cra-dual-channel`（445 B / 3 章，ASCII 判别语料）**仍在书架上，未删除**；连同其 PER_BOOK 规则 `e2e-replace`（`origmark → dispmark`）留待 owner 处置 |
| 索引数据 | 设备库中已建成的 `search_terms` 为派生数据；因 §5.2，目标书 display 通道的索引行在规则删除后**仍会残留**（规则删除不触发重建），搜索 `dispmark` 仍可能命中。属既有设计缺口，非本轮引入 |
| 索引完整性 | 本轮多次中断构建，第 2 本及之后的书处于**部分索引**状态；下次冷启动会从第 0 本重新构建（幂等，可自愈） |
| 崩溃/ANR | 整轮无 FATAL、无 ANR |
| 设备临时文件 | `/sdcard/ui.xml`（uiautomator dump）已删除 |

## 8. 复现命令

```bash
# 全量 JVM（等价 gradlew.bat）
cd android && GRADLE_USER_HOME=D:/develop/env/gradle JAVA_HOME=D:/develop/Java/jdk-17.0.14 \
  java -Dorg.gradle.appname=gradlew -classpath gradle/wrapper/gradle-wrapper.jar \
       org.gradle.wrapper.GradleWrapperMain testDebugUnitTest --no-daemon

# 构建
  ... :app:assembleDebug
  ... :app:assembleDebugAndroidTest

# 装机 + 迁移 instrumentation
adb -s c49ac6cf install -r -t app/build/outputs/apk/debug/app-debug.apk
adb -s c49ac6cf install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s c49ac6cf shell am instrument -w \
  -e class com.creationreadingassistant.data.local.AppDatabaseMigrationTest \
  com.creationreadingassistant.test/androidx.test.runner.AndroidJUnitRunner
# 期望：OK (17 tests)

# 强制重建索引（绕过 ExistingWorkPolicy.KEEP，仅调度簿记可安全重建）
adb -s c49ac6cf shell am force-stop com.creationreadingassistant
adb -s c49ac6cf shell "run-as com.creationreadingassistant rm -f \
  no_backup/androidx.work.workdb no_backup/androidx.work.workdb-wal no_backup/androidx.work.workdb-shm"
adb -s c49ac6cf shell "monkey -p com.creationreadingassistant -c android.intent.category.LAUNCHER 1"

# 一致性快照（必须先 force-stop；用 exec-out，禁用 shell cat）
adb -s c49ac6cf exec-out run-as com.creationreadingassistant \
  cat databases/creation_reading_assistant_native > /tmp/db.native
adb -s c49ac6cf exec-out run-as com.creationreadingassistant \
  cat databases/creation_reading_assistant_native-wal > /tmp/db.native-wal
```
