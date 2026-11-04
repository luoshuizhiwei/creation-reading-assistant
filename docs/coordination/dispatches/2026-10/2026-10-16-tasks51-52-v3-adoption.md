# 任务51/52-v3核验采纳与设备接续

## 任务编号/版本与输入指纹

2026-10-16，本轮51-v3、52-v3均有交付。原脚本SHA分别f35b48a883378a619028eb32be9ba66a8ac8158f97a52113d317c8a333458b83、b71aaf52426e3ecc095c3f47ae5c820d1c5072faa15d617875736447a608a349，均匹配ready。

v13冻结文件SHA9ff778e78b837063d747b05ee9e9a951f04f63cbe2218cb5d999afa8cf67a06a、源码清单SHA38a0ab28ff71091713a36e75db0ad213b7fe383f7fdf243758f6a28c92f757c5本轮fresh核同。既有产品APK/Android源码未改，暂存区空。

## 所有权与改动

Root没有覆盖外部交付目录；最小修正版在：
E:\creation-reading-assistant\tasks51-52-codex-review-20261016\clamp-root-fix\run_clamp_and_collect.py
SHA61e0147abdf48c336e8aeda6e6b81aa77bfc62788ac8410de2e2b84b53e78398

E:\creation-reading-assistant\tasks51-52-codex-review-20261016\saf-root-fix\task52_saf_producer.py
SHAc1394d463fcbd3d064f3abb2105f94413946777c8d50fc11814800650551096d

各目录有root-fix.patch。51修配置candidate/source/acceptance路径以真实freeze为准、UI adb绝对路径；52补实际host/test APK及源码清单文件SHA，并拒绝最终capture时刻倒退，禁止抬高时间。仅保存两个变更脚本及小patch，不复制工程、缓存或APK。

## 环境、执行与结果

52原22/22 fresh PASS；Root修正版22/22 fresh PASS，补4项实际文件SHA错误/最终capture倒退回归全部PASS。
51原套件包含大量真实sleep，直接运行未快速结束，Root停止自己唯一测试进程；15秒诊断栈在真实UI scroll设置等待，不能据此判业务失败。Root把离线time.sleep/time.monotonic成对替换为受控虚拟等待，保持同一真实业务编排与底层FakeDevice，原v3和Root修正版11/11均fresh PASS；修正版另核配置3个路径与freeze一致。虚拟时间仅加速离线模拟，不是自然墙钟/设备证明。

## 原始证据

原Root核验目录：
- task51-v3-bounded.log、task51-v3-timeout.log
- clamp-root-fix/verification.log、config-binding-regression.json
- saf-root-fix/verification.log、verification.json
- prepare_clamp_root_fix.py、prepare_saf_root_fix.py、verify_51_v3_bounded.py、verify_saf_root_fix.py

## 未覆盖项、恢复与副作用

两路离线准备核验2/2采纳，不等于DE.strict设备通过。无MuMu启动、安装卸载、在线设备旅程或APK构建，所有测试命令注入，未动正式书库。原两套交付保持不变，原产品v13证据复用。无D产品回灌、Git、发布或资产清理。

## 交付状态与下一步

下一外部任务53-v1，唯一owner整合上述两个Root修正版，冻结本轮工具及同run身份，运行clamp五步+SAF三步/账本/恢复，再交原严格消费者；完整保护基线/逐包卸载前取证/DB-WAL-SHM/APK对/设置/tmp/原实例状态恢复。
是否需要同批ABC六项由现有总消费者绑定合同决定：若已有不同driver的ABC无法原样复用则在本次唯一批次补最小六项，不篡改run，不扩大重跑其它产品证据。全局总消费者与DE分项分别如实报告。
53提示词准备不等于已运行。当前只有一个可执行关键节点：两路已完成，设备、工具集成及恢复必须单owner；不增加泛审查凑并行。发布、桌面、实体手机/MIUI和X整机中断仍不在本轮通过范围，automation保持PAUSED。
