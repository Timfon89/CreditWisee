package com.creditwise.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.creditwise.app.data.model.ScoreBreakdown;
import com.creditwise.app.data.model.StatementAnalysis;
import com.creditwise.app.domain.CreditScoreCalculator;

import org.junit.Test;

public class CreditScoreCalculatorTest {

    private StatementAnalysis weakButNoGambling() {
        StatementAnalysis a = new StatementAnalysis();
        a.monthCount = 11;
        a.avgIncome = 45_000;
        a.avgIncomeRegular = 5_000;
        a.avgExpense = 52_000;          // spending exceeds income
        a.avgFixedExpense = 9_000;
        a.avgGambling = 0;
        a.avgEndBalance = 2_500;
        a.savingsRate = (a.avgIncome - a.avgExpense) / a.avgIncome;
        a.incomeCv = 0.5;
        a.regularIncomeShare = a.avgIncomeRegular / a.avgIncome;
        a.fixedExpenseShare = a.avgFixedExpense / a.avgIncome;
        a.balanceTrendSlope = -1500;
        a.healthyBufferShare = 0.1;
        a.hadOverdraft = true;
        a.gamblingTotal = 0;
        return a;
    }

    @Test
    public void finalScoreBlendsExternalAndBehaviour() {
        CreditScoreCalculator calc = new CreditScoreCalculator();
        ScoreBreakdown s = calc.score(700, weakButNoGambling());

        assertEquals(700, s.externalScore);
        assertTrue("behaviour should be low for a negative saver", s.behaviorScore < 350);
        assertTrue(s.finalScore < 700 && s.finalScore > s.behaviorScore);
        assertEquals(6, s.factors.size());
    }

    @Test
    public void removingGamblingImprovesProjectedScore() {
        CreditScoreCalculator calc = new CreditScoreCalculator();
        StatementAnalysis a = weakButNoGambling();
        a.avgGambling = 4_000;
        a.gamblingTotal = 44_000;

        int base = calc.score(600, a).finalScore;
        int projected = calc.projectedFinal(600, a, 4_000, 0, true);

        assertTrue("dropping gambling must not lower the score", projected >= base);
        assertTrue("dropping gambling should raise the score", projected > base);
    }

    @Test
    public void scoreStaysInRange() {
        CreditScoreCalculator calc = new CreditScoreCalculator();
        ScoreBreakdown s = calc.score(999, weakButNoGambling());
        assertTrue(s.finalScore >= 0 && s.finalScore <= 999);
        assertTrue(s.behaviorScore >= 0 && s.behaviorScore <= 999);
    }
}
