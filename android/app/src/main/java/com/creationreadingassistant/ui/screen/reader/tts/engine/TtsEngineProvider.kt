package com.creationreadingassistant.ui.screen.reader.tts.engine

import android.content.Context
import com.creationreadingassistant.ui.screen.reader.tts.TtsController

/**
 * TTS 引擎工厂（单例注册表）。负责：
 * 1. 根据 [TtsEngineId] 创建/缓存引擎实例；
 * 2. "当前所选引擎" 的唯一入口；UI 层只读此映射不直连具体实现。
 *
 * 生命周期：
 * - 每个 [Context]（Application）进程内同一引擎 ID 只缓存 1 个实例，避免重复初始化；
 * - [releaseAll] 在 App 退出或"切换引擎后释放旧的"时调用（[TtsController.release] 已连到此方法）。
 *
 * ⚠️ 注意（P1-第四批-1）：
 * 当前 [TtsController] 仍是系统 TextToSpeech 的紧密包装；本 Provider 在此版本内**仅暴露
 * 引擎列表供设置 UI 选择 + 持久化引擎 ID**，真正切到 [TtsEngine.play] 播放通道留待后续
 * 渐进式重构（避免一次性大改影响已验证的听书/通知/蓝牙/定时停止等行为）。
 */
internal object TtsEngineProvider {

    private val lock = Any()
    private val instances = mutableMapOf<TtsEngineId, TtsEngine>()

    /** 所有引擎类型的完整枚举（供 SegmentedButton / 下拉菜单显示）。 */
    val all: List<TtsEngineId> = TtsEngineId.entries.toList()

    /**
     * 获取或创建指定引擎。[appContext] 必须为 Application Context，避免 Activity 泄漏。
     * 失败自动回退策略：
     * - 若用户选择 [TtsEngineId.EDGE] 但初始化失败（无网/服务端403等），由 [EdgeTtsEngine]
     *   内部**自行创建 [SystemTtsEngineWrapper]** 并切到 fallback 模式，调用方始终拿到的是
     *   Edge 引擎对象，对外只暴露一个 TtsEngine 接口。
     */
    fun get(appContext: Context, engineId: TtsEngineId): TtsEngine = synchronized(lock) {
        instances.getOrPut(engineId) {
            when (engineId) {
                TtsEngineId.SYSTEM -> SystemTtsEngineWrapper(appContext)
                TtsEngineId.EDGE -> EdgeTtsEngine(appContext)
            }
        }
    }

    /** 释放单个引擎（比如切换引擎时释放旧的）。 */
    fun release(engineId: TtsEngineId) = synchronized(lock) {
        instances.remove(engineId)?.close()
    }

    /** 全量释放（应用退出兜底）。 */
    fun releaseAll() = synchronized(lock) {
        instances.values.forEach { runCatching { it.close() } }
        instances.clear()
    }
}
