package com.creditwise.app.domain;

import com.creditwise.app.data.model.TelegramFlag;
import com.creditwise.app.data.model.TelegramScanResult;
import com.creditwise.app.data.model.TrustworthinessScore;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns one Telegram export scan into a signed adjustment, capped at
 * ±{@link TrustworthinessScore#TELEGRAM_CAP}. Evaluated once, right when the user reviews their
 * scan in "Задания" (see {@code TelegramFragment}) — the result is persisted
 * ({@code CreditCaseStore#saveTelegramAdjustment}) and replayed into every future score
 * computation, so the export doesn't need to be re-scanned each time.
 *
 * Flag-based only — counts of pre-classified categories (work mentions, budgeting talk, overdue
 * debt, stress about money). Never a free-form reading of writing style or vocabulary: that would
 * be judging someone by how they write rather than what's verifiably true about their finances.
 */
public final class TelegramScoring {

    private TelegramScoring() {}

    public static class Result {
        public int delta;
        public String note = "";
        public boolean applied;
        public final List<TrustworthinessScore.Adjustment> reasons = new ArrayList<>();
    }

    public static Result evaluate(TelegramScanResult tg, boolean consent) {
        Result r = new Result();
        if (!consent) {
            r.note = "Согласие на учёт Telegram не дано — анализ переписок не влияет на балл.";
            return r;
        }
        if (tg == null || !tg.ownMessagesIdentified) {
            r.note = "Не удалось выделить ваши собственные сообщения — Telegram не влияет на балл.";
            return r;
        }

        int work = tg.raw(TelegramFlag.Category.WORK_ACTIVITY);
        int fin = tg.raw(TelegramFlag.Category.FINANCIAL_LITERACY);
        int overdue = tg.raw(TelegramFlag.Category.OVERDUE) + tg.raw(TelegramFlag.Category.CREDIT_MFO);
        int stress = tg.raw(TelegramFlag.Category.STRESS) + tg.raw(TelegramFlag.Category.LOANS_PEOPLE);

        int delta = 0;
        if (work >= 8) {
            delta += 6;
            r.reasons.add(new TrustworthinessScore.Adjustment(
                    "Регулярно пишете о работе и проектах — признак стабильной занятости", +6));
        } else if (work >= 3) {
            delta += 3;
            r.reasons.add(new TrustworthinessScore.Adjustment(
                    "Периодически упоминаете работу и проекты", +3));
        }
        if (fin >= 4) {
            delta += 4;
            r.reasons.add(new TrustworthinessScore.Adjustment(
                    "Пишете о планировании бюджета и накоплениях", +4));
        } else if (fin >= 1) {
            delta += 2;
            r.reasons.add(new TrustworthinessScore.Adjustment(
                    "Встречаются упоминания финансового планирования", +2));
        }
        if (overdue >= 1) {
            delta -= 6;
            r.reasons.add(new TrustworthinessScore.Adjustment(
                    "Упоминания просрочек, кредитов и МФО", -6));
        }
        if (stress >= 2) {
            delta -= 3;
            r.reasons.add(new TrustworthinessScore.Adjustment(
                    "Частые упоминания нехватки денег и займов у людей", -3));
        }

        delta = Math.max(-TrustworthinessScore.TELEGRAM_CAP,
                Math.min(TrustworthinessScore.TELEGRAM_CAP, delta));
        r.delta = delta;
        r.applied = true;
        r.note = "Проанализировано ваших сообщений: " + tg.ownMessages
                + ". Итоговая корректировка ограничена ±" + TrustworthinessScore.TELEGRAM_CAP + ".";
        return r;
    }
}
