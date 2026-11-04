# 任务145-r1：真实JSON形状及未来运行绑定缺口，离线修复

2026-10-26香港15:15；原145 owner同Claude global Flash。不是重派v1，不设备。

````text
Root已读完整15893B host145.py、offline_validate关键正例构造与11项原log、REPORT尾部；v1口头离线PASS不接收可运行状态。原145交付冻结，所有修正在同task145/recheck-r1/，20分钟/差量512KiB raw512KiB，不复制父host或工程APK历史。
先核v1host15893B SHA22a51f3a76bcc4b4123a81c359bf552d2935bf105e708279da593cbce88c3881、inputs3592B；读取实际test11092359/helper，不修改任何Android源码/APK。

Root具体缺口（代码实际，非泛审）：
1. 仪器tearDown写t141b_99_assets仅runId/lastPhase/policy/assets，没有phase或packageName。host目前对所有docs调用_phase_ok，真实assets必被拒绝；合成put却杜撰phase/packageName让positive假绿。按实际结构对assets专门核runId/合法lastPhase/policy/完整精确资产归属；普通阶段继续phase/package强核。失败分支也必须核runId/phase/package/合法lastPhase与first_failure真实type/message/stackTrace，不能只字段非空跨run冒充。不得为host期望去改instrument。
2. helper ProgressRowSnapshot.toJson真实输出12业务列加legacyOffset，共13key；host _check_rows要求set(row)==12keys，真实值必拒绝，合成_row却漏legacyOffset。以实际13key正例，12业务列必须完整精确逐值(nullable保留)，legacyOffset作为已知派生观察字段合法且前后核同/必要关联；不能接受任意未知业务列也不能删业务列。正例必须逐块照实际记录形状，不为validator编造字段。
3. host当前只是本模块新constants与validator，h=import父后没有将h.ROOT/h.b.V2/DEV/LOGS/RUN_ID_FILE/PROT_OUT_ROOT/TEST_CLASS/RESULTS_PREFIX/fixture/runid/collect/evaluator/inputload等实际未来调用接线。父函数仍使用task136路径/t129/F1/pins；无main并不等于“已绑定”。本批允许定义未来可调用main/入口及最小运行绑定代码（禁止执行），用guard+stub离线证明真实入口会选择新class/craRunId/T141BV1、145输出/新保护OUT_BASE/task145-scroll-host-binding-v1以及t141b collector/evaluator/inputs。可以薄层正确接h globals或最小派生，必须给明确未来命令和参数；原父host136只读，不能靠未来人手patch全局变量才能运行。导入必须无设备副作用；禁止在可复用模块导入时永久污染全进程subprocess，离线guard在验证调用上下文安装并明确恢复。不要真的运行设备入口。
4. load_and_verify_inputs当前只比entry自己提供SHA，没有固定角色pins/新144freeze核对，篡改inputs的path/hash/bytes可一起改仍PASS。继承原严格pin语义，对newAPK/instrument/adapter/source必要角色及原恢复件固定值、schema/task/class/runid等真实约束核定，拒重指pin旧件；父host文件本身只读依赖也在import前核sizeSHA。把144freeze作为固定指纹依赖引用或明确新增roles，reader变更输入不可仅只列SearchScreen又声称覆盖readerfreeze。Root不要求固定九/十角色数量，要求实际闭包无漏项。
5. targetCardKey/topItemKey目前只非空、marker只前缀，不能证明run/book关系；按现有content-<bookId>-original实际key、target ordinal marker/title精确关联；pre topkey与目标可不同但必须有效本轮实际key，itemCount按实际结构确认不能无根据硬设>25。assets须完整25book/25fixture文件与本轮两dir，不只路径含runId；真实失败资产可部分存在但需准确归属不制造缺失项。first_failure保原因。

新增离线正负只覆盖这些真实缺口，不重复11项充分旧输入正例或泛审：真实assets无phase/package+13key业务正例应PASS；extra业务key/缺业务列/legacy差/错run failure/assets/key/book/缺资产/篡改pins/父host漂移需失败。标SYNTHETIC_OFFLINE，绝不未来evidence路径；旧11合成不得当运行验收。真实collect接线用stub命令/模拟本地结果验证，不ADB/adapter.run_check/main实执行；不可伪造设备JSON。

尤其恢复安全：不要只声称“复用父host即有三根保全门”。查实际新入口恢复分支原代码；若父136没有完整product CE/DE/external原始三根保全后才正常uninstall的门，需在本批新host最小补齐或明确BLOCKED禁止设备，不能让元数据收集成功替代产品数据原件保全；原138/139逻辑与保全格式可按当前链接只读复用，不重复历史设备。所有删除/卸载/force-stop属于未来批准批，本批不执行，恢复失败保留product，不吞首错/恢复错。

新产物host145-r1.py/inputs-r1.json或同新子根scripts/host145.py/inputs.json，patch相对v1+REPORT/sizeSHA/必要离线原log/ready.json；原145-v1/raw/SYNTHETIC原件保留，不rmtree旧资产。父host/base/adapter/guard不修改，当前新产品539f44c5/testf2d2982b与reader/Search/helper源码全部冻结，无Gradle/构建/compile。

禁止任何ADB/VM探测启停/安装卸载force-stop设置/hostmain真实执行/真实run-id/budget/attempt/summary/F1/设备；禁Git云正式库私库自动记忆/旧资产删移压迁/杀进程。Root只核离线实际差量后另派设备，不能把本次修复视为设备授权。完成等Root，最终总结末行：这是任务145-r1。
````