# 32-v3 / 34-v1 / 35-v1核查与精确返修

## 任务编号/版本
2026-10-13 22:03香港时间，Root直接核查32-v3、34-v1、35-v1；下一32-v4、34-v2、35-v2可并行。三份均交付，全部只限定采纳，联合设备门未开。

## 输入指纹
共同source manifest SHA a1863f36fc1d3488955a51ed7e679153d20c8a716c1fef0b74481def6352548a；Root产品原1012/tree410b0768a317678b7e0f0a0fb00fc4b01b9253c4035c139c4a0d4e666e481be3。
32-v3实际源1012全同；34-v1实际1017（原1012全同，只新增5个androidTest）；35-v1实际1013（2旧androidTest改、1新增，main原件全同）。逐文件实核见下。

## 执行与结果
32结构化33-v2身份及证据绑定等改进保留，但安全工具仍需返修。34真实EPUB夹具、3个真实Room保护测试、错误UI准备采纳，原qahost编译日志exit0；EPUB旅程/全行/证据落盘不完整。35按schema12列、run专属证据及内容核验准备采纳；原debug编译exit0，Root实际重跑离线15反例被拒。URI明确BLOCKED，未冒称通过。任何编译/离线结果均非设备证据。

## 原始证据
[逐文件/原编译/真实shell反例](<E:/creation-reading-assistant/tasks32v3-34v1-35v1-codex-review-20261013/verification.json>)；[Root实际35离线输出](<E:/creation-reading-assistant/tasks32v3-34v1-35v1-codex-review-20261013/task35-offline.log>)。35原日志与meta的elapsed/BUILD时间不一致，机器exit0及BUILD SUCCESSFUL本身成立，摘要不混用时间。

## 所有权与改动
Root只写E审计/全局文档，无产品/设备操作。下一精确拆分：
32-v4输出task32-fixed-joint-freeze-mumu-accept-v4：仅主机tools/scripts/config/离线测试，不改Kotlin。
34-v2输出task34-abc-device-contract-prep-v2：仅Task34前缀androidTest，保留B，不改35文件/产品。
35-v2输出task35-clamp-saf-evidence-prep-v2：自有TxtShortClampSupplementTest、RealSystemUiAcceptTest、Task35EvidenceFixtures及必要测试；本轮新增允许仅ReaderNotesSheet.kt一个产品文件的可选导出完成观察回调，默认不启用，不日志化URI、不改生产调用者/导出行为。不改34文件。Root负责检查这个E侧最小接缝及新生产指纹/影响范围；没有D回灌许可。

### 32阻断证据
- 原生成命令n=0在find|sort|while管道while内更新，外层n不变。Root用真实Git Bash执行同一生成命令结构（只替换find/stat/sha为确定性外部夹具，无设备）：一文件输出COUNT=0，实际parser拒绝。原fake只拼了正确协议，没有执行shell，遗漏真实接缝。
- 复制后after_fp仍用原member_by_name的四元组重算，没有重新读取size/mtime/SHA；同名文件内容变化不会被检测，前后相等是自比较。不得签整组不变。
- atomic_write_json使用os.replace，函数本身会覆盖已存在目标；Root自有E反例实证。首次基线exists前检查不等于原子拒绝覆盖，需要无覆盖发布。
- CMDLOG仍截4000字符，报告“完整输出”不成立；基线仅私有inventory不能代表external/sdcard/Download全账，须按实际触碰范围登记，而非文字声明。

### 34阻断证据
- 普通阅读器打开后直接openTemporaryInspection等待“我的”，没有退出阅读器回到主导航动作；辅助函数只有等待/点击，未建立合法起点。
- 临时查阅测试停在跨章后四字段比较，缺返回普通阅读与继续位移保存，尽管KDoc声称包含。
- 普通baseline直接读取预置/正在变动的行，未证明初次保存稳定；A仅比4字段，C仅比5字段，不是ReadingProgressEntity完整行保护。
- contract-methods列出证据字段，但测试没有落盘这些证据；最终driver不可仅凭JUnit绿断言整行/时点/模式。
- Room B为真实DAO三合同，保留不重做；测试内文案“inMemory实际落盘”应收窄。

### 35限定采纳及下一最小接缝
URI不可观测原因实读ReaderNotesSheet.kt确认：真实CreateDocument回调局部uri写成功后未外露。下一给NotesSheet末尾加默认null可选onExportCompleted(Uri)观察回调，仅成功关闭流后调用，失败/取消不调用；测试真实系统SAF传入观察器，记录实际uri→resolver冷读字节→SHA/runtoken，不受控registry、不猜URI。正常调用者无需修改，不记录正式数据。
Kotlin gate当前仅写JSON，必须由测试或实际consumer执行强拒绝；离线checker允许uri=BLOCKED作为“如实报告”通过，但不能据此把完整SAF验收计PASS。35-v2要区分证据合法和合同完成。
时序报告已声明HZ=100假设；需从真实系统频率取证，不能把未校准值当精确证明。reader dump由driver提供，必须绑定实际时点/run/PID，不能用陈旧dump充身份。

## 环境
Windows/PowerShell。Root真实POSIX shell实验仅主机夹具，未运行ADB/Gradle；已有编译日志按变体限定（34=qahost，35=debug）。E证据保存，无子代理、应用发消息、网络/订阅修改。
建议32-v4 GLM5.3/已配置DeepSeek Pro；34-v2 DeepSeek Flash/GLM5.3；35-v2 GLM5.3/已配置DeepSeek Pro。实际菜单为准，不外派GPT/Claude。DeepSeek参考32 30–50min，34 35–60min，35 45–75min，排队另计。

## 未覆盖项
三线设备均NOT_RUN，URI未验，D/F NOT_PROVEN；无实体手机/MIUI/LAN/X整机中断/R8/正式签名升级/发布新结论。全产品分母未知。33-v2原8JVM不重做；35未来若改产品接缝，Root重核输入和受影响导出合同，不把原源码整树身份沿用。

## 恢复与副作用
无设备/数据恢复操作，无D产品/Git操作。HEAD e39a78da9c62f02614207cffb36969a32492eaea、暂存空。保护正式书库/原损坏仓库/桌面HOLD/Claude与DSH私库。各v1/v3原件保留只读，修复在新E版本。

## 交付状态与下一步
保持原已核准备，32-v4/34-v2/35-v2精确返修待转发；三条文件所有权互斥，可立即并行。设备/最终APK仍等待Root核验与唯一owner合流新冻结，不直接运行旧停点。最终用户总结末行分别“这是任务32-v4。”、“这是任务34-v2。”、“这是任务35-v2。”。
