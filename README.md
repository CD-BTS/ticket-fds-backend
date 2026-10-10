# 🎫 Ticket FDS Backend (`ticket-fds-backend`)

> **암표 거래 및 매크로 이상 행위 탐지 시스템 (Fraud Detection System)**
> 티켓팅 과정의 사용자 행동 이벤트 수집, Feature 집계, AI 분석 요청 및 최종 위험도 평가(Assessment)를 담당하는 백엔드 코어 서버입니다.

---

## 🛠 Tech Stack

* **Language**: Java 17
* **Framework**: Spring Boot 3.x
* **Database**: MySQL 8.0 (Docker)
* **ORM**: Spring Data JPA / Hibernate
* **Build Tool**: Gradle (Groovy)

---

## 🚀 Quick Start (Local Setup)

### 1. Docker MySQL 실행
프로젝트 루트 경로에서 MySQL 컨테이너를 구동합니다:
```bash
docker compose up -d
```
- 포트: `3306` (기본 계정: `user` / `userpassword`)
- DB명: `fairticket_db`

### 2. Spring Boot 애플리케이션 실행

- IntelliJ: `FairticketApplication.java` 실행
- CLI:Bash
```bash
./gradlew bootRun
```
- 서버 포트: 8080

## 📌 주요 모듈 및 엔드포인트

- `global`: 공통 예외 처리, 공통 응답 규격, BaseEntity
- `user`: 사용자 정보 관리 (`user_account`)
- `event`: 사용자 행동 이벤트 수집 (`behavior_event`)
    - `POST /api/v1/events`: 사용자 행동 로그 수집 (중복 수집 방지)
- `feature`: 최근 30일 행동 로그 기반 Feature 집계
- `assessment`: 위험도 평가 및 AI 연동
    - `POST /api/v1/users/{userId}/assessments`: 특정 사용자에 대한 FDS 이상 탐지 평가 요청 및 결과 저장

## 🌿 Git 브랜치 전략

- `main`: 프로덕션 안정화 브랜치
- `dev`: 개발 통합 브랜치
- `feat/{issue_no}-{기능명}`: 단위 기능 개발 브랜치 (예: `feat/2-event-collector`)
