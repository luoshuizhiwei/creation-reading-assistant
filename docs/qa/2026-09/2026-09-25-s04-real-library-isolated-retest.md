# S04 修复后独立竖屏真机验收（隔离真实书库样本）

- 日期：2026-09-25
- 验收人：Claude（独立验收窗口）
- 设备：Redmi K50 Ultra，序列号 `c49ac6cf`（全程 `adb -s c49ac6cf` 明确指定，竖屏，未用 MuMu，未旋转）
- 被测 APK：`android/app/build/outputs/apk/debug/app-debug.apk`，53,532,616 B，SHA-256 `53d0c01b0d36dc19690dc1f62834f19fc61634a2dd394de2b23efc9da85359ac`
- 仓库/分支：`creation-reading-assistant-recovery-20260919` @ `codex/recover-worktree-20260920`

## 0. 结论速览

| 步骤 | 项目 | 判定 |
|---|---|---|
| 1 | 设备/方向/serial 约束 | PASS |
| 2 | 恢复点可靠（弃用上轮 snap-02） | PASS |
| 3 | 真实来源 ↔ 隔离副本字节对应 | PASS |
| 4 | 同一 URI 换 V2 → 同步取「新正文 + 一致新基线」+ 新文案 | **PASS** |
| 5 | 无效 EPUB 在另一新对象上被拒，零副作用 | **PASS** |
| 6 | 弹窗不再承诺主动重置进度，且与实测字段一致 | PASS |

未覆盖项与边界见 §8。

## 1. 设备与安装归因

- `adb devices` 仅列出 `c49ac6cf`（entries=1），全程 `adb -s c49ac6cf`。
- 方向：竖屏（截图 1220×2712 全程不变）。
- `stay_on_while_plugged_in`：验收前后均为 `3`，**未修改**。
- 安装：先走 `android/scripts/install_with_confirm.ps1 -Apk <apk> -Serial c49ac6cf`，exit=0，日志 `RESULT: CONFIRM_LOOP (installed through the MIUI installer UI)` —— **脚本通过，非直接安装回退**。
- 归因核验：`pm path` → `base.apk`，设备端 `sha256sum` = `53d0c01b0d36dc19690dc1f62834f19fc61634a2dd394de2b23efc9da85359ac`，与待测 APK 完全一致。安装后 `lastUpdateTime=2026-09-25 02:12:17`。
- 构建窗口协调：窗口 C 于 02:1x 跨会话通知「Gradle 构建已全部结束，设备归你独占」后方才安装；安装前 `.gradle` 目录无活跃锁。

## 2. 恢复点（写入前）

- 路径（仓库外私有目录，不入仓）：`<repo-external-private-dir>/cra-s04-retest-pre.tar`
- 生成方式：force-stop 后 `exec-out run-as <pkg> tar -cf - -C /data/data/<pkg> databases files shared_prefs`
- 体积 2,270,021,120 B，SHA-256 `71df85bdff9db3f0f01c4fd7570d3fd5bfb77c1f40d0cb508fe7954f62f14f26`，`tar -t` exit 0，68 条目录项，顶层恰为 `databases/ files/ shared_prefs/`。
- **未沿用上轮体积异常的 snap-02**；本轮恢复点核验通过，才允许后续任何写入。

## 3. 真实来源 ↔ 隔离副本

- 样本：用户真实书库中一册 7.15 MB EPUB（报告中书名与父路径一律脱敏为「测试 EPUB」/`<isolated-dir>`）。
- 原书：7,149,850 B，MD5 `b26563045d753283975d27295465bd36`。
- 隔离副本：`<isolated-dir>/test-epub-v1.epub`，`cp` exit 0，字节级一致（同 size/MD5）；复制后复读原书 hash 未变。
- V2 构造：在首个可读 xhtml（titlepage.xhtml）`</body>` 前注入 `<p>S04RETESTV2MARKER …</p>`，保持 mimetype 为首条 STORED；V2 = 7,149,926 B，MD5 `92ead790e20793e6f89c2371ee69b0ce`，与 V1 互异。
- 未使用 1478 B 合成文件冒充真实规模。

## 4. 核心项：同一 URI 换 V2 → 同步

测试书 `epub_-xmmobn`（V1 导入），阅读进度预置：`progress_percent=0.650182008743286`，`chapter 5`，`updated_at=2026-09-24T18:01:59.934Z`。随后将 `<isolated-dir>/test-epub-v1.epub` **同路径**覆盖为 V2（`cp` exit 0，mtime 1790271611→1790273034）。

操作：书架下拉刷新 → 弹窗 → 点「重新同步」。

### 4.1 弹窗文案（步骤 6 观测点）

实际文案（uiautomator 原文，r10_new_dialog.png；书名已脱敏，弹窗涉及 2 本书的计数为对账事实）：

> 同步源文件更新？
> 检测到 2 本书（test-txt-stageA、测试 EPUB）的源文件已被修改，是否重新同步正文？**同步不会主动清除已有阅读记录；但正文更新后章节会重新划分，阅读位置也可能随之变化。**

- 脱敏说明：弹窗中第二本书显示的是本轮真实样本的书名，报告中统一以「测试 EPUB」指代；test-txt-stageA 为上轮自建的中性命名测试书，保留原名。
- 不再出现旧文案「同步后将重置章节与阅读进度」。
- 「2 本书」含上轮遗留的 test-txt-stageA（其源同样被改过），非本轮对象；本轮只对 `epub_-xmmobn` 取证。

### 4.2 同步后文件 + DB 三方一致（通过判据）

| 字段 | V1 基线 | 同步后 | 判定 |
|---|---|---|---|
| 内部副本 `files/books/epub/epub_-xmmobn.epub` | 7,149,850 / `b2656304…` | **7,149,926 / `92ead790…`** | ✓ 新正文落盘 |
| `books.size` / `content_hash` | 7149850 / `b2656304…` | **7149926 / `92ead790…`** | ✓ |
| `library_source_refs.size` / `content_hash` | 7149850 / `b2656304…` | **7149926 / `92ead790…`** | ✓ 与 books 一致 |
| `refs.last_modified` | 1790271611017 | **1790273034217** | ✓ 采用新 mtime |
| `books.updated_at` | 17:50:19 | **18:14:05** | ✓ 目标书推进（对账修正：见下） |

> 对账修正（2026-09-25 独立复核）：「仅该书推进」原表述不准确。事后用测试前恢复点与
> 同步后快照逐本对账发现，弹窗中的另一本书（test-txt-stageA，上轮遗留源变更）也被本轮
> 同步一并处理：其 `books.updated_at` 15:07:46.088 → 18:14:05.206（与目标书同一秒），
> `library_source_refs` 的 size/hash/last_modified 对齐至源文件现状（253 B / `63c0b4e1…` /
> 1790262281000）。该书的 books 内容基线在本轮前已与源一致，本轮变化是 refs 对齐 +
> updated_at 推进。其余 18 本既有书的 books/refs 关键字段及全部 10 条既有进度行逐字段
> 零差异。详见 `2026-09-25-s04-retest-independent-reconciliation.md` §3。

-  marker 核验：从设备内部副本解出 titlepage.xhtml，`S04RETESTV2MARKER` 存在（字节级证据）。阅读器 UI 中该 marker 所在 SVG 标题页的文本不被渲染，故 UI 截图不可见 marker —— 以字节证据为准。
- 无 `.importing` 暂存残留（同步后 `ls files/books/epub/` 无暂存文件）。

### 4.3 进度字段（与新文案对照）

| 字段 | 同步前 | 同步完成后立即导出 | 判定 |
|---|---|---|---|
| `progress_percent` | 0.650182008743286 | 0.650182008743286 | ✓ 同步未触碰 |
| `current_location_json` | `{"chapter":5,…"offset":5137,"ci":5…}` | 同 | ✓ |
| `updated_at` | 2026-09-24T18:01:59.934Z | 2026-09-24T18:01:59.934Z | ✓ 同步未触碰 |

后续我打开该书翻页验证正文可读，进度变为 `0.028 / chapter 1 / 18:23:29` —— 这是**用户式继续阅读**触发的正常进度保存（章节重排后落点变化），时间戳与同步时刻（18:14）分离，恰好实证新文案的后半句「正文更新后章节会重新划分，阅读位置也可能随之变化」。**不得**将此误读为同步主动重置。

## 5. 无效 EPUB 拒绝（另一新对象）

新对象 `epub_-3veupq`（由隔离目录内 V1 字节另存为 `test-epub-second.epub` 导入，7149850 / `b2656304…`）。无效文件 `<isolated-dir>/invalid-test.epub`（48 B 纯文本，MD5 `ad46689cbfa1f8335ba1d2878d14c261`）。

操作：长按该书 → 重新定位文件 → 选 invalid-test.epub。两次尝试结果一致。

| 检查项 | 基线 | 操作后 | 判定 |
|---|---|---|---|
| 内部副本字节 | 7149850 / `b2656304…` | 7149850 / `b2656304…` | ✓ 字节级未变 |
| `books` 行 size/hash/updated_at | 7149850 / `b2656304…` / 18:29:40.580 | 同 | ✓ 未推进 |
| `refs` size/hash/last_modified | 7149850 / `b2656304…` / 1790271688000 | 同（仅 `last_seen_at` 推进，属扫描心跳） | ✓ |
| 可见反馈 | — | toast「修复失败：zip END header not found」（r15_toast.png） | ✓ 明确拒绝 |
| 孤儿/暂存 | 上轮 90 B `epub_-dgcgpe.epub` | 仍是唯一孤儿，无新增、无 `.importing` | ✓ |

- 未清理上轮遗留现场文件，未合成 `.backup`（阶段 B 不在授权范围）。
- invalid-test.epub 源文件操作后 MD5 未变。

## 6. 文案语义与实测的一致性

新文案承诺两点：(a) 不主动清除阅读记录 —— §4.3 同步前后逐字段一致，证实；(b) 正文更新后章节重排、位置可能变化 —— 继续阅读时落点按新章节保存，证实。**但本验收仅覆盖这两条**，不据此推断同步语义「全面正确」；章节重排的落点映射策略（原 chapter 5 → 新位置如何选取）未在授权范围内深挖。

## 7. 命令与退出码摘要

| 环节 | 命令（关键） | exit |
|---|---|---|
| 安装 | `install_with_confirm.ps1 -Apk … -Serial c49ac6cf` | 0（CONFIRM_LOOP 通过） |
| 设备 APK 归因 | `pm path` + `sha256sum base.apk` | 0，hash 匹配 |
| 恢复点 | `exec-out run-as tar -cf -` | 0（2.27 GB，tar -t 0） |
| V1/V2 复制 | `cp`（设备内隔离目录） | 0 / 0 |
| 同步 | UI：下拉刷新 → 重新同步 | 弹窗消失，结果见 §4.2 |
| DB 导出 | force-stop → `exec-out run-as tar` | 0（integrity_check ok） |
| 无效拒绝 | UI：重新定位 → invalid-test.epub | toast 拒绝 ×2 |

完整 UI XML 转储与原始截图存于仓库外私有目录，不入仓。

## 8. 未覆盖项与边界

1. **真实规模单一**：样本 7.15 MB 一册，不代表所有真实规模/排版（多卷、超大、加密、嵌套 OPF 路径等未覆盖）。
2. **手工 UI 验收**：全部经 `adb shell input` + uiautomator 观测，非 instrumentation PASS；MIUI 阻断 Compose instrumentation（result code 102）的既有约束未变。
3. **弹窗多书提示**：弹窗同时列出上轮遗留 test-txt-stageA；本轮只对 `epub_-xmmobn` 逐字段取证。**（对账修正）** 事后对账确认 test-txt-stageA 在本轮同步中也被更新（refs 对齐 + `books.updated_at` 推进），原报告验收时未将其纳入判据属取证范围缺口，已由独立对账补齐（见 `2026-09-25-s04-retest-independent-reconciliation.md` §3）。
4. **落点映射策略**：章节重排后旧进度的映射规则未深验（仅确认「同步不主动清记录 + 继续阅读按新章节保存」）。
5. **入口 B（重新定位文件 → 保留阅读记录）** 的文案强度错配仍未改（本轮授权外，见 `docs/coordination/legacy-stages/2026-09/2026-09-24-s04-progress-dialog-copy.md`）。
6. **阶段 B（残留清理/.backup 语义）** 未在授权范围，未执行。
7. 截图含真实书架/封面，原始件在仓库外；报告引用编号 r09–r15 仅作私有索引，如需入仓须先脱敏。

## 9. 工作区状态

- 仓库无 commit/push/stage/reset/checkout/clean；仅新增本报告文件。
- 设备遗留：隔离目录 `<isolated-dir>/` 内含 test-epub-v1.epub（现为 V2 字节）、test-epub-v2.epub、test-epub-second.epub、invalid-test.epub；书架含 `epub_-xmmobn`（V2）、`epub_-3veupq`（V1）两本测试书；上轮孤儿 `epub_-dgcgpe.epub` 保留未动。用户原书全程未修改。
- `stay_on_while_plugged_in=3` 未变。
