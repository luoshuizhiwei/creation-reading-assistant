# S05-④「导入完成后查看」MuMu UI 验收报告

> **日期**: 2026-09-27
> **验收范围**: qahost 隔离包（`com.creationreadingassistant.qahost`）
> **设备**: MuMu 模拟器 `emulator-5556`（SM-S9280, Android 12, SDK 32, x86_64）
> **依据**: `docs/coordination/reviews/2026-09/2026-09-27-s05-import-focus-current-review.md` §4 三场景

---

## 1. APK 核验

| 项 | 值 |
|----|-----|
| 包名 | `com.creationreadingassistant.qahost` |
| versionName | 0.5.1 |
| versionCode | 3 |
| SHA-256 | `a4669f8532055656be6341444f29eaf3a972678533e5ceb351ff34e40021a638` |
| 核验结果 | ✅ 与预期一致 |

## 2. 设备状态

### 2.1 测试前

| 项 | 值 |
|----|-----|
| wm size | 1080×1920 |
| wm density | 280（平板布局，左侧 NavigationRail） |
| rotation | auto（accelerometer_rotation=1） |
| 书架视图 | 列表 |
| 书架排序 | 最近导入 |
| 书架数量 | 29 本 |

### 2.2 临时调整

| 项 | 原值 → 测试值 | 原因 |
|----|--------------|------|
| density | 280 → 420 | 切换到手机布局，底部 NavigationBar 可见，便于操作 |

手机布局截图证据: `screens/b_phone_layout.png`（底部导航栏 y≈1859–1901 可见）

### 2.3 测试后恢复

| 项 | 恢复结果 |
|----|---------|
| wm density | 280 ✅ |
| wm size | 1080×1920 ✅（未改） |
| rotation | auto ✅（未改） |
| 书架排序 | 最近导入 ✅ |
| 书架筛选 | 全部 ✅ |

## 3. 场景结果

### 场景 1: 排序为书名 → 导入新书不在顶部 → 点「查看」→ 记录跳转目标

**结果: PASS**

| 步骤 | 记录 |
|------|------|
| 排序设为「书名」 | ✅ 书架标签显示 `29 本 · 全部 · 书名`，列表首项为既有书（具体标题不写入测试报告） |
| 导入 `zz-s05-import-B.txt` | ✅ 导入完成提示「共解析 1 本，成功入库 1 本」 |
| 新书是否在列表顶部 | ❌ 不在顶部（测试 TXT B 使用靠后的排序前缀，位于既有书和合成样本之后） |
| Snackbar「查看」按钮 | ✅ 显示「导入完成：成功 1 本，失败 0 本」+「查看」action |
| 点击「查看」后 | ✅ 书架滚动到 `zz-s05-import-B`，显示「新」角标，**未进入阅读器** |
| 跳转目标 | 书架（滚动定位到新书），非阅读器 |

**证据**:
- `screens/s1_shelf_b.png` / `ui/s1_shelf_b.xml` — 点击前书架 + snackbar
- `screens/s1_after_view.png` / `ui/s1_after_view.xml` — 点击后书架滚动到 `zz-s05-import-B`

**备注**: 首次尝试（`zz-s05-import-A`）时 snackbar 已超时消失，tap 穿透到下方的 `sample-006` 书条并打开了阅读器。第二次用更快时序成功。这说明 `SnackbarDuration.Long`（~10s）在操作延迟较大时可能不够，但「查看」逻辑本身正确。

---

### 场景 2: 保留上一批未查看 → 导入新批 → 点新批「查看」→ 证明目标是新批

**结果: PASS**

| 步骤 | 记录 |
|------|------|
| 上一批未查看书 | `zz-s05-import-B`（有「新」角标，未打开） |
| 导入新批 `zz-s05-import-C.txt` | ✅ 成功 |
| 点击新批「查看」 | ✅ |
| 跳转目标 | `zz-s05-import-C`（新批），**非** `zz-s05-import-B`（旧批） |
| `zz-s05-import-B` 可见性 | 不可见（已滚出视口） |
| `zz-s05-import-C` 可见性 | 可见，在视口顶部 y=319，带「新」角标 |

**结论**: `focusIdsForBatch()` 正确按批次 ID 过滤，未取到旧批次的 `newBookIds.firstOrNull()`。S05-④ 核心修复有效。

**证据**:
- `screens/s2_shelf_c.png` / `ui/s2_shelf_c.xml` — 点击前书架 + snackbar
- `screens/s2_after_view.png` / `ui/s2_after_view.xml` — 点击后滚动到 `zz-s05-import-C`

---

### 场景 3: 筛选排除新书 → 点「查看」→ 记录不可见反馈 + 无错误滚动 → 恢复筛选 → 确认无残留自动滚动

**结果: PASS**

| 步骤 | 记录 |
|------|------|
| 筛选设为「已完成」 | ✅ 书架标签 `9 本 · 已完成 · 书名`，新书（未读）被排除 |
| 导入 `zz-s05-import-D.txt` | ✅ 成功 |
| 点击「查看」 | ✅ |
| 反馈消息 | ✅ 显示「新导入的书不在当前筛选结果里，可调整筛选后再查看」 |
| 是否发生滚动 | ❌ 未滚动（点击前后可见书籍完全一致：`S04 PT A` → `S04 Verify A` → ...） |
| 恢复筛选为「全部」 | ✅ 书架标签 `33 本 · 全部 · 书名` |
| 恢复后是否自动滚动到 D | ❌ 未滚动（列表顶部仍为同一本既有书，测试 TXT D 不可见） |

**结论**: `onFocusHandled(false)` 正确触发消息提示；`focusNewBookIds` 在处理完后被清空，恢复筛选时无残留定位请求。

**证据**:
- `screens/s3_filtered.png` / `ui/s3_filtered.xml` — 筛选「已完成」后书架
- `screens/s3_shelf_d.png` / `ui/s3_shelf_d.xml` — 导入 D 后书架 + snackbar
- `screens/s3_after_view.png` / `ui/s3_after_view.xml` — 点击「查看」后（消息 + 无滚动）
- `screens/s3_restored.png` / `ui/s3_restored.xml` — 恢复筛选后（无自动滚动）

---

## 4. 汇总

| 场景 | 结果 |
|------|------|
| 场景 1: 书名排序 + 查看 | **PASS** |
| 场景 2: 新批目标正确性 | **PASS** |
| 场景 3: 筛选排除 + 无残留滚动 | **PASS** |

**总判定: PASS（3/3）**

## 5. 代码验证要点

| 验证点 | 代码位置 | 状态 |
|--------|---------|------|
| 「查看」入口为 Snackbar action | `ShelfRoute.kt:218-222` | ✅ 确认 |
| 点按时同步读批次成功 ID | `ShelfRoute.kt:228-229` | ✅ 确认 |
| `focusIdsForBatch` 按批次 ID 过滤 | `ShelfRoute.kt:51` + `ShelfScreen.kt:623` | ✅ 场景 2 验证 |
| 不可见时 `onFocusHandled(false)` → 消息 | `ShelfScreen.kt:150-154` + `ShelfRoute.kt:317` | ✅ 场景 3 验证 |
| 定位完成后清空 `focusNewBookIds` | `ShelfRoute.kt:314` | ✅ 场景 3 验证（无残留滚动） |
| 滚动用 `animateScrollToItem`，不打开阅读器 | `ShelfScreen.kt:156` | ✅ 场景 1 验证 |

## 6. 设置恢复

| 项 | 测试前 | 测试中 | 恢复后 | 状态 |
|----|--------|--------|--------|------|
| density | 280 | 420 | 280 | ✅ |
| size | 1080×1920 | 1080×1920 | 1080×1920 | ✅ |
| rotation | auto | auto | auto | ✅ |
| 排序 | 最近导入 | 书名 | 最近导入 | ✅ |
| 筛选 | 全部 | 已完成→全部 | 全部 | ✅ |

## 7. 清理

| 项 | 操作 | 状态 |
|----|------|------|
| `/sdcard/Download/zz-s05-import-{A,B,C,D}.txt` | 已删除 | ✅ |
| 本地临时 TXT 副本 | 已删除 | ✅ |
| 导入的 4 本测试书 | 保留在 qahost 书库（不删除阅读数据） | ✅ |

## 8. 证据归档

| 路径 | 内容 |
|------|------|
| `E:\creation-reading-assistant\codex-s05-import-focus-20260927\screens\` | 25 张 PNG 截图 |
| `E:\creation-reading-assistant\codex-s05-import-focus-20260927\ui\` | 43 个 UI 层级 XML dump |

## 9. MuMu 范围声明

本次验收仅在 MuMu 模拟器 `emulator-5556` 上执行，**未在真实 Android 设备上验证**。MuMu 模拟器的 SAF 文件选择器、Snackbar 时序行为可能与实体手机存在差异。用户已明确要求当前以 MuMu 继续设备验收；本报告结论限定为 MuMu 手机布局，不外推到实体手机。

## 10. 设备交接

验收完成，设备 `emulator-5556` 所有权交回。所有临时设置已恢复，临时文件已清理。

## 11. Codex 总指挥复核（2026-09-27）

- 目视本轮原始截图：420 dpi 有底部导航，场景 1 的目标在点击前首屏之外、点击后居于可见区域；场景 2 点击后显示本批测试 TXT C，上一批 B 不在视口；场景 3 有明确的“当前筛选结果里”提示，恢复筛选后没有跳到测试 TXT D。XML 与截图文件均存在。接受 **MuMu 手机布局下 S05-④ 三个交互场景的限定 PASS**，不称实体手机 PASS。
- 首次尝试时 Snackbar 超时，点击穿透打开过一本**既有书**。报告未提供该书操作前后 DB/阅读进度对账，因此不能据本报告断言它的状态未变化；后续只需对 qahost 中这一本做只读差异核对，不必重跑已通过的三个场景。
- E 盘截图和 UI XML 包含既有书的标题/封面，属于本地私有原始证据；本报告已去除既有书具体标题，对外分享证据前应做脱敏。报告仅给文件数量和截图路径，缺逐条 ADB 交互命令及退出码，故这部分依赖截图/XML 结果佐证。
