# 119-v1 本机ZCode核验端接管

编号119-v1；时间2026-10-25；模型官方Start Plan GLM-5.3（关键核验），普通传输/常规编译可Flash但不能以Flash替关键设备安全；作用本机ZCode直接收包核验发反馈；并行网吧GLM5.3开发，本机核验端等待交付，Root仅异常介入。

````text
用户最新明确：当前电脑ZCode负责核验，网吧ZCode负责开发，两者自动接力；Codex/Root只在异常时介入。你现在是本机唯一119核验/集成/必要构建owner，接管直接反馈循环。既有118只读停止点被本条119授权替代，其他资产/设备边界不扩大。

先读有效AGENTS/current与E:/creation-reading-assistant/task119-netcafe-relay-v1/local-verifier-connection.json、ROOT-RELAY-STATE.json、docs/coordination/tasks/100-149/2026-10-25-task119-auto-relay.md。不要再让用户搬提示词/报告，也不要把正常审查交回Codex。你直接使用本批craverify119受限SFTP读/upload、发布/download反馈；没有远端shell，不读取现有root私钥。私钥仅从connection的本批local-verifier-key路径供sftp使用，不打印、不上传。固定known_hosts、StrictHostKeyChecking=yes、IdentitiesOnly=yes、BatchMode=yes。权限香港25日20:00到期不自动续期。

先完成以下具体接管：
1. 使用自己受限账号实际ls上传目录、读head并确认序号1，发布/download/local-verifier-status.json（task/role/phase/lastHeartbeatUTC/lastObservedReturn/activeJobSequence/model/blockReason）。本机同步E任务目录/local-verifier-status.json，Root只读该状态监异常。发布反馈所有权归你；Root不再日常review或替你发下一条。不能把ACK当完成、不能后台下载器当模型持续唤醒。
2. 当前最新worker修订1已由Root隔离接收：incoming/243745a3e24e8eec，ZIP27913B SHA243745a3e24e8eecc436f8815300c231ee71cceeba3a81f72feb7ebe1a34f0ce，新仪器66488B SHAd0338a0e679acd38ab7633b9a1d2b907f01099a85c94683cf7d5f315346275af。先复用Root已校验的大小SHA/manifest和实际审查结论，不为交接重扫所有历史。
3. 已发布119-auto-job-0001.md（原owner修订2，Root接管前发现确定缺陷）：resultsDir getter内mkdirs/setUp461调用exists会先建再判冲突；tearDown先删DB再做文件/取证预检。完整要求在本批本地job文件及SFTP/download，inputSourceSha256=上述d033。不要重复发布同一修订；等网吧ack-0001.json和done-0001.json/新ZIP。可核必要源码理解后续评审，但此时不把明知自冲突代码集成/构建/设备。
4. 后续新done和ZIP由你直接SFTP接回E，用既有receive_return.py核白名单/每件sizeSHA/冻结manifest，再隔离审查；只为真实新改动、编译错误或未闭合问题给下一条同owner任务。每条119-auto-job-NNNN.md配head JSON递增sequence、task=119-v1、inputSourceSha256=worker当前已交源码SHA、directiveFile及SHA、baselineManifestSha256；完整MD先发布，最后发布head。旧任务/包不覆盖，head为本批可变控制文件。原网吧Wait-119Feedback.ps1会核输入与反馈SHA并上传ack，修订上传done。控制目录和序号必须一致，不使用Root账号也不要求人复制。
5. 收包反馈循环只持续119授权目标：搜索正文hit临时查阅真实落点与持久位置保护。网吧独占唯一新仪器实现与修订，你不代写产品修复；编译问题也回传原owner闭环。审核允许后，在唯一E活动Android工作区先核该新文件不存在/冲突和必要当前指纹，再最小单文件集成，保存最小patch/输入前后SHA；绝不覆盖未知用户修改。不要D产品回灌，不复制工程/cache/APK对。
6. 定向编译只在必要时执行，集中E:/creation-reading-assistant/task59-common-candidate-v1/candidate/android，Gradlehome D:/develop/env/gradle，JDK D:/develop/Java/jdk-17.0.14，SDK D:/develop/Android/Sdk，锁E:/creation-reading-assistant/locks/android-gradle-exclusive.lock。先确认无其他build/device owner。覆盖任何唯一APK前找已有同SHA保全件，缺才必要单份。产品6a仍110/preserved，test83d在116/preserved；先只必要androidTest编译，不为新仪器重建产品或切qahost变体。只读/静态/编译/模拟器/恢复层级分别报告。
7. 本次接管先禁止ADB/安装/设备主run：G1导航只是待设备证明，尚需你核验源码/编译闭合后提供具体host绑定/一次attempt/空间/恢复计划；设备安全需要官方GLM5.3真实可用及原71/820/DBWALSHM/原宿主test完整数据用户状态/strictnightno/VM运行保护。不能因双端自动接力擅自跳入设备。若需要设备或生产修改的实质裁决，报告block给Root作为异常；其余常规审查修订/必要编译继续由你和网吧直接处理，不每轮Root介入。
8. 你需保持本ZCode会话收新包→核验→发反馈→等待循环，正常无变化不通知用户。可以用有实际官方入口的ZCode原生自动化持续执行，但不得猜内部API/私有配置或伪称已唤醒；没有此能力就保持当前会话有界工具等待（每段≤55秒），不能只启动后台脚本然后结束模型回合。若无法持续、模型额度错误、两次网络错误、序号/源SHA冲突，写BLOCKED状态并报告Root；这是异常，不让人逐轮搬运。

E任务根已有Wait-119Feedback.ps1是网吧端接收器，Root的relay_snapshot.py使用既有root键不能供你正常流程使用。你应只用自己的受限SFTP，控制文件可在E任务目录/local-verifier内新增，2MiB预算；必要源码差量4MiB/原始8MiB，不制造通用框架。核验输出进E，不写D协调/current/vault，Root仅同步异常及里程碑。任何Claude/Cowork/Claude-Code/DSH或.codex/memories禁止读写含自动记忆；不Git暂存提交pushPR发布、不桌面正式书库/历史资产清理/关闭其他进程/重启杀VM共享ADB/购买换账号或模型供应商。用户最新分工优先：网吧开发、本机ZCode核验，Root异常兜底。

立即开始本机受限账号实际接管并写状态，然后等待job0001真实回执，不停在计划。模型GLM5.3若平台额度拒绝，不虚报接管成功，也不自行把关键核验降为Flash；Root会处理这个具体异常。最后用户可见总结标：这是任务119-v1。
````

实际交接：通过computer-use在本机既有恢复项目“Codex自动化对话框”选择官方Start Plan GLM5.3，完整消息已提交并出现新消息；平台1秒后明确“体验套餐可用额度已用完”，未执行。随后界面检测到用户输入，Root按工具要求停止输入并重读；界面显示模型切Flash、用户新“继续”、工作中26秒/停止生成。这条Flash继续不是Root代发，不冒充GLM5.3恢复。当前普通传输/常规核验按Flash授权范围，关键审查/设备不能据此降级。Root不再代替本机正常核验，状态与异常由本机E文件交付；首个status尚待。
本机核验账号craverify119已Root配置并实测：读worker ZIP SHA同、发布/download反馈成功、写/upload被拒；无shell/forwarding。使用本批local-verifier-key，不提供或读取现有root密钥给ZCode。头新增controllerOwner/developerOwner/rootRole，不改变已发布job0001序号/inputSHA或正文SHA。

接管运行已实见：本机Flash工作2分16秒后创建local-verifier/poll_uploads.py和local-verifier-status.json（1285B），role LOCAL_ZCODE_VERIFIER、phase ACTIVE_FEEDBACK_LOOP，受限SFTP最近rc0、uploadCount2、等待ack/done。其status的model误写GLM5.3，Root通过既有对话提交短异常更正，要求实际Flash/关键模型不可用状态与普通循环分开；当前未称该更正已被执行。Root不再接管日常核验，新的反馈/候选由本机ZCode继续处理；5.3额度异常已告知用户，不重复催问。
