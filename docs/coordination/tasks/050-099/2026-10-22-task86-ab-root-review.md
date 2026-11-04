# 86-v1 A/B Root核验与87-v1交接

## 任务编号/版本
86-v1-A、86-v1-B；Root解析修正86-v1-A-parser-root-r1。核验日期2026-10-22。下一87-v1只补修后APK的MD普通使用旅程，不重跑已充分验证的TXT。

## 输入指纹
- 唯一构建工作区：`E:\creation-reading-assistant\task59-common-candidate-v1\candidate\android`；Gradle home `D:\develop\env\gradle`；构建锁 `E:\creation-reading-assistant\locks\android-gradle-exclusive.lock`。
- A修后debug APK SHA256：`33a08a05a75e482dc73cba00ce5f5eec15ddb070ad5cbe16b41299b5657d86df`，52,763,881B。路径为上述工作区`app\build\outputs\apk\debug\app-debug.apk`。
- 旧APK SHA256：`e59cf92286c6aad2b99e1c55f4dec17bc9b3cea52b9051db3d25560bbebf9b22`；A唯一保留副本`artifacts\app-debug-86v1A-before-e59cf922.apk`，不作为新版MD通过证据。
- A补丁`patch\86v1A-paged-exit.patch` SHA256：`46822b06b5f9a576caf781a150500ad078c8fe1e7cf70aa051b71459bfd68fe1`。
- 最终真实Flow：`E:\creation-reading-assistant\task86-exit-progress-v1\A\parser-root-r1\candidate\b2_selfuse.py`，由`fe8f6aaae10db307f93994029154e5aa467c72a0b86152acc34ee73266363aca`派生为`d04b8ccacf10cf999ef948023cd9ec62a99c8c9810e51de9f6c9d65279a2ea17`。
- 解析binding SHA256：`747869bcb81d81e26578dcd2d7271c710431191490536ca1d5068a289a74cc43`；原始7项日志SHA256：`32548f0a188e3f066a15c16d84383dbc3fab5dfeb8eb3685c1615650da171305`。Root已实核候选、binding、日志和两项动态依赖SHA。

## 所有权与改动
A负责产品最小修复、必要编译及受影响验收；B先独占设备复现旧版MD，恢复交付后A才合流和运行。时间证据支持先B后A，没有设备并发。Root独立审查与解析修复并行：前者只读，后者只写A/parser-root-r1。没有D产品回灌。

产品行为改动位于`PagedReaderControllerEffects.kt`：已有上报且源anchor仍在当前页区间时，抑制重排产生的较早页首写回；真正离开区间的翻页继续上报。`ReaderProgressActions.kt`、`ReaderPlatformEffects.kt`为诊断改动；新增`PagedPositionReportTest.kt`。

关键文件前后SHA：PagedReaderControllerEffects由`d343e34f9d311fc345d51700a2d02c3ac8b124c7f71e730bc579f9953d0a7028`变为`08d80bf790d24d843bfe99eafe937261609c73973894f6ccf502d6e3b16af2f6`。其余三项源码Root已与A ready逐项匹配。

解析修正直接被Flow.run_format的mid/reopen/cold-reopen门禁调用。底部分页TextView独立百分比按显示小数精度比较；顶部ReaderChrome的`MD · 标题 · 21%`按Kotlin toInt截断比较（DB21.9006对应21%，不能四舍五入成22）。正文/书架伪候选、多候选、真正DB不匹配继续拒绝。未放宽bookId、locator、marker或恢复门禁。

## 环境
仅原生Android和MuMu隔离实例1证据。历史serial/boot不是下一批现场身份；87须重新绑定。设备不得关闭或重启，结束保持运行；不重启共享ADB服务。两个自动化保持PAUSED。

## 执行与结果
- B旧版run `20261022-100749-b3f5e5`：MD由961/21.90063858%退至825/18.80127716%，实际BACK后书架截图已留；业务失败，恢复成功。B对Dispose的解释仅为推测。
- A日志捕获TXT页数11→13→11、页首1235→1034→1235而源anchor1235未变；MD页首961→825、源anchor961未变。修后抑制该临时页首回写。独立原生审查采纳这个有界机制，未发现有证据的新阻塞缺陷；具体触发重排的视口事件尚未定位。
- A真实JUnit XML：PagedPositionReportTest 6/6，零失败/错误；unit-test.log和build-debug.log均BUILD SUCCESSFUL。独立策略验证也是同6项，不重复计为12项。
- A修后run `20261022-102958-8f0660`：TXT B/C/D/E/F同bookId，offset均1235，28.4627799987793%，marker13相同；force-stop无残PID、冷启动成功。采纳该布局条件下TXT全旅程通过。
- 同run MD B/C/D offset均961，21.9006385803223%；D因UI progress missing or invalid停下，E/F未执行。仅采纳退出/重开DB位置稳定，MD整旅程未通过。
- Root解析修正7/7离线通过，实际Flow入口及原失败MD XML被覆盖，含正文百分比拒绝、正文伪组合拒绝、歧义拒绝、真实不匹配拒绝。未产生新设备或编译证据。
- A有一次KeyError的提前终止入口：`aborted-launch-console.log`和binding保留。代码在guard前生成binding并执行设备只读查询，因此不得宣称仅一次run入口或完全零设备动作；可确认一次实际安装/业务attempt。程序性偏差不抹除已充分的TXT证据。下批入口失败也计入一次限制。

## 原始证据
- `E:\creation-reading-assistant\task86-md-selfuse-v1\B\REPORT.md`、`ready.json`及run `20261022-100749-b3f5e5`。
- `E:\creation-reading-assistant\task86-exit-progress-v1\A\REPORT.md`、`ready.json`、`evidence\unit-test.log`、`evidence\build-debug.log`、`evidence\diagnostics-summary.json`。
- A `evidence\run\20261022-102958-8f0660\flow.json`、`reopen-MD.xml`及`evidence\logs\logcat-20261022-102958-8f0660.log`；aborted-launch原始记录保留。
- `E:\creation-reading-assistant\task86-exit-progress-v1\A\parser-root-r1\{REPORT.md,ready.json,binding.json,logs\test-ui-percent-flow.log}`。

## 恢复与副作用
Root分别读取B、A的严格initial/final readonly-protection-proof.json：consumer=true、diffs=[]，原71私有/820外部及实际采集分母一致，DB组匹配，reasons=[]。证据根为`E:\creation-reading-assistant\task83-selfuse-delivery-v1\B\v2\protection-root-r1\outputs\<上述run>\{initial,final}`。

两次本轮材料/包/设置night=no、保活恢复采纳，无新恢复欠账；历史证据不代表当前实时设备。87继续复用严格保护适配器SHA `16c93794844650f2116fff7c97c5adb29acb15bfbc623a000643fa2fa5f722bd`，每run输出必须在该adapter允许的outputs根，身份由新owner现场绑定。

空间：A交付60,111,033B（含唯一旧APK51,846,868B）；B交付3,463,847B，另严格采集2,639,295B。Root解析差量最终38,624B，无工程、缓存或APK对复制。唯一原始证据及冻结资产保留，本轮不作存量清理。

## 未覆盖项
MD E/F及整旅程；不同布局/新host首帧仍可能按既有策略规范到页首，未证实全场景。6项单元不是Compose跨章节全覆盖。全产品分母未知；无实体手机/MIUI通过结论，不把旧四旅程算新版联合通过。

## 交付状态与下一步
实现交付：采纳有界修复。离线验证：6项产品单元与7项实际Flow解析验证采纳。模拟器验收：新版TXT全旅程采纳，MD部分未完成。整链验收：尚未通过。

87-v1由一个owner复用当前33a08 APK和最终d04b Flow，准备最小fresh launcher→离线核对真实guard输入形状→一次MD B/C/D/E/F→同attempt必要恢复→REPORT/ready闭环；不再构建，不重跑TXT或未变充分门禁。入口失败计一次；不得改完再第二次主入口。输出/flag/run/serial重新绑定；MD门禁失败保全即停，恢复继续且保留首错和恢复错。预计新增小于8MiB，24MiB为停止预算（必要恢复不受证据预算阻断）。

## 停止条件
身份/所有权冲突、保护初态不符、night不为no、既有包/材料冲突、输入SHA变化、业务失败或必要恢复失败均明确阻塞。不可关重启实例、覆盖旧报告、复用旧run为新证据、改严格消费者、访问正式书库/私库/HOLD、Git提交发布或自动外部接力。下一设备由87 owner执行，Root本轮未新启动设备批次。
