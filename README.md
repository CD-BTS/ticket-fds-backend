# 🎫 Ticket FDS Backend (`ticket-fds-backend`)

> **암표 거래 및 매크로 이상 행위 탐지 시스템 (Fraud Detection System)**  
> 티켓 예매 과정에서 발생하는 사용자 행동 로그 및 예매 트래픽을 실시간/배치 수집하고 분석하여 비정상 예매 및 암표상을 탐지·필터링하는 백엔드 서버입니다.

---

## 🛠 Tech Stack & Requirements

프로젝트 실행을 위해 로컬 환경에 아래 도구들이 설치되어 있어야 합니다.

| 분류 | 기술 / 버전 | 비고 |
| :--- | :--- | :--- |
| **Language** | **Java 17 (JDK 17)** | Corretto 17 또는 Temurin 17 권장 |
| **Framework** | **Spring Boot 3.x** | Gradle - Groovy |
| **Database** | **MySQL 8.0** | Docker 컨테이너 기반 구동 |
| **ORM** | Spring Data JPA / Hibernate | |
| **Container** | **Docker & Docker Compose** | Windows의 경우 Docker Desktop + WSL2 필수 |

---

## 🚀 Getting Started (초기 개발 환경 세팅)

새로운 팀원은 아래 단계를 순서대로 진행하여 로컬 개발 환경을 구성합니다.

### 1. Repository Clone
```bash
git clone [https://github.com/CD-BTS/ticket-fds-backend.git](https://github.com/CD-BTS/ticket-fds-backend.git)
cd ticket-fds-backend
