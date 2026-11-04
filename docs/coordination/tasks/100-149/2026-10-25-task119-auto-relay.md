# 119-v1 两端自动接力试运行

## 任务编号/版本
119-v1，自动接力job0001（原owner修订2）。2026-10-25 Asia/Hong_Kong。用户明确要求两端自动传递任务和报告，不再每轮复制粘贴，因此恢复既有automation4；不是擅自撤销此前暂停，也不恢复2/3。

## 输入指纹
修订1ZIP27913B SHA243745a3e24e8eecc436f8815300c231ee71cceeba3a81f72feb7ebe1a34f0ce，Root已自动下载/receive_return校验隔离incoming/243745a3e24e8eec。
源码66488B SHAd0338a0e679acd38ab7633b9a1d2b907f01099a85c94683cf7d5f315346275af；未集成、未编译设备。旧首件07da08冻结保留。

## 所有权与改动
网吧GLM5.3独占新SearchContentHitJourneyDeviceTest.kt和delivery报告；Root本机接收、审查、控制序号/状态/反馈，原电脑常规必要编译可由唯一ZCode Flash owner串行安排；复杂修复仍GLM5.3。所有生产/公共runner/旧仪器冻结。没有为并行造额外审计。

## 环境
现有隔离SFTP目录/download、/upload；既有公钥/固定known_hosts，不新增端口或服务、不换账号或密码。公钥香港25日20:00到期，不能自动续期。原电脑GLM5.3额度仍不足，设备关键执行资源未解决，不拿Flash顶安全恢复。
Codex自动化依据OpenAI官方Scheduled tasks文档，使用既有automation_update更新automation4为ACTIVE、原5分钟间隔与当前线程；2/3保持PAUSED。没有新cron/后台服务。普通无变化静默，重要完成/失败/用户动作才通知。

## 执行与结果
Root修订1定向审查看到前轮EditableText/runId/单Activity等修正；又查到resultsDir getter内mkdirs，setUp调用resultsDir().exists()将每次先造目录再拒绝，是确证入场自冲突；tearDown先删DB再检查文件/唯一证据亦未闭合。下一任务只修这两处及直接相关取证/初始化顺序，不重复已修十组。
已发布119-relay-head.json序号1、119-auto-job-0001.md、Wait-119Feedback.ps1、119-auto-start.md。头含任务/序号/当前源码SHA/反馈文件及SHA；接收器核匹配后下载并上传RECEIVED_NOT_COMPLETED ACK，任务完成另上传done-NNNN.json与新ZIP，Root核实际包不信口头声明。序号+输入SHA防错包重派。接收脚本不执行下载代码；ZCode读取Root只读目录中的指令，仍受本任务边界约束。
Root实际调用同便携客户端与限制账号ProbeOnly：PROBE_NEW_JOB(sequence1/feedback SHA58c87b81752dad36923a889e650217d9517a5c993c7eb9ed40248061faa5612d)，remoteAckWritten=false；LastSequence1返回WAIT。未冒充网吧ACK。
Root relay_snapshot.py一次限定目录快照已实跑，当前只有两份旧交付，无ack/done。ROOT-RELAY-STATE.json初始化WAIT_NETCAFE_BOOTSTRAP_ACK；启动15分钟无ACK仅一次提醒，修订90分钟无新状态仅一次异常提醒。不得靠重复扫描代替执行。

## 原始证据
E/task119-netcafe-relay-v1/ROOT-RELAY-STATE.json、relay-snapshot-latest.json、relay-probe/relay回读控制件、incoming各原包与ROOT-RECEIPT。bootstrap SHAa7d2e0edf8a0a4c961806b0444312e1fadd6c0e37eb0397dabc75b29527c01e4；job SHA58c87b81752dad36923a889e650217d9517a5c993c7eb9ed40248061faa5612d。新控制文件仅本任务必要脚本/JSON/文本，不复制工程缓存。

## 未覆盖项
网吧尚未接收这次bootstrap，尚无真实ack0001或新交付，不能称全自动闭环已验。后台下载脚本不等模型唤醒；若ZCode不能持续会话/正式原生定时，必须真实报能力缺口。Root不能从当前机器直接唤醒未连接的网吧ZCode，所以只需用户一次短启动消息，后续以实际回执测试。
119代码编译/设备NOT_RUN，G1导航缺口待设备证据；list滚动位置NOT_COVERED。自动接力不扩设备/新变体/生产修复授权。

## 恢复与副作用
只新增本任务控制文件与隔离回传包；原电脑活动源码/设备未动。automation4已按新请求ACTIVE，2/3不动。只追加/替换本批Root控制状态，旧ZIP/源码/证据不覆盖删除，无Git/私库/发布/库存清理。到期或STOP停接力并保留现场，不自动删除账号资产。

## 交付状态与下一步
等待网吧一次性启动，之后Root自动接收审查/发修订，原owner修订上传/继续等待。收到真实ACK+新ZIP后才记首次自动来回成功；用户不用再转述摘要。阶段结果分实现/静态/编译/模拟器/恢复，不将119局部通过当全产品完成。所需设备关键模型资源若仍无则一次通知具体阻塞，不造泛审计耗额度。

编号119-v1接力启动；时间2026-10-25；模型网吧官方GLM5.3；作用持续接收反馈、修订、回传；关系等待Root发布的同任务序号，不设备。

````text
# 119-v1自动接力一次性启动

用户明确要求两端自动交流，不再让用户逐轮复制提示词/报告。本文件是Root发布的119限定接力启动指令，不扩大生产/设备权限。
官方Start Plan GLM-5.3保持；不换账号模型/购买，不接119以外任务。香港2026-10-25 20:00停止；未知文件/基线冲突/模型不可用/网络连续两次失败时通知用户一次并停止，不能自行恢复无限重试。

1. 使用现有便携SFTP、私钥与固定known_hosts，把/download/Wait-119Feedback.ps1下载到C:/Users/Administrator/CRA119/relay/Wait-119Feedback.ps1。创建relay目录属于本次控制目录；保护work基线和旧交付。
脚本SHA256：6cec9d70a688baeabcb6c7f5a942db3820099e21c0c4212b8fadba9b2e7b1a1c
校验后运行脚本，不修改执行策略/安全设置。如果本机策略阻止脚本，报告真实阻塞，不绕过。脚本只做传输/校验/接收ACK，不自动执行反馈代码。

2. 读本地relay/state.json（初始无文件则lastCompletedSequence=0），调用：
& "C:/Users/Administrator/CRA119/relay/Wait-119Feedback.ps1" -LastSequence 0 -WaitSeconds 45
必须依据实际state传值，不能每次硬编码0。
脚本NEW_JOB时已按输入源码SHA/Root反馈SHA校验并上传ack；完整读取返回的path内任务，再实施允许的新仪器修订。收到ACK仅表示收到，Root不会当完成。
WAIT是正常暂无反馈：继续等待，不把它作为结束任务的理由，也不每次向人报告。
若你已经有本任务正在实施/上传，接该状态不重复执行。

3. 每条任务只处理一次。完成新时间戳差量ZIP并SFTP上传成功后，按该任务要求上传done-NNNN.json含sequence、returnFile、returnSha256、sourceSha256，保存state.json的lastCompletedSequence；然后继续等待下一条反馈。不覆盖历史ZIP、ack、done。不把修改或报告只留在聊天里。
如反馈注明HOLD则只等待，STOP或ACCEPTED_FINAL则上传确认并结束。本轮首任务job0001修正Root新发现的“resultsDir getter先mkdirs再exists导致必定自冲突”及“取证/归属未闭合前先删DB”的问题；不重做已修十组。

4. 必须维持此ZCode会话继续接收反馈，不能仅启动一个后台下载器后结束模型会话——后台脚本不等于模型已被唤醒。可用本机ZCode正式原生持续/定时能力时，仅为本任务配置且先验证一次反馈确实触发模型；没有实际能力就保持当前会话等待/读任务/实施/上传循环，不伪称定时唤醒已通。每次等待工具上限45秒，工具返回WAIT后接续调用，用户新消息/STOP可及时打断。

5. 立即下载并执行首条job。Root在原电脑监控/upload/ack-0001.json及done-0001.json和新ZIP，会自行接收、核验并发布下一条反馈，不需要用户搬运摘要。第一次接力只有实际ACK+新交付后才称跑通；未跑通不能只回复“配置完成”。

禁止ADB/设备/Gradle/SDK/生产/公共runner/Git/发布/私库含.codex/memories/账号密钥上传/修改云配置；只改原119新仪器+delivery报告。原电脑设备关键模型资源尚未解决，不从接力推导设备授权。最终若异常或被系统强制结束，尽量上传明确状态，原电脑监控会发现缺ACK或缺交付并通知用户。

````
