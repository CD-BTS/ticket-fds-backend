# ERD 및 JPA 매핑

기준은 [db/schema.sql](../backend/db/schema.sql)의 MySQL 8.0 DDL입니다. 스키마는 수동으로 적용하며 Flyway, Hibernate 자동 생성 및 Spring SQL 자동 초기화를 사용하지 않습니다. 기본 `ddl-auto=none`을 유지합니다.

| 테이블 | Entity / 도메인 | PK |
| --- | --- | --- |
| user_account | UserAccount / user | user_id VARCHAR(50), 호출자가 지정 |
| behavior_event | BehaviorEvent / behavior | event_id BIGINT AUTO_INCREMENT |
| user_credit | UserCredit / credit | user_id VARCHAR(50), 사용자와 공유 |
| credit_history | CreditHistory / credit | history_id BIGINT AUTO_INCREMENT |
| risk_assessment | RiskAssessment / risk | assessment_id BIGINT AUTO_INCREMENT |
| policy_decision | PolicyDecision / policy | decision_id BIGINT AUTO_INCREMENT |
| concert | Concert / concert | id BIGINT AUTO_INCREMENT; UNIQUE(service_id, concert_id) |

## 공연 기본정보 (#38)

공연은 외부 서비스가 PUT으로 등록·갱신하는 조회용 복제 정보입니다. 원본 DB에 직접 연결하지 않습니다.
concert는 service_id와 concert_id를 utf8mb4_bin으로 비교하는 복합 UNIQUE로 식별합니다.
name(200), starts_at/ends_at(UTC DATETIME 초 단위), venue(255)를 저장합니다.
이벤트와 FK를 연결하지 않으므로 미등록 공연의 이벤트도 기존 수집 계약대로 저장할 수 있습니다.
공연 등록 후 동일한 서비스·metadata.concertId의 기존 이벤트가 자동으로 조회·집계 대상이 됩니다.

기존 DB에는 `db/manual/038-create-concert.sql`만 검토 후 1회 수동 적용합니다.
전체 schema.sql은 신규 빈 DB 전용이며 기존 DB에 재실행하지 않습니다.
이 작업에서는 실제 개발 DB에 SQL을 적용하지 않습니다.
concert의 쓰기·조회는 원자적 upsert와 집계 SQL을 위해 JDBC Repository를 사용하며 Entity는 스키마 매핑 검증에 사용합니다.
기존 Credit의 booking_history/user_card DDL은 utf8mb4_unicode_ci를 명시하므로 신규 빈 DB의 기본 collation도
동일해야 사용자 FK가 호환됩니다. 격리 테스트 서버에만 이 기본값을 지정하며 기존 DB 설정은 변경하지 않습니다.

각 Entity의 `repository` 패키지에 기본 `JpaRepository`를 제공합니다. dashboard는 별도 테이블과 Entity가 없습니다.

## 관계

- `user_credit.user_id → user_account.user_id`: 단방향 1:1, `@MapsId`로 PK 공유
- `behavior_event.user_id → user_account.user_id`
- `credit_history.user_id → user_account.user_id`
- `credit_history.event_id → behavior_event.event_id`: NULL 허용
- `risk_assessment.user_id → user_account.user_id`
- `policy_decision.user_id → user_account.user_id`
- `policy_decision.assessment_id → risk_assessment.assessment_id`: NULL 허용

관계는 자식에서 부모로 향하는 단방향 LAZY 관계이며 cascade 및 orphan removal을 지정하지 않습니다. FK 삭제·갱신 정책은 SQL의 MySQL 기본 동작을 따릅니다.

## 타입과 기본값

- 길이, NULL, PK, 복합 UNIQUE 및 18개 인덱스 정의를 SQL에 맞춰 매핑합니다. FK 지원에 필요한 추가 인덱스는 MySQL에서 생성될 수 있습니다.
- 상태, 등급, 이벤트 종류 및 대응 행동은 VARCHAR 문자열로 유지합니다. 새로운 enum, 점수 범위 CHECK 또는 서비스 정책을 추가하지 않습니다.
- `account_status=ACTIVE`, `credit_score=70`, `tier=BASE`는 SQL 기본값과 동일한 Entity 초기값을 사용합니다.
- DATETIME 기본값은 DB의 `now()`를 사용합니다. Hibernate `@Generated`로 INSERT 후 생성값을 읽습니다.
- `user_credit.updated_at`은 INSERT 기본값만 있습니다. 자동 갱신 정책이나 `ON UPDATE`를 추가하지 않습니다.
- JSON `behavior_event.metadata`는 `@JdbcTypeCode(SqlTypes.JSON)`와 `Map<String, Object>`로 매핑합니다. NULL을 허용합니다.

## 적용과 검증

기존 DB에서 테이블과 FK를 먼저 확인한 뒤 적용 여부를 결정합니다. 이미 테이블이 있다면 SQL 전체를 재실행하거나 삭제하지 않고 정의 차이를 검토합니다. MySQL DDL은 일반 데이터 트랜잭션처럼 롤백되지 않으므로 적용 승인이 필요합니다.

`EntitySchemaMappingTests`는 DB 없이 SQL과 Hibernate 메타데이터의 컬럼 타입·NULL·PK·DEFAULT·인덱스·FK를 비교합니다.

`JpaRepositoryMySqlTests`는 실제 MySQL에서 테스트 전용 `ddl-auto=validate`로 매핑을 검사합니다. 이 옵션은 생성·변경을 수행하지 않습니다. 이어서 6개 Repository의 저장·조회, 공유 PK, DB 생성 시각, JSON 및 선택적 FK를 검증합니다.

저장 테스트는 Spring 테스트 트랜잭션에서 자동 롤백됩니다. 기존 행을 수정하지 않으며 테스트 데이터의 ID는 임의로 생성합니다. AUTO_INCREMENT 번호는 롤백해도 소비될 수 있습니다. 테이블이 없거나 DB에 접속할 수 없으면 실패로 보고합니다.

```powershell
.\gradlew.bat build
# DB_URL, DB_USERNAME, DB_PASSWORD를 설정한 뒤 실행
.\gradlew.bat mysqlTest --rerun-tasks
```
