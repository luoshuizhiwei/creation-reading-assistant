# 任务145-r4：最后收口真实主入口、完整字节门与恢复独立性（离线）

2026-10-26香港15:40，同Claude global Flash原owner。原r3九项离线API适配事实可复用，不再刷模板/签名旧case；设备仍禁。

````text
Root实际读r3host bind_scope/t141b_eval_tuple/preserve_three_roots/install_guarded_recover与9原log，对照父run_attempt与recover。真实Dev参数/runner/evaluator tuple/noargloader/保护DEV适配已改，有价值；仍不能作为设备可执行闭包。下面是原r3合同尚缺，不是额外泛审：

1. 完整源字节证明仍未完成。r3只find源文件size，与tar member size逐一比，既没源sha256、也没localfile SHA、没集合双向相等，源文件同size变字节/归档额外成员能假绿。必须源清单含path/type/size/SHA，所有目录/文件完整枚举，local tar相同文件逐字节SHA和集合全等，DB/WAL/SHM自然包含，源归档前后清单一致。空合法DE根允许明确父可读/目录清单成立而零文件，不把src empty直接拒真根；探测错误不是空根。现有138完整保全实现可只读按链接复用，不需新发明采集框架。新离线最少三case：真实binary正例、同size不同内容必须红、缺/多member必须红；DB-WAL-SHM原始字节别漏。

2. writer/身份证据不全。pidof只看stdout，rc错误空输出也被当静止，还漏:remote等prefix与fresh identity。需真实get-state/boot/current-user==本批fresh/devserial、有效ps rc0所有产品prefix缺席、pidof rc0/1可解释及stderr/原rc落盘；前后writer与清单一致窗口，未知KEEP_PRODUCT。正常仪器完后product进程可能仍活，不能“永远保全失败”当闭环；可返回明确NEEDS_APPROVED_QUIESCE并留product，后续Root新设备批单次授权时按138“pre完整字节保全后一次精确产品user0 force-stop再post保全”流程操作，不在本批真执行。不能无证自动kill/盲停/把读正忙DB当稳定。同scope静止门准确即可，不假设pid空证明无prefix。

3. 保存原rc/stdout/stderr与proof实际落盘。r3大部分probe/listing/pid结果没留原stderr，proof只返回内存，guarded返回成功也没把proof加入新decisions。未来应在新task145实际scope原始回执/本地tar/源清单/local校验/最终proof持久化，文件已存在拒覆盖；失败也保留部分原件。不能把hash只计算在内存当原始证据闭环。offline fake只SYNTHETIC_OFFLINE，不写真的evidence/attempt/budget。

4. 保全失败不能使其他恢复也被跳过。r3guarded直接return KEPT_BLOCKED，绕过父recover内原test code/data/userstate、settings/night/keepalive/保护恢复，这会把“保产品”扩大成“不恢复其余约定”。保护产品同时仍调用父真实恢复的安全独立部分（例如从installed筛去产品再原recover，保留真实instrument stop门），并合并productAction=KEPT_BLOCKED/三根错误/proof，不冒充原缺席或overallPASS。mock证明三根失败产品uninstall0，但原test恢复和settings/保护等按父门继续；stop未确认仍遵从父依赖阻断，不乱恢复活writer。三根成功原父路径允许正常精确uninstall，无rm/-k/强删。不要只替换parent.recover函数然后mock它自己当证明。

5. 实际主入口仍需完整代码。r3只定义bind_scope/install_guarded_recover，脚本没有从CLI解析runId/inputs/批准状态→load_parent→bind_scope→install_guarded_recover→真实parent.run_attempt的调用函数；不能下一设备批再改源才运行。新增明确可运行future_main函数/CLI（本批默认拒真实，必须显式Root批准批参数才可设备），完整输入/预算/单次attempt始终父run_attempt掌握。实际调用完整父run_attempt的离线mock要求所有文件输出仅SYNTHETIC_FAKE路径，所有b.run/Dev命令出口guard+mock，不能ADB；未给approve应在任何attempt/设备前拒绝。批准参数本身不是本批设备授权，本批绝不真实调用。报未来精确命令，Root以后只写已授权run-id/budget并执行，不再手工patch globals。
特别parent.ROOT当前TASK_ROOT而b.V2=r3root，父budget/attempt/summary与b.write不同scope，必须同一新运行scope闭合；父loadinputs要读新r4，父collect也要同root。verify_fixture_paths_absent与capture_logcat等剩余父F1常量按真实函数是否关联仅必要调整，不能新T141BV1却硬跑T136 guard。PROT_OUT_ROOT保持actual OUT_BASE/task145-scroll-host-binding-v1，不回写task目录内。

只新E:/creation-reading-assistant/task145-scroll-host-binding-v1/recheck-r4/host/inputs/最小patch/REPORT/必要原离线log/ready.json。先核r3host29105B52de269f5d593a419e24828c3cad586f48ff2ce48580755fe42f225a2d7959ef，不改原v1/r1/r2/r3任何原件。25分钟/差量512KiB raw512KiB。只新增以上源字节/恢复独立性/真实主入口调用证据，不重刷旧九项/所有旧suite；必要回归本批新hunk即可。

严格validator模板及15rolepins复用，新产品539f44c5/testf2d2982b/instrument11092359/全部Android源码冻结不build/compile。Root不旁路ADB/build。任何ADB/VM探测启停/安装卸载force-stop设置/hostmain真执行/真实runid预算attemptsummary/F1/设备皆禁；Git云正式库私库自动记忆/旧资产删移压迁/杀共享进程亦禁。当前只离线定义真实调用，不是设备批准。超scope或时间仍未闭精确BLOCKED，不能以同size hash缺失降语义或写PASS。完成Root核再派下一批，最终总结末行：这是任务145-r4。
````