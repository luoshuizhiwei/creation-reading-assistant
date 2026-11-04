# 联合v2、阅读覆盖、WIP预案核查

- 任务编号/版本：CRA-THREE-DELIVERIES-REVIEW-v1；2026-10-04。
- 输入指纹：HEAD e39a78da9c62f02614207cffb36969a32492eaea，暂存区空；32路径manifest SHA cf05ea577548de034dfbef16c612c658f60c48bdc4b62d352061a70ae7107358。
- 所有权与改动：Root只读核查代码/证据/32路径WIP；写本报告/current/TODO与E审核工件、九文件快照。没有产品源码、Git或设备写入。
- 环境：Windows/Python3.13。独立只读审查回滚双实现归属；没有重跑构建或设备批次。
- 执行与结果：32SHA、树差、217JUnit、20运行SHA、APK与恢复账、三方分类、真实章节划分核对。
- 原始证据：[Root audit](<E:/creation-reading-assistant/integrationv2-three-deliveries-review-20261004/audit.json>)；[联合v2](../../dispatches/2026-10/2026-10-03-next-integration-build-accept-v2.md)、[覆盖复核](2026-10-03-next-reading-coverage-review-v1.md)、[集成预案](../../decisions/2026-10/2026-10-03-next-integration-scope-precheck-v1.md)。
- 未覆盖项：真正Markdown跨阅读器章节设备验收；九路径WIP语义合并；实体手机、发布、独立笔记/跨书产品入口决定不由本轮新增通过。
- 恢复与副作用：联合原始前后53文件/3APK/8设置相等；本轮仅审核落盘账，没有现场设备再验证。九文件原字节快照与编辑前文档保存在E。
- 交付状态与下一步：联合源码/JVM/最终APK限定通过，设备合同3/4通过、跨章1项未证；WIP预案分类通过、实施建议部分须纠正。下一三个互斥owner并行。

## Standards

32/32候选SHA匹配。统一排除生成物/local.properties，基础882→候选885，变4旧文件＋新3文件，无额外产品改动；外部883→886统计口径不采用。10份原XML217/217，0failure/error/skipped。20份运行SHA全匹配，17PASS/3FAIL保留。

最终宿主4bce2b4e…与test6d83b109…实算匹配安装读回。test实际9,219,191字节，报告表误写9,166,635。B3机器批次仍占位，current B4有全SHA和方法列表；旧批次关联不补造，不将B4追溯绑定所有旧运行。

WIP预案五类实算一致：无需写10（6 tracked＋4 untracked）；可直接应用13（5同HEAD＋8新）；须合并9。Root读取32路径当前原始SHA与预案一致，未漂移。换行规范化只用于比较，不能因此改掉固定输入SHA；精确操作与EOL转换需另记。不要为整齐全仓renormalize。

报告称范围外1550与总1570/范围内19存在1条计数偏差；不将此近似数当范围外文件可删除清单。共享WIP是资产，不可凭“更新较晚”舍弃已验修复，也不可用候选直接覆盖D。

## Spec

联合单章MD回源截图/locator、TXT实际入口backup文件拒绝、EPUB实际入口backup目录拒绝与正常TXT替换，三项限定合同采纳；目录细分保护其他变体由47JVM复用，不声称两格式全矩阵设备验过。

“MD多章跨章PASS”不成立：IntegrationBuildV2Test夹具只有一个H1书名与四个H2（## 第n章）；MarkdownDocument.doc真实规则只按H1拆阅读器章节，H2-H6仅渲染块。定位原始JSON仍ci0/co462、chapterTitle为整书标题。md2-jumped竖屏截图目标可见46.8%，证明同一阅读器章节内长文回源，未触发goToChapter/加载后pending消费。合同应记NOT PROVEN而不是产品失败。下一以多个H1且document.chapters至少3、目标ci>0并真实移到另一章补验；不重跑217JVM或其他三合同。

覆盖报告将旧v1测试APK缺证说成被v2关闭，与Root已记录历史边界冲突：保留未知，不追溯补证。它又将既定可达书内旅程描述为需重新裁决旧NOTES入口；当前已授权书内高亮旅程完成，不因此重开旧独立笔记页或阻塞当前集成。跨书/临时入口需要单独产品范围，维持待决。

## 下一批与规划复核

保留阅读优先、数据安全与共享资产保护；完成一个构建不代表可以直接回灌。当前关键路径是“真跨章证据＋WIP保全合并”，不扩新功能。

1. 设备owner：CRA-MD-TRUE-CHAPTER-ACCEPT-v1，只改新E设备夹具/驱动，补真实H1章节合同与完整恢复。
2. 同步owner：CRA-SYNC-WIP-RECONCILE-v1，在独立E副本语义合并SyncRepository/WebDavBackup/ProfileRoute/ProfileViewModel/ProfileViewModelTest；相关定向测试。Legado删除/保留争议先查范围内证据，无充分依据保存备选patch与未决边界，不擅自复活或删除功能。
3. 回滚owner：CRA-ROLLBACK-WIP-RECONCILE-v1，在另一E副本合并ShelfImporter/EpubRepository及其两测试；同时保全既有content_hash/fileOps等WIP与已验回滚保护。

第九冲突ReaderProgressEffects由Root在两分支完成后合流时负责。两实现owner无设备所有权；设备owner不改产品。Root已冻结[九路径WIP快照manifest](<E:/creation-reading-assistant/integrationv2-three-deliveries-review-20261004/wip-snapshot/manifest.json>)；后续任务用快照，不悄悄刷新D输入。合并结果必须新SHA/新测试，不沿用旧32SHA装作未变。D回灌/暂存/提交仍没有本轮授权。

进度：联合源码32/32=100%，JVM217/217=100%，最终双APK一致2/2；设备合同3/4=75%，跨章尚未证。WIP冲突解决0/9（仅预案与快照），不与功能通过相加。全产品统一分母未知。
