# 98-v2真实Controller焦点验证

| 编号 | 时间香港 | 当前模型 | 作用 | 依赖/并行 |
|---|---|---|---|---|
| 98-v2集成 | 2026-10-24 01:45 | workbuddy/cn:deepseek-v4.1-flash 1M不变 | 应用已核差量/编译/一次受控仪器 | 独立差量已与96并行完成；同步APK与设备由Claude唯一owner串行 |

````text
继续98-v2集成验证，不等用户再次确认。先读有效AGENTS/current及docs/coordination/tasks/050-099/2026-10-24-task96-v7-task98-v2-root-review.md。Root已核96-v7七artifact/原运行rc0与EPUB单run成功、完整资产和原test/71/820/DB恢复；最小EPUB接受，身份原字段覆盖缺口如实保留，不造96-v8确认式重跑。当前产品6031b84171da640f8f454589208386db42121d0ec4f099186fde2e4bc937121b/53826879B，test08f13a988beb905ae756a54bf1e48814f586001425ec99ea38588f7378624ef8/10567325B已经在96/v7/integration/preserved单份保全。

98 worker已结束，E:/creation-reading-assistant/task98-tts-focus-feedback-v1/v2-delta六artifact大小SHA核同。你为workspace/构建设备唯一owner，同问题实施→必要编译修正→验证→REPORT闭环；Root只写D协调。先fresh before核fingerprints：Controller2048efbdcf36436abd2d43e22bd0f29aff644c06e68dd97a269cf7140fcb00ed，新增ReaderTtsControllerAudioFocusTest.kt原缺席。应用change.patch或必要source hunk，after Controller c005dcf3c61400c62b213943e1d04c61123f332c687834e6ad9ae90121e3955a、仪器d5264b6d0f2041e394cb533246f0922582a98a7ea49475a27945252dd13c6ee4。不覆盖他人WIP；冲突只合并必要hunk。保持四默认兼容外部参数，只让真实Controller驱动play/resume/speak/focus/utterance；不复制门控policy/加通用会话框架，不改ReaderActions/Repository/DAO/97三文件/D2业务。

复用已有mockk-android与AndroidJUnitRunner，不新依赖。必要main+androidTest编译，修本seam/仪器具体错误到闭环。构造签名变了必须同步构建产品+test APK，不能新仪器配旧6031，否则NoSuchMethodError；构建前找6031原字节保全，无则只保全必要单份53826879B；08f13已保全引用不复制。旧f393/110均不复制。只跑com.creationreadingassistant.ui.screen.reader.tts.ReaderTtsControllerAudioFocusTest这8项，不重复48旧测试/161/97的22/96设备矩阵。静态核每项真实生产路径/断言与MockK兼容，不能降为镜像类测试冒绿。

预期受控覆盖：FAILED play/resume拒绝且speak/STATE_PLAYING=0，Ready/Idle及错误可消费；DELAYED拒绝与GAIN不自启；GRANTED真实speakCurrent/onStart；LOSS_TRANSIENT→GAIN真实resume；stop后GAIN不复活；失焦后next/prev/参数调整不能绕过共享speak门；真实Handler排队onStart/onDone失焦或stop后不playing不推进完成。测试mock外部AudioManager/TTS/media，所以只称真实Controller+受控Android边界仪器，不称真实焦点仲裁、实际声音/通知、UI snackbar或实体MIUI。API<26与旧utterance跨新会话身份风险保留，不扩重构。

冻结本批源码/构建配置/APK对/runner class/runId/hostcollect恢复完整绑定，明确T98V2唯一run，最好保留JUnitXML与每项原始instrument输出，不复用96方法/结果目录或runId。主运行前fresh serial/boot/owner/foreground/预算，完整阶段上限+至少1200秒恢复预留。最多一次attempt含入场失败；失败立即停业务完整取证恢复，禁止批内修码重试或换编号循环。若同未知外部前台再阻塞且无新证据，停止设备，不force-stop未知应用不HOME循环。编译失败可同owner定向修；设备主run失败先保全恢复交付，Root决定新证据是否值得下一批。

原设备保护合同完整复用：71私有/820外部、DB/WAL/SHM、原宿主test APK及完整数据用户状态、strict night=no。隔离MuMu1已授权使用/必要启动，未知应用不关闭；全程不关闭重启杀VM/共享ADB，结束实例运行。ADB D:/Application/NetEase/MuMuPlayer/nx_main/adb.exe命令全绑fresh serial。此仪器不建书不改正式库、不发真实通知不申请真实焦点、不调用真实TTS构造，仍不可跳过外层保护恢复。若collect失败/唯一原证据未保全，保留依赖包BLOCKED，独立test/系统安全恢复继续，原业务/取证/清理/恢复错误分开，不吞错静默retry。仅本轮确属资产精确清理，不清存量。

唯一workspace E:/creation-reading-assistant/task59-common-candidate-v1/candidate/android，Gradle D:/develop/env/gradle，JDK D:/develop/Java/jdk-17.0.14，SDK D:/develop/Android/Sdk，锁E:/creation-reading-assistant/locks/android-gradle-exclusive.lock。输出E:/creation-reading-assistant/task98-tts-focus-feedback-v1/v2-integration，源码/日志XML/原证据预算8MiB，必要build预计512MiB，6031必要单份保全另列；先估空间。worker原REPORT/ready保持。最小patch/改动source/前后SHA/输入产物绑定/原日志exitcode/XML/REPORT/ready与完整恢复，列实际字节/保留理由/build增量测量限制。无工程/build/.gradle/.kotlin/缓存/依赖/历史证据/APK对复制，无存量删除移动压缩迁移。

严禁任何Claude/Cowork/Claude-Code/DSH或.codex/memories读取写入（含自动记忆收尾），不建自动化不发外部消息。保持现模型，只有确证quota保全报Root转ZCode，网络错不当额度。无Git暂存提交pushPR发布、D回灌、桌面/正式库/HOLD/云部署/关闭其他进程。分实现/编译/受控仪器/真实音频未验/恢复，不能用局部分母冒充全产品。Root核后继续授权路线，不把这一批完成当项目停止。最终最末单独写：这是任务98-v2集成。
````
