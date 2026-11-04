# 任务10-v2核查与两项并行返修

核对：2026-10-10。任务编号/版本：10-v2 CRA-JOINT-31-ACCEPT-CLOSURE-v2。
交付状态与下一步：诊断材料已交付并核查，产品修复未完成；下一10-v3修恢复实现与定向JVM，11-v1补SQL目标拒绝门与离线测试。两者独立E候选、文件所有权互斥，不使用设备、不构建最终APK。两项核查后才由唯一owner合流、冻结并验收。当前同功能修复已有授权，无需用户再次选择修复方案。

## 输入指纹、所有权与改动

Root实算最终996源码、42验收工件均与10-v1字节一致，交付清单1146/1146文件存在且SHA匹配。最终宿主APK SHA187ee9e7a43d08560c0af0372fc06c560f3c30d8779e5138e58546246c11dd57；测试APK SHA4ad918bb4a02591e8a96b781d8786948fa83c2b05407fe704e4b79672adce6f1。

源码清单SHA996d70c2acf7a9b19151f3bb1c2cfae103c15dfb4e1b84eacab57dce03c31dfa；验收清单SHA1dac059a4880e306f56a260a3cbc07bbed4babd9b30ae2508765d04228e0fc4a。REPORT SHA43db7a3ac2c46da792afd6090a07769a18d3c780405b397ddaa91a7f019509cf。独立候选诊断改动和APK曾运行，最终均恢复原输入；不能以诊断构建替代最终冻结源码的修复证据。HEAD e39a78da9c62f02614207cffb36969a32492eaea、暂存区空；无D产品回灌、Git或发布授权。

## 环境、执行与结果

实际应用/模型按外部REPORT自报Claude Code及opaque模型标识记录，不将推荐模型当运行身份。隔离MuMu诊断，不是实体手机。v2未新跑最终31，沿用v1 r9的10 PASS/1 FAIL/20 NOT_RUN（分母31）；诊断轮次不能覆盖此最终结果。Root未重复构建或重开设备。

诊断记录支持这次真实scroll阅读会话pending持续为true、位置已有变化而S2未更新。独立只读审查认为“同帧采样必然永久挂住”超出现有证据；真实后续布局/位置更新是否被消费尚需定向回归确认。不能凭一次yield、人工采样序列或诊断说明中的引用签下设备根因。

## 原始证据

- [外部报告](<E:/creation-reading-assistant/joint-31-closure-task10-v2/REPORT.md>)、其runs/和evidence/原件保留。
- [Root机械核对](<E:/creation-reading-assistant/task10v2-codex-review-20261010/verification.json>)与verify.py：996源码、42验收、1146交付、双APK和本地保护DB/WAL/SHM实算。
- 诊断文件root-cause-j1-55s.md、diag-scroll-r6.txt与各轮diag-reader-state.json需按各自绑定理解；空日志不作为补证。

## 恢复与副作用

owner restore-verify-final.json声称全部保护后账成立且problems为空；Root实算本地原保护DB/WAL/SHM备份3/3与原始protected_inventory的size/SHA一致。Root当轮MuMuManager核进程和Android均关闭。Root未重开设备重验全部持久文件，恢复结论以owner原始后账加本地核对限定。刷新sidecars_initial不能替代原始protected_inventory。v1 sidecar变化来源仍未定位，不追溯宣称解决。

## 未覆盖项与集成决策

最终DEVICE_ACCEPTED=false；31未通过。X整机中断、Back专属完成归因、全部pending队列排空、初始新save及实体手机/MIUI保持未覆盖。全产品分母未核，不能计算全产品百分比。

保留未确认落位不清pending、同状态不重复滚动、取消可退出、末项合法clamp和marker/S/R/S2/updated_at/冷读合同。允许修真实事件观察与等待顺序，不以超时放行或目标仅可见替代落位。10-v3不能再次仅诊断后以“需新产品授权”停止。

42验收文件不变说明v2仅静态审计，没有新增SQL目标拒绝门与命令审计实现；这构成独立11-v1的实际价值。两项分别拥有Android恢复源码与Python callback/离线测试，均不用设备，之后由Codex核查并绑定最终输入再派唯一合流/设备owner。不重派1—9，不重复充分且输入未变的验证。
