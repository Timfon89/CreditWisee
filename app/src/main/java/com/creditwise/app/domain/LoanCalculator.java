package com.creditwise.app.domain;

import com.creditwise.app.data.model.LoanEvaluation;
import com.creditwise.app.data.model.LoanOffer;
import com.creditwise.app.data.model.StatementAnalysis;
import com.creditwise.app.util.Money;

public final class LoanCalculator {

    /** Annuity payment: {@code P * i / (1 - (1 + i)^-n)}. */
    public double annuityPayment(double principal, double annualRatePercent, int months) {
        if (months <= 0 || principal <= 0) return 0d;
        double i = annualRatePercent / 100d / 12d;
        if (i <= 0) return principal / months;
        double pow = Math.pow(1d + i, -months);
        return principal * i / (1d - pow);
    }

    public LoanEvaluation evaluate(LoanOffer offer, StatementAnalysis a) {
        LoanEvaluation e = new LoanEvaluation();
        e.monthlyPayment = annuityPayment(offer.amount, offer.annualRatePercent, offer.termMonths);

        double income = Math.max(1d, a.avgIncome);
        e.pti = e.monthlyPayment / income;
        e.dti = (e.monthlyPayment + a.avgFixedExpense) / income;

        if (e.dti <= 0.35) {
            e.level = 0;
            e.verdict = "Платёж " + Money.format(e.monthlyPayment) + " в месяц — комфортная нагрузка ("
                    + Money.percent(e.dti) + " дохода с учётом текущих обязательных расходов).";
        } else if (e.dti <= 0.50) {
            e.level = 1;
            e.verdict = "Платёж " + Money.format(e.monthlyPayment) + " в месяц — нагрузка на пределе ("
                    + Money.percent(e.dti) + " дохода). Стоит уменьшить сумму или увеличить срок.";
        } else {
            e.level = 2;
            e.verdict = "Платёж " + Money.format(e.monthlyPayment) + " в месяц — нагрузка " + Money.percent(e.dti)
                    + " дохода. Такой кредит брать не рекомендуется без снижения расходов.";
        }
        return e;
    }
}
