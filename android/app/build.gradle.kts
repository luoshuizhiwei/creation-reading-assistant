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

    defaultConfig {
        applicationId = "com.creationreadingassistant"
        minSdk = 24
        targetSdk = 35

        // 版本号可由 CI 注入（./gradlew assembleRelease -PcraVersionName=1.2.3 -PcraVersionCode=10203）；
        // 本地缺省值仅为开发占位，正式发布一律走 release.yml 的 android-v tag 流程。
        versionCode = providers.gradleProperty("craVersionCode").orNull?.toInt() ?: 2
        versionName = providers.gradleProperty("craVersionName").orNull ?: "0.5.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
                signingConfig = signingConfigs.getByName("debug")
                logger.lifecycle("⚠️ 未配置正式签名，使用 debug 密钥兜底签名；产物仅可用于本地验证，绝不可发布！")
            }
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

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

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
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.hilt.android.testing)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
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
