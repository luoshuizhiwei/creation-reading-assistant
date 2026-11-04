# TXT-v3 与验收准备 v2：Codex 核查

## 任务编号/版本
CRA-TXT-V3-PREP-V2-CODEX-REVIEW-v1；2026-10-05。比较基线为 unified-candidate-integration-v1 的 978 输入，与前轮 TXT-v2/prep-v1；审查 E 盘候选，不修改 D 产品或 Git。

## 输入指纹
恢复仓库 HEAD e39a78da9c62f02614207cffb36969a32492eaea，当前暂存区空。TXT-v3 的 freeze/source-sha256.json 实算 980/980 一致；prep-v2 manifest/payload-sha256.txt 实算 20/20 一致。原报告见 [TXT-v3](../../dispatches/2026-10/2026-10-05-txt-reentry-fix-v3.md) 与 [prep-v2](../../dispatches/2026-10/2026-10-05-txt-final-acceptance-prep-v2.md)。

## 所有权与改动
本次只维护本报告、交接入口与 Codex TODO/技能使用记录；产品候选与外部原证据只读。未安装 APK，未执行 ADB，未提交。

## 环境
Windows、Python 3.13；证据放 E:/creation-reading-assistant/txt-v3-prep-v2-codex-review-20261005。设备未运行，历史 serial 不作为本次在线事实。

## 执行与结果
### Standards
0 项规范缺陷。候选所有权与证据范围成立，没有发现 D 产品越界写入证据。

### Spec
- [P1] ReaderScrollPosition.kt:69–78 的 plainRestoreSettled 缺少最后单元身份参数；只检查 target > first 且 lastVisible == target。40 单元下 first=36,lastVisible=37,target=37 也返回 true，即使最后单元为39。生产随后释放 pending，currentPlainListOffset 从首可见项36计算；中间目标项只在视口末端不能证明请求已落位。必须仅对 target==units.lastIndex 允许该 clamp 分支，并增加这个中间项反例。原测试使用 lastVisible=39,target=37，未覆盖此反例。
- [P2] ReaderProgressEffects.kt:447–456 的恢复 effect keys 没有 bid 与 plainListState；注释/报告称它们失效会取消，实际测试仅手动 job.cancel()，没有证明 effect 身份变化。调用点 ReaderSessionEffects 直接调用该函数，ReaderScreen 未见书籍 key 包裹此调用。将明确身份加入实际 effect key，修正文档与对应验证；不要以手动取消代替 key 已接入的证明。

需求轴 2 项（最高 P1），规范轴 0 项。TXT-v3 不进入设备验收；保留同状态等待与共享生产控制流这两项有效修复。

TXT 原始7份 JVM XML：36 tests，0 failures/errors/skipped；输入匹配，复用而未重跑 Gradle。prep-v2 编译原始日志 BUILD SUCCESSFUL，与清单SHA相符，只证明 Kotlin测试源编译，不证明设备行为。Root独立执行 j1_offline_tests.py 的26正反例与 j1_seam_tests.main 的7执行接缝全部符合期望，原外部结果未被覆盖。缺原始runner、缺恢复账、账失败、run_id不符等现在拒绝整批。prep-v2 接受为 READY_FOR_BINDING；真实恢复等待阶段仍明确 NOT_RUN。

## 原始证据
- [Root验证](<E:/creation-reading-assistant/txt-v3-prep-v2-codex-review-20261005/verification.json>)
- [26例结果](<E:/creation-reading-assistant/txt-v3-prep-v2-codex-review-20261005/offline-results/SUMMARY.json>)
- [7接缝结果](<E:/creation-reading-assistant/txt-v3-prep-v2-codex-review-20261005/seam-results/SUMMARY.json>)
- [TXT候选](<E:/creation-reading-assistant/txt-reentry-fix-v3/android>)
- [prep-v2工件](<E:/creation-reading-assistant/txt-final-acceptance-prep-v2>)

## 恢复与副作用
设备未动；仅新建 Root E 证据与文档更新。编辑前文档副本见 [before](<E:/creation-reading-assistant/txt-v3-prep-v2-codex-review-20261005/before>)。无新设备恢复结论。

## 未覆盖项
当前 TXT 真 UI 恢复、短末单元 clamp、最终同 APK 联合验收尚未关闭。历史 J2–J5 跨候选证据 4/5=80% 仅复用已有核查，不是本次新跑或全产品完成率。完整产品分母未核定。

## 交付状态与下一步
本轮两交付中1/2通过：prep-v2就绪，TXT-v3返修。下一实施者在新 E txt-reentry-fix-v4 候选仅修改两个生产文件与所属定向测试，修上述两个接缝；并行第二任务可只读准备精确 D 集成清单，明确临时验收工件排除与共享 WIP 归属，不执行应用/暂存。v4核通过后唯一 owner 绑定 prep-v2 构建/运行 MuMu J1及具体受影响邻接；J5不重复派发。

规划复核：保留稳定阅读/进度不丢失的关键路径，暂不扩展功能；不再返修已通过的门禁，不重复全量 JVM，设备保持单一所有者。精确 D 应用/Git 需当轮授权。
