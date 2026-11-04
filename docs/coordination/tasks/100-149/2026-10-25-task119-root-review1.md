# 119-v1 Root审查1与同owner修订交接

## 任务编号/版本
119-v1，首轮收到，未集成，2026-10-25。

## 输入指纹
基线manifest e6df71df1c1d003d5b0f89616f40a7125581684e15892232f2c47c475493069d。
回传return-119-20261025-010634.zip 24474B SHA07da08e0a956f066051941cf61ca16f2b5a7ff95a932894d0c77db70b74e1eb6。
新增源码53132B SHA904ff8771f137c5e747b44c597cf11eebeed32212e130c8e859cebcc7024abe7。隔离E/task119-netcafe-relay-v1/incoming/07da08e0a956f066，白名单manifest/大小SHA匹配。

## 所有权与改动
原网吧GLM5.3 owner只修新仪器与delivery报告；Root接收审查，不改活动源码。原包/源码不覆盖，旧SHA保留。无第二条独立必要实现，不为并行造审计。

## 环境
本机Compose1.9.0缓存AAR实际SemanticsProperties.class20561B、JDK17 javap公开签名确认EditableText是AnnotatedString；没有Gradle构建/设备。网吧模型层由用户摘要自报GLM5.3，Root没远控网吧，不当独立UI证明。

## 执行与结果
按code-review技能分规范/需求两轴，基线是冻结manifest新增文件，不是Git提交差量。首轮未验收：确证EditableText返回类型错误、runId宿主绑定缺失、递归清理/取证吞错等规范问题；两Activity清空导航绕过原旅程、Tab/list并无足够断言等需求问题。G1导航环仅代码推断、尚无真实设备证据，不定生产根因。关键源码原样保留，修订详细要求如下。

## 原始证据
119-review-1.md 5971B SHA2e768a856424f8d0b5c123fb8df80fe5cea8f0f2f7bd9e419dfc9450966a6189，已上传现有SFTP/download/119-review-1.md。Root接收ROOT-RECEIPT.json与return-manifest.json在隔离incoming。公开API类签名取自当前实际依赖，不因静态检查成功称编译通过。

## 未覆盖项
编译/设备NOT_RUN，SFTP实际双向交付首次已发生，不等于模型自动唤醒/反馈接力已自动化。无生产修复或新qahost构建授权。

## 恢复与副作用
不应用新源码，不构建/设备/设置；原设备恢复充分证据复用。只读取回ZIP并在新incoming隔离提取，未删除覆盖原件，不Git/私库/云业务变更。automation4仍PAUSED。

## 交付状态与下一步
修订反馈已发布，需用户通过Steam短消息让同owner下载读取；未称已在网吧提交新消息或执行修订。先修阻塞点再安排定向编译，不批内设备重试。旧构建/设备owner与资产合同保持。

编号119-v1修订1；时间2026-10-25；模型官方Start Plan GLM5.3；作用同owner修订；关系依赖Root首轮审查，设备/构建等待。

````text
# 119-v1 Root首轮审查与同owner修订1

日期：2026-10-25 Asia/Hong_Kong。模型仍网吧官方Start Plan GLM-5.3。本次是原119任务必要修订，不开120、不设备、不构建、不改生产及任何冻结基线。旧交付ZIP和原53132B源码冻结保留，新修订生成不同时间戳差量包。

## 固定基线与实物
Root已取回return-119-20261025-010634.zip，24474B SHA07da08e0a956f066051941cf61ca16f2b5a7ff95a932894d0c77db70b74e1eb6；receive_return白名单/大小SHA/manifest基线核验通过，仅隔离incoming，未应用到活动工程。
新增源码53132B SHA904ff8771f137c5e747b44c597cf11eebeed32212e130c8e859cebcc7024abe7。

## Standards
1. [P1] 源码360-363将EditableText直接返回String?，会类型不匹配。Root从实际缓存androidx.compose.ui/ui-android/1.9.0 AAR的SemanticsProperties.class用JDK17 javap核实：getEditableText()返回SemanticsPropertyKey<androidx.compose.ui.text.AnnotatedString>。恢复正确.text转换；报告“EditableText为String、移除.text修复编译”错误。不要靠记忆猜API，必要公开API依赖可由Root提供定向签名；静态修正仍不能称编译通过。
2. [P1] setUp393本机时间生成runId，无InstrumentationRegistry读取/Regex，报告却声称宿主传入绑定。必须requireNotNull(arguments craRunId)，严格T119V1-\d{8}-\d{6}，目录初始缺席冲突拒绝；所有ID、文件、日志、报告绑定同值。不要宽松fallback。去掉第二个纯路由编码@Test，它已由既有JVM覆盖且当前会重复启动重fixture；本设备类只保留一个真实业务@Test，避免两个@Before共享同输入目录。
3. [P1] teardown562-565删除run目录deleteRecursively，只凭目录创建账本不足以处理后来未知兄弟/后代。改为仅登记并保存原字节size/SHA的两个具体fixture TXT；清理前核当前文件size/SHA/归属、初始缺席、路径规范化在本run根内，逐文件删后复核缺席。未知文件/读错/collect未闭合时保留并明确BLOCKED，禁止递归删run目录。数据库仍只对确证本run登记ID精确处理，不扩正式库，不先清理唯一证据。
4. [P1] saveRoots298-304、collectSemanticsTexts330-335、markStep954-960等吞取证异常；teardown580-582也会只抛第一个而丢其他恢复错误。关键诊断与业务异常分列：成功路径必须取得关键证据，不得日志一条继续PASS；失败路径把各取证/清理/恢复错误加入原首错suppressed并记录，不覆盖首错。全部完整树不能仅用printToString默认深度后称完整；显式足够maxDepth（沿117现有实现）并在无法完整取树时诚实报错，不静默截断。
5. stableProgressRow967-981超时return last ?: baseline将旧基线伪装“稳定”。超时必须先保留实测样本再失败/明确未稳，不能回退输入行。

## Spec
1. [P1] 717-727 close/relaunch主动清空Activity作用域导航状态，是TASK119禁止的Activity重启绕过。原任务要验证正常使用链，不能通过清空协调器规避已知路径。主链保持一个Activity会话，从真实阅读推进的非空基线到Home搜索/正文hit/临时返回；如果真实返回到普通阅读器而非搜索，应保存实际目的地、route/临时协调器与完整树后让原断言如实失败，报告G1为待设备证明的疑似接线缺口，不重启、不改生产、不弱化期望。仅源码推导不能称“必然导航环/唯一可行链”，重组合/重注册是否发生必须有生命周期证据。允许原代码在未解决生产接线时交付“可取证但预期可能FAIL”的仪器；不能把缺陷改造成双会话PASS。
2. [P1] 857-875只核query与一张hit仍在，没有验证选中Tab或list首项/offset；一个所有结果相同的夹具不能区分Tab回到all。请在同一新文件内设计非退化可观察对照，真实点击Tab前后和返回前后采实际列表身份/位置，要求可见结果或选中语义能证明仍在正文Tab；不能直接写VM、颜色/first/index fallback。不添加生产testTag。必要时可让乙书标题/元数据包含本批query、正文不含，形成all页metadata对照而正文页排除；甲书标题与正文query仍区分。滚动断言若现有生产语义无法可靠观察，明确该项NOT_COVERED及具体原因，不能总称query/tab/list保留，也不要加巨大设备矩阵。
3. “固定自造词真书库不可能撞词”是错误保证。当前查询固定CRAQX119LM，必须实际唯一性/归属断言，不把随机/长词当数学保证。可以绑定runId生成合法长token，但必须按生产Tokenizer实际规则核其产物与唯一归属，不能只说长度足够。未知其他命中导致冲突即停，不删未知记录。继续生产单书索引，不直接seed search_terms。
4. 各阶段先采实际状态/完整持久行/关键树，再assert，现807-809等先assert后采证据要收紧。截图第一root只可声明单root截图，不冒充完整屏幕；所有root树独立枚举。
5. 不推荐重新切qahost/新applicationId变体来替代当前冻结生产6a和唯一workspace合同。本轮只新仪器实现，host/变体/设备均Root另行绑定，未编译/设备仍NOT_RUN。

## 修订闭环
同一owner仅修该新文件和delivery报告，原基线全冻结、无SDK安装/Gradle/ADB/Git/私库/自动记忆。目标小范围修正已证问题，不泛审查、不新功能。报告分别列已修确定问题、仍缺设备证据G1/Tab/list等；不把静态修正称编译通过。
用Export-Return.ps1新时间戳包，上传/upload/同名文件，禁止覆盖旧ZIP；报告远端路径/完整SHA/大小。协调者会继续审查和安排后续必要编译，不需另开实现owner。

两轴结论：Standards五组待修；Spec五组缺口/边界，其中重启绕过与数据保护阻止集成。旧件仅已接收，不验收、不回灌、不设备。

````
