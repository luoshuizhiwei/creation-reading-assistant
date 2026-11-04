# 任务145-r6：真实安装hook与提权清单闭环（仅离线）

2026-10-26香港19:20，推荐原WorkBuddy global Flash，同owner串行；25分钟原预算，差量512KiB/raw512KiB。Root读r5实际20822B host、18860B真实链脚本、REPORT与两原log，四交付sizeSHA核同。接受逐根集合/目录/SHA拒错及真实父run_attempt调用事实，不接受设备可执行；本批仍无设备授权。

````text
接续145-r5，只修以下实际代码缺口，不刷旧11项或其他充分suite，不重构框架。所有旧v1-r5源码/raw/SYNTHETIC冻结不改不rmtree。

基线：E:/creation-reading-assistant/task145-scroll-host-binding-v1/recheck-r5/host/host145_r5.py 20822B SHAce3b0342d94986397fd8d9b005fbcab50133bbf623ec5a24094decaea30fae08；真实链18860B SHA78c67789f6997808a65cd3e67c29571f5a01a0535a9ad1a926ea61026443a48e。inputs-r5.json5325B SHA0e8d544cb5176c42f5ac06b9c2b18a4ed990d84909daf6606fe5b4dba3d98cb7；15role固定pins/新144APK/readerfreeze不变。

1. source_manifest现用dev.sh('shell',整段script)，没有父shell的shell -T su 0 sh -s/stdin，CE/DE真实权限不满足；r5假端点直接接受cd字符串掩盖了差异。改用真实父shell(dev, script)或完全相同真实签名，不造Dev API。完整find/stat/sha语法按138原已跑脚本引用，避免find|maxdepth/pipeline丢find错误；保存原rc/stdout/stderr。源清单和tar规范化不能用lstrip('./')吞合法点文件前缀，例如./.settings必须保留.settings，只去恰好一个./。源集合/目录/文件/SHA严格语义复用，不能放宽来变绿。

2. main实际安装R4.install_guarded_recover，而链测试自行定义guarded/guarded2替代恢复hook，未证明生产安装路径。新测试必须经过真正新main->load_parent->bind_scope->install_guarded_recover->真实父run_attempt/collect/recover；仅mock外部b.run、独立subprocess、实例身份提供者。禁止测试另写保全/恢复包装体，也不替换run_attempt/collect/原recover。生产hook的preserve若抛错（例如归档timeout、证据落盘冲突、权限错误），必须保产品并按父stop安全门恢复独立test/settings/protection，原cause与恢复cause分别保留，不能直接异常跳过其他恢复。这里为明确外部边界定向处理，禁止吞错假PASS。

3. r5 real-parent-chain.log实际primaryError为terminal=0，collect结果目录absent，keepalive returnedfailure，summary.ok=false。脚本仅assert attempted/attempt存在，且两次都用相同H.RUN_SCOPE；第二场景复用了第一场景落盘结果，不能证明新失败场景。准备独立且按实际仪器shape构造的最小完整成功结果（不能调用被测validator生成expected），外部stub返回真实唯一终态、OK1test和pull结果，须实际父collect调用新tuple并成功、恢复门成功后精确产品uninstall一次、原test/settings/protection成功。另一个独立scope的失败场景须真hook抛归档外部异常，产品uninstall0且其他安全恢复有真实调用及最终分列错误。每个场景fresh parent和唯一scope，文件已存在拒覆盖，不能删旧fake输出或拼旧summary。

4. RUN_SCOPE现硬编码SYNTHETIC_FAKE/run，真实CLI也写fake且复用已有attempt，未来不能直接批准使用。新入口参数化实际run scope：未来真实输出只能task145新run子根、保护actual OUT_BASE/task145新run子根；离线通过显式注入SYNTHETIC_FAKE下新scope，不把fake默认当真实。本批绝不执行真实CLI批准分支外部端点；只全部guard/mock的main(argv)全链，CLI无approve负例必须真__main__到拒绝。fresh serial/boot/user由本批预检注入并与父实际instance核同，不能任意CLI expected漂移继续安装。保持r5既有pid/boot核验，不重新刷全部边界。

验收仅必要新增：提权shell argv/stdin真实签名与点文件保真；真main+已安装生产hook的独立成功闭包（summary业务/collect/恢复均真绿且产品卸载一次）；独立preserve抛异常闭包（产品保留、test/settings/保护恢复、原错可观察）；实际CLI未approve拒绝。所有adb/VM/文件pull/保护/keepalive等外部出口在调用前严guard且未匹配必须抛，不能默认ok空stdout。fake证据明确SYNTHETIC_FAKE，不能让实际原设备运行。无法完成则准确BLOCKED，不以调用发生/attempt存在冒充链成功，不改原父源码来适配fake。

唯一owner只新E:/creation-reading-assistant/task145-scroll-host-binding-v1/recheck-r6/下host/必要inputs/最小相对r5patch/REPORT/raw/ready.json。报告列原log真实状态、前后指纹、实际调用顺序/场景scope、存储分类与新增体积；脚本不要复制工程/APK/cache。真实父13652645B9457a4c5989e2c408923048b9c6b3c818b35573e6c9345eeb048632a9c5912b2、base t94v3 16583Bc806ce36、strictadapter7864B16c93794844650f2116fff7c97c5adb29acb15bfbc623a000643fa2fa5f722bd只读。Android源码/APK/Gradle冻结。没有任何ADB/VM探测启停/安装卸载force-stop设置/真实hostmain/runid预算attemptsummary/F1/实体授权。仅离线外部全mock，不能因为--approve-device参数存在视为授权；Root读真实剩余delta后另裁。无Git/云/私库/正式库/删移旧资产/kill共享进程。最终总结末行：这是任务145-r6。
````
