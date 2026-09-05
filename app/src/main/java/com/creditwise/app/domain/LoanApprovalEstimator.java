package com.creditwise.app.domain;

import com.creditwise.app.data.model.LoanApprovalEstimate;
import com.creditwise.app.data.model.LoanEvaluation;
import com.creditwise.app.data.model.StatementAnalysis;
import com.creditwise.app.data.model.TrustworthinessScore;
import com.creditwise.app.util.Money;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns the 0–100 index into a friendlier "chance of approval" with a traffic-light band and
 * a short list of concrete reasons — this is a simplified, illustrative estimate, not a real
 * underwriting decision.
 */
public final class LoanApprovalEstimator {

    private static final int MAX_REASONS = 4;

    public LoanApprovalEstimate estimate(TrustworthinessScore trust, LoanEvaluation loan, StatementAnalysis a) {
        LoanApprovalEstimate e = new LoanApprovalEstimate();
        List<String> negatives = new ArrayList<>();
        List<String> positives = new ArrayList<>();

        int prob = trust.total;

        if (loan != null) {
            if (loan.dti > 0.50) {
                prob -= 25;
                negatives.add("Платёж по кредиту вместе с текущими обязательными расходами — "
                        + Money.percent(loan.dti) + " дохода, выше комфортных 50%.");
            } else if (loan.dti > 0.35) {
                prob -= 10;
                negatives.add("Долговая нагрузка на грани — " + Money.percent(loan.dti)
                        + " дохода (комфортно до 35%).");
            } else {
                positives.add("Платёж по кредиту — всего " + Money.percent(loan.dti)
                        + " дохода, нагрузка комфортная.");
            }
        }

        if (a != null && a.hadOverdraft) {
            prob -= 8;
            negatives.add("В выписке был уход счёта в минус.");
        }
        if (a != null && a.hadGambling) {
            prob -= 8;
            negatives.add("В выписке есть операции со ставками.");
        }

        TrustworthinessScore.Factor weakest = null;
        TrustworthinessScore.Factor strongest = null;
        for (TrustworthinessScore.Factor f : trust.factors) {
            if (weakest == null || f.value < weakest.value) weakest = f;
            if (strongest == null || f.value > strongest.value) strongest = f;
        }
        if (weakest != null && weakest.value < 0.5) {
            negatives.add("Слабое место — «" + weakest.name + "»: " + weakest.detail);
        }
        if (strongest != null && strongest.value >= 0.8) {
            positives.add("Сильная сторона — «" + strongest.name + "»: " + strongest.detail);
        }

        for (TrustworthinessScore.Adjustment adj : trust.adjustments) {
            if (adj.points < 0) negatives.add("Telegram: " + adj.label);
            else positives.add("Telegram: " + adj.label);
        }
        if (trust.externalBuff > 0) {
            positives.add("Рейтинг с сайта бюро добавил +" + trust.externalBuff + " балл(ов).");
        }

        prob = Math.max(3, Math.min(97, prob)); // an estimate is never absolutely 0 or 100

        e.probabilityPercent = prob;
        if (prob < 40) {
            e.band = LoanApprovalEstimate.Band.LOW;
            e.headline = "Низкий шанс одобрения";
            fill(e.reasons, negatives, "Основных факторов риска не выявлено — но общая оценка по выписке пока низкая.");
        } else if (prob < 70) {
            e.band = LoanApprovalEstimate.Band.MEDIUM;
            e.headline = "Средний шанс одобрения";
            List<String> mixed = new ArrayList<>(negatives);
            mixed.addAll(positives);
            fill(e.reasons, mixed, "Оценка на грани — часть показателей хорошие, часть требуют внимания.");
        } else {
            e.band = LoanApprovalEstimate.Band.HIGH;
            e.headline = "Высокий шанс одобрения";
            fill(e.reasons, positives, "Показатели по выписке стабильно хорошие.");
            e.reasons.add("Ниже — полная детализация расчёта.");
        }
        return e;
    }

    private void fill(List<String> target, List<String> source, String fallback) {
        if (source.isEmpty()) {
            target.add(fallback);
            return;
        }
        for (int i = 0; i < Math.min(MAX_REASONS, source.size()); i++) {
            target.add(source.get(i));
        }
    }
}
