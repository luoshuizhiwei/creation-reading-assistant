# 任务184-v1：保活判定接入严格身份链，离线闭环

2026-10-29；原182/179 owner同会话接续，推荐保持ZCode当前模型；20分钟自实际全文发送起评估。你负责新生产接缝实现、必要定向离线验证和交付，不修改冻结源。Root负责state/current。唯一新目录 E:/creation-reading-assistant/task184-strict-keepalive-entry-offline-v1/，唯一ready.json。B183-r1已集成编译交付，不普通继续、不重构建；当前没有第二个必要独立任务，不为并行造任务。后续业务批依赖本生产判定接缝。

## 基线和Root裁决

182-r1当前只读证据有限接受：包pm path rc1空stderr、三根严格NAME_ABSENT、原test0d18/userState/设置、冻结consumer71private/820external/DB/diffs=[]及三capture实际SHA匹配。完整capture1320582B，r1实际83767B非ready76299B。限制保留：两个identity before/after均在脚本开头；sameSerialBoot布尔仅比较serial，Root实际原IO boot四次同4579019f-deba-4102-b371-e34d8c671097但不覆盖保护结束；inventory仅摘要hash无原rc/stdout/stderr；tasklist按名字不能充分归属旧driver退出；保护stdout被截断但完整consumer文件保留。report多处把182历史缺口写181，原件不改。182-v1原stop/uninstall回执/精确次数UNKNOWN、覆盖日志/P4/P3/P6不足永久留，不重做恢复。

183-r1 ready5301B SHAb5810c91d39adf890b66e00d8195b38a7bae66b9adb996bdd590076bbc0e0992，Root实际源码31645Bcfc7af26b976127be77bb6eb1ad93941d8788fa46d26ee0e7a48bb373a0e05e4、新test10689059B5defd08054be927a79ca231c04924a82bfb587dce8529940cd56f0a7fdc741ec、product4d302不变、旧test47f2保全、原buildrc0/51.4s/BUILD SUCCESSFUL/签名runner target已核。只接受集成编译，设备NOT_RUN，scrubber其他门控/autoHide时序、LIFO入口缺口保留。

## 已确认生产缺陷

E:/creation-reading-assistant/task94-organizing-export-integration-v1/v3/scripts/t94v3_device.py:323 cmd_keepalive仍以 inst.serial_attached && inst.instance.is_process_started 判ok。181真实08原false，原manager flags仍false；179r3严格resolver已通过实际VM进程/配置磁盘/监听/ADB/boot完整链确认在线，不能改manager旗标伪绿，也不应把这条旧旗标当完整在线判定。host136.py:686真实恢复尾调用b.cmd_keepalive(None)。只替换这个生产接缝，其他身份/业务/恢复门完全保持。

冻结 t94v3 16583B SHAc806ce361c9c3929c4c32e08f6435e5e1f5b8ce088c8e6f0aa487cdee1d5fdbe；host136 E:/creation-reading-assistant/task136-f1-global-offset-repair-v1/lane-b/scripts/host136.py 52645B SHA9457a4c5989e2c408923048b9c6b3c818b35573e6c9345eeb048632a9c5912b2；host176_r3 E:/creation-reading-assistant/task176-contract-b-host-binding-offline-v1/recheck-r3/host/host176_r3.py 21289B SHA9ff38f2626f7eac901c5c479fbeb5b6f1e06521cc089259ab8fb7e98da059cb7。

现有生产入口 E:/creation-reading-assistant/task179-instance-identity-adapter-offline-v1/recheck-r3/entry_wiring_r3.py 2047B SHAc300fce8d5e88934347294d53b2f68009b81b656ba95fd7ee10d3c454b640f7c；identity_adapter_r3.py16872B SHAa0c12c4da7743fa5493e37471797bc668a5f5fec73404c7cf561fc3d3da93cbc；依赖r2/r1沿原pin只读。不动任何冻结文件、candidate/APK、183或182产物。

## 最小实现

1. 新entry薄层沿179r3每次H.R3.load_parent出口绑定同一个真实parent.b，继承原严格resolver，新增仅该b.cmd_keepalive适配。必须实际可供将来host.main使用，不是局部验证闭包。默认真实IO，但本任务不调用真实IO/设备/main；内部不预填成功。
2. 新keepalive用原严格resolver的本次完整在线结果裁决，保留原managerRaw/flags false和实际绑定证据。未attached/问题非空/身份链缺字段/不一致/非空stderr等仍沿严格resolver拒；不得自行将serial_attached置true或伪改instance flags。不能只检查一个attached布尔代替生产完整resolve。保留policy为实例保持运行、不关闭重启；08新receipt明确schema/新判定依据与legacyManagerFlag分记，不改旧18108。异常也写新失败回执并传播/非零，不吞错误。新业务scope当前serial/boot一致门仍由原父严格执行，不豁免。
3. 新bind/restore必须覆盖实际loader的新b实例且正常与异常出口恢复本层改动，不能泄漏成全局补丁。保留179r3接线能力，不重复复制adapter整文件或旧工程。

## 定向离线证明

只mock最深外部IO（subprocess等），未知调用立即抛错；不得mock resolver结果、内部门、cmd_keepalive返回、host.main或consumer来造绿。不再整main离线回放/设备probe/大suite。

最多四个有意义case：真实形态managerfalse但完整严格链在线应新keepalive rc0且flags原样false；原181 daemon stderr原文/rc失败仍拒；真实identity链boot或归属漂移拒；生产真实loader两次新b实例皆绑定+正常/异常restore不泄漏。尽量复用179r3已有真实夹具与三项充分验证，只对新接缝增加证据，保存新红→绿如有。主张接线必须指向实际host136恢复尾真实b.cmd_keepalive调用；无需为此运行host.main。若无法在不改内部逻辑下接好，交精确阻塞，不扩大授权。

## 禁止与交付

本批严格离线：无真实ADB/VM/设备/identityprobe/main/install/instrument/stopuninstall/Home/launchrebootconnectkill/设置/Gradle。不要修182旧driver，不再当前末态探针。未来设备批须Root验本入口后另准备freshAPK24roleowner/runtimeinputs与原166真实恢复门；不能直接沿用旧snapshot当许可。

源码/patch/doc/REPORT/ready<=96KiB，全部raw<=96KiB，先估空间复用环境，不复制APK工程cache/历史日志，不删移证据。交独立生产entry、必要最小验证、精确依赖pins/fullSHA/size、新原log/退出码/实际时间及全部新产物互斥分类实占；索引不自引用hash。声明离线通过非设备/恢复/全产品通过，旧FAIL/预算错误保留。不Git暂存提交pushPR发布/云/正式库/私库sessionDB/.codex/memories/自动记忆。用户stop/Esc/锁屏/login/权限安全即停不绕。最后独立一行“这是任务184-v1。”
