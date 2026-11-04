# 统一候选与验收合同：Codex 独立核查

## 任务编号/版本
CRA-UNIFIED-V1-CODEX-REVIEW-v1；2026-10-05。复核两项外部交付，未执行新构建或设备任务。

## 输入指纹
恢复仓库 HEAD e39a78da9c62f02614207cffb36969a32492eaea，暂存区空。统一候选位于 E:/creation-reading-assistant/unified-candidate-integration-v1/android。逐行重算 application-manifest 的980项D输入SHA和E应用后SHA，零不一致。补录完整源码文件指纹（含新增ReaderProgressActionsPersistTest）至本轮verification.json；后续复制与冻结须使用此完整文件清单，不能只冻结原980行。

## 所有权与改动
Codex仅维护本报告、current和TODO；外部报告及候选原件只读。无D产品源码、设备、原仓库或Git写入。

## 环境
Windows PowerShell / Python 3.13。原构建JDK17/Gradle8.9日志复用，不重新运行。

## 执行与结果
- 980行分类纠正：保持D/no-op931；双方同值no-op11单列；D干净采用14；语义采用9；新增3分页+8候选；精确组合2；保D测试1；构建配置1。原报告将11同值写成包含于931，属于统计表述错误，总行数980成立。
- ReaderProgressActions为D基座+三hunk，保留TXT实时列表、既有章号优先级及调用方；新增MD canonical分支。EpubRepository为候选基座+D缺省globalOffset契约，抑制异常/回滚闸依旧保留。
- 配置相对D不仅增加仪器依赖，也包含既有候选release debug签名显式开关；已核精确差异，无本轮发布验收。qahost后缀回退在位。不能写成“其余配置全部等D”。
- 原始退出码0、BUILD SUCCESSFUL；独立解析28份JUnit XML：424 tests/0 failures/0 errors/0 skipped。ReaderProgressActionsPersistTest五项确实调用生产函数并检查回调；EpubRepositorySaveProgressLocatorTest三项调用生产repository，但DAO是MockK，属于JVM契约证据，不是真实DB/设备证据。缺省offset测试直接核JSON；自愈用例核缺省offset后的实际SourceNavigationContract派生；退出用例核全局一致性及重读。
- 接受统一候选实现/JVM交付；尚无统一qahost双APK冻结、androidTest编译或设备通过。

### 验收合同补正（后续以本节为准）
原合同READY_FOR_BINDING及J1–J5结构采用，以下旧状态退出当前判断：
1. MD分页r3已由MarkdownChapterSource全局范围转章内范围修复，并在独立候选完成限定功能5/5与恢复。新统一APK需要回归绑定，不再派“根因未定位”调查。
2. 新APK不能继承旧安装身份/全批通过，但不要求重跑全部历史G1/N1/J3等场景。下一设备最小集J1 TXT实时偏移退出重进、J2 MD canonical滚动退出重进、J3 MD分页TOC与冷启、J4 EPUB真实笔记回源、J5 Profile中断提示跨实例。只在实际失败/未解决风险时增补。
3. 合同待跑的受影响JVM项已由本候选424项闭合；不重复全量执行。J5需要可控隔离恢复流程；直接写标记只能作夹具，不能冒充真实恢复/UI行为。
4. 旧71文件/旧serial/旧APK身份均不能当新批永久基线；设备owner开工实读，记录完整前后对账。新UID须开工确认不存在。MuMu不是实体手机/MIUI；1080x1920 density280不当手机宽度证据。
5. 同一设备与联合构建只有一个owner；其余并行任务只能离线脚本，不改Android输入、不操作ADB。

## 原始证据
- [统一候选报告](../../dispatches/2026-10/2026-10-05-unified-candidate-integration-v1.md)
- [合同原报告](../../dispatches/2026-10/2026-10-05-unified-acceptance-contract-v1.md)
- [Root逐行SHA/完整源码/XML计数](<E:/creation-reading-assistant/unified-v1-codex-review-20261005/verification.json>)
- [原始门禁](<E:/creation-reading-assistant/unified-candidate-integration-v1/test-results/gate-run.log>)
- [分页已核历史结论](2026-10-05-semantic-v3-codex-review.md)

## 恢复与副作用
本轮无设备动作；没有新增设备恢复结论。文档编辑前副本保存于本轮E目录before，原始历史报告未删除。共享源码/HOLD保全；原仓库不动。

## 未覆盖项
统一APK实装与J1–J5尚未执行；当前未授权共享D产品回灌或Git操作。真实DB/实体硬件、正式签名升级、发布、跨书和桌面边界不扩大。

## 交付状态与下一步
两项外部文档/候选交付2/2已核（合同附上述补正）。下一主任务：单owner新E沙箱后缀、qahost双APK构建冻结与J1–J5限定MuMu；可并行独立离线证据检查器，不等待其结果来构建、不复制其旧状态为PASS。实现/JVM门禁424/424=100%；新统一设备旅程0/5=0%；全产品分母未核，不能给全产品百分比。产品目标仍为阅读/笔记回源与恢复安全，当前不扩大功能。

文档收尾验证：collaboration_check检查17文件/85链接，0错误，1条入口体积告警；不为清告警删除历史。复查HEAD仍e39a78da、暂存区空。
