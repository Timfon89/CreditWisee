package com.creditwise.app.data.model;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class TelegramScanResult {
    public int messagesScanned;
    public int ownMessages;
    public boolean ownMessagesIdentified;

    /** Example phrases, capped per category, for display. */
    public final List<TelegramFlag> flags = new ArrayList<>();
    /** Every hit counted (uncapped) — used by the scoring adjustment. */
    public final Map<TelegramFlag.Category, Integer> rawCounts =
            new EnumMap<>(TelegramFlag.Category.class);
    public final List<String> warnings = new ArrayList<>();

    public int raw(TelegramFlag.Category c) {
        Integer v = rawCounts.get(c);
        return v == null ? 0 : v;
    }

    public Map<TelegramFlag.Category, Integer> countByCategory() {
        Map<TelegramFlag.Category, Integer> m = new EnumMap<>(TelegramFlag.Category.class);
        for (TelegramFlag f : flags) {
            m.merge(f.category, 1, Integer::sum);
        }
        return m;
    }

    public boolean hasAnySignal() {
        return !rawCounts.isEmpty();
    }

    /**
     * Removes a phrase the user marked as irrelevant (no real connection to credit/finance) and
     * un-counts it, so it stops contributing to the Telegram scoring adjustment.
     */
    public void removeFlag(TelegramFlag flag) {
        if (flags.remove(flag)) {
            Integer count = rawCounts.get(flag.category);
            if (count == null || count <= 1) {
                rawCounts.remove(flag.category);
            } else {
                rawCounts.put(flag.category, count - 1);
            }
        }
    }
}
