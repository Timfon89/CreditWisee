package com.creditwise.app.data.model;

import java.util.ArrayList;
import java.util.List;

/**
 * A short, transparent self-assessment of financial habits (budgeting, debt attitude,
 * payment discipline) — deliberately NOT a personality/clinical-psychology test. Every
 * question is directly about money behaviour, phrased so the user always sees exactly
 * what is being asked and why it matters for the index.
 */
public final class HabitsQuestionnaire {

    public static final class Question {
        public final String id;
        public final String text;
        public final String[] options;
        public final int[] points; // same length as options, signed

        public Question(String id, String text, String[] options, int[] points) {
            this.id = id;
            this.text = text;
            this.options = options;
            this.points = points;
        }
    }

    public static final List<Question> QUESTIONS = build();

    private HabitsQuestionnaire() {}

    private static List<Question> build() {
        List<Question> list = new ArrayList<>();
        list.add(new Question("budget",
                "Как часто вы планируете бюджет на месяц?",
                new String[]{
                        "Веду учёт доходов и расходов регулярно",
                        "Иногда прикидываю в уме",
                        "Не планирую вообще"},
                new int[]{2, 0, -2}));

        list.add(new Question("cushion",
                "Есть ли у вас финансовая подушка на непредвиденные траты?",
                new String[]{
                        "Да, на 3 месяца расходов и больше",
                        "Есть немного, меньше месяца",
                        "Нет совсем"},
                new int[]{2, 0, -2}));

        list.add(new Question("borrow",
                "Как часто вы занимаете деньги на повседневные траты (не крупные покупки)?",
                new String[]{
                        "Почти никогда",
                        "Иногда, если не хватает до зарплаты",
                        "Регулярно, почти каждый месяц"},
                new int[]{2, -1, -2}));

        list.add(new Question("impulse",
                "Часто ли вы совершаете незапланированные крупные покупки?",
                new String[]{
                        "Редко — сначала обдумываю",
                        "Иногда поддаюсь порыву",
                        "Часто, потом жалею"},
                new int[]{2, 0, -2}));

        list.add(new Question("payments",
                "Как часто вы платите по счетам и обязательствам с задержкой?",
                new String[]{
                        "Всегда вовремя",
                        "Иногда забываю на пару дней",
                        "Регулярно затягиваю"},
                new int[]{2, -1, -2}));

        list.add(new Question("goals",
                "Есть ли у вас чёткий финансовый план или цель накоплений?",
                new String[]{
                        "Да, план есть, и я его придерживаюсь",
                        "Есть общее представление, без деталей",
                        "Не думаю об этом"},
                new int[]{2, 0, -1}));

        return list;
    }
}
