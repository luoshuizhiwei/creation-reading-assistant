plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "com.creationreadingassistant"
    compileSdk = 35
    // AGP only generates androidTest for one build type per invocation. Keep the
    // normal debug test target unless an explicit offline QA-host build is requested.
    testBuildType = providers.gradleProperty("craAndroidTestBuildType").orNull ?: "debug"

    defaultConfig {
        applicationId = "com.creationreadingassistant"
        minSdk = 24
        targetSdk = 35

        // 版本号可由 CI 注入（./gradlew assembleRelease -PcraVersionName=1.2.3 -PcraVersionCode=10203）；
        // 本地缺省值仅为开发占位，正式发布一律走 release.yml 的 android-v tag 流程。
        //
        // 0.5.1(3)：数据丢失修复（父表 @Insert(REPLACE) → @Upsert，见 BookDao.upsert 注释）。
        // 与 0.5.0(2) 的代码差异只在数据库写入层，界面完全一致，故必须靠版本号区分 ——
        // 0.5.0(2) 在「我的 → 日志与诊断 → 应用版本」里显示为 0.5.0，本版显示 0.5.1。
        versionCode = providers.gradleProperty("craVersionCode").orNull?.toInt() ?: 3
        versionName = providers.gradleProperty("craVersionName").orNull ?: "0.5.1"

        testInstrumentationRunner = "com.creationreadingassistant.testing.CraIsolatedTestRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    // 发布签名只从环境变量 / gradle property 读取，绝不硬编码、绝不提交密钥（docs/release/ANDROID_RELEASE.md §2）。
    val keystoreFile = providers.environmentVariable("CRA_ANDROID_KEYSTORE_FILE")
        .orElse(providers.gradleProperty("craAndroidKeystoreFile")).orNull?.takeIf { it.isNotBlank() }
    val keystorePassword = providers.environmentVariable("CRA_ANDROID_KEYSTORE_PASSWORD")
        .orElse(providers.gradleProperty("craAndroidKeystorePassword")).orNull?.takeIf { it.isNotBlank() }
    val keyAlias = providers.environmentVariable("CRA_ANDROID_KEY_ALIAS")
        .orElse(providers.gradleProperty("craAndroidKeyAlias")).orNull?.takeIf { it.isNotBlank() }
    val keyPassword = providers.environmentVariable("CRA_ANDROID_KEY_PASSWORD")
        .orElse(providers.gradleProperty("craAndroidKeyPassword")).orNull?.takeIf { it.isNotBlank() }
    val releaseSigningReady = listOf(keystoreFile, keystorePassword, keyAlias, keyPassword).all { it != null }

    buildTypes {
        create("benchmark") {
            initWith(getByName("release"))
            applicationIdSuffix = ".benchmarktarget"
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            isDebuggable = false
            isProfileable = true
        }
        create("benchmarkRelease") {
            // 显式声明，杜绝 baselineProfile 插件自动生成的同名变体缺少 applicationIdSuffix：
            // 插件默认 maybeCreate("benchmarkRelease") 时 applicationId 与主包一致，
            // connected 基准安装会覆盖主应用（见 results/shelf-sort-performance-report.md §8.6）。
            // 不用 initWith(release)：它连带拷贝 release 源集的 manifest.srcFile，导致
            // benchmarkRelease 丢失种子 Activity 声明（BenchmarkSeedActivity）。
            applicationIdSuffix = ".benchmarktarget"
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            isDebuggable = false
            isJniDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (releaseSigningReady) {
                signingConfig = signingConfigs.create("craRelease") {
                    storeFile = file(keystoreFile!!)
                    storePassword = keystorePassword
                    this.keyAlias = keyAlias
                    this.keyPassword = keyPassword
                }
                logger.lifecycle("✅ assembleRelease 使用正式签名（${keystoreFile!!.substringAfterLast('/')}）。")
            } else {
                val buildingRelease = gradle.startParameter.taskNames.any {
                    it.contains("Release", ignoreCase = true)
                }
                if (System.getenv("GITHUB_ACTIONS") != null && buildingRelease) {
                    // CI 缺签名必须失败，绝不退回 debug 签名冒充正式包。
                    throw GradleException(
                        "CI 环境缺少 Android 发布签名（CRA_ANDROID_KEYSTORE_* 四个变量必须齐全），已中止 release 构建。",
                    )
                }
                val allowDebugFallback = providers.gradleProperty("craAllowDebugSigningFallback")
                    .orNull?.toBoolean() ?: false
                if (allowDebugFallback) {
                    signingConfig = signingConfigs.getByName("debug")
                    logger.lifecycle("⚠️ 未配置正式签名，已显式启用 debug 密钥兜底签名；产物仅可用于本地验证，绝不可发布！")
                } else {
                    signingConfig = null
                    logger.lifecycle("⚠️ 未配置正式签名，产物为未签名 APK（app-release-unsigned.apk），不可正式分发！若需本地设备验证，可添加 -PcraAllowDebugSigningFallback=true。")
                }
            }
        }
        create("qahost") {
            // S04 沙箱测试宿主（docs/coordination/2026-09-25-s04-workmanager-nondestructive-alternatives.md §3）：
            // 独立 applicationId 使 androidTest APK 的 targetPackage 指向 com.creationreadingassistant.qahost，
            // instrumentation 内 WorkManagerInitializer 打开的是沙箱私有 workdb，不触碰正式包任务队列。
            // initWith(debug) 只拷贝构建属性、不继承 debugImplementation 依赖 ——
            // Compose 测试的 ComponentActivity test manifest 需在下方单独补 qahostImplementation。
            initWith(getByName("debug"))
            applicationIdSuffix = ".qahostj1persistv2"
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    // benchmarkRelease 变体复用 benchmark 源集的 java 源码（BenchmarkSeedActivity）。
    // 该变体的 manifest 用专用文件 src/benchmarkRelease/AndroidManifest.xml（见下方
    // androidComponents.finalizeDsl 重置：插件会把 release 源集的 manifest.srcFile 拷过来）。
    // 不共享时 benchmarkRelease 包缺少种子 Activity，宏基准的种子步骤会静默失败（空书架）。
    sourceSets {
        getByName("benchmarkRelease") {
            java.srcDir("src/benchmark/java")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    lint {
        abortOnError = true
        checkDependencies = false
    }
}

// baselineProfile 插件在 finalizeDsl 阶段把 release 源集拷贝进 benchmarkRelease 源集（含
// manifest.srcFile），导致该变体丢失种子 Activity 声明。这里用注册顺序更晚的
// finalizeDsl 回调（在变体创建前执行）把 manifest 重置回专用文件。
androidComponents {
    finalizeDsl { ext ->
        ext.sourceSets.getByName("benchmarkRelease")
            .manifest.srcFile("src/benchmarkRelease/AndroidManifest.xml")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.work.runtime.ktx)

    // Compose BOM：统一管理 Compose 相关库版本
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    // 依赖注入
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // 持久化
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.security.crypto)
    coreLibraryDesugaring(libs.coreLibraryDesugar)

    // 图片 / 序列化 / 协程
    implementation(libs.coil.compose)
    implementation(libs.coil.svg)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.collections.immutable)
    implementation(libs.androidx.profileinstaller)

    // 局域网同步（Retrofit + OkHttp）
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    implementation(libs.okhttp)

    // 扫码配对（ML Kit 条码识别）
    implementation(libs.mlkit.barcode.scanning)

    // 相机（CameraX）
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    // 设置持久化（DataStore Preferences）
    implementation(libs.androidx.datastore.preferences)

    // Markdown 语义解析（CommonMark + GFM 表格/删除线/任务列表）
    implementation(libs.commonmark)
    implementation(libs.commonmark.ext.gfm.tables)
    implementation(libs.commonmark.ext.gfm.strikethrough)
    implementation(libs.commonmark.ext.task.list.items)

    // 测试
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.okhttp.mockwebserver)
    // JVM 单测的 XmlPullParser 实现（WebDavBackup PROPFIND 解析测试用；android.jar 桩返回 null）
    testImplementation(libs.kxml2)
    testImplementation(libs.xmlpull)

    // Android 测试（Room 迁移测试）
    androidTestImplementation(libs.room.testing)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    // NB04 SAF 旅程：系统 DocumentsUI 驱动（仅 androidTest 类路径，产品 APK 不受影响）
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.hilt.android.testing)
    // J1 联合门禁新增（最小依赖）：SyncRepositoryPersistenceTest 用进程内
    // MockWebServer 作为受控桌面端，驱动真实 Retrofit/OkHttp 协议栈验证
    // R1 同步合并修复的持久化事实。版本与主 okhttp 4.12.0 一致。
    androidTestImplementation(libs.okhttp.mockwebserver)
    // J1-v2 返修新增（最小依赖）：SyncConfigStore / DeviceInfoProvider 为 final 类
    // 且经 SecurePrefs 触碰 AndroidKeyStore 默认主密钥与真实加密 prefs——仪器测试
    // 用 mockk-android（1.13.12，与 JVM 侧 mockk 同版本）作测试替身隔离它们，
    // 杜绝对 qahost 的 sync_config / cra_device_identity / 默认主密钥的任何读写。
    androidTestImplementation("io.mockk:mockk-android:1.13.12") {
        // mockk-android 传递引入 JVM-only 的 JUnit5 构件，其 META-INF/LICENSE.md
        // 与 androidTest APK 内既有许可文件合并冲突；运行期不需要，按组排除。
        exclude(group = "org.junit.jupiter")
        exclude(group = "org.junit.platform")
    }
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    // qahost 变体与 debug 不共享 debugImplementation（initWith 不继承 configuration），
    // 缺它则 Compose 测试无法在沙箱宿主内启动 ComponentActivity。
    add("qahostImplementation", libs.androidx.compose.ui.test.manifest)
    baselineProfile(project(":benchmark"))
}

baselineProfile {
    automaticGenerationDuringBuild = false
    saveInSrc = true
}

// Room schema 导出目录（exportSchema = true 时必填，否则 KSP 报错）。
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// 将 Room schema 复制到构建目录，避免测试准备任务写回 src/ 并与 Lint 并发冲突。
val roomMigrationSchemasDir = layout.buildDirectory.dir("generated/roomMigrationSchemas")
val copyRoomSchemasForMigrationTest = tasks.register<Copy>("copyRoomSchemasForMigrationTest") {
    from("$projectDir/schemas")
    into(roomMigrationSchemasDir)
    include("**/*.json")
}
android.sourceSets.named("androidTest") {
    assets.srcDir(roomMigrationSchemasDir)
}
tasks.matching { it.name.contains("AndroidTest") }.configureEach {
    dependsOn(copyRoomSchemasForMigrationTest)
}
