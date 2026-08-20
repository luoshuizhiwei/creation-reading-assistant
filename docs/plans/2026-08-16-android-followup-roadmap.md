# 原生 Android 后续路线图（2026-08-16）

> 状态：**现行计划**。覆盖 2026-08-16 两批功能交付（R1 假功能修复+快赢、R2 中型功能）
> 之后的工作安排。前置事实：
> - 两批共 16 项改动已在工作区完成并验证（1354 项 JVM 测试 0 失败 + lintDebug 通过），
>   **全部未提交**，叠加在 reader 引擎 WIP（未提交）之上。
> - P0-A3 发布链路（构建/签名/版本注入/检查更新）已收口，但**从未实际发版**。
> - 替换净化规则接线设计已落档：`plans/replace-rules-render-integration-design.md`。

---

## P0：收口与真机验证（立即，其他一切的前置）

### 0.1 工作区收口提交

当前工作区混有三类未提交改动，需要分层提交（建议顺序）：

1. **reader 引擎 WIP**（分页/文档/MainActivity 等约 20 文件）——由其所有者先收口；
   注意 `android-reeden-mobile` worktree（分支 `codex/android-reeden-mobile`）若为同一
   工作流的并行分支，先明确两边合并顺序，避免 pager/doc 文件二次冲突。
2. **示例书移除的测试侧**（ShelfImporterTest/ShelfViewModelTest/BookViewModelTest
   已清理死引用）——与 1 同属一个语义单元，建议同批提交。
3. **2026-08-16 两批功能改动**（色温/纹理/灵感跳转/删除文案/EPUB 封面/全局搜索正文/
   TTS 接续与拔耳机/方向锁定/进度交互/灵感状态筛选/语速滑条/书架筛选/内容哈希/
   AI 预检/TOC 书签/导出增强/StrictMode）——WIP 提交后再提，保持 diff 可审。

验收：工作区干净；每层提交后 `testDebugUnitTest + lintDebug` 全绿。

### 0.2 真机验证清单（真实设备，遵守 AGENTS.md 约束）

按功能逐项过（`adb devices` 先核对，长测临时开 `stay_on_while_plugged_in` 后恢复）：

| 功能 | 验证点 |
|------|--------|
| TTS 章末接续 | EPUB/MD 末句播完自动翻章续读；末章正常停止；手动停止不受影响 |
| TTS 拔耳机 | 播放中拔有线耳机/断蓝牙自动暂停，不外放 |
| TTS 语速滑条 | 0.5–2.0x 拖动即时生效；拖动不重读 |
| 色温 | 护眼开启时色温滑条有可见暖色变化；强度仍独立生效 |
| 纸张纹理 | 外观开关关闭后阅读器噪点消失、重开恢复 |
| EPUB 封面 | 真实 EPUB 导入后书架显示内嵌封面；用户手动封面不被覆盖 |
| 方向锁定 | 竖屏/横屏锁定生效，退出阅读器恢复，尊重系统旋转锁 |
| 进度交互 | 底栏/进度 Sheet 拖动显示百分比预览；百分比输入精确跳转 |
| 灵感 | 「定位来源」打开来源书；状态筛选正确参与过滤 |
| 书架 | 格式筛选、标签多选（交集语义）、TOC 书签跳转 |
| 全局搜索 | 正文关键词能命中书籍结果 |
| 内容哈希 | 同内容改名文件二次导入被判重；>32MB 文件正常导入（回退弱指纹） |

顺带：StrictMode 已在调试构建启用（penaltyLog），真机跑一轮
`adb logcat -s StrictMode` 记录违规清单（已知一处：`PagedEpubContent` 组合期
`File.lastModified()`），列入下批修复。

### 0.3 gap-audit 真机收尾项（与 0.2 同场完成）

- **A8**：Room 1→9 全链迁移真机复核（模拟器 CI 已覆盖，真机人工过一遍）。
- **A1**：50MB 级「测试 TXT」压力验证（打开/目录/搜索/翻页/TTS/退出重进，无 ANR/OOM）。

---

## P1：首次正式发版（P0 完成后）

1. **配置签名 Secrets**（用户操作，一次性）：按 `docs/release/ANDROID_RELEASE.md` §2.4
   添加 `ANDROID_KEYSTORE_BASE64 / ANDROID_KEYSTORE_PASSWORD / ANDROID_KEY_ALIAS /
   ANDROID_KEY_PASSWORD` 四项；keystore 按 §2.3 生成并妥善保管。
2. **首发 tag**：`git tag android-v0.5.0 && git push origin android-v0.5.0`
   （版本号与桌面端 `package.json` 解耦，Android 独立演进）。
3. **流水线实测**：`build-android-release` job 全绿；Release 附件齐全
   （APK + GPL 源码包 + SHA256SUMS-android.txt + 许可证）；apksigner 校验日志正常。
4. **安装与升级验证**：真机覆盖安装（若此前装过 debug 兜底包需先卸载——证书不同）、
   书架/进度/笔记数据不丢；应用内「检查更新」能发现新版本并直达 Release 页。
5. 更新 `CHANGELOG.md` 与发布说明。

> 红线：本地 debug 兜底签名产物严禁外发；Secrets 不落仓库。

---

## P2：质量门禁补齐（与 P1 并行或紧随）

| 项 | 内容 | 验收 |
|----|------|------|
| CI 设备测试 | 评估把 `android-migration-tests` 模拟器 job 扩展到 Compose 关键路径类（分批，控制时长） | 至少 Reader 关键路径类在 CI 执行 |
| A7 剩余 | 同步/WebDAV 两端真机↔桌面冒烟；UI 层重复点击/离线重试场景 | 无静默丢数据；冲突可解释 |
| A9 剩余 | EPUB 异常语料矩阵（无 TOC/坏 ZIP/路径穿越/超大图等）+ 首屏/翻页/内存基线记录 | 异常均进可退出错误态；基线入档 |
| A12 | 无障碍与资源化：约 337 处硬编码中文收口到 `strings.xml`、补 `contentDescription`、清 85 lint warnings | 正式对外分发前必须完成 |
| StrictMode 违规 | 按 0.2 收集的清单清零主线程 I/O | 调试构建 logcat 无 StrictMode 违规 |
| 桌面端 A10 | 修 4 个失败的 `splitTxtChapters` 用例并把桌面单测/构建加入 CI | 75 测试全绿（非移动端，同仓债务） |

---

## P3：功能深化（按性价比排序，P0 后可开始）

1. **替换净化规则接入正文** —— 设计已就绪（`replace-rules-render-integration-design.md`
   四片方案）。**前置：reader WIP 收口合并后**（文件所有权冲突）。工作量 3–5 天，
   真机验收（既有高亮不错位）是硬门槛。
2. **阅读目标 + streak 打卡 + 提醒通知** —— 设计已就绪（`2026-08-16-reading-goal-streak-design.md`：
   片 0 本地会话写入补齐、片 1 统计口径统一、片 2 `GoalStore` + 进度环、片 3 WorkManager 提醒通知）。
   streak 已算好；补每日/每周目标设置、Stats/Home 进度环、提醒通知
   （`POST_NOTIFICATIONS` 权限已申请未用）。通知渠道行为需真机验证。工作量 3.5–4.5 天。
3. **TOC 已读标记持久化 + 分类/标签/书单手动排序** —— **数据底座已于 2026-08-20 验收**：
   schema v10、`chapter_reads` Entity/DAO、9→10 与 1→10 迁移、三类 `sort_order`
   DAO/Repository 均已通过 JVM 与真实手机测试。后续仍需接章节到达写入、删书/手动清理、
   TOC 弱化样式与计数，以及分类/标签/书单三个完整管理入口；不实现书单内书籍排序。
4. **backlog 候选池**（`plans/legado-feature-backlog.md`，候选非承诺）：
   热力图增强、全书页码/剩余页数（等 A9 排版性能基线后评估）、简繁转换、
   主题三件套、点击区域动作自定义、AI 批注层、共享元素转场、手柄/滚轮。

---

## 明确不做 / 悬置

- **TTS 选引擎入口**：无官方直达 Intent，脆弱 hack 不做（保留现有"去系统设置"提示文案）。
- **书源引擎 / RSS / 云端 TTS / MOBI-PDF**：产品边界外（backlog 第五节）。
- **替换规则按 ReadingUnit 拆开投影**：seam 契约禁止。

---

## 建议节奏

```
本周      P0.1 收口提交 + P0.2/P0.3 真机验证（一场真机 session 可完成大部分）
下周      P1 发版闭环（Secrets → 首发 tag → 安装升级验证）
并行      P2 按表推进（A12 资源化建议拆成每日一小批，避免单次大 PR）
WIP 合并后 P3.1 替换规则接线（最优先）、随后 P3.2 阅读目标
```

维护规则：完成一项后回填本文件状态；被新计划取代时移入历史参考。
