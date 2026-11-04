# 三项并行交付 Codex 核查

## 任务编号/版本与输入指纹
2026-10-04；核查 CRA-ROLLBACK-WIP-RECONCILE-v2、CRA-SYNC-MD-JOIN-v1、CRA-JOINT-MUMU-DRIVER-v1。恢复仓库 HEAD e39a78da9c62f02614207cffb36969a32492eaea，暂存区空。三份原报告保留，不覆盖历史结论。

## 所有权与改动及环境
Root 只读产品候选，核查源码、payload、原始 JVM XML；重跑驱动离线测试并执行纯函数反例。没有 Gradle 或设备执行，没有 D 产品源码或 Git 写操作。

## 执行与结果
- EPUB：去重后 6 套件 142/142 绿；重复命名 XML 不累加。原 v1 74 测试方法保留。三路径 payload 与候选匹配。fresh-source 与备份内容闸门已实现，但整体返修。
- 同步/MD：13 套件 154/154 绿，13 路径 payload 匹配；基树差恰六个 MD 文件。ReaderProgressEffects 相对 Root 版只有两处章号映射与 SE4 三个 key 嫁接。限定源码/定向测试接受；组合时序、书架章号和新联合 APK 设备证据仍开放。
- 驱动：重跑离线 55/55 绿，但三个离线反例均暴露错误：空 results 返回 PASS；TERMINATED 返回 PASS；已有 APK/数据被卸载、清空后 cmd_restore 仍返回 (True, ok)。准备合同返修，不可运行设备。

## 未覆盖项与返修依据
1. EPUB fresh 解析成功后，DAO/备份/暂存等后续失败，finally 只删除 .repairing，validatedFile 仅成功路径删除，因此 .importing 可残留。Parser 自己的解析失败清理不能覆盖此后失败。需真实 Parser 回归验证这些失败分支。
2. EPUB 同书并发串行性尚未证明。报告所称 viewModelScope.launch 内循环不等于多个 coroutine 间互斥；固定 .importing/.repairing/.backup 仍有竞争风险。先核实际 UI 去重与调用可达性，再给最小串行/独有临时文件处理。
3. driver cmd_restore 未恢复基线 APK/数据，只卸载后查为空；设置只对比不恢复。保护数据清单仅采测试宿主，不覆盖约定原宿主，ADB 非零退出未可靠传播。应采用新的未安装隔离 UID 并前置拒绝已有资产，保护原宿主/原APK与设置；已有资产需真备份还原，不能空目录替代恢复。
4. aggregate 未要求非空且全部严格 PASS，也未绑定合同 id 集合；report 直接接受外部 results 的 restore_ok。需基于实际执行和恢复证据，拒绝空项、未知状态、缺项、重复项、失配 run_id。
5. precheck 要求仪器已注册，但 runbook 放在安装前，阶段顺序矛盾；安装前静态与基线检查、安装后读回与仪器注册检查应分开。
6. EPUB 设备验收方法尚未交付；MD 验收类尚未进入合流树；字符串含 requireFreshSource 不能替代方法存在/实际执行证据。
7. EPUB payload 三路径是相对 v2 起点增量；最终集成必须同时保全 v1 的 ShelfImporter/测试以及 Parser/FileOps 依赖，不可仅拷三路径。

## 原始证据
[独立核查 JSON](<E:/creation-reading-assistant/three-parallel-deliveries-review-20261004/verification.json>)；各任务报告链接原始 XML/日志。驱动反例仅 Fake ADB，不触设备。

## 恢复与副作用
仅核查文档及 E 盘结果写入；无设备恢复需要。既有候选保持原件。

## 交付状态与下一步
本批严格验收 1/3（33%）：同步/MD 限定接受；EPUB 和驱动返修。返修可并行，最终合流/构建/设备串行。无最终联合验收通过，无全产品百分比。
