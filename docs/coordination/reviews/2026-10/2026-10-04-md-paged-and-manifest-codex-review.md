# 分页修复与集成清单：Codex 核查

## 任务编号/版本
CRA-MD-PAGED-AND-MANIFEST-CODEX-REVIEW-v1；2026-10-04。两项外部交付已落盘，尚未全部验收关闭。

## 输入指纹
恢复仓库 HEAD `e39a78da9c62f02614207cffb36969a32492eaea`、暂存区空。分页基线 `E:/creation-reading-assistant/next-round-joint-final-v3/android`；修复候选 `E:/creation-reading-assistant/next-round-md-paged-chapter-fix-v1/android`。清单绑定旧 final-v3，并未包含本次分页修复。

## 所有权与改动

本次为只读核查与文档收尾。
Root 仅核源码/原始日志/XML/JSON/截图/APK，补写本报告与交接/TODO。未改产品、外部报告、Git或设备设置；仅只读查询当前 MuMu 夜间模式。独立只读智能体核清单；没有执行外部会覆盖工件的比较脚本。

## 环境
MuMu serial `127.0.0.1:16416`，1080×1920、280dpi 竖屏宽布局。不能当实体手机、MIUI或手机窄布局通过。

## 执行与结果
### 规范审核
- 分页基线与修复候选逐文件 SHA-256 比较（排除构建缓存/local.properties）：仅 MarkdownChapterSource.kt、隔离后缀配置两处改变，新增两个测试，无删除。最终产品链无临时诊断修改残留。
- 实算最终 APK：宿主 `eeb6b4bbf4b737adf1283e973d6494a56326edb67922511134a5c6538b593319`；测试 `448e7bf0db2ee34f543ba3e935dfef59b902795af5fc49ba69d0a5ec6a238ed5`。final-b/f 安装读回与之相同。
- Git HEAD/暂存区未变化。禁止整候选覆盖 D WIP，未执行集成或提交。
### 需求审核
- 根因原日志：第2章 global start=377、章 text length=353；分页读取 global range 导致 index=382 越界。减去章起点只作用 wholeDocumentParse，流式路径保留；全书章表坐标不变。
- 原红日志确有 StringIndexOutOfBoundsException；当前 15 个 JVM XML 合计 **111/111**，0失败/错误/跳过。外部报告“16套件”应勘误为15个实际XML，不扩测。
- final-b 的四项分页旅程：目录1→2、2→3、3→2等待8秒、强停重开，原始 UI XML正文/底栏与持久offset377/730相符；已查看冷启动原PNG。final-b整体为 NOT PASS（滚动步骤没跑），不改写原结论。
- final-f另批真实UI切上下滚动并选第2章：真实XML正文为第2章、持久offset377；该批PASS。helper只支持分页body提取，滚动body-state显示no-paged-body-text，使用真实XML核定，不将该辅助字符串当正文失败。
- **功能验收5/5通过（100%，分母仅上述四分页旅程+一滚动旅程）**；不是全部阅读模式、全产品或永久集成通过。

## 原始证据
- [分页原报告](../../dispatches/2026-10/2026-10-04-next-md-paged-chapter-fix-v1.md)。原件有恢复口径问题，以下独立核查优先。
- [修复及原始证据](<E:/creation-reading-assistant/next-round-md-paged-chapter-fix-v1/>)：freeze、logs/jvm-red.log、logs/mdpaged-seam-diag3.log、android/app/build/test-results/testQahostUnitTest、device-mdpc-final-b/f 的 raw、db-results、ui、baseline、adb-readback、restore、summary。
- [原清单报告](../../dispatches/2026-10/2026-10-04-next-joint-integration-manifest-v1.md)、[清单工件](<E:/creation-reading-assistant/next-round-joint-integration-manifest-v1/>)。本报告分类依据原manifest/summary逐类计数，不把Git归一化hash当原始字节SHA。

## 恢复与副作用
- b/f baseline与末次原始readback独立比对：原qahost71/71文件一致、原测试包0/0一致；APK对保持。七项设置在各批driver finally时一致，测试UID包/数据缺失已核原件，转发为空。
- **阻止完整关闭的真实问题**：b/f原始baseline及末次readback均为 `Night mode: yes`。执行者报告称“无基线记录”，并在收工改 `no`，与原账矛盾。Root当前只读 adb 查询为 `Night mode: no`。因此各批driver恢复通过不等于收工整体恢复通过；报告A–F6/6不能采纳。
- 后续只由唯一设备owner查开工前基线（diag与上游），解释来源，按约定恢复口径处理并保存原始命令输出。Root未自作主张修改设置，也未让别人同时操作设备。

## 未覆盖项
- 大MD流式设备、嵌套富文本在新章source回归中的覆盖、手机窄布局、实体设备/MIUI、发布仍未新增验证。现有共享Markdown测试不等于全部新递归分支证明；没有发现本次路径错误，不因该边界重跑全量。
- 持久locator_v2中的ci/co与重新推导章号的关系未由该测试断言；本次只核全局offset及真实UI，不能宣称全部locator字段合同关闭。
- 集成清单为机械差异与归属草案：1029路径中674归一化相同、136 D独有、181候选=HEAD且D有WIP、8候选改变D干净、11双方改变、1双方新增不同、15候选新增、3 D已删。合计1029。
- **181项应保留D并no-op**，不能制造181项合并任务；真正需要语义判定的是11+1交集，另保留3个删除意图/HOLD边界。674相同是CRLF归一化Git blob相同，不能称逐字节相同；独立复核其中4文件实际仅CRLF/LF不同。七个清单绑定输入SHA全部匹配。清单原报告未完成全树11+1交集逐hunk包含关系；因此不能据此直接精确集成。注意该12交集不是delta12：后者仅4条D-WIP冲突＋1双方新增，6候选新增和1相同，不能都当语义合并。来源记录43项；另6项HOLD缺明确来源/删除意图，应保留而非推断。当前android状态未跟踪92条目展开93文件，报告142需解释额外49项范围/快照；暂不拿其文件总数直接形成操作清单。

## 交付状态与下一步
两项交付落盘 **2/2（100%，仅落盘交付）**；严格整项验收未全部关闭，不给全产品百分比。
1. 分页功能5/5已核，完成夜间恢复来源/口径和15套件勘误后再关闭整体任务；保留所有失败批原件。
2. 清单纠正归一化措辞及181 no-op分类，补12交集语义判断。分页修复接受后再增补精确payload，禁止将旧清单当最新统一候选。
3. 前两项可由不同owner并行；随后才做单一集成者的精确候选合流与受影响验证。原303/12方法未变部分复用；没有D产品/Git写入授权。
