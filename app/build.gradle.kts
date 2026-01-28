import com.android.build.api.dsl.Packaging

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
    id("kotlin-parcelize")
}

android {
    signingConfigs {
        create("release") {
            storeFile =
                file("C:\\Users\\pauls\\AndroidStudioProjects\\Cloud\\keystore\\my-release-key.jks")
            storePassword = "CdRYqvzH75jcOnvIMx75Ewl5sOrzHtCe"
            keyAlias = "my-key-alias"
            keyPassword = "CdRYqvzH75jcOnvIMx75Ewl5sOrzHtCe"
        }
    }
    namespace = "com.example.cloud"
    compileSdk = 36

    packaging {
        resources {
            excludes += "/META-INF/DEPENDENCIES"
        }
    }

    defaultConfig {
        applicationId = "com.example.cloud"
        minSdk = 35
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField(
            "String",
            "SUPABASE_ANON_KEY",
            "\"${project.properties["SUPABASE_ANON_KEY"]}\""
        )

        buildConfigField(
            "String",
            "WEATHERAPI_API_KEY",
            "\"${project.properties["WEATHERAPI_api_key"]}\""
        )
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    buildTypes {
        debug {
            buildConfigField(
                "String",
                "SUPABASE_PUBLISHABLE_KEY",
                "\"${project.properties["SUPABASE_PUBLISHABLE_KEY"]}\""
            )
        }
        release {
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
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlin {
        jvmToolchain(11)
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            excludes += "dump_syms/**"
            excludes += "lib/**/dump_syms.bin"
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
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.appcompat)
    implementation(libs.protolite.well.known.types)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui.text)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.compose.runtime.saved.instance.state)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.play.services.location)
    implementation(libs.androidx.tools.core)
    implementation(libs.androidx.media3.session)
    implementation(libs.firebase.crashlytics.buildtools)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
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
    ksp(libs.androidx.room.compiler)
    implementation(libs.ktor.ktor.client.android)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.websockets)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.utils)
    implementation(libs.accompanist.swiperefresh)
    implementation(libs.gson)
}