# 三项 v4 核查与 Codex 窄补齐（2026-10-07）

## 任务编号/版本
CRA-THREE-V4-DELIVERIES-CODEX-REVIEW-v1。用户报告全部完成且GLM5.2现不可用；本轮不再安排GLM5.2，不影响其已有交付核查。

## 输入指纹
D HEAD e39a78da9c62f02614207cffb36969a32492eaea，暂存区空。TXT/D2 v4报告可见；约定J1 v4目录/REPORT不存在，当前仅在E任务根按相关目录名做低成本定位，没有找到。已异步询问交付路径，不把“未找到”写成从未实施，也不重复派发原v4。
E核查根：E:/creation-reading-assistant/three-v4-deliveries-codex-review-20261007。
root-check.json：TXT15/15工件SHA匹配、原机器结果46/46，J1报告缺失。
d2-source-current.json：980项当前完整SHA；相对v3恰两授权文件变化。manifest SHA 940bbca58cbe93c3c59ac9aba560d7ac267666cc15bbe4a748b7855b28624be0。
Root补交d2-v4-vs-v3.patch SHA a3149ac03798808813ceb46b89ca646d9a5bd3db2236c541fd97b9f63df4ba55；不另派机械工件任务。

## 所有权与改动
两独立reviewer只读核TXT/D2，不设备不构建。Root将TXT完整行严格校验窄补丁实施在新E:/creation-reading-assistant/txt-v4-codex-finalization-20261007；v4/v3产品与脚本不改。D仅维护本报告/current。J1未定位前不新派实现者。

## Standards
TXT v4 15工件SHA/46机器结果/4日志SHA成立；五个Android文件与v3相同，v4仅overlay。复制来的freeze仍指v3，不能当完整v4冻结。
D2原XML红4项3失败、绿77项0失败错误跳过，SHA成立；没有原始Gradle日志/机器退出码，BUILD SUCCESSFUL只能称执行者自报，不为该缺项重复旧充分测试。报告“v3候选清单与D HEAD匹配”补正为候选指纹已核，并非候选内容等于D产品。
启动未发现匹配三任务路径的java/gradle/python进程，但不能代替外部应用完整占用检查。

## Spec
D2 v4可进入合流核查：companion按bookId共享Mutex覆盖旧实体读取、staging身份、换入/事务/同步回滚，两入口均经此锁；EPUB锁序单向，无新P1/P2。createNewFile原子创建并据ownedByUs清理。两并发用例调用真实repair/refresh、控制DB barrier并断言最终正文；碰撞测试走真实helper，取消测试后续refresh成功。跨实例/跨bookId实际并发、EPUB真实消费、Room资料、取消残留逐项断言仍未验，不扩写通过。
TXT root/fallback两主要缺陷限定接受，但旧转换器仍将坏percent变0、浮点offset截断、无效时间/错误book返回成功。Root直接窄补齐有限0..100非bool百分比、非负整数非booloffset、可解析带时区时间、精确book身份；不再派整套TXT v5。独立reviewer核真实TXT嵌套locator、9位小数Instant和+08时区兼容，无新增阻断。
唯一owner合流输入必须为完整TXT v3＋v4两脚本patch＋Root strict-fields.patch；不得直接用五文件overlay构建。

## 环境 / 执行与结果 / 原始证据
Windows/Python，Root只跑离线合成定向检查：
verify_strict_rows.py red：15项11失败，退出1；green：18项0失败，退出0。
green含三实际fake握手分支（坏percent/小数offset/坏时间）均无back、无Exit握手，保留失败记录。
E窄补齐evidence/red-summary.json、green-summary.json、green-console.log、execution.json与sha256.json；strict-fields.patch SHA bf4f62af6f84c1b2e75de7455a90e2bc4716ad77bea3a6000bbc1c006556017c；最终callback SHA 54e7e954b32693cea6457d00550a51531c05bca1aeeb122d62a553753b67d6a1。
未重跑充分46/77证据，没有设备或APK动作。

## 恢复与副作用
TXT执行者承认在v3运行chain/e2e再写合成evidence与pyc，违反输入只读；代码/freeze未变和证据再生分开记录，不自动恢复/删除。Root新overlay和fake证据只在本任务E目录，未改原成果、正式书库/设备/缓存。原设备恢复没有本轮新核结论。

## 未覆盖项
J1 v4成果位置/代码/证据未知；不能沿用v3绿测代替。
真实MuMu、SQLite实际可用性与dump层级、产品保存根因、D2真实Room/EPUB、X整机中断及物理硬件/MIUI仍未新增验证。
D产品回灌、暂存、提交、push、发布无授权。全产品分母未核。

## 交付状态与下一步
可见报告2/3；源码/离线可合流输入2/3（TXT带Root补丁、D2），J1待补交。不是设备/全产品2/3通过。
当前资源：Codex、DeepSeek、GLM5.3Flash、Gemini（不稳）；GLM5.2用户报告不可用，移出本轮调度。已有D2无需重做。
下一条提示词仅发给原J1 DeepSeek会话补交，不创建新实现owner、不重复原v4。收到后Root先核；通过才主动给唯一owner合流冻结/MuMu批次。公共构建/同设备单owner，Gemini不放关键路径；不自动发外部消息/定时任务。

### 截图后的路由补正
用户补充截图：补交提示词被送入“TXT验收链 v3 返修”会话，该会话明确未实施J1 v4。不能据此确认所有其他会话都未实施，但当前本机仍无v4目录/交付，原J1 DeepSeek会话的存在没有证据；不再循环要求寻找它。上述“仅发原J1会话补交”的计划由此补正。
Root重新核J1 v3完整982项SHA无漂移；新提示词明确一个J1 v4实施owner，从已核v3关闭两请求归属P2。推荐截图应用中可用DeepSeek（底部显示deepseek-v4.1-flash），新建明确标题的独立执行会话；启动先核占用，若实际发现其他v4 owner/成果则停重复实施并补交。不是v5，不重做TXT/D2；v4先源码/JVM，联合构建/设备仍等Root核。截图提及PR不构成授权，不创建PR或提交。
