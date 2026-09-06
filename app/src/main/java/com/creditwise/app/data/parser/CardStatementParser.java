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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses a second statement layout — a "Справка о движении средств" (funds-movement
 * certificate) rather than a running-balance account statement. Deliberately generic: this
 * class doesn't name the issuing bank anywhere, since the app must not imply partnership with
 * (or even identify) any specific institution beyond what's already disclosed elsewhere.
 *
 * The PDF renders each operation as a 6-column table row (op date+time, write-off date, amount
 * in operation currency, amount in card currency, description, card's last 4 digits), but a
 * cell's own line-wrap (e.g. "date" over "time" in one cell) means the text layer's positional
 * extraction interleaves columns by visual row rather than by logical field. In practice one
 * record comes out as:
 * <pre>
 *   DD.MM.YYYY DD.MM.YYYY  ±SUM ₽ ±SUM ₽  description-word(s)...  [NNNN|—]
 *   HH:MM HH:MM  [more description word(s)...]
 *   [description continuation line(s)...]
 * </pre>
 * There's no per-row balance column — only a single available balance as of the report date for
 * the whole account, so every {@code balanceAfter} is reconstructed by walking the (newest-first)
 * list backwards from that one known point. The last-4-digits card marker (or "—"/"-" for a
 * card-less fee) is only ever used to know where a description ends, never stored.
 */
public final class CardStatementParser {

    private static final Pattern RECORD_HEAD = Pattern.compile(
            "^(\\d{2}\\.\\d{2}\\.\\d{4})\\s+(\\d{2}\\.\\d{2}\\.\\d{4})\\s+"
                    + "([+\\-\\u2212][\\d\\s\\u00A0\\u202F]+[.,]\\d{2})\\s*\\u20BD\\s+"
                    + "([+\\-\\u2212][\\d\\s\\u00A0\\u202F]+[.,]\\d{2})\\s*\\u20BD\\s*(.*)$");
    private static final Pattern RECORD_TIME = Pattern.compile(
            "^(\\d{2}:\\d{2})\\s+(\\d{2}:\\d{2})\\s*(.*)$");
    private static final Pattern TRAILING_CARD_MARKER = Pattern.compile("^(.*?)\\s*(?:\\d{4}|\\u2014|-)$");

    private static final Pattern PERIOD = Pattern.compile(
            "за период с\\s+(\\d{2}\\.\\d{2}\\.\\d{4})\\s+по\\s+(\\d{2}\\.\\d{2}\\.\\d{4})");
    private static final Pattern CLOSING_BALANCE = Pattern.compile(
            "остатка на \\d{2}\\.\\d{2}\\.\\d{4}:\\s*([\\d\\s\\u00A0\\u202F.,]+)\\s*\\u20BD");
    private static final Pattern ACCOUNT = Pattern.compile("Номер лицевого счета:?\\s*(\\d+)");
    private static final Pattern TOTAL_IN = Pattern.compile("Пополнения:\\s*([\\d\\s\\u00A0\\u202F.,]+)\\s*\\u20BD");
    private static final Pattern TOTAL_OUT = Pattern.compile("Расходы:\\s*([\\d\\s\\u00A0\\u202F.,]+)\\s*\\u20BD");
    private static final DateTimeFormatter D = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private CardStatementParser() {}

    /** True if the extracted text looks like this layout rather than the other supported one. */
    public static boolean matches(String text) {
        return text != null && (text.contains("Справка о движении средств")
                || (text.contains("Номер лицевого счета") && text.contains("доступного остатка")));
    }

    public static ParseResult parse(String text) {
        ParseResult result = new ParseResult();
        if (text == null || text.isEmpty()) {
            result.warnings.add("Пустой файл");
            return result;
        }
        String[] lines = text.replace("\r", "").split("\n");

        boolean hasClosingBalance = parseHeader(text, lines, result.header);
        parseTransactions(lines, result);

        if (hasClosingBalance) {
            reconstructBalances(result);
        } else {
            result.warnings.add("Не удалось найти итоговый остаток по счёту — показатели, "
                    + "связанные с остатком, могут быть неточными.");
        }
        return result;
    }

    // ---------------------------------------------------------------- header

    private static boolean parseHeader(String text, String[] lines, StatementHeader h) {
        Matcher p = PERIOD.matcher(text);
        if (p.find()) {
            h.periodStart = safeDate(p.group(1));
            h.periodEnd = safeDate(p.group(2));
        }

        boolean hasClosing = false;
        Matcher cb = CLOSING_BALANCE.matcher(text);
        if (cb.find()) {
            h.closingBalance = Money.parse(cb.group(1));
            hasClosing = true;
        }

        Matcher acc = ACCOUNT.matcher(text);
        if (acc.find()) h.accountNumber = acc.group(1);

        Matcher in = TOTAL_IN.matcher(text);
        if (in.find()) h.totalCredit = Money.parse(in.group(1));
        Matcher out = TOTAL_OUT.matcher(text);
        if (out.find()) h.totalDebit = Money.parse(out.group(1));

        // The owner's full name sits alone on the line right before "Адрес места жительства:".
        for (int i = 0; i < lines.length; i++) {
            if (!lines[i].trim().startsWith("Адрес места жительства")) continue;
            int k = i - 1;
            while (k >= 0 && lines[k].trim().isEmpty()) k--;
            if (k >= 0) {
                String[] parts = lines[k].trim().split("\\s+");
                if (parts.length >= 2 && parts[0].matches("[А-ЯЁ][а-яё\\-]+")) {
                    h.ownerLastName = parts[0];
                    h.ownerFirstName = parts[1];
                    if (parts.length >= 3) h.ownerMiddleName = parts[2];
                }
            }
            break;
        }
        return hasClosing;
    }

    // ------------------------------------------------------------ transactions

    private static void parseTransactions(String[] lines, ParseResult result) {
        int i = 0;
        while (i < lines.length) {
            Matcher head = RECORD_HEAD.matcher(lines[i].trim());
            if (!head.matches()) { i++; continue; }

            Transaction tx = new Transaction();
            LocalDate opDate = safeDate(head.group(1));
            tx.processingDate = safeDate(head.group(2));

            // the second amount ("в валюте карты") is the one that actually moved the balance
            double cardAmount = Money.parse(head.group(4));
            tx.direction = cardAmount < 0 ? Direction.DEBIT : Direction.CREDIT;
            tx.amount = Math.abs(cardAmount);

            StringBuilder desc = new StringBuilder(stripTrailingCardMarker(head.group(5)));

            int j = i + 1;
            LocalTime opTime = null;
            if (j < lines.length) {
                Matcher time = RECORD_TIME.matcher(lines[j].trim());
                if (time.matches()) {
                    opTime = safeTime(time.group(1));
                    // no card-marker stripping here: the marker column only ever lands on the
                    // head line (see class doc) — a trailing digit run here is real description
                    // content (a contract/phone number), not the card suffix.
                    appendDescription(desc, time.group(3));
                    j++;
                }
            }
            tx.operationDateTime = opDate == null ? null
                    : (opTime != null ? LocalDateTime.of(opDate, opTime) : opDate.atStartOfDay());

            while (j < lines.length) {
                String next = lines[j].trim();
                if (next.isEmpty()) { j++; continue; }
                if (RECORD_HEAD.matcher(next).matches() || next.startsWith("Дата и время")
                        || next.startsWith("Пополнения") || next.startsWith("Расходы")
                        || next.startsWith("С уважением") || next.startsWith("АО «ТБанк»")
                        || next.startsWith("БИК") || next.matches("\\d{1,3}")) {
                    break;
                }
                appendDescription(desc, next);
                j++;
            }
            tx.rawDescription = desc.toString().trim();
            tx.bankCategory = "";
            result.transactions.add(tx);
            i = j;
        }

        if (result.transactions.isEmpty()) {
            result.warnings.add("В файле не найдено ни одной операции");
        }
    }

    /** Drops a trailing last-4-digits card marker (or "—"/"-" for a card-less operation) from
     *  the end of a line, if present — it's positional noise, never stored on the transaction. */
    private static String stripTrailingCardMarker(String s) {
        if (s == null || s.isEmpty()) return "";
        Matcher m = TRAILING_CARD_MARKER.matcher(s.trim());
        return (m.matches() ? m.group(1) : s).trim();
    }

    private static void appendDescription(StringBuilder desc, String piece) {
        if (piece == null || piece.isEmpty()) return;
        if (desc.length() > 0) desc.append(' ');
        desc.append(piece);
    }

    /**
     * This layout has no per-operation balance column — only the final available balance as of
     * the report date. Since the operation list is complete for the declared period and ordered
     * newest-first, every balance-after value can be reconstructed exactly by walking backwards
     * from that one known point, undoing each operation in turn.
     */
    private static void reconstructBalances(ParseResult result) {
        if (result.transactions.isEmpty()) return;
        double running = result.header.closingBalance;
        for (Transaction t : result.transactions) {
            t.balanceAfter = running;
            running -= t.signedAmount();
        }
    }

    private static LocalDate safeDate(String s) {
        try {
            return LocalDate.parse(s.trim(), D);
        } catch (Exception e) {
            return null;
        }
    }

    private static LocalTime safeTime(String s) {
        try {
            String[] parts = s.split(":");
            return LocalTime.of(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
        } catch (Exception e) {
            return null;
        }
    }
}
