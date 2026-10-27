plugins {
    id("com.android.application")
}

android {
    namespace = "ai.pleos.hello"
    compileSdk = 34

    defaultConfig {
        applicationId = "ai.pleos.hello"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // 시작 가이드의 2.3.1 은 저장소에 없다 — 2.3.2 가 최신
    implementation("ai.pleos.playground:Vehicle:2.3.2")
}
