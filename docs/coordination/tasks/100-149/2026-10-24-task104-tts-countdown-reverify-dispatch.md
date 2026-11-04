# 104-v1已修夹具的单例复验

| 编号 | 香港时间 | 模型 | 作用 | 依赖/并行与耗时 |
|---|---|---|---|---|
| 104-v1 | 2026-10-24 07:35 | workbuddy/cn:deepseek-v4.1-flash 1M不变 | 同产品+修后夹具补齐timer单例 | 原Claude唯一设备owner，无并行同设备；业务上限1800秒+至少1200秒恢复预留，无新构建 |

````text
开始104-v1，Root已核103原FAIL、源和产物及恢复，继续原timer一个用例的修后夹具复验。先有效AGENTS/current及docs/coordination/tasks/100-149/2026-10-24-task103-root-review.md，未变复用。你只写E报告/必要原证据，Root D协调/current；原模型不变、明确quota才报Root，网络错误非额度。禁止任何Claude/Cowork/Claude-Code/DSH/.codex/memories读写含自动记忆，不建自动化/外部消息。

102Controller原倒计时红已被103修复，103单run T103V1-20261024-072400前四关键断言通过，但resume因mock缺onStart失败。测试候选已离线补onStart，Root核源码93d011704b6a723385b6fb01e66a04d4b8416e52da866c5ef4d52a19f068939b；生产Controller1546dab420aea639eb06330372ff718a863c4f405f11b46b161283398cb1c961，不改。103恢复Root已核71/820/DB组和原test字节/inventory/userstate同，recoveryErrors=[]；原102/103失败保留。

只使用已保全输入：E:/creation-reading-assistant/task103-tts-countdown-fix-v1/preserved/app-debug-694bf93d-run.apk SHA694bf93dcae49b0c36375856537c6a195c42604b1e843554b1daf061edc52f3b/52763781B；同preserved/app-debug-androidTest-960808b5-candidate.apk SHA960808b52a4bffad1273ddd07538df487d70c85678f669c73b69046b0eb953d6/10567324B。核同后直接引用，不复制/重建/新wrap。执行ReaderTtsControllerAudioFocusTest#timedStopLifecycleOnRealController单例，全部断言包括resumeonStart、release；不扩其他8焦点/协调器/跨章/Edge/真实音频UI，不等待/缩短真实15分钟到期。当前模拟器无真实TTS，结论仅真实Controller/Handler+mock外部边界。

新的104-v1编号/runId、输入指纹、owner、预算独立绑定，一次主run含入场失败。必须fresh adb devices/serial/boot/前台与owner；ADB D:/Application/NetEase/MuMuPlayer/nx_main/adb.exe全命令-s。只授权MuMu1必要启动，可关闭已授权游戏、未知前台不强停；不关闭重启杀VM/共享ADB，结束运行。业务总上限1800秒+至少1200秒恢复预留进场前确认足够，沿用已核103runner保护恢复不重构工具、不解析矩阵。失败立即停业务完整取证恢复，不改源码夹具再试、不静默retry/重算attempt。

原71私有/820外部/DB-WAL-SHM/原宿主及test完整APK数据/inventory/userstate/设置strict night=no合同完整保持，前基线后恢复核同。无fixture需要就不创建；如必要仅本批中性资产，精确清理须初始缺席/runURI/E原字节sizeSHA/当前设备核同单资产再删后缺席，不递归未知后代/兄弟，探测失败非缺席。collect失败/唯一证据未保全/归属不明保留依赖资产BLOCKED，独立安全恢复继续；业务取证清理恢复错误分别记录。

输出E:/creation-reading-assistant/task104-tts-countdown-reverify-v1。差量报告/输入/日志XML1MiB、必要设备原始8MiB，build新增0/APK复制0；现有唯一workspace task59/candidate/android与Gradle D:/develop/env/gradle/JDK17/SDK/锁不变，不执行新构建。报告同一REPORT记录实际字节/保留理由，原生stdout与明确派生XML/ready/恢复齐全，不能test进程rc0掩盖AssertionError。所有权冲突/输入不匹配/预算恢复不明停依赖业务，必要恢复优先。无Git暂存提交pushPR发布、D产品回灌、正式库/HOLD/桌面/云部署/实体手机/存量删除移动压缩迁移/关闭其他进程/私库。Root核104后继续现行实际目标，不把单批完成当项目停止。最终最末单独写：这是任务104-v1。
````

