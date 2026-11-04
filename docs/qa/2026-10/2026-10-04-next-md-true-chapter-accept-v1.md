# 真 Markdown 跨章：Root 接手、修复与限定 MuMu 验收

## 任务编号/版本

原任务 CRA-MD-TRUE-CHAPTER-ACCEPT-v1；2026-10-04 用户要求接手未完成工作并继续。Root 接手形成 MD-RENDER-PROGRESS-FIX-v1 六路径独立候选。原固定输入发现崩溃，不能标它通过；通过结论只属于下列 Root 候选。

## 输入指纹

- 恢复仓库 HEAD `e39a78da9c62f02614207cffb36969a32492eaea`，暂存区空；本轮不改 D 产品源码、不暂存/提交/push。
- 固定产品输入 [原任务 android](<E:/creation-reading-assistant/next-round-md-true-chapter-accept-v1/android>) 已恢复原字节；Root 修复独立保存于 [六路径候选](<E:/creation-reading-assistant/next-round-md-true-chapter-accept-v1/root-md-render-carrier-fix/android>)。
- [精确六路径 before/after SHA](<E:/creation-reading-assistant/next-round-md-true-chapter-accept-v1/root-evidence/source-manifest.json>) 文件 SHA `77bb49b17c5ddead10c75f35585398c68209662723d76c47b301d35eae6149b0`；六路径与实际编译的 device/android 对应源码逐字一致。
- 最终宿主 `b43f441788cc735f42e915bfb992a308cee869caf483628adc675da4ee8a7670`；测试 `21489a7c1c6ea97db0c27c92dc305ce5b2842896c765f056bd8e50c7b7768a94`。冻结见 [最终 APK 对](<E:/creation-reading-assistant/next-round-md-true-chapter-accept-v1/root-evidence/final5-freeze>)，安装读回逐项等 SHA。

## 所有权与改动

Root 为唯一设备 owner。接手时无本任务 Python/adb instrumentation 客户端运行；旧 execution.json 仍自报运行，不用它推断实际占用。原源码/旧 APK 可回退副本位于 root-evidence/before-source 与 original-apks；中间版本结果保留 first/second/third-fix-results，没有用最终绿覆盖原始失败日志。

六路径：

- `app/src/test/java/com/creationreadingassistant/ui/screen/reader/MarkdownVisibleSourceOffsetTest.kt`
- `app/src/test/java/com/creationreadingassistant/feature/reader/doc/MarkdownDocumentTest.kt`
- `app/src/main/java/com/creationreadingassistant/ui/screen/reader/ReaderContentHostMarkdownBranch.kt`
- `app/src/main/java/com/creationreadingassistant/ui/screen/reader/ReaderProgressActions.kt`
- `app/src/main/java/com/creationreadingassistant/ui/screen/reader/ReaderProgressEffects.kt`
- `app/src/main/java/com/creationreadingassistant/feature/reader/doc/MarkdownDocument.kt`

修复：整本解析块保留 global canonical range，却传 chapter-local 文本给渲染，导致第2章 substring 越界；现在为渲染与高亮提供同坐标 carrier，`text(chapterIndex)` 保持章节语义。另补 Markdown 初始化恢复门控、实际列表退出保存与500ms防抖，TXT列表不再写 Markdown 的0偏移；保留 temporary/initial/focus/Legado 保存门控与普通阅读 source 上报。Legado真实移动观察与首次锚定采用同一书内章/list身份；anchorAt不覆盖既有锚点。

## 环境

MuMu，显式 serial `127.0.0.1:16416`；`emulator-5556`为同一VM别名。1080×1920竖屏、density280；截图尺寸/实际内容与 XML 共同核对。独立包 `.qahostmdchapv1`，生产 App 透传；不是实体手机或 MIUI 验收。JDK17、Gradle离线缓存和工件全在E盘。

## 执行与结果

- 原输入真实 TOC 第2章：FAIL，`StringIndexOutOfBoundsException length=353 index=382`，ReaderMarkdownRender.kt:44。原始 crash buffer保留 [日志](<E:/creation-reading-assistant/next-round-md-true-chapter-accept-v1/root-evidence/crash-log.txt>)。设备时钟与宿主不同，不把buffer日期当宿主发生时间。
- 中间版本：界面回到第2章但落盘offset0；真实失败原日志仍在 device-runs。选色确认之前的早断言失败属于操作顺序错误，单记，不当已保存断言。
- 最终构建成功，定向 JVM **90/90**（0失败/错误），含 Markdown文档/展平/渲染/持久高亮/可见位置、source合同与Legado相关回归。不是全量门禁。
- 最终 instrumentation **7/7**：00夹具、01生产章节表、10真实保存、11强停冷读、20离章、30回源落盘、90终态。行为验收分母为**5/5**（01/10/11/20/30），不把夹具/终态算额外功能。
- 生产章节表4个真实H1章：起点0/377/730/1107。目标第2章index1，marker章内228、全局605；locator的ci=0是MD/TXT生产forPlain约定，真实章身份以chapter_title与全局canonical区间证明，不强造ci>0。
- 最终真实UI选marker→高亮→确认yellow；冷读PID变化且同locator605。真实TOC切第3章，暂停保存offset730/index2。真实书内笔记“跳转”返回第2章，正文marker与黄色高亮可见；暂停保存offset378/index1。再次强停启动后仍第2章且marker可见。
- offset378为实际视口顶端进度，marker605为已保存标注位置；短章接近单屏，LazyColumn尾部限制使marker不一定贴顶。只声明正确章节/可见标注及进度恢复，不声明逐字/像素定位或页码精度。

## 原始证据

- [最终构建原日志](<E:/creation-reading-assistant/next-round-md-true-chapter-accept-v1/logs/root-final-build.log>)；[固定JVM XML](<E:/creation-reading-assistant/next-round-md-true-chapter-accept-v1/root-evidence/final-jvm-xml>)。
- [最终JSON/截图](<E:/creation-reading-assistant/next-round-md-true-chapter-accept-v1/device-evidence>)；[原instrumentation日志](<E:/creation-reading-assistant/next-round-md-true-chapter-accept-v1/device-runs>)。
- [真实UI回源截图](<E:/creation-reading-assistant/next-round-md-true-chapter-accept-v1/screens/root-final5-return.png>)；同名XML在ui-runs；[冷启动回读](<E:/creation-reading-assistant/next-round-md-true-chapter-accept-v1/screens/root-final5-cold-reopen.png>)。
- [独立机械核验结果](<E:/creation-reading-assistant/next-round-md-true-chapter-accept-v1/root-evidence/verification.json>)；root_verify.py可复查。

## 未覆盖项

不含实体手机/MIUI、跨书/临时查阅新增入口、完整联合门禁或D永久集成。流式/表格有本轮JVM证据，但没有新设备场景；位置比例是视口近似，不是字符几何精度。Legado armed且首次启动同时携带外部source/annotation跳转时的“首次主动位置是否解除”合同边界未设备验证，保留为窄风险，不推成全导入流程通过。

## 恢复与副作用

Root接手前后：[before](<E:/creation-reading-assistant/next-round-md-true-chapter-accept-v1/root-evidence/before.json>) / [after](<E:/creation-reading-assistant/next-round-md-true-chapter-accept-v1/root-evidence/after.json>)。原qahost **53/53文件SHA相同**，原宿主/测试 **2/2 APK SHA与路径相同**，八系统项与forward/reverse相同。density280无override，夜间no；本轮不凭旧批次夜间yes恢复。

最终安装读回与APK冻结SHA一致；任务host/test均卸载Success，任务external目录及两条public XML不存在，见cleanup.json。未修改原宿主/测试APK、正式书库、真实手机；没有直接验证AndroidKeyStore零残留，不能如此声明。

前执行者开工前没有交付原宿主53文件SHA，本报告只证明Root接手区间前后一致；其更早区间不冒充本轮全链恢复证据。已记录的开工前包/八设置基线与最终现态一致。

## 交付状态与下一步

Root六路径候选限定验收完成；本任务行为 **100%（5/5）**，另有恢复核验。原任务固定输入保留FAIL，integration-build-accept-v2的旧APK不得继承这个绿。当前三项交付按限定范围核查 **67%（2/3：同步、Root MD；EPUB回滚返修）**，不是全产品完成率。

MuMu已释放。本轮停止点：报告与交接更新，无D产品写入/Git变更。下一保持阅读/数据安全主线，将六路径修复与已冻结MD/WIP保全合流，核EPUB返修后只跑受影响联合门禁；不能直接把未测试的旧ReaderProgressEffects dry-merge覆盖本轮修复。全产品统一分母尚未确定。
