# S07-F4.1 最小修复 — Zcode 执行报告

> 执行 Agent：Zcode　|　实际模型：`deepseek v4.1Flash`
> 工作仓库：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`
> 工作分支：`codex/recover-worktree-20260920`
> 固定比较基线：`origin/main` = `5aed5939600e491843e3c44453921bd6273a233c`
> 复核时 `git rev-parse --short HEAD` = `5aed593`（基线未推进）
> 报告路径：`docs/coordination/agent-results/agent-result-s07-f41-zcode.md`
> 状态：**等待 Codex 主任务复核**（不自宣布 S07 完成，不领取 S08）

---

## 1. 约束遵守

| 约束 | 结果 |
|---|---|
| 不 reset / checkout / clean / stash | 未执行；`git stash list` 为空 |
| 不 stage / commit / push | 未执行；`git diff --cached --name-only` 为空 |
| 不全仓格式化 | 未执行 |
| 不覆盖或整理他人改动 | 只写本轮授权文件，其余 300+ 既有改动原样保留 |
| 不做真机 / 不用 MuMu | 未执行任何 adb 或设备命令 |
| 不修改 S08–S20 | 未触碰 |
| 不读改 Obsidian（Claude / Codex 记忆库） | 未访问 |
| 不使用 `git add -N` | 未使用；未跟踪文件改用 `git diff --no-index` + SHA256 |

---

## 2. 三个问题的逐项修复

### 问题一：UI 与 ViewModel 的 replacement capability 事实源分裂（P1 回归）

**原缺陷**：`readerLoadedContentReplacementAvailability(ReaderLoadedContent.Epub)` 把**所有**
EPUB 固定映射为 `ESTIMATED_COORDINATES`，而 UI 读的是 `pagerEngine.replacementAvailability`。
分页 EPUB 的引擎裁决实际是 `APPLIED / NO_EFFECTIVE_RULES / PARTIALLY_APPLIED /
ALL_SCOPES_OVERSIZED / UNVERIFIED_CHAPTER_LENGTHS`（经 `readerReplacementCapability` **全部
`Available`**），只有 legacy / 非分页路径才被 `effectiveReplacementAvailability` 遮蔽为
`ESTIMATED_COORDINATES`。净结果：分页 EPUB 上 UI 给「单处纠错」入口、保存被 VM 拒绝。

**修复**：删掉按文档类型猜测的函数，改为**单一事实源**。

| 环节 | 实现 |
|---|---|
| 生产层（唯一真源） | `pagerEngine.replacementAvailability` ← `effectiveReplacementAvailability`（`ReaderPagerEngineState.kt:70-78, 287`） |
| UI 消费 | `ReaderScaffold.kt:562` 工具栏摘槽、`ReaderSheetHost.kt:315,330` 规则页门控（**未改**，本来就是对的） |
| 新增发布动作 | `ReaderAction.SetReplacementAvailability(bookId, availability)`（`ReaderModels.kt`） |
| 发布点 | `ReaderScreen.kt` 的 `LaunchedEffect(pagerEngine.replacementAvailability, bid, documentUiState.requestedBookId)` —— 只做搬运，不做解释 |
| VM 消费 | `ReaderViewModel.currentReplacementCapability()`：读 `_replacementAvailability`，未发布 / 书身份不符 ⇒ `SOURCE_UNAVAILABLE` ⇒ 拒绝 |
| 解释函数 | `readerReplacementCapability` —— UI 与 VM **共用同一个**（未改） |

**默认拒绝**：初值 `null`；切书时清空；发布值带 `bookId`，与 `_uiState.value.requestedBookId`
不符即视为未发布。切书后新书自己的发布到达前，一次合法形状的纠错一律被拒 ——
绝不沿用上一本书的 availability。

**纵深防御保留**：`SaveSingleCorrection` 分支仍在 `rulesRepository.execute` **之前**判能力，
判据直接读这个共享事实源（不再退回只依赖 UI 门控）。

**已删除的循环论证测试**：原 `the view model gate uses the same capability mapping the toolbar
uses` 把「UI 会看到 `ESTIMATED_COORDINATES`」当输入写死在期望值里，只证明了同一个函数被调用
两次。已删除，替换为 §3 的 6 条真实 DAO 行为断言 + 1 条「UI 判据 ↔ VM 行为」同值断言（左值由
行为反推，右值是发布输入，不是同一个枚举常量写死两边）。

### 问题二：切书未清除旧书选区（F4 的错误产品契约）

**原缺陷**：切书只调 `.withSelectionSnapshotReleased()`，并用测试
`switching books keeps the active selection untouched but drops the snapshot` 把「活动选区保留」
锁成了产品契约。旧书的 `selectedText` / `selectedRangeStart` / `selectedGlobalOffset` /
`selectedSourceEnd` / `showColorRow` 留在新书，且新书打开消费者时
`withSelectionCaptured()` 会把它们**重新捕获**成一份看似合法的快照。

**修复**：

1. 切书（`bookId` 变化）时 `withSelectionCleared()` + `withSelectionSnapshotReleased()` 同时执行；
2. `sheet` / `noteOpen` / `noteBody` 一并收回（仍是持有旧选区语义的消费者 UI 状态），
   收口位置仍挂在 `ReaderChromeEvent.BookSwitched` reducer 的结果上，**未另造第二套状态机**
   （`BookSwitched` 只改 chrome 可见性，因此消费者状态在它之后显式清理）；
3. `_ruleMutationResult` 与 `_replacementAvailability` 同时作废；
4. 同书无操作重复 `OpenBook` 仍走 `sameBookNoop` 早返回 ⇒ 不误清合法在途快照（反向钉子保留）；
5. **force reload 的取舍**：新增显式判据 `documentIdentityRebuilt = bookChanged || force`。
   理由写在代码注释里：force 重载（错误页重试）会重新走 `documentLoader.load` 并把旧
   `loadedBook` 交给 `release()`，**内容身份确实重建**，旧选区坐标指向被丢弃的文档实例 ——
   因此与切书同样作废。这是事实判断，不是依赖分支顺序碰巧成立。
   （注意 `_replacementAvailability` 只在 `bookChanged` 时清：force 重载书身份不变、
   `LaunchedEffect` 不会重发，清掉就再也填不回来。）

**已反转的错误契约测试**：`switching books keeps the active selection untouched but drops the
snapshot` 已删除，替换为 `switching books clears all five active selection fields` 等 5 条。

### 问题三：外部浏览器与词典提示不真实（F1 Q-3，F4 误记为「已固化」）

**原缺陷**：`NO_HANDLER` 时先提示「正在打开浏览器…」再提示失败（两条互相矛盾）；
在线词典失败复用「未找到可用浏览器」（把用户引向错误的排查方向）。

**修复**：提示改为**结果确定后单条**。`launcher` 调用前不再显示任何成功 / 进行中文案。

| 通道 | launcher 结果 | 提示 | 常量 |
|---|---|---|---|
| 浏览器 | `OPENED` / `OPENED_WEB_FALLBACK` | 已打开浏览器 | `NOTICE_BROWSER_OPENED` |
| 浏览器 | `NO_HANDLER` | 未找到可用浏览器 | `NOTICE_BROWSER_UNAVAILABLE` |
| 系统词典 | `OPENED` | 已调用系统文字工具 | `NOTICE_SYSTEM_DICT_OPENED` |
| 系统词典 | `OPENED_WEB_FALLBACK` | 未找到词典应用，已用在线词典查询 | `NOTICE_DICT_FALLBACK` |
| 系统词典 | `NO_HANDLER` | 未找到可用词典或浏览器 | `NOTICE_DICT_OR_BROWSER_UNAVAILABLE` |
| 在线词典 | `OPENED` / `OPENED_WEB_FALLBACK` | 已打开在线词典 | `NOTICE_ONLINE_DICT_OPENED` |
| 在线词典 | `NO_HANDLER` | 无法打开在线词典，请确认系统浏览器可用 | `NOTICE_ONLINE_DICT_UNAVAILABLE` |
| 任意 | 空选区 | 请先选择要查询的文字（**不调用 launcher**） | `NOTICE_BLANK_SELECTION` |

产品选择：**保留成功提示**（每次只发一条），因为原实现已有成功反馈习惯，去掉属于产品变更、
超出本轮范围。三个 `selection*ResultNotice` 都是 `when` 穷举（无 `else`），新增
`SelectionExternalLaunchResult` 值会编译失败。旧的 `selectionExternalPreJumpNotice` /
`selectionExternalFailureNotice` 与 `ExternalActionKind` 枚举已整体删除（全仓零引用）。

### 附带低风险收口

- **`SelectionActionStore.setActionEnabled()` 拒绝 dormant 写入**：新增
  `if (id in SelectionActions.DORMANT_IDS) return`。此前读侧 `pickableDefs()` 已挡住，
  写侧从未拒绝 —— 旧版本写下的 `edit` 或绕过设置页的直接调用能把它塞进持久化集合。
  **未实现 S08 的 edit**。
- **`RenderMarkdownUnit` 的两个裸 Int 默认值（`blockGlobalOffset: Int = -1` /
  `chapterBase: Int = 0`）**：见 §8 SEAM REQUEST —— 该文件不在本轮授权边界内，保留为非阻断 P3。
- **`addStyle` 性能**：仍未测量，转交 WorkBuddy，本轮未重构。

---

## 3. 分页 EPUB 允许 / legacy EPUB、Markdown 拒绝的真实 DAO 行为证据

全部为**行为测试**：真实 `documentLoader.load` 加载 → 真实
`ReaderAction.ExecuteRuleCommand` 派发 → 真实 `RulesRepository` → 真实
`FakeReaderCorrectionDao` 行数断言。

| # | 场景 | 发布值 | 期望 | 断言 | 结果 |
|---:|---|---|---|---|---|
| 1 | 分页 EPUB + `APPLIED` | `APPLIED` | 落库 | `Saved` + DAO 1 行 + `source_start=100/source_end=102` | PASS |
| 2 | 分页 EPUB + `NO_EFFECTIVE_RULES` | `NO_EFFECTIVE_RULES` | 落库 | `Saved` + DAO 1 行 | PASS |
| 3 | legacy EPUB + `ESTIMATED_COORDINATES` | `ESTIMATED_COORDINATES` | 拒绝 | `NotAnchorable`（reason 含「单处纠错」）+ DAO **0** 行 | PASS |
| 4 | legacy EPUB + `PAGER_ENGINE_DISABLED` | `PAGER_ENGINE_DISABLED` | 拒绝 | `NotAnchorable` + DAO **0** 行 | PASS |
| 5 | Markdown + `NON_SOURCE_COORDINATES` | `NON_SOURCE_COORDINATES` | 拒绝 | `NotAnchorable` + DAO **0** 行 | PASS |
| 6 | 未发布（新书打开、引擎未裁决） | — | 拒绝 | `NotAnchorable` + DAO **0** 行 | PASS |
| 7 | 切书后未重新发布 | — | 拒绝 | `NotAnchorable` + DAO **0** 行 | PASS |
| 8 | 别书迟到发布不得覆盖当前书 | 当前书 `APPLIED`，另发 `ESTIMATED` | 落库 | `Saved` + DAO 1 行 | PASS |
| 9 | TXT + `APPLIED` | `APPLIED` | 落库 | `Saved` + DAO 1 行 + 四字段逐项 | PASS |
| 10 | 能力放行但 `findText` ≠ 选区 | `APPLIED` | 拒绝 | `NotAnchorable` + DAO **0** 行 | PASS |
| 11 | 能力放行但无锚点 | `APPLIED` | 拒绝 | `NotAnchorable` + DAO **0** 行 | PASS |

**拒绝发生在 repository 写入前**：能力门在 `if (command == null) return@io` 之前，
`rulesRepository.execute` 从未被调用，因此 DAO 行数 0 是「没写」而不是「写了再报错」；
提示走 `_ruleMutationResult = NotAnchorable(...)`，**无任何成功文案**。

**同值断言（非循环论证）**：`the view model decides on exactly the availability the producer
published` —— 左值 `vmAccepted` 由**行为**反推（DAO 是否落库），右值
`readerReplacementCapability(PARTIALLY_APPLIED)` 是发布输入。两者相等即证明两端同源。

---

## 4. 切书后旧选区无法重新捕获的行为证据

| # | 用例 | 断言 | 结果 |
|---:|---|---|---|
| 1 | `switching books clears all five active selection fields` | 五字段复位（`selectedText=""`、两个起点 `-1`、`sourceEnd=-1`、`showColorRow=false`） | PASS |
| 2 | `switching books releases the consumer snapshot` | `selectionSnapshot == null` | PASS |
| 3 | `a fresh selection cannot be recaptured from the previous book after a switch` | 切书后打开 RULES，快照仍为 null、活动选区为空 | PASS |
| 4 | `the consumer UI state is closed on a book switch` | `sheet`/`noteOpen`/`noteBody` 全部收回 | PASS |
| 5 | `reopening the same book does not release an in flight snapshot`（反向钉子） | 同书重开保留快照与活动选区 | PASS |
| 6 | `a force reload rebuilds the document so it clears the selection too` | force 重载清选区 + 释放快照 | PASS |
| 7 | `a book switch clears the stale rule feedback too` | `ruleMutationResult == null` | PASS |

「新书保存高亮 / 笔记 / 灵感 / 纠错不能消费旧书选区」由 1+3 联合保证：消费路径读的都是
`screenState` 的这五个字段与 `selectionSnapshot`，两者在切书时都已归零。

---

## 5. 修改文件与 hunk

| 文件 | 类型 | hunk 目的 |
|---|---|---|
| `ui/viewmodel/ReaderViewModel.kt` | 修改 | ①删 `readerLoadedContentReplacementAvailability`，新增 `PublishedReplacementAvailability` 数据类；②新增 `_replacementAvailability` 状态；③新增 `SetReplacementAvailability` 分支（含书身份校验）；④`currentReplacementCapability()` 改读发布值 + 默认拒绝；⑤`SaveSingleCorrection` 门控注释更新；⑥`openBook()` 拆分 `bookChanged` / `documentIdentityRebuilt` 两个判据 |
| `ui/viewmodel/ReaderModels.kt` | 修改 | 新增 `ReaderAction.SetReplacementAvailability` + import |
| `ui/screen/reader/ReaderScreen.kt` | 修改 | 新增发布 `LaunchedEffect`（三个 key：availability / bid / requestedBookId） |
| `ui/screen/reader/ReaderSelectionExternalActions.kt` | 修改 | ①删 3 个 opening 常量 + `ExternalActionKind` + 2 个旧函数；②新增 3 个 `selection*ResultNotice` 穷举函数；③两个 dispatcher 改为结果确定后单条提示 |
| `data/settings/SelectionActionStore.kt` | 修改 | `setActionEnabled` 新增 dormant 拒绝 |
| `test/.../ReaderSingleCorrectionCapabilityTest.kt` | 改写 | 11 → **14** 条，全部真实 DAO 行为断言 |
| `test/.../ReaderSelectionSnapshotTest.kt` | 改写 | 21 → **24** 条，第 8 组重写为「切书整体作废」 |
| `test/.../ReaderSelectionExternalActionsTest.kt` | 改写 | 14 → **18** 条，提示矩阵 |
| `test/.../SelectionActionSettingsTest.kt` | 修改 | +3 条（`SelectionActionStoreTest` 13 → **16**），断言打在**落盘原始集合**上 |
| `test/.../EpubSingleCorrectionReachabilityTest.kt` | 修改 | KDoc：删除对已删函数的引用，补 F4.1 事实源更正（分页 EPUB 可达且应可达） |

**未修改**：`ReaderScreenState.kt`（本轮曾列入允许边界，最终 `withSelectionCleared` /
`withSelectionCaptured` / `withSelectionSnapshotReleased` 的既有语义已足够，**哈希与改动前
逐字节一致**）。

---

## 6. before / after 同文件集证据

同一文件清单（10 个文件）、同一命令（`sha256sum`），仓库外临时目录 `/tmp/s07f41/`：

```
文件（main，5）                                       before(前16)        after(前16)
ui/viewmodel/ReaderViewModel.kt                      1e83dd39ba89b068    19e6fb71fc5fb7ec   CHANGED
ui/viewmodel/ReaderModels.kt                         b278d236f1c0a0dd    256e7e0a3827eb63   CHANGED
ui/screen/reader/ReaderScreen.kt                     0eb7c904ef2ee337    64990ceeb7d333e3   CHANGED
ui/screen/reader/ReaderScreenState.kt                974ddeddc6a3c7c1    974ddeddc6a3c7c1   IDENTICAL
ui/screen/reader/ReaderSelectionExternalActions.kt   e1a6edb0f42870ec    9a63d5c61f8b9408   CHANGED
data/settings/SelectionActionStore.kt                d1d011b7fef4ded9    f84f7ad318448f8e   CHANGED

文件（test，5）
ui/viewmodel/ReaderSingleCorrectionCapabilityTest.kt cea979a8a6b77bcd    919268dfde24f6bc   CHANGED
ui/viewmodel/ReaderSelectionSnapshotTest.kt          e9c2785d17cdfef5    9869fd0a2d3d2318   CHANGED
ui/screen/reader/ReaderSelectionExternalActionsTest.kt 906342e685b92c3b 7303f94ad72240a6  CHANGED
data/settings/SelectionActionSettingsTest.kt         0874a5057e9ccceb    ef3693c3f619fd48   CHANGED
```

（`EpubSingleCorrectionReachabilityTest.kt` 在 after 阶段追加进清单，故不在 before 表中；
其当前 SHA256 = `a3164a04c8c9e2d7d9dfe65fe38258b581197656ebfabce898db6c7724be49c0`，
改动仅为 KDoc（F3.1 措辞更正 + F4.1 事实源更正），测试方法体未变，XML tests 仍为 8。）

**未跟踪文件的补丁证据（不使用 `git add -N`）**：

```
ReaderSingleCorrectionCapabilityTest.kt  -> git diff --no-index: 1 file changed, 580 insertions(+)
ReaderSelectionSnapshotTest.kt           -> git diff --no-index: 1 file changed, 555 insertions(+)
```

**边界外文件自检（mtime + SHA256，本轮时段为 2026-09-20 23:50 ～ 2026-09-21 01:24）**：

| 文件 | mtime | SHA256(前16) | 判定 |
|---|---|---|---|
| `ReaderActions.kt` | 2026-09-20 17:34:25 | `9e6441c704d9264c` | 未触碰 |
| `ReaderScaffold.kt` | 2026-09-20 17:35:47 | `7955c1fe69e2b141` | 未触碰 |
| `ReaderMarkdownRender.kt` | 2026-09-20 20:55:15 | `81a07cd47934ee3a` | 未触碰 |
| `ReaderSheetHost.kt` | 2026-09-20 18:28:48 | `b330a66d7549be1d` | 未触碰（与 F2.4/F4 登记值一致） |
| `ReaderSelectionToolbar.kt` | 2026-09-20 23:26:33 | `04301c010537c41a` | 未触碰（与 F4 登记值一致） |
| `ReaderPagerEngineState.kt` | 2026-09-19 23:58:15 | `ea29e7886424cb4d` | 未触碰 |
| `MarkdownScrollHighlight.kt` | 2026-09-20 23:32:51 | `89bd78e2c77d637c` | 未触碰（与 F4 登记值一致） |
| `ReaderSearchIndexCoordinator.kt` | 2026-09-17 20:21:42 | `2540de7b4a0a5da8` | 未触碰 |
| `SelectionActionSettings.kt` | 2026-09-20 09:05:20 | `2a906f11ee532423` | 未触碰 |
| `ReplacedChapterSource.kt` | 2026-09-19 23:58:15 | `3d75aaa58984302c` | 未触碰 |
| `ReaderConsumerAndActionGateTest.kt` | 2026-09-20 23:21:50 | `942c57bdf71de256` | 未触碰 |
| `ReaderCopyNoticeTest.kt` | 2026-09-20 09:04:34 | `7250f872ccedd799` | 未触碰 |
| `MarkdownUnknownChapterLengthTest.kt` | 2026-09-20 23:29:34 | `810596f8ca6bb72b` | 未触碰 |

**结论**：全部边界外文件 mtime 早于本轮窗口，且哈希与既有登记值一致 → 无越界修改。
未触发任何 `SEAM REQUEST` 强制项（唯一一项为非阻断 P3，见 §8）。

---

## 7. 测试命令、退出码、XML 计数

所有命令在 `android/` 下执行，均带 `--rerun` 以避免 UP-TO-DATE 假绿。

### 7.1 直接相关定向测试

```bash
./gradlew --console=plain :app:compileDebugKotlin          # 退出码 0
./gradlew --console=plain :app:compileDebugUnitTestKotlin  # 退出码 0
./gradlew --console=plain :app:testDebugUnitTest --rerun \
 --tests "…ReaderSingleCorrectionCapabilityTest" \
 --tests "…ReaderSelectionSnapshotTest" \
 --tests "…ReaderSelectionExternalActionsTest" \
 --tests "…SelectionActionStoreTest"                        # 退出码 0
```

| 测试类 | tests | failures | errors | skipped |
|---|---:|---:|---:|---:|
| `ui.viewmodel.ReaderSingleCorrectionCapabilityTest` | 14 | 0 | 0 | 0 |
| `ui.viewmodel.ReaderSelectionSnapshotTest` | 24 | 0 | 0 | 0 |
| `ui.screen.reader.ReaderSelectionExternalActionsTest` | 18 | 0 | 0 | 0 |
| `data.settings.SelectionActionStoreTest` | 16 | 0 | 0 | 0 |

### 7.2 S07 代表性回归（11 类，`--rerun`，退出码 0）

| 测试类 | 归属 | tests | F | E | S |
|---|---|---:|---:|---:|---:|
| `ui.screen.reader.ReaderReplacementCapabilityTest` | 能力映射 | 15 | 0 | 0 | 0 |
| `ui.screen.reader.EpubSingleCorrectionReachabilityTest` | F3.1 | 8 | 0 | 0 | 0 |
| `ui.screen.reader.ReaderSelectionSnapshotSemanticsTest` | F2.4 | 12 | 0 | 0 | 0 |
| `ui.screen.reader.ReaderConsumerSnapshotWiringGuardTest` | F2.4 守卫 | 9 | 0 | 0 | 0 |
| `ui.screen.reader.ReaderConsumerAndActionGateTest` | F4 守卫 | 6 | 0 | 0 | 0 |
| `ui.screen.reader.ReaderCopyNoticeTest` | F1 | 3 | 0 | 0 | 0 |
| `ui.screen.reader.MarkdownBlockSelectionLocatorTest` | F3.1 | 10 | 0 | 0 | 0 |
| `ui.viewmodel.ReaderSingleCorrectionCapabilityTest` | F4.1 | 14 | 0 | 0 | 0 |
| `ui.viewmodel.ReaderSelectionSnapshotTest` | F4.1 | 24 | 0 | 0 | 0 |
| `ui.screen.reader.ReaderSelectionExternalActionsTest` | F4.1 | 18 | 0 | 0 | 0 |
| `data.settings.SelectionActionStoreTest` | F4.1 | 16 | 0 | 0 | 0 |
| **合计** | | **135** | **0** | **0** | **0** |

**请求 11 类 == XML 11 个文件，缺失类 0，零匹配 0。**

### 7.3 汇总集

```bash
./gradlew --console=plain :app:testDebugUnitTest --rerun \
 --tests "com.creationreadingassistant.ui.screen.reader.*" \
 --tests "com.creationreadingassistant.ui.viewmodel.*" \
 --tests "com.creationreadingassistant.data.settings.*"
```

**退出码 0**，`BUILD SUCCESSFUL`：

| 项 | 数 |
|---|---:|
| 测试类 | 126 |
| tests | 1081 |
| failures | **0** |
| errors | 0 |
| skipped | 0 |

**关于 S14 WebDAV**：F4 汇总运行中失败的
`ProfileWebDavManagerTest > downloadRestore fetches and imports backup json`
本轮**已被包含在汇总集内且通过**（`ui.viewmodel.*` 通配覆盖该类）。如实记录：
本轮**未修改**该类及其产品文件（`WebDavBackup.kt` / `WebDavSubPage.kt` /
`ProfileWebDavManager.kt` 均未触碰），其 F4 的失败是 JVM 主调度器环境问题，本轮未复现。
**该类的通过不作为 S07 的证据，也不作为 S14 已解决的结论** —— 归属仍由 Codex 裁决。

### 7.4 行为测试 vs 结构守卫（分开统计）

| 类别 | 类 | tests |
|---|---|---:|
| **结构 / 源码守卫**（读源文件文本、反射枚举集合） | 2（`ReaderConsumerAndActionGateTest` 6、`ReaderConsumerSnapshotWiringGuardTest` 9） | 15 |
| **行为 / 语义测试**（真实 loader、真实派发、真实 DAO、真实状态断言） | 9 | 120 |
| 合计 | 11 | 135 |

本轮**新增/改写的 72 条测试全部是行为测试**（真实 `documentLoader.load` + 真实
`ExecuteRuleCommand` 派发 + 真实 `FakeReaderCorrectionDao` 行数 / 真实 `screenState` 断言），
**没有一条是源码字符串守卫**。

### 7.5 变异验证（证明新测试咬得住）

每次变异后从 `/tmp/s07f41/*.after.bak` 还原并复核哈希。

| 变异 | 内容 | 失败用例 |
|---|---|---|
| A | `currentReplacementCapability()` 退回按 `ReaderLoadedContent` 类型猜 | **5 FAILED**（分页 EPUB 两条、未发布、切书失效、同值断言） |
| B | 切书退回只 `.withSelectionSnapshotReleased()`（F4 行为） | **4 FAILED**（五字段、force 重载、重新捕获、消费者 UI 状态） |
| C | 浏览器恢复「先报正在打开、失败再报错」 | **2 FAILED** |
| D | 在线词典失败复用浏览器文案 | **1 FAILED** |
| E | 移除 dormant 写侧拒绝 | **1 FAILED**（第 1 次未咬住 → 见下） |

**变异 E 的方法学修正（重要）**：第一版 dormant 测试断言 `store.settings`（读侧），
而读侧 `SelectionActions.sanitize()` 会兜底过滤 dormant id ⇒ 写侧漏了也被「救绿」，
变异 E 首次运行 BUILD SUCCESSFUL。已改为断言
`settingsStore.preferencesDataStore.data.first()[stringSetPreferencesKey(...)]`
（**落盘原始集合**，绕过 sanitize），并补一条对照用例
`a non dormant action still writes to the raw set` 证明该断言不是恒真。
重跑变异 E → **1 FAILED**。

---

## 8. 未运行项与 SEAM REQUEST

| 项 | 说明 |
|---|---|
| 真机验收 | **未做**（本轮禁止）。`adb` / MuMu 全程未使用 |
| `androidTest` 插桩测试 | 未运行 |
| `:app:lintDebug` | 未运行 |
| `assembleDebug` / `assembleRelease` | 未运行 |
| **SEAM REQUEST（非阻断 P3）** | `ReaderMarkdownRender.kt` 的 `RenderMarkdownUnit` 仍有两个裸 Int 默认值（`blockGlobalOffset: Int = -1`、`chapterBase: Int = 0`）。该文件**不在本轮建议边界内**，且删除默认值需同步核对 2 个调用点（`ReaderMarkdownRender.kt:206`、`ReaderContentHostMarkdownBranch.kt:168`）—— 未越界修改，保留为非阻断 P3。 |
| WorkBuddy 待办 | ①分页 EPUB 单处纠错的真机全链（保存 → 重开 → 回显）；②外部浏览器/词典在**无 handler** 真机上的提示是否与本文矩阵一致；③小屏 / 横屏 / 大字体下五个动作的拥挤与截断；④TalkBack 焦点顺序与手柄语义；⑤切书后旧选区真机确认（工具条不带旧文字、旧摘抄不进新书）；⑥长章 + 多条高亮 + TTS 同开时的滚动流畅度（`addStyle` 额外遍历代价未测） |

---

## 9. 双轴结论

### Standards 轴（工程质量、可维护性、测试纪律）

**问题数：3**（本轮全部处理或降级）

| 级别 | 数量 | 项 |
|---|---:|---|
| P0 | 0 | — |
| P1 | 0 | — |
| P2 | 0 | — |
| P3 | 3 | ①`RenderMarkdownUnit` 两个裸 Int 默认值（**SEAM REQUEST**，未越界）；②`consumesSelection` 的反射 + 源码守卫相对编译期穷举属重复保险（无害，保留）；③`addStyle` 额外遍历的性能未测（转交 WorkBuddy） |

**Standards 最严重项：P3-①（SEAM REQUEST）**。本轮的方法学改进是：
F4 的循环论证测试已删除；dormant 断言从「读侧」改为「落盘原始集合」，
避免被读侧 sanitize 救绿 —— 这是本轮发现并修正的唯一一处**测试本身不可靠**的问题。

### Spec 轴（用户可感知行为、产品契约、数据正确性）

**问题数：3**（本轮全部关闭）

| 级别 | 数量 | 项 |
|---|---:|---|
| P0 | 0 | — |
| P1 | 1 | 分页 EPUB 单处纠错被 VM 误拒（**已修**：两端共读生产层 availability；3 条真实落库断言 + 变异 A 证明） |
| P2 | 2 | ①切书后旧选区可被重新捕获（**已修**：五字段 + 快照 + 消费者 UI 状态整体作废；7 条行为断言 + 变异 B）；②外部动作提示不真实（**已修**：结果确定后单条提示矩阵；4 条矩阵断言 + 变异 C/D） |
| P3 | 0 | — |

**Spec 最严重项：P1 分页 EPUB 单处纠错被误拒**。这是 F4 引入的**用户可见功能回归**：
UI 提供入口、保存被拒且理由是「格式不支持」。F4.1 的修复不是放宽门控，而是让两端读**同一个**
事实源 —— 纵深防御保留，且比 F4 更强（未发布 / 切书 / 书身份不符一律默认拒绝）。

---

## 10. 是否已达到「可交 WorkBuddy 真机验收」

**答：本轮三个问题已在静态与 JVM 证据层面全部关闭，但本轮仍不声明"可以交真机"。**

理由：

1. 本轮**未做任何真机验收**（任务禁止），§8 的 WorkBuddy 清单全部仍是未验证项；
2. 历轮从未在真机上验证过布局拥挤 / 截断、TalkBack 语义、无 handler 真机回退；
3. **分页 EPUB 单处纠错**是本轮才恢复的能力，其真机全链（保存 → 关闭 → 重开 → 回显）
   从未跑过 —— 这正是 Qoder F4 复核 SP-6 第 1 条点名「绝不能写成已通过」的项；
4. 外部动作的提示矩阵只覆盖了纯函数与 dispatcher 层，真机上「系统真的没有浏览器 /
   没有 PROCESS_TEXT handler」时的实际文案未验证。

**因此结论是：S07-F4.1 范围内的三个问题已修复并自证，是否放行 WorkBuddy 真机验收
由 Codex 主任务复核后决定。** 本报告不写"发布完成"，不领取 S08。

---

## 11. 回传

- Agent 与实际模型：**Zcode / `deepseek v4.1Flash`**
- 报告绝对路径：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919\docs\coordination\agent-results\agent-result-s07-f41-zcode.md`
- 问题数：Standards 3（最严重 P3-①，SEAM REQUEST）、Spec 3（最严重 P1，已修并证明）
- 测试：定向 4 类 72 项 + 回归 11 类 135 项 + 汇总 126 类 1081 项，**全部 0 failure / 0 error / 0 skipped**，退出码 0
- index：`git diff --cached --name-only` = 0；`git stash list` = 空；HEAD 仍 `5aed593`
- 是否修改报告以外文件：**否**（仅 §5 列出的 10 个文件，全部在授权边界内）
- **等待 Codex 主任务复核，未领取下一片；S08 及其余队列保持 HOLD**
