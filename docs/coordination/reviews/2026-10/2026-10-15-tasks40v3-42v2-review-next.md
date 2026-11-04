# Android范围40-v3/42-v2实际核查

## 任务编号/版本
2026-10-15 01:09香港时间。用户“都完成了”后仅核40-v3、42-v2；44已撤销，不读其成果或恢复桌面任务。下一2项独立Android任务40-v4与42-v3，不制造第三任务。

## 输入指纹
D HEAD e39a78da9c62f02614207cffb36969a32492eaea，暂存空。42-v2输出1020清单逐文件全部实核匹配；vsRoot-v2原1019为2改/1017不变、加1仪器文件，main/配置不变。原APK冻结和未改生产证据复用，不复构建。40原代码/日志保留只读，Root只拷host/tests及顶层evidence文件至自有E核验。

## 所有权与改动
Root只核查/全局交接；40-v4唯一写E:/creation-reading-assistant/task40-host-joint-integration-v4的host/runtime/定向测试/冻结，42-v3唯一写E:/creation-reading-assistant/task42-saf-run-cleanup-v3自有仪器候选/定向验证/编译证据。无共写，设备/最终APK等核后唯一owner合流；桌面/HOLD退出当前队列，44撤销不恢复。

## 环境
Python3.13，40原17项在E只读副本实际运行17/17 exit0、76.17s，禁pycache和pytest输入缓存。42不重复Gradle，检查原始编译日志与真实源码/清单；无设备/ADB/新APK，无子代理/外部消息。

## 执行与结果
40正确增量采纳：取证前置、目标操作finally恢复、共享状态接通、工具清单覆盖自身代码/缺失/摘要/外部检查。但完整链不采纳：全套在消费前复制36旧fixtures/pulled，使用runx通过，D/E未由本run invoke/harvest产生；单方法用invoke_ok与index_problems替代实际证据结果。Root实际注入consumer rc1/overall FAIL/当前旅程拒绝，但最终ok=true、consume_step.ok=true。完整consume失败不能变成通过。
40实际能力反例：空授权基线+operation_scope=saf_export，URI_REVOKE_CAPABILITY=UNAVAILABLE仍ok=true；unknown_operation亦ok=true。新增授权操作能力不足必须前置阻断，与旧授权是否为空无关。普通流程作用域须绑定真实方法；包名在raw内的字符串匹配不是可靠UID授权归属。未来同阶段单方法判定应调用实际对应严格子consumer，缺其他旅程只记未执行；不再借旧夹具拼全套PASS。
42正确源码增量采纳：快照防覆盖、snapshot/ledger runId、缺账本拒绝、异常absence=false、read/write授权比较、失败保留凭证、新1020清单准确。剩余删除前只在handoff有hash/size且ledger有效时比较两份声明，字段缺失可跳过，没读实际对象SHA/size/token；尚不能证明待删对象属于本run。8仪器用例未运行，旧consumer自测与新清理实现验证分开，不能称实现逻辑全部通过。
42原编译日志第43行BUILD SUCCESSFUL in40s，但最后244行为BUILD FAILED in41s、245 command exit1，失败因任务本地.gradle/buildOutputCleanup缓存锁释放。日志SHA300536c7f8de8ba43a7bd028b99f2613340b5a8f813f9f8eb87f33456434dcd8。报告exit0/35s与最终日志不符，不能采纳最终编译成功；源码编译任务UP-TO-DATE阶段证据保留，不据环境释放锁失败断言Kotlin源码错误。下一隔离新项目缓存，保留旧现场，不删锁、不杀共享进程，取得最终exit0的单次清晰日志。

## 原始证据
[40原17测试](<E:/creation-reading-assistant/tasks40v3-42v2-codex-review-20261015/40-original-tests.log>)；[consumer失败仍通过反例](<E:/creation-reading-assistant/tasks40v3-42v2-codex-review-20261015/40-consume-reject-counterexample.json>)；[空基线SAF/未知scope反例](<E:/creation-reading-assistant/tasks40v3-42v2-codex-review-20261015/40-capability-counterexample.json>)；[夹具复制与单方法捷径](<E:/creation-reading-assistant/tasks40v3-42v2-codex-review-20261015/40-evidence-shortcuts.json>)；[42源差量与最终构建状态](<E:/creation-reading-assistant/tasks40v3-42v2-codex-review-20261015/42-source-and-build-review.json>)；[42输出清单](<E:/creation-reading-assistant/tasks40v3-42v2-codex-review-20261015/42-output-manifest-review.json>)；[删除前弱门禁](<E:/creation-reading-assistant/tasks40v3-42v2-codex-review-20261015/42-deletion-gate.json>)。

## 未覆盖项
交付2/2不等验收通过。40的模拟逻辑/原17项成立，不关闭完整链/实际SAF；42新清理仪器8项NOT_RUN、provider NOT_PROVEN、最终构建exit1。MuMu全NOT_RUN，D/F及X/实体/MIUI边界不扩，Android完整分母未知。报告“NOT_PROVEN清零/100%闭环”不采纳。

## 恢复与副作用
仅E副本/合成反例和D全局文档，无产品/原候选/Git/设备/桌面操作；暂存空。原文快照保存在E，写前字节一致。

## 交付状态与下一步
40-v4保留已证顺序/共享/冻结，只完成真实已执行子旅程严格消费，删除旧夹具补齐，全套缺证明确BLOCKED/NOT_RUN；SAF能力不足、未知scope在动作前拒绝。42-v3只补实际对象归属及成功构建证据，不扩大Native范围。两线自有E并行，无设备/最终APK；核过后转唯一owner合流，不再循环泛审计或为了凑并行增加任务。Gemini3.8与GLM5.3/DeepSeek强推理均可按实际菜单作为候选，路由不固定，不外派GPT/Claude。
