# 96-v2 D2安全收尾与剩余真实旅程

| 编号 | 时间（香港） | 推荐/当前模型 | 作用 | 并行与等待 |
|---|---|---|---|---|
| 96-v2 | 2026-10-23 20:40 | 保持workbuddy/cn:deepseek-v4.1-flash 1M | 修仪器抢先清理与定位接缝，集中剩余D2闭环 | Claude独占仪器/宿主/必要test构建/设备；Root只读S1入口准备并行，设备等安全修正与绑定 |

````text
接续创作阅读助手96-v2，实际推进至交付。先读有效AGENTS、current页首、docs/coordination/tasks/050-099/2026-10-23-task96-v1-root-review.md；复用你刚交付96-v1上下文，不重读全历史，不读写任何Claude/Cowork/Claude-Code/DSH或.codex/memories（包括自动记忆收尾）。你只写授权E差量和唯一工作区所属仪器/宿主，D由Root维护。当前模型保持；只有确证quota/usage limit才报告保全转交，网络错误不算额度。

Root已核：7份outputs匹配、161项JVM XML全通过、原test与最终71/820/DB组/设置恢复记录一致；业务40.376s/1 failure仍保留，D2同内容重关联/异内容拒绝/取消/恢复可读未执行。P1不重跑、不称设备通过。产品1102346485a9e47b7b12885310f678e56b5c86d36082834fbec8732906ffc30c未变。候选test8775434dcea12149002c97f7af7b876cb443a19e4123f060cdab4e27bc0758bc(10557355B)、候选仪器b4993622f913df927b82b2a5f9762517a5e7c10611481d443c253929e61f2e0c。运行22ab原APK已保全；65d1运行源码已覆盖未见独立原字节，不补造。已冻结输入中20项现匹配、两项候选替换要如实绑定，不能报22项全同。报告构建是两轮test组装，非一轮。

必须先修确证安全缺口，再允许主run：D2RelinkUserJourneyAcceptTest tearDown对downloadFiles无条件rm -f，且runCatching仅记结果；该删除发生在host collect之前，未有E原字节保全和删除前当前size/SHA复核，host事后already-absent不能补证明。移除仪器抢先删除，复用当前宿主cleanup_download_fixtures：collect成功→E原字节size/SHA核同→本轮initial缺席/run精确路径归属→当前设备size/SHA核同→单资产删除→缺席复核。探测失败不算缺席；不同字节/缺E/未知归属不删，不递归未知兄弟后代；不吞删失败。采集失败或唯一证据未保全保留依赖产品资产并BLOCKED，独立原test/设置恢复继续。只对这个实际入口做针对性红绿：正常单夹具、当前字节不同、缺E、探测失败/删除失败不得被当成功；复用既有恢复模拟，不建通用框架、不重跑无关测试。

同时核搜索候选真实动作：按已取证DocumentsUI入口查精确文件名，在结果列表中绑定唯一文件行而非搜索输入框自己的同名文本；记录搜索前/后树和动作结果，取证放在失败断言之前。当前searchAndSelectFile的nodes.any(text==fileName)可能先命中输入框，先核节点身份实际可行，不凭猜测修产品，也不加sleep/盲重试。可作必要最小仪器修正，一次必要test构建；覆盖当前唯一8775前查是否已有同SHA保全件，没有只保全单份，原22ab/57477引用不复制。修后候选与运行件分别绑定，不直接运行旧8775。无产品改动不重建产品，复用161项与94通道/恢复充分证据。

安全与定位前置闭合后，同owner新绑定96-v2最多一个主run/attempt（含入场失败）：自己的中性TXT真实导入→移除正文保资产→实际重新定位同内容恢复同bookId/原位置标注source并可读→异内容拒绝且记录资产不变→取消不破坏。复用现有旅程集中剩余流程，不把重复导入当重关联，不现场修码再试。同一选择器阻塞无新证据停止设备新尝试，完整取证恢复；不能只改编号循环。EPUB UI仍待覆盖如实保留，不扩大本次成矩阵。

当前唯一工作区E:/creation-reading-assistant/task59-common-candidate-v1/candidate/android；Gradle home D:/develop/env/gradle，JDK D:/develop/Java/jdk-17.0.14，SDK D:/develop/Android/Sdk，锁E:/creation-reading-assistant/locks/android-gradle-exclusive.lock。先低成本核owner/锁/进程，Root有独立只读S1梳理，不碰你文件。只剩本仪器/宿主安全修正和同设备依赖，当前块串行有必要；不假称多lane并行。输出接续task96-library-settings-closure-v1/v2，差量8MiB、必要既有build增量256MiB，唯一候选APK保全约10.6MiB单列。不复制工程/build/.gradle/.kotlin/Gradle home/旧证据/APK对，不删除移动压缩迁移存量。报告最终实际字节及build增量测量条件，不把未测写成达标。

设备已有授权：可用/必要启动隔离MuMu1，可关闭授权游戏；不关闭重启杀VM、不操作共享ADB，结束运行。ADB D:/Application/NetEase/MuMuPlayer/nx_main/adb.exe；fresh核serial/boot/owner/前台，所有命令绑serial；完整阶段上限+1200秒恢复预留。原71私有/820外部、DB/WAL/SHM、原宿主及test代码完整数据/用户状态、strict night=no合同不降级。仅本批新建资产可测试移除，不接触正式书库。业务/取证/清理/恢复错误分开保留，必要恢复先完成再交付。

交付最小patch/改动源码/前后SHA/真实输入输出绑定/针对性红绿原日志/XML/REPORT/ready。旧v1 FAIL与报告不改写；v1报告指向不存在integration/inputs.json的文字在v2说明真实root inputs.json，不需另造勘误包。REPORT区分实现、离线、构建、模拟器、整链、恢复；MuMu不是实体MIUI，全产品分母未知。无Git暂存提交pushPR发布、D产品回灌、桌面/正式库/HOLD/云/私库/存量清理、关闭其他进程。你不建自动化不向其他应用发消息。Root核后继续已授权Android路线，不以本批结束停止项目。

最后单独写：这是任务96-v2。
````
