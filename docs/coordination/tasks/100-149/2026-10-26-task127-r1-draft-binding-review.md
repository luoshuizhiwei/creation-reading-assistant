# 127-r1 派发草案的具体缺口复检

````text
任务127-r1，同127-v1 owner/ZCode桌面GLM-5.3-Flash，完全离线，和126-v3只读设备lane并行，无ADB/build/业务许可。只修下面实际缺口，不重做充分源审/哈希/编译。
Root已读127-v1 ready/REPORT/contract-check/draft。有效发现night硬编码no、attempt门禁前消耗，保留。需要订正：
1. 报告/ready/草案称instance1未启动及仍需启动许可是旧126-v1状态。任务127输入已告知126-v2新boot763ee045-fff4-455c-9b31-a1988f91fb73/产品缺席，用户确认卸载及night变更；当前运行与否只由126-v3 fresh核，不能写“未启动”为事实。不启动VM是当前边界，草案只依赖fresh身份，不要求无端新启动。
2. host124 ROOT固定task124/lane-b，host无CLI参数；草案却承诺所有evidence输出task128/lane-b。请明确实际文件落点/哪些只引用、REPORT新目录、是否要最小新host派生来隔离。不得复制工程、改原host或旧证据，不执行host。用现成ROOT会写哪几处，和现成空状态/冻结交付是否冲突，具体给最小绑定方案供Root审，不含省略号的执行路径。
3. 派发草案缺显式存储预算与预计产物/复用保留策略，补REPORT/差量<=1MiB、必要raw日志/XML/截图<=8MiB；现成APK/原件引用不copy。若真实全旅程可能超过说明估算，不改失败标准/不删原件。
输出E:/creation-reading-assistant/task127-offline-next-journey-v1/recheck-r1/REPORT.md、draft-next-dispatch.md、ready.json；不覆盖v1红草案；新文档全四反引号、准确owner/依赖/绑定/预算/停止点。任务总差量<=1MiB，10分钟有界。对已充分问题直接复用。Root正在问用户占用前台与night决定，未得到回复不推断许可。不其他任务/私库/自动记忆/Git/云/设备操作。最后单独一行：这是任务127-r1。
````