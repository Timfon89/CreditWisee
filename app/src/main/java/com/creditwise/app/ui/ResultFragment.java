package com.creditwise.app.ui;

import android.os.Bundle;
import android.view.HapticFeedbackConstants;
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
import com.creditwise.app.data.model.CreditCase;
import com.creditwise.app.data.model.ExpenseCategory;
import com.creditwise.app.data.model.Recommendation;
import com.creditwise.app.data.model.ScoreBreakdown;
import com.creditwise.app.data.model.StatementAnalysis;
import com.creditwise.app.data.model.TrustworthinessScore;
import com.creditwise.app.databinding.FragmentResultBinding;
import com.creditwise.app.databinding.ItemCategoryRowBinding;
import com.creditwise.app.databinding.ItemRecommendationBinding;
import com.creditwise.app.databinding.ItemTrustFactorBinding;
import com.creditwise.app.databinding.SectionExtendedBinding;
import com.creditwise.app.domain.ChallengeEngine;
import com.creditwise.app.domain.OptimizationEngine;
import com.creditwise.app.util.Money;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ResultFragment extends BaseFragment {

    /** Colors for the three base factors, in their fixed calculation order (balance/regularity/essentials). */
    private static final int[] FACTOR_SLOT_RES = {
            R.color.chart_slot_1, R.color.chart_slot_3, R.color.chart_slot_7
    };

    private FragmentResultBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentResultBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        binding.gauge.setRange(TrustworthinessScore.MAX);

        binding.btnRestart.setOnClickListener(v -> {
            viewModel().reset(requireContext());
            NavHostFragment.findNavController(this).navigate(R.id.action_result_to_home);
        });
        binding.btnSaveHome.setOnClickListener(v -> saveAndGoHome());

        Assessment data = viewModel().data;
        if (data.trust == null) {
            binding.tvBand.setText("—");
            binding.tvDelta.setText("Недостаточно данных. Вернитесь назад и загрузите выписку.");
            return;
        }

        renderHeader(data.trust);
        renderAccordion(data);
    }

    // ------------------------------------------------------------------ header

    private void renderHeader(TrustworthinessScore t) {
        binding.gauge.post(() -> binding.gauge.setScore(t.total));
        binding.tvBand.setText(getString(R.string.trust_band, t.band));

        String email = new LocalAuthStore(requireContext()).currentEmail();
        List<CreditCase> previous = new CreditCaseStore(requireContext()).loadCases(email);
        if (previous.isEmpty()) {
            binding.tvDelta.setText(R.string.result_delta_first);
        } else {
            int delta = t.total - previous.get(0).trustTotal;
            binding.tvDelta.setText(delta >= 0
                    ? getString(R.string.result_delta_up, delta)
                    : getString(R.string.result_delta_down, delta));
        }
    }

    // ---------------------------------------------------------------- accordion

    private void renderAccordion(Assessment data) {
        binding.accordionContainer.removeAllViews();
        ViewGroup transitionRoot = (ViewGroup) binding.getRoot();
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        TrustworthinessScore t = data.trust;

        // 1. Finance behaviour (from the statement) — open by default
        AccordionSection finance = AccordionSection.inflate(binding.accordionContainer, transitionRoot,
                getString(R.string.section_finance_title), "+" + t.base, true);
        finance.addBody(financeDonut(t));
        for (int i = 0; i < t.factors.size(); i++) {
            TrustworthinessScore.Factor f = t.factors.get(i);
            int color = ContextCompat.getColor(requireContext(), FACTOR_SLOT_RES[i % FACTOR_SLOT_RES.length]);
            ItemTrustFactorBinding row = ItemTrustFactorBinding.inflate(inflater, (ViewGroup) finance.body(), false);
            row.tvName.setText(f.name);
            row.tvDetail.setText(f.detail);
            row.tvPoints.setText("+" + f.points);
            row.bar.setMax(1000);
            row.bar.setProgressCompat((int) Math.round(f.value * 1000d), true);
            row.bar.setIndicatorColor(color);
            row.tagContainer.addView(SourceTag.create(requireContext(), SourceTag.Type.BANK));
            finance.addBody(row.getRoot());
        }

        // 1b. Risk factors found in the statement itself (e.g. МФО/microloan payments)
        if (t.riskPenalty != 0) {
            AccordionSection risk = AccordionSection.inflate(binding.accordionContainer, transitionRoot,
                    getString(R.string.section_risk_title), signed(t.riskPenalty), false);
            for (String reason : t.riskReasons) risk.addBody(textRow(reason));
            risk.addBody(tagRow(SourceTag.Type.BANK));
        }

        // 2. Site rating
        AccordionSection site = AccordionSection.inflate(binding.accordionContainer, transitionRoot,
                getString(R.string.section_site_title), signed(t.externalBuff), false);
        site.addBody(textRow(getString(R.string.section_site_body, TrustworthinessScore.EXTERNAL_CAP)));
        site.addBody(tagRow(SourceTag.Type.SITE));

        // 3. Telegram — persisted from "Задания", not tied to this session
        AccordionSection tg = AccordionSection.inflate(binding.accordionContainer, transitionRoot,
                "Telegram", signed(t.adjustment), false);
        for (TrustworthinessScore.Adjustment adj : t.adjustments) {
            tg.addBody(textRow(signed(adj.points) + "  " + adj.label));
        }
        if (t.adjustments.isEmpty() && !t.telegramNote.isEmpty()) {
            tg.addBody(textRow(t.telegramNote));
        }
        tg.addBody(tagRow(SourceTag.Type.TELEGRAM));

        // 4. Ongoing challenges — «Экономия» (quarterly) + «Регулярность» (weekly), combined
        AccordionSection challenges = AccordionSection.inflate(binding.accordionContainer, transitionRoot,
                getString(R.string.section_challenges_title), signed(t.challengesBuff), false);
        if (data.challenges != null) {
            for (String reason : ChallengeEngine.savingsReasons(data.challenges)) challenges.addBody(textRow(reason));
            for (String reason : ChallengeEngine.regularityReasons(data.challenges)) challenges.addBody(textRow(reason));
        }
        challenges.addBody(tagRow(SourceTag.Type.APP));

        // 4b. Financial-habits questionnaire (self-reported, optional)
        AccordionSection habits = AccordionSection.inflate(binding.accordionContainer, transitionRoot,
                getString(R.string.section_habits_title), signed(t.habitsBuff), false);
        if (t.habitsBuff == 0 && (data.habitsReasons == null || data.habitsReasons.isEmpty())) {
            habits.addBody(textRow(getString(R.string.section_habits_body_none)));
        } else if (data.habitsReasons != null) {
            for (String reason : data.habitsReasons) habits.addBody(textRow(reason));
        }
        habits.addBody(tagRow(SourceTag.Type.APP));

        // 5. Extended 0–999 model
        if (data.score != null) {
            AccordionSection extended = AccordionSection.inflate(binding.accordionContainer, transitionRoot,
                    getString(R.string.trust_extended_title), String.valueOf(data.score.finalScore), false);
            SectionExtendedBinding ext = SectionExtendedBinding.inflate(inflater, (ViewGroup) extended.body(), false);
            renderExtended(ext, data);
            extended.addBody(ext.getRoot());
        }
    }

    private void renderExtended(SectionExtendedBinding ext, Assessment data) {
        ScoreBreakdown s = data.score;
        ext.tvRating999.setText(String.valueOf(s.finalScore));
        ext.tvExternal.setText(String.valueOf(s.externalScore));
        ext.tvBehavior.setText(String.valueOf(s.behaviorScore));

        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (ScoreBreakdown.Factor f : s.factors) {
            ItemCategoryRowBinding row = ItemCategoryRowBinding.inflate(inflater, ext.factorContainer, false);
            row.tvName.setText(f.name);
            row.tvValue.setText(Money.percent(f.value));
            row.bar.setMax(1000);
            row.bar.setProgressCompat((int) Math.round(f.value * 1000d), true);
            ext.factorContainer.addView(row.getRoot());
        }
        if (!s.penaltyReasons.isEmpty()) {
            ItemCategoryRowBinding row = ItemCategoryRowBinding.inflate(inflater, ext.factorContainer, false);
            row.tvName.setText("Штрафы: " + android.text.TextUtils.join(", ", s.penaltyReasons));
            row.tvValue.setText("");
            row.bar.setVisibility(View.GONE);
            ext.factorContainer.addView(row.getRoot());
        }

        OptimizationEngine.Result opt = data.optimization;
        if (opt != null) {
            for (Recommendation r : opt.items) {
                ItemRecommendationBinding row = ItemRecommendationBinding.inflate(inflater, ext.recommendationContainer, false);
                row.tvTitle.setText(r.title);
                row.tvBody.setText(r.body);
                row.chipSaving.setText("−" + Money.format(r.monthlySaving) + "/мес");
                row.chipDelta.setText(getString(R.string.result_delta_score, r.deltaScore));
                ext.recommendationContainer.addView(row.getRoot());
            }
            if (!opt.items.isEmpty()) {
                ext.tvApplyAll.setText(getString(R.string.result_apply_all) + ":\n" + opt.combinedSummary);
                ext.cardApplyAll.setVisibility(View.VISIBLE);
            }
        }
    }

    // ---------------------------------------------------------------- saving

    private void saveAndGoHome() {
        Assessment data = viewModel().data;
        CreditCase c = new CreditCase();
        c.trustTotal = data.trust.total;
        c.trustBand = data.trust.band;
        c.baseScore = data.trust.base;
        c.externalBuff = data.trust.externalBuff;
        c.challengesBuff = data.trust.challengesBuff;
        if (data.challenges != null) {
            c.challengesReasons.addAll(ChallengeEngine.savingsReasons(data.challenges));
            c.challengesReasons.addAll(ChallengeEngine.regularityReasons(data.challenges));
        }
        c.habitsBuff = data.trust.habitsBuff;
        if (data.habitsReasons != null) c.habitsReasons.addAll(data.habitsReasons);
        c.riskPenalty = data.trust.riskPenalty;
        c.riskReasons.addAll(data.trust.riskReasons);
        c.telegramAdjustment = data.trust.adjustment;
        for (TrustworthinessScore.Factor f : data.trust.factors) {
            CreditCase.FactorSnapshot fs = new CreditCase.FactorSnapshot();
            fs.name = f.name;
            fs.detail = f.detail;
            fs.value = f.value;
            fs.points = f.points;
            c.baseFactors.add(fs);
        }
        for (TrustworthinessScore.Adjustment adj : data.trust.adjustments) {
            c.telegramReasons.add(signed(adj.points) + "  " + adj.label);
        }
        Map.Entry<ExpenseCategory, Double> top = topDiscretionary(data.analysis);
        if (top != null) {
            c.topDiscretionaryCategory = top.getKey().name();
            c.topDiscretionaryMonthlyAmount = top.getValue();
        }
        if (data.analysis != null) {
            c.avgIncome = data.analysis.avgIncome;
            c.avgExpense = data.analysis.avgExpense;
            for (com.creditwise.app.data.model.MonthlyAggregate m : data.analysis.months) {
                CreditCase.MonthPoint mp = new CreditCase.MonthPoint();
                mp.label = TrendChartView.shortRuMonth(m.month);
                mp.income = m.incomeEffective;
                mp.expense = m.expenseTotal;
                c.monthlySeries.add(mp);
            }
        }

        String email = new LocalAuthStore(requireContext()).currentEmail();
        new CreditCaseStore(requireContext()).saveCase(email, c);

        binding.btnSaveHome.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
        viewModel().reset(requireContext());
        NavHostFragment.findNavController(this).navigate(R.id.action_result_to_home);
    }

    @Nullable
    private static Map.Entry<ExpenseCategory, Double> topDiscretionary(@Nullable StatementAnalysis a) {
        if (a == null) return null;
        java.util.EnumSet<ExpenseCategory> excluded = java.util.EnumSet.of(
                ExpenseCategory.GROCERIES, ExpenseCategory.UTILITIES, ExpenseCategory.HEALTH,
                ExpenseCategory.PUBLIC_TRANSPORT, ExpenseCategory.EDUCATION,
                ExpenseCategory.CASH, ExpenseCategory.TRANSFERS_OUT, ExpenseCategory.MFO_PAYMENT);
        Map.Entry<ExpenseCategory, Double> best = null;
        for (Map.Entry<ExpenseCategory, Double> e : a.categoryMonthlyAvg.entrySet()) {
            if (excluded.contains(e.getKey())) continue;
            if (best == null || e.getValue() > best.getValue()) best = e;
        }
        return best;
    }

    // ------------------------------------------------------------------ small view helpers

    /** Donut breaking the base score down into its three weighted factors. */
    private DonutChartView financeDonut(TrustworthinessScore t) {
        List<DonutChartView.Segment> segments = new ArrayList<>();
        for (int i = 0; i < t.factors.size(); i++) {
            TrustworthinessScore.Factor f = t.factors.get(i);
            int color = ContextCompat.getColor(requireContext(), FACTOR_SLOT_RES[i % FACTOR_SLOT_RES.length]);
            segments.add(new DonutChartView.Segment(f.name, Math.max(0, f.points), color));
        }

        DonutChartView donut = new DonutChartView(requireContext());
        int size = dp(150);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
        lp.gravity = android.view.Gravity.CENTER_HORIZONTAL;
        lp.bottomMargin = dp(10);
        donut.setLayoutParams(lp);
        donut.setSegments(segments);
        donut.setCenterText("+" + t.base, "из " + TrustworthinessScore.MAX);
        return donut;
    }

    private TextView textRow(String text) {
        TextView tv = new TextView(requireContext());
        tv.setText(text);
        tv.setTextSize(14f);
        tv.setLineSpacing(0, 1.15f);
        tv.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.darker_gray));
        int pad = dp(4);
        tv.setPadding(0, pad, 0, pad);
        return tv;
    }

    private LinearLayout tagRow(SourceTag.Type type) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(6);
        row.setLayoutParams(lp);
        row.addView(SourceTag.create(requireContext(), type));
        return row;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private static String signed(int v) {
        return (v >= 0 ? "+" : "") + v;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
