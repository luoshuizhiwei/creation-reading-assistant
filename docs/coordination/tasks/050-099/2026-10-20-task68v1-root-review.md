# 68-v1 Root核验：MD位移FAIL，取材未运行，恢复已核

## 任务编号/版本
68-v1；2026-10-20香港时间。单次run 20261020-130429-d38375。执行交付完成，模拟器验收FAIL，必要恢复证据采纳；不补造65-v3历史。

## 输入指纹
本轮ready的6项入口/worker/freeze/binding/host/test实际SHA全部匹配。65-v5两APK及67-v3执行契约未变，详见[67-v3核验](2026-10-20-task67v3-root-review.md)，不重复构建或19项离线验证。

## 所有权与改动
相关task65/67/68/59 Python/Java执行进程查询为空。Root仅只读相关证据/仪器源码，在task68/root-review/checks.json写小型核验输出并更新本报告/current；未改原交付、源码、APK、工具或冻结。并行下一批按互斥源码分工，最终构建/入口/绑定唯一集成owner。

## 环境
复用59工作区、D:/develop/env/gradle及既有依赖。Root本轮没有设备命令、启动、构建、Git或外部应用消息。自动化保持原暂停决定，本轮未修改自动化配置。

## 执行与结果
Root核对真实10-md-instrument.log、verdict和99-summary：MD rc0但Tests run:1 Failures:1，JUnit失败正确使入口exit1；取材没有被启动，未现场改码或第二试。
导入和正文打开阶段已有真实JSON：64,032字节MD正文、8章/320标记，打开时offsetOnOpen=-1。断言失败为MdReadingResumeAcceptTest.kt:542（测试体437）位移要求>1200实得0，mdr_20_displace未落盘。
报告原UTC结束05:07:29/161.8秒不代表完整attempt；summary finished_at=13:10:07+0800，原始start/end epoch差338秒。161.8是恢复前预算elapsed字段。以原始记录纠正口径，不改旧报告。

## 失败定位与未覆盖项
工作区MdReadingResumeAcceptTest.kt:528、536的等待表达式now!=null && (offsetBefore==null || now>offsetBefore+MIN_DELTA_CHARS)在初始null、后续0时返回true，但542以0作初始基准又严格拒绝delta0。因此它可能提前结束等待并跳过下一章兜底；这是已确认的仪器条件矛盾，不能据此断定实际滚动/分页或产品保存接缝均正确。
返修须统一严格位移谓词，真实UI正文位置与持久offset互证，失败先写出证据；不能缩阈值、直写DB/调用save helper或用source定位提示当成产品根因。MD退出续读未覆盖，取材全旅程未运行。
取材仪器另有独立已知缺口：标签仅按输入框数量条件尝试、无最终标签DB断言；mat_70_exportEntry仅写静态描述并明确文件NOT_COVERED。下一批补实际归类与同批导出证据，不以旧63 SAF拼成此旅程通过。

## 恢复与副作用
Root比较本run初末清单，71私有/820外部路径-size-SHA完全一致，settings一致；初末protection ok/diffs空。5份恢复步骤均ok，归档两tar实际size/SHA与ready/卸载回执匹配，host包含22个普通文件，test0；原回执保留归档文件核验与卸载/absence证明。tmp原始结果final_file_count616、无需清理、exact_owned_roots空。
记录的fresh末态实例0/1关闭；本run连接定向disconnect后adb列表空。Root只核原始记录，没有重新启动设备验证历史。恢复采纳只限合同保护面，不是整个虚拟磁盘逐字节回滚。
旧FAIL及原报告保留；本轮E新增仅root-review/checks.json约数KiB，无工程/缓存/APK复制或资产删除迁移。

## 交付状态与下一步
68-v1交付和必要恢复收口；产品两旅程本批通过0/2（MD失败，取材未运行），不称全产品进度。63有限公共链/D/F/未变构建继续复用，全产品分母未知，不称实体手机或MIUI通过。
下一批69-v1-A独占MdReadingResumeAcceptTest.kt及其最小定向证据；69-v1-B独占MaterialGatheringJourneyTest.kt，补轻量归类最终持久化与同批实际导出链。两实现可并行，A不碰材料源码，B不碰MD源码；若涉及共享产品文件先报Root分配，不覆盖他人。B在A交付后作为唯一集成/构建/新执行绑定owner，现有59工作区一次必要合流构建，完整传递依赖与恢复合同不降级。旧67输入不改写，候选新输入须独立交付。
本批下一步先完成源码与离线/构建交付，不默认沿用68的单attempt授权启动新设备；新设备批次由Root依据新工件与合同给出。automation-2/3不恢复，无自动Claude投递。

## 原始证据

E:/creation-reading-assistant/task68-md-material-device-v1/REPORT-68-v1.md、ready-68-v1.json、logs/10-attempt-stdout.log及start/end epoch；attempts/20261020-130429-d38375下10-md-instrument.log、99-summary.json、初末保护清单、recovery归档/卸载/临时资产/设置/宿主回执；evidence最终adb/实例记录。Root实算输出root-review/checks.json。均原路径引用，未复制历史。
