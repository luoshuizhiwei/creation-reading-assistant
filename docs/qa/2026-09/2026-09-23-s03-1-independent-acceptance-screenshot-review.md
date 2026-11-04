# S03-1 独立验收截图回读（2026-09-23）

来源：用户提供的 WorkBuddy 报告四张截图，保存在 [evidence/2026-09-23-s03-1-independent-acceptance](../evidence/2026-09-23-s03-1-independent-acceptance)；这是 Codex 对截图的摘要，不是 WorkBuddy 原始 Markdown 报告或完整命令日志。截图中的命令、设备状态与安装结论尚未取得独立原始日志，不能扩大为 Codex 亲自复跑的结论。

## 截图可见的结论

- WorkBuddy 总判定 **PARTIAL**：JVM 协程并发状态机、相关单测、Lint、Debug 和 androidTest APK 构建、真机安装确认通过；因缺少隔离 in-memory Room fixture，按安全规则停止在用户现有书库上的重建动作，真实 Room/设备重建路径 **UNCOVERED**。
- 截图列出定向并发 19/19、相关搜索 40/40、Lint 0 errors/9 warnings、两个 APK 构建退出码 0；主 APK 与 androidTest APK 安装均报告 `install_with_confirm.ps1` 的 `CONFIRM_LOOP`、退出码 0，没有 ADB 安装回退。
- 截图报告设备 serial `c49ac6cf`，竖屏，`stay_on_while_plugged_in` 测前/测后均为 `3`；未对用户书库执行删除、重置或重建。设备命令日志、安装日志和状态回读原件未随截图提供，设备侧细节仅按报告转述。

## Codex 工作区回读边界

- 当前工作区能回读相关搜索三类 JVM XML：27 + 4 + 9 = 40 tests，均 0 failures/errors。并发类 19 项 XML 已被后续 Gradle 测试覆盖，当前工作区无该 XML；19/19 依据截图，不冒充本轮本地 XML 回读。
- 两个 APK 文件存在，字节数分别为 52,481,150 和 1,572,731，与截图相符。当前 Lint XML 时间早于截图所述本轮验收，截图中的本轮 Lint 结果没有可回读的新鲜 XML/原始命令日志；保留来源限制。
- 仓库 `androidTest` 尚无 S03-1 专用隔离 in-memory Room 测试类。未执行设备动作复验。

## 状态与下一步

S03-1 代码/JVM 范围维持通过；独立验收为 **PARTIAL**，真实 Room/设备重建路径未覆盖，不能记为完整验收通过。下一片应先在 `androidTest` 中建立仅使用 in-memory Room 与合成书籍的隔离 fixture，审查其不会打开或修改应用正式数据库，再由独立验收者在真实手机竖屏运行该定向用例；若隔离无法证明，则继续保持未覆盖，不在用户书库补测。
