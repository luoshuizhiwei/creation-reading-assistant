package com.creationreadingassistant.feature.library.deletion

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 删除/撤销的进程内依赖。
 *
 * 只在本模块内提供绑定，不改动共享的 DatabaseModule 或任何既有 DI 文件；
 * 凭证刻意只存在于内存，进程重启即失效，因此这里没有任何持久化绑定。
 */
@Module
@InstallIn(SingletonComponent::class)
object DeletionModule {

    @Provides
    @Singleton
    fun provideDeletionUndoPolicy(): DeletionUndoPolicy = DeletionUndoPolicy()

    @Provides
    @Singleton
    fun provideDeletionClock(): DeletionClock = DeletionClock { System.currentTimeMillis() }
}
