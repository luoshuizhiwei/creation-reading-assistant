# 任务145-r3：接真实parent API与原始归档，禁止玩具stub冒充运行闭包

2026-10-26香港15:32；同owner/Claude global Flash，原r2_DEVICE_BLOCKED保留。不设备。

````text
Root已实际读r2host180-350行/validator关键后段/12原log，并对照父host136 collect/recover/run_attempt与base.Dev.sh。真实模板这次已正确，可复用，不再重审旧格式；但运行/恢复仍确定性错误。原145v1/r1/r2源码raw/SYNTHETIC全冻结，新recheck-r3/，25分钟/差量512KiB raw512KiB。

以下用实际代码证据修，不猜新API：
1. 实际base.Dev.sh(self,*args,timeout=90,stdin=None)返回base.run文本dict，不接受capture_stdout。r2 _run_su传capture_stdout=True/False必TypeError，stub自创此参数掩盖真实问题。直接复用真实Dev普通文本命令；二进制归档另用现有subprocess.run([b.ADB,'-s',dev.serial,'exec-out'或'shell','-T',...], capture_output=True, text=False, timeout=...)原stdout bytes保存，核真实返回returncode/stdout/stderr；不改base，不把二进制经text=True decode。离线adapter使用与真Dev.sh签名一致的autospec或直接stub真实b.run出口，不能自造dev.sh API。
2. 真实仪器命令父run_attempt已正确：shell am instrument -w -r -e class TEST_CLASS -e craRunId runId TEST_PKG+'/'+b.RUNNER。r2 build_instrument_command末尾TEST_PKG/TEST_CLASS错把test类当runner且没有-e class。沿父真实命令组件CraIsolatedTestRunner，离线精确比完整argv，不只包含类名字符串。
3. r2保护outroot=R2_ROOT/evidence/protection，实际strictadapter OUT_BASE不允许，旧133阻断必重演。固定actual OUT_BASE E:/creation-reading-assistant/task83-selfuse-delivery-v1/B/v2/protection-root-r1/task145-scroll-host-binding-v1；其下未来runId/initial/final唯一不存在。父b.DEV也未接新device目录（r2只有V2/LOGS等），故b.write仍可能task136；ROOT/DEV/LOGS/RUN_ID_FILE/预算attemptsummary/PROT必须全部真实新scope路径闭合。路径纯核，不创真实目录。
4. parent.collect实际调用evaluate_f1_evidence(local,business_ok,b.RUN_ID)，期望tuple(ok,reason,problems)。r2只赋parent.evaluate_scroll_evidence返回dict，实际collect依旧F1，stub调用新函数绕过了父collect。parent.load_and_verify_inputs实际无参，r2新函数必填inputs_path，直接patch会TypeError。parent.recover_after_run真实参数(dev, installed,instrument_started,business_ok,baseline_ok,recovery,stop_ok=True,fixture_gate_ok=False,fixture_gate_reason=None)，按真实签名适配，不多传/少传。请直接复用真正父collect/run_attempt/recover函数全链适配，而非另写一个返回plan、永远不调用父函数的run_pipeline。本批可定义未来main/执行入口，默认guard拒真实执行；后续Root批准可直接传argv运行，无人手patch globals。
5. r2 recover_after_run_guarded从未接到实际parent.run_attempt finally调用，不能声称卸载前已wired。必须父真实finally调用新wrapper，wrapper保存原recover引用避免递归。实际运行前fixture/identity/gate/budget/single attempt/stop/recovery原合同保持；未install产品的早失败不得凭空要求保全不存在product；已装且writer未静止/身份未证则KEEP_PRODUCT，不乱停其他包，不盲force-stop。

三根保全必须真实字节闭包不是tar可读即绿：
- 根正确沿r2 user0 CE/DE/external整根。r2 `tar -tf - path`没有stdin归档，会读空流；改为本地tarfile.open读取刚保存的同一archive bytes，拒绝越界/链接，列所有目录/文件/DB WAL SHM并核字节。保存原设备stdoutbytes/rc/stderr。
- 设备源文件清单/sizeSHA与本地归档逐文件一致，同一可信静止窗口；tar stdout本身SHA只证明本地文件不证明源未漏/未变。复用138完整保全既有只读脚本/流程最小实现，先核writer/实际协议终止与产品prefix ps；仍活则按此前受控流程pre完整保全后才可未来一次精确产品force-stop（本批不执行），不killVM/其他进程。若超scope，准确列BLOCKED，不把ok=True当凭证。
- 未来恢复前source inventory与local tar一致/三根父正对照+原rc落盘，任何缺证/归档失败/源变化/正在writer即KEEP_PRODUCT，绝不调用父uninstall。不能collect metadata成功代产品数据保全。保全空间预算原合同可在未来设备批Root具体定，不实际创建raw大数据。不要只复用父原先collect元数据门声称数据门已存在。

最小真正离线验证：加载真实父模块（先pin），在Guard内mock其b.run/Dev.sh真实出口/本地collector pull返回，不执行任何ADB/设备。实际调用parent.collect，确认会进新t141b evaluator并产出兼容tuple；实际调parent.run_attempt的设备边界可stub全部外部端点而不生成真实attempt，只在SYNTHETIC_OFFLINE隔离fake路径（明确非运行证据），验证新class/runner、inputs无参wrapper、b.write路径与PROT允许根、finally确实先保全再原recover。保全失败parentuninstall次数0，成功流程才可调受控mock恢复。真实Dev参数签名不匹配必须失败。原r2格式正例复用，不再刷12case当新证明。

当前r2已有validator回退缺口：新host丢了10_search query/tab/count、pre tab=query=预期、itemCount有效/targetVisible检查，仅prepost同可假绿。这些旧约定保留，精确补回不降断言；真实阶段fixture/source内容/25本12业务列+派生前后精确同保持，not_covered数组非字符串。只补本批引入回归，不额外泛审。

输入新product539f44c5/newtestf2d2982b及15role固定pins复用，父13652645B9457a4c5/base16583Bc806ce36/adapter7864B16c93794只读。原恢复APK10508820B0d18d788a04e366271df3ad2ac18cc2ceeb95edc3c64c37cb995745aefbc9a9c/tar5632Bfbdbb692ee494c6d3d10312be4d7b764f08791724ff648a3e244c9c0b5afe50a固定，不任意更新pin。

交付recheck-r3/ready.json、新最小host/inputs/相对r2patch/REPORT/必要真实API mock原log；Android源码/APK/Gradle冻结无build/compile，不复制工程/缓存/历史。禁止ADB/VM探测启停/安装卸载force-stop设置/hostmain真执行/真实runid预算attemptsummary/F1/设备，禁止Git云正式库私库自动记忆/旧资产删移压迁/杀进程。Root不旁路ADB/build。真实可运行主入口+严格数据门离线闭合才候选，未闭保持BLOCKED不能再以plan/mock自造API称PASS。完成Root核后另派设备，最终总结末行：这是任务145-r3。
````