CREATE TABLE `user_account` (
  `user_id` varchar(50) PRIMARY KEY COMMENT 'BTS 내부 사용자 ID',
  `service_id` varchar(50) NOT NULL COMMENT '외부 티켓팅 서비스 식별자',
  `external_user_id` varchar(100) NOT NULL COMMENT '외부 서비스의 사용자 ID',
  `created_at` datetime NOT NULL DEFAULT (now()),
  `account_status` varchar(20) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE, SUSPENDED'
);

CREATE TABLE `behavior_event` (
  `event_id` bigint PRIMARY KEY AUTO_INCREMENT,
  `user_id` varchar(50) NOT NULL,
  `service_id` varchar(50) NOT NULL COMMENT '이벤트가 발생한 외부 서비스',
  `external_event_id` varchar(100) NOT NULL COMMENT '외부 서비스의 이벤트 고유 ID',
  `event_type` varchar(30) NOT NULL COMMENT 'BOOKING, CANCEL, CHECK_IN',
  `booking_id` varchar(100) COMMENT '외부 예매 ID. BOOKING/CANCEL 시 필수',
  `ticket_id` varchar(100) COMMENT '외부 티켓 ID. CHECK_IN 시 필수',
  `occurred_at` datetime NOT NULL COMMENT '외부 서비스에서 실제 행동이 발생한 시각',
  `metadata` json COMMENT '추가 행동 정보. bookingDurationSeconds, deviceChanged 등 확장 가능'
);

CREATE TABLE `user_credit` (
  `user_id` varchar(50) PRIMARY KEY,
  `credit_score` int NOT NULL DEFAULT 70 COMMENT '관람 신용 점수 0~100, 초기값 70',
  `tier` varchar(20) NOT NULL DEFAULT 'BASE' COMMENT '관람 신용 등급. 구체적인 등급 기준은 추후 확정',
  `updated_at` datetime NOT NULL DEFAULT (now()) COMMENT '신용 점수 및 등급 최종 갱신 시각'
);

CREATE TABLE `credit_history` (
  `history_id` bigint PRIMARY KEY AUTO_INCREMENT,
  `user_id` varchar(50) NOT NULL,
  `event_id` bigint COMMENT '관련 BTS 내부 행동 이벤트 ID. 직접 관련 이벤트가 없으면 NULL',
  `score_before` int NOT NULL COMMENT '변경 전 신용 점수',
  `score_after` int NOT NULL COMMENT '변경 후 신용 점수',
  `reason` varchar(255) NOT NULL COMMENT '신용 점수 변경 사유',
  `created_at` datetime NOT NULL DEFAULT (now()) COMMENT '신용 점수 변경 시각'
);

CREATE TABLE `risk_assessment` (
  `assessment_id` bigint PRIMARY KEY AUTO_INCREMENT,
  `user_id` varchar(50) NOT NULL,
  `anomaly_score` double NOT NULL COMMENT '정규화된 이상도 점수 0~1. 높을수록 이상 행동 정도가 큼',
  `model_version` varchar(30) NOT NULL COMMENT '분석에 사용한 AI 모델 버전',
  `assessed_at` datetime NOT NULL DEFAULT (now()) COMMENT 'AI 분석 수행 시각'
);

CREATE TABLE `policy_decision` (
  `decision_id` bigint PRIMARY KEY AUTO_INCREMENT,
  `user_id` varchar(50) NOT NULL,
  `assessment_id` bigint COMMENT '정책 결정에 사용한 AI 분석 ID. 분석 이력이 없으면 NULL',
  `context` varchar(30) NOT NULL COMMENT '정책 요청 상황. MVP: BOOKING_ATTEMPT',
  `credit_score_at_decision` int NOT NULL COMMENT '정책 결정 당시 관람 신용 점수',
  `anomaly_score_at_decision` double COMMENT '정책 결정 당시 사용한 이상도 점수. 분석 이력이 없으면 NULL',
  `action` varchar(30) NOT NULL COMMENT 'ALLOW, REQUIRE_CAPTCHA, DELAY, RESTRICT',
  `reason` varchar(255) COMMENT '정책 결정 사유',
  `decided_at` datetime NOT NULL DEFAULT (now()) COMMENT '정책 결정 시각'
);

CREATE UNIQUE INDEX `user_account_index_0` ON `user_account` (`service_id`, `external_user_id`);

CREATE INDEX `user_account_index_1` ON `user_account` (`service_id`);

CREATE INDEX `user_account_index_2` ON `user_account` (`account_status`);

CREATE UNIQUE INDEX `behavior_event_index_3` ON `behavior_event` (`service_id`, `external_event_id`);

CREATE INDEX `behavior_event_index_4` ON `behavior_event` (`user_id`);

CREATE INDEX `behavior_event_index_5` ON `behavior_event` (`user_id`, `occurred_at`);

CREATE INDEX `behavior_event_index_6` ON `behavior_event` (`user_id`, `event_type`, `occurred_at`);

CREATE INDEX `behavior_event_index_7` ON `behavior_event` (`service_id`, `booking_id`);

CREATE INDEX `user_credit_index_8` ON `user_credit` (`tier`);

CREATE INDEX `credit_history_index_9` ON `credit_history` (`user_id`, `created_at`);

CREATE INDEX `credit_history_index_10` ON `credit_history` (`event_id`);

CREATE INDEX `risk_assessment_index_11` ON `risk_assessment` (`user_id`, `assessed_at`);

CREATE INDEX `risk_assessment_index_12` ON `risk_assessment` (`assessed_at`);

CREATE INDEX `policy_decision_index_13` ON `policy_decision` (`user_id`, `decided_at`);

CREATE INDEX `policy_decision_index_14` ON `policy_decision` (`user_id`, `action`, `decided_at`);

CREATE INDEX `policy_decision_index_15` ON `policy_decision` (`user_id`, `context`, `decided_at`);

CREATE INDEX `policy_decision_index_16` ON `policy_decision` (`assessment_id`);

CREATE INDEX `policy_decision_index_17` ON `policy_decision` (`decided_at`, `action`);

ALTER TABLE `user_account` COMMENT = '외부 티켓팅 서비스별 사용자 식별 정보. 사용자 등록 시 BTS 내부 user_id 생성';

ALTER TABLE `behavior_event` COMMENT = '외부 서비스에서 수집한 예매, 취소, 체크인 이벤트. 동일 외부 이벤트 중복 저장 방지';

ALTER TABLE `user_credit` COMMENT = '사용자별 현재 관람 신용 상태. 사용자 등록 시 함께 생성';

ALTER TABLE `credit_history` COMMENT = '관람 신용 점수 변경 이력. 점수 변경 시 기록';

ALTER TABLE `risk_assessment` COMMENT = 'Isolation Forest 기반 사용자 행동 분석 결과. 분석 시점 이전 30일의 행동 이벤트로 Feature 계산';

ALTER TABLE `policy_decision` COMMENT = '신용 점수와 최신 AI 이상도 점수를 바탕으로 결정한 대응 정책 및 당시 판단 근거';

ALTER TABLE `user_credit` ADD FOREIGN KEY (`user_id`) REFERENCES `user_account` (`user_id`);

ALTER TABLE `behavior_event` ADD FOREIGN KEY (`user_id`) REFERENCES `user_account` (`user_id`);

ALTER TABLE `credit_history` ADD FOREIGN KEY (`user_id`) REFERENCES `user_account` (`user_id`);

ALTER TABLE `credit_history` ADD FOREIGN KEY (`event_id`) REFERENCES `behavior_event` (`event_id`);

ALTER TABLE `risk_assessment` ADD FOREIGN KEY (`user_id`) REFERENCES `user_account` (`user_id`);

ALTER TABLE `policy_decision` ADD FOREIGN KEY (`user_id`) REFERENCES `user_account` (`user_id`);

ALTER TABLE `policy_decision` ADD FOREIGN KEY (`assessment_id`) REFERENCES `risk_assessment` (`assessment_id`);
