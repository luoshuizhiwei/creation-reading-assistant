# 90-v1 Root核验及解析入口闭环修正

## 任务编号/版本、输入指纹
90-v1-Root，2026-10-22。采纳产品最小修复、已有定向回归/构建及B/C有界设备结果；D因双显示观测拒绝，冷启动及完整EPUB旅程未通过。

Root实核90 binding全部28项SHA匹配，REPORT SHA与ready匹配；实际生产CustomTipPlaceholder.kt由d7f3a538c16b27a0a6037e031efce0bb777f563daad08787098ae6e47852b71a变为c12cdf51bbebe4128cefc92beb689dc1ecd444ac2e95f40dfdf044159ed7bc55，实际7,726B（报告/ready的7,381B有误）。最小patch实际1,181B，SHA bfdf73cd0c90523d127faa6fe6a6168c4cfd6634eb3b12543b88b9ef061cf0b1匹配（表称1,392B有误）。新增测试135658260169ccde7cb086c0b91120ff580b6f53b1f4826c0e0a6b4caaca6447匹配。

当前debug APK：`E:\creation-reading-assistant\task59-common-candidate-v1\candidate\android\app\build\outputs\apk\debug\app-debug.apk`，SHA256 `22f6d8c1778136c81a9a2ed904757d2e73d2ed4d5b0eacacbf74af56dfd227ae`，53,895,731B。旧33a08 APK在`E:\creation-reading-assistant\task90-epub-progress-v1\preserved\app-debug-33a08a05-before.apk`保全，SHA33a08a05a75e482dc73cba00ce5f5eec15ddb070ad5cbe16b41299b5657d86df、52,763,881B，Root实核两APK匹配。

## 所有权与改动、环境
外部owner在唯一E共同工作区改一处生产显示及对应单元，必要构建和设备独占。Root仅核验/集成，与一个只写task90/root-parser-r1的worker并行修仪器，不改产品、旧Flow或旧报告；未发现匹配task90/t90的活动执行进程。无新设备/构建/外部消息。

产品实际patch：source.chapterLengthsAreEstimated为true时ReadProgress使用pageInfo.progressPercent，其他分支保留offset/total。没有改canonical locator、absOffset、持久化、86防回写或TXT/MD显示公式。

报告称启动前实例已停止并launch一次，boot确与89不同；不独立归因先前由谁停止。新boot e4e516fd-4291-4dec-8d21-17cbad195b56，serial127.0.0.1:16416，实例pid30076/headless28088。env-prep-night.json直接记录strict initial前yes→设置no→复核no；最终恢复的是约定严格no，不是即时yes入场状态。允许使用/启动实例的既有范围保持，禁止关闭重启，历史身份不当当前实时状态。

## 执行与结果、原始证据
Root检查红XML10项/5失败、绿XML分别10/8/6项零失败。新增测试调用真实CustomTipPlaceholder.render，含实际LearningProgressEstimator及TXT/MD控制用例，不复制实现自证。绿日志testDebugUnitTest为FROM-CACHE，按匹配输入的缓存测试成果采纳，不称Root或本次最终命令新执行了24项。build-debug.log为BUILD SUCCESSFUL in14s，assembleDebug成功；dex对比仅classes16.dex这个归档成员变化，不等同仅一个Java/Kotlin类改变。

设备run20261022-184752-ef9e1f，18:47:52—18:55:00，入口一次，exit6。Root直接读run-summary、flow、XML、逐bookId DB raw_tail：
- initial/准入/安装、真实EPUB导入及共享分页通过，bookId=epub_-hambcz。
- 第一次页脚35.5%；中间页64.8%，DB64.8309173583984%，B gate通过，产品显示修复有直接设备证据。
- C真实BACK后书架XML已留，DB进度和locator不变。
- D重开同marker13、同locator={chapter:1,offset:0,locator_v2:{v:2,offset:1791,ci:1,co:0}}；同屏有底部64.8%与顶栏EPUB·第二章乙·64%。原Flow要求总候选唯一，故观测None并拒绝，不能认作本次产品口径矛盾。
- E/F未执行，不能宣布冷启动续读/完整EPUB旅程通过；保留原first_error与FAIL。

原件根`E:\creation-reading-assistant\task90-epub-progress-v1`：REPORT/ready、patch两源码原件、logs/red/green-unit-test.log、build-debug.log、env-prep-night.json、evidence/red/green XML、evidence/run-summary.json及run/20261022-184752-ef9e1f下flow、after-exit/reopen-EPUB.xml。

## 恢复与副作用
Root核同run initial/final proof：consumer=true、71私有/820外部比较与capture一致、diffs=[]、DB组匹配、reasons=[]。恢复RECOVERED、blockers=[]、包卸载与精确材料收尾、11设置无差异/night=no；保活device/boot_completed1、boot与准入一致。完整恢复采纳，无新欠账，不当作当前实时设备状态。证明根为`E:\creation-reading-assistant\task83-selfuse-delivery-v1\B\v2\protection-root-r1\outputs\20261022-184752-ef9e1f\{initial,final}`。

## Root解析修正与离线验证
为减少往返，Root在本轮完成真实Flow修正。路径：`E:\creation-reading-assistant\task90-epub-progress-v1\root-parser-r1\candidate\b89_flow.py`。

基线15f24d8bda106f71624b398068d381b4e4a4b811e63773e9040a9517bceb4e21；修后SHA256 `d237a0bbe2049a260c2a6b4f30d6b54b48ffefe2ea0d4197212d47184bf24c47`，22,240B；binding SHA256 `2daa219db6df6541b1b3cd58a3fc81be07592dfffb049bb0507d4cb52e929dbf`。Root实核候选、基线、真实UI/DB依赖、测试、失败XML、红绿日志8项绑定全部匹配。

允许每种已知身份各唯一节点，不把两个合法不同精度显示当歧义；观测中保留全部节点，真实progress_gate逐一对同DB校验：页脚按显示精度ROUND_HALF_UP，顶栏按整数截断。任何一处冲突、同类多个节点、正文伪候选或无节点继续拒绝，不改bookId/marker/locator/位移等门禁。

真实90 reopen XML SHA eb9aa3f3a71cc1538637dcde801b850368145077f1ea0590d5348f6eaa126168，在原真实Flow.run_format复现D红灯；修后9/9通过，覆盖真实双显示、footer冲突、chrome冲突、同类重复、单节点兼容、正文拒绝。原始日志root-parser-r1/logs/red-baseline.log及green-real-flow.log；测试只注入设备/DB外部边界，不代替新设备验收。新增43,718B，无产品/构建/设备动作。

## 交付状态与下一步、未覆盖项
实现修复和有界设备生效采纳；完整EPUB/V0仍待冷启动闭环。下一91-v1直接复用当前22f6 APK和d237 Flow，唯一owner准备最小新launcher/实际闭包绑定并完成一次EPUB旅程及严格恢复，不再派parser返修、产品修复或构建任务。导入与B/C是本run建立新定位基线的必要准备，不把旧90的DB行当新run输入。

独立产品证据核验和仪器修正本轮已并行；剩余同设备与其接线有真实依赖，交同owner闭环，不制造第二泛审查。旧TXT/MD、取材搜索、D/F、限定备份按不受影响范围继续复用；不重跑全量JVM/Lint或矩阵。

## 空间与停止条件
90自报任务根56,040,638B，其中旧APK52,763,881B，非APK3,276,757B；本run保护另2,638,431B，现有build净增1,132,062B。以上范围不同不可把build存量当新交付。Root解析另43,718B；必要资产保留，不复制工程/缓存/APK对或清理存量。

下一91单attempt，入口失败计数，失败保全并完成同attempt恢复，不能现场改后第二次main。身份/owner/输入不明、strict/night或恢复失败均阻塞；不得只挑匹配的一个显示而忽略另一个冲突显示。实例不关闭重启、结束保持运行，两个自动化PAUSED。无D产品回灌、Git/发布、外部消息、正式库、桌面、HOLD/私库或云。全产品分母未知，仅MuMu，非实体手机/MIUI。
