# BTS Admin Dashboard (Frontend)

최신 `ticket-fds-backend`(dev, #38 공연·관리자 조회 API 포함)의 관리자 API에 연결되는 React + TypeScript + Vite 관리자 대시보드입니다.

## 실행 방법

```bash
npm install
cp .env.example .env      # Windows PowerShell: Copy-Item .env.example .env
npm run dev
```

기본 주소는 `http://localhost:5173`이며 `/api` 요청은 `http://localhost:8080`으로 프록시됩니다(백엔드에 CORS 설정이 없음). 주소가 다르면 `.env`의 `VITE_API_PROXY_TARGET`을 수정하고 Vite를 재시작합니다.

## 인증

상단에 **서비스 ID**와 **관리자 API Key** 하나만 입력합니다. 관리자 키는 백엔드 `BTS_ADMIN_API_KEYS`에 해시로 등록한 키이며, 모든 요청에 `X-API-Key`로 전달됩니다. 키는 저장소(localStorage/sessionStorage)에 저장하지 않고 메모리에만 두므로 새로고침하면 다시 입력합니다. 서비스 키로는 관리자 API를 호출할 수 없습니다(403).

## 연동 API (모두 관리자 키)

| API | 화면 |
|---|---|
| `GET /api/v1/admin/summary?serviceId=` | 연결 확인, 등급 목록, 상단 요약 |
| `GET /api/v1/admin/concerts?serviceId=` | 공연 목록 (정렬·페이지네이션) |
| `GET /api/v1/admin/concerts/{concertId}?serviceId=` | 공연 상세 (예매 시도 수, 시도 사용자 수) |
| `GET /api/v1/admin/concerts/{concertId}/users?serviceId=` | 공연별 사용자 (등급 필터, Credit 정렬) |
| `GET /api/v1/admin/users` | 전체 사용자 목록 (ID·등급·상태·Credit 범위 필터, Credit 정렬) |
| `GET /api/v1/admin/users/{userId}` | 사용자 상세 + 최근 정책 결정 |
| `GET /api/v1/admin/users/{userId}/assessments` | 위험 평가 이력 |
| `GET /api/v1/admin/users/{userId}/events` | 예매·취소·체크인·예매 시도 이력 |

공연 데이터는 외부 서비스가 `PUT /api/v1/concerts/{concertId}`로 등록해야 보입니다. 기존 DB라면 `backend/db/manual/038-create-concert.sql`을 한 번 적용해야 합니다.

## 아직 백엔드에 없어 표시하지 못하는 항목

임의의 값을 만들어 보여주지 않고 "—" 또는 "미제공"으로 표시합니다.

- 공연별 이상 탐지·제재 건수, 평균 Credit
- 공연별 시계열(10초 단위 추이) 통계 — 그래프 영역은 안내만 표시
- 사용자별 AI Feature 계산값
- 이상 점수 기준 검색·정렬 (목록 API 정렬은 `createdAt`, `userId`, `creditScore`만 지원)

## 참고

- 사용자·평가 시각(`LocalDateTime`)은 서버가 타임존 없이 내려주므로 그대로, 공연·이벤트·정책 시각은 오프셋을 반영해 브라우저 시간대로 표시합니다.
- 공연 목록의 검색 입력은 현재 페이지 안에서만 걸러냅니다(서버에 검색 파라미터 없음).
- 이상 점수는 공연별 평가가 아니라 사용자 전체의 최신 평가입니다.
