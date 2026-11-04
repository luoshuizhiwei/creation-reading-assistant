# 阶段 E 首批双边界复核（2026-09-29）

## 独立验证

- ZCode 两份首批补丁位于 E 盘，SHA-256：G0+U `9d8d33607deb6059b4abb9a58f7505be2609c8a5c96a78e309d788a8c0b772a3`，G2+runner `b54c9cbf569a0c1955779c83a795591577d1daaa7394726460106bfc8241b434`。`git apply --stat` 显示前者 10 文件、后者 4 文件；共享 `build.gradle.kts` 被拆为版本 hunk 与 A/C/D/E 四 hunk。两者未 stage/commit。
- **G0-only 已有证据，Claude 报告称缺失是误读。** `stage-e-g0-isolated-20260929/evidence/compile-g0-candidate.log` SHA `6d98b234e8b56cca8246abdb36d5b10de8d7a6621028b09c1438d37e0fbee4fd`，编译 EXIT 0；259 XML 实算 JVM 2286 tests、0 failures/errors/skipped，JVM 日志 SHA `86465aca11d0ec5b6e488d5b0a657d4d74c6ac7641b6c8cf2de346c7f9f5f84e`。
- Codex 新建 `E:\creation-reading-assistant\stage-e-g0u-boundary-20260929\`，从 HEAD `android/` 提取并应用 G0+U 补丁。`:app:compileDebugKotlin :app:compileDebugAndroidTestKotlin` 离线构建 EXIT 0、`BUILD SUCCESSFUL in 1m 4s`；日志 SHA `e0a2076205fbe1b14d83a47a3f4be3147031359e2ca84a7f36556a49d8973c0b`。这是拟第一个提交**自身**的编译证据，不依赖 G2。
- Codex 新建 `E:\creation-reading-assistant\stage-e-g2-runner-boundary-20260929\`，从 HEAD 提取并应用 G2+runner 补丁。debug androidTest 与 qahost 主/测试 APK 分两次离线构建，均 EXIT 0、`BUILD SUCCESSFUL`；日志 SHA 分别为 `b72be12af9b4f46637d94cb8bf8df3e6bc06d865a959d5f99e0cda4f5e051088`、`cc982e87a5f6eabceafaa07e9d11b874b49ab608df0c1442f95f661c59b8a21e`。runner 文件相对工作树仅行尾不同（`git diff --no-index --ignore-space-at-eol` EXIT 0）。这是拟第二个提交**自身**的构建证据。两边界的设备测试只在**联合态** MuMu qahost 8/8 实跑；普通 debug 设备 NOT RUN。
- ZCode 的 E 盘机械清单：180 HOLD 行中候选分组 22、首批 2、首批和后续拆分 1、保留 HOLD 69、**未映射 86**；后续八批次尚未隔离编译，不能宣称 E 整体就绪。65 原目录代码项中七类摘要覆盖 56、原语义报告另有 H 类 #57–65 共 9；Claude 补出九项，但它们含有意行为改变，只能按已决定的产品合同处理，不写成逐字节等价。

## 增量备份限制

- ZCode 报告称 1521 展开条目中既有快照覆盖 1451、增量复制 28、排除 42；28 份副本实算 SHA 与来源匹配。**但 28 份含 1 个 `.opencode/.../preference.json` 和 2 个 `android/scripts/__pycache__/*.pyc`，超出必要项目资产范围。** 不读取其内容，不将它们纳入任何提交、分享或后续备份。合理的必要增量副本为 25 份，原始 `incremental-manifest.csv` 仍记载 28 份。
- Codex 尝试对这 3 个 E 盘副本做精确路径清理，命令被自动审批以 `blocked by policy` 拒绝，**未删除**；不能称 E 盘增量副本已清理。用户原仓库中的对应原件未被修改。需要通过允许的文件清理路径单独移除这 3 个副本并重写清单。旧快照也只是时点保护，后续新报告需增量入账。

## 首批拟提交顺序与边界

1. **提交 A：G0+U（10 文件）**。Room v15：`AppDatabase.kt` 2 hunk、`DatabaseModule.kt` 3 hunk、`SearchTermDao.kt` 仅索引删除 S5、`15.json`、`AppDatabaseMigrationTest.kt` 8 hunk；父表 `@Upsert`：`BookDao.kt` 仅 B1–B4、`InspirationDao.kt` I1–I2、`TaxonomyDao.kt` T1–T4、`ParentUpsertCascadeSafetyTest.kt`；`build.gradle.kts` 仅版本号 B。明确排除 `BookDao` B5、`SearchTermDao` S1–S4/S6 和其他 gradle hunk。
2. **提交 B：G2+runner（4 文件）**。`build.gradle.kts` 仅 A/C/D/E；`CraIsolatedTestRunner.kt`（准确 qahost 包分支修正版）、`CraIsolatedTestApplication.kt`、`IsolatedTestContext.kt`。明确排除版本号 B。普通 debug 透传分支仅编译，设备 NOT RUN。

首批以外：原仓库保留完整、不移动；3 个原目录独有 Electron 源文件和桌面提交不混入 Android；其余原目录资产、86 未映射 HOLD、69 保留 HOLD 与后续 8 个未证可编译分组保持原位。阶段 E 仍 **4/5=80%**，最终目录和发布均未完成。依项目收口计划 §E，stage/commit 前向用户展示本清单并取得明确授权；授权不涵盖 push、reset、checkout、目录迁移或清理他人资产。
