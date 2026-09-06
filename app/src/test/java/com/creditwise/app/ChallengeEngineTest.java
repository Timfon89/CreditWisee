package com.creditwise.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.creditwise.app.data.model.ChallengeState;
import com.creditwise.app.data.model.Direction;
import com.creditwise.app.data.model.FlowType;
import com.creditwise.app.data.model.MonthlyAggregate;
import com.creditwise.app.data.model.ParseResult;
import com.creditwise.app.data.model.StatementAnalysis;
import com.creditwise.app.data.model.Transaction;
import com.creditwise.app.domain.ChallengeEngine;

import org.junit.Test;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Collections;

public class ChallengeEngineTest {

    private static MonthlyAggregate month(int y, int m, double expense) {
        MonthlyAggregate ma = new MonthlyAggregate(YearMonth.of(y, m));
        ma.expenseTotal = expense;
        return ma;
    }

    private static StatementAnalysis analysisOf(MonthlyAggregate... months) {
        StatementAnalysis a = new StatementAnalysis();
        Collections.addAll(a.months, months);
        return a;
    }

    // ------------------------------------------------------------------ «Экономия»

    @Test
    public void firstMonthSetsBaselineWithoutScoring() {
        ChallengeState state = new ChallengeState();
        ChallengeEngine.updateSavings(state, analysisOf(month(2026, 1, 40_000)));

        assertEquals(40_000, state.baselineExpense, 0.01);
        assertEquals("2026-01", state.baselineMonth);
        assertEquals(0, state.savingsPoints, 0.01);
        assertEquals(1, state.savingsHistory.size());
        assertTrue(state.savingsHistory.get(0).endsWith(":BASE"));
    }

    @Test
    public void spendingAtOrBelowBaselineIsAHit() {
        ChallengeState state = new ChallengeState();
        ChallengeEngine.updateSavings(state, analysisOf(month(2026, 1, 40_000), month(2026, 2, 35_000)));

        assertEquals(1.0, state.savingsPoints, 0.01);
        assertEquals(0, state.savingsConsecutiveMisses);
        assertTrue(state.savingsHistory.get(1).endsWith(":HIT"));
    }

    @Test
    public void reprocessingTheSameMonthsIsANoOp() {
        ChallengeState state = new ChallengeState();
        StatementAnalysis a = analysisOf(month(2026, 1, 40_000), month(2026, 2, 35_000));
        ChallengeEngine.updateSavings(state, a);
        ChallengeEngine.updateSavings(state, a); // same data again, e.g. user tweaks loan terms

        assertEquals(1.0, state.savingsPoints, 0.01);
        assertEquals(2, state.savingsHistory.size());
    }

    @Test
    public void missDecaysExistingPointsInsteadOfResettingThem() {
        ChallengeState state = new ChallengeState();
        ChallengeEngine.updateSavings(state, analysisOf(month(2026, 1, 40_000), month(2026, 2, 35_000)));
        assertEquals(1.0, state.savingsPoints, 0.01);

        ChallengeEngine.updateSavings(state, analysisOf(
                month(2026, 1, 40_000), month(2026, 2, 35_000), month(2026, 3, 45_000)));

        assertEquals(0.90, state.savingsPoints, 0.01); // 1.0 * 0.90 — first slip costs the least
        assertEquals(1, state.savingsConsecutiveMisses);
        assertTrue(state.savingsHistory.get(2).endsWith(":MISS"));
    }

    @Test
    public void aHitFullyResetsTheConsecutiveMissCounter() {
        ChallengeState state = new ChallengeState();
        ChallengeEngine.updateSavings(state, analysisOf(
                month(2026, 1, 40_000),   // base
                month(2026, 2, 45_000),   // miss #1
                month(2026, 3, 35_000))); // hit — recovers

        assertEquals(0, state.savingsConsecutiveMisses);
    }

    @Test
    public void baselineRefreshesEveryThreeProcessedMonths() {
        ChallengeState state = new ChallengeState();
        ChallengeEngine.updateSavings(state, analysisOf(
                month(2026, 1, 40_000),  // base
                month(2026, 2, 35_000),  // 1 since reset
                month(2026, 3, 38_000),  // 2 since reset
                month(2026, 4, 39_000))); // 3 since reset -> baseline refreshes here

        assertEquals(39_000, state.baselineExpense, 0.01);
        assertEquals("2026-04", state.baselineMonth);
        assertEquals(0, state.monthsSinceBaselineReset);
    }

    // ------------------------------------------------------------------ «Регулярность»

    private static Transaction tx(LocalDate date, Direction dir, FlowType flow, double amount) {
        Transaction t = new Transaction();
        t.operationDateTime = date.atStartOfDay();
        t.direction = dir;
        t.flowType = flow;
        t.amount = amount;
        return t;
    }

    private static ParseResult parseResultOf(Transaction... txs) {
        ParseResult p = new ParseResult();
        Collections.addAll(p.transactions, txs);
        return p;
    }

    @Test
    public void aWeekWithConfirmedIncomeIsAHitAndTheLatestWeekIsNeverScored() {
        ChallengeState state = new ChallengeState();
        ParseResult p = parseResultOf(
                tx(LocalDate.of(2026, 1, 5), Direction.CREDIT, FlowType.INCOME_REGULAR, 50_000),   // week A: hit
                tx(LocalDate.of(2026, 1, 12), Direction.DEBIT, FlowType.EXPENSE_VARIABLE, 2_000),   // week B: activity, no income -> miss
                tx(LocalDate.of(2026, 1, 26), Direction.CREDIT, FlowType.INCOME_REGULAR, 50_000));  // week D: latest — not scored yet

        ChallengeEngine.updateRegularity(state, Collections.singletonList(p), 0.1); // low cv — strict pattern

        assertEquals(2, state.regularityHistory.size()); // week A and week B only; week D held back
        assertTrue(state.regularityHistory.get(0).endsWith(":HIT"));
        assertTrue(state.regularityHistory.get(1).endsWith(":MISS"));
        assertEquals(0.45, state.regularityPoints, 0.01); // 0.5 hit, then a first miss decays it ×0.90
    }

    @Test
    public void lenientPatternCreditsAQuietWeekNextToAPaidOne() {
        ChallengeState state = new ChallengeState();
        ParseResult p = parseResultOf(
                tx(LocalDate.of(2026, 1, 5), Direction.CREDIT, FlowType.INCOME_REGULAR, 80_000),  // week A: paid (lump sum)
                tx(LocalDate.of(2026, 1, 12), Direction.DEBIT, FlowType.EXPENSE_VARIABLE, 2_000),  // week B: quiet, but adjacent to A
                tx(LocalDate.of(2026, 1, 26), Direction.DEBIT, FlowType.EXPENSE_VARIABLE, 500));   // week D: latest — not scored

        ChallengeEngine.updateRegularity(state, Collections.singletonList(p), 0.8); // high cv — freelance-like

        assertTrue(state.regularityHistory.get(0).endsWith(":HIT"));
        assertTrue("a quiet week next to a paid one should still count under a lenient pattern",
                state.regularityHistory.get(1).endsWith(":HIT"));
    }

    @Test
    public void ambiguousCreditsDoNotCountAsConfirmedIncome() {
        ChallengeState state = new ChallengeState();
        ParseResult p = parseResultOf(
                tx(LocalDate.of(2026, 1, 5), Direction.CREDIT, FlowType.INCOME_OTHER_BANK, 50_000), // ambiguous — not confirmed
                tx(LocalDate.of(2026, 1, 12), Direction.DEBIT, FlowType.EXPENSE_VARIABLE, 2_000),
                tx(LocalDate.of(2026, 1, 26), Direction.DEBIT, FlowType.EXPENSE_VARIABLE, 500));

        ChallengeEngine.updateRegularity(state, Collections.singletonList(p), 0.1);

        assertTrue("an unverified cross-bank credit should not count as confirmed regular income",
                state.regularityHistory.get(0).endsWith(":MISS"));
    }
}
