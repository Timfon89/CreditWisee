package com.creditwise.app.data.model;

/**
 * Illustrative Sberbank consumer-loan tiers based on publicly advertised ranges (Sep 2026),
 * not a live feed and not a personalized offer — picking one just pre-fills the tariff
 * calculator with realistic starting numbers that the user can still edit.
 */
public class SberbankLoanOffer {

    public final String label;
    public final double amount;
    public final int termMonths;
    public final double annualRatePercent;

    public SberbankLoanOffer(String label, double amount, int termMonths, double annualRatePercent) {
        this.label = label;
        this.amount = amount;
        this.termMonths = termMonths;
        this.annualRatePercent = annualRatePercent;
    }

    @Override
    public String toString() {
        return label;
    }

    public static final SberbankLoanOffer[] CATALOG = {
            new SberbankLoanOffer("Небольшой — 100 000 ₽ · 12 мес. · от 21,9%", 100_000, 12, 21.9),
            new SberbankLoanOffer("Средний — 300 000 ₽ · 24 мес. · от 19,9%", 300_000, 24, 19.9),
            new SberbankLoanOffer("Крупный — 700 000 ₽ · 36 мес. · от 17,9%", 700_000, 36, 17.9),
            new SberbankLoanOffer("Долгосрочный — 1 500 000 ₽ · 60 мес. · от 16,9%", 1_500_000, 60, 16.9),
    };
}
