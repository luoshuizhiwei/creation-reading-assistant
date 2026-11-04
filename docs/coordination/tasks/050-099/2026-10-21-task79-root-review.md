# 79-v1 Root核验（2026-10-21）

任务编号/版本：79-v1 / Root。范围为A/B交付核验；未构建、未操作设备、未运行旧整链。

## 输入指纹与原始证据
A REPORT SHA256 acd711ab28024a5de89c18de5d4205c33460a2f414e9225bec86167de68114fa。A提取PNG 92191B，SHA256 411792db504ab1bca52b4a6b45cc4af3e45682fc8cd210a78245d2f3180b8a26；Root直接读取78原tar的唯一匹配成员，SHA相同，并查看图片。本轮文件名含MAT65V3-a5bb7b，页面显示Download及保存按钮。采纳选择器外观已显示与探测假阴性；图片不证明实际包名、Intent参数或整个执行期间无崩溃。A声称缺窗口标志等为建议/假设，不是已证根因。

B交付源码SHA256 1b8d3b6e365f6ae36f2cb660146d46c177c8eed0dd7f565c9ed1c60df64729ef、patch SHA256 82ba1f233c0bd4024a2844452d2f81b83ae94d479c491f7c9422bafaf3f7b165，实际均匹配ready。59工作区源码仍是基线0379a0b54324e2f750844b86b6e728a50b6bf6a923516f2e42bdcbda929939cc。原证据/差量位于E:/creation-reading-assistant/task79-picker-runtime-v1/A与B。C、integration、attempts均不存在。相关进程定向查询未发现task79命令或活动GradleWrapper；这不能单独证明其他执行者没有所有权。

## 所有权与改动、执行与结果
Root仅维护本报告与current页首。A事实部分接收；B暂不接收合流：
1. ensureAutomationFlags的runCatching结果未使用，配置异常静默丢失，后续lastError不能保留该根因。
2. windows循环只核包名就接收第一个根，未核当前目标窗口身份/焦点/可交互性及歧义；同包背景窗口可被误当当前选择器，保存也复用该结果。
3. pickerDetails先复制旧诊断再额外调用documentsUiRoot，包名可能来自另一次采样，证据可能拼接两次状态。
4. offline-check.py的SimulatedDocumentsUiDetector为另写Python模型，未执行交付Kotlin入口。模型的异常路径亦不同于Kotlin；10项结果只能作为模型/文本边界证据，不能称实际入口行为回归或78运行时复现。
5. B报告将缺FLAG及多窗口写成确定根因，但原78没有窗口/flags快照，证据不支持该确定性。

## 环境、未覆盖项、恢复与副作用
唯一59工作区、Gradle home、锁沿用current，未写产品源码、构建缓存、APK或设备。没有新设备基线/恢复义务，不将78历史恢复当本轮实时状态。没有重跑B会覆盖原结果的脚本；Root本次直接读取原文件、核SHA/归档成员并检查实际代码。
79 A+B实测总212793B（含REPORT/ready及候选文件），无工程/缓存/APK复制。Root仅新增本小型D报告并修改current；旧FAIL与全部交付保留。自动化未操作，按现态保持PAUSED。全产品分母未知；79设备验收NOT_RUN。

## 交付状态与下一步、停止条件
A事实部分接收，B需要79-v2-B精准返修，C未开始。下一步在B候选目录另存v2，修异常保留、明确目标窗口/歧义拒绝与同次诊断，使用真实Kotlin调用入口的定向验证；不造新框架。B通过Root接收后再继续原79-C一次受影响构建/新冻结/单attempt设备批次，不重复A取证或旧ADB/恢复测试。所有权/运行时前提不明则停止依赖工作，不能盲目启动设备。


## 79-v2-B与79-v1-C交付续核
Root直接核候选、59合流源码SHA均为4aefdc928c8a2b05d80c1f9bb1ca72bf578301e85b8f23913bcb8e8f147dfb24，v2 patch实际ff987875be3e523a86bb2b7c51dfec8439ba6c37a523e4ba8daa21e862422b2d。读取原始assemble-test.log及build-result.json：2026-10-21 09:41:49起构建33秒，exit1，630/653/664三处isDocumentsUiPackage未解析。普通嵌套DocumentsUiPickerProbe调用外层实例方法，编译失败；没有新APK/冻结/attempt。旧test原字节已保存在78 integration/artifacts，Root核实际SHA d3bde88b6fd93c3000b52b20a91b886c87202a9324d8634185db26c2a171fc4d；host仍ceb60dd1…e79a，实际SHA核同。

Bv2实现还存在具体缺口：getWindowCount与getWindowCandidates分别读取windows，且默认数量查询runCatching.getOrDefault(0)仍吞异常，不能称同次采样；实际Kotlin验证多个成功场景使用root=null并只断言foundInWindows，不证明生产documentsUiFocused会成功或保存得到可操作root。异常只记录字符串，后续仅最后一次采样失败才转换断言，未保留原异常链。以上限定为实际代码缺口，未执行设备复现。

C正确在编译失败后不安装/不执行设备。其将单attempt解释为禁止第二次离线构建不采纳：离线修复/必要编译可迭代；设备冻结批次仍最多一次run/attempt，执行后禁止现场改码重绑重试。C称全盘找APK不属于授权的定向取证，后续只查冻结索引与已知产物路径，不重复全盘扫描。同boot_id只支持已记录端点间未观察到boot变化，不证明整个生命周期没有崩溃；候选未安装不等于已复核全部保护数据。本次没有新设备恢复义务，设备报告为只读历史快照而非新验收。

下一步79-v3-BC：一个owner负责限定候选修复及现有59定向编译闭环，保留所有旧FAIL/报告；先修编译作用域、同次窗口快照、原异常与有效节点验证，再必要构建/绑定和一次设备批次。无需重复A或未变ADB/历史恢复核验。Root不启动设备、不重建，自动化保持暂停。整链0 PASS/0 FAIL/4 NOT_RUN，BLOCKED_AT_BUILD，全产品分母未知。
