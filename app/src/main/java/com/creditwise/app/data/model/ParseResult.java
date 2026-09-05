package com.creditwise.app.data.model;

import java.util.ArrayList;
import java.util.List;

public class ParseResult {
    public StatementHeader header = new StatementHeader();
    public final List<Transaction> transactions = new ArrayList<>();
    public final List<String> warnings = new ArrayList<>();
    public boolean reconciled = true;

    public boolean isUsable() {
        return transactions.size() >= 10 && header.periodStart != null && header.periodEnd != null;
    }
}
