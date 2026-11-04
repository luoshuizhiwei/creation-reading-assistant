# 83-v1-A/B Root核验（2026-10-21）

任务编号/版本：83-v1-A/B-Root。A构建交付采纳；B仅采纳有限普通使用观察及所列收尾，不采纳单attempt五步全过/完整严格恢复。80-v6-H既有四旅程及恢复采纳不变，全产品分母未知。

## 输入指纹、所有权与改动、环境
Root维护本报告/current；两个独立explorer分别只读B恢复/attempt和进度语义，不改产品、设备或构建。相关进程查询未发现83执行脚本（不等于设备可随意接管）。A ready、metadata、构建原日志已读；Root实际debug APK SHA e59cf92286c6aad2b99e1c55f4dec17bc9b3cea52b9051db3d25560bbebf9b22，51846868B，与ready一致。原日志BUILD SUCCESSFUL in 5m17s，锁包装记录exit0/released。标准applicationId=com.creationreadingassistant，debug签名及MainActivity元数据支持应用产物身份，不证明各ABI全部设备适配或发布资格。
A未新建变体，工作区task59-common-candidate-v1/candidate/android及缓存D:/develop/env/gradle复用。A以全E卷空闲差估计455892992B增量，这是卷级变化，不严格等于可归因构建物字节；不为此重扫/清理。APK无额外复制。

## 执行与结果、原始证据
B/evidence/run/flow.json记录首试install成功、launch成功后step1失败；logs/01-selfuse.log exit6。run2再次启动失败，之后04至09日志记录修驱动后多次继续。不能合并为合同规定的单main/run/attempt五步通过；失败后修脚本不重新安装也属于同批继续设备业务，未满足本批失败即停要求。
可采纳普通使用观察：系统选择器导入TXT成功；读到可辨正文；横向翻页后UI76.4%；退书重开仍76.4%及同第7/8段。MD只有材料推送、未导入，NOT_RUN。cold-start只到主界面和查数据库，未重新打开书核页面，不支持冷启动精确续读。
进度证据还存在UI76.4%而SQLite100%的未解释差异；脚本未按bookId筛选/保全current_location_json及completion_state时间线。源码ReaderProgressComputations从pager取UI全书百分比，ReaderProgressEffects保存同源百分比及locator，ReaderDocumentLoader用locator offset恢复；BookRepository.markFinished可抬到100而保留locator，但本run无证据证明触发此合法例外，不能据此排除或断定产品缺陷。下一次只补同书逐阶段位置行和冷重开落点，不重测整条已验业务链。

## 恢复与副作用、未覆盖项
安装前pm path/精确包列表/数据目录探针完成支持标准包缺席；结束卸载该本批包、两个材料SHA核后精确删除、11所采设置保持、相关qahost包列表不变、实例在该采样窗口保活。这不证明83旧数据已损坏。
B只有外部顶层ls名称及设置比较，没有原71私有/820外部size/SHA、DB-WAL-SHM原字节前后证据。pre-uninstall-inventory是ls名称/size，不是DB副本；本包初始缺席故不存在该包既有私有DB要回滚，但卸载后也无法补造本轮进度数据库原证据。
实际b_recover.py显式过滤night差异，最终ok也不消费batch_materials.all_clean及external列表差异。故即使此次11项无差异，也不能把其RECOVERED作为严格整链证明。night初始yes、最终yes仅证明本批没改变该值，不能豁免现行strict no。Root不修改或降低night合同。
准备前后boot_id变化可证明重启发生，报告称外部所为但Root没有独立全命令归因证据，不自动归责；报告称游戏停止另获用户指示，仅将操作作为本run记录，不推为以后永久许可。preflight实际时间21:52:38，报告写22:52为笔误。

## 交付状态与下一步、停止条件
A已交可安装debug产物，不再构建或复制。B部分使用观察可保留，单attempt/精确冷续读/MD/严格恢复未通过。下一84-v1同owner在独立版本目录先修脚本准入、恢复结果汇总和只读DB取证，再用同一APK完成一次TXT+MD普通使用闭环；字段语义先离线查清，不未证派产品修复。设备失败即停并恢复，不在同批修后继续业务。原失败、旧报告和最终摘要保留，新增勘误不改旧绿结论以掩盖。
先完成身份/owner、严格保护和night准入，缺证或初始不满足不能当新基线绕过；不可用82 Root-r1固定H合同改路径硬套。原保护源只读，按本批明确包/创建物和既有合同做必要最小适配，不另建框架。标准包有旧应用/数据即停，不覆盖或卸载旧资产。恢复缺proof/差异、创建物收尾失败、卸载失败、保活失败或night违约均阻塞，不忽略字段。没有另一个已证独立产品修复，不为并行数量增加泛审查；本轮独立证据核验已并行完成。
Root本轮构建0、设备命令0、新增E资产0，只写本报告/current。两个自动化PAUSED，无外部消息/Git/发布/桌面/云/D产品回灌/存量清理。仅MuMu证据，非实体手机/MIUI，全产品分母未知。
