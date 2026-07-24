val appVersionName = findProperty("APP_VERSION_NAME")?.toString() ?: "1.8.0"
val appVersionCode = findProperty("APP_VERSION_CODE")?.toString()?.toIntOrNull() ?: 30
val releaseStoreFile = providers.gradleProperty("RELEASE_STORE_FILE").orNull
val releaseStorePassword = providers.gradleProperty("RELEASE_STORE_PASSWORD").orNull
val releaseKeyAlias = providers.gradleProperty("RELEASE_KEY_ALIAS").orNull
val releaseKeyPassword = providers.gradleProperty("RELEASE_KEY_PASSWORD").orNull
val hasReleaseSigning = listOf(
    releaseStoreFile,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword
).all { !it.isNullOrBlank() }
val allowReleaseCleartext = providers.gradleProperty("ALLOW_RELEASE_CLEARTEXT")
    .orNull
    ?.toBooleanStrictOrNull()
    ?: false

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
}

android {

    kapt {
        arguments {
            arg("room.schemaLocation", "$projectDir/schemas")
        }
    }

    buildFeatures {
        buildConfig = true
    }

    namespace = "com.hana.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.hana.app"
        minSdk = 24
        targetSdk = 35
        // Versioning convention:
        // 1.7.0 -> 1.7.1 -> ... -> 1.7.9 -> 1.8.0
        versionCode = appVersionCode
        versionName = appVersionName
        resValue("string", "app_version_title", "Hana $appVersionName")
        resValue("string", "app_version_display", "v$appVersionName (build $appVersionCode)")
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(requireNotNull(releaseStoreFile))
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            isDebuggable = true
            isMinifyEnabled = false
            manifestPlaceholders["usesCleartextTraffic"] = "true"
        }
        release {
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            isDebuggable = false
            isMinifyEnabled = false
            isShrinkResources = false
            manifestPlaceholders["usesCleartextTraffic"] = allowReleaseCleartext.toString()
        }
    }

    bundle {
        language {
            enableSplit = false
        }
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    lint {
        checkReleaseBuilds = true
    }
}

val copyDebugApkToRoot by tasks.registering {
    doNotTrackState("Root workspace contains tool-managed files and locks")
    doLast {
        val sourceApk = layout.buildDirectory.file("outputs/apk/debug/app-debug.apk").get().asFile
        check(sourceApk.isFile) { "Debug APK was not generated: ${sourceApk.absolutePath}" }
        sourceApk.copyTo(rootDir.resolve("AIChat-phone-install.apk"), overwrite = true)
    }
}

tasks.configureEach {
    if (name == "assembleDebug") {
        finalizedBy(copyDebugApkToRoot)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.datastore:datastore-preferences:1.0.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("io.coil-kt:coil-compose:2.6.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
