# 任务38-v4/39-v1实核与合同定向返修

## 任务编号/版本
2026-10-14 18:57香港时间。38-v4/39-v1已交付2/2，均9/9原定向测试实际重跑通过，完整安全合流门仍未过。下一38-v5/39-v2互斥并行，不新增编号、不重派36。

## 输入指纹
活动D HEAD e39a78da9c62f02614207cffb36969a32492eaea、暂存空。沿用已核Root-v2源码1019与36-v1双APK冻结证据，不重复产品构建。本轮读取并记录38/39实际交付SHA，39 backup_restore实际SHA3559afc65cd4edb3247d9bbde0d415266ffb49417203750f6813ade277633e47与REPORT匹配。原测试仅在Root自有E运行，不覆盖原交付。

## 所有权与改动
38-v5只库存/首次基线/冻结准入/schema/准入CLI，输出E:/creation-reading-assistant/task38-device-admission-restore-closure-v5；39-v2只备份/恢复/安全编排/模拟边界，输出E:/creation-reading-assistant/task39-device-backup-restore-completion-v2。38额外添加host/device_state.py是其测试夹具，后续应放tests/避免合流与39同名冲突；没有证据认为曾覆盖39目录。Root本轮只E取证与D全局文档。两项核后唯一owner合流，不设备/Android/构建。

## 环境
Windows Python3.13。38测试编译载入时只重定位TEST_TMP到自有E；39测试逐函数调用、自有E子目录、不调用main的系统temp清理。两进程独立避免同名device_state污染。全部device argv注入mock，无实际ADB/设备/Gradle。首次39核查脚本误用forwards属性中止，按实际add_forward/_global_forwards修正后完整跑并记录，未修改外部代码。

## 执行与结果
正确增量采纳：38 scope已按packages/roots/operations输出且实际36消费者接受；外部根有真实枚举；裸PASS冻结拒绝；39实际APK备份/安装/数据回写/外部文件恢复/mtime/mode及finally已有真实mock控制流，不再需要测试手工预恢复。

Root实际独立阻断：
1. 38冻结删除host/test signer且不提供manifest/root仍admission=true。树检查仍optional，producer未实际执行完整freeze_gate，只调用36格式消费者且构造identity缺期望freeze SHA。必须把实际冻结+树核验接入准入，不靠字段非空或候选APK哈希自证。
2. 38缺少真正已证absent包/DB合法路径：inventory_pkg仍要求所有包非空DB，实际mock还把test私有数据与host子串混淆，记录test组实际路径属于host。APK pm path精确匹配只修了一半，库存身份和合同必须一起修。
3. 实际38基线输出external_inventory map，39只读取external_files list；设置仅value/rc缺namespace/state；device_state.focus为原始mCurrentFocus字符串，39期待组件；文件元数据仅4字段，39恢复验证使用mode。各自fixture不能代替实际跨任务数据。必须固定一个baseline schema与库存回调，不重新堆第三套兼容fallback。
4. 39实际orchestrate传入admission_ok=false、OLD run、OTHER serial的完整基线仍执行测试callback（CALLED）；无完整准入调用、无首基线身份核对。
5. 39同serial别人新增tcp:7777映射被restore删除；当前仅按now-want/serial删除，没有run创建账。其他serial测试通过不能扩大为同serial他人会话安全。reverse把首列当local，需按真实host/local/remote协议。
6. 39模拟器直接支持am revoke-uri-permission --all并清全uri，标准Android14 ActivityManagerShellCommand无此命令；不能把mock自造命令当真实恢复。基线非空旧授权也不能用全清恢复。需现有真实可调用接口按本run对象释放，否则在改变前能力阻断。
7. 39stage probe无rc判断；多处pm/settings/映射/URI/focus回读未核rc；test -e && echo EXISTS || echo ABSENT仍可能把内部错误当ABSENT。精确成功协议必须有错误分支与边界证据，不能仅改字符串。
8. 39读取actual38基线进入真实编排发现上述schema缺口，现有测试9只有手造baseline。库存callback也仍旧tuple签名，与上一约定dict不一致。无需改已核36；producer/consumer两owner定向修复。

## 原始证据
[38实际核查](<E:/creation-reading-assistant/tasks38v4-39v1-codex-review-20261014/38-verification.json>)；[39实际核查](<E:/creation-reading-assistant/tasks38v4-39v1-codex-review-20261014/39-verification.json>)；[脚本](<E:/creation-reading-assistant/tasks38v4-39v1-codex-review-20261014/verify.py>)；[38重跑](<E:/creation-reading-assistant/tasks38v4-39v1-codex-review-20261014/38-tests.log>)；[39重跑](<E:/creation-reading-assistant/tasks38v4-39v1-codex-review-20261014/39-tests.log>)。
[AOSP Android14 shell命令实现](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android14-release/services/core/java/com/android/server/am/ActivityManagerShellCommand.java)。此源码检查是标准命令可用性边界，不是MuMu版本或实机已运行证明。

## 未覆盖项
设备全NOT_RUN，D/F NOT_PROVEN，X/实体/MIUI/发布不扩大。36-v4独立离线已核，不重派；完整主机安全合流0/1，设备阶段未执行。全产品分母未知，新主机工具未冻结。

## 恢复与副作用
未实际设备/正式数据操作，无Android/D产品/Git/发布。Root只自有E合成材料，无外部消息/子代理/定时任务/节点切换，私库和原损坏仓库/桌面HOLD保护。

## 交付状态与下一步
保留上述正确增量，两条实际缺陷返修38-v5/39-v2，立即并行。38建议DeepSeek，替换GLM5.3，DeepSeek粗估40–70min；39建议GLM5.3，替换DeepSeek较强推理档，粗估60–100min。实际菜单优先，不据执行者自述推断GLM5.2可用，不外派GPT/Claude。Root核后唯一owner整合接口与工具冻结，再限定MuMu；当前不设备/打包，不制造重复审计。

共同基线固定schema=cra-first-baseline/1：scope沿用36 packages/roots/operations；external_inventory保持根存在性/原始证据，external_files是从其成功files去重派生的唯一列表；settings记录namespace/key/state/value；device_state记录约定组件或Home及raw证据，不能将dumpsys原文作为组件；文件每项path/size/SHA/mtime/mode/必要owner与context；pkg_states记录已证installed/absent，DB组存在状态明确；库存回调dict含ok/files/receipts/problems。39必须直接消费实际38产物并核身份/准入，测试不得手造替代prod schema或补齐遗漏字段。38不改恢复模块，39不改38，公共合流单owner。
