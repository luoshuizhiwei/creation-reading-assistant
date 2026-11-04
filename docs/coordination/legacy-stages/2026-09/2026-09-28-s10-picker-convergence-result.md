# S10-B1 双 HeaderFooterPicker 折叠文案收敛 — 实施结果

> **Codex 复核：代码/JVM 范围接受，UI 待验。** 当前两入口共用 `ui.components.HeaderFooterPicker`，`ReaderSettingsCommon.kt` 本地同名实现已移除；E 盘 XML 为 describe 5/5、scope 21/21，编译 EXIT=0。报告 §6 把仓库旧“禁用 MuMu”规则当作当前限制：**用户已明确改为使用 MuMu 验证，当前授权优先**。下一轮可在 MuMu qahost 隔离场景目视两入口中文折叠行，不要求无法取得的实体手机；模拟器结论仍只标 MuMu。

- 日期：2026-09-28
- 窗口：实现窗口（S10-B1），依据 `docs/coordination/decisions/2026-09/2026-09-28-s10-display-contract-decision-brief.md` 的任务卡
- 范围：仅 UI 折叠行文案收敛，不涉及 token 存储、槽位作用域、卷标题、页码、热区
- 未在真机 / MuMu 上做 UI 目视验收（见第 6 节边界）

---

## 1. 收敛前：两条入口各自的文案

同一份模板（例如 `{BookName} · {BatteryPercent}`）在两处折叠行上显示不同文字：

| 入口 | 路径 | 折叠行实现 | 用户看到 |
|------|------|-----------|---------|
| 我的 → 阅读设置 | `ui/screen/profile/ReaderSubPage.kt:550…590`（经 `ProfileSubPage.READER` → `ReaderSettingsSubPage` → `ProfileScreen.kt:179`） | `ui.components.HeaderFooterPicker`（`CustomTipDialog.kt:219`） | **中文回显**：`CustomTipPlaceholder.describeTemplate(template)` → 「书名 · 电量」 |
| 书内显示设置 | `ui/screen/reader/sheets/ReaderDisplaySettings.kt:104…132`（经 `ReaderSettingsSheet.kt:216`） | `sheets` 包内的同名本地函数（`ReaderSettingsCommon.kt:783`） | **原始 token**：`template.ifBlank { "无" }` → 「{BookName} · {BatteryPercent}」 |

分叉的根因不是某处写错，而是**存在两份同名同签名实现**：`sheets` 包内的本地版本与 `ui.components` 里的版本各自渲染折叠行，前者抄的是后者的旧形态（早于 `describeTemplate` 回显加入之前），此后只有一份跟着演进。

另有第三份——`CustomTipDialog.kt:287` 的 `HeaderFooterItem` 向后兼容重载——在**全仓零生产调用**下同样散发着旧形态的影响面。

## 2. 文件改动（三份文件，均在本窗口独占范围）

### 2.1 `ui/components/CustomTipDialog.kt` — 保留并成为唯一实现

- **保留** `fun HeaderFooterPicker(label, template, onTemplateChange, modifier)`（改动后位于 `:219`），折叠行文案为：
  ```kotlin
  text = CustomTipPlaceholder.describeTemplate(template).ifBlank { "无" },
  ```
  其上方注释（`CustomTipDialog.kt:251-252`）保留原样，说明设计意图：折叠行给人看中文，token 只在点开后的输入框里出现。
- **删除** `:283-305` 的向后兼容重载（`fun HeaderFooterPicker(label, selected: HeaderFooterItem, onSelect, modifier)`）及其 KDoc。删除前已用全仓 grep 确认该函数**零生产调用、零测试引用**。
- **删除** 随之失效的 `import com.creationreadingassistant.data.settings.HeaderFooterItem`。
- **保留** `import CustomTipPlaceholder`（仍被 `:64/:66/:116/:254` 使用：占位符清单、合法性校验、示例渲染、折叠行回显）。

### 2.2 `ui/screen/reader/sheets/ReaderSettingsCommon.kt` — 删除本地重复实现

- **删除** `internal fun HeaderFooterPicker(label, template, onTemplateChange)`（原 `:783-841`，含原始 token 折叠行 `:814`）。
- **删除** `internal fun HeaderFooterPicker(label, selected: HeaderFooterItem, onSelect)`（原 `:843-860`），同为全仓零调用。
- **删除** 仅被上述函数使用的 `import com.creationreadingassistant.ui.components.CustomTipDialog` 与 `import com.creationreadingassistant.feature.reader.pager.CustomTipPlaceholder`、`import androidx.compose.foundation.layout.widthIn`。
- **未改动** `:743-780` 的 `displayValue` / 作用域说明相关映射（D-2-B 范围，不在本任务卡内）。

### 2.3 `ui/screen/reader/sheets/ReaderDisplaySettings.kt` — 六处调用改指向共享实现

- **新增** `import com.creationreadingassistant.ui.components.HeaderFooterPicker`（`:11`）。
- 六处调用（改动后 `:104/:110/:116/:122/:128/:134`）保持语义不变，但**参数形式必须改写**，原因见第 3 节。

## 3. 一个必须记录的编译坑（首轮红灯的真实原因）

首轮 `compileDebugKotlin` **失败（EXIT=1）**，报 18 条错，全部落在 `ReaderDisplaySettings.kt`，例如：

```
e: …ReaderDisplaySettings.kt:107:19 Argument type mismatch: actual type is
   'kotlin.Function0<kotlin.Unit>', but 'androidx.compose.ui.Modifier' was expected.
e: …ReaderDisplaySettings.kt:107:66 Unresolved reference 'it'.
```

**根因不是逻辑错**：`ui.components` 版比被删的 `sheets` 版多一个**带默认值的 `modifier: Modifier = Modifier` 尾参**。原调用写成 trailing lambda：

```kotlin
HeaderFooterPicker(
    label = "页眉左侧",
    template = settings.effectiveHeaderLeft(),
) { onChange(settings.copy(customTipHeaderLeft = it)) }   // ← 被解析为 modifier，而非 onTemplateChange
```

trailing lambda 只能落到**最后一个参数**位置上，于是它被当作 `modifier` 传入，`onTemplateChange` 反而没赋值。改为显式命名参数后编译通过：

```kotlin
HeaderFooterPicker(
    label = "页眉左侧",
    template = settings.effectiveHeaderLeft(),
    onTemplateChange = { onChange(settings.copy(customTipHeaderLeft = it)) },
)
```

这个坑的性质值得记一笔：**它是"存在两份实现"这个分叉本身的直接代价**——正是两者签名细节不一致，才让看似等价的替换无法通过编译。收敛之后这类差异不可能再出现。

## 4. 两入口一致性静态核验

用脚本逐槽提取两入口的 `label` / `template` / 写入字段并比对，**12 个槽位完全逐字一致**：

| 槽位 | label | template 取值 | 写入字段 |
|------|-------|--------------|---------|
| 1 | 页眉左侧 | `settings.effectiveHeaderLeft()` | `customTipHeaderLeft` |
| 2 | 页眉中间 | `settings.effectiveHeaderCenter()` | `customTipHeaderCenter` |
| 3 | 页眉右侧 | `settings.effectiveHeaderRight()` | `customTipHeaderRight` |
| 4 | 页脚左侧 | `settings.effectiveFooterLeft()` | `customTipFooterLeft` |
| 5 | 页脚中间 | `settings.effectiveFooterCenter()` | `customTipFooterCenter` |
| 6 | 页脚右侧 | `settings.effectiveFooterRight()` | `customTipFooterRight` |

`onTemplateChange` 的**写入路由不同**是预期内的：全书设置走 `ProfileAction.UpdateReader`，书内设置走 `(ReaderSettings) -> Unit`，两者落到各自的 sink，但最终写入的是同一个 `ReaderSettings` 的同一批字段。

另外确认：`CustomTipPlaceholder.describeTemplate` 在**生产代码中的唯一调用点**现在是 `CustomTipDialog.kt:253`——即两条入口共用的那个折叠行，不可能再出现一处改编一处漏改。

## 5. 测试结果（原始日志与退出码已落 E 盘）

证据目录：`E:\s10-b1-evidence\`

| # | 命令 | 退出码 | 日志 | 结论 |
|---|------|--------|------|------|
| t1 | `:app:testDebugUnitTest --tests CustomTipTemplateDescribeTest`（修 Equipment 前） | **EXIT=1** | `t1-describe.log` | 红灯，根因见第 3 节 |
| t1b | `:app:compileDebugKotlin`（修复后） | EXIT=0 | `t1b-compile.log` | `BUILD SUCCESSFUL in 13s` |
| t2 | `:app:testDebugUnitTest` 两个目标测试 | EXIT=0 | `t2-both-tests.log` | `BUILD SUCCESSFUL in 25s` |
| t3 | `:app:compileQahostDebugAndroidTestKotlin` | EXIT=1 | `t3-qahost-compile.log` | **该任务名在本仓不存在**（`Cannot locate tasks that match`），非代码问题 |

t2 的两个测试结果（XML 已连同 sha256 归档到 E 盘，均为 `2026-09-27T18:52:50` 新生成，`testDebugUnitTest` 任务实际执行而非 `FROM-CACHE`）：

| 测试类 | tests | failures | errors | skipped | XML SHA256 |
|--------|-------|----------|--------|---------|-----------|
| `CustomTipTemplateDescribeTest` | 5 | 0 | 0 | 0 | `02a2b03bb8e46b67bdfc9f0cad4de1503d700cccf900e2424ca542c6d13a2ab0` |
| `ReaderSettingsScopeNoticeTest` | 21 | 0 | 0 | 0 | `9d62a32cce0ef85f4820c356cd65ab3e557343eab04efb32ff06153655b22f01` |

两个测试都是不依赖 Compose 的纯 JVM 测试，因此它们**验证的是被测的契约本身没有回归**，而不是验证折叠行渲染：`CustomTipTemplateDescribeTest` 钉住 `describeTemplate` 的中文化映射（含"未知占位符原样保留、不吞内容"这条关键语义），`ReaderSettingsScopeNoticeTest` 钉住 D-2-B 的六槽位全局作用域说明文案未被我的删除波及（`displayValue`/notice 映射原地未动，21/21 通过是对"没顺手改坏"的直接证据）。

## 6. 边界：本轮**没有**做的事

**设备 UI 未验证。** 收敛后的实际渲染效果——两入口折叠行是否都显示「书名 · 电量」而非 `{BookName} · {BatteryPercent}`——本轮完全没有在 MuMu 或实体手机上目视过，本窗口也没有申请设备：

- t1/t1b/t2 全是 JVM 侧编译与单元测试，**不渲染任何 Compose 节点**；
- 全仓 androidTest 对 `HeaderFooterPicker` 零引用（已 grep 确认），所以也不存在"已有 UI 测试会替我发现"的兜底；
- `compileQahostDebugAndroidTestKotlin` 任务在本仓不存在（t3 红灯），本轮没有另找一个 androidTest 编译任务去凑数——那是 Commander 侧的决定；
- AGENTS.md 要求 Android UI 只在已连接的**真实手机**上验证，不使用 MuMu 模拟器。所以严格地说，即便将来有人能在 MuMu 上看到两个折叠行一致，那也不是 AGENTS.md 意义上的 UI 验收。

因此**这项收敛的合格线目前只到"编译通过 + 契约测试全绿 + 两入口 12 槽位逐字一致"为止，UI 目视仍是未关闭项**，建议 Commander 排一次真机/设备 UI 抽查，看两个入口的六个折叠行：均应显示中文回显，点开后输入框内才是 token。

## 7. 交给 Commander 的两点说明

1. **`ReaderDisplaySettings.kt` 的 diff 里混有非本窗口的既有改动**（如新增 `BatteryTipStyle` 电量显示方式行、`) { … }` 形式的其他调用行）。这些属于工作区接手前的脏改动，我只在必要时重写了六处 `HeaderFooterPicker` 的参数形式，其余原样保留。请 Commander 在 review diff 时按 hunk 区分。
2. **多余实现已全部清理**：`sheets` 包本地两份 + `ui.components` legacy 重载共 3 份重合实现已删除或去重，现只剩 `ui.components.HeaderFooterPicker` 一份（定义 `CustomTipDialog.kt:219`），被两入口 12 处调用共用。未触碰 `.codex/config.toml`，未 commit / push / reset / checkout。
