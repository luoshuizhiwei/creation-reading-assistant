# 80-v5-G Root核验（2026-10-21）

任务编号/版本：80-v5-G-Root。实现部分交付，v5.1编译产物存在但尚缺位置门禁；业务整链未通过。全产品分母未知。

## 输入指纹、所有权与改动、环境
Root维护本报告/current；独立explorer只读保存器源码和G/v5/diag两个XML，未改产品、构建或设备。进程查询未发现v5执行脚本；存在Gradle daemon PID4340，未停止，不能据此判断有活动构建，下一次构建须遵守锁。
实际工作区MaterialGatheringJourneyTest.kt SHA为6d4ae90590623dfbad0d5bb99099dbbd35f07ecb2797896eef9aaf0c91ff0da5；当前test APK SHA c1b44484c49f8ce96848f6b840fbd4e31dfc6ebde968cb2509a0abbac898d062，10344273字节；host dc736485d1fee38a44e3cc250cebaea7b08e69b42f51d1cd9a5b0124beba1d95，53804578字节，与交付匹配。G/v5/logs/03-rebuild-test-apk-v51.log末尾BUILD SUCCESSFUL in 17s。不重跑充分的编译/接线证据。

## 执行与结果、原始证据、恢复与副作用
run 20261021-165926-878750的原始probe verdict为ok=true，material verdict为rc=0但ok=false；99-summary.exit_code=1，main_error为material instrumentation failed，recovery_errors=[]。四业务旅程0/4通过，material FAIL，其余三项NOT_RUN。该attempt使用首版，不是当前v5.1 APK。
原始20-recovery五个步骤均ok=true；最终保护71私有/820外部diffs=[]。诊断后的final-state.txt记录17:17:11设备在线、原boot_id、night=no、候选包缺席、Launcher前台。am start打开并关闭选择器是设备UI操作，不能统称纯只读；恢复阶段严格保护结果与后续诊断末态证据应分开陈述。无新导出/导出删除不等于没有tmp恢复清理命令。

## 冻结记录差异与修正
G报告7.5称覆盖前原字节未保留、只有SHA，此说与实际不符。Root逐项读取run/00-input-bytes.json并实际核算其指向的四份input-bytes快照：存在4/4、SHA匹配4/4。当前规范freeze/binding/ready/provenance四项全部已改变，因此运行后回写规范输入仍属违规；但原attempt字节可以追溯，不得再称丢失，也不需要伪造重建。保留原报告及INPUT-VERSIONS，将本核验作为勘误。

## 新增只读核查、未覆盖项
独立源码核查发现v5.1仍有两个实际缺口：describePickerNodes从整个documentsUiRoot收集标签；waitForActionablePickerNode及clickPickerNode没有限定roots_list后代，点击时仅按label/class在全树重定位。locationConfirmed只取drawerClosed，providerLocation为固定说明字符串，未读取实际位置身份。静态12/12只证源码文本不变量，不能证明这些行为正确。
两份diag XML证明无roots_list和有roots_list时的节点结构、面包屑不可点击和抽屉行可点击；没有旧实现实际调用时间轴，不能单独确证动画未完成就是失败因果。报告的时序结论应降为推断。v5.1未设备验证。

## 交付状态与下一步、停止条件
下一批80-v6-H仅补两个真实缺口：候选与点击限定新鲜唯一roots_list子树；导航后通过实际可得位置/provider身份确认Downloads，禁止把硬编码描述或抽屉关闭当位置证明。保留唯一名读回、冲突、真实导出和正文/持久链断言。围绕实际调用helper做必要行为验证，编译后独立封存新输入，再一次MuMu联合验收和恢复；同owner闭环，不重做旧接线或泛审查。已有并行只读核查已完成，没有第二个独立实现缺口，不为凑数量制造任务。
用户设备与本run已证新建且保全size/SHA匹配导出精确收尾授权继续适用；不关闭重启实例，结束保持运行；同批仅一个main/run/attempt，失败停后续并恢复，未确认身份/owner、漂移、预算不足或恢复失败即阻塞。原始快照、旧FAIL和报告不覆盖。两个自动化保持PAUSED；不Git/外部消息/桌面/云/存量清理。
Root本轮设备0、构建0，仅新增本报告及更新current，不复制工程/APK/缓存。G自报新增约5.2MiB，Root未为此重复全树盘点。当前唯一构建工作区task59-common-candidate-v1/candidate/android与Gradle home D:/develop/env/gradle不变。
