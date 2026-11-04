# TXT 补证与只读因果分析：Codex 核查

## 任务编号/版本
CRA-TXT-EVIDENCE-CAUSE-CODEX-REVIEW-v1；2026-10-06。外部两任务已交付，不等于产品修复。

## 输入指纹
补证新候选982源码实算一致。冻结/安装读回双APK完全一致：host 9f568fa1e383af1adb3b36f31edd8d04664ca8d881fa060e0e01eb0886637300；test 256a279c0f46b1fd30411c7bd624d3213109723c0c394ee39404c241f96b6779。原始真实保存观测runId=evir-min-20261006，S=9855、单元3、目标起点9129。

## 所有权与改动
Root仅核本地证据/源码，维护本报告/current/TODO/技能日志；未构建、未ADB、未修改产品/Git。外部报告见[补证](../../../qa/2026-10/2026-10-06-txt-failure-evidence-repair-v1.md)与[只读分析](2026-10-05-txt-restore-cause-review-v1.md)。

## 环境
Windows/Python本地SHA/JSON/MD5账解析；不把历史serial当当前在线事实。

## 执行与结果
### Standards
源码与设备所有权未见重叠实施。补证清单与APK绑定成立；旧证据保持原件。设备报告删除了一项早于本批的历史tmp文件，超出精确清理边界，需记录而不能称完全无副作用；后续只清本批创建项。
### Spec
新批确认非零退出保存S=9855；首页重进原始runner断言0，故TXT问题方向在正确绑定的新批成立，根因未核定。末项j1_20观测在卸载前仍未取回，不能完成行级生命周期证据链；“run_all结束后再harvest”若已执行卸载仍太迟，采集必须发生在恢复/卸载之前，并覆盖方法失败路径。

只读分析可采纳调用链与观测方案，但两项结论需修正：
- 把旧APK问题凭行为特征降级为陈旧账不能替代SHA/安装证据；另一报告的时间线又指向旧包，互相矛盾。旧批最终绑定仍未成立，新批独立双APK绑定才是本轮依据，不追溯翻案。
- 产品getByBook过滤deleted_at，测试裸SQL只读locator，两者语义不同已由源码核实；软删/percent分裂是待证假设。“唯一生产路径”“编解码不是根因”没有完整时点状态，不宜排除其他路径。缺失进度行正常新书也存在，不能未经证据对全部null/0永久保留pending。

## 原始证据
[Root验证](<E:/creation-reading-assistant/txt-evidence-cause-codex-review-20261006/verification.json>)；[真实保存JSON](<E:/creation-reading-assistant/txt-failure-evidence-repair-v1/runs/evir-min/harvest/j1_10_recordExitSaved.json>)；[最小批](<E:/creation-reading-assistant/txt-failure-evidence-repair-v1/runs/evir-min>)；[下一观测建议](<E:/creation-reading-assistant/txt-restore-cause-review-v1/04-device-owner-observations.md>)。不采用其中旧包仍可追回的假设，旧包已卸载。

## 恢复与副作用
新批protected43文件MD5前后逐项一致，Root实算43/43（不是SHA256账）。七设置、wm、forward/reverse原始文本一致，night=yes是本批继承基线，不能关闭旧批no→yes差异。原APK前账文件SHA成立，后账SHA有原始命令记录；after文件写/tmp未保存在新证据目录，接受限定命令证据，不称Root重新哈希后账APK。沙箱卸载证据复用；本次不再次设备取证。旧批缺失前账不能由新账补造。

## 未覆盖项
重进前/后完整行（percent/deleted_at/locator/updated_at）、Loader实际输入、宿主进程稳定情况下重进、末项观测尚未闭合。MD邻接、书尾clamp、恢复等待阶段未验；实体手机/MIUI未验。跨候选4/5=80%保持原限定范围；全产品分母未核。

## 交付状态与下一步
两任务2/2已交付；补证实现主要闭合，末项采集缺口保留；分析提供可用假设，根因未关闭。下一批两个真正独立窗口可同时启动：设备owner做T0/T1/T2完整行级观测，先确保终态JSON在卸载前归档；脚本owner在独立目录完成逐项after_item采集接缝及失败/末项离线回归，不用设备。设备owner不读取脚本进行中工件、不改公共驱动原件。新驱动验收后再绑定后续批次；产品修复等Root核定原因后派，不执行D集成/Git。
规划保持稳定续读与数据保全；复用已闭合APK/候选事实，不重跑全部旅程；本次不按未证实的软删原因写产品防御逻辑。
