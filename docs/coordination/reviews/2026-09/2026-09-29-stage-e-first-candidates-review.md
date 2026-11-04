# 阶段 E 首批精确候选复核（2026-09-29）

## 判定

G0（Room v15）、U（父表 `@Upsert` + 版本号）和 G2（qahost 测试基建）三份候选报告均已落盘。A–E **仍 4/5=80%**；本轮没有 stage、commit、push、reset、checkout、目录切换或设备操作。G0 已获得隔离编译和 JVM 证据，但新增迁移 androidTest 未运行；U/G2 是 hunk 级候选，尚无隔离构建或测试。阶段 E 尚未进入版本提交。

## 独立核对

- G0 隔离目录为 `E:\creation-reading-assistant\stage-e-g0-isolated-20260929\`。`compile-g0-candidate.log` SHA-256 `6d98b234e8b56cca8246abdb36d5b10de8d7a6621028b09c1438d37e0fbee4fd`，末尾 `BUILD SUCCESSFUL`，包含 `compileDebugKotlin` 与 `compileDebugAndroidTestKotlin`。`test-g0-candidate-jvm.log` SHA-256 `86465aca11d0ec5b6e488d5b0a657d4d74c6ac7641b6c8cf2de346c7f9f5f84e`，末尾 `BUILD SUCCESSFUL`；Codex 从 259 个原始 XML 实算 **2286 tests / 0 failures / 0 errors / 0 skipped**。这是 HEAD + G0 的基线范围，不能与 D 阶段整树 3208 相加或写成三项迁移测试通过。
- G0 候选 5 路输入：`AppDatabase.kt` 2 hunk、`DatabaseModule.kt` 3 hunk、未跟踪 `15.json`、`SearchTermDao.kt` 仅索引删除 hunk、`AppDatabaseMigrationTest.kt` 8 hunk。源码 diff 证实 `SearchTermDao.kt` 其他 DAO/coverage hunk 不属于 G0。隔离目录的 `git archive HEAD` 提取有 5 个中文文档失败，Android 编译输入已满足；这限制对**完整仓库导出**的断言，不影响本次 Android 候选编译结论。
- U 报告将 `BookDao.kt` 前 4 hunk、`InspirationDao.kt` 2 hunk、`TaxonomyDao.kt` 4 hunk、未跟踪 `ParentUpsertCascadeSafetyTest.kt` 与 `build.gradle.kts` 版本号 hunk 分开；`BookDao.kt` 后续 `getAllActive/activeIds` hunk 排除。E 盘台账 SHA-256 `fccebde2e769f6712c60c13246e9bec3503b7efe1496740eb65ba958879c39ff` 本轮核对在位。既有设备 `5/5` 是全工作树产物，不证明 U-only 拆分组已验证。
- G2 报告把 `build.gradle.kts` 的 U0 A/C/D/E 与 U 的版本号 B 分开，并列出三个 androidTest 新文件。E 盘矩阵 SHA-256 `2914336c1fdcbad589b5a70a7cc872b7d2e054bf6be0be173067bb83ebe595d8` 在位。`testInstrumentationRunner` 在 `defaultConfig`，会把**普通 debug androidTest** 的 Application 也换为裸隔离 Application；qahost V5/EPUB 设备证据不覆盖 debug 变体，需定向冒烟或重新设计 runner 作用域，不能标为无行为影响。

## 下一验收与最小提交条件

1. G0 的三项新增 `AppDatabaseMigrationTest` 需在 MuMu **隔离宿主**运行，留 APK/测试输入 SHA、命令与退出码、DB/文件前后对账。不能安装到或改写正式包/书库；若需借用 G2 作为 qahost 宿主，报告明确区分“G0+G2 设备组合态”与“G0 单独编译态”。
2. U 与 G2 各自在 E 盘隔离目录做 HEAD + 精确候选编译；U 的父表级联回归、G2 的普通 debug runner 冒烟按对应风险执行，不重复全量 D 阶段门禁。
3. 三组的 hunk、验证与资产去向清单明确后，连同仍 HOLD 的 UI/reader/新功能单列，向用户展示阶段 E 的**具体候选执行方案**。收口计划 §E 要求用户明确授权后才 stage/commit；本复核不构成授权。
