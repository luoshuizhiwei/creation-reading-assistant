# 121-v1 F1接线终核与host必要修正

2026-10-25。Root核B实际F1 patch与原始compile日志BUILD SUCCESSFUL，尚未设备证明；新AppNavigation34985B SHA7a6046a50550dad54a1a85f2cbf097b009fafb79d8169b889de1d27fb46f9fd9。Root核A host-review原件和host collect/input实际段落，F-H0/F-H1/F-H2是设备前必须闭合项。B改host，A审F1产品delta并行；暂不assemble/ADB/attempt。

## B 国内路由：同owner必要host修正
````text
接续121-v1 B。Root已核你F1 patch及原始Kotlin成功日志，保留F1仅源码/编译接受、运行时未证。A host-review已出，读取lane-a/host-review/REPORT.md与ready。现在只改你独占的host121.py必要hunk，产品F1文件冻结给A审，其他产品/119/旧J1不动：
1.F-H0将AppNavigation输入pin改实际新SHA7a6046a50550dad54a1a85f2cbf097b009fafb79d8169b889de1d27fb46f9fd9，其他三原pin不变。旧host2222537e原件必要单份保全，补丁/前后SHA写lane-b/host-repair，不覆盖初始离线原件。
2.F-H2在入场前确保inputs.json productApk/testApk/protectionAdapter解析后的路径等于实际install/执行调用使用的b.APK/b.TEST_APK及实际保护适配器路径；不能只核输入的另一路同名文件而执行固定路径。必要pin保护适配器SHA16c93794844650f2116fff7c97c5adb29acb15bfbc623a000643fa2fa5f722bd，按实际调用读直接依赖不猜属性。所有实际执行件都必须纳入输入闭包，未知冲突停，不fallback。
3.F-H1把业务成功判据与取证完整性分开：业务失败不能因没有journey_success.json就自动判取证失败，但也不能仅目录拉取成功就认唯一证据保全。沿119r3实际成功/失败/teardown文件约定，明确成功路径必有成功JSON，失败路径必有首错及应有失败行/时间线与teardown证据，保存每件size/SHA和实际缺失；缺必需原件或资产账本/清理归属不能证则collect BLOCKED保留依赖资产，独立安全恢复继续。业务原FAIL/异常不改绿。不要预授权卸载未知保留资产绕过下一批门禁，不重试运行。
4.F-H3 tautology不当有效保护，删除无效判据或明确仅依赖已有真实离线绑定；F-H5不要求新增类探测/矩阵，后续真实APK绑定再核必要类即可，不为低风险造通用框架。

只必要离线py_compile/真实regex-class-dir-collect校验和有意义的输入路径错配/成功与业务FAIL取证分支定向验证，不能接触ADB、不能import有入口副作用模块；可隔离mock边界做少量有效测试，不能重复历史套件。保持1800+1200/一次attempt/71/820/原宿主test/night/VM/不递归未知资产合同，原始日志每修订新路径。输出host-repair/REPORT.md与ready.json/必要patch/原件SHA及离线结果≤2MiB，当前不build/assemble/设备。完成由Root交A核host关键delta，再放行必要一次构建。你不是独自工作不覆盖A，私库含自动记忆/云/SFTP/Git/D写/存量清理/旧J1仍禁。最后：这是任务121-v1 B-host修正。
````

## A 全球路由：F1实际delta定向终核
````text
接续121-v1 A。B F1实际ready已出：lane-b/f1-repair/ready.json、REPORT、patch与原始compile日志，AppNavigation实际34985B SHA7a6046a50550dad54a1a85f2cbf097b009fafb79d8169b889de1d27fb46f9fd9，原a4d2...已单份保全。现在只读核这个实际必要delta：真实null entry/route不清会话，真实reader→非reader清会话，reader内/嵌套/加载请求身份不受影响，effect key是否合理。B此时在修host，你不读正在变化host重判、不改任何源码/build/ADB。已充分24JVM复用，Kotlin原始编译日志可核不重跑。不要求改字体密度语言或额外设备矩阵；F1尚无运行时复现，审查结论限源码及编译。

输出lane-a/f1-review/REPORT.md与ready.json≤1MiB，固定前后SHA、Standards/Spec与剩余设备缺口。你host原审F-H0/H1/H2已由Root交同B必要修正，旧host审原件保留；待新host-repair/ready出后才下一次关键delta审核，当前不轮询空转。无新发现及时收口，不造泛审计/镜像测试，旧J1冻结，所有私库/自动记忆/源码buildADB/D写禁令保持。最后：这是任务121-v1 A-F1终核。
````
