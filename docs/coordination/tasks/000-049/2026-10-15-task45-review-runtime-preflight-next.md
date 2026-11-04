# 45共同产物核过与运行入口/只读MuMu预检

## 任务编号/版本
2026-10-15 08:29香港时间。用户“完成了”后实核45-v1。1020源/63验收/双APK/强冻结产物采纳，执行入口与说明不一致返45-v2（仅主机工具）；独立46-v1只读MuMu预检可并行，无安装/卸载/仪器/设置写入。当前仍Android-only，桌面44撤销。

## 输入指纹
D HEAD e39a78da9c62f02614207cffb36969a32492eaea，暂存空；tracked状态229项不是clean，既有WIP保护，不由此断言45改动。45源清单逐1020全匹配，tree d8c90257c54e835fcfc6c25ffb5f368b960b20163cafd48fb69ea7668380a953；actual source manifest SHA78db77454114857d4f00327b452a769d544dc832e0a2658c9394aaa336c52ba5；验收63清单SHA70874c03e18b1830b11da9d84a666610165fe6931b47540f0f5f7f4b243874da。freeze SHA fccaf38d8f5c39f18a3b0ac0de3aa47e7bec65773d3e521dc79a7d125395e631。

## 所有权与改动
45-v2唯一写E:/creation-reading-assistant/task45-android-joint-candidate-freeze-v2，仅host/CLI/模式/恢复入口/工具冻结/合同及必要定向测试；45-v1源码、APK与freeze全部只读复用，不Gradle或重APK。46-v1唯一写E:/creation-reading-assistant/task46-mumu-readonly-preflight-v1，只读当前MuMu身份、占用、系统设置/包与保护路径可用性元数据，不做恢复或测试写入。两线无共写/无共同构建，46设备只读owner，不能扩大设备变更许可。Root只核查/交接。

## 环境
Python3.13，实际调用已核项目强冻结器verify返回exit0/PASS；SHA5b4dbfe577234eeb895d39a0da2cdb1976debd271af4e7c0d3a39d2a35141e56。原14测试中13项通过，另1项强freeze测试由Root独立真实verify替代，不重复相同SDK验证；原test模块只在内存改变ROOT，把写manifest/临时数据导向自有E，原host/产物只读。13 passed/1 deselected，24.54s。无Gradle/ADB/设备/子代理。

## 执行与结果
强冻结及SDK元数据真实成立：host APK SHA7ab63a0b430b36d461329275d2d6824e5df7bd1a73ada8556a68f11b9777c91b、51814124字节；test APK SHA7b1532c0b3d8d43be063a4ae010ce0195a1107c68b78b7666cb235d2ea37c2e2、9609062字节。实际signer双方05249d786e3d3d117bb5bd7b1f83512a0c9b8f1ee4e502112c4b2d9ccbd7b9a1；runner com.creationreadingassistant.testing.CraIsolatedTestRunner；host com.creationreadingassistant.qahostj1persistv2，test加.test。真实构建记录24.94s，构建与强verify无需重做。
报告有源清单SHA、signer、工具SHA、构建耗时/锁路径、instrument reader目录和clean等错误；实际文件是acceptance目录。上述由Root按冻结原件纠正，不因文案错误重建产品。
运行入口尚未可交接：integration_chain无CLI main/parser，实际执行文档plan命令exit0但无输出；函数默认execution_mode=mock，非plan。recovery_cli仍旧39参数gate/backup/restore/orchestrate、--serial/--run-id/--mock，非文档--device_serial/--action；缺--mock默认选system_runner，未统一owner/live serial/mode/作用域/工具冻结入口。Root注入execution_mode='typo'到实际函数，仍admitted=true、ok=true、发出107个mock边界命令且包含instrument，不能将未知mode自动当mock。real永久阻断是本任务未设备执行的事实，不等于未来real接口已经可用；不能只删一行阻断就进入设备。
45-v2须补真正可运行CLI及默认plan、未知mode拒绝、所有入口同一准入、完整实际运行接线和明确未来设备许可；原冻结SHA固定绑定，不用从同一未可信文件重算expected自证。46只读预检与该修复独立，不等待APK、不启动产品、不使用错误文档命令。

## 原始证据
[强冻结实际verify](<E:/creation-reading-assistant/task45-codex-review-20261015/strong-freeze-verify.log>)；[verify进程](<E:/creation-reading-assistant/task45-codex-review-20261015/strong-freeze-process.json>)；[13原测试](<E:/creation-reading-assistant/task45-codex-review-20261015/original-tests.log>)；[产物实算/报告纠偏](<E:/creation-reading-assistant/task45-codex-review-20261015/artifact-review.json>)；[空plan CLI反例](<E:/creation-reading-assistant/task45-codex-review-20261015/plan-cli-review.json>)；[未知mode仍执行反例](<E:/creation-reading-assistant/task45-codex-review-20261015/mode-counterexample.json>)。

## 未覆盖项
共同源码/APK/强冻结限定通过；实际设备执行入口未核过，MuMu全部NOT_RUN，46未来只读观察不是验收。SAF Provider/仪器9组/D-E/D-F/X/实体/MIUI/发布等仍按原边界；Android全产品分母未知。错误报告中的“所有目标达成/可直接设备执行”不采纳。

## 恢复与副作用
原产物、D产品、设备未改。原测试写入定向E路径，无原输入缓存；counterexample全部mock。无正式库/桌面/HOLD、私库、Git写入、外部消息或定时任务。写文档前字节校验并保全原文。

## 交付状态与下一步
45-v2仅主机接线不重APK；46-v1只读当前实例，不沿用文档固定serial，不连接未确认实例，不启动/停止应用，不安装卸载、不force-stop、不设置写入、不创建设备文件或读取正式书库。记录preflight_only=true、baseline_final=false、device_execution_authorized=false，不能冒充最终可变操作前基线。两项核后再由唯一owner执行限定模拟器旅程。
建议45-v2用Gemini3.8（替代GLM5.3或DeepSeek强推理），46-v1用GLM5.3Flash（替代DeepSeek普通/Flash）；按实际菜单，不断言模型价格/能力已实测。DeepSeek粗估45为40–70min、46为15–30min。最终总结最下面“这是任务45-v2。”“这是任务46-v1。”。
