import java.util.Properties

plugins {
    id("com.android.application")
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

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // 시작 가이드의 2.3.1 은 저장소에 없다 — 2.3.2 가 최신
    implementation("ai.pleos.playground:Vehicle:2.3.2")

    // 1일차 SDK 스파이크(app/src/debug/.../spike) 전용. 본 기능에 쓰게 되면 implementation 으로 올린다
    debugImplementation("ai.pleos.playground:SpeechToText:3.3.1")
    debugImplementation("ai.pleos.playground:TextToSpeech:3.3.1")
    debugImplementation("ai.pleos.playground:NaviHelper:3.0.5")
    debugImplementation("ai.pleos.playground:LLM:2.1.3.1")
}
