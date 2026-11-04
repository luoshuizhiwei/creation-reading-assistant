# 阶段 E：U 设备验收与 G2 runner 审查复核（2026-09-29）

## 结论

- **U+G2 精确候选的父表运行期语义限定通过。** 隔离候选 qahost 主/测试 APK 构建成功；`ParentUpsertCascadeSafetyTest` 原始 instrumentation 日志为 5 个测试各结束码 0、`OK (5 tests)`、无失败。测试使用 Room 内存库，不覆盖已装持久库升级、同步合并和未播种的其他子表。
- **版本号界面证据成立。** 实看 MuMu 截图的“应用版本 0.5.1”；候选初次启动碰到设备已有 v15 库，而 HEAD+U+G2 只含 v14，崩溃栈确有 `A migration from 15 to 14 was required but not found`。换到临时新建的 v14 库后同一候选能启动。故本次崩溃是候选与夹具库版本不配，不可称 U 的回归通过或产品故障。最终合并 G0+U+G2 后仍需一次联合构建/受影响测试，不能用两组分开的设备结果代替。
- **G2 runner 存在真实范围问题。** 当前工作树 `CraIsolatedTestRunner.newApplication()` 对所有 build type 都强制裸 `CraIsolatedTestApplication`；普通 debug 的 target 包仍是正式包。只读审查提出按 target 包区分 qahost 与其他包、其他包透传清单 Application 的最小修法。现有测试源码检索未见直启 `MainActivity`/Hilt 的调用，但“所有现有 debug 测试机械安全”只是一项静态判断；普通 debug 设备行为未验，推荐方案尚未实现。
- **整体 A–E 仍 4/5 = 80%。** E 阶段健康开发入口、候选提交及目录决策未完成；未 stage/commit/push/reset/checkout。

## 独立原件对账

- `E:\creation-reading-assistant\stage-e-ug2-mumu-20260929\` 中候选构造记录 SHA `dcbc560774fb34f09e10801d4f9e1f8071927c028af989c2fd6582fcc14907f6`、构建日志 SHA `0654ce1014a40357cb84d5dbb695fa2eebb60aa3e5834a4df9be084f57da1345`、测试日志 SHA `5f6ce30ee178f95783b188d78f074b019c7dc03431ba322b2aaa4394ed706bff` 与报告一致。主/测试 APK SHA 依次为 `d14fde50b5be16d197e39f9b6a7e8f512890ede16fac6874b62f558a9603c88f`、`768fe5f9a218ee1c5ae4779906b6349336bfeeb4d314359e2c0d21ea29cfba12`，与报告一致。
- 冷备 tar 实算 SHA `95b1b2bef4bd9d09b6ea45289101b836c772ed465cb58c8d3b8fb3afd6737253`；前/后 77 行文件 SHA 清单逐行相同。当前 MuMu `emulator-5556` 主 DB SHA 为 `7bd076c60763d9d9a65fa3df990bcd67f2911686cdbcbf4310d37f0588499a28`，原 qahost 主/测试 APK 设备 SHA 为 `e82904cc9c1315466ed5c521693f93f541560feef038bf074ce7f7cf8324ef8a` / `fdbd019011088505fa37d71565259a0d76f6927407af69148379e2ef7c27038d`；密度为物理 280，正式包未安装。
- `ParentUpsertCascadeSafetyTest.kt` 五个 `@Test` 与日志中的五个方法相符；截图实际显示版本 0.5.1，且保留了先前崩溃的诊断记录，未被误作全程无故障。

## 下一执行边界

1. G2 runner 仅改 `CraIsolatedTestRunner.kt`，按目标包精确分支；保留 qahost 隔离语义。先在 E 盘 HEAD+G0+U+G2+runner 修正精确候选做联合构建和受影响的 Room v15 三项、父表五项 qahost 设备测试，逐项留日志、冷备/恢复和 APK 哈希。两组分开的旧结果仅作对照。
2. 普通 debug 分支因正式包不应安装到当前 MuMu，先保留“设备 NOT RUN”，在隔离构建中检查 debug androidTest 生成配置与 runner 分支源码，不能虚称普通 debug 已冒烟。
3. 并行整理剩余 Android HOLD 的最小候选批次及原/恢复资产去向，形成 §E 要求的文件/hunk、依赖、保留资产和失败停止点。未经展示具体方案与用户授权，不 stage/commit 或切目录。
