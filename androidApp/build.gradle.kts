import org.jetbrains.kotlin.gradle.dsl.JvmTarget
plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}
kotlin { compilerOptions { jvmTarget = JvmTarget.JVM_11 } }
android {
    namespace = "com.nexo.agenda"
    compileSdk = 37
    defaultConfig {
        applicationId = "com.nexo.agenda"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "1.1"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures { compose = true }
}
dependencies {
    implementation(project(":shared"))
    implementation(libs.androidx.activity.compose)
}
