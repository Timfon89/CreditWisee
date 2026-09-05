package com.creditwise.app.data.model;

import java.util.ArrayList;
import java.util.List;

/** Interactive "chance of approval" shown to the user — a friendlier read of the same numbers. */
public class LoanApprovalEstimate {

    public enum Band { LOW, MEDIUM, HIGH }

    public int probabilityPercent; // 0..100
    public Band band = Band.MEDIUM;
    public String headline = "";
    public final List<String> reasons = new ArrayList<>();
}
