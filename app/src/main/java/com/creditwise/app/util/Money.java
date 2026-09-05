package com.creditwise.app.util;

import java.util.Locale;

/** Parsing and formatting of rouble amounts as they appear in Sber statements. */
public final class Money {

    private Money() {}

    /** Parses tokens like {@code "1 660,00"}, {@code "+63 000,00"}, {@code "63 000,00"}. */
    public static double parse(String raw) {
        if (raw == null) return 0d;
        StringBuilder sb = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == ',') {
                sb.append('.');
            } else if (c == '-' || c == '−') {
                sb.append('-');
            } else if (c >= '0' && c <= '9' || c == '.') {
                sb.append(c);
            }
            // everything else (spaces, NBSP U+00A0, narrow NBSP U+202F, '+', currency) is dropped
        }
        String s = sb.toString();
        if (s.isEmpty() || s.equals("-") || s.equals(".")) return 0d;
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return 0d;
        }
    }

    /** {@code 44700.0 -> "44 700 ₽"} */
    public static String format(double value) {
        long r = Math.round(value);
        boolean neg = r < 0;
        String digits = Long.toString(Math.abs(r));
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (int i = digits.length() - 1; i >= 0; i--) {
            sb.append(digits.charAt(i));
            if (++count % 3 == 0 && i != 0) sb.append(' ');
        }
        sb.reverse();
        return (neg ? "-" : "") + sb + " ₽";
    }

    public static String percent(double fraction) {
        return String.format(Locale.US, "%.0f%%", fraction * 100d);
    }

    public static String percent1(double fraction) {
        return String.format(Locale.US, "%.1f%%", fraction * 100d);
    }
}
