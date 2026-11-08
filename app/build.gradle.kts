import java.util.Properties

plugins {
    id("com.android.application")
    id("com.google.devtools.ksp")
}

// 비밀값은 local.properties(gitignore)에서 읽는다. 커밋하지 않는다
val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
fun localProp(key: String, env: String? = null) =
    "\"" + (localProps.getProperty(key) ?: env?.let { System.getenv(it) } ?: "") + "\""

android {
    namespace = "ai.pleos.hello"
    compileSdk = 34

    defaultConfig {
        applicationId = "ai.pleos.hello"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        buildConfigField("String", "PLEOS_CLIENT_ID", localProp("pleos.clientId"))
        buildConfigField("String", "PLEOS_CLIENT_SECRET", localProp("pleos.clientSecret"))
        // 임시 LLM(Pleos LLM 400 해결 전까지). local.properties llm.geminiKey 가 없으면 셸의 GEMINI_API_KEY
        buildConfigField("String", "GEMINI_API_KEY", localProp("llm.geminiKey", "GEMINI_API_KEY"))
    }

    buildFeatures {
        buildConfig = true
    }

    useLibrary("android.car")

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // 시작 가이드의 2.3.1 은 저장소에 없다 — 2.3.2 가 최신
    implementation("ai.pleos.playground:Vehicle:2.3.2")

    implementation("ai.pleos.playground:SpeechToText:3.3.1")
    implementation("ai.pleos.playground:TextToSpeech:3.3.1")
    implementation("ai.pleos.playground:NaviHelper:3.0.5")
    implementation("ai.pleos.playground:FusedLocation:1.1.3")

    // Room 2.8.x + KSP2 (AGP 9 내장 Kotlin)
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")

    // Gemini 스파이크 전용(debug). 플랫폼 LLM 이 열리면 재검토
    debugImplementation("ai.pleos.playground:LLM:2.1.3.1")

    testImplementation("junit:junit:4.13.2")
}
