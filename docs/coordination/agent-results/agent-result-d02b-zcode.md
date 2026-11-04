# D-2-B 实现报告：阅读设置页级作用域说明

> 执行 Agent：Zcode　|　实际模型：`deepseek v4.1Flash`
> 工作仓库：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`
> 分支：`codex/recover-worktree-20260920`
> 固定基线：`origin/main` = `5aed5939600e491843e3c44453921bd6273a233c`
> 开工与收工 `git rev-parse --short HEAD` = `5aed593`（基线未推进）
> 前置：`agent-design-d02b-zcode.md`（Zcode 设计）+ `agent-review-d02b-design-qoder.md`（Qoder 复核，方案 4′）
> 报告路径：`docs/coordination/agent-results/agent-result-d02b-zcode.md`
> 状态：**等待 Codex 与 Qoder 复核**

---

## 1. 最终结论

**CANDIDATE PASS**

任务书 12 条通过条件逐条满足：

| # | 通过条件 | 结果 | 证据 |
|---:|---|---|---|
| 1 | 产品代码只修改 `ReaderSettingsSheet.kt` | ✅ | §2 / §15 |
| 2 | ROOT 不显示页级提示 | ✅ | 纯函数 `ROOT -> null`；Compose 用例 `rootPageShowsNoScopeNoticeButMarksPageTurnSpeedAsShared` |
| 3 | 根页翻页速度明确标记所有书籍共用 | ✅ | `reader-page-turn-speed-global-note` + 文案「全局设置，所有书籍共用」 |
| 4 | BOOK 下五个子页面文案全部正确 | ✅ | JVM 逐页断言 + Compose 五页遍历 |
| 5 | GLOBAL 文案采用 Codex 修正版 | ✅ | 「本页修改将保存为全局默认；其他书已有的单书设置不会被清除」 |
| 6 | 没有逐行徽标 | ✅ | 未新增任何字段级徽标；仅页级 1 条 + 根页翻页速度 1 条 |
| 7 | 没有修改路由、存储、枚举或公共组件 | ✅ | §15（14 个禁改文件哈希逐一比对） |
| 8 | JVM 定向测试通过 | ✅ | 2 类 / 36 项 / 0 失败，退出码 0 |
| 9 | AndroidTest 编译通过 | ✅ | 退出码 0 |
| 10 | 主 Kotlin 编译通过 | ✅ | 退出码 0 |
| 11 | 无 P0/P1/P2 | ✅ | §12（P0/P1/P2 = 0/0/0） |
| 12 | 无越界修改 | ✅ | §15 |

---

## 2. 实际修改文件

**产品代码 1 个（授权范围内）**：

| 文件 | 状态 | before(前16) | after(前16) | 行数变化 |
|---|---|---|---|---|
| `ui/screen/reader/sheets/ReaderSettingsSheet.kt` | ` M` | `97fe297af343c1f8` | `43134fd58db3e8a5` | +173 / −25 |

**测试代码 2 个（授权范围内）**：

| 文件 | 状态 | before(前16) | after(前16) | 行数变化 |
|---|---|---|---|---|
| `test/.../sheets/ReaderSettingsScopeNoticeTest.kt` | `??`（新增） | — | `48e20ee4a50c1b63` | +279 |
| `androidTest/.../sheets/ReaderSettingsSheetTest.kt` | ` M` | `5081894f5367a98d` | `84229fb8ff6ede9a` | +110 |

**报告 1 个**：`docs/coordination/agent-results/agent-result-d02b-zcode.md`（本文件）。

`git diff --stat`（仅授权跟踪文件）：

```
 .../reader/sheets/ReaderSettingsSheetTest.kt       | 110 +++++++++++++
 .../ui/screen/reader/sheets/ReaderSettingsSheet.kt | 173 ++++++++++++++++++---
 2 files changed, 258 insertions(+), 25 deletions(-)
```

新增测试文件（未跟踪，`git diff --no-index` 证据）：

```
ReaderSettingsScopeNoticeTest.kt  -> 1 file changed, 279 insertions(+)
```

**未新增测试文件以外的新文件**；未修改任何公共组件、Gradle、Manifest、Room/DAO/DB。

---

## 3. 页级映射函数

位置：`ReaderSettingsSheet.kt` 顶部（import 之后、`SettingsSheet` 之前）。
纯函数，不依赖 Compose 状态、不读 DataStore、不修改任何设置：

```kotlin
internal fun readerSettingsScopeNotice(
    page: ReaderSettingsPage,
    scope: ReaderSettingsScope,
): String? = when (scope) {
    ReaderSettingsScope.BOOK -> when (page) {
        ReaderSettingsPage.ROOT -> null
        ReaderSettingsPage.TYPOGRAPHY,
        ReaderSettingsPage.ADVANCED,
        -> "本页设置只对当前书籍生效"

        ReaderSettingsPage.PAGING ->
            "除阅读模式、翻页效果和屏幕方向外，本页其他设置均为全局设置，对所有书籍生效"

        ReaderSettingsPage.DISPLAY ->
            "除沉浸模式、进度条、菜单隐藏和页眉页脚开关外，本页其他设置均为全局设置，对所有书籍生效"

        ReaderSettingsPage.EYE_CARE ->
            "本页均为全局设置，对所有书籍生效"
    }

    ReaderSettingsScope.GLOBAL -> when (page) {
        ReaderSettingsPage.ROOT -> null
        ReaderSettingsPage.TYPOGRAPHY,
        ReaderSettingsPage.PAGING,
        ReaderSettingsPage.DISPLAY,
        ReaderSettingsPage.EYE_CARE,
        ReaderSettingsPage.ADVANCED,
        -> "本页修改将保存为全局默认；其他书已有的单书设置不会被清除"
    }
}
```

**设计属性**：

1. **`when` 穷尽 `ReaderSettingsPage` 且无 `else`** —— 新增页面成员时**编译失败**，
   迫使维护者显式作答。有结构守卫用例断言函数体内不含 `else ->` 且显式列出 `ROOT` 分支。
2. **BOOK 用例外清单表述** —— 新增一个**全局**字段时句子自动仍然正确；
   只有新增**本书可覆盖**字段时才必须更新句子（守卫盯这个方向）。
3. **例外清单与该页事实双向对齐** —— 翻页页 3 项、显示页 4 项、护眼页 0 项（全页全局）、
   排版页与高级页无例外（全部本书可覆盖）。

---

## 4. 最终中文文案

### 4.1 页级说明（BOOK 作用域）

| 页面 | 文案 |
|---|---|
| 根页（阅读设置） | **不显示**（已有「只改本书 / 改全局默认」胶囊） |
| 排版 | `本页设置只对当前书籍生效` |
| 翻页与按键 | `除阅读模式、翻页效果和屏幕方向外，本页其他设置均为全局设置，对所有书籍生效` |
| 显示与页眉页脚 | `除沉浸模式、进度条、菜单隐藏和页眉页脚开关外，本页其他设置均为全局设置，对所有书籍生效` |
| 护眼与阅读提醒 | `本页均为全局设置，对所有书籍生效` |
| 排版引擎兼容 | `本页设置只对当前书籍生效` |

### 4.2 页级说明（GLOBAL 作用域）

五个子页**统一一句**（根页仍不显示）：

```
本页修改将保存为全局默认；其他书已有的单书设置不会被清除
```

**这是 Codex 修正版**，已按任务书要求**替换** Qoder 报告 §8.2 的
「本页设置会写入全局默认，影响所有书」—— 后者不准确：修改全局默认**不会**清除
其他书已存在的 per-book override。测试对旧文案做了反向断言（`globalScopeNoticeUsesTheCorrectedCopyOnSubPages`
与 `the global copy makes no promise it cannot keep`）。

### 4.3 根页翻页速度说明

```
全局设置，所有书籍共用
```

位于「翻页速度」滑块**正下方**，`start/end = 14.dp` 与滑块行缩进对齐形成视觉关联；
**未**塞进滑块标题；**未**写成「系统设置」；**未**设置额外 `contentDescription`
（文字本身可见，TalkBack 自然朗读）。

### 4.4 文案约束（已由测试钉住）

| 约束 | 验证 |
|---|---|
| 不含 Markdown 星号 `**` | `no notice uses markdown emphasis` |
| 不含技术词（override / DataStore / scope / router / 覆盖项） | `no notice exposes implementation terms` |
| 不含「系统设置」（`brightness` 是阅读窗口亮度，不是系统设置） | `no notice describes the app brightness setting as a system setting` |
| 不以句号结尾（统一短语式，避免测试仅靠标点区分） | `no notice ends with a full stop` |

---

## 5. Compose 布局结构

```
ReaderSheetScaffold(title = page.title, onBack = …)   ← 未改动（ColumnScope）
└── AnimatedContent(targetState = page, modifier = fillMaxSize())   ← 动画保留
    └── Column(Modifier.fillMaxSize())
        ├── readerSettingsScopeNotice(target, scope)?.let { ScopeNoticeRow(it) }   ← 可选，自适应高度
        └── Column(Modifier.weight(1f))                                            ← 拿到有限剩余高度
            └── when (target) { … 原五个页面 + SettingsRoot … }                     ← 内容未变
```

要点（对应任务书 §二 的每条要求）：

| 要求 | 实现 |
|---|---|
| ROOT 不显示 | 纯函数返回 `null` → `let` 不渲染 |
| 位于内容之前、无需滚动即可见 | 在 `Column` 内、页面内容**之前**渲染 |
| `Info` 图标 + 普通可见文本 | `Icon(Icons.Outlined.Info, contentDescription = null)` + `Text` |
| 参考 `ThemeSheet` 形态但不改它 | 同样用 `Icon + Text`；`ReaderThemeSheet.kt` **未修改**（哈希不变） |
| 使用当前 MaterialTheme | `MaterialTheme.colorScheme.primary` / `onSurfaceVariant`、`typography.labelSmall` |
| 不使用 Markdown | 纯 `Text`，无 `AnnotatedString` |
| 不设置覆盖可见文字的 `contentDescription` | 图标为 `null`；文本节点无额外语义 |
| `testTag("reader-settings-scope-notice")` | 加在提示容器 `Row` 上 |
| 允许换行、不强制单行省略 | `Text` 未设 `maxLines` / `overflow` |
| 正文仍占满剩余空间并正常滚动 | 子页面容器 `weight(1f)`；各页内部仍是 `LazyColumn(Modifier.fillMaxSize())` |
| 不遮挡标题 / 返回按钮 / 第一项 | 提示在 `ReaderSheetScaffold` 的内容区（标题栏之下）渲染 |
| `Column + Box(weight)` 高度正确 | 用 `Column + weight(1f)`（等价且更简单），页面拿到**有限且正确**的剩余高度 |
| `AnimatedContent` 动画保留 | `targetState` / `transitionSpec` / `label` 逐字未改（`:178-186`） |
| `BackHandler` 行为不变 | `:171` 逐字未改 |

**为什么不用 `Box(weight)`**：`weight` 只能在 `ColumnScope` 内使用，任务书的
`Column + Box(weight)` 建议在语法上需要 `Box(Modifier.weight(1f))`。
我采用了等价且更直接的 `Column(Modifier.weight(1f))` —— 同样给子页面分配有限剩余高度，
且少一层容器。任务书允许「等价且更安全的 Compose 布局」。

---

## 6. 根页翻页速度说明

| 项 | 实现 |
|---|---|
| 位置 | 「快捷排版」微岛内，紧跟 `MicroTrackSliderRow(title = "翻页速度")` 之后（`:216-235` 区块） |
| 文案 | `全局设置，所有书籍共用` |
| testTag | `reader-page-turn-speed-global-note` |
| 视觉关联 | `padding(start = 14.dp, end = 14.dp, top = 2.dp, bottom = 4.dp)`，与滑块行缩进一致 |
| 未修改公共组件 | `MicroTrackSliderRow` 及其下层 `CompactSliderRow` 未动 |
| 未塞进标题 | `title = "翻页速度"` 保持原样 |
| 未写「系统设置」 | 文案为「全局设置，所有书籍共用」 |
| 无重复 `contentDescription` | `Text` 未设语义；TalkBack 朗读可见文字 |
| ROOT 其他项目无新提示 | 字号 / 行距 / 翻页效果 / 分组导航均未加提示 |

**为什么不能省略**（任务书 §二 明确要求）：`pageTurnSpeed` 是全局字段（不在
`ReaderOverrideKey`），却位于「只改本书」作用域胶囊下方，容易被误认为只影响当前书。
根页有胶囊**不构成**对该行影响范围的说明。

---

## 7. JVM 测试结果

### 7.1 命令

```powershell
.\gradlew.bat --console=plain :app:testDebugUnitTest `
  --tests "com.creationreadingassistant.ui.screen.reader.sheets.ReaderSettingsScopeNoticeTest" `
  --tests "com.creationreadingassistant.data.settings.ReaderSettingsRouterTest" `
  --rerun
```

| 项 | 值 |
|---|---|
| 退出码 | **0** |
| 结果 | `BUILD SUCCESSFUL in 5s` |
| 编译警告 | **0** |

### 7.2 测试统计

| 测试类 | tests | failures | errors | skipped |
|---|---:|---:|---:|---:|
| `ui.screen.reader.sheets.ReaderSettingsScopeNoticeTest`（**新增**） | 20 | 0 | 0 | 0 |
| `data.settings.ReaderSettingsRouterTest`（既有，未改动） | 16 | 0 | 0 | 0 |
| **合计（2 类）** | **36** | **0** | **0** | **0** |

**请求 2 类 == XML 2 个文件；缺失类 0；零匹配 0。**
XML 路径：`android/app/build/test-results/testDebugUnitTest/TEST-com.creationreadingassistant.*.xml`
XML mtime `00:18:21`，与运行时刻一致（非陈旧文件）。

### 7.3 `ReaderSettingsScopeNoticeTest` 覆盖的 13 组契约

| # | 契约 | 用例 |
|---:|---|---|
| 1 | ROOT + BOOK 返回 null | `the root page has no notice under the book scope` |
| 2 | ROOT + GLOBAL 返回 null | `the root page has no notice under the global scope` |
| 3 | BOOK + TYPOGRAPHY 精确文案 | `typography under the book scope says only this book` |
| 4 | BOOK + PAGING 精确包含三个例外 | `paging under the book scope names exactly its three book level exceptions` + 反向「不含其他页例外」 |
| 5 | BOOK + DISPLAY 精确包含四个例外 | `display under the book scope names exactly its four book level exceptions` + 反向 |
| 6 | BOOK + EYE_CARE 明确全部全局 | `eye care under the book scope states the whole page is global` + 反向「无例外清单」 |
| 7 | BOOK + ADVANCED 明确只对当前书籍 | `advanced compatibility under the book scope says only this book` + 反向「不得含全局/所有书籍」 |
| 8 | GLOBAL 下五子页统一文案 | `every sub page under the global scope returns the same corrected copy` |
| 9 | 所有文案不含 `**` | `no notice uses markdown emphasis` |
| 10 | GLOBAL 含两个关键承诺 | `the global copy states both the write target and the non destructive guarantee` |
| 11 | GLOBAL 不含错误承诺 | `the global copy makes no promise it cannot keep` |
| 12 | 技术词 / 「系统设置」/ 句号约束 | 三个独立用例 |
| 13 | `when` 穷尽性（无 `else ->`） | `the notice function has no catch all else branch`（结构守卫）+ `every page is explicitly answered for both scopes` |

---

## 8. AndroidTest 编译结果

### 8.1 命令

```powershell
.\gradlew.bat --console=plain :app:compileDebugAndroidTestKotlin --rerun
```

| 项 | 值 |
|---|---|
| 退出码 | **0** |
| 结果 | `BUILD SUCCESSFUL in 7s` |
| 编译警告 | **0**（`warning:` 与 `w:` 均为 0） |

### 8.2 新增的 Compose 用例（已编译，**未执行**）

| # | 用例 | 断言 |
|---:|---|---|
| 1 | `rootPageShowsNoScopeNoticeButMarksPageTurnSpeedAsShared` | ROOT 无 `reader-settings-scope-notice`；翻页速度处有 `reader-page-turn-speed-global-note` 且文本「全局设置，所有书籍共用」可见 |
| 2 | `bookScopeNoticeAppearsOnEverySubPageWithTheExpectedCopy` | 五页逐页进入 → 提示可见 + 精确文案；返回后回根页 |
| 3 | `advancedCompatibilityNoticeNeverClaimsGlobalScope` | 高级页显示「只对当前书籍生效」，且不出现翻页/护眼页的全局文案 |
| 4 | `globalScopeNoticeUsesTheCorrectedCopyOnSubPages` | GLOBAL 根页无提示；进子页显示修正文案；旧文案 0 命中 |
| 5 | `returningToRootRemovesTheScopeNotice` | 返回根页后提示节点计数为 0 |
| 6 | `scopeNoticeRemainsVisibleAt320DpWithLargeFont` | 320dp × 640dp × 1.3 字号 + 夜间主题下，长句提示仍可见（不截断） |

**`renderSettings` helper 扩展**：新增 `scope: ReaderSettingsScope = ReaderSettingsScope.BOOK` 参数
（默认值与生产一致），既有 6 个用例**未改动**、无需改写（它们不传 scope，行为不变）。

**明确声明：这 6 个 Compose 用例「已编译未执行」**，不得写成执行通过 ——
任务书禁止运行 instrumentation（无设备/模拟器）。真机/模拟器执行属后续验收。

### 8.3 未采用的测试（按任务书与 Qoder §10.4 的裁决）

| 不写的测试 | 理由 |
|---|---|
| 「26 个徽标数量正确」 | 本方案**没有徽标** |
| 26 项参数化路由遍历 | 与既有 `non overridable keys always go global regardless of scope`（`:72`）与 `book scope keeps the other device level keys global as before`（`:254`）重复 |
| 「字段附近有标记调用」的文本扫描 | 不可靠（多参 `copy` / 间接写点 / 相邻行误配） |
| 逐项断言 6 个 `customTip*` 标记 | 同一 `HeaderFooterPicker` 的重复渲染，本方案无字段级标记 |

---

## 9. 主 Kotlin 编译结果

### 9.1 命令

```powershell
.\gradlew.bat --console=plain :app:compileDebugKotlin --rerun
```

| 项 | 值 |
|---|---|
| 退出码 | **0** |
| 结果 | `BUILD SUCCESSFUL in 57s` |

### 9.2 编译警告

**1 条 Kotlin 警告，与本任务无关**：

```
w: .../ui/screen/shelf/LibraryBrowserRoute.kt:145:48
   'val Icons.Outlined.Sort: ImageVector' is deprecated.
   Use the AutoMirrored version at Icons.AutoMirrored.Outlined.Sort.
```

- 该文件**不在本任务授权范围**，本轮**未修改**（未出现在 §2 修改清单）；
- 属既有警告（deprecated 图标），与 D-2-B 无因果关系；
- **本任务修改的 `ReaderSettingsSheet.kt` 产生 0 条警告**。

---

## 10. 测试 XML 统计

| 命令 | 类数 | tests | failures | errors | skipped | XML mtime |
|---|---:|---:|---:|---:|---:|---|
| 定向 JVM（2 类） | 2 | 36 | 0 | 0 | 0 | 00:18:21 |
| 附加回归：`data.settings.*` + `ui.screen.reader.sheets.*`（28 类） | 28 | 290 | 0 | 0 | 0 | — |

**附加回归命令**（非任务书要求，用于确认未波及相邻 package）：

```powershell
.\gradlew.bat --console=plain :app:testDebugUnitTest --rerun `
  --tests "com.creationreadingassistant.data.settings.*" `
  --tests "com.creationreadingassistant.ui.screen.reader.sheets.*"
```
退出码 **0**，`BUILD SUCCESSFUL in 16s`，28 类 / 290 项 / 0 失败。

**XML 目录**：`android/app/build/test-results/testDebugUnitTest/`

---

## 11. 警告

| 来源 | 数量 | 内容 | 是否本任务 |
|---|---:|---|---|
| `:app:compileDebugKotlin` | 1 | `LibraryBrowserRoute.kt:145` deprecated `Icons.Outlined.Sort` | ❌ 既有、越界文件 |
| `:app:compileDebugAndroidTestKotlin` | 0 | — | — |
| `:app:testDebugUnitTest` | 0 | — | — |

**本任务 3 个文件产生 0 条编译警告。**

---

## 12. P0 / P1 / P2 / P3

| 级别 | 数量 |
|---|---:|
| **P0** | **0** |
| **P1** | **0** |
| **P2** | **0** |
| **P3** | **3** |

### P3 明细

| ID | 内容 | 说明 |
|---|---|---|
| **P3-1** | 显示页的例外清单写成「页眉页脚开关」，而该页可覆盖项实为 `showReaderInfo`（开关本身）+ `autoHideSeconds`（菜单隐藏）+ `immersiveMode` + `showProgressBar` 四项。清单已逐项点名四项，**语义正确**；但「页眉页脚开关」一词可能与用户对「页眉页脚」的理解（含六个槽位模板）略有偏差 | 文案微调项，非缺陷 |
| **P3-2** | Compose 用例 `scopeNoticeRemainsVisibleAt320DpWithLargeFont` 只断言 `assertIsDisplayed`，未断言「文本未被截断」（Compose 无直接的截断断言 API） | 与 Qoder §10.3 的「不能证明排版不挤」一致；需真机确认 |
| **P3-3** | 根页 `pageTurnSpeed` 提示与子页翻页页的说明存在信息重叠（根页说「所有书籍共用」，翻页页说「除…外其余全局」） | 有意为之（任务书 §二 明确要求根页不能省略），但属轻微冗余 |

---

## 13. 未覆盖项

| # | 项 | 状态 | 说明 |
|---:|---|---|---|
| U1 | 6 个 Compose 用例的**实际执行** | **已编译未执行** | 任务书禁止 instrumentation 执行；需设备/模拟器 |
| U2 | 提示行在小屏 / 横屏 / 大字号下的**实际排版观感** | 未验证 | 只做了 320dp × 1.3 字号的编译期断言，未跑 |
| U3 | TalkBack 实际朗读效果 | 未验证 | 未设额外 `contentDescription`，预期自然朗读可见文本；需真机 |
| U4 | 提示行是否遮挡内容 | 未验证 | 结构上在 `Scaffold` 内容区顶部、页面内容之前，理论上不遮挡；需真机确认 |
| U5 | GLOBAL 文案的「其他书已有的单书设置不会被清除」是否让用户困惑 | 未验证 | 属措辞可理解性，需真机/产品确认 |
| U6 | `ReaderSettingsPage` 未来新增成员时的编译失败体验 | 设计属性 | `when` 无 `else`，新增成员必然编译失败；有结构守卫断言 |

---

## 14. SEAM REQUEST

**无。**

产品改动 100% 落在 `ReaderSettingsSheet.kt` 内（任务书授权的唯一产品文件）：

- 页级提示做成**文件内私有** `ScopeNoticeRow`，未新增公共组件；
- 根页说明用内联 `Text`，未修改 `MicroTrackSliderRow` 或任何 `ui/components/**`；
- 未触碰 `ReaderPagingSettings.kt` / `ReaderDisplaySettings.kt` / `ReaderEyeCareSettings.kt`
  （任务书 §四 明确要求本轮不采用分组说明）；
- 未触碰路由、存储、枚举、预设、公共组件。

---

## 15. 边界遵守声明

### 15.1 明确未修改的禁改文件（哈希逐一比对，均与改动前一致）

| 文件 | 基线(前16) | 当前(前16) | mtime | 判定 |
|---|---|---|---|---|
| `ReaderSettingsRouter.kt` | `d6e6de3d8e0cc12e` | `d6e6de3d8e0cc12e` | 2026-09-19 23:58:14 | **未改** |
| `ReaderSettingsEditApplier.kt` | `f85ad026e022b04c` | `f85ad026e022b04c` | 2026-09-17 18:58:22 | **未改** |
| `ReaderOverride.kt` | `69d55677fb48961e` | `69d55677fb48961e` | 2026-09-21 20:35:25 | **未改** |
| `ReadingPresets.kt` | `1c655b9bff4f2aa6` | `1c655b9bff4f2aa6` | 2026-09-21 20:33:48 | **未改** |
| `PerBookSettingsStore.kt` | `bef8c822c52c6616` | `bef8c822c52c6616` | 2026-09-21 20:34:45 | **未改** |
| `SettingsStore.kt` | `b9eb035ee41938a9` | `b9eb035ee41938a9` | 2026-09-18 19:35:33 | **未改** |
| `ReaderPagingSettings.kt` | `55233e3dc35f0d49` | `55233e3dc35f0d49` | 2026-09-18 00:53:28 | **未改** |
| `ReaderDisplaySettings.kt` | `0310f601504e7fc1` | `0310f601504e7fc1` | 2026-09-18 17:55:00 | **未改** |
| `ReaderEyeCareSettings.kt` | `66e1a04bfd423e68` | `66e1a04bfd423e68` | 2026-09-16 13:29:46 | **未改** |
| `ReaderAdvancedSettings.kt` | `32815139b73edf14` | `32815139b73edf14` | 2026-09-19 23:58:15 | **未改** |
| `ReaderTypographySettings.kt` | `ab1200a246e07107` | `ab1200a246e07107` | 2026-09-18 16:11:44 | **未改** |
| `ReaderSettingsCommon.kt` | `c2580fbb4d9d656c` | `c2580fbb4d9d656c` | 2026-09-21 20:29:13 | **未改** |
| `ReaderThemeSheet.kt` | `65be0e9e5c4c674c` | `65be0e9e5c4c674c` | 2026-09-18 22:38:28 | **未改** |
| `ReaderSheetScaffold.kt` | `792981f1e0f96a43` | `792981f1e0f96a43` | 2026-09-18 22:33:56 | **未改** |

全部 mtime 早于本轮窗口（2026-09-21 23:5x 起），且哈希与开工前逐字节一致。
`ui/components/**` 未触碰；Gradle / Manifest / Room / DAO / DB 未触碰。

### 15.2 数据语义未变

- 26 个设备/交互字段**未加入** `ReaderOverrideKey`（枚举哈希不变：`69d55677fb48961e`）；
- D-2-A 的 `pagerEngineMode` / `epubPagerEngineMode` 语义未动
  （`ReaderOverride.kt` / `ReadingPresets.kt` / `PerBookSettingsStore.kt` 哈希均不变）；
- 未改任何存储位置、生效路径、路由规则。本轮为**纯渲染增量**。

### 15.3 未执行的操作

未 commit / push / stage / stash / reset / checkout / clean / 全仓格式化；
未构建或安装 APK；未操作手机；未使用 MuMu 或任何模拟器；未进行真机验收；
未修改用户真实书籍或测试书状态；未进入原始仓库
`D:\develop\Code\Codex\creation-reading-assistant`；未领取下一任务。

### 15.4 工作区其他改动

工作区内其他 Agent 与用户的既有修改**全部保留**，本轮未覆盖、未回退、未整理。

### 15.5 变异验证（证明测试有效）

每次变异后从 `/tmp/d02b/Sheet.after.bak` 还原并复核哈希。

| 变异 | 内容 | 失败用例 | 结果 |
|---|---|---|---|
| **A** | `ROOT -> null` 改为返回文案（根页不该有提示） | `the root page has no notice under the book scope`、`every page is explicitly answered for both scopes` | **2 FAILED** ✅ |
| **B** | GLOBAL 文案退回 Qoder 不准确版「本页设置会写入全局默认，影响所有书」 | `every sub page under the global scope returns the same corrected copy`、`the global copy makes no promise it cannot keep`、`the global copy states both the write target and the non destructive guarantee` | **3 FAILED** ✅ |
| **C** | 翻页页例外清单删掉「屏幕方向」（句子不再与页面事实对齐） | `paging under the book scope names exactly its three book level exceptions` | **1 FAILED** ✅ |

**恢复证据**：`ReaderSettingsSheet.kt` 当前哈希 `43134fd58db3e8a5…` 与
`/tmp/d02b/Sheet.after.bak` **逐字节一致**（RESTORED OK）；
残留扫描：旧文案 0 命中、删项版 0 命中、`ROOT -> null` 两处均在（BOOK / GLOBAL 各一）。

### 15.6 索引与分支状态

```
HEAD                          = 5aed5939600e491843e3c44453921bd6273a233c
git diff --cached --name-only = 0
git stash list                = 0
branch                        = codex/recover-worktree-20260920
```

---

## 16. `git diff --` 摘要（仅本任务授权文件）

```bash
git diff --stat -- \
  android/app/src/main/java/com/creationreadingassistant/ui/screen/reader/sheets/ReaderSettingsSheet.kt \
  android/app/src/androidTest/java/com/creationreadingassistant/ui/screen/reader/sheets/ReaderSettingsSheetTest.kt
```

```
 .../reader/sheets/ReaderSettingsSheetTest.kt       | 110 +++++++++++++
 .../ui/screen/reader/sheets/ReaderSettingsSheet.kt | 173 ++++++++++++++++++---
 2 files changed, 258 insertions(+), 25 deletions(-)
```

`ReaderSettingsSheet.kt` 的 4 个 hunk：

| hunk | 目的 |
|---|---|
| 1 | import 区之后新增 `readerSettingsScopeNotice` 纯函数 + `ScopeNoticeRow` 私有 composable（含 KDoc 说明设计属性） |
| 2 | `AnimatedContent` 内容体：改为 `Column(fillMaxSize)` → 可选提示行 → `Column(weight(1f))` 包裹原 `when(target)` |
| 3 | 根页「快捷排版」内，翻页速度滑块之后新增全局说明 `Text`（含 testTag） |
| 4 | （同 hunk 2 内的缩进调整，无独立语义） |

新增文件（未跟踪）：

```bash
git diff --no-index --stat /dev/null \
  android/app/src/test/java/com/creationreadingassistant/ui/screen/reader/sheets/ReaderSettingsScopeNoticeTest.kt
# -> 1 file changed, 279 insertions(+)
```

---

## 17. 回传摘要

| 项 | 内容 |
|---|---|
| 结论 | **CANDIDATE PASS** |
| 修改的产品文件数 | **1**（`ReaderSettingsSheet.kt`） |
| 修改/新增的测试文件数 | **2**（1 改 + 1 新增） |
| 页级映射函数 | `readerSettingsScopeNotice(page, scope): String?`（纯函数、`when` 穷尽无 `else`、ROOT 返回 null） |
| JVM 测试 | 2 类 / **36 项** / 0 失败 / 0 错误 / 0 跳过，退出码 **0** |
| AndroidTest 编译 | 退出码 **0**，0 警告；新增 6 个 Compose 用例（**已编译未执行**） |
| 主 Kotlin 编译 | 退出码 **0**；1 条既有越界警告（`LibraryBrowserRoute.kt` deprecated 图标），本任务 3 文件 0 警告 |
| 附加回归 | 28 类 / 290 项 / 0 失败，退出码 0 |
| 变异验证 | A/B/C 分别打红 2 / 3 / 1 项，已全部恢复并复核哈希 |
| P0/P1/P2/P3 | **0 / 0 / 0 / 3** |
| SEAM REQUEST | **无** |
| 报告路径 | `docs/coordination/agent-results/agent-result-d02b-zcode.md` |

**完成后停止，等待 Codex 与 Qoder 复核。未领取下一任务。**
