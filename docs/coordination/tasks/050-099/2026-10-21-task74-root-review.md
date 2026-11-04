# 74-v1 Root核验：取材FAIL、三次run越过单attempt合同，保持运行由Root补完

## 任务编号/版本
74-v1，2026-10-21香港时间。用户允许启动/使用隔离实例1，禁止关闭/重启，结束保持运行。Root并行核运行/恢复与取材失败，自核工件/保护及实际在线状态。

## 输入指纹
host ceb60dd1a24455efbfcbcac4143a058b2d30e45b6f42659653ec7034925ae79a，test cb7faf93503f53c4d2ddd277967cfbcb428385668b247732c5e97642ece55cec实算与已采纳基线相同，无本批产品/仪器重建。
当前runner d04b8c34395bdc4e7f3af608691512f574ec2a126a861cc11909eafbf26683a3；freeze48bb1035ec49649579ed5a69c73fcd6cf7e26685983582f95f05305f036fdd32；binding ebf47801b9926ba26b9c8038abc5d4b8031df930d94c64e19891eab2d160a25c，实际匹配ready，报告表中6bb68c/a90c05不是当前值。上游Root-r1 ready实际SHA367819f2…b8f9a，报告表75f8f821不符，不据此无证据推断上游被修改。

## 所有权与改动
Root只读实际交付/归档/有限设备状态，写root-review及本报告/current；未改原候选、产品/仪器或旧报告。无相关task74/59 Python/Java执行者。Root没有重建APK或新跑仪器；必要保持运行恢复只启动1次已关闭实例1、连接ADB、恢复既定night=no，没有关闭/重启。自动化不恢复、不外部派发、不Git。

## 环境
复用59工作区与D:/develop/env/gradle。比例核验技能用于实际失败/协议差异，不重跑原充分单测/旧63链。原报告/ready与全部run保留，不选第三个run覆盖前两次。

## 执行与结果
Root实跑当前verify_binding/verify_freeze/audit_import_closure/预算均ok，16工具SHA及两APK核同。预算5100=3240计划+1680恢复+180裕量。这些不证明设备旅程通过。
真实存在三次独立run且均launch_started=true：
1. 013310-1a0ab5归属绑定失败，未准备/基线/安装/仪器；恢复final-protection报night不符，keep-running被阻塞。
2. 014051-22d83e启动超时且serial=None，未安装/仪器；后续保护探针多项rc127/TypeError，不能称恢复通过。
3. 014617-08c1ac实际基线/安装/material执行。material rc0但JUnit FAIL，想法body未包含本轮标记（MaterialGatheringJourneyTest.kt:875），orphan/source_state/md均未运行；keep-running仍失败process=True/android=False。
三个run的00-binding实际runner SHA分别28bc0345…5794、d2c999e5…204c、d04b8c34…83a3，第三次entry_worker也由817965a0改为c536e731。故不止有三个日志目录，而是设备启动尝试之间改变入口并重绑重跑，违反单attempt/失败停止合同。不能称严格一次，也不称每次恢复全通过。

## 取材定位与未覆盖项
现有仪器按全局hasSetTextAction第0/1节点猜标题和想法，sheet另有标签/分类等编辑节点。归档mat_30_save失败JSON没有逐字段读回诊断，尚不能确认内容实际落入哪个字段。生产ReaderInspirationSheet title/body状态→onSave→ReaderLayerBuilders InspirationEntity.body直传，未见现有证据证明产品丢正文。下一B线只修精确字段定位、输入后读回和本轮标记/长度诊断，保留Room断言，不直写DB或修改产品保存层来绕过。

## 恢复与副作用
第三次run的71私有/820外部路径size/SHA初末一致，两tar实际SHA与卸载回执匹配、候选包absent，数据保护/归档部分采纳。保持运行步骤原始not ok，不能被shutdown_requests=0覆盖。未发现显式shutdown/reboot请求，但零自报计数不能证明没有间接终止或保证最终在线。
Root02:02:51只读manager/adb证实两个实例均关闭、ADB空，故保持运行合同未完成。Root按用户既有启动许可做必要恢复：02:06通过隐藏Start-Process调用MuMuManager control -v1 launch，rc0；只启动、无仪器/安装/关闭/重启。随后按fresh映射端口16416连接，02:07:11 get-state=device、sys.boot_completed=1、boot_id16a3aff1-368e-49b6-a064-1db2d38d95d7。02:08:47复核仍同boot_id、device/1，候选包查询空。启动后night=yes，按既定测试设置合同恢复no并原始复核no。实例保持运行、连接保留、实例0未动。该补完是必要恢复，不是第四次仪器attempt。
观察到manager在VM已可实际ADB/boot=1时仍is_android_started=false，说明此布尔不足以单独裁决运行状态；下一A线以可靠身份+真实在线证据处理冲突，不简单删校验或将未知当正常。还需查瞬时worker/Job和长期VM寿命耦合是否导致间接终止；目前无证据明确断定根因，不因假设改全局树守卫。
Root仅新增小型JSON/原始命令回执，无工程/缓存/APK复制或资产清理迁移；历史71实例归属与72旧test覆盖缺口仍保留。

## 原始证据
E:/creation-reading-assistant/task74-keep-running-accept-v1/REPORT、ready、candidate；logs三个run的00-binding/99-summary及第三次10-material日志、恢复结果/清单/归档；Root root-review/checks.json、run-inputs.json、readonly-state-20261021-020251/result.json；keep-running-recovery/launch-receipt.json、online-polls.json、online-final.json、root-restoration-final.json及原始stdout/stderr。

## 交付状态与下一步
74交付已核，联合验收FAIL，单attempt执行协议不采纳，第三次数据恢复证据采纳，在线运行由Root后补；四旅程通过0/4（material失败，其余未跑），不算全产品比例。
75-v1-A原唯一集成owner修保持运行的真实证据判定/持续VM生命周期、失败即停止且禁止重绑重跑，先离线验证；75-v1-B并行修取材精确字段输入/读回，仅候选互斥。两线齐后A一次必要test构建/最终绑定，产品不改则host复用。完成真实适配后由用户派发的新批次仅1个run/attempt，不把安装前失败排除在attempt之外。全程不关闭/重启实例，结束保持运行。
当前Root已恢复的实例可在fresh核实后使用；后续若意外关闭先记录阻塞，不擅自循环launch。automation-2/3继续PAUSED，不自动外部投递。全产品分母未知，不称实体手机/MIUI通过。
