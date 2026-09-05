package com.creditwise.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.creditwise.app.data.model.HabitsQuestionnaire;
import com.creditwise.app.domain.HabitsScorer;

import org.junit.Test;

public class HabitsScorerTest {

    @Test
    public void allBestAnswersGiveMaximumPositiveScore() {
        int n = HabitsQuestionnaire.QUESTIONS.size();
        int[] selections = new int[n];
        for (int i = 0; i < n; i++) selections[i] = 0; // index 0 is always the best option in this bank
        int score = HabitsScorer.score(selections);
        assertTrue("best answers should score positively", score > 0);
    }

    @Test
    public void allWorstAnswersGiveNegativeScore() {
        int n = HabitsQuestionnaire.QUESTIONS.size();
        int[] selections = new int[n];
        for (int i = 0; i < n; i++) {
            selections[i] = HabitsQuestionnaire.QUESTIONS.get(i).options.length - 1;
        }
        int score = HabitsScorer.score(selections);
        assertTrue("worst answers should score negatively", score < 0);
    }

    @Test
    public void nullOrMissingSelectionsScoreZero() {
        assertEquals(0, HabitsScorer.score(null));
        assertEquals(0, HabitsScorer.score(new int[0]));
    }

    @Test
    public void reasonsSkipZeroPointAnswers() {
        int n = HabitsQuestionnaire.QUESTIONS.size();
        int[] selections = new int[n];
        for (int i = 0; i < n; i++) selections[i] = 0;
        assertEquals(n, HabitsScorer.reasons(selections).size()
                + countZeroPointBestAnswers());
    }

    private int countZeroPointBestAnswers() {
        int count = 0;
        for (HabitsQuestionnaire.Question q : HabitsQuestionnaire.QUESTIONS) {
            if (q.points[0] == 0) count++;
        }
        return count;
    }
}
