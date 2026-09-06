package com.creditwise.app;

import static org.junit.Assert.assertEquals;

import com.creditwise.app.data.classify.TransactionClassifier;
import com.creditwise.app.data.model.Direction;
import com.creditwise.app.data.model.FlowType;
import com.creditwise.app.data.model.StatementHeader;
import com.creditwise.app.data.model.Transaction;

import org.junit.Test;

import java.util.Collections;

public class TransactionClassifierTest {

    private static Transaction tx(Direction dir, double amount, String description) {
        Transaction t = new Transaction();
        t.direction = dir;
        t.amount = amount;
        t.rawDescription = description;
        t.bankCategory = "";
        return t;
    }

    @Test
    public void transfersToOwnContractsAreNeitherIncomeNorExpense() {
        Transaction out = tx(Direction.DEBIT, 29000, "Внутренний перевод на договор 0146312984");
        Transaction in = tx(Direction.CREDIT, 29000, "Перевод с договора 5140687248");

        new TransactionClassifier(new StatementHeader()).classifyAll(java.util.Arrays.asList(out, in));

        assertEquals(FlowType.INTERNAL, out.flowType);
        assertEquals(FlowType.INTERNAL, in.flowType);
    }

    @Test
    public void aTransferNamingAnActualRecipientIsNotTreatedAsInternal() {
        Transaction toSomeone = tx(Direction.DEBIT, 455, "Внешний перевод по номеру телефона +79026079168");

        new TransactionClassifier(new StatementHeader()).classifyAll(Collections.singletonList(toSomeone));

        assertEquals(FlowType.EXPENSE_VARIABLE, toSomeone.flowType);
    }
}
