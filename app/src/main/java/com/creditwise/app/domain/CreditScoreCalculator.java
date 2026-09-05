package com.creditwise.app.domain;

import com.creditwise.app.data.model.ScoreBreakdown;
import com.creditwise.app.data.model.ScoreConfig;
import com.creditwise.app.data.model.StatementAnalysis;
import com.creditwise.app.util.Money;

/** Implements the 0–999 credit-score model described in the project plan. */
public final class CreditScoreCalculator {

    private final ScoreConfig cfg;

    public CreditScoreCalculator(ScoreConfig cfg) {
        this.cfg = cfg;
    }

    public CreditScoreCalculator() {
        this(ScoreConfig.DEFAULT);
    }

    public ScoreBreakdown score(int externalRating, StatementAnalysis a) {
        Inputs in = Inputs.from(a);
        return build(externalRating, in, true);
    }

    /** Recomputes the final score after a hypothetical monthly expense change. */
    public int projectedFinal(int externalRating, StatementAnalysis a,
                              double variableReduction, double fixedReduction, boolean removeGambling) {
        Inputs in = Inputs.from(a);
        double income = Math.max(1d, in.avgIncome);
        double newExpense = Math.max(0d, in.avgExpense - variableReduction - fixedReduction
                - (removeGambling ? in.avgGambling : 0d));
        in.savingsRate = (in.avgIncome - newExpense) / income;
        in.fixedShare = clamp01((Math.max(0d, in.avgFixed - fixedReduction)) / income);
        if (removeGambling) in.gamblingTotal = 0d;
        return build(externalRating, in, false).finalScore;
    }

    // ------------------------------------------------------------------ core

    private ScoreBreakdown build(int externalRating, Inputs in, boolean withNotes) {
        ScoreBreakdown b = new ScoreBreakdown();

        double fSavings = clamp01(in.savingsRate / cfg.savingsRateTarget);
        double fStability = clamp01(1d - in.incomeCv / cfg.incomeCvCap);
        double fRegular = clamp01(in.regularIncomeShare / cfg.regularIncomeTarget);
        double fDebt = clamp01(1d - in.fixedShare / cfg.fixedExpenseCap);
        double fDiscipline = clamp01(in.healthyBufferShare);
        double fTrend = clamp01(0.5d + in.balanceTrendSlope / Math.max(1d, in.avgExpense));

        b.factors.add(new ScoreBreakdown.Factor("Норма сбережений", fSavings, cfg.wSavings,
                withNotes ? "Сбережения: " + Money.percent1(in.savingsRate) + " от дохода" : ""));
        b.factors.add(new ScoreBreakdown.Factor("Стабильность дохода", fStability, cfg.wStability,
                withNotes ? "Разброс дохода по месяцам: " + Money.percent(in.incomeCv) : ""));
        b.factors.add(new ScoreBreakdown.Factor("Собственный регулярный доход", fRegular, cfg.wRegularIncome,
                withNotes ? Money.percent(in.regularIncomeShare) + " дохода — регулярные поступления" : ""));
        b.factors.add(new ScoreBreakdown.Factor("Долговая нагрузка", fDebt, cfg.wDebtLoad,
                withNotes ? "Постоянные расходы: " + Money.percent(in.fixedShare) + " дохода" : ""));
        b.factors.add(new ScoreBreakdown.Factor("Финансовая дисциплина", fDiscipline, cfg.wDiscipline,
                withNotes ? Money.percent(in.healthyBufferShare) + " месяцев с запасом на счёте" : ""));
        b.factors.add(new ScoreBreakdown.Factor("Динамика остатка", fTrend, cfg.wTrend,
                withNotes ? (in.balanceTrendSlope >= 0 ? "Остаток растёт" : "Остаток снижается") : ""));

        double raw = 0d;
        for (ScoreBreakdown.Factor f : b.factors) raw += f.contribution;

        double penalty = 0d;
        double totalIncome = Math.max(1d, in.avgIncome * Math.max(1, in.monthCount));
        if (in.gamblingTotal > 0) {
            double p = Math.min(cfg.gamblingPenaltyMax, in.gamblingTotal / totalIncome * 3d);
            penalty += p;
            if (withNotes) b.penaltyReasons.add("Ставки и азартные игры: −" + points(p));
        }
        if (in.hadOverdraft) {
            penalty += cfg.overdraftPenalty;
            if (withNotes) b.penaltyReasons.add("Уход в минус по счёту: −" + points(cfg.overdraftPenalty));
        }
        if (in.avgEndBalance < cfg.lowBalanceThreshold) {
            penalty += cfg.lowBalancePenalty;
            if (withNotes) b.penaltyReasons.add("Почти нулевой остаток на счёте: −" + points(cfg.lowBalancePenalty));
        }

        double behavior = clamp01(raw - penalty);
        b.behaviorRaw = behavior;
        b.penalty = penalty;
        b.externalScore = clamp(externalRating, 0, cfg.scoreMax);
        b.behaviorScore = (int) Math.round(cfg.scoreMax * behavior);
        b.finalScore = (int) Math.round(cfg.weightExternal * b.externalScore
                + cfg.weightBehavior * b.behaviorScore);
        return b;
    }

    private String points(double fraction) {
        return Math.round(fraction * cfg.scoreMax) + " б.";
    }

    private static double clamp01(double v) {
        return Math.max(0d, Math.min(1d, v));
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    /** Mutable snapshot of the analysis values the model needs. */
    private static final class Inputs {
        double avgIncome, avgExpense, avgFixed, avgGambling, avgEndBalance;
        double savingsRate, incomeCv, regularIncomeShare, fixedShare, healthyBufferShare, balanceTrendSlope;
        double gamblingTotal;
        boolean hadOverdraft;
        int monthCount;

        static Inputs from(StatementAnalysis a) {
            Inputs in = new Inputs();
            in.avgIncome = a.avgIncome;
            in.avgExpense = a.avgExpense;
            in.avgFixed = a.avgFixedExpense;
            in.avgGambling = a.avgGambling;
            in.avgEndBalance = a.avgEndBalance;
            in.savingsRate = a.savingsRate;
            in.incomeCv = a.incomeCv;
            in.regularIncomeShare = a.regularIncomeShare;
            in.fixedShare = a.fixedExpenseShare;
            in.healthyBufferShare = a.healthyBufferShare;
            in.balanceTrendSlope = a.balanceTrendSlope;
            in.gamblingTotal = a.gamblingTotal;
            in.hadOverdraft = a.hadOverdraft;
            in.monthCount = a.monthCount;
            return in;
        }
    }
}
