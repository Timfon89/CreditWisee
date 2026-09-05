package com.creditwise.app.domain;

import com.creditwise.app.data.model.HabitsQuestionnaire;

import java.util.ArrayList;
import java.util.List;

/** Turns the financial-habits questionnaire answers into a score and plain-text reasons. */
public final class HabitsScorer {

    private HabitsScorer() {}

    public static int score(int[] selections) {
        if (selections == null) return 0;
        List<HabitsQuestionnaire.Question> qs = HabitsQuestionnaire.QUESTIONS;
        int sum = 0;
        for (int i = 0; i < qs.size() && i < selections.length; i++) {
            int sel = selections[i];
            HabitsQuestionnaire.Question q = qs.get(i);
            if (sel >= 0 && sel < q.points.length) sum += q.points[sel];
        }
        return sum;
    }

    public static List<String> reasons(int[] selections) {
        List<String> out = new ArrayList<>();
        if (selections == null) return out;
        List<HabitsQuestionnaire.Question> qs = HabitsQuestionnaire.QUESTIONS;
        for (int i = 0; i < qs.size() && i < selections.length; i++) {
            int sel = selections[i];
            HabitsQuestionnaire.Question q = qs.get(i);
            if (sel < 0 || sel >= q.options.length) continue;
            int points = q.points[sel];
            if (points == 0) continue;
            out.add((points > 0 ? "+" : "") + points + "  " + q.options[sel]);
        }
        return out;
    }
}
