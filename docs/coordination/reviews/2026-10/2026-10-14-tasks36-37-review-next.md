# 任务36/37实核与并行补齐

## 任务编号/版本
2026-10-14 00:28香港时间，36-v1/37-v1已交付。Root只核查/规划，不恢复自主实施或启动子代理。36源码/APK/强冻结限定采纳，运行接缝返修；37审计交付采纳并由Root修正发现分类，不重派。

## 输入指纹
Root-v2原1019/tree36331e2af4544586eba32e1766854e69afbd61b8b7d472697e0e945470e77f5e，source manifest SHA5599ce1c2c0b9f001db2ab8e45bdd98074389fcb3e2f9590f01cab05fb88a762。
36实际源1019逐字节全同。其source rebound SHA01dbe9dcfceba468f54c72ac0c90e3e4c6af6528d907b65fa4d70822cb37de12；验收63（原58全同，只新增5个runtime文件），rebound SHAcd2361233d6375ac124222c5bea804d874e09d736f45952854045dab57ebea3d。
36强冻结SHA8505a76a4710f197fc4f9f027f71379f289be32b304bc7df26158210f9091d9b。

## 执行与结果
Root实际逐文件1019/63核查、运行13Root强冻结verify返回exit0/PASS；最终宿主SHA7ab63a0b430b36d461329275d2d6824e5df7bd1a73ada8556a68f11b9777c91b，测试SHA800946fccd7ac96495922b12a02352b30f88664c2bd6001fd9a66e2275e350ef。Lint原日志exit0可采纳，不重构建。
Root独立调用实际36消费者：URI=BLOCKED/无实际字节证明、clamp旧run/gate=false/无reader身份、temporary locator与updated_at变更、无返回/续读阶段均误接受；实际Task34 phase名称反而拒绝。实际准入CLI函数所有参数为不存在路径仍exit0称输入齐备（代码无设备调用，Root未执行ADB）。因此冻结通过不等于可执行验收。

## 原始证据
[Root指纹/APK/强冻结与反例](<E:/creation-reading-assistant/tasks36-37-codex-review-20261014/verification.json>)；[原强冻结实际verify日志](<E:/creation-reading-assistant/tasks36-37-codex-review-20261014/freeze-verify.log>)；[虚假路径准入实证](<E:/creation-reading-assistant/tasks36-37-codex-review-20261014/admission-fake-paths.json>)；[输入身份](<E:/creation-reading-assistant/tasks36-37-codex-review-20261014/input-identities.json>)；[37发现分类](<E:/creation-reading-assistant/tasks36-37-codex-review-20261014/task37-classification.json>)。

## 所有权与改动
Root仅E核查与D全局文档。下一36-v2只运行调度/采集/消费；38-v1只准入/基线/恢复代码。两个自有E候选、只写互斥主机文件，不改任何Kotlin/产品，均不设备/最终APK。36-v1原目录/APK/冻结保留只读，不新建重复构建owner。
36-v2：task36-joint-runtime-bind-freeze-v2/runtime_bind_check.py、runtime-bind.json、真正joint_runtime_execution.py及adapters/离线测试；不改admission/restore文件。
38-v1：task38-device-admission-restore-closure-v1/device_admission_gate.py、device-admission-bind.json、device-execution-contract.json及基线/恢复模块/离线测试；不改runtime消费与调度。Root核查后合流由唯一36 owner绑定新验收冻结，当前不复跑Lint/APK。

## 36确定阻断与最小落点
1. 只有绑定JSON和自测函数，没有实际dispatch/harvest入口接到joint运行；CLI不能读取真实bundle。实现真实调用路径与供后续设备owner使用的执行计划/采集器，但本轮只能模拟外部命令。
2. Task34真实phase是ordinary_reader_exited_to_top_level、temporary_cross_chapter_marker_visible等，消费者却查baseline/temporary。按真实phase消费，强制完整所需阶段、序号/时点和逐阶段12列行；临时期间所有合同保护字段冻结，不能把返回后允许变化的6列放到临时期间。
3. 生产者UUID不是天然缺陷：允许在单次instrumentation精确before/after新文件与方法/进程/时间窗回执下建立driver_run→producer_run映射，保留原UUID，不要求修改Kotlin来硬等driver UUID；历史/歧义/重复不放行。
4. D/E新函数复制了弱逻辑，忽略run/gate/实际URI/字节/恢复/身份；直接调用已核task35-v2严格summarize/evaluate_strict，避免第二套弱规则。URI观察接缝现已存在，36旧“无接缝”结论错误；BLOCKED不能算完整PASS。
5. B原始instrumentation必须实际绑定类/方法和真实状态，不能仅OK字样/shell exit0；D/F未触发/未观察NOT_PROVEN。
6. admission缺参数拒绝不等于真实准入。全部输入存在性/路径范围/APK SHA/签名/runner/强冻结/实际树/基线admission_ok/run/serial/数据组/原件备份/触碰根都需实际核验，无实证PENDING非0，不能靠字段非空返回0。
7. 恢复13步JSON仍是说明，需可执行接口与实际验证账：数据/DB组/APK/设置/映射/权限/创建物/设备状态，逐卸载前验证，失败禁止破坏。38实现并用mock边界验证，本轮无真实设备。

## 37审计结论复核
F1是已知待接线，现在36仍未实现，纳入返修。
F2随机producer UUID可通过精确采集映射绑定，不必改Kotlin；36要求同UUID且无映射确有问题。
F3按要求类型使用不同证据键不天然错误；消费者应显式分支并拒漏项。
F4真实Room完整实体序列化列名常量不是“缺列”实证，信任DAO/实体合同，核实际row值即可。
F5来源合同保留旧input可作来源记录，但实际运行层必须绑定新输入与等价关系。
F6原32 gate固定33-v2且PENDING是历史依赖证据，不应替换成36自己的PASS循环自证；新device准入应独立核实际freeze/基线等。
F7旧需求目录作为历史参考合法，当前调度必须以34/35新正式合同为权威，不混旧probe/JVM充实测。原7“确定缺陷”表述范围过宽，按此分类收窄，不制造新审计返修任务。

## 环境
Windows/PowerShell，Root未Gradle/ADB/设备操作；宿主快照只见ADB server，未来owner仍需实查。推荐36-v2 GLM5.3（换视角、避免旧上下文沿用），替换已配置DeepSeek Pro/Flash；38-v1 GLM5.3Flash，替换GLM5.3。实际菜单优先，不外派GPT/Claude，不切订阅/反代。DeepSeek粗估36约45–75min，38约40–70min，均无需构建锁/设备。

## 未覆盖项
MuMu A–E/实际恢复全NOT_RUN；新runtime缺口未闭，device_ready=false。D/F NOT_PROVEN，X/实体/MIUI/LAN/R8/签名升级/发布不扩大。全产品分母未知。36已核APK保持产物层成立，其旧63验收冻结不能代表后续新runtime。

## 恢复与副作用
无设备/正式数据访问；D HEAD e39a78da9c62f02614207cffb36969a32492eaea、暂存空，无D产品/Git/发布。Root反例只自有E虚拟材料，未创建假输入路径、未触发ADB。保护原损坏仓库/正式库/桌面HOLD/Claude与DSH私库。

## 交付状态与下一步
36/37交付2/2不代表验收通过。36冻结产物采纳、runtime返修；37审计结束不重派。下一36-v2+38-v1两线可立即并行，待转发/执行核实；完成后Root核查、唯一owner合流与新强冻结，再限定设备。两线本轮都不构建APK、不跑设备，复用原源码/Lint/构建证据，避免工具仍变化时反复冻结。
最终用户总结末行分别“这是任务36-v2。”、“这是任务38-v1。”。
