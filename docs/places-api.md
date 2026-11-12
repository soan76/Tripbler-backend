# 장소 API

Google Places API (New)를 서버에서 호출해 장소 정보를 돌려준다. Google API 키는 서버에만 있고 앱으로 내려보내지 않는다.

| 엔드포인트 | 인증 | 용도 |
| --- | --- | --- |
| `GET /api/v1/places/search` | **로그인 필요** | 검색어로 장소 검색 |
| `GET /api/v1/places/nearby` | 공개 | 좌표 주변 장소 목록 |
| `GET /api/v1/places/nearest` | 공개 | 좌표에서 가장 가까운 장소 하나 |
| `GET /api/v1/places/{placeId}` | 공개 | 장소 상세 |

인증 규칙은 `SecurityConfig`에 있다. `/api/v1/places/search` 규칙이 `/api/v1/places/**` 공개 규칙보다 앞에 있어야 한다.

## 공통 응답 형식 (`PlaceResponse`)

```json
{
  "id": "ChIJ...",
  "name": "경복궁",
  "latitude": 37.5796,
  "longitude": 126.977,
  "address": "서울특별시 종로구 사직로 161",
  "rating": 4.6,
  "category": "tourist_attraction",
  "openNow": true
}
```

- `rating`, `openNow`는 값이 없으면 필드를 빼지 않고 `null`로 내려간다. 검색 API는 항상 `null`이다(아래 비용 참고).
- `name`이 없으면 `"이름 없는 장소"`, `address`가 없으면 `"주소 정보 없음"`이다.
- Google 응답에 위치가 없거나, 빈 `location {}`이거나, 위도·경도 중 하나만 있으면 `latitude`, `longitude`는 필드를 빼지 않고 둘 다 `null`로 내려간다. 앱은 이 장소를 목록에는 표시하고 지도 마커와 카메라 범위 계산에서는 뺀다.
- `category`는 Google `primaryType` 값이다.

## 장소 검색

`GET /api/v1/places/search?query=경복궁&lat=37.5665&lng=126.978`

| 파라미터 | 필수 | 규칙 |
| --- | --- | --- |
| `query` | 예 | 앞뒤 공백류를 지운 뒤 1~100자(코드 포인트 기준) |
| `lat` | 아니오 | -90~90. `lng`와 함께 보내거나 둘 다 생략 |
| `lng` | 아니오 | -180~180. `lat`와 함께 보내거나 둘 다 생략 |

- 앞뒤에서 지우는 공백류: Java 공백(`Character.isWhitespace`), 유니코드 구분자(NBSP U+00A0, U+3000 등), 서식 문자(U+200B, U+FEFF 등). 단, 태그 문자(U+E0020~U+E007F)는 지역 깃발 이모지(예: 스코틀랜드 깃발)의 일부라 지우지 않는다. 가운데 문자는 그대로 둔다.
- 좌표가 있으면 그 지점 반경 **5000m** 안의 결과를 우선한다(`locationBias`). 결과를 이 범위로 제한하지는 않는다.
- 좌표를 생략하면 Google이 요청 IP를 기준으로 결과를 우선한다(IP biasing, `locationBias`·`locationRestriction`이 모두 없을 때의 기본 동작). Tripbler에서는 백엔드가 Google을 호출하므로 사용자 기기가 아니라 **서버의 외부 IP**가 검색 순위에 영향을 줄 수 있다. 근거: [Text Search (New)](https://developers.google.com/maps/documentation/places/web-service/text-search)
- 결과는 최대 20개이고, 없으면 `200 []`이다.

응답: `200 List<PlaceResponse>`

### Google 요청

`POST https://places.googleapis.com/v1/places:searchText`

```json
{
  "textQuery": "경복궁",
  "languageCode": "ko",
  "pageSize": 20,
  "locationBias": {
    "circle": {
      "center": { "latitude": 37.5665, "longitude": 126.978 },
      "radius": 5000.0
    }
  }
}
```

- 좌표가 없으면 `locationBias` 필드 자체를 보내지 않는다(`null`도 보내지 않는다).
- 키는 `X-Goog-Api-Key` 헤더로 보낸다.

### 비용 (필드 마스크)

Google은 요청한 필드 중 가장 높은 등급으로 과금한다. `rating`, `currentOpeningHours`는 Enterprise 등급이다.

| 엔드포인트 | 필드 마스크 | 등급 |
| --- | --- | --- |
| 검색 | `places.id,places.displayName,places.location,places.formattedAddress,places.primaryType` | Pro |
| 주변·최근접 | 위 필드 + `places.rating,places.currentOpeningHours.openNow` | Enterprise |
| 상세 | `id,displayName,location,formattedAddress,rating,primaryType,currentOpeningHours.openNow` | Enterprise |

검색은 평점과 영업 여부를 요청하지 않는다. 필요하면 선택한 장소만 상세 API로 조회한다.

검색은 로그인 사용자만 쓸 수 있지만 사용자별 호출 제한과 캐시는 없다. 반복 호출은 그대로 유료 호출이 된다. Google Cloud 콘솔에서 Places API 일일 쿼터와 예산 알림을 설정해 둔다.

## 주변 장소

`GET /api/v1/places/nearby?lat=37.5665&lng=126.978&type=restaurant&radius=1500`

| 파라미터 | 필수 | 기본값 | 규칙 |
| --- | --- | --- | --- |
| `lat` | 예 | | -90~90 |
| `lng` | 예 | | -180~180 |
| `type` | 아니오 | `tourist_attraction` | `tourist_attraction` 또는 `restaurant`. 빈 값이면 기본값 |
| `radius` | 아니오 | `1500` | 1~50000(m) |

- Google `places:searchNearby`를 `locationRestriction`(반경 안으로 제한), 최대 20개, `languageCode: "ko"`로 호출한다.
- 응답: `200 List<PlaceResponse>`. 결과가 없으면 `[]`.

## 최근접 장소

`GET /api/v1/places/nearest?lat=37.5665&lng=126.978&radius=40`

| 파라미터 | 필수 | 기본값 | 규칙 |
| --- | --- | --- | --- |
| `lat` | 예 | | -90~90 |
| `lng` | 예 | | -180~180 |
| `radius` | 아니오 | `40` | 1~50000(m) |

- 유형 없이 주변 검색(최대 20개 후보)을 한 뒤 그 후보 중 직선거리가 가장 가까운 장소를 고른다. 좌표가 없는 후보는 제외한다. Google이 돌려준 20개 밖에 더 가까운 장소가 있으면 선택되지 않는다.
- 응답: 있으면 `200 PlaceResponse`, 없으면(좌표가 있는 후보가 없을 때 포함) `204 No Content`. 시간 초과, Google 응답 본문 누락 같은 장애는 빈 결과가 아니라 503이다.

## 장소 상세

`GET /api/v1/places/{placeId}`

- Google Place Details를 `languageCode=ko`로 호출한다. 평점과 영업 여부를 포함한다(Enterprise 등급). 검색·주변·최근접·상세 모두 한국어로 받아 이름·주소 언어가 섞이지 않는다.
- 응답: `200 PlaceResponse`.

## 입력 검증

- 좌표는 `BigDecimal`로 받아 `NaN`, `Infinity`, 숫자가 아닌 값을 형식 오류로 막는다.
- 범위 경계값(±90, ±180, 반경 1과 50000)은 허용한다.
- 검증은 Google 호출 전에 끝난다. 실패하면 Google을 호출하지 않는다.

## 오류

모든 오류는 공통 `ErrorResponse`(`timestamp`, `status`, `code`, `message`, `path`) 형식이다.

| 상황 | 상태 | code | message 예 |
| --- | --- | --- | --- |
| 필수 파라미터 없음 | 400 | `INVALID_REQUEST` | `필수 요청 파라미터가 없습니다: lat` |
| 숫자 형식 오류 | 400 | `INVALID_REQUEST` | `요청 파라미터 형식이 올바르지 않습니다.` |
| 범위 밖 위도 | 400 | `INVALID_REQUEST` | `위도는 -90~90 사이여야 합니다.` |
| 범위 밖 경도 | 400 | `INVALID_REQUEST` | `경도는 -180~180 사이여야 합니다.` |
| 범위 밖 반경 | 400 | `INVALID_REQUEST` | `반경은 1~50000m 사이여야 합니다.` |
| 허용하지 않는 `type` | 400 | `INVALID_REQUEST` | `장소 유형은 tourist_attraction 또는 restaurant만 사용할 수 있습니다.` |
| 검색어가 비었거나 100자 초과 | 400 | `INVALID_REQUEST` | `검색어는 공백을 제외하고 1~100자여야 합니다.` |
| 검색 좌표가 하나만 옴 | 400 | `INVALID_REQUEST` | `위도와 경도는 함께 보내야 합니다.` |
| 검색을 토큰 없이 호출 | 401 | `UNAUTHORIZED` | `인증이 필요합니다.` |
| 상세: Google 404 | 404 | `RESOURCE_NOT_FOUND` | `요청한 장소를 찾을 수 없습니다.` |
| 상세: Google 400(키 문제 아님) | 400 | `INVALID_REQUEST` | `장소 ID가 올바르지 않습니다.` |
| Google 장애, 시간 초과, 키 오류, Google 응답 본문 누락 | 503 | `PLACES_PROVIDER_UNAVAILABLE` | `현재 장소 정보 제공 서비스를 이용할 수 없습니다. 잠시 후 다시 시도해 주세요.` |

- 검색·주변·최근접은 입력을 서버에서 먼저 검증하므로 Google 쪽 오류(400 포함)를 모두 503으로 본다.
- 검색·주변·최근접에서 Google 200 응답의 본문 자체가 없으면 결과 0건이 아니라 503이다. 본문이 정상 JSON 객체이고 `places`가 없거나 빈 배열이면 결과 0건(검색·주변은 `200 []`, 최근접은 `204`)이다.
- 상세는 `placeId`가 사용자 입력이라 Google 404·400을 그대로 구분한다. 단, 400이어도 오류 본문의 `reason`이 `API_KEY_INVALID`, `API_KEY_SERVICE_BLOCKED`, `SERVICE_DISABLED`이면 서버 설정 문제이므로 503이다.
- 파라미터 제약(위도, 경도, 반경, `type`) 위반이 여러 개면 메시지를 `, `로 이어서 함께 돌려준다(순서는 보장하지 않음).

## 타임아웃

Google 호출은 연결 2초, 읽기 5초 제한이 있다. 앱의 장소 요청 제한 시간(8초)보다 먼저 503으로 끝나도록 정한 값이다.

- 읽기 제한은 응답 전체 시간이 아니라 데이터를 기다리는 구간마다 걸린다. Google 응답은 작아 보통 한 번에 오므로 대개 7초 안에 끝나지만, 응답이 조금씩 끊겨 오면 합계가 7초(드물게 앱의 8초)를 넘을 수 있다. 전체 시간을 강제하지 않기로 했다(2026-11-12).

## 앱 연동 시 확인할 것

- 검색 요청에는 `Authorization: Bearer <액세스 토큰>`을 붙인다. 없거나 만료되면 401이다.
- 좌표는 둘 다 보내거나 둘 다 생략한다.
- 검색 결과의 `rating`, `openNow`는 `null`이다. 필요하면 장소를 고른 뒤 상세 API로 다시 조회한다.

## 검증

```powershell
.\gradlew.bat test --tests "com.tripbler.backend.place.*" --tests "*SecurityConfigTest"
```

Google 호출은 `MockRestServiceServer`와 로컬 HTTP 서버로 대체하므로 실제 API 키와 외부 네트워크 없이 검증한다.
- `GooglePlacesClientTest`: Google 요청 본문·헤더(필드 마스크), 응답 변환, 오류별 예외, 타임아웃 연결
- `PlaceControllerTest`: 입력 검증, 상태 코드, 오류 응답
- `PlaceControllerSecurityTest`: 실제 `SecurityConfig`로 검색 401과 나머지 공개 API 확인
