# 96-v1 Root核验与96-v2接续

任务编号/版本：96-v1核验，96-v2接续。时间：2026-10-23香港时间20:40。

## 输入指纹、所有权与改动、环境

Root只读核验E:/creation-reading-assistant/task96-library-settings-closure-v1，D协调由Root维护；Claude已交付且UI不再运行，下一批仍由Claude独占仪器/宿主/构建/设备。唯一task59 Android工作区、既有Gradle/JDK/SDK不变。独立S1源码与直接证据梳理由只读explorer并行，不触碰当前仪器/设备。

7/7 outputs文件大小/SHA匹配。22项冻结输入中20项当前仍匹配；运行test已被修后候选替换，22ab运行原APK保全件10555720B/SHA核同；运行源码65d1已改成b499候选，未找到独立运行源码原字节，不称22项现物全匹配。当前产品1102346485a9e47b7b12885310f678e56b5c86d36082834fbec8732906ffc30c未变，候选test8775434dcea12149002c97f7af7b876cb443a19e4123f060cdab4e27bc0758bc已构建未设备；候选源码b4993622f913df927b82b2a5f9762517a5e7c10611481d443c253929e61f2e0c。

## 执行与结果、原始证据

6份integration/evidence/junit XML共161项、failures/errors均0（D2 77，P1 84）；定向JVM及两轮仪器编译/组装原日志BUILD SUCCESSFUL，不重跑。integration/REPORT.md标题“构建1次test”与正文两次组装口径不一，采用原日志两轮。P1仅数据作用域JVM通过，未作设备视觉/手势或全链宣称。

T96V1-20261023-202147原始logs/20-instrument-stdout.log：40.376秒、1 test/1 failure，selectDownloadFile失败；证据支持真实导入、UI移除正文、资产快照保持。重新关联同内容/拒绝异内容/取消/恢复可读未执行。候选搜索属于未验证修正，不推定已解决。

## 恢复与副作用

evidence/device/summary.json保留primaryError、ok=false、recoveryErrors=[]；原test核入场与恢复APK SHA0d18一致、完整inventory/userState一致声明与原记录核同；最终保护原结果ok/rc0、71私有/820外部、diffs=[]、db_groups_match=true、reasons=[]。设置night=no，实例保持运行。本次不新增设备操作。

确证新安全缺口：lane-a/D2RelinkUserJourneyAcceptTest.kt tearDown直接对downloadFiles执行rm -f，在宿主collect之前；没有删除前当前size/SHA复核，且runCatching结果仅写日志未验证失败。宿主后见already-absent不能补证明此前删除合规。最终恢复保护一致不等于该清理过程满足合同。96-v2先移除此抢先删除，以现有宿主collect→E原字节/初始缺席/精确路径与当前设备size/SHA→精确清理→缺席复核单owner闭环，故障应保留证据依赖资产并BLOCKED，独立安全恢复继续。禁止直接沿候选主run。

## 未覆盖项、交付状态与下一步

状态PARTIAL：P1数据合同和D2身份门JVM采纳；D2真实重新关联待验；EPUB UI尚未覆盖。无需重开94/95或全量测试。

方向自审：用户目标是正文生命周期可靠，不是反复调选择器。当前只需修确证清理缺口、实核搜索动作及失败现场后集中一次D2剩余流程；同阻塞无新证据停止新设备。独立S1入口梳理并行准备下一块，当前仪器/宿主/设备须同owner串行。只改仪器/宿主，无已证产品缺陷不改产品。候选若因清理安全修正变化则必要一次test构建；161项/产品/既有保护充分证据复用。冻结运行件覆盖前保全必要单份，并记录候选/运行两种指纹。

实际E交付24247754B（含两份必要保全APK21093917B），REPORT旧统计24235556B为生成时口径，ready较新；build增量未独立计量，列未测不报预算达标。无存量资产清理、Git或发布。下一96-v2按新绑定一次attempt；不因本批部分完成停止整个项目。

实际交接复核：原Claude项目“原书缺失时摘录仍可读”已收到完整96-v2新消息，随后已运行2条命令并读取本报告；模型workbuddy/cn:deepseek-v4.1-flash 1M未变。automation-4已更新当前96-v2上下文并保持ACTIVE。

独立S1只读结果：唯一工作区SearchViewModel.kt:152–157把searchContent异常折为空命中，SearchNotice只有未建索引/覆盖不完整；SearchScreen.kt:570的提示条无正文查询错误，SearchViewModelTest.kt:241现测试明文锁定静默降级。既有README的R2“错误反馈”要求与之有缺口。下一实施限SearchViewModel/SearchScreen/对应测试，保留元数据结果、明确区分查询失败与真实零命中；不因此重写索引/Worker/DAO或重跑全部续建测试。此为源码与契约判断，未做新设备证明；96当前设备所有权不受影响。
