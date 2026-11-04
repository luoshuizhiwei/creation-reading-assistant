# 任务10-v2：保存链修复与联合验收

提示词准备时间：2026-10-10 11:18（香港时间）。推荐DeepSeek；作用：有效诊断、同功能最小修复、31联合与恢复。单owner可开始，不另派并行设备任务；未自动发送外部应用。

````text
执行任务10-v2，技术ID：CRA-JOINT-31-ACCEPT-CLOSURE-v2。沿用任务10编号，修复v1未闭合的问题，不重做已验任务1—9。用户粘贴后不再参与中间技术步骤，你连续完成诊断、最小修复、定向验证、31项联合和恢复，再交Codex核查。

先读：
C:\Users\23254\.codex\AGENTS.md
D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919\AGENTS.md
该仓库 docs\handoff\current.md
docs\coordination\tasks\000-049\2026-10-10-task10-v1-codex-review.md
E:\creation-reading-assistant\task10-codex-review-20261010\task10-v2-input-binding.json
原任务完整权限/合同见 docs\coordination\tasks\000-049\2026-10-10-task10-joint-31-closure-prompt.md；本v2覆盖其输入、诊断重点与运行安排，其数据/Git/设备边界继续有效。不要读其他应用私有记忆库。

输入与唯一所有权：
只读输入根 E:\creation-reading-assistant\joint-31-closure-task10-v1。
产品 candidate\android：996源码，仍为已核任务3-v9字节；验收工件 candidate\acceptance：42项，只有j1_ui_callback.py相对Root9变化。
冻结输入：freeze\source-sha256.json、acceptance-sha256.json、apk-freeze.json；完整SHA在Root的v2-input-binding中。先实际核指纹与执行占用；若已有同任务有效成果，检查后续作，不覆盖、不重复派实现、不杀他人进程。旧v1所有文件/失败轮次保留。
你的新输出根 E:\creation-reading-assistant\joint-31-closure-task10-v2；仅拥有此处产品/仪器测试/验收驱动/工具、配置、freeze、runs、logs、evidence、REPORT.md。共享源码、设备、Gradle均不得与别人同时写或运行。
登记实际应用/可见模型ID；不可见写未知，推荐与实际分开，不切节点或排查网络。

已核事实与禁止的误判：
v1最终r9是10 PASS/1 FAIL/20 NOT_RUN，DEVICE_ACCEPTED=false；31固定分组4 J1_NAV、12 TXT_ORDINARY、8 TXT_CLAMP、7 D2_RELINK。
失败j1_55s：UI marker确有位移，但阅读器仍存活时S2的offset/updated_at仍等于S/R；握手拒绝后未退出，所以仪器首先报缺handshake_exit.json。它不是根因，也不能靠制造该文件修绿。
“ADB注入导致不保存”尚未证成；两份probe不构成有效对照：一份一直rows=[]，另一份前置仪器方法均FAIL/缺binding。不得复用它们作为产品根因证据。
静态SQL路径指向正确隔离包，未查到具体解析错误；不假定读错包，也不假定产品保存正确。必须取实际状态证明。
原保护qahost的WAL/SHM曾消失，已按备份恢复字节/mtime，但触发来源未定位；不能凭猜测写“由SQLite checkpoint导致”。Root核DB备份与恢复后账，当前MuMu关闭。

先补隔离与诊断，再修复：
1. 上机前审计所有SQLite/导入/诊断目标，增加可验证的目标身份检查及实际命令审计：查询只准本次隔离宿主的库，核绝对路径、包UID/target；拒绝原保护包、正式包或无绑定目标。保护库不拿来探schema/试SQL。基线取证仅按既定文件盘点、stat/SHA/备份，不通过SQLite打开保护原库。
2. 从现有日志和实际进程/生命周期定位或排除原保护包sidecar变化的触发通道，区分误选目标、后台生命周期和工具动作。必要进程隔离须先登记、结束恢复；不可删除sidecar或放松恢复门禁。若历史来源无法追溯，诚实保留未知，同时以目标拒绝门、命令审计和前后文件核对证明本轮不再误触，才继续验收。
3. 优先复用已有可观测通道；不足时在自有候选添加必要、隔离的诊断，记录真实LazyListState index/scrollOffset/canScrollForward、pendingInitialPosition及恢复门控、debounce/守卫结果、SaveProgress派发/仓库落盘与独立SQL行。绑定同一task/run/book/位置，保留原始状态，避免人为采样排序或一次yield证明时序。
4. 据证据决定修哪里：列表没更新则修实际输入命中/仪器；列表更新但pending/guard拦截则修具体门控或恢复接缝；门控放行但未派发/未落盘则修ReaderProgressEffects、回调、ViewModel或仓库的最小相关路径。不要预先把ADB手势换成Compose手势就宣称产品根因关闭；有效对照须先满足同一夹具/绑定/模式/marker/真实位移条件。

自主修完的范围：
你已获同功能内产品、仪器和驱动修复范围：阅读恢复/临时返回、TXT保存/clamp、D2重新关联及对应测试/夹具/配置/工具。涉及相关文件时自行完成，不再以“需要产品授权”或“另一个文件没列名”为由让用户中转。
禁止扩展新功能、桌面、DB schema/迁移、依赖升级、正式签名或runner隔离策略。保持pending/marker和完整S/R/S2、updated_at、活着时先取S2再退出、新位置退出后观测与冷读合同；不能用退出后双采样、直接写DB进度、放宽断言或伪造握手取代。
保留v1的sqlite3旧版本兼容、数值归一化与真实scroll模式修正；为实际改动补针对性的有效正反验证，不重跑未变100/64 JVM或全量套件。

减少等待的执行安排：
先用真实依赖前置（夹具/绑定/S/R）定向复现j1_55s并诊断，再确认新位置退出/冷读。可在自有候选增加严格限定的诊断入口、注入同款真实消费者；诊断结果不能冒充完整31联合，不削弱joint_run.py原有完整31门禁。
同一输入、同一首异常未新增证据时，不再整批重跑。原v1已经9轮，不复制这个循环。
输入源码/仪器未改时复用已冻双APK：host SHA 187ee9e7a43d08560c0af0372fc06c560f3c30d8779e5138e58546246c11dd57；test SHA 4ad918bb4a02591e8a96b781d8786948fa83c2b05407fe704e4b79672adce6f1。
只有源码/仪器变更才定向回归及按原qahost合同重构建/冻结。复用原E Gradle缓存、JDK/SDK，持Local\CRA_Android_Gradle_Execution互斥，offline/no-daemon/max-workers=1；不要在D构建。
定向链闭合后由你唯一owner，用driver/joint_run.py真实联合入口与before/after消费者完成最终31项，保持固定模板顺序及4/12/8/7分组。最终源码/APK/配置/消费者/原始结果须同版本绑定；旧PASS只按明确未受影响依据复用，不能拼接不同版本冒充31/31。新失败继续在此同一任务诊断和最小修复，不拆给用户转发另一个owner。

设备与恢复：
仅隔离MuMu，不是实体手机。先核启动前状态、adb devices及本轮serial；全程adb -s <实核serial>。实例1原始关闭、shell uid2000、night no；启动漂移也登记并恢复。
宿主com.creationreadingassistant.qahostj1persistv2，测试同名.test；runner沿用CraIsolatedTestRunner；签名/target身份按输入freeze验证。不得操作正式书库。
保护原qahost/.test、原APK对及持久文件，保存本轮完整size/SHA/ns-mtime、DB/WAL/SHM、8设置、forward/reverse、shell、约定进程/电源状态；上一71项只是参考，分母重新核。
使用已真实验证的Root9取证驱动：任何每包卸载/清数据前，私有原件→原子自有暂存→本地size/SHA三方核同，精确清理成功才放行。保留冲突/源变化/部分复制/清理失败拒绝、0字节WAL区别缺失；不绕门、不先卸载后取证、不递归清共享tmp。
成功或失败均finally取证/恢复、逐项后账核同；数据损坏或恢复缺证不得写通过。恢复原数据/DB三件/mtime/APK对/设置/映射/shell/新包absence/自有创建物与约定电源状态，保留失败原件。只清此次登记的自有对象。

交付与停止：
未授权D产品回灌、暂存、commit/push/PR/发布；不动原损坏仓库、桌面/HOLD、Claude/DSH私有库，不发外部消息/自动接力/恢复定时任务。
保留NOT_COVERED：X整机中断单点清零、Back专属完成归因、全部pending保存排空、初始新save、实体手机/MIUI。
真正越出范围或缺外部资源才保留现场、完成可做取证与安全恢复后报告；一般代码/配置/相关测试故障自行修完，不要求用户做技术步骤，不无限盲试。
REPORT.md中文记录编号10-v2、实际应用/模型、输入SHA与改动、根因证据/有效对照、保护库访问审计与未定位项、命令及真实exit、定向和31矩阵、最终源/APK/config/run绑定、完整恢复后账、复用依据/未覆盖项/所有权释放。原始stdout中的JUnit结果与shell exit分别记录：exit0不能替代测试PASS。报告写完后生成证据SHA清单。
仅最终31项合同和数据恢复均满足才DEVICE_ACCEPTED=true；否则如实FAIL/NOT_RUN。全产品分母未核。
结束直接给用户“任务10-v2已交付，REPORT绝对路径，31结果和恢复结果，是否有阻断，等待Codex核查”，不让用户代收日志、选命令或联系其他窗口。
````
