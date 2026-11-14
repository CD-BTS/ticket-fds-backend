# BTS Admin Dashboard (Frontend)

최신 `ticket-fds-backend-feat-29-booking-attempt` 백엔드와 `대시보드 화면.pdf`를 기준으로 새로 구성한 React + TypeScript + Vite 관리자 대시보드입니다.

## 실행 방법

```bash
npm install
cp .env.example .env
npm run dev
```

Windows PowerShell에서는 환경 파일을 복사할 때 다음 명령을 사용할 수 있습니다.

```powershell
Copy-Item .env.example .env
npm install
npm run dev
```

기본 Vite 주소는 `http://localhost:5173`이며 `/api` 요청은 `http://localhost:8080`으로 프록시됩니다. 백엔드 주소가 다르면 `.env`의 `VITE_API_PROXY_TARGET`을 수정하고 Vite를 재시작합니다.

## 인증 방식

- 관리자 API Key: `BTS_ADMIN_API_KEYS`에 등록된 관리자 키. `/api/v1/dashboard/summary`, `/api/v1/admin/users` 호출에 사용합니다.
- 서비스 API Key: 해당 서비스의 `BTS_API_KEY_HASHES`에 등록된 서비스 키. 사용자 상세·이벤트 이력·예매 시도 API 호출에 사용합니다.
- 두 키는 브라우저 저장소(localStorage/sessionStorage)에 저장하지 않고 React 메모리 상태로만 유지합니다. 새로고침하면 다시 입력해야 합니다.

백엔드 현재 구현은 관리자 세션 발급 방식이 아니라 각 요청에 `X-API-Key`를 요구합니다. 프론트도 실제 백엔드 계약에 맞춰 요청별로 키를 구분합니다.

## 실제 연동 API

| API | 인증 | 화면 |
|---|---|---|
| `GET /api/v1/dashboard/summary?serviceId=...` | 관리자 키 | 서비스 통계 및 연결 검증 |
| `GET /api/v1/admin/users` | 관리자 키 | 전체 사용자 목록 |
| `GET /api/v1/users/{userId}` | 서비스 키 | 사용자 상세 |
| `GET /api/v1/users/{userId}/events` | 서비스 키 | 사용자 이벤트 이력 |
| `GET /api/v1/events/booking-attempts` | 서비스 키 | 예매 시도 및 공연 ID 탐색 |

## 백엔드 미구현 항목과 화면 처리

현재 백엔드 코드에서 확인한 API 계약을 넘어서는 데이터를 임의 생성하지 않습니다.

1. **공연 목록**: 전용 공연 목록 API가 없습니다. 서비스 키로 예매 시도 API를 호출해 최근 최대 100건의 `concertId`를 추출합니다. 공연명·공연일·예매 상태는 표시할 데이터가 없습니다.
2. **공연 상세 통계**: 예매 시도 API에서 선택 공연의 고유 예매 시도 수와 최근 조회 결과를 표시합니다. 공연별 이상 탐지 수, 제재 수, 평균 Credit은 API가 없어 `—`로 표시합니다.
3. **시계열 그래프**: 최근 조회된 최대 100건의 `startedAt`을 10초 단위로 묶은 참고용 그래프입니다. 전체 기간의 집계 그래프나 이상 탐지 추이가 아닙니다.
4. **공연별 사용자**: 공연 ID로 사용자 목록을 필터링하는 관리자 API가 없습니다. 예매 시도 결과에서 확인된 고유 사용자와 마지막 진행 단계만 표시합니다.
5. **전체 사용자 목록**: 관리자 사용자 목록 API를 사용합니다. 목록 DTO에는 anomalyScore가 없어 해당 칸은 `미제공`으로 표시합니다. Credit 정렬, tier, userId, Credit 범위 필터는 서버 API를 사용합니다.
6. **사용자 상세**: 기존 사용자 상세 API는 서비스 키 인증입니다. 사용자 상세 DTO에서 anomalyScore와 latestAction을 표시하고, 이벤트 이력 API로 예매·취소·체크인 기록을 표시합니다.
7. **Feature 값**: `FeatureAggregationService` 및 `UserCalculatedFeatures` DTO는 존재하지만, 사용자별 Feature를 반환하는 조회 API는 확인되지 않았습니다. 따라서 값은 표시하지 않고 미제공 상태를 안내합니다.

## 백엔드 담당자와 추가 협의할 항목

- 관리자 인증을 booking-attempt 조회까지 허용하거나 관리자 전용 조회 API 제공
- 공연 목록(공연명, 공연일, 상태, concertId) API
- 공연별 사용자 목록 API 및 예매자 범위(예매 성공/시도 참여자) 정의
- 공연별 통계 및 10초 단위 시계열 API
- 이상 탐지 건수/고유 사용자 수, 제재 적용 건수 집계 기준
- 관리자 권한의 사용자 상세·이벤트 이력 조회 API
- 사용자별 Feature 계산값 조회 API

## 참고

백엔드에서 관리자 API와 서비스 API는 서로 다른 키를 사용합니다. 서비스 키를 관리자 API에 보내거나 관리자 키를 일반 사용자/이벤트 API에 보내면 403이 발생하는 것이 정상입니다.
