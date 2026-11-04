# 121-v1 F1修复与host独立复核

2026-10-25香港时间。Root已核A/B实际ready/报告及当前AppNavigation:192-205。A报告F1为静态风险，尚无设备复现，不能称数据已丢失。缺少真实NavBackStackEntry时使用底栏Home回退值触发破坏性会话清空不符合实际入口合同，授权必要最小接线修正。B改产品且唯一构建，A同时只读host保护/collect，是互不冲突的实际工作。当前仍不设备/attempt；不为F1修改系统字体/密度/语言或旧J1造覆盖。

## B 国内DeepSeek：最小修复与定向编译
````text
接续121-v1 B同owner，Root已读你的GATED_READY和A实际ready/REPORT。A发现F1：AppNavigation把navBackStackEntry初始null回退Home，effect据此调用endReaderSession；Activity重建时协调器跨重建存活，存在误清真实临时栈风险。只称静态发现未设备复现。现在授权你在唯一E工程只改AppNavigation.kt必要hunk：会话结束判据必须来自真实非null的backStackEntry及其真实destination.route；null/尚未恢复不是用户离开reader，不能借底栏显示fallback判会话结束。保留底栏既有routeBase逻辑，避免引入一次性helper/框架/无必要抽象；选择稳定effect key并核切换reader/非reader/reader与迟到请求语义，不改SourceNavigationContract/VM/119仪器/旧J1或其他产品文件。修改前SHA必须仍a4d2d5b92874576acb8032d2fc3cebc7f3f12cda1bb777588aa9e7e93b170ec9，否则先报冲突不覆盖；必要单份源保全、patch、前后完整SHA入本lane修复子目录，不覆盖原GATED_READY/输入原件。

同owner完成一次必要:app:compileDebugKotlin，若此接线改动影响既有JVM才加对应定向验证；已有24JVM同核心输入不重跑，禁止添加只镜像实现的测试。锁/环境/不复制工程缓存及每修订新日志路径沿121原合同，真实exit/stderr/log保全；不assemble产品或test APK，不ADB/设备/attempt。A正在只读审你现冻结host2222537e7465a0ad290309d78d8ed6bfa5f9d6b7c55d31897bad7236a9f5e10d，期间不改该host，等待精确意见后仍由你闭环必要修订。

输出lane-b/f1-repair/REPORT.md、ready.json、必要源码/patch/日志，≤2MiB（build增量另如实估计），附新产品SHA和仅真实离开域清会话的依据。尚无运行时复现不写F1设备修复已验，设备仍由Root核A最终接线/host后单批放行。私库/自动记忆、Git/D回灌/云/SFTP/正式库/存量清理/设备动作全禁，其他原121边界不变。立即实施必要最小修复→编译→交接，不停在建议。最后：这是任务121-v1 B-F1。
````

## A 全球DeepSeek：并行host独立安全审核，随后审F1实际delta
````text
接续121-v1 A，Root已核你F1静态发现及B实际host准备。B现在独占AppNavigation最小F1修复与一次必要Kotlin编译，你不改源码、不build/ADB，保持原lane只读审查。利用独立时间核B冻结host121.py（E:/creation-reading-assistant/task121-local-deepseek-closure-v1/lane-b/scripts/host121.py，23994B SHA2222537e7465a0ad290309d78d8ed6bfa5f9d6b7c55d31897bad7236a9f5e10d）相对host117基线df3ab14082a05d824a37c8f64756fc691af07eaf0fe882d000ea0c6eae812b34的必要delta，以及直接依赖保护适配器少量必要段落，不读全历史。重点真实119 class/runId/唯一方法/目录collect/成功原件绑定，入场失败算一次attempt、finally恢复不丢首错、collect/清理错独立、71/820/DBWALSHM/原宿主test完整数据用户状态/night保护、未知资产不删、不会引用旧6a/83d冒充新源码、缺输入严格拒绝不静默fallback；具体可执行命令与状态捕获是否符合合同。只给真实触发风险不泛审计，不跑脚本主入口、不import可能有副作用host，静态核必要内容即可。

输出lane-a/host-review/REPORT.md和ready.json，≤1MiB，精确host SHA/结论/发现/必要B修复范围。B修F1同时你审host，文件互斥，不互相等待。完成host审后，若lane-b/f1-repair/ready.json已经存在，直接核AppNavigation实际最小delta并给F1终审补充；若未出先报告host结果后等待Root接续，不自己轮询空转或新造任务。null回退Home不应触发会话清理；不假定LaunchedEffect前后调度已运行时证明，不要求系统字体密度改动来造复现，不改旧J1/扩大设备矩阵。保留F1静态/编译/设备层级。原私库/自动记忆/Git/源码buildADB/D写边界不变。最后：这是任务121-v1 A-host。
````
