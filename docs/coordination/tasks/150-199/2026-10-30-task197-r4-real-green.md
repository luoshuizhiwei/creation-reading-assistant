````text
# 任务197-r4：完成未接线生产修复，修复实际失败测试，禁止复用旧XML称新通过

原197-r3同owner继续唯一candidate产品/测试/Gradle闭环。工作区 E:/creation-reading-assistant/task59-common-candidate-v1/candidate/android，保护无关WIP；无第二必要独立项，不另建工程。

Root已核r3，不能通过：
1. ready5726B SHA27de0dcde6a732027eb7d3bf68cb9bfcb04d8f0d908ffb8db4709fe16ad04b6d。实际raw/test-final-result.json status=FAILED exit_code=1 elapsed22.2，test-final.log为compileDebugUnitTestKotlin FAILED：ReaderPagedTransitionSignalTest.kt:180 nullable ReadingProgressEntity不能直接访问current_location_json。test-boundary-result亦FAILED rc1。报告把22.2秒写成5/5 pass，错误。必须保留原红，不重写或改成rc0。
2. raw/xml内5项XML timestamp2026-10-29T18:11:03，case仍signal consume/idempotent等r2旧case；r3交付测试源码有8个@Test（boundary4+decision3+persist1）。这是旧XML，不证明r3执行；不能仅看5/0或拷旧XML称新通过。
3. candidate ReaderScreen.onInspectSourceWithBoundary实际仍只requestExitWithAction(action,onFailure)，没有传skipExitSave，也没有temporaryEntryTransition=true。新boundary默认false，临时入口仍保存刷新S0。r3“已接线”声称与源码不符。
4. candidate ReaderViewModel.ReaderExitBoundary新增skipExitSave，ReaderScreen仍有未置位temporaryEntryTransition状态和消费分支；Args旧isTemporaryEntryTransition残留。无用的多层机制没有完成产品需求。
5. r3 pins36项中35匹配，索引自引用raw/deliverable-pins.json错；实际目录309344B，报告约60KB/33KB错误。元数据/旧APK SHA与原rc错误永久保留，不再反复填模板。

先停下总结，完成当前必要工作，再输出交付。只报实际观察，未执行/失败就明确标记，严禁把旧XML或构建绿代替新JVM绿。三构建原rc0有限接受，不能证明JVM通过或入口生效。

## 产品最小闭环
- 真实入口的这次操作原因/跳保存判定必须在权威dispatchExitSave之前实际传入。用最少接缝，在真实ReaderScreen入口调用；不能只给boundary加参数但调用方留默认。
- 临时未移动保持完整12列exact；真实移动必须保存最新合法进度和时间，不得临时入口一律skip=true。当前paged锚/current及合法保存事实清楚核实；没有证明的持久化事实不猜。
- 普通Back和生命周期在同有效anchor==current时仍保存合法时间/元数据。无状态残留、失败/取消/返回后不得抑制普通保存；删除自己引入的冗余未接线signal/callback即可，不清他人资产。
- pending未排空不得导航；失败/internalcancel/scopecancel不得onDrained或action；防重复点击保持。复用现有ReaderExitBoundary和原保护门，不开平行框架，不改host/collector/fixture/断言阈值/12列，不改scroll规避或加sleep。

## 有效验证
修复当前测试编译错误，然后直接测试生产实际入口决策接缝+ReaderExitBoundary，观测dispatch/await/onDrained/action先后和次数；生产ReaderScreen确实调用同一接缝。测试不能复制生产if、只测裸skip参数、或手动置flag假装生产接线。
至少同有效锚下普通Back/lifecycle保存、临时未动不写、临时已动最新位置写入；pending/error/internalcancel/scopecancel/防重/后续普通保存无残留，仅跑新增/真实受影响项。先保留真实行为红再修绿；编译失败不当行为红，无法复现则诚实说明。
执行后必须读原结果JSON exit_code和log，确认XML对应本次源码的case名称/数量/时间且与本次log关联，不用旧5case。测试不通过持续同owner修，不先交ready。匹配输入的旧197充分11项和未变构建证据复用，不全suite。必要源码变化后用既有tools/gradle_locked.py/唯一android-gradle-exclusive.lock，JDK17.0.14/原SDK/Gradle目录，定向绿后compileDebugAndroidTestKotlin+assembleDebug+assembleDebugAndroidTest。

## 输入与交付
基线以当前candidate真实文件SHA及r3 after核；真实差异公开，不以报告过期指纹替代。唯一新根 E:/creation-reading-assistant/task197-reader-entry-boundary-integration-v1/recheck-r4/，根ready.json。旧r1/r2/r3原件只读不覆盖。交必要全部最终after/test/最小patch/完整真实pins（不自引用）与candidate逐字节同、原红绿log/XML/rc、APK实际size/fullSHA及签名runner/target/目标类方法，准确REPORT。修正“测试通过”必须基于实际新rc0与新XML，不能改旧失败回执。
预算35分钟自本次实发起；source/patch/test/REPORT/ready<=256KiB、新文字raw<=256KiB，必要旧APK独份另列；引用现有before，禁止又复制整套。先估算，不复制工程cache。准确互斥字节与真实时间，超额不删证据补绿，历史错误保留。旧APK哈希日志不等于原APK存在，覆盖前仅缺必要独份才保全一次。
禁止ADB/VM/设备/main/instrument/安装/恢复/launch/reboot/connect/kill、Git暂存提交pushPR发布、删移既有资产、云/正式库/私库sessionDB/.codex/memories/自动记忆。194恢复关闭，193业务原红保留。输入漂移/所有权冲突/用户stop或Esc/安全权限停对应操作不旁路。只在本任务实际闭环后总结；阻塞如实交BLOCKED及缺口，不能PASS。末尾独立一行：这是任务197-r4。
````
