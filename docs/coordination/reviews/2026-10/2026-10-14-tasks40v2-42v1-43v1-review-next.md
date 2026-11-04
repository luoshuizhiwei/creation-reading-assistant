# 40-v2 / 42-v1 / 43-v1成果实核与下一批

## 任务编号/版本
2026-10-14 23:23香港时间。用户“完成了”后实核3份成果。下一批3项：40-v3同owner修安全顺序和实际完整链；42-v2修仪器清理归属/真实absence/授权比较；44-v1独立E桌面候选构建与A/B/C实际验证。43调查结束不重派。

## 输入指纹
D HEAD e39a78da9c62f02614207cffb36969a32492eaea，暂存空；原Root-v2和APK冻结复用。42候选按原1019项逐文件实核：2改、1017不变，加1个androidTest，生产源码/配置未漂移。42的freeze/source-sha256.final.json仍是指向原Root-v2的旧清单，不能当新候选冻结。43实核所列30项指纹，24一致、6不一致；不据此断言漂移原因，重取真实当前指纹。无历史版本逐文件对照则不能称当前输入与历史完全匹配。

## 所有权与改动
40-v3唯一写E:/creation-reading-assistant/task40-host-joint-integration-v3的host/runtime/工具冻结；42-v2唯一写E:/creation-reading-assistant/task42-saf-run-cleanup-v2自有Android仪器候选及定向测试；44-v1唯一写E:/creation-reading-assistant/task44-desktop-usable-candidate-v1的桌面候选/构建/隔离profile/证据。无共同文件写入；44不操作D桌面/HOLD原资产，42不改main。公共最终APK/设备合流等待40和42核过。Root仅核查与全局交接，不自主实施。

## 环境
Python3.13；40原测试会重写自身清单和修改mock_runner后恢复，所以Root仅拷host/tests/evidence至自有E再执行，原交付只读；pytest临时目录、缓存与日志在自有E。实际20/20 exit0，129.90s。42原编译日志BUILD SUCCESSFUL 1m54、exit0/排他锁记录成立；未重复编译，新增6项均在androidTest，仅编译，没有原始运行结果，不当JVM已跑。无ADB/设备/子代理。

## 执行与结果
40前置36明确拒绝即return的改动保留，但原用例存在条件断言，不能据20绿宣称完整控制流证明。实际代码295行invoke、314行callback、322行才调用保护取证/恢复编排；在取证前执行仪器，违背先保护后可变操作合同。函数缺统一覆盖invoke/consume异常的恢复finally。共享mock类经command_adapter重新加载成为另一类，Root实际same_device_object=false，两段设备状态并不相同。严格总consume为FAIL，不能把调用到失败函数叫完整链PASS。合成数据可按真实合同满足离线严格consumer，不能把失败归为“合成永远不可能通过”。
40工具校验缺manifest记skip成功、仅核files忽略digest/external且允许空files。Root实际空files+坏digest+缺external校验返回ok=true、checked=0。manifest本身应排除自引用，工具Python代码本身应被清单覆盖，不能排除tool_freeze.py。URI按操作范围的要求并未实现，仍沿用全局非空拒绝；不放松保护，修准确作用域。
42正确的pre-run快照和独立冷读后清理入口采纳，编译采纳。余缺：SecurityException和Throwable在documentDeleted分支直接observedUriAbsent=true；snapshot.runId未核、ledger缺时新建空账而非拒绝、删除前不核handoff内容SHA/token与ledger/绑定，归属不足；快照可覆盖；只比较URI字符串，未核read/write授权状态；失败后仍删handoff/dump且忽略delete结果、可能丢续接证据。新增成功用例用虚构otherUri快照对真实resolver，缺成功恢复严格断言，6场景仅编译。没有实际严格consumer验证日志，verification_receipt中的PASS不能证明真实输出通过。
43源码/脚本索引与活动仓库未见旧工件的限定观察采纳；“核心100%完备/无需任何修复/历史输入完全匹配”不采纳。6指纹不符须重算；Stage4脚本自述只做新增入口/IPC smoke，不能独自关闭正文重开、卡片持久关系、备份恢复三条核心流程。下一44直接在隔离候选执行，避免再发纯报告返修。

## 原始证据
[40原20测试](<E:/creation-reading-assistant/tasks40v2-42v1-43v1-codex-review-20261014/40-original-tests.log>)；[工具冻结反例](<E:/creation-reading-assistant/tasks40v2-42v1-43v1-codex-review-20261014/40-forged-freeze-counterexample.json>)；[共享状态反例](<E:/creation-reading-assistant/tasks40v2-42v1-43v1-codex-review-20261014/40-shared-state-counterexample.json>)；[执行顺序](<E:/creation-reading-assistant/tasks40v2-42v1-43v1-codex-review-20261014/40-source-order.json>)；[42实际源差量](<E:/creation-reading-assistant/tasks40v2-42v1-43v1-codex-review-20261014/42-source-review.json>)；[42控制流证据](<E:/creation-reading-assistant/tasks40v2-42v1-43v1-codex-review-20261014/42-control-flow-evidence.json>)；[43指纹](<E:/creation-reading-assistant/tasks40v2-42v1-43v1-codex-review-20261014/43-input-check.json>)。

## 未覆盖项
实现交付3/3，完整闭环尚未通过；此比率不是全产品进度。40严格链未过，42真实provider/授权NOT_PROVEN、6仪器用例NOT_RUN，桌面运行NOT_RUN。D/F、X、实体/MIUI、真实跨端、发布保持范围；全产品分母未知。

## 恢复与副作用
本轮仅E只读副本与反例、全局文档，原产品/候选/工件未改，无正式数据/设备、Git写入、外部消息或定时任务。原文快照保存在E，写前核字节一致。

## 交付状态与下一步
保留所属成果，40/42只修上述最小因果缺口，不泛审计；44承接现有桌面源码进入独立E构建与隔离运行，不等Android。44可在自身候选修已复现的核心流程/构建缺陷，不能回灌D、改原HOLD或新增功能；若隔离或所有权无法成立停止相关动作并报告。三项现在并行；最终AndroidAPK与MuMu等40/42核查合流后唯一owner执行，44不占Gradle/设备。
推荐40 GLM5.3（替代Gemini3.8或DeepSeek强推理），42 Gemini3.8（替代GLM5.3或DeepSeek强推理），44 DeepSeek较强推理（替代GLM5.3）。按当前用户菜单不声称已测模型能力/价格；不派GPT/Claude。DeepSeek粗估40为60–100min、42为50–90min、44为60–120min；不是硬承诺。外部总结最下面分别“这是任务40-v3。”“这是任务42-v2。”“这是任务44-v1。”。
