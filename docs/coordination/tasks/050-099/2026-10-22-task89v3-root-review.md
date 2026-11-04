# 89-v3 Root核验与90-v1最小修复边界

## 任务编号/版本、输入指纹
89-v3-Root，2026-10-22。交付完成不等于业务通过：采纳本次真实导入/共享分页/位移和完整恢复；EPUB续读整链仍FAIL。

任务根`E:\creation-reading-assistant\task89-epub-selfuse-accept-v1\v3`。Root实核binding全部28项SHA匹配，REPORT SHA匹配ready。主入口17ee0a95998e289914adbaa77beb4f5477dff118b50a7c0d6c8f2ffdc9ed5e76，binding3e93495deda12ff85a6be4bc2ebb8b7d82cf4fc022845f9a45038a7fbde38edc；Flow为v2/scripts/b89_flow.py，15f24d8bda106f71624b398068d381b4e4a4b811e63773e9040a9517bceb4e21。APK仍33a08a05a75e482dc73cba00ce5f5eec15ddb070ad5cbe16b41299b5657d86df，52,763,881B，本轮未构建。

## 所有权与改动、环境
外部owner只执行本批设备并交报告；Root只核证据/写交接，与一个只读explorer并行定位实际生产计算链，无新产品改动或设备动作。未发现匹配task89/t89的活动执行进程。唯一工作区仍E:\creation-reading-assistant\task59-common-candidate-v1\candidate\android，Gradle home D:\develop\env\gradle及独占构建锁不变。

实际run20261022-165359-e1db9d，16:53:59—17:00:53 +0800，serial127.0.0.1:16416，boot f93845f0-6ebf-4a07-b686-e5bb65139b23；实例1 pid26492/headless60632。身份是本run历史证据，下批重新核实。check含manager只读查询，不能笼统称全部纯离线。

## 执行与结果、原始证据
Root读取run-summary、flow逐bookId原始行、reader-open/mid XML及logcat：
- initial、准入、安装均通过；正确host forward/reverse探测已验证，不重开89-v2命令修复。
- 真实导入测试EPUB-89，bookId=epub_-hambcz；reader-open XML包含分页正文已就绪，证明共用分页分支；起点marker01、0.0%。
- 第一次实际翻页：marker07、页脚12.8%，controller emit35.45894%；第二次：marker13/第二章，页脚62.6%，controller emit64.83092%。
- 同bookId B行progress64.8309173583984、completion=reading；locator={chapter:1,offset:0,locator_v2:{v:2,offset:1791,ci:1,co:0}}。因此页脚显示精度与DB不一致，B gate正确拒绝。
- 主入口exit6，business first_error保留；BACK/退出/重开/冷启动/冷重开均NOT_RUN，不采纳任何“退出后是否回退”结论。

原始证据均在v3/evidence：run-summary.json、binding.json、console-run.log、run/20261022-165359-e1db9d/flow.json、reader-open-EPUB.xml、reader-mid-EPUB.xml、logs/logcat-20261022-165359-e1db9d.log及01—07阶段回执。

## 恢复与副作用
Root直接消费本run initial/final proof：consumer=true，比较及capture均71私有/820外部，diffs=[]、DB组匹配、reasons=[]。保护原件位于E:\creation-reading-assistant\task83-selfuse-delivery-v1\B\v2\protection-root-r1\outputs\20261022-165359-e1db9d\{initial,final}。

恢复原件RECOVERED、blockers=[]，本批包卸载成功，唯一EPUB材料按SHA精确删除，11设置无差异/night=no；keepalive真实device/boot_completed1、boot与准入一致。采纳本run完整恢复，无新欠账；不当作当前设备实时状态。旧FAIL保留，未修改入口再试。

## 只读定位与报告措辞纠正
实际E共同候选CustomTipPlaceholder.kt:149-153的ReadProgress直接absOffset*100/source.totalChars；EPUB使用LegacyOffsetCodec混合坐标，source.totalChars为bookIndex估算章长固定总量（ReaderPagerEngineState.kt:220-225），不是运行时实测校准分母。

TxtPagedController.kt:135-152对chapterLengthsAreEstimated使用LearningProgressEstimator.percent；PagedReaderControllerEffects.kt:199-228将其放入回调及PageInfo；ReaderProgressComputations.kt:238-243用pagedPercent作为reader进度。保存路径ReaderProgressActions.kt:366-380另调用EpubProgressCalibration.percentFor。本run controller与DB在第二章样本相合，页脚旧公式偏离。

交付报告“非首页必然分叉”过强，收窄为两个真实样本不一致；没有证明任意位置/所有EPUB都如此。控制器和保存路径目前仍是两个模型，样本相合不构成所有状态相等的数学证明；当前没有证据要求为此重构整条校准链。

## 交付状态与下一步、未覆盖项
实现/运行交付采纳；EPUB业务及V0退出未通过。下一90-v1由同一owner闭环最小页脚修复→必要回归→唯一debug构建→一次EPUB旅程→严格恢复/报告。优先对chapterLengthsAreEstimated的来源使用已有pageInfo.progressPercent，TXT/MD保留现有显示计算；不改canonical locator、absOffset、持久化算法或86防回写策略。若实际代码证明该建议不可行，先给真实阻塞并在最小范围内解决，不能以扩大容差或隐藏页脚替代修复。

回归必须调用真实占位符/页脚消费入口，复现本次混合坐标、估算章长与校准percent差异；至少覆盖已实测章/未测章场景及TXT/MD保持原行为。不要复制公式到测试helper自证，不默认重跑全量JVM/Lint或旧设备链。

新产品代码改变后必须必要构建，并绑定真实源码差量/新APK/工具闭包。旧33a08 APK若无已保全的同SHA副本，应在覆盖构建输出前只保全一份，作为已有冻结输入的唯一二进制证据；不复制APK对或整工程。下一批需预先列空间预算：源码/证据预计8MiB、必要旧APK保全52,763,881B、现有工作区构建增量按现有依赖估算并如实报告；不以空间压力清理存量。

本轮原始核验与代码定位已并行；后续同一显示数据链、共同构建及设备有真实依赖，交一owner连续闭环，不人为拆多个实现/泛审查任务。TXT/MD旧设备证据在源码影响范围内复用，不重跑旧取材、D/F或限定备份；如新增改动确实扩及这些范围，再只补受影响项。

## 空间与停止条件
Root实测v3总2,250,168B，本run保护2,638,339B；相对上轮离线候选76,581B，本轮新增4,811,926B。原报告表中增量一处算术差331B，以该实算关系为准，不另开治理任务。Root仅新增本报告/current小文本，无工程/缓存/APK复制或旧资产清理。

业务失败即停止本批设备，禁止现场修改后第二次main。下一90仍单attempt，身份/owner/输入不明、strict保护/night或必要恢复失败均阻塞，首错/恢复错同时保留。实例不关闭不重启、结束保持运行，两个自动化PAUSED。无D产品回灌、Git/发布、外部消息、正式库、桌面、HOLD/私库或云。全产品分母未知，仅MuMu，非实体手机/MIUI。
