# BTS 협업 컨벤션

## 1. Git Convention

### 1.1 Commit Convention

**형식:** `type: 작업 내용 (#이슈번호)`

| Type       | 설명                |
| ---------- | ----------------- |
| `init`     | 프로젝트 초기 설정        |
| `feat`     | 새로운 기능 추가         |
| `fix`      | 버그 수정             |
| `refactor` | 코드 리팩토링           |
| `docs`     | 문서 추가 및 수정        |
| `test`     | 테스트 코드 및 실험       |
| `chore`    | 빌드, 환경 설정 등 기타 작업 |
| `style`    | 코드 포맷팅 및 스타일 수정   |

**예시**

```text
init: Spring Boot 프로젝트 초기 설정 (#2)
feat: 사용자 등록 API 구현 (#3)
feat: Credit 점수 산정 로직 구현 (#4)
test: Isolation Forest 성능 비교 (#5)
docs: API 명세서 작성 (#6)
```

**규칙**

- 커밋 타입은 영문 소문자, 작업 내용은 한국어로 작성한다.
- 커밋 메시지 끝에 관련 Issue 번호를 작성한다.
- 하나의 커밋에는 하나의 논리적 변경 사항만 포함한다.

### 1.2 Branch Convention

**브랜치 구조:** `main` / `dev` / 작업 브랜치

| 브랜치    | 역할              |
| ------ | --------------- |
| `main` | 최종 제출 및 안정화된 코드 |
| `dev`  | 개발 기능 통합 및 테스트  |
| 작업 브랜치 | 개별 기능 개발 및 수정   |

**규칙**

- 모든 작업 브랜치는 `dev`에서 생성한다.
- `main`, `dev`에 직접 push하지 않는다.
- 작업 완료 후 `dev`로 Pull Request를 생성한다.
- 최종 통합 및 검증이 완료되면 `dev`를 `main`에 병합한다.
- 병합된 작업 브랜치는 삭제한다.

**브랜치 네이밍 형식:** `type/#이슈번호/기능명`

| Type       | 예시                          |
| ---------- | --------------------------- |
| `init`     | `init/#2/spring-setup`      |
| `feat`     | `feat/#3/user-api`          |
| `fix`      | `fix/#4/credit-error`       |
| `refactor` | `refactor/#5/event-service` |
| `docs`     | `docs/#1/github-convention` |
| `test`     | `test/#6/isolation-forest`  |
| `chore`    | `chore/#7/db-config`        |

- 브랜치 생성 전 GitHub Issue를 등록한다.
- 기능명은 영문 소문자와 하이픈(`-`)을 사용한다.
- 하나의 브랜치는 원칙적으로 하나의 Issue에 대응한다.

## 2. Issue Convention

**제목 형식:** `[type] 작업 내용`

**예시**

```text
[feat] 사용자 등록 API 구현
[feat] Credit 점수 산정 로직 구현
[test] Isolation Forest 성능 비교
[docs] GitHub 협업 컨벤션 작성
```

**본문 형식**

```markdown
## 작업 개요
작업 목적 및 설명

## 작업 내용
- [ ] 작업 항목 1
- [ ] 작업 항목 2

## 완료 조건
- 완료 기준 작성
```

**규칙**

- 작업 시작 전 Issue를 생성한다.
- 작업 단위별로 Issue를 구분한다.
- 담당자를 지정한다.
- 관련 PR을 Issue에 연결한다.

## 3. Pull Request Convention

**제목 형식:** `[type] 작업 내용 (#이슈번호)`

**예시**

```text
[feat] 사용자 등록 API 구현 (#3)
[test] Isolation Forest 성능 비교 (#5)
[docs] GitHub 협업 컨벤션 작성 (#1)
```

**본문 형식**

```markdown
## 관련 Issue
Refs #이슈번호

## 작업 내용
- 주요 변경 사항

## 테스트
- [ ] 로컬 실행 확인
- [ ] 관련 기능 테스트

## 참고 사항
- 리뷰 시 확인할 내용
```

**규칙**

1. 작업 브랜치에서 `dev`로 PR을 생성한다.
2. PR에는 관련 Issue를 연결한다.
3. 최소 1명의 팀원에게 리뷰를 요청한다.
4. 리뷰 승인 후 병합한다.
5. 병합 후 작업 브랜치를 삭제한다.
6. `dev`에 병합된 Issue는 작업 완료 여부를 확인한 후 닫는다.

## 4. Coding Convention

### 4.1 Java / Spring Boot

| 항목  | 규칙               | 예시                        |
| --- | ---------------- | ------------------------- |
| 클래스 | PascalCase       | `CreditService`           |
| 메서드 | camelCase        | `updateCreditScore()`     |
| 변수  | camelCase        | `creditScore`             |
| 상수  | UPPER_SNAKE_CASE | `DEFAULT_CREDIT_SCORE`    |
| 패키지 | 소문자              | `user`, `event`, `credit` |

**규칙**

- 도메인별 패키지 구조를 사용한다.
- Controller, Service, Repository의 역할을 분리한다.
- Controller에는 비즈니스 로직을 작성하지 않는다.
- Service에서 비즈니스 로직을 처리한다.
- DB 접근은 Repository 계층을 통해 수행한다.
- API 요청과 응답에는 DTO를 사용한다.
- 공통 API 응답 형식과 예외 처리 규칙을 준수한다.

### 4.2 Python / AI

| 항목  | 규칙               | 예시                          |
| --- | ---------------- | --------------------------- |
| 클래스 | PascalCase       | `AnomalyDetector`           |
| 함수  | snake_case       | `calculate_anomaly_score()` |
| 변수  | snake_case       | `booking_count`             |
| 상수  | UPPER_SNAKE_CASE | `MODEL_VERSION`             |
| 파일  | snake_case       | `train_model.py`            |

**규칙**

- 모델 학습 코드와 추론 코드를 분리한다.
- 학습 및 추론 과정에 동일한 Feature와 전처리 규칙을 적용한다.
- Feature 이름과 입력 순서를 일관되게 유지한다.
- 모델 파라미터, 버전 및 실험 결과를 기록한다.

### 4.3 공통 규칙

- 변수와 함수는 의미가 명확하도록 작성한다.
- 불필요한 중복 코드와 사용하지 않는 코드는 제거한다.
- 복잡한 로직에는 필요한 주석을 작성한다.
- `.env`, API Key, DB 비밀번호 등 민감한 정보는 커밋하지 않는다.
- API, DB, AI 입력 형식 변경 시 관련 문서를 함께 수정한다.
- 공통 인터페이스 변경 전 팀원들과 협의한다.

## 5. Documentation Convention

**문서 관리 위치:** `docs/`

| 파일                   | 내용                 |
| -------------------- | ------------------ |
| `README.md`          | 프로젝트 소개 및 실행 방법    |
| `docs/convention.md` | GitHub 및 코드 컨벤션    |
| `docs/api-spec.md`   | API 명세             |
| `docs/erd.md`        | 데이터베이스 설계          |
| `docs/ai-design.md`  | AI Feature 및 모델 설계 |

**규칙**

- 문서는 Markdown(`.md`) 형식으로 작성한다.
- 문서 수정도 Issue 및 PR을 통해 진행한다.
- 설계 및 구현 변경 사항은 관련 문서에 반영한다.

## 6. Collaboration Rules

- 작업 시작 전 최신 `dev` 브랜치를 반영한다.
- 동일한 파일을 여러 명이 수정하는 경우 사전에 공유한다.
- 공유 브랜치에 임의로 force push하지 않는다.
- Merge Conflict 발생 시 변경 사항을 확인한 후 해결한다.
- 다른 팀원의 코드에 영향을 주는 변경은 PR에서 공유하고 리뷰받는다.