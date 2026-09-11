# Codex R1-D1.1：全局删除撤销接线

日期：2026-09-10

状态：**开发接线完成；JVM 定向回归通过；未做真机验收。**未 commit、未 push。

## 范围

Qoder 的删除快照与会话内凭证已提供正确的数据事务，但下列既有删除入口仍直接走仓储或局部 action，且撤销提示会随路由切换消失：

| 删除入口 | 收口方式 |
| --- | --- |
| 首页“继续阅读” | `BookViewModel` 注入 `BookDeletionCoordinator`，删除改为登记凭证；底部面板复用同一 `DeletionUndoViewModel`，关闭面板后全局宿主继续显示。 |
| 完读归档 | `BookOperationsViewModel` 把协调器传给 `ShelfBookActions`；确认框改用同一作用范围与实际倒计时文案。 |
| 阅读器 | `ReaderViewModel` 的 `ReaderAction.DeleteBook` 改走协调器；返回上级路由后仍可撤销。 |
| 阅读历史 | 保持其既有 `DeletionUndoViewModel` 删除路径，移除页面私有撤销条，避免与全局条重复。 |

`AppNavigation` 新增唯一的 `DeletionUndoHost`：凭证、过期清理、撤销结果 Snackbar 都在导航层处理。书架、搜索和阅读历史页不再各自渲染同一张凭证；首页底部面板由于运行在独立 Dialog 窗口中，保留同源的可见提示条。

## 额外集成修补

并行的 N1.1 新增了 `AnnotationEntry.navigationTargetOrNull()`，但调用点缺少导入。只补充该导入，未改动 Trae 的类型化导航逻辑。

## 验证

清理一次损坏的可再生 `android/app/build` 输出后，最终命令为：

```powershell
.\gradlew.bat :app:testDebugUnitTest `
  --tests "com.creationreadingassistant.ui.viewmodel.BookViewModelTest" `
  --tests "com.creationreadingassistant.ui.viewmodel.ReaderViewModelTest" `
  --tests "com.creationreadingassistant.feature.library.deletion.*" `
  --no-build-cache --no-daemon --console=plain
```

结果：`BUILD SUCCESSFUL in 1m 4s`；6 个套件、116 tests、0 failures、0 errors：

- 删除快照/恢复/凭证/委托：47
- `BookViewModelTest`：7（含首页删除改走协调器）
- `ReaderViewModelTest`：62（含阅读器删除改走协调器）

`git diff --check` 在本任务相关路径无空白错误。此前两次失败分别是并行写入时的 N1.1 缺失导入，以及 Windows Gradle/KSP 增量生成与本地构建缓存故障；均不作为功能测试结论，最终无缓存命令为本报告唯一的通过证据。

## 尚未覆盖

- 未安装、未启动、未进行真机或视觉验收；删除后在阅读器返回、首页面板、归档和跨路由撤销的实际 UI 流程须由 WorkBuddy 在冻结快照后独立验证。
- 本轮不改变 Qoder 的 12 秒会话窗口、快照恢复策略或数据库 schema；不把 JVM 通过表述成独立验收完成。
