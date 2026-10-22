import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

// مفتاح Pexels: من متغير بيئة (GitHub Secret) أو local.properties. لا يُكتب في الشيفرة.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val pixabayKey: String = (System.getenv("PIXABAY_API_KEY") ?: localProps.getProperty("PIXABAY_API_KEY") ?: "")
    .trim().replace("\"", "")
val pexelsKey: String = (System.getenv("PEXELS_API_KEY") ?: localProps.getProperty("PEXELS_API_KEY") ?: "")
    .trim().replace("\"", "")

android {
    namespace = "com.imagegrabber.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.imagegrabber.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 5
        versionName = "0.7.0"
        buildConfigField("String", "PEXELS_API_KEY", "\"$pexelsKey\"")
        buildConfigField("String", "PIXABAY_API_KEY", "\"$pixabayKey\"")
    }

    // مفتاح تصحيح ثابت حتى يمكن تحديث التطبيق فوق النسخة السابقة دون حذفه
    signingConfigs {
        getByName("debug") {
            if (file("debug.keystore").exists()) {
                storeFile = file("debug.keystore")
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // توقيع بمفتاح التصحيح الثابت ليكون APK الإصدار قابلًا للتثبيت (للاستخدام الشخصي)
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.coil-kt:coil-compose:2.6.0")
    implementation("androidx.documentfile:documentfile:1.0.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
