package com.bts.fairticket.credit.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.bts.fairticket.credit.policy.CreditCalculator.CalculationInput;
import com.bts.fairticket.credit.policy.CreditCalculator.CalculationResult;
import com.bts.fairticket.credit.policy.CreditCalculator.CreditGrade;

class CreditCalculatorTest {

    private final CreditCalculator calculator = new CreditCalculator();

    @Test
    @DisplayName("[시나리오 1] 신규 정상 사용자: 관람 이력 없음, 감점 0 -> 50점 (NORMAL)")
    void calculate_NewUser() {
        CalculationInput input = new CalculationInput(0, 0, 0, 0.0, 0.0);

        CalculationResult result = calculator.calculate(input);

        assertThat(result.baseCredit()).isEqualTo(50.0);
        assertThat(result.dynamicPenalty()).isEqualTo(0.0);
        assertThat(result.finalCreditScore()).isEqualTo(50);
        assertThat(result.grade()).isEqualTo(CreditGrade.NORMAL);
    }

    @Test
    @DisplayName("[시나리오 2] 빠른 정상 사용자: 베이스 50점, 룰 감점 5점 -> 45점 (NORMAL)")
    void calculate_FastNormalUser() {
        // AI 감점 0, 간격 분산 룰 감점 5점 가정
        CalculationInput input = new CalculationInput(0, 0, 0, 0.0, 5.0);

        CalculationResult result = calculator.calculate(input);

        assertThat(result.baseCredit()).isEqualTo(50.0);
        assertThat(result.dynamicPenalty()).isEqualTo(5.0);
        assertThat(result.finalCreditScore()).isEqualTo(45);
        assertThat(result.grade()).isEqualTo(CreditGrade.NORMAL);
    }

    @Test
    @DisplayName("[시나리오 3] 이상 행동 의심 사용자: AI 이상도 0.5 (감점 20점) -> 30점 (SUSPICIOUS)")
    void calculate_SuspiciousUser() {
        // AI 이상도 0.5 * 40 = 20점 감점
        CalculationInput input = new CalculationInput(0, 0, 0, 0.5, 0.0);

        CalculationResult result = calculator.calculate(input);

        assertThat(result.baseCredit()).isEqualTo(50.0);
        assertThat(result.dynamicPenalty()).isEqualTo(20.0);
        assertThat(result.finalCreditScore()).isEqualTo(30);
        assertThat(result.grade()).isEqualTo(CreditGrade.SUSPICIOUS);
        assertThat(result.penaltyPolicy()).contains("CAPTCHA 추가 인증");
    }

    @Test
    @DisplayName("[시나리오 4] 다계정 매크로 의심 사용자: AI 0.75(30점) + 카드/IP 룰(15점) 감점 45점 -> 5점 (HIGH_RISK)")
    void calculate_MacroHighRiskUser() {
        CalculationInput input = new CalculationInput(0, 0, 0, 0.75, 15.0);

        CalculationResult result = calculator.calculate(input);

        assertThat(result.baseCredit()).isEqualTo(50.0);
        assertThat(result.dynamicPenalty()).isEqualTo(45.0);
        assertThat(result.finalCreditScore()).isEqualTo(5);
        assertThat(result.grade()).isEqualTo(CreditGrade.HIGH_RISK);
        assertThat(result.penaltyPolicy()).contains("후순위 배치");
    }

    @Test
    @DisplayName("[시나리오 5] 관람 이력이 우수한 사용자: 관람 완료 10회, 노쇼 0회, 미입금 0회 -> Base 약 75점")
    void calculate_GoodHistoryUser() {
        // A = 10 -> BaseCredit = 100 * (10 + 5) / (10 + 10) = 1500 / 20 = 75.0
        CalculationInput input = new CalculationInput(10, 0, 0, 0.0, 5.0);

        CalculationResult result = calculator.calculate(input);

        assertThat(result.baseCredit()).isCloseTo(75.0, offset(0.01));
        assertThat(result.dynamicPenalty()).isEqualTo(5.0);
        assertThat(result.finalCreditScore()).isEqualTo(70);
        assertThat(result.grade()).isEqualTo(CreditGrade.NORMAL);
    }

    @Test
    @DisplayName("[경계값] 감점이 BaseCredit보다 큰 경우 음수가 되지 않고 0점으로 클램핑된다")
    void calculate_ClampedToZero() {
        CalculationInput input = new CalculationInput(0, 5, 2, 1.0, 30.0);

        CalculationResult result = calculator.calculate(input);

        assertThat(result.finalCreditScore()).isZero();
        assertThat(result.grade()).isEqualTo(CreditGrade.HIGH_RISK);
    }
}