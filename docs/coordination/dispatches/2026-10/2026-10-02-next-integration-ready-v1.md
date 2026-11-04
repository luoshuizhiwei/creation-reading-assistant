# 新联合候选合并与定向回归

## 任务编号/版本
integration-ready-v1；2026-10-02。用户连续要求“继续推进”，Root在E副本实施合并、核依赖和定向测试。未获新Git或共享源码应用许可。
## 输入指纹
HEAD e39a78da9c62f02614207cffb36969a32492eaea，暂存空。J3-v2已验输入与当前D-WIP逐文件三方对照；N1 UI7/7、core7/7和G1原证据仅各自原版本复用，不当作本合并版本设备通过。完整[最终25路径指纹](<E:/creation-reading-assistant/next-round-integration-ready-v1/final-manifest.json>)与[HEAD补丁](<E:/creation-reading-assistant/next-round-integration-ready-v1/final-HEAD.patch>)。
## 所有权与改动
Root单owner，新[E候选](<E:/creation-reading-assistant/next-round-integration-ready-v1/android>)及final-payload。初始14文件不是最终范围：保留WIP需要10个明确依赖（SyncOptions/SyncPrefsStore/ProfileSyncEngine/ProfileUiState/ProfileUpdateChecker/WebDavSubPage/SyncSubPage/AppLayout/SettingsDensity/LayoutTokens）和1个ViewModel测试构造适配，最终25路径、121个HEAD逻辑hunk。临时qahostn1后缀恢复为永久.qahost，原沙箱驱动和5换行差异脚本不纳入final-payload。Sync/WebDAV人工合并，Profile自动三方合并并核实际编译；新增7个接缝断言在E测试类。
## 环境
Windows/JDK17，E独立候选与缓存；离线Gradle。零ADB、零新APK安装、零实体手机/正式书库、零Claude库操作。
## 执行与结果
Standards：14初始路径共享WIP SHA未变，HEAD未变、暂存空；所有新产品内容限E。独立reviewer发现两P2（WIP PUT/test网络重试与404提示丢失），Root修复后再次只读审核关闭。PUT重试每个网络execute有界，不包整个MKCOL流程；业务401/403不重试，404/409一次建目录，建目录失败终止。

Spec：R1安全解析/本机事实与payload回填保持；3类别开关、冲突策略保留。HEAD→PROPFIND→OPTIONS、UTF8认证、命名空间/集合排除、二进制声明及实际流上限保留。目录404为可达未创建提示，不在测试连接时MKCOL。源码编译成功；6套定向JUnit共153条全部通过，其中新接缝7/7。覆盖REMOTE_WINS等revision且旧时间的安全字段合并、LOCAL_WINS等revision不写、高revision仍接受、关闭类别不写、OPTIONS后备、404无创建和中文认证。

失败日志全部保留：01缓存复制未完成使wrapper下载超时；02–05为实际依赖缺失（状态/更新checker、WebDAV action签名、布局state/密度和tokens）；06生产编译通过但旧ViewModel测试缺新构造参数；仅给原夹具补SyncPrefsStore flow，07定向门禁成功。没有用删除测试、改产品默认值绕过编译失败。
## 原始证据
[E目录](<E:/creation-reading-assistant/next-round-integration-ready-v1>)：inventory/三方merge输入、完整patch、resolution脚本、build-01至07日志、最终manifest及25文件payload。最终JUnit XML逐文件SHA已记。独立审查通过限定源码控制流，不冒充运行证据。
## 未覆盖项
合并后Profile引入已有WIP依赖，尚未新MuMu验证；旧UI/core合同不能外推，下一新UID需要验证受影响同步设置/Profile恢复链。永久.qahost使用isolated runner，与旧UI专用新UID生产Hilt不同，不能直接安装覆盖原qahost。R8/签名/升级/全量功能/实体硬件/F未验；更新checker未联网。原临时驱动仍在E候选仪器源码内作输入历史，但不纳入提交payload，不可直接整包提交candidate。没有自动批准10依赖的全部产品需求，只证明本轮接缝编译/定向行为。
## 恢复与副作用
共享D产品14指纹全等，Git HEAD/暂存不变；新输出仅E。本报告/current/项目/TODO/skill log的前文副本保存在docs-before，其他TODO项目原字节保留。旧冻结/历史报告/原损坏仓库/桌面/HOLD未改。
## 交付状态与下一步
集成准备三关（指纹/合并/源码+定向回归）3/3=100%；新版本设备关0/1，永久应用/提交0/1，完整联合交付未关闭。全产品分母仍未核；11/23证据覆盖口径不因测试数提升。下一在新UID验证本合并输入的Profile恢复、同步设置实际接线与恢复，然后给新的25文件精确hunk范围；不沿用旧142路径授权，不stage/commit/push。规划保持阅读稳定和数据安全优先，停止重复核心旧批/不扩LAN或F；将原“可自动合并”升级为必须含依赖/受影响设备验证，原因是真实编译暴露WIP接口变化。
