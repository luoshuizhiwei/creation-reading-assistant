# Root 实施批：备份正文、灵感保存与书架按压

用户本轮指定 Codex 实施。沿用 E 盘 task59 的候选和既有独占 Gradle 锁。Root 负责集成、记录和定向 JVM 验证，ZIP 与灵感两个实现者只写各自领域。书架按压由 Root 接回已有 UIX 书卡差量。不对外派发、不操作实体手机。设备实际手势和 SAF 导出件仍按原分工验收。

## 输入与改动范围

- 输入为当前工作文件、R6S 的非空及删除后旧 ZIP、RDS 宿主 `3c3a358a…` 和 UIX 书卡差量。证据根为 `E:/creation-reading-assistant/rds-v1-real-device-smoke/root-checks/data-interaction-v1/`。复用依赖和构建目录，新增空间预算 180 MiB（历史宿主需保全时最多一份、原始日志/XML、输入输出指纹和源码差量）。
- E 候选的 ZIP 所有者处理 LocalZipBackup、必要的快照解析及定向测试。导出按实际书目与引用收取，导入旧包保全有效内容、排除无主正文，并继续遵守较新记录、恢复中断和共享文件规则。
- E 候选的灵感所有者处理编辑器到真实写入结果的路径及定向测试。延迟写入期间防止重入，失败保留草稿，成功才离开并提示。书架采用相同按压事件源，减少动态效果时维持交互。
- 独占构建使用既有 `task59-common-candidate-v1/tools/gradle_locked.py`。定向测试覆盖新问题和受影响的旧恢复规则，保留原始退出码与 XML。已有书架/灵感列表位置尚无设备失败，不预先重写导航或滚动状态。
- 停止条件为输入产生冲突、锁忙或恢复证据受损。设备、Git、原证和正式资料边界沿用 current。验证结论分别记录源码、JVM、编译、设备。

## 实施与验证结果

**结论：本批离线实现和定向测试通过，设备行为待验。** E 候选为实施与构建来源。D 树只补了现有 UIX 书卡的按压来源接线与重复 import；灵感保存、备份与测试的完整差量在 E。未整合 UIX 其余页面或改动数据库 schema。

| 路径 | 实施 | 已验证的范围 |
|---|---|---|
| 默认 ZIP 与旧包导入 | JsonBridge 生成一份书目快照，同一份用于序列化和选择正文。旧 ZIP 导入时按其有效书目、实际路径和合并结果生成复制清单。仍被引用的共享正文保留，较新本地记录胜出时保护文件与 EPUB 图片。 | 用真实 R6S 两份 ZIP 的书目和真实正文字节，在 JVM 上调用生产 LocalZipBackup。删除前保留 TXT 和 MD，删除后仅 MD，独立检查器核对字节和 MD 身份通过。旧删除后 ZIP 导入隔离空目录只恢复 MD 正文，内部恢复日志收敛为空。回放替代了 SAF 和数据库接口，不能据此签设备恢复。 |
| 灵感编辑 | 保存按钮等待 ViewModel 返回真实写入结果。保存中禁用重复提交和编辑；失败保留草稿及其 ID 并提示重试。写入由 ViewModel 持有，编辑页离开后不再发成功导航。 | 延迟写入、失败、来源保全和同步链已做 JVM 验证。另已编入真实编辑器的失败重试交互用例，等待设备执行。 |
| 书架 | 书卡动画与点击/长按共用同一事件源；减少动态效果时跳过缩放。 | 正常点击、取消和长按用例已编入测试 APK；实际手势、位置和减少动态效果待观察。未实测到的列表位置问题继续保留观察。 |

最终定向运行 `joint3.json` 与打包 `device-apks.json` 退出码均为 0。8 份 XML 共 152 项，失败、错误、跳过均为 0。其中 68 项为 ZIP 模块测试、16 项为恢复状态测试、33 项为 JSON 与关系合并测试、34 项为灵感相关测试、1 项为真实旧 ZIP 的 JVM 回放。前序失败日志保留。已补真实引用的历史测试夹具，并修复旧 SAF 测试中两行阻塞整个 androidTest 编译的 Rect 调用；本轮没有运行该 SAF 测试。

下列文件均位于本合同证据根，`ready.json` 含完整指纹和 13 个 E 源码/测试文件差量。两包通过 apksigner 验签且证书一致。旧宿主和测试包在编译前保全。本批产物及保全件共约 123 MiB。

| 文件 | SHA-256 | 字节 |
|---|---|---:|
| app-debug.apk | `6f70484dadd40dd487252269493f4d4396029ac3c493ca678f04cc0085ac8d05` | 52,763,886 |
| app-debug-androidTest.apk | `a25b722467309ee08a4b9c029a2631429321364feeda4426513318dc6ef84e34` | 10,768,377 |

## 下一批如何验

1. ZCode 使用上述冻结宿主和中性材料，保全本次设备基线。真实导出删除前后两份 ZIP，运行上一轮的两书检查器，再在隔离空库导入保留的旧包，核 MD 可读、已删 TXT 未落盘。另按 R5 核带来源的灵感与非空笔记。保留原始 ZIP、原始返回码、设备身份和恢复对照。
2. UIX 验收方在原约定的空闲 MuMu 1 上核实本轮身份、单 owner 和恢复基线，再使用匹配 APK 对。测试方法为 `com.creationreadingassistant.ui.screen.shelf.ShelfScreenComposeTest#cancelledPressDoesNotOpenBookAndLongPressOnlyOpensMenu` 和 `com.creationreadingassistant.ui.screen.inspiration.InspirationComposeTest#editorKeepsDraftOnFailureAndReportsOnlySuccessfulRetry`。补正常/减少动效下实际拖走取消，以及切页与编辑返回后的列表位置。
3. 先核本包指纹再决定旧证能否复用。分开记录业务结果与设备恢复，交 Trae 增量复核后由 Root 裁决 R8。此次没有自动派发，也没有触发设备或 Git 操作。
