# 피치스코프 실시간 복숭아 시세

aT 경매를 조회하는 Python 서버와 Android Studio에서 가져다 쓸 Java 클라이언트입니다. Java와 Kotlin 프로젝트에서 호출할 수 있습니다. 피치스코프 Android 프로젝트의 기존 가격 화면 또는 ViewModel에 연결합니다.

## 서버 실행

1. [aT 실시간 경매정보 API](https://www.data.go.kr/data/15141808/openapi.do)에 활용신청합니다.
2. Python 3.10 이상을 설치하고 아래 명령을 실행합니다. 인증키 입력 시 포털의 일반 인증키(Decoding)를 사용합니다. 서버가 요청에 필요한 URL 인코딩을 적용합니다.

PowerShell

```powershell
$env:AT_SERVICE_KEY = Read-Host "aT Decoding key"
python server.py
```

macOS·Linux Bash

```bash
read -rs -p 'aT Decoding key: ' AT_SERVICE_KEY
export AT_SERVICE_KEY
python3 server.py
```

3. 같은 PC에서 브라우저로 `http://localhost:8000/peach/live?marketCode=110001&boxKg=4`를 엽니다. 기본 시장은 서울 가락입니다. 등급을 맞춰 비교할 때는 `&gradeCode=등급코드`를 추가합니다.
4. 팀원 앱에서 사용할 때는 서버를 HTTPS 주소로 배포하고, 배포 환경의 환경변수에 키를 등록합니다. 이 Python 서버는 개발용입니다. 배포 시에는 운영용 서버 또는 호스팅 서비스 뒤에서 실행하고 타임아웃과 호출 한도를 확인합니다. 키를 앱, APK 또는 GitHub에 넣으면 외부에 노출될 수 있습니다.

## Android Studio 연결

1. `android/PeachPriceClient.java`를 기존 프로젝트의 네트워크 패키지에 복사하고, 파일 첫 줄에 해당 패키지 선언을 추가합니다.
2. `AndroidManifest.xml`의 `<manifest>` 아래에 다음 권한을 추가합니다.

```xml
<uses-permission android:name="android.permission.INTERNET" />
```

3. 기존 화면의 갱신 함수나 ViewModel에서 백그라운드로 호출합니다. Kotlin 코루틴을 이미 사용하는 경우 아래처럼 연결합니다.

```kotlin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

val result = withContext(Dispatchers.IO) {
    PeachPriceClient.fetch("https://your-server.example", "110001", "4", null)
}
if (result.getString("status") == "ok") {
    val wonPerBox = result.getInt("wonPerBox")
    val tradeCount = result.getInt("tradeCount")
    // 기존 화면에 시세와 거래 건수를 전달합니다.
} else {
    // 당일 조건에 해당하는 거래 없음
}
```

네트워크 실패 및 HTTP 오류는 기존 화면의 조회 실패 상태로 처리합니다. Kotlin 코루틴을 쓰지 않는 Java 프로젝트에서는 기존 백그라운드 실행 방식에서 `PeachPriceClient.fetch(...)`를 호출합니다.

## 가격 기준

- 한국 시간의 당일 정산일을 조회하고, 낙찰일시의 날짜가 당일인 거래를 집계합니다. 시장별 정산일과 낙찰일이 다른 사례가 확인되면 추가 정산일 조회가 필요합니다.
- 복숭아 일반·백도·기타 코드(00·11·99)를 사용합니다. 화면에는 **미홍 출하기 참고 시세**로 표시하세요.
- 상자 가격은 낙찰 단가를 거래 수량으로 가중평균한 값입니다. kg당 가격은 총 거래금액을 총 중량으로 나눈 값입니다. 같은 시장, 상자 무게, 등급 조건에서 기존 3년치와 비교하세요.
- `no_trades`이면 가격은 `null`입니다. `lastAuctionAt`은 마지막 낙찰 시각이며 `fetchedAt`은 데이터 조회 시각입니다.
- aT 인증키를 사용한 실거래 응답과 실제 안드로이드 앱 연결은 팀 환경에서 확인해야 합니다.
