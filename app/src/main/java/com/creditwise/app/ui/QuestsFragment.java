package com.creditwise.app.ui;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import com.creditwise.app.R;
import com.creditwise.app.data.classify.TransactionClassifier;
import com.creditwise.app.data.local.CreditCaseStore;
import com.creditwise.app.data.local.LocalAuthStore;
import com.creditwise.app.data.model.CreditCase;
import com.creditwise.app.data.model.ExpenseCategory;
import com.creditwise.app.data.model.ParseResult;
import com.creditwise.app.data.model.SavingsQuest;
import com.creditwise.app.data.model.StatementAnalysis;
import com.creditwise.app.data.model.TrustworthinessScore;
import com.creditwise.app.data.parser.SberStatementParser;
import com.creditwise.app.databinding.FragmentQuestsBinding;
import com.creditwise.app.domain.AggregationEngine;
import com.creditwise.app.domain.HabitsScorer;
import com.creditwise.app.util.Money;
import com.creditwise.app.util.PdfTextExtractor;

import androidx.navigation.fragment.NavHostFragment;

import java.io.InputStream;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class QuestsFragment extends BaseFragment {

    private FragmentQuestsBinding binding;
    private LocalAuthStore authStore;
    private CreditCaseStore caseStore;
    private ActivityResultLauncher<String[]> picker;
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        picker = registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
            if (uri != null) verify(uri);
        });
    }

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
    }

    @Override
    public void onResume() {
        super.onResume();
        render();
    }

    private void render() {
        if (binding == null) return;
        String email = authStore.currentEmail();
        int bonus = caseStore.loadBonusPoints(email);
        binding.tvBonus.setText(getString(R.string.quests_bonus_points, bonus, TrustworthinessScore.QUEST_CAP));

        SavingsQuest active = caseStore.loadActiveQuest(email);
        List<CreditCase> cases = caseStore.loadCases(email);

        binding.tvEmpty.setVisibility(View.GONE);
        binding.cardSuggestion.setVisibility(View.GONE);
        binding.cardActive.setVisibility(View.GONE);

        if (active != null) {
            renderActive(email, active);
        } else if (!cases.isEmpty() && !cases.get(0).topDiscretionaryCategory.isEmpty()
                && cases.get(0).topDiscretionaryMonthlyAmount > 0) {
            renderSuggestion(email, cases.get(0));
        } else {
            binding.tvEmpty.setVisibility(View.VISIBLE);
        }

        renderHistory(email);
        renderHabits(email);
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

    private void renderHistory(String email) {
        List<String> history = caseStore.loadQuestHistory(email);
        binding.medalStrip.removeAllViews();
        binding.historyBlock.setVisibility(history.isEmpty() ? View.GONE : View.VISIBLE);
        for (String entry : history) {
            binding.medalStrip.addView(medalDot(entry.endsWith("SUCCESS")));
        }
    }

    private View medalDot(boolean success) {
        TextView tv = new TextView(requireContext());
        int size = dp(28);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
        lp.setMarginEnd(dp(8));
        tv.setLayoutParams(lp);
        tv.setGravity(Gravity.CENTER);
        tv.setTextSize(13f);
        tv.setText(success ? "✓" : "✕");
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        if (success) {
            bg.setColor(ContextCompat.getColor(requireContext(), R.color.brand_emerald));
            tv.setTextColor(Color.WHITE);
        } else {
            bg.setColor(ContextCompat.getColor(requireContext(), R.color.score_low_tint));
            tv.setTextColor(ContextCompat.getColor(requireContext(), R.color.score_low));
        }
        tv.setBackground(bg);
        return tv;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private void renderSuggestion(String email, CreditCase latest) {
        binding.cardSuggestion.setVisibility(View.VISIBLE);
        String categoryName = displayName(latest.topDiscretionaryCategory);
        binding.tvSuggestionTitle.setText(categoryName);
        binding.tvSuggestionBody.setText("Сейчас на «" + categoryName + "» уходит "
                + Money.format(latest.topDiscretionaryMonthlyAmount) + " в месяц. Задание: сократить траты "
                + "на этой категории минимум на " + SavingsQuest.TARGET_REDUCTION_PERCENT + "% за "
                + SavingsQuest.DURATION_DAYS + " дней. Через месяц загрузите новую выписку — если получится, "
                + "начислим +" + SavingsQuest.REWARD_POINTS + " бонусных баллов к индексу благонадёжности.");

        binding.btnStart.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
            SavingsQuest q = new SavingsQuest();
            q.category = latest.topDiscretionaryCategory;
            q.baselineMonthlyAmount = latest.topDiscretionaryMonthlyAmount;
            caseStore.saveQuest(email, q);
            render();
        });
    }

    private void renderActive(String email, SavingsQuest quest) {
        binding.cardActive.setVisibility(View.VISIBLE);
        String categoryName = displayName(quest.category);
        binding.tvActiveCategory.setText(categoryName);

        long daysLeft = quest.daysLeft();
        int elapsed = (int) Math.max(0, Math.min(SavingsQuest.DURATION_DAYS,
                SavingsQuest.DURATION_DAYS - daysLeft));
        double elapsedShare = elapsed / (double) Math.max(1, SavingsQuest.DURATION_DAYS);

        int ringColorRes;
        if (daysLeft <= 0) {
            ringColorRes = R.color.score_high; // time's up — ready to verify, a positive nudge
        } else if (elapsedShare >= 0.66) {
            ringColorRes = R.color.score_mid; // final third — getting close
        } else {
            ringColorRes = R.color.brand_emerald; // plenty of time left
        }
        int ringColor = ContextCompat.getColor(requireContext(), ringColorRes);

        binding.questRing.setSegments(
                java.util.Collections.singletonList(new DonutChartView.Segment("Прошло", elapsed, ringColor)),
                SavingsQuest.DURATION_DAYS);
        if (daysLeft > 0) {
            binding.questRing.setCenterCount((int) daysLeft, "");
        } else {
            binding.questRing.setCenterText("✓", "");
        }

        String daysText = daysLeft > 0
                ? getString(R.string.quests_days_left, daysLeft)
                : "Срок истёк — можно проверять";
        binding.tvActiveBody.setText("Цель: сократить траты на «" + categoryName + "» минимум на "
                + SavingsQuest.TARGET_REDUCTION_PERCENT + "% от " + Money.format(quest.baselineMonthlyAmount)
                + "/мес.\n" + daysText);

        binding.btnVerify.setOnClickListener(v ->
                picker.launch(new String[]{"application/pdf"}));
    }

    private void verify(Uri uri) {
        String email = authStore.currentEmail();
        SavingsQuest quest = caseStore.loadActiveQuest(email);
        if (quest == null) return;

        binding.progress.setVisibility(View.VISIBLE);
        binding.btnVerify.setEnabled(false);
        io.execute(() -> {
            try (InputStream in = requireContext().getContentResolver().openInputStream(uri)) {
                if (in == null) throw new IllegalStateException("Не удалось открыть файл");
                String text = PdfTextExtractor.extract(in);
                ParseResult parsed = SberStatementParser.parse(text);
                if (!parsed.isUsable()) throw new IllegalStateException("Не похоже на выписку СберБанка");
                new TransactionClassifier(parsed.header).classifyAll(parsed.transactions);
                StatementAnalysis fresh = new AggregationEngine().aggregate(parsed);

                ExpenseCategory category = ExpenseCategory.valueOf(quest.category);
                double newAmount = fresh.categoryAvg(category);
                double reductionPercent = quest.baselineMonthlyAmount <= 0 ? 0
                        : (quest.baselineMonthlyAmount - newAmount) / quest.baselineMonthlyAmount * 100d;
                boolean success = reductionPercent >= SavingsQuest.TARGET_REDUCTION_PERCENT;

                requireActivity().runOnUiThread(() -> onVerified(email, quest, success, reductionPercent));
            } catch (Exception e) {
                requireActivity().runOnUiThread(() -> {
                    binding.progress.setVisibility(View.GONE);
                    binding.btnVerify.setEnabled(true);
                    new AlertDialog.Builder(requireContext())
                            .setMessage(e.getMessage() == null ? "Не удалось прочитать файл" : e.getMessage())
                            .setPositiveButton(android.R.string.ok, null)
                            .show();
                });
            }
        });
    }

    private void onVerified(String email, SavingsQuest quest, boolean success, double reductionPercent) {
        binding.progress.setVisibility(View.GONE);
        binding.btnVerify.setEnabled(true);
        caseStore.clearQuest(email);
        caseStore.appendQuestHistory(email, java.time.YearMonth.now().toString(), success);
        binding.getRoot().performHapticFeedback(success
                ? HapticFeedbackConstants.CONFIRM : HapticFeedbackConstants.REJECT);

        String message;
        if (success) {
            caseStore.addBonusPoints(email, SavingsQuest.REWARD_POINTS);
            message = getString(R.string.quests_success, displayName(quest.category),
                    Money.percent1(reductionPercent / 100d), SavingsQuest.REWARD_POINTS);
        } else {
            message = getString(R.string.quests_failed, displayName(quest.category),
                    SavingsQuest.TARGET_REDUCTION_PERCENT);
        }

        new AlertDialog.Builder(requireContext())
                .setTitle(success ? "Задание выполнено" : "Задание не выполнено")
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, (d, w) -> render())
                .setCancelable(false)
                .show();
    }

    private static String displayName(String enumName) {
        try {
            return ExpenseCategory.valueOf(enumName).displayName();
        } catch (IllegalArgumentException e) {
            return enumName;
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        io.shutdownNow();
    }
}
