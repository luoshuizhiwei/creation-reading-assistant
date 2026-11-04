# 三组语义决策v3：Codex核查与合流门禁

## 任务编号/版本
CRA-SEMANTIC-V3-CODEX-REVIEW-v1，2026-10-05。三个只读任务覆盖3+2+7=12文件。

## 输入指纹
HEAD `e39a78da9c62f02614207cffb36969a32492eaea`，暂存空。Root实算12文件候选/D双侧24个SHA，全部与三份execution.json一致。基线仍final-v3；分页修复另绑md-paged-chapter-fix-v1，不自动称统一候选已验。

## 所有权与改动
Root只读审核源码、提议patch、调用方及报告；写本报告/current/TODO/skill日志。外部未实施。未改源码、Git、设备或旧证据。

## 环境
Windows/Python只读SHA/Git只读；未构建、运行测试或操作设备。模型为报告自报Ling/DeepSeek/GLM路由，未由Root界面核实，不建立永久模型排名。

## 执行与结果
### Standards
三报告遵守互斥范围，只有各自E工件与独立报告。拟议ReaderProgressActions合并件未应用/未编译。机械清单980、181no-op等复用上一轮，不重新全量统计。
### Spec
三组决策作为候选合流输入可采纳（3/3任务、12/12文件形成处置），不等于实现/联合验收通过。
- ProfileRoute、ProfileViewModel采用候选，已纠正zip横幅/状态属候选；ProfileViewModelTest保持D新重建索引测试。配套LocalZipBackup/LocalZipRestoreStateStore同批引入，ProfileScreen搜索等D WIP保留。
- ReaderProgressActions精确组合：保D atChapterEnd/TXT目标函数/实时TXT保存/ci推导，补候选MD canonical helper与保存分支。Root已读提议patch，三hunk仅imports/helper/MD分支，合并工件含全部上述行为；真实分支接缝需要合流后测试，不只凭存在符号通过。
- ReaderProgressEffects采用候选；原diff中D侧保留旧HEAD路径，并无候选缺失的D新语义。候选有MD恢复/锚定/防抖/注解分派，直接依赖ReaderAnnotationNavigation。D ReaderActions/LayerBuilders/Scaffold/Screen保留并按真实调用验证。
- 其余七文件：SyncRepository、ShelfImporter、EpubParser、WebDavBackup及两测试采用候选；EpubRepository需要精确组合。
- Root核实EpubRepository.saveProgress：D的absoluteOffset.takeIf{it>=0}必须保留，候选把缺失全书偏移退化为legacyOffset。已读LocatorBuilder合同：EPUB缺全书offset应省略v2 offset，由章首+co派生。禁止直接整文件取候选丢失该行。

合计：9文件采用候选、1文件保持D、2文件精确组合；其直接依赖与新增文件按机械清单核绑定，不能只应用12路径。

## 原始证据
- [Profile三文件](../../decisions/2026-10/2026-10-05-profile-merge-decision-v3.md)及`E:/creation-reading-assistant/profile-merge-decision-v3/execution.json`。
- [阅读进度两文件](../../decisions/2026-10/2026-10-05-reader-progress-merge-decision-v3.md)及其E目录execution/hunk-classification/提议patch。
- [其余七文件](2026-10-05-remaining-semantic-merge-review-v3.md)及其E目录execution/hunk-verdicts/diffs。
- [上轮机械与恢复核查](2026-10-05-restore-manifest-v2-codex-review.md)。原三报告、v2误判文本与失败批保留追溯。

## 恢复与副作用
本轮没有设备写入，无新增恢复需求。既有分页5/5、111JVM与night yes/71文件/APK对恢复结论复用；不以这些旧APK证据证明未来合流APK通过。

## 未覆盖项
- 新合流树还不存在，提议patch未编译。2处组合行为须定向回归，不能报告已集成。
- D的canonical locator ci>0但co仍全局的合同偏离登记为既有风险；本轮不顺带重写定位体系，也不将其称“已证无影响”。合流门禁要测试当前实际消费路径并注明未覆盖消费者。
- WebDAV错误体文案/文件名字符限制等两侧差异有候选已验合同依据及外部判弃说明；仅适用于新E候选提议，D资产保持，不能据此取得永久D覆盖权限。
- 三个D已删测试HOLD保持删除状态；临时qahost后缀/仪器资产不自动成为生产提交清单。
- 手机窄布局、实体设备/MIUI、发布没有新增证据。

## 交付状态与下一步
本轮语义决策完成100%（3/3任务、12/12文件）；实现合流与新联合验收尚未执行，全产品分母未定。
里程碑复盘：保留稳定阅读→准确续读/回源→数据保全主线；当前关键路径是将已核修复和D WIP组合成可用统一输入，不扩展桌面、跨书新入口或发布。下一单一集成者从D源码快照保全WIP，精确引入候选payload、两组合与分页增量，所有构建/证据在E；未明确来源不带入、HOLD不恢复。完成受影响编译/JVM与必要MuMu回归，未变原始证据满足指纹时复用；D产品应用与Git提交另核当前范围。
