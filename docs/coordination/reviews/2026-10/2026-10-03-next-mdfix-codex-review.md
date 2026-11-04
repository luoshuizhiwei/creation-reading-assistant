# Markdown回源修复核查与32路径集成准备

- 任务编号/版本：CRA-MDFIX-REVIEW-v1；2026-10-03。
- 输入指纹：HEAD e39a78da9c62f02614207cffb36969a32492eaea，暂存区空；integration-ready-v1 manifest SHA a0240a395c20251f480378ed07ba901c0c0061c79cd98ea4b43985b229ed491d。
- 所有权与改动：Root核查E代码/原始工件，准备32路径精确源码payload及文档；设备只补核屏幕超时、删除本批脚本生成的精确临时文件。未回灌产品、未构建、未操作Git。
- 环境：Windows/Python3.13；MuMu serial127.0.0.1:16416。没有操作另一serial或实体手机。
- 执行与结果：源码树/文件SHA、XML、设备JSON与UI层级、APK、恢复账核查；独立只读审查实际SE4→SE3状态移交。
- 原始证据：[Root audit](<E:/creation-reading-assistant/mdfix-review-20261003/audit.json>)；[执行报告](../../dispatches/2026-10/2026-10-03-next-md-annotation-return-fix-v1.md)；原始文件均保留。
- 未覆盖项：Markdown跨章设备、note typed目标、无定位失败提示的设备可见性；旧APK逐批安装读回仅粗粒度文字关联，本轮不扩大为完整原始安装证明。
- 恢复与副作用：53文件/3原APK/7项设置原始前后账一致；第8项屏幕超时未记入本批基线，Root现场读取60000，与上一批基线相同，不伪称本批有完整8项前后账。
- 交付状态与下一步：单章Markdown非起点回源限定通过；32路径源码包准备完成，下一合流构建及受影响MuMu验证，不应用D或提交。

## Standards

输入882文件，候选884文件：仅改ReaderProgressEffects，新增生产ReaderAnnotationNavigation及测试ReaderAnnotationNavigationTest。与授权三路径一致。实算三个交付SHA与after.txt一致。

纯计划与动作函数由生产effect实际调用，非独立镜像。原locator移交pendingSourceLocatorJsonState，再由SE3监听/定位；SE4清自身pending不清SE3请求。跨章首次保留source请求，加载后再应用focus，源码路径成立；不能因此称设备跨章通过。locator缺失时进度兜底沿用直接Markdown跳转的保守语义，只有章节标题的旧记录回退不在本轮闭环。

原红annotation10/7，原progress7/0；绿10+7=17/17，0failure/error/skipped。红使用等价旧行为占位计划，是测试构造，不冒充在未修改原完整effect上直接红。实际旧设备失败由上一批报告补足。

## Spec

设备JSON实际高亮offset1247，冷读保留；UI层级seq3-after.xml包含目标段落55及68.2%，退出后DB记录offset1036、65.075%，符合渲染块回写语义，不把它写成精确字符offset1247回写。原日志mdfix_20为OK(1 test)。这支持单章真实UI非起点回源修复。报告引用的seq3-after截图在screens目录未找到，不伪称Root已经检查该张截图；该步以UI层级与DB证据限定采纳。

宿主APK实算47ff9aa5…；测试B1 bb6d80e0…、B2 62514c32…、B3 7bc43c99…三件真实保留。sha256.txt只列B1，批次关联为省略SHA文字；不将其当新联合候选可执行冻结协议。下一批须保留完整安装读回及精确机器索引。

恢复报告“没有公共临时文件”错误：mdfix_ui.py写入/sdcard/mdfix-window.xml；Root现场发现该文件，SHA d654756595442a473007d787f9662d766dddbb788bcc0cc767e77818c8e98360。仅删除该精确任务文件，之后ls确认不存在。Root没调整任何系统设置；night no沿用本批与前批后账，误设置yes的历史记录保留。

## 规划与下一步

Root已准备[32路径manifest与源码payload](<E:/creation-reading-assistant/mdfix-review-20261003/payload32-preparation/manifest.json>)：25基础＋4回滚＋3Markdown，互不重叠，32/32源SHA核定。只是源码包；尚无对应联合APK/设备通过，也未写共享产品树。单章MD修复闭环成立，下一进入集成，不继续重复调查。

下一单owner：CRA-INTEGRATION-BUILD-ACCEPT-v2，复制冻结基础到新E候选，精确覆盖32路径，先定向合流回归再构建；隔离新UID MuMu仅验证合流后的回滚保护、Markdown非起点及跨章定位接缝。复用其余N1/G1/Profile/同步旧证据，不全量设备重跑。设备恢复必须明确8设置、临时文件、APK、DB/文件、映射账。失败留证停止，不越界修其他功能。

进度：本批JVM17/17=100%，单章MD回源1/1=100%；三格式非起点回源在各自候选累计3/3，不等于32路径联合验收通过。联合候选门禁待执行；全产品分母未定。
