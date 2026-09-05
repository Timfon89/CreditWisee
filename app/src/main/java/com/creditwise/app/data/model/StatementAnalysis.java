package com.creditwise.app.data.model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Aggregated, month-normalised view of a statement — the input to scoring. */
public class StatementAnalysis {

    public LocalDate periodStart;
    public LocalDate periodEnd;
    public int monthCount;

    public final List<MonthlyAggregate> months = new ArrayList<>();

    public double avgIncome;          // effective, per month
    public double avgIncomeRegular;
    public double avgExpense;
    public double avgFixedExpense;
    public double avgVariableExpense;
    public double avgGambling;
    public double avgEndBalance;

    public double savingsRate;        // (income - expense) / income
    public double incomeCv;           // coefficient of variation of monthly income
    public double regularIncomeShare; // avgIncomeRegular / avgIncome
    public double fixedExpenseShare;  // avgFixedExpense / avgIncome
    public double balanceTrendSlope;  // roubles per month (linear fit of end balance)
    public double liquidityBufferMonths;
    public double healthyBufferShare; // share of months with min balance > 0.5 * monthly expense

    // --- simple metrics for the 0–100 «trustworthiness» index ---
    public double avgBalanceToExpense;   // avgEndBalance / avgExpense
    public double incomeMonthShare;      // share of months that had any income
    public double essentialExpenseShare; // essentials / (essentials + discretionary)

    public boolean hadOverdraft;
    public boolean hadGambling;
    public double gamblingTotal;
    public int mfoHitCount; // debit transactions that look like МФО/микрозайм payments

    public final Map<ExpenseCategory, Double> categoryMonthlyAvg = new EnumMap<>(ExpenseCategory.class);
    public final List<String> warnings = new ArrayList<>();

    public double categoryAvg(ExpenseCategory c) {
        Double v = categoryMonthlyAvg.get(c);
        return v == null ? 0d : v;
    }
}
