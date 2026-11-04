# 96-v1下一阶段总览（2026-10-23 香港时间）

| 编号 | 执行端/模型 | 用户结果 | 并行与所有权 | 粗估 |
|---|---|---|---|---|
| 96-A D2 | 原Claude会话/当前模型不变 | 正文缺失后安全恢复关联，拒绝错书且保留位置/笔记/来源 | A独占书库/重关联相关差量，与B并行 | 20–50分钟，视真实缺口 |
| 96-B P1 | 同会话统筹独立lane，不自行切模型 | 本书设置不污染其他书，全局/预设/恢复默认范围正确 | B独占设置相关差量，与A并行 | 20–40分钟，视现有证据 |
| 96集成 | 唯一集成owner | 合流、必要构建与受影响一次模拟器验收、严格恢复 | 等A/B冻结，构建/绑定/设备串行，Root维护D | 按实际必要范围 |

````text
继续创作阅读助手原生Android下一阶段，任务96-v1。用户明确要求“开始下一阶段的，继续自动化”。95已完，不再重复自用说明或要求用户先反馈才能开发。当前选取既有已授权需求D2正文重新关联和P1设置作用域，推进现有实现的实际用户闭环，非新产品线。Root负责方向/核验/接续，当前模型不变；确证额度限额保全现场报告，由Root按用户授权转ZCode，不买额度不改提供商。

权威：有效AGENTS、Codex项目入口、D:/develop/Code/Codex/creation-reading-assistant-recovery-20260919/docs/handoff/current.md页首；95/94 Root已核结论复用。范围契约见docs/plans/2026-10-01-product-roadmap-realignment.md的现有Android功能域，及docs/plans/android-parallel-delivery-2026-09-09/README.md的D2/P1契约；旧目录/owner/启动命令不沿用。现行排期先前的首版停止点被用户本次“下一阶段”覆盖，桌面/发布/新服务等边界不变。旧“未覆盖”不是已证缺陷，不能据日期重做已有实现。仅按本任务相关链接补证，不扫全部历史/盘/私库。

基线：E:/creation-reading-assistant/task59-common-candidate-v1/candidate/android。产品1102346485a9e47b7b12885310f678e56b5c86d36082834fbec8732906ffc30c（52763889B），test57477ef2aa6d91838e1b59a360e475ba9d4e3d5975c3695b15dcade91d6bfc16（10538197B）。94-v17分类/来源/标签导出同run通过，首次保护UNKNOWN错误留存，后补只读71/820/DB一致已核；95交付说明已核，均不重开。先低成本核进程/锁/owner/文件mtime/96目录，存在活动执行者不重复启动/抢写。报告记录本轮before指纹，既有WIP不回退。

开两条真正独立lane并行，独立执行者必须明确互斥文件且不覆盖别人；无独立能力则如实说明，不假称已并行。A/B只定向验证，唯一集成owner负责共同构建/绑定/设备。Root只写D协调/current，执行者只写本任务授权E差量与唯一工作区的自有文件。

A：D2既有正文生命周期。优先读实际feature/library/ShelfImporter.kt的repairFile/refresh/staging身份门（当前已有CRA-D2-RELINK-IDENTITY-FIX-v4，不可从旧缺口重做）、相关ShelfViewModel/BookOperationsViewModel/ShelfRoute/BookActionSheet入口及已有定向测试/最新直接证据。可用旧docs/coordination/dispatches/2026-10/2026-10-03-next-relink-precheck-v1.md和E:/creation-reading-assistant/d2-device-artifacts-v4/REPORT.md作导航，不把旧FAIL或旧输入当当前结果。成功标准：本批自己的测试书移除正文→同内容文件通过实际重新定位入口恢复同bookId/可阅读，原位置/标注/灵感source及关联保留；不同内容拒绝且不改旧资产；无可靠身份/取消/读取失败可解释且不破坏记录；普通重复导入Duplicate不能冒充恢复。先核是否已有充分匹配证据；缺真实接线就最小修并定向验证，代码已正确则不人为改动。所有权A：ShelfImporter、确需BookRepository与上述书库UI/VM路径，以及对应自有测试/资源；只修改必要hunk，不能扩成refresh重写/数据库重构/扫库。DAO/schema/共享AppNavigation如确需，明确seam给唯一集成owner，不自行改公共文件。

B：P1现有设置。读data/settings/ReaderSettingsRouter.kt、PerBookSettingsStore.kt、ReaderSettingsOverlay/ReadingPresets、ui/screen/reader/ReaderSettingsEditApplier.kt、SettingsViewModel与必要ReaderSettingsSheet当前接线/直接测试。成功标准：本书仅显式覆盖项持久化；更改全局不覆盖另一书的显式选择；本书/全局预设和恢复默认说明范围与实际写入一致；书间切换/重开有效设置正确，非本轮编辑的覆盖不得被顺手清掉。已有router与预设保留engine key等机制复用，不按旧R3从头开发。只为实际缺口最小修复/定向验证，若已有匹配充分证据直接采纳。所有权B：上述data/settings相关自有hunk、ReaderSettingsEditApplier、SettingsViewModel、必要ReaderSettingsSheet及自有测试/资源；不动A书库文件、Reader保存/locator/手势/渲染公共接口。公共SettingsStore仅集成owner根据具体seam修改，不并发覆盖。

单个问题由原owner从修复→必要编译修正→定向验证→报告闭环，不为每次编译失败另编号。A/B交界归唯一集成owner。若两lane完全已有充分证据，给出具体证据与该结果，不造测试；Root将继续排当前Android下一项真实未闭合目标，而不是把该单批已完成等于项目停止。

集成：合流前核A/B指纹和互斥改动；必要JVM/编译仅覆盖本次受影响范围，未变69项/已有阅读取材/恢复充分证据复用。没有产品改动无需重建产品；若产品变更，唯一owner在现工作区做一次必要候选构建，并证明本轮源与产物对应。覆盖唯一110/57477前先找已保全同SHA副本；无副本仅必要单份（产品52763889B/test10538197B）保全，真实运行件覆盖前同样保护。旧94与95资产不更名/改写/删除。

储存：输出E:/creation-reading-assistant/task96-library-settings-closure-v1，A/B差量分目录，各预算4MiB、集成证据8MiB（若预计超出说明必要原始证据而不复制历史），build增量预计512MiB；旧证据原路径/SHA引用。唯一工作区如上；Gradle home D:/develop/env/gradle，JDK D:/develop/Java/jdk-17.0.14，SDK D:/develop/Android/Sdk，锁E:/creation-reading-assistant/locks/android-gradle-exclusive.lock。只占一个构建owner/锁，不硬链接可写cache、不复制工程/build/.gradle/.kotlin/缓存/依赖/历史证据/APK对，不清理迁移存量，实际空间及保留理由写REPORT。

设备：本批在确实有需补受影响真实流程、前置与恢复合同闭合后，由唯一owner执行一个新绑定主run/attempt（包括入场失败）；可一个明确联合入口覆盖A/B，但不现场修码重绑第二试。不为已有充分匹配证据开设备。若缺口无法安全执行先完成独立离线工作并明确阻塞，不取消安全门禁。用户许可使用/必要启动项目隔离MuMu1，可关闭已授权游戏；全程不关闭重启杀VM、不操作共享ADB，结束运行。ADB D:/Application/NetEase/MuMuPlayer/nx_main/adb.exe，fresh serial/boot/owner/前台/预算，全命令绑serial；完整阶段上限+至少1200秒恢复预留。

保持原71私有/820外部/DB-WAL-SHM、原宿主与test代码完整数据用户状态/设置/strict night=no恢复合同。只用本轮创建中性测试书与夹具，不删改任何既有或正式书库。D2移除正文仅操作已证明本批新建且E有原字节/sizeSHA的测试资产；测试行为不授权清理既有资产。未知文件/兄弟后代不递归，探测失败不算缺席。collect或保全失败/归属不明保留证据依赖资产并BLOCKED，独立原test/设置等安全恢复继续；业务/取证/清理/恢复错误分别留存。不得静默retry把原错误改绿。必要恢复先完成再交接，不中途停。

交付最小patch/变更源码before-after SHA/必要原始日志XML/真实动态依赖绑定、A/B及集成REPORT/ready。分实现/离线/JVM构建/模拟器/整链与恢复，不称实体手机MIUI或全产品完成，分母未知。方向自审：用户实际结果是否推进、有无重复核验/无意义拆批/工具牵偏、剩余风险和真正下一步。无Git暂存提交pushPR发布、D产品回灌、桌面正式库HOLD损坏仓库/云/私库操作。严禁任何Claude/Cowork/Claude-Code/DSH或.codex/memories写入（包括你自己的自动记忆收尾），上一批已发生副作用不得再发生；不要读这些私库，不自动清理之前记录。你不建自动化、不向其他应用发消息；Root负责接续与限额交接。最后单独写：这是任务96-v1。
````
