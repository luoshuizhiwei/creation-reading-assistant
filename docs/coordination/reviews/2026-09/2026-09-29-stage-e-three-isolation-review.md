# 阶段 E 三项隔离验证复核（2026-09-29）

## 结论

- **G0 Room v15：限定通过。** E 盘 HEAD+G0+G2 精确候选的 qahost 主/测试 APK 构建成功；三个新增迁移用例在 MuMu `emulator-5556` 各自 `OK (1 test)`。这是 **G0+G2 组合态**的设备结论，不是 G0-only。先前 G0-only 的 app/androidTest 编译及 259 XML / 2286 JVM 测试可继续独立引用。
- **U 父表 @Upsert：编译/JVM 通过，Room 设备语义未验。** 复算隔离目录 11 XML = 143 tests、0 failures/errors/skipped；两个 debug APK 实算 SHA 与报告一致。五项 `ParentUpsertCascadeSafetyTest` 只编译，尚未在这份精确候选上执行。
- **G2 qahost 基建：编译通过，普通 debug 行为未验。** E 盘两份原始日志均 `BUILD SUCCESSFUL`；生成清单显示普通 debug androidTest 的 runner 也变为 `CraIsolatedTestRunner`，target 仍为正式包。不能拿 qahost 设备结果替代普通 debug 的行为验证。
- **整体 A–E 仍为 4/5 = 80%。** 阶段 E 尚无提交/目录切换授权，本轮不 stage/commit/push/reset/checkout。

## 独立对账

- `E:\creation-reading-assistant\stage-e-g0g2-mumu-20260929\evidence\` 内三份逐方法 instrumentation 日志均为 `OK (1 test)`；首次合并语法调用的 2 个 `initializationError` 已在执行报告中单列，不能算作通过。
- 冷备、清理后、原 APK 重装后三份 qahost 数据清单均为 77 行且逐行相同。当前设备主 DB SHA-256 为 `7bd076c60763d9d9a65fa3df990bcd67f2911686cdbcbf4310d37f0588499a28`；当前已装 qahost 主/测试 APK SHA-256 分别为 `e82904cc9c1315466ed5c521693f93f541560feef038bf074ce7f7cf8324ef8a`、`fdbd019011088505fa37d71565259a0d76f6927407af69148379e2ef7c27038d`，与报告冷备值一致。当前密度为物理 280；正式包未安装。
- U JVM 日志 SHA-256 为 `4e029a49f5f8a6d3c57727850abcdef4f3f5689e62af5997a99b3aaeb6f496c9`，与报告一致。U 隔离 debug 主/测试 APK 实算 SHA-256 分别为 `1e7aa4d1565befbdcb195f0c5d635ea2acdb80fd2bb6fc29edf296efe535f5a1`、`73547e350b16b970a4750eff7ebf1c2e78be53188168f7ee0eec3cc50268907b`。
- G2 `g2-input-and-build-manifest.txt` 实算 SHA-256 为 `fcd2d432f484570358a2931e6dc7bae0d6e39b4049dcb611566a0f6f3bb59a92`，与报告一致。其 debug runner 声明是配置证据；本轮没有安装正式包，也没有做 debug 设备冒烟。

## 下一执行边界

1. 由**单一 MuMu 所有者**在 E 盘构造 HEAD+U+G2 精确候选，只安装 `com.creationreadingassistant.qahost` 和 `.qahost.test`，在冷备与前后数据/APK/密度对账下执行五项 `ParentUpsertCascadeSafetyTest`。不能直接把 U-only 的正式 debug APK 装入正式包。
2. 同时由只读窗口评估 G2 runner 是否能限定到 qahost，并给出最小配置方案及 debug/qahost 双变体编译检查；不操作 MuMu、不编辑 U/G2 共享配置。普通 debug 行为在不触碰正式包的约束下不得虚称已验。
3. 两项结束后再处理首批精确 hunk、依赖顺序、原/恢复仓库资产归属与阶段 E 的授权材料。UI/reader HOLD 和发布边界继续登记，不用本轮三项结果外推。
