package com.creditwise.app.data.model;

/**
 * Illustrative lender catalog for the "Кредитные предложения" screen — demo/reference tiers
 * gated purely by the 0–100 trust index, not real financial institutions or live offers
 * (same framing as {@link SberbankLoanOffer}: public-style reference data, not a personalized
 * or binding decision from a real lender).
 */
public class LenderOffer {

    public final String name;
    public final String type;              // "МФО" / "Банк"
    public final int minTrustScore;        // 0..100 threshold on TrustworthinessScore.total
    public final double maxAmount;
    public final int maxTermMonths;
    public final double annualRatePercent;

    public LenderOffer(String name, String type, int minTrustScore, double maxAmount,
                        int maxTermMonths, double annualRatePercent) {
        this.name = name;
        this.type = type;
        this.minTrustScore = minTrustScore;
        this.maxAmount = maxAmount;
        this.maxTermMonths = maxTermMonths;
        this.annualRatePercent = annualRatePercent;
    }

    public boolean isEligible(int trustTotal) {
        return trustTotal >= minTrustScore;
    }

    public static final LenderOffer[] CATALOG = {
            new LenderOffer("МикроФинанс Плюс", "МФО", 40, 30_000, 6, 180.0),
            new LenderOffer("Экспресс Кредит", "МФО", 50, 100_000, 12, 45.0),
            new LenderOffer("Городской Кредитный Банк", "Банк", 60, 300_000, 24, 22.0),
            new LenderOffer("Северная Финансовая Группа", "Банк", 70, 700_000, 36, 17.0),
            new LenderOffer("Премиум Финанс Банк", "Банк", 82, 1_500_000, 60, 13.0),
            new LenderOffer("Элит Капитал", "Банк", 92, 3_000_000, 84, 10.0),
    };

    /** Lowest threshold in the catalog — below this, no lender is willing at all. */
    public static int lowestThreshold() {
        int min = Integer.MAX_VALUE;
        for (LenderOffer o : CATALOG) min = Math.min(min, o.minTrustScore);
        return min;
    }
}
