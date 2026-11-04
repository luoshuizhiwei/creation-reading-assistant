# 2026-10-07 三项交付 Codex 核查

## 任务编号/版本
CRA-THREE-DELIVERIES-CODEX-REVIEW-v1。用户报告三项完成；Codex按实际源码、原始测试、设备证据核查，不采纳口头完成为验收。

## 输入指纹
恢复仓库HEAD e39a78da9c62f02614207cffb36969a32492eaea，暂存区空。只读输入为E:/creation-reading-assistant下的txt-persist-merge-mumu-accept-v1、d2-relink-identity-fix-v1、j1-crossbook-temporary-entry-v1。原A981输入未漂移。独立证据根为本报告E目录；原报告和失败run不修改。

## 所有权与改动
Root核TXT冻结与恢复后账，三名独立只读reviewer分别核TXT执行链、D2、J1；无产品修改、无构建、无安装卸载或设置写入。D只维护本报告与current.md。用户要求加快并行的安排保持：产品候选互斥、设备与构建单owner、复用充分证据。

## Standards
TXT984/984冻结源码一致；相对A仅qahost单配置与三新增测试源，其中普通测试的单章断言相对B有适配修改，身份如实分列。双APK和构建日志SHA匹配。当前callback/harvest为设备适配版，不能沿用原B离线通过结论覆盖新增执行链。20个失败/中断run保留，不以重试次数作为产品通过证据。D2报告缺失、唯一日志编译失败，无红断言/绿XML；产品修复只保存在freeze/ShelfImporter.repaired.kt，未应用候选。J1四XML40/40、0失败/错误/跳过及日志SHA成立，新增7例，既有33例。

## Spec
### TXT：验收未通过，保存冻结根因不能签署
Root离线cmd_report复核普通批6PASS、j1_55 FAIL、后6 NOT_RUN；clamp8项NOT_RUN。55 JSON uiDumpPresent=false。回调scroll_until_movement生成ui_new_scroll_b0等批次dump，却未交付Kotlin读取的ui_new_scroll.xml，因此仪器证据链断裂。marker变化仅显示同一渲染单元几何位置改变，未证明新可保存位置发生变化；DB相同不足以裁定产品保存冻结。driver仪器FAIL后继续下一项before_item，宿主在仪器结束后被杀，60退出动作失败产生summary original_failure；不能误认55当前分支执行了退出。
source_manifest门禁拒绝为schema不兼容：checker读取source_sha256字段，冻结清单是裸路径映射；exclusive_dump_paths拒绝为FUSE适配后实际staging/媒体路径与checker仅允许sdcard前缀不符，不能称简单布局镜像差。local/harvest binding SHA不同是CRLF/CRCRLF通道换行转换，JSON字段一致；分别保留原SHA，不互代。先补完整dump交付、失败即停止依赖动作和身份/路径/schema端到端门禁，再独立最小设备证据；pending/marker保护不放松，不猜修产品。是否产品缺陷保持待证。
### D2：未完成，P1身份合同反向
候选ShelfImporter等同原A，产品校验修复未应用。保存patch UNKNOWN放行违反无法可靠确认则拒绝合同；先hash再另开URI复制存在校验字节与实际换入字节脱节。需要校验实际staging，并在备份/替换旧正文之前完成。新增测试错误要求UNKNOWN成功，且ShelfImporterTest.kt:1816非协程调用suspend导致编译失败。缺同内容进度/笔记/标签等实际保留证据；不得重复已验回滚修复。
### J1：限定40 JVM成立，产品入口未完成
crossBookInspectionRequest只定义无生产调用，原样返回state，不证明真实失败转移。SearchScreen在无法精确locator时普通打开B的降级可达，影响导航normalReading而非已证A数据库进度损坏；InspirationRoute相似分支被发送入口同判据挡住，无实际可达反例，不能作为已证根因扩修。目标书缺失/正文不可读时ReaderRoute先压临时栈，helper仍返回Ready，失败恢复合同未覆盖。不独立集成无调用helper；实际入口/加载失败接缝应明确分配后实施，保留既有40证据。

## 环境
Windows/Python3.13，当前MuMu serial 127.0.0.1:16416经adb devices核实；Root仅执行受保护隔离qahost的只读哈希/设置/包状态查询，未读取正式书库内容，未改变设备状态。原任务报告应用/模型自报不当作Root界面实测。

## 执行与结果 / 原始证据
source-build-review.json：TXT984源码、双APK、日志实算结果。
m3t-driver-offline-review.json：6PASS/1FAIL/6NOT_RUN原判定复核。
device-readonly-after-review.json：本次只读after核查。
J1原始日志test2.log SHA d64d6d4e6605bfa1a032595c087c09736a0703d479d23fecec5df21046af0120，四原XML40绿，复用不重跑。
D2原始logs/red-baseline.log EXIT1/BUILD FAILED，无测试运行PASS。

## 恢复与副作用
Root当前受保护qahost71/71哈希清单（含DB/WAL/SHM）、原APK对2/2、七设置7/7均与m3t baseline一致；新沙箱对absent、forward/reverse空。Root没有恢复写入。host VM内存配置恢复8与约定前台状态本轮未独立核定，不外推完整恢复全部通过。源候选、原日志、失败run保持。

## 未覆盖项
普通后6、clamp8未验；TXT产品根因、X整机中断单点清零未证；D2无绿测试；J1真实入口/缺书失败/设备未验；hostVM和前台后账未完整核。无实体手机/MIUI/发布结论，全产品分母未核。

## 交付状态与下一步
三项原任务完整闭环0/3，限定交付分开保留：TXT构建冻结与6方法证据、J1合同40JVM。D2未完成。
保留并行方向、重排最小返修：TXT先验收链端到端离线接缝，核通过后才唯一owner最小MuMu；D2独立内容身份/staging校验与定向JVM；J1独立真实入口和加载失败会话保护。D2/J1互斥E候选并行编码、JVM串行，未闭合前不合流新联合APK。三项返修不能重派原v1，也不制造新功能或全量验证。当前仅记录下一范围，未自动向外部应用发送任务。D产品回灌/Git/发布授权不变，暂存/提交/push均无授权。

补充原始核定：m3t before_item46/46 SHA一致；m3s/m3t对应UI字节一致，含44个marker，并非报告文字所写全空。j1_55实际percent40.41386，非报告58%。不得推断clamp必然失败，停止后记NOT_RUN即可。
