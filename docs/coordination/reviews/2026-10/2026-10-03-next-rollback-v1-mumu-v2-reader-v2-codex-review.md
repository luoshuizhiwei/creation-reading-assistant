# 回滚修复、门控补验与阅读预查v2：Root核查

## 任务编号/版本
CRA-ROLLBACK-MUMU-READER-REVIEW-v1；2026-10-03。用户报告三任务完成；核实际输入、XML、源码、设备证据，按风险确定下一批，不把交付当全验收。

## 输入指纹
HEAD `e39a78da9c62f02614207cffb36969a32492eaea`，暂存空。共同产品输入仍为integration-ready-v1与25路径manifest `a0240a395c20251f480378ed07ba901c0c0061c79cd98ea4b43985b229ed491d`。独立修复副本不是原25候选；未写D产品。

## 所有权与改动
Root仅本报告、current/TODO与E核查证据；独立GPT-6 Luna只读审回滚路径。原三报告/工件保留。下一回滚返修仅独立E两产品函数及测试；阅读旅程设备与构建单owner，消费原冻结输入，不混未验P1副本。

## 环境
Windows/Python/XML/SHA。首次adb devices为空，确认16416宿主监听为MuMuVMMHeadless PID33356后仅adb connect；未启动播放器、安装包或打开App。随后只读核数据/设置/包/映射，并恢复已记录的原夜间模式。MuMu不是实体手机。

## 执行与结果
### Standards
- 回滚XML原件：红Shelf30/3+Epub3/1，绿30/0+3/0，error/skip全0。四XML SHA均与报告一致；日志实际test任务执行，红BUILD FAILED/绿SUCCESSFUL。原33条/4红→33绿成立，不重跑充分证据。
- MuMu v2双APK实算分别`7de243c08287fa168634e88611a12b0b90ad0ec34e594589f1b108b4e6725411`与`b29f858d4c983996f7df20a5c17508094fe6f35ae87f5dd492cadd215af6b1f5`，installed-paths回读同值。实际device-runs有10份，不是报告7份；7个OK、3个早期pair失败，失败均保留，不作为有效门控PASS。
- 阅读v2 25条candidate SHA全部Root实算一致，v1三错误来源已纠正。无NOTES打开者、旧磁贴改灵感直达可从实际E看到；代码注释不是永久产品授权，先保留现有入口做有限旅程，不删除旧实现、不据此取消跨书/临时查阅需求。

### Spec
- 暂存失败保护限定成立：阶段状态不再于尚未碰旧目标时删原文件，红绿测试确实复现/关闭原P1。尚不能关闭回滚整体：ShelfImporter入口:879–880和EpubRepository:94–95先无条件backup.delete；上次中断后target缺失、backup为唯一旧字节时，下次操作会毁它。Root与独立审查一致。下一只保全已有backup并拒绝新覆盖，或用明确恢复规则处理；不引入journal/身份/UI重构。须覆盖两格式普通文件残留且target缺失/存在。恢复失败可能掩盖主异常，保留backup并明确cause/suppressed，不称已原子回滚。提交后异常/target不存在另作边界，不夸大33绿覆盖。
- 同步门控2/2限定采纳：mrgv2_41非空三行关状态SHA未变、push仅device；mrgv2_42 progress/inspiration远端99真实应用且push两键存在、books仍关。与v1 books对照组成三类受控门控，不证明实际桌面LAN。
- 原宿主文件Root新鲜读回53/53集合及SHA一致，7个prefs逐项匹配，弥补外部readback只有“46OK/7missing”再摘要更正缺逐项原输出。两个新UID包当前均不在package列表，reverse空；原APK3/3沿外部原件。当前密度280、超时60000。夜间模式fresh为no，v1原基线yes，v2before未采该项，不能归因于其产品代码；Root将它恢复为yes并留原输出，不声称整个系统绝对零副作用或KeyStore直接无遗留。
- v1 APK时间线如实查出：首装test cd1e2e99已回读，build02/03没有安装回读。mtime不能证明安装，故最终test11686013运行对应仍是历史未证边界；v2新增门控用例有完整对应。不得把v2新freeze追溯用于v1所有运行。A1–A5/S1/S3原行为证据保留，不能统一绑定最终testSHA。
- 阅读v2基线和删除语义修正可采纳，非设备通过。NOTES入口不可达不通过增加旧页面直接处理；以可达书内笔记/书籍详情冷读回源+仅移除正文为下一有限设备合同。临时查阅跨书入口缺失保留，成功此子链也不关闭整条跨书/临时查看旅程。

## 原始证据
[Root本轮E证据](<E:/creation-reading-assistant/next-round-v2-review-20261003>)：qahost-current-sha.txt、fresh-restore-count.json（53/53差异0）、fresh-settings-packages.txt、fresh-reverse.txt、restore-night-to-v1-baseline.txt、fresh-settings-after-restoration.txt。
[回滚修复原报告](../../dispatches/2026-10/2026-10-03-next-reselect-rollback-fix-v1.md)、[MuMu v2原报告](../../../qa/2026-10/2026-10-03-next-integration-mumu-accept-v2.md)、[阅读v2原报告](../../dispatches/2026-10/2026-10-03-next-read-note-return-precheck-v2.md)保留，引用须附本补正。

## 未覆盖项
没有新跑JVM/构建，未独立核全882文件树，只核关键补丁/XML和25阅读SHA；33绿不证明强杀恢复、旧残留安全或提交后原子性。旧v1test安装关联缺失不伪造；R8/签名升级/实体手机/全产品/F维持未验。

## 恢复与副作用
Root只恢复原记录夜间yes；对受保护文件仅hash读回、不读凭证内容，未安装/卸载包或回灌DB。HEAD/index保持。文档写前核指纹并备份E，原报告不覆盖。没有读取其他应用私有库或写自动记忆索引。

## 交付状态与下一步
回滚v1“暂存失败误删”限定修复1/1，整条资产保护返修；MuMu门控补验2/2=100%，旧test全批freeze对应保留未证；阅读预查修正交付1/1，旅程设备0/1。全产品分母未确定。
规划保留阅读优先/轻量灵感/深入整理桌面；暂不恢复旧独立笔记页，不删除已有源码或取消临时查阅要求。下一并行两项：回滚v2补旧backup保护和必要失败用例；新UID执行可达三格式笔记冷读回源与正文移除有限旅程。Root后续合流需新29路径范围及受影响验证，不直接向D套原25补丁，不stage/commit/push。
