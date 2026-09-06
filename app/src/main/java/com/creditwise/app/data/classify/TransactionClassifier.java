package com.creditwise.app.data.classify;

import com.creditwise.app.data.model.Direction;
import com.creditwise.app.data.model.ExpenseCategory;
import com.creditwise.app.data.model.FlowType;
import com.creditwise.app.data.model.StatementHeader;
import com.creditwise.app.data.model.Transaction;

import java.time.LocalDate;
import java.time.MonthDay;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Assigns {@link FlowType} and {@link ExpenseCategory} to parsed transactions. */
public final class TransactionClassifier {

    private final String ownerFirst;
    private final String ownerMiddle;
    private final String ownerInitial;

    public TransactionClassifier(StatementHeader header) {
        this.ownerFirst = header.ownerFirstName == null ? "" : header.ownerFirstName;
        this.ownerMiddle = header.ownerMiddleName == null ? "" : header.ownerMiddleName;
        this.ownerInitial = header.ownerInitial();
    }

    public void classifyAll(List<Transaction> transactions) {
        for (Transaction t : transactions) classifyOne(t);
        detectRecurringExpenses(transactions);
        detectRegularIncome(transactions);
    }

    // --------------------------------------------------------------- one pass

    // A transfer "to/from a contract number" (no person's name or phone number attached) is the
    // account holder moving money to their own other product at the same institution — a
    // savings sub-account, another card, etc. — not income or spending. Distinguishes this from
    // e.g. "Внешний перевод по номеру телефона +7...", which names an actual recipient.
    private static final java.util.regex.Pattern OWN_CONTRACT_TRANSFER = java.util.regex.Pattern.compile(
            "ПЕРЕВОД.*ДОГОВОР");

    private void classifyOne(Transaction t) {
        String upper = norm(t.rawDescription + " " + t.bankCategory);
        String cat = t.bankCategory == null ? "" : t.bankCategory.toLowerCase();

        if (OWN_CONTRACT_TRANSFER.matcher(upper).find()) {
            t.flowType = FlowType.INTERNAL;
            t.category = ExpenseCategory.OTHER;
            return;
        }

        if (t.direction == Direction.CREDIT) {
            classifyCredit(t, upper, cat);
        } else {
            classifyDebit(t, upper, cat);
        }
    }

    private void classifyCredit(Transaction t, String upper, String cat) {
        if (isSelf(t.rawDescription) || upper.contains("VKLAD") || upper.contains("ВКЛАД")
                || upper.contains("SBERBANK ONL")) {
            t.flowType = FlowType.INTERNAL;
            t.category = ExpenseCategory.OTHER;
            return;
        }
        if (cat.startsWith("возврат")) {
            t.flowType = FlowType.REFUND;
            return;
        }
        if (cat.contains("внесение наличных") || upper.contains("ATM ") || upper.contains("БАНКОМАТ")) {
            t.flowType = FlowType.INCOME_CASH;
            return;
        }
        if (t.counterpartyName != null && t.counterpartyInitial == null) {
            // "Перевод из T-Bank" — likely an own account at another bank or an outside top-up
            t.flowType = FlowType.INCOME_OTHER_BANK;
            return;
        }
        if (t.counterpartyInitial != null && ownerInitial.length() == 1
                && t.counterpartyInitial.startsWith(ownerInitial)) {
            t.flowType = FlowType.INCOME_FAMILY;
            return;
        }
        if (isNearHoliday(t.date()) || t.amount <= 1500d) {
            t.flowType = FlowType.INCOME_GIFT;
            return;
        }
        t.flowType = FlowType.INCOME_REGULAR; // may be re-checked by detectRegularIncome
    }

    // "мфо", "микрозайм", "займ" — but not "займа на пополнение [вклада]", which is the
    // bank's own savings-top-up wording and would otherwise false-positive on "займ".
    private static final java.util.regex.Pattern MFO_RISK = java.util.regex.Pattern.compile(
            "МФО|МИКРОЗАЙМ|ЗАЙМ(?!А НА ПОПОЛНЕНИЕ)");

    private void classifyDebit(Transaction t, String upper, String cat) {
        if (cat.startsWith("возврат")) {
            t.flowType = FlowType.REFUND;
            return;
        }
        if (cat.contains("выдача наличных") || upper.contains("ATM ")
                || (upper.contains("БАНКОМАТ") && !cat.contains("внесение"))) {
            t.flowType = FlowType.CASH_OUT;
            t.category = ExpenseCategory.CASH;
            return;
        }
        if (MFO_RISK.matcher(upper).find()) {
            t.flowType = FlowType.EXPENSE_VARIABLE;
            t.category = ExpenseCategory.MFO_PAYMENT;
            return;
        }
        if (MerchantDictionary.isGambling(upper)) {
            t.flowType = FlowType.GAMBLING;
            t.category = ExpenseCategory.GAMBLING;
            return;
        }

        ExpenseCategory byMerchant = MerchantDictionary.categoryFor(upper);
        if (byMerchant != null) {
            t.category = byMerchant;
        } else {
            t.category = byBankCategory(cat, t);
        }
        t.flowType = (t.category == ExpenseCategory.SUBSCRIPTIONS
                || t.category == ExpenseCategory.UTILITIES)
                ? FlowType.EXPENSE_FIXED : FlowType.EXPENSE_VARIABLE;
    }

    private ExpenseCategory byBankCategory(String cat, Transaction t) {
        if (cat.contains("супермаркет")) return ExpenseCategory.GROCERIES;
        if (cat.contains("ресторан") || cat.contains("кафе")) return ExpenseCategory.CAFE;
        if (cat.contains("отдых") || cat.contains("развлеч")) return ExpenseCategory.ENTERTAINMENT;
        if (cat.contains("транспорт")) return ExpenseCategory.PUBLIC_TRANSPORT;
        if (cat.contains("здоровье") || cat.contains("красот")) return ExpenseCategory.HEALTH;
        if (cat.contains("образован")) return ExpenseCategory.EDUCATION;
        if (cat.contains("коммунальн") || cat.contains("связь")) return ExpenseCategory.UTILITIES;
        if (cat.startsWith("перевод")) return ExpenseCategory.TRANSFERS_OUT;
        if (cat.contains("qr") || cat.contains("оплата")) {
            t.needsReview = true;
            return ExpenseCategory.OTHER;
        }
        return ExpenseCategory.OTHER;
    }

    // ------------------------------------------------------ recurring / regular

    private void detectRecurringExpenses(List<Transaction> transactions) {
        Map<String, List<Transaction>> groups = new HashMap<>();
        for (Transaction t : transactions) {
            if (t.direction != Direction.DEBIT) continue;
            if (t.flowType == FlowType.CASH_OUT || t.flowType == FlowType.GAMBLING) continue;
            if (t.flowType == FlowType.INTERNAL) continue; // own-contract transfer — not spending
            if (t.category == ExpenseCategory.MFO_PAYMENT) continue; // keep the risk flag visible
            String key = merchantKey(t.rawDescription);
            if (key.length() < 3) continue;
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(t);
        }
        for (List<Transaction> g : groups.values()) {
            if (g.size() < 3) continue;
            if (distinctMonths(g) < 3) continue;
            if (coefficientOfVariation(amounts(g)) > 0.35) continue;
            for (Transaction t : g) {
                if (t.category != ExpenseCategory.UTILITIES) {
                    t.category = ExpenseCategory.SUBSCRIPTIONS;
                }
                t.flowType = FlowType.EXPENSE_FIXED;
            }
        }
    }

    private void detectRegularIncome(List<Transaction> transactions) {
        Map<String, List<Transaction>> groups = new HashMap<>();
        for (Transaction t : transactions) {
            if (!t.flowType.isIncome()) continue;
            String key = null;
            if (t.counterpartyName != null) {
                key = t.counterpartyName;
            } else if (t.counterpartyInitial != null) {
                key = t.counterpartyInitial + "|" + t.rawDescription.length();
            }
            if (key == null) continue;
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(t);
        }
        for (List<Transaction> g : groups.values()) {
            if (g.size() < 3) continue;
            if (distinctMonths(g) < 3) continue;
            if (coefficientOfVariation(amounts(g)) > 0.45) continue;
            for (Transaction t : g) t.flowType = FlowType.INCOME_REGULAR;
        }
    }

    // ------------------------------------------------------------------ helpers

    private boolean isSelf(String description) {
        if (description == null || ownerFirst.isEmpty()) return false;
        boolean hasFirst = description.contains(ownerFirst);
        boolean hasMiddle = !ownerMiddle.isEmpty() && description.contains(ownerMiddle);
        boolean hasInitial = !ownerInitial.isEmpty() && description.contains(ownerInitial + ".");
        return hasFirst && (hasMiddle || hasInitial);
    }

    private static final Set<MonthDay> HOLIDAYS = buildHolidays();

    private static Set<MonthDay> buildHolidays() {
        Set<MonthDay> s = new HashSet<>();
        for (int d = 28; d <= 31; d++) s.add(MonthDay.of(12, d));
        for (int d = 1; d <= 8; d++) s.add(MonthDay.of(1, d));
        s.add(MonthDay.of(2, 14));
        s.add(MonthDay.of(2, 23));
        s.add(MonthDay.of(3, 8));
        s.add(MonthDay.of(5, 9));
        return s;
    }

    private boolean isNearHoliday(LocalDate d) {
        if (d == null) return false;
        return HOLIDAYS.contains(MonthDay.of(d.getMonthValue(), d.getDayOfMonth()));
    }

    static String norm(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            sb.append(Character.isSpaceChar(c) || Character.isWhitespace(c) ? ' ' : c);
        }
        return sb.toString().toUpperCase().replaceAll(" +", " ").trim();
    }

    static String merchantKey(String raw) {
        String s = norm(raw)
                .replaceAll("[0-9]", " ")
                .replaceAll("RUS|MOSCOW|MOSKVA|CHELYABINSK|CHELJABINSK|EKATERINBURG|MIASS|"
                        + "LAZURNYJ|NOVOABZAKOVO|_P_QR|_QR|\\*|\\.|,|N |\"", " ")
                .replaceAll(" +", " ")
                .trim();
        String[] parts = s.split(" ");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(2, parts.length); i++) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(parts[i]);
        }
        return sb.toString();
    }

    private static int distinctMonths(List<Transaction> g) {
        Set<String> months = new HashSet<>();
        for (Transaction t : g) {
            LocalDate d = t.date();
            if (d != null) months.add(d.getYear() + "-" + d.getMonthValue());
        }
        return months.size();
    }

    private static double[] amounts(List<Transaction> g) {
        double[] a = new double[g.size()];
        for (int i = 0; i < g.size(); i++) a[i] = g.get(i).amount;
        return a;
    }

    static double coefficientOfVariation(double[] values) {
        if (values.length < 2) return 0d;
        double mean = 0;
        for (double v : values) mean += v;
        mean /= values.length;
        if (mean == 0) return 0d;
        double var = 0;
        for (double v : values) var += (v - mean) * (v - mean);
        var /= values.length;
        return Math.sqrt(var) / mean;
    }
}
