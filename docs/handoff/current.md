# 当前交接入口

## 2026-11-13 R8 收尾批（ZCode，四项全部完成）

① 新候选 `0a81ac04…` vs 旧包 `5ef408d7…` **entry 级对比 PASS**（565=565、resources/assets/libs 零差、未压缩总差 +2720B 全在 dex，2MB zip 体积差=zipflinger 波动，疑点排除）。② 定向门禁 **412/412 rc=0**（搜索族+源导航+备份+接线+新回归）。③④ **五个本地提交**：`3a478bca`(fix 3 源文件)→`cebe771`(current.md)→`04ef16f`(13 份 coordination/handoff 归档)→`2dee4c8`(2 份 TalkBack 探针测试)→`5dde584`(探针适配 MD 夹具+收尾交接)；**工作树清零、暂存 0**，stray `0` 已删（留原证）。**TalkBack instrumentation 尝试 BLOCKED**：探针已适配（第001章→第01节）+androidTest APK `b8b32e74…` 构建完成，但设备出现并行变动（产品包被并行卸载+非本批包 `local.creationReadingAssistant.mobile` 在装=用户亲自操作），按 owner 冲突停写未执行 `am instrument`；下次唯一动作见 `r8-zcode-search-landing-fix-v1/evidence/talkback-instrumentation-blocked.md`。**冻结 APK 链**：现行候选=`0a81ac04…`（离线 412 门禁+真机首屏 7391 修复验证）；`5ef408d7…`=修复前基准（已保全）；`fc600e8c…`=R8 真机 A1-A6 历史基准。

## 2026-11-12 RDS-R8-ZCODE-SEARCH-LANDING-FIX-v1 收官（ZCode，首屏偏页修复 PASS）

首屏偏页根因=搜索索引 `charOffset` 基于**原始 Markdown 源文本**，阅读器 chapterStartOffsets/分页基于 **canonicalText**（去标记）；搜索来的 `legacyOffset` 未经 `MarkdownDocument.migrateLegacyOffset` 就被 `resolveChapteredPosition` 当 canonical 偏移消费（该方法原本只服务已存进度）。最小修复 3 文件（`landing-fix.patch` 2906B）：`SourceNavigationContract.migrateTargetOffset` 纯函数 + `ReaderProgressEffects.navigateChaptered` 的 MD 分支迁移 target + 新定向回归；TXT/EPUB/普通续读不变。修前红（rc=1）→修后绿 **32/32 rc=0**；新包 `0a81ac0400142d2d8d100c9bf9e1bd9a00166f91108f409ddab8e13cf5806e8e`（51,977,940B）装机 SHA 回读逐位一致；同一夹具搜 `7391`→**未翻页首屏 59.4%、第12节 `7391` 直接可见**（旧包为 75.3% 第13/14节需翻页）=**PASS**；返回保留、恢复 EXACT MATCH（历史 ZIP 3555f331… 保留）。旧包 `5ef408d7…` 构建前已保全（逐位一致）作修复前基准；`fc600e8c…` 保持 R8 A1-A6 基准。**现行候选=`0a81ac04…`**。源码改动留工作树，未 Git 暂存/提交。报告：`E:/AgentWork/CRA/rds-v1-real-device-smoke/root-checks/r8-zcode-search-landing-fix-v1/`。

## 2026-11-12 RDS-R8-ZCODE-SEARCH-FIRST-LANDING-v1 收官（ZCode，首屏落点 FAIL）

真机 c49ac6cf 装 `5ef408d7…` 装机 SHA 逐位一致。既有 40 段同构夹具无唯一可输入远页词，按合同新建 `fixture-first-landing.md`（3575B/`75de68ac…`，唯一词 `7391` 仅出现 1 次于第12节约第941字）；初始页 0.0% 证不可见；搜索框逐位=`7391`，正文=1「第941字附近」。**点击后未翻页未滚动的首屏=75.3%（第13/14节），不含 `7391` 与第12段；一次左翻页 59.4% 才见目标段落 → 业务 FAIL（首屏偏页）**，返回查询/结果仍保留。只读定位：941 与夹具位置自洽，偏差在 `ReaderProgressEffects.targetPercent`/`ChapterPaginator` 的"偏移→百分比→分页首屏"映射；缺 route legacyOffset / totalChars / absoluteOffset 三个观测值，建议加 instrumentation 核五值链（交 Root，未改源码）。恢复 EXACT MATCH（历史 ZIP 3555f331… 保留）。**新增登记=`5ef408d7…` 首屏精确定位存在偏页缺陷**；既有远页可翻页到达/查询84/日志/A1-A6 证据不变。报告：`E:/AgentWork/CRA/rds-v1-real-device-smoke/root-checks/r8-zcode-search-first-landing-v1/`。

## 2026-11-12 RDS-R8-ZCODE-SEARCH-FAR-PAGE-v1 收官（ZCode，真机远页搜索定位）

真机 c49ac6cf 空闲门成立，装 `5ef408d7…` 装机 SHA 回读逐位一致；沿用既有隔离测试 MD（历史 ZIP 按精确路径保留未动）。普通打开 0.0% 证 `84v1-MD-20` 不在初始页；查询 `20`（`input text` 一次送入、提交前 dump 逐位核 `'20'` 正确；长串 `84v1-MD-20` 被 WeType 改写为首页命中已按合同弃用）→ 正文=1 "第 2268 字附近" → 点击落 50.1%（远页）→ 一次左翻页 46.3% **第20节 `【 84v1-MD-20】` 完整可见** → Back 回搜索页 `20`/结果卡保留=业务 **PASS_WITH_NOTE**（目标标记需一次翻页可见，不夸大为点击即见）。恢复逐键 EXACT MATCH（历史 ZIP sha256sum=3555f331… 保留、前台回桌面）。`5ef408d7…` 新增"真机远页搜索定位"已验范围；全标记串注入受 WeType 限制（设备输入边界），更严口径需用户手工输入该一个查询。报告：`E:/AgentWork/CRA/rds-v1-real-device-smoke/root-checks/r8-zcode-search-far-page-v1/`。

## 2026-11-12 RDS-R8-ZCODE-LOCAL-COMMIT-v1 收官（ZCode，本地提交）

按用户精确授权完成**一次本地提交**：`dbe2bc08e4f5676ae671569a36bf0f155c24ef72`（父 `cb18a7b79ab9ec54fe2d159750ded1784dc3b5fc`，标题 `feat(android): integrate validated reader and data fixes`）。提交树恰好 45 个授权 Android 文件（A=19/B=9/C=6/E=11，排除 2 份 TalkBack 探针），45 项 SHA-256 与审查清单逐项一致，`git diff --cached --check` rc=0、暂存 blob 内容 45/45 无漂移（CRLF 按 Git 属性规则解释）。提交后核验：父提交正确、暂存区=0、工作树剩 26 项（D 组 23 文档+2 探针+根 `0`）且内容与开工指纹 0 变化（含 Root 发现的 current.md 指纹变化，留树对账）。冻结 APK 绑定不变：`5ef408d7…` 现行候选、`fc600e8c…` R8 真机 A1-A6 基准；远页定位/TalkBack 遗留沿用。**未 push/发布/构建/设备/资产清理**；本交接更新留在工作树，未纳入提交。报告：`E:/AgentWork/CRA/rds-v1-real-device-smoke/root-checks/r8-zcode-local-commit-v1/`。

## 2026-11-12 RDS-R8-ZCODE-COMMIT-REVIEW-v1 收官（ZCode，只读提交审查）

只读核定提交范围：HEAD=`cb18a7b`，暂存 0，工作树 71 项（旧 67 项草案 +4 份路径迁移文档：`antigravity-next-prompt-v2/antigravity-start-prompt/continuation-playbook/next-codex`，纯 `E:/creation-reading-assistant/...`→`E:/AgentWork/CRA/...` 改写，消失 0、真状态变化 0；旧清单无 SHA 仅存在性/状态可比）。**两处修正旧草案**：① 全树实查 D `app/src`(1038) 与 `5ef408d7…` 候选源(1038) `content_diff=0` → E 组（UIX/书架/分页 5 生产文件）**必含**，排除即缺现行候选生产差量；② 逐 hunk 核 ReaderViewModel/Models/Route/Args/SheetHost 全属 A 的 InspirationSaveOutcome 信号链、符号闭包仅 A → 组1/2 可独立提交。推荐顺序=1 A阅读附页（排 2 个 TalkBack 探针）→2 B备份灵感→3 C搜索→4 E既有交互→5a 未跟踪文档→5b current.md+4 路径迁移文档（最后）；`git diff --check` rc=0、符号闭包互不相交、18 份文档无真凭证/PII（2 份本机路径待用户确认）；**所有中间提交编译状态=待验证**（本轮禁构建）。可批准范围=仅本地 commit，须 Codex Root 核定+用户批准；本轮未暂存/提交/改源码/动设备。交付：`E:/AgentWork/CRA/rds-v1-real-device-smoke/root-checks/r8-zcode-commit-review-v1/`（COMMIT-PLAN.md/REPORT.md/ready.json/指纹清单）。

## 2026-11-12 RDS-R8-ZCODE-SEARCH-PHONE-HANDOFF-v1 收官（ZCode，真机补测+离线交接草案）

真机 c49ac6cf 14:18 三次采样桌面空闲→开工门成立；装 `5ef408d7…` 装机 SHA 回读逐位一致；BAD_ZIP_1 种子中性 MD，先证初始页无远页标记→搜 `84`→正文命中→点击→reader 落点经正文锚点 `【84v1-MD-01】` 实核→Back 查询/结果保留=A PASS；B 端到端（内部登记 NOT_VERIFIED）；C=时间窗 14:29:13→14:32:29 CST logcat（app pid 10318/7192 行+全 buffer 171114 行，**全程未执行 `logcat -c`**）`CRA_SEARCH_DIAG` 命中 0=PASS。远页独特查询 84v1-MD-20 因真机 WeType 注入改串=NOT_RUN（设备输入边界，已登记）。恢复逐键一致；收尾 rm 误删基线历史散份 cra-backup-missing-state.zip 后已按 SHA 同源件重推复原（3555f331… 逐位一致），全过程在 phone-restore.log 补记。**`5ef408d7…` 新增登记=真机搜索补测通过**；`fc600e8c…` 保持 R8 真机 A1-A6 历史基准。**离线提交切分草案**（`COMMIT-SPLIT-DRAFT.md`，只读）：67 项 WIP 分 5 组，组1阅读附页/组2备份灵感共享 ReaderViewModel/Models hunk 须同提交，组3搜索、组4文档、组5其他 WIP 建议不纳入；暂存/提交/推送等仍待 Codex Root 新授权。报告：`E:/AgentWork/CRA/rds-v1-real-device-smoke/root-checks/r8-zcode-search-phone-handoff-v1/`。

## 2026-11-12 RDS-R8-ZCODE-SEARCH-DEVICE-v1 收官（ZCode，MuMu 模拟器验收）

用户授权切换设备（真机 c49ac6cf 被连续占用，HyperOS 装机弹窗被前台用户活动顶掉→`INSTALL_FAILED_USER_RESTRICTED`）。MuMu instance 1（serial `127.0.0.1:16416`，control -v 1 launch 后 start_finished）装 `5ef408d7…`（装机 SHA 回读逐位一致）；BAD_ZIP_1 种子中性 MD 夹具→搜 `84`→正文命中（第54字附近）→点击→reader 落点经**正文锚点 `【84v1-MD-01】`**实核→Back 回搜索页查询/结果保留=A PASS；B=端到端行为 PASS（内部请求登记 NOT_VERIFIED 如实限定）；C=时间窗 05:01:37→05:04:57Z logcat（app pid 5108/1270 行+全 buffer 4124 行，未清全局）`CRA_SEARCH_DIAG` 命中 0=PASS。恢复逐键一致（包缺席/boot_id/8设置/Download190/前台）。**`5ef408d7…` 登记为已通过本轮搜索模拟器补测的现行候选**；`fc600e8c…` 继续为 R8 真机 A1-A6 历史基准。真机同流程 NOT_RUN；MuMu 收尾保持运行（开工为关闭态，未擅动共享资源）。报告：`E:/AgentWork/CRA/rds-v1-real-device-smoke/root-checks/r8-zcode-search-device-v1/`。

## 2026-11-12 RDS-R8-ZCODE-DIAG-CLEAN-FREEZE-v1 收官（ZCode，离线）

R6 裁决执行完毕：产品源码 9 条 `CRA_SEARCH_DIAG` 临时日志（含查询词/bookId/route）已全部移除（SearchIndexRepository×2/SearchScreen×5+括号恢复/ReaderRoute×2），生产源零残留；`SearchHitRankerMetadataFilterTest.kt:12` 历史原证注释按裁决保留。清理后 D→E 逐字节同步（全树 content_diff=0 绑定不变），定向 JVM **191/191 rc=0**（搜索族 184+接线 7）+ `assembleDebug` rc=0 → **新候选 `5ef408d7…`/53,957,267B 冻结**（离线验证通过，待设备补测：搜索点击定位返回主项+临时查阅链+logcat 无诊断负例）。**已验 `fc600e8c…` 二进制已于构建前保全**（`evidence/preserved-fc600e8c-app-debug.apk` 逐位一致），其真机 A1-A6 证据按保全路径继续有效。D 树 WIP 67 项（含本轮 3 文件清理）未 commit。报告：`E:/AgentWork/CRA/rds-v1-real-device-smoke/root-checks/r8-zcode-diag-clean-freeze-v1/`。

## 2026-11-12 RDS-R8-ZCODE-SOURCE-CONVERGENCE-v1 收官（ZCode，离线）

R8 后 D/E 源码收敛完成：10 文件清单+ReaderRoute 共 11 文件按 E 已验内容回补 D（逐文件判定 APPLIED，before/after/E 三方 SHA 台账与 36,219B 最小 patch 在 `E:/AgentWork/CRA/rds-v1-real-device-smoke/root-checks/r8-zcode-source-convergence-v1/`）。**绑定验证：D `android/app/src`(1040) vs 已验候选 `fc600e8c…` 源树(1038) content_diff=0、E 独有=0**——唯二 D 独有=2 个 TalkBack 探针测试（androidTest-only 不进产品 APK）；R8 的 JVM/构建/真机证据经字节等价绑定适用 D 回补结果。CRA_SEARCH_DIAG 判定=临时诊断埋点（非产品需求），随字节绑定保留（D 树 SearchIndexRepository 本就保留同族埋点且为新测试注明的根因定位原证）；统一去留留待 Root 清理批。`git diff --check` rc=0；D WIP 57→67（=回补集）；零构建零设备；冻结 APK 原位实算一致未动。

## 2026-11-12 RDS-R8-ZCODE-OVERNIGHT-v1 收官（ZCode 唯一执行者+接任协调者）

共同候选已核定并冻结：`fc600e8c…`/53,957,267B（debug，E candidate 工作区构建，装机 SHA 回读逐位一致），**取代 fa5ee9db 为现行唯一候选**（fa5ee9db 降前代只按 SHA 引用；其二进制无保全副本=G-R8-1 如实登记）。内容绑定=fa5ee9db 全部源 + reader-pages 20 文件集 + UIX 四文件 + ReaderRoute 三方合并（D↔E 29 文件分歧全分类，evidence/candidate-audit/ 含 before/after SHA 台账）。定向 JVM 191/191 rc=0。真机 A1-A6 全过（BAD_ZIP_1/2 独立空库导入三方一致、导出对 checker PASS rc=0、灵感保存落库、四入口开合、来源回跳），零 pm clear，每次安装即回读 SHA。恢复 PASS 带缺口 R8-G2（基线服务串含系统级坏引用 `mipay…Companion` 粘连，重绑后被系统归一化剪除，坏组件不可绑定亦不可字节还原；GKD 正常条目已回写一致）。报告/合同/ready：`E:/AgentWork/CRA/rds-v1-real-device-smoke/root-checks/r8-zcode-overnight-v1/`。待 Root：登记 fc600e8c；D↔E 收敛时按 after-sha256.txt 台账回补 E 侧 11-06 交互修复 10 文件（D 树现缺）。

## 2026-11-10 READER-PAGES-TALKBACK-v1 Root裁决

本轮执行结束，专项保持 PARTIAL。朗读日志支持 TalkBack 基础功能。目录、外观、设置、灵感的界面动作结果按普通交互证据保留，四入口逐控件聚焦、朗读及经 TalkBack 激活的完整链路待验。停止重复脚本试跑。原冻结产品包及真机主流程验收结论继续有效。

本地产品包复算为 `98b629ea…`。本轮报告声称装机哈希一致，但所引 install-identity.log 无哈希行。恢复快照可比的动画、字体与服务串相符，收尾快照首项为 1，开工旋转首项为 0，需核对命令顺序及实际值。权限、卸载与 Download 计数在收尾快照缺同口径原始输出，暂按报告自述保留，不接受逐字一致。优先补现有原证并核清旋转，免重装和重测。报告见 `E:/AgentWork/CRA/reader-pages-talkback-v1/REPORT.md`。

## 2026-11-10 READER-PAGES-DEVICE-v1 Root裁决

关闭本批真机主流程验收。产品APK实算及装机回读均为 `98b629ea…`，接受真实键盘与草稿保护、实际横屏和四入口短回归。TalkBack保持PARTIAL，四入口逐节点聚焦、朗读、激活矩阵另列待验。报告与原证在 `E:/AgentWork/CRA/reader-pages-device-v1/`。

交付校正：开工测试APK记录为 `4b0177c9…`，当前默认路径实算为 `52abd4af…`，有assembleDebugAndroidTest日志，故“未重建”只能用于已核对的产品APK身份，不接受测试包全程冻结表述。前后快照共同记录的旋转、动画、字体和服务串值一致，字段覆盖不同，恢复整体以交付报告为限。该交付差异不推翻使用冻结产品包完成的三项交互结果。旧MuMu组件证据仍绑定原测试包。

## 2026-11-10 READER-PAGES-v1 Root验收关闭（MuMu已验范围）

Root复算最终APK对及20个源码SHA，核对110份JVM XML（984项全过）、最终构建日志和6项MuMu组件测试输出。P1保存锁定通过，接受长标题展开和组件动效补验。关闭本批实施及已验范围，详见[最终复核](../coordination/2026-11-09-reader-pages-v1-root-review.md#2026-11-10-最终复核)。真机、真实IME推顶、实际横屏、TalkBack保持NOT_RUN。首轮font_scale存储值差异保留，修正轮以自身基线核对恢复。以下为历史交付记录。

## 2026-11-09 READER-PAGES-v1 集中修正交付记录

[Root复核](../coordination/2026-11-09-reader-pages-v1-root-review.md) 的P1必修项已落实：灵感保存期间**全页编辑锁定**（标题/正文readOnly、标签/分类/新建锁定、放弃确认双重守卫、保存onClick在途复查）落地于 `ReaderInspirationSheet.kt`，并以可控延迟（frozen outcome，无sleep）androidTest 模拟器实测 `ReaderInspirationPageSaveLockTest` 3/3 OK（锁定键入无效/连点单次派发/返回不开放弃确认且显示"正在保存"/失败解锁留稿/重试同id/成功回调序）。有限补验：TOC长标题长按展开 1/1 OK（101字，不跳章）；容器180/140ms动效以 mainClock 停表+captureToImage 逐帧验证 2/2 OK（入场90ms中间帧、可取消、退场中间帧、减动画0ms即终态），替代易竞态的screenrecord。**NOT_RUN如实保留**：真机（用户占用；connected测试曾触发HyperOS拒装未安装成功，已改手动仅对模拟器instrument并记录）、IME推顶视觉（MuMu IME窗口零高度）、横屏真旋转（MuMu竖屏锁定）、TalkBack、防重复设备观察。最终APK debug `98b629ea…`/androidTest `4b0177c9…`；JVM定向 984/984（最终树重跑）；lint 0e/9w。报告 `E:/AgentWork/CRA/reader-pages-v1/REPORT.md`（§3=P1修复，§7=口径更正：已实现/已运行/待验三列制；baseline证据补原始命令输出级对账；完整64位SHA×20；§9=未验项）。模拟器基线恢复且关机；未commit/push；改动清单见 ready.json。等待Root按实际已验范围复核关闭。

## 2026-11-09 READER-PAGES-v1 首轮Root复核记录（已由上方裁决替代）

Root已核四入口源码、APK对、登记SHA前缀、110份定向JVM XML合计984项全绿及3张布局截图。实现与已有模拟器证据有限接受，尚不全面关闭：灵感保存中仍可继续编辑/放弃，旧Saved无条件清稿关页存在后续输入丢失窗口，必须按 [Root复核及唯一集中修正](../coordination/2026-11-09-reader-pages-v1-root-review.md) 锁定在途修改/返回并补延迟行为测试。IME、横屏、连续动效与部分恢复证据仍有限。其他页面不重新设计、不全量重跑；原实现者ZCode沿原合同完成这一次集中修正。以下交付自报按该裁决读取。

TOC/SETTINGS/THEME/INSPIRATION 四入口已按 [实施合同](../coordination/2026-11-08-reader-pages-v1-implementation.md) 改造为 ReaderPageDialog 全屏附页并真实接线；灵感保存完成信号（Saved/Failed+seq）经 VM→ReaderScreenInputs→附页闭环。最终APK `7849269f…`(debug)/`c0c3683a…`(androidTest)；JVM定向 984/984；lint 0e/9w。**模拟器验收**（MuMu instance 1）：四入口交互/220章目录/搜索定位/位置跨进程保存/草稿确认/保存落库闭环/夜读纸/320dp/1.5字阶/减动画全过；真机因用户占用未动（零改动）；未验项（IME推顶视觉、防重复观察、横屏真旋转、TalkBack、androidTest运行）见 REPORT §6。实现期修复4缺陷（预览空文字/分类丢失/容器漏底/长按跳章）+1个落地谱系预存在的 androidTest 编译错误机械修复（SaFTree 3行）。报告 `E:/AgentWork/CRA/reader-pages-v1/REPORT.md`（含 ready.json/patch/SHA/截图/恢复原证）。模拟器基线已逐项恢复并关闭；工作树未 commit/push；改动清单见 ready.json，与既有WIP无文件交集。下批如需真机复验，从该报告§6未验项进入。

## 2026-11-08 阅读附页实施（已准备，用户转交ZCode）

用户已授权按Root确定的具体设计修改代码。新任务 [READER-PAGES-v1完整实施合同](../coordination/2026-11-08-reader-pages-v1-implementation.md) 和 [ZCode读取执行词](zcode-reader-pages-v1-prompt.md) 已准备，共7项顺序工作，覆盖TOC、THEME、SETTINGS、INSPIRATION四入口。方案为索引附页、真实正文预览调节页与摘录旁写页；单ZCode owner闭环，不自动发送、不恢复自动化。本次仅准备文档，尚未实施/构建/设备验收；UIX旧证据核清范围继续独立保留，不把新设计任务误认为旧缺陷返修。原工作树保护，构建/设备各单owner，输入以执行前实核为准。

## 2026-11-08 UIX 收尾校正（本范围最新）

UIX第七轮 `d9c5adcf…` / 52,812,949B 已由ZCode交真机节点复验。Root已解析两份原始XML，确认整理入口clickable和底栏五钮节点恢复、顶栏/scrubber描述存在；停止新增产品修改。当前裁决为 `NODE_RECOVERY_VERIFIED_BEHAVIOR_EVIDENCE_PARTIAL`，尚缺六控件实际激活及部分恢复原证链接，TalkBack未运行。按 [本次Root复核与唯一接续动作](../coordination/2026-11-08-uix-closure-root-review.md) 先复用现有证据，必要时仅补有限激活验证，不全量返工，不凭XML预设朗读通过。报告位于 `E:/AgentWork/CRA/uix-v1/closure-v1/REPORT.md`。原 [有界收尾合同](../coordination/2026-11-08-uix-bounded-closure.md) 保留。用户手动转交，手机仍单owner，自动化不恢复。

下文为11月4日起历史阶段口径，不能据其“工作清零”推断后续UIX或全产品已完成；具体历史结论仍按对应报告范围读取。

## 2026-11-04 阶段记录

**项目状态：两产品线全部标准质量门绿，可自主执行工作清零，剩余全部为用户决策项。**

- **Android（MuMu 口径）**：V0 八卡（AR01—08）全部真实闭合，总裁决 `V0_ACCEPTED_WITH_SCOPE_DEVICE`（裁决书 `E:/AgentWork/CRA/task209-navigation-coverage-v1/continuation-20261102/next-v2/ar08/REPORT-20261103-final.md`）；JVM 3808/3808、设备批 AR03/04/05 + NB03 图测试 + NB04 SAF 文件渠道全 PASS、166+R2 恢复全绿、lint 0 errors/7 warnings。
- **桌面**：设置重构+设置搜索 WIP（20 路径未提交）**功能已完整**（tsc×3 + vitest 981/981 + 契约 54/54 + electron-vite build 全绿，审计 `next-v2/nb14-desktop-wip-audit/REPORT.md`）；打包验收**全绿**（stage1 族 2/2 + stage3/4/4b 43/43 checks，经 NB16 维护：脚本原生支持 CREATION_PACKAGED_DIRECTORY 覆盖、stage4 app.close() 挂起已修；`next-v2/nb15-desktop-packaged-acceptance/` + `nb16-stage-scripts-maintenance/`）。
- **NB 线台账**：NB01 成因调查（无法归因，保护门守护）/ NB02 裁定不跑 / NB03 闭合 / NB04 闭合 / NB05 盘点 / NB06 文档 / NB07-09 闭合 / NB10 闭合 / NB11 BLOCKED_ENVIRONMENT（MuMu 树确认按钮被导航栏遮挡，测试源带诊断保留 candidate）/ NB12-13 闭合 / NB14 审计 / NB15 PARTIAL。报告全在 `E:/AgentWork/CRA/task209-navigation-coverage-v1/continuation-20261102/next-v2/`。

## Root 决策批（2026-11-04 已裁决，[决策文档](../coordination/2026-11-04-root-decision-batch.md)）

原待决事项及盘点新识别事项已由 Root 全部裁决（用户 2026-11-04 委任决策），待 ZCode 按文档执行：

1. **RD-A 桌面提交**：授权 commit 20 桌面路径 + 桌面验收脚本（全门绿证据），不 push。
2. **RD-B 文档/配置提交**：授权提交文档重组成果与交接记忆，`.gitignore` 增补排除会话工件（`dump_secrets*.xml` 等只忽略不删除），不 push。
3. **RD-C Android 源码**：授权只读盘点 + 锚点核对（6be3b01b pin、D↔candidate diff 归类）；全过才条件提交，任一不符即停报告。
4. **RD-D NB02**：维持不跑（原红保留，208 覆盖同类边界）。
5. **RD-E SAF 渠道**：产品路径保留，仪器测试挂起；NB11 裁决降格为工作假设。
6. **RD-F 巡检自动化**：维持暂停；重建需单 ID + 新鲜 prompt 快照。
7. **RD-G 证据卫生**：授权离线小批修 NB04 字节数/措辞/重钉、NB11 降格披露、ar03 撤销注记（原证只增不改）。

执行顺序 RD-A → RD-B → RD-G → RD-C；报告落 `E:/AgentWork/CRA/coordination/root-decision-batch-20261104/REPORT.md`；全部 commit 仅本地，不 push。

**RD-C 分歧裁决（2026-11-05 补）**：RD-A/B/G 已执行（commit `3751b41` / `35ed1a5`），RD-C 锚点核验证伪了原合同假设——恢复成果唯一载体是 E candidate（only-in-E 123 文件 = 207/209/NB 线成果；D 树侧 89 个差异为 10 月初独立演进，验收归属无法建立）。裁决取落地路线：[RD-C-LAND 合同](../coordination/2026-11-05-rdc-landing.md)——先 archive commit 保全 D 树分歧谱系，再整体落地 candidate 快照并以 treediff 零差异为硬门；恢复期"不回灌 D"隔离措施于收官解除（理由见合同）。落地后 D 仓库为后续源码主载体，E candidate 转为冻结已接受基线（只读参照）。

## 设备与权限（仍然有效）

- MuMu instance 1 启动/验收/各批 166+R2 恢复已获用户同意（[权限纠正](../coordination/2026-11-02-mumu-acceptance-authorization-clarification.md)）；serial/boot 由执行者 fresh 探测，禁止转向实体手机，一切设备证据只能称模拟器。
- `android-zcode` 保持 PAUSED；接任会话连续执行不是恢复该自动化。
- 无 Git 暂存提交 push PR 发布、既有资产删移、D 产品回灌、pm clear/盲恢复、正式库/私库 session DB 或记忆写入许可。WIP/DB-WAL-SHM/原红/唯一证据/冻结 APK/恢复资产保留。
- 接任 Root 由用户委任，可承担普通技术决定、最小因果修复和验收；公共 candidate/Gradle/设备各单 owner，Gradle 持 `E:/AgentWork/CRA/locks/android-gradle-exclusive.lock` 串行。
- 新业务设备批前置：单 owner、fresh 身份链、原 APK/data/userState/八设置/保护根/创建物基线及 166+R2 恢复准备；保护查询后最终 identity；已成功卸载/恢复不为补证重做。
- 判伪纪律：**0 项 instrumentation 不构成通过证据**；业务真伪三件套=stdout 时长量级对照基线、logcat start/finished instr 链、testApk SHA 三方（磁盘/冻结 manifest/capture）互核。

## 产品与实际候选（2026-11-04 实核）

D 仓库：`D:/develop/Code/Codex/creation-reading-assistant-recovery-20260919`；唯一 candidate：`E:/AgentWork/CRA/task59-common-candidate-v1/candidate/android`。不 D 回灌、不复制工程/缓存。

| 输入 | 2026-11-04 现态 |
|---|---|
| 产品 APK | 53899310B / `c396ebe1…`（未变，接受输入） |
| androidTest APK | NB04 当批 `1555fc7f… / 10760068B`（SAF/SAFTREE 测试；其二进制未被单独保全，身份由 summary/capture/evidence-manifest 钉扎）；更早的 95e734bc 冻结包二进制保全于 `next-v2/nb04-saf-journey/artifacts/`；盘上现态已被 NB11 系列重建覆盖为 `6e2c171b… / 10768258B` |
| 打包产物 | `release-beta/win-unpacked`（NB15 构建绿）+ `release-beta-stage4-final`（NB15 构建绿） |
| 证书/runner | SHA256 `05249d78…`，`CraIsolatedTestRunner` / `com.creationreadingassistant` |

## 已有业务与恢复边界（复用，不重跑）

| 任务/批次 | 可沿用结论 |
|---|---|
| 205-v1 | 普通退出 shelf-screen 超时原 FAIL 保留（T165V1-20261030-150607）；新输入不复现不抹旧红 |
| 206-v1 | recover-only 有限关闭；原 query rc/userState/绑定等限制保留 |
| 207-r3/r4 | 源码/编译及当批 APK 对限定接受，不扩全产品 |
| 208 (162905) | 普通退出/书架重开单方法 OK；接受范围见 task208 REPORT |
| 209 A/B/D | A 图 7 项 PASS（NB03 已在冻结包复验）、B/C1 生产组合（NB03 批B 复验） |
| E151729/P5 165519/I190927/C-R2 210503 | 各自既有接受范围保留 |
| AR03/04/05 (11-03) | T165V1-20261103-114643/120753/121822，全 PASS+UNINSTALLED |
| NB03 A/B (11-04) | T165V1-20261103-234022（A：PASS+recover-only 有限收口）/235956（B：全绿） |
| NB04 (11-04) | T165V1-20261104-015548，SAF 文件渠道 PASS+UNINSTALLED |
| R2 合同 | gen10106-v1（inventory 8b2e3317、test 包 0d18、保护 71/820）；恢复优先于新 mutation |

业务 scope 根：`E:/AgentWork/CRA/task192-reader-internal-lifo-host-offline-v1/attempt-<runId>/`；NB 交付根：`E:/AgentWork/CRA/task209-navigation-coverage-v1/continuation-20261102/next-v2/`。runtime：`E:/AgentWork/CRA/task209-navigation-coverage-v1/inventory-repair-r2/scripts/`（preflight_209_r2 → prepare_inputs_209_r2 --batch X → run_batch 驱动；AR02 frozen manifest 门覆盖 EP/C/I）。

## 阶段史（详情按需取，不整份加载）

- 11-02 Antigravity AG-NEXT 八卡离线交付 + Root 复核（[队列](../coordination/2026-11-02-antigravity-next-queue-v2.md)、[复核](../coordination/2026-11-02-antigravity-stage-delivery-review.md)）。
- 11-03 夜间 ZCode 接任：保护基线 18 文件恢复 → AR03/04/05 真实闭合 → AR08 终裁 V0_ACCEPTED_WITH_SCOPE_DEVICE。
- 11-04 Wave1-5：NB01-15（见头部 NB 线台账）。其 AR03-05 设备报告被审计证伪：`E:/AgentWork/CRA/task209-navigation-coverage-v1/continuation-20261102/next-v2/ar03/RETRACTION-20261103.md`（原文件保全）。
- 11-04 收官+全面复审+NB16 打包验收闭合后，Root 决策批七项裁决落盘（[RD-20261104](../coordination/2026-11-04-root-decision-batch.md)），ZCode 执行 RD-A/B/G/C。
- 11-05 RD-C 分歧裁决：恢复成果载体实测为 E candidate；授权 archive+落地两段式入库（[RD-C-LAND](../coordination/2026-11-05-rdc-landing.md)）。

## 执行与状态入口

- state：`E:/AgentWork/CRA/coordination/android-zcode-luna-state.json`（写前核指纹合并；readySHA 实算）。
- 夜间巡检：`E:/AgentWork/CRA/coordination/zcode-night-watch-20261103/`（heartbeat+progress+各 run 日志）。
- [接手说明](next-codex.md)、[通用手册](continuation-playbook.md)、[单任务模板](next-agent-start-template.md)、[下一批规划](../coordination/2026-11-03-next-batch-proposal.md)。旧路径查 [document-map](../document-map.csv)。
