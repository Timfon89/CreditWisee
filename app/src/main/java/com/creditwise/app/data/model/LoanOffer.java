package com.creditwise.app.data.model;

import java.time.YearMonth;

public class LoanOffer {
    public double amount;
    public int termMonths;
    public double annualRatePercent;
    public YearMonth start;

    public LoanOffer() {}

    public LoanOffer(double amount, int termMonths, double annualRatePercent, YearMonth start) {
        this.amount = amount;
        this.termMonths = termMonths;
        this.annualRatePercent = annualRatePercent;
        this.start = start;
    }

    public boolean isComplete() {
        return amount > 0 && termMonths > 0 && annualRatePercent >= 0;
    }
}
