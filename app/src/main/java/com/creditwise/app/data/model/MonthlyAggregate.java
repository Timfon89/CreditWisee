package com.creditwise.app.data.model;

import java.time.YearMonth;
import java.util.EnumMap;
import java.util.Map;

public class MonthlyAggregate {
    public final YearMonth month;

    public double incomeRaw;         // sum of all credits treated as income (before coefficients)
    public double incomeEffective;   // sum of credits * coefficient
    public double incomeRegular;     // effective income from INCOME_SALARY / INCOME_REGULAR only

    public double expenseTotal;
    public double expenseFixed;
    public double expenseVariable;
    public double gambling;
    public double cashOut;

    public double minBalance = Double.MAX_VALUE;
    public double endBalance;
    public boolean hadOverdraft;
    public int mfoHits;

    public final Map<ExpenseCategory, Double> byCategory = new EnumMap<>(ExpenseCategory.class);

    public MonthlyAggregate(YearMonth month) {
        this.month = month;
    }

    public void addCategory(ExpenseCategory c, double v) {
        byCategory.merge(c, v, Double::sum);
    }
}
