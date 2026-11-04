# 57/58交付核验与Root修正结果

## 任务编号/版本

57-v1、58-v1；Root最终联合差量root-r2，2026-10-18香港时间。用户报告“都完成了”并要求继续，Root实际核验、最小修正及定向JVM验证已执行。

## 输入指纹

原交付为E盘`task57-highlight-source-v1`和`task58-search-cancel-state-v1`。4个原基线SHA及4个交付后SHA实算匹配。原REPORT/ready和源码保留，不把“代码已交付、JVM待Root”当成已通过。

完整前后SHA、实际命令和限制见[Root报告](<E:/creation-reading-assistant/tasks57-58-root-review-20261018/REPORT.md>)、[ready](<E:/creation-reading-assistant/tasks57-58-root-review-20261018/ready.json>)。最终4文件在`root-r2/changed-source`；原task48冻结源码和56-v13 APK没有被覆盖。

## 所有权与改动

Root唯一拥有核验目录、必要修正、定向编译/JUnit和交接；两路独立只读审查并行。初检没有相关活动执行者，未抢占owner。

### Standards

58新增顶层`io.mockk.coAnswers` import是1项P1，实际MockK1.13.12不提供该顶层API。Root真实classpath下坏import编译exit1，对照去import后exit0；原4交付文件编译也仅此错误。Root删除坏import。

### Spec

57有1项P2：过滤`progress_percent > 0f`丢合法0%，Root改成非null保留，补真实转换/upsert/消费模型0f回归，保留null不伪造合同。

58有1项P2测试缺口：C已经完成才释放旧B，未测C仍loading窗口。Root补双gate及finally释放；首轮因新增测试跨真实线程池与虚拟调度竞争而36/37，最终r2只把新增3例改为纯测试调度推进，既有测试未改。最终两轴无剩余可执行发现；58生产源码与原交付字节一致。

## 环境

Windows/JDK17/Kotlin2.0.21。复用既存150项编译classpath以及对应runtime/既有应用classes.jar，268项输入指纹已落盘；只编译本次4文件，不复制工程/缓存，不调用Gradle/KSP，不覆盖已有产物。4个被测类与测试类CLASS_ORIGIN确认来自本轮输出。

## 执行与结果

| 生产输入＋同组回归 | 编译 | 实际JUnit |
|---|---|---|
| 原基线 | exit0 | 37项6失败：笔记4、搜索2 |
| 外部生产交付＋Root修正后的测试 | exit0 | 37项仅“合法0%丢失”1失败 |
| 最终Root r2 | exit0 | **37/37：笔记17/17、搜索20/20，0失败0跳过** |

搜索第三项迟到保护用例基线已绿，原58报告“3个新例都应红、旧B必覆盖C”不采纳。原始语法检查exit1不能证明编译通过。Root r1所有FAIL及XML仍保留；最终结果只对应r2实际SHA。

最小联合patch在4文件副本apply-check/apply通过，文本归一换行核同，原字节SHA另列，不能称逐字节相同。

## 原始证据

E盘Root目录内：`input-check.json`、`compile-original-delivery-actual/`、`jvm-root-r1-javacfix/`、`jvm-root-r2-deterministic/red|green/junit.xml`、`jvm-root-r2-zero-counterexample/external/junit.xml`及各compile/run args与stdout/stderr、`validation-summary.json`、`validation-inputs-sha.json`、`review57/REPORT.md`、`review58/REPORT.md`、`root-r2/source-sha.json`和`root-r2/patch/`。

## 未覆盖项

**Root r2差量与限定JVM采纳；尚未纳入完整共同产品源码树、未跑完整Gradle/KSP/Hilt/资源/Lint门禁、未构建新APK、未跑模拟器/Room冷启动/UI或整链。** 原冻结APK不含57/58修复，不能拿它验新代码。全产品分母未知，不报全产品百分比或实体手机/MIUI通过。

## 恢复与副作用

设备attempt=0、APK构建=0、Gradle调用=0；有真实定向Kotlin编译和JUnit，不笼统称“编译0”。原57/58交付、task48冻结、56-v13工具/APK保留，无新设备恢复义务。只写E必要差量与D交接，无仓库暂存/提交/push/PR/发布、D产品回灌、桌面、正式书库、私有库、清理迁移或云部署。两个自动化保持PAUSED。

## 交付状态与下一步

57/58外部交付2/2已核，经Root最小修正后形成4文件联合差量并通过37/37定向JVM。下一步唯一集成/构建owner先明确单一E构建工作区与56-v13冻结资产保护，再纳入共同版本、运行受影响Gradle门禁及必要新APK构建；不复制整套工程或覆盖旧冻结。

56-v14旧冻结单次设备方案仍待用户决定，自动化不恢复、不自动外部派单。当前停点是“Root r2差量限定采纳；完整产品候选、新APK及设备待后续安排”。
