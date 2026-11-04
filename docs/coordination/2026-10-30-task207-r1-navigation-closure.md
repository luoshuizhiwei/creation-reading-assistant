# 207-r1：导航返修、真实栈回归及必要构建合同

状态：2026-10-30分类整理时新ready已出现（5048B），待Root核验；实际派发时间未知。自动化暂停。此文件用于换窗交接，不是新增派发，不重复发或预先宣称通过。

## 输入与责任

原207 owner连续负责导航最小实现、相关测试、candidate集成、必要定向测试/编译和报告。206设备恢复已有限关闭；本批无设备/ADB/VM/恢复许可。

唯一工作区：`E:/creation-reading-assistant/task59-common-candidate-v1/candidate/android`。

- `app/src/main/java/com/creationreadingassistant/ui/screen/reader/ReaderRoute.kt` before SHA `ce0fd7b2996e374dc971063928ee913d12e458cdf1e453fbff8e02e041e7b9e4`。
- `app/src/test/java/com/creationreadingassistant/ui/screen/reader/ReaderTemporaryBackTest.kt` before SHA `aa3de67cdd3c8c9d39c3acd4f7d3f7da93d84629332fdf354f7a6c1a9b9de587`。
- 203设备测试保持 `ReaderInternalTemporaryLifoTest.kt` SHA `1f2895409b6e90d0f0e2f4e4f943d5acf040bb6618365ad754636681db4f02af`，不修改其断言或timeout。
- 允许必要时新增独立androidTest：`app/src/androidTest/java/com/creationreadingassistant/acceptance/ReaderTemporaryNavigationStackTest.kt`。

207-v1外部根：`E:/creation-reading-assistant/task207-reader-exit-stack-repair-v1`，ready SHA `a76fc11c55de783aabac87fb2e73600e94b99b5363775186a049459120aee160`。该版本**未合流**，不是默认可应用补丁。

## 必须解决的证据和行为缺口

205单次测试在普通退出等待书架15s超时；前段LIFO已到S0，重开未执行。UI退出helper的failure为空只证明触摸动作没抛错，不能排除异步保存失败。报告不得把返回栈累积当205唯一已证根因。

核项目实际Navigation版本，使用真实生产函数和实际NavController/NavHost图证明：原入口→普通reader→T1→T2→T1→普通S0→普通退出回真实原入口，废弃reader不再露出；搜索/笔记入口不被硬编码成书架；pending加载失败/取消/迟到结果不破坏原会话及合法返回栈。

不得以自写List栈模型、Mock收到NavOptions或只检查`launchSingleTop/popUpTo`参数代替实际栈行为。`popUpTo(currentDestination.id)`不能直接当作“清空全部reader”证明。最小必要策略优先，不默认沿用双重防护，不硬编码Back次数或顶层route，不绕过保存与await。生产其他文件/公共构建配置/fixture/冻结host不在可写范围；确需扩展则交理由与停止点。

## 编译、产物与报告

原JDK17/SDK/Gradle home与`tools/gradle_locked.py`唯一锁；运行真实受影响的定向JVM，保留真实红→绿日志/XML，不全suite。必要`compileDebugKotlin`、`compileDebugAndroidTestKotlin`、`assembleDebug`、`assembleDebugAndroidTest`同owner闭环。Android专属真实图测试可编译交付，但未执行必须标device NOT_RUN，不能用镜像JVM测试充数。

因产品修改，生成匹配的宿主/测试APK并核实际size/SHA、证书05249完整指纹、CraIsolatedTestRunner/target和目标方法。旧APK先查唯一保全，只缺才留一份；不复制工程/缓存/依赖，不构建完再丢失旧输入。

交付根：`E:/creation-reading-assistant/task207-reader-exit-stack-repair-v1/recheck-r1/`；REPORT、ready、after与candidate同、最小标准patch、before路径/SHA、真实正反apply、原rc/XML/build日志、新APK完整pins无自引用。source/runtime/report/ready≤256KiB、普通raw≤256KiB，必要唯一旧APK单列。

实际收到任务起40分钟，发送时间未知不补造。到预算交实际进度与具体缺口，不随机重跑；实施/必要编译修复/验证归同owner。禁止设备、Git暂存提交pushPR发布、删移资产、云正式库/私库session DB、`.codex/memories`和自动记忆。用户stop/Esc、权限登录、输入或所有权冲突停对应操作。完成后Root验收，不自行设备重跑。最终总结独立末行“这是任务207-r1。”。
