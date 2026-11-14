# 복숭아 도매시장 경매정보 (안드로이드 Kotlin 연동 예제)

공공데이터포털의 **한국농수산식품유통공사_전국 공영도매시장 실시간 경매정보**를 호출하여 복숭아 거래를 보여주는 코드입니다. 기존 앱 프로젝트에 복사해서 사용할 수 있습니다. 이 저장소는 완성된 안드로이드 프로젝트가 아니라 연동용 소스와 예제 화면입니다.

- [`android/PeachAuctionClient.kt`](android/PeachAuctionClient.kt): API 호출, 오류 처리, 복숭아 코드 검색, 페이지 정보
- [`android/PeachAuctionActivity.kt`](android/PeachAuctionActivity.kt): 결과를 바로 확인하는 간단한 화면. 기존 화면이 있으면 클라이언트만 사용해도 됩니다.

## 알아둘 점

표시 가격은 **도매시장 경매 건별 낙찰가**이며, 소비자 소매가격이나 복숭아 전체의 대표 평균가격이 아닙니다. 가격과 함께 규격, 포장, 등급, 경매 시간, 정산일을 확인하세요. 제공처의 실시간 데이터는 조회 시점에 갱신되는 데이터이며, 이 예제는 자동 갱신 대신 새로고침 버튼을 제공합니다. API 응답 순서가 최신순이라는 보장은 없습니다.

API의 정산일 조건은 필수입니다. 화면은 한국 시간 기준 오늘을 조회합니다. 오늘 거래가 없거나 경매 자료가 아직 공개되지 않은 경우 결과가 없을 수 있습니다. 날짜를 바꿔 확인하려면 `fetchPage(settlementDate = "2025-11-19")`처럼 지정하세요. 정산일과 경매일시는 서로 다를 수 있습니다.

## 앱에 붙이기

1. 공공데이터포털에서 위 API의 활용 승인을 받고 **일반 인증키(Decoding)**를 확인합니다. Encoding 키를 넘기면 이중 인코딩으로 인증이 실패할 수 있습니다. 키는 소스나 GitHub에 올리지 마세요.
2. 두 Kotlin 파일을 앱 모듈의 `src/main/java/` 아래 원하는 패키지에 복사하고, 각 파일의 `package com.example.peachauction`을 앱 패키지에 맞게 바꿉니다. 예제 화면은 `BuildConfig`를 사용하므로 패키지가 앱 모듈의 `namespace`와 다르면 `import 앱의.namespace.BuildConfig`를 추가하세요.
3. `app/src/main/AndroidManifest.xml`의 `<manifest>` 바로 아래에 인터넷 권한을 추가하고, 예제 화면을 사용할 때는 `<application>` 안에 Activity를 등록합니다.

   ```xml
   <uses-permission android:name="android.permission.INTERNET" />

   <!-- application 안에 추가. 패키지가 다르면 전체 클래스 이름 사용 -->
   <activity android:name=".PeachAuctionActivity" android:exported="false" />
   ```

4. 프로젝트 루트의 `local.properties`에 아래 줄을 추가하고 실제 **일반 인증키(Decoding)**로 교체합니다. Android Studio가 이미 생성한 `sdk.dir` 줄은 그대로 둡니다.

   ```properties
   DATA_GO_KR_KEY=여기에_일반_인증키_Decoding
   ```

   `local.properties`가 Git에 포함되지 않도록 `.gitignore`를 확인하세요. 해당 파일을 팀원에게 공유하는 대신 각자 키를 안전한 경로로 설정해야 합니다.

5. 앱 모듈의 `app/build.gradle.kts`에 아래 내용을 추가하고 Gradle을 동기화합니다. Groovy(`build.gradle`)를 사용하는 프로젝트라면 같은 설정을 Groovy 문법으로 바꿔야 합니다.

   ```kotlin
   // 파일 상단의 plugins 블록 앞에 추가
   import java.util.Properties

   // plugins 블록 다음에 추가
   val localProperties = Properties().apply {
       val file = rootProject.file("local.properties")
       if (file.exists()) file.inputStream().use { load(it) }
   }
   val dataGoKrKey = localProperties.getProperty("DATA_GO_KR_KEY", "")
       .replace("\\", "\\\\").replace("\"", "\\\"")

   // 기존 android 블록 안에 추가
   android {
       buildFeatures { buildConfig = true }
       defaultConfig {
           buildConfigField("String", "DATA_GO_KR_KEY", "\"$dataGoKrKey\"")
       }
   }
   ```

   기존 `android` 및 `defaultConfig` 블록이 이미 있으면 해당 블록 안에 설정만 추가하세요. 이 예제 화면은 Android 4.2(API 17) 이상에서 사용합니다.
6. 기존 화면에서 테스트 화면을 열려면 `startActivity(Intent(this, PeachAuctionActivity::class.java))`를 호출합니다. `Intent` import가 필요합니다. 화면을 열면 오늘 정산일 기준 첫 페이지가 조회되고, 버튼으로 새로고침 또는 다음 페이지를 확인할 수 있습니다.

**인증키 보안:** 위 방식은 Git에 키를 넣지 않기 위한 개발용 설정입니다. APK에 포함된 키는 추출할 수 있습니다. 배포 앱에서는 자체 서버가 인증키로 공공 API를 호출하고 앱은 그 서버에 요청하도록 구성하세요. 이 예제에는 서버가 포함되어 있지 않습니다.

## 기존 화면에서 사용하기

`fetchPage`는 네트워크를 사용하므로 메인 스레드가 아닌 작업 스레드에서 호출해야 합니다. 아래는 Activity에서 호출하는 최소 예시입니다.

```kotlin
val executor = java.util.concurrent.Executors.newSingleThreadExecutor()
executor.execute {
    try {
        val page = PeachAuctionClient(BuildConfig.DATA_GO_KR_KEY).fetchPage(
            settlementDate = PeachAuctionClient.todayInSeoul(),
            pageNo = 1,
            rows = 100,
            // marketCode = "확인한 도매시장 코드",
            // varietyCode = "11" // 백도만 조회. 생략하면 모든 복숭아 품종
        )
        runOnUiThread {
            // page.auctions를 기존 UI에 표시합니다.
            // page.hasNext가 true이면 pageNo + 1로 다음 페이지를 요청합니다.
            // page.totalCount는 지정한 시장·품종 등 서버 검색 조건에 해당하는 전체 건수입니다.
        }
    } catch (e: Exception) {
        runOnUiThread { /* 오류 상태를 UI에 표시 */ }
    }
}
// Activity/ViewModel 종료 시 executor.shutdownNow()를 호출하고,
// 화면이 파괴된 뒤 UI를 갱신하지 않도록 생명주기를 관리하세요.
```

사용자가 확인한 표준코드를 사용해 서버에 과실류 대분류 `06`, 복숭아 중분류 `04`를 조건으로 요청합니다. 품종을 생략하면 전체 복숭아 품종을 조회합니다. `varietyCode`에 `00`(복숭아), `11`(백도), `99`(기타)를 지정해 품종을 제한할 수 있습니다. 코드의 앞자리 0을 보존하기 위해 문자열을 사용하세요. `marketCode`로 시장을 제한할 수도 있습니다. 전체를 보려면 `hasNext`가 false가 될 때까지 페이지를 조회하세요.

한 페이지마다 API 요청이 발생하므로 자동 반복 조회와 요청 한도를 고려하세요. 조회 중 데이터가 변하면 페이지의 중복이나 누락이 생길 수 있고, 이 예제는 전체 평균이나 최신 거래를 계산하지 않습니다.

## 확인할 사항

- 첫 실행 시 키가 없으면 `local.properties`와 Gradle 동기화를 확인하세요. 인증 승인 반영 상태, 네트워크 및 인터넷 권한도 확인하세요.
- 정산일에 자료가 있는 날짜로 테스트하고, 전체 거래를 보려면 다음 페이지도 확인하세요.
- 제공처의 실제 응답과 첨부 API 명세서에서 `gds_mclsf_nm`, `scsbd_prc`, `unit_qty`, `unit_nm` 등 필드를 확인하세요. 필드가 비어 있으면 화면에 빈 값으로 표시됩니다.
- 이 코드의 실제 API 호출 및 앱 빌드는 인증키와 기존 앱 프로젝트가 없어 아직 확인하지 못했습니다. 팀 앱에 복사한 뒤 실기기 또는 에뮬레이터에서 검증하세요.

제공처: [전국 공영도매시장 실시간 경매정보](https://www.data.go.kr/data/15141808/openapi.do), [농축수산물 표준코드](https://www.data.go.kr/data/15141818/openapi.do)

