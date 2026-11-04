# 81-v1-A/B Root核验与范围纠正（2026-10-21）

任务编号/版本：81-v1-A/B-Root。A证据与部分实现采纳，恢复入口暂不放行复用；B既有证据归并有价值，拒绝新增纯净包作为V0强制阻塞。80-v6-H已采纳业务与恢复不重开。全产品分母未知。

## 输入指纹、所有权与改动、环境
Root只写本报告/current及独立模拟反例；独立explorer只读B报告/直接Root链接/当前Gradle与Manifest。相关进程查询未见81执行脚本，未停止任何进程。A入口实际SHA8c7eb37f8cfd9b17c285a5a7a913634c4660d056120abe497d920f3e7c0b827d；19项绑定实际SHA全部匹配19/19。A原6项边界注入结果读取复用，未全量重跑。Root无构建/设备命令/外部消息。

## 执行与结果、原始证据
A/logcat-nemuinit-excerpt.txt直接支持nemuinit在10-21 07:52:50至07:52:53下发9条uimode_night yes（id367-375），以及附近clipboard事件。它不证明H旧run 18:36至19:01窗口由同一机制改写；没有时钟映射/窗口日志不能强关联。id375不是375次写入计数，50秒无新增也不足以证实事件触发机制。源码grep零命中不能单独排除所有产品/外部路径。A当前观察可作为模拟器设置来源线索，不修改既定night硬门禁。
A称全量dump可复现过强：只保留摘录，原环形logcat会滚动，已删全量不能保证重新取得同一原字节。保存现有摘录，不补造全量，不重复读剪贴板或私人内容。

## 恢复入口实际反例与未覆盖项
Root直接导入A实际入口，复用其Harness只替换设备/进程外部边界，在独立新fixture运行3个反例，无真实设备动作。证据E:/creation-reading-assistant/task81-recovery-closeout-v1/root-review-d0775fb0/red-cases.json。
1. 已存在recovery-completion.json预置原字节被覆盖，入口rc0；默认mkdir(exist_ok=True)不保护历史输出。
2. bound=false仍先请求restore_settings和final_protection_check，直到保活才判归属失败。变更设置前未核真实实例归属。
3. summary.freeze_sha256错为全零仍请求三步骤并rc0；入口固定v6 freeze，未在变更前验证该attempt/输入/绑定适配性。模拟成功时final_protection=null仍ok=true，说明当前测试也未约束真实proof消费。
这些是实际入口反例，不能以6项绿测替代。只代表模拟边界下请求，不声称真实设置被改或文件被删。入口应在下一小修中先身份/attempt/输入及并发准入，独占新输出，严格消费本轮proof后才允许成功；不扩大成通用框架。

## V0归并与范围裁定
B把MaterialGatheringJourneyTest.kt单文件SHA077b77fb…称整个源码版本，纠正为该文件指纹；共同候选身份应引用既有freeze/provenance源码清单，不用单文件代替全树。
B关于D/F/备份复用已有直接Root裁定。Root只补核65-v2/root-review-receive/checks.json所列13个相关生产/仪器/构建依赖当前SHA，13/13仍匹配，包括ReaderNavLateDisposePersistenceTest、ReaderPlatformEffects、D探针、四阅读保存文件、LocalZip/WebDav及4配置。按2026-10-20-tasks64v2-65v2-root-review原边界复用，不重开D/F或备份测试，不推广全部依赖、自然墙钟或真实弱网。MD/取材剩余接缝由80-v6-H补足。B不存在已知业务缺陷的结论按已验合同范围理解，不推出全产品无缺陷。
不采纳B把标准自用包/DEX零测试类/无桩冷启动列为现行V0-5必需阻塞。当前V0-5要求共同源码、host/test、受影响MuMu和恢复后账；debug applicationId=com.creationreadingassistant已有配置，qahost为独立后缀；配置存在不等于debug APK已构建。无需新增selfuse变体或改build.gradle，不派B建议的82纯净包构建/设备任务，不自动回灌D或Git。

## 交付状态与下一步、恢复与副作用、停止条件
80-v6-H四旅程与恢复采纳不变；B提供的旧D/F/备份证据按限定合同复用，标准包不是新增退出门。A下一82-v1仅修恢复入口真实安全缺口并离线验证、重绑，单owner在A/versioned新目录闭环。现有产品和设备结果不需重跑；无第二个已证独立产品实现缺口，不制造任务。
Root未更改night或恢复合同，A原报告未覆盖/重写；默认旧recovery-completion目录尤其禁止复用。两个自动化PAUSED，不Git/发布/外部消息/桌面/云/存量清理。
A报告实测空间与文件清单已过时（REPORT当前13106B而表称12441B）；B报告当前25414B，表中预计15-20KiB不是实际统计。不为这类小文本差异另起治理任务；无APK/工程/缓存复制。Root新增本报告及小型模拟fixture保留为反例，唯一构建工作区/缓存不变。
停止条件为归属/并发/输入不明、旧输出存在、必要proof缺失或恢复失败；下一版必须在任何设置写入前拒绝不适配请求。原FAIL及night严格合同保留，不能降级告警来通过。
