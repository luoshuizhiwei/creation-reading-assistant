# 121-v1 必要APK构建与实际输入绑定

2026-10-25 香港06:48。Root核A host-final-review REPORT/ready：PASS_SOURCE_OFFLINE__NO_BUILD__NO_DEVICE，必要F-H0/H1/H2已闭合。F1源码与Kotlin编译已接受，运行时未证。继续目标是将119r3/120/F1实际打入APK，再准备唯一设备批。当前工作依赖B唯一共享构建owner；A审查已收口，无独立新目标，不造重复任务。

## B 国内WorkBuddy DeepSeek：构建与绑定，同owner闭环

````text
接续121-v1 B。Root已读A lane-a/host-final-review/REPORT.md与ready.json，必要host问题已闭合。现明确授权你作为唯一build owner，在唯一既有工程E:/creation-reading-assistant/task59-common-candidate-v1/candidate/android执行一次必要:app:assembleDebug :app:assembleDebugAndroidTest，生成包含120/F1与119r3的新产品/test APK。该授权仅离线构建与输入绑定，不授权ADB、安装、设备探测、host主入口、attempt。你不是独自工作，保持其他owner资产，不改A文件或D/current。

执行前核当前输入未漂移：host121.py 28167B SHA7ccf53867039d2ff13674dd12d96ea65e13ee8b897e6fdcd01ce8b2b970d39bc；AppNavigation34985B SHA7a6046a50550dad54a1a85f2cbf097b009fafb79d8169b889de1d27fb46f9fd9；SourceNavigationContract c6c5aebc9eb6db05ac11a65685de089f82a5d0fa86b3d1ffed96eb72227401ee；VM e4af886a393f4596bceadcaca80302faab4d09c92f6905fefedaa7ab0e4949b5；119r3仪器3db974d44042504e19b29d9d67c69eb9900c67378c59e9b74b3c17a5d294532c。未知漂移先报冲突，不覆盖。A终核报告8525B SHA2c0452ffa395f24ce6c10c12bc8b47520eccdf956030f743314ca31c96b0f42a。已充分24JVM与F1 Kotlin原始证据直接复用，不另重跑。

构建环境固定Gradlehome D:/develop/env/gradle、JDK D:/develop/Java/jdk-17.0.14、SDK D:/develop/Android/Sdk，锁E:/creation-reading-assistant/locks/android-gradle-exclusive.lock。查实际活跃owner，不把文件存在等同占锁；不抢活跃构建，不杀进程。构建前核E可用空间/预计增量。旧产品6a6c65af30f8f7744e1ac666275ccb2b92a26a818a2b79cf84266cf38ce50ae4和test83d0e8bb2047999605a40a200e61978587a22f04f996098a8a930e996ed8aee5已有110/preserved、116/preserved同SHA单份保全，核同后复用不重复复制。若现APK另有未知SHA且无同SHA保全，先保全必要单份，不能覆盖未知WIP。禁止复制工程/build/.gradle/.kotlin/node_modules/Gradlehome/cache、历史证据/APK对，不硬链接可写缓存、不删移压迁存量。

原始构建stdout/stderr/真实exit写lane-b/apk-build/logs独立新路径；构建失败先保留红日志，明确编译错误可由你在本批已有所有权必要范围最小修复后继续定向验证；若需改产品/仪器语义、公共配置或超当前范围，报告Root不越权。不得仅为绿改断言/门禁。最终实算新APK size/SHA、源码指纹，指出实际task执行/UP-TO-DATE情况；用现有SDK只读工具核必要APK applicationId/test runner/119测试类，别新建通用探针或完整矩阵。旧6a/83d不冒充新源码。

可离线生成lane-b/inputs.json，所有角色必须对应实际执行件；含新APK SHA、三生产+仪器与保护适配器及原恢复APK/archive实际路径/指纹。A低风险R1提示：恢复件依赖真实PRESERVED/CONTRACT钉住，输入也直接引用相同实际恢复路径，不能验另一路同名件。host保持冻结，不为可选R2/R3扩大改动；缺口如实记录。此时不创建attempt、不运行host。runId/budget/deadline若需要等Root设备批具体放行再生成，不能用过期预算误开批。

只交lane-b/apk-build/REPORT.md、ready.json和必要原始日志/元数据（≤2MiB；既有build增量另报，必要APK保全另列），固定前后APK与源码SHA、输入闭包、原始exit及剩余设备门禁。不额外复制新APK到任务根，引用唯一build输出。禁止ADB/安装/设备/attempt、私库含自动记忆/Claude/Cowork/DSH/.codex/memories、Git暂存提交push发布、云/SFTP/账号购买、D产品回灌、正式库桌面HOLD实体手机、存量清理、重启或关闭VM/其他进程。完成停止等待Root核实际APK及完整单批设备放行，不自行开跑。

最后单独一行：这是任务121-v1 B-APK构建。
````
