# 63-v2离线交付核验：返修，设备不放行

## 任务编号/版本

63-v2，Root于2026-10-18核验。实现材料已收到，未采纳为可用设备入口。只查本次差量和三个具体风险，不重跑已有完整门禁。

## 输入指纹

E:/creation-reading-assistant/task63-common-device-entry-v1/REPORT-v2.md、ready-v2.json及v2目录。ready列出的6个差量文件SHA **6/6匹配**。实际新入口SHA `32bce4d5b69c3d0d1d6a79a026adf21994c7657a681ff740d1c92916818a608b`；100项绑定SHA `d8e3a32d92a7c347415b0471db9289135167f8f02e9202e4a76ae19f1243dad1`。原Root-r1、冻结、APK及既有验证复用，不重新全量核工具或编译。

## 所有权与改动

未见相关活动Python/Gradle执行者。Root只读交付源码、原始日志，新增本报告/反例日志并更新current；没有改外部v2交付、原始报告或绑定，没有启动原回归脚本。其测试清理存在未知资产删除请求风险，不能直接重跑。

## 环境

Windows/Python3.13。使用实际phase driver的加载函数，以及生产/测试源码中抽出的精确清理块配虚拟路径反例；不调用阶段业务、ADB、Gradle或真实删除。没有新E目录、工程、缓存、APK副本。

## 执行与结果

1. **P1：短路径适配丢失于真实阶段worker边界。** v2薄入口只覆盖父模块常量。Root-r1 `run_phase_with_deadline`第573行写入的runner_path仍是其自身`__file__`；实际phase driver按此路径重新加载模块。Root使用实际加载函数复现：父进程ABC入口为v2包装，重新加载后的worker入口回到共享task56_abc_entry.py。原10项中的thin_runner_bindings只检查父进程常量，静态wiring和offline模拟都没有覆盖这个边界。下一次真实设备执行仍可能走旧长路径，报告“真实worker一定用到”不成立。

2. **P1：测试按公共目录内容匹配删除未知资产。** `v2/test_pull_adapter.py`第273行起遍历`adapter.SHORT_ROOT`全部子目录，以“只有一个名为p且SHA匹配的文件”授予unlink/rmdir，最后还尝试删除公共父目录。文件名/相同内容不是本轮归属证据。Root执行实际AST清理块，预置虚拟unknown_preexisting/p，得到UNLINK_UNKNOWN:p、RMDIR:unknown_preexisting、RMDIR:public_short_root请求；没有真实文件删除。这与提示词和存储治理边界冲突。

3. **P2：生产短目录清理失败被静默通过。** `task63_v2_pull_adapter.py`第147–154行捕获OSError后pass，随后返回成功。向实际清理块注入PermissionError，异常被吞掉；既无阻塞，也无残留错误记录。不能支持“成功路径已清理”及必要恢复失败不静默的合同。

原10/10日志确实存在，但只采纳其已限定的FakeADB路由/字节等范围，不当作以上风险已覆盖。两份历史采集失败JSON仍不得补成新run PASS；Windows底层长路径原因仍是现场支持的假设。

## 原始证据

[Root三个直接反例及差量SHA结果](../../2026-10-18-task63v2-root-counterexamples.log)。原v2/evidence/pull-adapter-regression.log与offline-entry.log保持；旧报告、FAIL、Root-r1及原冻结不修改。虚拟路径反例证明的是实际代码会请求错误删除，不是发生了设备或用户文件删除。

## 未覆盖项

新入口下所有设备阶段仍NOT_RUN。没有进行真实ADB长路径对照、编译、模拟器/整链验收。全产品分母未知。此次拒绝依据是三个确定接缝，不额外制造泛审查或重复31项消费者验证。

## 恢复与副作用

Root设备命令0、构建0、真实删除0。只产生小型D报告与日志；外部原交付保持。旧单次模拟器run恢复已核结论不变，无本轮设备恢复义务。automation-2/3保持PAUSED，无外部消息、Git或发布。

## 交付状态与下一步

**63-v2返修，拒绝设备放行。** 沿同任务交63-v3最小差量，不重开项目、不复制工程、不重建APK：

- 唯一owner让阶段worker重新加载后仍使用本轮包装/适配和正确绑定；用实际worker加载边界验证，不只检查父模块或文本字符串。保持真实子进程截止/进程树守卫及旧Root-r1修正。
- 测试只处理显式记录的本轮独占创建资产；不遍历公共根清理其他token，不用文件名/SHA证明归属，不删公共父目录。未知兄弟/后代必须保留，已有v2回归脚本未经修正不要重跑。
- 生产清理失败须保留具体错误和资产定位，按合同阻塞；失败留证也失败时保留原异常及后续异常。不要用静默pass当恢复完成。
- 只补上述反例和必要原采集回归，修正后再生成实际依赖绑定、REPORT-v3/ready-v3及单次设备方案。设备仍等待用户决定。
