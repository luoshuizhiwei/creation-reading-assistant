# 任务146-r2：Antigravity接手恢复门核实、最小修复与复查（仅离线）

2026-10-26香港20:12；执行者Antigravity，推荐当前可用Gemini 3.8 Flash High。用户本轮明确要求优先用完Antigravity周额度再用Claude，质量不足可由Antigravity复查。UI刚核Gemini周剩余99%/5小时99%，不以估计代实测；Claude146-r1已交，无并行写owner。20分钟，delta256KiB/raw512KiB，无工程/APK/cache复制。

````text
你是本批唯一实施+必要定向验证+自复查owner。恢复工程D:/develop/Code/Codex/creation-reading-assistant-recovery-20260919，先读该目录AGENTS.md及docs/handoff/current.md；本任务是新的Antigravity路由覆盖历史“只Claude”的路由要求，其他授权边界不变。当前不允许任何真实设备/ADB/VM/Gradle，先离线把恢复接缝闭合；不要自行扩大到设备。Claude已交146-r1，不再给它派任务或由它修改你的范围。

固定基线E:/creation-reading-assistant/task146-quiesce-recovery-offline-v1/recheck-r1/host/host146_r1.py13123B SHA aefaf3e908fda584fef900e989a3ec7a353753644112eba99f39da038b80c009；offline_validate_146_r1.py22893B SHA588c9fe61f79bf43ba8b1bf8ae62a48189c85f8d64dee2e9ef036d83320b06c0；v1-to-r1.patch27879B SHA6e9081ad70df9e458d823d0d4a9700584fb25975b4f895a0e1fc4fd496618978。Root实际读完整host/REPORT/7项原log并核三件SHA。身份错误不被批准覆盖/父stop_ok前置门/静止无stop/新identity重核/七件回执有实现事实；7mock绿不等设备或完整闭合，旧原件不改。

只核实并修以下具体剩余缺口，不泛审、不刷历史suite：
1. r1 _persist_or_note失败只记persistNotes，调用后仍可继续pre/stop。hook把notes放recovery但result.ok为真时仍交orig完整installed，父可能卸载产品。证据写入失败必须在对应危险动作之前阻断：pre原件/清单/receipt/identity-before-stop未完整落盘不得stop；post稳定proof/最终phaseproof未落盘不得uninstall。保产品且依父安全门恢复独立test/settings/protection，整体summary假/exit非0。原cause和落盘次错分列，不宽catchpass吞错，不只把summary标红而仍删产品。
2. archive_roots=V1.archive_roots，沿旧v1仅rc0+非空bytes就pre成功，没验证tar可读/成员都在本根/根目录存在、原stderr仍截400B；r1要求的精确输出卷空间检查未实现。核这条真实调用边界；只在新host做最小补齐，借用已有安全tar解析逻辑拒越界/链接/坏tar/缺根，pre-live标签准确PRE_LIVE_UNSTABLE不冒充一致快照、不强求writer存活时文件SHA稳定。空间估算以本次源文件size总和+必要tar余量及输出卷free为准，不全盘扫；不足不stop/不删资产。完整二进制归档/原rc/fullstderr/清单与proof引用落盘，不用metadata/pullrc0替原字节。
3. 复查实际新入口是否真的调用你的新函数，避免import别名仍用旧模块globals。已有146-r1 identities/stop硬门保持，不回退成approved覆盖错误；保持fresh serial/boot/user/本次installed归属/instrumentstop。stop精确一次 `am force-stop --user 0 com.creationreadingassistant`，不碰test/其他进程。post通过后才父正常精确uninstall，失败保产品且独立恢复/错误可观察。只新host接缝，不改旧父136/base/adapter/旧146和145文件。

实施顺序：先以实际代码/最小外部fake故障证明根因（必要性质红保留），最小修复，再必要红绿。测试必须调用真实新main/生产安装hook/原父run_attempt+collect+recover/cmd_*体，只mock外部b.run/Dev.sh实际签名、独立subprocess/身份/空间free边界；不测试local重写包装体、不lambda0跳过cmd内部函数。重点断言：pre落盘失败→stop0/uninstall0；post/最终proof落盘失败→uninstall0/产品保留；坏pre tar或低空间→stop0/uninstall0；合法live成功先三根pre原字节及stop前identity落盘、唯一精确stop、完整post稳定proof晚于stop且先于uninstall1。原test/settings/protection恢复真实回执、失败summary假/exit非0。未匹配外部调用必须抛，所有fake输出隔离新SYNTHETIC_FAKE；不得执行真实ADB/VM/main端点。

完成实施后做一次具体自复查：按实际main→绑定→hook→pre→stop→post→orig recover追踪globals，核上面危险动作是否真被落盘/空间/身份门约束；检查测试expected不依赖被测函数自证，原log与summary匹配。发现具体缺陷同owner小修必要重跑，不为耗额度新增泛审/重复充分项。若无法闭合如实BLOCKED并保全部红，不宣称设备可执行。Root收到真实交付后裁下一阶段。

唯一可写E:/creation-reading-assistant/task146-quiesce-recovery-offline-v1/recheck-r2-antigravity/：最小相对r1patch、修改/新增host和必要测试、REPORT、必要原红绿log/独立summary及小fake原件proof、ready.json。delta256KiB/raw512KiB；写前检查精确输出卷free，旧任何原件存在拒覆盖，不删移压缩存量、不rmtree旧fake。引用pin的旧host/inputs不复制整链；独立新fake场景已存在则换明确新子目录而非擦除。报告列实现/离线验证/未设备分开、基线/前后sizeSHA、具体自复查及实际新增分类体积，ready只信原日志。

新144产品53896765B SHA539f44c583874e43de866744939f18a28b2e98494cc343fed41fc435de0d149b/test10677879B SHAf2d2982bba57cb53cc72e96258fede386da9574fbe92cc269ac8d67d02d72bbe仍原buildoutput，15rolepin与readerfreeze/r8host14162Bb70406c0/inputs5325B1bb6a233/原父1369457a4c5/basec806ce36/strictadapter16c93794冻结。唯一Android候选E:/creation-reading-assistant/task59-common-candidate-v1/candidate/android，本批不写不Gradle。142八目标/143compile/144APK充分证据复用不重跑。139当前恢复/138三根保全接受，但旧卸载前若干rc未持久化限制永留；137仅MuMu非实体旧collectfalse/okfalse红不改。全产品分母未知。

禁止真实ADB/VM探测启停/安装卸载force-stop设置/真实hostmain/runid预算attemptsummary/F1/实体/buildcompile/Android源码APK配置变更。参数approve只是离线fake开关，绝不授权真实操作。禁Git暂存commitpushPR发布/云SSH SFTP/D产品回灌/正式库HOLD/购买换号切供应商/旧资产删移压迁/kill共享进程/ClaudeCoworkDSH私库/.codex/memories/自动记忆。仅Codex Root维护current/自动化，本批不要改它们。用户停止优先。最终总结末行：这是任务146-r2。
````
