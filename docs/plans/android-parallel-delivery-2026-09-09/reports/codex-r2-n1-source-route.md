# Codex R2-N1：通用 source locator 路由接线

日期：2026-09-10
状态：**开发接线已完成；未做真机/视觉验收，且不是 R2-J1 临时查阅 UI 的完整交付。**未 stage、commit 或 push。

## 范围

本切片把 R2-N0 的 source 坐标契约接入 Android 的 reader route 与现有阅读器定位链。它不改导入/同步、Room schema、分页/滚动的坐标算法、现有标注回源入口或 desktop。

| 区域 | 改动 |
| --- | --- |
| `AppNavigation` → `ReaderRoute` → `ReaderScreenInputs` | reader route 新增可选 `sourceLocator` 查询参数，并以单独字段传到阅读器；原有 `highlightId` 保持兼容。 |
| `SourceNavigationContract` | 新增 source position 解析：TXT 用绝对 source offset；EPUB 校验全局 offset 与章节元组一致；Markdown 以 canonical 全局 offset 推导当前章，避免历史 `ci=0/co=全局位置` 元数据把跨章位置误判为第一章。 |
| `ReaderProgressEffects` | 参数只在文档就绪后消费。跨章先请求目标章；若已发起请求后用户手动离开目标章则丢弃。分页按 source absolute offset 跳转；滚动 EPUB/Markdown 在块加载后发送带书籍/章节身份的 viewport-focus 请求；TXT 继续调用既有 source-offset 跳转。 |
| `SearchScrollFocusRequest` | 为共享的一次性 viewport-focus 通道增加 `SOURCE_NAVIGATION` 来源与非搜索默认 result id，保留其既有书籍/章节 stale guard，避免通用导航冒充搜索状态。 |

## 语义与边界

- 路由只接受 locator JSON；不接受页号、LazyList 索引或净化后的 display offset。
- `sourceLocator` 与 `highlightId` 是两条不同入口。若某个外部调用异常同时传入两者，source locator 先消费，已有标注回源随后才能运行；本轮没有改变既有标注菜单的路由构造。
- 当前导航图已能消费 `reader/{bookId}?sourceLocator=...`，但现有可见菜单/搜索尚未生成此参数。因此不能把它描述为已完成的“临时查阅/返回阅读处”用户旅程。
- 正常阅读与临时查阅的状态模型仍在 R2-N0；将其变为可见的返回按钮、跨书历史和重启策略，需要独立 UI 接线，且不得让临时查看覆盖普通阅读进度。

## 开发验证

在 `android/` 目录执行：

```text
.\gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest --tests "com.creationreadingassistant.feature.reader.navigation.SourceNavigationContractTest" --tests "com.creationreadingassistant.ui.screen.reader.ReaderProgressNavigationTest" --tests "com.creationreadingassistant.ui.screen.reader.SearchHitNavigationTest" --tests "com.creationreadingassistant.ui.screen.reader.SearchScrollFocusRequestTest" --console=plain
```

退出码 0，`BUILD SUCCESSFUL in 10s`。

- `SourceNavigationContractTest`：11 tests，0 failures，0 errors。
- `ReaderProgressNavigationTest`：7 tests，0 failures，0 errors。
- `SearchHitNavigationTest`：16 tests，0 failures，0 errors。
- `SearchScrollFocusRequestTest`：8 tests，0 failures，0 errors。

合计 42 个定向 JVM 测试。限定 tracked diff 的 `git diff --check` 无空白错误；新增 source 文件与本报告在收尾行尾检查中也无尾随空白。

## 未覆盖

- 未跑 lint、APK、安装、真机、视觉或 WorkBuddy 验收；用户已决定统一后置验收。
- `sourceLocator` 尚无可见发起入口；R2-J1 的临时查阅栈/返回正常阅读处尚未接入 `NavController` 与 back 行为。
- 不将现有 Markdown 的历史 locator 写入格式整体迁移；本轮只在通用路由消费时按其 canonical global offset 安全推导章节。
