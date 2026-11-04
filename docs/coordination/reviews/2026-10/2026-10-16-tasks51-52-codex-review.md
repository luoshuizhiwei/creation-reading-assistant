# 任务51/52核验及返修

2026-10-16 10:55 +08:00。交付2/2，进入设备批次的核验通过0/2。没有设备操作或Android构建；不影响既有v13产品修复结论。外部下一轮为51-v2、52-v2，在原独占目录接续，不新增完整候选。

## 任务编号/版本

核验51-v1、52-v1；下一轮返修51-v2、52-v2。

## 输入指纹、所有权与改动

51实际脚本SHA b2ec638ea10128d6e377c40812ae160e22ddbe1db1dbfc322fcca2a439b89f53，与其REPORT/handoff所列c575e199…不符；52脚本SHA 05cbfe5b1f743ba8e0776340911a6adcf3deaec32d1e56b16466d2b6a3fe3969匹配报告。两目录未见ready.json。未发现项目Python/Gradle占用，E剩余99836354560字节，暂存区空。禁止D产品回灌/Git/发布与设备并发。

## 环境、执行与结果

Root实际执行51离线脚本8项PASS、52 unittest 13项PASS。前者主要测自造fixture；后者FakeDevice没有严格执行Android冷读回执解析，均不足以证明接线可用。

Root另用真实函数、真实v13冻结输入和原严格消费者的显式synthetic夹具进行离线反例复现：

1. 51真实模式直接返回dry_run_disabled，run_clamp_execution仅生成配置；报告命令所调run_clamp_v9.py本身没有--config参数解析且硬编码旧目录/run。未交付可执行的五步生产入口。
2. 51按不存在的freeze.apk等字段读取，真实freeze.artifacts下的host/test包、runner、APK路径及SHA未读入，生成配置为空。
3. 51自行重写consume_de绕过Root运行绑定与缺项判断：strict_fixture明确synthetic；传入非空missing和不同driver_run_id仍all_ok=true、contract_complete=true。反例仅证明主机缺陷，绝非设备证据。
4. 51账本/恢复读取run_id，真实生产schema是runId；真实形状被误拒。脚本dry_run还写入任务52专属目录，应移到测试临时fixture。
5. 52进程终止回执写argv/rc/stdoutB64/stderrB64，Android Task35EvidenceFixtures.kt:521起要求command/returncode/stdoutBase64/stderrBase64，当前冷读无法采用；FakeDevice未模拟该解析条件。
6. 52绑定push失败触发_finalize缺host_package参数的TypeError，清理未调用。
7. 52只记录任意freeze文件SHA、不校验身份；假freeze仍执行全部4个模拟仪器步骤并allOk=true。
8. 52将远端test探测rc=2传输/命令失败当目录不存在，继续全流程并allOk=true。
9. 52拒绝整个父evidence_dir非空，clamp兄弟目录已存在即拒，破坏两路合流；应只互斥自身子目录，统一批次身份由Root绑定。

## 原始证据

E:\creation-reading-assistant\tasks51-52-codex-review-20261016\reproduce.py
E:\creation-reading-assistant\tasks51-52-codex-review-20261016\reproduction-results.json

## 未覆盖项、恢复与副作用

本轮纯主机离线，零adb/MuMu/安装卸载/构建，未访问正式书库，不需要设备恢复。未运行真实SAF或clamp，不宣称DE.strict通过。现有Android与v13冻结保持只读。

## 交付状态与下一步

51-v2独占原task51目录，修真实clamp执行/原消费者/真实冻结schema及兄弟目录兼容；52-v2独占原task52目录，修Android冷读回执/身份/探测失败/收尾/兄弟目录兼容。两路可独立返修，仅离线定向验证。Root核验通过前不合流、不启动设备。返修提示词已准备不代表已转发或运行。没有增加第三项泛审查。
