# 103-v1 TTS倒计时异步启动修复

| 编号 | 香港时间 | 模型 | 作用 | 依赖/并行、预计耗时 |
|---|---|---|---|---|
| 103-v1 | 2026-10-24 07:20 | workbuddy/cn:deepseek-v4.1-flash 1M不变 | 修复102真实Controller倒计时红例 | 原Claude唯一源码/build/设备owner；同问题闭环，约15分钟实现构建+至多30分钟业务及至少20分钟恢复预留 |

````text
开始103-v1，Root已核102失败原件与完整恢复，继续已授权既有TTS缺口修复，不需要用户重复授权。读取有效AGENTS/current与docs/coordination/tasks/100-149/2026-10-24-task102-root-review.md，已加载未变复用。保持原模型，明确quota才报Root；严禁Claude/Cowork/Claude-Code/DSH/.codex/memories读写含自动记忆；不建自动化、不发外部消息，Root只维护D协调/current，你只写E唯一workspace/交付。

102一次attempt T102V1-20261024-070800已经消耗，绝不可覆写或改绿。原Controller c005dcf3c61400c62b213943e1d04c61123f332c687834e6ad9ae90121e3955a，原仪器ca4d097f155b5724618d8a907a1e1a8d8570e145ddb7d514133f52b70fbab1b6，test d0d2f0ccdcfbd348efd9859db643df476443d32fc2453a73f61aae7cd6839b44(10567324B)已在102/preserved单份保全不复制。当前产品5049b01f2dcfdce48c0f5b9d450263105b089b6625d10cba1db9b10558a0778d(52763781B)，覆盖前先查同SHA保全无才必要单份。102 Root已核71/820/DB组匹配、原test SHA/inventory/userstate同、night=no、recoveryErrors=[]、产品原缺席恢复卸载、VM仍运行，独立新批准入仍须fresh核，不继承serial/前台/预算。

根因核定：startCountdown首个tick是handler.post立即入队，不是先等待1秒；play与另次onMain onStart之间可先执行，status仍idle，387行 rem>0 && status!=idle 导致stopCountdown清零，deadline仍在。最小修复去除与异步idle状态的不当耦合，以已武装deadline/remaining为继续条件；保留pause/stop/关闭/release取消统一清理，不添加新timer开关/策略/通用框架。不改真实分钟数、不扩播完本章(-1)/跨章/Edge/淡出调音量。没有证据证明到期stop失败，不这样宣称。

独占ReaderTtsController.kt最小hunk及ReaderTtsControllerAudioFocusTest.kt直接对应必要测试。先复用102真实Controller行为红，不为编号重复before设备；现仪器设置15→play/onStart→倒计时、关闭不停止播放、重设、pause清零、resume重武装、release清零，修后定向验证这一个用例。若测试确需补充首次onStart延迟条件，必须走真实Controller/Handler，不复制policy或sleep碰运气；不把待测实现写进断言。协调器8/8与98八焦点充分结果复用，除非本hunk实际影响需明确追加，不能全量重跑/造矩阵。必要main/test编译与产品+test配套构建同owner闭环；先保全实际原件，既有保护原字节只引用，不复制APK对。

新103-v1设备批只有在修复已落地且输入指纹/编译明确、新证据和102恢复闭合、充足预算下执行。新编号/runId绑定修后产品/测试/源码，最多一次主run含入场失败。业务阶段总上限1800秒+至少1200秒恢复预留，入场前确认足够；失败停业务、完整取证恢复，不批内修码再试/静默retry/重算attempt。沿用102/98现有runner和保护恢复，不重构工具、不扩解析矩阵。此为真实Controller+mock外部TTS/Audio，模拟器缺真实引擎，结论不冒充真实UI/声音/系统焦点/15分钟到期/实体手机MIUI。

唯一MuMu1必要可启动，fresh adb devices/serial/boot/owner/前台/预算，命令全-s；ADB D:/Application/NetEase/MuMuPlayer/nx_main/adb.exe。不能关闭重启杀VM/共享ADB，结束运行；未知前台不强停，仅已授权游戏可关闭。原71私有/820外部/DB-WAL-SHM/原宿主test完整数据APK/inventory/userstate/settings strict night=no全部先基线后恢复核同。无测试书需要不创建；若必要只本批中性fixture，精确清理须初始缺席/run URI归属/E原字节sizeSHA及当前设备字节核同单资产，删后核缺席；不递归未知兄弟/后代，探测失败非缺席。collect失败/唯一原证据不保全/归属不明保留依赖资产BLOCKED，独立安全恢复继续，业务/取证/清理/恢复错误分别记录。原102FAIL/ready/raw保持。

唯一workspace E:/creation-reading-assistant/task59-common-candidate-v1/candidate/android，Gradle home D:/develop/env/gradle，JDK D:/develop/Java/jdk-17.0.14，SDK D:/develop/Android/Sdk，锁E:/creation-reading-assistant/locks/android-gradle-exclusive.lock。输出E:/creation-reading-assistant/task103-tts-countdown-fix-v1；最小源码/patch/日志XML4MiB，必要设备原始8MiB，build预计256MiB，缺原产品单份52763781B保护另列。复制前估空间，不复制工程/build/.gradle/.kotlin/Gradle home/node_modules/依赖缓存/历史证据/APK对，不硬链可写cache，不删移动压缩迁移存量。所有权冲突/恢复或预算不明停依赖操作，必要恢复优先完成。

交最小patch、前后SHA、实际执行源码/APK绑定、原始退出码/log/XML/REPORT/ready/恢复；同REPORT实际字节分类保留依据/build增量是否测量。区分实现/离线/受控模拟器/真实声音未验。无Git暂存提交pushPR发布、D产品回灌、桌面/正式库/HOLD/云部署/实体手机/关闭其他进程/私库读写。Root核103后继续现行实际未闭合目标，不把单批完成当项目停止。最终最末单独写：这是任务103-v1。
````

