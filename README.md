# 🎫 Ticket FDS Admin (`ticket-fds-admin`)

> **암표 거래 및 매크로 이상 행위 탐지 시스템 (Fraud Detection System)**
> BTS 백엔드 서버와 관리자 대시보드를 한 저장소에서 관리합니다.

## 구성

| 경로 | 내용 | 실행 안내 |
| --- | --- | --- |
| `backend/` | Spring Boot 4.1.1 / Java 17 / MySQL 8.0 백엔드 서버, DB 스키마, 부하 테스트, 스크립트 | [backend/README.md](backend/README.md) |
| `frontend/` | React + TypeScript + Vite 관리자 대시보드 | [frontend/README.md](frontend/README.md) |
| `docs/` | API 명세, ERD, 협업 컨벤션, 부하 테스트 보고서 | [docs/api-spec.md](docs/api-spec.md) |

## 빠른 실행

백엔드 (기본 `http://localhost:8080`). DB·API Key 환경변수 설정은 [backend/README.md](backend/README.md)를 따릅니다.

```powershell
cd backend
docker compose up -d mysql
.\gradlew.bat bootRun
```

프론트엔드 (기본 `http://localhost:5173`). `/api` 요청은 백엔드로 프록시됩니다.

```bash
cd frontend
npm install
cp .env.example .env      # Windows PowerShell: Copy-Item .env.example .env
npm run dev
```

## 협업 규칙

브랜치·커밋·PR 규칙은 [docs/convention.md](docs/convention.md)를 따릅니다.
