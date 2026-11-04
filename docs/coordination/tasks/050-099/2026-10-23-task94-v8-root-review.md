# 94-v8 Root核验与剩余流程方向

## 任务编号/版本、输入指纹、环境

2026-10-23，94-v8接收，下一94-v9。[实施报告](2026-10-23-task94-v8-review.md)。输入为E的task94-organizing-export-integration-v1/v8/REPORT、ready、inputs与直接原始证据。Root实核REPORT、patch、仪器、产品、当前测试APK和两份保全APK共7项SHA/大小均匹配。

当前源码c207e0f770e7626351a79b063bec72e6c21f121f40fc0ca82cea3f19121eb8e6；尚未设备执行的test APK17ccdc99306832054f6bf975db961e65b92b50d7f9d20076b89d3d7c87e7b2a5（10,523,506B）。实际run使用84f1e3dd…b9ef2ba，原字节已保全核同；474也已单份保全，补齐上批类似冻包风险。19项旧inputs当前仅仪器/test两项发生报告声明的离线改动，其余17项匹配，旧绑定不可直接跑新包。

## 所有权与改动

Root本次只读核验E文件，仅写本报告及current，无构建、设备、外部派发或自动化。相关进程低成本查询未见task94/task59/GradleWrapperMain执行者。唯一task59工作区/既有依赖与构建锁不变。

## 执行与结果、原始证据

- 原始仪器为14.285s、1 test/1 failure，失败于搜索后editableNodeCount=0，而非分类保存。
- t94v8_persist_wait原始JSON：同一精确ID，updated_at从01:52:19.599Z到01:52:25.080Z、payload变化、预期/实际A+B精确ID匹配；前后payload可核，来源为解码语义一致（缺省null与显式null不称逐字节相同），标签保留。结合阶段50/60记录，分类编辑持久化与再次回显采纳为模拟器通过。
- 原test恢复记录独立对照合同：原代码SHA、完整数据清单SHA和用户状态一致；产品卸载、night=no、同boot在线保活与exportCreated=false记录成立。
- initial保护ok；首次final原始失败文件是适配器原生结果而非host wrapper，其consumer_result.ok=false、diffs含global:airplane_mode_on读取rc1，明确不采纳为通过。后续独立recheck与final wrapper均rc0/oktrue/reasons空、71/820、DB组一致、diffs空。原summary仍保留一次protection-final错误，不能说首次恢复无错误或清空原recoveryErrors。重核是同次必要恢复续验，不是第二业务attempt。
- 新增搜索日志版本assemble原日志及当前APK指纹匹配，仅离线构建，不算新设备结果。

## 未覆盖项与方向自审

剩余为关键词×分类AND、精确回源、真实导出、明确清空、取消。搜索前后新增JSON未在旧run执行，不能用补写的脚本意图替代运行现场。

应采纳已通过分类三边界，不重开新建保存/分类实现或重复69项。下一94-v9聚焦搜索按钮实际点击→ToggleSearch状态→ToolbarConfig标题内容渲染/宿主消费：直接相关文件为InspirationToolbar.kt、InspirationScreen.kt、InspirationRoute.kt及实际工具栏消费者。现有脚本点击后立即resolveEditableField，缺少搜索呈现完成的边界门禁；应在既有预算内等真实字段出现，并记录真实可见点击和树/截图，不重复点击或盲加sleep/全局超时。仅凭0节点不能确认入口未命中、状态未改或渲染未完成，也不能确认产品缺陷。

优先完成最小离线定位和确定修正，再一次验证剩余闭环；不能仅为收日志不断开新设备批次。已通过步骤可作为本轮真实夹具/快速回归，不另派重验任务，也不向DB灌数据替代真实路径。同一仪器/搜索接缝→必要构建→单设备有依赖，由一个owner闭环，不造并行审查任务。

## 恢复与副作用

本次无设备操作，无新恢复义务；以上是94-v8历史记录，未来fresh入场。保护/strict night=no/原test代码数据用户状态合同不放宽，实例不关闭重启、结束保持运行。2/3保持PAUSED，10-23已删除，不恢复自动化。无Git发布/D产品回灌/桌面私库云/存量清理。

## 交付状态与下一步

分类三边界/回显/分类排除筛选模拟器证据采纳；整体业务仍部分完成。94-v9先补搜索边界，未改输入可复用17cc；确定需要改动才必要test构建并新绑定，产品fac29与69项复用。覆盖唯一17cc前必要单份10,523,506B保全，其他旧原件原地引用，不复制APK对/工程/缓存。差量证据8MiB、既有build增量预算256MiB，实际字节按报告时点记录。全产品分母未知，MuMu非实体手机/MIUI。
