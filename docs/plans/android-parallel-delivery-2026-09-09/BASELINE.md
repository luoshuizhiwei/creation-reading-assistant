# 开发检查点证据

日期：2026-09-09。记录只覆盖本地开发检查，不是WorkBuddy真机验收。

- 原代码HEAD：`7499dcf`。
- Reader/layout/pager/选区/Manifest及对应测试：`05ebb56`（22文件）。含ScrollReplaceTrace和未闭环修补，不宣称修复成功。
- 首页/书架/灵感/个人页/历史/搜索/统计视觉WIP：`05afb886aa75e1e17a629bd67be9df07397b059d`（24文件）。
- 本目录与状态更正为后续文档提交；Trae/Qoder工作树从包含该文档的提交创建，实际起点以各自首次报告HEAD为准。

## 开发验证

在主仓库android/执行：

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:compileDebugAndroidTestKotlin --no-daemon
```

退出码0，BUILD SUCCESSFUL，4m19s。JVM XML：208 suites / 1703 tests / 0 failures / 0 errors / 0 skipped。
其中JVM任务本次执行，assembleDebug为UP-TO-DATE，androidTest Kotlin任务执行；仅编译仪器测试，未上机运行。
检查期间未修改产品源码，结果对应上述两个检查点的合并树。
APK SHA-256：`4EB055BC6BAB2EF1CAA4C8CEEE50BBE4CE9D37286BE1929D98EE64966C67BF62`。
`git diff --check -- android/app/src docs` 通过；CRLF→LF提示不作为错误。
单独执行 `.\gradlew.bat :app:lintDebug --no-daemon`，退出码0，BUILD SUCCESSFUL，4m22s。
Lint XML为0 errors / 4 warnings：3条ObsoleteLintCustomCheck、1条HardwareIds；lintReportDebug为UP-TO-DATE，分析任务本次执行。未运行独立真机验收。

## 保留但不纳入移动端检查点

- 桌面端三个源码文件和docs/handoff/current.md末尾桌面第30节：原样留在主工作树，不stage。
- 未跟踪的设备截图、XML、日志、数据库、scripts诊断文件：原样保留，不提交、不删除。
- docs/perf/reader-fluency-baseline-2026-09-06.md：旧测量快照，含已变化的实现描述，先保留为未跟踪，不充当当前性能证据。
- 不push；不修改现有其他工作树。

## 后续诊断线索

只读审查发现scrollPreparedSource受txtChapters非空条件限制，而底层准备函数消费readingUnits。需在无目录TXT实际失败路径上核对是否因此退回原文；尚非已确认根因，不能直接按猜测修改。
