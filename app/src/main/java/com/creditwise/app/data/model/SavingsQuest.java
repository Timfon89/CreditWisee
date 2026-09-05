package com.creditwise.app.data.model;

import java.time.LocalDate;
import java.util.UUID;

/** A "save on category X" quest: explain → wait ~30 days → verify with a follow-up statement. */
public class SavingsQuest {

    public enum Status { ACTIVE, SUCCESS, MISSED }

    public static final int TARGET_REDUCTION_PERCENT = 20;
    public static final int DURATION_DAYS = 30;
    public static final int REWARD_POINTS = 8;

    public String id = UUID.randomUUID().toString();
    public String category = "";
    public double baselineMonthlyAmount;
    public LocalDate startDate = LocalDate.now();
    public LocalDate dueDate = LocalDate.now().plusDays(DURATION_DAYS);
    public Status status = Status.ACTIVE;

    public long daysLeft() {
        return java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), dueDate);
    }

    public boolean isDue() {
        return !LocalDate.now().isBefore(dueDate);
    }
}
