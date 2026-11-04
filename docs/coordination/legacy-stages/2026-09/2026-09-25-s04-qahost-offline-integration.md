# S04 QA host 沙箱变体：集成者离线复验

- 日期：2026-09-25
- 仓库：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`
- 范围：仅 `android/app/build.gradle.kts` 构建接线与本报告；未运行 adb、安装、真机、MuMu、instrumentation 测试，也未触碰原始仓库或用户书籍数据。

## 实际修正

外部实现窗口先加了 `qahost` buildType 与 Compose test manifest 依赖，但其会话权限拒绝所有 Gradle 调用，故报告 `2026-09-25-s04-qahost-variant-implementation.md` 中的构建结论为 BLOCKED。集成者随后在当前工作区新鲜离线执行，发现并最小修正两处实际构建问题：

1. `qahostImplementation(...)` 在此 Kotlin DSL 脚本里是 unresolved reference，改用 `add("qahostImplementation", ...)`。
2. AGP 默认只为 `debug` buildType 生成 androidTest；单有 qahost buildType 没有 `assembleQahostAndroidTest` 任务。增加 `testBuildType = providers.gradleProperty("craAndroidTestBuildType").orNull ?: "debug"`，保持默认 debug 行为，仅显式传 `-PcraAndroidTestBuildType=qahost` 时构建沙箱测试包。

保留了 `android/app/build.gradle.kts` 中原有版本号和测试 Runner 等既有未提交改动；未修改产品 Kotlin/manifest/测试代码。

## 新鲜离线门禁

| 检查 | 结果 |
|---|---|
| 原实现直接运行 `:app:assembleQahost :app:assembleQahostAndroidTest --offline` | EXIT 1，DSL 中 `qahostImplementation` 未解析；此结果不是最终状态 |
| 改依赖写法后同命令 | EXIT 1，未生成 `assembleQahostAndroidTest`；此结果不是最终状态 |
| `:app:tasks --all --offline --console=plain` | EXIT 0；确认默认只有 `debugAndroidTest`、有 `assembleQahost` |
| `:app:assembleQahost :app:assembleQahostAndroidTest -PcraAndroidTestBuildType=qahost --offline --console=plain` | **EXIT 0，BUILD SUCCESSFUL in 5m 4s**；72 actionable tasks，其中 58 executed、13 from cache、1 up-to-date |
| 打包清单 | host package `com.creationreadingassistant.qahost`；test package `com.creationreadingassistant.qahost.test`；test `targetPackage=com.creationreadingassistant.qahost`，Runner `com.creationreadingassistant.testing.CraIsolatedTestRunner` |
| `git diff --check -- android/app/build.gradle.kts` | EXIT 0 |

产物 SHA-256：

- `android/app/build/outputs/apk/qahost/app-qahost.apk`：`064F53341475CCFDC898205ADFB36451E380F3A49C8C014AECEA9373BC7DC054`
- `android/app/build/outputs/apk/androidTest/qahost/app-qahost-androidTest.apk`：`FA48A6BC6DA850C367FA4DD53679384E21D5A7C5C717DD9D711FB002F9A21B54`

## 判定与下一门槛

**离线构建与包名隔离门禁通过；真机验收未开始。** 独立包名使 Android 应用私有目录与正式包区分，但编译和 manifest 不能证明系统设置、共享存储、ContentProvider、设备权限或具体测试行为没有旁路副作用，也不能证明 34 个 androidTest 类全都可安全运行。未来第一轮只考虑已经逐类审查的最小 EPUB 隔离测试；正式应用/书库/workdb 的前后对账仍必须实施。未经用户对安装两个沙箱包及验收边界的决定，不自动安装或执行。
