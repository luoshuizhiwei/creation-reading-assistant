package com.creationreadingassistant.testing

import android.app.Application
import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.AndroidJUnitRunner
import java.lang.reflect.Field
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference
import org.junit.runner.Description
import org.junit.runner.notification.RunListener

/**
 * androidTest 专用 InstrumentationRunner（S04 启动隔离，设计依据：
 * `docs/coordination/2026-09-25-s04-instrumentation-startup-isolation-design.md` §5.2；
 * 范围收窄依据：`docs/coordination/2026-09-29-stage-e-g2-runner-scope-review.md`）。
 *
 * ## craRunId 宿主自动绑定（2026-11-18）
 *
 * 历史验收用例（如 D2RelinkUserJourneyAcceptTest、InspirationCategorySourceRetentionDeviceTest 等）
 * 强制要求 `InstrumentationRegistry.getArguments().getString("craRunId")` 非空且匹配特定的任务号正则。
 * 在宿主脚本外部直接通过 Gradle（connectedDebugAndroidTest）运行组合用例时，
 * 命令行单一的 `-Pandroid.testInstrumentationRunnerArguments.craRunId=<值>` 无法同时满足不同类的前缀正则。
 *
 * 本 runner 在 [onCreate] 注册 [CraRunIdBindingListener]，在每个测试方法启动前（`testStarted`，早于 `@Before`）
 * 检查当前测试类：若外部未传或当前值不匹配该类约束，则自动注入合规的唯一 `craRunId`；
 * 若外部传入了有效值，则严格优先保留外部传入值。
 *
 * 注意：由于 `InstrumentationRegistry.getArguments()` 返回的是 Bundle 的防御性浅拷贝，
 * 更新必须同时作用于 `InstrumentationRegistry.registerInstance(...)` 及反射修改底层 `AtomicReference<Bundle>`，
 * 确保后续调用 `getArguments()` 能够读到注入的参数。
 */
class CraIsolatedTestRunner : AndroidJUnitRunner() {

    override fun onCreate(arguments: Bundle?) {
        val args = arguments ?: Bundle()
        val ourListener = CraRunIdBindingListener::class.java.name
        val existingListeners = args.getString("listener")
        val combinedListeners = if (existingListeners.isNullOrBlank()) {
            ourListener
        } else if (!existingListeners.split(",").contains(ourListener)) {
            "$existingListeners,$ourListener"
        } else {
            existingListeners
        }
        args.putString("listener", combinedListeners)

        // 若外部未传 craRunId 且指定了单一 class，在 onCreate 提前注入首选绑定
        val rawRunId = args.getString("craRunId")
        val classArg = args.getString("class")
        if (rawRunId == null && !classArg.isNullOrBlank() && !classArg.contains(",")) {
            val initialRunId = CraRunIdBindingListener.resolveDefaultRunId(classArg, null)
            if (initialRunId != null) {
                Log.i(TAG, "Pre-binding craRunId=$initialRunId for single class argument $classArg")
                args.putString("craRunId", initialRunId)
            }
        }

        super.onCreate(args)
    }

    override fun newApplication(
        cl: ClassLoader?,
        className: String?,
        context: Context?,
    ): Application {
        val targetPackage = context?.packageName.orEmpty()
        val isolate = targetPackage == QA_HOST_PACKAGE
        Log.i(
            TAG,
            "newApplication target=$targetPackage " +
                "manifestApplication=$className -> " +
                if (isolate) "CraIsolatedTestApplication (isolated)" else "manifest Application (pass-through)",
        )
        return if (isolate) {
            super.newApplication(cl, CraIsolatedTestApplication::class.java.name, context)
        } else {
            super.newApplication(cl, className, context)
        }
    }

    private companion object {
        const val TAG = "CraIsolatedTestRunner"

        /** S04 沙箱宿主 applicationId（`build.gradle.kts` qahost `applicationIdSuffix = ".qahost"`）。 */
        const val QA_HOST_PACKAGE = "com.creationreadingassistant.qahost"
    }
}

/**
 * 监听测试启动事件，在用例进入 `@Before` 之前按当前测试类的正则约束动态绑定合规的 `craRunId`。
 */
class CraRunIdBindingListener : RunListener() {

    override fun testStarted(description: Description?) {
        val className = description?.className.orEmpty()
        if (className.isBlank()) return

        val currentRunId = try {
            InstrumentationRegistry.getArguments().getString("craRunId")
        } catch (t: Throwable) {
            null
        }

        val resolved = resolveDefaultRunId(className, currentRunId)
        if (resolved != null) {
            Log.i(TAG, "testStarted: class=$className bind craRunId='$resolved' (previous was '${currentRunId ?: "<null>"}')")
            injectCraRunId(resolved)
        }
    }

    companion object {
        private const val TAG = "CraRunIdBinding"

        private fun timestamp(): String = System.currentTimeMillis().toString()

        private fun formattedDateTime(): String {
            val sdf = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
            return sdf.format(Date())
        }

        /**
         * 将 craRunId 注入到 InstrumentationRegistry 的内部存储中。
         */
        private fun injectCraRunId(runId: String) {
            try {
                // 1. 获取当前最新 Bundle 并写入新值
                val bundle = InstrumentationRegistry.getArguments()
                bundle.putString("craRunId", runId)

                // 2. 调用 registerInstance 重新注册（更新 sArguments）
                InstrumentationRegistry.registerInstance(InstrumentationRegistry.getInstrumentation(), bundle)

                // 3. 反射修改 InstrumentationRegistry 内部静态 AtomicReference<Bundle>，做双重保障
                try {
                    val field: Field = InstrumentationRegistry::class.java.getDeclaredField("arguments")
                    field.isAccessible = true
                    @Suppress("UNCHECKED_CAST")
                    val atomicRef = field.get(null) as? AtomicReference<Bundle>
                    atomicRef?.get()?.putString("craRunId", runId)
                } catch (e: Throwable) {
                    Log.d(TAG, "Reflective atomic update fallback: ${e.message}")
                }

                // 验证注入效果
                val verified = InstrumentationRegistry.getArguments().getString("craRunId")
                Log.i(TAG, "injectCraRunId verified in getArguments(): '$verified'")
            } catch (t: Throwable) {
                Log.w(TAG, "injectCraRunId failed: ${t.message}", t)
            }
        }

        /**
         * 根据测试类名及当前已有的 runId 解析应绑定的 runId：
         * - 若当前已有且符合正则，返回 null（保留当前值）；
         * - 若当前为空或不合规，返回新生成的合法值；
         * - 若该类无特殊正则要求，返回 null。
         */
        fun resolveDefaultRunId(className: String, currentRunId: String?): String? {
            return when {
                className.contains("D2RelinkUserJourneyAcceptTest") -> {
                    val regex = Regex("T96V[0-9]+-[A-Za-z0-9-]+")
                    if (currentRunId != null && currentRunId.matches(regex)) null
                    else "T96V1-${timestamp()}"
                }
                className.contains("InspirationCategorySourceRetentionDeviceTest") -> {
                    val regex = Regex("T94V4-[A-Za-z0-9-]+")
                    if (currentRunId != null && currentRunId.matches(regex)) null
                    else "T94V4-${timestamp()}"
                }
                className.contains("MaterialAdoptionJourneyDeviceTest") -> {
                    val regex = Regex("T94V[0-9]+-[A-Za-z0-9-]+")
                    if (currentRunId != null && currentRunId.matches(regex)) null
                    else "T94V1-${timestamp()}"
                }
                className.contains("ShelfSavedViewsJourneyDeviceTest") -> {
                    val regex = Regex("T95V[0-9]+-[A-Za-z0-9-]+")
                    if (currentRunId != null && currentRunId.matches(regex)) null
                    else "T95V1-${timestamp()}"
                }
                className.contains("SearchContentHitJourneyDeviceTest") -> {
                    val regex = Regex("^T119V1-[0-9]{8}-[0-9]{6}$")
                    if (currentRunId != null && currentRunId.matches(regex)) null
                    else "T119V1-${formattedDateTime()}"
                }
                className.contains("SearchListScrollRetentionTest") -> {
                    val regex = Regex("^T141BV1-[0-9]{8}-[0-9]{6}$")
                    if (currentRunId != null && currentRunId.matches(regex)) null
                    else "T141BV1-${formattedDateTime()}"
                }
                className.contains("TemporaryReadingRecreationRegressionTest") -> {
                    val regex = Regex("^T136V1-[0-9]{8}-[0-9]{6}$")
                    if (currentRunId != null && currentRunId.matches(regex)) null
                    else "T136V1-${formattedDateTime()}"
                }
                else -> null
            }
        }
    }
}
