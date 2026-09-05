package com.creditwise.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.creditwise.app.data.model.Direction;
import com.creditwise.app.data.model.ParseResult;
import com.creditwise.app.data.model.Transaction;
import com.creditwise.app.data.parser.SberStatementParser;

import org.junit.Test;

import java.time.LocalDate;

public class SberStatementParserTest {

    private static final String FIXTURE = String.join("\n",
            "Выписка по платёжному счёту",
            "За период 03.09.2025 — 03.09.2026",
            "Владелец счёта",
            "Галицкий Тимофей Алексеевич",
            "Номер счёта 40817 810 8 7200 8044288",
            "ИТОГО ПО ОПЕРАЦИЯМ ЗА ПЕРИОД:",
            "Остаток на 03.09.2025 71 754,79",
            "Пополнение 614 870,00",
            "Списание 683 940,95",
            "Остаток на 03.09.2026 4 343,84",
            "Расшифровка операций",
            "02.09.2026 18:20 Перевод с карты 1 660,00 2 683,84",
            "02.09.2026 387186 Перевод для М. Элнара . Операция по счету ****4288",
            "01.09.2026 14:11 Прочие операции 388,85 4 343,84",
            "01.09.2026 017829 MAPP_SBERBANK_ONL@IN_PAY. Операция по счету",
            "****4288",
            "В сумму операции включена комиссия 3,85 руб.",
            "01.09.2026 07:31 Выдача наличных 63 000,00 4 732,69",
            "01.09.2026 065203 Банкомат №60211168. Операция по счету ****4288",
            "01.09.2026 05:37 Перевод СБП +63 000,00 67 732,69",
            "01.09.2026 737143 Перевод из Yandex. Операция по счету ****4288");

    @Test
    public void parsesHeader() {
        ParseResult r = SberStatementParser.parse(FIXTURE);
        assertEquals(LocalDate.of(2025, 9, 3), r.header.periodStart);
        assertEquals(LocalDate.of(2026, 9, 3), r.header.periodEnd);
        assertEquals(71754.79, r.header.openingBalance, 0.01);
        assertEquals(4343.84, r.header.closingBalance, 0.01);
        assertEquals(614870.00, r.header.totalCredit, 0.01);
        assertEquals(683940.95, r.header.totalDebit, 0.01);
        assertEquals("Галицкий", r.header.ownerLastName);
        assertEquals("Тимофей", r.header.ownerFirstName);
    }

    @Test
    public void parsesFourTransactions() {
        ParseResult r = SberStatementParser.parse(FIXTURE);
        assertEquals(4, r.transactions.size());

        Transaction first = r.transactions.get(0);
        assertEquals(Direction.DEBIT, first.direction);
        assertEquals(1660.00, first.amount, 0.01);
        assertEquals(2683.84, first.balanceAfter, 0.01);
        assertEquals("Перевод с карты", first.bankCategory);

        Transaction fee = r.transactions.get(1);
        assertEquals(3.85, fee.fee, 0.01);

        Transaction cash = r.transactions.get(2);
        assertEquals(63000.00, cash.amount, 0.01);
        assertEquals(Direction.DEBIT, cash.direction);

        Transaction credit = r.transactions.get(3);
        assertEquals(Direction.CREDIT, credit.direction);
        assertEquals(63000.00, credit.amount, 0.01);
    }

    @Test
    public void reconciles() {
        ParseResult r = SberStatementParser.parse(FIXTURE);
        assertTrue(r.reconciled);
    }
}
