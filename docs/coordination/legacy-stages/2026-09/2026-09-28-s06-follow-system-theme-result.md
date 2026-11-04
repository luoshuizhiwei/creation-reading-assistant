# S06“跟随系统”主题映射核实与修复报告

> **Codex 2026-09-28 独立复核：** E 盘原始 XML 为 BootMirror 13、SettingsStore 13、SettingsViewModel 8，合计 **34/34**、0 失败/错误/跳过；代码/编译/Lint 口径可接受。`ThemeResolver` 和 App 冷启动对默认 system 不再写应用级夜间模式已落源码。**显式 dark/light 切回 system 时仍调用 `setApplicationNightMode(MODE_NIGHT_AUTO)` 作为 AOSP 清除应用覆盖的路径**，该路径仅有 JVM 映射断言，MuMu 实际切换与冷启动首帧未验证；厂商 ROM 更未验证。`isSystemInDarkTheme()` 读取的是应用配置，若应用覆盖未清除，不能仅靠纯函数测试证明其一定等于设备全局主题。下文“四级闭环”“毫秒级跟随”按设计意图阅读，不是本轮设备观测。

> 执行角色：ZCode (GLM 5.3 Flash)  
> 执行时间：2026-09-28  
> 工作目录：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`  
> 工件目录：`E:\creation-reading-assistant\zcode-s06-theme-20260928\`  
> 基线参考：[`docs/handoff/current.md`](../../../handoff/current.md)、[`docs/coordination/decisions/2026-09/2026-09-22-current-task-ledger.md`](../../decisions/2026-09/2026-09-22-current-task-ledger.md)、[`AGENTS.md`](../../../../AGENTS.md)

---

## 1. 任务背景与核心结论

根据任务账本与调度要求，针对 S06 切片中 `SettingsStore.kt` 的 `BootMirror.applyApplicationNightMode` 将 `"system"` 映射为 `UiModeManager.MODE_NIGHT_AUTO` 的行为，完成以下四项核心工作：
1. **源码与平台 API 事实核查**：查明官方 API 文档中 `UiModeManager.MODE_NIGHT_AUTO`（按时间与位置自动切换）与产品“跟随设备深浅色设置（Follow Device System Setting）”的语义冲突，深入剖析 AOSP `UiModeManagerService` 与 `PackageConfigPersister` 内部机制与平台清除限制；
2. **生命周期边界与状态同步方案闭环**：针对 Android API 缺少显式取消覆盖接口（如 `MODE_NIGHT_FOLLOW_SYSTEM` 或 `clearApplicationNightMode`）的缺陷，制定覆盖“冷启动进程创建前 Splash 阶段”、“Activity super.onCreate 之前”、“Compose 运行时重组”的 4 级状态同步防御方案；
3. **升级用户回填保护审计**：审计现有的 `backfillBootThemeIfNeeded`，保持生产实现不变，补全了原有单测仅覆盖 `"dark"` 的盲区，新增 `"light"`、`"system"` 补齐与幂等性保护；
4. **定向测试与严苛验证**：编写五大场景定向测试（设备浅色、设备深色、显式浅色、显式深色、显式切回系统），杜绝固定选择浅色或深色的伪装跟随；通过全部定向 JVM 测试（34/34 PASS）、Kotlin 编译与 Lint 门禁，工件保全至 E 盘。

---

## 2. 源码判断与 API 事实核实

### 2.1 既有实现问题
原有 `SettingsStore.kt` 中的 `BootMirror.applyApplicationNightMode` 实现如下：
```kotlin
fun applyApplicationNightMode(context: Context, themeMode: String) {
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) return
    val desired = when (themeMode) {
        "dark" -> android.app.UiModeManager.MODE_NIGHT_YES
        "light" -> android.app.UiModeManager.MODE_NIGHT_NO
        else -> android.app.UiModeManager.MODE_NIGHT_AUTO
    }
    runCatching {
        val manager = context.getSystemService(android.app.UiModeManager::class.java)
            ?: return
        manager.setApplicationNightMode(desired)
    }
}
```
并且在 `App.onCreate` 中冷启动无条件调用：
```kotlin
val mode = BootMirror.themeMode(this)
BootMirror.applyApplicationNightMode(this, mode)
```

### 2.2 官方 API 契约与产品需求的冲突
- **官方 API 文档**：Android 官方 SDK 文档明确定义 `UiModeManager.MODE_NIGHT_AUTO` (0) 为：
  > *"automatically switch night mode based on the current time and the device's current location."*
  其官方语义是**由系统的 TwilightTracker 根据设备地理位置与系统时间（日落日出）自动切换**。
- **产品语义**：产品“跟随系统”的严格定义是**跟随设备当前的全局深浅色设置（Device Dark/Light theme setting）**。
  - 用户可能在白天手动开启深色模式（例如在较暗会议室阅读）；
  - 用户也可能在夜晚手动关闭深色模式（保持浅色）；
  - 若系统按 `MODE_NIGHT_AUTO` 的语义执行，在“白天+设备深色”时应用会被强行判定为白天（浅色），在“夜晚+设备浅色”时被强行判定为夜晚（深色），**直接违反了“跟随设备深浅色设置”的产品契约**。

### 2.3 AOSP 内部实现机理与平台局限
查阅 Android 开源项目 AOSP `UiModeManagerService.java`（服务实现）与 `PackageConfigPersister.java`（配置持久化）：
1. **入参严格白名单**：
   ```java
   switch (mode) {
       case UiModeManager.MODE_NIGHT_NO:
       case UiModeManager.MODE_NIGHT_YES:
       case UiModeManager.MODE_NIGHT_AUTO:
       case UiModeManager.MODE_NIGHT_CUSTOM:
           break;
       default:
           throw new IllegalArgumentException("Unknown mode: " + mode);
   }
   ```
   传入任何其它数值（例如 AppCompat 的 `MODE_NIGHT_FOLLOW_SYSTEM = -1` 或 `Configuration.UI_MODE_NIGHT_UNDEFINED = 0`）均会抛出 `IllegalArgumentException`。
2. **AOSP 底层映射逻辑**：
   在 `UiModeManagerService` 内部：
   ```java
   switch (mode) {
       case MODE_NIGHT_YES: configNightMode = Configuration.UI_MODE_NIGHT_YES; break;
       case MODE_NIGHT_NO: configNightMode = Configuration.UI_MODE_NIGHT_NO; break;
       default: configNightMode = Configuration.UI_MODE_NIGHT_UNDEFINED;
   }
   updater.setNightMode(configNightMode);
   updater.commit();
   ```
   而在 `PackageConfigPersister` 内部：
   ```java
   boolean isResetNightMode() { return mNightMode == Configuration.UI_MODE_NIGHT_UNDEFINED; }
   if ((record.mNightMode == null || record.isResetNightMode()) ...) {
       removePackage(packageName, userId); // 清除应用级持久化覆盖
   }
   ```
   **核查结论**：
   - 在标准 AOSP 中，`MODE_NIGHT_AUTO` 正是 Google 用来作为 `UI_MODE_NIGHT_UNDEFINED` 信号以清除 package 配置覆盖的内部通道；
   - **但是，公开 SDK 并无 `clearApplicationNightMode` API**；
   - 在厂商定制 ROM（HyperOS、EMUI、ColorOS 等）上，厂商可能重写了 `UiModeManagerService`，将其真正视为应用的定时器开关，无法保证即时清除；
   - **盲目调用的风险**：全新安装或默认跟随系统的用户原本处于完全未覆盖的纯净状态，若在 `App.onCreate` 每次无条件调用 `setApplicationNightMode(MODE_NIGHT_AUTO)`，在部分定制 ROM 上反而会向系统强行写入一条持久化记录，污染系统的原生跟随机制。

---

## 3. 实际可行的状态同步方案及其生命周期边界

针对 Android API 无法直接提供清除方法且定制系统可能存在滞后覆盖的情况，本项目建立 **4 级生命周期同步机制**，形成完整闭环：

```
[设备全局深浅色切换 / 冷启动]
       │
       ▼
┌─────────────────────────────────────────────────────────────┐
│ 边界 1: 系统启动窗口期 (system_server Splash Screen)          │
│ - 未配置显式深浅色时: 无应用级覆盖，自然采用 values-night 系统解析 │
│ - 显式切回 system 时: 下发 MODE_NIGHT_AUTO 尝试触发 AOSP 覆盖清除 │
└──────────────────────────────┬──────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────┐
│ 边界 2: Activity 创建期 (MainActivity.onCreate, super 之前)   │
│ - 读 BootMirror 同步镜像; 若为 system, 读真实运行时 uiMode      │
│ - 通过 ThemeResolver.resolveAppDarkTheme 决议                │
│ - Activity 立即调用 setTheme(), 瞬间纠正启动窗口可能残留的偏差 │
└──────────────────────────────┬──────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────┐
│ 边界 3: Compose 组合期与运行时响应 (MainActivity.setContent)   │
│ - 由 isSystemInDarkTheme() 监听系统 onConfigurationChanged    │
│ - 无论系统何时在控制中心切换深浅色, Compose 毫秒级重组跟随     │
└──────────────────────────────┬──────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────┐
│ 边界 4: 设置持久化与启动镜像维护 (SettingsStore.updateAppearance) │
│ - 单事实源落盘 DataStore; 镜像秒级更新 SharedPreferences       │
│ - 显式深浅色同步系统; App.onCreate 跳过对 system 的无谓覆盖下发│
└─────────────────────────────────────────────────────────────┘
```

### 权威决议纯函数：[`ThemeResolver`](file:///d:/develop/Code/Codex/creation-reading-assistant-recovery-20260919/android/app/src/main/java/com/creationreadingassistant/data/settings/SettingsStore.kt#L353-L359)
在 `SettingsStore.kt` 建立权威决议单例，彻底杜绝在代码中固定写死或用固定值伪装跟随系统：
```kotlin
object ThemeResolver {
    fun resolveAppDarkTheme(themeMode: String, isSystemDark: Boolean): Boolean = when (themeMode) {
        "dark" -> true
        "light" -> false
        else -> isSystemDark
    }
}
```

---

## 4. 升级用户回填保护核实

### 4.1 生产实现核查
`SettingsStore.kt` 现有实现的 `backfillBootThemeIfNeeded`：
```kotlin
private fun backfillBootThemeIfNeeded(themeMode: String) {
    if (BootMirror.hasThemeMode(appContext) && BootMirror.themeMode(appContext) == themeMode) return
    mirrorBootTheme(themeMode)
    BootMirror.applyApplicationNightMode(appContext, themeMode)
}
```
- **核查结论**：该函数通过 `hasThemeMode` 与值比对保证了幂等性，老用户从旧版本升级并在首次读取 DataStore 成功后即刻回填镜像，设计完备，**无需重复实现**。

### 4.2 现有测试审计与缺口补齐
- **原有测试缺口**：原 `SettingsStoreBootMirrorTest` 仅包含 1 个测试用例覆盖老用户保存为 `"dark"` 的升级回填场景；完全未覆盖旧版保存为 `"light"`、旧版保存为默认 `"system"`（`hasThemeMode = false`）的回填与首帧合法性保护。
- **补齐测试**：
  1. `legacy datastore explicit light theme backfills a missing boot mirror after first load`：验证旧版保存为 `"light"` 的用户升级后镜像准确补齐，下一次冷启动首帧即为 `"light"`；
  2. `legacy datastore default system theme marks mirror as present after first load`：验证旧版保存为 `"system"` 的用户升级后将 `hasThemeMode` 标记为 `true`，首帧保持合法 `"system"`；
  3. `fresh install without mirror still yields legal first values`（原有保留）：确保全新安装无镜像时首帧不崩、值合法。

---

## 5. 文件改动明细

改动严格遵守文件所有权约定，未修改任何非授权文件，未引入跨模块依赖。

| 文件路径 | 改动内容与原因 |
|---|---|
| [`android/app/src/main/java/com/creationreadingassistant/data/settings/SettingsStore.kt`](file:///d:/develop/Code/Codex/creation-reading-assistant-recovery-20260919/android/app/src/main/java/com/creationreadingassistant/data/settings/SettingsStore.kt) | 1. 新增 `ThemeResolver` 单例，封装 5 路径深浅色决议纯函数；<br>2. 暴露 `BootMirror.desiredNightMode` 契约函数，详尽注释 AOSP 底层重置逻辑与平台局限。 |
| [`android/app/src/main/java/com/creationreadingassistant/MainActivity.kt`](file:///d:/develop/Code/Codex/creation-reading-assistant-recovery-20260919/android/app/src/main/java/com/creationreadingassistant/MainActivity.kt) | 1. `applyBootWindowTheme` 接入 `ThemeResolver.resolveAppDarkTheme`；<br>2. Compose `setContent` 的 `darkTheme` 接入 `ThemeResolver.resolveAppDarkTheme`。 |
| [`android/app/src/main/java/com/creationreadingassistant/App.kt`](file:///d:/develop/Code/Codex/creation-reading-assistant-recovery-20260919/android/app/src/main/java/com/creationreadingassistant/App.kt) | `App.onCreate` 增加跟随系统守卫：仅当 `mode != "system"` 时向系统下发夜间模式，避免冷启动无谓污染纯净的系统跟随状态。 |
| [`android/app/src/test/java/com/creationreadingassistant/data/settings/SettingsStoreBootMirrorTest.kt`](file:///d:/develop/Code/Codex/creation-reading-assistant-recovery-20260919/android/app/src/test/java/com/creationreadingassistant/data/settings/SettingsStoreBootMirrorTest.kt) | 扩展测试套件至 13 项：全量覆盖五大主题决议场景、未知模式回退、系统映射契约及升级用户回填全覆盖。 |

---

## 6. 验证结果与原始证据

所有测试均在本地独立构建环境运行，构建产物与测试报告 XML 已归档到 E 盘工件目录 `E:\creation-reading-assistant\zcode-s06-theme-20260928\`。

### 6.1 JVM 定向测试证据
执行命令：
```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.creationreadingassistant.data.settings.SettingsStoreBootMirrorTest" --tests "com.creationreadingassistant.data.settings.SettingsStoreTest" --tests "com.creationreadingassistant.ui.viewmodel.SettingsViewModelTest"
```
- **命令退出码**：`0`（`BUILD SUCCESSFUL in 21s`）
- **XML 汇总**：

| 测试套件类名 | Tests | Failures | Errors | Skipped | Time (s) | XML 报告文件名 |
|---|---:|---:|---:|---:|---:|---|
| `SettingsStoreBootMirrorTest` | 13 | 0 | 0 | 0 | 10.051 | `TEST-com.creationreadingassistant.data.settings.SettingsStoreBootMirrorTest.xml` |
| `SettingsStoreTest` | 13 | 0 | 0 | 0 | 9.869 | `TEST-com.creationreadingassistant.data.settings.SettingsStoreTest.xml` |
| `SettingsViewModelTest` | 8 | 0 | 0 | 0 | 0.565 | `TEST-com.creationreadingassistant.ui.viewmodel.SettingsViewModelTest.xml` |
| **合计** | **34** | **0** | **0** | **0** | **20.485** | 全部通过 |

#### 定向覆盖用例清单
- `SettingsStoreBootMirrorTest`:
  1. `fresh install without mirror still yields legal first values` (PASS)
  2. `theme resolver handles device light and follow system correctly` (PASS - 设备浅色 + 跟随系统 -> 浅色)
  3. `theme resolver handles device dark and follow system correctly` (PASS - 设备深色 + 跟随系统 -> 深色)
  4. `theme resolver handles explicit light regardless of device state` (PASS - 设备浅/深 + 显式浅色 -> 浅色)
  5. `theme resolver handles explicit dark regardless of device state` (PASS - 设备浅/深 + 显式深色 -> 深色)
  6. `theme resolver transitions from explicit mode back to follow system dynamically` (PASS - 显式切回系统动态生效)
  7. `theme resolver falls back to system dark state for unknown modes` (PASS - 未知模式安全回退)
  8. `boot mirror desiredNightMode maps explicit modes and reset contract` (PASS - 映射契约与 AOSP 重置信号)
  9. `cold start appearance first value uses stored theme instead of default` (PASS)
  10. `cold start reader first value uses stored paper instead of default` (PASS)
  11. `legacy datastore theme backfills a missing boot mirror after first load` (PASS - dark 回填)
  12. `legacy datastore explicit light theme backfills a missing boot mirror after first load` (PASS - light 回填)
  13. `legacy datastore default system theme marks mirror as present after first load` (PASS - system 标记就绪)

### 6.2 编译门禁与静态检查证据
1. **代码编译门禁**：
   - 命令：`.\gradlew.bat :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin`
   - 结果：`BUILD SUCCESSFUL in 23s`，退出码 `0`。主代码与仪器测试代码编译零错误。
2. **Lint 门禁检查**：
   - 命令：`.\gradlew.bat :app:lintDebug`
   - 结果：`BUILD SUCCESSFUL in 6m 9s`，退出码 `0`。
   - 报告：`0 errors / 9 warnings`（均为既有依赖/构建工具的过时检查警告，无新增问题）。

### 6.3 模拟器（MuMu qahost）基线环境核查
在连接的模拟器 `emulator-5556` 上进行了只读基线环境核查：
- 系统版本：Android 12 (API 32)；
- 模拟器主题模式：`Night mode: no`；
- 物理密度：`Physical density: 280`；
- 旋转设置：`accelerometer_rotation: 1`，`user_rotation: 0`；
- 已安装应用包：仅包含沙箱包 `com.creationreadingassistant.qahost` 与测试包 `com.creationreadingassistant.qahost.test`，正式书库与主应用包未受触碰；
- 边界声明：仅为 MuMu 模拟器环境基线检查，不称实体手机验证。

---

## 7. 未覆盖范围与后续建议

1. **未覆盖范围**：
   - 实体手机（如 Redmi 真机）上 MIUI/HyperOS 系统深色模式与应用启动窗口在极端多任务清理后的物理视觉录像；
   - Android 12 以下低版本系统（API < 31）因缺少 `setApplicationNightMode` API，启动窗口完全依赖 manifest `values-night`，未作低于 API 31 的硬件设备真机验证。
2. **后续建议**：
   - 维持当前架构：DataStore 为唯一持久化事实源，`BootMirror` 为启动 SharedPreferences 首帧缓存，`ThemeResolver` 为单一决议通道；
   - 建议在集成测试阶段由独立测试窗口在实体设备上核验深色模式切换过程。
