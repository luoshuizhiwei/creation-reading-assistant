# 121-v1 设备前业务超时边界修正

2026-10-25 香港07:00。B APK构建和输入绑定已交；Root实算9角色实际SHA。新产品74d106b6、新test6c294a04，不重构建。A host终核已接受原必要差量，但Root在具体设备预算核对中发现host121.py仪器调用固定timeout=2400，businessCapSeconds=1800只在finally事后判定；可侵占恢复保留窗口。此为新确证门禁，不代表已经发生设备超时；本轮未ADB/attempt。

## B 国内DeepSeek：同owner只修业务等待边界

````text
接续121-v1 B。你的APK构建/inputs已交，Root已核原始成功日志与9角色实际文件SHA。不重建APK，不重跑24JVM或23检查。设备仍未授权。Root核host具体设备预算发现真实静态缺口：run_attempt中am instrument的timeout=2400，大于businessCapSeconds=1800，finally事后超时标记不能保护恢复reserve。先只修这一等待边界，再交实际delta审核。

你独占lane-b/scripts/host121.py，修改前应仍28167B SHA7ccf53867039d2ff13674dd12d96ea65e13ee8b897e6fdcd01ce8b2b970d39bc；如异先报冲突，不覆盖。必要单份原件复用现host-repair后文件或保存于lane-b/business-timeout/source（只必要一份），最小patch与前后SHA。只在业务instrument启动前按本次business_start及1800计算剩余业务秒数，已耗尽则不启动仪器、转原有独立恢复；仪器调用timeout不得超过剩余秒数，超时保留原错误并走原finally恢复，不重试/重跑。明确instrument_started仅在真正将启动仪器时置true，避免预算入场拒绝被误认为已产仪器证据。不要改产品/119/host取证语义/旧J1/保护适配器/公共runner，不引入通用deadline框架或新一次性helper。核现有business入口耗时和恢复reserve含义；若发现其他实际可阻断边界，只精确报告Root，不扩展代码范围。恢复阶段使用自己的保留预算，不能因business超时跳过独立恢复。

仅离线语法与有意义的时间边界定向验证：正常剩余、剩余很少、已耗尽不调用仪器且仍走恢复；可隔离mock现有边界但不能ADB/import有入口副作用模块，勿做镜像实现测试。保留真实原始log与exit，写lane-b/business-timeout/REPORT.md、ready.json、必要patch和离线原始结果≤1MiB；不得覆盖既有host/compile/build报告或日志。你不是独自工作，不改A/D/current。新产品APK74d106b6b1349cfea1f4b4bb5f49e41c27691d6621ca1424e4c55effa0de84e4与test6c294a04c70651eadf096f4d632cc9a2ab303b0ccbbe42770a09cd81be413a85/inputs保持不动。完成Root交A只核这个关键hunk，不重复泛审宿主。

当前禁止ADB/安装/设备/host主入口/attempt、私库含Claude/Cowork/DSH/.codex/memories及自动记忆、Git暂存提交push发布、云/SFTP/新账号购买、D产品回灌、正式库桌面HOLD实体手机、存量删移压迁、关闭重启杀VM共享ADB及其他进程。1800业务+至少1200恢复、一次attempt、71/820/原test/strictnightno/VM保持运行合同不变。本轮只离线修host预算边界，不自行设备开跑。

最后单独一行：这是任务121-v1 B-业务超时边界。
````
