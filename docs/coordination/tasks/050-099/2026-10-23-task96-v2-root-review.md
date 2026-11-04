# 96-v2 Root核验与96-v3接续

任务编号/版本：96-v2核验，96-v3接续。日期：2026-10-23香港时间21:00。

## 输入指纹、所有权与改动、环境

Root只读核验E:/creation-reading-assistant/task96-library-settings-closure-v1/v2。输出9/9大小与SHA匹配；22冻结输入中20项当前匹配，两项已变为修后仪器b9fb760b5f13b1ebf9740e8722e4c6c131f85b929f22aaff43611710893e45cd与test54369035d9cb1aeadf6717a5b2e6e617608224385f2a6c27b76ada50a736c9c7。运行d2f6783a原APK遗失，不称完整运行输入现物全匹配、不重建补造。产品110不变，唯一task59工作区/依赖不变。D归Root；Claude继续独占D2仪器/宿主/构建/设备。

## 执行与结果、原始证据

仪器抢先删除已移除；宿主新增当前size/SHA不符拒删。fixture-cleanup-redgreen.json为实际宿主入口7种FakeDev离线模拟，7/7声明通过；脚本明确导入实际t96_device.py，不代表设备清理实测。本次未重复运行会删除旧模拟目录的脚本。候选搜索过滤非editable、可见文件行并先落现场，尚未真实执行。

唯一T96V2-20261023-204114的04-run原日志0.088秒、setUp要求旧T96V1前缀失败，tearDown连带失败；D2业务零执行。候选源码改为T96V[0-9]+-[A-Za-z0-9-]+，54369035已构建且单份保全。原运行APK覆盖前保全要求再次遗漏，缺口保留，不以新候选冒充。REPORT称两次组装而ready称3，采纳integration/logs实际02/03两次组装，文字差异不额外造勘误任务。

## 恢复与副作用

summary保留ok=false、collect缺结果目录和protection-final两条原错误、产品KEPT_BLOCKED。06-original-test-restored原记录与入场代码SHA0d18、完整inventory/userState一致。首次保护DB组一致但consumer差异含夜间设置，不能改成全绿。

补只读原证据：E:/creation-reading-assistant/task83-selfuse-delivery-v1/B/v2/protection-root-r1/T96V2-20261023-204114-final-rerun/readonly-protection-proof.json实际ok=true、71/820、diffs=[]、DB组一致、reasons=[]。Root本轮fresh adb devices后绑定127.0.0.1:16416精确包清单：仅qahost/qahost.test及原test，产品包缺席；cmd uimode night输出no。未操作设备持久状态。执行者手工卸载前“仅profileInstalled24B”的原始命令证据未在交付内定位，不将口头描述补造为完整证据；新批入场必须fresh独立确认。

## 未覆盖项、交付状态与下一步

状态PARTIAL，安全修正离线采纳、D2业务未验，P1/JVM161复用。实际新增24190198B，build增量未独立测量。旧FAIL/缺失APK保留，无存量删除迁移。

方向自审：继续现有D2目标有具体新修正（编号正则），但不能再让绑定失配和覆盖丢件消耗设备批次。96-v3先离线逐项核实际仪器参数/编号正则/结果目录/宿主采集和恢复路径一致、确认候选单份保全，再最多一次剩余D2。已构建候选不重复编译，不放松恢复/保全合同。设备流程仍由同owner串行。独立97-v1 SearchViewModel/SearchScreen/对应测试差量实施已派android_worker，只写独立E97交付、不写唯一工作区、不构建设备，待96恢复闭合后集成，避免改变D2冻结输入。不是为并行数量制造审查。
