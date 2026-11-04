# 89-v1停点接收、89-v2执行与89-v3离线准备

## 任务编号/版本、授权、所有权与改动
2026-10-22，用户在Antigravity额度耗尽后明确要求Codex继续完成。Root接管本任务设备、核验与交接；explorer只读旧入口，worker仅写新版本必要launcher/准入/恢复及定向离线测试，Root写新Flow、报告和公共入口。不覆盖旧89-v1、旧FAIL或共享producer。接管前未发现其他匹配项目执行进程；未打断工作。

## 输入指纹与环境
当前APK仍为`E:\creation-reading-assistant\task59-common-candidate-v1\candidate\android\app\build\outputs\apk\debug\app-debug.apk`，SHA256 `33a08a05a75e482dc73cba00ce5f5eec15ddb070ad5cbe16b41299b5657d86df`，52,763,881B；本轮构建0、产品源码修改0、APK复制0。唯一工作区、Gradle home D:\develop\env\gradle及独占构建锁不变。

89-v2实际入口SHA256 `13f29403a652294ff17c2261760b3801bbceeaeec6d9abd5cdfcae17e36e4fc3`；Flow `15f24d8bda106f71624b398068d381b4e4a4b811e63773e9040a9517bceb4e21`；准入`2a4734e4bb2545582ae0d8d53bc7547dbaa09023fc4fcd2538cd1d9cfc3c0ee2`；binding `a84a186743edf72e25af2021ad888846f9f4d343a2755014decd238a3ba53b92`。实际25项闭包消费核同，check通过。

现场实例1 pid26492/headless60632，serial127.0.0.1:16416，boot f93845f0-6ebf-4a07-b686-e5bb65139b23。这是本轮新绑定，不沿用87的boot/进程。MuMuManager is_android_started=false而真实ADB sys.boot_completed=1；用真实系统/服务回执核准，不把该manager字段直接等同未启动。未关闭重启实例或共享ADB。

## 原停点与最小修复
旧89-v1已消耗一个入口attempt：`20261022-161611-f4c841`，16:16:11—16:16:12，device_binding FAIL，缺席门禁为false；未进入initial、准入、安装或业务，没有新设备恢复义务。原目录无REPORT/ready，已保留现有源码/绑定/日志和run-id，不把它当尚未开跑。

Root只读复现旧探测：同一不存在的EPUB路径，旧`adb shell su 0 sh -c <script>`返回rc2/空stdout/stderr；stdin `shell -T su 0 sh -s`返回rc0/CRA_ABSENT。真实红绿见`E:\creation-reading-assistant\task89-epub-selfuse-accept-v1\root-review\probe-transport-red-green.json`。这证明当前传输接线有问题，不补造旧16:16未保存的逐命令结果，不归咎代理节点。

v2改stdin脚本传输和原始回执、实际boot门禁；准入包/设置/基线等先决失败在push前退出。Root另修Flow：记录翻页前状态拒绝零位移，BACK后先snapshot并证明书架再读DB门禁。新增launcher4项、准入2项（4失败子例）、真实Flow2项定向离线通过；旧89-v1的8项充分证据复用。无全量重测。

## 实际执行与结果
89-v2唯一run `20261022-162748-6fd99b`，16:27:48—16:31:49，原入口exit5，first_error=admission。设备业务NOT_RUN，APK未安装、EPUB未推送。

strict initial通过：71私有/820外部、diffs=[]、DB组匹配。准入失败为forward/reverse两项SETTING_PROBE_FAILED：继承的`[shell,forward,--list]`/reverse实际rc127；正确host ADB forward/reverse --list均rc0。Root开跑前未识别这两项继承命令错误，本轮严格边界在push前拦下，不隐藏失败或重跑入口。

原恢复器返回MATERIAL_LIST_MISSING、MATERIALS_NOT_CLEAN，因为准入在生成材料清单前失败。原恢复FAIL和总FAIL完整保留。随后同attempt strict final通过71/820无差异、DB组匹配，keepalive通过、boot相同、night=no。

## 恢复与副作用
Root独立只读continuation再次核目标包/材料均absent；结合冻结准入在push前return、没有install/business阶段，确认没有本批创建物遗留。证据`v2/evidence/root-readonly-closeout.json`的no_batch_assets_remain=true；没有再卸载、删除或写设置。采纳“本轮无新增设备数据恢复欠账”，不把原恢复器改成PASS，不宣称整链/EPUB通过。

原71/820保护原件：`E:\creation-reading-assistant\task83-selfuse-delivery-v1\B\v2\protection-root-r1\outputs\20261022-162748-6fd99b\{initial,final}\readonly-protection-proof.json`。结束设备在线、实例保持运行，现场结论限本轮采样，不承诺之后实时状态。

## 89-v3离线交付（未运行设备）
同任务根`v3`只存必要差量，引用v2 Flow和v1材料，不复制工程/APK。host forward/reverse接线已修；准入持久保存files空清单、push_started=false和mutation_stage=none，push前记录精确intent；本地恢复器核同run/serial/boot、receipt/准入SHA及当前包/目录/设置/保护，只有确证未写入才NO_MUTATION_CONFIRMED。空清单或失败探测不能独自放行。共享恢复原件不改。

- entry SHA256 `17ee0a95998e289914adbaa77beb4f5477dff118b50a7c0d6c8f2ffdc9ed5e76`。
- admission `df5757f5f1fad0e791e516e93c763a982b036c17ba7e775541f6ee77c18e9243`。
- local recover `1ce3d144cc89e3eaa83c82f5d60379de08aa2327f6173952bfdfdb55cec565ab`。
- binding `3e93495deda12ff85a6be4bc2ebb8b7d82cf4fc022845f9a45038a7fbde38edc`。

6项新离线测试通过（准入2项含5失败子例，恢复4项）；host命令127仍阻塞、空清单单独拒绝、旧receipt拒绝。Root实核28项绑定SHA全部匹配。状态OFFLINE_PREPARED_NOT_RUN，v3无check/main/设备attempt，不能算EPUB验收。

## 原始证据与空间
任务根`E:\creation-reading-assistant\task89-epub-selfuse-accept-v1`；旧evidence保留。v2 REPORT.md/ready.json、binding、evidence/run-summary.json、console-run-root.log、admission/admission.json、recovery/recovery.json、原阶段回执、root-readonly-closeout.json；v2离线日志/root-flow-test.log；v3 ready、binding及offline-evidence两份针对性日志。

空间统计时task89 v1/v2/v3合计449,592B，本run保护采集另2,637,940B；v3其中76,581B，均低于预算。统计不含随后少量报告写入，不当作精确归因所有旧字节。仅新增必要源码差量、日志与唯一证据；未复制缓存/工程/APK，无存量资产删除迁移。

## 交付状态与下一步、未覆盖项、停止条件
实现和离线修复已交付；本次模拟器业务未执行，整链失败，EPUB续读尚未完成。既有86 TXT、87 MD、旧取材/搜索返回及限定D/F/备份采纳保持，不重跑。V0仍待受影响EPUB一次同版旅程，不能写全部退出；全产品分母未知，无实体手机/MIUI或发布结论。

本批已执行一次89-v2主入口并失败，按现行“单次失败即停”停止设备，不用增版本规避重试约束。下一明确设备批使用已就绪89-v3重新现场绑定，只补EPUB真实导入/共用分页位移/退出重开/冷重开与严格恢复；不重建或扩大矩阵。两个自动化保持PAUSED，不自动启动/外部接力。无Git/发布、D产品回灌、桌面、正式书库、HOLD/私库、云或存量清理。
