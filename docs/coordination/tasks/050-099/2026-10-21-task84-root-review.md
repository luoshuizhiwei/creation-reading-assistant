# 84-v1 Root核验与离线补齐（2026-10-21）

任务编号/版本：84-v1-Root。84业务验收FAIL；采纳实际有限收尾，不扩大为原71/820严格保护已通过。Root本轮并行补齐脚本接缝与严格保护适配，离线候选可进入下一明确批次。全产品分母未知，仅MuMu范围。

## 输入指纹、所有权与改动、环境
84 ready列出的四脚本实际SHA匹配4/4；原12项恢复离线结果读取复用，不重跑。唯一本批日志01-selfuse.log为exit6，stopped_at=txt:open_reader。进程查询未见b2 admission/selfuse/recover活动。Root不设备/不构建，两个worker分别独占root-r1与protection-root-r1，未覆盖原84交付、设备证据或共享producer，Root维护D报告/current。
继续复用83-A标准debug APK e59cf92286c6aad2b99e1c55f4dec17bc9b3cea52b9051db3d25560bbebf9b22，51846868B；没有产品代码或Gradle改动。

## 执行与结果、原始证据
84 evidence/run/flow.json记录attempt=1，TXT导入成功、bookId=17da511a-05e3-4805-90ed-7844c1fdf04f、尚无进度行；后open_reader失败，日志和reader-open-TXT.xml仍是导入完成界面。无阅读位移/退出续读/冷启动重开证据，MD NOT_RUN。按现有记录未失败后重试，较83流程纠正采纳；不据此断定产品缺陷。
原goto_shelf未退出导入完成弹层。Root直接派生候选修复，精确识别完成态后一次BACK并确认书架，非完成页不盲目BACK。顺手在同一真实成功路径补84要求但原脚本仅记录的门禁：分页哨兵、中间页百分比有效、B/C/D/E/F同bookId位置行与UI显示精度下进度一致、force-stop成功和PID为空；completion_state仅留证不推断具体值；保留正文marker及精确locator匹配，每格式结束确认书架。
修后入口B/v2/root-r1/scripts/b2_selfuse.py，SHA fe8f6aaae10db307f93994029154e5aa467c72a0b86152acc34ee73266363aca（改前90bbb2627639aa414eb3cf7b015e67d3dc894d869f0351016b150c16ce1e10c2）。18/18实际helper/run_format边界替换测试，原日志root-r1/logs/test_goto_shelf.log。Root核候选/原始日志SHA及binding.evidence列出8项SHA全部匹配。真实失败XML字节副本相同，Android未执行。
独立MD只读核查确认fresh默认TXT/MD均分页，MD也走MarkdownChapterSource及水平翻页，原marker/百分比可达；现场仍需分页哨兵，不凭默认推断支持任意scroll配置，不增加滚动模式开发。

## 恢复与副作用、严格证据缺口
84 recovery.json实际blockers=[]：本批新包卸载、两份材料SHA核后删除、11设置不变且night=no、相关包/顶层目录列表相符、boot与保活相符。以上按所列范围采纳，不推断原字节损坏。
但是b2 admission/recovery仍只有顶层ls列表，没有原71私有/820外部sizeSHA、数据库组及APK原字节核对；恢复测试12/12只证明其实现列出的blocker消费，不足以证明完整保护合同。旧84报告“严格恢复全部通过”作此范围纠正，不能事后补造安装前快照。
Root独立交付B/v2/protection-root-r1/candidate/protection_check.py，SHA16c93794844650f2116fff7c97c5adb29acb15bfbc623a000643fa2fa5f722bd。直接委托task53原initial/final保护消费者，加载已核ADB和窄serial绑定，不改基线/设置/消费者；要求原结果ok、diffs=[]、71/820计数、真实capture及db_groups与原基线一致。只读检查，不restore/cleanup/install/launch，归属与标准debug包缺席须由设备owner另核。输出为适配目录内独占新子目录，initial/final分开。
真实入口/原消费者边界替换6/6通过（tests/test.log）；Root核adapter及binding9项实际SHA全部匹配。此为离线工具证据，不是新设备保护通过，不追认84历史恢复。

## 交付状态与下一步、未覆盖项、停止条件
原84业务FAIL保留；两份Root离线候选已接收，下一85-v1唯一集成/设备owner将新run/serial/输出和恢复输入显式绑定：先正确实例/包缺席/night与原严格initial保护，再安装现有debug，TXT→MD一次普通使用，任何失败停止后续并完成恢复；在包/材料精确收尾后运行严格final保护及保活。原b2_recover所列收尾只是必要子项，不能代替该final消费者。不能直接调用旧main覆盖84输出，不修改共享producer，不套82固定H合同入口。
只对新编排/路径接线做必要离线检查，复用18/6/12项和已核APK，不重复构建或旧四旅程。同一设备与编排唯一owner；本轮独立的UI修复、严格保护适配、MD模式调查已并行，不再凑重复任务。
新增root-r1为110998B，protection-root-r1交付约24016B，均无工程/缓存/APK复制，必要原始日志保留。原84新增约0.86MiB为报告统计，Root不重复全树盘点。唯一本轮新增是E离线差量与必要D交接；无Git/外部消息/桌面/云/存量清理。两个自动化PAUSED。归属/并发、输入/输出漂移、初始严格检查或night不符、缺proof及恢复失败即阻塞，不能降标准继续。全产品分母未知，实体手机/MIUI未验。
