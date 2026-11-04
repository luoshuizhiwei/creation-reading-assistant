# 任务184-r1：修回执真实scope和绑定还原，严格离线

原184 owner同会话，15分钟自本全文实际发送起评估。只新 E:/creation-reading-assistant/task184-strict-keepalive-entry-offline-v1/recheck-r1/，唯一ready.json。旧v1/原始红/所有历史证据不改。Root独占state/current。当前无第二必要独立任务；183构建已接受不重刷，设备业务尚未授权。

## Root验收与具体退回

v1 keepalive_entry_r4.py5880B SHA833f75ab262ffa3fc25edf6e40619ba4d219e26273fda7dfb12a1ff7520ade6f。四case原输出allPass与stderr空只能有限接受被测夹具情形，不能放行生产：

1. `_write_receipt(scope_dir=None)`重新调用`host176_r3.H.R3.load_parent()`，拿的是新parent.b.DEV，不是当前真实恢复调用的b.DEV。host136.py:33-34新模块默认旧ROOT/evidence/device，真实run的重绑定scope未必在新模块，故回执可能写错归属；`except Exception: target=Path.cwd()`又静默隐藏失败。必须绑定当前loader返回的那个b，写它本次DEV/原write合同，不额外load_parent，不fallback cwd，不吞写盘错误。保留实际成功/失败receipt，新receipt归属必须精确本轮真实scope。
2. restore仅还原H.R3.load_parent，没有还原已经返回/仍被父持有的b.resolve_instance与b.cmd_keepalive。现case4只重新load新b比较module，没检查先前两个b。必须记录本层实际改动并在正常/异常出口还原同一已绑定模块对象的原函数，保证可重复restore且不踩掉外层既有绑定；只做所需最小实现，不造插件框架。生产run_main finally语义不改。
3. v1报告承认早期case1/2/3缺subprocessmock触发真实只读查询，违反离线范围。现有最终代码不能证明历史零IO，原ready声称全离线和早期原文并存保留。补交已有日志/实际已知调用清单和UNKNOWN，不自行新查询设备重建历史、不删红。新验证必须在任何生产import/调用之前建立最深subprocess严格拦截，未知命令立即抛错，finally恢复；异常不能被宽泛catch当预期拒绝吞掉。每个case独立输出目录，禁止case3覆盖case2失败receipt；原始stdout/stderr/rc完整保存，不能截断print或traceback作唯一原证。

Root更正自己的初步猜测：identity_adapter_r3自行加r2、r2加r1依赖路径，路径初始化缺失未证，不要求因此改依赖。保留已验179r3完整严格resolver逻辑、managerfalse原值、新keepalive用实际严格链判定，不能改旗标或mock内部resolver/门。

v1索引自引用raw/deliverable-pins.json1701B与实际1965B不符；别再自引用SHA。v1报告约35分钟与实发02:04:05及约02:11ready矛盾，实际不到10分钟，以真实文件时间记录，不借预算估计。所有旧元数据原件保留，在新报告纠正。声明完整总量含pyc/fixture/raw/报告/ready，分类互斥，不默认复制旧文件。

## 实施与定向验证

新r1生产入口仅必要薄层修复，沿v1/179r3原loader继承，不改冻结t94v3/host136/host176/identityadapter、candidate/APK或全局配置。默认真实IO作为将来生产能力，但本任务运行一律只离线。

只为新增缺口执行至多三组定向证明：
- 真实loader给出两个新b，分别绑定不同测试scope；mock最深外部IO驱动真实严格resolver后调用实际b.cmd_keepalive，成功或失败回执各自进入该b.DEV，无额外loader调用/无cwd文件。捕获所有实际外部argv/rc/stdout/stderr，未知调用不得出机器。夹具仅外部IO/磁盘内容与输出scope；不mock resolver结果或内部cmd_keepalive。
- 对这两个已返回且仍持有引用的b，检查正常restore后原resolve/cmd函数对象身份均恢复；异常出口亦相同，检查loader函数身份复原。不能只验证新加载模块。
- 新回执目标写入失败应明确传播并保留失败原因，不转移路径/写成功；可以在最深文件IO边界注入真实写失败，不改业务门。无需再main离线回放、真实probe或旧身份四case全量重刷；原managerfalse在线/daemon拒/boot拒在输入逻辑未变时引用v1原证，指出case2被覆盖历史证据不足而不是冒称全原文完整。

完整代码+原始定向日志+独立rc+新报告交由同owner一次闭环。若生产写盘合同需要传实际b参数，显式绑定该模块，不通过全局重新查找或隐含fallback。

## 停止与交付

严禁真实ADB/VM/设备/identityprobe/main/install/instrument/stopuninstall/Home/launchrebootconnectkill/设置/Gradle。新进程不得先调用真实IO再装mock。任何未拦截调用立即失败交准确阻塞，不补跑真实调用。用户stop/Esc/锁屏/login/权限安全停止不旁路。

source/doc/REPORT/ready<=96KiB，raw与fixture<=96KiB，已有依赖只引用，零工程APKcache复制，不删移证据。每个新产物绝对path/size/fullSHA，精确输入pins、真实argv/rc/原stdoutstderr、真实开始结束时间/实际预算，说明历史IO越界已知与未知。不自引用索引，不伪全设备/全恢复/全产品PASS。不Git暂存提交pushPR发布/云/正式库/私库sessionDB/.codex/memories/自动记忆。最后单独一行“这是任务184-r1。”
