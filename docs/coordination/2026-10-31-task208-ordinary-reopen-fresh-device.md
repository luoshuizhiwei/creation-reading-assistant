# 208-v1：新APK普通退出重开单批MuMu合同（待用户授权，未派发）

2026-10-31香港时间。207-r3/r4源码及匹配APK对已通过Root限定交付审查；全部androidTest仍NOT_RUN，205业务红保留。当前自动交接授权仅离线工作，current明确无ADB/VM/设备/恢复许可；本稿准备完整后再请求本批授权，不先发送执行提示词。以下只有本批授权明确落到current/state后才生效。

接任与权限：用户可将本合同直接交给其他agent并明确要求执行208及本稿列明的设备/恢复范围；接任协调者把该轮指令记入current/state、绑定唯一owner后即可推进，无需原Codex在线或重复批准。若仅阅读/审核/文档工作，或暂停未解除，仍不得执行。自动化保持暂停；用户手动交办不要求电脑代发。其他agent不得因此读取彼此私有记忆库。

1个任务，唯一设备/runtime/driver owner；用户指定的其他agent或原ZCode会话可承接。电脑交接操控使用GPT-6 Luna，设备执行模型以ZCode当前实际菜单记录。预计业务30分钟、恢复预留20分钟，45分钟评估；安全恢复不能被预算截断。没有必须新开的独立源码工作，不为并行凑任务。不得改candidate/Gradle或冻结host。

````text
执行任务208-v1前，必须确认current/state已明确记录本批设备授权；本稿待授权时不得ADB/VM/设备/恢复，不能从历史合同推当前许可。用户本轮明确交办对应设备/恢复范围后，由Root或用户指定接任协调者记录该批授权及实际交接时间，唯一owner再开工。

目标：在全新fresh身份/基线/恢复合同下，只执行一个普通退出重开用例，验证207修复后的新APK是否解除205普通退出等待书架超时。205旧run/原红永久保留，不重用旧attempt或自动重试；不是全产品、物理机/MIUI验收。图测试7项和pending失败/取消真实UI仍不在本批运行范围，继续NOT_RUN。

先读活动仓库AGENTS/current和稳定机制合同docs/coordination/2026-10-30-task202-lifo-body-device.md；旧路径查docs/document-map.csv。202旧APK/版本/owner/发送时间不是本批输入；只继承本稿明确复用的fresh179r3/26role/166恢复机制。206有限恢复已收口，不重复它。

Root接受的只读candidate：E:/creation-reading-assistant/task59-common-candidate-v1/candidate/android。
宿主app/build/outputs/apk/debug/app-debug.apk：53899310B/SHA c396ebe14b1583180c005dabbc51184826d05a0838933203a21adf5bb72b1d31。
测试app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk：10724766B/SHA e4b7b70b00e49adf5e420f99812b688d019233ccdf4784593fcbf7aeb8f5a86f。
证书SHA256 05249d786e3d3d117bb5bd7b1f83512a0c9b8f1ee4e502112c4b2d9ccbd7b9a1；runner com.creationreadingassistant.testing.CraIsolatedTestRunner，target com.creationreadingassistant。
生产ReaderRoute.kt SHA5368065b25a85fe1437e638ac52e02c4e4041c1bf7352bc926a0765a07ee7a0e；203冻结测试SHA1f2895409b6e90d0f0e2f4e4f943d5acf040bb6618365ad754636681db4f02af；图测试d2a08684cc30374030b2e672f8d002b8e3cb083a3af2d27b669130de0821c1ea；fixture维持0aea2c5eed35d7f189df72590013fdea1bdf658045b5ae7373d16617d9265ce9，开工核实际SHA，有漂移停。

执行和恢复：
1. 唯一原MuMu instance1，创建时间1777974446694936只作绑定校验；先adb devices，再由完整fresh179r3实际identity/唯一owner/transport/boot验证本轮serial。历史127.0.0.1:16416和旧boot只读对照，不写死。只用adb -s 本轮serial；原VM离线时仅官方正常启动原instance1和必要连接，在线不重启，不换实例/重建删盘/改GPU或安全设置。
2. fresh26role+完整179r3 strictidentity、APK归属、无活动instrumentation；实际产品pm/user0及CE/DE/external三根确认基线，原testAPK/data/userState、八设置/focus、71private/820external/DB-WAL-SHM及strictkeepalive完整原始基线。原test0d18d788a04e366271df3ad2ac18cc2ceeb95edc3c64c37cb995745aefbc9a9c/inventoryd6ac3c5ff7845b4afc9b384fb11b7a2bf921b9f722d4706bb6fe10801d003e29仅作已知基线预期，实漂移BLOCKED，不自动改数据迎合预期。实际argv/rc/stdout/stderr判定，查询失败不能当空值成功，manager原flags保留完整resolver判断。
3. runId沿冻结入口合法T165V1-YYYYMMDD-HHMMSS格式，实际当前时刻生成；任务报告根E:/creation-reading-assistant/task208-ordinary-reopen-fresh-device-v1/。执行scope仍在原TASK192合法根下新attempt-runId，不能绕路径门。冻结host入口E:/creation-reading-assistant/task192-reader-internal-lifo-host-offline-v1/host/entry_lifo_192.py和host192.verify_inputs_192全核；184r2 keepalive.bind(host192)绑定真实parent。只在本任务最小input-prep/driver派生输入，所有26role依赖、runId、scope、testAPK、source、owner、preflight、serial/boot真实一致，不修改冻结host/collector/consumer/resolver来绕门。
4. 完整预检后仅一次main、一次instrument，唯一方法com.creationreadingassistant.acceptance.ReaderInternalTemporaryLifoTest#readerInternalLifo_twoSources_returnChain_preservesOrdinaryRow，expectedCount1。必须按当前冻结203实际方法体保留S0权威保存排除seed、临时行12列exact、正文正/对立负、真实移动合法保存、T1/T2/回T1/普通S0/一次普通退出书架及重开S0全强断言。原argv/stdout/stderr/terminal/count/rc/DB落盘；业务失败KEEP，不换包、不修改断言/timeout/fixture/源码/模式、不重试。
5. 166完整恢复：真实冻结host166.run_recovery/sameHOST158/preserve166/RUNNER/父正常恢复体，184r2绑定实际HOST158.H.R3.load_parent及已返回b对象，正常/异常finally还原。完整pre三根manifest/tar成员type,size,内容SHA读回→必要单user0stop→post静止集合writer→父正常uninstall门；stop/uninstall按冻结合同次数，原回执及时独立保存，不等最后总写，不重复操作补证、不手写简化卸载。恢复失败KEEP，保全原证不循环、不改门、不硬编码binding=true。
6. 独立恢复消费：原testAPK及data/inventory/userState/八设置focus、71private/820external/DB保护、strictkeepalive原rc与实际DEV回执、原宿主/APK对与安装状态均核。userState不得只给stdout片段，父卸载rc0/Success与after查询rc和缺席证据单独保存；pre/post四tar核真实成员type/size/内容SHA，不能只核整tar哈希。最终完整identity在全部保护查询之后；恢复缺证/损坏不得写全绿。全部书名/记录中性测试TXT/EPUB。

交付本任务根REPORT.md/ready.json、最少runtime/inputs和完整原rc/log/恢复原证。业务和恢复分开裁决，ready不是通过。预算从实际发送起算，未知不补造。复制前估算新增与E可用；source/runtime/report/ready≤256KiB、普通raw≤512KiB，task+scope合并互斥统计；必要保护capture/tar单列实际bytes，不排scope逃预算，不删脚本或截断原证。复用已冻结APK和依赖，不复制工程/cache/历史证据/APK对。已有资产不擅自删移。

无Gradle/生产或测试源码修改、第二业务、其他实例/实体机、Git暂存提交pushPR发布、D回灌、正式库/私库sessionDB/记忆许可。用户stop/Esc停止新增业务输入；已开始的安全恢复按166合同保全/安全停止，不丢原失败。权限登录/owner冲突或基线漂移停相应操作不旁路。最后独立一行：这是任务208-v1。
````
