# W2设备交付 / M0范围初稿独立核查与规划复盘

## 任务编号/版本

Codex-review-W2-M0-v1，2026-10-01。来源：用户“都完成了”，核查原W2设备窗口与M0只读规划窗口。完成声明不替代验收；本报告取代日常入口中“W2仅准备物、报告缺失”的旧现态，原报告保留。

## 输入指纹

- 恢复仓库HEAD `e39a78da9c62f02614207cffb36969a32492eaea`，暂存区空；J1-v3六源码SHA本轮重新计算6/6一致。
- W2冻结APK本轮重算SHA `0221d88fb34e8c88e274f09b939c5f1b84f20d9c2674abb7c8588e6593c71594`。此前APK/Manifest/签名核查复用；不重建。
- 实读[W2设备报告](../../../qa/2026-10/2026-10-01-next-w2-webdav-ui-mumu-acceptance.md)、[M0报告](../../decisions/2026-10/2026-10-01-m0-current-product-scope-and-evidence.md)与[原始设备工件](<E:/creation-reading-assistant/next-round-w2-webdav-ui-acceptance>)、[M0 CSV](<E:/creation-reading-assistant/m0-product-scope-20261001>)。

## 所有权与改动

Codex只维护本报告、current、产品总规划、Codex项目页/TODO及实际skill记录。修改前[完整回退副本](<E:/creation-reading-assistant/w2-m0-codex-review-20261001/before>)已保存。产品、测试、配置、原外部报告与工件保持原件；未stage/commit/push/reset/checkout。下一批任务以用户转交为执行入口，本轮没有向外部应用发送消息。

## 环境

Windows；Python只读解析SHA、JSONL、PNG、XML与SQL输出。Codex零ADB/设备操作，未扫描书籍正文，未重跑全量测试。实际设备证据为MuMu Android模拟器，不能外推实体手机/MIUI。PNG全部为1080×1920竖屏；已目视核对连接/备份、非法URL、Legado及JSON结果关键图。

## 执行与结果

### Standards：输入、原始记录与恢复

1. 设备实际日志与图/SQL记录已交付。此前“evidence/logs空”只适用于上一轮快照，本轮不再沿用。
2. 原宿主71条**测试前**SHA记录严格解析有效；测试后仅`reconciliation_result.json`称71/71。清理脚本计算结果但不保存逐文件后SHA、旧APK对/UID、七设置及forward原始回读，不能独立复算本批恢复。脚本忽略ADB returncode；密度只检查没有Override、reverse只检查没有18081，均弱于基线逐项相等。服务停止仅报告任务CANCELED，缺监听端口/PID回读。恢复结论暂不通过，不断言已损坏。
3. Case5脚本用`urllib.request.urlopen`从电脑下载；对应GET的User-Agent均为`Python-urllib/3.13`。两个夹具SHA相同只支持服务/夹具字节，不支持Android下载字节。
4. 服务`fixture_bytes`按扩展名返回首个ZIP/JSON，未核精确文件名；PROPFIND的href含`-测试`，displayname含` 测试`。Case6/7真实App使用displayname构造GET，与href不同仍得到200。这会掩盖路径错误；不能据此证明精确路径合同。服务HEAD错误场景还写响应体；403图/XML实际为`Unexpected status line: UnauthorizedHTTP/1.1 403 Forbidden`。响应体污染连接是代码支持的原因推断，须修夹具后定向复验，不直接归为产品403缺陷。

### Spec：七类既定用例逐项采纳范围

| 类别 | 本轮结论 | 实际证据与剩余缺口 |
|---|---|---|
| 1 保存配置/HEAD405回退 | 部分成立 | 保存PROPFIND Depth1；测试HEAD405→PROPFIND Depth0/207实际出现。报告指定XML为“配置已保存”，现有XML没有连接成功结果；补真实测试结果UI，区分保存/测试阶段 |
| 2 PUT409/MKCOL重试 | 已验收（本用例范围） | 实际PUT409→MKCOL201→PUT201；后续另一个快照PUT201及PROPFIND207属于备份流程，不能当额外失败重试；截图“备份成功” |
| 3 401/403不回退 | 部分成立 | 两场景只有HEAD，没有“立即备份”的PUT；403 UI为解析异常。补PUT401/403单请求、无MKCOL/重试及正确UI反馈 |
| 4 userinfo/公网HTTP拒绝 | 未闭合 | 零本地请求与合法地址正对照存在；非法URL图/XML只显示“配置已保存”，无拒绝结果。公网请求不打本地服务不能证明网络前拒绝；补实际操作后拒绝信息和阶段记录 |
| 5 中文/空格精确路径及字节 | 未闭合 | Python GET不能代替App。真实App Case6/7 GET没有双重编码，但服务接受错误文件名，精确路径与App字节哈希未证实 |
| 6 Legado有效导入 | 已验收（所列字段/本用例范围） | App扫描/确认/导入1条，GET200；目标进度约30%、时间2025-12-29T09:20:00Z、ci3/co120、reading/completed_at空；书籍所查询字段与对照书籍/进度整条查询输出不变。不是全DB所有列证明 |
| 7 JSON有效恢复/安全快照 | 部分成立 | App确认/GET/恢复UI及查询字段成立：目标书size4096/revision2/updatedAt、进度42%/revision2/locator_v2；对照查询输出不变。快照“存在且可用”的原始目录、完整SQLite及恢复前状态对账尚未落盘，报告所称380928字节不可独立确认 |

完整用例闭合2/7≈29%；另5类为部分证据/未闭合，**不是判5个产品缺陷**。整体构建+完整设备关卡仍1/2=50%。Case7恢复功能证据采纳，快照与整体恢复门禁不被省略。

### M0范围与分母

- CSV结构实数34行×13字段、索引25行×8字段；“是”28、“待产品决定”6成立，初稿作为导航采纳。
- 不采纳为全产品分母：G7-04自己注明“验证方式，不是产品旅程”，G8-01为构建关卡，却进入28项；G5-02需表述云备份/恢复/导入的用户能力而非独立测试包。剔除两工程行得到26个暂列功能条目，也不等于用户已批准的完整产品范围。
- 分母由本版范围决定；通过数由证据决定。工件缺失、未知验收不能阻止记录明确旅程的范围，只影响分子/状态。不得等待所有项通过才确定分母。
- G2-02仅引用2026-09-09旧失败，未提供J1-v3相同路径失败；应为“历史缺陷，当前候选待核”，不按当前已证实缺陷排新开发。源码入口存在也不能证明整条用户流程已实现。
- M0内W2缺报告/无执行证据已过时；三个Android需求相对链接失效。新版本纠正，原件保留。G6六条桌面历史交付先核当前源码/证据归属，不整批重开发。
- 全产品实现/验收百分比仍不可可靠计算；拒绝把A–E5/5或工程关卡计成全部产品100%。

## 原始证据

- [本轮审计脚本](<E:/creation-reading-assistant/w2-m0-codex-review-20261001/audit.py>)、[逐会话/哈希/图像/分母审计JSON](<E:/creation-reading-assistant/w2-m0-codex-review-20261001/verification.json>)。
- [Case5脚本](<E:/creation-reading-assistant/next-round-w2-webdav-ui-acceptance/scripts/run_case5.py>)、[服务](<E:/creation-reading-assistant/next-round-w2-webdav-ui-acceptance/service/controlled_webdav_w1.py>)、[清理脚本](<E:/creation-reading-assistant/next-round-w2-webdav-ui-acceptance/scripts/cleanup_and_reconciliation.py>)。
- [Case6前后SQL](<E:/creation-reading-assistant/next-round-w2-webdav-ui-acceptance/evidence/case6_legado>)、[Case7前后SQL与UI](<E:/creation-reading-assistant/next-round-w2-webdav-ui-acceptance/evidence/case7_json>)、[原请求会话](<E:/creation-reading-assistant/next-round-w2-webdav-ui-acceptance/logs/sessions>)。
- [此前构建独立核查](2026-10-01-next-w2-codex-review.md)、[总规划](../../../plans/2026-10-01-product-roadmap-realignment.md)。

## 未覆盖项

旧KeyStore/凭据可恢复性、完整DB所有列、真实桌面LAN、N1最低中断恢复安全、实体硬件/MIUI、TalkBack、R8运行、正式签名/升级/F、HOLD归属保持原边界。只读工件未证明服务已停止或设备当前空闲；原设备owner先核自身状态。

## 恢复与副作用

Codex没有设备副作用。外部报告称卸载新包、撤销reverse、恢复density、停止服务及71/71成立，但缺本轮原始后账，不能写“绝对零污染”。新批先核本轮历史基线与现状，差异列出，不将有差异的现状覆盖旧基线或自动回灌旧书库。原宿主/正式包不启动、不写；只管理本任务新UI包及自有临时映射/服务。

## 交付状态与下一步

### 规划复盘

- **保留**：双产品本地优先目标、M1限定闭合后候选收口→M2最低中断恢复安全顺序、旧已核证据复用、F另立范围。此次没有关闭M1。
- **调整**：剩余W2先修测试工具的HEAD响应与精确路径检查，只补未成立分支和后账，避免继续用宽松服务产生虚假PASS；不重复JVM/Room/整包构建。
- **并行**：原DSH窗口接W2-accept-v2；原构建窗口接D1-v1，只读核G6桌面归属并修M0-v2范围/链接/过期状态。无设备/产品写交集。N1数据安全产品修复由后续单一实现者承担，不因为设备owner角色自动取得源码所有权。
- W2-accept-v2允许自有新E目录的服务/脚本/证据及唯一新QA报告；D1-v1允许自有新E矩阵/归属证据及唯一协调报告。两者不改公共账本、产品或Git；发现真实产品缺陷提交具体证据和建议最小文件清单后停，交Codex重新分工。
- 实际路由按可用模型：原设备任务DeepSeek V4.1 Flash适合定向脚本/验收；D1可用GLM5.3 Flash或同级可用模型。关键数据修复另选合适强模型，未发送自动任务或更改模型。
- 下一任务不是重新七项全量：Case2/6限定结果复用；补Case1结果、Case3/4/5与Case7快照及恢复后账。M0-v1保留为原始初稿，不作为现行全产品百分比来源。
- 收尾入口验证：21文件/138本地链接，0错误/0提醒；其他项目TODO后缀与skill历史字节保持，HEAD/暂存区未变。此静态结果不替代产品验收。
