# 122-v1 O1拒绝后的资产保留接缝

2026-10-25香港10:07。Root已读新构建原始EXIT0/7s、patch与5分支离线结果，并实算9角色全匹配、新test0ea4f600、host77b2da8d、inputs7a8ed4fd。只补一个明确恢复接缝，不重建或重复全审。

````text
接续122-v1 B，Root已核新APK10641990B SHA0ea4f600be5ea3c88e354359752c268ef1339c7cd1fc605d365b5ca3549b77f5、9角色全同、原构建EXIT0/7s，接受该构建与原5分支探针结果，不重建/JVM/充分检查。

现有host12238977B SHA77b2da8ddc437f6c460c3a982b54b059b7fc6767865e683f92e362e3286bc5d3存在确证接缝：O1 gate在install后拒绝，instrument_started仍false；finally recover_after_run的proof_ok=(not instrument_started)为true，随后uninstall产品会连带删除刚发现或未能确认归属的results/fixture目录。这违反本批“存在/未知拒绝、不删除冲突目录”的明确合同。设备仍禁，不能实际重现。

只你修改122/lane-b/scripts/host122.py必要hunk：将O1路径门禁未证安全状态明确传递至恢复产品资产判据（包括探针抛异常、身份失败、存在、未知）；若安装后门禁没有明确成功，则产品不得卸载，记KEPT_BLOCKED及具体原因，不能因未启动instrument就推定proof充分。没有instrument writer时原test可按既有合同独立恢复；settings/protection/keepalive继续。保持原仪器异常stop依赖门禁不变，不把所有独立恢复一并禁止。也可用等价最小实现，但不可仅改报告/假定不存在，不手动删除或挪动目录。原主错与恢复决定分列、不改summary为绿。

新增真实恢复函数mock/调用接缝用例：存在或未知拒绝后installed含产品/test且instrument_started=false，实际无产品uninstall调用、productAction KEPT_BLOCKED，原test恢复及独立恢复仍执行；明确缺席成功且仪器前其他预算失败则按已证归属原恢复，不无故阻断。探针异常也不可落回未启动即卸载。用最少有意义用例，不重跑原套件，不ADB/设备探测/host主入口/attempt，不改仪器/产品/121原件/APK/input九角色。

保留现host前件必要单份与既有报告/日志，不覆盖红或旧离线结果。在122/lane-b/test-apk-binding/entry-recovery/写REPORT.md/ready.json、最小patch与新增原始离线log，≤1MiB；最终报告真实新hostSHA、原构建充分复用、设备未授权。你不是独自工作，不覆盖A/current；私库自动记忆/Git/云SFTP/存量清理/杀VM共享ADB禁令不变。完成冻结等Root，不自行新attempt。最后单独一行：这是任务122-v1 B-入场恢复接缝。
````
