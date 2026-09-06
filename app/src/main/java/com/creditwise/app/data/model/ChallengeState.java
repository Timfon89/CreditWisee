package com.creditwise.app.data.model;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Persistent, per-user progress on the two ongoing challenges — "Экономия" (quarterly, total
 * spend vs a frozen baseline) and "Регулярность" (weekly, confirmed income deposits). Updated
 * automatically every time a fresh statement is analysed; never touched by hand.
 */
public class ChallengeState {

    // ------------------------------------------------------- «Экономия» (Savings)
    public double baselineExpense;
    public String baselineMonth = "";           // "yyyy-MM" the current baseline was captured/reset from
    public int monthsSinceBaselineReset;        // counts up to 3, then the baseline refreshes
    public double savingsPoints;
    public int savingsConsecutiveMisses;
    /** Chronological "yyyy-MM:BASE" / "yyyy-MM:HIT" / "yyyy-MM:MISS", last 6 kept. */
    public final List<String> savingsHistory = new ArrayList<>();
    public final Set<String> processedMonths = new HashSet<>();

    // ------------------------------------------------------- «Регулярность» (Regularity)
    public double regularityPoints;
    public int regularityConsecutiveMisses;
    /** Chronological "yyyy-Www:HIT" / "yyyy-Www:MISS", last 8 kept. */
    public final List<String> regularityHistory = new ArrayList<>();
    public final Set<String> processedWeeks = new HashSet<>();

    public double combinedPoints() {
        return savingsPoints + regularityPoints;
    }

    /** ISO date ({@code LocalDate.toString()}) of the last real statement processed — drives
     *  the "haven't updated in a while" reminder notification. Empty if never. */
    public String lastUpdatedAt = "";
}
