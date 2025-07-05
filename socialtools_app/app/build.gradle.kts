import java.util.Properties

plugins {
    id("com.android.application")
    kotlin("android")
}

val envProps = Properties().apply {
    val envFile = rootProject.file(".env")
    if (envFile.exists()) {
        envFile.inputStream().use { load(it) }
    }
}

android {
    namespace = "com.cicero.socialtools"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.cicero.socialtools"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
        // OAuth credentials have been removed
        buildConfigField("String", "OPENAI_API_KEY", "\"${envProps["OPENAI_API_KEY"] ?: ""}\"")
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("androidx.fragment:fragment-ktx:1.6.2")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("com.github.bumptech.glide:glide:4.16.0")
    implementation("com.github.instagram4j:instagram4j:2.0.7")

    testImplementation(kotlin("test"))
    testImplementation("junit:junit:4.13.2")
}
