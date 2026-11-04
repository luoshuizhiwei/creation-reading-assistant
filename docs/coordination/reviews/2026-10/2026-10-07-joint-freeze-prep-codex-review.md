# 联合冻结准备 v1：Codex 核查与下一步

## 任务编号/版本

CRA-TXT-D2-J1-JOINT-FREEZE-PREP-v1；2026-10-07。实现合流、构建和 APK 身份限定通过；设备前工件返修，MuMu NOT_RUN。

## 输入指纹

交付根：`E:/creation-reading-assistant/txt-d2-j1-joint-freeze-prep-v1`。Root 核查根：`E:/creation-reading-assistant/joint-freeze-prep-codex-review-20261007`。

- Android manifest：728dff4b05e7660e7939888b323d1a677e07e3bed4db9b6db199157c0ff0d243；987/987 文件匹配。
- acceptance manifest：b588794c28b9b1f17abfa2fbd9b31c91056577a0a4c8610a70d5389726d56c67；24/24 文件匹配。
- candidate root manifest：aac237868042dfdec4c7199f89dcad7f7a29b41e50b408f2c0d9f8cc5114ee1e；2/2 文件匹配。
- 证据清单1320项中1319匹配；唯一漂移为 `freeze/provenance.json`，声明 b7f262498a4008e8f71ae26c45bf22b734040bb87685b8578167359cd38db0ff，实算 eada689fd2ae5d029a6a84c899fa319421f726733fada5426011a967f89019f5。保留原冻结，下一版按稳定生成顺序修正，不能覆盖原件掩盖差异。
- D HEAD e39a78da9c62f02614207cffb36969a32492eaea，暂存区空；本轮未写 D 产品源码。

## 所有权与改动

原 v1 单 owner 合流已验 TXT、D2、J1 输入，新增 D2/J1 仪器类与验收/恢复合同。产品修复3/3仍可采纳，不重复实施。

下一批只并行互斥工件：A D2仪器类/夹具/断言；B J1真实阅读器仪器类/夹具/断言；C 离线恢复驱动/绑定/取证/清理及合同。三者各写独立 E 根，不改 v1、不共同构建。Codex 核三份后由 C 原联合 owner 单独合流并重新冻结。尚未派发到外部应用，提示词交用户。

## Standards

不回灌 D、不暂存/提交/push/PR/发布。公共构建配置、合流、冻结及同一设备只有一个 owner。GLM5.2当前不可用；A建议GLM5.3Flash（边界清楚的测试补齐），B/C建议DeepSeek（真实导航与数据恢复）。Gemini不稳定，不进入关键路径。

## Spec

产品目标保持 TXT 真实移动保存/退出冷读、D2身份与用户数据保护、J1真实搜索临时阅读/返回与普通进度保护。构建通过不能替代这些行为及恢复证明。

### J1 工件缺口

`J1TemporaryNavigationJourneyTest.kt` 使用私有 probe 图与复制的 observer、手动 request/documentState，没有进入真实 ReaderRoute/ReaderScreen。9项只可作为辅助接缝测试。普通位置仅查 coordinator 内存，没有真实 reading_progress 完整行、updated_at 和页面落点。需真实 MainActivity/生产导航、目标正文身份/标记、B/C临时期间与返回A的完整DB及UI证据。合法返回后普通保存应与临时期间禁止写入区分。

runner 精确 `.qahost` 分支不匹配本候选 `.qahostj1persistv2`，实际透传真实 App/Hilt。不可简单改为裸 Application 破坏 MainActivity。明确实际分支、启动副作用及新UID触碰清单；现无证据证明访问正式书库。

### D2 工件缺口

`D2RelinkPersistenceAcceptTest.kt` 的固定 d2-accept.db 经 ContextWrapper 委托到宿主数据库目录，并未置于任务 root；teardown只删root不能清理DB。隔离并断言实际SQLite路径，关闭后处理自身DB/WAL/SHM。

补完整 BookEntity、book_content/book_files、用户进度/标注/来源相关行及机器可读前后证据；所有五分支共用完整重新打开断言。补真实最小测试 EPUB、Uri.fromFile(staging)到真实 EpubRepository 消费路径，不能推到设备阶段临时设计。自定义provider只替代传输层，不算系统SAF通过。宿主datastore写入必须隔离或纳入触碰/恢复合同。

### 恢复工件缺口

joint_driver 当前forward/reverse只取第一列，把 serial 当mapping，漏检同serial/local不同remote；删除参数错误。离线复现已证漏报。应比较完整(serial, local, remote)，按local移除/恢复，保护外国serial/mapping，回读核对并覆盖负向测试。

每次卸载前缺即时APK/数据/DB-WAL-SHM/未归档结果取证，初次及失败路径亦须覆盖；不能以此前baseline或after-item替代。取证失败不得继续毁掉唯一证据。

消费任务/run/step创建物账并精确清理 `/data/local/tmp` 自有文件，近似前缀/外国对象拒绝；正常与失败均留不存在证明。恢复声称覆盖音量/前台/hostVM却没有对应完整字段，按实际触碰与约定状态建立前后账，不盲目启动受保护原宿主改变其数据。

旧 config 指向 v2/旧APK/旧路径且只有TXT；准备联合config生成器与离线validator，绑定当前冻结、实际D2/J1入口及TXT子合同，serial/run设备前绑定。原隔离qahost只读SHA取证要明确权限和静止/一致性边界；文件SHA不等同可恢复数据库备份。新候选包已存在时仍拒绝，不擅自卸载绕过。

## 环境

本轮仅本机只读核验，独立 SDK aapt/apksigner 核APK；未构建、未安装/卸载、未使用设备。预期设备仍为隔离MuMu，不是实体手机/MIUI。

## 执行与结果

- 原始13份JUnit XML：197/197，0失败/错误/跳过；输入未变不重跑。
- 原报告离线18+46、Lint0错误9警告、仪器编译与双APK构建；相应原始工件SHA已核，未冒充本轮重跑。
- Android/acceptance/root清单匹配；3/3来源绑定匹配。
- 宿主SHA 440514acd90de7bf38edb744851eefcf345f2c3b9d4710984358df2c142552ae，51781356字节；包 com.creationreadingassistant.qahostj1persistv2，versionCode3/versionName0.5.1。
- 测试SHA 57ae37bbf8a822f9d81fe31a575b5e35782bf078ca7e9404d8449ecd2e38b401，9472768字节；包 com.creationreadingassistant.qahostj1persistv2.test，target匹配，runner CraIsolatedTestRunner。
- 独立签名验证均exit0；双APK证书SHA 05249d786e3d3d117bb5bd7b1f83512a0c9b8f1ee4e502112c4b2d9ccbd7b9a1。

## 原始证据

Root `root-fingerprint-check.json`、`apk-observation.json`；外部原 `REPORT.md`、freeze/、logs/、evidence/。独立 UI/D2/恢复审查仅只读，恢复mapping反例为纯内存模拟，不涉及adb。

## 恢复与副作用

本轮无设备变更，不存在本轮设备恢复PASS。原候选及原证据保留；1项元数据漂移独立记录。D/Git权限未扩展。

## 未覆盖项

真实TXT/D2/J1联合MuMu、真实J1阅读器旅程、EPUB staging消费、完整设备恢复均未通过。实体手机、X整机中断单点清零、正式书库、桌面、发布未扩展。全产品分母未核，无法计算全产品完成率。

## 交付状态与下一步

实现输入3/3合流、197/197 JVM及双APK身份成立；设备前准备尚未通过。先A/B/C独立工件返修，Codex核查后唯一owner合流、编译和重冻结；再核新冻结，才给限定MuMu提示词。主源码及构建合同未变时复用原宿主与充分JVM证据；测试APK变更必须绑定新SHA。不要把新提示词发到不对应的旧会话。
