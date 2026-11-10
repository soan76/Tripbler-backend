# 로그아웃 API

`POST /api/v1/auth/logout`

이 기기(세션)의 Refresh Token 하나만 서버에서 폐기한다. Access Token 없이 호출할 수 있어, Access Token이 만료된 상태에서도 로그아웃이 서버에 반영된다.

## 요청

```
POST /api/v1/auth/logout
Content-Type: application/json

{ "refreshToken": "<기기에 저장된 Refresh Token>" }
```

- `Authorization` 헤더는 보내지 않는다. 인증 규칙은 `SecurityConfig`의 `/api/v1/auth/**` 공개 규칙을 따른다.
- 참고: 공개 경로라도 잘못되거나 만료된 `Authorization: Bearer …`를 함께 보내면 리소스 서버 필터가 401을 낸다. 앱은 이 헤더를 붙이지 않는다.

## 응답

| 상황 | 상태 | code |
| --- | --- | --- |
| 요청한 토큰과 일치하는 토큰을 폐기함 | 204 | |
| 일치하는 토큰이 없음(이미 폐기됨, 같은 계정의 재로그인으로 교체됨, 모르는 값) | 204 | |
| 일치하는 토큰이 만료됨(함께 정리) | 204 | |
| `refreshToken`이 비었거나 512자를 넘음 | 400 | `INVALID_REQUEST` |
| 본문이 없거나 JSON 형식이 아님 | 400 | `INVALID_REQUEST` |

- 토큰이 있든 없든 같은 204라서 응답으로 토큰 존재 여부를 알 수 없다.
- 오류 응답과 로그에는 요청 본문(토큰)을 담지 않는다.

## 폐기 방식

- `RefreshTokenRepository.deleteByToken`이 `DELETE ... WHERE token = ?` 한 문장으로 값이 정확히 일치하는 행만 지운다(조회 후 삭제가 아님).
- Refresh Token은 사용자당 한 행이고 다시 로그인하면 같은 행의 값이 바뀐다. 그래서 로그아웃 요청이 늦게 도착해도 그 사이 같은 계정으로 다시 로그인한 세션의 새 토큰은 지우지 않는다.
- 계정 탈퇴는 이와 별개로 그 사용자의 토큰을 모두 지운다(`deleteByUser`).

## 앱 동작

- 로그아웃은 로컬 우선이다. 기기의 Refresh Token을 읽어 둔 뒤 로컬 토큰을 먼저 지우고, 서버 로그아웃은 결과를 기다리지 않고 보낸다(실패는 무시).
- 서버 요청이 실패하면(네트워크 끊김 등) 서버의 Refresh Token은 만료될 때까지 남는다. 재전송은 하지 않는다.

## 검증

```powershell
.\gradlew.bat test --tests "*AuthLogoutControllerTest" --tests "*AuthLogoutFlowTest" --tests "*AuthServiceTest" --tests "*RefreshTokenServiceTest"
```

- `AuthLogoutFlowTest`는 실제 `AuthService`·`RefreshTokenService`에 사용자당 한 행 규칙을 따르는 메모리 저장소를 붙여, 같은 계정으로 재로그인한 뒤 이전 로그아웃이 도착해도 새 Refresh Token으로 재발급되는지 확인한다.
- DB(PostgreSQL)를 쓰지 않으므로 `deleteByToken` JPQL 자체는 이 테스트로 검증되지 않는다. 앱 시작(컨텍스트 로딩) 또는 DB 연동 테스트에서 확인해야 한다.
