package com.creditwise.app.data.model;

/** How a transaction is treated by the scoring model. */
public enum FlowType {
    INCOME_SALARY(1.00),
    INCOME_REGULAR(1.00),
    INCOME_FAMILY(0.50),
    INCOME_OTHER_BANK(0.50),
    INCOME_CASH(0.60),
    INCOME_GIFT(0.30),
    /** A transfer matched, across two uploaded statements, as the user moving money to themselves. */
    INCOME_SELF_TRANSFER(1.00),

    INTERNAL(0.00),
    REFUND(0.00),

    EXPENSE_FIXED(0.00),
    EXPENSE_VARIABLE(0.00),
    GAMBLING(0.00),
    CASH_OUT(0.00),

    UNKNOWN(0.00);

    private final double incomeCoefficient;

    FlowType(double incomeCoefficient) {
        this.incomeCoefficient = incomeCoefficient;
    }

    /** Weight applied to a credit before it counts as income (0..1). */
    public double incomeCoefficient() {
        return incomeCoefficient;
    }

    public boolean isIncome() {
        return this == INCOME_SALARY || this == INCOME_REGULAR || this == INCOME_FAMILY
                || this == INCOME_OTHER_BANK || this == INCOME_CASH || this == INCOME_GIFT
                || this == INCOME_SELF_TRANSFER;
    }

    public boolean isExpense() {
        return this == EXPENSE_FIXED || this == EXPENSE_VARIABLE
                || this == GAMBLING || this == CASH_OUT;
    }
}
