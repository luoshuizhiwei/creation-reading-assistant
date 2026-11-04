# 16-v5与23-v1实际成果核查

## 任务编号/版本

Codex-review-16v5-23-v1。用户“完成了”后核实际源码、原始XML/log并由两名独立只读审核者复核。两项均有有效实现与测试交付，新增问题未解决，暂不把两份生产增量合入共同候选。下一批16-v6/23-v2为互斥文件的小范围收敛，不重做调查。

## 输入指纹

HEAD e39a78da9c62f02614207cffb36969a32492eaea、暂存空。Root共同1001仍匹配manifest d4d062a807a421488adb8fa50f441cdda69c935c4d2c9fabd5c8625034b01db4与tree5cd1db936648b0121873889358d568ee69916fb69b361415beb45d1548cde08b。

16-v5 1005项逐项SHA一致，相对Root5改/4增/0删，manifest ebd245793c5c9f0994e0a2b83b76b63312b251e1a95499fb120c811dbb4165e4、tree6b4e30814685828b583acb609e5ec711e125f8e53e2e57b248d5072cb067c806。

23-v1 1001项逐项SHA一致，对Root3改/0增删；文本清单SHA a4405555bec281c6181c4f6c8d53943fdc5257ed9b1c4cee7c8b7e2be34789fe、tree2652070260caa8e7b38213979cc7d07448c9b00c7c3d3d5bbea4a8216b93908c。

## 所有权与改动

16为Book/EPUB保存链、真实VM派发及章加载接缝与直接测试，新增共用顺序对象有两条真实生产用途，可作为必要共享代码。23限定ReaderNotesExporter、ReaderNotesSheet导出错误hunk、NotesExportTest。两组无文件交集。

Root只写自有E核查/提示词与本轮D协调记录，不修改D产品或外部候选。原报告保留，不为报告瑕疵另派材料任务。

## 环境

16可见标识解码为workbuddy AI/global:deepseek-v4.1-flash；23自报ZCode/new-provider-2/global:glm-5.3-flash。推荐路由与实际标识分开。

Root只读进程核查见ADB进程，无本轮MuMu实例进程；不由此推导旧任务完整恢复或设备可占用，不启动/操作设备。下批仍无设备/最终APK，JVM用共享排他文件句柄锁串行。

## 执行与结果

### 16-v5有效成果及必须收敛的身份层

green_v5_v2真实执行4类112项：F11、Book25、VM73、EPUB3，0fail/error/skip、机器exit0。ce_v4_rule 11项3红，ce_order_in_io 73项1红，确实区分墙钟判据和IO入口取号。final_v5 XML来自缓存，与首次执行范围相同，不当第二次独立执行；androidTest实际编译通过仅是编译证据。

两个repository共用序号源、mutex及按book记账；两个保存分支在真实onAction同步边界取号，章加载在导航派发取号并传到EPUB，保存成功后才记账，错误向上传播。这些已证成果保留，无需重跑全部旧877。

**会话层条件式回归必须移除或证明真实激活边界。** ReaderViewModel.kt155固定sessionEpoch，ReaderProgressSaveOrder.kt59–60以session优先。若同书旧VM仍存活、较新VM已保存，返回旧VM后的合法新order仍被较旧session拒绝。ReaderRoute采用backstack作用域hiltViewModel且存在返回/pop，因此VM lifetime不等于活跃阅读区间。上述条件下算法错误已确认，具体NavHost触发没有运行证明，不能写现场故障已复现。

repository默认参数创建较新session还可能提升门槛；当前三个生产调用点都显式传参，未发现实际生产默认入口触发，不另计已发生缺陷。人工old/new token测试不证明真实旧Compose闭包携带正确身份。

**集成决策：16-v6只保留已经证实的dispatch order+mutex，删除尚无真实可达旧回调证据支持的session层，不新建激活身份框架。** 明确保留旧Compose迟到回调/D真实重测量未证，不以缩小措辞宣称全生命周期已关闭。后续唯一owner的同版MuMu仍核恢复/回读，不重复静止采样。

### 23-v1有效格式证据及实际缺口

红XML12项6失败→终版12项0fail/error/skip，原5合同保持；该格式红绿可复用，不是SAF/分享UI通过。

**去重键会合并不同来源。** listOfNotNull+U+0001拼接丢失字段槽位、允许分隔符碰撞，而且遗漏InspirationSourceInfo.locationLabel。使用已有数据类完整相等即可，不新造key/helper。这是数据边界的真实来源区分，不为不可能场景添加防御。

现有locationLabel/progressPercent快照未输出，与“章节/位置说明”最小目标仍有缺口；只补原值，不推算新位置或加可点击链接。多行摘录仅首行加引用前缀，后续可脱离摘录区；新增测试只有文字contains，必须补结构断言。

解析层和NotesSheet新增runCatching捕获任意Throwable，虽然Toast有反馈，仍违反新AGENTS的窄异常/不吞错误规则。只在payload解析数据边界处理明确SerializationException；内部错误不统一包装成来源损坏。移除一次性格式helper，保留实际由导出/分享复用的必要反馈接缝。

**集成决策：23-v2沿原三文件修去重、已有位置字段、引用结构、窄异常，预算小幅；不扩大为跨书导出、素材UI或新功能。**

## 原始证据

- [Root逐项核查](<E:/creation-reading-assistant/tasks16v5-23-codex-review-20261012/verification.json>)。
- [16-v5报告](<E:/creation-reading-assistant/reader-save-order-task16-v5/REPORT.md>)、evidence/source-sha256.final.json、logs/runs/green_v5_v2与两反例原XML/log。
- [23-v1报告](<E:/creation-reading-assistant/inspiration-source-export-task23-v1/REPORT.md>)、evidence/final-source-sha256.txt、evidence/red与green原XML/log。

## 未覆盖项

D真实无手势重测量、旧UI回调真实可达性、同版双APK/MuMu、SAF/分享实际界面未验。全产品分母未核，112/12均限定测试分母，不作为产品百分比；X整机清零、Back归因/队列、初始new save、实际LAN/实体硬件/签名发布旧边界保留。

## 恢复与副作用

16报告披露运行器误指旧16-v4并删除自建外来run目录。本轮未采用该run，后续须核脚本硬编码路径；源码原清单一致不等于缓存元数据绝对零变化，未有前后元数据证据就不外推。23中间失败日志覆盖已发生，原核心红/终绿仍完整，不伪造历史恢复。Gradle离线仍可能写共享缓存，不能称只读复用。

Root未构建/设备/修改候选/操作Git，只记录实际核查。正式书库、损坏原仓库、桌面/HOLD、私有库、订阅网络保持边界。

## 交付状态与下一步

本批核查完成，两生产增量暂不吸收。下一批2个可并行离线提示词：16-v6推荐DeepSeek，中等复杂度45–75分钟；23-v2推荐GLM5.3Flash，中低复杂度30–60分钟。二者文件互斥、Gradle排他串行，均自有E完整交付；不自动向应用发消息，不以提示词已提供当作运行事实。

这轮收敛已确认缺陷与过度身份层，不重新盘点产品，不制造测试准备/报告任务。过审后Root整合，唯一owner再完成同版冻结与受影响MuMu。无D产品回灌/stage/commit/push/PR/发布授权。
