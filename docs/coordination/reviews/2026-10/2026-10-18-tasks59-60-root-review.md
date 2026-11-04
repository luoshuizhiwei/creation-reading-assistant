# 59/60交付核验与Root测试修正

## 任务编号/版本

59-v1、60-v1；Root修正版root-r1，2026-10-18香港时间。[完整核验报告](<E:/creation-reading-assistant/tasks59-60-root-review-20261018/REPORT.md>)、[ready](<E:/creation-reading-assistant/tasks59-60-root-review-20261018/ready.json>)。

## 输入指纹

59候选1033输入，相对当前task48恰为4个已采纳Root-r2文件；清单SHA `69bf5ddffe19eadcdc7d7ef5193b95b92e5705c2bb1f019a044845dc73ef67ed`。60原测试SHA `174d877730a57d1018b735e9978b7ae9838a0a1bc266ca1cdebfaea91d7492ea` → Root-r1 `74c90e04822593db13b917991144ac1cc3f4630e53d47b7106c996b3be099f6d`。全部原交付未改，完整输入/依赖SHA与源文件在E报告索引。

## 所有权与改动

Root唯一核验/修正/交接，60独立只读审查并行；未见活动相关执行者，无owner抢占。未写入59候选；只在Root核验目录交单测试差量，未改生产/schema/公共runner。

### Standards

60原件P1可空数据库路径编译失败，P2关闭异常转字符串丢真实Throwable。Root补边界非空/精确路径核验，保留关闭异常并交JUnit聚合。独立复审原2项已消除，无新增阻断。

59复制1033文件/约19MiB完整源码候选，总目录报告约689MiB，违背本批最小差量/不复制整套工程要求；记录偏差，保留既有资产，不删除或迁移，后续只复用这一份工作区。

### Spec

60原件P2：未证归属的实际路径在隔离断言前获得删除许可。Root独占创建叶目录、限制精确库名/路径，并取消所有自动文件删除；保留数据库及证据目录给未来设备owner纳入ledger/取证/恢复。四个测试主体未改，保留不是恢复完成。独立复审原1项已消除，无新增阻断。

59保护脚本检查较早的`root-runtime-device-20261015/freeze-v13`，不能支持当前56-v13保护结论。Root已按当前ready补核正确`task56-joint-accept-v1/freeze/attempt3`、1033源码、原APK对及92工具，全部匹配。其两个task55差异已在当前task56v3清单内，无需重开是否纳入冻结的决定。

## 环境

Windows/JDK17/Kotlin2.0.21；Root复用163项AndroidTest依赖并使用59宿主实际classes.jar做定向编译。收尾回归是MockK DB/Context＋本轮E本地文件系统，不运行Room/SQLite或设备。60 ready时间10:10晚于本轮08时段核验，不采作真实时间依据。

## 执行与结果

- 59的4次Gradle回执/原始日志均成功；365份XML与构建树逐SHA核同，**3671项、0失败/错误/跳过**（含笔记17、搜索20）。Root未重复充分且输入未变的全量门禁；交付XML是一套全量结果，不当作另存首轮定向XML。
- Lint实际**0 error、6 warning**，未放宽规则。
- 新宿主APK SHA `49610255de1e2930102e95da9193f19a9e5164f60ffca3151359a87632186978`，51,830,508字节，与构建输出相同；Root实际aapt2/apksigner exit0，包`.qahostj1persistv2`，signer `05249d786e3d3d117bb5bd7b1f83512a0c9b8f1ee4e502112c4b2d9ccbd7b9a1`。
- 60原件真实Kotlin编译exit1、3条可空路径错误；Root-r1同163依赖编译exit0、stderr空。
- 真实tearDown入口离线回归**3/3**；仅补编译所需requireNotNull、保留原清理代码的对照版本**3项3失败**。验证未知路径保留、关闭失败保留原异常与资产、JUnit同时保留body/cleanup根因。不是4个Room仪器用例结果。
- Root-r1新文件patch check/apply exit0，LF patch及单次关闭换行转换后输出字节核同；初次换行/运行时classpath探测FAIL保留。

## 原始证据

E报告索引中的`verification.json`、`test-xml-sha.json`、APK读验日志、`compile60-original|root-r1`、`teardown60-cleanup-red|runtime-unit-cp`的原始stdout/stderr/XML/args/依赖SHA、`root-r1/changed-source`及patch/改前后SHA。原59/60报告与所有FAIL未覆盖。

## 未覆盖项

Root-r1未合入59候选；没有包含60的新test APK/最终共同冻结，4个Room用例未执行。既有runner只精确隔离`.qahost`，当前宿主是`.qahostj1persistv2`，源码会透传生产App。ContextWrapper不证明整个宿主启动隔离；后续须明确Hilt/既有测试所需启动合同并核验，不能直接扩大runner分支或立即启动设备。无新模拟器/UI/进程冷启动/整链或实体手机/MIUI证明，全产品分母未知。

## 恢复与副作用

Root本轮Gradle0、APK0、设备/adb0；有真实定向Kotlin编译与离线JUnit。红反例只删除本轮Root新建的E控制夹具，未删除设备或用户既有资产，剩余夹具保留。旧56-v13冻结/1033源码/APK/92绑定均匹配；原交付不改，无D产品回灌/Git/发布/外部派单/自动化恢复/桌面/正式库/私有库/云部署。两个自动化现场PAUSED。

## 交付状态与下一步

59宿主构建限定采纳；60原件不直接采纳，使用Root-r1差量。后续唯一集成owner复用59既有候选，先明确启动合同，再合入Root-r1、必要AndroidTest Gradle编译/打包、核manifest/target/runner/signer并冻结共同源码和APK对。仅androidTest变化时复用本次全量JVM/Lint/宿主证据，不机械全量重跑、不复制工程。

共同冻结与新test APK完成后才形成具体单次设备方案；绑定同版全部指纹、fresh保护基线、完整阶段+至少1200秒恢复，以及60保留目录创建物ledger/恢复合同；不能配旧test APK或借旧run补证。56旧冻结方案保持历史可参考状态，新候选设备批次仍等用户决定，两个自动化保持PAUSED。
