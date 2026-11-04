# S04 单文件修复报告：EpubParserInstrumentedTest 清理与路径隔离

- 日期：2026-09-25
- 分支：`codex/recover-worktree-20260920`（HEAD 5aed593，未 commit）
- 所有者窗口：S04 单文件安全修复（对应设计文档
  `2026-09-25-s04-instrumentation-startup-isolation-design.md` §3.7 登记的问题）
- 改动文件（仅此两个）：
  - `android/app/src/androidTest/java/com/creationreadingassistant/feature/reader/EpubParserInstrumentedTest.kt`（修改）
  - 本报告（新增）

## 问题回顾（§3.7）

原测试把真实 `Context` 直接交给 `EpubParser.parse`，用例 id 为固定字符串
（如 `instrumented-empty-spine`），导致：

1. `EpubParser` 的内部副本落在**正式** `filesDir/books/epub/<固定id>.epub`；
2. `@After` 按固定 bookId 在**正式 filesDir** 删文件。

与真机上用户已导入的书共用一个目录，bookId 固定字符串虽有前缀但仍属
「在正式目录按 id 删文件」这一类缺陷（§3.7 已证实，原窗口只登记不修改）。

## 具体改动（仅 `EpubParserInstrumentedTest.kt`）

1. **复用既有 `IsolatedTestContext`**（未改它本身）：
   - 新增 `testRoot = File(context.cacheDir, "epub-parser-isolated-test/<UUID>")`，
     每次测试实例唯一（UUID 命名，不再依赖固定 id 的低碰撞概率）。
   - `isolated = IsolatedTestContext(context, testRoot)`；四处
     `EpubParser.parse(context, …)` 全部改为 `EpubParser.parse(isolated, …)`。
     由此 `EpubParser` 内部副本、staging、img 缓存全部落在 `testRoot/files/books/epub/`，
     其 cacheDir 兜底路径也落在 `testRoot/cache/`。
   - `contentResolver` 未被包装覆盖，仍委托真实实现只读合成源
     （`Uri.fromFile`），读源能力保留（与 `IsolatedTestContext` 文档的覆盖范围审查一致）。
2. **合成源文件迁移**：`createEpub` 的落盘目录由正式 `context.cacheDir`
   改为 `testRoot/src/`。
3. **`@After` 重写**：`cleanUp` 现在只做
   `isolated.assertInsideRoot(testRoot)` → 复核防线 → `testRoot.deleteRecursively()`。
   删除前经 `assertInsideRoot`（canonical 路径前缀校验）确认只清理自己的根；
   **不再**按固定 bookId 在正式 `filesDir` 删除任何文件。
4. **新增运行时防线 `assertRootDisjointFromRealStorage()`**（题目要求的静态/运行时防线，
   取运行时形态，在 `@Before` 与 `@After` 各执行一次）：
   - 测试根不得等于/位于正式 `filesDir` 之内；
   - 正式 `filesDir/books/epub` 不得等于/位于测试根之内；
   - 均用 canonical 路径 + 分隔符前缀比较（与 `assertInsideRoot` 同一严格度），
     失守即 `check` 抛出，**拒绝继续**而非照常删除。
   - 由于测试根在正式 `cacheDir` 下且带专属前缀目录，防线在当前实现下恒应通过；
     它防的是将来有人改动根目录构造方式（例如误改为 `filesDir` 下）时的静默回归。
5. **删除冗余跟踪**：`sourceFiles`、`importedIds` 两个列表及其逐项删除逻辑已无存在必要
   （整个 `testRoot` 一次性清理），已删除；各用例内的 `importedIds += id` 一并移除。
6. **断言语义未动**：四个用例的全部 `assertEquals` / `assertFalse` / `assertTrue`
   及 `loadChapterText` 校验原样保留。
7. 类级 KDoc 补充了隔离方式与边界说明；未记录任何真实书名（合成内容标题均为
   「测试 EPUB」类中性名称，与改动前一致）。

## 未验证项（如实登记）

- **未编译**：本窗口禁止运行 Gradle，未执行任何编译/单元/instrumentation 构建。
- **未上设备**：禁止 adb/安装/设备命令，未做任何真机验证。
- 因此「编译通过」「测试通过」「真机验收」均**未发生**，不得由本报告推断。
- 静态自查已做：`importedIds`/`sourceFiles` 引用已全部清除，不存在悬空引用；
  `IsolatedTestContext` 的构造签名与 `parse(isolated, …)` 参数类型匹配。

## 风险

1. `IsolatedTestContext` 未覆盖的 Context 成员（`getExternalFilesDir`、`getDir`、
   `databasePath` 等）在当前 `EpubParser.parse` 链路上不可达（该类文档已 grep 审查），
   但若将来生产代码改用它们，本测试的隔离会静默出现缺口 —— 防线只覆盖
   `filesDir`/`books/epub` 不相交，不覆盖其他存储成员。
2. 测试根位于正式 `cacheDir` 之下（唯一 UUID 子目录）。这是刻意选择：系统清缓存
   最多残留/清掉测试垃圾，不影响正式 `filesDir`；但意味着测试根与正式 cacheDir
   是父子关系（非同层），若评审要求「测试根不得在任何正式目录之下」，需要
   `context.getExternalFilesDir(null)` 之外的自定义挂载点，属越界改动，本窗口未做。
3. 测试失败的用例若在 `cleanUp` 之前抛出且 `@After` 防线误报（理论上仅当目录被
   外部移动/符号链接替换），会以防线异常而非原断言失败报告，排障时需注意区分。
4. `@Before`/`@After` 双重防线带来极少量的 canonical 路径解析开销，可忽略。

## 边界遵守情况

- 仅修改 `EpubParserInstrumentedTest.kt`；未触碰 `IsolatedTestContext`、Gradle、
  Runner、产品源码、其他测试。
- 未运行 Gradle / adb / 任何设备命令；未 commit / push / stage / reset / checkout / clean。
- 无 SEAM REQUEST：实现未需要越界文件。
