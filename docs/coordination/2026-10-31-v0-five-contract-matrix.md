# V0五合同证据矩阵（C6维护件）

> 最新裁决（AG-REVIEW-20261102-v2）：V0仍PARTIAL，当前门禁/manifest/MD关系/取材原证缺口按[新Root复核](2026-11-02-antigravity-stage-delivery-review.md)及[8项任务](2026-11-02-antigravity-next-queue-v2.md)处理。AG01/02原17+10用例通过不关闭新增反例；AG03—05设备NOT_RUN，MD六路径4同2异；V0-5设备/恢复不可N/A。下面历史证据行按原范围保留，不能被生产者OFFLINE_UNIFIED_ACCEPTED扩成退出。

建立：2026-10-31（接任协调者，手册§10.1）。**维护规则**：每新设备批/源码增量后仅更新受影响行；全部结论引用已接受原证绝对路径，不重审历史D、不造新结论、不报全产品PASS；分母未知故无百分比。当前候选版本锚：207-r4源码（ReaderRoute `5368065b…`）+APK对（宿主`c396ebe1…`/53899310B；**测试APK按批分列见V0-5**，当前盘上测试APK `7f4e7f0f…`/10751112B匹配EP最终013251业务RED）。

> 2026-11-02 Root纠偏：[交付复核与后续合同](2026-11-02-zcode-delivery-review-and-epub-next.md)优先。I/C原运行PASS保留；正确书/来源位置/进度不污染/返回原上下文尚未全验。EP原RED保留，DIAG不能证明产品无缺陷。恢复driver旧归属与漏门先离线修复，不恢复自动化或启动设备。

| 合同 | 具体用户流程 | 原证（绝对路径） | 设备类型 | 恢复结论 | 未覆盖项 | 当前结论 |
|---|---|---|---|---|---|---|
| V0-1 日常阅读 | TXT导入/打开/阅读/真实移动保存/退出/重开继续 | `E:/creation-reading-assistant/task208-ordinary-reopen-fresh-device-v1/REPORT.md`（冻结203单方法OK (1 test)，含S0真实落库seed三重排除、真实移动合法保存、一次普通退出书架、重开S0精确恢复）；历史：任务94导出旅程、D限定复用（current已核行）；EPUB导入链（`inventory-repair-r2/REPORT-BATCH-EP.md`：importFiles→解析落库→tile渲染MuMu部分证据） | MuMu instance1（fresh b11c1824，runId T165V1-20261031-162905） | 166恢复UNINSTALLED+四tar逐文件SHA核同+71/820零差异+原test/inventory恢复（208 REPORT §5）；EP最终recover014321卸载与具体保护有回执；旧E归属/聚合漏门使恢复全合同有限确认 | EPUB/MD在新APK的**打开/翻章/精确保存/退出重开步骤**（当前仪器点击未通；DIAG某窗口成功，根因未证，不能定性产品无缺陷）；实体机/MIUI；自然墙钟全竞态 | **205退出超时阻塞已由208解除＋EPUB解析4方法MuMu OK (4 tests)（015813）＋EPUB导入管道MuMu部分证据（批EP Phase 1）**；EPUB打开步骤仪器RED、根因未证，且后半段硬断言不足；MD待核同输入证据 |
| V0-2 查阅返回 | reader内笔记条目「临时查看来源」→逐层LIFO→返回阅读处→普通位置不污染 | 208 REPORT §4＋209设备批（`task209-navigation-coverage-v1/device-batch/REPORT.md`）＋批E（`inventory-repair-r2/REPORT-BATCH-E.md`）＋批I（`inventory-repair-r2/REPORT-BATCH-I.md`）＋批C-R2（`inventory-repair-r2/REPORT-BATCH-C-R2.md`） | MuMu instance1 | A/B/E沿用原接受范围；I/C最终summary全绿；红批recover-E2旧归属/聚合漏门见最新复核，不统称每轮完整恢复已验 | pending失败/取消真实UI（D1裁决：本版不新增产品接缝） | **reader内LIFO链MuMu接受（208）＋图7项MuMu PASS（195041）＋生产UI链MuMu PASS（214445）＋三外部入口均有运行PASS：批C-R2（210503，OK (1 test)/11.084s当批summary全绿：Profile直入口「阅读笔记」→pill→通用reader chrome→Back到Profile域）＋批E搜索入口（151729）＋批I灵感入口（190927）——D3入口运行覆盖成立，但C/I通用chrome、marker仅Log，正确书/位置/普通进度/原来源上下文仍缺证**；pending失败/取消分支=D1裁决维持分记；v11历史按其负断言范围保留 |
| V0-3 数据保全 | 删除撤销/备份恢复/保护数据不覆盖较新数据 | 206有限关闭（`task206-recover205-only-v1/`，current已核行）；208恢复独立核（71private/820external/DB零差异+四tar成员核同）；209 r2源码证据：删除级联软删快照可撤销（`BookRepository.kt:275-276`+BookDeletionCoordinator） | 各批MuMu（208为最新） | 每批166合同恢复全链（最新=208 UNINSTALLED） | N1/G1/Room/WebDAV按受影响范围复用未重审（current边界）；实体机数据链 | **两闸门均已闭合**：test包门=R2合同gen10106-v1（`inventory-repair-r2/REPORT-R2.md`，核验ACCEPTED_WITH_LIMITS）；external_added=8个ui-*.xml经用户授权精确清理（A双核一致→B逐条删rc0→C验缺席，本地原件保留，`inventory-repair-r2/REPORT-R2.md` §7）——历史可核结果保留，新批解锁（经`host_r2_adapter.apply_r2_gate`接新门） |
| V0-4 明确边界 | 原书缺失/不支持状态失败可解释；已删书查阅有防御 | 209 r2源码分析（`task209-navigation-coverage-v1/recheck-r2/REPORT.md`）：已删书灵感门（InspirationRoute.kt:294）/M-BUG-10索引清除（BookRepository.kt:272）/级联软删/OpenBook失败→errorMessage→ABANDON（ReaderViewModel.kt:883）；J1夹具含不可读书（损坏TXT路径存在）；P5设备批（`inventory-repair-r2/REPORT-BATCH-P5.md`） | 源码级+MuMu（P5） | P5批UNINSTALLED全绿（165519） | 会话内还原分支不可自然触发（D1裁决维持不新增接缝）；ABANDON内部状态不可直测（行为证据分记）；灵感入口链；实体机权限/存储失败 | **源码级证据成立＋P5设备级真实失败场景PASS（OK (1 test)，runId T165V1-20261101-165519：真实清缓存→索引留存命中→reader错误态正文不渲染→Back回搜索页+位置不变）**；失败UX具体文案/Snackbar/输入框逐字保留为附加观察缺口 |
| V0-5 同版交付门 | 共同源码版本+宿主/测试APK+受影响MuMu结果+恢复后账 | candidate=207-r4接受源码（ReaderRoute `5368065b…`，宿主APK `c396ebe1…`/53899310B全程未变）；**测试APK分列**：批A/B=e4b7b70b…/10724766B、批C v11=cb3de733…/10726481B、批D=cb3de733…（同C）、盘上过渡版ad6f4c02…/10730015B（现场观察，未跑批）、批E/P5版=01dff554…/10738062B（含P5测试）、批I最终190927=4d07a2d9…/10743197B、批C-R2最终210503=0d59f6df…/10744451B、EP最终013251=7f4e7f0f…/10751112B（以原summary+inputs-bound为准，报告旧简写已纠偏）；这三项测试源码未入28role，source/build关联仍待补；离线增量=runner209/host209+R2链四件套（`inventory-repair-r2/scripts/`，测试侧不入产品APK） | MuMu instance1 | 208恢复后账；209各批+批E/P5独立166/R2恢复（REPORT分列） | 同版增量若有产品改动需新APK对+新批；**不以当前test APK套历史接受结论**（裁决§7.5） | **产品版本统一于207-r4**；各批真实宿主/测试APK/source/spec SHA已分列（批E/P5已补） |

## 阻塞与待决（跨合同）

1. **两闸门均已闭合（2026-11-01）**：test包门=R2合同gen10106-v1（重绑闭包+恢复验证批全绿+核验ACCEPTED_WITH_LIMITS）；external_added=8个ui-*.xml经用户授权精确清理（三段回执，本地原件保留）。**R2基线层面曾解锁**；2026-11-02新发现的recover归属/聚合问题先离线修复——批次入口经`host_r2_adapter.apply_r2_gate(parent)`接新test包门（批末finally还原绑定）。
2. **P5已执行**：165519实际PASS及恢复范围沿用REPORT-BATCH-P5，不重新排同场景。下一项是本复核209-EP-r1离线门禁/断言修复，设备执行另核真实许可。
3. **D1接缝**：已裁决——本版不新增产品接缝，维持现有入口与防御（裁决§5），矩阵D1/D2保持未覆盖分记。
4. 子代理核验通道：general-purpose/Explore/Vision-Inspector类型deepseek-v4-flash路由缺失（多次尝试，滚动累计见state）；code-reviewer/bug-hunter类型可用——r1/r2/r3/C6已经code-reviewer独立核验ACCEPTED，r1/r2/r3以同会话清单核验ACCEPTED_WITH_LIMITS记录于state；恢复后补独立agent核验。

这是C6维护件2026-11-02定向纠偏版（D0取证后受影响行更新）。
