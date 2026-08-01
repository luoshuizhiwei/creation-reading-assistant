package com.creationreadingassistant.data.local

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlin.annotation.AnnotationRetention.RUNTIME

/**
 * 全局 Application 级 CoroutineScope 与 Dispatcher 装配。
 *
 * 为什么要统一注入而不是每个 Store/ViewModel 各写一份：
 * 1. **可测试性** —— 单元测试里能 bind 到 `UnconfinedTestDispatcher` /
 *    `StandardTestDispatcher`，避免真的跑主线程或线程池，消除 flaky 测试。
 * 2. **生命周期一致** —— 所有 @Singleton Store 的协程共享同一个 SupervisorJob，
 *    将来要加整体取消/监控（比如崩溃前记录活跃协程）只有一个点。
 * 3. **避免重复对象** —— 此前 4 个 Store 各自创建了一份 SupervisorJob + Dispatcher 封装，
 *    其实完全等价。
 * 4. **调度策略可控** —— 目前 stateIn/后台写入都跑在 IO，将来要切 Default 或加
 *    CoroutineName / CoroutineExceptionHandler 只改一处。
 *
 * 注意：
 * - `@ApplicationScope` 的生命周期 = app 进程生命周期；需要更短生命周期的场景
 *   （Activity/ViewModel）请用各自已提供的 lifecycleScope / viewModelScope，不要注入这个。
 * - Composable 里的 `LaunchedEffect` / `rememberCoroutineScope()` 不受此约束，
 *   它们本身绑定 Composition 生命周期，是正确的页面级 scope。
 */
@Module
@InstallIn(SingletonComponent::class)
object CoroutineScopeModule {

    @Qualifier
    @Retention(RUNTIME)
    annotation class ApplicationScope

    @Qualifier
    @Retention(RUNTIME)
    annotation class IODispatcher

    @Qualifier
    @Retention(RUNTIME)
    annotation class DefaultDispatcher

    @Qualifier
    @Retention(RUNTIME)
    annotation class MainDispatcher

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(
        @IODispatcher io: CoroutineDispatcher,
    ): CoroutineScope =
        CoroutineScope(SupervisorJob() + io)

    @Provides
    @IODispatcher
    fun provideIODispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @DefaultDispatcher
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

    @Provides
    @MainDispatcher
    fun provideMainDispatcher(): CoroutineDispatcher = Dispatchers.Main.immediate
}
