package com.creditwise.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.creditwise.app.data.model.Direction;
import com.creditwise.app.data.model.ParseResult;
import com.creditwise.app.data.model.Transaction;
import com.creditwise.app.data.parser.CardStatementParser;
import com.creditwise.app.data.parser.StatementParsers;

import org.junit.Test;

import java.time.LocalDate;

public class CardStatementParserTest {

    // Fictional data mirroring the layout of a "funds-movement certificate" style statement:
    // no per-row balance column, a name line right before the address, and record blocks of
    // op-date/op-time/proc-date/proc-time/amounts+description(/continuation)/card-suffix.
    private static final String FIXTURE = String.join("\n",
            "Справка о движении средств",
            "01.01.2026",
            "Иванов Иван Иванович",
            "Адрес места жительства: 000000, Тестовая обл, г Тест, ул Тестовая, д. 1",
            "О продукте",
            "Номер лицевого счета: 40817000000000000000",
            "Сумма доступного остатка на 01.01.2026: 1000.00 ₽",
            "Движение средств за период с 01.01.2025 по 01.01.2026",
            "15.12.2025",
            "12:00",
            "15.12.2025",
            "12:00",
            "-500.00 ₽ -500.00 ₽ Оплата в",
            "TEST SHOP MOSCOW RUS",
            "1234",
            "14.12.2025",
            "09:30",
            "14.12.2025",
            "09:31",
            "+1500.00 ₽ +1500.00 ₽ Пополнение. Система быстрых платежей",
            "1234",
            "10.12.2025",
            "08:00",
            "10.12.2025",
            "08:00",
            "-1.00 ₽ -1.00 ₽ Плата за обслуживание —",
            "Пополнения: 1500.00 ₽",
            "Расходы: 501.00 ₽");

    @Test
    public void detectedByDispatcher() {
        ParseResult r = StatementParsers.parse(FIXTURE);
        assertEquals(3, r.transactions.size());
    }

    @Test
    public void parsesHeader() {
        ParseResult r = CardStatementParser.parse(FIXTURE);
        assertEquals(LocalDate.of(2025, 1, 1), r.header.periodStart);
        assertEquals(LocalDate.of(2026, 1, 1), r.header.periodEnd);
        assertEquals(1000.00, r.header.closingBalance, 0.01);
        assertEquals("40817000000000000000", r.header.accountNumber);
        assertEquals("Иванов", r.header.ownerLastName);
        assertEquals("Иван", r.header.ownerFirstName);
        assertEquals("Иванович", r.header.ownerMiddleName);
        assertEquals(1500.00, r.header.totalCredit, 0.01);
        assertEquals(501.00, r.header.totalDebit, 0.01);
    }

    @Test
    public void parsesThreeTransactionsWithMultilineAndCardlessRecords() {
        ParseResult r = CardStatementParser.parse(FIXTURE);
        assertEquals(3, r.transactions.size());

        Transaction debit = r.transactions.get(0);
        assertEquals(Direction.DEBIT, debit.direction);
        assertEquals(500.00, debit.amount, 0.01);
        assertTrue(debit.rawDescription.contains("Оплата в"));
        assertTrue(debit.rawDescription.contains("TEST SHOP"));

        Transaction credit = r.transactions.get(1);
        assertEquals(Direction.CREDIT, credit.direction);
        assertEquals(1500.00, credit.amount, 0.01);

        Transaction fee = r.transactions.get(2);
        assertEquals(Direction.DEBIT, fee.direction);
        assertEquals(1.00, fee.amount, 0.01);
        assertEquals("Плата за обслуживание", fee.rawDescription.trim());
    }

    @Test
    public void reconstructsBalancesBackwardsFromTheClosingFigure() {
        ParseResult r = CardStatementParser.parse(FIXTURE);
        // closing balance (1000.00) is the balance right after the newest (first) transaction;
        // walking backwards undoes each operation to recover the balance before it
        assertEquals(1000.00, r.transactions.get(0).balanceAfter, 0.01);
        assertEquals(1500.00, r.transactions.get(1).balanceAfter, 0.01);
        assertEquals(0.00, r.transactions.get(2).balanceAfter, 0.01);
    }
}
