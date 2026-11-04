# 并行返修及仪器测试交付核查

## 任务编号/版本、输入指纹
2026-10-04；CRA-ROLLBACK-WIP-RECONCILE-v3、CRA-JOINT-MUMU-DRIVER-v2、CRA-JOINT-ACCEPTANCE-TESTS-v1。实读 HEAD e39a78da9c62f02614207cffb36969a32492eaea，暂存区空。各原报告与候选保留。

## 所有权与改动、环境
Root 只读候选源码/原XML/编译日志；复用只读 reviewer 检查锁路径；重跑驱动离线门禁及独立纯函数反例。仅 E 盘核查产物及本报告/current 文档写入，无产品、Git或设备操作。

## 执行与结果
- EPUB 原始 7 XML 核定147/147、零失败/错误/跳过。finally 清理及日志保护已实现。但锁设计出现两条源码可达 P1，尚未运行复现，暂不接受整个v3。
- driver 重跑39/39通过，上一轮空合同/TERMINATED及已有沙箱拒绝有改善；额外反例依然错误通过：同run/config的record声称PASS、exit0，而raw含FAILURES!!!时cmd_report返回PASS；缺失item/restore run_id在expected_run_id给定时aggregate仍PASS。未设备执行。
- instrumentation恰3测试源payload，编译日志BUILD SUCCESSFUL；12方法仍NOT RUN。EPUB ZIP标记与清理SQL有确定实现缺陷，编译不覆盖它们。

## 未覆盖项与返修依据
### EPUB锁（只读独立复核，源码路径成立，非运行复现）
1. Repository:93持非重入EpubSwapGate调用:159普通parse；Parser:172-180/:228-240错误清理无条件重取闸，二次解析失败会自等待，无法回滚。明确已持锁内部路径，测试二次解析失败有界返回/恢复旧正文。
2. Parser:80-87在闸外算reuseExisting/landing；普通miss等待替换完成后仍以旧miss执行landingCopy:91-97，删除刚替换的新target。闸内重估现场，测试target先missing、replace持闸、普通parse排队、新target不得被旧来源覆盖。
3. 普通落位后解析与失败清理的所有权须一起审视，不能仅加更多锁掩盖重入或旧清理删除新文件风险。
### 驱动（纯函数反例）
1. cmd_report只检查raw存在，不复核raw真实门禁/runner证据，矛盾PASS记录仍被接受；必须绑定方法、run、退出码及真实runner原始判据，不需防恶意签名篡改，但需拒绝内部矛盾。
2. aggregate run_id接受None；cmd_report也接受exit=None的PASS。缺字段不能冒充一致/零退出。
### 仪器测试（源码与本地等价反例）
1. EpubReplaceFreshSourceAcceptTest.bytesContain把DEFLATED ZIP原始字节按UTF-8查正文标记。夹具经ZipOutputStream默认压缩，正文标记不能这样检索；应读正确ZIP entry或真实Parser正文，另作target与来源完整字节核对。本地DEFLATED夹具反例raw找不到V1MARKER。
2. tearDown对所有表统一WHERE book_id=? OR id=?；真实reading_progress/book_content/book_files无id，books无book_id。本地SQLite等价schema报no such column: id。按真实schema/DAO精确清理，finally关闭DB，不因首表错误跳过其余文件清理。
3. 新方法未在MuMu运行，旧MD通过不绑定新最终APK；SE4编译基树为rollback-v2而非最终合流effects，最终合流编译/行为仍需验证。

## 原始证据
[独立反例与原XML计数](<E:/creation-reading-assistant/three-followup-deliveries-review-20261004/verification.json>)；[仪器payload SHA](<E:/creation-reading-assistant/three-followup-deliveries-review-20261004/test-payload-sha.json>)。各交付报告中的原日志/XML保留。

## 恢复与副作用
无设备操作；纯函数/Fake反例；原输入、产品源码及Git状态未改。驱动离线测试短暂使用系统临时目录，不含APK真实大文件或设备数据；后续测试临时目录设E。

## 交付状态与下一步
本批落盘交付3/3（100%），严格验收0/3（0%，仅本批），三项均小范围返修；此前sync-md-join-v1的限定接受保留，不反推失效。最终合流/构建/MuMu未完成；不计算全产品百分比。返修独立并行，所有权按模块保持，禁止设备执行与D/Git写入。
