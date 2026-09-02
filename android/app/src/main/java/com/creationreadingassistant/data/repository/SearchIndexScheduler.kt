package com.creationreadingassistant.data.repository

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SearchIndexScheduler @Inject constructor(
    @ApplicationContext val appContext: Context,
) {

    fun start() {
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(false)
            .build()

        val oneTimeRequest = OneTimeWorkRequestBuilder<SearchIndexWorker>()
            .setInitialDelay(10, TimeUnit.SECONDS)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(appContext).enqueueUniqueWork(
            UNIQUE_ONCE_WORK,
            ExistingWorkPolicy.KEEP,
            oneTimeRequest,
        )

        val periodicRequest = PeriodicWorkRequestBuilder<SearchIndexWorker>(
            24, TimeUnit.HOURS,
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(appContext).enqueueUniquePeriodicWork(
            UNIQUE_PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            periodicRequest,
        )
    }

    companion object {
        const val UNIQUE_ONCE_WORK = "search_index_once_v1"
        const val UNIQUE_PERIODIC_WORK = "search_index_daily_v1"
    }
}
