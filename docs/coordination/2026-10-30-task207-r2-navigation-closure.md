# 207-r2 ZCode自动交接合同（待确认发送）

编号207-r2；2026-10-30香港时间；实施使用ZCode当前实际可用且适合导航修复的模型；电脑操控单独委派GPT-6 Luna，不混淆两者；复杂度高，粗估40–60分钟，硬预算40分钟，届时交实际缺口。用户本轮已授权自动交接到ZCode，确认全文进入会话后开始；时间未知不能补造。
作用：闭合207-r1的生产失败/取消回退与真实生产测试缺口。依赖Root本轮核验；本任务ZCode为唯一实施owner、独占candidate导航和Gradle，无设备线并行；现无可独立派发的明确B线工作，不造任务。用户2026-10-30重新授权自动化提示词交接；设备批仍需另派。

````text
你接续任务207，执行207-r2。Root未接受207-r1；r1已经由你改入E candidate，不要套用旧“207-v1未合流”假定当前仍为旧基线，不回滚用户资产。先读活动仓库AGENTS/current、docs/coordination/2026-10-30-task207-r1-navigation-closure.md和E:/creation-reading-assistant/task207-reader-exit-stack-repair-v1/recheck-r1/root-review/REPORT.md。

唯一工作区E:/creation-reading-assistant/task59-common-candidate-v1/candidate/android。Root本次已获用户授权将本合同发送至ZCode，原外部owner不得并写；本合同不授权执行者操作设备、ADB、VM或其他桌面应用，不授权向外部代发消息或自动继续其他任务。不得改D产品源码、Git暂存提交pushPR发布、删除移动已有资产、正式库、私库session DB或记忆。

输入：r1 ready SHA 396bd602947801d8743092f23582e1c7eb2d8ab4cf5a64d59cdb04a1770a0579；ReaderRoute.kt 17153B/SHA6451a94ec6115a36d0162c2fb51446d23e7e1235e01d3a599d85ea7dfb6f88dc；新图测试14718B/SHA06aeb981aa7f114c749bb55f59555876ce65cf6bd9b1798e9b117caaae6b373a。原基线和r1完整证据就地只读复用。JVM测试aa3de67c、203测试1f289540保持原断言和timeout。

所有权：只允许ReaderRoute.kt、ReaderTemporaryBackTest.kt及ReaderTemporaryNavigationStackTest.kt中的必要最小修复。生产其他文件、AppNavigation、公共配置、fixture/冻结host不可写；确需扩展先交准确文件、理由和停止点。你并非独占整个代码库，不覆盖他人修改；开工核输入，冲突停写。新增报告放task207-reader-exit-stack-repair-v1/recheck-r2，不复制整工程或旧证据。

先处理真实语义，再构建：
1. 2.5.1 singleTop替换原reader后，当前未提交请求return null→popBackStack会失去原S0/T1页面。最小策略必须兼顾成功LIFO与pending失败/取消返回仍合法的原reader，不能只维持协调器内存而丢导航路径。不得强行消费合法LIFO、硬编码书架/Back次数或绕过保存await；报告保留异步保存失败不确定性，不把导航假设称205唯一已证根因。
2. Root改变测试方法：不再用DisposableEffect组合存活数当真实栈数量。核实际2.5.1框架来源，使用真实可支持的backstack观测或pop/destination序列。测试必须关联生产受改函数/回调；禁止复制生产navigate选项后称生产回归；不得把ReaderRoute替成Text再跳过其request/ownedRequestId/加载结果/保存边界。
3. 明确覆盖原入口（书架、搜索、笔记中实际存在的入口）→S0→T1→T2→T1→S0→一次普通退出回原入口；失败/取消至少S0→T1 pending失败和S0→T1成功→T2 pending取消；迟到结果不能提交到更新会话。pending回退不消费既有合法栈。仅纯函数测试不能证明NavController语义；图测试可只编译，但必须NOT_RUN，不能写“独立复现”“彻底解决”。无法在许可文件中真实接生产即如实列缺口停止，不交镜像测试充数。
4. 原锁tools/gradle_locked.py、JDK17/SDK/Gradle home，真实受影响JVM定向测试；需要才compile/assemble宿主和androidTest。保留实际rc/log/XML，复用未变充分证据，不全suite、不重复旧D审计/206恢复。Android专属红绿未运行则明确NOT_RUN。
5. 最终candidate/after/patch一致，标准最小patch与真实正反apply在自有scratch；REPORT/ready/pins必须最终字节一致，不自引用。修正r1报告旧SHA的做法是在r2勘误中引用原件，不覆盖r1原证。新APK实核SHA/size、证书05249完整指纹、runner CraIsolatedTestRunner/target com.creationreadingassistant及目标类方法。先查现有唯一保全，不默认重复复制APK。

交付recheck-r2/REPORT.md、ready.json、必要before/after/patch、原rc/log/XML及APK路径pins。source/runtime/report/ready≤256KiB，普通raw≤256KiB；不复制build/.gradle/缓存/依赖，构建复用同一候选。40分钟从实际收到起算，未知不得伪造；预算到交部分成果与具体阻塞，不随机重跑。记录新增实际空间和保全依据在同一REPORT。报告必须区分完成、未完成、下一步及device NOT_RUN。完成后停，等Root核验，不自行设备重试。用户stop/Esc、输入/所有权冲突或权限边界即停止对应操作。
最终面向用户总结的最下面独立一行：这是任务207-r2。
````
