# 阶段 E 最终候选：Codex MuMu 限定验收（2026-10-01）

## 结论和输入

用户明确授权使用 MuMu（目前无实体手机），并授权通过后按精确清单本地提交。限定验收 PASS；没有把模拟器结果称作实体手机验收。只操作隔离 qahost，未接触正式应用、正式书库或真实手机。

- 基线 HEAD：`513891d6a66a9b78e072ba759d34ed580d377ecf`。
- 候选：`E:\creation-reading-assistant\stage-e-final-v2-20260930\android`。
- 全部原始证据根目录：`E:\creation-reading-assistant\stage-e-final-v2-codex-acceptance-20261001`（下文路径相对此目录）。
- 精确范围：142 文件，42 ADD / 100 MOD，433 语义 hunk；未扩展原142路径集合。
- 清单：`final-freeze-accepted\final-source-manifest.csv`；逐项冻结 SHA 和候选 SHA 当前一致。
- 补丁：`final-freeze-accepted\final-semantic-from-head.patch`，SHA-256 `5211b0171b5f0bdc9c360b2a891db4aa7ff2c0acdd7a7ec847e608e7f4b9d744`。
- 从基线 HEAD 重建与候选872文件规范化一致，0 mismatch。两处继承的 EOF 空行警告保留，不能宣称 diff-check 全清。
- `accept_final.py` 汇总检查实际退出0，结果见 `acceptance.json`。

## 构建与测试

| 项目 | 实测结果与证据 |
|---|---|
| 完整 JVM | 285 XML，2628/2628，0 failure/error/skip；`final-s05-gates.log`，BUILD SUCCESSFUL，8m52s |
| Lint | 当前 `lint-results-debug.xml` 实算0错误、6警告；之前READY中的7警告为旧记录，已勘误 |
| debug / qahost / 对应androidTest | 构建成功；最终测试夹具修改后两个test APK重新构建，见 `canonical-fixture-*-test-build.log` |
| MuMu 手机逻辑布局 | `final-phone.log`，OK (43 tests) |
| MuMu 平板逻辑布局 | `final-tablet.log`，OK (9 tests) |
| 冷启动三模式 | 1080×1920竖屏，全部190帧解码；`cold-summary.json`及`cold\`原视频/逐帧图；已核对实际方向及关键图像 |

MuMu Android12/API32 x86_64，serial `127.0.0.1:16416`。手机组420dpi，平板组140dpi以扩展逻辑宽度；这是模拟布局覆盖。初轮两项reader断言错误及平板布局不合条件的失败日志保留，修正后重跑；不是以ADB退出0冒充仪器通过。

仪器52项覆盖手势8、TOC8、规则6、选区2、EPUB1、持久恢复6、Room回滚2、marker4、统计5+1、平板书架9。marker仪器4项的旧未知字段夹具产生0%是其测试合同，不承担99.4%钳制证明。

## 生产 UI / DB 链路

1. **S12**：真实WebDAV入口PROPFIND→扫描→导入受控ZIP，使用`totalChapterNum=200, ci=199`的99.5%估算，落库99.4000015%、reading、completed_at为空。打开未移动后退出，整行相同；实际翻页后换成原生locator_v2。证据 `s12-final.json`、`db\s12-final-*`、`webdav-requests.log`和对应PNG/XML。受控测试TXT夹具元信息被设为流式路径条件，不能据此宣称5MiB性能测试。
2. **S05**：实际“标记为已读完”→归档管理→“取消读完”。回到reading/100%，completed_at清空，locator及累计时长保持。`db\explicitly-completed` / `db\after-real-cancel-finished`对账；归档实际变空。旧夹具状态READY导致统计不可读，已将androidTest夹具改为规范available。消费者复核使用捕获的实际取消结果DB，随后仅修正受控书籍状态，明确记录在`db\after-cancel-canonical-content-status`；未伪称再次取消。首页、书架、搜索、详情、统计、我的、我的阅读及归档截图/节点见`s05-consumers.json`，统计在读1/读完0。新增取消入口仅归档与书架；MyReadingRoute在142范围之外，其状态消费验证通过。
3. **S07**：实际正文选区→添加批注→新建笔记→保存。摘录“受控正文用于核验进度和选区。”与选区一致，notes1条且locator_v2非空；`db\after-real-selection-note\rows.json`、`s07-*`图/XML。实际保存正文为`QA-S7-note`，不将ADB最初输入串当作实际结果。typed回源/快照清理还有4条JVM回归；未调用外部AI服务。
4. **G6**：真实“我的”时长长按不导航，DAYS偏好跨进程重启字节一致；`g6-main-persistence.json`与DataStore前后文件。≥24小时的时/天显示及语义由受控Compose用例覆盖，主应用短时长仍显示分钟。
5. **S06**：显式深色+系统浅色64帧、随系统浅色65帧、随系统深色61帧。已逐帧解码并结合关键帧/联系图目视核对，记录帧内未见相反主题整屏闪帧；不外推实体屏幕效果。

## 验收期间补修

Reader四文件闭包与4条运行期回归；namespaced ZIP WebDAV解析修复（真实红/绿MockWebServer回归）；ProfileRoute订阅legadoBackups；归档/书架恢复在读入口复用既有restoreReading；两处reader仪器选择器/既有标签断言修正；测试书状态规范化。全部包含在上述冻结142路径/433hunk。

## APK 输入 SHA-256

| APK | SHA-256 |
|---|---|
| debug | `a515cdeed9aca4a0a06c2ebebd1c9beca2087d5d1cb3df66cfeae2505ad3ced5` |
| debug test | `92a390e6111935755c5ef1f85e67850cf3ee2bd154bbaaaefe71d017aed2184f` |
| qahost | `e3704fe9eedfa5ce6af07d74e4aa7d4ce2e4ebdf774848c25d513c53f7830150` |
| qahost test | `46cb579ea644e3ce7f1ee71db8c251200bedb6c67ab60328c594e36290591527` |

最终实装对见`final-installed.json`，qahost.test指向qahost及隔离runner。实际UI与最终宿主同哈希；最后测试夹具APK更新后52项重新跑通。

## 恢复与边界

- 冷备`qahost-before.tar` SHA `76f94c208e47d7434300759b66fc0d974365878f37151e5092bc5752574cedf0`，51个持久文件逐项验证。停进程后WAL/SHM属于已合并的暂态文件，使用新鲜冷备基线。
- 恢复原APK对SHA：host `e82904cc9c1315466ed5c521693f93f541560feef038bf074ce7f7cf8324ef8a`；test `fdbd019011088505fa37d71565259a0d76f6927407af69148379e2ef7c27038d`。
- `restored.json`：51文件、0 mismatch；density280无override、night yes、stay0、user_rotation0、accelerometer1；恢复后再次读取rotation/accelerometer一致，正式包不存在。没有重新打开应用破坏冷备对账。
- 本轮临时WebDAV服务及专属ADB reverse tcp8765已停止/移除。
- 实体手机/MIUI、实际TalkBack语音、实际process-kill恢复、整包DB+文件原子性、Release与F未验；持久Room回滚结果不替代这些边界。
- 精确提交只含候选源码/配置/测试，报告与大量证据不混入。共享脏工作树不等于验证过的冻结候选；其用户资产保持原位。
