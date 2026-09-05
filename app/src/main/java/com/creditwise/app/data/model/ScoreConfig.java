package com.creditwise.app.data.model;

/** Tunable coefficients of the credit-score model. */
public final class ScoreConfig {

    public static final ScoreConfig DEFAULT = new ScoreConfig();

    public final double weightExternal = 0.50;
    public final double weightBehavior = 0.50;

    // Behaviour factor weights (must sum to 1.0)
    public final double wSavings = 0.28;
    public final double wStability = 0.18;
    public final double wRegularIncome = 0.16;
    public final double wDebtLoad = 0.14;
    public final double wDiscipline = 0.14;
    public final double wTrend = 0.10;

    // Normalisation targets
    public final double savingsRateTarget = 0.30;   // 30%+ savings -> factor 1.0
    public final double incomeCvCap = 0.60;         // CV >= 0.6 -> factor 0.0
    public final double regularIncomeTarget = 0.70;
    public final double fixedExpenseCap = 0.50;

    // Penalties
    public final double gamblingPenaltyMax = 0.15;
    public final double overdraftPenalty = 0.10;
    public final double lowBalancePenalty = 0.05;
    public final double lowBalanceThreshold = 3000d;

    public final int scoreMax = 999;
}
