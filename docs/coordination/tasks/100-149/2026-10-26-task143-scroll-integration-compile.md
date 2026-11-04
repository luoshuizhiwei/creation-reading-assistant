# 任务143-v1：集成已审scroll草稿并编译，保留reader修复冻结

2026-10-26香港14:52；同Claude桌面WorkBuddy global Flash，原142 owner接集成/必要编译/交付，不并行竞争Gradle。

````text
Root实际读142-r3 232行patch/REPORT/原2case红log/8case绿XML及compile log，VM55808B/f8762da6326972c3c4582c6a38aa142656dbbeedd6a5fd04b73265edd5c2e3e3、VMTest103388B/cec25280cc84d17ecf784bd83742d665b37d241b65b8aa2229f7baf77473c19b实核同。接受限定源码修复/八目标与编译事实，不设备/全产品。原141/142-v1/r1/r2/r3红绿原件冻结，仅引用。

本批目标：将已审141-B-r1新SearchScreen观察接缝与新scroll测试整合到唯一共享候选，完成必要Kotlin编译；不构建APK、不设备、不host/inputs/attempt。不要顺便重做已充分reader JVM。
唯一工程 E:/creation-reading-assistant/task59-common-candidate-v1/candidate/android。

输入先核sizeSHA：
- 共享原SearchScreen.kt43228B/72cc0d1a6900c2b8aed423b8a3e2ff05ef22ce185c436df89420f3983625ecba。
- B未变seam源码 E:/creation-reading-assistant/task141-offline-implementation-v1/lane-b/android/app/src/main/java/com/creationreadingassistant/ui/screen/search/SearchScreen.kt 45026B/80181fa42605dc9616635795af26623bfa20aa309cc8b58052ca78331110570f。
- B seam patch同lane-b/SearchScreen.seam.patch3158B/37862c933178454e105ab3b112b5f02adf4ad13265c0d06d1e98671ce1fd989e。
- 最终修正test同lane-b/recheck-r1/android/app/src/androidTest/java/com/creationreadingassistant/acceptance/SearchListScrollRetentionTest.kt32650B/11092359b9857571d304646d9694e1e74e1f0763853cbf0555855ac0b40a012f。
- B-r1原REPORT5735B/c08617b4936d431c9266e44dcecda479d559a6e9ed53918b7843080405e5ad19；Root已读相对v1实际修正patch，五缺口已修：QUERY真实起点/Back状态/窄Compose超时/25本12列nullableJSON/点击中心listbounds。原件只读引用，不复制整个交付或工程。

只写共享candidate上述SearchScreen.kt与新SearchListScrollRetentionTest.kt。已有新类若非缺席则核fingerprint冲突停，不覆盖其他WIP。Seam保持仅testTag/typed snapshot semantics，不搜索业务行为改变；25本中性测试TXT/runId T141BV1/空term写前拒错/真实稳定key/非零indexoffset/精确prepost tab query topkey itemcount/临时reader真实Back/会话结束及12列精确保持，均不可放宽。选卡以后不可滚动改变pre，旧topkey仍精确断言；itemcount含header不能盲=25。

先按真实调用编译：:app:compileDebugAndroidTestKotlin，一次必要原log；本批新增代码造成的import/API/nullable/参数等编译缺陷由同owner最小修正并必要重跑，全红日志留。不能为编译吞错/改断言/删测试。特别B awaitBackState的ComposeTimeoutException被换UNKNOWN：若本批触及该块，保留原cause再报明确失败，不让超时丢因。其余不造泛审/架构变动。若真实API缺口需要产品新框架则准确BLOCKED。无全JVM/整VMTest/lint/assemble/运行仪器。

reader frozen：VM f8762da6，VMTest cec25280，Screen c1727db4、Platform6de3b254、Session19c5ddc5、Route3c0cd8bd、ReaderNavLateDisposePersistenceTest ca28dab9；F1cc429bb8、SaveOrder61b2ca9d、AppNavigation7a6046a5/helper原件不改。只核必要前后指纹，不重复旧全部日志或保护扫描。Root不build/ADB。JDK D:/develop/Java/jdk-17.0.14 SDK D:/develop/Android/Sdk Gradlehome D:/develop/env/gradle，独占锁E:/creation-reading-assistant/locks/android-gradle-exclusive.lock，冲突停不杀daemon。

输出 E:/creation-reading-assistant/task143-scroll-integration-compile-v1/ready.json、REPORT、两文件最小集成patch/必要编译修正patch、前后sizeSHA、必要原log。20分钟，差量256KiB/raw1MiB；不复制工程/cache/历史/APKs。新增文件与patch按需最小交付，不多份复制同源。列明真实compile结果、未运行UI/未设备/未构建APK，确认新增类源码不等于现有APK已含类。

当前产品52763832B/74d106b6b1349cfea1f4b4bb5f49e41c27691d6621ca1424e4c55effa0de84e4、test10658820B/281731702481a5a92f65efe8c64558a38577c0e711105726f6f9c6e1dc3db25b冻结不得覆盖。未来产品接缝+reader修复需Root另行新产品/测试APK构建冻结，旧产品单件保全；单testassemble不足。本批明确不授权build任何APK或device。

不Git暂存commit push PR发布/云/正式库HOLD/私库自动记忆/任何ADB VM探测启停安装卸载force-stop设置/hostmain run-id attempt F1/旧资产删移压迁/杀其他进程。139恢复/137限定与旧红不追改。完成等Root实际核后再规划下一批，不自动后续。最终总结末行：这是任务143-v1。
````