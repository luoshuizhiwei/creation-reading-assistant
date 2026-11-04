# 任务3/4-v4核查

任务编号/版本：3-v4、4-v4；2026-10-09。本轮报告2/2可见，限定实现退出1/2（4，包含Root补正）；3返修。4设备/Compose/WorkManager端到端另计。

输入指纹：3实算992源码，仅JourneyTest及Fixtures两测试路径变化，41验收工件原字节不变。冻结host SHA440514acd90de7bf38edb744851eefcf345f2c3b9d4710984358df2c142552ae；新test SHAb29e0093d432c0c290da6b940444b6a2768898a6544160425301e3bfa1049ed9。4基树/最终994逐SHA核，2修改/0新增/0删除；Repository SHA9c398ea3130079088e0c88011628c071788c01fe9c764e70a808802f5e6a2500，CancelControlTest SHA296668773a52281710b8cbd5ba38eeb9a6206ed3816a74584b34a6c7fdad5533。4报告SHA481a14850414fe8d10b0c4d951aa18b33a45aeec1864f994130f7de4a73c0e22，patch SHA0da546e0defb2f4ee93d26dcce366b3db977ecd6913329ec39b2143345a5ee8f。

所有权与改动：3外部仅两androidTest文件及专属定向入口；Root不采纳1.4降低断言。4原仅Repository/CancelControlTest两路径。Root反馈候选独立E，所有权限定Repository/ProfileViewModel/CancelControlTest，不写D产品。两只读独立审查分别负责J1和取消，不重复Root SHA/XML。

环境：隔离MuMu；无实体手机。HEAD e39a78da9c62f02614207cffb36969a32492eaea，暂存区空。实际应用/模型以原报告登记为准，3未完整登记，不以建议模型补写。

执行与结果：
- 3只交付一条可核诊断run_b74e23ab：搜索/chromeBack/marker1计数依次0/1/1→0/0/1→1/0/0，支持该次Back先隐藏chrome再回Home。报告所引run_50c2fa2d和run_5ecd9635文件未找到，不据此签两独立诊断已交。当前单项summary FAIL，raw在JourneyTest:322等搜索正文卡超时，完整J1未通过，不能进入31联合。
- 3步骤1.4从legacyOffset==contentA.offset1与assertObservedPersistedLocation改成>=0，放松已采纳位置合同，拒绝采纳。报告称121变0缺阶段DB原始行证据。下一必须采fixture前/进入Reader后/Back后完整同书位置行，并解释页起始/marker/locator关系；不能把任何非负值当正确位置。
- 3搜索失败无失败帧树/结果/索引行。静态匹配来源(title/location/原文/click)与fixture beta原文FULL存在，尚不能断定selector或产品索引错误。下一同批受限采query/loading、B hit/offset/coverage、实际DB term/cov与merged/unmerged树，一次聚焦定位。
- 4红基树生产Repository字节与v3一致，同测试SHA与v4一致；红原XML31项恰2失败（等reset资格取消、countActive挂起取消），绿31/31与8类107/107、exit0成立。WorkerPolicy未改复用12/12，不累加重复集合当独立功能数。提交资格定义下两窗口关闭，没有新P1证据。
- 4报告“锁内无挂起”不准确：stateDao.set为suspend。资格先到允许reset提交，合同行为成立，但DAO未完成时首次cancel仍可能anchorPreserved=true；ProfileVM据此虚称断点保留。反向测试丢弃首次结果而漏测。Root独立补进行中/成功/失败真实反馈，避免新一轮外部转发。

原始证据：[Root实算](<E:/creation-reading-assistant/tasks-3-4-v4-codex-review-20261009/delivery-verification.json>)；3单run raw/summary/back_diag三截图及JSON；4evidence/raw/red-v3base-windows与green-v4-directed逐XML及_meta。未用旧报告代替原始日志。

未覆盖项：J1正确初始位置/搜索B卡/整项旅程；31联合；搜索取消Compose渲染及WorkManager端到端。X整机中断、实体手机、Back归因/队列排空/初始新save不关闭，全产品分母未核。

恢复与副作用：Root当前再次核71文件size/完整mtime/SHA、原APK对、8设置/映射、原shell uid2000以及正式/新宿主不存在状态全部一致。外部仍留MuMu运行，Rootcontrol-v1-shutdown返回errcode0，info进程false。证据在tasks-3-4-v4-codex-review-20261009/root-readonly-restore-proof.json、root-power-final.json与root-adb-final.txt。没有应用数据或D产品/Git写入。

Root反馈补正：独立候选[E目录报告](<E:/creation-reading-assistant/parallel-search-task4-root-feedback-v4-20261009/ROOT_REPORT.md>)。单原子三态PRESERVED/RESET_PENDING/RESET_COMMITTED，DAO成功才发布已重置、失败恢复此前真实状态，VM进行中反馈优先；三处游标状态发布移入相应写入锁内，避免迟到覆盖。原v4生产加真实VM断言红32/2；最终33取消+4VM+19重建并发56/56、两批exit0，Root实算994源码/3修改/991不变及3原XML SHA全部一致。最终清单文件SHA ab916de24375c776def3a4740bc3dbf97383ad4b4759ce66ee670e9dd86dd102。原107对未改部分复用，不相加冒充最终独立全集。

交付状态与下一步：3-v5唯一MuMu继续正确位置合同及搜索卡补证；4含Root补正退出实现返修，不再派4-v5。任务7纯E机械合流已验4/5产品增量及清单，可与3并行，无设备/构建/最终APK，不吸收3-v4放松断言，不提前应用D。任务6旧材料不含4/5，保留只读及C2签名待决定边界。复盘保持稳定阅读/检索回源主线；7仅提前准备互斥增量，不新增功能、不签整体验收，下一联合门禁仍等J1合同成立。5/6不重派，不扩桌面或发布。
