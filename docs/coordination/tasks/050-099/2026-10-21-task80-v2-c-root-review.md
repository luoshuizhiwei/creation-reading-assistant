# 80-v2-C Root核验（2026-10-21）

任务编号/版本：80-v2-C / Root。本轮只核集成交付，无构建、设备或外部派发。

## 输入指纹、所有权与改动、环境
新host实际53804578B/SHA dc736485d1fee38a44e3cc250cebaea7b08e69b42f51d1cd9a5b0124beba1d95；test10340750B/SHA b8f615ce40a38d3f92c8e5bb2d74006e4b571189f0fa661caef4835852790500，均核同ready。C/candidate六项新工具、Material/两份判定/判定测试/ReaderSelectionToolbar变更源码实际SHA逐项核同binding。runner ea51a306e8d576a6be132817386df3943d5541077e3c2acc8f35b6def3dde18a。旧host/test在C/preserved实际SHA核同，不重复制。
ready d3cfbb181f4fb265366f2d5e6143aeda67dac8834cc7a90b1a12941d06d97aa1，provenance60846e33f4311dec6d0c8184965fd37181518c507c233fe89fd3fc7be0c5d80d，freezedfbae33b839f7614156cf92a1e2fffa5676d3fb0bc055ed12cb15b67c5d2c4ac，binding227e1bd2519b502c22dc36a995e1bcb1ee40e1bced2b0346fb6b7c44c6a76745均实际匹配。唯一59工作区及缓存不变。未见task80/GradleWrapper活动，不据此覆盖owner。

## 执行与结果、原始证据
C/logs/01-build-host-and-test.log原始BUILD SUCCESSFUL in1m20s/EXIT0，02-compile-unit-test-sources.log成功12s/EXIT0。接收main/androidTest/test编译产物；A原19项/变异及B19项离线证据未变继续复用，不重复运行。原四静态门禁结果引用现有C报告/日志，Root仅针对变更指纹核验，不重新扫旧producer。attempt ledger尚不存在，设备NOT_RUN。

## 未覆盖项、恢复与副作用
授权后的同attempt恢复续接尚无可执行控制入口。当前runner只有首次恢复内调用，不能先假设可用再上设备；Root技术决定是独立恢复入口，仅复用原attempt身份/冻结/归档/基线，核授权与fresh身份后调用已绑定模块及bounded恢复，不调用main/install/instrument。首次未授权及原FAIL留原路径不改，新续接回执/最终proof用新目录。该入口应在不动APK的前提下完成定向离线验证并纳入实际工具绑定。
新导出收尾许可没有授予，79-v5许可已执行完，不可复用。运行后生成的URI/archive等不能事先捏造。Root后续放行设备需同时明确本轮精确资产收尾许可或保全阻塞路线，不让执行者自行授删。
C+integration实测63952344B（含63636714B必要旧APK保全），在96MiB内。没有设备动作或新恢复义务；原设备最新状态仅79-v5历史，不是实时事实。无Git/外部消息/D产品回灌/桌面/云/存量清理，自动化保持暂停。

## 交付状态与下一步、停止条件
实现与编译/冻结交付接收；设备尚未放行。下一步80-v2-D唯一入口owner补同attempt授权恢复入口，复用本批新APK、源码、构建日志及冻结，不重建、不重做A/B；只新增必要控制脚本与现有绑定差量，不搭新框架。入口完成并核后再发单次设备批次。普通可确定接缝不让用户决定技术实现；实际设备/文件操作按本轮明确授权执行。身份/owner/冻结/恢复合同不明停止。全产品分母未知，无新模拟器整链结论。
