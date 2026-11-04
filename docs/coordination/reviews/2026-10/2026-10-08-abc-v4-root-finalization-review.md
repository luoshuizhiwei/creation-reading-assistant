# v4核查、Root窄补齐与下一停止点

## 任务编号/版本

A/D2、B/J1、C/恢复工件v4，2026-10-08。三个实际报告已交付。Root在独立E副本补齐A/C两个限定接缝；B继续限定真实旅程返修。原件保留，不重复派发A/C整包。

## 输入指纹

Root核查根 `E:/creation-reading-assistant/abc-v4-codex-review-20261008`，fingerprints.json：A4/4、B9/9、C2263/2263匹配。B三源码与candidate_build/android对应3/3，准确编译输入绑定成立。
A manifest 413f046a882979d76f62933b316a94b83166f0fcf8a765276b1063a2aaa0db11；B manifest 0fbdeab909d6f164891c511c0298fcd2f928495bda6c314f53eb4e0f86953b74；C evidence manifest 1c730ff98518902fdc864b089a474bea022821a76a4d13f992efaad1c38e002c。

Root补齐根 `E:/creation-reading-assistant/abc-v4-codex-finalization-20261008`：
- A/android/.../D2PersistenceContract.kt SHA de5cc537a2b806666a54af347471c39a0d093d18ea5e53ac5a79006cf42b9ab4。
- C/acceptance/driver/joint_driver.py SHA 3ad9ba27a1adc5624c5ea80e8a103f0fb75587c00d54bab0e4255aa703c849f6。
changed-sha256.json为精确overlay，不是全产品冻结或APK冻结。
D HEAD e39a78da9c62f02614207cffb36969a32492eaea，暂存区空。

## 所有权与改动

Root仅在新E根改A一测试合同、C一清理驱动及11项定向测试。原v4/v3、产品、runner、公共构建配置只读。C仍为后续唯一合流/冻结owner，但B核通过前不执行。下一只派B v5真实旅程余项，执行者自主准备/修复/编译/交付，不要求用户中转材料。

## Standards

当前用户偏好为发完提示词后执行者自办闭环；可查事实自行查，固定路径交付由Root读取，真正外部阻断如实报告。实际模型A自报Gemini、B自报DeepSeek、C自报Claude，不以建议模型替代事实或授权读取私有库。下一B推荐当前可用DeepSeek；GLM5.2未恢复不安排。不为利用资源新增任务。

## Spec

### A前置工件：v4＋Root测试合同补丁可采纳

DataStore双方具体Store指纹/路径全集与MAY_TOUCH_BOTH恢复要求、run证据身份均关闭。实际Store名称匹配，不制造不同名缺陷。
原SAME要求BookFile hash保留MD5，生产重建可选字段默认null；TXT预览种子20字符而生产合法更新take20000。Root允许并显式核new BookFile hash=null，继续核BookEntity hash和实际字节SHA；TXT内容仅允许按现有UTF8/LF夹具实际预览变化，其他内容字段保持，EPUB旧行保持。不改生产迁就测试。
独立D2 reviewer核补丁与实际产品吻合，Root仪器Kotlin编译成功exit0；采纳时必须使用Root这一文件。FAILED阶段快照可能为空的现有边界保留，不签失败全部快照保全。

### B：编译绑定成立，真实旅程仍需五点

保留移除DAO伪造保存、12列比较、run ID、finally引导恢复、延迟启动、诚实未覆盖多层/pending。当前页正文有text semantics，不是Canvas无正文节点。
1. SearchScreen不显示实际上下文片段，只显示第N字附近/覆盖说明；等待snippet必超时。按真实书名、正文来源、位置说明及可点击卡片选择。
2. offsets纯数字不符合SearchOffsets.parse的start:len，需查询词实际起点/长度与预览章节身份一致，不用snippet起点冒充词起点。
3. 默认分页on只响应水平拖动，swipeUp未绑定滚动模式不能证明移动；核位置变化后再核新位置持久化，不能仅updated_at增大。
4. Back→waitForIdle→立即查询并未确认异步退出保存，需真实最终保存完成后再冻结基线，不固定sleep。
5. 创建前登记全部计划ID，冲突抛错后tearDown会删未知资产；取得所有权后才记账，检查所有ID/目录，部分失败只收自己创建对象。
同步实际offset、正反marker断言及methods；不可达多层/pending仍NOT_COVERED，不新增产品功能。

### C：v4＋Root精确清理overlay可采纳为合流输入

探针故障fail-closed、集合/字节核验、item_cfg真实before/after/record/harvest链及实际冻结schema已接，Root原v4专项8/8＋repro5/5 exit0。
仍有任意unrelated-app/run路径被黑名单绕过并允许rm-rf，Root追加真实producer固定根+配置认可task/run/step/name四段正向验证，目录一律拒绝。fixed binding/fixture单文件明确例外保留。Root实际clean函数11正负用例通过：三合法producer路径、固定文件、外国app/task/run/step及目录均覆盖。不以列举foreign-task黑名单当所有权。
合流时仍需显式将generator APK路径/源码manifest与新冻结绑定，旧v1硬编码路径不得用于新APK；旧mock正向缺step需按真实producer更新，不能放松守卫迁就样例。联合真实consumer纯FakeAdb补验由唯一集成owner执行。报告147含重复套件，不作独立分母。

## 环境

无设备、无APK构建。RootA本机E隔离候选仪器编译；C纯MockAdb离线。原编译缓存复用，不删除不迁移。真实设备类型仍隔离MuMu，不是实体手机。

## 执行与结果

RootA命令：gradlew.bat :app:compileQahostAndroidTestKotlin -PcraAndroidTestBuildType=qahost --offline --no-daemon --max-workers=1 --console=plain -g E:/creation-reading-assistant/txt-persist-codex-completion-20261006/gradle-home；cwd Root A/android，BUILD SUCCESSFUL in1m22s，机器exit0存A/compile-exit.txt，完整A/compile.log。
Root原C两专项8/8、5/5 exit0存核查根C-v4-specific.log、C-repro-tests.log。
Root修订C实际清理函数11/11 exit0存补齐根C/ownership.log及逐例evidence；未调用真实adb。
原充分197产品JVM及APK身份复用，不重跑。

## 原始证据

原v4报告/manifest/源码/编译产物；Root核查fingerprints、专项日志；补齐finalize.py、changed-sha256、A编译log/exit、C/test_exact_ownership.py及逐例summary。独立review只读，设备PASS未生成。

## 恢复与副作用

只E候选/构建/证据和D交接文档写入。未安装卸载/改设备数据settings，不存在本轮设备恢复PASS。原书库/损坏仓库/桌面/HOLD和旧资产不变，D产品/Git权限未扩展。

## 未覆盖项

B真实限定旅程准备尚未核通过；联合新APK冻结和MuMu未执行。DataStore设备保护恢复、FAILED完整快照、不可达多层/pending、X整机中断、实体手机/正式书库/桌面/发布仍按边界。全产品分母未核。

## 交付状态与下一步

A/C加Root overlay经两位独立reviewer可采纳，限定合流输入2/3，B等待；不是设备前完整门禁或全产品完成。C reviewer独立内存11/11及现有配置366路径组合检查吻合，366不作独立测试分母。只续B v5，保留所有已修项。B核通过后唯一owner一次合流、定向联合编译、准确源码/新测试APK冻结与运行配置接缝；新冻结核后另给限定MuMu。用户无需中途配置、补日志或转交文件。
