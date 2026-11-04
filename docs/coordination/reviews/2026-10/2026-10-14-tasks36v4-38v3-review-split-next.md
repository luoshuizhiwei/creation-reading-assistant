# 任务36-v4/38-v3实核与安全工作拆分

## 任务编号/版本
2026-10-14 10:46香港时间。36-v4独立离线闭环采纳，不再返修或派36-v5；38-v3部分正确成果保留，完整安全闭环未过。下一38-v4准入/库存与39-v1备份/恢复互斥并行，均主机离线、无设备/Android/构建。

## 输入指纹
沿用当前已核Root-v2 1019源码及36-v1双APK/强冻结，无本轮产品增量，不重复全源码构建验证。36-v4和38-v3实际新交付SHA记录于本轮verification.json。D HEAD e39a78da9c62f02614207cffb36969a32492eaea、暂存空；原资产保留只读。

## 所有权与改动
Root仅自有E核查与全局交接。38-v4输出E:/creation-reading-assistant/task38-device-admission-restore-closure-v4，只准入/库存/首次基线及准入CLI，拥有host/inventory_baseline.py、admission_gate.py、freeze_admission.py、cli_admission.py与所属离线测试。39-v1输出E:/creation-reading-assistant/task39-device-backup-restore-completion-v1，只备份/恢复/安全编排，拥有host/backup_restore.py、device_state.py、recovery_cli.py与所属测试。原admission_restore.py只读，必须抽取现有必要函数到两侧独立职责，不两owner继续整文件改写。文件清单是未来合流所有权，不授权改共同目录。36原交付只读复用。

## 环境
Windows Python3.13，全部设备argv注入mock，未实际ADB/设备/Gradle；原测试以模块载入并把ROOT/TMP改为Root自有E，不覆盖原测试日志或删除他人资产。模型菜单未实查，不据执行者自述GLM5.2推断已恢复可用。

## 执行与结果
36实跑43/43 exit0，实际invoke自动产出mapping index后consume总PASS，重复/旧run/schema/配置错误拒绝成立；该独立主机限定交付采纳。
38实跑25/25 exit0，但独立反例证明完整范围不足：
1. 实际produce_admission_schema→实际36 admission_contract.check_admission直接拒绝：38输出allowed_packages/touch_roots/operation_scope，36合同为packages/roots/operations。38 N8只是本地mock_load_admission忽略scope，不能证明兼容。
2. 38 N5在restore前dev.install原APK、清uri_grants并重置focus；Root去掉手工预恢复、保留完整备份，实际函数不能恢复原APK/URI/state。其代码仍只有APK/权限/state比较，无对应恢复动作，报告“全修好”超证据。
3. protected_roots只是记录到scope，实际库存仍只find包私有目录。Root置受保护/sdcard/Download/original.txt并改坏，未进入inventory/备份/恢复账。
4. check_freeze_admission接受仅verdict=PASS且artifacts为空的假冻结；身份缺项与未提供manifest/tree悄然跳过。produce/orchestrate也未真正调用完整freeze_gate。
5. 原APK对仅采host、不采test，无原APK文件备份。settings仍固定system命名空间；owner/mode/context/原不存在key未恢复；临时创建物未实际清理。实际测试包初始absent/无DB也不应被一律“数据库必须非空”挡住，需真实不存在证据。
6. forward --remove-all被mock错误地改成按serial过滤。AOSP当前adb.cpp handle_forward_request的killforward-all直接remove_all_listeners，不能靠-s当范围保护；必须差量逐条操作本run归属映射。reverse的host字段格式也应按真实协议，而非伪serial模拟。

## 原始证据
[独立验证及指纹](<E:/creation-reading-assistant/tasks36v4-38v3-codex-review-20261014/verification.json>)；[核查脚本](<E:/creation-reading-assistant/tasks36v4-38v3-codex-review-20261014/verify.py>)；[36重跑](<E:/creation-reading-assistant/tasks36v4-38v3-codex-review-20261014/36-tests.log>)；[38重跑](<E:/creation-reading-assistant/tasks36v4-38v3-codex-review-20261014/38-tests.log>)。
[AOSP adb当前源码](https://android.googlesource.com/platform/packages/modules/adb/+/HEAD/adb.cpp)；[ADB服务协议](https://android.googlesource.com/platform/packages/modules/adb/+/HEAD/docs/dev/services.md)。线上源码用于核命令语义，不是本机/设备已运行证明。

## 未覆盖项
36仅独立离线采纳，不等于与38真实集成或设备验收。38完整安全闭环未过；设备全NOT_RUN，D/F NOT_PROVEN，X/实体/MIUI/发布不扩大。全产品分母未知；新工具未冻结。

## 恢复与副作用
仅E合成mock与D全局文档，未实际设备/正式数据操作，无D产品stage/commit/push/发布。无子代理、外部消息、定时任务、节点切换。原损坏仓库/桌面HOLD/正式库/私库保护。

## 交付状态与下一步
本批2/2交付，独立离线交付采纳36为1/2，完整联合设备验收未执行。保留38的读取失败拒绝、全路径暂存、mtime及finally正确增量。把持续整包返修改为38-v4准入/库存和39-v1恢复互斥责任，实质工作并行；不制造第三个重复审计任务。38建议DeepSeek可用档、替代GLM5.3；39建议GLM5.3、替代DeepSeek较强推理档，实际菜单优先，不外派GPT/Claude。DeepSeek粗估38为40–70分钟、39为70–110分钟。两项核过再唯一owner合流新工具冻结，再限定MuMu；D产品/Git/发布仍无授权。

共同接口沿用36-v4 cra-device-admission/1，scope严格packages/roots/operations，不让38重新命名。首次基线保留包存在状态/完整APK对/触碰根实际库存/DB组/文件元数据/设置命名空间与不存在状态/原始映射/权限状态/约定设备状态/原始回执；39以baseline和指纹为只读输入，inventory回调协议为ok、files、receipts、problems。离线跨任务接口测试必须使用实际36消费者；恢复测试不允许在调用恢复前预先恢复设备mock。不能可靠恢复的具体改变在准入前阻断，不越权设备执行。
