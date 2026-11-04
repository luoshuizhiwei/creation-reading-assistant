# 47-v1真实执行链实核与47-v2定向返修

## 任务编号/版本
2026-10-15 11:17 香港时间。用户“e完成了”按47-v1交付核查。Root只核查、规划、交接，外部提示词执行；无子代理或设备操作。

## 输入指纹
D HEAD e39a78da9c62f02614207cffb36969a32492eaea、暂存空；既有WIP保护。47-v1唯一E根实存。原45-v1 freeze SHA fccaf38d8f5c39f18a3b0ac0de3aa47e7bec65773d3e521dc79a7d125395e631继续绑定，未重APK；47现存工具清单独立verify checked26、无漂移。原测试中的最后一项先重写manifest再验，Root不调用该项，直接核现存manifest。

## 所有权与改动
47-v1只有host五项/测试与证据，新增RealCommandRunner与跨动态加载注册表，串联已有38/36与45恢复。实际正向、invoke失败/消费失败恢复证据限定采纳。47-v2唯一写新E根，只补发现缺口，不改原交付/共同Android/双APK；公共入口同一owner，不双边拆分。

## 环境
Python3.13，pytest无bytecode/cache、独立rootdir/confcutdir/basetemp。未Gradle、未ADB/模拟器、未访问桌面或正式库。仅边界注入和本地主机CLI。

## 执行与结果
原测试23 passed,1 deselected in40.82s，exit0。现存工具manifest verify26项PASS。默认plan/错误模式/参数拒绝/独立serial dest、实际编排正向与两种失败后恢复限定采纳；不把绿测试等同完整合同。

### R1：合法CLI仍无法接续
Root按报告命令补齐owner/device_serial/live_serial/scope实际子进程执行，exit1、real_mode_runner_missing。integration main恒传runner=None；recovery CLI同样无runner入口。任务报告称可无缝设备执行不成立。没有真实ADB被执行。

### R2：real路径没有执行真实准入合同
check_real_mode_admission只验owner非空与两个调用者字符串相等；未核本轮独立许可或实际现场身份。主编排仍固定evidence_level=SIMULATED、check_admission expect_real=False；只读36-v4 cmd_invoke也expect_real=False。Root将所有外部命令截获至离线DeviceState，但适配器标记is_injected=False，owner填任意未登记字符串，运行结果仍ok/admitted/restored=true且无OFFLINE标记。该反例证明非注入分支可接受模拟准入，不能据此称真实设备成功。边界伪装本身不是产品缺陷，问题是实际代码根本未区分两种合同；不能靠改类名/标志或复写DEVICE字段冒充现场证明。

### R3：工具清单缺失绕过
integration从未调用verify_manifest；recovery仅文件存在时验证。Root传入确实不存在的工具清单路径，用原正向生成的真实基线与实际门禁运行recovery gate，exit0、admitted=true、capability_ok=true、ok=true。必须所有可执行入口共用不可缺省门禁；缺失/漂移在外部命令前拒绝。测试先write_manifest再verify属于自证，不证明交付时清单未漂移。

### R4：恢复范围仍需收口
保护取证与hv.mkdir在try/finally之前；异常可能跳过收尾。finally creation_ledger=None。须核实既有函数对部分暂存失败与异常的行为，在自有范围用外部边界故障反例补必要收尾、持久恢复账与原始/恢复双错误传播；不能在尚未修改目标数据时盲目重装。此项源码可见风险，尚未宣称设备损坏或真实复现。

## 原始证据
[23原测试](<E:/creation-reading-assistant/task47-codex-review-20261015/original-tests.log>)；[现存26清单验证](<E:/creation-reading-assistant/task47-codex-review-20261015/existing-manifest-verify.json>)；[报告CLI反例](<E:/creation-reading-assistant/task47-codex-review-20261015/documented-cli-result.json>)；[非注入标记路径反例](<E:/creation-reading-assistant/task47-codex-review-20261015/noninjected-boundary-result.json>)；[缺失manifest准入反例](<E:/creation-reading-assistant/task47-codex-review-20261015/recovery-missing-manifest-result.json>)。整体consumer未执行的其他旅程FAIL不冒充已执行单旅程FAIL，Root只采实际执行旅程的限定结果。

## 未覆盖项
合法生产命令/现场准入/工具门禁未过；设备NOT_RUN。SAF Provider、D/F、X、实体/MIUI/发布等保持旧边界，Android全产品分母未知。报告中的实体设备接续并非授权。

## 恢复与副作用
反例所有命令由离线backend解释，不调用真实设备；CLI缺runner提前阻断。所有新文件在自有E，原测试清单不写，D产品/Git/桌面/HOLD/私库/网络/定时任务不动。

## 交付状态与下一步
47-v1部分采纳、需47-v2定向返修，不进入MuMu。保留原实际编排，单owner只补R1–R4，先实际命令最小串联和反例，不新造框架或广泛审计、不重APK。外部推荐Gemini3.8（菜单可用时），替代DeepSeek强推理/GLM5.3；不派GPT/Claude/不可用GLM5.2。一条提示词，DeepSeek约60–100min。没有已证且不重复的独立缺口，不为数量另造任务；公共准入、runner和恢复入口必须同owner，核过后才派唯一MuMuowner，用户无需手动启动设备。
