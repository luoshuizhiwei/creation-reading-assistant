# 联合冻结v2核查与下一并行边界

## 任务编号/版本

CRA-TXT-D2-J1-JOINT-FREEZE-v2。口头完成后Root原始证据核验，UI/恢复两个reviewer并行只读审核。

## 输入指纹

Root证据 E:/creation-reading-assistant/joint-freeze-v2-codex-review-20261008/fingerprints.json。
源码992/992、验收35/35、根3/3指纹匹配；证据清单实际41/41匹配，报告写40是计数差异，不是缺失。
源码manifest 9ec78f87760ca48a63916be60d38782e1b3e3223c1778310229e4211babd9ee4；acceptance manifest 7841b68652edf1084e42a6ba1694d961f55d0752553db7259d98e5ff3c908ec5。
宿主SHA440514acd90de7bf38edb744851eefcf345f2c3b9d4710984358df2c142552ae，测试SHA bd7ff57d46a6a21294102c7b71474aa6757b4e522c2c2c759de6a9063958ebc5；大小、包身份匹配。aapt实际test target/runner与冻结一致，apksigner双APK证书同05249d786e3d3d117bb5bd7b1f83512a0c9b8f1ee4e502112c4b2d9ccbd7b9a1。
D HEAD e39a78da9c62f02614207cffb36969a32492eaea，暂存区空。

## 所有权与改动

源码合流精确两测试源覆盖、五测试源新增，生产/runner/公共配置不变。原freeze-v2只读。
下一A CRA-JOINT-J1-SEARCH-RETURN-FIX-v1独占新E Android测试小hunk及测试APK冻结；B CRA-JOINT-RUNTIME-EVIDENCE-REPAIR-v1独占新E Python运行链/配置/采集/恢复及离线用例。可现在并行；无公共写入、无设备。Root收齐后唯一绑定配置/最终身份，设备继续等待。

## Standards

实际执行者自报Claude Code/DeepSeek路由，未授权读取其私有库。下一A建议GLM5.3Flash执行准确小hunk/构建，B建议DeepSeek处理数据和消费者接缝。GLM5.2不可用不安排。用户仅发送完整提示词，执行者固定路径自主交付。

## Spec

已核可复用：源合流、编译/构建exit0、双APK身份、有限矩阵、Root精确清理守卫。31item=TXT12+短末8+D2 7+J1 4；J1中3UI/观察+1纯route，不称4真实UI。
阻断：
1. J1 journey02临时A1 Back实际返回SearchScreen，测试却等首页搜索图标。A删除“首页再进搜索”块，直接复用原搜索输入框清空后SECOND；仅test-only修复，需新test APK。
2. TXT回调先切scroll，J1随后要求paged/on；B优先J1四项，再保留TXT内部顺序，核31id不增不漏，fresh沙箱前置必须实核。
3. D2/J1十一项无after采集；JSON在宿主cache/external files，卸载丢失。按实际producer逐项原始JSON绑定主run/item，不能套TXT schema或stdout冒充12列。
4. 实际入口应扩j1_run.py的真实回调注入，不用joint_driver CLI裸run_all。现5tests注入after自行写空对象，不证真实harvest。
5. D2双方Store需每方法前真实字节基线，后先采集再静止、精确恢复/回读，才能下一项；不以最终卸载或host-only代替。只本轮新host两侧，禁止读改正式/protected数据。
6. validator显式freeze_manifest不存在时仍本地兜底通过；必须显式缺失/非法拒绝，默认只适用于未指定。最终新APK由Root late bind。
7. precheck/baseline要求本轮host/test包与数据absent；不能为此先卸载旧资产。约定前台状态缺恢复时不签通过，不盲目启动protected应用。

## 环境

Root只哈希/aapt/apksigner/只读源码，无adb或重构建。两个reviewer并行，无设备。

## 执行与结果

原编译1m55s、双APK构建1m3s原日志及机器exit0成立。992/35/3/41清单与双APK实核。原197JVM输入充分不变复用。旧离线套件证明其范围，不签真实11项采集/Store/完整运行入口通过；reviewer纯内存复现显式manifest缺失validator错误放行及裸run_all缺callback拒绝。

## 原始证据

原freeze-v2全部manifest/logs/exit/APK；Root fingerprints、source-diff.txt；UI/恢复独审定位。原件保留，不删除历史build/assets。

## 恢复与副作用

无设备操作、无安装卸载/数据settings改动。只Root新E核查证据与D交接文档，无D产品回灌/stage/commit/push/PR/发布，不触正式书库/损坏仓库/桌面/HOLD/私有记忆库。

## 未覆盖项

设备运行链门禁未闭合、MuMu未执行，J1 Back专属请求完成/队列排空/初始新save继续NOT_COVERED；X整机中断、实体手机/MIUI、正式书库/桌面/发布未覆盖，全产品分母未核。

## 交付状态与下一步

冻结交付已到，源码/构建/身份成立，设备前运行链未通过。只派A小测试hunk+新test APK和B运行链窄修并行；不派旧A/B/C整包、不重建宿主、不重跑197JVM。两报告核过后Root合流身份，再给唯一owner限定MuMu。
