# 任务179-r2：交付可复用生产接线，收口剩余身份边界

原179 owner同会话，推荐保持GLM-5.3-Flash最高。15分钟从实际发送起评估；只新写 E:/creation-reading-assistant/task179-instance-identity-adapter-offline-v1/recheck-r2/。同owner完成最小实现、定向验证与交付。无第二独立必要任务：下一设备业务仍依赖本接线，不为并行重跑已充分验证。

仓库 D:/develop/Code/Codex/creation-reading-assistant-recovery-20260919；先读AGENTS与docs/handoff/current.md页首。你不是唯一工作者，不覆盖他人修改。旧179-v1/r1、180、176及Android/candidate/APK全部只读，state/current由Root维护。

## Root实际验收

r1 ready4499B SHA a811d1fb266c4038323c5de5beee969939635c6231552d5fc2a6087c3b1be27a；identity_adapter_r1.py20647B c8929f1876ce34e051e426cc4fa8878046da33d0e96848593e08bd450f186ded；offline-verify/verify_179r1.py18183B 5545f351ba76c6a2e823d72dfd65ea9c0c522089671e950a58a6185d2dafc73a。Root逐项核89个pins，88匹配，唯一不匹配是索引自己的自引用pin，不能自证索引SHA。真实探针12条argv均只读，get-state正确、config22ae6b...、nemu5c859d...、UUID/comment/PID/boot关联和当前回执有限接受，勿重复真实探针。

Stage B真实main调用新resolver4次、owner门dumpsys未知严格停下、mainrc1/summaryfalse成立，有限接受，不需再模拟完整恢复。r1的wrapper只写在验证stage_b局部函数，resolver闭包固定VM_DIR_FIX，未交独立可供下一设备driver使用的生产入口；旧entry_wiring仍一次预绑定失效。adapter头注写wiring_179r1.py但文件不存在。下一批不能直接import验证脚本拿夹具闭包当生产接线。

报告元数据纠正：Root实际发送00:28:25.160、ready文件创建/写入00:44:29，约16分钟，不支持“约65分钟/超20分”的陈述。保留r1原件，仅新报告列Root时钟/执行者错误估计，不制造超时。实际89文件295364B，非262969B；source两源38830B，其余256534B（还含报告/ready/索引，应准确互斥分类），原raw超128KiB确实保留，不能删证据补绿。.nemu回执ginstanceName=null，不是报告称的已匹配；UUID匹配可据实接受，不能补造ginstance证据。

## 最小实现

1. 新生产entry_wiring_r2.py独立导出清楚的绑定/调用入口：import冻结host176_r3，包裹真实H.R3.load_parent，每次实际加载后只绑定该次parent.b.resolve_instance到新生产adapter（默认真实StrictIdentityIO、真实VM路径，不含fixture/预填serial或boot）。实际run_main调用冻结main；finally还原内存接线，禁止修改冻结文件、内部owner/baseline/恢复/collector门或生产scope判据。原loader每次重建b这一红证据直接引用，无需再做同一红例。
2. adapter最小新版本修实际合同漏项：manager、listener和process两次查询目前未全部拒绝非空stderr，尽管报告称每步均核；统一按外部命令失败边界拒绝，完整回执保留，不能吞错或只截错误结论。
3. .nemu绝对磁盘路径现在直接用loc绕过realpath，只有相对路径canonicalize；两种都用规范实际路径核严格祖先。原实际引用已安全通过可复用，但绝对路径含..或链接不可绕过。描述文件是本轮新增身份依据，前后复核其rawSHA（与config同样有界两次）；漂移拒绝，不只由探针事后freshFileSha字段记录而不比较。外部只读base维持原已证共享路径合同，未知外部引用如实拒绝，不因标签Readonly放行任意路径；无需哈希大镜像。记录ginstanceName缺失的真实限制。

只需以上实际接缝，不扩展Android功能，不重写整套adapter架构，不因新编号复制整工程。若需要adapter差量，交最小patch及新版本源，保留r1原件。

## 定向验证和交付

仅离线：验证导出的同一生产入口确实被测试调用，注入只在外部IO层，明确fixture路径仅由测试传入而非生产默认。一次有界真实host main strict回放到instance后owner首未知命令停点即可，沿用r1有证据的内部逻辑；所有mock必须阻断真实subprocess，未知命令抛错，禁止运行真实main或真实ADB。离线输出只落r2（必要换离线输出锚明确记录，不改落盘冻结源、不假称原scope门设备通过）。不要重复8次建scope调summary断言，先读已有summary.primaryError schema再一次验证。加必要负例：查询rc0但stderr非空、绝对路径..越界、描述文件第二次SHA漂移；正常真实形态来源引用180/r1，不重刷无关suite，不运行stage_c。

唯一ready E:/creation-reading-assistant/task179-instance-identity-adapter-offline-v1/recheck-r2/ready.json。REPORT准确区分生产可复用入口已交、离线到达点、原只读probe复用、业务设备NOT_RUN；完整path/bytes/SHA，独立子进程rc原回执；索引禁止自引用SHA，ready不自我hash。source/doc/patch/报告/ready≤128KiB，全部raw/fixture/离线scope≤64KiB，预计无需大文件；复制前核空间，按实际所有新文件互斥计数，超额不删证据。冻结t94v3_device c806ce361c9c3929c4c32e08f6435e5e1f5b8ce088c8e6f0aa487cdee1d5fdbe、host136 9457a4c5989e2c408923048b9c6b3c818b35573e6c9345eeb048632a9c5912b2、host176r3 9ff38f2626f7eac901c5c479fbeb5b6f1e06521cc089259ab8fb7e98da059cb7前后复核，路径引用r1原报告即可。

禁止真实设备探针/main/install/instrument/stop/uninstall/Home/launch/reboot/connect/kill/设置/Gradle，禁止Git暂存提交pushPR发布/云/正式库/私库sessionDB/.codex/memories/自动记忆。遇权限/所有权/证据边界停止并具体报告，不扩大范围。未来实际业务批另由Rootfresh输入/唯一owner/原166恢复合同派发。本轮最终总结末行：这是任务179-r2。
