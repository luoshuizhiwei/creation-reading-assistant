# 25–31交付核查及精准返修

## 任务编号/版本
CRA-TASKS25-31-CODEX-REVIEW-v1；2026-10-13。用户报告全部完成，Root直接核查，不创建GPT/Claude子代理。

## 输入指纹
HEAD e39a78da9c62f02614207cffb36969a32492eaea、暂存空。主输入1009清单SHA 08e09f3965a1540e0c85d6cfdbcfc46b4afaec3ec229f8b22cc79d0bd6d798b5。六个新候选实际源码SHA差量与原XML已核；31七工具SHA与五原结果SHA共12/12匹配。详细输入/差量在[完整核查](<E:/creation-reading-assistant/tasks25-31-codex-review-20261013/REPORT.md>)。

## 执行与结果
7/7交付存在。25/27/28/29/30五项源码/JVM限定采纳，26/31两项返修；不合流、不推进新功能。六候选原绿JVM85/30/56/24/17/57均与XML/日志相符，是不同树269次原执行，不是共同树新门禁。原10项发现8项限定采纳，R3/R7未完整关闭。

26消除整表清空、已读时间不倒退等改动成立，但JsonBridge直接shelfBookDao.upsertAll以REPLACE覆盖本地同键position；SQLite复合主键反例本地9→备份1→恢复1。新用例尚未覆盖这个字段。

31部分失败/预存保护成立，但完整validator在同路径源码改字节并更新派生SHA、同路径消费者改字节并更新派生SHA、新增未登记源码三种Root离线反例均valid=true。正常输入对照也true；仅比较count/keyset和冻结声明tree不等于扫描当前实物。未实际使用完整强verify，R7继续返修。

## Standards
原报告/候选只读，未确认实际历史数据损坏。26假DAO失败用例不证明真实Room回滚，原事务合同按已有证据复用，报告须收窄。本轮无设备/新Gradle/产品写入。

## Spec
两P1剩余点在完整报告精确定位。返修沿26-v2/31-v2，不新增32号。26只补现有同键关联字段保护及其回归；31闭合强冻结实际内容/集合并绑定真实当前根，保留已过S1/S2/R8。五个合格增量不重派，不重跑输入未变的充分证据。

## 原始证据
[六树SHA与XML](<E:/creation-reading-assistant/tasks25-31-codex-review-20261013/artifact-verification.json>)、[Root新反例](<E:/creation-reading-assistant/tasks25-31-codex-review-20261013/remaining-gaps.json>)、[可复核脚本](<E:/creation-reading-assistant/tasks25-31-codex-review-20261013/check_remaining_gaps.py>)、[31收据SHA](<E:/creation-reading-assistant/tasks25-31-codex-review-20261013/receipt-checks.json>)。反例首次正常夹具因CRLF/缺参数拒绝不计证据，修正自有夹具后才取正常/坏输入对照。

## 所有权与改动
Root仅审核E与活动协调文档。26-v2独立E目录`E:/creation-reading-assistant/task26-backup-relation-preservation-fix-v2`，31-v2独立E目录`E:/creation-reading-assistant/task31-acceptance-freeze-backup-guards-fix-v2`。两任务互斥文件，可并行；26定向JVM仍排他锁，31纯离线。v1原件保留。

## 环境
Windows/Python。无新构建/ADB/模拟器/网络/模型配置或外部消息。模型继续实际交付的26 GLM5.3（替换DeepSeek Flash/Pro）、31 DeepSeek Flash（替换GLM5.3），禁止GPT/Claude外部执行；参考耗时26 30–50分钟、31 45–75分钟，排队另计。

## 恢复与副作用
无产品、测试、设备、APK或Git改动；离线夹具只自有E。D WIP/正式书库/原损坏仓库/桌面HOLD/私有记忆均保护。文档写前核指纹并快照。

## 未覆盖项
5项通过不是新合流树/最终APK/设备通过；两项缺口未修。旧24 D/F、短末项、系统UI/恢复链仍保留；实际LAN、实体手机/MIUI、X中断、R8/签名升级/发布未覆盖。

## 交付状态与下一步
本轮核查完成，两个原任务精准返修提示词交用户转发。两v2通过后Root统一合流、冻结并明确受影响门禁/MuMu范围。此之前不安排下一批新功能。全产品分母未知。
