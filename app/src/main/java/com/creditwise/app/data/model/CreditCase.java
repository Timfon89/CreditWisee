package com.creditwise.app.data.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** A saved, completed assessment — the source for the "Разбор балла" tab, which re-displays
 *  this snapshot without re-parsing anything. */
public class CreditCase {
    public String id = UUID.randomUUID().toString();
    public LocalDateTime createdAt = LocalDateTime.now();

    public int trustTotal;
    public String trustBand = "";
    public int baseScore;
    public int externalBuff;
    public int challengesBuff;
    public int habitsBuff;
    public int riskPenalty;
    public int telegramAdjustment;

    /** Biggest discretionary spending category found in the statement — feeds the passive
     *  spend forecast. */
    public String topDiscretionaryCategory = "";
    public double topDiscretionaryMonthlyAmount;
    public double avgIncome;
    public double avgExpense;

    public final List<FactorSnapshot> baseFactors = new ArrayList<>();
    public final List<String> telegramReasons = new ArrayList<>();
    public final List<String> habitsReasons = new ArrayList<>();
    public final List<String> riskReasons = new ArrayList<>();
    public final List<String> challengesReasons = new ArrayList<>();
    public final List<MonthPoint> monthlySeries = new ArrayList<>();

    public static class FactorSnapshot {
        public String name = "";
        public String detail = "";
        public double value;
        public int points;
    }

    /** One month's income/expense, in statement order — feeds the "Динамика по месяцам" chart. */
    public static class MonthPoint {
        public String label = "";
        public double income;
        public double expense;
    }
}
