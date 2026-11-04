# 67-v2 / 65-v5接收：工件收口，执行前契约仍阻塞

## 任务编号/版本
67-v2、65-v5，2026-10-20。65合流/编译/test APK交付采纳，67真实恢复函数接线及局部离线修复采纳；新设备验收不放行。旧历史不可补证保留，不重新调查。

## 输入指纹
65两仪器工作区SHA：MD aabd083aaa232d04e066f9cbd3f6b5156883b60a80d2ec4b6169a839a9fa92dc、取材1e31f24e7e44001896185d32c24d42001ea551f2959680deb74bb3ae10207a66核同。新test APK db741d1dafc48201cc92ba139ba41dc4ce30d6b934a28874740ab04d8cbc1b55，10246316字节；host14e573ab…82aa及保全旧test3565f6da…003a实算size/SHA匹配。
67入口74e748ec13e993806e9e59bb249ad0f642926a8b6db8c59c3bfb0b94c3106c29，绑定f23ee2bdadd925a1b9ce7a91c2e6572c40fe85576cc2329cb75e5b760a546a10、freeze7fde78acf4d7afd9df369cd1062efb3870f1d33558597fa8b4a0ef6efadcd60a见原ready；生产verify_binding实算ok，但下述缺项未被该校验捕获。

## 所有权与改动
65已合入现有59工作区两个androidTest，无生产/公共runner/构建配置差量；Root只读工件/代码并写task67/v2/root-review及本D报告/current，不改原交付、共享工具、APK/冻结，不启动构建/设备或外部消息。

## 环境
复用59工作区及D:/develop/env/gradle。Root按verification-before-completion只核本次新工件与未解决真实接缝，未重复全量JVM/Lint/63链或原9项模拟。日志含本地编码，成功标志从原字节核实，未修改日志。

## 执行与结果
65两份真实日志BUILD SUCCESSFUL，对应编译和assemble，工件实物匹配，采纳；不因下一编号重新构建。printToString静态字段矛盾以真实编译通过收口。
67确已接到device_protection与run_step_bounded/worker真实函数，解决了前轮仅卸载关机的路线错误，保留新JUnit协议判定、绑定serial包探测、归档失败阻止卸载等必要改进。其测试替换整个设备层/恢复函数只能证明编排或转发，不能独立证明真实worker上下文完整。
Root真实main离线反例（FakeDevice仅替换设备动作；无真实subprocess/ADB）：把FREEZE_67指向不存在路径，verify_binding仍ok，main仍安装两包、运行并exit0；总预算10/恢复预留1200时main先launch再在baseline前exit6。已有成功路径不是新设备证据。
原始结果见E:/creation-reading-assistant/task67-recovery-runner-v1/v2/root-review/checks-and-counterexamples.json、fixtures与budget-main-stdout.log。第一次读取UTF8遇到本地编码错误，在任何反例执行前停止，后改按字节核成功标记；不据该读日志错误推断构建失败。

## 原始证据
65/v5 REPORT/ready、logs/01-compileQahostAndroidTestKotlin.log、02-assembleQahostAndroidTest.log、evidence/build-provenance.json、保全旧test及实际新test路径；67/v2 runner、binding、freeze、tests/test_task67_v2.py、原offline-cases与Root新增反例。未复制工程/历史结果。

## 未覆盖项与具体阻塞
1. FREEZE_67只在恢复worker内读入，执行前verify_binding并不验证存在、内容或摘要，不绑定到工具清单；artifact列表形状不足以替代来源/源码/工件的严格冻结合同。必须在任何设备动作前验证，并将freeze及65构建来源与对应工件可靠绑定。
2. REQUIRED_TOOLS仅10项，实际传递依赖至少缺task56_tree_guard_v4.py、task53_process_tree_root.py、动态导入task56_adb_lifecycle_v7.py；还有cleanup/ledger等转发依赖，需按真实worker执行链补完整闭包，不能只绑定别名层。
3. Budget在launch后创建，launch阶段没有使用limit_for；baseline算出limit却不传入真实调用，包探测也在总时钟外；“全部阶段实际受截止约束”不成立。两次install均可各180，阶段注释只计一次，且恢复五步骤上限和为1620、预留1200，需统一实际合同和可用恢复资源，不能只调整JSON数字。
4. run_recovery给worker独立上下文，测试直接替换run_step_bounded只证转发，不证实际子进程承接窄stdin/归档APK身份/日志目录/归属ledger等生产上下文。下一修复必须针对真实父入口→spec→worker→恢复实现接缝验证，保留归档失败不卸载/恢复阻塞不并发，不用整体Fake绿测替代。

## 恢复与副作用
本轮双方设备0，Root无新设备恢复义务。67-v1当前保护面核定与65-v3历史不可补证分类保持，不再次采集历史缺失文件。自动化2/3保持PAUSED，无Git/发布/桌面/正式库/私有库/云部署或既有资产删除迁移。
65报告交付约9.81MiB主要为保全一份旧test；工作区构建约20.4MiB为其mtime口径估算，不提升为精确物理新增。Root仅KiB检查与保留离线夹具，不新造缓存或APK对。

## 交付状态与下一步
65-v5已收口，不派65-v6、不重新构建；67-v3同一集成owner只完成上述契约/传递依赖/真实截止/worker接缝，复用已交工件和成功原证据，完成全部离线修复及最终绑定后交Root。当前只剩一个共享执行生命周期接缝，不能为并行数量制造无关任务；串行原因是入口/截止/绑定/worker上下文强依赖、共同文件单一owner。未采纳前不设备。通过后再形成一次明确输入、恢复合同与停止点的单次设备提示词；全产品分母未知，不称实体手机/MIUI通过。
