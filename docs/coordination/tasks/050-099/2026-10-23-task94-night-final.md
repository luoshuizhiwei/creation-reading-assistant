# 94-v4/v5/v6夜间收尾与下一批起点

## 任务编号/版本、输入指纹、环境

2026-10-23香港时间03:37，夜间自动接续10-23收尾。实际交付在`E:/creation-reading-assistant/task94-organizing-export-integration-v1/v4`、`v5`、`v6`，各有REPORT/ready及必要原始证据；[v4](2026-10-23-task94-v4-night-review.md)、[v5](2026-10-23-task94-v5-night-review.md)。唯一task59工作区、既有Gradle/JDK/SDK/锁不变。

产品候选fac29f55…f1e4705未变，既有test APK d80d6213…eebf7c6仍保留。当前仪器源码53ff2d8ae947d703ef849eee2c65b84fd099b6616870f2e4c329f2a5c436b509（67,828B），patch8b2b0403…bc816bdb，仅完成Kotlin测试编译，尚未打包进新test APK。

## 所有权与改动

仪器worker独占一个androidTest文件，Root独占宿主/构建/设备/交接；只读explorer独立调查旧重复菜单边界，不改运行输入。真正独立文件任务并行，恢复合同、冻结输入、构建、单设备按依赖串行。未改Android产品源码。

v4补选区/导航退出、无来源对照SQL及安全导出边界；宿主cleanup只允许初始缺席、真实目录/URI绑定、E与设备字节匹配的精确单资产。v5补已知遗留test包原APK/数据/用户状态恢复合同，未直接放松门禁。v6针对新的真实保存失败补最小边界证据与可核UI前置，未猜测修产品。

## 执行与结果、原始证据

- v4：实际cleanup入口8项模拟通过，必要test构建通过。单attempt入场拒绝既有test包，安装0/仪器0/删除0，严格恢复通过。
- v5：保全已确证原test APK10,508,820B与原数据tar5,632B及精确清单/用户状态，恢复guard/restore入口5项模拟通过。单主仪器29.749s、1 test/1 failure：真实导入/打开/首条保存通过，新建对照后精确title行20秒未落库；后续核心段未执行。Root独立核原test APK/数据完整清单/原用户状态还原一致；实际原tar恢复缓存，原外部目录缺席，产品包恢复缺席。原71/820、DB组、diffs、night=no和保活均实核通过，恢复errors空。
- v6：源冻结后仅`:app:compileDebugAndroidTestKotlin`，exit0、BUILD SUCCESSFUL。未assemble，不覆盖唯一d80 test APK，不新设备运行；旧69项及红绿证据复用。

原始host/仪器日志、initial/final保护JSON、06-original-test-restored.json、输入绑定、compile-test.log和exit及源码/patch见各E报告。Mock/静态/编译不代表设备验收。

## 未覆盖项、方向自审

新建保存失败根因尚未确证。v6补保存前真实title、按钮bounds/display/enabled、IME；仅IME实际可见时用系统API关闭，滚动到可见按钮单次点击；失败保全原Throwable/树/截图/本轮限定DB行，诊断错误suppressed后重抛原根因。

分类编辑/回显/筛选/精确回源/真实导出/清空/取消均未通过本批设备验证。旧重复菜单已确证Snackbar与按钮几何重叠，缓存ui-test字节码证明performClick经坐标触摸，实际事件消费未知，不把候选机制当产品根因。

方向仍为轻量整理与标签导出的用户闭环；连续脚本前置阻断后，不继续凭同假设改脚本开设备。夜间两次attempt已用尽，补齐可执行恢复和最小证据后停止空转，不扩功能，不取消项目。保留旧91/92阶段结论，候选fac29未晋升为新增量验收通过的稳定版。

## 恢复与副作用

v6设备0，无新恢复义务；v5最终恢复证据是历史已核状态，未来仍fresh核身份/初始保护。实例1保持运行，未关闭/重启，未删除导出或其他既有资产。原test包恢复为已保全v3原字节/数据/用户状态，不能在下一批当缺席或顺手卸载。

automation_update已确认删除本夜heartbeat `10-23`，原因本夜attempt用尽、离线准备完成且无无需新设备的可证独立动作，不再轮询空转。automation-2/3保持PAUSED。无Git暂存/提交/push/发布、D产品回灌、桌面/正式库/私库/云或存量清理。

## 交付状态与下一步

本夜交付为部分完成：恢复接缝已实际验证、v6离线准备和测试编译完成，业务验收仍FAIL/未覆盖。下一明确设备批次：先读v5恢复合同、v6差量；按新source与APK绑定必要打包含53ff的test APK，不能拿旧d80当v6；复用产品fac29与69项，fresh核设备/预算/原test基线，单次执行并恢复。保留明确失败与真实保存边界输出，不提前承诺通过。

空间：v4约1.59MB，v5约14.74MB（其中原test代码/数据保全10.52MB、其余约4.02MiB），v6统计时约93,525B，均为报告时点。无整工程/缓存/APK对复制；v6既有编译缓存增量未独立量测，不谎报零。全产品分母未知，MuMu非实体手机/MIUI。
