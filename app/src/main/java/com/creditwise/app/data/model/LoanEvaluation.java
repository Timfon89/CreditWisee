package com.creditwise.app.data.model;

public class LoanEvaluation {
    public double monthlyPayment;
    public double pti;   // payment / monthly income
    public double dti;   // (payment + fixed obligations) / monthly income

    public int level;    // 0 comfortable, 1 borderline, 2 not recommended
    public String verdict = "";
}
