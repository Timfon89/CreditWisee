package com.creditwise.app.data.model;

import java.time.LocalDate;

public class StatementHeader {
    public LocalDate periodStart;
    public LocalDate periodEnd;
    public double openingBalance;
    public double closingBalance;
    public double totalCredit;
    public double totalDebit;

    public String ownerLastName = "";
    public String ownerFirstName = "";
    public String ownerMiddleName = "";
    public String accountNumber = "";

    public String ownerInitial() {
        return ownerLastName.isEmpty() ? "" : ownerLastName.substring(0, 1).toUpperCase();
    }
}
