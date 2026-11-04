# 94-v5 夜间恢复接缝与单次验收

## 任务编号/版本、输入指纹、环境

94-v5，2026-10-23香港时间。唯一task59工作区、既有依赖/锁不变。产品fac29/测试d80/仪器3cba未变，不重建、不重跑69项。E交付为`E:/creation-reading-assistant/task94-organizing-export-integration-v1/v5`，inputs.json绑定21项真实输入及恢复原件。

## 所有权与改动

Root独占宿主/设备/交接，独立只读explorer调查既有Snackbar/选区重叠边界，不动运行输入。本批不改产品或仪器。

## 执行与结果、原始证据

已只读核对v3遗留test包身份，保全必要单份原test APK10,508,820B及两处私有数据tar5,632B。精确原文件目录模式/UID/GID/路径/SHA清单及包用户状态、外部目录缺席证明保留。恢复合同不删除原test包，只在原代码/数据/用户状态与保全匹配时替换测试代码，结束还原原APK并核完整清单与用户状态；原文件有变化时只恢复原tar所含精确包根，不删除未知新增资产，仍有差异即阻塞。

实际恢复guard/restore入口5项模拟检查通过；UTF-8命令清单明确采用LF，APK/tar及被保护原文件内容SHA保持原字节语义。日志、restoration-contract.json及offline-restoration-check.json为直接证据。

本夜最后一次新attempt为T94V4-V5-088887db（沿仪器既有run前缀，独立94-v5输入/报告）；初始保护ok后安装产品及仅对确证可恢复的原test包install-r，组件核实后主仪器执行1次，29.749s、1 test/1 failure。真实导入/打开/首条带分类A保存通过；新建无来源对照后精确title行20秒未落库，失败于t94v2_30_control_item。根因未知，不认定产品缺陷，不改后重跑。

## 未覆盖项、方向自审

核心分类/回显/筛选/回源/导出/清空/取消均未执行。补齐已有test包恢复合同是必要接缝，不泛化为任意包覆盖许可。独立只读调查确证旧Snackbar/更多按钮几何重叠，且缓存ui-test字节码的performClick为中心坐标触摸输入；实际事件是否被覆盖层消费仍未知，不猜测修产品。

## 恢复与副作用

业务失败后已取证并严格恢复：原test APK代码SHA、完整原数据模式/UID/GID/路径/文件SHA清单、原用户状态全部对照保全合同核同；缓存变化后实际执行原tar恢复，未删除未知资产。产品包恢复缺席，night=no，保护initial/final均ok、71/820、DB组一致、diffs空，实例1在线，恢复errors空。原始证明在E的06-original-test-restored.json、02-protection-final.json等。实际exportCreated=false、Download无新资产，删除0。本轮后不再开新夜间设备attempt。

## 交付状态与下一步

状态为业务FAIL、恢复实核完成。本轮目录约14.74MB，其中必要原test APK/数据/清单等保全10,523,497B，余下源证据约4.02MiB，低于8MiB预算，构建增量0。不复制工程/缓存/APK对，不清理旧资产，无Git发布/D产品回灌。

夜间两次attempt已用尽，停止新设备动作。94-v6仅补真实新建保存边界的必要诊断/可验证UI前置并做必要test编译，不改产品、不称修好；结束后若无可自主推进的独立工作，停止并删除本夜heartbeat交接，不空转。2/3保持PAUSED。MuMu非实体手机/MIUI，全产品分母未知。
