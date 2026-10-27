# pleos-hello

Pleos Connect(42dot 차량 IVI, Android Automotive 기반)용 앱. 공식 시작 가이드:
https://document.pleos.ai/docs/pleos-only/getting-started

세션별 진행 상황은 여기 적지 않는다 — `~/.claude/handoff/` (`/wrap`) 에 남긴다.
이 파일에는 세션이 바뀌어도 유지되는 사실만 둔다.

## 빌드

- Gradle 9.8.2 wrapper, AGP 9.4.1. Kotlin 은 AGP 내장이라 `org.jetbrains.kotlin.android` 플러그인을 넣지 않는다.
- compileSdk / targetSdk 34, minSdk 28 (Pleos 요구사항: 서명 키 로테이션·lineage).
- JDK 는 Android Studio 번들 JBR: `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"` (`~/.zshrc` 에 설정됨).

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n ai.pleos.hello/.MainActivity
adb logcat -s PleosHello:I
```

## Vehicle SDK

- 저장소: `https://nexus-playground.pleos.ai/repository/maven-releases/` (로그인 불필요)
- **`ai.pleos.playground:Vehicle:2.3.1` 은 저장소에 없다 (404).** 시작 가이드가 2.3.1 이라고 적었지만 틀렸다.
  존재하는 버전은 2.3.2(최신)와 2.0.3. 새 버전 확인:
  `curl -s https://nexus-playground.pleos.ai/repository/maven-releases/ai/pleos/playground/Vehicle/maven-metadata.xml`
- 패키지: `ai.pleos.playground.vehicle.Vehicle`, `...vehicle.api.model.area.DoorArea`, `...vehicle.constant.area.DoorValue`.
  문서와 시그니처가 다르면 AAR 의 `classes.jar` 를 `javap` 로 직접 확인한다.
- AAR 이 `ai.pleos.playground.service.vehicle.VEHICLE_BINDING` 권한을 자동 merge 하고, 차량 서비스 최소 버전 2.3.13 을 요구한다.
- `initialize()` 후 쓰고, `onDestroy()` 에서 `release()`.

## 권한

- 차량 권한(예: `pleos.car.permission.CAR_DOORS`)은 매니페스트 선언만으로 설치 시 부여된다.
  `adb shell pm grant` 는 예외를 내지만 `dumpsys package ai.pleos.hello` 에서 `granted=true` 면 정상이다.

## 에뮬레이터

- AVD: `Pleos_Connect_API_34` (이미지 `system-images;android-34;pleos_car_emulator;arm64-v8a`, Pleos Connect v2.1.0,
  디바이스 `Pleos_Connect_v2_0`, 2560×1440, RAM 4GB, cold boot)
- 실행: `emulator -avd Pleos_Connect_API_34` — 콜드 부팅 약 22초. 부팅 확인: `adb shell getprop sys.boot_completed` 가 `1`.
- SDK 업데이트 사이트(Mac arm64): `https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-arm64-sys-img.xml`
  (`~/.android/repositories.cfg` 에 등록됨. Windows/Linux 는 `crp-sys-img.xml`)
- 새 이미지 버전으로 바꿀 때는 기존 이미지를 먼저 지운다 (가이드 지시).
- Virtual Console 은 에뮬레이터 2.1.0 이상이 필요하다.
