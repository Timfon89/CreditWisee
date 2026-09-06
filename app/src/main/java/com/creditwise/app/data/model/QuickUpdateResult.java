package com.creditwise.app.data.model;

import java.util.ArrayList;
import java.util.List;

/** Outcome of uploading one fresh statement from the standalone "Обновить выписку" screen —
 *  a lightweight check-in that feeds the ongoing challenges without creating a new saved
 *  {@link CreditCase}. */
public class QuickUpdateResult {
    public boolean success;
    public String message = "";
    public int trustBefore;
    public int trustAfter;
    public final List<LenderOffer> newlyUnlocked = new ArrayList<>();
}
