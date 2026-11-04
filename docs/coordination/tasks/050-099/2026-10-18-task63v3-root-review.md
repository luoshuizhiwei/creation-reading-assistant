# 63-v3核验与Root测试收尾修正

## 任务编号/版本

63-v3，Root测试修正版root-r1，2026-10-18。离线返修收口，等待用户决定下一次单次模拟器批次；不称设备或整链通过。

## 输入指纹

现有63目录REPORT-v3.md、ready-v3.json及v3差量；实算7项SHA **7/7匹配**，详见Root input-checks.json。生产入口 `v3/task63_v3_runner.py` SHA `21c46382512763fbd5670fcc03ad80879560ec8f8ceddfa13dcb82ddedde4f30`；101项绑定 `v3/tool-bind.task63v14.v3.json` SHA `9e6b2eb76ad69f183591bf1325b4916f0129a4bdbd931dfbb4612c507f6abbd8`，Root修正后再次核同。冻结 `31f0f8594bd2f096c3d3e3534c4254e9c8878c66ca464f3e403196968e0090dc` 及APK原路径复用，不生成新冻结或复制。

## 所有权与改动

未见相关活动Python/Gradle执行者。Root不改外部交付、生产入口、适配器、绑定或共享原件；只在 `E:/creation-reading-assistant/task63-common-device-entry-v1/root-review/v3-r1` 交两个测试源码差量、patch和必要验证日志，并更新本报告/current。

## 环境

Windows/Python3.13。原任务目录为必要cwd：`E:\creation-reading-assistant\task63-common-device-entry-v1`，因为原冻结引用prebuild/build-record使用相对路径。唯一59构建工作区/D依赖保持，不构建。Root测试使用该核验目录下独占小夹具，未操作公共 `E:\c63v2` 或设备。

## 执行与结果

- **worker接线修复采纳。** v3显式改变已加载Root-r1模块的`__file__`运行期值，使生产阶段spec指向v3；v3导出run_phase_body，重新加载后再绑定并委托原阶段体。已核Root-r1仅在spec生成处使用该全局，磁盘原件身份保持。原日志及Root真实生产run_phase_with_deadline→phase driver→ABC包装preflight再次证明跨进程使用v3，**3/3、exit0**。不是只查父进程属性，也没有取消子进程/截止/守卫。
- **生产清理错误修复采纳。** ShortCleanupError保留原OSError、cause及精确短文件/目录位置，失败不再返回成功。Root直接复用v3生产实现，采集/清理定向回归 **11/11、exit0**，覆盖字节/SHA、冲突、失败留证、清理错误阻塞、未知兄弟保留；FakeADB范围仍受限。
- **测试收尾由Root补小修。** 原v3采集回归仍对只记录根的tree调用rmtree，未覆盖未知后代；原worker回归重跑先rmtree既有wiring-parent证据。Root测试副本取消tree递归删除，保留并列出夹具树，仅处理精确记录文件和空目录；worker测试用新目录且exist_ok=False，既有证据不会被重跑覆盖。虚拟目录反例：原helper请求递归删除1次，Root helper 0次。原测试/旧证据不改，后续重跑使用Root安全副本，不能直接运行原v3测试。
- Root第一次从D仓库启动真实preflight，因原冻结相对prebuild路径无法解析而FAIL，原日志及spec/out保留。核实后从原任务cwd启动、使用另一个新目录，strict preflight通过。未重建、未补造快照、未修改冻结来掩盖错误。

Root测试副本不是生产动态依赖，不改变原101项运行绑定；生产入口和适配仍是已绑定v3原件。新增测试源码的前后SHA和patch单独记录，不伪装已进入原绑定。

## 原始证据

Root目录：`input-checks.json`、`root-results.json`、`cleanup-root-counterexample.json`；两份test_*.py及对应.patch；`pull-adapter-regression.log`、`worker-wiring-regression.log`（首次FAIL）、`worker-wiring-cwd-fixed.log`（正确cwd PASS）；`evidence/pull-adapter-regression.json`、`evidence/wiring-parent/`（FAIL原件）和`evidence/wiring-parent-cwd-fixed/`（真实子进程PASS）。

Root测试SHA：

| 文件 | 原v3 SHA | Root测试SHA |
|---|---|---|
| test_pull_adapter.py | cca3329cc7206c1d959d46dd947254be4de561b405e640360ef97eaa2194c0aa | 1e57388f5305a7beb797a46abf91fe6a8cc082a912fe0e1262be0c5a4b091258 |
| test_worker_wiring.py | dd2855d0848d23121fabcbe2899fff0e1695594cb10a22ecfa644e40d78517e6 | a6f1ef2bf3217507142e8f634970fca32c76f0e39b4897e53a05195e22600388 |

## 未覆盖项

真实设备ABC execution、clamp、SAF、Room4、DE/总消费者均NOT_RUN；长路径底层原因仍是假设，Mock短路径结果不等于真实ADB通过。全产品分母未知，无实体手机/MIUI通过结论。既有3671项JVM/Lint/APK证据复用，不全量重跑或再搭验证框架。

## 恢复与副作用

设备/ADB命令0、构建0、APK复制0。只对本轮Root新建夹具中的精确记录文件/空目录执行测试收尾，未删除用户既有资产；10个未逐项证明整树归属的夹具根保留并写入root-results.json。Root核验目录约76KiB（元数据写入前76,261字节；最终总量见root-results.json的最终体积字段），加少量D交接文本；没有工程、缓存或APK副本。源码/日志为必要交付及唯一证据，夹具为可再生成保留物，历史恢复资产仅引用。

原63设备FAIL及已核恢复不变，无新设备恢复债务。两个自动化保持PAUSED；无Git/发布、外部消息、D产品回灌、桌面/正式库/私有库或云部署操作。

## 交付状态与下一步

**63-v3生产离线交付采纳，使用Root安全测试副本；不再开整批63-v4。** 下一步等待用户决定单次模拟器验收：唯一owner、cwd固定原63任务目录，使用v3生产入口/101绑定、原冻结/APK，新run/一次attempt、fresh serial/保护/原实例状态，完整阶段预算加1200秒恢复；失败不现场改码重绑重试。执行ABC→clamp→SAF→Room4→DE/总消费，按既有合同核对保护、创建物、APK/设置/实例恢复；本地短路径残留精确记录，不清理未知资产或公共根。不直接复用历史启动命令、serial或旧保护清单。设备授权未到，本轮到此停止。
