# R1/R2 返修关闭与联合输入（2026-10-01）

## 当前结论

本轮R1、R2、R3各自限定交付已核对，3/3=100%；联合构建及设备验收为下一道门禁，尚未运行。全产品分母尚未核定，不把此100%写成全产品发布完成率。HEAD仍`e39a78da9c62f02614207cffb36969a32492eaea`，暂存区空；没有新提交、push、共享产品源码覆盖或目录迁移。

### Standards

R2已去除自行放宽主机的分支，使用HttpUrl读取真实host，拒绝userinfo，保持PairingManager合同；认证编码恢复基线。原规范轴越界项关闭。R1/R2分别只修改原来独占的一个产品文件和一个测试文件，R3仅构建配置；全部原基线872文件逐项规范化核对，没有额外已跟踪源码修改。

### Spec

上轮4项具体需求缺口均有修正：R1非空payload合并管理键、最终完成态决定完成时间、新旧Book统一安全解析；R2真实URL主机校验及原始控制字符拒绝。R1未知payload字段保留/缺filePath可检查跳过、非有限值防御等追加回归已落盘。此结论限上轮具体缺口及专项证据，不等于桌面端实际协议/无文件墓碑/shelved中继合同已完整验收。

## 新鲜证据检查

`E:\creation-reading-assistant\prepare-r1-r3-joint-20261001.py`实际退出0：

- R1二轮红测21 tests/6 failures；最终定向XML126 tests/0 failure/error/skip。
- R2返修红测21 tests/3 failures；同批归档5 XML64 tests/0 failure/error/skip。报告已纠正旧58计数。
- R1源码SHA与追加报告一致：`3e3467935c339fcb73e9e8f9a0a9791427edb822d98fc42d3a887dde9b58173f`。
- 逐项核对原基线android内容：R1仅SyncRepository变更，R2仅WebDavBackup变更，R3仅app/build.gradle.kts变更；两新增测试另列。
- 本轮仅回读/校验已执行证据，没有重复Gradle或MuMu测试。

## 已具体准备的联合目录

目录：`E:\creation-reading-assistant\next-round-joint-20261001\android`。
来源：`git archive e39a78da android`，不是共享脏工作树；基线ZIP已保存。
输入清单：上级`source-manifest.csv`，每项完整SHA和来源路径；`READY.json`标为SOURCE PREPARED，**不代表构建/设备通过**。

精确5文件：

1. SyncRepository.kt（R1产品修复）
2. SyncRepositoryLocalFactsTest.kt（新增）
3. WebDavBackup.kt（R2产品修复）
4. WebDavCompatibilityTest.kt（新增）
5. app/build.gradle.kts（R3签名回退配置）

不纳入N1测试接缝、共享WIP、HOLD或桌面候选；N1原进程中断证据保留，不外推新联合APK全部行为。R1/R2专项计数有共同回归套件，不直接相加作为唯一测试总数。

## 下一步与剩余边界

联合输入冻结后：一次完整JVM/Lint与debug/qahost宿主及androidTest构建；Release静态构建相称复验。随后单一设备验收者在MuMu用受控服务验证WebDAV真实入口/请求边界、同步本地事实与持久状态，完整恢复原APK、冷备持久文件和系统设置。不得安装正式包/访问正式服务。

R1无有效filePath记录/墓碑与shelved跨桌面词汇仍有已登记接缝，桌面真实兼容性另行判断；不得通过伪造filePath或删除旧资产来关闭。整包恢复原子性已实证不支持，R8运行、实际TalkBack、实体硬件与正式签名/发布尚未验。新提交须依据新的精确清单授权，上一轮142路径授权不复用。

规范轴原1项关闭、需求轴原4项关闭；联合输入已准备，联合验收NOT RUN。
