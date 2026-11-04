# AR02：真实门禁接入、断言补齐与共同产物冻结

版本：AG-NEXT-20261102-v2/AR02。日期：2026-11-02（香港时间）。状态：PLANNED，未派发、未实施。接任执行者：Antigravity获用户交办后登记真实owner。卡号不预占全局任务编号；先核state已有同项则接续。

## 共用合同

必须同时遵守[八项队列](2026-11-02-antigravity-next-queue-v2.md)的权限、单owner、资源、交付与停止条款；依据[Root复核](2026-11-02-antigravity-stage-delivery-review.md)，不得把旧生产者自报接受当已关闭本卡。接任Root可裁决常规技术方法，不等待原Codex在线。

## 输入和进入条件

AR01离线接受后实施公共runtime；先读可独立。输入为source_build_manifest_gate.py、prepare_inputs_209_r2.py、run_batch_E_r2.py、当前EP/C/I测试及原编译日志。旧EP/C manifest当前不匹配，I现包0585723c可作只读基线而非最终验收输入。

## 文件所有权

本批manifest模块/prepare/run入口、EP/C/I三个现有androidTest文件、必要最小夹具差量与单一构建输出。生产源码冻结；若实证需生产修复，接任Root另写精确hunk及影响分析后由同owner闭环，登记新产品SHA。

## 实施步骤

1. 强制校验测试package/class、真实方法集合/expectedCount、源码、构建原rc和pin过的日志、APK、spec和实际安装argv；缺构建日志拒绝。class仅字符串声明、旧成功日志套新source不算闭环。由真实构建命令包装保存输入/rc/输出关系，不事后补造历史。
2. prepare/run在任何设备mutation前必须消费门禁；离线跑真实入口，用stub证明错manifest/过时source/错APK/失败构建/错spec均零mutation，正确输入才能进入父入口。
3. EPUB补合法生产locator具体章节/章内或全局偏移的匹配、真实移动新提交及重开同位置；长章夹具避免只证明同章。退出以当前可见shelf/destination/内容消失绑定，不以任意后台shelf-tag存在判断。Root旧BackStack原因仍是假说，记录证据再定性。
4. Profile来源offset和正文marker选择同一目标范围，记录非空合法普通进度并精确比对，禁止0默认值/0.001容差掩盖污染；返回NOTES真实身份及原卡上下文。
5. 灵感正文必须可见且绑定目标书/locator；详情用真实详情独有身份/节点或上下文，排除列表摘要命中。不是把通用章名存在当精确定位。
6. 所有相关测试改动完成后，仅在既有唯一candidate集中一次必要compile/assemble，冻结一个共同test APK及全source manifest，以不同spec运行EP/C/I。历史三包不必重新复制；受影响目标共包是否齐全用实际manifest/测试发现证明。

## 验证和成功标准

现存27项测试可复用并增加本次缺口负例；真实driver消费链通过；定向Kotlin编译/assemble原rc0；共同产品/测试APK签名、runner/target、全source/spec pins一致。EP/C/I的最终manifest在交接时全部可验证，不指向已被覆盖旧包。没有设备时交OFFLINE_READY不写业务PASS。

## 停止、预算和接续

首轮90分钟；门禁接入、测试及构建同owner闭环，不拆成多轮造任务。只为实际变更增验，禁止全量构建/复制工程。成功由接任Root接受共同输入，释放AR03—05；AR06并行只读。

## 本卡交付

E盘报告根：`E:/creation-reading-assistant/task209-navigation-coverage-v1/continuation-20261102/next-v2/ar02/`。目录已有先核owner，不覆盖。交REPORT.md、ready.json、实际source/patch/前后SHA及必要原rc/log/XML；每卡普通新增预算512KiB，必要构建/设备保护原件单列实bytes与保留原因，不因超预算删原证。APK/工程/cache原路径引用，禁止按卡复制整套。

ready最后写并pin真实路径/size/SHA，不自引用；构建、业务、模拟器、恢复分别裁决。收尾核原证后更新current/state.nextContinuationQueue及受影响矩阵，成功直接转下一可执行卡；PARTIAL/NOT_RUN/BLOCKED如实，只有真实范围/权限/不可逆差额才问用户。
