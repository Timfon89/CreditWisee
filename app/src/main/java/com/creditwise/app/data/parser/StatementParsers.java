package com.creditwise.app.data.parser;

import com.creditwise.app.data.model.ParseResult;

/** Detects which of the supported statement layouts the extracted PDF text matches and routes
 *  to the right parser. Add new formats here rather than special-casing call sites. */
public final class StatementParsers {

    private StatementParsers() {}

    public static ParseResult parse(String text) {
        if (CardStatementParser.matches(text)) {
            return CardStatementParser.parse(text);
        }
        return SberStatementParser.parse(text);
    }
}
