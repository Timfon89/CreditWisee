package com.creditwise.app.data.model;

import java.util.ArrayList;
import java.util.List;

public class ScoreBreakdown {

    public static class Factor {
        public final String name;
        public final double value;        // 0..1
        public final double weight;       // 0..1
        public final double contribution; // value * weight (0..1)
        public final String note;

        public Factor(String name, double value, double weight, String note) {
            this.name = name;
            this.value = value;
            this.weight = weight;
            this.contribution = value * weight;
            this.note = note;
        }
    }

    public int externalScore;
    public int behaviorScore;
    public int finalScore;

    public double behaviorRaw;   // 0..1 before scaling
    public double penalty;       // 0..1 total deduction

    public final List<Factor> factors = new ArrayList<>();
    public final List<String> penaltyReasons = new ArrayList<>();
}
