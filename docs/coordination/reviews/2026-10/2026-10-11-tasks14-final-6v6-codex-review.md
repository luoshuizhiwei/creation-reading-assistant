# 任务14最终31与6-v6核查、守卫回归及恢复缺口

核对：2026-10-11。任务编号/版本：14-v1最终、6-v6。交付状态与下一步：14最终31/31原始PASS与强版冻结成立；新会话守卫P1回归和完整保护后账未关闭，不记整批验收退出。6-v6完整65重放/72交付SHA限定成立，WIP声明/locator/夹具边界待v7。下一三线并行：15-v1独立E产品守卫/JVM，无设备/最终APK；原14唯一设备owner只补完整恢复/报告，无产品修改/重跑31；6-v7不可变12材料/WIP返修，无设备构建。

## 输入指纹、所有权与改动

Root实算14最终999源码全部SHA匹配，清单005bc14232d929a4f156114718e0d7752f10c8d9a92dea47c2b95989ee762082，tree f3030eb37268e729ae9f364204e394cb11cb0091d4a572fb25e6d6aae653d79b。44验收逐项匹配、内容tree未变。双APK实算host7615039be98f9a7cb25378f9609e5069be009b96987ed49928483b12f51495d8、testa710d511796e34b983dd44e0b603be01cfd7eeaf87f64e70feeaefe4d0898ee6。

相对12-v2共六改：ReaderScrollPosition、ReaderProgressEffects、ReaderProgressActions、ReaderScreen、ReaderViewModel及TxtShortClampContentReadinessTest。新增进程级ReaderSessionPositionGuard按bookId存Map，observe仅在500ms debounce collector，离开直接getInitialPosition；快速滑动后防抖尚未发射退出会压回旧initial进度，小于10px位移也可能被误抑制。未见disarm调用；同书临时模式observe先于模式过滤，存在会话污染风险。暂无专门守卫测试。15须定向修/验证这些引入的风险，不因此宣称既有Back专属完成归因等全部盲区已关闭。

6-v6完整INDEX65、SUMS72/72 SHA匹配，报告69项需修正。默认只读/重放日志65成立，但实际D当前503状态与INDEX交集15条脏路径（含14修改/1未跟踪），报告全干净/无WIP交集错误。其当前字节前置SHA匹配只证明绑定现态，不证明可覆盖用户WIP。三个supersedes.sha=null，两条旧root-v2真实文件可补SHA，TxtRestoreObservationSeam旧root-v2路径不存在需改实际v4locator。FixtureDir允许任意根下新增写入，未见递归删除/覆盖，但违反任务专属E边界。

## 环境、执行与结果

HEAD e39a78da9c62f02614207cffb36969a32492eaea、暂存空。Root实际运行强版freeze verify PASS/exit0。run_t14v1_device6 summary31/0/0，Root逐读31item verdictPASS/exit0、31raw存在且均含Instrumentation OK。clamp35与40 payload有S2=1775/46.223957、locator相等和CRACLAMP0023命中，可确认特定夹具合同通过；不得据此排除快速退出、小滚动/会话边界回归。报告占位帧唯一根因未见完整设备诊断时间线，保留推断边界。比例charFraction*itemHeight仅估计映射，不称通用精确定位。

## 原始证据

[Root核验](<E:/creation-reading-assistant/tasks14-final-6v6-codex-review-20261011/verification.json>)、verify.py、freeze-verify.log与[next-input-binding](<E:/creation-reading-assistant/tasks14-final-6v6-codex-review-20261011/next-input-binding.json>)。14原REPORT/freeze/run_device6/raw/harvest/restore与6-v6 INDEX/replay/SUMS保留。独立审查默认pwsh与日志/源码证据已核，不重新设备31/广JVM。

## 恢复与副作用

14原restore仅沙箱卸载前取证、包/data absent及remote账本清理，ok/verified不能代替完整保护后账。原baseline包含保护71文件、DB/WAL/SHM、原APK对、8设置、端口/shell及约定状态。最终报告未提供全部保护文件size/SHA/ns-mtime比较与独立全局后账。当前MuMuNxDevice/Headless及adb对16416会话仍存在，和报告无占用遗留不一致；可能外部工具会话，不无证归因。本轮Root没有设备写入、构建或关闭他人进程。原14owner核占用/基线、补恢复与原报告勘误，不修改产品或伪造旧mtime。

## 未覆盖项与里程碑复盘

31合同限定100%（31/31），全产品分母未核。14整体退出被新守卫回归及恢复证据缺口阻塞；旧结果保留，不先重跑。产品15与设备14所有权分开到不同E目录，6继续不可变12材料，不刷新待变15/14源，避免反复合流。X整机清零、Back专属完成归因、队列排空、初始new save、实体手机/MIUI、正式签名发布未验；无D产品回灌/暂存/提交/push/PR/发布授权。
