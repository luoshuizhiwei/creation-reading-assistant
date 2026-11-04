# 40/41实核与新规划下一批

## 任务编号/版本
2026-10-14 22:03香港时间。用户要求按已调整规划继续给提示词。本批3项：40-v2主机完整链定向返修，42-v1仪器SAF按run恢复闭环，43-v1桌面既有交付只读承接，可立即并行；无设备执行者。

## 输入指纹
D HEAD e39a78da9c62f02614207cffb36969a32492eaea，暂存空。Root-v2原1019源和36-v1APK冻结复用。40实际交付逐项SHA见E hash-review，14条清单仅自身条目不符；41所列9个调查源码逐文件实算一致。原交付只读，所有新候选/原始证据放各自E。

## 所有权与改动
40-v2唯一拥有E:/creation-reading-assistant/task40-host-joint-integration-v2的host/runtime/适配/调度/总消费/工具冻结，原40-v1只读；42-v1拥有E:/creation-reading-assistant/task42-saf-run-cleanup-v1自有Android候选中的RealSystemUiAcceptTest.kt、Task35EvidenceFixtures.kt及必要定向测试，不改main、主机工具、公共配置或原候选；43-v1只读活动仓库桌面源码及明确交付路径，只写E:/creation-reading-assistant/task43-desktop-delivery-reuse-v1承接清单。三个无共写，只有42需要定向编译且独占Gradle锁。公共集成、双APK和设备均等Root核后再唯一owner安排。

## 环境
Windows/Python3.13。40原15项pytest在自有E临时目录、禁pycache与cacheprovider下实际跑过。最初pytest目录收集和基路径错误属于Root运行环境设置，修正rootdir/confcutdir并创建父目录后15项通过；不将两次启动错误算产品失败。无真实ADB/Gradle/设备，无子代理。

## 执行与结果
40实际15/15 exit0，schema显式转换及所属离线成果采纳；完整链未验收。其test_callback只有hit.append，没有调用既有36-v4 invoke/harvest/mapping/consume。原报告“全链通过/无BLOCKED”仅适用其测试回调范围，不能替代原任务完整退出。
Root在实际run_full_chain外部边界注入36消费者明确拒绝：最终ok=false，但admitted=true、orchestrated=true、callback仍CALLED。前置失败未阻止后续编排，是须修的真实接口控制流反例。冻结清单包括自身且自身SHA不匹配，未覆盖外部36/38运行依赖；修正为不自包含的有效清单与独立摘要，真实entry运行前核执行代码范围。
41调查9/9输入SHA成立；当前Android src内releasePersistableUriPermission和DocumentsContract.deleteDocument零调用，markCleanup仅定义1处无调用。清理/恢复生产端缺口成立，调查任务结束不重派。草案不照抄：grantsBefore应在创建/新增授权前保存；删除应在仍持有必要写权限时完成；权限拒绝不能证明对象不存在。源码/API可调用不等于MuMu实际通过，不扩“真机必须”表述。

## 原始证据
[40原测试实跑](<E:/creation-reading-assistant/tasks40-41-codex-review-20261014/40-original-tests.log>)；[36拒绝仍回调反例](<E:/creation-reading-assistant/tasks40-41-codex-review-20261014/36-gate-reject-counterexample.json>)；[40指纹](<E:/creation-reading-assistant/tasks40-41-codex-review-20261014/hash-review.json>)；[41源码与草案校正](<E:/creation-reading-assistant/tasks40-41-codex-review-20261014/41-source-review.json>)。

## 未覆盖项
40非完整runtime/设备通过；41未证明provider运行能力。42新代码核后才进入新共同候选并构建一次所需APK，不沿用旧测试APK身份。43历史桌面交付不冒充当前桌面通过。D/F、X、实体/MIUI、LAN与发布未关闭，全产品分母未知。

## 恢复与副作用
Root仅E离线核验与全局文档，原外部成果和D产品无改动；暂存空。pytest只自有E目录，原输入无pycache写入。无正式库、旧损坏仓库、桌面/HOLD改动、私库、自动任务或外部消息。写全局文档前保存原文并核SHA未漂移。

## 交付状态与下一步
复用40 schema/15项、41能力定位，不重开调查。40-v2实际mock外部命令接既有36入口，前置失败零后续变更，区分PASS/BLOCKED/NOT_PROVEN；42新增最小仪器清理入口，保留冷读并提供真实恢复证据，设备NOT_RUN；43直接产出三条桌面核心流程可承接清单与最小下一执行合同，找不到工件如实缺证，不开发重做。
建议40 GLM5.3，42 DeepSeek较强推理档，43 DeepSeek普通/Flash；以用户当前菜单为准，不外派GPT/Claude。DeepSeek粗估40为60–100min，42为60–100min，43为20–40min。三项可并行，设备/最终APK等待40和42核查、合流及绑定。用户只复制一次，各执行者自行检查输入、占用、配置、定向验证并写报告；最终总结最后分别“这是任务40-v2。”“这是任务42-v1。”“这是任务43-v1。”。
