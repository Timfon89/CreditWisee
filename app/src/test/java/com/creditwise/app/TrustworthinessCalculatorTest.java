package com.creditwise.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.creditwise.app.data.model.EmploymentType;
import com.creditwise.app.data.model.StatementAnalysis;
import com.creditwise.app.data.model.TelegramFlag;
import com.creditwise.app.data.model.TelegramScanResult;
import com.creditwise.app.data.model.TrustworthinessScore;
import com.creditwise.app.domain.TrustworthinessCalculator;

import org.junit.Test;

public class TrustworthinessCalculatorTest {

    private StatementAnalysis solidStatement() {
        StatementAnalysis a = new StatementAnalysis();
        a.monthCount = 3;
        a.avgIncome = 60_000;
        a.avgExpense = 45_000;
        a.avgEndBalance = 50_000;
        a.avgBalanceToExpense = a.avgEndBalance / a.avgExpense; // ~1.1
        a.incomeCv = 0.15;
        a.incomeMonthShare = 1.0;
        a.essentialExpenseShare = 0.65;
        return a;
    }

    private TelegramScanResult tg(boolean identified) {
        TelegramScanResult t = new TelegramScanResult();
        t.ownMessagesIdentified = identified;
        t.ownMessages = 120;
        return t;
    }

    @Test
    public void baseScoreIsHighForSolidStatement() {
        TrustworthinessScore s = new TrustworthinessCalculator().score(solidStatement(), 0, null, false, 0, 0, EmploymentType.EMPLOYEE, false);
        assertTrue("base should be high", s.base >= 80);
        assertEquals(0, s.adjustment);
        assertEquals(0, s.externalBuff);
        assertEquals(0, s.challengesBuff);
        assertEquals(0, s.habitsBuff);
        assertEquals(0, s.riskPenalty);
        assertEquals(s.base, s.total);
        assertEquals("Высокая", s.band);
        assertEquals(3, s.factors.size());
    }

    @Test
    public void externalRatingGivesOnlyASmallCappedBuff() {
        TrustworthinessScore low = new TrustworthinessCalculator().score(solidStatement(), 0, null, false, 0, 0, EmploymentType.EMPLOYEE, false);
        TrustworthinessScore high = new TrustworthinessCalculator().score(solidStatement(), 999, null, false, 0, 0, EmploymentType.EMPLOYEE, false);
        assertEquals(0, low.externalBuff);
        assertEquals(TrustworthinessScore.EXTERNAL_CAP, high.externalBuff);
        // a poor site rating must never subtract points — it can only add up to the cap
        assertTrue(low.total <= high.total);
        assertTrue(high.externalBuff <= TrustworthinessScore.EXTERNAL_CAP);
    }

    @Test
    public void challengesBonusIsCappedAndNeverNegative() {
        TrustworthinessScore s = new TrustworthinessCalculator().score(solidStatement(), 0, null, false, 999, 0, EmploymentType.EMPLOYEE, false);
        assertEquals(TrustworthinessScore.CHALLENGES_CAP, s.challengesBuff);

        TrustworthinessScore negative = new TrustworthinessCalculator().score(solidStatement(), 0, null, false, -50, 0, EmploymentType.EMPLOYEE, false);
        assertEquals(0, negative.challengesBuff);
    }

    @Test
    public void newUserGetsAHigherTemporaryChallengesCap() {
        TrustworthinessScore established = new TrustworthinessCalculator().score(solidStatement(), 0, null, false, 999, 0, EmploymentType.EMPLOYEE, false);
        TrustworthinessScore fresh = new TrustworthinessCalculator().score(solidStatement(), 0, null, false, 999, 0, EmploymentType.EMPLOYEE, true);
        assertEquals(TrustworthinessScore.CHALLENGES_CAP, established.challengesBuff);
        assertEquals(TrustworthinessScore.NEW_USER_CHALLENGES_CAP, fresh.challengesBuff);
        assertTrue(fresh.challengesBuff > established.challengesBuff);
    }

    @Test
    public void habitsBonusIsCappedBothWays() {
        TrustworthinessScore positive = new TrustworthinessCalculator().score(solidStatement(), 0, null, false, 0, 999, EmploymentType.EMPLOYEE, false);
        assertEquals(TrustworthinessScore.HABITS_CAP, positive.habitsBuff);

        TrustworthinessScore negative = new TrustworthinessCalculator().score(solidStatement(), 0, null, false, 0, -999, EmploymentType.EMPLOYEE, false);
        assertEquals(-TrustworthinessScore.HABITS_CAP, negative.habitsBuff);
    }

    @Test
    public void noConsentMeansNoTelegramAdjustment() {
        TelegramScanResult t = tg(true);
        t.rawCounts.put(TelegramFlag.Category.WORK_ACTIVITY, 12);
        TrustworthinessScore s = new TrustworthinessCalculator().score(solidStatement(), 500, t, false, 0, 0, EmploymentType.EMPLOYEE, false);
        assertEquals(0, s.adjustment);
    }

    @Test
    public void workMentionsRaiseScoreWithinCap() {
        TelegramScanResult t = tg(true);
        t.rawCounts.put(TelegramFlag.Category.WORK_ACTIVITY, 12);
        t.rawCounts.put(TelegramFlag.Category.FINANCIAL_LITERACY, 5);
        TrustworthinessScore s = new TrustworthinessCalculator().score(solidStatement(), 500, t, true, 0, 0, EmploymentType.EMPLOYEE, false);
        assertTrue(s.adjustment > 0);
        assertTrue(s.adjustment <= TrustworthinessScore.TELEGRAM_CAP);
        assertTrue(s.total <= 100);
    }

    @Test
    public void debtMentionsLowerScore() {
        TelegramScanResult t = tg(true);
        t.rawCounts.put(TelegramFlag.Category.OVERDUE, 3);
        TrustworthinessScore s = new TrustworthinessCalculator().score(solidStatement(), 500, t, true, 0, 0, EmploymentType.EMPLOYEE, false);
        assertTrue(s.adjustment < 0);
        assertTrue(s.adjustment >= -TrustworthinessScore.TELEGRAM_CAP);
    }

    @Test
    public void singleChatExportIsNotScored() {
        TelegramScanResult t = tg(false); // could not isolate own messages
        t.rawCounts.put(TelegramFlag.Category.WORK_ACTIVITY, 20);
        TrustworthinessScore s = new TrustworthinessCalculator().score(solidStatement(), 500, t, true, 0, 0, EmploymentType.EMPLOYEE, false);
        assertEquals(0, s.adjustment);
    }

    @Test
    public void freelancerGetsASofterRegularityCeilingThanEmployee() {
        StatementAnalysis a = solidStatement();
        a.incomeCv = 0.5; // uneven month-to-month income, typical for freelance work
        TrustworthinessScore employee = new TrustworthinessCalculator().score(a, 0, null, false, 0, 0, EmploymentType.EMPLOYEE, false);
        TrustworthinessScore freelancer = new TrustworthinessCalculator().score(a, 0, null, false, 0, 0, EmploymentType.FREELANCER, false);
        assertTrue("same uneven income should score higher as a freelancer", freelancer.base > employee.base);
    }

    @Test
    public void mfoHitsApplyACappedPenalty() {
        StatementAnalysis a = solidStatement();
        a.mfoHitCount = 5; // would be -50 uncapped
        TrustworthinessScore s = new TrustworthinessCalculator().score(a, 0, null, false, 0, 0, EmploymentType.EMPLOYEE, false);
        assertEquals(-TrustworthinessScore.RISK_CAP, s.riskPenalty);
        assertEquals(1, s.riskReasons.size());

        StatementAnalysis clean = solidStatement();
        TrustworthinessScore s2 = new TrustworthinessCalculator().score(clean, 0, null, false, 0, 0, EmploymentType.EMPLOYEE, false);
        assertEquals(0, s2.riskPenalty);
    }
}
