# 사용자 등록 및 조회 API

기본 경로는 `/api/v1/users`입니다. 기존 `common`의 `ApiResponse`, `ApiException`, `GlobalExceptionHandler`를 사용합니다.

## API Key 인증

모든 사용자 API 요청에 서비스에 발급한 `X-API-Key`가 필요합니다. 누락·빈 값·잘못된 키·여러 개의 헤더 값은 HTTP 401 / `COMMON_401`로 거부합니다.

서버 환경변수 `BTS_API_KEY_HASHES`에 서비스 ID와 API Key의 SHA-256 해시를 매핑한 JSON 객체를 설정합니다. 실제 API Key 원문은 서버 설정 파일이나 Git에 저장하지 않습니다. 미설정 또는 `{}`이면 모든 사용자 API 요청을 거부합니다. 형식이 잘못되었거나 여러 서비스가 같은 해시를 사용하면 시작에 실패합니다.

```text
BTS_API_KEY_HASHES={"ticket-service-a":"<발급 키의 64자리 SHA-256 해시>"}
```

키는 충분히 긴 암호학적 난수로 발급하고, 서비스에 별도 안전한 경로로 전달합니다. HTTP 요청은 운영 환경에서 HTTPS를 사용하며 프록시와 애플리케이션 로그에 `X-API-Key`를 기록하지 않습니다. 현재 서비스당 활성 키는 하나입니다. 회전 시 환경변수 갱신 및 애플리케이션 재시작이 필요합니다.

인증 필터는 키 원문을 요청의 서비스 정보로 전달하지 않고, 인증된 서비스 ID만 전달합니다. 요청 본문의 `service_id`는 이 서비스 ID와 정확히 일치해야 합니다. 서비스 ID는 등록 설정과 동일한 대소문자를 사용합니다.

## POST /api/v1/users

```http
POST /api/v1/users
Content-Type: application/json
X-API-Key: <서비스에 발급한 키>
```

```json
{
  "service_id": "ticket-service-a",
  "external_user_id": "external-user-123"
}
```

| 필드 | 조건 |
| --- | --- |
| service_id | 공백만으로 구성될 수 없는 문자열, 최대 50자, 인증 서비스와 일치 |
| external_user_id | 공백만으로 구성될 수 없는 문자열, 최대 100자 |

입력값은 임의로 대소문자 변경이나 trim을 하지 않습니다. 중복 판정은 기존 MySQL UNIQUE 제약의 문자열 비교 규칙을 따릅니다.

신규 등록은 HTTP 201 / `COMMON_201`입니다. UUID 기반 내부 사용자 ID를 생성하고 기존 Entity 기본값으로 Credit 70 / BASE를 동일 트랜잭션에서 저장합니다.

```json
{
  "success": true,
  "code": "COMMON_201",
  "data": {
    "user_id": "c4825ba5-5692-44eb-9882-a8abf733008e",
    "service_id": "ticket-service-a",
    "external_user_id": "external-user-123",
    "account_status": "ACTIVE",
    "created_at": "2026-10-10T18:00:00",
    "credit_score": 70,
    "tier": "BASE"
  },
  "message": "요청이 성공했습니다."
}
```

이미 등록된 `(service_id, external_user_id)`는 HTTP 200 / `COMMON_200`과 기존 ID·현재 사용자 정보·현재 Credit을 반환합니다. 기존 상태·점수·등급·시각을 변경하지 않습니다.

동시 등록은 DB UNIQUE 제약으로 보호합니다. 충돌한 등록 트랜잭션을 롤백한 뒤 별도의 트랜잭션에서 기존 사용자를 조회합니다. Credit 저장에 실패하면 계정 저장도 롤백됩니다. 중복 사용자로 복구할 수 없는 제약조건 오류는 409입니다.

## GET /api/v1/users/{userId}

```http
GET /api/v1/users/c4825ba5-5692-44eb-9882-a8abf733008e
X-API-Key: <동일 서비스의 키>
```

성공은 HTTP 200 / `COMMON_200`이며 등록 응답과 동일한 사용자 DTO를 반환합니다. userId는 공백만으로 구성될 수 없고 최대 50자입니다. 다른 서비스의 사용자와 존재하지 않는 사용자는 모두 동일한 404 응답입니다.

```json
{
  "success": false,
  "code": "COMMON_404",
  "data": null,
  "message": "요청한 리소스를 찾을 수 없습니다."
}
```

응답에는 Entity 자체, API Key, API Key 해시, 타 도메인의 행동·위험 정보가 포함되지 않습니다.

## 오류

| 상황 | HTTP / code |
| --- | --- |
| 필수값·길이·JSON 오류 | 400 / COMMON_400 |
| 키 누락·인증 실패 | 401 / COMMON_401 |
| POST의 service_id와 인증 서비스 불일치 | 403 / COMMON_403 |
| 사용자 미존재·타 서비스 사용자 조회 | 404 / COMMON_404 |
| 중복으로 복구할 수 없는 DB 제약조건 충돌 | 409 / COMMON_409 |
| 내부 오류·기존 계정에 Credit 없음 | 500 / COMMON_500 |

기존 계정에 Credit이 없다면 자동으로 새 Credit을 생성하거나 점수를 초기화하지 않습니다. 데이터 정합성 확인이 필요합니다.

## 테스트

```powershell
.\gradlew.bat build
# 기존 DB_* 환경변수를 설정하고 테이블이 준비된 상태에서 실행
.\gradlew.bat mysqlTest --rerun-tasks
```

단위·MVC 테스트는 API 인증, 검증, 상태·응답 코드 및 서비스 접근 범위를 검증합니다. MySQL 테스트는 실제 등록·조회·재등록, 기존 Credit 유지, Credit 실패 롤백 및 병렬 등록을 검증합니다.

기존 트랜잭션 롤백 테스트를 유지합니다. 신규 사용자 API 테스트는 독립 트랜잭션으로 커밋되는 데이터를 검증하므로 실행마다 임의 서비스 ID를 만들고 해당 서비스의 사용자·Credit 행만 테스트 종료 시 정리합니다. DB 스키마·기존 사용자·Docker 볼륨은 변경하지 않습니다.
