# 127-v1 离线准备最小真实旅程验收

````text
任务127-v1，2026-10-26香港，ZCode桌面GLM-5.3-Flash。作用：把已冻结但运行时未证的搜索命中→临时阅读→Back旅程整理成可直接审阅的下一批执行文档。与126-v3并行：126唯一设备owner，本任务完全离线，不ADB/VM/build/安装/host主入口/运行测试。用户明确允许多开窗口，额度9点到期，目标有效交付而非耗费重复验证。Root集成/决定下一派发。你不是独自工作，不改公共源码或Root文档。

先核本文SHA。只读活动current页首及本任务直接引用输入，不扫历史/全源码。唯一工程E:/creation-reading-assistant/task59-common-candidate-v1/candidate/android。已充分编译/JVM/审核不重复，production APK74d106b6、本次testAPK a646c8de已冻结。仪器app/src/androidTest/java/com/creationreadingassistant/acceptance/SearchContentHitJourneyDeviceTest.kt 75994B SHA0557506f1730a669257d4982b806dabc366a3bf0ab1210a4028a4e5758b1d05b；host E:/creation-reading-assistant/task124-instrument-coordinate-cleanup-v1/lane-b/scripts/host124.py 40753B SHAbce4a930c516b21193f5d80163dc296fe4354354ac127d59c961f2592317d519；同lane-b/inputs.json 2861B SHAffc778e0198b3c5121fb1de746e76f6e2c3faaa4e5b76ded5d3f90a5128c76a8。若具体inputs路径不对按同任务报告直接定位，勿全盘查找。原test0d18d788/10508820B、完整数据inventory d6ac3c5f、protected baseline9f60e52c、settings e8fa75bf合同引用原件不copy。

事实：123失败源于chapter-local3991需加chapterStart15=global4006，以及pre-delete empty-check问题；124已修编译与定向充分，实际新旅程未跑。不得修改断言/通过标准、放宽失败。126-v2发现产品包及数据三根已缺席，用户刚确认是本人卸载并改night yes，当前有用户游戏/MAA，不能默认独占设备或切前台/恢复night许可。126-v3正在只读补保护，不授权新运行。

交付：
1. draft-next-dispatch.md，完整可复制四反引号任务正文，明确下一编号建议、唯一owner、冻结输入/路径/SHA、依赖126-v3实际保护结果、开始前用户游戏/独占和night基线决定；精确一次host调用的现成参数/绑定方式与attempt上限、超时与停止预算。仅“建议/待Root放行”，不得执行。
2. contract-check.md，仅核host124实际CLI/inputs角色/仪器坐标修复与清理步骤对这次单旅程是否自洽；列出真实阻断（文件/行/具体反例）或明确未发现新阻断。不重做已充分源审。要具体回答night yes能否由本轮新基线支持，若host硬编码no会怎样，是否必须新输入绑定；不修改源码/host/inputs。
3. 成功/失败与恢复证据清单：真实search命中/tempreader/back/query-tab-list/原阅读位置保持与12列最终行、收集原始状态/返回码、失败主因和cleanup次因分离；产品/原test/71/820/179/设置恢复必须由未来设备owner闭环。滚动NOT_COVERED，模拟器不冒充实体/全产品完成。不能把rc0当instrumentation成功。

输出E:/creation-reading-assistant/task127-offline-next-journey-v1/REPORT.md、ready.json、上述两文件与最小必要摘录。预算全部<=1MiB，不copy工程/APK/cache/历史原件，不新增依赖/工具/后台服务。整体20分钟有界，具体缺口不能查明就精确UNKNOWN+所需原件，不无限查。ready最后写，模型/provider/本文SHA/实际读取输入SHA、已完成/真实阻断/下一建议。Root D/current由Root写。

禁止设备访问或业务执行、构建/JVM全量重跑、源码/host改动、CLI派发/登录、内部API/私有会话DB、Claude/Cowork/DSH/.codex/memories含自动记忆；禁ZCode自动记忆收尾/Git暂存提交pushPR发布/云SSH/SFTP/购买换号/正式书库/既有资产删移压迁。充分输入复用，不为额度制造任务。最后单独一行：这是任务127-v1。
````