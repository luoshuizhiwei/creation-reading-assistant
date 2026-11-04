````text
任务149-r2｜ZCode B：修测试导航起点/生命周期，集中合入并定向编译｜2026-10-27 HK｜沿当前GLM-5.3-Flash，不额外泛审
用户授权自动接续、多任务并行，ZCode额度明确耗尽才Antigravity。A148-r3只recover-only处理旧已装APK数据，不调用完整新业务main，不占Gradle；你独占candidate指定源码与Gradle，不碰设备/host/inputs/旧APK。
基线只读交付E:/creation-reading-assistant/task149-reader-exit-page-test-v1/recheck-r1/。Root完整读实际test与patch，SHA实核test8b43ea648aa1fdb51278757ef2603e3beea242e3cc94499a61897b45929e323e、patcha8a3521ed6d291c6d756f3adf6d2388bbbdeb83acc4829ef39a6f8f61e97a940。旧六问题修改方向接受，尚无编译证据。
本轮只修真实缺陷：
1 TestNavHost以HOME为startDestination，却没有任何navigate reader动作，四测试不可能到ReaderScreen。通过真实一次导航建立home->reader栈，按实际项目Nav版本实现；每用例触发Back之前明确确认reader当前目的地、真实reader节点已出现、无初始pop。不把起点设reader后无返回栈，不能手动调用drain替实际Back。
2 LifecycleEventObserver在reader组合onDispose移除，可能在entry真实ON_DESTROY之前移除而丢事件；确保注册与解除owner覆盖实际待观察的entry生命周期，测试结束可清理。不要强加框架无保证的reader_disposed<entry_destroyed顺序，只断言保存正常完成先于pop、最终reader离开/entry销毁符合实际导航合同；两事件内部顺序无依据不假设。
3 失败重试代次与成功后页面转换必须等到真正达到所断言状态；不能await pop callback后立刻读异步销毁计数。保持生产失败snackbar与四性质，不降低业务语义。
4 构造/资源/import/空书态/BackHandler先后等实际编译问题同owner闭环。测试仍是受控await lambda+真实ReaderScreen/NavHost组合，不声称Room落盘或进程死亡。
先自查修复实际差量，然后本轮明确允许集成到唯一candidate：E:/creation-reading-assistant/task59-common-candidate-v1/candidate/android。所有权只有ReaderScreen.kt两处测试SettingsViewModel注入接缝，以及新增app/src/androidTest/java/com/creationreadingassistant/ui/screen/reader/ReaderExitSaveDrainDeviceTest.kt。其他生产文件/公共runner/build配置不改。基线ReaderScreen46779B c1727db4f9c1624051f3d2532cfba254a5c4259abc3eaaeed207cbf825347dff，写前核当前指纹，漂移先对照合并不覆盖WIP。旧交付冻结。
只新交付E:/creation-reading-assistant/task149-reader-exit-page-test-v1/recheck-r2/，有效最小patch/新增test、前后完整SHA、REPORT和必要原log，source/doc512KiB raw2MiB；不复制工程/APK/cache/历史。A使用已装固定144 APK，不读你修改后的candidate源码作旧批pin；你不得回改旧inputs或冒充旧APK匹配新源码。
本轮Gradle唯一owner：先读项目AGENTS和当前锁约定，取得E:/creation-reading-assistant/locks/android-gradle-exclusive.lock独占后，仅在唯一candidate跑:app:compileDebugAndroidTestKotlin，JDK D:/develop/Java/jdk-17.0.14、SDK D:/develop/Android/Sdk、GRADLE_USER_HOME D:/develop/env/gradle。其他owner在锁上则不抢占/kill。禁止assemble/package/connected/device/JVM全suite/lint全量，不ADB/VM。保留第一次原编译红及每次必要修复结果；新增编译错误同owner在范围内修正至定向编译过，不改断言迎合编译。
固定144 APK仍原字节：产品53896765B SHA539f44c583874e43de866744939f18a28b2e98494cc343fed41fc435de0d149b，test10677879B SHA f2d2982bba57cb53cc72e96258fede386da9574fbe92cc269ac8d67d02d72bbe，绝对路径见148原ready；本批编译前后核同，不复制或覆盖APK。若发生无法避免的变体打包依赖，停止而非默许assemble。
提交前同owner核实际交付：四性质、真实reader入场、无旧事件假绿/不可能await、observer寿命与资源释放、有效patch、实际编译原rc/XML（若无XML则原log）、当前源码和交付SHA、旧APK未动、锁归还。自查发现具体错先修复再交，不新开泛审；不把静态脚本0问题叫运行通过。
预计30分钟；完成标SOURCE_INTEGRATED_COMPILE_PASS_RUNTIME_NOT_RUN，设备运行另排，修复不到则如实PARTIAL。ZCode额度未知不猜、不反复探查/切供应商/购买；额度紧张保存可接续patch与首次红、释放本批锁，不占A恢复资源。不Git提交/发布/云/私库/自动记忆/删移旧件，不改current.md。
最终单独末行：这是任务149-r2。
````