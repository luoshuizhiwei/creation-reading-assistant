# 三项 v3 交付核查与下一批（2026-10-07）

## 任务编号/版本
CRA-THREE-V3-DELIVERIES-CODEX-REVIEW-v1。用户报告三项完成；Codex只读核实际成果并主动给窄返修提示词，不将交付等同验收通过。

## 输入指纹 / 所有权与改动
活动仓库 HEAD e39a78da9c62f02614207cffb36969a32492eaea，暂存区空。三个 v3 候选只读核查；三个独立 reviewer 分别负责 TXT、D2、J1，未改产品、未构建或操作设备。Root只维护本报告、current 与 E 原始核查文件。
E 证据根：E:/creation-reading-assistant/three-v3-deliveries-codex-review-20261007。
reviewed-inputs.json 记录下一批完整源码指纹：TXT984项；D2由已核v2完整980项和两文件实际变化合成；J1当前982项实算。D2原v3清单仅两文件，不能称完整冻结。
Root启动时未发现匹配三任务路径的java/gradle/python进程，不等于外部应用完整占用检查；下一owner启动仍须核占用。

## Standards
TXT源码984/984和验收清单15/15无漂移，三离线日志和编译日志SHA与报告一致；59项合成门禁及编译EXIT=0原始证据保留。未全量重跑。
D2相对完整v2清单980项仅两授权文件改变，gradle.properties未变；原XML红73项6失败/绿73项0失败错误跳过及SHA成立。red-log/green-log是人工摘要，不能当作原始Gradle stdout/退出码；有原日志可补交，不为缺失重跑充分旧证据。
J1相对v2共982项仅四授权文件改变，原10XML红91项2失败、绿95项0失败错误跳过；两原始构建日志与报告SHA一致。

## Spec
### TXT：1 P1、1 P2，尚不冻结
存活S2握手先于退出、Exit独立观察与S2 SHA绑定、完整bookId、实际65s步骤和未赋值变量修复成立；canonical与失败停止保留。
P1 j1_ui_callback.py:642–662 的活库回退分次cp DB/WAL/SHM，无一致快照；cp隐藏失败、pull未检查、目录复用可能消费旧文件；路径缺run/step且三个创建物未登记。主sqlite SELECT通道可保留，最小修复直接禁用不可靠回退并fail-closed，不新增备份架构。
P2 callback:342–351/374–379与checker:417–427/434–447用最大正文节点面积决定视口。reviewer纯内存调用实际Ctx：根[0,0][1080,1920]，完全离屏正文[0,2400][1080,6000]竟被选为视口并接受marker。应独立从可信窗口/root取视口，正文不参与选择；不可信拒绝。多个marker合并节点仍不能证明每个字符可见或真实source位移，保留设备盲区。

### D2：同书并发合同未闭合
正文固定名冲突已解决、四个refresh旧hash→新内容语义恢复、部分字节失败清理已证明；73项保留。EPUB新增mock只证明replaceStoredEpub调用接缝，不证明实际消费/Room。
P1 ShelfImporter.kt:1039–1052/1067–1069/1083–1085/1147仍共用.repairing/.backup；SAME relink和changed refresh均过入口后可覆盖同一换入文件，A可能提升B字节，finally/回滚可干扰另一请求。既有下游缺口但本轮要求未关闭，不称新引入或设备实测。需要两入口同bookId共享互斥覆盖读取旧实体、校验、换入、DB及回滚；可控barrier JVM验证。
973–978 UUID+outputStream没有原子排他创建，合同欠缺；不将极低概率碰撞当已发生损坏。原子创建并仅清理实际所有文件，补未知归属保护。

### J1：2 P2，请求归属仍未闭合
四参真实返回、按ownedRequestId取消方向成立，95项原证据保留。
VM:120–123及ReaderRoute:202–210以active或旧栈成员的目标值判断本次route已提交。A→B成功→再次请求栈内A等待/失败，返回会错误弹合法栈；重复旧active也误判。须以本次request/route成功身份判断，不靠目标值。
ReaderRoute:89–100加载observer仍消费全局pending，只有bookId/ready检查，未核ownedRequestId与完整destination/加载归属；旧同书ready可提交新locator请求。应仅处置自身请求并绑定实际加载epoch/result。真实Compose触发时序仍未验。
VM直接返回pending及popBackStack页面恢复是接口/UI盲区，不额外扩成已证缺陷。报告“真机”与D patch建议均不是本轮授权。

## 环境 / 执行与结果 / 原始证据
Windows；Root SHA核查文件root-fingerprint-check.json、reviewed-inputs.json、j1-source-current.json、d2-source-current.json。
三个v3各自原日志/XML/离线汇总和失败run保持原样。reviewer只读核查与纯内存反例，无新设备操作。

## 恢复与副作用
本轮没有设备写入，也没有新做设备恢复验收；不把前轮71/2/7恢复证据写成本轮重新核验。TXT自报删除自有输出内旧脚本/中间夹具以及同目录自造并发，明确记录，不扩大为可清理原候选或缓存的授权。不触碰正式书库、原损坏仓库、桌面/HOLD及私有库。

## 未覆盖项与进度
报告交付3/3；可进入联合冻结的候选0/3。59离线、73/95JVM分别为各自范围，不相加为全产品进度。全产品分母未核。真实MuMu、物理手机/MIUI、X整机中断清零、D产品回灌与发布均没有新增通过或授权。

## 交付状态与下一步 / 资源
用户实际资源：Codex、Antigravity Gemini（不稳）、DeepSeek、GLM5.3Flash、glm5.2。
按新缺口重新分工：TXT低风险脚本窄修给GLM5.3Flash（禁用回退+独立可信视口）；D2并发/原子创建给glm5.2；J1请求/加载归属给DeepSeek。Codex核新接缝与证据，不默认更贵模型；Gemini不放关键路径。
三个独立E v4可并行，不设备、不最终APK、不改v3。单owner联合构建/冻结/限定MuMu等待源码与定向门禁核通过，届时Root主动给提示词。不自动向外部应用发消息，不自动恢复定时任务。
