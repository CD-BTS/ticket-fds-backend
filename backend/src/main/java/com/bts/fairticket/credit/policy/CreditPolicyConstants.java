package com.bts.fairticket.credit.policy;

public final class CreditPolicyConstants {

    private CreditPolicyConstants() {}

    // 1. BaseCredit 가중치 및 파라미터
    public static final double WEIGHT_COMPLETED = 1.0;       // A (정상 관람)
    public static final double WEIGHT_NO_SHOW = 2.0;         // N (노쇼)
    public static final double WEIGHT_AUTO_CANCEL = 1.5;     // C_auto (미입금 자동 취소)
    public static final double BAYESIAN_ALPHA = 5.0;         // alpha
    public static final double BAYESIAN_TOTAL_SMOOTHING = 10.0; // alpha + beta (5 + 5)

    // 2. DynamicPenalty 가중치 및 상한
    public static final double WEIGHT_ANOMALY_SCORE = 40.0;  // IF 최대 감점
    public static final double MAX_RULE_PENALTY = 30.0;      // 룰 기반 최대 감점
    public static final double MAX_TOTAL_PENALTY = 70.0;     // 동적 감점 최대치

    // 3. 룰 기반 감점 수치
    public static final double PENALTY_CARD_SHARING_HIGH = 15.0; // 4계정 이상 공유
    public static final double PENALTY_CARD_SHARING_MID = 5.0;   // 2~3계정 공유
    public static final double PENALTY_IP_BURST = 5.0;           // 동일 IP 집중도
    public static final double PENALTY_LOW_INTERVAL_VARIANCE = 5.0; // 요청 간격 분산 미달

    // 4. 등급 판정 임계값 (Credit Thresholds)
    public static final int THRESHOLD_NORMAL = 40;     // 40점 이상: NORMAL
    public static final int THRESHOLD_SUSPICIOUS = 20; // 20~39점: SUSPICIOUS, 0~19점: HIGH_RISK
}