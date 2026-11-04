# 94-v1 Root接收核验与94-v2续验边界

日期：2026-10-23。Root本次只读核对既有交付，不执行构建、设备命令或外部派发。来源：[94协调报告](2026-10-22-task94-review.md)、`E:/creation-reading-assistant/task94-organizing-export-integration-v1/ready.json`及其直接证据。

## 任务编号/版本、输入指纹、所有权与改动、环境

任务94-v1接收核验，下一批94-v2。输入与指纹为下列实核结果；环境为既有task59 Android工作区，本次不改变运行环境。Root仅拥有本报告及current现态更新，不写E产品源码、共享构建或设备。

## 执行与结果、原始证据

- 独立重算REPORT、patch、产品APK、test APK、保全旧APK以及两处修复源码SHA，均与ready的SHA一致。93合流17文件按ready逐项核对，除94明确的来源修复和仪器调用点修复外均为93的after，17/17匹配。
- 读取7份原始JUnit XML：69项、0失败、0错误、0跳过；修前日志为8项4失败；最终构建日志BUILD SUCCESSFUL。复用这些实际输出，不重跑无变化测试。
- 产品APK：`fac29f55a6896c6c8390b15342093861ae5658cf8b009d7e975d72195f1e4705`，52,763,889B。旧稳定22f6单份保全匹配。来源输入未改时返回原source的修复已接入UI草稿构造，不将此扩大为所有来源编辑语义均已设备验证。
- 读取仪器stdout：来源进度非空断言失败，1 test/1 failure；ReaderHelpers.kt真实实现为progressPercent>0才存数值，否则null。首页空进度不构成已确认产品缺陷；应保留前后完整source相等断言，不能只删检查换绿。
- 原始initial/final保护JSON解析均ok=true、reasons=[]、71私有/820外部、diffs=[]、db_groups_match=true；卸载回读为空、night=no、ADB附着及同boot保活记录成立。管理器is_android_started=false与其余在线信号不一致，不能只靠该字段描述状态。以上只证明94历史恢复，不证明此刻实时状态。

## 未覆盖项：不采纳的扩大结论与后续脚本缺口

1. 分类编辑、再次编辑、筛选、导出未执行，模拟器业务验收仍FAIL/未完成，不能晋升fac29为已完整设备验收的稳定版。
2. `InspirationCategorySourceRetentionDeviceTest.kt`在分类编辑器中调用`addChipViaUi("新建分类名称…", ...)`，但真实Editor的CategorySelector只选择已有字典；该新建输入在ReaderInspirationSheet等入口。续验前应经既有管理/阅读面板UI预备分类，再在编辑器选择，不能给产品添加测试专用新建功能或DB灌入绕过。
3. 导出段直接调用`buildNotesExportMarkdown`并写resultsDir，只能证明设备进程内生产Exporter与实际持久数据的集成，不证明用户导出入口/SAF。续验应通过真实用户导出入口取字节证据，旧SAF基础合同可复用但不能把直接函数调用冒称UI导出。
4. 当前筛选仅断言本条仍可见，缺少排除对照及声称的组合条件；来源精确跳转也未执行。按实际验收范围补真实最小断言，避免只有步骤名而没有行为证据。
5. ready列两次INSTRUMENTATION_NOT_FOUND后一次实际测试。首两次原始独立stdout未在已核日志中找到，本轮不能独立确认其全部副作用；如实保留为3次仪器调用、1次实际测试执行，不追认成只有一次调用。续验安装后只读核实instrumentation组件，再主调用一次，任何失败停止业务，不在批内纠错重启主入口。

## 元数据勘误

test APK SHA匹配，但实际10,497,439B，ready.build.testApk.bytes写9,838,418B；ready.newTests未列精确路径/SHA。设备payload JSON承认控制台乱码且使用替换占位，不能作为原始中文逐字节证据。旧报告保留；下一ready补准确元数据，续验字节以可复核原文件为准。既有目录占用数字在REPORT/ready间有小差异；不影响代码或恢复结论，但下一批按明确统计时点/范围报告，不复制旧数字。

## 恢复与副作用

本次无设备操作，94历史恢复证据见上；不产生新的设备恢复义务。只新增本报告并更新current页首，不删除旧证据。

## 交付状态与下一步：94-v2

同一owner校正仪器前置与剩余UI路径、真实断言，复用产品APK及69项未变证据，仅必要重建test APK；若产品输入变化则停止APK复用、说明真实缺陷及所需范围。先做离线接线检查再一次受影响模拟器验收，严守恢复。沿用唯一task59工作区、D:/develop/env/gradle和独占锁；不新复制工程、依赖或APK对。差量/证据预算8MiB，既有工作区增量预算256MiB，新增测试二进制只在既有build输出统计。

设备按用户现行明确授权纳入下批提示词，无需再次重复询问同样范围；使用/必要启动隔离实例1，不关不重启、结束保持运行；初始保护与strict night=no不放宽，本轮证明新建且保全字节/size/SHA的精确资产才可清理。无Git/发布/D产品回灌/私库/桌面/存量清理，automation-2/3保持PAUSED。设备owner唯一；剩余工作有测试修正→构建→设备依赖，不为数量制造第二审查任务。

全产品分母未知。保留91/92既有阶段结论，93/94增量仍待模拟器闭环；MuMu不代表实体手机/MIUI。
