# Antigravity九卡交付Root复核

版本：AG-REVIEW-20261102-v2。日期：2026-11-02（香港时间）。依据：用户要求核查是否按项目文档执行，并编制后续任务。仅评审、离线验证和文档维护；本轮没有ADB/VM/设备/恢复、Android构建、外部代发或自动化操作。

## 1. 裁决

**方向和部分实现符合队列；不接受“9项业务目标全部完成”“无离线技术阻塞”“仅剩三个设备批”的总结。** 9项均有报告不等于9项目标全部闭合。保留已交付代码、构建日志和原测试通过事实；新增原证指出AG01/02门禁未闭合、AG03—05断言及产物冻结仍有缺口、AG06复用前提不实、AG07/09仅静态分析不足以接受完整合同。V0仍PARTIAL，不认OFFLINE_UNIFIED_ACCEPTED等措辞为整体退出。

原队列：[9卡合同](2026-11-02-antigravity-continuation-queue.md)。新排期：[8项后续队列](2026-11-02-antigravity-next-queue-v2.md)。旧报告只读保留，以本补充纠正接受范围。

## 2. 本轮实际取证

- 原交付根：`E:/creation-reading-assistant/task209-navigation-coverage-v1/continuation-20261102/`，9份REPORT/ready、3份新测试manifest、对应compile/assemble日志、现存源码和runtime脚本。
- 实算当前产品APK仍`c396ebe14b1583180c005dabbc51184826d05a0838933203a21adf5bb72b1d31`/53899310B；当前共同test APK为`0585723c58a1523b30238e85242f28ea722ebcd1505a12d93a0fca98ec28f39b`/10751701B。没有重新构建或设备运行。
- 以unittest loader运行原AG01/02测试，避免原脚本main覆盖其日志；本轮原日志独立存放，结果17/17、10/10通过。该事实仅支持已写用例，不证明遗漏路径正确。
- 追加纯离线检查，真实调用交付函数：父恢复None及空dict获True；错误sourceRunId/serial/boot/SHA获OWNERSHIP_OK；错误class/expectedCount=999/targetMethod=None/缺buildLog获manifest有效。未调用真实设备入口。
- 当前EP/C/I三manifest通过原校验器：EP失败、C失败（都指向可变candidate test APK路径，已变成I包）；I通过。构建日志记录历史成功保留，不据此否认历史曾产出EP/C包；但目前不能称有三个独立冻结待跑包。
- MD六路径逐字比较：MarkdownDocument、Markdown渲染分支及两测试共4个相同；ReaderProgressActions和ReaderProgressEffects共2个有实质差异。旧Root MD原报告本身明确为独立候选限定验收，不是当前c396共同版本验收。
- 本轮证据根：`E:/creation-reading-assistant/coordination/antigravity-stage-review-20261102/`，含offline-findings.json和AG01/02复验日志；新增指纹/比对证据及修改前快照同根。

## 3. 按卡接受范围

| 卡 | 保留成果 | Root当前判定 / 必要缺口 |
|---|---|---|
| AG01 | 去除旧E固定路径、引入聚合函数；17项测试通过 | PARTIAL_REQUIRES_REPAIR：归属只检查文件存在、mtime造createdTimestamp；父结果缺失仍成功、缺字段默认绿、已缺席提前overallOk=true，真实rc/身份链未闭合 |
| AG02 | 独立manifest模块、10项函数测试、历史APK纠错索引 | PARTIAL_NOT_INTEGRATED：prepare/run未调用门；class/计数/构建日志缺失可绕；原rc为调用方自填且log未pin；没有安装argv/实际读回消费闭包 |
| AG03 | 编译/assemble日志、去三级点击、第一章/第二章及重开章断言加强 | SOURCE_BUILD_ONLY：设备NOT_RUN，manifest现已失效；BackStack保留不等于非当前semantics必在场，根因仍是假说；退出又用shelf tag存在作为判据；JSON非空及重开第二章不等于精确位置保存/恢复 |
| AG04 | 增正文可见、普通进度比对、原笔记标题返回检查 | SOURCE_BUILD_ONLY：设备NOT_RUN，manifest失效；百分比比较用0.001容差且nullable默认0，返回未绑定页身份/上下文；noteOffset是章首，marker1是否属于目标分页范围未证明 |
| AG05 | 增防污染比对、详情正文存在检查 | SOURCE_BUILD_ONLY：设备NOT_RUN；来源正文允许通用“第一章 序章”，fetch存在不等于屏幕可见；详情正文是否列表也渲染需定点证明，不能仅靠报告声称排除 |
| AG06 | 正确找到MD旧Root限定验收、原JVM/设备证据可引用 | REUSE_RELATION_PENDING：六路径全一致声明不成立（4同2异），旧host b43f与当前c396不同；须做差异影响关系或最小定向增验，不能直接REUSED_AND_CLOSED |
| AG07 | 本轮主要为工具/测试改动、无生产Schema变更的范围分析 | IMPACT_ANALYSIS_ONLY：恢复工具是主动设备修改入口，不能称纯被动无风险；未具体引用F/N1/G1等原证及输入关系，不接受“零风险/V0-3全面闭合” |
| AG08 | 明示EP/C/I设备待跑、整理版本摘要 | V0_PARTIAL：V0-5合同含共同版本、受影响MuMu与恢复后账，不能标设备N/A；门禁未接入/manifest失效/MD关系未明均是实际技术缺口 |
| AG09 | 现有手机取材代码路径梳理 | STATIC_CAPABILITY_MAP：没有冷启动持久性/原书缺失仍可读/实际复制或导出内容的原证；不能用源码存在接受完整B线，也不扩成桌面或新增写作产品 |

## 4. 必修问题的可复现边界

### R1 恢复安全边界

`inventory-repair-r2/scripts/recover_E2_r2.py`：build_ownership未解析回执rc/stdout/实际argv，未与source attempt身份/实际安装时刻关系核对；receipt mtime不是currentCreatedTimestamp来源。当前随意传wrong run、wrong serial/boot/SHA仍返回OWNERSHIP_OK。

evaluate_recovery_overall_ok(None,None,valid_end)和空父dict均返回(True,[])。主流程产品缺席直接overallOk=true，绕过其余合同。CLI serial默认旧16416，而fresh resolver结果未与实际用serial严格绑定；部分pm/path/inventory命令rc未进入判定。最后identity采样早于保护查询，不满足约定的保护后最终identity。下一卡应对真实边界做负例，不能只复制现有布尔函数再自证。

### R2 构建门实际消费与冻结

检索同目录prepare_inputs_209_r2.py/run_batch_E_r2.py未见新gate调用；不能以单独CLI可调用推成运行前强制拦截。verify函数不检查testClass；expectedCount校验有条件且不严格；buildLogPath为空会跳过校验。installBinding只是manifest两个声明互比，没有核实际host argv和安装物。即使函数测试10/10，仍未关闭原“构建失败旧包继续跑”的完整执行路径。

三个manifest都指向同一个可变test APK路径，EP/C两份已被后续I构建覆盖。优先从最终共同candidate仅做一次必要构建，统一EP/C/I源清单和新manifest后按类/方法分别运行；不默认复制三套APK/工程。若必须保留多个二进制，先证明确有字节差异及验收需要，遵守最小存储合同。

### R3 业务断言和根因措辞

源码增添硬断言是进步，但不能把“存在非空JSON”“回到第二章”“通用章名存在”当精确目标位置/进度合同。双Back必须基于可见前台/chrome和真实destination，不能用后台节点数或缺失来猜。只有新证据区分实际导航失败和观测假阴性，才能宣布EP原因已查明。继承原严标准，不因分页观察困难删除定位要求。

### R4 交接准确性

state.currentTask仍写未派发九卡；continuationQueue却STAGE_CONCLUDED；current旧APK表仍7f4e。九张卡的readySHA实际填的是reportSha256，不是ready文件SHA。本轮纠正当前摘要和机器字段，不覆盖原producer报告/原ready。更新ready后应重算新readySHA，processed不代表接受。

## 5. 后续范围

下一批共8项：AR01恢复边界修复；AR02真实门禁接入/断言补齐/共同构建冻结；AR03 EPUB验收；AR04 Profile验收；AR05灵感验收；AR06 MD及数据关系；AR07手机取材实际闭环；AR08 V0合流裁决。AR01/02是设备前真实技术前置；AR06只读可独立先行；AR07先复用旧原证再补缺。不得把本轮评审解释成设备或正式数据授权，不重复208/R2/P5/已成功卸载。
