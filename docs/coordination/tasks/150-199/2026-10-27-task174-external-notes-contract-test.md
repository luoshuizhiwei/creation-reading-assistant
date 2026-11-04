# 任务174-v1：现有外部Notes临时查阅合同的测试修正与定向构建
2026-10-27。推荐使用现有ZCode glm-5.3-flash最高，保留现有帐号，不购买/换号/provider/API旁路。173只读诊断已交付，Root已核关键源码与16项输入：15项SHA匹配，Nested测试指纹在报告漏首字符2，实际原源16232B SHA2645c3ec1229f64f88ffdd89a2a194aea77a96f599e1e1e233a30fc62fc4c8f0；报告该元数据缺陷保留不改旧件。Root本任务显式选择173方案一：只测试现有合同B，不做新产品入口。20分钟从实际UI新消息起，包含实施/必要编译修复/定向compile与testAPK/交接同owner闭环。遇普通系统繁忙可在原任务继续，保留原超时/续作时间，不重派、不Retry与消息双发。

唯一源码所有权：E:/creation-reading-assistant/task59-common-candidate-v1/candidate/android/app/src/androidTest/java/com/creationreadingassistant/acceptance/NestedTemporaryReaderEntryTest.kt。唯一既有candidate/android Gradle workspace owner，精确锁 E:/creation-reading-assistant/locks/android-gradle-exclusive.lock，复用tools/gradle_locked.py；其余产品源/测试/Gradle配置/旧host和报告只读。本任务目录 E:/creation-reading-assistant/task174-external-notes-contract-test-v1/ 仅最小patch/前后源码/pins/REPORT/必要raw/ready。无device/ADB/VM/安装/实际instrument，不启动实例。当前设备恢复另待用户原实例单次launch许可，不能用此任务越界。

原证据：171一次instrument两个方法均Nested184找不到我的；初始marker1与普通行已经前进通过。现有AppNavigation201仅顶层底栏，209-213离reader即endReaderSession；SourceNavigationContract150-183仅active提供返回目标，离开清active与栈保留normalReading；ProfileRoute269-280真实外部临时来源入口。ReaderChrome623/633仅temporaryInspection且hasReturnableTarget才显示返回阅读处；外部进入应无该marker，Back按ReaderRoute132-136回实际Notes来源页。173 REPORT/ready为只读诊断，不是设备通过；旧171/168/165红不重跑/不改绿。

目标与Root明确授权：把当前两方法的UI编排和断言更正为合同B。允许替换原“返回阅读处必须出现/Back直接回普通reader”的错误断言，但必须以更强的具体外部合同验证替代，不是直接删断言做绿。不得改产品/注入VM或NavController状态/伪造temporary/fixture进度归零/等任意文字标题/sleep/延长timeout。保留两方法现有技术ID以复用冻结host方法绑定；类头与注释须诚实说明本批合同B：Back回Notes，再次进入新代次；回普通位是明确重新普通打开，不是Back凭空恢复reader。原reader内LIFO返回目标及缺少真实入口的功能缺口单独记未覆盖，不能把方法名当原目标已完成。

具体成功条件：
1. 从普通reader走真实退出，按实际状态等待顶层导航后用唯一selectable底栏我的进入阅读笔记，证明列表真实身份/本轮fixture精确条目，不仅任意同名节点。保留已有Home→Shelf/精确booktile/初始contentA.marker1正文就绪修复，不逐个猜入口。先只读精确当前helper及直接UI依赖，整体核两用例后续路径，不再只修184等下一次失败。
2. 正常退出可能合法写入普通阅读行时间等元数据；S0隔离基线应在普通退出保存已按实际生产可观察边界完成、尚未临时点击之前建立。记录退出前后位置并证没有跳到sourceOffset1/2，不能把正常退出更新算作临时写入，也不能事后把临时写入回填S0。保留原12业务列exact/nullableJSON逐列比较，在临时正文真实就绪及Back回Notes后再次比较完整行。
3. 点击本轮精确noteTitle条目及临时查看来源，使用同书同sourceOffset的精确正文marker/snippet（先核fixture定位关系）证明确实到目标；不能仅凭marker不存在/任意文字认为就绪。显式断言外部来源没有返回阅读处目标，真实Back后证明回原Notes列表与精确条目。不要用仅数据库偏移相同冒充UI返回正确。
4. 用例2在第一次Back实际回列表后选择第二条不同offset，再核精确新目标正文/无reader返回目标/普通行exact不变、Back回列表；不携带前次临时栈或源状态。
5. 完成临时流后通过真实UI重新普通打开同书，核普通正文/派生位置仍为已建立S0，且不是两个临时sourceOffset。此为普通重开恢复持久指针的独立行为，不偷称临时Back回普通reader。真实状态边界覆盖临时退出保存，不加人为延时。若源码证据不足以写有意义断言，明确具体缺口停，不盲改/缩门。

构建前当前test APK原buildoutput10683079B SHAec19dba1efb44f27be7029053910e5bbbd1a71db17a2abbbad61be7a88fb181b，先只核已知task170/preserved和当前任务preserved是否已有相同独份；没有才必要单份冻结旧test（约10.7MB另列），不得泛扫所有task。product54969452B SHA4d302624d603c06b12b1a30480dd1a9d5b7ce5e44cb24efafecd81d4d6c62577已在task167/preserved保全，不复制。新APK只原buildoutput不复制项目/cache。JDK D:/develop/Java/jdk-17.0.14、SDK D:/develop/Android/Sdk、Gradle home D:/develop/env/gradle。

仅定向 :app:compileDebugAndroidTestKotlin 与 :app:assembleDebugAndroidTest，原编译/修编译归本owner，不重跑JVM/Lint/158四PASS/充分scroll/166五case或旧offline suite。记录真实rc/stdoutstderr/锁释放、新源/testAPK完整sizeSHA/签名runner，product源/APK不变。原test恢复资产0d18不动。source/doc≤128KiB、全部raw≤128KiB，必要旧test独份实占另列；不复制旧日志/APK工程镜像、不覆盖删移唯一证据。

REPORT须明确本批更正的合同/错误旧断言与替代断言/源patch及前后SHA、普通退出S0边界/两目标正文依据、原范围未覆盖部分、定向构建原日志/新APKpins/锁与owner退出/实际空间时间。唯一ready E:/creation-reading-assistant/task174-external-notes-contract-test-v1/ready.json。交付只源码与编译，不称devicePASS/任意深度reader嵌套功能完成；原业务红/恢复缺证/超时预算史永留。禁Git暂存提交pushPR发布/云/正式库/私库sessionDB/.codex/memories/自动记忆/其他VM设备/kill共享ADB。用户stopEsc/锁屏/login安全权限停不绕，旧草稿保留。最终独立末行“这是任务174-v1。”
