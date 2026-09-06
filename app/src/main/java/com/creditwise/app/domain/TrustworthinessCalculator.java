package com.creditwise.app.domain;

import com.creditwise.app.data.model.EmploymentType;
import com.creditwise.app.data.model.StatementAnalysis;
import com.creditwise.app.data.model.TrustworthinessScore;
import com.creditwise.app.util.Money;

/**
 * 0–100 «Индекс благонадёжности».
 *
 * Base (three simple metrics from a ~3-month statement):
 *   • средний остаток относительно месячных расходов        — вес 35
 *   • регулярность поступлений                              — вес 35
 *   • доля трат на необходимые товары                       — вес 30
 *
 * Внешний рейтинг с сайта бюро больше не весит 50% результата — он даёт небольшой
 * односторонний бафф до +{@link TrustworthinessScore#EXTERNAL_CAP} баллов, пропорционально
 * тому, насколько высок рейтинг (0/999 → +0, 999/999 → +10). Низкий внешний рейтинг никогда
 * не штрафует индекс — только не добавляет бонуса.
 *
 * Telegram adjustment: capped at ±{@link TrustworthinessScore#TELEGRAM_CAP} — pre-computed once
 * by {@link TelegramScoring} when the user reviews their scan in «Задания», then replayed here
 * on every future score computation (see {@link TelegramScoring.Result}).
 *
 * Financial-habits questionnaire (self-reported, optional, in "Задания"): capped at
 * ±{@link TrustworthinessScore#HABITS_CAP}. Deliberately about money behaviour only —
 * budgeting, debt attitude, payment discipline — not a personality/psychological test.
 */
public final class TrustworthinessCalculator {

    private static final double WEIGHT_BALANCE = 35;
    private static final double WEIGHT_REGULARITY = 35;
    private static final double WEIGHT_ESSENTIALS = 30;
    private static final double EXTERNAL_RATING_MAX = 999d;

    public TrustworthinessScore score(StatementAnalysis a, int externalRating, TelegramScoring.Result telegram,
                                      double challengesBonusPoints, int habitsBonusPoints,
                                      EmploymentType employmentType, boolean newUser) {
        TrustworthinessScore s = new TrustworthinessScore();

        // Freelancers/self-employed have naturally uneven income — judging them against the
        // same coefficient-of-variation ceiling as a salaried employee would unfairly punish a
        // normal, healthy freelance income pattern. A softer ceiling (1.2 vs 0.8) means the same
        // variability costs fewer points.
        boolean freelancer = employmentType == EmploymentType.FREELANCER;
        double cvCeiling = freelancer ? 1.2 : 0.8;

        double fBalance = clamp01(a.avgBalanceToExpense);                     // 1 month buffer -> full
        double fRegularity = clamp01(0.6 * (1 - a.incomeCv / cvCeiling) + 0.4 * a.incomeMonthShare);
        double fEssentials = clamp01(a.essentialExpenseShare / 0.6);          // 60%+ -> full

        int pBalance = (int) Math.round(WEIGHT_BALANCE * fBalance);
        int pRegularity = (int) Math.round(WEIGHT_REGULARITY * fRegularity);
        int pEssentials = (int) Math.round(WEIGHT_ESSENTIALS * fEssentials);

        s.factors.add(new TrustworthinessScore.Factor(
                "Средний остаток на счёте",
                Money.format(a.avgEndBalance) + " · " + Money.percent(a.avgBalanceToExpense)
                        + " месячных расходов",
                fBalance, pBalance));
        s.factors.add(new TrustworthinessScore.Factor(
                "Регулярность поступлений",
                Money.percent(a.incomeMonthShare) + " месяцев с доходом, разброс сумм "
                        + Money.percent(a.incomeCv)
                        + (freelancer ? " · порог смягчён для фрилансеров/самозанятых" : ""),
                fRegularity, pRegularity));
        s.factors.add(new TrustworthinessScore.Factor(
                "Доля трат на необходимое",
                Money.percent(a.essentialExpenseShare) + " расходов — продукты, ЖКХ, транспорт, здоровье",
                fEssentials, pEssentials));

        s.base = clampScore(pBalance + pRegularity + pEssentials);
        s.externalBuff = externalBuff(externalRating);
        int challengesCap = newUser ? TrustworthinessScore.NEW_USER_CHALLENGES_CAP : TrustworthinessScore.CHALLENGES_CAP;
        s.challengesBuff = (int) Math.round(Math.max(0, Math.min(challengesCap, challengesBonusPoints)));
        s.habitsBuff = Math.max(-TrustworthinessScore.HABITS_CAP,
                Math.min(TrustworthinessScore.HABITS_CAP, habitsBonusPoints));
        applyTelegram(s, telegram);
        s.riskPenalty = riskPenalty(s, a);
        s.total = clampScore(s.base + s.externalBuff + s.challengesBuff + s.habitsBuff + s.adjustment + s.riskPenalty);
        s.band = band(s.total);
        return s;
    }

    /** Debit transactions that look like МФО/microloan payments — a real, verifiable risk signal
     *  from confirmed money movement, not a guess. Capped so one payment doesn't overreact. */
    private int riskPenalty(TrustworthinessScore s, StatementAnalysis a) {
        if (a.mfoHitCount <= 0) return 0;
        int penalty = -Math.min(10 * a.mfoHitCount, TrustworthinessScore.RISK_CAP);
        s.riskReasons.add("Платежи, похожие на МФО/микрозаймы: " + a.mfoHitCount
                + " шт. за период — " + penalty);
        return penalty;
    }

    private int externalBuff(int externalRating) {
        double clamped = Math.max(0, Math.min(EXTERNAL_RATING_MAX, externalRating));
        return (int) Math.round((clamped / EXTERNAL_RATING_MAX) * TrustworthinessScore.EXTERNAL_CAP);
    }

    private void applyTelegram(TrustworthinessScore s, TelegramScoring.Result telegram) {
        if (telegram == null) {
            s.telegramNote = "Telegram не подключён — привяжите чат в «Заданиях», чтобы учесть его в балле.";
            return;
        }
        s.adjustment = telegram.delta;
        s.telegramApplied = telegram.applied;
        s.telegramNote = telegram.note;
        s.adjustments.addAll(telegram.reasons);
    }

    private static String band(int score) {
        if (score >= 70) return "Высокая";
        if (score >= 45) return "Средняя";
        return "Низкая";
    }

    private static double clamp01(double v) {
        return Math.max(0d, Math.min(1d, v));
    }

    private static int clampScore(int v) {
        return Math.max(0, Math.min(TrustworthinessScore.MAX, v));
    }
}
