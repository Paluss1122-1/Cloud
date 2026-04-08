import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
    id("kotlin-parcelize")
    alias(libs.plugins.hilt.android)
}

val localProps = Properties().apply {
    load(rootProject.file("local.properties").inputStream())
}

kotlin {
    jvmToolchain(17)
}

android {
    /*signingConfigs {
        create("release") {
            storeFile =
                file("C:\\Users\\pauls\\AndroidStudioProjects\\Cloud\\keystore\\my-release-key.jks")
            storePassword = localProps["KEY_STORE_PASSWORD"] as String
            keyAlias = localProps["KEY_ALIAS"] as String
            keyPassword = localProps["KEY_PASSWORD"] as String
        }
    }*/
    namespace = "com.cloud"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.cloud"
        minSdk = 35
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            buildConfigField(
                "String",
                "SUPABASE_PUBLISHABLE_KEY",
                "\"${project.properties["SUPABASE_PUBLISHABLE_KEY"]}\""
            )
        }
        create("minifiedDebug") {
            initWith(buildTypes.getByName("debug"))
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField(
                "String",
                "SUPABASE_PUBLISHABLE_KEY",
                "\"${project.properties["SUPABASE_PUBLISHABLE_KEY"]}\""
            )
        }
        /*release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField(
                "String",
                "SUPABASE_PUBLISHABLE_KEY",
                "\"${project.properties["SUPABASE_PUBLISHABLE_KEY"]}\""
            )
            signingConfig = signingConfigs.getByName("release")
        }*/
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        buildConfig = true
        compose = true
    }
    packaging {
        resources {
            excludes += listOf(
                "/META-INF/DEPENDENCIES",
                "META-INF/LICENSE.md",
                "META-INF/LICENSE-notice.md",
                "META-INF/NOTICE.md",
                "dump_syms/**",
                "lib/**/dump_syms.bin"
            )

        }
        jniLibs {
            useLegacyPackaging = false
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.compose.runtime.saved.instance.state)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.play.services.location)
    implementation(libs.androidx.tools.core)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation.layout)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.runtime)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    implementation(libs.storage.kt)
    implementation(libs.postgrest.kt)
    implementation(libs.supabase.kt)
    implementation(libs.realtime.kt)
    implementation(libs.coil.compose)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.room.runtime)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.common)
    implementation(libs.androidx.biometric)
    implementation(libs.core)
    implementation(libs.zxing.android.embedded)
    debugImplementation(libs.androidx.compose.ui.tooling)
    ksp(libs.androidx.room.compiler)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.websockets)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.utils)
    implementation(libs.accompanist.swiperefresh)
    implementation(libs.gson)
    implementation(libs.text.recognition)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.hilt.android)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.osmdroid.android)

    ksp(libs.hilt.compiler)

    implementation(libs.mp3agic)

    implementation(libs.shizuku.provider)

    implementation(libs.androidx.core.splashscreen)

    implementation(libs.android.mail)
    implementation(libs.android.activation)

    implementation(libs.androidx.autofill)

    implementation(libs.androidx.slice.builders)
}
