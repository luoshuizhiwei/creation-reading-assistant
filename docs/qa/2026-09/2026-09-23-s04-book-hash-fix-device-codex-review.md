# S04 Book hash V4 真机复验 Codex 回读（2026-09-23）

来源：[WorkBuddy 报告](2026-09-23-s04-book-hash-fix-device-retest.md)与[脱敏截图](../evidence/2026-09-23-s04-source-refresh/redacted/06_retest_v4_shelf_state.png)。Codex 仅只读检查现有 APK、私有 V4 文件及前后数据库副本；不连接或操作手机。

## 可独立核实的结果

- 当前 Debug APK 大小 52,481,150 字节，SHA-256 为 `85788A8008629E220EC76DDC986150E557F9D0BB3438B0AA36C9013582B79A5F`，与报告一致且不同于旧包。私有 V4 测试文件为 460 字节，MD5 `82fd5133f19480e1c00b51d8e1327f34`、SHA-256 `0eaeeedcab04e8bf605a2ff08e091f3768b69b17d3c3d7d899a659db05d8b399`，与报告一致。
- 对现存 `retest_pre` 和 `retest_post` SQLite 主库及 WAL 副本执行只读查询：测试书 `books.size/content_hash` 从 `419/V1` 变为 `460/V4`；`library_source_refs.size/content_hash` 从 `419/V3` 变为 `460/V4`。后置两表哈希与私有 V4 文件 MD5 完全一致。原文索引仍为 full 1/1，新英文测试词由 0→1 行，旧英文测试词由 1→0 行。
- 使用此前基线记录的全部 11 个原有书 ID（含 1 个非 UUID ID）逐项对比 `books`、`book_files`、`reading_progress`、`library_source_refs` 的既定选取列：前后行数为 11/11/7/11，各表所选列均相同。首次仅按 UUID 筛选会漏掉 1 本，已改为按基线完整 ID 集比较。此结论不扩展为所有表、所有列或设备外部文件均未变化。
- 脱敏截图 06 可见书架共 12 本、测试书不再显示“可更新”角标；其余书封面/标题已遮盖。截图不能证明数据库哈希，数据库副本提供该部分证据。

## 限定项

- `install_with_confirm.ps1` 退出码 0 和 `CONFIRM_LOOP`、设备内部正文文件 MD5、竖屏及常亮设置前后值，目前主要依据 WorkBuddy 报告；未找到独立安装原始日志或内部正文文件副本，不把这些项目称为 Codex 现场复测。
- 本轮只有一条隔离 TXT 小文件 V4 真机路径。Markdown 的 36 项 JVM 中有定向用例，但未真机复验；EPUB 仍有 `books.content_hash` 陈旧风险，大文件清旧 hash 无专项真机证据；Provider 具体系统异常及失败原子性未覆盖。原有隔离测试书与外部来源仍保留在手机，未经用户决定不删除。
- 原报告“复核者 Codex”发生于独立回读前，已改成指向本页；Provider 具体故障“JVM 闭环”过度声明已收窄。

## 裁决

**S04 的 TXT 小文件 Book/source hash 修复：真实设备自然路径 PASS。S04 父项仍 PARTIAL。** 下一步优先处理已识别的 EPUB 同类陈旧哈希风险；保持与 TXT/MD 及用户真实书库分离。当前无需重复本次 V4 测试或清理测试书。
