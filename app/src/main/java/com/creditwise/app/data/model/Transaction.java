package com.creditwise.app.data.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** One line of the "Расшифровка операций" table. Mutable: the classifier fills in flow/category. */
public class Transaction {

    public LocalDateTime operationDateTime;
    public LocalDate processingDate;
    public String authCode = "";

    public Direction direction = Direction.DEBIT;
    public double amount;          // always positive, in roubles
    public double fee;             // commission included in the amount, if any
    public double balanceAfter;

    public String bankCategory = "";   // "Супермаркеты", "Перевод СБП", ...
    public String rawDescription = ""; // "YANDEX*4121*GO MOSCOW RUS" / "Перевод от Т. Татьяна Петровна"

    public String counterpartyInitial;  // "Т."
    public String counterpartyName;     // "Татьяна Петровна" / "T-Bank" / "Alfa-Bank"

    // Filled by TransactionClassifier:
    public FlowType flowType = FlowType.UNKNOWN;
    public ExpenseCategory category = ExpenseCategory.OTHER;
    public boolean needsReview;

    public LocalDate date() {
        return operationDateTime != null ? operationDateTime.toLocalDate() : processingDate;
    }

    /** Signed amount relative to the account balance. */
    public double signedAmount() {
        return direction == Direction.CREDIT ? amount : -amount;
    }
}
