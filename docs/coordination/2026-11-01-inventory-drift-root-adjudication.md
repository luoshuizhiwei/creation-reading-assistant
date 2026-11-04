# Root裁决：inventory漂移、D1接缝与P5加签

日期：2026-11-01（香港时间）。裁决版本：ROOT-20261101-INVENTORY-D1-P5-v1。依据[裁决请求](2026-11-01-inventory-drift-adjudication-request.md)、[current](../handoff/current.md)及本轮只读核验的代码/原证。用途：ZCode或用户指定接任协调者可直接按本裁决编制最小执行单并接续，无需原Root持续在线。

**结论：D为主路线；A、B不批准；C不作为默认修复。D1维持当前产品接缝；P5限定加签通过，运行以恢复保护全部关闭为前置。** 本轮Root只读取证并写文档，没有执行ADB、设备清理/恢复、改基线、改源码、重新构建或自动化操作。

## 1. 裁决摘要

| 事项 | 裁决 | ZCode下一动作 |
|---|---|---|
| A：`pm clear com.creationreadingassistant.test` | **不批准**。清空包数据不能恢复既有路径、权限/owner、原缓存内容或userState，也不能证明得到原基线 | 不清包、不卸载原test来重置、不把空数据当恢复 |
| B：直接接受8b2e3317并更新preserved SHA | **不批准**。原因未证，且会覆盖保护依据 | 保留d6ac原inventory/tar/APK/合同；不改常量/忽略路径/只比较子集放行 |
| C：完整设备恢复/清VM，或一把覆盖所有归档 | **不批准作为默认方案**；禁止整机wipe/重建VM。若以后D证明需扩大恢复，只能另立精确test包恢复合同 | 不“顺手恢复所有文件”。parent原恢复函数也不能在未知差异时盲调 |
| D：逐文件/元数据比对后最小修复 | **批准采用，按第4节分阶段推进** | 先完整取证和分类；满足准入条件才做精确原件恢复，新增/未知资产不删 |
| D1：新增会话内失败产品接缝 | **本版不新增** | 维护现有同书入口与防御；保留图seam证据和完整UI未覆盖说明 |
| P5：清缓存→搜索→外部pending失败 | **限定加签批准**，单独任务/单方法/独立fresh批 | 可先做测试侧离线合同与实现；实际设备运行等inventory和全部保护差异关闭 |

本裁决不是扩大209 A/B授权范围的空白许可。P5新增范围由本次用户明确委任Root一并裁决而加签；不能扩成任意压力测试、无限重跑或真实书库操作。设备执行仍需原实例fresh身份/唯一owner/现行恢复授权；自动化暂停状态不因本裁决解除。

## 2. 本轮已确认事实与证据边界

### 2.1 inventory门真实保护的内容

冻结parent：`E:/creation-reading-assistant/task136-f1-global-offset-repair-v1/lane-b/scripts/host136.py`。

- `original_test_probe`（约466–477行）先核原test APK，再执行preserved脚本采inventory，读取完整User 0行。
- `verify_original_test`（约480–487行）原APK、inventory SHA、userState全部符合才接受；inventory不符抛出 `test data changed since preservation; do not replace`。这是保护门，不是要取消的障碍。
- `restore_original_test`（约490–521行）验证原APK/tar哈希，安装原test；若inventory不符可解原test数据tar，然后再次核APK/inventory/userState。它不是整个VM恢复，也不自动清除tar内没有的新增文件。
- 原脚本为 `find ... stat '%f %u %g %n'` 加 `find ... -type f ... sha256sum`，覆盖CE/DE路径、类型/权限、UID/GID、普通文件内容，并检查外部test目录缺席。**没有排序**；因此raw SHA变化本身不能区分内容、元数据、路径集合、输出顺序、命令异常或采集过程中写入。

### 2.2 原件本轮实算

preserved根：`E:/creation-reading-assistant/task94-organizing-export-integration-v1/v5/preserved/`，本轮只读核验：

| 原件 | bytes | SHA256 |
|---|---|---|
| `original-test-data-inventory.txt` | 694 | `d6ac3c5ff7845b4afc9b384fb11b7a2bf921b9f722d4706bb6fe10801d003e29` |
| `original-test-data.tar` | 5632 | `fbdbb692ee494c6d3d10312be4d7b764f08791724ff648a3e244c9c0b5afe50a` |
| `original-test-v3.apk` | 10508820 | `0d18d788a04e366271df3ad2ac18cc2ceeb95edc3c64c37cb995745aefbc9a9c` |
| `data-inventory-script.sh` | 293 | `5d7e3ef9b93158b635112bf51316183619ca79ac86fabd97a14c73eb07b6e312` |

tar成员本轮核出：CE/DE两个test包根及其cache/code_cache共**6目录+1普通文件**。唯一文件为 `/data/user_de/0/com.creationreadingassistant.test/code_cache/com.android.skia.shaders_cache`，916B、mode0400、UID10071/GID20071、SHA `0c025ac62c2058492ae28d1cc7c17500f1cf01e470370767dfca2258ab92fa97`。根目录mode0700、UID/GID10071；cache/code_cache目录mode02771、UID10071/GID20071。

这些只是原基线，**尚不证明本次偏差正好发生在shader cache**；不能把“可再生缓存”当作可以不核内容/权限的理由。后续若实际package UID变化，不得强行chown成旧UID绕过系统归属校验。

### 2.3 漂移归因未被原始差异证明

state报8b2 SHA：`8b2e33179b9b5606e767ec3a0c40f6db48b1b3a8c9cda2303f6a7723640c9ac2`。`task209-navigation-coverage-v1/recover-A/recover-log.json`在2026-11-01 08:04的P1记录同SHA，但该脚本只存hash，产品已缺席即 `ALREADY_ABSENT_NO_OP`；这个结论只说明产品缺席，**不能说明原test/保护基线恢复成功**。

08:28批次scope：`E:/creation-reading-assistant/task192-reader-internal-lifo-host-offline-v1/attempt-T165V1-20261101-082841/`。其原 `summary.json` 确认：

- primaryError为上述inventory拒绝，`installedByThisRun=[]`，无本轮instrument业务结果；不是新业务FAIL。
- `recoveryDecisions.productAction=NOT_INSTALLED`，仅描述本attempt安装归属，不证明所有原数据未变。
- 还有 `recoveryErrors=[{stage:protection-final,error:returned failure}]`；不能称“只有inventory一项阻塞”。

本轮未在该failed scope定位到完整8b2 inventory stdout/rc的持久原件，前述日志也没有逐文件before/after差异；**“多轮install/uninstall累积导致”暂记假设，不认定唯一根因。** 可引用已有完整新原件；没有就由合法owner按第4节新鲜采集，不回写/补造旧日志。

### 2.4 第二个独立阻塞：保护集合出现8个新增文件

同scope的 `evidence/device/02-protection-final.json` 原rc2、ok=false。consumer仍比较71private/820external，但diff并非零，列 `external_added`：

```text
/data/local/tmp/ui-notes.xml
/data/local/tmp/ui-prof2.xml
/data/local/tmp/ui-prof3.xml
/data/local/tmp/ui-profile.xml
/data/local/tmp/ui-s1.xml
/data/local/tmp/ui-s2.xml
/data/local/tmp/ui-s3.xml
/data/local/tmp/ui-step1.xml
```

它们看起来对应209 UI诊断导出，但归属/内容是否已保全必须用创建回执、文件内容hash与任务本地原件证明；本裁决**不授权删除或移动这8项**。已有“无资产删除移动”边界继续有效。ZCode先整理精确清单、比对已保全本地副本、说明删除后果；若没有既有明确同批创建物清理授权，向用户一次确认这8项的精确清理批次。不得把它们加进受保护baseline、改consumer忽略或过滤diff来消除阻塞。

inventory修好后仍需独立完整保护结果通过；只看原test包SHA或产品已卸载都不够。

## 3. 既有业务结果怎样复用

本轮不是对所有209历史重做技术审计。下面核对了具体scope的原 `04-run.json`、`summary.json`、`12-recovery-decisions.json`，不重新运行设备：

| 批次/runId | 本轮确认 | 沿用范围 |
|---|---|---|
| A `T165V1-20261031-195041` | 原stdout `OK (7 tests)`、7个terminal-pass；businessOk=true；本attempt ok=false，恢复PRE_FAILED（WAL/SHM在归档期间变化） | 图7项业务通过。恢复后来经C4闭合是另一份证据链，不能把原attempt改写成一次全绿 |
| B正式 `T165V1-20261031-214445` | `OK (1 test)`/6.218s，businessOk/ok=true，UNINSTALLED，recoveryErrors=[] | 生产UI成功链限定MuMu通过；前面argv/spec拒收和重跑仍保留 |
| C v11 `T165V1-20261101-013500` | `OK (1 test)`/11.141s，businessOk/ok=true，UNINSTALLED | 只能按实际断言接受。REPORT和当前测试使用“阅读笔记消失”及“更多消失”的负断言；**不能单凭此证明定位到reader正文并Back回Profile**。请求摘要的“pill→离开Profile导航已验证”比“完整返回链PASS”更准确 |
| D `T165V1-20261101-015813` | `OK (4 tests)`/0.133s，businessOk/ok=true，UNINSTALLED | EPUB解析4项；不能推广为EPUB导入/打开/退出续读全reader流 |
| 208 `T165V1-20261031-162905` | 原报告及已接受冻结203链：普通退出书架/重开S0完成 | 205旧失败在新输入下未复现；不改写205原红，不证明所有保存异常不存在 |

上述设备均为**MuMu模拟器instance1，不是实体真机**。已有code-reviewer接受记录按其明确范围引用，不因“独立核验ACCEPTED”就扩大断言覆盖。批C的正向落地/回真实来源页断言仍应列缺口；修断言需读真实UI语义，不能以另加sleep或只看某控件消失当根因修复。

209 REPORT当前还有“批B形式重跑待设备空闲”等被后段覆盖的旧段落；V0矩阵还有“批A/B待授权/NOT_RUN”等过时单元格。接任协调者只更新现态/矩阵受影响行，旧原日志不改，不把本次漂移倒推成历史每批都未恢复。

## 4. D路线执行单：先对账，再有限修复

### 4.1 Owner、位置与权限

由现有设备owner或完成显式交接后的唯一owner实施。建议报告根 `E:/creation-reading-assistant/task209-navigation-coverage-v1/inventory-repair-r1/`，不预占新业务编号。允许新增最小采集/离线比对/修复计划driver和REPORT；candidate、原preserved、冻结host136/158/166/192、collector、consumer、resolver和既有报告只读。

本裁决批准D路线的调查与满足下列条件的**原test包原件定点恢复方案**；不批准新业务运行、整包清空、未知新增资产删移或任意改基线。本轮Root不执行设备写操作；ZCode实施时记录其实际有效设备/恢复授权和唯一owner。原授权有效且范围覆盖的动作不重复向原Root请示；超出范围才按精确动作清单问用户。

### 4.2 D0：离线原件核验与fresh只读采集

1. 先核第2.2节4个原件SHA和tar成员白名单，拒绝绝对路径、`..`、symlink/hardlink、跨包或意外成员；原tar不解到设备、不覆盖原件。
2. fresh179r3核原实例/transport/boot/唯一owner，查无活动instrumentation/原test进程写入；不假定旧serial/boot仍有效，不重启VM/杀无关应用“获得空闲”。有用户前台冲突时停设备输入。
3. 按原脚本以相同通道新鲜采集 **完整stdout/stderr/rc及实际argv**，不要只留head/hash。同时保留原test APK/实际package UID/userState、CE/DE/external范围状态；查询失败不能归一化为空成功。只读采两份，确认没有变化再计划修复；两份变化则先定位writer，不边写边恢复。
4. 将原inventory和实际stdout解析为按完整path索引的结构：成员集合、文件类型、mode、UID/GID、普通文件SHA；补实际size对照tar。报missing/added/content/metadata/order-only/command-failure六类，不只输出“diff=true”。同一路径重复、缺字段、哈希格式不合法须失败，不吞错。
5. 另查第2.4节8个文件的实际存在/创建归属/本地保全hash。保持protection-final失败原件，不能把“已比较71/820”写成零差异。

### 4.3 D1：按真实差异选择动作

| 真实差异 | 允许的下一动作 | 停止点 |
|---|---|---|
| 脚本/采集异常或权限失败 | 修本任务采集调用方式，保留失败原件后再采 | 不更改冻结保护脚本内容/期望SHA，不改设备数据 |
| 两份采集不稳定 | 识别本任务/原test writer，按已有quiesce授权稳定后再采 | 无quiesce许可或无owner不得随意stop，不循环“碰碰运气” |
| 路径、metadata、file SHA/size全部相同，仅顺序不同 | **没有数据需要恢复**。保留原raw和完整语义等价证明，写独立版本的确定性inventory门方案/离线正反例，再交协调者核验后适配新driver | 本裁决不预先批准gate新实现；不得向旧gate返回伪造的d6ac字符串，也不得仅改期望hash放行。旧preserved/raw永不覆盖 |
| 只涉及第2.2节7个既有成员的丢失、原file内容或mode偏差，原件齐全、package UID归属未变，无未知新增数据 | 列精确“path/当前type,mode,UID,GID,size,SHA→期望值/来源tar成员SHA/动作”清单并保全偏差前态；在有效恢复授权内**仅恢复清单指明的原test包成员及必要元数据** | 不无条件解全tar、不递归chmod/chown。新增成员、数据库/密钥/未知文件、UID漂移或其他包路径不在定点许可内 |
| 有新增文件/目录、原包UID变化、未知数据、非test包差异 | 保全，报告准确范围和数据含义，另定恢复/清理精确合同 | 不删新文件、不强行改UID、不自动切A/B/C；不能靠覆盖少量文件宣称完全恢复 |

“order-only”若成立，后续确定性门必须比较**原始基线和新采集的完整同一语义集合**，保留所有原保护字段与外部目录缺席校验；不把新增数据、权限/owner变化或缺文件当可忽略缓存。冻结旧门保持原样，新适配器/合同有独立版本、pins与拒绝差异用例。若协调者无法在当前权限/冻结机制下合法接入，保持BLOCKED并交具体接缝问题，不伪造旧输出。

### 4.4 D2：恢复后关闭标准

- 除已独立核验并明确版本化的order-only新门外，目标仍为原脚本正常rc0且inventory **d6ac完整SHA匹配**；原test APK0d18、完整userState/实际归属匹配。变成8b2稳定不算恢复。
- 原保护consumer独立核71private/820external/DB-WAL-SHM，**diffs确为空**；8个新增诊断文件若仍存在且未获精确处置，继续BLOCKED，不过滤。
- 产品包/CE-DE-external三根状态、八设置/focus、strictkeepalive按本轮合同核。最终完整identity位于全部保护查询之后。
- 报告对inventory与外部保护两条分别给原rc/输出/结论。原NOT_INSTALLED/no-op不是恢复PASS；修复成功不是新业务PASS。
- 全部关闭后，接任协调者基于真实新原件接受恢复并更新current/state；才允许下一批E/C/P5/EPUB reader的新合同进入。不要为“试试gate通没通”启动instrument业务。

### 4.5 交付、预算、停止

源/driver/REPORT/ready≤256KiB，普通raw≤512KiB；完整保护capture与必要偏差前态/原件恢复证据单列实际bytes，引用原档优先。不复制工程、Gradle home、APK对或历史归档；不删移资产腾空间。先估算E新增量与剩余空间；预计只读诊断15–20分钟，必要定点恢复另留20分钟，复杂差异到点交精确清单，不无限重试。

REPORT分开写：确认事实/未证假设、baseline源pins、fresh身份/owner、两次raw采集、结构diff、动作准入清单、是否实际写设备、修复后原rc/保护diff、未覆盖与下一步。`ready`最后生成，PARTIAL/BLOCKED不得写PASS。ZCode可继续完成明确的离线比对/合同工作；涉及未知新增资产的删除、整包/整机恢复或基线迁移仍须独立精确授权。

## 5. D1产品接缝裁决：维持现有产品，不为测试新增入口

209-r2源码分析和现有实现表明，当前reader内临时入口是当前已加载书的定位，已删书/孤儿记录另有存在性/级联/索引防御。**本版不新增reader内跨书入口、可注入加载失败卡片、暂停加载开关或生产故障注入接口。** 不为获得一个测试PASS扩大产品范围。

- 会话内pending失败/取消还原语义保留；图7项真实MuMu运行可支持其图seam/共享分派范围，不能升级成“完整自然UI会话内失败已验证”。
- “在现有已分析入口和稳定触发条件下未找到自然可达场景”不等于证明所有竞态/未来入口永远不可达，仍列明确未覆盖项，不写业务PASS。
- 将来若原日志证明当前正常用户操作可达错误，按产品缺陷处理，不能用本裁决拒修。新增真实产品入口时再评估最小测试接缝，不在本轮预建功能。

## 6. P5限定加签：允许真实产品外部失败场景，先小后完整

**加签范围**：一个独立测试侧任务，目标为“测试书清本地缓存后，搜索结果的既有临时查看入口遇到真实加载失败，Back回原搜索页，查询/结果上下文和原普通阅读位置保留”。不涉及正式书库，不直接SQL伪造失败，不新增生产功能。

### 6.1 可先做的离线准备

- 由唯一测试/candidate/Gradle owner在任务文档中写具体新class#method/expectedCount=1、文件所有权、输入前SHA、runner209 spec/knownMethods与恢复机制。建议新增 `ReaderExternalPendingFailureProductionTest.kt`；其他现有测试修改只在有真实selector证据的最小hunk，不碰冻结203/原业务红。
- 只读核`BookRepository.clearBookCache`现行为：删除本地正文并置`content_status=missing`、`local_uri/local_content_path=null`、bookFile软删；该函数无索引清理调用。搜索命中能否实际留存、UX是否走errorMessage/ABANDON仍是待运行假设，**不得把源码链写成已实证场景**。
- 本轮不批准为了构造P5修改`ReaderNotesComponents`目标模型、索引产品行为或ReaderViewModel加载逻辑。编译可复用宿主不重建；测试APK变更须实核新pins、签名、runner/target/方法并冻结后才运行。

### 6.2 设备运行前置与单场景强断言

只有第4.4节两类保护差异全部关闭、设备fresh身份/owner/数据基线成立且本P5执行单已落盘后，才运行一次main/一次instrument；沿166完整恢复，不继承旧runId/scope。**这不是给仍被baseline门拒绝的批次加绕过开关。**

1. 仅创建唯一runId绑定的中性测试TXT及索引/笔记，确认正常可打开、实际索引完成，保存单独普通阅读位置基线。
2. 通过产品真实“移除正文/清缓存”入口，只操作该run测试书的内部副本；准确列出会删除的测试资产/改动行，**保留原测试导入文件和正式书库**。此有限测试创建物的产品清缓存动作属于P5加签范围，不含pm clear/VM清盘/未知文件清理。
3. 断言该测试书仍活跃、正文缓存状态和指针按真实操作改变；搜索用唯一非空marker实际命中，记录原查询/结果身份；若索引已消失或产品拒绝操作，报告P5前提不成立/NOT_REACHED，不改产品门或数据库迎合。
4. 从搜索结果真实临时入口点击，**正向确认当前reader/book身份及真实失败/不可读UX**，不能仅以搜索页消失证明正确导航。准确记录实际用户可见提示，不预设字符串或用固定sleep吞掉时序差异。
5. 在失败未提交路径按生产Back：正向证明回到**原搜索页**、查询/测试结果上下文一致、原普通阅读进度/相关持久行在许可变化白名单之外不变；不能用“更多消失”或“没有抛错”替代。
6. 无论PASS/FAIL，完整166恢复和独立保护核验必须另列。本轮不为强行验证内部ABANDON而添加生产测试开关；无法直接观测内部状态时写明行为证据与内部推断边界。

P5一次场景预计实现/编译20–30分钟、设备业务约20分钟+恢复预留20分钟；预算真实起算，恢复不中断。入口selector先按源码/已有原UI证据定位；业务失败保留原红、停止该批，不再延长timeout/替换断言进行盲循环。需新修复由协调者给出改变方法后的具体小合同。

## 7. 执行顺序、文档维护与完成判据

1. 当前设备新业务全部HOLD；先D0取证和结构diff，同时可在互斥所有权下做P5测试侧离线准备，不共写candidate/Gradle。
2. 对8个新增UI诊断文件保全归属证据，若无已有清理授权，给用户精确批次确认；本Root不代替该资产处置许可。
3. D1定点恢复或明确停点 → 协调者核inventory、外部保护、原包/设置/最终身份 → 更新current/state，恢复缺口不得被产品不存在的no-op盖掉。
4. 保护闭合后安排P5或已有C/E剩余正向断言批；EPUB解析4项不重复跑，不代替EPUB reader流。各批独立spec/fresh/166，不以当前test APK套历史接受结论。
5. 更新V0矩阵受影响行：A/B运行不再写NOT_RUN；C只认实际断言范围；D只认parser；V0-3在本次基线保护失败期间标“当前恢复/保护待闭合”，历史可核结果保留；V0-5分别列每批真实宿主/测试APK/source/spec SHA，不能继续统一套e4b7老测试包。

本轮Root实算当前candidate：宿主仍 `c396ebe14b1583180c005dabbc51184826d05a0838933203a21adf5bb72b1d31`/53899310B，ReaderRoute仍 `5368065b25a85fe1437e638ac52e02c4e4041c1bf7352bc926a0765a07ee7a0e`；**当前测试APK已为 `ad6f4c02313c7feb18a7a4fb68ba009935bcf22ff9e5daebaf740d86897c0064`/10730015B**，这是现场只读观察，不是本轮新签名/编译/设备接受。批C013500输入实际test APK是 `cb3de733e21b482b773dfadda0bd30a263c1f1a7abfdd5e01d842ceff88bae4e`/10726481B，不能用“207-r4 APK对”标签掩盖版本变化。

完成本修复的标准是**数据/metadata差异解释并按许可关闭、完整保护diff为空、门禁真实成立**，不是把程序改成不报错。D1范围裁决和P5条件加签在本文件生效；未知差异、未经授权资产删除或新产品功能不得自选fallback。

## 8. 原证、可复核结果与范围

本轮只读证据索引及before快照：`E:/creation-reading-assistant/coordination/inventory-drift-root-adjudication-20261101/evidence.json`，含preserved实算SHA/tar成员、各关键scope结果原文件SHA及08:28保护diff；`before-current.md`、`before-state.json`、`before-request.md`保留读取时原字节。

本文件没有声称当前设备已修复、漂移根因已证实、所有209恢复都失败或所有自然竞态通过；没有ADB/VM/电脑操控、源码/Git/资产删移/基线替换或新业务执行。ZCode接续时只写本裁决允许的任务差量与现态，保护原红和全部历史原件。
