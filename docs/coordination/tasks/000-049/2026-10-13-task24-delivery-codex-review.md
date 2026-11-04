# 任务24-v1交付核查与下一批边界

核对：2026-10-13 02:14 +08:00。Root依据实际交付、原始日志、逐文件指纹、独立冻结verify及两只读审核。Root未构建、未操作设备。

## 任务编号/版本

24-v1 / CRA-V0-JOINT-FREEZE-MUMU-ACCEPT-v1。已交报告/APK/设备工件，采纳限定成果；完整阅读/系统UI/恢复全账门禁未关闭，V0仍未验收通过。交付与验收分开。

## 输入指纹

- Root-v2原1005源仍匹配。24最终1008源逐项匹配，manifest SHA62111f4eb779250315c47529b6241fad67c6ab160f1b24d1397c42ed87ed92f5，tree540c11f0a8b5677e333f09c590bae6cb47481fc5a06f8333247f810c33869f05。
- 验收46工件逐项匹配，manifest SHAfc355271d74e16d5559d59be04f24ad786320d51be4228b2039b474e4b799b32，treec91e668a2cb67d52ecd6968a6d93136ad169af171e1fff7c1df8e4803dad2c62。
- host APK SHAac580c95902a9c03226bc17f25e0d713f01879b48088fe8775f517845d698c34；test APK SHAcc61d0a1c808a8f900a03435436bea8ad7b58243b19b4e52129a430b23e5f1d0。

## 所有权与改动

相对Root-v2实际2改/3增/0删，生产0改；外部报告的0改/3增统计不准确。两改为BootMirror测试等待system+amoledPureBlack、J1夹具初始percent对应offset，均最小合理。三增为ReaderRegressionAcceptTest、NotesExportShareAcceptTest、EpubDispatchAcceptTest。当前冻结版本保留，下一24仅测试/配置/设备补门，不改产品。已有16-v6真实VM派发/异步IO乱序证据充分；F余项为旧Compose闭包重新产生迟到动作，须真实生命周期证据，当前无可控离线窗口，不新派25或推测产品修复。新冻结由原唯一owner执行。

## 环境

HEAD e39a78da9c62f02614207cffb36969a32492eaea，暂存空；D产品只读。报告可见路由标识DeepSeek，执行者自报，不核实供应商后台。MuMu实例1启动前关闭、ADB空的原记录与最终关闭记录存在。当前Root进程只读未见MuMu/MAA/Java构建，未来仍须实时核占用/serial。

## 执行与结果

- 实算保存355 XML：3543 tests/0 failures/0 errors/0 skipped；gate_check2.out中test实际执行、lintDebug执行、BUILD SUCCESSFUL/exit0。此全量成立，不重跑。
- Root独立执行既有强工具verify：exit0/verdict PASS；源码/验收逐文件、prebuild/build-record/真实日志/APK SHA等绑定成立。包名qahostj1persistv2/.test、runner CraIsolatedTestRunner和历史预期debug signer由冻结实读支持；非正式发布APK。
- run_t24_device3确有12个runner原始OK(1 test)。这是执行12/12；summary将D算PASS错误，不能称合同12/12通过。D实际triggered=false、changedObserveCount=0，Root分类NOT_TRIGGERED。其余11项只能按各自实际证明层级采纳，不构成完整V0分母。
- 前进实际行4014→4576→4576及updated_at可接受暖重進限定结果，但新用例最终仅断言重进行非空；正向身份只用首页消失，marker=null。需收紧仪器门。
- 回读真实反向坐标变化、最终6438暖重进保持；没有前进阶段已保存行对照，不能证明回读落库从前进位置回退。
- 同fixture不同PID冷重进、短末项S/R/S2完整行与updated_at合同未执行；两个方法各自不同producer PID不代替同fixture冷进程。
- B/C/E各自有效范围复用；E正向marker与真实位移、普通完整行冻结成立。B不归因为防抖collector完成；C保留坐标到字符映射精度限制。
- Notes真实Compose/生产写字节在受控file://URI通过；chooser只捕获Intent。真实系统SAF/chooser界面NOT_RUN。首段主链可用过宽。
- Room生产deleteBook为软删除：非空来源书ID记录仍保留，payload逐字节不变并可导出。采纳软删快照，不扩为物理删除/FK置空。损坏payload launch0且文件不存在，不宣称Toast观察。
- EPUB真实Room/zip/repos手动order1→3→2，最终章节内offset800/chapter2支持派发合同；夹具一章却保存索引1/2，不作自然阅读UI或真实并发证明，不因范围限制重派实现。

## 原始证据

[核查目录](<E:/creation-reading-assistant/task24-delivery-codex-review-20261013>)：verification.json含源码/工件差量与哈希；evidence-check.json含355 XML、12 runner及5原件备份绑定；inventory-check.json含受保护71/0文件完整原始清单SHA。原件在任务24的logs、freeze、evidence/jvm/full-gate、runs/run_t24_device3、baseline及recovery-final-ledger。两独立审核均只读，未构建/ADB。

## 恢复与副作用

Root实核本轮基线与完整after原始清单：受保护qahost71/71、test0/0，size/SHA/ns-mtime一致，stdout与机器清单SHA一致、exit0未截断。DB/WAL/SHM三个本地备份与基线/最终SHA一致，size匹配；原APK两份备份三方SHA一致。restore中测试宿主卸载前私有数据库组取证有原件/暂存/本地size/SHA和清理证据，原保护包未卸载。设置8项、包状态、转发及关闭约定原记录支持限定恢复。

完整创建物账未交：created_remote_files.json缺失，final-ledger total=null；所谓inventory完整只遍历受保护包，未覆盖/data/local/tmp嵌套或系统SAF新文件。tmp顶层相同/包absent不能证明全部创建物不存在。inventory/compare-result.json也未找到，不引用为存在证据。因此不可接受绝对零副作用/完整恢复全门通过。下一24必须逐路径登记创建物与清理回读；上一批只能基于旧命令静态重建可知路径，明确回溯局限，不补造事前证据。

## 未覆盖项

D第二有依据真实控制、F旧UI迟到回调、阅读身份/非空marker/前进与回读对照/同fixture冷PID/短末项S-R-S2、真实SAF与chooser、创建物全账仍未关闭。X清零、实体硬件/MIUI、正式签名/R8实际运行/发布、桌面/HOLD、实际LAN等范围不变。

## 交付状态与下一步

本里程碑复核保留既有16/23实现、完整JVM/Lint/同版APK与限定设备证据；调整为只补缺口，不复跑合格全量：24原owner仅补阅读/系统UI/创建物门禁并限定设备。独立审核不建议另派F离线闭环：真实VM/IO乱序已有充分证据；未证Compose旧闭包需真实UI，人工旧VM调用不能替代。故仅一接续，无25，不扩大为全项目永久串行。结果须Codex核查，不称当前V0关闭。全产品分母未核。
