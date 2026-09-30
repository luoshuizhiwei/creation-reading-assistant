package com.creationreadingassistant.data.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.creationreadingassistant.data.local.CoroutineScopeModule.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * 阅读时长的显示单位。
 *
 * 默认 [HOURS]：跨天的累计时长显示成「30 小时」比「1 天 6 小时」更便于横向比较
 *（「这本书我读了多久」），也避免「0 天 5 小时」这种无信息量的前缀。
 * 需要按天看时，在**支持的**阅读时长文本或卡片代表区域**长按**即可切换
 *（[DurationPrefs.toggle]）。
 *
 * 「支持的」是刻意的范围限定：并非每个时长文本都挂了入口，同一语义组件内重复的
 * 时长文本（副标题、图表刻度、单日浮层）只给组件的代表区域一个入口，避免读屏用户
 * 在同一张卡里听到多个同名操作。
 */
enum class DurationUnit {
    HOURS,
    DAYS,
    ;

    val storageValue: String
        get() = when (this) {
            HOURS -> "hours"
            DAYS -> "days"
        }

    companion object {
        /** 未知值（首次安装、脏数据）一律回落到默认 [HOURS]。 */
        fun fromStorage(raw: String?): DurationUnit = when (raw) {
            DAYS.storageValue -> DAYS
            else -> HOURS
        }
    }
}

private val Context.durationPrefsDataStore by preferencesDataStore(name = "duration_prefs")

private val KEY_DURATION_UNIT = stringPreferencesKey("duration_unit")

/**
 * 时长显示单位偏好（DataStore Preferences）。
 *
 * **全局单一偏好**：任一入口长按都会切换它，因此所有展示位同时变更。
 * 这是刻意的 —— 单位表达的是「用户习惯怎么看时间」，属于偏好而非某个数字的临时视图；
 * 若做成逐处独立状态，同一屏内出现「小时」和「天」两种单位反而更难读。
 */
@Singleton
class DurationPrefs @Inject constructor(
    @ApplicationContext context: Context,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val ds = context.durationPrefsDataStore

    val unit: StateFlow<DurationUnit> = ds.data
        .map { DurationUnit.fromStorage(it[KEY_DURATION_UNIT]) }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), DurationUnit.HOURS)

    suspend fun setUnit(unit: DurationUnit) {
        ds.edit { it[KEY_DURATION_UNIT] = unit.storageValue }
    }

    /**
     * 在「小时」与「天」之间切换，返回切换后的单位（供调用方给出反馈文案）。
     *
     * 刻意从 DataStore **重新读取**当前值，而不是读 [unit] 的 `StateFlow.value`：
     * 后者用 `WhileSubscribed` 启动，无订阅者时 `value` 恒为初始值 HOURS，
     * 会让「切到天之后再切回小时」失效（读到过期的 HOURS 又写成 DAYS）。
     */
    suspend fun toggle(): DurationUnit {
        val current = DurationUnit.fromStorage(ds.data.first()[KEY_DURATION_UNIT])
        val next = if (current == DurationUnit.HOURS) DurationUnit.DAYS else DurationUnit.HOURS
        setUnit(next)
        return next
    }
}
