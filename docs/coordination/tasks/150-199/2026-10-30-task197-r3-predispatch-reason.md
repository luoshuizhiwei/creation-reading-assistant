````text
# 任务197-r3：把临时转换原因传到保存之前，修复真实边界顺序并闭环

你是原197-r2同owner，继续唯一candidate产品/必要测试/Gradle/交付。工作区 E:/creation-reading-assistant/task59-common-candidate-v1/candidate/android。保护无关WIP；当前无第二必要独立项，不开工程或并行改同文件。

## 有限接受与输入
r2根 E:/creation-reading-assistant/task197-reader-entry-boundary-integration-v1/recheck-r2/，ready4870B SHA b022da8adca5e779b9c0a5e3ce70948b42e49b1a4aa23022c41e3c157e08dbc5；25pins实际匹配，三after与candidate同，原XML5tests/0fail及test-signal3原rc0、三buildrc0有限复用，不重复充分旧suite。
candidate产品文件SHA：ReaderScreen.kt 20a97b68f9f47a5a5e5062e42d47fc4958131b0106483722a34cfff9a805a119；ReaderRoute.kt 43155e69bc8775300b237b2e3ce7132212fe7896d0ef271947d47a801007767b；ui/navigation/TemporaryReadingNavigationViewModel.kt dfef051152c338f069e8bfcf89d39476907c75c3a84c7269d41b8e8aa565dee6；ReaderScreenArgs.kt 5007B60a960788dc70072d87ee8725ba2db902a421e62ed0afc986a3f2d5b8cc426c6（r2遗漏after/patch，必须补最终差量）。
产品APK53899310B687e9bfcef39a39be5f69015185c6105116fe032b883db5c8c65e07c2b87b916；test10696613Bc9c21bef41c9936874455f180ed3f8a5f80606fb1ea86366377811cfd843b5f6。test实际已变，不能沿用聊天的475ea076不变声称。

## 确认的顺序缺陷（不得只再加旁路布尔）
ReaderScreen.onInspectSourceWithBoundary先调用exitBoundary.requestExitWithAction(action={inspect(route)})。
ReaderExitBoundary真实实现：先同步dispatchExitSave()，再awaitProgressSaves，成功后onDrained()、navigated=true、action()。
你在ReaderRoute.onInspectSourceTemporarily内才signalTransitionFromNormalReader()然后navigate；这个回调正是action中的inspect(route)。所以首次权威保存读取信号时仍false，S0仍保存刷新；置位发生在保存和onDrained之后，dispose又可能被persistGate抑制。信号可能残留至返回后的下一次普通保存；全局一次性consume也没有绑定reader/request和失败取消，不能据单独signal/consume测试证明接线正确。
现有5tests手动先signal再consume再分别测position函数，与真实生产顺序相反。普通无信号case用Int.MIN_VALUE而非相同有效anchor也未覆盖原要求。原红是编译失败，不是该顺序行为反例。r2离线Python镜像不得当生产行为证明。

## 最小修复与验收
沿已有ReaderExitBoundary及ReaderScreen入口，在调用本次dispatchExitSave之前传递这次操作真实原因；普通Back/lifecycle默认仍保存合法时间/元数据，临时未移动只在对应操作跳保存。尽量直接随本次调用传递，清除r2自己引入而无必要的全局消费信号，不新建平行导航/保存框架，不扩大无关格式。
真实移动要保存最新合法进度；临时期间完整12列exact/LIFO及外部Notes合同B不放宽。不改scroll规避、不sleep、不临时操作后重采S0。页锚不自动等于已落库事实，明确恢复落位、采样和保存排空顺序。
未完成/失败/内部取消/调用scope取消不得提前导航或onDrained；重复点击不重复保存导航；失败后普通保存不能继承临时抑制状态。保留原异常，不吞错。

## 必须验证真实调用顺序
直接调用实际ReaderExitBoundary.requestExitWithAction/实际新生产接缝，测试观察dispatchExitSave当时的真实原因/是否写入、await前后顺序、onDrained和action次数；该接缝必须由ReaderScreen真正调用。不要在test复制生产if或只手动置flag证明顺序，不绕过保存决策。
相同有效anchor==current下：临时未移动不写S0；普通Back和生命周期仍写；临时真实移动写最新位置；pending/failure/internalcancel/scopecancel/重复点击/返回后普通保存无残留。只测本次新增或实际影响项，旧197的11项充分证据复用，若改到其边界才跑对应项。
先让针对r2真实顺序缺陷的新测试在r2实现下产生行为失败，存原log/XML/rc，再修绿。编译失败不当行为红；若旧行为无法直接回归准确报告缺口，不伪造。
同owner用现有 tools/gradle_locked.py、锁 E:/creation-reading-assistant/locks/android-gradle-exclusive.lock、JDK D:/develop/Java/jdk-17.0.14、SDK D:/develop/Android/Sdk、Gradle用户目录D:/develop/env/gradle；新增/受影响定向绿后compileDebugAndroidTestKotlin、assembleDebug、assembleDebugAndroidTest。与输入匹配的未变充分证据复用，不全suite。

## 新交付根与历史
只新增 E:/creation-reading-assistant/task197-reader-entry-boundary-integration-v1/recheck-r3/，唯一ready=根ready.json。旧r1/r2不覆盖。最终全体必要after/test/最小patch须等于已编译candidate，含Args接缝；patch可双向检查。不使用Git暂存提交来校验，不删除临时或旧证据。
真实完整size/SHA/pins（不自引用）、红绿log/XML/rc、APK签名runner/target和类方法验证、REPORT。设备NOT_RUN。r2实际267303B不是约62KB；时间02:22–02:26在Root检查02:17时仍未来，不采；如实计本轮实际时刻与互斥体积，旧错误/红保留。覆盖APK前仅缺必要独份才保全一次，日志哈希不等于原APK存在，丢失不得补造。
预算35分钟自实际收到本条起；source/patch/test/REPORT/ready<=256KiB，新增文字raw<=256KiB，必要旧APK独份另列；引用现有before不再整复制，超额不删证据补绿。
禁止ADB/VM/设备/main/instrument/安装/恢复/launch/reboot/connect/kill、host/collector/fixture/消费者/业务断言阈值改动、Git暂存提交pushPR发布、删移既有资产、云/正式库/私库sessionDB/.codex/memories/自动记忆。194恢复关闭、193原业务红永久留，新设备批仍待Root裁决。输入漂移/所有权冲突/用户stop或Esc/安全权限时停止相应新增操作，不旁路。最后独立一行：这是任务197-r3。
````
