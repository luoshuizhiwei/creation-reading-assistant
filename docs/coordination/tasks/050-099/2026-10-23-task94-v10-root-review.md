# 94-v10 Root核验与导出收口

## 任务编号/版本、输入指纹、环境
2026-10-23，94-v10接收，下一94-v11。[实施报告](2026-10-23-task94-v10-review.md)。E的v10 REPORT/仪器/patch/修后宿主/产品APK/test APK/保全29共7项SHA大小实核匹配。产品1102346…06ffc30c未变；仪器f2ccb286…ab6d8bfd、test245b8972…34bd46d3；修后宿主50e83b48…5a1622fb尚未执行。唯一task59工作区/依赖/锁复用。

## 所有权与改动、执行与结果、原始证据
Root只读E，写本报告/current，无构建或设备。原始70_filter与80_source_jump均pass；04-run确有StorageVolume标签断言失败。AND卡片作用域与精确回源设备证据采纳，不能称导出成功。
原test恢复代码SHA/数据完整清单SHA/用户状态独立对照合同匹配；initial/final均ok、reasons空、71/820、DB组一致、diffs空。summary恢复errors空、产品缺席/night=no/在线保活/未导出记录采纳为历史恢复。

## 未覆盖项、方向自审
剩余导出原字节、清空、取消。当前宿主移除craExportStorageRootLabel仅绕过前一个跨域比较；仪器prepareExportDestination仍将StorageVolume.getDescription作为rootLabel，并在DocumentsUI树查相同text。已知provider primary标题SM-S9280与StorageVolume描述不同，后续选择根仍有同类契约缺口，不能称“只剩已修宿主、原APK直接可跑”。
94-v11应明确区分：provider root_id/document_id及真实UI标题、StorageVolume.isPrimary及描述、canonical目录、系统返回documentId。UI选择用实际provider对应标题，描述独立记录；用primary身份/目录/URI证明同一资产，不强求不同显示字段相等，更不能删掉所有身份校验。同步宿主参数/preflight/receipt/cleanup后一次核对剩余导出链，再必要test构建/新绑定，产品不改不重建。已通过项复用，不重复全量测试或仅为日志另开批次。

## 恢复与副作用
本轮Root无设备新恢复义务。原test代码/数据/用户状态恢复、71/820保护、strict night=no及实例不关闭重启/结束运行均不放宽。未知归属不删，E原字节size/SHA及设备当前字节匹配后才许可精确清理，探测失败不能算不存在。无Git/发布/D产品回灌/桌面私库云/存量清理；2/3 PAUSED，10-23已删除。

## 交付状态与下一步
94-v10部分完成，AND/回源通过，剩余三项未验。94-v11同owner修导出三方字段合同→必要test构建→一次设备/恢复，依赖串行，不制造泛审查。覆盖唯一245b测试APK前必要单份10,524,492B保全，旧原件引用，无工程/缓存/APK对复制；差量证据8MiB/build预算256MiB，实际体积按报告时点。全产品分母未知，MuMu非实体手机/MIUI。
