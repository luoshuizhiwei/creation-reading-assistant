# R2-S1.4 命中 → 精确到达（source 定位）

- 执行方：WorkBuddy（原生端接管）
- 日期：2026-09-11
- 前置：[S1.1 覆盖模型](workbuddy-r2-s1-1-index-coverage-model.md)、
  [S1.2 命中载荷](workbuddy-r2-s1-2-hit-payload-upgrade.md)、
  [S1.3 全文检索接线](workbuddy-r2-s1-3-fulltext-search-wiring.md)
- 状态：**Dev-verified 通过**（编译 + 1950 JVM 测试全绿）
- 范围：仅 `android/**` + 本报告。未 commit / push / tag。

---

## 1. 本片要解决的问题

S1.3 让正文命中出现在结果里，但点击仍只是 `reader/{bookId}` —— **打开书，从上次进度开始**。
R2 退出条件是「搜到内容可精确到达并返回」，所以这一步必须真正跳到命中处。

### 卡点：`encodeSourceLocator` 只认全局偏移

`ReaderTemporaryRoute.encodeSourceLocator()` 明确要求 `ReaderLocator.legacyOffset`（全书偏移），
没有就返回 null，**绝不伪造 offset=0**。而 S1.2 定下的命中坐标是 `(章, 章内偏移)` ——
**恰好是这条路径拒绝的那一类**。

更关键的是 `SourceNavigationContract.resolveChapteredPosition`：

```kotlin
if (!preferGlobalOffset && explicitChapter != null && explicitOffset != null &&
    locator.legacyOffset != null && chapterStart + explicitOffset != locator.legacyOffset) {
    return null   // ← 全局偏移与章节元组不一致时，直接拒绝跳转
}
```

也就是说：**不能顺手估一个全局偏移塞进去** —— 估错了不是跳偏，是**跳不了**。

---

## 2. 关键调研

| 事实 | 出处 |
|---|---|
| `LocatorCodec.encode(legacyOffset: Int, …)` 的 `legacyOffset` 是**非空 Int**，且**总是**写出 `"offset":n` | `LocatorCodec.kt:22-43` |
| 因此**改不动**：它带明确的旧版兼容契约（KDoc：「始终保留旧版认识的 offset」），为 S1.4 去改它会波及全部历史定位 | 同上 |
| TXT 章节有**真实字符**起始偏移 `TxtChapterDetector.Chapter.startOffset` | `TxtChapterDetector.kt:136-142` |
| EPUB 章起始 = `LegacyOffsetCodec.chapterStartOffsets(estimatedLengths)`，**ZIP 字节估算量纲**（中文约真实字符 3 倍），且是**冻结的原样复刻**，KDoc 明令不得改动 | `LegacyOffsetCodec.kt:19-39` |
| 阅读器 `buildBookIndex` 用的也是 `chapters.map { it.estimatedTextLength }` 同一条公式 | `ReaderTextIndex.kt:215` |

### 由此定下的设计

> **只返回全局偏移，不返回章节元组。**

理由：只要不给 `explicitChapter / explicitOffset`，上面那段冲突检查就整段跳过，
阅读器会用**它自己那套章起始表**把偏移反解成 `(章, 章内偏移)` —— 结果一致，
且**不存在「被拒绝跳转」这条失败路径**。反过来两者都给，一旦两侧章起始表有
任何差异就直接跳不了。

---

## 3. 改动内容

### 3.1 `SearchIndexRepository.resolveLegacyOffset(hit): Int?`（新增）

把命中的「章内坐标」换算成**全书字符偏移**：

| 情况 | 处理 |
|---|---|
| 预览命中（索引章号 0 且有偏移） | 仅 TXT/MD 认：`reader_preview` 就是正文前 2 万字，**偏移即全书偏移**；其余返回 null |
| TXT/MD 逐章命中 | `章节.startOffset + 章内偏移`，**真实字符、精确**；沿用 `TXT_CONTEXT_MAX_BYTES = 8MB` 保护 |
| EPUB 逐章命中 | `LegacyOffsetCodec.chapterStartOffsets(estimatedLengths)[ci] + 章内偏移` —— **原样复刻既有估算口径**，不在这里「改成真实字符」 |
| 其余 / 任何失败 | **返回 null** |

`TxtChapterSlice` 增加 `startOffset` 字段（此前切片只带 title/body，丢了章起始，换算无从下手）。

### 3.2 `SearchViewModel.resolvePreciseOffset(hit): Int?`

薄封装 + `runCatching`：换算失败只降级，**不让点击崩搜索页**。
明确标注「只在点击时对单条命中调用，绝不对整页结果批量调用」——内部要读磁盘。

### 3.3 `SearchScreen`：点击 → 临时查阅 route

```kotlin
scope.launch {
    val offset = viewModel.resolvePreciseOffset(hit)
    val route = offset?.let {
        readerTemporaryRouteForSource(hit.bookId, it, null, null)
    }
    navController.navigate(route ?: "reader/${hit.bookId}")
}
```

- 走 **临时查阅**（`navigationMode=temporary`）而非普通导航 —— 搜索命中是「去看一眼」，
  不是「把阅读进度搬过去」。返回行为因此由既有的临时返回栈接管；
- 换算不出偏移 → 降级为 `reader/{bookId}`，**不伪造 offset=0**；
- 元数据命中（无 `charOffset`）同样走降级分支。

### 3.4 约束核验

`beginTemporaryInspection` 的生产调用点仍**恰好 1 处**（`ReaderRoute`），
入口方只 `navigate(...)`，不在入口处推进协调器状态 —— 与 J1-I.2 定下的纪律一致。

---

## 4. 验证（真实 JUnit XML 计数）

| 项目 | 结果 |
|---|---|
| `:app:compileDebugKotlin` / `:app:compileDebugAndroidTestKotlin` | 均 **EXIT=0** |
| `SearchResultPreciseNavigationTest`（新增） | **5 tests / 0 fail** |
| `SearchViewModelTest` | **13 tests / 0 fail**（原 10 + 新 3） |
| 全量 JVM | **232 suite / 1950 tests / 0 fail / 0 err / 0 skip** |

新增测试覆盖：
- 换算出的偏移**原样**进 route 且回读一致；route 带 `navigationMode=temporary`
- route → `SourceNavigationContract.target` 往返成立（否则阅读器会拒绝跳转）
- null / 负偏移 / 空白 bookId → **null**（不造假位置）
- **`0` 是合法位置并保留**（必须与「换算不出来」区分）
- 只有 (章, 章内偏移) 时既有契约确实拒绝（钉死 S1.4 为什么必须自己算偏移）
- VM：透传 / 换算不出返回 null / 换算抛异常不崩

---

## 5. 改动文件清单

| 文件 | 说明 |
|---|---|
| `data/repository/SearchIndexRepository.kt` | `TxtChapterSlice.startOffset`；新增 `resolveLegacyOffset()`；新增 `isTxtLike()` |
| `ui/viewmodel/SearchViewModel.kt` | 新增 `resolvePreciseOffset()` |
| `ui/screen/search/SearchScreen.kt` | 正文命中点击 → 临时查阅精确跳转；`rememberCoroutineScope` |
| `test/…/ui/navigation/SearchResultPreciseNavigationTest.kt` | 新增，5 项 |
| `test/…/ui/viewmodel/SearchViewModelTest.kt` | +3 项 |

无 schema 变更、无迁移。

---

## 6. 未覆盖项 / TODO

1. **`resolveLegacyOffset` 无 JVM 测试**（依赖 DAO / 解析器 / 磁盘），
   换算公式的两个分支（TXT 真实字符、EPUB 估算量纲）**留给真机或仪器化验证** ——
   这是本片最大的验证缺口，因为跳错位置不会崩溃。
2. **TXT 分章口径可能不一致**：索引用内置 `builtin` 规则分章，若用户设了自定义 TOC 规则，
   阅读器的章序号会与索引不同。由于我们传的是**全局偏移**（与分章规则无关），
   阅读器能按自己的章节表正确落位 —— 这正是选全局偏移而非章节元组的第二个理由。
   但索引时的「第 N 章」标签仍可能与阅读器显示不一致，属已知瑕疵。
3. **EPUB 定位精度受既有估算空间限制**（`LegacyOffsetCodec` 冻结语义），
   非本片引入；历史书签/笔记是同一口径。要提升应整体改造 `LegacyOffsetCodec`，
   而不是在搜索侧打补丁。
4. **S1.5 尚未完成**：本片给出「阅读器内返回」（临时返回栈），
   但「返回**搜索页**且保留查询词与滚动位置」还没做 —— 那是 #57。
5. 真机/视觉验收未做（设备 `c49ac6cf` offline，与 S1.1 同因）。

## 7. SEAM REQUEST

**无。** `feature/reader/**` 与 `ui/navigation/**` **只读未改**
（只新增了调用点，没有改 `ReaderTemporaryRoute` / `SourceNavigationContract` / `LocatorCodec`）。
改动落在搜索链路自身的三个文件 + 测试。

---

## 8. 结论

- 命中点击从「打开书」升级为**跳到命中处**。
- 全程遵守「绝不伪造 offset」纪律：换算不出就降级，不猜、不填 0。
- 选择**只带全局偏移**是有依据的（避开 `resolveChapteredPosition` 的拒绝分支），
  不是随手写的。
- R2 退出条件「搜到内容可精确到达并返回」：**到达**已落地；
  **返回**在阅读器内已由临时返回栈覆盖，**回到搜索页并保留查询**在 S1.5。
