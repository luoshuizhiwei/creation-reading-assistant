# ZCode交付复核与EPUB后续合同

日期：2026-11-02（香港时间）。Root审查编号：ROOT-20261102-209-REVIEW-v1。

状态：**部分成果有限确认；V0整体不接受退出；下一项先做离线门禁和断言修复。** 本轮用户请求“看看ZCode完成得怎么样、符合规划吗、需要调整什么”，授权读取证据、评审和维护交接文档；不构成新设备、恢复、外部代发或自动化启动许可。截图为执行者陈述，不作为独立验收。既有明确许可由接任协调者按原范围核实，不要求原Root一直在线。

## 1. 对照项目规划的结论

依据：[Android交付规划](2026-10-09-workflow-replan.md)、[V0五合同矩阵](2026-10-31-v0-five-contract-matrix.md)、[接续手册](../handoff/continuation-playbook.md)。方向仍符合稳定阅读与轻量取材：复用冻结产品、补外部查阅入口、补EPUB日常阅读，未另起桌面或新功能线。R2基线、搜索与P5成果按已有接受范围复用，不重新整批审计。

但“四阶段全部完成”只可描述四阶段均已处理并交报告，不能描述四项目标全部达标。EPUB完整阅读/续读仍红；MD在共同版本的完整流程仍缺明确结论；C/I仅补到入口与返回的有限证据。完整产品分母未知，不给全产品百分比。

## 2. 本轮实际核对与有限结论

原证公共根：`E:/creation-reading-assistant/task192-reader-internal-lifo-host-offline-v1/attempt-<runId>/evidence/`。
新增报告根：`E:/creation-reading-assistant/task209-navigation-coverage-v1/inventory-repair-r2/`。

| 项目 | 本轮核到的原件 | 可以确认 / 不能推出 |
|---|---|---|
| 灵感I，T165V1-20261101-190927 | 原instrument stdout为OK (1 test)/9.864s，summary ok/businessOk=true，无primary/recoveryErrors；12-recovery-decisions为UNINSTALLED；R2原test恢复回执ok=true | 仪器目标确实PASS、当批恢复记录全绿。当前源码主要要求通用chrome出现及灵感标题重现；不能据此接受正确书籍/精确来源位置/普通进度不污染，也未排除标题命中灵感列表而非详情 |
| Profile C-R2，T165V1-20261101-210503 | OK (1 test)/11.084s，summary ok/businessOk=true、recoveryErrors=[]、UNINSTALLED、R2回执ok=true | 比v11仅负断言有进步，正向页面域到达成立；返回判定允许NOTES或Profile首页，未强制原笔记上下文恢复。通用chrome不是书籍和位置身份；不能写完整回到真实来源页已强验 |
| EPUB，T165V1-20261102-013251 | stdout FAILURES，1 test/1 failure，20.411s；tile click must navigate away from shelf；原summary ok=false、KEPT_BLOCKED，原test R2恢复回执ok=true | 最终业务RED成立。导入直接调用产品ShelfImporter，已通过现存DB检查及tile出现；不是SAF选取完整UI导入。未走到翻章、退出、续读，不能称这些通过 |
| EPUB独立DIAG | raw/diag-ep-reader.xml及diag-ep-click.xml有书名、章正文和进度节点 | 支持某次独立诊断环境能打开并渲染。缺与失败批完全等价的状态/动作闭包，不能证明产品“本身无缺陷”或根因必为测试框架；点击与导航边界根因仍未知 |
| EPUB后续恢复 | recover-E2/attempt-T166V1-20261102-014321中卸载rc0/Success且后查无产品；recover-log.json尾部记录原test 0d18、inventory 8b2、保护rc0/ok=true/diff0/71private/820external | 支持已卸载及这些具体保护事实；恢复owner却引用旧E的140419，且聚合判定不完整，故恢复全合同只有限确认。不改原RED、不重做已成功卸载 |

设备类型统一为**MuMu模拟器**。REPORT标题里的“真机”不应再沿用；本轮没有连接实体手机、模拟器或执行ADB。

## 3. 必须调整的发现（按下一批依赖排序）

### F1：恢复归属与成功聚合必须先修

文件：`E:/creation-reading-assistant/task209-navigation-coverage-v1/inventory-repair-r2/scripts/recover_E2_r2.py`。

- 第24行R_A固定指向旧E的T165V1-20261101-140419；第163—175行又固定currentCreatedTimestamp、installedByRunId和E安装回执并声明OWNERSHIP_OK。最新01:43 recover-log仍引用它，实际待恢复的是EP 013251。产品APK SHA相同不等于安装/数据归属同批。
- 第224—237行verdict主要据productAbsent，overallOk未纳入rec_err、完整result/保护rc与ok/diffs、最终identity等；test APK SHA甚至允许None。由此存在异常或保护失败仍总体绿的路径。不能只补一段报告声称“已核”。
- 修复先离线：显式绑定真实source attempt、真实安装回执/创建时间与当前身份；由既有父恢复接口执行动作。成功聚合应包含本合同所有必要结果；缺回执、query失败、恢复异常、保护失败一律非成功。无新业务设备前先通过针对性负例。
- 现有恢复动作不重复；只能按保留原件追溯/补只读关系，无法追溯就保留缺口。发现新设备动作必要时另按实际权限处理。

### F2：APK/源码与报告链未闭合

本轮从原summary及inputs-bound原件交叉核得以下值，取代报告的手写摘要值：

| 批次 | 实际test APK SHA256 / bytes | 报告中的错误或过时值 |
|---|---|---|
| I 190927 | 4d07a2d9df5b02dddad0255eaf02b1e63d37b0a9697adfd81024b897c0865a0c / 10743197 | 6d827d48 / 10742402 |
| C-R2 210503 | 0d59f6df092410972edf0cbe02ed3be54f9dd3cea398f5b3f2fd0c76f1af28b0 / 10744451 | d89e4933 / 10744373 |
| EP 013251 | 7f4e7f0f54d80c1fbbc2a376a3b91ec1cc15b94093da2d63184be9c6f66d2a0b / 10751112 | af25aca0 / 10750862，正文称最终timing版待核 |

当前盘上test APK实算匹配EP最终7f4e7f0f；产品APK实算仍c396ebe14b1583180c005dabbc51184826d05a0838933203a21adf5bb72b1d31 / 53899310，ReaderRoute各批pin仍5368065b。新报告列值错误不等于设备装错包，但必须改交付索引，不能把任一旧test SHA套到新批。

28role绑定里没有I/C-R2/EP各自实际被改的新测试源。当前源码分析可揭示现存测试缺口，但尚不能单凭它认定历史APK源码逐字同版。需以已有构建rc/log/源码差量/可用dex行信息等建立实际关联；不能补造历史manifest。不必重构冻结28role解析器，可在本批新增受校验的source/build manifest由任务driver真实消费，保留旧闭包。

EP报告承认assemble失败后旧APK继续运行。下一批门应检查Gradle原rc及BUILD结果、源码输入指纹、构建产物指纹与安装绑定一致。mtime仅辅助，SHA变化也不能单独证明本次构建成功（UP-TO-DATE同SHA可合法，失败但旧APK仍在不可合法）。

### F3：EPUB现存测试后半段不足以验收续读

文件：candidate `app/src/androidTest/java/com/creationreadingassistant/acceptance/EpubReaderJourneyProductionTest.kt`。

- Phase 1只断言title正确及content_status != missing，未如REPORT所称强制available/size>0；验收不能超过实际断言。构造依赖并直接调用importFiles，只算管道集成，SAF选择UI另分记。
- Phase 3循环结束只Log `reached`，没有assertTrue(reached)，未翻到第二章仍能继续；翻页事件依然用0/0时序（已修tile时序并未修这里）。不要预判这就是打开失败的根因。
- Phase 4只要求progress行存在，未验证current_location_json可解码、章节/偏移匹配、确由本次真实移动新提交；Phase 5只查chrome，未比对重开恢复到保存的章节/位置，且允许等待正文命中后仍因chrome隐藏失败。
- 必须先补硬断言：第一章正文和bookId身份、第二章真实到达、合法位置提交及非seed来源、普通退出、重开到相同章节/位置。复用208的证据思想，不把TXT的坐标/locator语义直接套到EPUB。无法用现有数据观测时列出最小观测点，不能删除要求使绿。
- 三级点击fallback、固定坐标308/664和多段sleep应从正式验收中移除；单次明确动作配有界条件等待和失败快照。诊断可保留，但与验收分开。

### F4：C/I“强断言全闭合”需要降为有限覆盖

当前C/I把markerVisible仅Log、通用chrome作为主要reader证据，没有在测试中比较源bookId/locator及普通progress前后。C还将返回原NOTES放宽为任意Profile域。I以同名标题证明详情，该标题列表也可出现。

保持原运行PASS事实；将三入口“均有运行覆盖”与“正确正文/定位/进度/返回上下文均验收”分开。下一次受影响验收应：绑定目标书身份；正文marker选取符合当前分页窗口且可证明目标范围；比对普通progress行；返回验证原来源页身份与必要上下文。确需双Back先观测chrome态，不能不加区分多退一层后放宽落点。现有证据不能判定产品Back错误，也不能拿测试放宽作为预期导航合同。

### F5：停止规则和交接状态需要落实

EP的13项包括REFUSED/预检拒绝，不全是13次业务执行；但多次同类点击失败、两轮旧APK运行、晚到第13项才停，已偏离“同根因/同接口两轮调整方法”的规划。应在第二次同边界失败后冻结输入，做能区分假设的诊断，不再变换点击/timeout碰运气。

current仍旧批C待办和旧test值；state.currentTask仍等待R2选择，实际R2早已执行；矩阵又称三入口全闭合且P5仍待排。接任入口必须同步，不能让新agent重做已结束的R2/208/P5。本轮修改现态入口，历史报告保持原件，补充纠错以本文件为准。

## 4. 后续任务卡：209-EP-r1（先离线，未派发）

### 4.1 身份、所有权与权限

- 编号建议209-EP-r1；接任协调者开工前在state检查是否已有同名占用，有则接续原卡，不另建重复任务。
- 单一实施owner负责恢复driver、输入绑定、EP测试必要编译和交付；candidate/Gradle/设备不能出现两个owner。本轮仅写合同；没有给ZCode发送消息，也没有接管现有owner。
- 可写范围（实施需接任用户实际交办）：R2 scripts/recover_E2_r2.py、prepare_inputs_209_r2.py、run_batch_E_r2.py；本卡专属离线测试与manifest；candidate上述EpubReaderJourneyProductionTest.kt。C/I只先源码核对和合同化，不与EP同时共写同一candidate。需要改其他文件先说明具体必要性并由接任协调者收口。
- host136/158/161/165/166/179/184/192及既有collector、旧v13/preserved、R2原归档/合同只读；不改旧门绕校验。生产源码/APK保持冻结，本卡不授权产品修复、D回灌。
- 当前review不授权ADB/VM/新设备恢复、自动化/外部代发、Git、删移/清理资产或记忆写入。未来设备执行者核用户既有同范围授权及唯一owner；不足才问具体差额，不重复索取已有许可。

### 4.2 输入与步骤

1. 读本文件/current及对应3报告、上述3个business scope、EP最终recover scope；以本表实际test SHA建立历史对应，保存最小源码差量，不复制工程/cache/APK套装。
2. 修F1/F2离线门。负例至少包含：错误source runId/安装回执、错boot/归属、rec_err、保护rc非0或diff非空、test查询失败/缺失、旧APK残留且构建失败、source manifest与APK绑定不一致；各负例应在设备mutation前拒绝。冻结父接口通过真实适配边界调用，离线stub不写设备通过。
3. 补F3完整旅程断言及夹具生命周期。导入创建物、appScope工作/文件/DB应计入现有ledger/清理合同；不在测试外删除资产。编译用既有candidate与缓存，原rc真实记录；不为编译复制工程。
4. 根因方向选**b的最小边界追踪优先**：记录同一目标tile的节点/selectionMode、点击分派、onOpenBook/校验、navigate/当前destination和reader内容就绪边界，先用已有日志与失败capture，不臆测需要修改host136。既有日志不足时使用本批测试侧诊断文件或适配层采集允许的tag；冻结host不改。需新增产品观测则先列最小范围并由协调者确认来源，不能在冻结APK上假装生效。
5. **a仅作独立辅助对照**：读源码发现shelf/detail destination仍挂ShelfScreen，不先保证能绕过tile。必须先证实具体用户可达详情动作与入口；若用它验证reader后半段，则tile打开缺口单独保留，不能替代完整书架流程。
6. **c只能接受分记，不能关闭V0-1**：可以交PARTIAL继续安排其他独立工作，不能把导入/诊断打开当完整EPUB续读退出。

### 4.3 验证、资源、停止与交付

- 离线退出：F1负例及正例通过；来源回执闭包可信；新source/build/APK绑定可验；EP硬断言全部存在且编译rc0；报告不超范围。此时交离线READY，不称设备绿。
- 设备批另落fresh身份/单owner/基线/166+R2合同；本卡建议最多一次区分性诊断、定位并改正后一次验收。第一轮无新证据即停止，不形成第二串盲循环。恢复失败先KEEP与真实恢复合同，不自动新业务。
- EPUB验收需导入管道→正确书的第一章→真实翻到第二章→精确合法位置保存→普通退出→重开同位置；所有阶段独立可核。MD先查同输入既有证据，充分则复用，不默认加整批。
- 独立价值的文档/现有MD证据关系梳理可与单owner实现并行；不能为并行数量创建重复审计或启动另一个设备/Gradle owner。
- 新交付放原任务根的`ep-followup-r1/`（开工前核目录是否已有owner），普通文本/patch/source/离线raw合计预算512KiB；APK/必要设备恢复原件按真实bytes另列并优先绝对路径引用。复制前查可用空间，禁止复制完整candidate、依赖/cache、全部历史。
- REPORT写输入前后SHA、实际owner、原构建rc/log、定向负例、每个业务/恢复结论和未覆盖项；ready最后写并pin真实文件，不自引用。保留原红，更新current/state/matrix受影响行。若历史源码关系无法补齐明确记录，不重跑旧业务来粉饰历史。
- 后续普通技术选择由接任协调者承担；若只剩范围、不可逆或外部权限选择才交用户。Root额度不足不构成技术阻塞。

## 5. 本轮执行边界与验收限制

本轮只读报告、原instrument/summary/inputs-bound/恢复原件和当前源码并实算APK；不全量重审旧D或既有R2 tar，不重跑构建/设备。I/C的历史source→APK未独立闭合，恢复全项也未全量重验，因此采用有限确认。独立code-reviewer历史接受不替代本轮新差量核验。本报告和入口文档的写入不等于产品通过。
