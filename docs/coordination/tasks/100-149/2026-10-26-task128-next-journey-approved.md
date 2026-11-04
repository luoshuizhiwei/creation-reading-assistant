# 128-v1 已放行：单次真实旅程与恢复

````text
任务128-v1，2026-10-26香港，ZCode桌面GLM-5.3-Flash。作用：唯一设备owner单lane执行一次真实"搜索命中→临时阅读→Back"单旅程验收（T119仪器124坐标/清理修复版，运行时未证→本次运行时证明）。与127（离线准备）、126-v3（只读fresh核）不并发设备操作；Root集成与最终裁决。先核本文SHA。不改断言/通过标准、不放宽失败、不批内修码重跑、不新attempt。你不改公共源码或Root文档。

Root本批明确放行（本段优先于后文草案旧措辞）：用户已直接回复“可以，MAA/游戏已让位，接受关闭深色模式”，现允许单次业务占用实例1前台以及合同night=no并保持no。126-v3/126-r1已Root核原件：保护71/204/616全字段同、原test APK/data/userstate同、包与三根缺席，采集boot e8e8f221-691e-4945-ac26-8c7a0c770f1d。Root将这些只读快照事实作为新批参考，不追认126-v3换boot未停的程序偏离为PASS；该偏离原报告和订正永久保留。此次fresh必须仍同instance1/磁盘/serial与boot e8e8f221-691e-4945-ac26-8c7a0c770f1d，任何boot变化或映射异常立即停，不能再次自行覆盖；若offline不启动VM。night若yes，用户已允许本次按合同改no；其余设置不符只读预检即停，不消耗attempt、不改设置补门禁。

执行owner=原126-v3同ZCode桌面GLM-5.3-Flash会话，公共设备唯一你；127已结束离线，不再创建重复审查。直接完成最小预检→绑定→一次运行→必要停止/恢复→取证报告，同owner闭环。Root不旁路设备。只逐字节复制host与inputs两小件，共43614B，其他APK/基线全引用；允许写新的task128 lane专属run-id/budget/attempt/evidence/logs/REPORT/ready。现有task128输出若已占用或attempt存在，不覆盖/不删，不重跑。

香港9点是用户所述模型额度到期，不是强制中断本地恢复的时间。不得缩减1800业务+1200恢复预算或中途停本地host来赶9点；按原deadline启动epoch+5400确保恢复完整。开始时确认这一独立本地host会自行完成超时/恢复，模型额度过期不应中断已启动进程。9点之后不擅换模型/账号/购买；若模型失效但host在途，不另发设备命令，不杀host，保留自动恢复，Root读取本地结果。无法保证既定本地恢复路径就不开批。此授权不含第二attempt，失败不批内改码补跑。报告最后写“这是任务128-v1。”

原task126/127文档中的不新业务、等待用户回复条款仅被本任务的这一次具体授权覆盖；其它资产、隐私和停止边界不变。禁止Claude/Cowork/DSH/.codex/memories与自动记忆，ZCode不自动记忆收尾；禁CLI代发登录/内部API私有DB/购买换号/Git暂存提交pushPR发布/云SSH/SFTP/正式库HOLD/实体手机/杀共享ADB或其它应用进程。测试安装/卸载及原test恢复仅本host合同精确对象允许；不rm未知残余，不处理游戏MAA进程。

事实基线（以126-v3已核快照为参考，本批必须重新fresh身份预检，本草案不断言设备当前运行状态）：126-v2已记录新boot 763ee045-fff4-455c-9b31-a1988f91fb73、产品包缺席，用户确认本人卸载并自行变更night；当前实例运行与否、serial/boot只由126-v3 fresh核结论决定。本草案不要求无端新启动VM、不启动VM、不重启重置；所有设备命令依赖host首步fresh身份门禁（instance()核对serial attached+boot_id+instance1+user0，漂移即拒绝）。

冻结输入（仅host/inputs逐字节复制派生，其余引用不copy）：
- host124 E:/creation-reading-assistant/task124-instrument-coordinate-cleanup-v1/lane-b/scripts/host124.py 40753B SHAbce4a930c516b21193f5d80163dc296fe4354354ac127d59c961f2592317d519（ROOT为文件相对派生，非硬编码路径；逐字节复制到新lane后ROOT自动切换，SHA不变、pin持续有效）
- inputs E:/creation-reading-assistant/task124-instrument-coordinate-cleanup-v1/lane-b/inputs.json 2861B SHAffc778e0198b3c5121fb1de746e76f6e2c3faaa4e5b76ded5d3f90a5128c76a8（9角色全绝对路径指向原冻结原件，复制不改一字节）
- productApk E:/creation-reading-assistant/task59-common-candidate-v1/candidate/android/app/build/outputs/apk/debug/app-debug.apk 52763832B SHA74d106b6b1349cfea1f4b4bb5f49e41c27691d6621ca1424e4c55effa0de84e4
- testApk E:/creation-reading-assistant/task59-common-candidate-v1/candidate/android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk 10643866B SHAa646c8de46116d75bb4c17dbcc1bd97b9d95de0e30056b3834e53c106a4ba392
- instrumentSource E:/creation-reading-assistant/task59-common-candidate-v1/candidate/android/app/src/androidTest/java/com/creationreadingassistant/acceptance/SearchContentHitJourneyDeviceTest.kt 75994B SHA0557506f1730a669257d4982b806dabc366a3bf0ab1210a4028a4e5758b1d05b
- protectionAdapter E:/creation-reading-assistant/task83-selfuse-delivery-v1/B/v2/protection-root-r1/candidate/protection_check.py 7864B SHA16c93794844650f2116fff7c97c5adb29acb15bfbc623a000643fa2fa5f722bd
- originalTestApk E:/creation-reading-assistant/task94-organizing-export-integration-v1/v5/preserved/original-test-v3.apk 10508820B SHA0d18d788a04e366271df3ad2ac18cc2ceeb95edc3c64c37cb995745aefbc9a9c
- originalTestArchive E:/creation-reading-assistant/task94-organizing-export-integration-v1/v5/preserved/original-test-data.tar 5632B SHAfbdbb692ee494c6d3d10312be4d7b764f08791724ff648a3e244c9c0b5afe50a
- productionSourceNavigationContract E:/creation-reading-assistant/task59-common-candidate-v1/candidate/android/app/src/main/java/com/creationreadingassistant/feature/reader/navigation/SourceNavigationContract.kt 10277B SHAc6c5aebc9eb6db05ac11a65685de089f82a5d0fa86b3d1ffed96eb72227401ee
- productionTemporaryReadingNavigationViewModel E:/creation-reading-assistant/task59-common-candidate-v1/candidate/android/app/src/main/java/com/creationreadingassistant/ui/navigation/TemporaryReadingNavigationViewModel.kt 10542B SHAe4af886a393f4596bceadcaca80302faab4d09c92f6905fefedaa7ab0e4949b5
- productionAppNavigation E:/creation-reading-assistant/task59-common-candidate-v1/candidate/android/app/src/main/java/com/creationreadingassistant/ui/navigation/AppNavigation.kt 34985B SHA7a6046a50550dad54a1a85f2cbf097b009fafb79d8169b889de1d27fb46f9fd9
- 恢复合同 E:/creation-reading-assistant/task94-organizing-export-integration-v1/v13/restoration-contract.json（原test 0d18d788/10508820B、data inventory SHAd6ac3c5ff7845b4afc9b384fb11b7a2bf921b9f722d4706bb6fe10801d003e29、archive fbdbb692、userState行），引用不copy；protected baseline 9f60e52c、settings e8fa75bf合同引用原件不copy。

前置依赖与放行条件（全部满足才开批，缺一即停并如实报告）：
1. Root已核126-v3只读fresh核实际回执：实例身份（serial/boot/instance1/user0）、产品包与数据三根缺席状态、保护核验结论明确，无未知漂移。
2. 用户/Root明确三项决定，用户已明确同意前台与night条件，见本批Root放行段，不重复询问：
   (a) 前台/独占：业务窗口内用户不使用该实例（游戏/MAA让位）。旅程会真实拉起产品Activity到前台，属业务固有，非owner额外操作。
   (b) night基线：冻结合同强制night=no且入场≠no即主动改no、出场恢复到合同no而非入场值（t94v3_device.py:111-115、:255-261）。三选一：①用户开批前自行设night=no（零新增绑定）；②用户明示接受开批被改no、批后自行改回；③Root另行授权新host派生参数化NIGHT_EXPECTED+恢复入场值（新输入绑定+新审，本批不做不默认）。
   (c) 实例可达：若126-v3 fresh核结论为实例未运行，是否/何时启动由用户明确决定，owner不自启。
3. 设置预检（owner只读自查，在写run-id/复制host之前完成；baseline门禁8项硬比对、除night外不自动修复，失败即耗唯一attempt）：wm size=1080x1920、wm density=280、accelerometer_rotation=1、user_rotation=0、stay_on_while_plugged_in=0、screen_off_timeout=60000、volume_music_speaker=100、cmd uimode night读取：若yes按用户本批许可交host改no，其他值停；产品包pm path缺席；原test包com.creationreadingassistant.test present且pm path唯一。任一不符→停，报告Root/用户，不耗attempt。

绑定方案（最小隔离，零代码改动；具体文件落点）：
1. 新lane根 E:/creation-reading-assistant/task128-next-journey-device-v1/lane-b/；owner先建目录并逐字节复制两件：scripts/host124.py（SHA仍bce4a930）、inputs.json（SHA仍ffc778e0）；复制后实算SHA必须与pin一致，不一致即停。ROOT=host124.py所在lane-b（文件相对），全部运行时证据落新lane，与task124冻结交付（REPORT/ready/inputs/preserved/子lane/logs旧4件）零接触、不覆盖任何既有文件。
2. runId：按启动时香港时间生成T119V1-YYYYMMDD-HHMMSS，写入 E:/creation-reading-assistant/task128-next-journey-device-v1/lane-b/evidence/run-id.txt（host导入时读取，须在调用前创建；evidence/目录新建）。
3. E:/creation-reading-assistant/task128-next-journey-device-v1/lane-b/budget.json：{"businessCapSeconds":1800,"recoveryReserveSeconds":1200,"deadlineEpoch":<启动epoch+5400>}；businessCapSeconds恰为1800，恢复预留>=1200，deadline容纳业务+恢复全额，不足不开批不续期。
4. attempt上限=1（结构性）：E:/creation-reading-assistant/task128-next-journey-device-v1/lane-b/attempt.json须缺席，host独占创建(open 'x')；输入/预算错误不耗attempt（发生在创建前），身份/baseline/包存在性/O1门禁失败均消耗唯一attempt；不批内修码重跑、不静默retry、不删attempt重开。
5. 精确一次host调用（无CLI参数）：python E:/creation-reading-assistant/task128-next-journey-device-v1/lane-b/scripts/host124.py。内部顺序：输入闭包9角色核SHA+路径=执行路径→budget核验→attempt独占创建→身份/owner-user门禁→baseline门禁→包门禁（产品缺席否则拒、原test present并核三合同）→Download快照→initial保护→安装产品+test→instrumentation组件门禁→O1 fixture缺席门禁→am instrument -w -r -e class com.creationreadingassistant.acceptance.SearchContentHitJourneyDeviceTest -e craRunId <runId> com.creationreadingassistant.test/com.creationreadingassistant.testing.CraIsolatedTestRunner timeout=剩余业务秒。
6. 运行时证据实际落点（全部新lane内）：evidence/device/约15个状态JSON（01-baseline、01b-owner-user、00-download-initial、00-original-test-verified、03-install-两包、03c-fixture-paths、04-run、04c-instrument-stop、05-collect、06-original-test-*、06-uninstall-product、12-recovery-decisions、primary-error、summary）、evidence/instrument/t119collect/task119-results-<runId>/（pull的仪器证据）、logs/20-instrument-stdout.log与logs/21-logcat.log（新建两件，不触碰124旧日志）。

显式存储预算（写进批合同）：
- owner预写与报告差量（run-id.txt约20B、budget.json约100B、新lane ready.json+REPORT.md、复制host/inputs共43614B）合计<=1MiB。
- 运行时JSON与文本日志（evidence/device各件+logs两件）预计数百KB，计入REPORT差量<=1MiB。
- 仪器原始证据raw（截图PNG、语义树dump、XML、manifest，来自t119collect pull）：<=8MiB；真实全旅程若可能超出（截图数量/树dump大小运行时才定），执行中如实报告实际值与估算依据，不改失败标准、不删任何原件，超出部分由Root裁决保留方式。
- APK/原件/合同/保全件/历史证据一律绝对路径引用，零copy；不copy工程/APK/cache/依赖。

超时与停止预算：业务上限1800秒（仪器timeout=剩余预算，耗尽则不启动仪器直接进入独立恢复）；G3：adb返回≠设备结束，仅rc==0且唯一INSTRUMENTATION_CODE:-1终记录才算协议结束；已启动未正常返回→包级force-stop两包+pidof/ps设备侧确认（10秒探测窗，任一畸形行即探针失败）；停止未确认→collect/卸载/原test恢复全BLOCKED、产品KEPT_BLOCKED。

成功/失败与恢复证据清单（判定以此为准，rc0仅必要非充分，不能把rc0当instrumentation成功）：
成功须全部满足：
- summary.json：ok=true、businessOk=true、primaryError=null、recoveryErrors=[]、businessCapExceeded=false、instrumentStop.ok=true。
- 05-collect.json ok=true：精确results目录pull成功、manifest齐备（journey_success+teardown_timeline）、evidenceCaptureFailed≠true、cleanup_blocked缺席。
- 仪器原始stdout（logs/20-instrument-stdout.log）：OK (1 test)、class唯一=numtests=1、终态code全0、无FAILURES!!!/INSTRUMENTATION_FAILED/Process crashed/shortMsg标记、INSTRUMENTATION_CODE:-1唯一。
- 旅程证据（evidence/instrument/t119collect/task119-results-<runId>/）：journey_success、teardown_timeline（阶段时间线）、stage4_derived_coordinate（chapterStart/chapterOffset/global换算与甲书正文真实词项回溯匹配、乙书排除）、stage1_reader_entered树/UI状态、分页前置报告、12列基线行三处冻结断言（临时查阅前/期间/结束后assertExactEquals）、空栈返回按钮缺席证据、乙书无普通进度行。
- 恢复证据：00-download-initial与终态Download无新增；06-original-test-restored ok=true（APK SHA/inventory/userState三合同全同）；06-uninstall-product ok（proof_ok路径卸载恢复原缺席）；07-settings-restored（night=no合同基线）；protection-final；keepalive；12-recovery-decisions.json（productAction=UNINSTALLED、testPackageRestored=true、downloadState.ok）。
失败分离：primary-error.json=业务主因；recoveryErrors=恢复/cleanup次因，分列不吞错、不以恢复成功冲抵业务失败；失败业务须first_failure+teardown_timeline含firstFailureClass。
产品/原test/71私有/820外部/设置恢复由本批owner按上述证据闭环；任何KEPT_BLOCKED资产保留原状待Root另批，不手动清理卸载。滚动NOT_COVERED如实声明：list滚动、F1重建场景、全产品分母未知；本批为模拟器验收，不冒充实体手机/MIUI/全产品完成。

输出E:/creation-reading-assistant/task128-next-journey-device-v1/lane-b/ready.json、REPORT.md与evidence/原始件。禁：修改断言/通过标准/放宽失败、批内改码重跑、第二attempt、源码/host/inputs/原test/设置合同改动、启动重启VM（除非用户明确(c)决定）、新业务、私库含自动记忆、Git暂存提交pushPR发布、正式书库、既有资产删移、超预算复制。充分输入复用，不为额度制造任务。
````
