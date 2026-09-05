package com.creditwise.app.data.model;

import java.time.LocalDate;

/** One phrase found in the user's own Telegram posts/messages that carries a scoring signal. */
public class TelegramFlag {

    public enum Category {
        // positive / neutral signals
        WORK_ACTIVITY("Работа и проекты", true),
        FINANCIAL_LITERACY("Планирование финансов", true),
        // negative signals
        OVERDUE("Просрочки и задолженность", false),
        CREDIT_MFO("Кредиты и МФО", false),
        LOANS_PEOPLE("Займы у людей", false),
        STRESS("Финансовый стресс", false);

        private final String displayName;
        private final boolean positive;

        Category(String displayName, boolean positive) {
            this.displayName = displayName;
            this.positive = positive;
        }

        public String displayName() {
            return displayName;
        }

        public boolean isPositive() {
            return positive;
        }
    }

    public final Category category;
    public final String snippet;
    public final LocalDate date;
    public final String chatName;

    public TelegramFlag(Category category, String snippet, LocalDate date, String chatName) {
        this.category = category;
        this.snippet = snippet;
        this.date = date;
        this.chatName = chatName;
    }
}
