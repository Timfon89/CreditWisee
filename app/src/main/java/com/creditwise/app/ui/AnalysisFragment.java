package com.creditwise.app.ui;

import android.os.Bundle;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.navigation.fragment.NavHostFragment;

import com.creditwise.app.R;
import com.creditwise.app.data.model.Direction;
import com.creditwise.app.data.model.ExpenseCategory;
import com.creditwise.app.data.model.MonthlyAggregate;
import com.creditwise.app.data.model.ParseResult;
import com.creditwise.app.data.model.StatementAnalysis;
import com.creditwise.app.data.model.Transaction;
import com.creditwise.app.databinding.FragmentAnalysisBinding;
import com.creditwise.app.databinding.ItemCategoryRowBinding;
import com.creditwise.app.databinding.ItemDetailRowBinding;
import com.creditwise.app.util.Money;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class AnalysisFragment extends BaseFragment {

    private static final DateTimeFormatter DF = DateTimeFormatter.ofPattern("MM.yyyy");

    /** Categorical chart slots, in fixed hue order — see dataviz color-formula. */
    private static final int[] SLOT_COLOR_RES = {
            R.color.chart_slot_1, R.color.chart_slot_2, R.color.chart_slot_3,
            R.color.chart_slot_4, R.color.chart_slot_5
    };

    private FragmentAnalysisBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentAnalysisBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        StatementAnalysis a = viewModel().data.analysis;
        binding.btnNext.setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigate(R.id.action_analysis_to_telegram));

        if (a == null) {
            binding.tvPeriod.setText("—");
            return;
        }

        String period = (a.periodStart != null ? a.periodStart.format(DF) : "?")
                + " – " + (a.periodEnd != null ? a.periodEnd.format(DF) : "?");
        binding.tvPeriod.setText(period);
        binding.tvIncome.setText(Money.format(a.avgIncome));
        binding.tvExpense.setText(Money.format(a.avgExpense));
        binding.tvFixed.setText(Money.format(a.avgFixedExpense));
        binding.tvSavings.setText(Money.percent1(a.savingsRate));

        List<String> notes = new ArrayList<>(a.warnings);
        notes.addAll(viewModel().data.transferNotes);
        if (!notes.isEmpty()) {
            binding.cardWarnings.setVisibility(View.VISIBLE);
            binding.tvWarnings.setText("• " + android.text.TextUtils.join("\n• ", notes));
        }

        renderCategories(a);
        renderReconciliation(viewModel().data.statements);
        renderTrend(a);
    }

    /** Income vs. expense across each month of the statement — the same figures already averaged
     *  above, just shown as a series instead of a single number, so trends aren't hidden. */
    private void renderTrend(StatementAnalysis a) {
        if (a.months.size() < 2) {
            binding.cardTrend.setVisibility(View.GONE);
            return;
        }
        binding.cardTrend.setVisibility(View.VISIBLE);

        List<TrendChartView.Point> points = new ArrayList<>();
        for (MonthlyAggregate m : a.months) {
            points.add(new TrendChartView.Point(TrendChartView.shortRuMonth(m.month),
                    (float) m.incomeEffective, (float) m.expenseTotal));
        }
        int incomeColor = ContextCompat.getColor(requireContext(), R.color.brand_emerald);
        int expenseColor = ContextCompat.getColor(requireContext(), R.color.chart_slot_2);
        binding.trendChart.setSeries(points, incomeColor, expenseColor);
    }

    /** Compares what our own classifier found against the bank's own stated period totals —
     *  a mismatch usually means part of the statement's layout wasn't recognised. Informational
     *  only: does not change the score. */
    private void renderReconciliation(List<ParseResult> statements) {
        binding.reconContainer.removeAllViews();
        if (statements.isEmpty()) return;

        double foundIncome = 0, foundExpense = 0, statedIncome = 0, statedExpense = 0;
        int incomeStatedCount = 0, expenseStatedCount = 0;
        for (ParseResult p : statements) {
            for (Transaction t : p.transactions) {
                if (t.direction == Direction.CREDIT && t.flowType.isIncome()) foundIncome += t.amount;
                else if (t.flowType.isExpense()) foundExpense += t.amount;
            }
            if (p.header.totalCredit > 0) { statedIncome += p.header.totalCredit; incomeStatedCount++; }
            if (p.header.totalDebit > 0) { statedExpense += p.header.totalDebit; expenseStatedCount++; }
        }
        boolean hasIncomeStated = incomeStatedCount == statements.size();
        boolean hasExpenseStated = expenseStatedCount == statements.size();

        LayoutInflater inflater = LayoutInflater.from(requireContext());
        addReconRow(inflater, getString(R.string.recon_income_label), foundIncome, statedIncome, hasIncomeStated);
        addReconRow(inflater, getString(R.string.recon_expense_label), foundExpense, statedExpense, hasExpenseStated);

        double incomeDiffPct = hasIncomeStated && statedIncome > 0
                ? Math.abs(statedIncome - foundIncome) / statedIncome * 100 : 0;
        double expenseDiffPct = hasExpenseStated && statedExpense > 0
                ? Math.abs(statedExpense - foundExpense) / statedExpense * 100 : 0;

        if ((hasIncomeStated && incomeDiffPct > 10) || (hasExpenseStated && expenseDiffPct > 10)) {
            binding.tvReconWarning.setVisibility(View.VISIBLE);
            binding.tvReconWarning.setText(R.string.recon_warn_diff);
        } else if (!hasIncomeStated && !hasExpenseStated) {
            binding.tvReconWarning.setVisibility(View.VISIBLE);
            binding.tvReconWarning.setText(R.string.recon_warn_missing);
        } else {
            binding.tvReconWarning.setVisibility(View.GONE);
        }
    }

    private void addReconRow(LayoutInflater inflater, String label, double found, double stated, boolean hasStated) {
        ItemDetailRowBinding row = ItemDetailRowBinding.inflate(inflater, binding.reconContainer, false);
        row.tvLabel.setText(label);
        row.tvValue.setText(hasStated
                ? getString(R.string.recon_value, Money.format(found), Money.format(stated))
                : getString(R.string.recon_value_no_stated, Money.format(found)));
        binding.reconContainer.addView(row.getRoot());
    }

    private void renderCategories(StatementAnalysis a) {
        List<Map.Entry<ExpenseCategory, Double>> entries = new ArrayList<>(a.categoryMonthlyAvg.entrySet());
        entries.removeIf(e -> e.getValue() <= 1d);
        entries.sort(Comparator.comparingDouble((Map.Entry<ExpenseCategory, Double> e) -> e.getValue()).reversed());

        renderCategoryDonut(entries);

        double max = entries.isEmpty() ? 1d : entries.get(0).getValue();
        double total = 0d;
        for (Map.Entry<ExpenseCategory, Double> e : entries) total += e.getValue();
        int otherColor = ContextCompat.getColor(requireContext(), R.color.chart_other);
        LayoutInflater inflater = LayoutInflater.from(requireContext());

        for (int i = 0; i < entries.size(); i++) {
            Map.Entry<ExpenseCategory, Double> e = entries.get(i);
            ItemCategoryRowBinding row = ItemCategoryRowBinding.inflate(
                    inflater, binding.categoryContainer, false);
            row.tvName.setText(e.getKey().displayName());
            String share = total > 0 ? " · " + Money.percent1(e.getValue() / total) : "";
            row.tvValue.setText(Money.format(e.getValue()) + share);
            row.bar.setMax(1000);
            row.bar.setProgressCompat((int) Math.round(e.getValue() / max * 1000d), true);
            if (e.getKey() == ExpenseCategory.GAMBLING) {
                row.bar.setIndicatorColor(0xFFDC2626);
            }
            int dotColor = i < SLOT_COLOR_RES.length
                    ? ContextCompat.getColor(requireContext(), SLOT_COLOR_RES[i])
                    : otherColor;
            row.dot.setVisibility(View.VISIBLE);
            row.dot.setBackgroundTintList(android.content.res.ColorStateList.valueOf(dotColor));
            binding.categoryContainer.addView(row.getRoot());
        }
    }

    /** Donut of the top categories by monthly spend; the rest folds into a single "Прочее" slice. */
    private void renderCategoryDonut(List<Map.Entry<ExpenseCategory, Double>> entries) {
        List<DonutChartView.Segment> segments = new ArrayList<>();
        double otherSum = 0d;

        for (int i = 0; i < entries.size(); i++) {
            Map.Entry<ExpenseCategory, Double> e = entries.get(i);
            if (i < SLOT_COLOR_RES.length) {
                int color = ContextCompat.getColor(requireContext(), SLOT_COLOR_RES[i]);
                segments.add(new DonutChartView.Segment(e.getKey().displayName(), e.getValue().floatValue(), color));
            } else {
                otherSum += e.getValue();
            }
        }
        if (otherSum > 0d) {
            int otherColor = ContextCompat.getColor(requireContext(), R.color.chart_other);
            segments.add(new DonutChartView.Segment("Прочее", (float) otherSum, otherColor));
        }

        binding.categoryDonut.setSegments(segments);
        binding.categoryDonut.setOnSegmentTapListener(segment -> {
            binding.categoryDonut.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
            // amount as the big line (always short) and the category name as the small one,
            // which is the line that safely ellipsizes if the name is long
            binding.categoryDonut.setCenterText(Money.format(segment.value), segment.label);
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
