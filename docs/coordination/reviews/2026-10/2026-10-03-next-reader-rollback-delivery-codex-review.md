# 回滚 v2 与阅读旅程 v1：Root 核查

日期：2026-10-03。范围为用户“都完成了”后的两项交付核查；没有产品源码、Git、设备写入。HEAD 实读 e39a78da9c62f02614207cffb36969a32492eaea；暂存区空。

- 任务编号/版本：CRA-READER-ROLLBACK-DELIVERY-REVIEW-v1。
- 输入指纹：上述 HEAD；integration-ready-v1 manifest SHA a0240a395c20251f480378ed07ba901c0c0061c79cd98ea4b43985b229ed491d；原始 XML/日志 SHA 见 audit.json。
- 所有权与改动：Root 只读审核 E 候选，写本报告/current/TODO；独立审查者只读两函数/测试。
- 环境：Windows PowerShell/Python 3.13；没有启动 Gradle 或操作设备。
- 执行与结果：解析原始 XML、SHA 对账、核安装日志/JSON/截图；结果如下。
- 原始证据：audit.json、v2 logs/results、阅读 v1 device-baseline/device-evidence/freeze/screens。
- 未覆盖项：非起点定位、个人注释、最终测试 APK 安装关联、实体手机及旧 KeyStore。
- 恢复与副作用：仅文档更新，编辑前快照在 E 盘本轮 before；旧设备恢复账按下述限定核定。
- 交付状态与下一步：限定采纳，两个窄补充后再集成；不宣称整体完成。

## 结论与实际证据

1. 回滚 v2 的普通文件 backup 保全修正成立；独立只读审查与原始 XML 对应。红 43 项/6 失败，绿 43 项/0 失败/0 error/0 skipped，green Gradle 原日志 EXITCODE=0。未重跑测试。
2. 两处入口为 backup.isFile，目录等同名残留尚未入口拒绝；注释“backup 位保证不存在”强于实现。下一最小补充为存在任何同名残留均拒绝，并增加两格式目录残留用例。恢复 helper 保留主异常、suppressed 和 backup；TXT API 返回字符串，测试未直接断言 Throwable 的 suppressed，不能称与 EPUB 同等异常观测证明。DB 成功后异常属于未测理论边界，没有证据据此新开大修。
3. 固定 v2 报告、execution.json 与 patch 交付尚缺。不得仅凭 XML 将最终源码/清单绑定为完整交付；下一补精确四文件及执行/测试指纹，不无谓重跑已通过内容。
4. 阅读旅程原报告自报 7/7，Root 限定采纳三格式首段高亮保存/冷读/回源，以及三格式 clearBookCache 后保留高亮且正文缺失保护。后者走真实仓库 API，不是 UI 清缓存按钮。全部 locator 位于 offset=0、ci=0、co=0；EPUB 截图实际为竖屏第一章 0.0%，不能证明非起点定位。个人注释未真实输入，摘录笔记未覆盖；不据此关闭全部笔记需求。
5. freeze 测试 APK 实算 b46ce1af6e58adb7d48986cb63a3c50ba9d496cb555ca60ff0ff329ed067f947，installed-apk-sha 记录却为 9c6e9f430c0c082e7cb3832ce15b546bf4d9bae799f458b858f4bb4325ad0f50。宿主一致。需要每批驱动/安装/日志关联，不能把最终重建 APK 追溯绑定所有旧运行。
6. 原始 run index 40 条为 19 PASS、11 FAIL、10 OTHER；40/40 日志 SHA 一致。11 次调试失败保留，19 个 PASS 不是 19 项功能合同。恢复前后持久文件 53/53 相同；原 APK 格式不同，但 package+SHA 3/3 相同；八项设置相同，包括密度 280、night yes。该结论来自落盘前后账，未在本轮重新读取在线设备。新 UID 卸载不等于实测 KeyStore 绝对零残留；公共诊断临时文件清理需按本批实际路径确认。

原始报告：[阅读旅程 v1](../../../qa/2026-10/2026-10-03-next-read-note-journey-accept-v1.md)。Root 独立核对输出：[audit.json](<E:/creation-reading-assistant/reader-rollback-review-20261003/audit.json>)；两份 green、两份 red XML SHA 与恢复语义比较见该文件。

## 规划复核与下一步

保留阅读优先、数据安全优先的既定方向；复用已成立首段/正文移除与恢复证据，下一不重开旧 N1/G1 或全量构建。

- CRA-RESELECT-ROLLBACK-FIX-v3：E 独立四路径；补任何 backup 残留拒绝的窄修及最终交付清单，无设备所有权。
- CRA-READ-NOTE-JOURNEY-ACCEPT-v2：另一 E 独立候选；三格式非起点冷读回源，EPUB 第二章，以及实际可达个人注释保存（入口不存在则明确未验）；统一冻结/安装证据。MuMu 唯一 owner，不改产品。
- 两项完成后 Root 才准备新联合精确范围。现有 25 路径冻结输入保持；拟纳入四路径形成至多 29 路径需实际去重/对账，当前没有已冻结 29 路径候选。D 共享 WIP 未回灌，不暂存/提交。

进度口径：本轮定向 JVM 43/43=100%；三格式首段旅程 3/3=100%；本轮有意义非起点证据 0/3=0%（尚未验证，不代表产品失败）。A–E 恢复已退出；全产品统一分母未定，不能计算全产品完成率。上述数字不相加成产品百分比。
