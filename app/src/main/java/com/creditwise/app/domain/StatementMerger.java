package com.creditwise.app.domain;

import com.creditwise.app.data.model.Direction;
import com.creditwise.app.data.model.ExpenseCategory;
import com.creditwise.app.data.model.FlowType;
import com.creditwise.app.data.model.ParseResult;
import com.creditwise.app.data.model.Transaction;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * When more than one statement is uploaded, finds transfers that are really the user moving
 * money between their own accounts (an outgoing transfer in one statement, a same-amount
 * incoming transfer in another) so the amount is never double-counted as spending in one
 * account and a discounted "maybe income" in the other. A matched pair is re-tagged so the
 * money is counted exactly once, as income.
 */
public final class StatementMerger {

    private static final long MATCH_WINDOW_DAYS = 4;
    private static final double MIN_TOLERANCE = 5.0;
    private static final double TOLERANCE_SHARE = 0.005;

    private StatementMerger() {}

    /** Mutates matched transactions' {@link FlowType} in place. Returns human-readable notes. */
    public static List<String> reconcileSelfTransfers(List<ParseResult> statements) {
        List<String> notes = new ArrayList<>();
        if (statements.size() < 2) return notes;

        Map<Transaction, Integer> statementOf = new IdentityHashMap<>();
        List<Transaction> outgoing = new ArrayList<>();
        List<Transaction> incoming = new ArrayList<>();

        for (int i = 0; i < statements.size(); i++) {
            for (Transaction t : statements.get(i).transactions) {
                statementOf.put(t, i);
                if (t.direction == Direction.DEBIT && t.category == ExpenseCategory.TRANSFERS_OUT
                        && t.flowType == FlowType.EXPENSE_VARIABLE) {
                    outgoing.add(t);
                } else if (t.direction == Direction.CREDIT && t.flowType == FlowType.INCOME_OTHER_BANK) {
                    incoming.add(t);
                }
            }
        }

        boolean[] usedIncoming = new boolean[incoming.size()];
        int matched = 0;
        for (Transaction out : outgoing) {
            LocalDate outDate = out.date();
            if (outDate == null) continue;
            double tolerance = Math.max(MIN_TOLERANCE, out.amount * TOLERANCE_SHARE);

            int bestIndex = -1;
            long bestDayDiff = Long.MAX_VALUE;
            for (int j = 0; j < incoming.size(); j++) {
                if (usedIncoming[j]) continue;
                Transaction in = incoming.get(j);
                if (statementOf.get(in).equals(statementOf.get(out))) continue; // must be cross-statement
                if (Math.abs(out.amount - in.amount) > tolerance) continue;
                LocalDate inDate = in.date();
                if (inDate == null) continue;
                long diff = Math.abs(ChronoUnit.DAYS.between(outDate, inDate));
                if (diff > MATCH_WINDOW_DAYS) continue;
                if (diff < bestDayDiff) {
                    bestDayDiff = diff;
                    bestIndex = j;
                }
            }

            if (bestIndex >= 0) {
                usedIncoming[bestIndex] = true;
                Transaction in = incoming.get(bestIndex);
                out.flowType = FlowType.INTERNAL;
                out.category = ExpenseCategory.OTHER;
                in.flowType = FlowType.INCOME_SELF_TRANSFER;
                matched++;
            }
        }

        if (matched > 0) {
            notes.add("Найдено переводов между вашими счетами: " + matched
                    + " — не учтены как траты, засчитаны как поступление один раз.");
        }
        return notes;
    }
}
