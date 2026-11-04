# 任务1/2交付核查与Root补齐

核查日期：2026-10-08。当前状态：任务1/2限定交付核查通过，Root最终绑定与独立审查通过；任务3已提供完整提示词，等待唯一外部owner执行，设备未验。

## 任务编号/版本

- 1：CRA-JOINT-J1-SEARCH-RETURN-FIX-v1。数字保持不变，不重复派发原v1。
- 2：CRA-JOINT-RUNTIME-EVIDENCE-REPAIR-v1。外部交付保留，Root补齐目录为 `E:/creation-reading-assistant/joint-runtime-codex-completion-20261008`。
- 3：限定MuMu联合验收，等待任务2与最终绑定通过。
- 连续编号规则及可读状态见[编号索引](../../dispatches/2026-10/2026-10-08-numbered-task-index.md)。

## 输入指纹

活动仓库HEAD `e39a78da9c62f02614207cffb36969a32492eaea`，本轮复核暂存区空。原核查指纹见 `E:/creation-reading-assistant/joint-tasks-1-2-codex-review-20261008/fingerprints.json`。

任务1源码manifest SHA `f8f0777ee4e00fe80ba263044eb7d195cf6cfb26162bbdef6604fea9062d4d10`，992/992匹配；相对freeze-v2仅 `J1RealReaderJourneyTest.kt` 搜索返回测试单hunk变化。宿主APK SHA `440514acd90de7bf38edb744851eefcf345f2c3b9d4710984358df2c142552ae`，新test APK SHA `3b4c8ab563a6301573596e8e13b0d0d1ba9eae491d950a19f9ec2a8f52d85d8d`。

任务2原脚本manifest SHA `f6b709d475f944625681adb4872e63a937a8742f162c472cd2ffef42c5d40f08`，实际33/33匹配（原报告计32）；原证据清单实际21条，20条匹配，清单自引用条不匹配（原报告计19）。保留原件，最终清单排除自引用。

## 所有权与改动

任务1仅新E测试与test APK，原产品/runner/配置未变。任务2由Root独立E脚本worker补真实consumer合同，独立只读UI与恢复reviewer核关键接缝。Root维护D交接与最终E绑定，worker释放后才复制脚本。原交付目录不覆写；D产品/Git操作未授权。

## Standards

遵守活动AGENTS互斥所有权、输入未变且原证据充分时复用、E盘隔离与恢复证据要求。既有WIP、桌面/HOLD、正式书库、原损坏仓库及其他应用私有记忆库未触碰。外部报告自报ClaudeCode/DeepSeek；建议模型不冒充实际执行模型。

## Spec

设备前闭合J1三真实UI/观察方法与一纯route方法的固定映射、主run/producer run分离、真实payload列；联合完整31项顺序为J1 4→TXT普通12→短末8→D2 7。入口必须绑定完整冻结身份与实际文件SHA，拒绝隐式真实adb、缺consumer及运行时覆盖。D2每项先备份四Store文件，采集原始manifest与完整状态行，静止后恢复实际字节及修改时间，再进入下一项；精确staging账本和失败清理不扩路径保护。

## 环境

Windows，E独立候选，Python纯内存/fake离线链。任务1复用已核宿主，仅编译并组装新test APK。Root补齐不操作adb/设备，不构建Android，不重跑未变197产品JVM。

## 执行与结果

任务1原编译日志和exit0成立；Root实核新APK SHA、签名、包名/target/runner；独立UI源码审查通过。任务2实际运行链已补齐：Root最终运行37/37 exit0，未变恢复回归29/29与配置接线5/5 exit0复用，共三套71/71。独立J1审查通过，恢复审查完成此前反例核定，最后账本短名修正经恢复reviewer实际代码与原日志复核通过。Root发现worker报告短名声明未落实际函数、测试自行计算期望名，已仅在最终E修改真实write_bytes并将用例改为实际31项写入/回读，重新运行37/37；保留worker原件以记录差异。

既有9套离线检查的首次汇总非全绿：旧fixture与新固定方法合同不匹配已定向修正；旧e2e/chain/v4正向案例仍受其自身缺失baseline/source manifest影响，不能把累计unittest或synthetic数字写成联合设备通过。最新三套结果已按日志与机器exit核实；旧e2e/chain/v4失败记录保留，不声称全量旧门禁通过。

## 原始证据

- 任务1：`E:/creation-reading-assistant/joint-j1-search-return-fix-v1/REPORT.md`、`logs/compile-and-assemble-test.log`与对应exit文件。
- 任务2原件：`E:/creation-reading-assistant/joint-runtime-evidence-repair-v1/REPORT.md`及freeze清单。
- Root补齐：`E:/creation-reading-assistant/joint-runtime-codex-completion-20261008/logs`。
- 最终绑定预留：`E:/creation-reading-assistant/joint-final-bound-20261008`。当前992源码、41验收工件与双APK实算匹配；严格静态身份/31项合同验证通过，模板serial/run未绑定且runnable=false。设备须另生成磁盘有效config；静态验证未调用adb。

## 恢复与副作用

本轮无设备操作，因此无新的设备恢复结论。原freeze-v2模板当前SHA `6cd72564c5da568481140e93654bbb4f4c41129b5755cbfbd1cd523d26816d46`成立。旧离线verify脚本写出Root候选自己的candidate/evidence树，保留并单独核归属，不删除未知资产。

## 未覆盖项

模拟器设备批次尚未运行；不能称实体手机/MIUI通过。Back专属退出归因、队列排空、初始新save与X整机中断单点清零仍未验证。全产品分母未核，局部准备完成不替代全产品进度。

## 交付状态与下一步

任务1完成限定交付核查；任务2已完成Root补齐和唯一合流冻结，恢复独审已确认本范围无剩余可执行阻断，设备准备放行。任务3必须等待该核查通过，且唯一owner执行限定MuMu与逐项取证、完整恢复；无D回灌、暂存、提交、push或发布授权。

最终冻结指纹：APK manifest aaec29af81316dc4b45ad0b390e49911a77dcac6e6f503fb98bd8baf6d3f0960；acceptance manifest 39b92dc0814d70a756e36ce04001074a6eaaab56f5e25c5740f37300593314df；源码manifest 198fe92a0104e663514b271cb37db30495fdcf11582af6fb7e2aec6ba1f61588；未绑定31项模板 b503cf479c681dc9388bab2a6fefdc1cfd676b83463a2ee3f55adaeb0a8956c3。原始静态记录见最终E evidence/root-static-binding.json，日志绑定见 freeze/root-release.json。
