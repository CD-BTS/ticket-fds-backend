# AI CSV로 사용자·행동 이벤트 검증

Python 3.10 이상과 표준 라이브러리만 사용합니다. 아래 명령은 `backend/`에서 실행합니다.
스크립트는 기본적으로 dry-run이며, 실제 전송에는 `--execute --confirm-test-environment`가 필요합니다.
이번 작업에서는 대량 전송을 실행하지 않았습니다.

## CSV 준비와 확인된 원본

기본 경로는 스크립트 위치 기준 `../../ai/data/behavior_event.csv`입니다.
CSV를 backend로 복사하지 않습니다. 현재 AI 레포의 `main.py` 안 `generate_data()`가 이벤트 원본을 생성합니다.
이미 파일이 있으면 생성 코드를 다시 실행하지 마세요. `main.py` 실행은 이벤트·ticket·label·feature CSV를 덮어쓸 수 있습니다.
파일이 없을 때만 AI 담당자와 생성 여부를 확인한 뒤 AI 가상환경에서 의존성을 준비하고 `ai/` 작업 디렉터리에서 `python main.py`를 실행합니다.

확인된 컬럼은 `event_id, external_event_id, user_id, event_type, booking_id, ticket_id, performance_id, occurred_at, metadata`입니다.
현재 원본은 사용자 1,000명, 이벤트 20,272건(BOOKING 10,665 / CANCEL 3,549 / CHECK_IN 6,058)입니다.
원본 UTC 범위는 `2026-09-10T12:18:12+00:00` ~ `2026-10-16T04:10:41+00:00`입니다.
이 수치는 원본이 재생성되면 달라질 수 있으므로 실행 때 출력되는 SHA-256과 건수를 함께 기록하세요.

## 백엔드와 MySQL 준비

- JDK 17, Docker/MySQL, 기존 `db/schema.sql`에 맞는 테이블이 준비되어야 합니다. 기존 DB를 초기화하거나 스키마 SQL을 재실행하지 않습니다.
- `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`를 Spring Boot 실행 환경에 설정합니다. 필요하면 `docker compose up -d mysql`로 기존 로컬 컨테이너를 시작합니다.
- 서버의 `BTS_API_KEY_HASHES`에는 serviceId와 발급 API Key의 SHA-256 해시를 설정합니다. 실제 키와 해시는 Git에 저장하지 않습니다.
- `localhost`의 백엔드도 원격 DB를 사용할 수 있습니다. **서버 프로세스의 DB_URL을 직접 확인**하여 운영 DB가 아닌 테스트 환경인지 확인합니다.
- 기존 KST 이벤트의 UTC 마이그레이션 문제는 `docs/api-spec.md`의 UTC 정책을 확인합니다. 스크립트는 기존 데이터를 수정·삭제하지 않습니다.

PowerShell에서 환경변수를 설정한 뒤 별도 터미널에서 백엔드를 실행합니다:

```powershell
# DB_* 및 BTS_API_KEY_HASHES는 실제 로컬 설정을 환경변수로 주입
.\gradlew.bat bootRun
```

클라이언트의 `BTS_API_KEY` 원문은 서버에 설정한 해당 서비스 해시와 일치해야 합니다.
기존 테스트 전용 서비스 하나를 사용하고 CSV 재전송에도 같은 서비스·키를 유지합니다.

```powershell
$env:BTS_SERVICE_ID = 'dummy-ai-issue12'
$env:BTS_API_BASE_URL = 'http://localhost:8080'
# 발급받은 키를 화면·명령 이력에 남기지 않고 입력
$keyInput = Read-Host '테스트 서비스 API Key' -AsSecureString
$env:BTS_API_KEY = [System.Net.NetworkCredential]::new('', $keyInput).Password
```

해당 키를 서버에 등록할 때는 **새 터미널의 로컬 테스트 환경**에서만 다음처럼 해시 설정을 만듭니다.
이 예시는 해당 프로세스의 서비스 설정을 한 개로 구성하므로 기존 실행 환경의 다른 서비스 설정을 덮어쓰지 않도록 구분합니다.

```powershell
$sha256 = [System.Security.Cryptography.SHA256]::Create()
$hashBytes = $sha256.ComputeHash([System.Text.Encoding]::UTF8.GetBytes($env:BTS_API_KEY))
$keyHash = ([System.BitConverter]::ToString($hashBytes)).Replace('-', '').ToLowerInvariant()
$sha256.Dispose()
$serviceHashes = @{}
$serviceHashes[$env:BTS_SERVICE_ID] = $keyHash
$env:BTS_API_KEY_HASHES = ConvertTo-Json -InputObject $serviceHashes -Compress
# DB_* 설정 확인 후 이 환경에서 서버 실행. 기존 서버에는 설정 후 재시작 필요
.\gradlew.bat bootRun
```

## Dry-run

키나 실행 중인 서버 없이 CSV 검증이 가능합니다. serviceId는 필요합니다.

```powershell
python -B scripts/import_dummy_events.py --dry-run --service-id dummy-ai-issue12
python -B scripts/import_dummy_events.py --dry-run --service-id dummy-ai-issue12 --limit 10
python -B scripts/import_dummy_events.py --dry-run --csv ../ai/data/behavior_event.csv --service-id dummy-ai-issue12 --as-of 2026-10-11T00:00:00Z
```

처리 대상·유효 행 수, 사용자 수, 유형별 건수, metadata 키·타입, UTC 범위, 최근 30일·기간 이전·미래 이벤트 수, 동일 키 중복 및 검증 오류를 출력합니다.
`--limit N`은 헤더를 제외한 CSV 순서의 앞 N개 이벤트만 검증·전송하며 해당 이벤트에 필요한 사용자만 등록합니다. 날짜순으로 정렬하지 않습니다.
기본값은 제한 없음이며 N은 양의 정수여야 합니다. CSV가 N개보다 짧으면 존재하는 이벤트만 처리합니다.
제한한 범위 내 오류는 전송을 중단하고, 범위 밖 행은 검증하지 않습니다. 잘못된 행을 건너뛰어 뒤의 행으로 N개를 채우지 않습니다.
보고의 `limit`, `rows`, `users` 및 유형별·날짜별 건수는 선택 범위를 기준으로 하며 SHA-256은 원본 파일 전체 기준입니다.
검증 오류는 CSV 물리 행 번호(헤더가 1행, 여러 줄 셀은 종료 행 번호)와 원인을 출력합니다.
CSV 헤더가 확인된 원본과 다르면 전송을 중단합니다. 변환·검증 오류가 한 건이라도 있으면 실제 전송도 시작하지 않습니다.
최근 30일 보고 기준은 UTC `[as_of - 30일, as_of]`이며 기본 as_of는 실행 시각입니다. `--as-of`는 보고에만 영향을 주고 전송 날짜를 변경하지 않습니다.
확인된 원본을 `2026-10-11T00:00:00Z` 기준으로 분석하면 기간 내 19,843건, 이전 387건, 미래 42건입니다.
기간 내 유형별 건수는 BOOKING 10,356 / CANCEL 3,471 / CHECK_IN 6,016입니다.

## 실제 전송과 재전송

아래 명령은 **실제로 DB에 데이터를 저장**합니다. 사용자 확인 후 대상 backend와 DB 설정을 확인한 상태에서 실행합니다.

```powershell
python -B scripts/import_dummy_events.py --csv ../ai/data/behavior_event.csv --execute --confirm-test-environment
# 같은 파일·서비스·옵션으로 한 번 더 실행하면 기존 사용자와 이벤트는 200이어야 함
python -B scripts/import_dummy_events.py --csv ../ai/data/behavior_event.csv --execute --confirm-test-environment
# 사용 후 클라이언트 키 제거
Remove-Item Env:BTS_API_KEY
```

전체 전송 전에 앞 10개 이벤트만 검증하려면 키 설정 상태에서 다음 명령을 사용합니다. 같은 명령 재실행 시 멱등성 및 결과 집계 정책은 동일합니다.

```powershell
python -B scripts/import_dummy_events.py --limit 10 --dry-run
python -B scripts/import_dummy_events.py --limit 10 --execute --confirm-test-environment
```

CSV 경로는 `--csv` / `BTS_DUMMY_CSV`, URL은 `--base-url` / `BTS_API_BASE_URL`, 키는 `--api-key` / `BTS_API_KEY`, 서비스는 `--service-id` / `BTS_SERVICE_ID`로 설정합니다. 인자가 환경변수보다 우선합니다.
키는 프로세스 명령줄에 노출되는 `--api-key`보다 환경변수 방식을 권장합니다.
기본 URL은 `http://localhost:8080`입니다. 원격 테스트 서버에는 `--allow-remote`를 명시해야 하며 HTTP(S) 리다이렉트는 따르지 않습니다.
자동 재시도는 하지 않으며 연결 실패 이후에는 같은 CSV를 재전송하여 멱등성으로 복구할 수 있습니다.
`--timeout`은 요청별 제한 시간(초), 기본 30입니다.

사용자를 CSV `user_id`(예: U0001)로 중복 없이 등록하고 `{serviceId, externalUserId}` 요청을 보냅니다.
등록 응답의 BTS UUID는 메모리 매핑으로 보관하여 이벤트 `userId`에 사용합니다. 재실행 시 다시 등록 API로 기존 UUID를 얻습니다.
계정 상태는 생략하여 기존 기본값 ACTIVE를 사용하며 재등록 시 기존 상태·Credit은 보존됩니다.

| CSV | API |
|---|---|
| external_event_id | externalEventId |
| user_id | 등록 응답의 내부 UUID → userId |
| event_type | eventType |
| booking_id / ticket_id | bookingId / ticketId, 빈 셀만 null로 변환 |
| occurred_at | occurredAt, 원본 오프셋·문자열 유지 |
| metadata | JSON Object 파싱, 기존 키와 중첩 정보 보존 |
| performance_id | metadata.performance_id에 원본 이름과 값으로 보존 |
| event_id | 전송하지 않음, DB가 eventId 생성 |

`performance_id`가 metadata에 이미 있고 값이 다르면 오류로 중단하며 덮어쓰지 않습니다.
metadata 필드명을 camelCase로 임의 변환하지 않습니다. 원본 `booking_duration_seconds`는 추가 속성이며 서버의 `bookingDurationSeconds` 검증 대상과 다른 키입니다.
camelCase `bookingDurationSeconds`가 있으면 0 이상 숫자, `deviceChanged`가 있으면 boolean이어야 합니다.
BOOKING/CANCEL은 bookingId, CHECK_IN은 ticketId가 필수입니다. DATETIME(0)에 저장하므로 0이 아닌 소수 초는 거부합니다.

사용자·이벤트 각각 `created_201`, `replayed_200`, `conflict_409`, `failed` 건수를 출력합니다.
사용자 등록 실패에 연결된 이벤트는 `skipped_user_failure`로 구분하고 행 번호를 출력합니다.
HTTP 실패가 발생해도 다른 사용자·이벤트를 계속 처리하고, 서버의 code·message·data를 해당 행 번호와 함께 출력합니다.
성공 응답은 200/201 상태와 COMMON_200/COMMON_201 코드도 확인합니다.
종료 코드는 0=성공/dry-run, 1=전송 실패·충돌·의존 사용자 실패, 2=설정·CSV 검증 오류입니다. 부분 성공은 롤백하지 않습니다.

## DB 확인 (읽기 전용)

로컬 MySQL에서 다음 쿼리의 serviceId를 사용한 테스트 서비스로 맞춥니다:

```sql
SELECT COUNT(*) FROM user_account WHERE service_id = 'dummy-ai-issue12';
SELECT event_type, COUNT(*) FROM behavior_event
WHERE service_id = 'dummy-ai-issue12' GROUP BY event_type;
SELECT COUNT(*) AS events, COUNT(DISTINCT external_event_id) AS distinct_events
FROM behavior_event WHERE service_id = 'dummy-ai-issue12';
SELECT u.external_user_id, e.external_event_id, e.event_type, e.occurred_at, e.metadata
FROM behavior_event e JOIN user_account u ON e.user_id = u.user_id
WHERE e.service_id = 'dummy-ai-issue12' ORDER BY e.event_id LIMIT 10;
```

새 테스트 서비스에 원본 전체 전송이 성공하면 사용자 1,000명과 이벤트 20,272건입니다.
동일 CSV 재전송 후 건수가 증가하지 않아야 하며 occurred_at은 UTC 값이어야 합니다.
CSV 전체를 실제로 import한 데이터는 자동 삭제하지 않습니다. 기존 API의 사용자·이벤트 목록 GET은 아직 미구현이므로 위 쿼리로 확인합니다.

## 실제 전송 검증 결과 (2026-10-11)

동일 원본 CSV(사용자 1,000명 / 이벤트 20,272건)를 `dummy-ai-issue12` 서비스로 전송한 사용자의 실행 결과입니다.

| 실행 | 사용자 | 이벤트 |
|---|---|---|
| 앞 10건 최초 전송 | 201: 1명 | 201: 10건 |
| 동일 10건 재전송 | 200: 1명 | 200: 10건 |
| 전체 CSV 전송 | 201: 999명 / 200: 1명 | 201: 20,262건 / 200: 10건 |

모든 실행에서 실패 및 409 충돌은 0건입니다.
이후 읽기 전용 MySQL 조회에서 해당 서비스의 사용자 1,000명과 이벤트 20,272건을 확인했습니다.
유형별 저장 건수는 BOOKING 10,665 / CANCEL 3,549 / CHECK_IN 6,058입니다.
DB 전체는 기존 `ticket-service-a` 데이터(사용자 2명 / 이벤트 1건)를 포함하여 `user_account` 1,002건, `behavior_event` 20,273건입니다.
최종 검증 과정에서는 CSV를 재전송하거나 이미 적재한 데이터를 삭제·초기화하지 않습니다.

## Feature 집계 및 팀 협의

현재 CSV에는 오래된 이벤트뿐 아니라 미래 CHECK_IN도 있습니다. 스크립트는 어느 행도 날짜 때문에 삭제하지 않습니다.
원본 전체 재전송과 최근 30일 Feature 분석 결과는 구분해야 합니다. 집계는 분석 기준 시각·미래 이벤트 제외·경계 포함 정책을 AI/Feature 담당자와 맞춰야 합니다.
원본 `booking_open_at`, `booking_started_at`, `booking_completed_at`도 UTC-offset string이며 `booking_duration_seconds`는 integer입니다. 이를 읽는 집계 키 이름을 팀과 확인해야 합니다.

이번 스크립트는 날짜 이동을 지원하지 않습니다. 필요하면 별도 테스트 옵션으로 모든 occurred_at에 동일한 timedelta를 더해 상대 간격을 보존하는 방법을 고려합니다.
그 경우 metadata 내부 세 시간과 ticket.csv의 공연 종료 시각도 일관되게 이동해야 하며 원본 파일을 덮어쓰면 안 됩니다.
서로 다른 이동 옵션으로 같은 externalEventId를 재전송하면 409 DATA_CONFLICT가 발생하므로 별도 테스트 serviceId 또는 확정된 동일 이동량을 사용해야 합니다.
이벤트 범위가 30일보다 길면 일정한 이동만으로 전체를 30일 범위에 넣을 수 없습니다.
Feature 집계·Assessment·FastAPI 호출은 이 스크립트에 구현하지 않습니다.

## 자동화 테스트

```powershell
python -B -m unittest discover -s scripts -p 'test_*.py' -v
.\gradlew.bat build
# DB_*가 기존 테스트 DB로 설정된 환경에서 실행
.\gradlew.bat mysqlTest
git diff --check
```

Python 테스트는 임시 CSV와 가짜 API 응답만 사용하여 실제 서버·DB를 호출하지 않습니다.
MySQL 테스트는 실행별 임의 서비스 ID 격리와 기존 @AfterEach 정리를 유지합니다. 원본 CSV import와 자동화 테스트 데이터는 별개입니다.
401은 API Key 누락·유효하지 않은 키, 403은 서비스 불일치, 404는 내부 사용자 미존재, 400은 입력 오류, 409 DATA_CONFLICT는 같은 키의 내용 충돌입니다.
CSV 원본 재생성이나 metadata 매핑 변경 후 기존 서비스에 다시 전송하면 409가 발생할 수 있습니다.
