# 67-v1 / 65-v4 Root接收：恢复现态核定，设备执行器仍拒绝

## 任务编号/版本
67-v1、65-v4，2026-10-20。恢复现态与不可补证分类限定采纳，65-v4仪器差量交付采纳但未编译；67两已证主机缺陷局部修复采纳，不放行设备。

## 输入指纹
67原runner SHA8f169ea1df70651a7b5df375bda1fab31fcc7a9387495f053f4ec1cf70dd046f实算匹配。65-v4 MD aabd083aaa232d04e066f9cbd3f6b5156883b60a80d2ec4b6169a839a9fa92dc、取材1e31f24e7e44001896185d32c24d42001ea551f2959680deb74bb3ae10207a66实算匹配；工作区仍v3两源码SHA，v4未合流。交付原报告/ready SHA及定向核对保存在task67/root-review-v1/adoption-checks.json。

## 所有权与改动
Root只新增67/root-review-v1的最小候选、原始检查和本D报告/current；67/65原件与工作区源码不改。未启动设备、Gradle、自动任务或外部消息。常驻Java进程不作活动owner判定。

## 环境
继续单一59工作区及既有Gradle home/锁。验证按verification-before-completion限新缺陷和证据关系，不重核63全资产/3674测试、不复跑67恢复设备或原9项模拟。

## 执行与结果
恢复：Root独立将67采集protected-baseline-initial.json与历史protected-original-baseline-v2.json按路径/size/SHA对照，71私有与820外部相同。adb-disconnect-proof原回执rc0，后续列表空；设置当前night=no等有原文。采纳67所记录时点的现态，不是本轮Root实时设备状态，不补写65-v3运行前后保护。两包原私有/专属外部目录当前不存在，未见其阶段文件及卸载前归档；原始阶段与数据库证据不可补，本记录长期保留，不以新run重新生成替代。
65-v3入场安装状态与设置原值缺失无法复原；该历史缺口不能改为恢复PASS，也不因追求不可能的证明反复开设备取证。当前没有证据指向历史71/820保护面丢失；新增验收需先恢复严格入口/归档门禁。
65-v4：显式页面证明与导航tab选择、书卡和正文身份断言修正有对应源码，局部差量限定采纳；报告声明未编译，ready printToString=false与正文“javap通过”有矛盾，交实际编译裁决，不新增纯文本审计。
67：原9项Mock及两反例红绿支持“JUnitFAIL/rc0停止”“transport失败进入收尾”的局部行为，但不覆盖真实Android输出与完整恢复合同。
Root实证新缺陷：既有63 Room仪器原文OK(4 tests)且INSTRUMENTATION_CODE:-1被67判failure；该最终code不是测试失败码。Root最小候选root-review-v1/task67_device_runner_junit_root.py只修JUnit协议：不把最终-1当FAIL，拒绝明确JUnit状态失败、无法识别及0/不完整OK。原成功日志红→绿、原失败仍拒绝；真实main配完整历史PASS stdout的FakeDevice成功。候选SHA c52e8083b99ec8596e7179dd11a5dda1e57fe2ff2a51cc5906a867845472638a。
另一个真实main反例：入场无serial的pm list返回rc1/no devices，67忽略探测错误仍install2次、运行2仪器并exit0。原测试FakeDevice恰对无serial探测返回rc0空，掩盖该现场风险。记录在junit-real-output-red-green.json，全部调用被替换，无真实ADB/设备。

## 原始证据
E:/creation-reading-assistant/task67-recovery-runner-v1的REPORT/ready、runner、tests、recovery-verify原回执；Root新增adoption-checks.json、junit-real-output-red-green.json及fixtures/logs保留。
E:/creation-reading-assistant/task65-reading-material-flow-v1/v4的REPORT/ready、两个源码与patch、static-checks；未运行会覆盖原结果的验证脚本。

## 未覆盖项
67仍非设备合格执行器：安装前探测没有-s且在启动绑定前、rc非0未阻塞；未知/外部已启动实例未拒而继续绑定安装；preflight所谓动态依赖只检查adb/MuMu文件存在，没有实际工具绑定/完整冻结；预算只把各段上限相加写JSON，没有总截止与恢复资源控制；_finish仍直接卸载包，没有阶段/应用证据归档与size/SHA、71/820入场末态、设置恢复、tmp/SAF创建物后账；卸载异常可能有条件误放、安装超时产生的部分副作用未核。不能因“只卸载本轮安装”消除删除前取证义务。
RootJUnit候选仅局部修正，仍包含以上未修门禁，不可直接设备运行。后续停止另写缩减版生命周期，复用63已验证的保护/归档/截止/收尾真实入口，只替换本轮阶段体。
轻量归类最终入库及同批导出接缝仍有未覆盖，复用63导出能力不等新仪器已执行这些行为；最终交付必须如实区分，不能以文件计数报产品进度。

## 恢复与副作用
67恢复核定已区分当前一致/历史未证/原证据不可补，可作为后续基线准备依据，但未来入场仍fresh采集。Root新增离线fixtures全部自有并保留，不删除旧资产、未新增设备恢复义务。自动化2/3继续PAUSED，无Git/发布/桌面/正式库/私有库/云部署；不将65-v3历史不可补证改名消失。

## 交付状态与下一步
不再为历史不可补证反复开恢复调查。67-v2唯一负责把本轮阶段接回已有63严格执行/恢复基础，采用RootJUnit修正并补真实入场探测红例；65-v5唯一合流v4两仪器并必要编译/test APK构建。前段不同文件可并行，公共工件和最后工具绑定按依赖串行，实际输入固定后67统一绑定。未获完整门禁核验前不启动新验收；没有依据单纯给缩减runner重绑SHA就放行。全产品分母未知，当前V0/取材闭环仍未通过，不称实体手机/MIUI验收。
