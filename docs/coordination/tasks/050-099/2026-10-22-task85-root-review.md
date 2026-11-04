# 85-v1 Root核验与下一阶段分工（2026-10-22）

任务编号/版本：85-v1-Root。唯一attempt业务FAIL，严格准入/保护与恢复采纳；TXT退出阶段出现同书真实持久位置回退，根因尚未证实。MD NOT_RUN。全产品分母未知，仅MuMu模拟器。

## 输入指纹、所有权与改动、环境
Root只读核验实际ready、run-summary、flow、两份严格保护proof、恢复结果、原始截图与直接相关代码；独立explorer只读保存/退出路径。Root未构建、未发设备命令、未改产品、未运行新attempt。进程查询未发现t85/b2执行脚本。
launcher SHA257513b6176cc11bf0bb9fbfebd45e96ad474abeef3bbc7d110a737fd04b487f实际匹配；ready所列7个输入（含debug APK e59cf92286c6aad2b99e1c55f4dec17bc9b3cea52b9051db3d25560bbebf9b22）当前SHA逐项匹配7/7。复用85新编排输入及既有18/6/12项离线证据，不重复构建或旧四旅程。

## 执行与结果、原始证据
run 20261022-092601-a6545d，单attempt=1，09:26:01至09:32:56。普通debug安装、引导、TXT导入、分页打开与三次横向翻页成立；flow在C_progress_gate失败后停止，未修后重试，MD NOT_RUN。
同一bookId feaa30d9-5f1d-4297-a4fa-32504a7d9e46：
B_after_move：offset1235、progress28.4627799987793、completion_state=reading、updated_at=2026-10-22T01:30:35.411Z；
C_after_exit：offset1034、progress23.8303756713867、completion_state=reading、updated_at=2026-10-22T01:30:44.649Z。
原raw_tail JSON locator的offset及locator_v2 offset/co都同步变化，非无bookId混读。Root查看reader-mid-TXT.png，显示正文第13段marker与页脚28.5%，支持B阶段UI/DB一致。回退201字符、约4.63个百分点不由合法单位换算解释。但C前gate失败未保存after-back UI/XML，不能断定该BACK已离开阅读器。
实际驱动是Flow.run_format的self.d.key(4)，b_ui.Device发送adb input keyevent4，不是androidTest ReaderChromeExitSupport或工具栏点击。ReaderScreen BackHandler在弹层、选区、controlsVisible等状态会消费BACK，其中controlsVisible会隐藏控件；否则交给外层导航。下一次须记录实际动作后界面/路由，不能把任一BACK都称成功退出。
直接代码风险线索：ReaderProgressActions.persistCurrentProgress先用scroll guard的resolveNoMovePosition，非空可优先于paged offset；分页一般不arm该scroll恢复，故是否触发未证。另纯路由离开通常不经ON_PAUSE/STOP，ReaderPlatformEffects的dispose仍可能重算保存，已有注释记载过渡viewport导致旧回退案例。两者都是待区分假设，不是本run根因；不得全局取消dispose保存或直接放松C gate。

## 恢复与副作用
严格initial/final的readonly-protection-proof均ok=true、reasons=[]，原consumer均ok=true/diffs=[]，71私有/820外部、capture计数、DB组均匹配。两份proof位于B/v2/protection-root-r1/outputs/20261022-092601-a6545d/{initial,final}；原始捕获及消费者结果保留。由此采纳本批原数据/DB组/APK与设置合同保护，不以目录列表替代。
85/evidence/recovery/recovery.json为RECOVERED、blockers=[]；本轮新装标准包已卸载、两材料精确清理、night=no，boot不变，keepalive实际device/boot_completed=1、原pid25736/headless44944仍运行。仅描述当时采样状态，不当作当前设备实时状态。本轮没有新恢复欠账，原业务FAIL不改为PASS。

## 交付状态与下一步、未覆盖项、停止条件
恢复与保护闭环采纳，普通TXT业务失败、MD未验。下一86-v1-A唯一问题owner先离线定位退出写入来源、必要最小修复及定向验证，未证根因不改行为；86-v1-B用当前冻结debug独立做MD普通使用及影响范围取证，补足之前被TXT失败按合同跳过的独立范围。B先独占设备，A前段只写E差量/离线验证，不动共同源码/产物。B严格恢复交付后，A才可唯一合入共同候选/必要构建及一次受影响验收；若修改共享保存链，B旧APK结果不自动算修后版本通过。
若A不能离线确认根因，允许该批单次带只读诊断的受控取证，保留具体写入序列，不同批修后第二次main；同一owner继续闭环，减少来回交接。不要制造新恢复工具或重复核已采纳D/F/保护；真正改变其依赖时才补相关验证。设备/构建均单owner，两个自动化PAUSED，无外部消息/Git/发布/桌面/云/存量清理/D产品回灌。
A/B共用当前debug原路径，B运行结束前A不得覆盖源码或APK。若将覆盖唯一既有APK，先查已有同SHA原字节，确属唯一才保全单份，不复制APK对/工程/缓存。初始归属/夜间/严格保护、既有标准包冲突、输入漂移或恢复失败均阻塞。
85新增任务约2.44MiB、保护输出约2.52MiB为报告统计，Root不重复整树盘点。Root仅新增本报告及更新current，无新增E目录或设备操作。全产品百分比不可计算，不称实体手机/MIUI通过。
