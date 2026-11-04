# 80-v2 A/B Root核验（2026-10-21）

任务编号/版本：80-v2 / Root。仅核交付及原始定向证据，未编译、未发设备命令、未启动自动化或外部消息。

## 输入指纹、所有权与改动、环境
A五份候选源码及四份patch实际SHA逐项匹配ready；Material2082c121c3c0d26cd9067e2ba2927cff2f0f7210f67965ee81d35c439ba9ef67，测试判定对象8a310cc102f460296aae942b7a6b774c14f1b48c8e4512ef8789a4993251ec33（test/androidTest相同），ReaderSelectionToolbar f4cf51b72dd1e785b8cd166411276b0c144eee182ca328c80f2c4dddea8d1e5d。B七项交付实际SHA全部匹配ready，模块401acc76acb6d41162e756403bf56f13a6f14ef685db2ba6b6657b633539eadf，等价runner d7fca89e1c701007832c72dc2768db707fa867937d14b347d47535ed410c401c。共享59 Material仍41870a3a…588a，尚未合流，C目录不存在。定向进程查询无task80/GradleWrapper活动；这不替代文件owner核实。

## 执行与结果、原始证据
A候选已从保存前唯一testTag节点读SemanticsProperties.Text，使用独立capturedSelection核payload逐字相等，再核导出完整引用块；解析失败保留cause。判定helper移出main并在两个测试源集字节相同。Root接受ReaderSelectionToolbar仅testTag的最小取证接缝，不扩大API或增加产品测试helper。testTag会增加测试语义属性，不采纳“语义完全不变”的绝对表述；不改变可见文本/权重/截断行为。真实UI能否唯一命中及完整读取仍待仪器验证。
原始A evidence/junit-verdict-test.log为OK(19 tests)，mutation日志为Tests run19/Failures9。采用其已有充分Kotlin离线证据，不再次运行；这不证明完整androidTest已编译或UI已验。
B原始offline-result19/19及报告明确真实patched runner/run_recovery导入执行，外部spawn/设备/bounded边界模拟；不称真实ADB命令或真实进程树验收。实际候选修正硬导入、独立continuation scratch、bounded状态+proof联合消费、历史与最终状态分离。预算3360阶段+2520恢复+60余量=5940，新增840并入恢复预留；旧严格阶段及至少1200保留。授权绑定serial/attempt/host/archive/baseline，缺授权保全阻塞。接收为合流候选，设备仍NOT_RUN。

## 恢复与副作用、未覆盖项
本轮无设备动作或新恢复欠账；79-v5旧文件已删除且续接恢复已核，不得重清理。A/v2实际276995B、B/v2实际214749B，共491744B。A略高于256KiB预估但低于1MiB上限，不因此制造清理。未写共享59，无新工程缓存/APK复制。自动化保持PAUSED，未切外部模型。

## 交付状态与下一步、停止条件
A/B离线候选接收，下一步80-v2-C唯一集成/构建owner：同批合入A五文件（仅一处产品testTag）、B接缝/模块，编译按真实错误迭代，保留历史FAIL。因产品tag变更，host和test都需必要构建，不再错误复用旧host；覆盖前按已知冻结索引定位原字节副本，唯一才单份保全。不能另复制环境。C预估源码/离线日志<512KiB；若必须同时保全唯一旧host+test，增量约64MiB，加证据上限96MiB，实际写同REPORT。无必要旧副本时不重复复制。
完整Android源集编译、新APK身份及新绑定闭包未验，由C闭环；原19项纯判定离线不因编号重跑，仅集成变化/失败有需要才补。静态设备就绪不等设备授权。下一设备批次需Root明确放行及导出收尾许可；未来URI/archive未知时，不捏造授权、不复用旧79-v5许可。C应交明确授权时点/续接协议，单attempt后不会用新main重试。无需重做A根因、B模块泛审或已充分旧消费者。owner/输入/预算/恢复合同不明停止依赖工作。全产品分母未知，无新模拟器或实体手机结果。
