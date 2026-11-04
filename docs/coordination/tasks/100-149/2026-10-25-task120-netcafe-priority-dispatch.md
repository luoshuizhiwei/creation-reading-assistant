# 120-v1 五小时窗口核心研发派发

时间2026-10-25；模型网吧官方GLM5.3；用途V0-2核心返回合同定位/最小修复/JVM差量；并行119新仪器owner与120生产/JVM owner互斥，本机ZCode统一核验，Root仅异常。用户明确网吧尚未启动接力，需一次短启动消息。

````text
# 网吧五小时核心研发启动：119入口修复 + 120核心返回合同

用户最新明确“网吧ZCode要担任重要任务，剩不到五小时，希望消耗完额度”。本指令将网吧端从单一仪器返修提升为既有Android V0-2核心研发owner；本机ZCode负责核验和反馈，Root只异常介入。不能为花token制造任务，余额/吞吐未独立核，不承诺5亿用尽。工作到香港2026-10-25 07:00或用户离开/STOP（较早者），最后30分钟收敛交付。原SSH密钥20点到期不代表可无限工作。

## 先启动119反馈接收（不要只回复计划）
已有便携SFTP路径、私钥、服务器known_hosts沿用：crarelay119@123.60.189.183，只读/download、写/upload，严格主机检查，不root密钥/远端shell。
下载/download/119-auto-start.md（原SHAa7d2e0edf8a0a4c961806b0444312e1fadd6c0e37eb0397dabc75b29527c01e4）及它声明的Wait-119Feedback.ps1并核SHA，执行其中受限传输/ACK规则。其“仅119/Root常规审查”旧角色被本文件更新：本机ZCode为核验反馈主控；当前新增明确120核心开发授权。119-job0001不要重复制造，输入仍d033，实际领任务上传ACK。
119已知resultsDir getter建目录自冲突与取证前删DB先用最小改动闭合并上传独立119差量，目标约20分钟预算而非硬保工期。无法完成给具体问题，不在这里无限重写仪器。119原新仪器/Export-Return全部基线保护与单会话/G1诚实证据约束保持。

## 主任务120-v1：V0-2来源上下文与普通阅读位置可靠性
这是真正核心产品工作，不是泛审计或确认报告。优先核实并修复已实现链中的实际缺口：
A. 阅读器内部发起临时查阅，返回原阅读处且位置不污染；
B. 已离开阅读器到Home后，从全局搜索发起临时查阅，按该真实入口上下文返回，不能凭历史普通阅读记录就擅自认定当前来源仍为阅读器。
必须先区分“历史normalReading持久指针”和“当前active来源/导航生命周期”；不要直接把119预期与现有设计不一致都叫产品bug，也不要把返回普通阅读器本身一律改为搜索。既有嵌套LIFO、失败加载从未提交、迟到请求所有权、原位置冻结合同不能破坏。

1. 最多先用约30分钟定位必要调用与现有测试，给出当前调用追踪/状态序列及需求依据。G1“必然导航环”此前已撤回，不冒充设备复现。若合同有实质冲突不能确定，给最小两种解释及影响，由本机ZCode/Root异常裁决；停止依赖修复但继续独立119工作，不编造结论。
2. 对确定违反上述既有合同的状态/导航问题，先补能区分正常设计与缺陷的定向回归用例，再写最小候选修复。未运行测试只能NOT_RUN，不能称红转绿。不得为绕过问题清空全部状态、重启Activity、跳过真实返回或直接写进度造绿；不添加无关新功能。
3. 主生产范围仅必要hunk：
android/app/src/main/java/com/creationreadingassistant/feature/reader/navigation/SourceNavigationContract.kt
android/app/src/main/java/com/creationreadingassistant/ui/navigation/TemporaryReadingNavigationViewModel.kt
android/app/src/main/java/com/creationreadingassistant/ui/navigation/ReaderTemporaryRoute.kt
android/app/src/main/java/com/creationreadingassistant/ui/navigation/AppNavigation.kt
android/app/src/main/java/com/creationreadingassistant/ui/screen/reader/ReaderRoute.kt
android/app/src/main/java/com/creationreadingassistant/ui/screen/search/SearchScreen.kt
定向JVM范围：既有SourceNavigationContractTest.kt、TemporaryReadingNavigationViewModelTest.kt、ReaderTemporaryReturnLifecycleSequenceTest.kt的必要hunk，或一个新GlobalSearchTemporaryReturnContractTest.kt。尽量更少文件，不因给了清单就全改；新增生产文件/修改公共构建Manifestrunner依赖不在本授权内。
4. 119的work/android与baseline-manifest保持冻结，不能为了120修改导致119打包失效。120只在CRA119/work120/delta下放“改动文件”的对应目录（不复制整工程/缓存），参照只读work/android源码实现最小差量/patch。所有改动注明原文件完整SHA（从已核baseline-manifest读取）、后SHA/实际字节。本机核验端按该固定基线接回，不能直接覆盖当前电脑工程。
5. 120交付return-120-时间戳.zip，含最小patch、仅改动/新增源码、REPORT.md、ready.json、return-manifest.json；manifest task=120-v1、baselineManifestSha256=e6df71df1c1d003d5b0f89616f40a7125581684e15892232f2c47c475493069d、每件path/bytes/sha256及修改前SHA。差量≤4MiB，必要原始证据≤8MiB，不全工程/APK/cache。SFTP上传/upload后另发done-120-时间戳.json含ZIP路径/大小/完整SHA，等待本机核验反馈。不要把120包交给只认119的旧Export脚本或改旧清单；本机ZCode会为120按本文件白名单接收。

## 并行与时间使用
官方Start Plan GLM5.3处理核心实现/关键审查。若本机ZCode实际支持原生子任务并行，安排两个真正独立owner：120核心生产/JVM差量、119独占新仪器/恢复取证。二者都不修改只读基线，不共写文件；120不写119仪器，119不写120生产；集成接缝由本机ZCode核验。若没有实际并行能力，同一owner先快速交119再全力120，不假装已派子任务。
每30-45分钟或形成可审查hunk就上传有意义快照，不等五小时结束才一次回传。等待本机反馈时可继续同目标独立用例/实现，不能为了消耗额度重读全工程/重写已验报告/开无限矩阵。收到编译错误由原owner修复并重新上传，闭环不换人。
无需网吧SDK/完整Gradle/ADB/设备。编译、运行、真实模拟器验证均由本机ZCode依模型和数据保护能力完成；没有结果就分层NOT_RUN。关键设备模型不可用时让本机报告异常，不让你操作原电脑设备。
禁止Git暂存提交pushPR发布、正式书库/设备备份/任何Claude/Cowork/DSH及.codex/memories、自动记忆、购买换账号/供应商/系统设置、存量删除移动、服务器功能或新端口。只做原生Android当前目标，不桌面、新服务端或实体手机。

立即先实际ACK并接119-job0001，同时开始120核心调用/测试定位；不要只返回“收到计划”。本机ZCode正常收包与发反馈，用户不搬摘要，Root仅异常兜底。最终分别写119和120实际分层结果，不能把局部完成说全产品通过。

````
