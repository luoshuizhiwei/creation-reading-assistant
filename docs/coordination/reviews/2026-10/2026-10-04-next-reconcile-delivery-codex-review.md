# 跨章/同步/回滚三交付核查

- 任务编号/版本：CRA-RECONCILE-DELIVERY-REVIEW-v1；2026-10-04。
- 输入指纹：HEAD e39a78da9c62f02614207cffb36969a32492eaea，暂存区空；固定联合v2及九文件WIP快照，派生三文件依赖快照单列SHA。
- 所有权与改动：Root只读核查源码/报告/XML、保存依赖快照，在E文本合并ReaderProgressEffects，维护本报告/current/TODO；未写D产品、未操作设备、未构建或Git写入。独立只读审查回滚安全遗漏。
- 环境：Windows/Python3.13；git merge-file --stdout仅生成E结果，不改工作树/暂存区。
- 执行与结果：同步7/7交付SHA及编译源一致、55/55绿；回滚4/4编译源一致、74/74绿但发现两个阻断；跨章无固定报告，台账未更新，不能关闭。
- 原始证据：[Root audit](<E:/creation-reading-assistant/reconcile-delivery-review-20261004/audit.json>)；[同步报告](../../dispatches/2026-10/2026-10-04-next-sync-wip-reconcile-v1.md)；[回滚报告](../../dispatches/2026-10/2026-10-04-next-rollback-wip-reconcile-v1.md)；跨章execution.json位于E next-round-md-true-chapter-accept-v1。
- 未覆盖项：两个EPUB保全/新鲜源路径；真H1跨章完整交付；MD三方文本合并尚未编译/实测；全产品分母与D回灌/提交授权未新增。
- 恢复与副作用：本轮无设备操作；E保存原字节依赖与文档快照。跨章目录无本轮完成恢复账，不能声称其设备已释放。
- 交付状态与下一步：同步限定合并接受；回滚返修；跨章尚未交付。下一沿原设备owner完成交付，并由原回滚owner做EPUB窄返修；Root随后单owner合流。

## Standards

同步三XML25+27+3=55，0failure/error/skipped；原日志BUILD SUCCESSFUL、exit0，payload七SHA匹配编译源。固定WIP确实保留RefreshLegadoBackups与legadoImporter/loadLegadoBackups，不存在旧预案所说删除；该预案把重排误判删除。撤销此处“必须产品裁决”的阻塞，保留两侧Legado。

同步“所有文件严格超集”表述不准确：明确未采纳放宽HTTP主机名规则。它沿用已核定严格Pairing合同且有27项证据，按限定合同接受；不泛称所有历史语义均保留。没有扩大本轮安全或联网权限。

Root负责的ReaderProgressEffects三方文本合并无冲突，SHA8d045aefb87de328040e00cb18dd0efff3c65d0b7c07970f60e8d9b4f15f13bf，位于E md-threeway/merged.kt。只证明文本合流，尚未验证新源码，不拿旧17项冒充新结果。

## Spec：两项EPUB阻断

1. 候选EpubRepository.restoreBackupOver在fileOps.copyTo正常返回后直接backup.delete，没有WIP流式contentEquals。复制短写或错误内容但返回正常会删完整旧备份。74用例中的恢复失败靠非空目录引发异常，未覆盖这个路径。建backup的复制兜底也需验证内容完整后再删除旧target。下一必须保留WIP字节校验、主异常/suppressed与backup保全，不是可选加固。
2. requireFreshSource未合入：同URI派生id等于stored.id时，旧Parser发现内部epub已存在会直接沿用，replacement解析旧正文、后续复制旧字节，可能“成功但V1仍是V1”。需要将固定WIP EpubParser的fresh-source合同与Repository调用一起保全合并，明确本次临时副本所有权；不能在普通模式盲删validatedFile，因为它可能就是旧target。

FileOps实算904148cb…与D用户资产一致，可在下一明确范围纳入；对应WIP替换测试须纳入并审查，不只运行新两类。Root已保存[三依赖固定快照](<E:/creation-reading-assistant/reconcile-delivery-review-20261004/dependency-snapshot/manifest.json>)：EpubParser/FileOps/EpubRepositoryReplaceTest。它们是待审输入，非自动通过。

## 跨章交付状态与计划

固定qa报告不存在，有限文件名搜索无同任务报告；E台账仍为进行中且更新时间开工时。相关结果/恢复目录未发现可关闭的交付。此证据只能说明尚未交付，不能判断外部窗口实际仍运行或已停止。不得派另一设备owner重做；原窗口先核实际运行状态，正在执行则继续等待，已停止则续做/补报告。

当前三项交付完全验收1/3（同步限定范围）；回滚74项虽绿仍有保全阻断，跨章未交。WIP冲突同步五路径语义归属关闭5/9；回滚三路径返修，MD一条文本已合但待门禁，不把文本合流当运行通过。全产品统一分母未定。

保留阅读/数据安全方向，下一不做全量重跑、不新增功能。先补上述两条EPUB确定风险与已有跨章任务；两者独立E所有权并行，设备仅原owner。Root等待实交后集成新依赖和MD合并、定向联合门禁；D产品/提交仍没有当前精确授权。
