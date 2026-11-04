# TXT MuMu失败批与精确清单v2：Codex核查

## 任务编号/版本
CRA-TXT-MUMU-PREP-V2-CODEX-REVIEW-v1，2026-10-05。只读核查交付，不执行设备或产品修改。

## 输入指纹
MuMu报告 [原报告](../../../qa/2026-10/2026-10-05-txt-final-mumu-accept-v1.md)；清单 [v2报告](../../dispatches/2026-10/2026-10-05-exact-integration-prep-v2.md)。清单980行实算D当前SHA与TXT-v4目标SHA，零不匹配；类别939/35/5/1，合计980。设备批输入冻结982项，其中两个测试路径仍为androidTest/...，与实际app/src/androidTest/java/...位置不符。

## 所有权与改动
Root只读E证据、APK、源码、清单；仅写本报告/current/TODO/技能日志。未用ADB，未构建，未改D产品/Git。旧外部报告与失败证据保留。

## 环境
Windows/Python本地静态审查；未核当前模拟器在线状态，不复用旧serial作为当前事实。

## 执行与结果
### Standards
范围边界未见产品写入违规；设备前后账与绑定完整性不成立，不能采纳原报告的全面恢复结论。
### Spec
- 原始j1_20/30/40 runner失败，观察断言读到0；j1_10原始runner为OK(1 test)。历史summary记录4PASS/4FAIL，因此任务确实执行并报告失败，不能称产品通过。
- [P1] 测试APK身份不自洽：freeze/app-qahost-androidTest.apk实算5c516b4d33b6984cab0591b2f940df3f74e4473df1c354eea68cc10354c619fd；installed-test-readback.apk实算7e342388bc65020d73c13426d6e21382498b31e10d2614db2412b78283c9a192，恰等于apk-sha256.json中的旧值。宿主冻结/读回实算均3211482225ee58d88b8e99aa6b090f7ab1c40eb5b25992fe2082dd66e3e3bfe8。不能声称最终双APK安装读回匹配。
- [P1] 关键JSON未取回：evidence/raw/j1_10-obs.json、j1_20-obs.json为cat文件不存在错误文本，harvest目录未见观测文件。没有实际S及完整逐步位置/书籍身份链，无法独立归因“生产重进消费链为根因”。报告的数值0来自原始失败断言，但完整因果链尚未证实。
- [P1] 恢复范围不足：开工夜间no，收工yes；本次Root未用ADB，不存在本次Root恢复夜间设置的证据。protected-files-list.txt是43条文件名而不是43条前账MD5；baseline只含DB/WAL/SHM的3条MD5，后账有43条MD5，不能证明43文件前后全量一致。原宿主APK前后SHA匹配；未找到原测试APK后账工件，原包路径不证明字节相同。restore.json的ok/verified只是驱动沙箱卸载范围，不等于完整资产恢复成立。
- [P2] checker原始结果列8项失败，与summary的4项失败不同，并且绑定taskId/源码清单等失败；必须分清运行结果与证据绑定拒绝，不把该绑定差异视作预期通过。末项clamp未运行，MD邻接NOT_PROVEN，恢复等待阶段NOT_RUN。

清单v2准备成果可采纳：980路径目标指纹匹配、已有决策已映射、5待仪器归属/1临时排除清晰。SRC/JVM仍保持原有效力；清单绑定TXT-v4现在须HOLD，不因准备完毕应用D。B6提出回到早期组合态再跑测试的口径应以已验统一候选最终指纹为准：实际输入未变复用，实际改变才重跑。

## 原始证据
- [原始方法失败](<E:/creation-reading-assistant/txt-final-mumu-accept-v1/runs/j1-txtv4-20261005-a/raw/j1_20_reentryFromHome.txt>)
- [运行汇总](<E:/creation-reading-assistant/txt-final-mumu-accept-v1/runs/j1-txtv4-20261005-a/summary.json>)、[checker](<E:/creation-reading-assistant/txt-final-mumu-accept-v1/evidence/raw/j1-check-result.json>)
- [冻结SHA](<E:/creation-reading-assistant/txt-final-mumu-accept-v1/freeze/apk-sha256.json>)、[设备读回测试APK](<E:/creation-reading-assistant/txt-final-mumu-accept-v1/evidence/installed-test-readback.apk>)
- [前账](<E:/creation-reading-assistant/txt-final-mumu-accept-v1/evidence/raw/baseline.txt>)、[收工账](<E:/creation-reading-assistant/txt-final-mumu-accept-v1/evidence/raw/final-reconciliation.txt>)
- [精确清单v2](<E:/creation-reading-assistant/exact-integration-prep-v2/integration-manifest-v2.json>)

## 恢复与副作用
本次未接触设备。外部双沙箱卸载已有驱动证据，但全量前账缺失不能事后补造；夜间差异仍待设备owner核占用后处理，后续新批须先建立完整基线。原43文件名与后MD5账仅用于限定当前已存事实，不记录具体书名。

## 未覆盖项
本批失败因果链和完整恢复未闭合；MD邻接、末项clamp、实体手机/MIUI未验。既有跨候选J2–J5证据4/5=80%保持原限定范围，最终同APK验收未过；全产品分母未核。

## 交付状态与下一步
1/2本轮交付可采纳为准备成果（清单v2）；设备批FAIL且证据/恢复HOLD。第一批可同时启动：唯一设备owner补失败证据绑定/恢复并受控最小复现；第二窗口只读分析生产与仪器的保存→加载→恢复因果链，无设备写入。第二批修复/重新冻结和验收须等两项回报经Root核定，不现在启动。D集成/Git不执行。
规划保持稳定阅读与数据保全主线；不再猜测布局时序追加修复。先区分实际产品状态变化与仪器进程/观测错误，再做根因修复；既有不变证据复用。
