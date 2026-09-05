package com.creditwise.app.data.parser;

import com.creditwise.app.data.model.Direction;
import com.creditwise.app.data.model.ParseResult;
import com.creditwise.app.data.model.StatementHeader;
import com.creditwise.app.data.model.Transaction;
import com.creditwise.app.util.Money;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the plain-text layer of a "Выписка по платёжному счёту" from СберБанк Онлайн.
 *
 * Layout of one operation (2–3 logical lines):
 * <pre>
 *   DD.MM.YYYY HH:MM  &lt;категория&gt;  &lt;сумма&gt;  &lt;остаток&gt;
 *   DD.MM.YYYY &lt;код авторизации 6 цифр&gt; &lt;описание&gt;. Операция по (карте|счету) ****XXXX
 *   [В сумму операции включена комиссия X руб.]
 * </pre>
 * A leading "+" on the amount marks a credit; its absence marks a debit.
 */
public final class SberStatementParser {

    private static final DateTimeFormatter D = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private static final Pattern HEAD = Pattern.compile(
            "^(\\d{2}\\.\\d{2}\\.\\d{4})\\s+(\\d{2}):(\\d{2})\\s+(.+)$");
    private static final Pattern DETAIL = Pattern.compile(
            "^(\\d{2}\\.\\d{2}\\.\\d{4})\\s+(\\d{6})\\s+(.*)$");
    private static final Pattern MONEY = Pattern.compile(
            "([+]?)(\\d{1,3}(?:[\\s\\u00A0\\u202F]\\d{3})*|\\d+)[.,](\\d{2})");
    private static final Pattern PERIOD = Pattern.compile(
            "За период\\s+(\\d{2}\\.\\d{2}\\.\\d{4})\\s*[\\u2014\\u2013-]\\s*(\\d{2}\\.\\d{2}\\.\\d{4})");
    private static final Pattern BALANCE_LINE = Pattern.compile(
            "Остаток на \\d{2}\\.\\d{2}\\.\\d{4}\\s+([\\d\\s\\u00A0\\u202F.,]+\\d{2})");
    private static final Pattern REFILL = Pattern.compile("Пополнение\\s+([\\d\\s\\u00A0\\u202F.,]+\\d{2})");
    private static final Pattern WITHDRAW = Pattern.compile("Списание\\s+([\\d\\s\\u00A0\\u202F.,]+\\d{2})");
    private static final Pattern ACCOUNT = Pattern.compile("Номер сч[её]та\\s+([\\d\\s]{15,30})");
    private static final Pattern COUNTERPARTY = Pattern.compile(
            "([А-ЯЁ])\\.\\s*([А-ЯЁ][а-яё]+(?:\\s+[А-ЯЁ][а-яё]+){0,2})");
    private static final Pattern FEE = Pattern.compile(
            "комисси[яю]\\s+([\\d\\s\\u00A0\\u202F.,]+\\d{2})\\s*руб");

    private static final String[] NOISE_PREFIXES = {
            "Выписка по платёжному счёту", "Страница ", "Продолжение на следующей",
            "ДАТА ОПЕРАЦИИ", "Дата обработки", "и код авторизации", "КАТЕГОРИЯ",
            "Описание операции", "СУММА В ВАЛЮТЕ", "Сумма в валюте", "операции",
            "ОСТАТОК СРЕДСТВ", "В валюте счёта", "Расшифровка операций",
            "ИТОГО ПО ОПЕРАЦИЯМ", "Заказано в СберБанк", "www.sberbank.ru"
    };

    private SberStatementParser() {}

    public static ParseResult parse(String text) {
        ParseResult result = new ParseResult();
        if (text == null || text.isEmpty()) {
            result.warnings.add("Пустой файл");
            return result;
        }
        String[] lines = text.replace("\r", "").split("\n");

        result.header = parseHeader(text, lines);
        parseTransactions(lines, result);
        reconcile(result);
        return result;
    }

    // ---------------------------------------------------------------- header

    private static StatementHeader parseHeader(String text, String[] lines) {
        StatementHeader h = new StatementHeader();

        Matcher p = PERIOD.matcher(text);
        if (p.find()) {
            h.periodStart = safeDate(p.group(1));
            h.periodEnd = safeDate(p.group(2));
        }

        List<Double> balances = new ArrayList<>();
        Matcher b = BALANCE_LINE.matcher(text);
        while (b.find()) balances.add(Money.parse(b.group(1)));
        if (!balances.isEmpty()) {
            h.openingBalance = balances.get(0);
            h.closingBalance = balances.get(balances.size() - 1);
        }

        Matcher r = REFILL.matcher(text);
        if (r.find()) h.totalCredit = Money.parse(r.group(1));
        Matcher w = WITHDRAW.matcher(text);
        if (w.find()) h.totalDebit = Money.parse(w.group(1));

        Matcher a = ACCOUNT.matcher(text);
        if (a.find()) h.accountNumber = a.group(1).replaceAll("\\s", "");

        for (int i = 0; i < lines.length; i++) {
            if (lines[i].contains("Владелец сч")) {
                for (int j = i + 1; j < Math.min(i + 4, lines.length); j++) {
                    String[] parts = lines[j].trim().split("\\s+");
                    if (parts.length >= 2 && parts[0].matches("[А-ЯЁ][а-яё\\-]+")) {
                        h.ownerLastName = parts[0];
                        h.ownerFirstName = parts[1];
                        if (parts.length >= 3) h.ownerMiddleName = parts[2];
                        break;
                    }
                }
                break;
            }
        }
        return h;
    }

    // ------------------------------------------------------------ transactions

    private static void parseTransactions(String[] lines, ParseResult result) {
        Transaction current = null;
        boolean inDescription = false;

        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty()) continue;

            Matcher head = HEAD.matcher(line);
            if (head.matches() && looksLikeHead(head.group(4))) {
                if (current != null) result.transactions.add(current);
                current = buildFromHead(head);
                inDescription = false;
                continue;
            }

            if (current == null) continue;

            Matcher detail = DETAIL.matcher(line);
            if (detail.matches()) {
                current.processingDate = safeDate(detail.group(1));
                current.authCode = detail.group(2);
                applyDescription(current, detail.group(3));
                inDescription = true;
                continue;
            }

            if (line.contains("комисс")) {
                Matcher fee = FEE.matcher(line);
                if (fee.find()) current.fee = Money.parse(fee.group(1));
                continue;
            }

            if (inDescription && !isNoise(line)) {
                applyDescription(current, current.rawDescription + " " + line);
            }
        }
        if (current != null) result.transactions.add(current);

        if (result.transactions.isEmpty()) {
            result.warnings.add("В файле не найдено ни одной операции");
        }
    }

    private static boolean looksLikeHead(String tail) {
        int matches = 0;
        Matcher m = MONEY.matcher(tail);
        while (m.find()) matches++;
        return matches >= 2;
    }

    private static Transaction buildFromHead(Matcher head) {
        Transaction t = new Transaction();
        LocalDate date = safeDate(head.group(1));
        LocalTime time = LocalTime.of(Integer.parseInt(head.group(2)), Integer.parseInt(head.group(3)));
        if (date != null) t.operationDateTime = LocalDateTime.of(date, time);

        String tail = head.group(4);
        List<int[]> spans = new ArrayList<>();
        List<double[]> values = new ArrayList<>(); // {amount, isCredit}
        Matcher m = MONEY.matcher(tail);
        while (m.find()) {
            spans.add(new int[]{m.start(), m.end()});
            double amount = Money.parse(m.group(2) + "." + m.group(3));
            values.add(new double[]{amount, "+".equals(m.group(1)) ? 1 : 0});
        }
        int n = values.size();
        double[] amountEntry = values.get(n - 2);
        t.amount = amountEntry[0];
        t.direction = amountEntry[1] == 1 ? Direction.CREDIT : Direction.DEBIT;
        t.balanceAfter = values.get(n - 1)[0];
        t.bankCategory = tail.substring(0, spans.get(n - 2)[0]).trim();
        return t;
    }

    private static void applyDescription(Transaction t, String description) {
        String d = description
                .replaceAll("Операция по (карте|счету|счёту).*$", "")
                .replaceAll("\\*{2,4}\\s?\\d{4}", "")
                .replaceAll("\\s+", " ")
                .trim();
        t.rawDescription = d;

        Matcher c = COUNTERPARTY.matcher(d);
        if (d.contains("Перевод") && c.find()) {
            t.counterpartyInitial = c.group(1) + ".";
            t.counterpartyName = c.group(2).trim();
        }
        for (String bank : new String[]{"T-Bank", "T-Банк", "Alfa-Bank", "Альфа-банк",
                "Alfa", "Yandex", "YandexBank", "Sberbank", "Райффайзен", "ВТБ", "Ozon Банк"}) {
            if (d.contains(bank)) {
                t.counterpartyName = bank;
                break;
            }
        }
    }

    private static boolean isNoise(String line) {
        for (String prefix : NOISE_PREFIXES) {
            if (line.startsWith(prefix)) return true;
        }
        return line.startsWith("*") || line.equals("900");
    }

    // ---------------------------------------------------------- reconciliation

    private static void reconcile(ParseResult result) {
        List<Transaction> tx = result.transactions;
        if (tx.size() < 3) return;
        int mismatches = 0;
        for (int i = 0; i < tx.size() - 1; i++) {
            double expected = tx.get(i + 1).balanceAfter + tx.get(i).signedAmount();
            if (Math.abs(expected - tx.get(i).balanceAfter) > 1.0) mismatches++;
        }
        double ratio = mismatches / (double) tx.size();
        if (ratio > 0.10) {
            result.reconciled = false;
            result.warnings.add("Остатки в выписке сходятся не полностью (расхождений: "
                    + mismatches + " из " + tx.size() + ").");
        }
    }

    private static LocalDate safeDate(String s) {
        try {
            return LocalDate.parse(s.trim(), D);
        } catch (Exception e) {
            return null;
        }
    }
}
