package com.creditwise.app.ui;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.navigation.fragment.NavHostFragment;

import com.creditwise.app.R;
import com.creditwise.app.data.local.CreditCaseStore;
import com.creditwise.app.data.local.LocalAuthStore;
import com.creditwise.app.data.model.ChallengeState;
import com.creditwise.app.data.model.CreditCase;
import com.creditwise.app.data.model.TrustworthinessScore;
import com.creditwise.app.databinding.FragmentQuestsBinding;
import com.creditwise.app.domain.ChallengeEngine;
import com.creditwise.app.domain.HabitsScorer;
import com.creditwise.app.util.Money;

import java.util.List;

/**
 * Two ongoing, automatic challenges — «Экономия» (quarterly, total spend vs a frozen baseline)
 * and «Регулярность» (weekly, confirmed income deposits) — plus the separate financial-habits
 * questionnaire. Both challenges update themselves whenever a fresh statement is analysed
 * (see {@link AssessmentViewModel#computeResults}); there's nothing to start or verify by hand
 * here, this screen only displays where things stand.
 */
public class QuestsFragment extends BaseFragment {

    private FragmentQuestsBinding binding;
    private LocalAuthStore authStore;
    private CreditCaseStore caseStore;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentQuestsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        authStore = new LocalAuthStore(requireContext());
        caseStore = new CreditCaseStore(requireContext());

        binding.tvHabitsBody.setText(getString(R.string.quests_habits_body, TrustworthinessScore.HABITS_CAP));
        binding.btnHabits.setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigate(R.id.action_quests_to_habits));
        binding.btnQuickUpdate.setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigate(R.id.action_quests_to_quick_update));
    }

    @Override
    public void onResume() {
        super.onResume();
        render();
    }

    private void render() {
        if (binding == null) return;
        String email = authStore.currentEmail();
        List<CreditCase> cases = caseStore.loadCases(email);
        ChallengeState state = caseStore.loadChallengeState(email);

        int combined = (int) Math.round(state.combinedPoints());
        int cap = cases.isEmpty() ? TrustworthinessScore.NEW_USER_CHALLENGES_CAP : TrustworthinessScore.CHALLENGES_CAP;
        binding.tvBonus.setText(getString(R.string.quests_bonus_points, combined, cap));

        boolean hasAnyData = !state.savingsHistory.isEmpty() || !state.regularityHistory.isEmpty();
        binding.tvEmpty.setVisibility(cases.isEmpty() && !hasAnyData ? View.VISIBLE : View.GONE);

        renderSavings(state);
        renderRegularity(state);
        renderHabits(email);
    }

    // ------------------------------------------------------------------ «Экономия»

    private void renderSavings(ChallengeState state) {
        int streak = trailingStreak(state.savingsHistory);
        binding.tvSavingsStreak.setText(getString(R.string.challenge_savings_streak, streak));
        binding.tvSavingsStreak.setTextColor(streak > 0
                ? ContextCompat.getColor(requireContext(), R.color.brand_emerald)
                : ContextCompat.getColor(requireContext(), android.R.color.darker_gray));

        binding.tvSavingsCopy.setText(state.baselineMonth.isEmpty()
                ? getString(R.string.challenge_savings_copy_waiting)
                : getString(R.string.challenge_savings_copy_progress, Money.format(state.baselineExpense)));

        renderMedalStrip(binding.savingsMedalStrip, state.savingsHistory);

        int pct = (int) Math.round(Math.min(1d, state.savingsPoints / ChallengeEngine.SAVINGS_MAX_POINTS) * 100);
        binding.savingsProgress.setProgress(pct);
        binding.tvSavingsProgressPct.setText(pct + "%");

        binding.tvSavingsNote.setText(R.string.challenge_savings_note);
    }

    // ------------------------------------------------------------------ «Регулярность»

    private void renderRegularity(ChallengeState state) {
        int streak = trailingStreak(state.regularityHistory);
        binding.tvRegularityStreak.setText(getString(R.string.challenge_regularity_streak, streak));
        binding.tvRegularityStreak.setTextColor(streak > 0
                ? ContextCompat.getColor(requireContext(), R.color.brand_navy_2)
                : ContextCompat.getColor(requireContext(), android.R.color.darker_gray));

        binding.tvRegularityCopy.setText(R.string.challenge_regularity_copy);
        renderMedalStrip(binding.regularityMedalStrip, state.regularityHistory);

        int pct = (int) Math.round(Math.min(1d, state.regularityPoints / ChallengeEngine.REGULARITY_MAX_POINTS) * 100);
        binding.regularityProgress.setProgress(pct);
        binding.tvRegularityProgressPct.setText(pct + "%");

        binding.tvRegularityNote.setText(R.string.challenge_regularity_note);
    }

    // ------------------------------------------------------------------ shared

    /** How many entries, counting back from the most recent, are HIT/BASE before the first MISS —
     *  a simple "current streak" figure for the 🔥 badge. */
    private static int trailingStreak(List<String> history) {
        int n = 0;
        for (int i = history.size() - 1; i >= 0; i--) {
            if (history.get(i).endsWith(":MISS")) break;
            n++;
        }
        return n;
    }

    private void renderMedalStrip(LinearLayout container, List<String> history) {
        container.removeAllViews();
        for (String entry : history) {
            container.addView(medalDot(entry));
        }
    }

    private View medalDot(String entry) {
        TextView tv = new TextView(requireContext());
        int size = dp(26);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
        lp.setMarginEnd(dp(6));
        tv.setLayoutParams(lp);
        tv.setGravity(Gravity.CENTER);
        tv.setTextSize(12f);

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        if (entry.endsWith(":HIT")) {
            tv.setText("✓");
            bg.setColor(ContextCompat.getColor(requireContext(), R.color.brand_emerald));
            tv.setTextColor(Color.WHITE);
        } else if (entry.endsWith(":MISS")) {
            tv.setText("✕");
            bg.setColor(ContextCompat.getColor(requireContext(), R.color.score_low_tint));
            tv.setTextColor(ContextCompat.getColor(requireContext(), R.color.score_low));
        } else { // ":BASE" — the reference point, not scored
            tv.setText("•");
            bg.setColor(ContextCompat.getColor(requireContext(), android.R.color.darker_gray));
            bg.setAlpha(60);
            tv.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.darker_gray));
        }
        tv.setBackground(bg);
        return tv;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private void renderHabits(String email) {
        int[] selections = caseStore.loadHabitsSelections(email);
        if (selections == null) {
            binding.tvHabitsResult.setVisibility(View.GONE);
            binding.btnHabits.setText(R.string.quests_habits_start);
        } else {
            int score = HabitsScorer.score(selections);
            int clamped = Math.max(-TrustworthinessScore.HABITS_CAP,
                    Math.min(TrustworthinessScore.HABITS_CAP, score));
            binding.tvHabitsResult.setVisibility(View.VISIBLE);
            binding.tvHabitsResult.setText(getString(R.string.quests_habits_result, clamped));
            binding.btnHabits.setText(R.string.quests_habits_retake);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
