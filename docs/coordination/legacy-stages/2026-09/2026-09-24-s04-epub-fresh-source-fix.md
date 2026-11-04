# S04 EPUB「同一来源 URI 更新后仍读取旧内容」修复

- **仓库**：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`
- **分支**：`codex/recover-worktree-20260920`（HEAD `5aed5939600e491843e3c44453921bd6273a233c`）
- **状态**：已实施 + 已跑门禁，**未 commit / push / stage / reset / checkout / clean**
- **真机操作**：本窗口**未**操作真机、未安装 APK、未碰用户书库。所有结论均来自 JVM 测试与构建产物。
- **启动前提核对**：开工前确认 `docs/coordination/legacy-stages/2026-09/2026-09-24-s04-epub-fresh-source-fix.md` **不存在**，且
  `docs/coordination/` 下没有任何报告提到 `requireFreshSource`／新鲜来源 ⇒ 判定任务未在别处完成，继续施工。

---

## 1. 根因

真机症状：同一 EPUB 来源 URI 的内容由 V1 更新为 V2 后，应用仍显示 V1
（`docs/qa/2026-09-24-s04-safe-real-library-acceptance.md` §9-1）。

根因比「忘了传 `expectedBookId`」更深一层，这一点必须讲清楚：

`EpubParser.parse` 原本的入口是

```kotlin
val id = expectedBookId ?: ("epub_" + uri.toString().hashCode().toString(36))
val epubFile = File(context.filesDir, "books/epub/$id.epub")
if (!epubFile.exists() || epubFile.length() == 0L) { /* 从 uri 拷贝 */ }
// 否则：直接沿用已有副本
```

书被导入时走的是**按 Uri 派生 id**（`openEpub` 不传 `expectedBookId`），于是

```
派生 id  ==  这本书的 bookId
⇒ EpubParser 的 epubFile  ==  EpubRepository 替换链路的 target（同一个文件）
```

**只要盘上存在旧副本，「文件已存在就直接沿用」这条早退就必然命中，来源的新字节从头到尾没被读过。**
替换因此退化成「用 V1 覆盖 V1」的 no-op。

推论：**只传 `expectedBookId` 解决不了这个问题** —— 它不改变 id，也不改变早退条件。
这是本任务的核心判断，也是本窗口改动的设计依据。

同源的第二条缺陷（§9-4）：`parse` 是「先落盘、后校验」，校验失败时只删 staging、不删已落位的正式副本，
于是损坏 EPUB 在私有目录留下无 DB 记录引用的孤儿文件（真机 90 B 文件，MD5 `9d1df8cc…`，
出现在 `snap-05-layer2.tar` 而不在 `snap-04-relocate.tar`）。

---

## 2. 改动

### 2.1 `EpubParser.kt`（我独占，改动后 sha256 `af223434c24d4773c12f2b7e34b338a271738ce6b3082b67aba37e405bdfdeaf`）

新增参数 `requireFreshSource: Boolean = false`，语义是「本次必须读取并校验**来源 URI 的当前字节**」：

| | 旧行为 | 新行为 |
|---|---|---|
| 已存在内部副本 | 无条件早退、解析旧字节 | `requireFreshSource = true` 时**绝不早退**，一律从来源重新取字节 |
| 字节落点 | 直接写 `epubFile`（可能覆盖仍完好的旧正文） | 新鲜模式一律落 `<id>.importing`，**不碰 `epubFile`** |
| 来源读不到时 | 回退 `fallbackLocalPath` / cacheDir 副本 | 新鲜模式下**禁用该兜底**，直接抛 `IllegalStateException("无法读取所选 EPUB 来源，请重新选择文件")` |
| 失败后 | 只删 staging，留下正式副本孤儿 | 复制阶段与解析阶段失败都清理**本次产生的**文件 |

兜底为什么必须在新鲜模式下禁掉：`fallbackLocalPath`／cacheDir 读到的正是本次要替换掉的**旧内部副本**，
用它「校验通过」等于把旧内容伪装成新来源 —— 与早退分支是同一缺陷的另一种形态。

「本次产生的文件」用 `landing` 变量精确界定，失败只删它：

```kotlin
val landing: File? = when {
    requireFreshSource -> staging          // 本次落盘 ⇒ 失败可删
    reuseExisting      -> null             // 不是本次写的 ⇒ 绝不能删
    else               -> epubFile         // 本次落盘 ⇒ 失败可删
}
```

### 2.2 一个必须记住的实现陷阱：Windows 文件句柄顺序

最初的写法把清理放在 `catch` 里：

```kotlin
try { zip = ZipFile(parsedFile); ...解析... }
catch (e: Exception) { discardLanding(...); throw e }   // ← 错
finally { zip.close() }
```

`catch` 先于 `finally` 执行，此时 `ZipFile` 仍持有文件句柄，**Windows 上 `delete()` 静默失败（返回 false）**，
孤儿文件照样留下。测试因此在「原版代码」上也失败 —— 我一度误判为「变异未被抓住」，实际是这个 bug。

正确写法是「失败标记 + finally 里关闭句柄之后统一清理」：

```kotlin
val zip: ZipFile
var parseFailed = false
try { zip = ZipFile(parsedFile) }
catch (e: Exception) { discardLanding(landing, staging); throw e }
try {
    ...解析...
} catch (e: Exception) {
    parseFailed = true
    throw e
} finally {
    zip.close()                                  // 先释放 Windows 句柄
    if (parseFailed) discardLanding(landing, staging)
}
```

### 2.3 `EpubRepository.kt`（我独占，改动后 sha256 `f3846ef7f2af78458ee8bdf4e302b12ae31edd1f47af331ad76c53d6ffc2214b`）

替换链路调用改为：

```kotlin
val validated = EpubParser.parse(
    context = context,
    uri = selectedUri,
    expectedBookId = stored.id,
    requireFreshSource = true,      // ← 本任务的关键一行
)
```

并在 `finally` 增加一行，保证新鲜来源副本在**任何**路径下都不残留：

```kotlin
} finally {
    staging.delete()
    runCatching { validatedFile.delete() }
}
```

### 2.4 明确保持不变的既有不变量（逐项 grep 复核，未被本任务弱化）

| 不变量 | 位置 | 复核结果 |
|---|---|---|
| 入口残留守卫 `if (backup.exists()) throw IllegalStateException(...)` | `EpubRepository.kt:96` | 未改动；仍**拒绝**而非自动恢复 |
| 无条件的 `backup.delete()` 已移除 | `:131` 注释说明 | 保持移除，回滚按「走到哪一步」分流 |
| `backupReady` / `promoteStarted` 双标记 | `:142-143`、`:152-163`、`:205-215` | 未改动 |
| 长度校验 `require(backup.length() == target.length())` | `:158` | 未改动 |
| `target.delete()` 仅在 `backupReady` 后 | `:160`、`:206`、`:215` | 未改动 |
| 回滚末尾 `throw error`（取消也走回滚） | `:219` | 未改动 |
| 自动恢复策略 | — | 未改动，仍是拒绝 + 保留残留 |

### 2.5 测试

- `EpubRepositoryReplaceTest.kt`（sha256 `9e01fd5f…`，13 → **17** 条）新增 4 条：
  - `reads fresh bytes when source uri is unchanged but content changed`（同 URI V1→V2）
  - `reads fresh bytes for a different source uri`（不同 URI 回归）
  - `rejects invalid epub without orphan copy and preserves old target`（无效 EPUB）
  - `keeps target untouched until backup is established`
- `EpubParserFreshSourceTest.kt`（新建，sha256 `13c4c58a…`，**5** 条）：
  - `fresh source parses new bytes when internal copy already exists`
  - `default path still reuses existing internal copy`（对照：证明上一条不是靠「一律重读」通过）
  - `corrupt epub leaves no orphan copy on disk`
  - `non-epub zip leaves no orphan copy on disk`
  - `fresh source failure never deletes the existing internal copy`

**为什么不只写在 repository 层**：孤儿副本不变量属于 `EpubParser` 自身。repository 的 `finally` 也会删，
两处重叠 ⇒ 在 repository 层断言孤儿会「假通过」（删它的不是被测代码）。因此补了 parser 层测试。

**断言的是字节，不是返回值**：V1→V2 用例断言 `parsed.cachedEpubPath` 指向的文件字节含 `V2MARKER`、
不含 `V1MARKER`，并逐字节断言正式内部副本在提升前仍为 V1（`assertArrayEquals`）。
只断言 `title` 无法区分「真的换了」与「沿用旧副本」。

### 2.6 无越界改动 / 无 SEAM REQUEST

`git status --porcelain` 中 `ShelfImporter.kt` 显示为已修改（+387/−24），
但那是**另一个 agent** 的改动（新增 `refreshSourceBaseline` 等），本窗口从未编辑过它，
也未调用它提供的任何接口 ⇒ **不需要提交 SEAM REQUEST**。

本窗口实际改动的文件仅限：

```
android/app/src/main/java/com/creationreadingassistant/feature/reader/EpubParser.kt       (M)
android/app/src/main/java/com/creationreadingassistant/feature/reader/EpubRepository.kt   (M)
android/app/src/test/java/com/creationreadingassistant/feature/reader/EpubParserFreshSourceTest.kt   (NEW)
android/app/src/test/java/com/creationreadingassistant/feature/reader/EpubRepositoryReplaceTest.kt   (NEW, 本仓库中从未 commit)
android/app/src/main/java/com/creationreadingassistant/feature/reader/FileOps.kt          (NEW, 上一任务留下，本窗口未再改)
android/app/src/test/java/.../FileOpsTest.kt 等 — 无
```

`git diff --check` 针对这两个 main 文件：exit **0**（无空白/CRLF 问题）。
（全仓 `git diff --check` exit 2，但全部是**其他 agent 文件**的 CRLF 告警，与本窗口无关。）

---

## 3. 原始运行结果

工作目录均为 `.../creation-reading-assistant-recovery-20260919/android`。

### 3.1 定向测试

```bash
./gradlew :app:testDebugUnitTest \
  --tests "com.creationreadingassistant.feature.reader.EpubParserFreshSourceTest" \
  --tests "com.creationreadingassistant.feature.reader.EpubRepositoryReplaceTest" --offline
```
退出码 **0**，BUILD SUCCESSFUL。

XML 汇总（取自 `app/build/test-results/testDebugUnitTest/TEST-*.xml` 的 `<testsuite>` 属性）：

```
EpubParserFreshSourceTest      tests="5"  failures="0" errors="0" skipped="0"
EpubRepositoryReplaceTest      tests="17" failures="0" errors="0" skipped="0"
```

### 3.2 全量 JVM 单测

```bash
./gradlew :app:testDebugUnitTest --offline
```
退出码 **0**，BUILD SUCCESSFUL（3m 34s）。

```
XML 文件数 325
tests=3065  failures=0  errors=0  skipped=0
```

所有 325 个 XML 的 `timestamp` 均落在 `2026-09-24T17` 这一小时桶内 ⇒ 全部来自本轮新跑，无陈旧结果混入。

### 3.3 构建门禁

```bash
./gradlew :app:assembleDebug --offline
```
退出码 **0**，BUILD SUCCESSFUL。

```
app/build/outputs/apk/debug/app-debug.apk
53532616 字节
sha256 53d0c01b0d36dc19690dc1f62834f19fc61634a2dd394de2b23efc9da85359ac
```

```bash
./gradlew :app:compileDebugAndroidTestKotlin --offline
```
退出码 **0**，BUILD SUCCESSFUL。

### 3.4 变异测试（证明新测试真的能抓到回归）

前置说明：本任务的失败分支无法用「先跑旧代码看 RED」来证明 —— 新测试依赖新参数
`requireFreshSource`，在旧代码上根本无法编译，物理上不存在「旧代码 + 新测试」这个组合。
因此改用变异测试：故意破坏修复，看测试是否失败。**变异体的判定标准是「变异下失败、还原后通过」两条都要成立。**

| # | 变异 | 结果 |
|---|---|---|
| 1 | 新鲜模式允许早退（恢复旧根因）：把 `!requireFreshSource &&` 从 `reuseExisting` 里删掉 | **被抓住**：两条 EPUB 测试类共 10 条失败（含 `fresh source parses new bytes…`、`reads fresh bytes when source uri is unchanged but content changed`、`reads fresh bytes for a different source uri` 等） |
| 2 | 解析失败不清理本次落盘文件（`if (false && parseFailed) …`） | **被抓住**：`non-epub zip leaves no orphan copy on disk` 失败（5 tests completed, 1 failed） |

**变异 1 的原始输出**（`--tests` 同时跑两个类）：

```
EpubParserFreshSourceTest   > fresh source parses new bytes when internal copy already exists FAILED
EpubRepositoryReplaceTest   > still rolls back the file when the caller is cancelled FAILED
                            > keeps target untouched until backup is established FAILED
                            > rejects truncated fallback backup via length check FAILED
                            > keeps target intact when fallback copy throws mid-write FAILED
                            > falls back to copy when rename fails and completes the swap FAILED
                            > reads fresh bytes for a different source uri FAILED
                            > succeeds and leaves no residue when no backup exists FAILED
                            > reads fresh bytes when source uri is unchanged but content changed FAILED
                            > restores original target bytes from backup when post-swap update fails FAILED
```

两个变异均在还原后 `cp /tmp/EpubParser.kt.v2` 覆盖并 `sha256sum` 核对为 `af223434…`、
`grep -c MUTANT` = 0，再跑同一命令确认 `BUILD SUCCESSFUL`。

变异 2 的**第一次判定是我自己的错误结论**，过程如实记录：

1. 加变异后测试失败 → 我宣称「变异 2 被抓住了」；
2. 还原后**同一条测试仍然失败** ⇒ 它根本不是有效检测器，第 1 步的结论作废；
3. 真因是 §2.2 的 Windows 句柄顺序 bug（清理跑在 `zip.close()` 之前，静默失败）；
4. 修好句柄顺序后重跑：

```
加变异  → EpubParserFreshSourceTest > non-epub zip leaves no orphan copy on disk FAILED
          5 tests completed, 1 failed
还原后  → BUILD SUCCESSFUL（5 tests, 0 failures）
```

还原用 `cp /tmp/EpubParser.kt.v2` 覆盖，并用 `sha256sum` 核对与变异前备份一致
（`af223434…`，`grep MUTANT` 无命中），确认逐字节还原。

---

## 4. 未覆盖项 / 本轮未验证

明确声明：**JVM 测试不等于真实 Room 行为，也不等于真机验收。** 以下均未验证：

1. **真实 Room 行为** —— 全量 3065 条是 JVM 单测，`bookDao` 为 mock。
   `content_hash` / `bookDao.update` 与真实 DAO 的交互未在本轮跑到真实数据库。
2. **真机验收未执行** —— 未安装 APK、未重启应用、未在真机上重放 V1→V2。
   §9-1 / §9-4 是否真已消除，需要集成者跑真机层 1（测试 EPUB）才能判定。
3. **同长度内容损坏** —— 若 V2 与 V1 字节数恰好相同且来源是截断/位翻转的错误数据，
   长度校验无法发现。需内容哈希或 EPUB 结构校验以外的一致性判据，本轮未做。
4. **回滚自身失败** —— `backupReady` 分支里 `backup.renameTo(target)` / `copyTo` 在还原途中失败
   （磁盘满、断电）时无兜底。此时旧正文可能既不在 target 也不在 backup。
5. **断电 / 进程被杀风险** —— `backupReady` / `promoteStarted` 是**进程内**标记，不落盘。
   进程在备份就绪后、回滚完成前被杀，下次启动会命中入口残留守卫并被**拒绝**（符合设计，
   不会自动销毁），但需要人工介入。本轮未验证杀进程场景。
6. **并发替换** —— 同一本书同时发起两次替换无锁，本轮未覆盖。
7. **`.importing` 与 `.repairing` 的命名差异** —— parser 用 `<id>.importing`，
   repository 用 `<id>.repairing`，二者并存且互不相同。本轮未合并，属于既有设计，需集成者确认无歧义。

---

## 5. 给集成者的交接说明

- 未 commit。本窗口改动仅限 §2.6 列出的文件。
- `EpubParser.kt` 与 `EpubRepository.kt` 可能被其他 agent 继续修改；合并时请核
  `af223434…` / `f3846ef7…` 两个 sha256 是否仍然对应，若已漂移需要重新比对 §2.4 的不变量表。
- `EpubRepository.kt` 中含另一任务（rename→copy 回退接缝）的 `FileOps` 注入，
  与本任务改动同处一个文件但互不冲突；合并时不要回退其中任一部分。
- 建议集成者接下来跑：真机层 1 的 EPUB 替换 V1→V2，并确认 `files/books/epub/` 下
  解析失败后无新增孤儿文件。
