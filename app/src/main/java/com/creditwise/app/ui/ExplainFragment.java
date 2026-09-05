package com.creditwise.app.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.creditwise.app.R;
import com.creditwise.app.data.local.CreditCaseStore;
import com.creditwise.app.data.local.LocalAuthStore;
import com.creditwise.app.data.model.CreditCase;
import com.creditwise.app.data.model.TrustworthinessScore;
import com.creditwise.app.databinding.FragmentExplainBinding;
import com.creditwise.app.databinding.ItemTrustFactorBinding;

import java.time.format.DateTimeFormatter;
import java.util.List;

/** Read-only replay of the latest saved assessment's score breakdown — no re-parsing. */
public class ExplainFragment extends BaseFragment {

    private static final DateTimeFormatter DF = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private FragmentExplainBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentExplainBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        binding.gauge.setRange(TrustworthinessScore.MAX);
    }

    @Override
    public void onResume() {
        super.onResume();
        render();
    }

    private void render() {
        if (binding == null) return;
        String email = new LocalAuthStore(requireContext()).currentEmail();
        List<CreditCase> cases = new CreditCaseStore(requireContext()).loadCases(email);

        if (cases.isEmpty()) {
            binding.tvEmpty.setVisibility(View.VISIBLE);
            binding.cardAccordion.setVisibility(View.GONE);
            binding.tvBand.setText("—");
            binding.tvDate.setText("");
            binding.gauge.post(() -> binding.gauge.setScore(0));
            return;
        }

        binding.tvEmpty.setVisibility(View.GONE);
        binding.cardAccordion.setVisibility(View.VISIBLE);
        CreditCase c = cases.get(0);

        binding.gauge.post(() -> binding.gauge.setScore(c.trustTotal));
        binding.tvBand.setText(getString(R.string.trust_band, c.trustBand));
        binding.tvDate.setText(getString(R.string.explain_date, c.createdAt.format(DF)));

        renderAccordion(c);
    }

    private void renderAccordion(CreditCase c) {
        binding.accordionContainer.removeAllViews();
        ViewGroup transitionRoot = (ViewGroup) binding.getRoot();
        LayoutInflater inflater = LayoutInflater.from(requireContext());

        AccordionSection finance = AccordionSection.inflate(binding.accordionContainer, transitionRoot,
                getString(R.string.section_finance_title), signed(c.baseScore), true);
        for (CreditCase.FactorSnapshot f : c.baseFactors) {
            ItemTrustFactorBinding row = ItemTrustFactorBinding.inflate(inflater, (ViewGroup) finance.body(), false);
            row.tvName.setText(f.name);
            row.tvDetail.setText(f.detail);
            row.tvPoints.setText("+" + f.points);
            row.bar.setMax(1000);
            row.bar.setProgressCompat((int) Math.round(f.value * 1000d), true);
            row.tagContainer.addView(SourceTag.create(requireContext(), SourceTag.Type.BANK));
            finance.addBody(row.getRoot());
        }

        if (c.riskPenalty != 0) {
            AccordionSection risk = AccordionSection.inflate(binding.accordionContainer, transitionRoot,
                    getString(R.string.section_risk_title), signed(c.riskPenalty), false);
            for (String reason : c.riskReasons) risk.addBody(textRow(reason));
            risk.addBody(tagRow(SourceTag.Type.BANK));
        }

        AccordionSection site = AccordionSection.inflate(binding.accordionContainer, transitionRoot,
                getString(R.string.section_site_title), signed(c.externalBuff), false);
        site.addBody(textRow(getString(R.string.section_site_body, TrustworthinessScore.EXTERNAL_CAP)));
        site.addBody(tagRow(SourceTag.Type.SITE));

        if (!c.telegramReasons.isEmpty() || c.telegramAdjustment != 0) {
            AccordionSection tg = AccordionSection.inflate(binding.accordionContainer, transitionRoot,
                    "Telegram", signed(c.telegramAdjustment), false);
            for (String reason : c.telegramReasons) tg.addBody(textRow(reason));
            tg.addBody(tagRow(SourceTag.Type.TELEGRAM));
        }

        AccordionSection quest = AccordionSection.inflate(binding.accordionContainer, transitionRoot,
                getString(R.string.section_quest_title), signed(c.questBuff), false);
        quest.addBody(textRow(c.questBuff > 0
                ? getString(R.string.section_quest_body_some, TrustworthinessScore.QUEST_CAP)
                : getString(R.string.section_quest_body_none, TrustworthinessScore.QUEST_CAP)));
        quest.addBody(tagRow(SourceTag.Type.APP));

        AccordionSection habits = AccordionSection.inflate(binding.accordionContainer, transitionRoot,
                getString(R.string.section_habits_title), signed(c.habitsBuff), false);
        if (c.habitsReasons.isEmpty()) {
            habits.addBody(textRow(getString(R.string.section_habits_body_none)));
        } else {
            for (String reason : c.habitsReasons) habits.addBody(textRow(reason));
        }
        habits.addBody(tagRow(SourceTag.Type.APP));

        if (!c.approvalReasons.isEmpty()) {
            AccordionSection approval = AccordionSection.inflate(binding.accordionContainer, transitionRoot,
                    getString(R.string.approval_title), c.approvalPercent + "%", false);
            for (String reason : c.approvalReasons) approval.addBody(textRow("•  " + reason));
        }
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
