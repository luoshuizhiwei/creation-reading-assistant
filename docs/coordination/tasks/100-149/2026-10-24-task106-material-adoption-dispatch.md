# 106-v1素材整理采用窄旅程与独立L1差量

| 编号 | 香港时间 | 模型/owner | 作用 | 并行/依赖、耗时 |
|---|---|---|---|---|
| 106-A-v1 | 2026-10-24 08:02 | 原Claude workbuddy/cn:deepseek-v4.1-flash 1M | 合并摘录→采用→筛选找回真实UI窄验收 | 唯一test构建/设备；业务上限1800秒+至少1200秒恢复预留 |
| 106-B-v1 | 同批 | android_worker既有s1_error_feedback | 真实DataStore保存筛选roundtrip差量 | E独立lane-b，只读workspace不构建；A恢复结束后才交共同owner集成验证 |

方向自审：105的播完本章实现/JVM接受，不为真实音频缺环境继续设备矩阵。I1/I2生产已有实现，近期94只覆盖分类/来源/导出，未覆盖多摘录聚合和采用回顾；L1 VM仅mock store。两条真实独立验证缺口可并行，均不预设产品缺陷、不重造功能；设备与共同build单owner。

````text
开始106-A-v1，原有Android轻量取材连续旅程窄验收：本批两条新测试摘录→合并素材卡保留两来源→详情新增文字采用去向→列表已采用筛选找回→返回/重开后来源与adoption仍在。不是新增素材工作台/专题/桌面功能。先读有效AGENTS/current及docs/coordination/tasks/100-149/2026-10-24-task105-root-review.md和直接I1/I2相关入口/原94可复用证据，已加载未变复用，不扫历史。严禁Claude/Cowork/Claude-Code/DSH/.codex/memories读写含自动记忆；Root D协调/current，你只写E。原模型不变，明确quota才保全报Root；网络错非额度，不建自动化或发其他外部消息。

105 Root已核Effect363e72eb290b5be38a1d23436e1d83f7de3157d44469b4b35ea5934688cbd5d9、测试0741093b/原红3中1fail→57/57原XML绿，产品bf9720a74f6007dacff3e51b3421567b4578e5a977beaef6dc49269890831330(52763781B)当前build输出，未设备；不重复105/104/timer/94导出。旧694/test960在103/preserved保全。bf覆盖或实际执行前先查同SHA原字节保全，无才必要单份；当前workspace/test960原字节已保全，只引用不复制。当前无已证产品缺陷，不能为了新批改产品代码。

依据已读生产链：InspirationList236-259阶段计数筛选，397-430多选MergeToMaterialCard，385-389详情；InspirationDetail498-513展示adoptions并新增/删除，720-815文字/链接去向；VM262-285保留payload、332-360采用推进状态、364+合并。直接MaterialOpsTest68-115采用、118-262多源locator/roundtrip、285-301阶段，VM449-503编辑保护字段已有范围，不重跑。94-v17只验分类/来源/导出/清空/取消，不能延伸成采用整链通过。

先核现有生产入口和现有真实UI instrumentation/fixture能力，复用94现有工具，不写同形业务policy；用生产Composable/VM/Repository实际点击/持久化。允许只为本批中性数据通过生产Repository创建两条新测试摘录和对应本批新中性TXT来源（如不用正文导入也可但明确fixture层级，不冒充阅读摘录动作）；只能读取/操作本批ID，禁止选择或接触正式/旧测试库。合并后的两个source/locator及文字adoption、used状态、筛选可达与保存返回/重开数据均需正向断言，不以只看无crash通过。所有夹具本批run/ID/输入绑定，原始断言与状态留证；当前目标不扩导出/删除撤销/链接类型矩阵/真实跨端。

只写106-A直接新增仪器/必要本批runner最小hunk/fixture和交付，不改产品I1/I2实现（如准入前确证可达缺陷，报告具体证据Root定下一最小修复；设备失败必须恢复停批）。不得为test参数改公共配置/Manifest/生产行为或新增依赖框架。你为唯一test编译/绑定/device owner；仅必要test APK构建，产品bf保持，精确配套身份绑定，不自动assemble产品/全库。并行worker106-B只读workspace在E/lane-b准备L1真实DataStore离线测试差量；你不复制或集成B到冻结输入，A恢复结束后Root核再安排共同构建，避免中途输入变化。

新106-A-v1 runId/源码/product/test/fixture输入登记，一批一次主run含入场失败，禁止同批失败修码重试/静默retry/重算attempt。业务总上限1800秒+至少1200秒恢复预留，准入前确认整个上限及能力；不足停依赖业务。fresh adb devices/serial/boot/owner/前台/预算，命令全-s；ADB D:/Application/NetEase/MuMuPlayer/nx_main/adb.exe。只授权MuMu1必要启动/可关闭已授权游戏，未知前台不强停；不关闭重启杀VM/共享ADB，结束运行。若复用104单方法host，必须将am selector与expected状态base class分开，先用现有104原stdout作一个离线接线核验即可，不扩解析矩阵/另开工具任务/设备确认run，不改104原FAIL。

保护恢复合同严格保持原71私有/820外部/DB-WAL-SHM/原宿主与test APK完整字节数据/inventory/userstate/设置strict night=no；前基线后核恢复，不以summary空错误代替原证据。仅本批新建中性fixture/摘录/书ID，清理走本批归属且不得影响保护正式资产；精确文件清理须初始缺席/runURI/E原字节sizeSHA归属及当前设备同字节单资产，之后复核缺席，不递归未知兄弟后代，探测失败非不存在。collect失败/唯一证据未保全/归属不明保留依赖资产BLOCKED，独立安全恢复继续，业务/取证/清理/恢复错误分开不吞错。无需要外部文件则不造，APK/DB恢复必须完整。

唯一workspace E:/creation-reading-assistant/task59-common-candidate-v1/candidate/android；Gradle home D:/develop/env/gradle，JDK D:/develop/Java/jdk-17.0.14，SDK D:/develop/Android/Sdk，锁E:/creation-reading-assistant/locks/android-gradle-exclusive.lock，你唯一build owner。输出E:/creation-reading-assistant/task106-material-adoption-v1/lane-a，差量4MiB、必要设备原始8MiB、必要test build预计256MiB；bf必要单份52763781B保护另列。B目录lane-b归worker，勿覆盖。复制前估可用空间，不复制工程/build/.gradle/.kotlin/Gradle home/node_modules/缓存/历史证据/APK对，不硬链可写cache，不删除移动压缩迁移存量。没有充分新证据不做额外批次。

交最小patch/改动测试源码/前后SHA/输入产物/原日志退出码XML/REPORT/ready/恢复，实际字节分类保留依据/build增量是否测量写同REPORT。严格区分夹具创建层级/真实UI/持久化/模拟器/全产品与未验，不把离线或seed当阅读全过程通过。所有权/输入/预算/恢复冲突停依赖业务，必要恢复优先。无Git暂存提交pushPR发布、D产品回灌、正式库/HOLD/云部署/实体手机/关闭其他进程/私库。Root核后继续既有目标，不把单批完成当项目停止。最终最末单独写：这是任务106-A-v1。
````

