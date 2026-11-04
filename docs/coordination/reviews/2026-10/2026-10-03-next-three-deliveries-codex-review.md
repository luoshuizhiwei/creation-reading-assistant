# 三项交付核查与下一批收口

## 任务编号/版本
CRA-THREE-DELIVERIES-REVIEW-v1；2026-10-03。用户报告三项均完成；Codex只读核查原件、源码和设备证据，保留外部原报告。不修改产品、不提交。

## 输入指纹
HEAD `e39a78da9c62f02614207cffb36969a32492eaea`，当前暂存区空。`final-manifest.json` SHA `a0240a395c20251f480378ed07ba901c0c0061c79cd98ea4b43985b229ed491d`。本轮重算25输入/25D-WIP均匹配；设备副本在25路径中仅build.gradle后缀不同。app/src及app/build.gradle核836原文件→837设备文件，只增加仪器类并改后缀，产品源码不变。这不是全883树核查。

## 所有权与改动
Root：本报告、current/TODO现态及E核查工件；原报告和候选保持不变。低成本GPT-6 Luna子智能体仅只读核查两预查的关键代码，Root独立复算版本指纹并纠正其初步HEAD/WIP称谓。未执行Gradle、暂存、提交、push、reset/checkout。

## 环境
Windows；Python3.13与文件SHA核对。只读ADB回读尝试启动了本机ADB daemon；`adb devices`为空，历史serial不可用，未安装/启动/修改任何设备包、数据或设置。不将无设备当作产品故障。原恢复证据按落盘原件审核。

## 执行与结果

### Standards：记录与版本归属
1. 设备索引实际30次，28个OK、1FAIL、1TERMINATED；30原日志SHA均与索引匹配。外部报告31次及28+1+1+1加法错误；28个OK中含至少一次已被报告判无效的早期SAF断言，不能全部作为有效验收次数。
2. 宿主冻结SHA `ee99374b209f5400e5f28d42d02f122c9dd5ff0d0880d585c38c35760a043be7`；最终test SHA `11686013a55eeadd2b6b173fe11a4fb8d28bea92fc2e2fd64d2997e4a9eb2abc`实算成立，实际test字节9327009（报告9166635不符）。installed-apk-sha.txt中test为`cd1e2e99…`，未见最终test的安装回读；可能是早期构建，当前不能证明最终冻结件就是后续运行件。所称freeze/freeze-manifest.json未存在；需版本时间线或重新针对性冻结读回，不能捏造历史。
3. 阅读预查9列SHA中3列不是候选：ReaderProgressEffects候选`7e4b4ab8…`、报告`a7baa349…`；ReaderProgressActions候选`eaacfb9b…`、报告`933513e1…`；ProfileRoute候选`8fde6ce2…`、报告`a1cad555…`。三个报告值均来自D工作树；ProfileRoute HEAD为`99eb2aaf…`，另外两者候选等HEAD。缺席25路径不证明整候选与HEAD/D相同，必须按真实E文件重做受影响链。

### Spec：实际合同
- A1–A5：限定核心证据可采纳。原arm有Process crashed、force-stop后PID空、新PID25833→26725、journal/db_committed/旧正文SHA保留；取消前后DB/正文/state/journal断言；同ZIP重试收敛；真实导出ZIP内容与DB、恢复正文对比及冷读。最终test版本对应仍为整批证据门禁，非新增产品缺陷。
- S1：实际UI三开关及策略、DataStore冷读证据可采纳。已目视s1-after-toggles及SAF截图；187PNG头尺寸全部竖屏1080×1920。
- S2：书籍类别关/开正反对照成立（off无books、on非空revision99上传）；progress/inspirations缺非空受控数据与关/开对照，合同只部分关闭。
- S3：REMOTE/LOCAL等revision7的独立快照和实际JUnit成立，标题分别远端/本地，local_content_path及正文SHA一致；不是实际桌面LAN证据。
- R1：53路径SHA前后字节一致，8行设置前后字节一致；三个原APK文件文本格式不同但包→SHA映射3/3一致。卸载/新目录消失/forward reverse收尾/服务停止主要只有报告自述，未找到相应原始收尾输出，当前又无可读设备；KeyStore“无遗留”不能据卸载自述当作直接观测。R1部分，需补可得原件或新鲜只读回读，不重装原包、不回灌旧DB。
- 重关联：有真实UI入口且当前主要只校验格式的结论可采纳。同格式异内容可换入、content_hash未随手动修复更新属于有源码依据的风险；普通Duplicate不等于恢复关联。报告取消异常结论错误：plain ShelfImporter:935–943与EPUB EpubRepository:132–142内层catch(Throwable)先文件回滚再外层重抛；强杀另计未验证，不因取消异常推定其失败。旧G1删除撤销已有后续v3验收，不重开。关于新交互、异内容替换、元数据及journal的四项选择没有因此自动成为新需求。
- 新发现P1（静态可达，尚未运行复现）：TXT/MD ShelfImporter:883–885复制staging失败会进入:936无条件internalFile.delete，旧目标到:887–889才备份；EPUB EpubRepository:92–95暂存复制失败进入:132无条件target.delete，旧目标到:98–100才备份。已有同名旧正文可被删除而无backup可恢复。Root与独立只读审查一致；原预查“复制失败总回滚到原状”错误。先给这两个路径补真实失败注入红测，再做阶段敏感回滚最小修复；不顺带重构内容身份/UI/journal。
- 阅读：不join books不足以证明删书后笔记仍可读；notes/highlights外键CASCADE，软删除与active流也需区分。移除正文保留书行、软删除/撤销、物理删行是三个合同，不能把“删书行仍可回看”直接列PASS。主要能力存在的方向可留，按真实E输入返修后再安排旅程验收，不重写笔记系统。

## 原始证据
[Root核查目录](<E:/creation-reading-assistant/next-round-delivery-review-20261003>)：audit.json（25输入/设备差异、30日志SHA及OK/FAIL/crash、恢复对账、双APK实算），supplement.json（836→837源码差异、187PNG方向、3APK语义对账、9源码HEAD/D/E），verify.py。
[设备原报告](../../../qa/2026-10/2026-10-03-next-integration-mumu-accept-v1.md)、[重关联原报告](../../dispatches/2026-10/2026-10-03-next-relink-precheck-v1.md)、[阅读原报告](../../dispatches/2026-10/2026-10-03-next-read-note-return-precheck-v1.md)保留原件，引用它们必须附本补正。

## 未覆盖项
没有全量构建/测试重跑；当前无在线ADB设备，未独立读回最新持久数据与清理状态；原test APK执行对应需补。没有实体手机/MIUI/真实跨端/发布证据，不授权D回写或Git操作。

## 恢复与副作用
产品25WIP指纹与HEAD/index不变；原报告不覆盖。current/TODO写前备份、核读取指纹；备份在本核查E目录。未读写其他应用私有记忆、自动memories索引、正式书库或原损坏仓库。

## 交付状态与下一步
三份报告3/3已交付，不能写3/3已验收。设备9合同：7项核心行为有可采纳证据（77.8%=7/9），S2/R1部分；最终test版本对应是额外整批门禁，尚未通过。重关联定位限定采纳，修复方案待补正；阅读预查返修。
下一并行：设备单owner仅补progress/inspirations门控及收尾/APK时间线；P1修复owner在独立E副本只改两条文件替换路径及测试，不抢设备；另一只读owner按冻结E修正阅读预查，不构建。旧A1–A5/S1/S3/153JVM复用，不全批重跑。P1先于体验增强；原25候选设备验收不因独立P1副本启动而改写输入，后续合流需新清单及受影响验证。全产品分母未核，不给全产品完成率。
