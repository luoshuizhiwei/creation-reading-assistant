# 回滚 v3 / 阅读 v2 核查与下一步

- 任务编号/版本：CRA-ROLLBACKV3-JOURNEYV2-REVIEW-v1，2026-10-03。
- 输入指纹：HEAD e39a78da9c62f02614207cffb36969a32492eaea，暂存区空；25路径 manifest SHA a0240a395c20251f480378ed07ba901c0c0061c79cd98ea4b43985b229ed491d。
- 所有权与改动：Root 只读代码/证据，维护本报告/current/TODO，并在 E 准备29路径源码 payload；没有产品回灌、Git写入、构建或设备操作。
- 环境：Windows/Python 3.13。code-review 两轴核查；独立只读审查者定位 MD 回源链，不改源码。
- 执行与结果：树 SHA、XML、运行日志 SHA、安装记录、定位 JSON、截图与恢复前后账核对，见下。
- 原始证据：[Root audit](<E:/creation-reading-assistant/rollbackv3-journeyv2-review-20261003/audit.json>)；[回滚 v3 报告](../../dispatches/2026-10/2026-10-03-next-reselect-rollback-fix-v3.md)；[阅读 v2 报告](../../../qa/2026-10/2026-10-03-next-read-note-journey-accept-v2.md)。
- 未覆盖项：MD 回源尚失败；旧 v1 最终测试 APK 对应仍不确定；独立 notes 行、跨书、临时查阅、实体手机、KeyStore绝对零副作用没有新增证明。
- 恢复与副作用：本轮未操作设备；阅读批次落盘前后53文件/3原APK/8设置一致，夜间 no 是本批基线，不覆盖 v1 yes 历史。编辑前文档快照位于 E 本轮 before。
- 交付状态与下一步：回滚限定源码/JVM通过；阅读 TXT/EPUB非起点与高亮个人批注通过，MD静默无位移单列修复。

## Standards

回滚 v2→v3 883/883 输入只变四授权路径，其他不变；两入口改为 backup.exists，四个目录残留用例检查 target、目录哨兵和 DB 不写。红47/4，绿47/0 errors/0 skipped；原 Gradle 绿日志 BUILD SUCCESSFUL。该窄修通过，TXT suppressed 仍按 API/源码可观察边界表述。

阅读输入实算882文件，候选883：只变 app/build.gradle.kts，新增一个仪器类，产品没有变。报告把输入写为883应改读882；测试 APK 报告表格写9,171,667字节，实算与 current_batch 为9,216,574，SHA一致。这两个是报告元数据勘误，不需要重建或重跑。

测试APK当前SHA5197f404…，宿主890c3a10…；设备安装读回文件与B2一致，驱动实算2b0db343…。batch-association 顶部保留B1、current_batch为B2，B2 runs数组未填，报告却列60/61/62/90；以运行日志时序和当前安装读回作限定关联，不把空数组称完整机器索引。原15份日志SHA全匹配：13PASS/1FAIL/1OTHER。以后最终机器索引需明确当前批次，旧失败不删。

## Spec

实际定位 JSON：TXT offset2191，EPUB ci2/co108/offset822，MD offset1270；各冷读PID不同，值保留。TXT回源和EPUB第三章回源截图成立；EPUB图实际竖屏第三章44.0%且目标高亮可见。MD两次跳转截图仍8.7%，目标未见；其合同不能绿。

个人批注通过真实入口写 highlights.note，冷读JSON保存中性内容；不是独立 notes 行创建。clearBookCache三个旧证据复用，不扩大为UI清缓存按钮通过。

阅读报告“6/7”分母混用了保存、回源与恢复。Root采用清楚口径：三格式非起点保存/冷读3/3；完整非起点回源2/3=66.7%；高亮个人批注1/1；恢复账1/1。不能将各项相加成全产品百分比。

## 合流准备与退出条件

Root已准备[29路径精确源码payload及manifest](<E:/creation-reading-assistant/rollbackv3-journeyv2-review-20261003/payload29-preparation/manifest.json>)：既有25＋回滚四路径互不重叠，29/29拷贝后SHA一致。该目录只是源码交付包，未构建、未设备验收、未应用D；不包含本批临时后缀/仪器驱动。MD修复后再更新最终范围，避免先建一个马上过期的联合APK。

下一仅修MD回源并定向验收。保留阅读优先、安全优先及既定有限功能，不新开大重构、不重跑旧 N1/G1、不扩展F发布。关键修复成功后Root集成、单owner构建及MuMu受影响验收；D回灌与Git仍需当次精确授权。

只读调用链定位：ReaderLayerBuilders 设置 pendingHighlightId，ReaderSessionEffects 透传，ReaderProgressEffects 第521–646行解析 locator 后仅分派 EPUB/plain/TXT，没有 Markdown，随后清 pending。ReaderScreen 给 Markdown 的 plainContent 为空，解释静默消费且无位移。已有 Markdown source navigation 可参考同文件447–506行；ReaderActions 的直接跨章 jump 只 goToChapter，不能忽略待定位offset。下一优先修 ReaderProgressEffects 的注释分派与真实消费回归，复用现有延迟章节导航；源码与现象高度吻合，改后仍需实测，不当作已验证修复。

两轴总结：Standards源码未发现新的阻断问题，元数据/索引不齐保留为证据限制；Spec一项阻断为MD非起点回源静默失败。
