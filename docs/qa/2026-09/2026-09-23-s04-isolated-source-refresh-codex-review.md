# S04 隔离来源真机验收 Codex 回读（2026-09-23）

来源：[WorkBuddy 验收报告](2026-09-23-s04-isolated-source-refresh-device-acceptance.md)和[脱敏证据](../evidence/2026-09-23-s04-source-refresh/README.md)。本轮只读检查已有本地文件和离线数据库副本；未连接或操作手机，未重跑 Gradle。

## 可确认的进展

- 脱敏截图 03–05 分别显示更新确认弹窗、取消后测试书的“可更新”角标、确认后角标清除。截图没有可见的原有书名；原图不应提交。
- 仓库外测试文件 V1/V3 的新鲜 MD5 分别为 `4ac50fb1f4d52196556df5190fb4ac37` / `1b79b626f94a6e1869e402ff575912cf`。只读查询 `s04_imported` 和 `s04_final` 数据库副本：测试书 `size` 从 394 到 419；来源引用 `content_hash` 推进为 V3；原文索引 coverage 为 full 1/1，新英文测试词有 1 条倒排行，取消路径测试词为 0 条。此证据支持隔离测试书的确认更新与索引自然路径，但不证明文件替换在崩溃/断电时的原子性。
- 运行现存 `verify_final.py`，脚本对原有 11 本在 `books`、`book_files`、`reading_progress`、`library_source_refs` 中选取的字段计算摘要，前后相同。它未比较所有表、所有列或原有外部源文件，不能扩大为“用户所有数据零变化”。

## 阻断差异与证据修正

- **S04 哈希一致性未通过**：测试书 `books.size=419`，但 `books.content_hash` 仍为 V1 MD5 `4ac50fb1f4d52196556df5190fb4ac37`；同书 `library_source_refs.size=419`、`content_hash` 已为 V3 MD5 `1b79b626f94a6e1869e402ff575912cf`。离线 `s04_confirmed` 与 `s04_final` 副本均如此。源码 `ShelfImporter.reselectPlainText` 的 `existing.copy(...)` 更新 size 和路径却未重算 `content_hash`；`refreshSourceBaseline` 仅更新来源引用。`books.content_hash` 仍参与导入去重、同步导出，属于实际持久状态不一致，不能按原报告 PASS 收口。
- 原报告以 394 字节的长度检查推断“确认前字节绝不变”，长度相同不足以证明内容逐字节相同；当前证据支持长度未变，未取得该时间点内部副本哈希。其“原子替换”也未有故障注入证据。
- 原报告曾把用户私有书库的具体父目录写入正文，现已脱敏；测试对象只标识隔离子目录及测试书 ID。报告自称“复核者 Codex”发生于独立回读前，已改为指向本页。原称 `SourceRefreshCoordinatorExceptionTest` 在当前源码树不存在；`ShelfImporterTest` 仅有 provider null 时间戳和 query 返回 null 模拟，不能据此宣称具体系统异常均已覆盖。
- 隔离测试书与其外部测试来源目前按报告留在手机，未经授权不删除；相关私有原图及数据库副本留在仓库外。

## 裁决

**S04 隔离真机验收：PARTIAL，有一项需修复的 P2 元数据一致性问题。** 确认/取消 UI 与来源引用、单书索引自然路径有证据；`books.content_hash` 陈旧阻断 S04-3/4 完整收口。下一步先由开发窗口在限定的 `ShelfImporter.kt` 与定向测试内修复并验证小文件刷新后的 Book/source hash 同步及失败保护；再由独立验收窗口只用现有隔离测试书定向复验，不重测原有 11 本或全量安装流程。大文件候选指纹、EPUB 刷新、Provider 故障和多书批量仍按各自现有证据边界标记未覆盖。
