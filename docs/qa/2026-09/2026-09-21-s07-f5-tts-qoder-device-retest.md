# S07-F5-TTS 最后单项真机补验 — Qoder

> 补验 Agent：**Qoder**　|　任务：S07-F5 唯一遗留项 —— TTS 程序化滚动分支的选区可见性失效
> 工作仓库：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`
> 工作分支：`codex/recover-worktree-20260920`
> 固定比较基线：`origin/main` = `5aed5939600e491843e3c44453921bd6273a233c`
> 开工与收工 `git rev-parse --short HEAD` = `5aed593`（基线未推进）
> 主报告：`docs/qa/2026-09/2026-09-21-s07-f5-tts-qoder-device-retest.md`（本文件）
> 证据目录：`docs/qa/evidence/2026-09-21-s07-f5-tts-qoder-device-retest/`
> 上游报告：`docs/coordination/agent-results/agent-result-s07-f5-zcode.md`（执行）、`docs/coordination/reviews/undated/agent-review-s07-f5-qoder.md`（代码/JVM 轴）、`docs/qa/2026-09/2026-09-21-s07-f5-qoder-device-retest.md`（D-1/D-3/D-7 真机复验）
> 判定：**PASS** —— 建议关闭 S07

---

## 0. 结论速览

| 判据（任务书「最终判定」6 条） | 结果 |
|---|---|
| 1. TTS 确实启动并自行推动滚动 | ✅ **PASS**（媒体会话 `PLAYING(3)`；logcat 逐句 `speak sync Begin` 自动推进并跨章） |
| 2. owner 离开可见范围后活动选区与工具栏自动清除 | ✅ **PASS**（`16:30:47` 仍在 → `16:31:03.711` 清除，与 TTS 跨入下一章同一时刻） |
| 3. 返回原位置后旧选区不复活 | ✅ **PASS**（滚回第4章，工具栏标记 0） |
| 4. 新选区仍可正常建立 | ✅ **PASS**（`16:33:43` 新选区 + 完整工具栏） |
| 5. 无崩溃 / ANR / 错误书籍写入 / 用户数据修改 | ✅ **PASS**（无 FATAL/ANR；进程未重启；未新增高亮或笔记；未触碰用户真实书籍） |
| 6. 临时权限、设备设置、测试 TXT readerMode 全部恢复 | ✅ **PASS**（逐项回读，见 §7） |

**核心旅程：PASS。消费者快照 × TTS 组合：NOT TESTED（UI 结构上无法同时启动 TTS 与保持消费者工作表打开，见 §6.3）。**

---

## 1. 硬性边界遵守

| 边界 | 结果 |
|---|---|
| 只用真实手机，禁止模拟器 | 全程 `adb -s c49ac6cf`；`adb devices -l` 见 §2 |
| 开始时执行 `adb devices -l` 并重新确认序列号 | 已执行，确认 `c49ac6cf` |
| 所有设备命令显式 `-s <serial>` | 是 |
| 不修改产品源码 / 测试源码 / Gradle / **数据库内容** | 未修改。DB 全程只用 `run-as cat` **复制到主机**做只读查询（`mode=ro` URI），未写回设备 |
| 不 commit / push / stage / stash / reset / clean | 未执行；收工 `git diff --cached` = 0，`git stash list` = 0，HEAD `5aed593` |
| 不删除测试书，不打开/修改/删除用户真实书籍 | 未删除任何测试书；本轮**只打开测试 TXT / 测试 EPUB**（首页"继续阅读"里的两本中性夹具），未进入任何用户真实书籍 |
| 报告/截图/日志只用中性名称 | 全文只用 测试 TXT / 测试 Markdown / 测试 EPUB；夹具内部名 `t-txt-s07` 为我生成的中性名 |
| 默认复用已安装的 F5 APK | **已复用**，未重新构建、未重新安装（哈希证明是同一份，见 §2.2） |
| 不处理 D-2 / N-1 / S08–S20 / 其他缺陷 | 未处理，未领取 |
| 不重新审查 D-1，不再做 D-7 gfxinfo 对照 | 未做 |
| 不得用手指滚动代替 TTS 自动滚动 | 清除发生在 **TTS 自主推进**期间（§5）；之后的"滚回原位置"是任务书第 7 步要求的定位动作，不参与清除判定 |

### 1.1 允许的临时权限操作

| 步骤 | 值 |
|---|---|
| 测试前状态 | `android.permission.POST_NOTIFICATIONS: granted=false` |
| 临时授予 | `adb -s c49ac6cf shell pm grant com.creationreadingassistant android.permission.POST_NOTIFICATIONS` → 退出码 0，回读 `granted=true` |
| 恢复 | `adb -s c49ac6cf shell pm revoke …` → 退出码 0，回读 **`granted=false`**（§7） |
| 其他 MIUI / 长期系统设置 | 未修改 |

---

## 2. 设备与安装包

### 2.1 设备

```
$ adb devices -l
List of devices attached
c49ac6cf    device product:diting model:22081212C device:diting transport_id:1
```

| 项 | 值 |
|---|---|
| 序列号 | `c49ac6cf`（真实手机） |
| 机型 | `22081212C` / `product:diting` |
| 分辨率 / 密度 | 1220x2712 / 480dpi（`wm size`、`wm density` 与上一轮一致） |

### 2.2 安装包：确认为本轮 F5 APK，直接复用

```
$ adb -s c49ac6cf shell pm path com.creationreadingassistant
/data/app/~~ZfrvScsNVVFSBwE_tYyRcg==/com.creationreadingassistant-aOazHpHX0HOxoWcAdUAtGg==/base.apk

$ adb pull <base.apk> && sha256sum
3164f41aae9616fd98d4d7ac63a74597d412429e494c1b56edfd08423881909c   # 设备上已安装

$ sha256sum android/app/build/outputs/apk/debug/app-debug.apk
3164f41aae9616fd98d4d7ac63a74597d412429e494c1b56edfd08423881909c   # 本轮 F5 构建产物
```

| 项 | 值 |
|---|---|
| 设备 APK 与 F5 构建产物 SHA256 | **逐字节一致** → 是本轮 F5 APK |
| 是否重新构建 / 重新安装 | **否**（任务书：默认复用） |
| `versionName` | `0.5.1` |
| `lastUpdateTime` | `2026-09-21 13:02:19`（上一轮 `install_with_confirm.ps1` → `RESULT: CONFIRM_LOOP` 的那次安装） |
| `firstInstallTime` | `2026-09-19 18:38:36`（未变，说明未清数据） |

> 因此本轮**不涉及安装脚本判定**，也就没有"回退成功"或"脚本通过"的任何新说法。

---

## 3. 测试前状态（全部在改动前采集）

| 类别 | 键 | 测试前值 |
|---|---|---|
| 设备 | `system/stay_on_while_plugged_in` | `3`（**未修改**，全程不需要临时改） |
| 设备 | `system/accelerometer_rotation` | `1` |
| 设备 | `system/user_rotation` | `0` |
| 设备 | `system/font_scale` | `1.0` |
| 权限 | `POST_NOTIFICATIONS` | `granted=false` |
| 全局 | `reader_mode` | `paged` |
| 全局 | `pager_engine_mode` | `on` |
| 全局 | `epub_pager_engine_mode` | `auto` |
| 全局 | `reader_page_turn_effect` | `none` |
| 全局 | `reader_screen_orientation` | `system` |
| **per-book** | 测试 TXT `d5bd07ef…_readerMode` | **`paged`** ← 本轮要恢复的目标值 |
| per-book（只读记录，未动） | 测试 Markdown `9a85d098…_readerMode` | `scroll` |
| per-book（只读记录，未动） | `202a634c…_fontSize` | `25.0` |
| per-book（只读记录，未动） | `epub_zhrum_fontSize` / `lineHeight` / `background` | `28.0` / `1.95` / `warm` |
| 安装包 | `versionName` / `lastUpdateTime` | `0.5.1` / `2026-09-21 13:02:19` |
| 数据基线 | `highlights`（live）/ `notes`（live） | 16 / 2（其中 测试 TXT = 2 高亮 + 2 笔记） |

取证方式：`adb -s c49ac6cf exec-out "run-as com.creationreadingassistant cat files/datastore/app_settings.preferences_pb"`，protobuf map 按"键长度前缀 + `\x12\xNN\x2A\xNN<value>`"逐条定位（`re.findall(r'[ -~]{4,}')` 只能看到键、看不到短值，会误判为"覆盖已清空"）。

---

## 4. 进入滚动分支并建立活动选区

| 步骤 | 实测 |
|---|---|
| 打开测试 TXT | 阅读器落在 **分页** 分支：`content-desc = 分页正文已就绪`，单一页面节点 → 与 §3 的 `readerMode=paged` 一致 |
| 切到滚动（只改本书） | 阅读设置 → 展开「预设与覆盖」→ 高级与偏好设置 → **翻页与按键** → 阅读模式 → 点「上下滚动」 |
| 切换后确认 | `content-desc = 阅读正文已就绪`，正文变成按章切分的 **ReadingUnit** 列表（每章一个单元） |
| 启动 TTS | 阅读器 chrome → **听书**。媒体会话立即建立：`TtsPlayback com.creationreadingassistant/TtsPlayback/1414`，`state=PLAYING(3)`，MIUI 媒体通知 `app=创作阅读助手 / artist=t-txt-s07 / song=第1章 测试章节1号 / isPlaying=true` |
| 建立选区 | `16:28:11.544` 在第4章单元内长按（`input swipe 300 900 300 900 700`）→ `16:28:13` 截图确认：平台"复制/全选"浮层 + 选区句柄 + **应用底部工具栏摘录 = `夜里风大`** |
| owner 判定 | 当时可见单元 = `第4章 测试章节4号`（`bounds=[65,182][1155,2712]`），选区起点落在该单元 → **owner = 第4章 ReadingUnit** |

证据：`screenshots/01-after-tts-tap.png`（TTS 播放中、视图已自动滚动）、`screenshots/03-longpress-ch3.png`（选区 + 工具栏 + TTS 正在推进）、`s1.xml`。

### 4.1 过程中的一次真实障碍（如实记录，未算作缺陷结论）

`16:25:11` 与 `16:25:42` 两次长按**没有产生选区**。原因是 TTS 逐句推进时正文会跳动，700–900ms 的按住期间触点相对内容发生位移，长按被判定为滑动而取消。`16:28` 那次成功，落在两句之间相对静止的窗口。
这属于合成手势在"内容自己会动"的场景下的固有脆弱性，**我没有据此报缺陷**（真人手指可能在任意时刻成功），但登记为 P3 观察项（§8 O-2）。

---

## 5. 核心断言：TTS 推动 owner 离开可见范围 → 活动选区自动清除

### 5.1 采样方法

清除时机由 TTS 自主决定，不可预知，因此用**轮询采样**同时记录两件事，每 ~4.5–8s 一条：

- `toolbar_nodes` = 无障碍树中 `替换` + `字典` 计数（这两个节点只存在于选区工具栏）；
- `visible_chapters` = 当前 `visibleItemsInfo` 渲染出的 ReadingUnit 章号集合。

完整原始序列：`ui-poll-toolbar-and-visible-units.log`（23 条采样，16:28:50 → 16:32:07）。

### 5.2 时间线（决定性）

| 设备时间 | toolbar_nodes | 可见单元 | 结论 |
|---|---:|---|---|
| 16:28:50.240 | **2** | 第3章(顶部残段) + **第4章** | owner 可见 → KEEP |
| 16:29:21.852 | **2** | 第3章 + **第4章** | owner 可见 → KEEP |
| 16:29:29.848 | **2** | **第4章** + 第5章 | owner 仍可见 → KEEP（**未过早清除**） |
| 16:30:47.158 | **2** | **第4章** + 第5章 | owner 仍可见 → KEEP |
| **16:31:03.711** | **0** | 第5章 + 第6章 | **owner 已完全离开可见范围 → 活动选区与工具栏被清除** |
| 16:31:11 → 16:32:07（8 次采样） | 0 | 第5章 + 第6章 | 保持清除，无抖动、无重复弹出 |

### 5.3 交叉验证：清除确实由 TTS 驱动，不是手势也不是搜索跳转

| 证据 | 内容 |
|---|---|
| logcat（`logcat-tts-window.txt`） | `16:31:03.862 I/TtsEngineProcessor: speak sync Begin … text=第6章 测试章节6号` —— **TTS 在 16:31:03 跨入第6章**，与工具栏消失（16:31:03.711）同一秒 |
| 前序句子序列 | 16:30:21 钟声从山那边… / 16:30:27 柜台后的老人… / 16:30:33 窗台上的陶盆… / 16:30:39 电车穿过高架桥… / 16:30:45 他把地图铺在膝盖上… / 16:30:51 码头的绳索… / 16:30:56 重复段落… / **16:31:03 第6章 测试章节6号** —— 每句 ~6s 自主推进 |
| 手势排除 | 16:28:50 → 16:32:07 期间我**没有发出任何 `input swipe`/`input tap`**（采样只跑 `uiautomator dump` + `cat`），视图位移全部来自 TTS 自动滚动 |
| 搜索跳转排除 | 本轮未使用书内搜索 |
| 引擎细节 | 小米云端引擎报 `CloudEngineListener onError code=-7` / `engineError=40010006`，随即回落本地引擎（`mLocalEngineListener onStart/onEnd`）继续朗读 —— 朗读未中断，仅音色/引擎降级 |

### 5.4 断言逐条

| 断言 | 实测 | 判定 |
|---|---|---|
| 活动选区被清除 | `selectedRangeStart` 归零后工具栏节点从 2 → 0；摘录 `夜里风大` 不再作为工具栏摘录节点存在 | **PASS** |
| 选区工具栏消失 | 同上（`复制/浏览器/替换/字典/更多` 五项全部消失） | **PASS** |
| 旧摘录不再显示 | 工具栏消失即摘录条消失；`p2.xml`/`p3.xml` 中 `替换`/`字典` 计数 = 0 | **PASS** |
| 没有重复提示 / 崩溃 / ANR | 清除后 8 次采样稳定为 0，未出现闪烁式重复派发；logcat 无 `FATAL` / `ANR in com.creationreading` / `Force finishing`；进程 pid 全程 `32160` 未重启 | **PASS** |
| 未过早清除 | owner 与第5章同时可见的 6 次采样（16:29:29 → 16:30:47）工具栏一直保留 | **PASS** |

**D-3 的 TTS 程序化滚动分支：PASS。** 至此 D-3 的三条路径（用户手势 / 搜索跳转 / TTS 自动推进）全部在真机闭合。

---

## 6. 停止 TTS 后的三项复验

### 6.1 返回原位置，旧选区不复活

| 步骤 | 实测 |
|---|---|
| 停止 TTS | 阅读器 chrome → 听书（切换）→ 媒体会话 `state=STOPPED(1)`，`updated=3180764834` |
| 滚回 owner 所在章 | 先反向 6 次 swipe（落在第3章，`04a-scrolled-to-ch3.png`），再正向 2 次 → **第4章单元重新占满视口**（`bounds=[65,45][1155,2712]`，`p3.xml`） |
| 断言 | `替换`/`字典` 计数 = **0 0**；无摘录条 | 
| 判定 | **PASS —— 旧选区不复活** |

### 6.2 新选区仍可建立（未被永久禁用）

| 步骤 | 实测 |
|---|---|
| 在第4章单元长按 (300,700) | `16:33:43.445` |
| 结果 | 摘录 = **`雨后的石阶泛着青色`**，工具栏完整：`复制 / 浏览器 / 替换 / 字典 / 更多`（`p4.xml`） |
| 判定 | **PASS** —— 选择能力正常，清除不是"一次性把选区机制关掉" |

### 6.3 消费者快照 × TTS：**NOT TESTED**

| 步骤 | 实测 |
|---|---|
| 建立活动选区 | ✅（§6.2 的 `雨后的石阶泛着青色`） |
| 打开捕获不可变快照的消费者 | ✅ 更多 → 添加批注 → 「新建笔记」工作表，**`摘录：雨后的石阶泛着青色`** 已捕获（`c2.xml`、`06-consumer-snapshot-open.png`） |
| 在消费者保持打开的同时启动 TTS | ❌ **UI 结构上做不到**：听书入口只存在于阅读器底部 chrome；工作表打开后 chrome 被覆盖且不可达。尝试用媒体键绕过：`input keyevent 126`（MEDIA_PLAY）→ 会话仍 `STOPPED(1)`，`input keyevent 127`（MEDIA_PAUSE）此前同样无效 |
| 结论 | **NOT TESTED**（未伪造证据、未用别的场景顶替） |
| 是否影响核心判定 | **不影响**。任务书明确该项"不取代核心 TTS 旅程的判定"；F2.4 的快照正交性另有 `ReaderSelectionSnapshotSemanticsTest`（12 项）与上一轮真机 G 组证据支撑 |
| 收尾 | 用「取消」关闭工作表，**未保存任何笔记**（`notes` live 计数 16:43 仍为 2，与测试前一致） |

---

## 7. 收尾恢复与回读

### 7.1 恢复动作

| 对象 | 动作 | 退出码 |
|---|---|---|
| `POST_NOTIFICATIONS` | `pm revoke com.creationreadingassistant android.permission.POST_NOTIFICATIONS` | 0 |
| 测试 TXT `readerMode` | 阅读设置 → 翻页与按键 → 阅读模式 → 「左右翻页」（**只改本书**，写回 `paged`，不是删覆盖） | — |
| 活动选区 | 更多 → 取消选择 | — |
| 消费者工作表 | 取消（未保存） | — |
| TTS | 听书切换停止 | — |
| 前台 | `am force-stop com.creationreadingassistant`，回到 `com.miui.home/.launcher.Launcher` | — |
| stay-awake | **全程未修改**（基线 `3` 已足够长亮） | — |

### 7.2 回读值（逐项与 §3 测试前值比对）

| 键 | 测试前 | **收工回读** | 判定 |
|---|---|---|---|
| `POST_NOTIFICATIONS` | `granted=false` | **`granted=false`** | ✅ 已恢复 |
| `system/stay_on_while_plugged_in` | `3` | `3` | ✅ 未改 |
| `system/accelerometer_rotation` | `1` | `1` | ✅ |
| `system/user_rotation` | `0` | `0` | ✅ |
| `system/font_scale` | `1.0` | `1.0` | ✅ |
| 全局 `reader_mode` | `paged` | `paged` | ✅ |
| 全局 `pager_engine_mode` | `on` | `on` | ✅ |
| 全局 `epub_pager_engine_mode` | `auto` | `auto` | ✅ |
| 全局 `reader_page_turn_effect` | `none` | `none` | ✅ |
| 全局 `reader_screen_orientation` | `system` | `system` | ✅ |
| **测试 TXT `d5bd07ef…_readerMode`** | `paged` | **`paged`** | ✅ 精确恢复（覆盖仍在、值仍为 paged） |
| 测试 Markdown `9a85d098…_readerMode` | `scroll` | `scroll` | ✅ 只读记录，未改 |
| `202a634c…_fontSize` | `25.0` | `25.0` | ✅ 未改 |
| `epub_zhrum_fontSize/lineHeight/background` | `28.0/1.95/warm` | `28.0/1.95/warm` | ✅ 未改 |
| `epub_zhrum_readerMode` | ABSENT | ABSENT | ✅ 未新增 |

### 7.3 数据面回读（只读查询设备 DB 副本）

| 项 | 测试前 | 收工 | 判定 |
|---|---|---|---|
| `highlights`（live，全库） | 16 | 16 | ✅ 未新增（`max(created_at)=2026-09-21T07:36:03Z` = 上一轮的重复高亮，非本轮） |
| `notes`（live，全库） | 2 | 2 | ✅ 未新增（批注已取消） |
| 测试 TXT 高亮 / 笔记 | 2 / 2 | 2 / 2 | ✅ |
| 用户真实书籍 | — | 未打开、未写入 | ✅ |
| 测试书 | 全部保留 | 全部保留 | ✅ 未删除 |
| 测试 TXT 阅读进度 | 首页 0% | 首页 **50%** | ⚠️ **本轮唯一预期内的夹具数据变化**：TTS 连续朗读第1→6章，正常推进阅读进度。属我自己生成的中性测试夹具，非用户数据；如实登记，不回滚（回滚需要写库，任务书禁止修改数据库内容） |

---

## 8. 观察项（不修复、不扩大范围）

| ID | 级别 | 观察 | 证据 |
|---|---|---|---|
| O-1 | P3 | **消费者工作表与听书入口互斥**：「新建笔记」等消费者打开后，阅读器 chrome 不可达，且 `KEYCODE_MEDIA_PLAY/PAUSE` 对该 `TtsPlayback` 会话无效（会话声明 `actions=567` 且通知栏有"暂停"动作），导致"消费者快照 × TTS 清除"无法从 UI 复验 | §6.3；`dumpsys media_session` 前后状态 |
| O-2 | P3 | **TTS 自动滚动期间长按选词容易落空**：正文逐句跳动时，700–900ms 合成长按有 2/3 次未产生选区（第 3 次成功）。真人手指是否受影响未验证，故只登记为可测性/体验观察，不判缺陷 | §4.1 |
| O-3 | 观察 | **小米云端 TTS 引擎报错并静默回落本地引擎**：`CloudEngineListener onError code=-7`、`getTextToSpeechError: engineError=40010006`，随后 `mLocalEngineListener onStart` 正常发声。朗读未中断，但用户看不到"已切换引擎"的任何提示 | `logcat-tts-window.txt` |
| O-4 | 观察 | 上一轮遗留的 **D-2 / N-1 / P3-①…P3-⑤ 本轮一律未触碰、未复验**，状态沿用上轮报告 | `2026-09-21-s07-f5-qoder-device-retest.md` §9 |

**本轮未新增 P0/P1/P2 缺陷。**

---

## 9. 最终判定

**PASS。**

任务书「最终判定」的 6 个条件全部满足（逐条见 §0）。上一轮唯一挡路的 D-3 TTS 分支现已在真机闭合，且清除时机与 TTS 跨章事件在同一秒对齐、期间无任何用户手势介入。

**建议：关闭 S07。**

保留声明：
- **D-2（设置作用域：分页引擎开关写全局不写 per-book）仍是独立 P2**，未修、未宣称解决，不属于 F5；
- N-1（笔记列表显示"暂无划线与笔记"而库中有 3 条未删除高亮）仍是独立 P2，本轮未处理；
- 消费者快照 × TTS 组合为 **NOT TESTED**，不取代核心 TTS 旅程判定；
- 本报告不写"发布完成"；
- **未领取 S08 或任何后续切片。**

---

## 10. 回传清单

| 项 | 值 |
|---|---|
| Agent / 模型 | Qoder |
| 真实设备序列号 | `c49ac6cf`（`22081212C` / `diting`，1220x2712 @480dpi，非模拟器） |
| 判定 | **PASS** |
| TTS 是否真正启动并推动滚动 | **是** —— `TtsPlayback` 会话 `PLAYING(3)`，logcat 逐句 `speak sync Begin` 自主推进并跨章，采样期间零用户手势 |
| owner 离屏后选区是否清除 | **是** —— `16:30:47` 工具栏在（第4章仍可见）→ `16:31:03.711` 工具栏 0（第5/6章可见），与 TTS 于 `16:31:03.862` 进入第6章同一秒；返回第4章不复活；新选区仍可建立 |
| 设置与权限是否完整恢复 | **是** —— `POST_NOTIFICATIONS` 回到 `false`；测试 TXT `readerMode` 精确回到 `paged`；stay-awake `3`、旋转 `1`/`0`、`font_scale 1.0`、5 项全局阅读设置、其余 per-book 覆盖逐项回读与测试前一致；未新增高亮/笔记，未触碰用户真实书籍 |
| 主报告 | `D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919\docs\qa\2026-09\2026-09-21-s07-f5-tts-qoder-device-retest.md` |
| 证据目录 | `D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919\docs\qa\evidence\2026-09-21-s07-f5-tts-qoder-device-retest\`（7 张截图 + `ui-poll-toolbar-and-visible-units.log` + `logcat-tts-window.txt` + 6 份 UI dump） |
| Git 纪律 | HEAD `5aed593`；`git diff --cached` = 0；`git stash list` = 0；未 commit / 未 push / 未改任何源码或配置 |
| 状态 | **等待 Codex 主任务复核，未领取下一片** |
