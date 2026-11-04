# 102-v1既有TTS定时停止旅程

| 编号 | 香港时间 | 当前/推荐模型 | 作用 | 并行/依赖与预计耗时 |
|---|---|---|---|---|
| 102-v1 | 2026-10-24 06:52 | workbuddy/cn:deepseek-v4.1-flash 1M不变 | 既有TTS定时停止生产调用及必要窄验收 | 唯一Claude owner，Controller/宿主/同设备相依；先只读10分钟，必要离线或单次设备旅程30分钟业务上限外加至少20分钟恢复预留 |

方向复核：101事务正确性已核，无理由追加Room设备批。96/97/98/99/100充分范围复用，83/84旧FAIL不冒充当前缺陷。现行产品规划第56行明确TTS续读/跨章/定时停止，本批只选定时停止链，先确认既有足够证据，避免确认式重复或音频矩阵；真实音频/实体手机单列。没有第二个有依据、独立且可安全实施目标，不为数量派发。

````text
开始102-v1：既有TTS定时停止后的播放状态、计时生命周期和阅读态收敛。此为已授权路线剩余用户流程核实，不是已确认产品缺陷。先读有效AGENTS/current、docs/coordination/tasks/100-149/2026-10-24-task101-index-deletion-root-review.md及现行workflow-replan、product-roadmap-realignment第56行；已加载未变复用。不扫全部历史。严禁Claude/Cowork/Claude-Code/DSH/.codex/memories任何读取写入，含自动记忆；Root维护D协调/current，你只写E。原模型不变，确证quota才保全报Root；网络/Server error不是额度。不建自动化，不发其他外部消息。

101已接受：DAO两事务门控+Repository四调用点经生成代码/同一Room DB/锁序独立审查无可执行缺陷；原红22中2失败→原四XML90/90全绿，main/产品构建成功。当前唯一产品5049b01f2dcfdce48c0f5b9d450263105b089b6625d10cba1db9b10558a0778d(52763781B)，构建未设备；旧3e2在task101-index-deletion-race-v1/preserved单份保全，不复制。workspace test23a2618e已保全不因新编号重建。98 Controller c005dcf3c61400c62b213943e1d04c61123f332c687834e6ad9ae90121e3955a和8受控设备通过、98r2宿主11离线/Root反例充分，不重跑。96 TXT/最小EPUB、P1数据合同、97搜索和99词典充分范围复用。

先最多10分钟窄核：ReaderTtsBar既有定时停止UI→ReaderTtsEffect/Controller→TtsTimedStopCoordinator及现有定向测试、直接对应已有验收报告。重点确认生产设置/关闭/到期stop路径，真实Controller状态而非孤立policy；暂停/恢复对现有timer语义的约定从代码与UI说明确定，不自行发明行为。既有输入相同、原日志/断言充分就直接复用，不能仅因为缺本编号报告而跑。若已足够闭合，返回精确现有证据及仍未验边界，不造新夹具/设备；若发现具体可达实现缺口，同owner先真实路径行为红→最小修复→受影响验证→报告，不能扩大跨章/音频/格式矩阵。若仅存在设备UI缺证，给一个窄旅程：本批中性测试TXT→生产TTS入口→设置现有最短合法timer→播放/到期→停止状态与可继续阅读。不得缩短产品timer或新增测试开关来宣称真实分钟到期通过。

所有权：只读生产TTS/宿主和直接证据；证实缺陷后独占ReaderTtsController.kt、TtsTimedStopCoordinator.kt、ReaderTtsEffect.kt、ReaderTtsBar.kt及直接相关既有定向测试必要hunk，其他源码需具体seam交Root，保护全部WIP。无缺陷不改产品源码。你是唯一完整build/绑定/设备owner；不碰101/词典/设置/桌面源码，不为泛审查建新框架/依赖。先采用现有测试/runner和冻结输入；必要编译只受影响变体，实际当前APK覆盖前查已有同SHA保全，无则仅保全必要单份，实际设备原宿主/test执行件也须覆盖前保护。不得复制工程/缓存/历史APK对。退出码、原日志XML与实际源码/APK指纹绑定，不能把mock/状态断言称真实系统声音仲裁。

若确需设备，本批最多一次主run/attempt，含入场失败；编号102-v1并绑定实际源码/APK/测试/fixture输入。先核全部阶段上限（业务最多1800秒）+至少1200秒恢复预留能完成，预算不足停依赖业务；不得进场后才发现缺保护能力。只用授权隔离MuMu1，必要可启动；fresh adb devices/serial/boot/owner/前台，所有命令-s。ADB D:/Application/NetEase/MuMuPlayer/nx_main/adb.exe。不关闭重启杀VM/共享ADB，结束运行；仅可关闭已明确授权游戏，未知前台不强停。失败立即停业务，完整取证与恢复，不本批改码再试/不静默retry/不重算attempt；只有新修复证据和恢复闭合才交Root决定新批。

设备保护合同不能放宽：原71私有/820外部、DB/WAL/SHM、原宿主/test APK完整字节及inventory/userstate/设置strict night=no，操作前可核基线，结束原样核恢复。仅本批新建中性测试书，不碰正式库。精确清理仅初始缺席、run/URI绑定、E原字节sizeSHA归属及当前设备字节核同的单资产，删后复核缺席；未知兄弟后代不递归，探测失败不当不存在。collect失败/唯一证据未保全/归属不明保留依赖资产BLOCKED，独立安全恢复继续。业务/取证/清理/恢复错误分开保留，不吞错。沿用98保护恢复合同及原runner有价值部分，禁止新造解析矩阵/工具重构。

唯一workspace E:/creation-reading-assistant/task59-common-candidate-v1/candidate/android；Gradle home D:/develop/env/gradle；JDK D:/develop/Java/jdk-17.0.14；SDK D:/develop/Android/Sdk；锁E:/creation-reading-assistant/locks/android-gradle-exclusive.lock。报告差量E:/creation-reading-assistant/task102-tts-timed-stop-v1，源码/patch/日志XML预算4MiB，若必要设备原始证据预算8MiB，必要build预计256MiB另列，必要保护当前产品52763781B/实际执行件只缺单份才新增。复制前估可用空间，超预算先报原因不删存量。不复制build/.gradle/.kotlin/Gradle home/node_modules/缓存/历史证据，不硬链接可写cache，不删除移动压缩迁移存量。

交付最小patch/变更源码/前后SHA/REPORT/ready及必要原始退出码日志XML/输入清单；同REPORT写实际字节分类保留理由/build增量是否测量，区分实现/离线/受控模拟器/真实UI/音频和恢复。引用充分旧证据原绝对路径不复制，不把旧FAIL改绿。所有权冲突、准入/预算/恢复不明停依赖操作并具体报告；必要恢复优先完成。无Git暂存提交pushPR发布、D产品回灌、正式库/HOLD/云部署/实体手机/私库读写/关闭其他进程。102完成后Root核并继续现行实际目标，不把本批完成当全项目结束。最终最末单独写：这是任务102-v1。
````

