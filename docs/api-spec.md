# 사용자 등록 및 조회 API

사용자 API의 기본 경로는 `/api/v1/users`입니다. User/Event 최상위 JSON 필드는 camelCase만 지원하며 기존 snake_case 요청에 대한 alias는 제공하지 않습니다. DB 컬럼명은 snake_case를 유지합니다. metadata 내부 키는 변환하지 않고 원본 그대로 저장·반환합니다. 기존 `common`의 `ApiResponse`, `ApiException`, `GlobalExceptionHandler`를 사용합니다.

## API Key 인증

모든 사용자 API 요청에 서비스에 발급한 `X-API-Key`가 필요합니다. 누락·빈 값·잘못된 키·여러 개의 헤더 값은 HTTP 401 / `UNAUTHORIZED`로 거부합니다.

서버 환경변수 `BTS_API_KEY_HASHES`에 서비스 ID와 API Key의 SHA-256 해시를 매핑한 JSON 객체를 설정합니다. 실제 API Key 원문은 서버 설정 파일이나 Git에 저장하지 않습니다. 미설정 또는 `{}`이면 모든 사용자 API 요청을 거부합니다. 형식이 잘못되었거나 여러 서비스가 같은 해시를 사용하면 시작에 실패합니다.

```text
BTS_API_KEY_HASHES={"ticket-service-a":"<발급 키의 64자리 SHA-256 해시>"}
```

키는 충분히 긴 암호학적 난수로 발급하고, 서비스에 별도 안전한 경로로 전달합니다. HTTP 요청은 운영 환경에서 HTTPS를 사용하며 프록시와 애플리케이션 로그에 `X-API-Key`를 기록하지 않습니다. 현재 서비스당 활성 키는 하나입니다. 회전 시 환경변수 갱신 및 애플리케이션 재시작이 필요합니다.

인증 필터는 키 원문을 요청의 서비스 정보로 전달하지 않고, 인증된 서비스 ID만 전달합니다. 요청 본문의 `serviceId`는 이 서비스 ID와 정확히 일치해야 합니다. 서비스 ID는 등록 설정과 동일한 대소문자를 사용합니다.

## POST /api/v1/users

```http
POST /api/v1/users
Content-Type: application/json
X-API-Key: <서비스에 발급한 키>
```

```json
{
  "serviceId": "ticket-service-a",
  "externalUserId": "external-user-123",
  "accountStatus": "ACTIVE"
}
```

| 필드 | 조건 |
| --- | --- |
| serviceId | 공백만으로 구성될 수 없는 문자열, 최대 50자, 인증 서비스와 일치 |
| externalUserId | 공백만으로 구성될 수 없는 문자열, 최대 100자 |
| accountStatus | 선택, ACTIVE/SUSPENDED만 허용, 생략 또는 null이면 ACTIVE |

입력값은 임의로 대소문자 변경이나 trim을 하지 않습니다. 중복 판정은 기존 MySQL UNIQUE 제약의 문자열 비교 규칙을 따릅니다.

신규 등록은 HTTP 201 / `COMMON_201`입니다. UUID 문자열 기반 내부 사용자 ID를 생성하고 신규 계정에 요청의 accountStatus를 반영하며 기존 Entity 기본값으로 Credit 70 / BASE를 동일 트랜잭션에서 저장합니다.

```json
{
  "success": true,
  "code": "COMMON_201",
  "data": {
    "userId": "c4825ba5-5692-44eb-9882-a8abf733008e",
    "serviceId": "ticket-service-a",
    "externalUserId": "external-user-123",
    "accountStatus": "ACTIVE",
    "createdAt": "2026-10-10T18:00:00",
    "creditScore": 70,
    "tier": "BASE"
  },
  "message": "요청이 성공했습니다."
}
```

이미 등록된 `(serviceId, externalUserId)`는 HTTP 200 / `COMMON_200`과 기존 ID·현재 사용자 정보·현재 Credit을 반환합니다. 기존 상태·점수·등급·시각을 변경하지 않습니다. 재등록 요청의 accountStatus도 기존 상태를 변경하지 않습니다.

동시 등록은 DB UNIQUE 제약으로 보호합니다. 충돌한 등록 트랜잭션을 롤백한 뒤 별도의 트랜잭션에서 기존 사용자를 조회합니다. Credit 저장에 실패하면 계정 저장도 롤백됩니다. 중복 사용자로 복구할 수 없는 제약조건 오류는 409입니다.

## GET /api/v1/users/{userId}

```http
GET /api/v1/users/c4825ba5-5692-44eb-9882-a8abf733008e
X-API-Key: <동일 서비스의 키>
```

성공은 HTTP 200 / `COMMON_200`이며 사용자 상세 DTO를 반환합니다. userId는 공백만으로 구성될 수 없고 최대 50자입니다. 다른 서비스의 사용자와 존재하지 않는 사용자는 모두 동일한 404 응답입니다.

```json
{
  "success": false,
  "code": "USER_NOT_FOUND",
  "data": null,
  "message": "해당 사용자를 찾을 수 없습니다."
}
```


상세 조회의 data는 기존 사용자·Credit 필드에 다음 세 필드를 추가합니다. 등록 및 재등록 응답은 기존 UserResponse를 유지합니다.

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| anomalyScore | number/null | 사용자의 최신 risk_assessment.anomaly_score |
| lastAssessedAt | string/null | 해당 Assessment의 assessed_at, 기존 LocalDateTime 직렬화 유지 |
| latestAction | string/null | 사용자의 최신 policy_decision.action |

Assessment는 assessed_at 내림차순, 같은 시각이면 assessment_id 내림차순으로 한 건을 선택합니다. Policy는 decided_at 내림차순, 같은 시각이면 decision_id 내림차순으로 한 건을 선택합니다. 두 이력은 독립적으로 조회하며, Policy가 최신 Assessment에 연결되어 있어야 한다는 조건은 추가하지 않습니다.

Assessment가 없으면 anomalyScore와 lastAssessedAt은 null, Policy가 없으면 latestAction은 null입니다. 세 필드는 null이어도 JSON에서 생략하지 않습니다. 데이터 조회 전에 기존 서비스별 사용자 접근 권한을 확인합니다. 관리자 인증이나 분석·정책 생성 기능은 추가하지 않습니다.

이력이 없는 사용자 응답 예시:

```json
{
  "success": true,
  "code": "COMMON_200",
  "data": {
    "userId": "c4825ba5-5692-44eb-9882-a8abf733008e",
    "serviceId": "ticket-service-a",
    "externalUserId": "external-user-123",
    "accountStatus": "ACTIVE",
    "createdAt": "2026-10-10T18:00:00",
    "creditScore": 70,
    "tier": "BASE",
    "anomalyScore": null,
    "lastAssessedAt": null,
    "latestAction": null
  },
  "message": "요청이 성공했습니다."
}
```

이력이 있는 경우의 추가 필드 예시:

```json
{
  "anomalyScore": 0.82,
  "lastAssessedAt": "2026-10-10T18:01:00",
  "latestAction": "REQUIRE_CAPTCHA"
}
```
응답에는 Entity 자체, API Key, API Key 해시 및 다른 사용자의 이력이 포함되지 않습니다.

## 오류

| 상황 | HTTP / code |
| --- | --- |
| 필수값·길이·JSON 오류 | 400 / INVALID_REQUEST |
| 키 누락·인증 실패 | 401 / UNAUTHORIZED |
| POST의 serviceId와 인증 서비스 불일치 | 403 / FORBIDDEN |
| 사용자 미존재·타 서비스 사용자 조회 | 404 / USER_NOT_FOUND |
| 중복으로 복구할 수 없는 DB 제약조건 충돌 | 409 / DATA_CONFLICT |
| 내부 오류·기존 계정에 Credit 없음 | 500 / INTERNAL_SERVER_ERROR |

기존 계정에 Credit이 없다면 자동으로 새 Credit을 생성하거나 점수를 초기화하지 않습니다. 데이터 정합성 확인이 필요합니다.

## 테스트

```powershell
.\gradlew.bat build
# 기존 DB_* 환경변수를 설정하고 테이블이 준비된 상태에서 실행
.\gradlew.bat mysqlTest --rerun-tasks
```

단위·MVC 테스트는 API 인증, 검증, 상태·응답 코드 및 서비스 접근 범위를 검증합니다. MySQL 테스트는 실제 등록·조회·재등록, 기존 Credit 유지, Credit 실패 롤백 및 병렬 등록을 검증합니다.

기존 트랜잭션 롤백 테스트를 유지합니다. 신규 사용자 API 테스트는 독립 트랜잭션으로 커밋되는 데이터를 검증하므로 실행마다 임의 서비스 ID를 만들고 해당 서비스의 사용자·Credit 행만 테스트 종료 시 정리합니다. DB 스키마·기존 사용자·Docker 볼륨은 변경하지 않습니다.

## 행동 이벤트 수집 — POST /api/v1/events

사용자 API와 동일한 `X-API-Key` 인증을 사용합니다. 인증 서비스와 본문의 `serviceId`가 다르면 403입니다. `userId` 필드가 누락되거나 null 또는 공백이면 400 / `INVALID_REQUEST`로 응답합니다. `userId`에 해당하는 사용자가 존재하지 않거나 타 서비스 소속이면 404 / `USER_NOT_FOUND`로 응답합니다.

```http
POST /api/v1/events
Content-Type: application/json
X-API-Key: <서비스에 발급한 키>
```

```json
{
  "serviceId": "ticket-service-a",
  "externalEventId": "booking-event-123",
  "userId": "c4825ba5-5692-44eb-9882-a8abf733008e",
  "eventType": "BOOKING",
  "bookingId": "booking-123",
  "occurredAt": "2026-10-10T19:30:00",
  "metadata": {
    "bookingDurationSeconds": 12.5,
    "deviceChanged": false
  }
}
```

| 필드 | 조건 |
| --- | --- |
| serviceId | 필수, 공백 불가, 최대 50자, 인증 서비스와 일치 |
| externalEventId | 필수, 공백 불가, 최대 100자 |
| userId | 필수, 공백 불가, 최대 50자, 같은 서비스의 BTS 사용자 |
| eventType | 필수, BOOKING / CANCEL / CHECK_IN만 허용 |
| bookingId | 최대 100자, BOOKING·CANCEL에서는 필수이며 공백 불가 |
| ticketId | 최대 100자, CHECK_IN에서는 필수이며 공백 불가 |
| occurredAt | 필수, 정확히 yyyy-MM-dd'T'HH:mm:ss, 유효한 날짜·시각, MySQL DATETIME 범위(1000~9999년) |
| metadata | 선택, JSON 객체 또는 null, 중첩 객체·배열 지원 |
| metadata.bookingDurationSeconds | 제공 시 0 이상의 숫자. 문자열·boolean·null은 거부 |
| metadata.deviceChanged | 제공 시 boolean. 문자열·숫자·null은 거부 |

모든 외부 서비스는 **Asia/Seoul 기준 시각**을 전달합니다. 시간대 변환 없이 해당 값을 MySQL `DATETIME`에 저장합니다. 소수 초, `Z`, `+09:00` 등의 시간대 표기, 초가 생략된 문자열은 400입니다. `occurredAt`은 실제 외부 행동 발생 시각이며 서버 수신 시각으로 덮어쓰지 않습니다. 현재 스키마에는 별도 수신 시각 컬럼이 없습니다.

신규 저장은 **201 / COMMON_201**입니다. eventId는 Java Long / MySQL BIGINT AUTO_INCREMENT에 대응하는 64비트 JSON 정수입니다. userId는 UUID 문자열이며 eventId로 바꾸거나 숫자로 변환하지 않습니다.

```json
{
  "success": true,
  "code": "COMMON_201",
  "data": {
    "eventId": 1,
    "serviceId": "ticket-service-a",
    "externalEventId": "booking-event-123",
    "userId": "c4825ba5-5692-44eb-9882-a8abf733008e",
    "eventType": "BOOKING",
    "bookingId": "booking-123",
    "ticketId": null,
    "occurredAt": "2026-10-10T19:30:00",
    "metadata": {
      "bookingDurationSeconds": 12.5,
      "deviceChanged": false
    }
  },
  "message": "요청이 성공했습니다."
}
```

멱등성 키는 `(serviceId, externalEventId)`입니다. 동일 키에서 사용자·유형·예약 ID·티켓 ID·발생 시각·metadata가 같으면 **200 / COMMON_200**으로 기존 이벤트를 반환합니다. 하나라도 다르면 **409 / EVENT_ALREADY_EXISTS**이며 기존 데이터를 변경하지 않습니다. JSON 객체의 키 순서는 중첩 깊이와 관계없이 무시하며 배열 순서는 유지합니다. 숫자는 값으로 비교하므로 `1`과 `1.0`은 같습니다. metadata의 null과 빈 객체는 다릅니다. 문자열은 임의로 trim하거나 변경하지 않으며 멱등성 키의 조회는 기존 MySQL UNIQUE 제약의 문자열 비교 규칙을 따릅니다.

동시 요청은 기존 UNIQUE 제약으로 보호합니다. 충돌한 저장 트랜잭션의 롤백 후 새 트랜잭션에서 저장된 이벤트와 요청 내용을 비교하여 같은 정책을 적용합니다. 복구할 이벤트가 없는 DB 제약조건 충돌은 409입니다. 이번 API는 이벤트만 저장하며 Credit·위험·정책 데이터는 변경하지 않습니다.

| 오류 | HTTP / code |
| --- | --- |
| 필수값·유형·길이·날짜 형식·JSON 오류 | 400 / INVALID_REQUEST |
| 키 누락·잘못된 키·복수 키 헤더 | 401 / UNAUTHORIZED |
| 인증 서비스와 serviceId 불일치 | 403 / FORBIDDEN |
| 사용자 없음·타 서비스 사용자 | 404 / USER_NOT_FOUND |
| 동일 이벤트 식별자의 내용 충돌 | 409 / EVENT_ALREADY_EXISTS |
| 복구 불가능한 DB 제약조건 충돌 | 409 / DATA_CONFLICT |
| 예상하지 못한 내부 오류 | 500 / INTERNAL_SERVER_ERROR |

기존 `build` 및 `mysqlTest` 명령에 이벤트 테스트가 포함됩니다. MySQL 테스트는 BOOKING·CANCEL·CHECK_IN 저장, JSON과 발생 시각 조회, 재전송 및 내용 충돌, 인증·사용자 검증, 동일/다른 내용의 동시 요청을 검증합니다. 실행별 임의 서비스의 이벤트를 먼저 정리하고 해당 테스트 사용자·Credit을 정리합니다. 기존 데이터와 테이블·볼륨은 초기화하지 않습니다. AUTO_INCREMENT 번호는 테스트 실행 중 소비될 수 있습니다.

## 공통 오류 코드 및 메시지

HTTP 상태와 애플리케이션 코드를 별도 필드로 관리합니다. 성공 코드는 `COMMON_200`, `COMMON_201`을 유지합니다. 사용자 미존재·타 서비스 사용자 접근은 `USER_NOT_FOUND`이며, 미등록 URL 등 Spring MVC의 일반 404는 `RESOURCE_NOT_FOUND`입니다. 중복으로 복구할 수 없는 DB 제약조건 충돌은 `DATA_CONFLICT`, 동일 이벤트 식별자에 대한 내용 충돌은 `EVENT_ALREADY_EXISTS`입니다. 아래 기본 매핑에 없는 MVC 오류도 원래 HTTP 상태를 유지하며, 5xx는 `INTERNAL_SERVER_ERROR`, 나머지는 `REQUEST_FAILED`를 반환합니다. 입력값 검증 실패의 필드별 상세 정보는 기존처럼 `data.errors`에 반환합니다.

| HTTP | code | message |
|---|---|---|
| 400 | INVALID_REQUEST | 요청 데이터가 올바르지 않습니다. |
| 401 | UNAUTHORIZED | 인증이 필요합니다. |
| 403 | FORBIDDEN | 접근 권한이 없습니다. |
| 404 | RESOURCE_NOT_FOUND | 요청한 리소스를 찾을 수 없습니다. |
| 405 | METHOD_NOT_ALLOWED | 지원하지 않는 요청 메서드입니다. |
| 406 | NOT_ACCEPTABLE | 지원하지 않는 응답 형식입니다. |
| 409 | DATA_CONFLICT | 요청이 현재 리소스 상태와 충돌합니다. |
| 415 | UNSUPPORTED_MEDIA_TYPE | 지원하지 않는 요청 형식입니다. |
| 500 | INTERNAL_SERVER_ERROR | 서버 내부 오류가 발생했습니다. |
| 503 | SERVICE_UNAVAILABLE | 현재 서비스를 이용할 수 없습니다. |
| 404 | USER_NOT_FOUND | 해당 사용자를 찾을 수 없습니다. |
| 409 | EVENT_ALREADY_EXISTS | 동일한 이벤트 식별자로 서로 다른 내용이 요청되었습니다. |
| 기본 매핑 외 4xx | REQUEST_FAILED | 요청을 처리할 수 없습니다. |

`INVALID_REQUEST`, `UNAUTHORIZED`, `FORBIDDEN`, `USER_NOT_FOUND`, `INTERNAL_SERVER_ERROR`의 코드와 메시지는 기존 Notion 명세서의 문구를 그대로 사용합니다. `EVENT_ALREADY_EXISTS`의 메시지는 최종 이벤트 API 명세서의 내용 충돌 문구를 유지합니다. 사용자 재등록과 동일 이벤트 재전송은 오류가 아니라 200 성공 응답입니다.

User/Event MVC 및 MySQL 테스트에서 HTTP 상태·코드·메시지를 함께 검증하며, MVC 테스트는 예상하지 못한 예외가 내부 정보를 노출하지 않고 500 응답으로 변환되는지도 검증합니다.
