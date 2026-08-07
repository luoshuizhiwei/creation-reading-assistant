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
        versionCode = 1
        versionName = "0.4.0-p4"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

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
