package com.creationreadingassistant.feature.library

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Wires the production SAF Adapter for [LibrarySource].
 *
 * The Adapter is stateless apart from the [android.content.ContentResolver] it holds, so a single
 * instance is enough for the whole process. Keeping the binding here (instead of an `@Inject`
 * constructor on the Adapter) preserves [SafLibrarySource] as a plain class that JVM tests can
 * construct with a fake resolver.
 *
 * [SmartBookRecognizer] is bound separately in `SafSmartBookRecognizer.kt`.
 */
@Module
@InstallIn(SingletonComponent::class)
internal object LibrarySourceModule {

    @Provides
    @Singleton
    fun provideLibrarySource(@ApplicationContext context: Context): LibrarySource =
        SafLibrarySource(context.contentResolver)
}
