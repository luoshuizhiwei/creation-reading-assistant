# 任务10：31项联合验收闭环

发送准备时间：2026-10-10 02:06（香港时间）。建议模型：DeepSeek；作用：同版本联合验证与数据恢复。可开始；本轮仅此唯一设备任务，无其他并行设备owner。实际发送/启动待用户复制，Codex未自动发外部消息。

````text
你执行创作阅读助手任务10，技术ID：CRA-JOINT-31-ACCEPT-CLOSURE-v1。

目标：在同一独立E候选中，一次连续完成准备、输入绑定、31项联合MuMu验收、必要的同功能最小修复和设备恢复。用户发完本提示词即离开；所有路径、配置、命令、证据整理由你自行完成，不要求用户中间转交、选步骤或补运行命令。Codex负责最终核查；你的“交付”不等于Codex已经验收。

一、先读与占用
依次读取：
1. C:\Users\23254\.codex\AGENTS.md
2. D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919\AGENTS.md
3. 活动仓库 docs\handoff\current.md
4. docs\coordination\reviews\2026-10\2026-10-10-tasks-3-9-codex-review.md
5. E:\creation-reading-assistant\tasks-3-9-codex-review-20261010\task10-input-binding.json
只按这些入口访问相关证据，不读Claude/DSH私有库。记录实际执行应用、实际可见模型ID（不可见就写未知）、推荐模型与实际模型分别登记。检查本任务目录、构建互斥、进程与设备占用；有既有有效成果先核其输入与完整性后续作，不重做，不覆盖其他owner，不杀他人进程。若另一owner仍占相同候选或MuMu，保留现场并等待，不能再创建第二个实现者。

二、已核输入，只读原件
产品输入：E:\creation-reading-assistant\joint-j1-closure-task3-v9\candidate\android
源码清单：该任务 freeze\source-sha256.final.json（996项）
tree SHA：750333e3383e4dff6d9605a09744b4cd330a6a13eab263a28d774424d6281b1a
双APK及身份：该任务 freeze\apk-freeze.json 与 freeze\apk\ 下文件。
宿主SHA：187ee9e7a43d08560c0af0372fc06c560f3c30d8779e5138e58546246c11dd57
测试SHA：4ad918bb4a02591e8a96b781d8786948fa83c2b05407fe704e4b79672adce6f1
验收工件输入：E:\creation-reading-assistant\private-db-evidence-task9-root-v2-20261010\candidate\acceptance
对应冻结：该任务 freeze\acceptance-sha256.json；Root完整SHA绑定见task10-input-binding.json。
必须使用Root9补正驱动，不能退回task9-v1或旧直pull驱动。开始重算绑定所列输入，发现漂移先查明，不能自动把漂移重新签成已验基线。充分证据已成立的任务1—9不重新派发。

三、你是唯一集成、构建及设备owner
输出根：E:\creation-reading-assistant\joint-31-closure-task10-v1
所有权：仅此新目录内 candidate\android、candidate\acceptance、freeze、config、runs、logs、evidence 和 REPORT.md。
将已核产品和完整验收工件复制到新候选，保持产品输入字节；不复制build/.gradle/cache等大目录，复用已有E缓存与依赖。你不独占整个机器：不覆盖别人的改动、设备占用或公共文件。
先复用已核双APK，不因目录复制或配置改路径重新构建。生成新源码/验收工件清单，重绑定新路径、run_id、serial、APK SHA、包名、runner、签名与证据根，保存最终有效config及SHA、APK安装后路径/SHA读回；拒绝旧路径静默回退。证据清单在报告写完后生成，避免REPORT后改导致旧SHA失效。配置中各item既定子合同/task标签按消费者合同保留；新外层task/run绑定说明旧标签来源，不机械改常量破坏消费者。
宿主：com.creationreadingassistant.qahostj1persistv2
测试：com.creationreadingassistant.qahostj1persistv2.test
runner：com.creationreadingassistant.testing.CraIsolatedTestRunner
双方签名：05249d786e3d3d117bb5bd7b1f83512a0c9b8f1ee4e502112c4b2d9ccbd7b9a1

四、31项联合合同与真实入口
合同模板以已核acceptance\config\joint-config.template.json为准，并核其items与
E:\creation-reading-assistant\joint-mtime-contract-v3-20261009\candidate\acceptance\config\joint-config.template.json一致。
共31项，保持顺序：4 J1_NAV + 12 TXT_ORDINARY + 8 TXT_CLAMP + 7 D2_RELINK。
使用新候选 acceptance\driver\joint_run.py 的真实联合入口，注入 j1_ui_callback.py 与 joint_runtime_consumers.py 等真实消费者；不要直接用无回调的joint_driver裸run_all，也不要用task3仅4方法定向包装器冒充31项。
运行前自动完成离线配置校验、真实消费者接缝检查及输入/身份绑定；当前充分、输入未变的100返回JVM与64搜索/TTS JVM等复用，不无故全量重跑。
真实阅读器身份、前后非空marker、真实位移、完整S/R/S2行和updated_at、新位置退出与冷读、短末项clamp合同必须保持；UI活着时取S2再退出，不能用退出后双采样替代。dump/临时证据路径须任务/run/step专属；D2核正文身份、原子暂存、真实换入字节和回滚保旧。保留pending/marker保护，不用一次yield或人为采样顺序签产品根因。
每个item保存原始仪器stdout/exit、before/after消费者输出及原生/DB/UI证据，绑定task/run/item/包/APK身份。J1 journey03仍仅观察已有位置，journey04是纯逻辑；不要把它们升格为退出完成或冷读新save。此次重跑4 J1项用于同版本31联合及新恢复驱动接缝，说明原因，不重复独立准备任务。

五、同功能内自主修完，减少返工
遇到失败先保留原始首异常、原始FAIL/NOT_RUN和设备现场，区分产品问题、测试合同错误、配置/工具兼容问题。
你可以在自己的E候选内调整相关阅读恢复/临时返回、TXT保存与clamp、D2重新关联模块，以及对应仪器测试/夹具/验收驱动与配置，完成本31项已授权合同。不要因修复涉及同模块另一个相关文件便停下来要求用户中转。按根因做最小改动，先验证假设；禁止扩到新功能、桌面、数据库schema/迁移、依赖升级、正式签名、runner隔离策略或其他任务。
仅配置/取证脚本变化时，跑受影响离线门禁后继续，不重建Android。产品或仪器源码变化才定向JVM/编译、按既定qahost变体构建新双APK并重新冻结绑定；最终31证据必须明确对应最终源码和APK。不得拼接不同未说明版本的PASS，旧证据复用须列不受影响依据。
构建唯一owner串行；复用 E:\creation-reading-assistant\txt-persist-codex-completion-20261006\gradle-home，JDK D:\develop\Java\jdk-17.0.14、SDK D:\develop\Android\Sdk。遵守已有 Local\CRA_Android_Gradle_Execution 互斥；优先offline/no-daemon/max-workers=1，原qahost属性和构建合同从已核apk-freeze读取，不猜变体或改变runner。共享设备不做并行批次。

六、MuMu与数据保全是通过条件
本轮允许隔离MuMu，不是实体手机。MuMuManager：D:\Application\NetEase\MuMuPlayer\nx_main\MuMuManager.exe；ADB：D:\develop\Android\Sdk\platform-tools\adb.exe。
先记录启动前实例/进程状态，再按需要启动；每次实核adb devices、serial，全程adb -s <实核serial>。当前约定实例1、开始/结束MuMu关闭、shell uid2000、night no；启动导致night漂移也需记录并恢复。
保护原com.creationreadingassistant.qahost及.test的数据和原APK对；正式包com.creationreadingassistant应不存在，正式书库不读不写。保存本轮完整持久文件清单、size/SHA/ns-mtime、DB/WAL/SHM、APK对、全部约定8设置、forward/reverse、shell与电源状态。上一批71文件仅作参考，本轮分母要实核。
任何每包卸载/清数据前必须完成即时取证。新Root9驱动需要在真实MuMu证明：私有原件→原子认领的本任务暂存→本地备份三方size/SHA核同，并成功精确清理自己的payload/目录后，门禁才放行。0字节WAL合法，缺失与0字节分开；同槽碰撞/符号链接/原件变化/部分复制/清理或回读失败都拒绝破坏性操作，不覆盖/删他人目录，不递归清共享tmp。真实shell兼容问题在自有候选最小修复及离线/真实能力补验，不能先卸载再补证、绕门或吞错误。
失败也要finally取证/恢复；缺恢复证据、数据损坏或关键门禁未过时不能写通过。核对并恢复全部约定数据及mtime、DB/WAL/SHM、原APK对、设置、映射、shell、创建物、新测试包对的应有状态、正式包absence与启动前电源状态。只删除此次登记的自有创建物，保留原输入和失败证据；记录每个卸载之前的门禁和备份。

七、边界、退出与报告
不回灌D产品，不暂存/commit/push/PR/发布；不动原损坏仓库、桌面/HOLD、正式书库、Claude/DSH私有库；不自动发外部消息、切订阅节点、排查网络或恢复定时任务。
保持NOT_COVERED：X整机中断单点清零、Back专属保存完成归因、全部pending保存排空、初始位置新save、实体手机/MIUI；不要顺带宣称解决。
退出通过必须同时满足：最终输入身份可靠；31项按合同全部有原始有效PASS证据（按4/12/8/7分别列）；新取证驱动真实门禁成功；完整设备恢复且独立后账一致。否则保留FAIL/NOT_RUN，给出具体阻断和现场路径，不将“已交付”写成“验收通过”。若阻断确实越过授权或需要外部资源，完成能做的取证与安全恢复后报告；不反复盲重跑，不要求用户做技术步骤。
最终 REPORT.md 用中文填写：任务编号/版本、实际应用/模型、输入指纹、所有权与改动、环境、每条命令与exit、原始证据及SHA清单、31项矩阵、恢复与副作用、复用依据、未覆盖项、交付状态与下一步、所有权释放。记录旧失败，源码/APK/config/run/消费者证据完整绑定。百分比只对明确定义分母；全产品分母未核。
最后直接告诉用户“任务10已交付，REPORT绝对路径，31项各组结果、恢复结果、是否仍有阻断，等待Codex核查”。不要让用户自己收集日志或替你联系另一个窗口。
````
