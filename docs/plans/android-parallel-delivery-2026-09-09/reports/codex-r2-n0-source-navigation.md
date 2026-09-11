# Codex R2-N0：Source 导航与临时回退契约

日期：2026-09-10
状态：**开发契约已完成；尚未接入路由或 Compose 阅读器，不是功能/真机验收。**未 stage、commit 或 push。

## 范围

本切片只增加纯 Kotlin 的 source 导航模型与状态规约，不修改已有路由、`ReaderScreen`、分页/滚动引擎、Room schema、导入同步或 desktop。

| 文件 | 改动 |
| --- | --- |
| `android/app/src/main/java/com/creationreadingassistant/feature/reader/navigation/SourceNavigationContract.kt` | 新增 `SourceNavigationTarget`、`SourceNavigationState` 与统一规约。解析存储进度时嵌套 `locator_v2` 优先，避免 EPUB 顶层 chapter-local legacy offset 覆盖全局 source 坐标；支持标注使用的独立 locator。普通阅读位置是唯一可恢复位置；临时查阅维护有界 LIFO 返回栈，重启时丢弃。 |
| `android/app/src/test/java/com/creationreadingassistant/feature/reader/navigation/SourceNavigationContractTest.kt` | 新增 7 个 JVM 回归：v2 优先、独立 locator、无效/半截坐标、普通进度隔离、嵌套临时回退、重启语义和历史上限。 |

## 坐标与状态约束

- 契约只承载 `bookId + ReaderLocator` 的 source 坐标，禁止保存或跨渲染器使用页号/显示单元索引。
- 空白书籍 ID、没有有效 offset 的定位、负坐标会被拒绝；仅有一半章节坐标时，如仍有合法绝对偏移，则丢弃不完整章节部分。
- `recordNormalReading` 才会更新重启恢复目标，并主动结束临时查阅链；临时跳转不改写正常阅读位置。
- 临时栈默认最多 8 层，超限舍弃最旧项目；重启只恢复正常阅读 target，不伪造近似位置。

## 开发验证

在 `android/` 目录执行：

```text
.\gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest --tests "com.creationreadingassistant.feature.reader.navigation.SourceNavigationContractTest" --tests "com.creationreadingassistant.feature.reader.locator.LocatorCodecTest" --console=plain
```

退出码 0，`BUILD SUCCESSFUL in 27s`。

测试 XML：

- `SourceNavigationContractTest`：7 tests，0 failures，0 errors，0 skipped。
- `LocatorCodecTest`：4 tests，0 failures，0 errors，0 skipped。

另以文件级行尾检查确认两个新增 Kotlin 文件没有尾随空白。

## 未覆盖与后续接线边界

- 这不是可见导航功能：尚未把 target 编入 `ReaderRoute`，也未连接 Reader 的实际跳转、标注菜单、搜索结果或进度持久化调用点。
- 后续接线必须继续使用 source locator，不能把 Compose 页码、滚动 display offset 或替换后的投影 offset 写回本契约；实际 UI 接线完成后才有条件安排最终统一验收。
- 未运行 lint、APK、安装、真机或 WorkBuddy 验收；用户已要求这些统一后置到全部开发结束后。
