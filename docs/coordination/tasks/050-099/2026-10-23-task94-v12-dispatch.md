# 94-v12自动交接：导出收口及分阶段恢复

派发日期2026-10-23。Claude当前项目会话“原书缺失时摘录仍可读”，保持当前模型；确认模型限额后才转ZCode，不能把Server error等同额度耗尽。Root核验、方向自审和交接自动接续，2/3不恢复。

| 编号 | 推荐模型 | 作用 | 依赖/预计耗时 |
|---|---|---|---|
| 94-v12 | Claude当前模型；确证限额才ZCode | 核实际探测通道与恢复分支，完成导出/清空/取消 | 同owner接缝→必要打包→单设备，约25–45分钟 |

````text
继续“创作阅读助手”原生Android项目，任务94-v12。本消息由用户授权的Codex Root自动交接。保持你当前模型，不自行切提供商/购买额度；若额度耗尽，保全进度与owner/运行状态后报告，由Root按用户授权转ZCode。

不要重复94-v11。Root已核v11 REPORT/源码/patch/当前宿主/两APK指纹；原test代码、完整数据清单、用户状态及补做后的保护final均核同，fresh只读核产品包缺席、原test包保留。v11两次run都在保存前失败，原始错误保留，不能写成单attempt或导出已验。

权威入口：
D:/develop/Code/Codex/creation-reading-assistant-recovery-20260919/AGENTS.md
D:/develop/Code/Codex/creation-reading-assistant-recovery-20260919/docs/handoff/current.md
以及当前有效用户约定、Codex项目入口。已加载未变复用，直接读E:/creation-reading-assistant/task94-organizing-export-integration-v1/v11/REPORT.md、ready.json和恢复合同，不扫历史/私库。

当前产品APK：1102346485a9e47b7b12885310f678e56b5c86d36082834fbec8732906ffc30c
当前test APK：aebcb018739bf0264260e19dd4acee01816a2f8904d698640a459166f0fb0612
当前仪器：89f3a7f406b0d185997bba953af34c28a019ae027d5f4f97bf9b27be69e398cb
当前宿主修正版：58ebedae99da03705a48628ab92e18ea9d7cb9a8f4b26d682caa0c5fd789e641
它们的最终探测/恢复修正尚未执行，不直接声称安全可用。

本批同一owner完成最小修正、定向验证、必要构建、绑定、一次设备、恢复及报告。先核相关进程/锁/owner/文件mtime，不覆盖正在运行任务。若已有人接续94-v12，先核归属并等待，不重复派发或重启。

工作区唯一：E:/creation-reading-assistant/task59-common-candidate-v1/candidate/android
Gradle home D:/develop/env/gradle，JDK D:/develop/Java/jdk-17.0.14，SDK D:/develop/Android/Sdk
锁E:/creation-reading-assistant/locks/android-gradle-exclusive.lock。
输出E:/creation-reading-assistant/task94-organizing-export-integration-v1/v12，存在则核归属接续，保留v11原件。
源差量/报告/日志证据预算8MiB，既有build增量预计256MiB。原test恢复资产直接引用；如确需构建覆盖唯一aebc原件，先核同SHA副本，无副本仅必要单份10525181B保全并单列预算。无工程/缓存/历史证据/APK对复制，无存量清理迁移。实际空间和保留理由写同一REPORT。

先做方向自审：现阶段只剩真实导出、清空、取消，不重做已经设备通过的分类、搜索、AND、回源，不重跑69项。一次核对剩余直接接线，不能每处失败拆新提示词或加通用框架。

设备前必须闭合两个接缝：
1. 保存前缺席探测：不能只因“用了base64”就认为多层引号已修。核UiAutomation实际命令解析/当前支持API，尽量沿已核可传stdin脚本的真实通道；中文书名号、空格及脚本自身引号都须正确。针对实际生产入口证明：缺席产生明确rc0标记；已存在/目录或探测失败不许可保存；输出为空不等于成功。可用不产生资产的窄只读验证，不额外开启业务主入口，不创建/删除未知文件。必要改动由你闭环编译，不重造工具框架。
2. 恢复分阶段：v11 blanket preserve跳过包恢复错误，简单无条件卸载也可能丢失尚未拉到E的唯一证据。明确“未创建导出”“已得到URI且已保全”“证据未保全/归属不明”三个实际状态。未创建且初始/最终目录证明成立时恢复包必须继续；已创建时先保全确切URI/原字节/sizeSHA再做精确清理和卸载；取证失败先保全证据并阻塞依赖清理，原test代码/数据及其他可独立恢复项仍继续，不能全跳过。系统选择器残留按明确归属退出/返回原前台再保护final，不改系统或杀VM。保留业务/取证/清理/恢复各自错误，不以随后异常覆盖根因。

修完整链而不是只修首个报错：provider primary身份/UI标题、StorageVolume独立描述、canonical目录、缺席证明、实际默认文件名、系统返回URI、原字节、内容断言、精确清理、清空持久化、取消未写入。未知界面或身份保存前阻塞，不模糊选第一个目录，不假设显示字段相等。

若输入不变复用两APK；仪器确改才必要test构建，产品不改不重建。冻结输入后再构建，实核实际动态依赖及恢复资产，用本批新run/输出/输入绑定，不运行旧v11 inputs，不只改编号。保存前/后失败树、截图、确切URI和字节证据须在后续断言前保全。

用户当前设备授权：使用或必要启动项目隔离MuMu实例1，已授权范围可关闭占用游戏；全程不关闭/重启/杀VM，不操作共享ADB服务，结束保持运行。不重复询问同样范围。
ADB D:/Application/NetEase/MuMuPlayer/nx_main/adb.exe，每轮fresh核serial/boot/owner/前台/预算，所有命令绑定本轮serial。预算覆盖完整阶段上限加至少1200秒恢复预留；严格原71私有/820外部、DB/WAL/SHM、原test代码/完整数据/用户状态、宿主APK/设置和strict night=no合同不放宽。
本批一次明确主run/attempt，含入场失败；失败停止业务并完整恢复，不同批修码再启第二次主入口。只读探测/必要同run恢复不是新业务attempt，必须如实分开。

验收：真实用户导出入口→系统选择器→返回URI实际原字节（标签/正文/摘录/来源）；明确清空分类并等待同精确行持久化、来源不变；取消编辑不改同一行payload与有效更新见证。既有通过项仅为必要真实夹具/快速回归，不能DB灌入替代用户流程。
删除许可只覆盖确证本轮新建、初始缺席、URI/路径绑定、E原字节size/SHA及设备当前字节核同的单个精确资产。不凭名称/单owner标记许可，不递归未知目录，不把探测错误当不存在；删除后严格复核，失败阻塞。

完成REPORT/ready/最小patch/前后SHA/必要原始日志XML和导出字节，区分实现/离线/模拟器/整链及恢复；旧FAIL和首次恢复错误不改写。更新current和本批协调报告，根因未知明确未知，不拿恢复通过代替业务通过。
下一派发前方向自审，用户要求加快，避免无意义重复检查或拆批。无Git暂存/提交/push/PR/发布、无D产品回灌、桌面/正式库/HOLD/其他应用私库/云部署或存量资产删除迁移。你不创建自动化、不向其他应用发消息；Root负责自动接续与限额转ZCode。
最终简列已完成、未完成、下一步，最下面单独写：这是任务94-v12。
````
