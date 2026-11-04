# N1 真实 Profile/SAF MuMu 验收

## 任务编号/版本
N1-ui-accept-v1；2026-10-02。用户“这个任务你自己去完成”授权 Root 实施上轮 A/B 两项；当前未授权 Git 暂存/提交。UI 批与设备均由 Root 独占。

## 输入指纹
恢复 HEAD e39a78da9c62f02614207cffb36969a32492eaea，暂存空。输入为已验 J3-v2 E 候选，未重跑核心98 JVM或21方法。生产 LocalZipBackup SHA f141941a628de19660d71875bb34faa5b4833f05f6db7d0025aba774165b298e 不变。完整源码对账见 [verification.json](<E:/creation-reading-assistant/next-round-n1-ui-accept-v1/verification.json>)：878文件不变，只改后缀一处及新增一个仪器类。

## 所有权与改动
仅新 E 候选 `app/build.gradle.kts` 的 `.qahostn1`→`.qahostn1ui` 和新增 LocalZipRestoreUiAcceptanceTest.kt。测试 SHA fec6acad9057ea6f6155f56fad70f6b16e43498c90929b6d54a147ae649db4e4。不替换实际应用 Context、不调用 import/export方法造 UI 通过；真实 Hilt 单例文件复制 seam 只阻塞等待外部真实强停。夹具只种新包实际 Room 行及中性测试 TXT。

## 环境
MuMu 127.0.0.1:16416，已保存初始设备信息；1080×1920、临时 density480=360dp、竖屏，原 density280。不是实体手机证据。新包及路径预先不存在；实际 manifest runner CraIsolatedTestRunner 透传生产 App/Hilt，logcat 已核。APK 宿主 SHA dca429c5392b82252bd92b380b0955c107bf19746edd59f1318085069aaa0e73；测试 SHA 9960b967e0ca3f157a59907ec023b4ab5fcce7b4f7a0aaff470001ea28ad4989；设备安装字节一致，aapt/apksigner输出保留。

## 执行与结果
构建一次成功：74 tasks，3m3s。普通仪器方法3/3 PASS（种夹具、UI session、冷读）；1次 arm为 TERMINATED，不算JUnit PASS。实际UI7项合同7/7=100%：
1. 真“我的→存储管理→导入整包→确认→系统SAF选择A”，db_committed复制前生成当前runId/PID/fresh信号，Root强停PID20428，instrument实际crashed；新PID22121真实持久提示出现。
2. 重试确认取消：实际行序列化SHA、正文SHA、状态文件SHA全部不变。
3. 重试确认后SAF取消：同样三项不变。
4. 重试选择同一A：正文SHA变为d33aa5e0dc5ec927ca10c97aa61dfb1fb05bcc5f9cb241f23f4e7d06f63ec0a2，状态删除，提示消失，真实“整包导入成功”。
5. 真UI导出整包→系统CreateDocument→保存：实际ZIP含backup.json与正文，id=n1-ui-b1/revision=2，正文逐字一致；ZIP SHA 9c5e25473c58a4bd705d50d23310b76dbed4ba2cde8c63cbfd7961d532b78492。
6. 冷进程读回状态为空、正文保持；冷启动真实Profile截图无中断提示，PID23099；独立instrument冷读PID23048。
7. 原APK/持久文件/设置恢复与新增包清理全部成立。

## 原始证据
[E任务目录](<E:/creation-reading-assistant/next-round-n1-ui-accept-v1>)；logs中的构建、instrument raw/JSON、真实SAF XML与PNG、actual-ui-export.zip，device-evidence含当前signal与三次ack、session_finished及cold_read；device-baseline/device-after各有raw与live-readback.json。图像已实际检查，为1080×1920竖屏。B协议及43离线门禁见[脚本报告](../../coordination/dispatches/2026-10/2026-10-02-next-n1-runner-hardening-v1.md)。

机械执行失败保留：首次用了未声明AndroidJUnitRunner，真实INSTRUMENTATION_FAILED被门禁拒绝，按pm list instrumentation改为实际runner后通过；文本“确认”与说明正文子串重合，改helper优先精确匹配；export编辑输入多了aaa前缀，实际保存/核对的是中性aaan1-ui-20261002-export.zip；finish读ack_4路径不正确（该动作写session_finished），客户端报错不影响实际OK及session_finished；一次冷启动点击早于页面切换，未作为证据，随后等真实home再点击并保存cold-profile-final。未覆盖或吞掉失败。

## 未覆盖项
实体硬件/MIUI、TalkBack、R8、正式签名升级及F发布不在本批。ZIP DB+文件非原子仍是设计边界；legacy保守阻断不被本批改写。手机宽度提示可读可操作，顶部卡片延伸到状态栏附近，截图保留；不宣称全屏视觉/安全区全面验收。实际正文验证只有受控TXT，不泛化全部格式。新测试候选尚未永久集成。

## 恢复与副作用
两新增包卸载及private/external路径不存在；本批两个公共ZIP与dump临时文件精确清理。原3 APK SHA、53持久文件（含DB/WAL/SHM）、9设置、空forward/reverse前后完全一致。新UID生产启动的prefs/identity/索引留在新UID内并随卸载清理，不能据此证明旧批KeyStore。baseline脚本原始输出无变化；未触碰正式书库/实体手机/原损坏仓库。

## 交付状态与下一步
本批UI7/7和B离线43/43关闭；A/B任务2/2=100%。核心与UI分开保留原范围，不提升11/23证据覆盖为全产品进度。下一关键路径是新联合候选精确集成清单与产品方向共享理解；旧提交授权不沿用。本轮不stage/commit/push，不扩新功能。
