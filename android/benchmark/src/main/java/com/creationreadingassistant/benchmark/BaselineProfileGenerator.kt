package com.creationreadingassistant.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun generateStartup() {
        baselineProfileRule.collect(
            packageName = PACKAGE_NAME,
            includeInStartupProfile = true,
        ) {
            pressHome()
            startActivityAndWait()
            dismissOnboardingIfPresent()
        }
    }

    @Test
    fun generateCriticalUserJourneys() {
        baselineProfileRule.collect(
            packageName = PACKAGE_NAME,
            includeInStartupProfile = false,
        ) {
            seedBenchmarkLibrary()
            pressHome()
            startActivityAndWait()
            dismissOnboardingIfPresent()
            listOf("书架", "灵感", "统计", "我的", "首页").forEach { label ->
                clickTopLevelNavigation(label)
            }
        }
    }
}
