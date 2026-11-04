# 94-v9 Root核验与加速收口

## 任务编号/版本、输入指纹、环境

2026-10-23，94-v9接收核验，下一94-v10。输入为E的task94-organizing-export-integration-v1/v9/REPORT、ready及直接证据；[实施报告](2026-10-23-task94-v9-review.md)。Root实核报告/两patch/产品与仪器源码/两当前APK/三保全APK共10项指纹与大小匹配。当前产品1102346485a9e47b7b12885310f678e56b5c86d36082834fbec8732906ffc30c；当前未设备test29e2e96e49b581495cdf97bb08d811a67fad064e33f44173a0c1436bd7de19e4。fac29已是旧产品基线，不再充当当前候选。

## 所有权与改动

Root只读E候选，写本报告及current，无设备/构建/外部派发。相关进程检查未见task94/task59/GradleWrapperMain执行者。唯一task59工作区/既有依赖与构建锁不变。产品只改标题内容转发，仪器改搜索呈现与AND观测，未扩新功能。

## 执行与结果、原始证据

源码/patch证实搜索输入框已在ToolbarConfig.titleContent提供，但此前未转发到AppScreenScaffold；当前接线补齐。原始t94v9_search_presented为presented=true、editableNodes=1、placeholderNodes=1，支持搜索入口设备修复通过。原始04/20日志为AND断言失败，不能称AND模拟器通过。

原test恢复JSON独立对照合同：原代码SHA、数据完整清单SHA、用户状态均一致。initial/final保护原始结果均ok、reasons空、71/820、DB组一致、diffs空；summary恢复errors空，产品缺席、night=no、保活/未导出记录采纳为本次历史证据。离线AND修正版assemble日志和当前29e2指纹匹配，不算新设备证据。

## 未覆盖项与方向自审

AND、回源、真实导出、清空、取消仍未验。当前listTextVisible只在全屏unmerged树过滤自身不带EditableText的节点，不是“限定真实列表卡片”；嵌套文本/其他区域仍可能误命中，不能仅凭helper名称或排除单属性宣布观察正确。

生产已有inspiration-list与inspiration-card-${item.id} testTag（InspirationList.kt），下一批直接以本轮实际ID卡片出现/消失判断匹配与排除，避免查询文本自我命中，也无需新产品测试接口。一次核对剩余五段的直接入口/可见性/异步完成门禁与失败保全，再构建/单次验收，减少“一处失败一轮提示词”。不重做已过分类/新建/搜索，不重跑69项或全量扫描，不把单设备限制推广为全项目串行。

## 恢复与副作用

本次无新设备恢复义务。严格原test代码/数据/用户状态及71/820保护、strict night=no、实例不关闭重启/结束运行不变。2/3保持PAUSED，10-23已删除，无Git发布/D产品回灌/桌面私库云/存量清理。

## 交付状态与下一步

搜索入口修复已采纳为模拟器通过；整体部分完成。94-v10同owner先用已有卡片tag纠正AND观察并收紧剩余直接接线，再必要test构建（产品1102346输入未变则复用）和一次明确设备验收。源码证据8MiB/build增量预算256MiB；覆盖唯一29e2前保全必要单份10,524,133B，其余保全原地引用，无工程缓存APK对复制。未变化证据复用，失败保留原错/恢复错，不同批现场重试；全产品分母未知，MuMu非实体手机/MIUI。
