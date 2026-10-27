# Event API 부하 테스트 (#18)

기존 API와 DB 스키마를 변경하지 않고 `POST /api/v1/events`의 신규 이벤트 적재를 비교한다.
**로컬 테스트 DB에 실제 데이터가 계속 쌓인다. 운영 API·운영 DB에서는 실행하지 않는다.**
로컬 API도 원격 DB를 사용할 수 있으므로 서버 프로세스의 `DB_URL`을 확인한다.

## 확인한 기존 구현

기준은 현재 브랜치의 `EventRequest`, `EventController`, `EventRegistrationTransaction`,
`ApiKeyFilter`, `ApiKeyAuthenticator`, `ApiResponse`, `db/schema.sql`이다.

| 필드 | 제약 |
|---|---|
| serviceId | 필수, 최대 50자, 인증 키의 서비스와 일치 |
| externalEventId | 필수, 최대 100자 |
| userId | 필수, 최대 50자, 해당 서비스에 등록된 내부 사용자 ID |
| eventType | 필수, BOOKING / CANCEL / CHECK_IN |
| bookingId | 최대 100자, BOOKING / CANCEL 시 필수 |
| ticketId | 최대 100자, CHECK_IN 시 필수 |
| occurredAt | 필수, UTC 오프셋 포함 초 단위 ISO 8601, UTC 기준 연도 1000~9999. 예: `2026-10-26T01:00:00Z`. UTC로 저장 |
| metadata | 선택 JSON 객체, bookingDurationSeconds가 있으면 0 이상 숫자, deviceChanged가 있으면 boolean |

인증 헤더는 `X-API-Key` 하나이며 JSON 전송에는 `Content-Type: application/json`을 사용한다.
서버는 원문 키의 SHA-256을 `BTS_API_KEY_HASHES`의 서비스별 해시와 비교한다.
신규 생성은 HTTP 201, `success: true`, `code: COMMON_201`, `data.eventId` 등을 반환한다.
`(service_id, external_event_id)`에 UNIQUE 인덱스가 있으며 기존 이벤트 조회와 UNIQUE 충돌 후 재조회로
동시 중복 삽입을 처리한다. 동일 내용은 200/COMMON_200, 다른 내용은 409/DATA_CONFLICT다.

현재 작업 트리에는 `scripts/import_dummy_events.py`와 `scripts/README.md`가 있다.
CSV 컬럼은 `event_id, external_event_id, user_id, event_type, booking_id, ticket_id, performance_id, occurred_at, metadata`다.
사용자를 먼저 등록해 CSV user_id를 내부 userId로 매핑하고, performance_id는 metadata에 보존하며 event_id는 보내지 않는다.
Python 스크립트는 오프셋 포함 날짜를 보내며 현재 DTO도 오프셋을 요구한다.
이번 k6는 CSV를 재전송하지 않고 현재 DTO에 맞는 BOOKING 요청을 생성한다. Python 코드·원본 CSV는 수정하지 않는다.

## Windows PowerShell 준비

명령은 모두 `backend/`에서 실행한다. JDK 17, Docker Desktop 또는 MySQL 8.0이 필요하다.
[k6 공식 설치 안내](https://grafana.com/docs/k6/latest/set-up/install-k6/)에 따른 설치:

```powershell
winget install k6 --source winget
# 설치 후 새 PowerShell 창에서 확인
k6 version
```

1. `.env`가 없다면 `.env.example`을 복사해 DB_USERNAME, DB_PASSWORD, MYSQL_ROOT_PASSWORD를 로컬 값으로 채운다.
   기존 `.env`는 덮어쓰지 않는다. `docker compose up -d mysql`로 MySQL을 시작한다.
2. **새 빈 테스트 DB에만** 기존 `db/schema.sql`을 MySQL 클라이언트에서 적용한다.
   기존 테이블이 있으면 재실행하지 않는다. Hibernate는 `ddl-auto=none`이므로 테이블을 생성하지 않는다.

```powershell
if (-not (Test-Path .env)) { Copy-Item .env.example .env }
# .env를 편집한 다음 실행
docker compose up -d mysql
docker compose exec mysql mysql -u root -p fairticket_db
```

빈 DB의 스키마를 컨테이너에서 적용하려면 별도 PowerShell에서 파일을 복사한 후 MySQL 프롬프트에서 읽는다:

```powershell
docker compose cp ./db/schema.sql mysql:/tmp/fairticket-schema.sql
```

```sql
SOURCE /tmp/fairticket-schema.sql;
```

서버용 PowerShell에서 다음을 설정한다. Spring Boot는 `.env`를 자동으로 읽지 않으므로
DB 사용자·비밀번호는 `.env`에 입력한 값과 동일하게 별도 설정해야 한다.

```powershell
$env:DB_URL = 'jdbc:mysql://localhost:3306/fairticket_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Seoul&characterEncoding=UTF-8'
$env:DB_USERNAME = Read-Host '로컬 DB 사용자'
$env:DB_PASSWORD = [System.Net.NetworkCredential]::new('', (Read-Host '로컬 DB 비밀번호' -AsSecureString)).Password
$env:SERVICE_ID = 'load-test-issue18'
$env:API_KEY = [System.Net.NetworkCredential]::new('', (Read-Host '로컬 테스트 전용 API Key' -AsSecureString)).Password
$sha256 = [System.Security.Cryptography.SHA256]::Create()
try {
    $keyHash = [BitConverter]::ToString($sha256.ComputeHash([Text.Encoding]::UTF8.GetBytes($env:API_KEY))).Replace('-', '').ToLowerInvariant()
} finally {
    $sha256.Dispose()
}
$serviceHashes = @{}
$serviceHashes[$env:SERVICE_ID] = $keyHash
$env:BTS_API_KEY_HASHES = $serviceHashes | ConvertTo-Json -Compress
.\gradlew.bat bootRun
```

위 해시 설정은 해당 서버 프로세스에 테스트 서비스 하나를 등록한다. 실제 키·DB 비밀번호는 Git이나 결과 문서에 기록하지 않는다.

## 사용자 준비와 실행

별도 PowerShell에서 서버와 같은 서비스·키를 설정하고 사용자 100명을 등록한다.
이 과정은 부하 측정 밖에서 실행되며 사용자와 초기 신용 데이터도 로컬 DB에 저장된다.
같은 externalUserId의 재등록은 기존 내부 ID를 반환하므로 반복 준비가 가능하다.
이미 준비한 같은 서비스의 내부 userId 목록이 있으면 등록 루프 대신 USER_IDS에 쉼표로 연결해 넣어도 된다.

```powershell
$env:API_BASE_URL = 'http://localhost:8080'
$env:SERVICE_ID = 'load-test-issue18'
$env:API_KEY = [System.Net.NetworkCredential]::new('', (Read-Host '서버와 같은 테스트 API Key' -AsSecureString)).Password
$headers = @{ 'X-API-Key' = $env:API_KEY }
$userIds = 1..100 | ForEach-Object {
    $body = @{ serviceId = $env:SERVICE_ID; externalUserId = ('k6-user-{0:D3}' -f $_) } | ConvertTo-Json
    $result = Invoke-RestMethod -Method Post -Uri "$env:API_BASE_URL/api/v1/users" -Headers $headers -ContentType 'application/json' -Body $body -ErrorAction Stop
    if (-not $result.success -or -not $result.data.userId -or $result.data.serviceId -ne $env:SERVICE_ID) { throw '사용자 등록 응답 확인 실패' }
    $result.data.userId
}
$env:USER_IDS = $userIds -join ','
$env:RUN_ID = [guid]::NewGuid().ToString()
$env:DURATION_SECONDS = '60'
Write-Output "RUN_ID=$env:RUN_ID"
```

이 시점에 `load-tests/verify-events.sql`의 **Section 1만** 실행한다.
SQL의 서비스·실행 UUID를 위 값과 맞추고 해당 MySQL 세션을 유지한다. 트랜잭션 없이 실행한다.
그다음 PowerShell에서:

```powershell
k6 run ./load-tests/event-load-test.js
# 즉시 종료 코드를 기록한다. threshold 실패 시 0이 아니다.
$k6ExitCode = $LASTEXITCODE
Write-Output "k6 exit code=$k6ExitCode"
```

10 → 50 → 100 VU 각각 기본 60초 동안 쉬지 않고 요청한다. 각 VU는 응답 후 다음 요청을 보내는 방식이며 고정 RPS 테스트가 아니다.
구간 사이에는 진행 중 요청을 기다리는 최대 10초와 추가 5초 간격이 있어 부하가 겹치지 않는다.
요청 timeout은 10초이며 자동 재시도·리다이렉트를 사용하지 않는다. 전체 기본 실행 시간은 약 3분 30~40초다.
DURATION_SECONDS로 모든 구간의 지속 시간을 동일하게 조절할 수 있다(1~3600초).
API_BASE_URL은 localhost, 127.0.0.1 또는 [::1]의 경로 없는 HTTP(S) origin만 허용한다. API_KEY는 원문 키다.
로컬 API가 원격 DB를 가리키지 않는지 서버 DB_URL도 반드시 확인한다.

externalEventId는 `k6-<RUN_ID>-vus_<VU수>-<전체 VU ID>-<시나리오 iteration ID>`이며 최대 100자 이내다.
매 실행마다 RUN_ID를 새 UUID로 설정한다. 병렬 실행에서도 서로 다른 UUID를 사용한다.
신규 적재 성능을 보기 때문에 200 재전송도 실패로 집계한다. 이 스크립트는 중복 요청 자체의 멱등성 경쟁 테스트는 수행하지 않는다.
100명 미만 사용자를 지정하면 여러 VU가 같은 사용자를 공유하므로 결과 해석 시 사용자 수를 기록한다.

## 결과와 DB 대조

콘솔과 `load-tests/results-<유형>-<RUN_ID>.md`에 구간별 요청 수, 생성 확인 수, 요청 RPS, 생성/s,
성공률, 평균/p95, 오류율, HTTP 실패율을 출력한다. 원본 집계는 같은 이름의 `.json`에 저장한다.
유형은 `load`, `stress-200/500/1000`, `spike`다. 결과 파일은 Git에서 제외한다.
매 프로세스마다 새 RUN_ID를 사용해야 기존 결과와 DB 이벤트가 겹치지 않는다.
응답 시간은 `http_req_duration`(송신·대기·수신, 연결 수립 시간 제외)이다.
성공률은 HTTP 201 + 응답 봉투 + 생성 ID + 서비스/이벤트/사용자/유형 일치를 검사한 비율이고 오류율은 그 보수다.
HTTP 실패율은 201 이외의 상태 및 네트워크 실패를 집계하므로 JSON 검증 실패까지 포함한 오류율과 다를 수 있다.
구간별 성공률 99% 이상, 오류율 및 HTTP 실패율 1% 이하, 요청/생성 건수 양수를 threshold로 둔다.
합의된 응답 시간 SLA가 없으므로 지연 시간은 비교용으로 기록하며 `p(95)>=0`은 구간별 집계를 표시하기 위한 조건이다.

완료 후 같은 MySQL 세션에서 **Section 2만** 실행한다. 다른 세션이면 전후 변수는 유지되지 않으므로
미리 적어 둔 전후 값을 수동 대조한다. 실행별 건수만 다시 조회하려면 Section 1의 서비스/UUID/prefix SET 세 문장을 다시 실행한다.

- 다른 이벤트 쓰기가 없으면 전체/서비스 증가분, 실행별 저장 수, k6 생성 확인 수가 같아야 한다.
- 구간별 stored_events를 결과의 생성 확인 수와 비교한다. 중복 쿼리는 0행이어야 한다.
- 다른 프로세스가 DB를 쓰면 전체 증가분보다 RUN_ID 범위의 저장 수를 기준으로 비교한다.
- timeout 후 서버에서 커밋되거나 응답 검증이 실패하면 DB 적재 수가 생성 확인 수보다 클 수 있다.
  요청 수·생성 확인 수·적재 수와 서버 로그를 함께 조사한다. 불일치를 곧바로 데이터 유실로 단정하지 않는다.
- threshold 통과와 DB 정합성 확인은 각각 기록한다. 데이터 자동 삭제·DB 초기화는 수행하지 않는다.

결과 문서에 다음 내용을 추가한다:

```markdown
실행 일시 / 브랜치·커밋:
RUN_ID / SERVICE_ID / 사용자 수:
OS / CPU / 메모리 / JDK / k6 / MySQL 버전:
서버·DB 실행 위치 / DB 풀·서버 설정 / 다른 작업 부하:
구간 지속 시간 / k6 종료 코드:
DB 전체 전후 / 서비스 전후 / 실행별·구간별 저장 수:
중복 검사 행 수:
threshold 판정 / DB 대조 판정:
오류 상태 코드·서버 로그 요약 / 병목 관찰:
```

사용 후 클라이언트 터미널의 키를 제거한다:

```powershell
Remove-Item Env:API_KEY
```

기존 #18 테스트 코드 최초 작성 시에는 k6가 설치되어 있지 않아 실제 부하 테스트 및 DB 전후 대조를 수행하지 않았다.
Node.js로 JavaScript 구문을 검사하고, k6 모듈을 대체한 모의 실행에서 구간 설정,
301개 요청 ID의 고유성, 요청 형식, 비 JSON 오류 집계, 결과 문서 PASS/FAIL 출력을 확인했다.
이는 실제 k6 런타임 및 MySQL 적재 검증을 대체하지 않는다.
측정값은 로컬 실행 후 기록해야 하며 성능 수치를 미리 가정하지 않는다.
시나리오·집계 설정 참고: [k6 옵션 공식 문서](https://grafana.com/docs/k6/latest/using-k6/k6-options/reference/).

## 과거 실제 부하 테스트 결과 (#18)

아래 61,099건은 당시 구현과 날짜 정책에서 측정한 과거 기록이며 현재 브랜치 및 확장 테스트의 실행 결과가 아니다.

### 테스트 정보

- SERVICE_ID: `load-test-issue18`
- RUN_ID: `ff3426d2-3b53-47aa-b3f2-f76933ec6744`
- 테스트 대상: `POST /api/v1/events`
- 테스트 사용자: 100명
- 시나리오: 10 / 50 / 100 VU, 각 60초
- k6 종료 코드: `0`
- Threshold 판정: **PASS**

### k6 측정 결과

| VU | 요청 수 | 생성 확인 수 | 성공률 | 평균(ms) | p95(ms) | 오류율 | HTTP 실패율 |
|---:|---:|---:|---:|---:|---:|---:|---:|
| 10 | 21,310 | 21,310 | 100.00% | 27.68 | 38.42 | 0.00% | 0.00% |
| 50 | 20,305 | 20,305 | 100.00% | 147.24 | 206.21 | 0.00% | 0.00% |
| 100 | 19,484 | 19,484 | 100.00% | 308.18 | 396.51 | 0.00% | 0.00% |
| **합계** | **61,099** | **61,099** | **100.00%** | — | — | **0.00%** | **0.00%** |

### MySQL 적재 검증

| 검증 항목 | 결과 |
|---|---:|
| 테스트 전 전체 이벤트 | 20,273 |
| 테스트 후 전체 이벤트 | 81,372 |
| 전체 증가량 | 61,099 |
| 서비스 증가량 | 61,099 |
| 이번 RUN_ID 적재 건수 | 61,099 |
| 서로 다른 external_event_id 수 | 61,099 |
| 중복 검사 | 0행 |

구간별 DB 저장 건수:

- 10 VU: 21,310건
- 50 VU: 20,305건
- 100 VU: 19,484건

### 결론

10/50/100 VU의 모든 테스트 구간에서 API 요청 성공률 100%, 오류율 0%를 기록하여 설정된 k6 threshold를 통과하였다.

k6 생성 확인 수 61,099건과 MySQL 실제 적재 건수 61,099건이 정확히 일치했으며, 중복 이벤트는 발견되지 않았다.

동시 사용자 수가 증가할수록 평균 및 p95 응답 시간이 증가하는 경향이 관찰되었으나, 이번 테스트 범위에서는 요청 실패 및 DB 적재 불일치가 발생하지 않았다.

**최종 판정: k6 Threshold PASS / DB 정합성 PASS**

※ 본 테스트는 고유한 이벤트 ID를 사용하는 신규 이벤트 적재 부하 테스트이며, 동일 이벤트 ID의 동시 중복 전송에 대한 별도 멱등성 경쟁 테스트는 포함하지 않는다.

## 확장 Stress / Spike 테스트 (#21): 최종 상태

실제 측정 결과, 완료 조건 대조, Git/민감정보 검토는
[이슈 #21 최종 보고서](issue21-final-load-test-report.md)를 기준으로 한다.
10/50/100 VU는 위 #18의 과거 60초 결과이며, #21과 동일 조건의 재실행이 아니다.

### 재사용 코드와 실행 조건

- `event-common.js`: 기존 API 인증, UTC 시각, 사용자 배정, 고유 이벤트 ID, 응답 identity 검증 및 집계.
- `event-load-test.js`: 기존 10/50/100 VU 시나리오. `DURATION_SECONDS` 기본 60초.
- `event-stress-test.js`: 한 프로세스에서 한 단계. `STRESS_VUS` 허용값 200/300/400/500/1000, 기본 200.
- `run-event-stress.ps1`: 200/500/1000 일반 ladder. 실패 시 상위 단계 중지, 진행 시 NEXT 확인. 300/400은 개별 실행 대상이다.
- `event-spike-test.js`: 하나의 `ramping-vus` 시나리오, 시작 50 VU, 최대 500 VU.

| Spike 구간 | 1차: peak 10초 | 2차: peak 20초 |
|---|---|---|
| baseline: 50 VU | 0~10초 | 0~10초 |
| surge: 50→500 VU | 10~11초 | 10~11초 |
| peak: 500 VU | 11~21초 | 11~31초 |
| decline: 500→50 VU | 21~22초 | 31~32초 |
| recovery: 50 VU | 22~42초 | 32~52초 |

Spike는 `SPIKE_PEAK_SECONDS=10`(기본) 또는 `20`을 사용한다.
`SPIKE_REPORT_TYPE=spike`(기본) 또는 `spike2`로 결과 이름을 구분한다.
Spike 일정은 위 고정 프로파일이며 `DURATION_SECONDS`로 바뀌지 않는다.
감소 구간을 1초로 유지하기 위해 `gracefulRampDown=0s`, 최종 종료는 `gracefulStop=10s`다.
취소된 요청은 서버에서 저장될 수 있으므로 HTTP/네트워크 실패 및 클라이언트 확인 성공과 따로 DB 대조한다.

### 결과 해석과 보호 조건

구간 태그는 요청 **시작 시점**을 기준으로 한다. 시작 RPS는 시도 수/설정 구간 초,
확인 성공/s는 응답 검증 성공 수/설정 구간 초다. 완료 응답의 지연/오류는 시작 구간에 귀속한다.
k6 전체 실행 시간 기반 RPS와 다르며, 중단 실행에서 count/예정 시간은 처리량으로 쓰지 않는다.
성공률 99% 이상·오류율 1% 이하가 응답 threshold다. `p(95)>=0`은 구간 집계를 노출하기 위한 조건이며 지연 SLA가 아니다.

호스트 안전 감시는 로컬 PowerShell 실행기에서 수행했다. `k6 run event-spike-test.js` 자체는
호스트 RAM 또는 서버 GET을 감시하지 않으므로 동일 보호 기능이 있다고 오해하지 않는다.
보호 조건은 가용 물리 RAM 64 MiB 미만, Java 종료, 인증 GET 실패/10초 무응답이다.
정상 상태에서도 CPU가 100%라는 이유만으로 중단하지는 않았다.

대시보드는 `K6_WEB_DASHBOARD=true`, `K6_WEB_DASHBOARD_OPEN=false`,
`K6_WEB_DASHBOARD_PORT=-1`로 HTML 내보내기만 사용해 브라우저 연결에 따른 종료 지연을 피했다.
Spike 집계 주기는 1초다. 로컬 인증 파일, 사용자 ID 목록, 원본 로그와 결과는 최종 보고서의 Git 분류를 따른다.

### 완료 결과의 범위

200/300/400/500 VU Stress와 Spike 1·2차는 완료됐다. 1,000 VU는 13.42초에 메모리 보호로 중단됐다.
MySQL 직접 CPU·메모리 및 200/300/400 DB 전후 증빙은 미확인이다. 자세한 평가와 종료 권고는 최종 보고서에 기록했다.
이 문서 정리 작업에서는 추가 테스트를 실행하지 않았다.
