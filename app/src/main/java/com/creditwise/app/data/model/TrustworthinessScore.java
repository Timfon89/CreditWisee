package com.creditwise.app.data.model;

import java.util.ArrayList;
import java.util.List;

/** The 0–100 «Индекс благонадёжности»: base from the statement + a capped Telegram adjustment. */
public class TrustworthinessScore {

    public static final int MAX = 100;
    public static final int TELEGRAM_CAP = 10;
    public static final int EXTERNAL_CAP = 10;
    public static final int QUEST_CAP = 10;
    public static final int HABITS_CAP = 10;
    public static final int RISK_CAP = 20;

    public static class Factor {
        public final String name;
        public final String detail;
        public final double value;  // 0..1
        public final int points;    // contribution to the base score

        public Factor(String name, String detail, double value, int points) {
            this.name = name;
            this.detail = detail;
            this.value = value;
            this.points = points;
        }
    }

    public static class Adjustment {
        public final String label;
        public final int points; // signed

        public Adjustment(String label, int points) {
            this.label = label;
            this.points = points;
        }
    }

    public int base;
    public int externalBuff;
    public int questBuff;
    public int habitsBuff;
    public int adjustment;
    public int riskPenalty; // <= 0, e.g. МФО/microloan payments found in the statement
    public int total;
    public String band = "";

    public boolean telegramApplied;
    public String telegramNote = "";

    public final List<Factor> factors = new ArrayList<>();
    public final List<Adjustment> adjustments = new ArrayList<>();
    public final List<String> riskReasons = new ArrayList<>();
}
