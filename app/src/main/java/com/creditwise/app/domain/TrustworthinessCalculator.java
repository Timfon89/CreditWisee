package com.creditwise.app.domain;

import com.creditwise.app.data.model.EmploymentType;
import com.creditwise.app.data.model.StatementAnalysis;
import com.creditwise.app.data.model.TelegramFlag;
import com.creditwise.app.data.model.TelegramScanResult;
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
 * Telegram adjustment: capped at ±{@link TrustworthinessScore#TELEGRAM_CAP}, applied only with the
 * user's explicit consent and only when their own messages could be isolated. Every ± has a reason.
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

    public TrustworthinessScore score(StatementAnalysis a, int externalRating, TelegramScanResult tg,
                                      boolean consent, int questBonusPoints, int habitsBonusPoints,
                                      EmploymentType employmentType) {
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
        s.questBuff = Math.max(0, Math.min(TrustworthinessScore.QUEST_CAP, questBonusPoints));
        s.habitsBuff = Math.max(-TrustworthinessScore.HABITS_CAP,
                Math.min(TrustworthinessScore.HABITS_CAP, habitsBonusPoints));
        s.adjustment = telegramAdjustment(s, tg, consent);
        s.riskPenalty = riskPenalty(s, a);
        s.total = clampScore(s.base + s.externalBuff + s.questBuff + s.habitsBuff + s.adjustment + s.riskPenalty);
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

    private int telegramAdjustment(TrustworthinessScore s, TelegramScanResult tg, boolean consent) {
        if (!consent) {
            s.telegramNote = "Согласие на учёт Telegram не дано — анализ переписок не влияет на балл.";
            return 0;
        }
        if (tg == null || !tg.ownMessagesIdentified) {
            s.telegramNote = "Не удалось выделить ваши собственные сообщения — Telegram не влияет на балл.";
            return 0;
        }

        int work = tg.raw(TelegramFlag.Category.WORK_ACTIVITY);
        int fin = tg.raw(TelegramFlag.Category.FINANCIAL_LITERACY);
        int overdue = tg.raw(TelegramFlag.Category.OVERDUE) + tg.raw(TelegramFlag.Category.CREDIT_MFO);
        int stress = tg.raw(TelegramFlag.Category.STRESS) + tg.raw(TelegramFlag.Category.LOANS_PEOPLE);

        int delta = 0;
        if (work >= 8) {
            delta += 6;
            s.adjustments.add(new TrustworthinessScore.Adjustment(
                    "Регулярно пишете о работе и проектах — признак стабильной занятости", +6));
        } else if (work >= 3) {
            delta += 3;
            s.adjustments.add(new TrustworthinessScore.Adjustment(
                    "Периодически упоминаете работу и проекты", +3));
        }
        if (fin >= 4) {
            delta += 4;
            s.adjustments.add(new TrustworthinessScore.Adjustment(
                    "Пишете о планировании бюджета и накоплениях", +4));
        } else if (fin >= 1) {
            delta += 2;
            s.adjustments.add(new TrustworthinessScore.Adjustment(
                    "Встречаются упоминания финансового планирования", +2));
        }
        if (overdue >= 1) {
            delta -= 6;
            s.adjustments.add(new TrustworthinessScore.Adjustment(
                    "Упоминания просрочек, кредитов и МФО", -6));
        }
        if (stress >= 2) {
            delta -= 3;
            s.adjustments.add(new TrustworthinessScore.Adjustment(
                    "Частые упоминания нехватки денег и займов у людей", -3));
        }

        delta = Math.max(-TrustworthinessScore.TELEGRAM_CAP,
                Math.min(TrustworthinessScore.TELEGRAM_CAP, delta));
        s.telegramApplied = true;
        s.telegramNote = "Проанализировано ваших сообщений: " + tg.ownMessages
                + ". Итоговая корректировка ограничена ±" + TrustworthinessScore.TELEGRAM_CAP + ".";
        return delta;
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
