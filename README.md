# 🎫 Ticket FDS Backend (`ticket-fds-backend`)

> **암표 거래 및 매크로 이상 행위 탐지 시스템 (Fraud Detection System)**
> 티켓 예매 과정에서 발생하는 사용자 행동 로그 및 예매 트래픽을 실시간/배치 수집하고 분석하여 비정상 예매 및 암표상을 탐지·필터링하는 백엔드 서버입니다.

---

## 🛠 Tech Stack & Requirements

프로젝트 실행을 위해 로컬 환경에 아래 도구들이 설치되어 있어야 합니다.

| 분류 | 기술 / 버전 | 비고 |
| :--- | :--- | :--- |
| **Language** | **Java 17 (JDK 17)** | Corretto 17 또는 Temurin 17 권장, Gradle toolchain에 필요 |
| **Framework** | **Spring Boot 4.1.1** | Gradle - Groovy |
| **Build** | **Gradle Wrapper 9.7.1** | 별도의 Gradle 설치 불필요 |
| **Database** | **MySQL 8.0** | Docker 컨테이너 기반 구동 |
| **ORM** | Spring Data JPA / Hibernate | 테이블 생성 정책은 추후 결정 |
| **Validation** | Jakarta Bean Validation | `spring-boot-starter-validation` |
| **Container** | **Docker & Docker Compose** | Windows에서 Docker Desktop의 Linux 컨테이너 엔진 실행 필요 |

기본 패키지는 `com.bts.fairticket`이며 코딩·협업 규칙은 [docs/convention.md](docs/convention.md)를 따릅니다.

---

## 🚀 Getting Started (초기 개발 환경 세팅)

새로운 팀원은 아래 단계를 순서대로 진행하여 로컬 개발 환경을 구성합니다.

### 1. Repository Clone

```bash
git clone https://github.com/CD-BTS/ticket-fds-backend.git
cd ticket-fds-backend
```

JDK 17을 설치하고 IDE의 Gradle toolchain 또는 `JAVA_HOME`을 설정합니다. Gradle을 Java 21로 실행하더라도 컴파일·테스트에는 선언된 JDK 17 toolchain이 별도로 필요합니다. JDK가 검색되지 않으면 로컬 Gradle 설정의 `org.gradle.java.installations.paths`에 설치 경로를 지정합니다. Wrapper 및 의존성의 최초 다운로드에는 인터넷 연결이 필요합니다.

### 2. MySQL 환경변수 설정

`.env`가 없을 때만 `.env.example`을 복사하고 비밀번호를 로컬에서 채웁니다. 기존 `.env`가 있으면 그대로 사용하며 덮어쓰지 않습니다. `.env`는 Git 추적에서 제외됩니다. 기본 예제 계정은 기존 Docker 설정과 같은 `user`입니다.

```powershell
if (-not (Test-Path -LiteralPath .env)) {
    Copy-Item .env.example .env
}
```

| 환경변수 | 용도 | 필수 여부 |
| --- | --- | --- |
| `DB_URL` | Spring Boot의 JDBC URL | 애플리케이션 / `mysqlTest` 필수 |
| `DB_USERNAME` | Spring Boot 접속 사용자, Compose의 `MYSQL_USER` | 필수 |
| `DB_PASSWORD` | Spring Boot 접속 비밀번호, Compose의 `MYSQL_PASSWORD` | 필수 |
| `MYSQL_ROOT_PASSWORD` | Compose의 MySQL root 비밀번호 | Compose 필수 |
| `JPA_SHOW_SQL` | Hibernate SQL 출력 여부 | 선택, 기본 `false` |

예제 JDBC URL은 로컬 개발용입니다. 원격 DB에는 해당 환경에 맞는 호스트·TLS 옵션을 설정합니다. 비밀번호와 API Key는 소스 코드나 공유 문서에 작성하지 않습니다.

### 3. MySQL 실행

기존 Docker 구성은 MySQL 8.0, DB `fairticket_db`, 계정 `user`, 호스트 포트 `3306`입니다. 이미 실행 중인 컨테이너는 그대로 사용합니다. 실행이 필요한 경우 Docker Desktop 또는 Docker 데몬을 실행한 뒤 기존 프로젝트 루트에서 아래 명령을 사용합니다.

```powershell
docker compose up -d mysql
docker compose ps mysql
docker compose logs --tail 50 mysql
```

기존 서비스 이름, 컨테이너 이름(`fairticket-mysql`), 포트(`3306`), `mysql_data` 볼륨을 유지합니다. 기존 볼륨을 삭제·초기화하거나 `docker compose down -v`를 실행하지 않습니다.

**이미 초기화된 볼륨에서는 `.env`의 초기화 변수를 변경해도 기존 DB 계정·비밀번호가 변경되지 않습니다.** 해당 볼륨의 기존 사용자와 비밀번호를 `.env` 및 애플리케이션 환경변수에 설정합니다. 계정 변경이 필요하면 데이터 보존을 전제로 DB 관리자와 별도로 조정합니다.

기존 로컬 MySQL을 사용하는 경우 Compose 실행은 생략하고 `DB_URL`을 해당 서버와 DB로 지정합니다. 3306 포트를 다른 MySQL이 사용 중이라면 컨테이너를 추가 실행하기 전에 확인합니다.

### 4. Spring Boot 실행 환경변수 설정

Docker Compose는 `.env`를 읽지만 **Spring Boot와 Gradle은 `.env`를 자동으로 읽지 않습니다.** 실행 터미널의 환경변수 또는 IDE의 Run Configuration에 동일한 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`를 설정합니다.

기존 Docker MySQL에 접속하는 PowerShell 예시(로그인에 성공한 기존 `user` 비밀번호 입력):

```powershell
$env:DB_URL = 'jdbc:mysql://localhost:3306/fairticket_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Seoul&characterEncoding=UTF-8'
$env:DB_USERNAME = 'user'
$env:DB_PASSWORD = [System.Net.NetworkCredential]::new('', (Read-Host 'DB password' -AsSecureString)).Password
.\gradlew.bat bootRun
```

기본 HTTP 포트는 `8080`입니다. ERD의 6개 테이블에 대응하는 Entity와 Repository를 제공하며 사용자 등록·조회 API는 [docs/api-spec.md](docs/api-spec.md)를 참고하세요.

JPA는 `ddl-auto=none`으로 테이블을 생성·변경·검증하지 않습니다. `open-in-view=false`이며 SQL 출력은 기본 비활성화입니다. 테이블은 `db/schema.sql`을 승인 후 수동 적용합니다. DB 접속 정보가 빠지거나 접속할 수 없으면 애플리케이션 시작에 실패할 수 있습니다.

## 빌드 및 테스트

DB가 없어도 실행 가능한 단위 테스트와 MVC 테스트:

```powershell
.\gradlew.bat test
.\gradlew.bat build
```

`test`는 JUnit `mysql` 태그를 제외합니다. 공통 응답 JSON, 요청 DTO Validation, 요청 파라미터 검증, 잘못된 JSON, HTTP 상태·응답 코드 및 내부 오류 정보 비노출을 검증합니다. MVC 테스트 Controller는 테스트 소스에만 존재합니다.

MySQL 연결 통합 테스트(위 `DB_*` 환경변수와 접근 가능한 DB 필요):

```powershell
.\gradlew.bat mysqlTest --rerun-tasks
```

`mysqlTest`는 `mysql` 태그만 실행합니다. 기존 기본 `@SpringBootTest`의 웹 애플리케이션 컨텍스트(MOCK)와 JPA 초기화 후 DataSource의 실제 연결에서 DB 제품이 MySQL인지 확인하고 `SELECT 1`을 실행합니다. 연결 확인 테스트는 데이터·테이블을 변경하지 않습니다. Repository 테스트는 기존 데이터를 수정하지 않고 테스트용 행을 저장한 뒤 롤백합니다. 연결 실패를 테스트 생략이나 성공으로 처리하지 않습니다.

테스트 보고서는 각각 `build/reports/tests/test/index.html`, `build/reports/tests/mysqlTest/index.html`에 생성됩니다.

문제 구분:

- JDK 17 미탐지, Wrapper/의존성 다운로드 실패: 빌드 실행 환경 확인
- Docker 데몬 연결 실패, 포트 충돌: Docker 및 로컬 MySQL 실행 환경 확인
- DB 환경변수 누락, `Access denied`, `Unknown database`, 연결 거절: DB URL·계정·권한·DB 생성 여부 확인
- Java 컴파일 오류 또는 공통 응답 테스트 실패: 코드 및 의존성 호환성 확인

## DB 스키마 및 Entity 검증

6개 테이블의 수동 생성 SQL은 [db/schema.sql](db/schema.sql), 매핑 설명은 [docs/erd.md](docs/erd.md)에서 관리합니다. Flyway와 자동 테이블 생성은 사용하지 않습니다.

기존 MySQL에서 먼저 테이블과 FK 상태를 확인하고, 스키마 적용 승인을 받은 뒤 진행합니다. `db/schema.sql`은 빈 스키마의 최초 생성용이며 기존 테이블이 있는 DB에는 전체를 재실행하지 않습니다. DROP, TRUNCATE 및 볼륨 초기화는 사용하지 않습니다.

적용 전 읽기 전용 확인 예시(비밀번호는 프롬프트에 입력):

```powershell
docker exec -it fairticket-mysql mysql -u user -p fairticket_db
```

MySQL 콘솔에서 다음을 실행합니다.

```sql
SHOW TABLES;
SELECT TABLE_NAME, COLUMN_NAME, REFERENCED_TABLE_NAME, REFERENCED_COLUMN_NAME
FROM information_schema.KEY_COLUMN_USAGE
WHERE TABLE_SCHEMA = 'fairticket_db' AND REFERENCED_TABLE_NAME IS NOT NULL;
```

DDL 적용은 승인 후 SQL 클라이언트에서 수행합니다. MySQL 콘솔의 `SOURCE`는 해당 콘솔이 실행되는 시스템의 파일 경로를 사용하므로 호스트의 `db/schema.sql` 경로를 Docker 내부 콘솔에서 그대로 사용할 수 없습니다.

DB 없는 빌드에는 SQL과 Hibernate 매핑 비교가 포함됩니다. `mysqlTest`에는 기존 연결 확인과 6개 Repository 저장·조회 테스트가 포함됩니다. Repository 테스트에만 `ddl-auto=validate`를 적용하여 테이블을 생성하지 않고 검사합니다. 테스트 데이터는 트랜잭션 롤백되지만 AUTO_INCREMENT 번호는 소비될 수 있습니다.

```powershell
.\gradlew.bat build
# 위에서 DB_* 환경변수를 설정한 터미널에서 실행
.\gradlew.bat mysqlTest --rerun-tasks
```

## 사용자 API 인증 설정

사용자 API에는 서비스별 `X-API-Key` 인증이 필요합니다. `BTS_API_KEY_HASHES` 환경변수에 서비스 ID와 발급 키의 SHA-256 해시를 매핑한 JSON 객체를 지정합니다. 기본값 `{}`에서는 모든 사용자 API 요청이 거부됩니다. 운영용 키 원문은 코드·Git·로그에 기록하지 않습니다.

기존 `.env`를 덮어쓰지 않습니다. Spring Boot는 `.env`를 자동으로 읽지 않으므로 환경변수를 실행 터미널이나 IDE에 별도로 설정합니다. 키 해시를 생성하는 PowerShell 예시:

```powershell
$clientApiKey = [System.Net.NetworkCredential]::new('', (Read-Host '서비스에 발급한 API Key' -AsSecureString)).Password
$sha256 = [System.Security.Cryptography.SHA256]::Create()
try {
    $keyHash = [BitConverter]::ToString($sha256.ComputeHash([Text.Encoding]::UTF8.GetBytes($clientApiKey))).Replace('-', '').ToLowerInvariant()
} finally {
    $sha256.Dispose()
}
$env:BTS_API_KEY_HASHES = @{ 'ticket-service-a' = $keyHash } | ConvertTo-Json -Compress
.\gradlew.bat bootRun
```

서비스 ID는 요청 본문의 `serviceId`와 일치해야 합니다. 다른 서비스의 사용자는 조회할 수 없습니다. 운영 요청에는 HTTPS를 사용하고 프록시에서도 인증 헤더 로깅을 비활성화합니다. 요청·응답·상태 코드는 [API 명세](docs/api-spec.md)에 정리되어 있습니다.
