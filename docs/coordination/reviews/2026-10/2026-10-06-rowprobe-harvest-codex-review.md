# 同进程行级观测与采集接缝：Codex核查

## 任务编号/版本
CRA-ROWPROBE-HARVEST-CODEX-REVIEW-v1；2026-10-06。两任务已交付，本轮只读审核。

## 输入指纹
行级候选982源码SHA全部匹配；双APK与两次安装读回完全一致（host df501792…，test aaabce1c…，完整SHA见Root验证）。21个时点工件；43文件MD5前后逐项一致。原驱动副本按冻结SHA复制到Root独立E目录重跑，未覆盖外部结果。

## 所有权与改动
本次写Root E反例/离线结果、本报告/current/TODO/技能日志；不设备、不构建、不产品/Git修改。外部[行级报告](../../../qa/2026-10/2026-10-06-txt-row-lifecycle-probe-v1.md)、[采集报告](../../dispatches/2026-10/2026-10-06-j1-post-item-harvest-v1.md)保留原件。

## 环境
Windows/Python；驱动重跑只使用fake-adb子进程，不调用真实ADB。设备原证据为MuMu宽布局，不是实体手机证据。

## 执行与结果
### Standards
两个实施者边界成立；产品未改。构建物/MD5恢复账核定为限定范围，旧批缺口不追溯关闭。
### Spec
行级观测限定通过：原始时间线PID11863不变，T0保存offset7495/percent72.13667/deleted_at空，首页三时点和重进15s/30s该行不变。原报告UI58%约对应单元2起点6061，符合恢复允许单元起点的既定合同，不能仅因58%≠72%就认新缺陷。这里只关闭同进程路径的软删/过滤怀疑，不证明冷启动或仪器重建正常。报告中“仅在冷启动条件复现”仍是嫌疑：本批没有无仪器冷启对照，两批保存位置、模式/流程也不同，必须同条件匹配。

采集接缝：Root独立8场景重跑exit0，失败/末项采集先于恢复及原始FAIL保留成立；1项P2补正：harvest只要求目录里有可解析JSON，并不要求本item的观测文件及runId/step/package身份。Root调用生产harvest，配置CURRENT-RUN/current item=j1_20，仅提供j1_00/OLD-RUN JSON，实际status=ok、problems=[]。因此旧步骤不能代替本方法产物的合同仍未成立；完整checker未被降级，但采集回执不能据此声称本步骤证据齐备。

规范轴0，需求轴行级0（限定范围）、采集1P2。行级成果采纳；驱动同状态时序成果保留，仅身份/必需文件窄返修，不重做整套脚本。

## 原始证据
[Root验证](<E:/creation-reading-assistant/rowprobe-harvest-codex-review-20261006/verification.json>)；[独立8场景](<E:/creation-reading-assistant/rowprobe-harvest-codex-review-20261006/offline-driver/offline/out/offline-verify-summary.json>)；[旧步骤反例](<E:/creation-reading-assistant/rowprobe-harvest-codex-review-20261006/stale-only-repro.json>)；[真实行时间线](<E:/creation-reading-assistant/txt-row-lifecycle-probe-v1/runs/timeline-summary.json>)。

## 恢复与副作用
本次未动设备。外部43文件MD5账Root实算一致，原APK前后SHA有原始文本证据；七设置/wm/ports按原记录复用，night yes为本批继承值，不关闭旧差异。未进行新的运行时设置检查，原设备恢复仅按该批时点成立。

## 未覆盖项
同条件无仪器冷启动与仪器重建的差异、实际偏移丢失写入者未证；MD邻接、短末项、最终同APK联合验收仍未关闭。跨候选4/5=80%保持原范围，全产品分母未核。

## 交付状态与下一步
两任务已交付，行级限定采纳，采集窄返修。第一批现在可同时运行：设备唯一owner在同一滚动设置/同一非零保存单元做暖重进、无仪器force-stop冷启、必要时仪器重建的匹配对照；独立脚本owner补本item必需文件和身份校验。设备任务不读脚本进行中工件，直接SQL/meta按原方法取证。第二批产品修复/正式绑定验收待Root核定后派，不改D/Git。保留稳定续读与数据保全目标，不按软删假设盲修。
