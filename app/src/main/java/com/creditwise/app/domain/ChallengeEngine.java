package com.creditwise.app.domain;

import com.creditwise.app.data.model.ChallengeState;
import com.creditwise.app.data.model.Direction;
import com.creditwise.app.data.model.FlowType;
import com.creditwise.app.data.model.MonthlyAggregate;
import com.creditwise.app.data.model.ParseResult;
import com.creditwise.app.data.model.StatementAnalysis;
import com.creditwise.app.data.model.Transaction;
import com.creditwise.app.util.Money;

import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Turns freshly-analysed statement data into progress on the two ongoing challenges —
 * "Экономия" (quarterly, total spend vs a frozen baseline) and "Регулярность" (weekly, confirmed
 * income deposits). Called every time a statement is (re-)analysed; every update is idempotent —
 * already-processed months/weeks are skipped via {@link ChallengeState#processedMonths}/
 * {@code processedWeeks}, so calling this repeatedly on the same data (e.g. while the user
 * tweaks loan terms in the wizard) never double-counts.
 */
public final class ChallengeEngine {

    public static final double SAVINGS_MAX_POINTS = 7d;
    public static final double REGULARITY_MAX_POINTS = 5d;

    private static final double SAVINGS_HIT_POINTS = 1.0;
    private static final double REGULARITY_HIT_POINTS = 0.5;
    private static final int QUARTER_MONTHS = 3;

    private ChallengeEngine() {}

    // a miss doesn't zero the streak, it eats into what's already banked — the first slip costs
    // the least, repeated slips cost more, matching what was agreed with the team
    private static double missMultiplier(int consecutiveMisses) {
        if (consecutiveMisses <= 1) return 0.90;
        if (consecutiveMisses == 2) return 0.75;
        return 0.50;
    }

    // ------------------------------------------------------------------ «Экономия»

    /** Compares each not-yet-seen month's total spend against a frozen baseline; the baseline
     *  itself refreshes every {@value #QUARTER_MONTHS} processed months, not every month. */
    public static void updateSavings(ChallengeState state, StatementAnalysis analysis) {
        if (analysis == null || analysis.months.isEmpty()) return;

        List<MonthlyAggregate> ordered = new ArrayList<>(analysis.months);
        ordered.sort((a, b) -> a.month.compareTo(b.month));

        for (MonthlyAggregate m : ordered) {
            String key = m.month.toString(); // "yyyy-MM"
            if (state.processedMonths.contains(key)) continue;
            state.processedMonths.add(key);

            if (state.baselineMonth.isEmpty()) {
                // the first month ever seen just sets the reference point — nothing to score yet
                state.baselineExpense = m.expenseTotal;
                state.baselineMonth = key;
                state.monthsSinceBaselineReset = 0;
                appendCapped(state.savingsHistory, key + ":BASE", 6);
                continue;
            }

            boolean hit = m.expenseTotal <= state.baselineExpense;
            applyResult(state, hit, true, key);

            state.monthsSinceBaselineReset++;
            if (state.monthsSinceBaselineReset >= QUARTER_MONTHS) {
                // the bar moves once a quarter, from actual behaviour — not every single month
                state.baselineExpense = m.expenseTotal;
                state.baselineMonth = key;
                state.monthsSinceBaselineReset = 0;
            }
        }
    }

    // ------------------------------------------------------------------ «Регулярность»

    /**
     * Groups confirmed regular-income credits (salary-pattern deposits only — never a one-off
     * top-up, gift, or ambiguous cross-bank credit) into ISO weeks and checks each complete week
     * for at least one such deposit. For a naturally uneven income pattern (freelance/self-employed
     * — detected from the statement's own {@code incomeCv}, not from what the user self-reported
     * at registration) a week also counts if either neighbouring week had a deposit, since bunched
     * payments are normal for that income shape.
     */
    public static void updateRegularity(ChallengeState state, List<ParseResult> statements, double incomeCv) {
        if (statements == null || statements.isEmpty()) return;
        boolean lenientPattern = incomeCv > 0.5; // naturally lumpy income — don't punish normal gaps

        WeekFields wf = WeekFields.ISO;
        Map<String, Boolean> hasIncomeByWeek = new TreeMap<>();
        LocalDate latest = null;
        for (ParseResult p : statements) {
            for (Transaction t : p.transactions) {
                LocalDate d = t.date();
                if (d == null) continue;
                if (latest == null || d.isAfter(latest)) latest = d;
                String weekKey = weekKey(d, wf);
                boolean confirmedRegular = t.direction == Direction.CREDIT
                        && (t.flowType == FlowType.INCOME_SALARY || t.flowType == FlowType.INCOME_REGULAR);
                hasIncomeByWeek.merge(weekKey, confirmedRegular, Boolean::logicalOr);
            }
        }
        if (hasIncomeByWeek.isEmpty() || latest == null) return;

        List<String> weeks = new ArrayList<>(hasIncomeByWeek.keySet());
        String currentWeek = weekKey(latest, wf); // may still be in progress — never score it yet

        for (int i = 0; i < weeks.size(); i++) {
            String key = weeks.get(i);
            if (key.equals(currentWeek)) continue;
            if (state.processedWeeks.contains(key)) continue;
            state.processedWeeks.add(key);

            boolean hit = hasIncomeByWeek.get(key);
            if (!hit && lenientPattern) {
                if (i > 0 && hasIncomeByWeek.get(weeks.get(i - 1))) hit = true;
                if (!hit && i + 1 < weeks.size() && hasIncomeByWeek.get(weeks.get(i + 1))) hit = true;
            }
            applyResult(state, hit, false, key);
        }
    }

    private static String weekKey(LocalDate d, WeekFields wf) {
        int week = d.get(wf.weekOfWeekBasedYear());
        int year = d.get(wf.weekBasedYear());
        return String.format(Locale.ROOT, "%d-W%02d", year, week);
    }

    // ------------------------------------------------------------------ shared scoring

    private static void applyResult(ChallengeState state, boolean hit, boolean savings, String key) {
        double cap = savings ? SAVINGS_MAX_POINTS : REGULARITY_MAX_POINTS;
        double hitPoints = savings ? SAVINGS_HIT_POINTS : REGULARITY_HIT_POINTS;
        List<String> history = savings ? state.savingsHistory : state.regularityHistory;
        int historyCap = savings ? 6 : 8;

        if (hit) {
            double current = savings ? state.savingsPoints : state.regularityPoints;
            double updated = Math.min(cap, current + hitPoints);
            if (savings) {
                state.savingsPoints = updated;
                state.savingsConsecutiveMisses = 0;
            } else {
                state.regularityPoints = updated;
                state.regularityConsecutiveMisses = 0;
            }
            appendCapped(history, key + ":HIT", historyCap);
        } else {
            int misses = (savings ? state.savingsConsecutiveMisses : state.regularityConsecutiveMisses) + 1;
            double multiplier = missMultiplier(misses);
            if (savings) {
                state.savingsConsecutiveMisses = misses;
                state.savingsPoints *= multiplier;
            } else {
                state.regularityConsecutiveMisses = misses;
                state.regularityPoints *= multiplier;
            }
            appendCapped(history, key + ":MISS", historyCap);
        }
    }

    private static void appendCapped(List<String> history, String entry, int max) {
        history.add(entry);
        while (history.size() > max) history.remove(0);
    }

    // ------------------------------------------------------------------ display text

    /** Plain-text explanation of the current «Экономия» standing, for the score breakdown. */
    public static List<String> savingsReasons(ChallengeState s) {
        List<String> out = new ArrayList<>();
        if (s.baselineMonth.isEmpty()) {
            out.add("Копим первый месяц данных, чтобы задать базовый уровень расходов.");
            return out;
        }
        out.add("База для сравнения: " + Money.format(s.baselineExpense) + "/мес (с " + s.baselineMonth + ")");
        int hits = countHits(s.savingsHistory);
        int scored = countScored(s.savingsHistory);
        if (scored > 0) {
            out.add("Уложились в базу: " + hits + " из " + scored + " учтённых мес.");
        }
        if (s.savingsConsecutiveMisses > 0) {
            out.add("Пропусков подряд сейчас: " + s.savingsConsecutiveMisses
                    + " — бонус снижается плавно, а не обнуляется.");
        }
        return out;
    }

    /** Plain-text explanation of the current «Регулярность» standing, for the score breakdown. */
    public static List<String> regularityReasons(ChallengeState s) {
        List<String> out = new ArrayList<>();
        int hits = countHits(s.regularityHistory);
        int scored = countScored(s.regularityHistory);
        if (scored == 0) {
            out.add("Копим первые недели данных о поступлениях.");
            return out;
        }
        out.add("Недель с подтверждённым доходом: " + hits + " из " + scored + " учтённых.");
        if (s.regularityConsecutiveMisses > 0) {
            out.add("Пропусков подряд сейчас: " + s.regularityConsecutiveMisses
                    + " — бонус снижается плавно, а не обнуляется.");
        }
        return out;
    }

    private static int countHits(List<String> history) {
        int n = 0;
        for (String h : history) if (h.endsWith(":HIT")) n++;
        return n;
    }

    private static int countScored(List<String> history) {
        int n = 0;
        for (String h : history) if (h.endsWith(":HIT") || h.endsWith(":MISS")) n++;
        return n;
    }
}
