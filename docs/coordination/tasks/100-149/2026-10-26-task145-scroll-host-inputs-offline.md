# 任务145-v1：新scroll宿主与输入闭包，仅离线

2026-10-26香港15:08；原144 owner，同Claude桌面WorkBuddy global Flash。不是设备批，不自动执行。

````text
Root读144实际REPORT/47s build原log/freeze/原APK离线证据，实核旧保全两件与新构建两件sizeSHA均同。接受匹配APK离线构建冻结，不运行验收。旧74d106b6/28173170已保全task144/preserved，不再复制。
新产品53896765B SHA539f44c583874e43de866744939f18a28b2e98494cc343fed41fc435de0d149b、新test10677879B SHAf2d2982bba57cb53cc72e96258fede386da9574fbe92cc269ac8d67d02d72bbe，均原candidate/android/app/build/outputs/apk/debug/app-debug.apk与androidTest/debug/app-debug-androidTest.apk。package/version3/0.5.1/SDK24->35、runner CraIsolatedTestRunner target com.creationreadingassistant、四角色签名同05249d78已核。DEX含新reader/seam/scroll类，非运行。

目标：为已冻结SearchListScrollRetentionTest建立新的隔离host和inputs，真实收集与严格判定当前t141b阶段，不复用旧F1证据合同冒充。只离线开发验证，无源码/Gradle/APK/设备。
只写 E:/creation-reading-assistant/task145-scroll-host-binding-v1/，新scripts/host145.py、inputs.json、最小派生patch/REPORT/必要离线raw与ready.json。20分钟/差量512KiB/raw512KiB。不复制工程/cache/APK/旧原始证据，不写D current。

只读复用父host E:/creation-reading-assistant/task136-f1-global-offset-repair-v1/lane-b/scripts/host136.py52645B SHA9457a4c5989e2c408923048b9c6b3c818b35573e6c9345eeb048632a9c5912b2，旧inputs同lane-b/inputs.json仅作九角色结构参考，内容旧APK与instrument pin已过时禁止沿用或改写。base E:/creation-reading-assistant/task94-organizing-export-integration-v1/v3/scripts/t94v3_device.py16583Bc806ce36；严格adapter E:/creation-reading-assistant/task83-selfuse-delivery-v1/B/v2/protection-root-r1/candidate/protection_check.py7864B16c93794844650f2116fff7c97c5adb29acb15bfbc623a000643fa2fa5f722bd。base/adapter/guard/恢复逻辑不改。

新仪器真实源码 E:/creation-reading-assistant/task59-common-candidate-v1/candidate/android/app/src/androidTest/java/com/creationreadingassistant/acceptance/SearchListScrollRetentionTest.kt32650B11092359b9857571d304646d9694e1e74e1f0763853cbf0555855ac0b40a012f，class com.creationreadingassistant.acceptance.SearchListScrollRetentionTest，唯一@Test。技术task145-v1但仪器已编译runId固定T141BV1-YYYYMMDD-HHMMSS、arg craRunId，必须与源码一致，不能改instrument/APK为T145。新host严格此正则，离线不能生成真实run-id/attempt/budget/summary文件。

读实际test源码建立而非猜字段：结果t141b-results-<runId>，fixture目录/25个bookID从源码精确导出；阶段t141b_00_fixture/10_search/20_scrolled/30_reader/40_back/50_success/99_assets；失败first_failure与assets按实际最小合同。query CRAQLIST141，25本中性测试TXT。fixture/back包含progressRows{bookId->真实12列nullableJSON}，不能只信progressRowsUnchanged布尔；严格25book归属/完整columns/null与值逐项相等。pre/post index/offset/tab/query/topItemKey/itemCount精确同，pre index/offset确非零，itemCount含header不可盲等25；selectedtarget真实book/key可与topkey不同，center必须在listbounds，targetCardPresent/marker/首次rid1提交/会话合法结束按实际阶段核，不能只statusPASS。
成功not_covered必须是JSONArray（Python list）非字符串；phase/runId/package/targetbook归属与assets精确核；缺key/parse/错误类型/字段错/任意一12列变化/错runId/伪成功均红，first_failure保因。不能沿旧t129/单book/旧marker/coordinator recreate要求或放宽缺证。不得让4-run包装exit0掩盖business/collect/recovery失败；状态分列/恢复停止原合同复用。

新inputs至少原九角色真实闭包：新product/test/newinstrument+strictadapter+原testAPK0d18/原testdata tarfbdbb692+SourceNavigationContractc6c5aebc+TemporaryReadingVM e4af886a+AppNavigation7a6046a5。增加真正新的生产观察接缝SearchScreen45026B80181fa42605dc9616635795af26623bfa20aa309cc8b58052ca78331110570f与reader改动由144freeze绝对路径SHA引用或作为明确额外role，不强制九角色数量造成漏依赖。新hostpins与load_and_verify_inputs角色一致，两个新APK实际指纹同144freeze，不旧cc/81instrument错pin；旧inputs永久留。host外层SHA单独报，不造自引用hash循环。

所有运行输出自然新145根；保护必须位于actual adapter OUT_BASE E:/creation-reading-assistant/task83-selfuse-delivery-v1/B/v2/protection-root-r1/task145-scroll-host-binding-v1/<未来唯一initial/final>，纯路径核包含关系与目标不存在；不得把保护写145外根导致旧133阻断重演，不改pin guard。原产品缺席/原test code data/userstate/settings/night/Download/保护/同VM与user0等fresh预检及停止恢复逻辑保持；本批不探测设备、不把旧139状态当fresh。保留失败先收集完整product三根原始数据成功才正常卸载的严格恢复停止门，不模糊为collect随便PASS；旧metadata问题不能重演。

仅新增绑定/阶段判定/闭包必要离线验证：导入前subprocess与任何设备调用装guard；禁止main/run_attempt/adapter.run_check/ADB/VM。load_and_verify_inputs纯离线验证允许，写pycache只新task内或禁用。不重复旧31项全面宿主审查/充分reader JVM/F1。合成阶段只在清楚标SYNTHETIC_OFFLINE目录作定向正负validator测试，绝不能放未来运行evidence路径冒充设备。证明关键12列变异/错归属/prepost错/JSONArray字符串/缺证不能假绿，同时正确合成合同可过；最小必要边界即可，不泛框架。

144生产/测试源码和两APK冻结，不改任何instrument/product/helper/公共配置/deps，不build/compile/assemble；禁任何ADB/VM探测启停安装卸载force-stop设置/hostmain/真实run-id/budget/attempt/F1/设备；禁Git暂存commit push PR发布/云/正式库HOLD/实体/购买换号切供应商/旧资产删移压迁/杀共享进程/私库自动记忆。139恢复与138保全/137限定及旧红原件不动。Root不旁路build/ADB。

交付实际最小host派生diff、inputs各role sizeSHA/hostSHA、REPORT、定向离线原log/ready，报告APK已构建但新host未设备执行。Root读实际接缝与闭包后另派新单次设备批，本批不得自动继续。最终总结末行：这是任务145-v1。
````