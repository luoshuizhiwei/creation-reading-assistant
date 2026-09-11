# Trae R1-N1.1 报告：类型安全的阅读笔记回源定位

- 工作树：`D:/develop/Code/Codex/creation-reading-assistant`
- 状态：**Dev complete（未真机验收，交 WorkBuddy，真机验收留待两个并行修补冻结后统一安排）**
- 变更性质：纯追加 + 既有文件局部修改，**未触碰** Qoder 的滚动 TXT 替换链、desktop、DAO/entity/schema、AppNavigation、ReaderRoute、ReaderScreen、共享 strings、`docs/handoff/current.md`；未 reset/checkout/format/commit/push。

## 背景与目标

「我的 → 阅读笔记」已有稳定 ID（`highlight:{id}` / `note:{id}` / `bookmark:{id}`），但此前 ProfileRoute 只把 rawId 传给 reader 的 `highlightId` 参数，ReaderProgressEffects（SE4）按「先高亮、后笔记」猜表，同一 rawId 出现在不同表时会跳到错误记录。本轮引入 typed stableId 的编码、解析与严格匹配，彻底消除跨表串台。

## 改动路径

| 文件 | 说明 |
|---|---|
| `feature/annotations/AnnotationNavigationTarget.kt`（新增） | 类型化回源核心：`AnnotationNavigationTarget(type, rawId)`；`navigationTargetId(type, id)` 构造纯文本 typed stableId；`AnnotationEntry.navigationTargetOrNull()`（有 locator 才返回 stableId，无 locator 返回 null 只开书）；`parseAnnotationNavigationTarget(raw?)`（识别 `highlight:`/`note:`/`bookmark:` 前缀，裸 rawId 或未知前缀返回 null）；`resolveAnnotationTarget(typed, rawParam, highlights, notes)`（typed 严格按类型匹配、不回退另一张表；裸 rawId 保留「先高亮后笔记」旧语义） |
| `ui/screen/profile/ProfileRoute.kt` | `jumpToEntry` 改为 `nav.navigate("reader/$bookId?highlightId=${Uri.encode(target)}")`，`target = entry.navigationTargetOrNull()`（无 locator 传 null → 只开书）；补 `navigationTargetOrNull` 扩展 import |
| `ui/screen/reader/ReaderProgressEffects.kt` | SE4 解析 typed 目标：`parseAnnotationNavigationTarget(hid)` → `resolveAnnotationTarget(...)`；typed 目标不存在或类型不符时清除请求并 `showNotice("无法定位该阅读笔记，记录可能已删除或类型不匹配")`，不再跨表猜测；裸 rawId 保留旧兼容 |
| `ui/screen/reader/ReaderLayerBuilders.kt` | `onJumpToBookmark` 改为 `onPendingHighlightIdChange(navigationTargetId(AnnotationType.BOOKMARK, id))`，书内书签回源带 `bookmark:` 类型；无 locator 历史书签仍由 SE4 按 locator/progress 降级，不伪造偏移 |

测试：

| 文件 | 说明 |
|---|---|
| `feature/annotations/AnnotationNavigationTargetTest.kt`（新增，9 测试） | 前缀小写构造、typed 解析、裸 rawId/未知前缀/空值返回 null、同 rawId 高亮与普通笔记各命中正确实体、bookmark 与普通 note 同 ID 不串台、类型不符不降级（四种 mismatch 场景）、裸 rawId 旧「先高亮后笔记」语义、无 locator 不携带定位目标、有 locator 携带 stableId |

未改动既有测试（AnnotationModelsTest / ProfileViewModelTest / ReaderProgressNavigationTest 均直接通过）。

## 必须完成项对照

1. **路由兼容 + typed stableId**：完成。沿用 `reader/{bookId}?highlightId=` 路由（未改 AppNavigation），从「我的」跳转时传 URL 编码后的 typed stableId。
2. **SE4 类型化解析**：完成。`highlight:{id}` 只匹配 HighlightEntity；`note:{id}` 只匹配 `kind != "bookmark"` 的 NoteEntity；`bookmark:{id}` 只匹配 `kind == "bookmark"` 的 NoteEntity；类型不符或不存在时清除请求并提示，不降级误跳；裸 rawId 保留旧语义。
3. **书内书签回源带类型 + 无 locator 降级**：完成。书内书签传 `bookmark:` 前缀；「我的」无 locator 条目 `navigationTargetOrNull()` 返回 null → 只开书。
4. **不改 locator/AnchorResolver/坐标/持久化/删除撤销**：完成。定位解析链路（LocatorCodec/AnchorResolver/坐标）与 SE4 其余逻辑体不动，仅替换目标实体解析入口。

## 验证记录（真实命令与结果）

工作目录 `android/`：

| 命令 | 退出码 | 结果 |
|---|---|---|
| `.\gradlew.bat :app:testDebugUnitTest --tests "com.creationreadingassistant.feature.annotations.AnnotationNavigationTargetTest" --tests "com.creationreadingassistant.feature.annotations.AnnotationModelsTest" --tests "com.creationreadingassistant.ui.viewmodel.ProfileViewModelTest" --tests "com.creationreadingassistant.ui.screen.reader.ReaderProgressNavigationTest"` | 0 | **27 tests, 0 failures, 0 errors, 0 skipped**（AnnotationNavigationTarget 9 + AnnotationModels 8 + ProfileViewModel 3 + ReaderProgressNavigation 7） |
| `.\gradlew.bat :app:compileDebugKotlin` | 0 | BUILD SUCCESSFUL（compileDebugKotlin UP-TO-DATE，主源码编译通过） |

测试数明细（来自 `app/build/test-results/testDebugUnitTest/*.xml`）：

- `AnnotationNavigationTargetTest`：`tests="9" skipped="0" failures="0" errors="0"`
- `AnnotationModelsTest`：`tests="8" skipped="0" failures="0" errors="0"`
- `ProfileViewModelTest`：`tests="3" skipped="0" failures="0" errors="0"`
- `ReaderProgressNavigationTest`：`tests="7" skipped="0" failures="0" errors="0"`

## 未覆盖 / 风险

- **未真机验收**：按任务要求留待两个并行修补冻结后统一安排。书内书签回源带类型、菜单点击跳转、`showNotice` 提示等运行时行为需真机确认。
- **SE4 副作用未直接断言**：ReaderProgressEffects 是 Composable LaunchedEffect，纯 JVM 单测无法直接断言其 `showNotice` 分支；核心类型化匹配逻辑由 `parseAnnotationNavigationTarget` / `resolveAnnotationTarget` 纯函数单测覆盖，效果层仅做编译级验证。
- **URL 编码回环依赖框架**：`Uri.encode` → Navigation 参数解码的回环未单测，依赖 Navigation 组件对字符串参数的自动解码。
- **锚点定位准确性**：typed 目标命中正确实体后，locator 精确性仍沿用既有 AnchorResolver 行为（与类型化无关，不在本轮变更范围）。