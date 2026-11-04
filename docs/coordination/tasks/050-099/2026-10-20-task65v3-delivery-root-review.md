# 65-v3交付核验：构建采纳，旅程失败，恢复拒绝通过

## 任务编号/版本
65-v3，2026-10-20香港时间09:26后核验。正式报告/ready已交，不能称阶段完成。夜间时段08:00已结束，automation-2/3实读均PAUSED，本轮不自动恢复、不外部派单、不发设备命令。

## 输入指纹
两仪器交付与59工作区副本字节相同：MD修正版5b892e0fe931b67da8b37d5225b1402fe95a5fe67777a648378d07abf5d2a04a，取材aa00d746b69933243109573c70e7efb560929eb98f3f7e5de23e7a673ac17417。新test APK SHA3565f6da5ca94327474ab7c87af99c5c254c20c64d467f258f8883f2f199003a实物匹配。
原REPORT SHA0af52c7240fc1735d0479f1f026dbcfda974d1d8d1814cda7446482c9c497781，ready SHA52fe8a6a0e9fe0c192dc326fa3dc69a6088f5dcb5d3d2c8048d1f77346b53be7。完整核对见E任务v3/root-review-20261020/artifact-checks.json。
ready记freeze/freeze.json实际不存在，正确旧路径freeze/attempt1/freeze.json SHA31f0f859…090dc匹配。旧freeze不包含新增仪器/test APK，不能支持新设备输入完整冻结；本次未见新工具动态依赖绑定和新工件的正式冻结证明。

## 所有权与改动
Root只写新核验目录及必要D报告/current，不改原源码/FAIL或启动进程。65执行者交两个androidTest差量及新test APK，未报告生产差量。常驻Gradle进程不是活动任务或owner空闲证明。

## 环境
复用既有59唯一工作区、D:/develop/env/gradle与排他锁；Root应用verification-before-completion做必要实物/原始日志核查，未重新编译或设备。63既有范围实证保持历史采纳，不能补成本次恢复。

## 执行与结果
两份最后成功Gradle原日志均BUILD SUCCESSFUL/EXIT_CODE0，实物test APK匹配，限定采纳编译构建。覆盖早前同名失败日志的偏差如实保留，不伪造找回。
09-md-instrument与10-material-instrument原文均Tests run:1, Failures:1，分别在MD:392与取材:312找书架卡片失败，旅程均FAIL。源码缺启动后进入书架动作，支持导航入口缺失的具体假设，但无当轮UI现场/截图不足以排除其他原因，不称产品缺陷或已证明唯一根因。
生产device_journey.py判定错误：虽计算*_ok=(rc0且OK标记)，但只在rc!=0停止；实际本次两次rc0/JUnitFAIL，继续第二条仪器且main_error=null。不符合“失败停止后续”的合同，不能称仅一次attempt就等于执行策略合格。
Root对真实main完全替换run/info，日志重定向到本轮Root夹具，离线反例：JUnitFAIL/rc0仍instrument2次、最终exit0；transport rc1则main提前return，uninstall0/shutdown0，恢复被跳过。没有真实ADB/模拟器/删除动作。原始runner-counterexamples.json及两组stdout/summary保留。此证据只证明主机编排缺陷，不是设备结果。

## 原始证据
E:/creation-reading-assistant/task65-reading-material-flow-v1/v3/REPORT-65-v3.md、ready-65-v3.json、scripts/device_journey.py、scripts/locked_gradle.py、logs/09-md-instrument.log、10-material-instrument.log、11-journey-summary.json及两份Gradle成功日志。Root离线反例及artifact-checks在v3/root-review-20261020，不复制已有APK/工程。

## 未覆盖项
MD完整退出续读、取材搜索回源返回未通过；归类最终入库和导出同批记录仍未覆盖。ready中旧freeze不等新candidate证据，run/attempt统一身份、阶段前预算/恢复1200、marker/PID/receipt/保护门禁未在新脚本接线。优先复用已验证63执行/恢复基础，不继续用缩减版脚本替代安全合同。

## 恢复与副作用
本次恢复PASS拒绝。summary仅证两包uninstall rc0、本轮实例关闭字段；adb_devices_after采样在shutdown之前，不能证明关闭后fresh状态。未见本次入场原包状态、71私有/820外部基线与末态对照、设置基线/恢复、卸载前应用数据与阶段证据归档、tmp/SAF创建物核账。报告自行承认夜间模式缺恢复。
脚本直接卸载，未先取证。报告“创建物保留在实例内”不能等于已保全：卸载会清除包私有/应用专属目录，实际残存及证据可恢复性待核，不谎称一定丢失或一定存在。不能重装重跑生成文件补成本次原证据。优先只读查既有日志/归档和确证可恢复资产，再在现有恢复授权内补必要设置及合同核查，基线不足则明确不可补证，不猜旧值回写。
Root无新设备恢复义务；原65本次恢复欠账需单列。没有保护对照不声称数据损坏，也不能声称已恢复。
本次v3在Root新增前实算164825字节，报告约53KiB不准确；构建增量未实测，不把工作区存量当增量。旧资产不删/迁移，无Git/发布、桌面、正式库、私有库或云部署。

## 交付状态与下一步
已完成：两个仪器修正源码和真实构建交付。未完成：两旅程、完整入场/冻结/恢复证明；恢复欠账优先于新验收。
下一批67-v1专属只读恢复核定与必要补恢复、真实执行器失败闭合修复；65-v4只做互斥仪器的UI入口及实际断言修复，前段可并行，禁止重编共享APK或新验收争用恢复现场。67恢复裁定及执行门禁被Root采纳前，不发新验收attempt。二者不改对方文件；后续公共绑定/构建/设备唯一owner另依交接收口。当前只交提示词，不恢复自动化/外部发送。全产品分母未知，不称实体手机/MIUI通过。
