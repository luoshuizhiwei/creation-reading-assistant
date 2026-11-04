# 任务145-r2：精确实际格式、真实调用接线、完整三根安全门

2026-10-26香港15:24；同原owner，Claude桌面global Flash。r1明确DEVICE_EXECUTION_BLOCKED事实保留，Root不把ready=true当可运行。

````text
Root已读r1实际host关键全部新hunks/REPORT/12项原log和synthetic构造，仍有确定性真实合同错误与未接线。原145-v1/r1所有源码/raw/SYNTHETIC冻结，新同task145/recheck-r2/独立delta，25分钟/差量512KiB/raw512KiB。不要再只给plan字典声称已绑定，先完成下面两类精确工作，不设备。

A. 真实JSON精确格式，禁止复用被测函数生成期望。
实际被冻结instrument SearchListScrollRetentionTest.kt32650B11092359：
- MARKER_PREFIX="【CRA141B"，紧接两位ordinal和"】"，第08本实际【CRA141B08】，没有横杠；r1 _marker_for却输出【CRA141B-08】必拒真数据。
- TITLE_PREFIX="测试 TXT 滚动"紧接%02d，第08本实际“测试 TXT 滚动08”，没有额外空格；r1期待“测试 TXT 滚动 08”必错。
- fixture文件实际名“测试 TXT 滚动08.txt”（r1前已改中性名），目录cra141b_isolated_fixtures/<runId>/；不是bookId.txt。bookID本身为cra141b-txt-08-<runId>。r1 assets按bookId文件名会拒真数据。
- assets形状仅runId/lastPhase/policy/assets；各file路径完整含fixture run目录；book为ID；dir精确fixture与results两目录。成功lastPhase来自t141b_50_success，业务异常recordEvidence(first_failure)后lastPhase可t141b_first_failure，setUp失败可start/最近真实阶段，按实际写入路径完整允许且不造字段。失败资产允许真实已登记部分，不可只成功PHASES白名单导致失败原件再被误拒。
- 行真实12业务列+legacyOffset，current_location_json为真实LocatorCodec版本格式，不假设顶层直接legacyOffset：helper是LocatorCodec.locatorFromProgress(it)?.legacyOffset ?: -1。保留12列+派生逐值精确对比；若做派生一致性必须实际复用/等价真实codec，不凭空顶层JSON；无需为本一次host移植codec框架，真实13值前后精确同已足够证明不变。

必须独立构造一组“来自源码逐字模板”的positive，不能调用host._marker_for或host.TITLE拼法来造期望；逐个比较ordinal01/08/25具体值。用实际bookIDs/中性25文件名/assets/locator原结构，不能拿host自己的输出喂回host自证。只为这些真实缺口增加必要负例，不重演已充分12项泛审。

B. 可未来调用的实际宿主接线（只定义与stub验证，不真实执行）。
r1 apply_runtime_binding目前只TEST_CLASS/RESULTS_PREFIX/RESULTS_NAME/FIXTURE_PARENT/RUN_ID_REGEX/COLLECT_SUBDIR/ROOT及b.RUN_ID。并未实际接b.V2/DEV/LOGS/RUN_ID_FILE/PROT_OUT_ROOT、collector/evaluator/inputload、newinputs/新pin。build_runtime_binding返回字符串不等于绑定函数。offline_validate只断言plan字典，没有调用apply、更未走future_main实际入口链。不能声称runtime-binding OK。

请最小实现真实未来入口调用链：父host136只读导入先核52645B/9457a4c5，base/adapter不改，所有新host继承函数实际globals都指向新输出/保护OUT_BASE/task145-scroll-host-binding-v1/新T141BV1 class/arg/newinputs/newcollector/evaluator。未来入口本批默认拒设备，但guard外的可注入执行函数需存在，Root批准下一批可传明确命令/参数直接运行，不再由人手改十个全局变量。定义入口不等于本批可执行。用不触设备的stub parent/b或命令adapter，实际调用apply与入口的设备边界前步骤，证明出站instrument命令目标类+craRunId正确、所有输出145新根、纯本地collector实际调用新t141b evaluator、载入新inputs而非旧136路径，错误路径/旧class/旧evaluator必须负例失败。stub不能只检查计划字符串。

固定pins仍有两个None（originalTestApk/Archive）；把完整原SHA固定写入：APK10508820B0d18d788a04e366271df3ad2ac18cc2ceeb95edc3c64c37cb995745aefbc9a9c，tar5632Bfbdbb692ee494c6d3d10312be4d7b764f08791724ff648a3e244c9c0b5afe50a；不要“首次实测回填”允许篡改输入。新product539f44c5/testf2d2982b/instrument11092359、观察seam80181fa4固定；readerfreeze依据task144/freeze.json固定SHA引用或加入必要实际roles，不能只十角色宣称全部reader闭包。

C. 三根保全+卸载门，只新host离线实现。
r1 require_three_root_preservation错误CE/DE路径/data/data/pkg/ce与/de不存在；external只files也不完整；dev.sh('shell','test'...)接口也未与父实际设备API一致，不可声称有门。真实user0根为CE /data/user/0/com.creationreadingassistant、DE /data/user_de/0/com.creationreadingassistant、external /storage/emulated/0/Android/data/com.creationreadingassistant 整根。实际138完整保全/139恢复文档已有精确流程可只读引用，不探测设备、不复制旧资产。
只探测/adb pull rc0不能证明完整保全：未来经真实root可读来源、writer状态裁决、完整文件/目录/DB-WAL-SHM原始归档/清单、原字节sizeSHA与本地核同/原rc stdout stderr、父目录正对照，任何失败/未证即KEEP_PRODUCT，绝不uninstall。缺根需按真实fresh产品状态裁决，不把probe失败当缺席；若实现必须超范围精确BLOCKED并列所需最小补充。门必须接到父recover_after_run真正uninstall调用之前，不能仅独立未调用函数；stub证明保全失败绝不发uninstall，collect成功但三根失败亦保留，三根通过才容许后续恢复逻辑，无-k/rm/盲force-stop。未跑设备不能称运行门通过，只称离线调用分支成立。

原件root145/recheck-r1/host27251Bce92f959c4d0860bf83d0c5cdf2b5a8472237abd36844fcc39968dcf0904fb88只读。交付新r2host/inputs/最小相对r1patch/REPORT/必要真实调用stub及格式离线原log/ready.json，所有假设备仅SYNTHETIC_OFFLINE，不能未来evidence。旧r1的12case=13checks包装不要求重审，不耗额度重扫历史；只关闭以上确定性缺口。所有APK/Android源码/Gradle冻结无build。

新APK144新产品53896765B539f44c583874e43de866744939f18a28b2e98494cc343fed41fc435de0d149b/newtest10677879Bf2d2982bba57cb53cc72e96258fede386da9574fbe92cc269ac8d67d02d72bbe不动。Root不旁路ADB/build。任何ADB/VM探测启停/安装卸载force-stop设置/hostmain真执行/真实run-id预算attemptsummary/F1/设备皆禁；Git云正式库私库自动记忆/旧资产删移压迁/共享进程处置亦禁。只有明确Root新设备批才能真实执行，不能以本批定义入口自动继续。完成等Root实际核，最终总结末行：这是任务145-r2。
````