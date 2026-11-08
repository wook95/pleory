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
        applicationId = "com.pplab.pleory" // Kotlin 패키지·namespace 는 ai.pleos.hello 그대로
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        // 임시 LLM(Pleos LLM 400 해결 전까지). local.properties llm.geminiKey 가 없으면 셸의 GEMINI_API_KEY
        buildConfigField("String", "GEMINI_API_KEY", localProp("llm.geminiKey", "GEMINI_API_KEY"))
        // 1순위 구조화 LLM(Claude). local.properties llm.claudeKey 가 없으면 셸의 PLEORY_CLAUDE_KEY.
        // ANTHROPIC_API_KEY 는 읽지 않는다(셸에 있으면 사용자의 Claude Code 과금 키가 앱에 들어간다). 비어 있으면 Claude 를 건너뛴다
        buildConfigField("String", "CLAUDE_API_KEY", localProp("llm.claudeKey", "PLEORY_CLAUDE_KEY"))
    }

    // local.properties 에 release.* 가 모두 있으면 그 키스토어로, 없으면 debug 서명(심사용)으로 서명한다. 키스토어·비밀번호는 커밋하지 않는다
    val releaseStore = localProps.getProperty("release.storeFile")
    if (releaseStore != null) {
        signingConfigs.create("release") {
            storeFile = rootProject.file(releaseStore)
            storePassword = localProps.getProperty("release.storePassword")
            keyAlias = localProps.getProperty("release.keyAlias")
            keyPassword = localProps.getProperty("release.keyPassword")
        }
    }

    buildTypes {
        debug {
            // 스파이크 화면 전용. 릴리스 APK 에는 넣지 않는다
            buildConfigField("String", "PLEOS_CLIENT_ID", localProp("pleos.clientId"))
            buildConfigField("String", "PLEOS_CLIENT_SECRET", localProp("pleos.clientSecret"))
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName(if (releaseStore != null) "release" else "debug")
        }
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

    // Room 암호화(보안 가이드: 기기 저장 민감정보는 SQLite 포함 암호화). SupportOpenHelperFactory 로 Room 에 붙인다.
    // 4.18.0 부터는 androidx.sqlite 2.7.0(compileSdk 36 요구)을 끌어온다 — compileSdk 34 를 유지하려고 4.17.0 + sqlite 2.6.2(Room 2.8.4 와 같음)
    implementation("net.zetetic:sqlcipher-android:4.17.0")
    implementation("androidx.sqlite:sqlite:2.6.2")

    // Claude 공식 Java SDK(구조화 1순위, TRD D6)
    implementation("com.anthropic:anthropic-java:3.3.0")

    // Gemini 스파이크 전용(debug). 플랫폼 LLM 이 열리면 재검토
    debugImplementation("ai.pleos.playground:LLM:2.1.3.1")

    testImplementation("junit:junit:4.13.2")
}
