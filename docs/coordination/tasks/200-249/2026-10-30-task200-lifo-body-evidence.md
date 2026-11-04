````text
# 任务200-v1：199失败的正文身份判定最小源码闭环（仅外部差量）
2026-10-30，Antigravity当前Gemini3.8FlashHigh，同原199会话owner。预计20min。只读原证并产最小源码patch；Root并行核199恢复原证，两线独立：你不访问设备/ADB/恢复、不改candidate或APK/Gradle/globalstate，Root不改你的外部源码。不要为凑数量重复旧验证。

先读活动D:/develop/Code/Codex/creation-reading-assistant-recovery-20260919/docs/handoff/current.md及199原ready/REPORT。199唯一main rc1/363.5s，原instrument stdout明确Testsrun1Failures1/terminal-1，ReaderInternalTemporaryLifoTest.kt:352 awaitFinalOrdinaryLayer失败：backToNormal temporary target1 '第一章 序章' must not be on ordinary page。原pageText同时含普通填充正文、第一章 序章、TXT·第一章 序章·46%；不能直接断言产品未返回，也不能未经证明说只是测试误报。

原证根E:/creation-reading-assistant/task199-reader-internal-lifo-device-v1；scope E:/creation-reading-assistant/task192-reader-internal-lifo-host-offline-v1/attempt-T165V1-20261030-121232。Root初版23pins中21同，build_deliverables_199.py及generate_final_deliverables_199.py缺失；旧readySHAea429b739bfce032ff3198ba99f175b10c89db835d1d7e4bb53f2f27472a41b9已在Root记录。收尾最终ready6607B SHA38278221f40e823783527a870b5bc5765cb07844edc17944c9c1a22288f698ad，freshUI显示remove temporaryPython scripts，属于明确禁删约束下的越界，不能用新索引抹历史。只在本任务REPORT如实说明现成记录可证明的删除/索引改动，无原文件就UNKNOWN，不删除/重建同名脚本假装旧证，不再重写199原件。

只读candidate E:/creation-reading-assistant/task59-common-candidate-v1/candidate/android：ReaderInternalTemporaryLifoTest.kt 28183B SHAa4bcca37582a69fff9dbcd751e270497e55c3c614d467740b62ac9555988071d，现target1Text="第一章 序章"；displayedPageTexts/pageShowsTargetText搜索多个displayed semantics文本，target是章节标题可能在普通页chrome复现。原fixture0aea2c5eed35d7f189df72590013fdea1bdf658045b5ae7373d16617d9265ce9不改。产品APK277b83bf/test122ea146冻结不写。

目标：用最小实际源码证据分清正文与chrome，修正本次有判别力的正文身份采集或目标选择。先检查真实paged/scroll渲染的semantics边界、正文annotation、标题/工具栏、fixture现有唯一正文及两source合法offset。若确认当前target在chrome命中，优先让正文判断只读取真实正文节点，或选择原fixture中合法、全书唯一且与S0/T2互异的精确正文目标；给出源码证明与前后差异，不能随意删除负断言、改contains为无证标签、改scroll规避或把临时目标设S0。真实产品导航若有缺陷只报告准确路径，勿扩大修改范围。

必须保持：真实默认paged+on，普通S0屏显且真实权威保存排除seed；T1/T2/回T1精确正文正断言+对立正文/普通marker负断言；最终普通marker恢复且两临时正文不在普通正文、返回阅读处按钮消失；两源与合法offset/独立fixture、完整12列exact、普通重开S0及时间/locator强合同、197普通Back/lifecycle合法保存/失败取消防重保护。禁止通过放宽语义使测试绿。

交付仅E:/creation-reading-assistant/task200-lifo-body-evidence-source-v1/：必要after/最小patch、before路径SHA引用、REPORT、原始定向离线证据和pins(不自引用)，ready=根ready.json。不要复制before工程或现有APK。只有有判别力且不需要复制工程的定向离线验证可跑；无合法编译环境就明确待Root集成编译/设备验证，不用文本搜索/Python模拟当Compose运行绿，不重复197套件。Root验差量及恢复资源交接后另行同owner集成编译，不提前写共享candidate。

source/patch/REPORT/ready<=128KiB，普通文字raw<=64KiB，无大目录/构建/新依赖，先估算，实际互斥计数；超额原证留不删。所有原193/199业务红及恢复限制保留，不自动业务重跑。禁止ADB/VM/设备/安装卸载stop恢复/launch/reboot/connect/kill、Gradle/candidate源码APK写、冻结hostcollectorconsumer修改、Git暂存提交pushPR发布、删除移动证据、云正式库私库sessionDB/.codex/memories/自动记忆。用户stop/Esc/安全权限/所有权冲突停对应工作。末尾用户可见总结独立一行：这是任务200-v1。
````
