package com.creditwise.app.domain;

import com.creditwise.app.data.model.ExpenseCategory;
import com.creditwise.app.data.model.Recommendation;
import com.creditwise.app.data.model.StatementAnalysis;
import com.creditwise.app.util.Money;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Suggests spending changes and estimates their effect on the credit score. */
public final class OptimizationEngine {

    private static final double PUBLIC_TRANSPORT_COST = 2500d; // месячный проездной, оценка

    private final CreditScoreCalculator calculator;

    public OptimizationEngine(CreditScoreCalculator calculator) {
        this.calculator = calculator;
    }

    public static final class Result {
        public final List<Recommendation> items = new ArrayList<>();
        public int baseScore;
        public int combinedScore;
        public double combinedMonthlySaving;
        public double combinedSavingsRate;
        public String combinedSummary = "";
    }

    public Result build(int externalRating, StatementAnalysis a, int baseFinalScore) {
        Result r = new Result();
        r.baseScore = baseFinalScore;

        double income = Math.max(1d, a.avgIncome);
        double taxi = a.categoryAvg(ExpenseCategory.TAXI);
        double delivery = a.categoryAvg(ExpenseCategory.FOOD_DELIVERY);
        double kick = a.categoryAvg(ExpenseCategory.KICKSHARING);
        double subs = a.categoryAvg(ExpenseCategory.SUBSCRIPTIONS);
        double cafe = a.categoryAvg(ExpenseCategory.CAFE);

        double totalVar = 0d, totalFixed = 0d;
        boolean removeGambling = false;

        if (a.avgGambling > 0) {
            add(r, externalRating, a, baseFinalScore, "gambling",
                    "Отказаться от ставок",
                    "Ставки — это не расход первой необходимости и сильнее всего тянут рейтинг вниз. "
                            + "Полный отказ высвобождает " + Money.format(a.avgGambling) + " в месяц.",
                    a.avgGambling, 0d, true);
            totalVar += a.avgGambling;
            removeGambling = true;
        }

        if (taxi > 0.08 * income || taxi > 2000) {
            double saving = Math.max(0d, taxi - PUBLIC_TRANSPORT_COST);
            add(r, externalRating, a, baseFinalScore, "taxi",
                    "Пересесть на общественный транспорт",
                    "На такси уходит " + Money.format(taxi) + " в месяц. Проездной обойдётся примерно в "
                            + Money.format(PUBLIC_TRANSPORT_COST) + ", такси можно оставить на срочные случаи.",
                    saving, 0d, false);
            totalVar += saving;
        }

        if (delivery > 0.10 * income || delivery > 3000) {
            double saving = 0.6 * delivery;
            add(r, externalRating, a, baseFinalScore, "delivery",
                    "Реже заказывать доставку еды",
                    "Доставка еды — " + Money.format(delivery) + " в месяц. Готовка дома и покупки в "
                            + "супермаркете экономят около " + Money.format(saving) + ".",
                    saving, 0d, false);
            totalVar += saving;
        }

        if (kick > 800) {
            double saving = 0.5 * kick;
            add(r, externalRating, a, baseFinalScore, "kick",
                    "Сократить аренду самокатов",
                    "Аренда самокатов и городского транспорта — " + Money.format(kick)
                            + " в месяц. Половину поездок можно заменить пешими или общественным транспортом.",
                    saving, 0d, false);
            totalVar += saving;
        }

        if (subs > 500) {
            double saving = 0.5 * subs;
            add(r, externalRating, a, baseFinalScore, "subs",
                    "Пересмотреть подписки",
                    "На подписки и сервисы уходит " + Money.format(subs)
                            + " в месяц. Отмена половины неиспользуемых экономит около " + Money.format(saving) + ".",
                    saving, saving, false);
            totalFixed += saving;
        }

        if (cafe > 0.15 * income) {
            double saving = 0.3 * cafe;
            add(r, externalRating, a, baseFinalScore, "cafe",
                    "Уменьшить траты на кафе",
                    "Кафе и рестораны — " + Money.format(cafe) + " в месяц (более 15% дохода). "
                            + "Цель −30% высвобождает " + Money.format(saving) + ".",
                    saving, 0d, false);
            totalVar += saving;
        }

        r.items.sort(Comparator.comparingInt((Recommendation x) -> x.deltaScore).reversed());

        r.combinedMonthlySaving = totalVar + totalFixed;
        r.combinedScore = calculator.projectedFinal(externalRating, a, totalVar, totalFixed, removeGambling);
        r.combinedSavingsRate = (a.avgIncome - Math.max(0d, a.avgExpense - r.combinedMonthlySaving)) / income;
        int delta = r.combinedScore - baseFinalScore;
        if (r.items.isEmpty()) {
            r.combinedSummary = "Явных возможностей для оптимизации не найдено — структура расходов сбалансирована.";
        } else {
            r.combinedSummary = "Экономия " + Money.format(r.combinedMonthlySaving) + " в месяц, норма сбережений "
                    + Money.percent1(a.savingsRate) + " → " + Money.percent1(r.combinedSavingsRate)
                    + ", рейтинг " + baseFinalScore + " → " + r.combinedScore
                    + " (" + (delta >= 0 ? "+" : "") + delta + ").";
        }
        return r;
    }

    private void add(Result r, int externalRating, StatementAnalysis a, int baseFinalScore,
                     String id, String title, String body,
                     double variableReduction, double fixedReduction, boolean removeGambling) {
        Recommendation rec = new Recommendation();
        rec.id = id;
        rec.title = title;
        rec.body = body;
        rec.monthlySaving = variableReduction;
        int projected = calculator.projectedFinal(externalRating, a, variableReduction, fixedReduction, removeGambling);
        rec.projectedScore = projected;
        rec.deltaScore = projected - baseFinalScore;
        double income = Math.max(1d, a.avgIncome);
        rec.projectedSavingsRate =
                (a.avgIncome - Math.max(0d, a.avgExpense - variableReduction - fixedReduction
                        - (removeGambling ? a.avgGambling : 0d))) / income;
        r.items.add(rec);
    }
}
