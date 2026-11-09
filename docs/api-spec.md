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
  "occurredAt": "2026-09-13T16:53:52+00:00",
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
| occurredAt | 필수, ISO 8601 문자열, UTC 오프셋 필수(Z, +00:00, +09:00 등), UTC 정규화 후 MySQL DATETIME 범위(1000~9999년), 0이 아닌 소수 초는 거부 |
| metadata | 선택, JSON Object 또는 null, 추가 속성 허용, 중첩 객체·배열 지원 |
| metadata.bookingDurationSeconds | 제공 시 0 이상의 숫자. 문자열·boolean·null은 거부 |
| metadata.deviceChanged | 제공 시 boolean. 문자열·숫자·null은 거부 |

요청의 `occurredAt`은 UTC 오프셋을 포함해야 합니다. `2026-09-13T16:53:52+00:00`과 `2026-09-14T01:53:52+09:00`은 동일한 순간이며 둘 다 UTC `2026-09-13T16:53:52`로 저장하고, 응답은 `2026-09-13T16:53:52Z`로 반환합니다. 오프셋 없는 문자열과 유효하지 않은 날짜·오프셋은 400 / `INVALID_REQUEST`입니다. 기존 `DATETIME`의 초 단위 정밀도를 유지하므로 0이 아닌 소수 초는 400이며, `.000`처럼 소수 부분이 0인 표현은 허용합니다. UTC 정규화 후 1000~9999년 범위를 벗어나도 400입니다. `occurredAt`은 실제 행동 발생 시각이며 서버 수신 시각으로 덮어쓰지 않습니다. 스키마·JPA의 `LocalDateTime` 매핑은 유지하고 저장 경계에서 `OffsetDateTime`을 UTC로 변환합니다. 사용자 생성·Assessment·Policy 등 다른 시간 컬럼의 정책은 변경하지 않습니다.

기존 행에는 KST 기준 값이 저장되어 있을 수 있습니다. 이번 변경은 기존 데이터를 수정하지 않습니다. UTC 계약 배포 전에 기존 행의 시간 기준과 전환 시점을 확인하고 별도 마이그레이션을 결정해야 합니다. 기존 KST 행을 UTC로 그대로 읽으면 응답 시각이 9시간 달라지고 동일 이벤트 재전송이 충돌할 수 있으며, 신·구 데이터 혼재는 시간 정렬과 Feature 집계에도 영향을 줍니다. 원본 시간 기준이 확인된 행만 별도 승인된 작업으로 변환해야 합니다.

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
    "occurredAt": "2026-09-13T16:53:52Z",
    "metadata": {
      "bookingDurationSeconds": 12.5,
      "deviceChanged": false
    }
  },
  "message": "요청이 성공했습니다."
}
```

멱등성 키는 `(serviceId, externalEventId)`입니다. 동일 키에서 사용자·유형·예약 ID·티켓 ID·발생 시각(UTC로 정규화한 동일 순간)·metadata가 같으면 **200 / COMMON_200**으로 기존 이벤트를 반환합니다. 하나라도 다르면 **409 / DATA_CONFLICT**이며 기존 데이터를 변경하지 않습니다. JSON 객체의 키 순서는 중첩 깊이와 관계없이 무시하며 배열 순서는 유지합니다. 숫자는 값으로 비교하므로 `1`과 `1.0`은 같습니다. metadata의 null과 빈 객체는 다릅니다. 문자열은 임의로 trim하거나 변경하지 않으며 멱등성 키의 조회는 기존 MySQL UNIQUE 제약의 문자열 비교 규칙을 따릅니다.

동시 요청은 기존 UNIQUE 제약으로 보호합니다. 충돌한 저장 트랜잭션의 롤백 후 새 트랜잭션에서 저장된 이벤트와 요청 내용을 비교하여 같은 정책을 적용합니다. 복구할 이벤트가 없는 DB 제약조건 충돌은 409입니다. 이번 API는 이벤트만 저장하며 Credit·위험·정책 데이터는 변경하지 않습니다.

| 오류 | HTTP / code |
| --- | --- |
| 필수값·유형·길이·날짜 형식·JSON 오류 | 400 / INVALID_REQUEST |
| API Key 누락·유효하지 않은 API Key·복수 키 헤더 | 401 / UNAUTHORIZED |
| 인증 서비스와 serviceId 불일치 | 403 / FORBIDDEN |
| 사용자 없음·타 서비스 사용자 | 404 / USER_NOT_FOUND |
| 동일 이벤트 식별자의 내용 충돌 | 409 / DATA_CONFLICT |
| 복구 불가능한 DB 제약조건 충돌 | 409 / DATA_CONFLICT |
| 예상하지 못한 내부 오류 | 500 / INTERNAL_SERVER_ERROR |

기존 `build` 및 `mysqlTest` 명령에 이벤트 테스트가 포함됩니다. MySQL 테스트는 BOOKING·CANCEL·CHECK_IN 저장, JSON과 발생 시각 조회, 재전송 및 내용 충돌, 인증·사용자 검증, 동일/다른 내용의 동시 요청을 검증합니다. 실행별 임의 서비스의 이벤트를 먼저 정리하고 해당 테스트 사용자·Credit을 정리합니다. 기존 데이터와 테이블·볼륨은 초기화하지 않습니다. AUTO_INCREMENT 번호는 테스트 실행 중 소비될 수 있습니다.

## 공통 오류 코드 및 메시지

HTTP 상태와 애플리케이션 코드를 별도 필드로 관리합니다. 성공 코드는 `COMMON_200`, `COMMON_201`을 유지합니다. 사용자 미존재·타 서비스 사용자 접근은 `USER_NOT_FOUND`이며, 미등록 URL 등 Spring MVC의 일반 404는 `RESOURCE_NOT_FOUND`입니다. 중복으로 복구할 수 없는 DB 제약조건 충돌은 `DATA_CONFLICT`, 동일 이벤트 식별자에 대한 내용 충돌도 `DATA_CONFLICT`입니다. 아래 기본 매핑에 없는 MVC 오류도 원래 HTTP 상태를 유지하며, 5xx는 `INTERNAL_SERVER_ERROR`, 나머지는 `REQUEST_FAILED`를 반환합니다. 입력값 검증 실패의 필드별 상세 정보는 기존처럼 `data.errors`에 반환합니다.

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
| 기본 매핑 외 4xx | REQUEST_FAILED | 요청을 처리할 수 없습니다. |

`INVALID_REQUEST`, `UNAUTHORIZED`, `FORBIDDEN`, `USER_NOT_FOUND`, `INTERNAL_SERVER_ERROR`의 코드와 메시지는 기존 Notion 명세서의 문구를 그대로 사용합니다. 이벤트 내용 충돌은 이번 UTC 계약 변경에 따라 `DATA_CONFLICT`로 응답합니다. 사용자 재등록과 동일 이벤트 재전송은 오류가 아니라 200 성공 응답입니다.

User/Event MVC 및 MySQL 테스트에서 HTTP 상태·코드·메시지를 함께 검증하며, MVC 테스트는 예상하지 못한 예외가 내부 정보를 노출하지 않고 500 응답으로 변환되는지도 검증합니다.

## 조회 API 공통 규칙

기존 사용자 상세·등록·이벤트 수집 계약은 변경하지 않습니다. 다음 네 조회 API는 HTTP 200 / COMMON_200 및 기존 success/code/data/message 형식을 사용합니다. 조회로 AI 평가·Credit 산출 또는 데이터 변경을 수행하지 않습니다.

| 조건 | 규칙 |
|---|---|
| page | 0부터 시작, 기본 0, 음수 불가 |
| size | 기본 20, 1~100 |
| sort | 하나의 필드와 방향을 쉼표로 연결. 방향은 소문자 asc 또는 desc |

잘못된 정렬 필드·방향·형식, 숫자 형식·범위는 400 / INVALID_REQUEST입니다. page * size가 정수 offset 범위를 초과해도 400입니다. 동일한 정렬값은 ID를 같은 방향으로 추가 정렬합니다. 요청 사이 데이터가 추가되면 offset 페이지 위치는 바뀔 수 있습니다.

페이지 data 형식:

~~~json
{"content":[],"page":0,"size":20,"totalElements":0,"totalPages":0,"hasNext":false}
~~~

필터 결과 없음과 마지막 페이지 초과는 200 및 빈 content입니다. totalElements와 totalPages는 조건에 맞는 전체 결과를 나타냅니다.

## GET /api/v1/users

일반 서비스 키 전용입니다. 인증된 서비스만 조회하며 serviceId 선택은 허용하지 않습니다. 요청 조건은 page, size, sort입니다. 관리자 검색 필터를 지정하면 400입니다.

기본 정렬은 createdAt,desc 및 userId,desc이며 허용 필드는 createdAt, userId, creditScore입니다. content는 기존 UserResponse와 동일한 userId, serviceId, externalUserId, accountStatus, createdAt, creditScore, tier입니다. AI 평가·정책·이력 컬렉션은 포함하지 않습니다.

사용자·Credit은 DTO projection으로 함께 조회하며 사용자별 쿼리를 반복하지 않습니다. 반환할 페이지의 사용자에게 Credit이 없으면 기존 정합성 계약대로 500이며 자동 생성하지 않습니다.

예: GET /api/v1/users?page=0&size=20&sort=creditScore,desc

## GET /api/v1/users/{userId}/events

일반 서비스 키 전용입니다. userId 조건은 기존 상세와 같습니다. 사용자 미존재·타 서비스 사용자는 동일한 404 / USER_NOT_FOUND입니다. 소속 확인 후 사용자 및 서비스 조건으로 이벤트를 조회합니다.

| 조건 | 규칙 |
|---|---|
| page, size, sort | 공통 페이지 규칙 |
| eventType | 선택. BOOKING, CANCEL, CHECK_IN |
| from | 선택. 발생 시각 하한 포함 |
| to | 선택. 발생 시각 상한 미포함 |

기본 정렬은 occurredAt,desc 및 eventId,desc이며 허용 필드는 occurredAt, eventId입니다. 기간은 from <= occurredAt < to이며 둘 다 있으면 from < to여야 합니다. ISO 8601 오프셋이 필수이며 UTC로 정규화합니다. 정규화 후 1000~9999년·초 단위 정밀도를 요구합니다. 오프셋 누락·잘못된 날짜·0이 아닌 소수 초는 400입니다.

content는 기존 EventResponse와 동일합니다. eventId는 64비트 JSON 정수, occurredAt은 UTC Z 문자열이며 metadata 원형을 유지합니다. 과거 KST 데이터는 변환하지 않으므로 기존 명세의 시간 기준 확인이 필요합니다.

예: GET /api/v1/users/{userId}/events?eventType=BOOKING&from=2026-01-01T00:00:00Z&to=2026-02-01T00:00:00Z

양수 오프셋의 +는 URL에서 %2B로 인코딩합니다.

## 관리자 인증 및 데이터 범위

X-API-Key를 유지하며 별도 환경변수 BTS_ADMIN_API_KEYS를 설정합니다.

~~~json
[
  {"hash":"<64자리 SHA-256 해시>","allServices":false,"services":["ticket-service-a","ticket-service-b"]},
  {"hash":"<다른 64자리 SHA-256 해시>","allServices":true,"services":[]}
]
~~~

서비스 관리자는 allServices=false와 비어 있지 않은 services, 전체 관리자는 allServices=true와 빈 services를 사용합니다. 서비스 ID는 최대 50자이며 공백 문자열·앞뒤 공백은 불가합니다. 키 원문을 저장·반환·로그 출력하지 않습니다. 관리자 키 사이 및 기존 서비스 키와 같은 해시는 허용하지 않습니다. 잘못된 설정은 시작에 실패합니다. 미설정 또는 []이면 관리자 키를 인증하지 않습니다.

- 일반 서비스 키로 admin 또는 dashboard 접근: 403 / FORBIDDEN.
- 관리자 키로 users 또는 events 접근: 403 / FORBIDDEN. 등록·수집용 서비스 키와 분리합니다.
- 키 누락·오류·복수 헤더: 401 / UNAUTHORIZED.
- serviceId 생략: 서비스 관리자는 허용 서비스 전체, 전체 관리자는 모든 서비스.
- serviceId 지정: 권한 내 해당 서비스만 조회. 권한 밖이면 403 / FORBIDDEN.
- 서비스 ID 권한은 대소문자·바이트를 구분하며 DB의 대소문자 무시 collation으로 범위가 확대되지 않습니다.

보호 경로는 /api/v1/admin 및 하위 경로, /api/v1/dashboard 및 하위 경로입니다. 기존 서비스 키 환경변수와 동작을 유지합니다.

## GET /api/v1/admin/users

관리자 키 전용이며 content는 AdminUserResponse입니다. 기존 UserResponse의 모든 필드를 유지하고
anomalyScore(number/null), lastAssessedAt(LocalDateTime/null)를 추가합니다. 평가가 없으면 두 값은 null입니다.
최신 평가는 assessedAt 내림차순, 동률이면 assessmentId 내림차순으로 선택합니다.
기존 목록 쿼리와 페이지 내 사용자 전체의 최신 평가 일괄 조회를 사용하며 상세 API를 사용자별로 반복 호출하지 않습니다.

| 조건 | 규칙 |
|---|---|
| page, size, sort | 사용자 목록과 동일 |
| serviceId | 권한 범위 내 서비스 선택 |
| userId | 내부 ID 정확 검색, 1~50자 |
| externalUserId | 외부 ID 정확 검색, 1~100자 |
| accountStatus | ACTIVE 또는 SUSPENDED |
| tier | 저장된 등급 문자열 정확 검색, 1~20자 |
| minCreditScore, maxCreditScore | 각각 0~100, 최소 <= 최대 |
| createdFrom, createdTo | 기존 LocalDateTime 형식. 하한 포함·상한 미포함, from < to |

모든 조건은 AND로 결합하며 공백만 입력은 400입니다. 식별자·등급 검색은 기존 DB 문자열 비교 규칙을 유지하지만 서비스 권한 범위는 바이트 단위로 제한합니다. 새 등급 enum·AI 임계값·최신 정책 필터는 추가하지 않습니다.

예: GET /api/v1/admin/users?serviceId=ticket-service-a&accountStatus=ACTIVE&minCreditScore=70&sort=creditScore,desc

## GET /api/v1/dashboard/summary

관리자 키 전용입니다. 선택 조건은 serviceId(1~50자, 공백만 입력 불가)이며 권한 범위는 관리자 목록과 같습니다. 기간 조건 없는 누적 통계입니다.

data 예시:

~~~json
{
  "totalUsers":120,
  "activeUsers":110,
  "suspendedUsers":10,
  "usersWithCredit":120,
  "averageCreditScore":70.0,
  "usersByTier":{"BASE":120},
  "totalEvents":450,
  "eventsByType":{"BOOKING":300,"CANCEL":100,"CHECK_IN":50}
}
~~~

사용자 상태는 user_account 행, Credit은 user_credit의 현재 값, 이벤트는 behavior_event 행을 각각 집계하여 이력 JOIN에 의한 중복 계산을 방지합니다. 이벤트 수는 고유 예약 수가 아닙니다. 평균은 Credit이 있는 사용자만 대상으로 하며 대상이 없으면 null입니다. usersWithCredit으로 누락 여부를 확인할 수 있으며 누락 Credit을 생성하지 않습니다.

빈 범위는 건수 0, 평균 null, usersByTier 빈 객체입니다. BOOKING/CANCEL/CHECK_IN은 0이어도 반환하며 기존 데이터에 다른 유형이 있으면 해당 키도 포함합니다. 집계는 MySQL REPEATABLE_READ 트랜잭션의 동일 스냅샷에서 수행합니다.

이상 사용자 수는 임계값·최신 평가 기준의 별도 합의가 필요하여 포함하지 않습니다. 별도 테이블·Entity·스키마·인덱스 변경은 없습니다. 사용자 복합 정렬과 대시보드 대량 집계는 운영 데이터 규모에서 실행 계획 확인이 필요합니다.

등록 기간 조건은 MySQL DATETIME 범위인 1000~9999년을 사용합니다.

## 관리자 사용자 상세 및 이력 조회 (#35)

모든 경로는 기존 관리자 `X-API-Key`를 사용합니다. 일반 서비스 키는 403,
키 누락/오류는 401입니다. `userId`는 공백 불가, 최대 50자입니다.
존재하지 않는 사용자와 관리자 허용 서비스 범위 밖 사용자는 동일한 404 / USER_NOT_FOUND입니다.
전체 관리자는 모든 서비스의 사용자에 접근할 수 있습니다. 조회는 평가·정책 생성이나 Credit 갱신을 실행하지 않습니다.

### GET /api/v1/admin/users/{userId}

쿼리 파라미터는 없습니다. `data`는 `AdminUserDetailResponse`입니다.

```json
{
  "user": {
    "userId": "bts-user-uuid", "serviceId": "ticket-service-a", "externalUserId": "external-123",
    "accountStatus": "ACTIVE", "createdAt": "2026-11-01T10:00:00", "creditScore": 70, "tier": "BASE",
    "anomalyScore": 0.3, "lastAssessedAt": "2026-11-10T10:00:00", "latestAction": "ALLOW"
  },
  "latestPolicy": {
    "decisionId": 10, "userId": "bts-user-uuid", "context": "QUEUE_JOIN",
    "creditScore": 70, "anomalyScore": 0.3, "action": "ALLOW", "reason": "정상 사용자 판정",
    "decidedAt": "2026-11-10T10:01:00+09:00"
  }
}
```

`user`는 기존 UserDetailResponse와 동일합니다. `latestPolicy`는 기존 PolicyDecisionResponse이며
정책 생성 당시 점수의 스냅샷을 포함합니다. 정책이 없으면 null입니다.
최신 평가와 최신 정책은 독립적으로 선택되므로 서로 다른 평가를 참조할 수 있습니다.
현재 Credit 누락 시 기존 상세 계약과 동일하게 500입니다.
사용자·평가 시각은 기존 LocalDateTime, 정책 시각은 기존 KST 오프셋 형식을 유지합니다.

### GET /api/v1/admin/users/{userId}/events

`page,size,sort,eventType,from,to` 및 응답은 기존 일반 사용자 이벤트 조회와 동일합니다.
`eventType`은 BOOKING/CANCEL/CHECK_IN/BOOKING_ATTEMPT입니다.
기간은 UTC 정규화 후 `from <= occurredAt < to`이며, 오프셋이 필수입니다.
기본 정렬은 occurredAt,desc이고 occurredAt/eventId 정렬을 허용하며 ID로 동률 정렬합니다.
`data`는 `PageResponse<EventResponse>`입니다. 조건에 맞는 이력이 없으면 빈 content를 반환합니다.

### GET /api/v1/admin/users/{userId}/assessments

| 파라미터 | 규칙 |
|---|---|
| page | 0부터 시작, 기본 0 |
| size | 기본 20, 1~100 |
| sort | assessedAt / assessmentId / anomalyScore 중 하나와 asc / desc. 기본 assessedAt,desc |

동률은 assessmentId를 같은 방향으로 정렬합니다. 잘못된 정렬·페이지·offset 범위는 400 / INVALID_REQUEST입니다.
`data`는 `PageResponse<AssessmentResponse>`이며 예시는 다음과 같습니다.

```json
{
  "content": [{"assessmentId": 5, "anomalyScore": 0.3, "policyAction": "ALLOW",
    "modelVersion": "iforest-v1", "assessedAt": "2026-11-10T10:00:00"}],
  "page": 0, "size": 20, "totalElements": 1, "totalPages": 1, "hasNext": false
}
```

`anomalyScore`는 저장된 이상 탐지 점수를 그대로 반환합니다. DB에는 정상/이상 판정 라벨이 없으며 임의의 AI 판정 등급을 생성하지 않습니다.
`policyAction`은 **같은 사용자와 해당 평가에 연결된 최신 정책의 action**으로, AI 판정과 구분되는 정책 대응 결과입니다.
decidedAt 내림차순, 동률이면 decisionId 내림차순으로 선택하며 연결 정책이 없으면 null입니다.
이는 AI의 이진 판정값이 아니며 새 임계값을 적용하지 않습니다. 모델 판정 라벨이 필요하면 별도 계약 합의가 필요합니다.
이력이 없거나 마지막 페이지를 초과하면 200 및 빈 content를 반환합니다.

기존 일반 사용자 API의 DTO와 Demo Ticketing 계약은 변경하지 않습니다.
Credit 전용 두 API의 Bearer 형식 검사 및 서비스 범위 검증 누락은 이번 변경 대상에서 제외했으며 별도 보완이 필요합니다.

## 공연 정보 연동 및 관리자 조회 (#38)

공연 원본은 외부 티켓팅 서비스에 있습니다. BTS는 관리자 조회에 필요한 복제 정보만 저장하며 외부 DB에 연결하지 않습니다.
공연은 `(serviceId, concertId)`로 식별합니다. ID는 대소문자를 구분하며 concertId 및 관리자 serviceId는 공백 없이 1~50자입니다.
공연 시각은 오프셋을 포함한 ISO 8601로 입력하고 UTC `Z`로 반환합니다. UTC 기준 1000~9999년, 초 단위만 허용합니다.
DB에는 UTC DATETIME을 저장하며 클라이언트에서 화면 시간대로 변환합니다.

공통 응답은 `success/code/data/message`이며 성공은 200 / COMMON_200 / "요청이 성공했습니다."입니다.
아래 JSON은 공통 응답의 **data** 예시입니다. 전체 응답 예시는 다음과 같습니다.

```json
{"success":true,"code":"COMMON_200","data":{"serviceId":"ticket-service-a","concertId":"c1","name":"BTS Concert","startsAt":"2026-12-01T10:00:00Z","endsAt":"2026-12-01T12:00:00Z","venue":"Seoul Hall"},"message":"요청이 성공했습니다."}
```

### PUT /api/v1/concerts/{concertId} — 외부 공연 등록·갱신

#### 1. Header

| 이름 | 필수 | 설명 |
|---|---|---|
| X-API-Key | O | 일반 서비스 키 1개. 서비스 ID는 서버에서 결정 |
| Content-Type | O | application/json |

#### 2. Request

| 위치 | 필드 | 타입 | 필수 | 조건 |
|---|---|---|---|---|
| Path Variable | concertId | string | O | 외부 서비스 공연 ID, 공백 없이 1~50자 |
| Query String | 없음 | — | — | serviceId를 선택하는 기능 없음 |
| Body | name | string | O | 공백만 입력 불가, 최대 200자 |
| Body | startsAt | string(datetime) | O | 오프셋 필수, UTC 초 단위 |
| Body | endsAt | string(datetime) | O | startsAt보다 뒤, 동일 시각 불가 |
| Body | venue | string | O | 공백만 입력 불가, 최대 255자 |

```http
PUT /api/v1/concerts/c1
X-API-Key: <서비스 키>
Content-Type: application/json

{"name":"BTS Concert","startsAt":"2026-12-01T19:00:00+09:00","endsAt":"2026-12-01T21:00:00+09:00","venue":"Seoul Hall"}
```

#### 3. Response

신규·재전송·갱신 모두 200입니다. 동일 키는 원자적 upsert로 갱신하며 중복 행을 만들지 않습니다.
PATCH가 아닌 전체 기본정보 교체이며 name/시작/종료/장소를 매번 모두 전송합니다.

| data 필드 | 타입 | 의미 |
|---|---|---|
| serviceId | string | 인증된 서비스 ID |
| concertId | string | 외부 공연 ID |
| name | string | 공연명 |
| startsAt | string(datetime) | UTC 시작 시각 |
| endsAt | string(datetime) | UTC 종료 시각 |
| venue | string | 장소 |

```json
{"serviceId":"ticket-service-a","concertId":"c1","name":"BTS Concert","startsAt":"2026-12-01T10:00:00Z","endsAt":"2026-12-01T12:00:00Z","venue":"Seoul Hall"}
```

#### 4. Error

| HTTP | code | 원인 |
|---|---|---|
| 400 | INVALID_REQUEST | 필수 필드/길이/ID/시각/기간/JSON 오류 |
| 401 | UNAUTHORIZED | 키 누락·오류·복수 헤더 |
| 403 | FORBIDDEN | 관리자 키 사용 |
| 415 | UNSUPPORTED_MEDIA_TYPE | 지원하지 않는 요청 형식 |
| 500 | INTERNAL_SERVER_ERROR | DB 미적용 또는 처리되지 않은 내부 오류 |

#### 5. Exception

| 상황 | 처리 |
|---|---|
| 동일 공연 재전송·동시 등록 | 복합 UNIQUE 및 단일 upsert SQL로 보호. 중복 생성 없음 |
| 다른 서비스의 같은 concertId | 별도 공연으로 저장. 다른 서비스 행 변경 안 함 |
| 이벤트가 먼저 도착한 공연 | 등록 가능. 등록 후 기존 이벤트가 조회에 연결됨 |
| 공연 삭제·취소 상태 동기화 | 미구현. 별도 정책 합의 필요 |

### GET /api/v1/admin/concerts — 관리자 공연 목록

#### 1. Header

| 이름 | 필수 | 설명 |
|---|---|---|
| X-API-Key | O | 관리자 키 1개. 일반 서비스 키는 403 |

#### 2. Request

Path Variable과 Body는 없습니다.

| Query String | 타입 | 필수 | 기본값/조건 |
|---|---|---|---|
| serviceId | string | O | 관리자 권한 내 서비스, 공백 없이 1~50자 |
| page | integer | X | 0, 0 이상 |
| size | integer | X | 20, 1~100 |
| sort | string | X | startsAt,desc. startsAt / concertId / name 및 asc / desc |

`GET /api/v1/admin/concerts?serviceId=ticket-service-a&page=0&size=20&sort=startsAt,desc`

#### 3. Response

| data 필드 | 타입 | 설명 |
|---|---|---|
| content | ConcertResponse[] | 위 PUT 응답의 serviceId/concertId/name/startsAt/endsAt/venue |
| page, size | integer | 현재 페이지·크기 |
| totalElements, totalPages | integer | 전체 공연 수·전체 페이지 수 |
| hasNext | boolean | 다음 페이지 여부 |

```json
{"content":[{"serviceId":"ticket-service-a","concertId":"c1","name":"BTS Concert","startsAt":"2026-12-01T10:00:00Z","endsAt":"2026-12-01T12:00:00Z","venue":"Seoul Hall"}],"page":0,"size":20,"totalElements":1,"totalPages":1,"hasNext":false}
```

동률은 concertId를 같은 방향으로 정렬합니다. 목록에서는 이벤트 집계를 수행하지 않으며 통계는 상세에서 제공합니다.

#### 4. Error

| HTTP | code | 원인 |
|---|---|---|
| 400 | INVALID_REQUEST | serviceId 누락/형식, 페이지/offset/정렬 오류 |
| 401 | UNAUTHORIZED | 키 누락·오류·복수 헤더 |
| 403 | FORBIDDEN | 일반 서비스 키 또는 허용 범위 밖 serviceId |
| 500 | INTERNAL_SERVER_ERROR | DB 또는 내부 오류 |

#### 5. Exception

| 상황 | 처리 |
|---|---|
| 등록된 공연 없음·페이지 초과 | 200 및 빈 content |
| 이벤트만 있는 미등록 공연 | 목록 제외 |
| 전체 관리자 | 전체 관리자도 serviceId를 반드시 지정 |

### GET /api/v1/admin/concerts/{concertId} — 관리자 공연 상세

#### 1. Header

| 이름 | 필수 | 설명 |
|---|---|---|
| X-API-Key | O | 관리자 키 1개 |

#### 2. Request

| 위치 | 필드 | 타입 | 필수 | 조건 |
|---|---|---|---|---|
| Path Variable | concertId | string | O | 공백 없이 1~50자 |
| Query String | serviceId | string | O | 공백 없이 1~50자, AdminScope 범위 내 |
| Body | 없음 | — | — | — |

`GET /api/v1/admin/concerts/c1?serviceId=ticket-service-a`

#### 3. Response

| data 필드 | 타입 | 설명 |
|---|---|---|
| concert | ConcertResponse | 위 PUT 응답과 같은 공연 기본정보 |
| bookingAttemptCount | integer | STARTED/ENTERED가 있는 고유 (사용자, 시도, 공연) 그룹 수 |
| bookingAttemptUserCount | integer | 위 시작 이벤트가 있는 고유 사용자 수 |

```json
{"concert":{"serviceId":"ticket-service-a","concertId":"c1","name":"BTS Concert","startsAt":"2026-12-01T10:00:00Z","endsAt":"2026-12-01T12:00:00Z","venue":"Seoul Hall"},"bookingAttemptCount":5,"bookingAttemptUserCount":3}
```

#### 4. Error

| HTTP | code | 원인 |
|---|---|---|
| 400 | INVALID_REQUEST | serviceId 누락 또는 ID 형식 오류 |
| 401 | UNAUTHORIZED | 인증 실패 |
| 403 | FORBIDDEN | 일반 서비스 키 또는 권한 밖 서비스 |
| 404 | CONCERT_NOT_FOUND | 권한 내 서비스에 해당 공연이 미등록 |
| 500 | INTERNAL_SERVER_ERROR | DB 또는 내부 오류 |

#### 5. Exception

| 상황 | 처리 |
|---|---|
| 등록됐지만 이벤트 없음 | 200, 두 통계 모두 0 |
| 후속 단계만 있고 시작 없음 | 해당 시도·사용자 집계 제외 |
| 같은 시도의 여러 단계·중복 시작 | 시도는 1회. 같은 사용자의 여러 시도는 사용자 1명 |
| BOOKING/CANCEL/CHECK_IN | 이 통계에서 제외 |
| 다른 서비스 사용자에 잘못 연결된 이벤트 | 사용자 소속 서비스까지 검증하여 제외 |
| 공연별 AI 점수·이상 사용자 수 | 현재 평가에 공연 ID/판정 라벨이 없어 산출하지 않음 |

### GET /api/v1/admin/concerts/{concertId}/users — 공연별 사용자

#### 1. Header

| 이름 | 필수 | 설명 |
|---|---|---|
| X-API-Key | O | 관리자 키 1개 |

#### 2. Request

Path Variable concertId는 공백 없는 1~50자이며 Body는 없습니다.

| Query String | 타입 | 필수 | 조건 |
|---|---|---|---|
| serviceId | string | O | 공백 없는 1~50자, AdminScope 범위 내 |
| page, size, sort | integer, integer, string | X | 기존 관리자 사용자 목록과 동일. 기본 0/20/createdAt,desc; size 1~100 |
| userId, externalUserId | string | X | 정확 일치, 각각 1~50/1~100자 |
| accountStatus | string | X | ACTIVE 또는 SUSPENDED |
| tier | string | X | 정확 일치, 1~20자 |
| minCreditScore, maxCreditScore | integer | X | 0~100, 최소 <= 최대 |
| createdFrom, createdTo | string(datetime) | X | 사용자 등록일 필터. 기존 LocalDateTime, 하한 포함·상한 미포함 |

정렬은 createdAt/userId/creditScore 및 asc/desc를 지원하고 userId로 동률 정렬합니다.
모든 필터는 AND이며 공연 기간·사용자 활동 시각 필터와 최근 활동 기준 정렬은 제공하지 않습니다.

`GET /api/v1/admin/concerts/c1/users?serviceId=ticket-service-a&minCreditScore=70&sort=creditScore,desc`

#### 3. Response

| data 필드 | 타입 | 설명 |
|---|---|---|
| content | ConcertUserResponse[] | 중복 제거한 사용자 목록 |
| content[].user | AdminUserResponse | 기존 #35 관리자 사용자 DTO 재사용 |
| user.userId / serviceId / externalUserId | string | BTS ID / 서비스 ID / 외부 사용자 ID |
| user.accountStatus / tier | string | 계정 상태 / 현재 등급 |
| user.createdAt | string(datetime) | 기존 사용자 생성 LocalDateTime |
| user.creditScore | integer | user_credit의 현재 점수 |
| user.anomalyScore | number/null | **공연별 점수가 아닌 사용자의 최신 전체 평가 점수** |
| user.lastAssessedAt | string(datetime)/null | 최신 전체 평가의 기존 LocalDateTime |
| content[].lastBookingAttemptAt | string(datetime) | 해당 공연 BOOKING_ATTEMPT의 최대 occurred_at, UTC Z |
| page, size, totalElements, totalPages, hasNext | integer/boolean | 기존 PageResponse 구조 |

```json
{"content":[{"user":{"userId":"bts-uuid","serviceId":"ticket-service-a","externalUserId":"external-1","accountStatus":"ACTIVE","createdAt":"2026-11-01T10:00:00","creditScore":80,"tier":"BASE","anomalyScore":0.2,"lastAssessedAt":"2026-11-10T10:00:00"},"lastBookingAttemptAt":"2026-11-10T10:05:00Z"}],"page":0,"size":20,"totalElements":1,"totalPages":1,"hasNext":false}
```

#### 4. Error

| HTTP | code | 원인 |
|---|---|---|
| 400 | INVALID_REQUEST | serviceId 누락, ID/페이지/정렬/검색조건 오류 |
| 401 | UNAUTHORIZED | 인증 실패 |
| 403 | FORBIDDEN | 일반 서비스 키 또는 권한 밖 서비스 |
| 404 | CONCERT_NOT_FOUND | 미등록 공연 |
| 500 | INTERNAL_SERVER_ERROR | 현재 Credit 누락(기존 목록 정합성 계약) 또는 DB/내부 오류 |

#### 5. Exception

| 상황 | 처리 |
|---|---|
| 같은 사용자 여러 시도·여러 이벤트 | DB의 EXISTS 조건으로 사용자 행 중복 방지 |
| 시작 없는 사용자 | 제외 |
| 평가 없음 | anomalyScore/lastAssessedAt null |
| 조회 결과 없음·페이지 초과 | 200 및 빈 content |
| 최근 활동 | 포함된 사용자의 해당 공연 모든 BOOKING_ATTEMPT 중 최대 발생 시각. sequence 최신 상태와 다른 개념 |
| AI·Credit·정책 | 조회만 수행. 계산·평가·정책 생성을 호출하지 않음 |

### 공통 처리 및 배포 주의

- 모든 관리자 조회는 serviceId 필수이며 AdminScope로 검증합니다. 범위 밖 serviceId는 403입니다.
- 서비스·공연 ID 비교는 바이트 기준으로 제한하여 다른 서비스/대소문자 변형이 혼입되지 않도록 합니다.
- 이벤트 수집은 공연 등록 여부를 검사하지 않습니다. 미등록 공연 이벤트도 수집하고 등록 후 기존 이벤트를 연결합니다.
- 조회는 MySQL REPEATABLE_READ 읽기 트랜잭션으로 처리하며 페이지 내 최신 평가·활동은 일괄 조회합니다.
- 오류 메시지는 INVALID_REQUEST="요청 데이터가 올바르지 않습니다.", UNAUTHORIZED="인증이 필요합니다.",
  FORBIDDEN="접근 권한이 없습니다.", CONCERT_NOT_FOUND="해당 공연을 찾을 수 없습니다.",
  INTERNAL_SERVER_ERROR="서버 내부 오류가 발생했습니다."입니다. MVC의 405/406/415도 기존 공통 규칙을 따릅니다.
- 신규 빈 DB는 schema.sql, 기존 DB는 `db/manual/038-create-concert.sql`만 검토 후 수동 적용합니다.
- Demo Ticketing 연동 방법은 [공연 정보 연동 가이드](concert-integration.md)를 참고합니다.
