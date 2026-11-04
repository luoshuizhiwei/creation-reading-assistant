# D0后续裁决建议：选择R2版本化重建test包保护基线

2026-11-01（香港时间）。承接[Root v1裁决](2026-11-01-inventory-drift-root-adjudication.md)第4.3节UID变化停点。用户提供ZCode选择页面截图后，Root读取D0原件并独立本地核验。本轮仍只读文件/更新文档，没有操作ZCode、ADB/VM、卸载重装、删文件或实际迁移基线。

**Root推荐选第二项R2，不选R3先卸载试UID。** 这是一份可供用户在ZCode确认的精确方向；截图不是执行批准。本文件不把“用户已选择R2”“8文件已获删除授权”写成事实。实际执行前由接任协调者记录用户在该选择框/聊天中的明确选择，之后按新合同推进，不重复向原Root索取同批确认。

## 1. D0哪些事实已核实

D0根：`E:/creation-reading-assistant/task209-navigation-coverage-v1/inventory-repair-r1/`。

本轮独立读取`raw/d0-round1.json`、`raw/d0-round2.json`并重新计算/解析：

- 原脚本inventory两轮rc0、stderr空、stdout逐字节同，SHA均为 `8b2e33179b9b5606e767ec3a0c40f6db48b1b3a8c9cda2303f6a7723640c9ac2`。
- 与原694B inventory逐path比较：**missing=1、added=0、metadata=6，6项mode/type均未变，UID/GID改变**。原test包UID10071变为10106，cache组20071变为20106。
- 唯一缺失是原DE shader cache（916B）；当前CE/DE inventory内没有普通文件。不能把“content=0”误解成“原内容无损”，该文件已在missing项明确计为差异，且其原副本仍在preserved tar，可读、未永久丢失。
- test APK采集rc0，SHA仍为原 `0d18d788a04e366271df3ad2ac18cc2ceeb95edc3c64c37cb995745aefbc9a9c`，代码未漂移。
- 两轮User0行一致：`ceDataInode=5506966 installed=true hidden=false suspended=false distractionFlags=0 stopped=true notLaunched=true enabled=0 instant=false virtual=false`；旧行inode6030531等字段不同。
- 8项UI XML本地副本实算SHA全部与ledger记录的设备SHA一致，原证保全成立。清理许可仍独立，未由此自动产生。

packages.list/D0记录支持当前UID归属变化；但**具体哪次历史卸载/重装造成变化尚未定位**。因此“完整卸载导致”是与现态一致的机制推断，不写成事件时间链已逐条实证。旧inode在当前受支持恢复方法下没有可保证的精确复原路径；不能夸大为文件系统层面绝对不可能。

## 2. 为什么R2比R3合理

1. 追旧UID不是产品验收目标，原test APK已相同。重新卸载会再次改设备目录/归属，不能当成可精确回滚的无风险实验。
2. 即便UID碰巧回10071，也不保证旧ceDataInode、旧userState行或未排序inventory的原字节顺序复原；R1/R3仍需要新合同，不能省掉版本化工作。
3. D0已经解释了当前test包保护域的全部结构差异，没有必要为916B原缓存去尝试UID分配器。选择R2可让新保护合同从已核实的实际安装世代开始。
4. 这不是v1拒绝的“未知差异直接改SHA”。必须保留原v13合同、preserved APK/tar/inventory、旧失败和完整旧→新差异，生成独立新基线版本，限定到**原test包**。当前8个外部新增文件及其他71/820/DB保护绝不能一起吸收进新基线。

## 3. 用户选择R2后，ZCode应完成的合同内容

### 3.1 本轮允许与禁止

- 不做UID实验，不`pm clear`，不卸载原test/重建VM，不对旧目录递归chown。
- 先编制独立版本的恢复合同与test包基线包；原v13及全部原件只读，不修改原SHA来伪装旧恢复成功。新版本号先核现有注册后选，不假定某号空闲。
- 新scope的raw采集必须真实，保留完整argv/rc/stdout/stderr、fresh实例/boot/owner、原test code+data+metadata+userState，不能只钉8b2字符串。
- 新基线仅限定`com.creationreadingassistant.test`的本次安装世代。产品包、qahost、其他包、正式数据、外部集合和原71/820/DB保护仍按原各自基线，不能一并重置。
- 916B旧缓存的缺失作为**显式接受的已知test环境差异**入迁移记录，不称“恢复到原样”。原tar保留，当前六目录及metadata建立新独立可恢复归档。新归档也必须白名单/成员核验与读回，不只有inventory文本。

### 3.2 userState不能只把截图短行复制成新常量

当前`stopped=true/notLaunched=true`是当前事实，但instrument执行、原test APK的`install -r`及恢复阶段可能改变这些字段。新合同先基于真实生命周期定义：

1. 哪些是本安装世代的身份不变量（package/UID、APK、CE目录归属及当前inode）；
2. 哪些是应按授权恢复的运行状态；
3. 哪些系统字段会因明确操作发生不可逆转换，以及本合同是否接受这项转换。

**不得为了让未来恢复通过而忽略整行userState、删掉inode或把true/false任意视为等价。** 若旧精确行在合法测试生命周期中无法还原，应在新合同明确记录允许的字段级转换及其操作依据、前后原回执，或者先建立合法稳定基线再冻结。不能把一次真实instrument伪装成“只读基线准备”；需要该验证批的真实授权/身份/恢复合同。

### 3.3 新门实现与核验

- 所有新基线pins/版本必须进入新driver/host适配的真实执行闭包。仅改预检摘要或传入值不够，真正的parent pre-gate和恢复post-gate都要使用同一新合同；核实际加载对象，不能只改一个未被调用的常量。
- 旧冻结host136及旧恢复链原文件保持只读。如需派生新适配层，在任务根做最小有版本差量；不得回传伪造的旧d6ac inventory或跳过校验。
- 离线正反例至少覆盖：合法新基线；错APK/UID/版本；目录/权限/owner变化；文件新增/缺失；命令rc失败；userState非法变化；错误serial/boot/scope；误套旧v13或缺少新基线资产。真实偏差不能被归为缓存忽略。
- 先独立核新合同/归档/pins和加载闭包，再制定一次最小恢复验证批。仅该批按合同原证通过，才能接受新基线恢复门；**基线文件生成不等于gate/恢复已运行通过**。

### 3.4 两个闸门仍须各自关闭

新test包基线/恢复门闭合，与8个UI XML造成的external_added是两条独立闸门。后者仍按v1列出的8条精确路径、已保全SHA和单独用户清理许可处理；不获许可就保持HOLD，不过滤consumer、不用新test基线包覆盖外部差异。

D0两轮raw里的UI XML `stat`调用曾有参数格式错误（rc1），ledger另记录修正后的statRc0。实际清理前应重新用正确格式采集完整stat/sha原回执、核8项仍与已保全hash一致，再按明确许可只删清单项；不能用`ui-*.xml`通配或递归删除。保留本地原件，任何新变化停清理。

## 4. 不变项与接续

- D1仍不新增产品故障注入/跨书接缝；P5条件加签仍有效，但实际运行仍等新test基线恢复门和外部保护两条均关闭。
- 旧205原红、208/209已核的MuMu有限业务结果和之前恢复偏差保留。新基线迁移不是对旧数据丢失/权限漂移的追认抹除。
- 自动化继续PAUSED；Root本轮未操作ZCode选择框、未代发、未给任何删除动作授权。用户的选择应由实际执行者记录，不能从本截图推断。
- 报告继续放task209的本次inventory修复任务根，分开记录D0、R2合同准备/审批、新基线生成、适配门离线验证、一次恢复验证和8文件处置；预算/新增bytes统一写REPORT，复用原件，不复制整工程/缓存或全套历史证据。

本轮复核原证索引：`E:/creation-reading-assistant/coordination/inventory-drift-root-adjudication-20261101/d0-followup/evidence.json`。前版裁决对未知差异的禁止仍有效；本增补只把“已解释的test包安装世代变化”推荐为新版本合同迁移，不授予全环境重置许可。
