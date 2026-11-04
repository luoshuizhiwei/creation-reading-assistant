# 80-v2-D / Root-r1核验（2026-10-21）

任务编号/版本：80-v2-D-Root-r1。Root只做入口小修和定向离线核验，无构建、设备或外部派发。

## 输入指纹、所有权与改动、环境
原D入口b131253ad870c1ba50efc97b4cea73356aa208d2a2815e87700860db3aeeee61、绑定1c24950af4fa72db45a326d84386214ba693a4fa2b20f5b9ddbf73b7c0b1c1ed实际匹配ready，原件不改。原17工具及4冻结输入全部实际SHA匹配绑定。D离线结果13/13原日志已读取；其offline-result实际e1fce6e83a9004d883f7204ef367876d638818c2d2c833a11a41345866c0c7ae与ready宣称46a390…1476不同，原因未证，Root新ready如实登记实际值及差异，不回写历史。

## 执行与结果、原始证据
D已有同attempt精确授权恢复入口、独立输出/scratch/严格proof消费，原13项设备边界模拟证据复用，不重跑A/B/C或编译。
Root只读实际调用发现default_process_query的PowerShell查询文本本身包含匹配needle，会检出查询helper自身。红证真实match为powershell.exe PID30740，查询入口PID7216，无设备命令。
为避免再派一轮往返，Root在D/root-r1另存候选，仅在Where-Object条件排除查询PowerShell自身PID和当前Python入口PID，其余匹配条件原样保留。真实Windows进程验证5/5：旧版自身误检、修正版无执行者为空、无害真实Python worker被检出、worker导致阻塞、该自建helper退出后恢复为空。只终止Root自己创建的无害sleep进程，未动他人工作进程。
修后入口SHA8167ee6b97f2c02f2d1523c5cfbc0eee1c1765d3e569632b8b68d0f2cb122c2e。已更新实际绑定及ready于E:/creation-reading-assistant/task80-export-content-v1/D/root-r1/，原D/C冻结及输入不修改。新增38069B（ready前），无APK/工程/缓存复制。

## 恢复与副作用、未覆盖项
本轮设备0、构建0，无新恢复义务。实际设备前须核fresh归属/状态，Root-r1五项只证真实Windows进程门禁，不证Android收尾。原D13项是设备/进程边界模拟，不称设备通过。原C新hostdc736485…1d95/testb8f615ce…0500继续复用，无需重建。两个自动化保持暂停，未Git/外部消息/云/桌面/存量清理。

## 交付状态与下一步、停止条件
入口与现有编译产物可进入下一次明确授权设备批次；不再派重复实现/审查。下一步Root单owner执行probe→material→orphan→source_state→md一次，失败停后续并恢复，不关闭重启VM。新run导出精确收尾尚未获许可；79-v5旧许可已执行完，不得默认扩大。执行前请用户一次确认该新批次及仅本run已证明新建、保全设备原字节并核size/SHA后的精确导出清理；不同意则保持设备不动。运行后填实测身份授权文件并经同attempt恢复入口续接，绝不第二次main。身份/owner/冻结/预算不明即停止依赖操作。

## 长期偏好更新
用户再次要求加快开发并明确请求长期记忆。已在C:/Users/23254/.codex/AGENTS.md强化同owner实施/必要编译/验证/交接闭环、减少拆批及复用充分证据；并按允许机制在memories/extensions/ad_hoc/notes/20261021T140958-development-speed-priority.md新增一份记忆增补。不修改MEMORY.md/summary索引。用户本轮重新提供的AGENTS已包含该条，按最新文本执行。此偏好不扩大设备/删除/外部/Git授权。全产品分母未知。
