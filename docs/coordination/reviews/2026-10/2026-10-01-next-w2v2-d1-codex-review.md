# W2-v2 / D1 独立核查与下一批重排（2026-10-01）

## 任务编号/版本

Codex-review-W2v2-D1-v1；核查W2-accept-v2、D1-v1/M0-v2。用户“都完成了”触发按风险验证与推进；不以外部完成声明代替验收。新任务按编号/指纹接手，不要求找回原应用窗口。

## 输入指纹

- 活动仓库HEAD `e39a78da9c62f02614207cffb36969a32492eaea`，暂存区空；源码资产保持。
- [J1-v3六文件清单](<E:/creation-reading-assistant/next-round-joint-j1-v3/freeze/six-file-sha256.txt>)实算6/6一致。
- [W2冻结APK](<E:/creation-reading-assistant/next-round-w2-webdav-ui-build/freeze/apk/app-qahost.apk>) SHA256 `0221d88fb34e8c88e274f09b939c5f1b84f20d9c2674abb7c8588e6593c71594`，临时包`com.creationreadingassistant.qahostwebdav`；不纳入永久包名。
- N1下一独立候选从J1-v3取源；LocalZipBackup.kt SHA256 `ab78a16e8e34c50a59ada31040278569baaa013f2c1eba6054a675b89997514c`，已有逐文件journal，不能按不存在重写。

## 所有权与改动

Codex只写本报告、current、项目页、TODO、现有规划有关段落和skill日志；原报告/CSV/产品源码/外部工件不改。本轮自有E目录存审查程序及结果。修改前五文档的[回退副本](<E:/creation-reading-assistant/w2v2-d1-codex-review-20261001/before>)已保存并检查编辑前字节一致。TODO其他项目后缀与skill既有记录保留。

## 环境

Windows；Python3.13，只读SQLite连接mode=ro/immutable。Root只读ADB核当前MuMu serial `127.0.0.1:16416`，并记录设备识别；手机形态1080×1920、density280。不是实体手机/MIUI验收。D1报告自报Antigravity/Gemini2.5Pro，未独立核实际应用/模型；不作永久路由。

## 执行与结果

### Standards Review

遵守互斥所有权、E盘原始证据、中性测试名和不操作原仓库的合同。没有产品写入、安装/卸载、系统设置修改、暂存/提交或外部通信。两个外部目录未提供execution.json：影响追溯，不能据此推定没有执行或抢占。报告已有偏差由本次补正，不增加只改格式的返修任务。

### Spec Review：W2

结合输入未变且充分的v1 Case2/6证据，七类完整闭合 **6/7≈86%**，第5类JSON已证、ZIP未证。不能宣布整批设备门禁关闭。

| 项目 | 独立采用的证据与结论 |
|---|---|
| Case1 | App okhttp实际PROPFIND207→HEAD405→Depth0 PROPFIND207，UI显示HTTP207连接成功；HEAD服务不写响应体修正成立 |
| Case3 | 401与403均有实际PUT失败；403另一次单PUT及Forbidden UI/XML成立，没有误用早期空反馈截图；401/403备份失败分支闭合 |
| Case4 | 采用fresh userinfo_backup与public_http_backup会话：实际拒绝XML且零服务请求。报告引用较早public_http会话含一次合法PROPFIND，不用它证明拒绝 |
| Case5 JSON | 会话20261001-225728-333-case5_v2_app_get_rerun实际okhttp GET200，精确含中文/空格路径且无双编码；UI显示已恢复。服务响应body SHA与受控JSON一致，只证明服务发送字节，不外推客户端下载文件SHA |
| Case5 ZIP | 精确路径GET200来自raw_get_exact电脑探针，无App User-Agent；v1宽松服务的错误文件名不能替代严格路径App GET。唯一剩余缺口交W2-v3 |
| Case7 | 两份落盘SQLite实读integrity_check=ok、version15、28表；快照业务行空，恢复后book/progress各1，其余表行数不变；size4096、revision2、progress42、reading/completed_at=null及locator字段成立。v1非空对照另行复用，不声称v2重测同一对照 |

JSON服务发送SHA `4bcbeabf3594c834082ee38e58b6211612c74046ec2f58d0c8c702d0de477770`。快照DB SHA `c164847326620b848e8de26d168b99e31dcd51398dc320aae6d1dfcc89efdd32`，恢复后DB SHA `de0997aad6aba3784b55dafcb941d210b104dbf26a1502ff3808270d00d8f5df`。23张PNG均1080×1920，Root另看403和非法HTTP实际画面，不能把仅尺寸核对当全截图内容验收。logs/raw目录空，采用JSONL、实际XML/截图与服务源码，不把探针summary冒充原始HTTP抓包。

### Spec Review：D1/M0-v2

限定采纳六桌面旅程的源码归属导航和分类修正：34行中26暂列功能、2工程关卡、6待决策，证据索引28行。G6源码路径均实际存在；关键UI→preload→electron/main/creation-ipc.ts→存储调用链有实存注册点。桌面历史交付不因旧P1待办被重开。

报告12项完整源码SHA实算11项一致；electron/preload/index.ts正确SHA为 **`987e0e4f68e58feb475a0f2e1b86ecf6c0af51adc8669ad16c01c4487648e28d`**，报告e08be140…不采用。本次补正即可，不为这处再派报告返修。历史测试计数、测试脚本存在或源码链存在均不等于当前端到端通过；26暂列功能行也不是已批准全产品分母。“工件不存在是gitignore导致”不能仅从局部缺失确证。

### 规划复核与下一批

保留：本地优先桌面创作/Android阅读目标、M1限定门禁、N1数据安全优先、正式发布后续范围；不重造已交付桌面功能。

合并/调整：D1归属任务退出当前队列；W2只补一个ZIP App分支，不重跑六类。N1-repair-v1在独立E副本实现和定向JVM可提前并行：读J1-v3冻结输入但不修改它，没有MuMu或公共构建所有权交集。联合冻结/集成/中断设备验收仍等M1，不宣布M1完成，不扩Git许可。N1须复用已存在journal，补整次导入持久中断状态/明确重试入口与可证明归属的残留导出保护；未知后缀文件保留，禁止按后缀误删。

## 原始证据

- [本轮审查结果](<E:/creation-reading-assistant/w2v2-d1-codex-review-20261001/verification.json>)、[审查脚本](<E:/creation-reading-assistant/w2v2-d1-codex-review-20261001/audit.py>)。
- [Root当前设备后账](<E:/creation-reading-assistant/w2v2-d1-codex-review-20261001/device-verification.json>)及[逐命令stdout/stderr](<E:/creation-reading-assistant/w2v2-d1-codex-review-20261001/device-readback>)。
- [W2-v2原报告](../../../qa/2026-10/2026-10-01-next-w2-v2-webdav-ui-mumu-acceptance.md)、[原始会话](<E:/creation-reading-assistant/next-round-w2-webdav-ui-acceptance-v2/logs/sessions>)、[UI/DB证据](<E:/creation-reading-assistant/next-round-w2-webdav-ui-acceptance-v2/evidence>)。
- [D1原报告](../../dispatches/2026-10/2026-10-01-next-d1-desktop-ownership-and-m0-v2.md)、[纠正矩阵](<E:/creation-reading-assistant/next-round-d1-desktop-scope/m0-journey-matrix-v2.csv>)、[证据索引](<E:/creation-reading-assistant/next-round-d1-desktop-scope/m0-evidence-index-v2.csv>)。
- [v1采纳边界](2026-10-01-next-w2-m0-codex-review.md)、[N1旧中断实证](2026-10-01-next-n1-n3-codex-review.md)、[现行总规划](../../../plans/2026-10-01-product-roadmap-realignment.md)。

## 未覆盖项

ZIP精确App GET、N1整次中断恢复与真实进程kill补验、桌面全部当前UI/打包运行、实际桌面LAN及全产品范围核定仍未闭合。KeyStore及应用绝对零副作用、实体手机/MIUI、TalkBack音频、正式签名/升级/F未证明；不外推旧授权。没有重跑全量JVM/Room/构建。

## 恢复与副作用

外部post_accounting_v2.json仅summary、无逐文件原始后账，声明22:15/22:20早于实际22:40–22:57请求，不能用作最终清理时间。Root在 **2026-10-01T15:21:41Z（本地23:21）** 只读新取当前端点：原宿主71/71逐文件SHA相同，modified/missing/extra均空；旧宿主/测试APK SHA与UID10083/10084相同；七设置匹配、reverse/forward空，临时UI包不存在，原宿主未运行，PID15732及18082监听不存在。此证据关闭当前约定持久资产恢复，不追溯外部清理过程或未测密钥。

原宿主SHA `e82904cc9c1315466ed5c521693f93f541560feef038bf074ce7f7cf8324ef8a`；测试APK SHA `fdbd019011088505fa37d71565259a0d76f6927407af69148379e2ef7c27038d`。设置：1080×1920、density280、font_scale=null、rotation1/0、stay_on0、night=yes。原始命令未改设置或启动应用。

## 交付状态与下一步

本轮核查已交付，D1限定已验收附补正；W2-v2返修仅剩ZIP分支。联合构建/完整MuMu **1/2=50%**；W2 **6/7≈86%**；A–E5/5=100%、R1–R3实现3/3=100%均为局部范围；全产品分母未核定，不给假百分比。

下一并行任务W2-accept-v3（一个设备owner）与N1-repair-v1（另一独立E候选owner），每项任意空闲且具备能力的窗口即可。前者完成后决定M1，后者实施/定向JVM完成后由Codex独立审查并串行安排联合验收；不自动暂存/提交。

定向文档验证：21文件、141本地链接，0错误/0提醒；其他项目TODO后缀及skill既有历史原字节保留，报告APK哈希与本轮实读一致，HEAD不变、暂存区空。静态验证不替代产品验收。
