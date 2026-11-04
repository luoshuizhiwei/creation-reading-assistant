# 任务172-r1定向修复：night差异是恢复目标，不能先要求已恢复
2026-10-28；同一ZCode owner继续172-r1，不新建同问题任务/窗口。原完整任务doc5943B SHAd634179eca1017cb66db5cf2408b7a09640940e7e0bf7566f5dfd12647ddd27e继续有效，本补充仅修复已证实的恢复门顺序错误。原25分截止00:25:10–14与blocked红记录保留；本次定向修复+真实recover-only追加20分钟，从实际新消息计，安全恢复不中断。174测试/Gradle独立，不触其可变源和APK。

实际红证：recheck-r1/attempt-T172R1-20261028-002431，00:24:31–00:24:34，原preflight只一项problems=settings differ from171 restored:['night']。当前night命令rc0 stdout Night mode: yes，171已验恢复合同stdout Night mode: no；其余settings匹配。原实例本次alreadyrunning，因此launch.attempted=false；真实serial127.0.0.1:16416、bootCompleted1/user0、稳定新boot a6d79e4c-e483-4b2d-9e31-8f9a3e2d4fd5/created1777974446694936，product已装SHA/安装时区窗口归属171及原test0d18/inventory/userState全部通过。stop/uninstall/install均0。ready却沿用UNKNOWN_DEVICE_NOT_RUNNING陈旧标签，必须在新输出纠正，旧原回执保留。

唯一源码修改范围：E:/creation-reading-assistant/task172-recover171-only-v1/recheck-r1/host/run_recover_172_r1.py，当前62257B SHAea5bb058a1f1457b35c944d4fe8d0bf3c38edeabf8a54d6b95607f727a297b92。先核原字节、保存最小可逆patch/前后SHA；不改冻结host166/158/父恢复源、不重刷充分suite。针对preflight508-515：当前把任何settings mismatch立即变成致命problem，导致待恢复的night永远到不了恢复体。仅将已实际证明的night可读且值为yes/目标明确no的差异归类为pending restoration，而非产品/实例归属门失败。保留原settings比较ok=false/mismatch与完整命令回执，不能伪造成比较通过/删除历史差异；另行表达实际preflight准入结论。未知值、非零rc/stderr、其它未核settings差异、原test/产品/data/身份/无instrument/空间等真实门照旧拒绝，不宽泛吞异常/静默默认/绕过恢复门。

这不是把断言改绿：恢复结束仍必须通过真实父设置恢复体把night恢复至合同no，并重新执行实际settings捕获核night与全部约定设置/原test/protection71/820/keepalive；不允许把yes改成新的expected，也不能提前改为no后隐藏原差异。保留before yes→actualrestorecommand→after no及rc的证据。只需一个必要外部边界小负例或针对这处门的定向验证，证明nightyes待恢复能进入准入而其它身份/命令错误仍拒，不复制谓词自证/StubParent/测试override，不全套重跑。

确认同owner原blocked执行已退出再改源；新runId取当前时钟，只在recheck-r1/night-fix/下保存新attempt/报告/ready；原002431及r1根全部证据/ready/REPORT只读保留，不覆盖唯一原始报告。不把原attempt翻绿，新总裁决必须引用原BLOCKED版本与RED→新执行事实。新driver selfpin/本补充doc path/bytes/fullSHA及原docSHA都实际读盘，不手改summary假通过。实例已运行不再launch或reboot；若实例意外关闭先报具体状态，不消耗本次修复再次循环启动。CROSS_BOOT_RECOVERY只用新scope新boot，不改旧expected。

本轮真实执行仍166生产策略：完整pre三根原字节/严格精确缺席proof/manifest/tar目录sizeSHA/readback空间门→freshidentity再核→精确user0产品stop最多一次→post静止完整三根集合/writerproof→父正常uninstall最多一次；任一缺口KEEP整体false，原test/settings/protection独立恢复。collect/download业务NOT_RUN，禁instrument/mainattempt/业务复跑/Gradle/新APK安装、后台策略修改/jobcancel/disable/循环stop/kill共享ADB。旧业务红和历史缺证均留。sourceDoc128KiB/raw256KiB预算沿用，必要真实保全实占另列；不要新增整套收据体系/复制工程APKcache，不删移旧证据。

本次唯一结果为 E:/creation-reading-assistant/task172-recover171-only-v1/recheck-r1/night-fix/ready.json，REPORT新增本补充修复、原night阻塞/归类与最终实际恢复/设置结果、精确计数及最终产品/实例状态，不再把在线设备标offline。普通系统繁忙按用户规则原会话精确发送“继续”，但本补充是一条具体修复指令，不是重新派发另一批设备业务。最终独立末行“这是任务172-r1。”
