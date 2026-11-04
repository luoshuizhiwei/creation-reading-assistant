# Antigravity下一批八项任务

版本：AG-NEXT-20261102-v2。日期：2026-11-02（香港时间）。状态：8项PLANNED，未派发/实施。用户本轮要求核交付、更新文档并给提示词，本窗口只完成评审/离线验证/文档，不操作设备或外部应用。

## 0. 2026-11-02权限纠正（优先）

用户已同意MuMu验收；Antigravity直接启动既有instance 1执行AR03/04/05及166+R2恢复，不再询问启动/使用许可。见[明确授权记录](2026-11-02-mumu-acceptance-authorization-clarification.md)。本窗口不代发/启动，接任者仍可执行。

## 1. 最新依据与接任

先读仓库/用户AGENTS → [current](../handoff/current.md) → 本文件 → [本批Root复核](2026-11-02-antigravity-stage-delivery-review.md) → 当前卡完整合同和必要原证。state为`E:/creation-reading-assistant/coordination/android-zcode-luna-state.json`，重点currentTask、nextContinuationQueue和原continuationQueue.rootReview；旧路径查document-map，不加载历史全集或其他agent私库。

旧9卡每项已有交付，但本批复核否定“只差3设备批”：17/17、10/10原用例通过仍有实证漏门；EP/C旧manifest失效，MD6路径4同2异，B线仅静态图。**接任者先修具体缺口，不重做整套旧工作。** 原209-EP-r1/旧AG01—09作为来源保留，新AR卡针对未闭合范围，不重建完整工程或并行旧修复。

活动仓库`D:/develop/Code/Codex/creation-reading-assistant-recovery-20260919`；唯一candidate`E:/creation-reading-assistant/task59-common-candidate-v1/candidate/android`；runtime在`E:/creation-reading-assistant/task209-navigation-coverage-v1/inventory-repair-r2/scripts/`。当前产品c396ebe1/53899310B，test0585723c/10751701B；执行前核实际完整SHA/current，不硬沿用旧包。

## 2. 八项任务、依赖与排期

| 卡 | 完整执行合同 | 进入/等待关系 | 初始状态 |
|---|---|---|---|
| AR01 | [恢复真实归属与失败关闭](2026-11-02-ag-next-ar01-recovery-boundary.md) | 首项；与AR06/AR07原证定位可独立 | PLANNED |
| AR02 | [真实门接入、断言补齐和共同冻结](2026-11-02-ag-next-ar02-integrated-gate-freeze.md) | AR01接受；公共runtime/candidate唯一owner，编译一次集中完成 | PLANNED |
| AR03 | [EPUB完整旅程及恢复](2026-11-02-ag-next-ar03-epub-device.md) | AR01/02技术条件满足+fresh/基线/单owner；MuMu启动/验收已授权 | PLANNED |
| AR04 | [Profile回源语义验收](2026-11-02-ag-next-ar04-profile-device.md) | 同设备门；AR03释放资源即可，不必业务PASS | PLANNED |
| AR05 | [灵感回源语义验收](2026-11-02-ag-next-ar05-inspiration-device.md) | 同设备门；AR04释放资源即可 | PLANNED |
| AR06 | [MD复用纠偏与数据合同关系](2026-11-02-ag-next-ar06-md-data-relations.md) | 只读立即可做；实际缺证才占构建/设备 | PLANNED |
| AR07 | [手机取材真实用户闭环](2026-11-02-ag-next-ar07-capture-proof.md) | 先原证定位；回源复用AR05；增验需门/资源/实际许可 | PLANNED |
| AR08 | [共同版本V0退出裁决](2026-11-02-ag-next-ar08-v0-adjudication.md) | 前7项明确结论；必需缺口未关则PARTIAL | PLANNED |

任务总数固定为**8项**。AR03—05为三项独立spec/恢复的业务任务，优先使用AR02同一共同test APK，不复制三套二进制。设备串行不等于全项目串行；AR06/AR07只读原件工作可与实现或设备任务独立进行，不制造重复审计。

## 3. 共用权限、所有权与资源合同

- 用户交办后Antigravity承担接任Root、实施组织、常规技术决定和验收；不等待原窗口额度。实际owner接任前确认原owner释放；你不是独占全库，保护他人WIP。runtime/candidate/Gradle/同设备各唯一owner。
- 用户已同意本项目MuMu验收，AR03/04/05所需启动既有instance 1、测试和166+R2恢复已获准，执行者直接实施。serial由fresh探测绑定，不让用户提供旧值。保留技术门禁，不操作实体手机、正式数据或重置/重建/克隆VM。
- `android-zcode`保持暂停。接任会话连续实施不等于恢复自动化；外部代发/桌面操控不由本队列授权。确有当前电脑操作许可时优先实际可用低额度操控模型，实施模型另记。
- 无Git暂存提交pushPR发布、既有资产删移、D产品回灌、pm clear/盲恢复、正式库/私库session DB或记忆写入许可。冻结host/collector/v13/preserved/R2原归档只读，不改门造绿。
- 设备前完整fresh/单owner/原APK/data/userState/八设置/保护根/创建物基线及166+R2恢复准备。所有ADB带本轮serial；新runId/scope，不重用旧boot/失败scope。已成功恢复/卸载不为补证重复做。
- 复用现有唯一candidate、Gradle home和依赖，开工先登记实际路径/owner/锁。报告根`E:/creation-reading-assistant/task209-navigation-coverage-v1/continuation-20261102/next-v2/`，普通文本/源码/patch/raw每卡512KiB、合计4MiB；构建日志和必要设备保护原件另列真实bytes。先估算空间，不复制工程/cache/历史/APK套装，不清理旧资产腾空间。
- 模型按当前实际可选资源和返工风险选择：恢复/数据/导航/最终验收用可靠强推理，机械工作可用低成本。不可用就记录并换可用方案，不虚称切换，不无限重试模型通道。

## 4. 连续执行、失败分支与状态维护

1. 每卡先把实际task版本、用户指令范围、输入SHA、精确文件、owner/锁、验证/预算写到REPORT开工段；未核owner不得标RUNNING。本文件和各卡已构成共用合同，不能以“还缺一份计划”为由再只产计划。
2. 同owner完成实现、必要编译、定向验证及交付。接任Root核原rc/log/XML/实际source/APK/恢复原证，编译与设备分判；不因测试27项全绿就假设遗漏边界无误。
3. 接受后更新current/state.nextContinuationQueue和受影响V0行，直接进入下一可执行卡，不每卡问继续。业务失败先安全收尾、保留原红；资源释放后其他卡可以继续。恢复缺口优先，禁止新业务mutation。
4. 同边界两轮无收敛/检查点到预算，记录新增证据、否定假说与改法；无新证据不盲重跑。未知外部发送结果不重发；用户stop/Esc立即停止新增输入。
5. 每卡状态分别记录implementation/offline/emulator/recovery；PARTIAL/NOT_RUN/REUSED_WITH_SCOPE准确使用。ready最后写，不自引用，readySHA实算ready文件，reportSHA另列。processed只代表处理过。
6. 充分旧证原路径引用；205红/206有限恢复/旧D限定复用、208/R2/P5已有接受范围不重做。MD两异文件先关系分析，B线原证充分先复用，不为新任务数造设备场景。
7. 写current/state前核读取指纹，冲突重读合并；原producer报告/ready保留，补Root review记录，不改历史为从未失败。额度将尽写精确进度/owner/恢复/下一动作，保证换agent能继续。
8. AR08关键缺口仍在就V0_PARTIAL，不称全产品完成；8项明确结束后只针对已授权真实剩余目标排下一批，不无限制造任务。

复制入口：[Antigravity最新提示词](../handoff/antigravity-next-prompt-v2.md)。本队列替代旧九卡作为当前排期，旧合同/交付保留追溯。
