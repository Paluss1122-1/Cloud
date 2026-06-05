import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
    id("kotlin-parcelize")
    id("com.autonomousapps.dependency-analysis")
    id("com.google.gms.google-services")
}

val localProps = Properties().apply {
    load(rootProject.file("local.properties").inputStream())
}

fun prop(key: String) = "\"${localProps.getProperty(key)}\""

android {
    namespace = "com.tabslify"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.paluss1122.tabslify"
        minSdk = 35
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "SUPABASE_URL", prop("SUPABASE_URL"))
        buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", prop("SUPABASE_PUBLISHABLE_KEY"))
        buildConfigField("String", "BWMP", prop("BWMP"))
        buildConfigField("String", "DBKEY", prop("DBKEY"))
        buildConfigField("String", "DBKEY1", prop("DBKEY1"))
        buildConfigField("String", "NVIDIA", prop("NVIDIA"))
        buildConfigField("String", "WEATHERAPI_KEY", prop("WEATHERAPI_KEY"))
        buildConfigField("String", "RAPID_API_KEY", prop("RAPID_API_KEY"))
        buildConfigField("String", "PODCASTINDEX_API_KEY", prop("PODCASTINDEX_API_KEY"))
        buildConfigField("String", "PODCASTINDEX_API_SECRET", prop("PODCASTINDEX_API_SECRET"))
        buildConfigField("String", "TMDB_API_KEY", prop("TMDB_API_KEY"))
        buildConfigField("Double", "LAT", localProps.getProperty("LAT"))
        buildConfigField("Double", "LON", localProps.getProperty("LON"))
    }

    signingConfigs {
        create("release") {
            storeFile = file("../keystore/my-release-key.jks")
            storePassword = localProps.getProperty("storePassword")
            keyAlias = "release-key"
            keyPassword = localProps.getProperty("keyPassword")
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".private"
        }
        release {
            signingConfig = signingConfigs.getByName("release")
        }
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
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.play.services.location)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.compose.foundation.layout)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.runtime)
    implementation(libs.androidx.ui)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    implementation(libs.storage.kt)
    implementation(libs.postgrest.kt)
    implementation(libs.supabase.kt)
    implementation(libs.realtime.kt)
    implementation(libs.functions.kt)
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
    implementation(libs.ktor.client.okhttp)
    implementation(libs.gson)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.osmdroid.android)
    implementation(libs.androidx.core.splashscreen)
    runtimeOnly(libs.android.mail)
    implementation(libs.androidx.autofill)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.ai)
    implementation(libs.multiplatform.markdown.renderer.android)
    implementation(libs.multiplatform.markdown.renderer.m3)
    implementation(libs.firebase.config)
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.appcheck.playintegrity)
    implementation(libs.firebase.appcheck.debug)
}
