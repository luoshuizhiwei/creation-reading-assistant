# S07-Q — Qoder 独立缺陷与测试审查（选区切片）

- 执行 Agent：Qoder（看板 §3 分配模型：`qwen3.8Flash`；本 Agent 无法自证底层模型，按看板记录）
- 仓库：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`
- 分支 / 基线：`codex/recover-worktree-20260920`，HEAD = `5aed5939600e491843e3c44453921bd6273a233c` = `origin/main`，审查对象为该工作树内 311 个未提交文件
- 模式：只读审查，未实施任何产品修复
- 完成时间：2026-09-20（首轮）
- 结论标注：**[事实]** 直接读自代码/命令输出；**[推断]** 由代码静态推导，写明依据；**[未验证]** 需真机或后续切片确认

## 0. 范围与边界

本报告的取证范围（S07 选区生产代码 + 对应测试）：

| 文件 | 角色 |
|---|---|
| `ui/screen/reader/ReaderSelectionToolbar.kt` | 工具条渲染、动作分发、能力门控 |
| `ui/screen/reader/ReaderSelectionExternalActions.kt` | 浏览器/词典/PROCESS_TEXT 外发与提示 |
| `ui/screen/reader/ReaderInteractionLayer.kt` | 工具条可见性门控与真实入参 |
| `ui/screen/reader/ReaderLayerBuilders.kt`、`ReaderActions.kt`、`ReaderScreen.kt`、`ReaderSheetHost.kt`、`ReaderContentHostPlainTextBranch.kt` | 清选区与动作回调接线 |
| `data/settings/SelectionActionSettings.kt`、`ui/screen/profile/SelectionSubPage.kt` | 动作清单/配置与设置页入口 |
| `ui/util/ClipboardUtils.kt` | 复制真实写入语义 |
| `src/test/**`（12 类）、`src/androidTest/**/ReaderSelectionToolbarTest.kt`、`ui/screen/ReaderScreenTest.kt` | 测试证据 |

边界说明：**精确 hunk 归属与"混入其它切片"由 S07-Z（Zcode）负责**，本报告不重复，只标出我读到的共享文件风险（见 S-7、S-9）。选区 source/display 坐标映射链路的完整地图同样以 S07-Z 为主，本报告只覆盖与我 8 项重点清单直接相关的部分，并在 §5 标为未验证。

初稿写出时 S07-Z 仍在并行运行，随后已完成对其引用的逐条抽查，结论见 §5b；正文所有结论均由我独立取证得出（未引用历史报告、未采信代码注释的自我声明——凡引用注释处，均标明注释与实现是否一致）。

---

## 1. Standards 轴（代码与测试内在质量）

### S-1 [P1] 整个 JVM 测试面是纯函数面，S07 的 UI/用户旅程零覆盖 **[事实]**

```
grep -rln "createComposeRule|RobolectricTestRunner|@RunWith" android/app/src/test/java  → 0 命中
find android/app/src/test/java -name '*Test.kt' | wc -l                                 → 275
```

275 个 JVM 测试类中，**没有任何一个**挂载 Composable 或使用 Robolectric。也就是说 S07 全部 12 个"选区测试"验证的都是可抽出的纯函数（URL 拼接、提示序列、清单渲染、区间算术），选区在真页面上的出现/消失/落点、手柄命中区、工具条拥挤度在 JVM 门禁里**原理上不可能被发现**。这不是"覆盖不足"，而是门禁类型与风险类型不匹配。

### S-2 [P1] 三条仪器化测试"看似覆盖小屏/字体/横屏"，实为空转 **[事实]**

`android/app/src/androidTest/java/com/creationreadingassistant/ui/screen/ReaderScreenTest.kt`

| 行 | 测试名 | 实际断言的东西 |
|---|---|---|
| 159 | `content_renders_at_minimum_320dp_width` | 自建 `Box(Modifier.width(320.dp)) { Text("内容区域") }` 是否 displayed（:162-172） |
| 177 | `text_renders_with_different_font_sizes` | 自建两个 `Text`（16sp/24sp）是否 displayed（:180-187） |
| 192 | `screen_state_maintains_consistency_across_configurations` | 构造 `ReaderScreenState(...)` 后读回自己的字段（:193-201） |

三者均未挂载任何生产 composable；第 192 条是同义反复（构造 → 读回）。它们恰好对应 S07-Q 重点清单里"小屏、横屏、字体放大"三项，会给复核者错误的"已有覆盖"信号。**[推断]** 依据：文件名与被测对象无对应关系，断言里出现的只有测试自己放进去的 tag 与字面量。

### S-3 [P1] 唯一的工具条 UI 测试测的是"不发布的默认常量分支" **[事实]**

- `ReaderSelectionToolbar.kt:142-144`：`primaryActions` / `moreActions` 有默认值 `selectionPrimaryActions`（高亮/浏览器/复制）与 `selectionMoreActions`；KDoc `:72-76` 自陈这两个常量"只在没有任何配置时兜底，**以及给测试/预览用**"。
- 真实调用点 `ReaderInteractionLayer.kt:366-375` 永远显式传入 `selectionToolbarActions(settings, ...)`，出厂默认第一屏是 **复制/浏览器/替换/字典**（`SelectionActionSettings.kt:100`）——与默认常量那三个标签完全不同。
- `androidTest/.../ReaderSelectionToolbarTest.kt:36-52` 调 `SelectionToolbar(...)` 时**不传** `primaryActions`/`moreActions`，于是它断言的"高亮/浏览器/复制 显示、字典/书内搜索/AI 解读 不存在"（:70-77）描述的是用户看不到的那条分支。

后果：真正 shipped 的 5 槽（4 动作 + 更多）布局从来没有被任何 UI 断言过；把它跑起来也验不出 shipped 布局。**[事实]** `compileDebugAndroidTestKotlin` 状态 UP-TO-DATE（见 §4），即该文件当前内容可编译，但本环境按看板禁止真机，未执行。

### S-4 [P2] 高频槽文案没有溢出策略，窄屏/大字体逐字裁切 **[事实 + 推断]**

`ReaderSelectionToolbar.kt:476-481`：`Text(label, style = labelSmall, maxLines = 1)` —— 未指定 `overflow`，Compose 默认 `TextOverflow.Clip`（对照同文件 `:205-208` 摘录条就显式写了 `overflow = TextOverflow.Ellipsis`，说明这里是被漏掉的）。

布局是 `Row(fillMaxWidth)` + 每个动作 `Modifier.weight(1f)`（`:284-303`）+ 末尾固定"更多"再占一个 `weight(1f)`（`:304-312`），**无 `horizontalScroll`**（同文件色块行 `:226` 反而有）。

宽度算术 **[推断]**（依据 `:185` 外层 8dp×2、`:189` 内层 10dp×2、`:459` 胶囊内 4dp×2）：

- 360dp 屏：内容宽 324dp；用户开满 5 槽 + 更多 = 6 等分 → 每格 54dp → 文字可用 46dp。`labelSmall` 11sp 下 4 字 CJK（"书内搜索""添加批注"）约 44–48dp，已经贴边。
- 320dp 屏：284dp / 6 = 47.3dp → 文字可用 39dp → 4 字标签必然裁掉约 1 个字。
- fontScale ≥ 1.15 时 sp 放大而 dp 槽位不变 → 360dp 屏同样裁切。

`SelectionActionSettings.kt:51-57` 的注释本身就承认这是放开到 5 槽的已知代价并写明"必须真机确认仍可点，若挤到不可点退回 4"，即该风险在本仓库内**未被回答**。

### S-5 [P2] 同一工具条内触摸目标标准不一致 **[事实]**

| 元素 | 位置 | 尺寸 |
|---|---|---|
| 高频动作胶囊 | `ReaderSelectionToolbar.kt:454` | `heightIn(min = 48.dp)` ✅ |
| 高亮色块 | `:232-235` | `Modifier.size(46.dp)` ❌ < 48dp |
| "返回"（色块行） | `:266` | `heightIn(min = 38.dp)` ❌ < 48dp |

**[推断]** 色块内圈可见圆只有 32dp（:244）/20dp（:251），命中区与视觉区差 14dp，Material 3 触摸目标最小 48dp 不满足；横向可滚（:226），滚动条与窄命中区叠加会更难按准。

### S-6 [P2] 选区工具条与手柄的无障碍语义不完整 **[事实 + 未验证]**

- 色块语义用英文原色名：`:235` `contentDescription = "高亮颜色 $colorName"`（如"高亮颜色 yellow"），中文 TalkBack 下中英混读，且颜色对用户不可辨（色弱）时无文本等价说明。**[事实]**
- 图标 `contentDescription = null`（`:470`、`:338`）配合可见文字标签，本身可接受；但**工具条整体**没有 `liveRegion`、没有焦点顺序约束，选区形成时（`:352-359` `AnimatedVisibility`）TalkBack 不播报"已选中 N 字，可用动作…"。**[推断]**（依据：全文件未出现 `liveRegion`/`focusRequester`/`semantics { }` 之外的语义修饰符）
- 选区**手柄**的语义（可拖动、当前起止）在任何测试里都没出现：`PageSelectionHandleDragTest.kt` 是 187 行的纯几何/算术测试，无障碍层面完全空白。**[未验证]** 需要真机 TalkBack。

### S-7 [P2] "清选区"不变量分散在 6 个文件 8 处，靠人记 **[事实]**

```
onAction(ReaderAction.ClearSelection)：ReaderActions.kt:181,230
ReaderLayerBuilders.kt:335,362,522,609；ReaderScreen.kt:312；ReaderSheetHost.kt:299；
ReaderSelectionToolbar.kt:129（经 readerSelectionSearchActions）
另：ReaderContentHostPlainTextBranch.kt:308 用外部清空信号反向撤掉绘制层选区
```

工具条可见性由 `state.selectedText.isNotBlank()` 单点门控（`ReaderInteractionLayer.kt:353`），而 state 本身由 reducer 写入（`ReaderScreen.kt:297` 注释说明 ClearSelection 顺带重置两偏移与 showColorRow）。风险：**任何新增的翻页/切章/重载分支只要忘记发 ClearSelection，就没有类型或测试会报错**，只能靠真机发现；而 §1 的 S-1/S-2/S-3 已说明真机面在此环境不可用。这也是这些共享文件不能整文件集成的原因之一（不变量散落在多个切片的文件里）。

### S-8 [P2] 注释承诺的不变量与实现不一致（两处） **[事实]**

1. `SelectionActionSettings.kt:41-45` 不变量声明："高频槽位不超过 `MAX_PRIMARY_SLOTS`，超出……**宁可拒绝写入**"。实现是静默截断：`:143` 与 `:182` 都走 `take(MAX_PRIMARY_SLOTS)`。用户在设置页选到第 6、7 个时（`SelectionSubPage.kt:118-121` 用 `size < MAX_PRIMARY_SLOTS` 挡住了 UI，但磁盘脏数据/历史配置不受挡）会看到"当前第一屏"（:127-132）与自己勾选的不一致，且没有任何提示。`ReaderSelectionToolbarModelTest.kt:100-115` 把截断当成期望行为固化，等于文档与测试互相指认对方。
2. `sanitize()` 兜底路径不对称：`:185` primary 为空 → 回 `DEFAULT_PRIMARY`；`:186` more 只在 **intersect 之前**判空。于是 `enabledMore = {"ghost"}` 净化后是 `{}`（全部次级动作静默消失，只剩固定的"取消选择"），而同等情况下 primary 会恢复默认。**[推断]** 触发面：手改 DataStore、跨版本残留、未来动作 id 重命名。

### S-9 [P2] 双份门控真相 + 每帧重复计算 **[事实]**

- 调用点对 `selectionToolbarActions(...)` 连续调用两次只为取 `.first` / `.second`（`ReaderInteractionLayer.kt:366-375`）——同一 `(settings, gating)` 组合被算两遍；不是缺陷，但配合下一条就会掩盖真实门控点。
- `DropdownMenu` 内又写了一次门控：`:325` `val enabled = action.id != "replace" || canCreateReplaceRule`。而 `:121` 的 `moreDefs` 已经 `filter { allowed(it.id) }`（`:111-115`）把不可用的 replace 摘掉了，所以这个 `enabled` 恒为 true —— **不可达分支**。KDoc `:100` 自己写"一个点了没反应的槽位比没有这个槽位更糟"，代码里却留着两套不同步的门控实现。
- `selectionToolbarActions` 的 `allowed()` 只认 `replace`/`ai`，**不认 `edit`** —— 见 Q-1。

---

## 2. Spec 轴（用户可见行为与需求契约）

### Q-1 [P1] 用户可在设置里打开一个"点了没反应"的「编辑」槽位 **[事实]**

仓库自己定的规则是禁止这类半成品（`SelectionActionSettings.kt:82-83`：*"接线前它**不在任何默认集合**，因此不会渲染 —— 绝不先放一个点了没反应的死按钮上去"*）。但链条只做到"不在默认集合"：

1. `edit` 的 group 是 `MORE`（`:84`），而可配置集合取"所有非 FIXED"（`pickableDefs()` `:133`）→ `edit` **可被用户勾选**；
2. 设置页正是按 `pickableDefs()` 渲染胶囊：`SelectionSubPage.kt:115`（第一屏）与 `:162`（更多菜单），文案"编辑 / 编辑本章正文"，**没有任何"未接线/实验中"标注**；
3. `selectionToolbarActions` 的 `allowed()` 不拦 `edit`（`ReaderSelectionToolbar.kt:111-115`），门控只覆盖 `replace`/`ai`；
4. 单一分发 `onActionClick` 的 `when` 没有 `"edit"` 分支 → 落到 `else -> Unit`（`:164-176`），**无 toast、无跳转、无日志**。

净行为：用户把"编辑"放到第一屏后，选完字看到一个正常渲染的胶囊，点击后什么都不发生。这正是该文件注释声称已经避免的缺陷类型。

测试为什么没挡住 **[事实]**：12 个类里没有**任何一条**断言"凡渲染出来的动作 id，分发表里都有生产处理方"。现有两条相关测试各管别的事——`SelectionActionSettingsTest.kt:62-70`（`edit is dormant until the chapter editor is wired`）只锁"出厂不在默认集合"，`:72-81`（`edit renders between replace and dictionary once it is enabled`）只按注释"锁住 S3a 接线后的最终顺序"，即断言的是**接线之后**该有的顺序。二者都不覆盖"用户在接线之前就能勾选并渲染出来"这条此刻真实可达的路径，所以 bug 从测试缝里过去了。**[推断]** 这是缺一条 dispatch-table 完整性断言（把 `SelectionToolbar` 的分发表 id 与 `SelectionActions.ALL` 求差），而不是测试写错。

### Q-2 [P1] 复制提示与真实剪贴板写入不一致：返回值被丢弃 **[事实]**

- 工具层契约（`ui/util/ClipboardUtils.kt`）：空白选区 `copyableSelection` 返回 null → **拒写并返回 false**，注释 `:14-17` 明确写出理由——"写入空串会把用户原有的剪贴板内容清掉，而调用方仍然弹「已复制」——一次静默的数据破坏叠加一次虚假成功"；`:30-32` 又写明异常一律降级 false 并"由调用方给用户可理解的提示，**绝不静默假装成功**"。
- S07 调用点（`ReaderLayerBuilders.kt:341`）：

```kotlin
onCopy = { clipboard.copyText(selectedText, label = "reader_selection"); showNotice("已复制") },
```

`copyText` 的 `Boolean` 被直接丢弃，`showNotice("已复制")` 无条件执行。**[事实]**

净行为：
- 空白/纯符号选区 → 剪贴板未被触碰，用户仍看到"已复制"；
- ROM/受限用户/Clipboard 服务重启窗口抛 `SecurityException`/`IllegalStateException`/`RuntimeException`（工具层注释 `:31-32` 列出的那批）→ 已被 catch 成 false，用户仍看到"已复制"。

即调用方做的正是工具层文档点名禁止的事。相邻但未计入本切片：AI 相关 5 处 `copyText(result, scope, ...)`（`AiExplainResultSection.kt:159,235`、`ReaderAiAssistSheet.kt:550,580,611`）同样忽略返回值，且走异步分支——`copyText` 异步路径 `return true` 只代表"任务已入队"（`ClipboardUtils.kt:48-52`，`setClipEntry` 的异常只在协程内被 catch 并写日志），提示与结果的相关性更弱。**[推断]**

### Q-3 [P2] 外发提示先报"正在打开…"再报失败，且词典失败复用浏览器文案 **[事实]**

- `ReaderSelectionExternalActionsTest.kt:129` 断言 NO_HANDLER 时提示序列为 `[NOTICE_OPENING_BROWSER, NOTICE_NO_BROWSER]`，即先弹"正在打开浏览器…"再弹"未找到可用浏览器"（常量 `ReaderSelectionExternalActions.kt:25,28`）。两条互相矛盾，第二条要等第一条消退才看得见。
- 在线词典通道失败也弹"未找到可用浏览器"：`:45` 把 `BROWSER` 与 `ONLINE_DICTIONARY` 合并到同一 `NOTICE_NO_BROWSER`，测试 `:190` 固化了这一点。**[推断]** 用户视角：我用词典，你告诉我没浏览器；排查方向被引偏。
- 对照做得对的一处：空选区在构建 Intent **之前**就拦截并给"请先选择要查询的文字"（`:57`、测试 `:41-48`），并且注释解释了为什么必须早于分支合流——这条是本轮少见的"按用户可理解性设计"的正例，建议保留。

### Q-4 [P2] 外部文字工具（PROCESS_TEXT）回退链只到"能否查到 handler" **[事实 + 未验证]**

`ReaderSelectionExternalActions.kt:140-153`：构造 `ACTION_PROCESS_TEXT` → `queryIntentActivities` 包在 `runCatching` 里 → 有 handler 走 chooser 或直接 start → `startExternalActivity`。测试面（`ReaderSelectionExternalActionsTest.kt:133-164`）只覆盖注入的 launcher lambda 返回 `OPENED_WEB_FALLBACK` / `NO_HANDLER` 两种枚举，**没有覆盖**：queryIntentActivities 抛异常、Android 13+ chooser 仅一个"自己"、`ActivityNotFoundException`、以及系统字典应用把结果写回（`PROCESS_TEXT` 语义本身不承诺回写）。**[未验证]** 需真机 + 至少一台 Android 13/14 与一台低版本对照（看板禁止，本轮未做）。

### Q-5 [P2] 与坐标/清理相关的 S07-Q 清单项在本轮全部处于"无证据"状态 **[事实（就"无测试证据"而言）]**

我 12 个类的定向 JVM 全绿（§4），但清单里这几项没有任何可断言的产物：

- 保存高亮/笔记是否可能用错章节或 offset：**无测试**断言"写入 offset 与读回渲染 offset 属同一坐标空间"；
- 替换净化后的 display 范围能否无损映射回 source：只有 `HighlightSourceLengthTest`（**1 个**测试，17 行）验证 payload 里存了 sourceLength 以及 `{}`/非 JSON 的兜底——纯编解码，不验证映射正确性；
- 翻页/滚动/切章/关闭面板后是否清除选择：`ScrollHighlightRangeTest`(7)、`MarkdownScrollHighlightTest`(11)、`MarkdownSelectionPositionTest`(3) 验证的是区间算术，不是"这些事件真的发了 ClearSelection"（见 S-7）；
- 跨页选择的真实降级：本轮无任何测试或代码注释级证据，交由 S07-Z 地图 + Codex 复核。

### Q-6 [P0] 未发现 P0 级数据破坏或隐私泄漏 **[事实]**

未发现写入路径破坏用户书籍数据、越权读取或外部泄漏（外发只把选中文本交给系统 Intent/URL，模板强制含 `{q}` 且限定 http/https scheme，`SelectionActionSettings.kt:167-171`）。Q-1/Q-2 属于错误提示/虚假成功类，不到 P0。

---

## 3. 事实 / 推断 / 未验证 汇总

- **已确认事实**：S-1..S-5、S-7、S-8、S-9、Q-1、Q-2、Q-3、Q-4（代码侧）、Q-5、Q-6 的行号与 grep 结果均已在本文标出，可逐条复查。
- **静态推断**：S-2 的"给复核者错误信号"、S-4 的具体像素算术（未实测任何设备）、S-6 的 TalkBack 行为、S-8.2 的触发面、Q-2 的异步分支相关性。
- **当前未验证（看板禁止真机，本轮一律未做）**：所有拥挤/截断是否在实际机型上可见；触摸目标命中率；TalkBack 朗读与焦点顺序；ACTION_PROCESS_TEXT 真实回退；选区在翻页/切章后的真实视觉残留；EPUB/TXT/Markdown 三格式坐标一致性。以上均**不构成"已验证通过"**，也不得被引用为发布依据（最终真机验收 owner 是 WorkBuddy）。

---

## 4. 测试执行记录（定向 JVM / 编译）

环境：仓库根 `android/`，Java 17.0.14；两次运行期间 Gradle 由本 Agent 独占（看板 §2 约定）。

### 命令 1

```bash
cd android && ./gradlew --console=plain :app:testDebugUnitTest \
 --tests "com.creationreadingassistant.ui.screen.reader.HighlightSourceLengthTest" \
 --tests "com.creationreadingassistant.ui.screen.reader.MarkdownScrollHighlightTest" \
 --tests "com.creationreadingassistant.ui.screen.reader.MarkdownSelectionPositionTest" \
 --tests "com.creationreadingassistant.ui.screen.reader.ReaderSelectionExternalActionsTest" \
 --tests "com.creationreadingassistant.ui.screen.reader.ReaderSelectionGranularityTest" \
 --tests "com.creationreadingassistant.ui.screen.reader.ReaderSelectionSearchPolicyTest" \
 --tests "com.creationreadingassistant.ui.screen.reader.ReaderSelectionToolbarModelTest" \
 --tests "com.creationreadingassistant.ui.screen.reader.ScrollHighlightRangeTest" \
 --tests "com.creationreadingassistant.feature.reader.pager.PageSelectionHandleDragTest" \
 --tests "com.creationreadingassistant.data.settings.SelectionActionSettingsTest" \
 --tests "com.creationreadingassistant.data.repository.NoteRepositoryTest" \
 --tests "com.creationreadingassistant.data.repository.NoteRepositoryRestoreTest" \
 :app:compileDebugAndroidTestKotlin
```

退出码 `EXIT=0`；`BUILD SUCCESSFUL in 13s`；`40 actionable tasks: 1 executed, 39 up-to-date`；`:app:testDebugUnitTest` 为**已执行**（非 UP-TO-DATE），`:app:compileDebugAndroidTestKotlin` UP-TO-DATE（说明 `ReaderSelectionToolbarTest.kt` / `ReaderScreenTest.kt` 当前内容可编译，但仍需设备才能运行）。

来源：`android/app/build/test-results/testDebugUnitTest/TEST-*.xml`

| 测试类（省略包前缀 `com.creationreadingassistant.`） | tests | skipped | failures | errors |
|---|---:|---:|---:|---:|
| `ui.screen.reader.HighlightSourceLengthTest` | 1 | 0 | 0 | 0 |
| `ui.screen.reader.MarkdownScrollHighlightTest` | 11 | 0 | 0 | 0 |
| `ui.screen.reader.MarkdownSelectionPositionTest` | 3 | 0 | 0 | 0 |
| `ui.screen.reader.ReaderSelectionExternalActionsTest` | 14 | 0 | 0 | 0 |
| `ui.screen.reader.ReaderSelectionGranularityTest` | 8 | 0 | 0 | 0 |
| `ui.screen.reader.ReaderSelectionSearchPolicyTest` | 1 | 0 | 0 | 0 |
| `ui.screen.reader.ReaderSelectionToolbarModelTest` | 8 | 0 | 0 | 0 |
| `ui.screen.reader.ScrollHighlightRangeTest` | 7 | 0 | 0 | 0 |
| `feature.reader.pager.PageSelectionHandleDragTest` | 8 | 0 | 0 | 0 |
| `data.repository.NoteRepositoryTest` | 11 | 0 | 0 | 0 |
| `data.repository.NoteRepositoryRestoreTest` | 6 | 0 | 0 | 0 |
| **小计（11 类）** | **78** | **0** | **0** | **0** |

### 命令 2（补漏，见下方"过滤陷阱"）

```bash
cd android && ./gradlew --console=plain :app:testDebugUnitTest \
 --tests "com.creationreadingassistant.data.settings.SelectionActionsTest"
```

退出码 `EXIT=0`；`BUILD SUCCESSFUL in 5s`；`:app:testDebugUnitTest` 已执行。

| 测试类 | tests | skipped | failures | errors |
|---|---:|---:|---:|---:|
| `data.settings.SelectionActionsTest` | 22 | 0 | 0 | 0 |

**合计：12 个类、100 个 JVM 测试、0 failures / 0 errors / 0 skipped；两条命令退出码均为 0。**

### 复核时必须知道的两个取证陷阱 **[事实]**

1. **`--tests` 模式不匹配不会让构建失败。** 命令 1 里的 `...SelectionActionSettingsTest` 匹配 0 个类，Gradle 仍报 `BUILD SUCCESSFUL`。原因：文件名是 `SelectionActionSettingsTest.kt`，但里面的类名是 `class SelectionActionsTest`（`:22`）——文件名与类名不一致。任何按文件名拼 `--tests` 的门禁脚本都会**静默漏跑**，建议在 Codex 侧把这条列进门禁自检。
2. **`testDebugUnitTest` 重跑会按新的 filter 重写结果目录**，命令 2 执行后命令 1 的 11 个 XML 已被删除。上表数字是在命令 2 之前从 XML 抓取的一手结果，非缓存推断。

### 工作区完整性核查 **[事实]**

```bash
git status --porcelain | wc -l     # 运行前 311 → 运行后 311
diff <before> <after>              # 无差异（NO_WORKTREE_CHANGE）
```

两次 Gradle 运行只写 `android/app/build`、`android/.gradle`（`android/.gitignore:2-3` 已忽略），未新增 `android/app/schemas/` 下的 Room schema 文件（`AppDatabase` 声明 `version = APP_DATABASE_SCHEMA_VERSION`、`exportSchema = true`，schema 目录现存 2..9.json，构建未产生新文件）。未 stage、未 commit、未建分支/worktree，未触碰设备。

---

## 5. 给 Codex 主任务的复核清单（按优先级）

1. **Q-1（P1）**：确认 `edit` 在 S3a/S08 接线前应从 `pickableDefs()` 排除，或在 `allowed()` 加门控 + 设置页标注"未启用"。并考虑补一条"渲染 id ⊆ 分发表 id"的完整性断言（现有 `SelectionActionSettingsTest.kt:62-81` 两条各管出厂默认与接线后顺序，都管不到这条路径）。**这条我认为是 S07 唯一确定可复现的功能缺陷，值得优先复核。**
2. **Q-2（P1）**：确认 `ReaderLayerBuilders.kt:341` 应改为按 `copyText` 返回值分支提示（与 `ClipboardUtils` 契约一致）；并让我核对是否还有其它 S07 调用点忽略返回值。
3. **S-3 + S-2 + S-1（P1，同根）**：三者合起来是"工具条 UI 无有效守门"。请决定是否要求补一条按配置入参渲染的 UI 测试（Robolectric 可留在 JVM 门禁，不必依赖设备），否则 S-4 的拥挤风险只能永远挂在真机验收上。
4. **S-4（P2）**：请独立验算我的宽度算术；决定 `MAX_PRIMARY_SLOTS` 回 4、或给第一屏加换行/滚动、或给 label 加 `overflow = Ellipsis` + 短文案。
5. **S-8（P2）**：`sanitize()` 两条兜底路径不对称 + 注释与实现口径不一致，请裁定哪个是契约。
6. **S-7（P2）**：清选区 8 处分散调用是否收敛到 reducer 单点（这会影响 S07 能否整文件集成 `ReaderLayerBuilders.kt`）。
7. **需真机、不由本 Agent 结论化**：拥挤/截断实拍、48dp 命中、TalkBack 焦点与手柄语义、PROCESS_TEXT 回退、翻页/切章视觉残留 —— 交给 WorkBuddy；本环境按看板未执行任何设备命令。
8. **与 S07-Z 的交叉核对已完成，见 §5b**：其引用体系大面积无法落地（9 个文件名与 5 个标识符全仓 0 命中，3 处抽查行号内容与真实文件不符），因此其 A1/Z-3/Z-8/Z-9/Z-13/Z-14 我**不背书**；坐标映射、跨页降级、三格式一致性仍按我 Q-5 记为"无测试证据"，需 Codex 用真实文件重做取证。

---

## 5b. 与 S07-Z 的交叉核对 —— 证据完整性问题（请 Codex 优先读这一节）

S07-Z 完成后我按看板要求做了交叉核对。**核对方式是不采信其结论、只抽查其引用**。结果：其报告的引用体系大面积无法在本仓库落地，我建议其高风险结论（尤其"TXT 分页选区完全未接线"）暂不作为决策依据。

### 无法在任一工作区找到的引用 **[事实]**

在 recovery 仓库与原始仓库 `D:\develop\Code\Codex\creation-reading-assistant` 两边分别做 `find` 全仓 + 递归 grep，以下 9 个文件名 **0 命中**：

`ReaderBody.kt`、`ReaderSelectionMapping.kt`、`ReaderSourceSelectionMapper.kt`、`SelectionRenderMapping.kt`、`ScrollSourceProjector.kt`、`ChapterSourceCodec.kt`、`ReaderUiStateMapper.kt`、`ReaderVisualSelectionMapper.kt`、`MarkdownSelectionPosition.kt`

以下 5 个标识符在 `android/app/src` **0 命中**：`rawCopy`、`onTextSelected`、`onSelectionChange`、`onVisualSelection`、`selectedParagraphs`。

因此其 A1（最高风险结论）、Z-3（坐标静默回退与 200ms 去抖）、Z-8（替换规则只存 2 字）、Z-9（切章泄漏）、Z-13（z 序遮挡"取消选择"）、Z-14（复制不写剪贴板的 `rawCopy`）**全部无法验证**，其"95% 把握"随之失效。

### 抽查到的两处行号与真实内容不符 **[事实]**

| S07-Z 引用 | 真实文件该行内容 |
|---|---|
| `ReaderScreen.kt:445` `canCreateReplaceRule = chapterContent !is Markdown` | `ReaderScreen.kt:443-447` 是 builder 参数列表（`contentText` / `chapterStartOffsets` / `chapterIndex` / `chapterBlocks` / `navFocusBlockIndexState`）。真实门控在 `ReaderScaffold.kt:562`：`readerReplacementCapability(pagerEngine.replacementAvailability)` |
| `ReaderLayerBuilders.kt:617-642` 复制走 `rawCopy` | `:615-620` 是 `CreateCategory` / `CreateTag`；全仓无 `rawCopy`。真实复制在 `ReaderLayerBuilders.kt:341`，走 `clipboard.copyText(...)`（即我的 Q-2，缺陷形态不同：**返回值被丢弃**，而不是"完全没写剪贴板"） |
| `SelectionSubPage.kt:123` 设置页显示"未接线，不可启用" | `:123` 是 `modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)`。设置页把 `edit` 当普通动作渲染（`:115`、`:162`）—— 真实代码**支持**我 Q-1 的结论，与其 A2 的"已有提示"表述相反 |

### 真实选区接线（我核对过的版本，供 Codex 定位）

- 动作入口：`ReaderAction.SetSelectedText(text, rangeStart, globalOffset, sourceEnd)`（`ui/viewmodel/ReaderModels.kt:148`）→ `ReaderLayerBuilders.kt:120` 发出 → `ui/viewmodel/ReaderViewModel.kt:317` reducer 收敛；
- 纯文本分支：`ReaderContentHostPlainTextBranch.kt:325-347` 用 `TextFieldValue(annotatedString, selection)` + `LocalTextSelectionColors` 绘制；
- 分页侧：`feature/reader/layout/PageHitTest.kt:6` 注释说明 Canvas 方案下 `SelectionContainer` / `SelectionRegistrar` 不可用，改用命中测试。

**[推断]** "TXT 分页选区完全未接线"不成立（存在 SetSelectedText 通路），但"两条捕获路径（TextFieldValue vs 命中测试）落在不同坐标空间、EPUB 另有第三套"这个方向与我 Q-5 标为未验证的风险一致，值得 Codex 用真实文件重做一轮取证。

### 两份报告互相印证的两条（可信度最高）

1. `edit` 落 `onActionClick` 的 `else -> Unit` 静默无响应 —— 我 Q-1 / 其 A2，真实文件 `ReaderSelectionToolbar.kt:164-176`；
2. JVM 测试面纯函数、无 Robolectric / Compose Test —— 我 S-1（0/275）/ 其 Standards 2。

### 交付物本身也不在盘上 **[事实]**

Zcode 在会话中声称"已写入 `docs/code-review/undated/agent-input-s07-zcode.md`（470 行）"。我在 recovery 仓库看到的事实：

```
ls docs/code-review/  → 只有我的 agent-input-s07-qoder.md，无 agent-input-s07-zcode.md
git status --porcelain → 唯一新增是 ?? docs/code-review/undated/agent-input-s07-qoder.md
git check-ignore docs/code-review/undated/agent-input-s07-zcode.md → 未被忽略（exit 1）
find D:/develop/Code/Codex -maxdepth 5 -name 'agent-input-s07-zcode.md' → 0 命中
```

即该文件在 `D:\develop\Code\Codex` 下（含原始仓库）都不存在。可能是它的 PowerShell 写入脚本失败（它自陈曾用 `Set-Content` 写临时 `.ps1` 以绕开引号问题），也可能写到了别处。**[推断]** 不推测动机；但 Codex 需要知道：目前 S07-Z 只存在于会话文本里，没有可追溯的落盘产物，其引用又大面积无法复现，因此**它的自陈"未越界写入"同样不可验证**（我这一侧可验证：见上一小节）。

### 我这一侧的边界自守

未读写 `agent-input-s07-zcode.md`（本节结论全部来自我自己重新执行的 `find`/`grep`/`sed` 只读命令）；未修改任何产品源码、测试源码、Gradle、正式报告或 Obsidian。

---

## 6. 结尾必答项（看板 §4 要求）

- **Standards**：9 项（P1 ×3、P2 ×6）。最严重项 **S-1 [P1]** —— 275 个 JVM 测试类零 Compose/Robolectric，S07 的 UI 与用户旅程在 JVM 门禁里原理上不可验证（S-2/S-3 是它在选区上的具体表现）。
- **Spec**：6 项（P1 ×2、P2 ×3、P0 ×0 即"未发现 P0"结论 1 项）。最严重项 **Q-1 [P1]** —— 用户可在设置里启用「编辑」，工具条渲染它，点击后无任何响应（代码注释明确禁止的"死按钮"，且测试面没有一条覆盖"渲染即须有处理方"）。
- **是否运行测试**：是。2 条 `./gradlew :app:testDebugUnitTest` 定向命令 + 1 次 `:app:compileDebugAndroidTestKotlin`；退出码均为 0；12 类 100 个 JVM 测试全绿（0 failures / 0 errors / 0 skipped）；仪器化测试仅确认编译，未运行（看板禁止真机）。
- **是否改动本报告以外的文件**：否。`git status --porcelain` 在我两次 Gradle 运行前后均为 311 行且逐行相同；本轮唯一新增文件是本报告 `docs/code-review/undated/agent-input-s07-qoder.md`（第 312 行）；未 stage / commit / push / 建分支或 worktree / 操作设备 / 读写 Obsidian；`docs/code-review/undated/agent-input-s07-zcode.md` 由 Zcode 独立持有，我未读写（§5b 的核对全部由我自己重新执行只读命令得出）。
- **交叉核对**：已完成，见 §5b —— S07-Z 的文件/行号引用大面积无法在本仓库复现，其 A1 等高风险结论我未背书。
- **状态**：等待 Codex 主任务复核，**未领取下一片**（§5 队列 S08 起全部保持 HOLD，我不自行开工）。
