package com.creditwise.app.domain;

import com.creditwise.app.data.model.Direction;
import com.creditwise.app.data.model.ExpenseCategory;
import com.creditwise.app.data.model.FlowType;
import com.creditwise.app.data.model.MonthlyAggregate;
import com.creditwise.app.data.model.ParseResult;
import com.creditwise.app.data.model.StatementAnalysis;
import com.creditwise.app.data.model.Transaction;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Turns classified transactions into month-normalised metrics for scoring. */
public final class AggregationEngine {

    private static final double GIFT_CAP_SHARE = 0.20;

    /**
     * Combines several accounts' statements into one set of month-normalised metrics: amounts
     * (income, expense, categories) are summed across accounts, and each account's own monthly
     * end/minimum balance is summed into a total-liquidity figure. Self-transfers between the
     * accounts should already be reconciled via {@link StatementMerger} before this is called.
     */
    public StatementAnalysis aggregate(List<ParseResult> statements) {
        if (statements.isEmpty()) return new StatementAnalysis();
        if (statements.size() == 1) return aggregate(statements.get(0));

        giftPerMonth.clear();
        StatementAnalysis a = new StatementAnalysis();
        LocalDate periodStart = null, periodEnd = null;
        Map<YearMonth, MonthlyAggregate> byMonth = new LinkedHashMap<>();

        for (ParseResult parsed : statements) {
            a.warnings.addAll(parsed.warnings);
            if (parsed.header.periodStart != null
                    && (periodStart == null || parsed.header.periodStart.isBefore(periodStart))) {
                periodStart = parsed.header.periodStart;
            }
            if (parsed.header.periodEnd != null
                    && (periodEnd == null || parsed.header.periodEnd.isAfter(periodEnd))) {
                periodEnd = parsed.header.periodEnd;
            }
            for (Transaction t : parsed.transactions) {
                LocalDate d = t.date();
                if (d == null) continue;
                byMonth.computeIfAbsent(YearMonth.from(d), MonthlyAggregate::new);
            }
        }
        a.periodStart = periodStart;
        a.periodEnd = periodEnd;

        List<YearMonth> allMonths = new ArrayList<>(byMonth.keySet());
        Collections.sort(allMonths);
        if (allMonths.isEmpty()) return a;

        List<YearMonth> months = allMonths;
        if (allMonths.size() >= 5) {
            months = allMonths.subList(1, allMonths.size() - 1);
        }

        for (ParseResult parsed : statements) {
            Map<YearMonth, Double> acctMinBalance = new LinkedHashMap<>();
            Map<YearMonth, Double> acctEndBalance = new LinkedHashMap<>();
            Set<YearMonth> acctEndSeen = new HashSet<>();

            for (Transaction t : parsed.transactions) {
                LocalDate d = t.date();
                if (d == null) continue;
                YearMonth ym = YearMonth.from(d);
                MonthlyAggregate m = byMonth.get(ym);
                if (m == null) continue;

                if (t.direction == Direction.CREDIT && t.flowType.isIncome()) {
                    double eff = t.amount * t.flowType.incomeCoefficient();
                    m.incomeRaw += t.amount;
                    m.incomeEffective += eff;
                    if (t.flowType == FlowType.INCOME_SALARY || t.flowType == FlowType.INCOME_REGULAR) {
                        m.incomeRegular += eff;
                    }
                    if (t.flowType == FlowType.INCOME_GIFT) {
                        giftBuffer(m, eff);
                    }
                } else if (t.flowType == FlowType.REFUND) {
                    m.expenseVariable = Math.max(0, m.expenseVariable - t.amount);
                    m.expenseTotal = Math.max(0, m.expenseTotal - t.amount);
                } else if (t.flowType.isExpense()) {
                    m.expenseTotal += t.amount;
                    switch (t.flowType) {
                        case EXPENSE_FIXED: m.expenseFixed += t.amount; break;
                        case GAMBLING: m.gambling += t.amount; break;
                        case CASH_OUT: m.cashOut += t.amount; break;
                        default: m.expenseVariable += t.amount; break;
                    }
                    m.addCategory(t.category, t.amount);
                    if (t.category == ExpenseCategory.MFO_PAYMENT) m.mfoHits++;
                }

                double curMin = acctMinBalance.getOrDefault(ym, Double.MAX_VALUE);
                if (t.balanceAfter < curMin) acctMinBalance.put(ym, t.balanceAfter);
                if (t.balanceAfter < 0) m.hadOverdraft = true;
                if (acctEndSeen.add(ym)) acctEndBalance.put(ym, t.balanceAfter);
            }

            for (Map.Entry<YearMonth, Double> e : acctEndBalance.entrySet()) {
                byMonth.get(e.getKey()).endBalance += e.getValue();
            }
            for (Map.Entry<YearMonth, Double> e : acctMinBalance.entrySet()) {
                MonthlyAggregate m = byMonth.get(e.getKey());
                if (m.minBalance == Double.MAX_VALUE) m.minBalance = 0;
                double v = e.getValue() == Double.MAX_VALUE ? 0 : e.getValue();
                m.minBalance += v;
            }
        }

        applyGiftCap(byMonth);
        return finish(a, byMonth, months);
    }

    public StatementAnalysis aggregate(ParseResult parsed) {
        giftPerMonth.clear();
        StatementAnalysis a = new StatementAnalysis();
        a.warnings.addAll(parsed.warnings);
        a.periodStart = parsed.header.periodStart;
        a.periodEnd = parsed.header.periodEnd;

        Map<YearMonth, MonthlyAggregate> byMonth = new LinkedHashMap<>();
        for (Transaction t : parsed.transactions) {
            LocalDate d = t.date();
            if (d == null) continue;
            YearMonth ym = YearMonth.from(d);
            byMonth.computeIfAbsent(ym, MonthlyAggregate::new);
        }
        List<YearMonth> allMonths = new ArrayList<>(byMonth.keySet());
        allMonths.sort(YearMonth::compareTo);
        if (allMonths.isEmpty()) return a;

        // Drop the (usually partial) first and last calendar months, but only when doing so
        // still leaves at least 3 full months (the client's minimum window).
        List<YearMonth> months = allMonths;
        if (allMonths.size() >= 5) {
            months = allMonths.subList(1, allMonths.size() - 1);
        }

        java.util.Set<YearMonth> endBalanceSet = new java.util.HashSet<>();
        for (Transaction t : parsed.transactions) {
            LocalDate d = t.date();
            if (d == null) continue;
            YearMonth ym = YearMonth.from(d);
            MonthlyAggregate m = byMonth.get(ym);
            if (m == null) continue;

            if (t.direction == Direction.CREDIT && t.flowType.isIncome()) {
                double eff = t.amount * t.flowType.incomeCoefficient();
                m.incomeRaw += t.amount;
                m.incomeEffective += eff;
                if (t.flowType == FlowType.INCOME_SALARY || t.flowType == FlowType.INCOME_REGULAR) {
                    m.incomeRegular += eff;
                }
                if (t.flowType == FlowType.INCOME_GIFT) {
                    giftBuffer(m, eff);
                }
            } else if (t.flowType == FlowType.REFUND) {
                m.expenseVariable = Math.max(0, m.expenseVariable - t.amount);
                m.expenseTotal = Math.max(0, m.expenseTotal - t.amount);
            } else if (t.flowType.isExpense()) {
                m.expenseTotal += t.amount;
                switch (t.flowType) {
                    case EXPENSE_FIXED: m.expenseFixed += t.amount; break;
                    case GAMBLING: m.gambling += t.amount; break;
                    case CASH_OUT: m.cashOut += t.amount; break;
                    default: m.expenseVariable += t.amount; break;
                }
                m.addCategory(t.category, t.amount);
                if (t.category == ExpenseCategory.MFO_PAYMENT) m.mfoHits++;
            }

            if (t.balanceAfter < m.minBalance) m.minBalance = t.balanceAfter;
            if (t.balanceAfter < 0) m.hadOverdraft = true;
            // list is newest-first, so the first row seen for a month is its month-end balance
            if (endBalanceSet.add(ym)) m.endBalance = t.balanceAfter;
        }

        applyGiftCap(byMonth);
        return finish(a, byMonth, months);
    }

    /** Shared tail: folds the already-populated per-month map into final averages/ratios. */
    private StatementAnalysis finish(StatementAnalysis a, Map<YearMonth, MonthlyAggregate> byMonth,
                                     List<YearMonth> months) {
        // Collect the selected months into the analysis
        double[] incomeSeries = new double[months.size()];
        double[] endBalanceSeries = new double[months.size()];
        int healthy = 0;
        int monthsWithIncome = 0;
        for (int i = 0; i < months.size(); i++) {
            MonthlyAggregate m = byMonth.get(months.get(i));
            if (m.minBalance == Double.MAX_VALUE) m.minBalance = m.endBalance;
            a.months.add(m);
            incomeSeries[i] = m.incomeEffective;
            endBalanceSeries[i] = m.endBalance;
            if (m.incomeEffective > 0) monthsWithIncome++;

            a.avgIncome += m.incomeEffective;
            a.avgIncomeRegular += m.incomeRegular;
            a.avgExpense += m.expenseTotal;
            a.avgFixedExpense += m.expenseFixed;
            a.avgVariableExpense += m.expenseVariable;
            a.avgGambling += m.gambling;
            a.avgEndBalance += m.endBalance;
            a.gamblingTotal += m.gambling;
            if (m.hadOverdraft) a.hadOverdraft = true;
            if (m.gambling > 0) a.hadGambling = true;
            a.mfoHitCount += m.mfoHits;
            if (m.minBalance > 0.5 * Math.max(1, m.expenseTotal)) healthy++;

            for (Map.Entry<ExpenseCategory, Double> e : m.byCategory.entrySet()) {
                a.categoryMonthlyAvg.merge(e.getKey(), e.getValue(), Double::sum);
            }
        }

        int n = Math.max(1, months.size());
        a.monthCount = months.size();
        a.avgIncome /= n;
        a.avgIncomeRegular /= n;
        a.avgExpense /= n;
        a.avgFixedExpense /= n;
        a.avgVariableExpense /= n;
        a.avgGambling /= n;
        a.avgEndBalance /= n;
        a.categoryMonthlyAvg.replaceAll((k, v) -> v / n);

        double income = Math.max(1d, a.avgIncome);
        a.savingsRate = (a.avgIncome - a.avgExpense) / income;
        a.incomeCv = coefficientOfVariation(incomeSeries);
        a.regularIncomeShare = clamp01(a.avgIncomeRegular / income);
        a.fixedExpenseShare = clamp01(a.avgFixedExpense / income);
        a.balanceTrendSlope = slope(endBalanceSeries);
        a.liquidityBufferMonths = a.avgEndBalance / Math.max(1d, a.avgExpense);
        a.healthyBufferShare = months.isEmpty() ? 0 : healthy / (double) months.size();
        a.avgBalanceToExpense = a.avgEndBalance / Math.max(1d, a.avgExpense);
        a.incomeMonthShare = months.isEmpty() ? 0 : monthsWithIncome / (double) months.size();
        a.essentialExpenseShare = essentialShare(a.categoryMonthlyAvg);

        if (a.monthCount < 3) {
            a.warnings.add("Выписка охватывает меньше 3 полных месяцев — точность оценки снижена.");
        }
        if (a.hadGambling) {
            a.warnings.add("Обнаружены операции со ставками — они снижают оценку платежеспособности.");
        }
        return a;
    }

    // ------------------------------------------------------------- gift capping

    private final Map<YearMonth, Double> giftPerMonth = new LinkedHashMap<>();

    private void giftBuffer(MonthlyAggregate m, double eff) {
        giftPerMonth.merge(m.month, eff, Double::sum);
    }

    private void applyGiftCap(Map<YearMonth, MonthlyAggregate> byMonth) {
        for (Map.Entry<YearMonth, Double> e : giftPerMonth.entrySet()) {
            MonthlyAggregate m = byMonth.get(e.getKey());
            if (m == null) continue;
            double gift = e.getValue();
            double nonGift = m.incomeEffective - gift;
            double cap = Math.max(0, nonGift) * GIFT_CAP_SHARE;
            if (gift > cap) {
                m.incomeEffective -= (gift - cap);
            }
        }
    }

    private static final java.util.EnumSet<ExpenseCategory> ESSENTIALS = java.util.EnumSet.of(
            ExpenseCategory.GROCERIES, ExpenseCategory.UTILITIES, ExpenseCategory.HEALTH,
            ExpenseCategory.PUBLIC_TRANSPORT, ExpenseCategory.EDUCATION);

    static double essentialShare(Map<ExpenseCategory, Double> monthlyByCategory) {
        double essential = 0, discretionary = 0;
        for (Map.Entry<ExpenseCategory, Double> e : monthlyByCategory.entrySet()) {
            ExpenseCategory c = e.getKey();
            if (c == ExpenseCategory.CASH || c == ExpenseCategory.TRANSFERS_OUT
                    || c == ExpenseCategory.MFO_PAYMENT) continue;
            if (ESSENTIALS.contains(c)) essential += e.getValue();
            else discretionary += e.getValue();
        }
        double total = essential + discretionary;
        return total <= 0 ? 0d : essential / total;
    }

    // ------------------------------------------------------------------- maths

    static double slope(double[] y) {
        int n = y.length;
        if (n < 2) return 0d;
        double sx = 0, sy = 0, sxx = 0, sxy = 0;
        for (int i = 0; i < n; i++) {
            sx += i;
            sy += y[i];
            sxx += (double) i * i;
            sxy += i * y[i];
        }
        double denom = n * sxx - sx * sx;
        if (denom == 0) return 0d;
        return (n * sxy - sx * sy) / denom;
    }

    static double coefficientOfVariation(double[] v) {
        if (v.length < 2) return 0d;
        double mean = 0;
        for (double x : v) mean += x;
        mean /= v.length;
        if (mean <= 0) return 1d;
        double var = 0;
        for (double x : v) var += (x - mean) * (x - mean);
        var /= v.length;
        return Math.sqrt(var) / mean;
    }

    private static double clamp01(double v) {
        return Math.max(0d, Math.min(1d, v));
    }
}
