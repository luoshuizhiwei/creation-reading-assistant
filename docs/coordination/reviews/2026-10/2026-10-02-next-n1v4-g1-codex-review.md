# N1-v4 / G1-build-v1 独立核查与窄返修（2026-10-02）

## 任务编号/版本

Codex-review-N1v4-G1build-v1。来源：用户“都完成了”；本轮只核两项既定交付，不将该表达视为上一轮grilling产品方向的明确确认，也不扩大新功能、Git或设备授权。

## 输入指纹

恢复仓库 HEAD `e39a78da9c62f02614207cffb36969a32492eaea`，暂存区空。N1父候选v3与候选v4各878非生成文件，6改/872相同，无增删；8授权路径SHA均与原报告一致。G1父J1-v3与G1候选按本轮同一排除规则计876/877文件：只有build.gradle.kts一处后缀hunk和新增仪器类，875相同、无删除。原报告875/876计数与本轮规则不同，不影响已核差异集合，后续以完整清单为准。

## 所有权与改动

Root只写本报告、现有执行状态文档及自有E审查工件；只读reviewer负责N1语义。编辑前回退副本见[E before](<E:/creation-reading-assistant/n1v4-g1-codex-review-20261002/before>)。共享产品、原损坏仓库、桌面/HOLD与外部原报告均保留，不暂存/提交/push/reset/checkout，不读取Claude独立库。

## 环境

Windows、Python3.13、SDK build-tools 34.0.0 aapt/apksigner。Root本轮未运行Gradle、未安装APK、未连接设备；新鲜执行的是原始日志/XML解析、全候选差异、SHA、APK manifest与签名核验。N1原文本为UTF-16，按BOM解码；应用/模型名称仅是原报告自报，不作当前界面实测。

## 执行与结果

### Standards

0项独立源码规范发现。N1在8授权路径内6改，两个Profile文件保持父输入；G1产品代码不变，配置单hunk加一个androidTest类成立。

### Spec：N1-v4仍返修（2P1）

原始绿XML20+56+15=91 testcase、0 failure/error/skip；4个原始红testcase全部failure。绿归档与候选build/test-results逐字节相同，原日志分别有BUILD SUCCESSFUL/BUILD FAILED。上述是外部原始定向JVM证据的本轮复核，不声称Root重跑，也不是Room/设备实测。

1. **P1 等版本单独解除保护，绕过正文身份。** `LocalZipBackup.kt:257–281`，真实`JsonBridge.kt:410–414`回报equal；`StateStore.kt:160–165`按路径去掉旧义务。A提交DB后正文复制失败，义务要求hash(A)。再选同JSON、同路径但正文不同的B：完整身份不同、义务不匹配，但`isEqualRel`仍单独授权B正文写入；随后旧A义务被同路径剪除并允许导出。W3b现有桩只给skipped、未给真实equal，因此绿色不能证明此合同。equal只能说明DB版本相等，正文补偿还须匹配完整身份或可靠内容义务。
2. **P1 legacy长度直接判完成，允许混合内容放行。** `StateStore.kt:333–344`、清除链233–243，`LocalZipBackup.kt:335`。v2中断状态A目标长度11，磁盘旧正文内容不同但也11字节；导入不含A的B后，A义务继承到unresolved，B完成时clear仅凭长度清除状态。新增legacy测试预置任意11字节并期待clear成功，实际未证明来源字节一致。旧长度必须保留为未验证，通过确认来源的重试建立SHA证明，源长度相等也不能授权冒充原义务。此风险仅针对旧长度持久状态，本轮未确认已发布安装包携带它。

前轮四项原反例中3项已关闭：真实事务内、任何upsert前持久计划与strictly-newer保护；A→B→A匹配原义务的收敛；finally删除false/异常保留staging journal。旧长度安全迁移仍未闭合，另正文身份保护出现回归。3/4=75%只描述前轮四类原反例的关闭，不代表N1整体75%或产品进度。

`BookDao.kt:118–120`为真实@Transaction入口。JsonBridge测试使用真实合并代码和mock DAO，能证明回调抛错时upsert为0；runInTransaction桩直接执行block，不是实际Room回滚测试。预提交状态仍名db_committed，后续DB失败会保留阻断计划，未证实此命名单独造成数据丢失，不新增重构任务。其他JSON/WebDAV调用默认回调，未发现新增调用方缺陷。

### Spec：G1二进制准备成立，运行协议未就绪

双APK SHA与原报告一致，aapt实读宿主`com.creationreadingassistant.qahostlibrary`、测试包`.test`，targetPackage与runner正确，provider authorities随新包名命名。双包apksigner verify均exit0，证书SHA均`05249d786e3d3d117bb5bd7b1f83512a0c9b8f1ee4e502112c4b2d9ccbd7b9a1`。原构建日志BUILD SUCCESSFUL可复核；不重建既有有效二进制。

- **P2 确定的夹具副作用。** 新仪器类`LibraryLifecycleAcceptanceTest.kt:101–106`的contentFile会mkdir并在文件缺失时创建正文。242的`!contentFile().exists()`因此必然为false，且检查把已删除正文重新写回；199/222会把误删掩盖为存在，272的“如实记录”也会制造文件。断言路径必须纯读，创建仅限seed。
- **P2 撤销协议依赖不能保证的跨仪器会话。** KDoc44–47要求UI删除→独立instrument断言→独立instrument种新改动→UI撤销。真实`DeletionUndoStore.kt:10–13,25–29`凭证仅在内存，`DeletionUndoPolicy.kt:8`窗口12秒。跨仪器调用有目标进程重启/超时风险，本轮未运行不能声称可保留凭证；不得为迁就测试更改产品凭证期限/持久化合同。应将删除、读回、新改动与真实UI撤销保持在同一存活会话、同一仪器调用中，满足真实时限。
- L1现方法只确认同名视图存在并输出条件，仍需设备流程核具体预期条件、应用视图后的可见书与前后结果；同名结果文件会覆盖，设备侧须逐次保全或用不同phase，不能拿一个最终JSON当两次证明。
- runner只对精确`.qahost`替换Application，新`.qahostlibrary`会透传生产Application。本轮包命名空间成立，不能称它沿用裸Application启动隔离；新包是否预先不存在和启动/设置副作用留给设备基线与后账。现有Hilt UI需求不自动授权改runner。

## 原始证据

- [本轮核查JSON](<E:/creation-reading-assistant/n1v4-g1-codex-review-20261002/verification.json>)与[脚本](<E:/creation-reading-assistant/n1v4-g1-codex-review-20261002/audit.py>)，含清单差异、8SHA、XML testcase、APK与manifest输出。
- [N1-v4原报告](../../dispatches/2026-10/2026-10-02-next-n1-repair-v4.md)、[G1原报告](../../dispatches/2026-10/2026-10-02-next-g1-library-build-v1.md)，原报告与二进制保留，不覆写其历史结论。
- N1增量patch SHA `b189c5764631a6e7abb1f1131ade40f3c7818b1cbc2fffa774b40cb01e8c3e13`。
- G1宿主SHA `4bf670ab4006c6d60a7e18d8d31d0856d55755d6b8ff6994b3f41edc3b272531`，测试SHA `70b04ecf3628caeedc5cb2d14a89628d23ab8927665efcdf3cd30121bcd95c8f`。

## 未覆盖项

真实Room/kill/存储故障/UI/SAF未运行；N1源码未合流；G1设备验收未开始；D2真实重关联不包含在G1包。MuMu许可不等实体硬件/MIUI，正式签名/升级/R8/F及桌面/HOLD维持原边界。移动端grilling已得到阅读优先、轻量灵感、桌面承担深入整理、稳定日常自用四个回答，最终共享理解核对未答；旧总规划§1.1–1.2尚未据此替换，不用其中扩展候选派新功能。

## 恢复与副作用

本轮零设备操作，原包/文件/DB/设置无需本轮恢复，不重复旧71后账。编辑只替换当前执行状态，TODO其他项目后缀与skill历史保留原字节；HEAD/暂存区再次核验。E回退副本保全原文，未直接改自动memory索引。

## 交付状态与下一步

两项实际交付都成立（2/2）；完整语义/可运行协议验收0/2，尚未到设备。N1 Standards0/Spec2P1，G1 Standards0/Spec2P2；二进制准备限定成立。下一N1-repair-v5只改身份/义务与真实分类测试，保留已成立事务接缝；并行G1-build-v2只修单仪器类及验收协议，重建受改动测试APK并重新核冻结合同。各自新E候选互斥；不让设备等待无效夹具、不重跑W2/迁移、不扩新功能。通过后由唯一设备owner串行MuMu验收，再准备新精确集成清单。

A–E限定恢复5/5=100%、既定联合门禁2/2=100%继续复用，不能向N1/G1或全产品外推。历史23功能表11/23≈47.8%只为交付/归属证据覆盖，产品定位调整后其跟踪范围须重核；全产品验收分母未核定，不能给可靠整体完成百分比。
