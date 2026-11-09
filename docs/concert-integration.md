# Demo Ticketing / 외부 서비스 공연 연동 (#38)

Demo 저장소는 이번 작업에서 수정하지 않습니다. 아래 사항을 Demo 서버에서 별도 구현합니다.

1. 공연 생성·변경 후 서버에서 BTS `PUT /api/v1/concerts/{concertId}`를 호출합니다.
2. 일반 서비스용 X-API-Key는 서버 설정에 보관하고 브라우저에 전달하지 않습니다.
3. 요청에는 name, startsAt, endsAt, venue를 모두 포함합니다. 외부 공연 ID는 공백 없는 1~50자입니다.
4. 공연 시작·종료는 오프셋을 포함한 초 단위 ISO 8601을 전송합니다. 종료는 시작보다 뒤여야 합니다.
5. 동일 공연 재전송·수정 모두 200입니다. 응답 유실 시 동일 요청으로 재시도해도 중복 공연이 생성되지 않습니다.
6. 이벤트 metadata.concertId와 PUT 경로의 concertId는 대소문자까지 동일하게 유지합니다.
7. 공연 등록 실패가 이벤트 수집을 중단시키지 않도록 합니다. 등록 성공 전까지 관리자 조회만 제한됩니다.

```http
PUT https://{BTS_HOST}/api/v1/concerts/concert-123
X-API-Key: <서버에 설정된 일반 서비스 키>
Content-Type: application/json

{
  "name": "Demo Concert",
  "startsAt": "2026-12-01T19:00:00+09:00",
  "endsAt": "2026-12-01T21:00:00+09:00",
  "venue": "Seoul Hall"
}
```

BTS는 serviceId를 키에서 결정합니다. 요청 body/query의 serviceId로 서비스를 선택하지 않습니다.
다른 서비스에서 동일 concertId를 사용해도 독립적으로 저장합니다. 원본 공연 DB에 BTS가 직접 접근하지 않습니다.

관리자 화면은 관리자 키로 아래 API를 사용하고 serviceId를 반드시 지정합니다.

```http
GET /api/v1/admin/concerts?serviceId=ticket-service-a
GET /api/v1/admin/concerts/concert-123?serviceId=ticket-service-a
GET /api/v1/admin/concerts/concert-123/users?serviceId=ticket-service-a
X-API-Key: <관리자 키>
```

공연 상세에는 시도 수와 시작 이력이 있는 고유 사용자 수가 있습니다. 이벤트 행 수와 시도 수는 다릅니다.
사용자 목록의 anomalyScore는 공연별 평가가 아니라 사용자 전체의 최신 평가입니다.
기존 Demo 등록·이벤트 수집·정책 API 계약은 변경하지 않습니다.

## BTS DB 적용

기존 DB의 개발/운영 담당자가 적용 전에 `SHOW TABLES LIKE 'concert'`를 확인합니다.
이미 존재하면 `SHOW CREATE TABLE concert`와 제공 SQL을 비교하고 중복 실행하지 않습니다.
공연 테이블이 없을 때만 승인된 DB 클라이언트에서 다음 파일을 1회 수동 실행합니다.

```sql
SOURCE /absolute/path/to/db/manual/038-create-concert.sql;
```

SQL 클라이언트가 실행되는 시스템에서 접근 가능한 경로를 사용합니다.
적용 후 `SHOW CREATE TABLE concert`로 복합 UNIQUE·문자열 비교·UTC DATETIME 컬럼을 확인합니다.
DDL은 일반 데이터 트랜잭션처럼 롤백되지 않습니다. 기존 DB에 전체 schema.sql을 재실행하지 않습니다.
이번 구현 과정에서는 개발 DB에 SQL을 적용하지 않습니다.

## 남은 범위

- 공연 삭제/취소 상태, 동기화 실패 재시도·주기·운영 모니터링은 외부 연동 정책으로 별도 구현해야 합니다.
- 공연별 AI 평가 모델이나 임의의 이상 사용자 판정 통계는 제공하지 않습니다.
- 대규모 이벤트에서 JSON 조건 집계 실행 계획과 필요 인덱스는 실제 데이터 규모에서 확인해야 합니다.
