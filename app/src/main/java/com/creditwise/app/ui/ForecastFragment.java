package com.creditwise.app.ui;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.navigation.fragment.NavHostFragment;

import com.creditwise.app.R;
import com.creditwise.app.data.local.CreditCaseStore;
import com.creditwise.app.data.local.LocalAuthStore;
import com.creditwise.app.data.model.CreditCase;
import com.creditwise.app.data.model.ExpenseCategory;
import com.creditwise.app.databinding.FragmentForecastBinding;
import com.creditwise.app.util.Money;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Collections;
import java.util.List;

/**
 * A passive, real-time projection of "how much will be left" from the latest saved statement's
 * average spend, plus a soft flag if one discretionary category dominates spending. Purely
 * informational — recalculated from what's already known, never affects the trust index.
 */
public class ForecastFragment extends BaseFragment {

    private static final int RUNWAY_DISPLAY_CAP = 90; // beyond ~3 months the projection isn't meaningful

    private FragmentForecastBinding binding;
    private double weeklyBurn;
    private double monthlyBurn;
    private double dailyBurn;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentForecastBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        binding.backRow.setOnClickListener(v -> NavHostFragment.findNavController(this).popBackStack());

        String email = new LocalAuthStore(requireContext()).currentEmail();
        List<CreditCase> cases = new CreditCaseStore(requireContext()).loadCases(email);

        if (cases.isEmpty() || cases.get(0).avgExpense <= 0) {
            binding.tvEmpty.setVisibility(View.VISIBLE);
            binding.content.setVisibility(View.GONE);
            return;
        }

        CreditCase c = cases.get(0);
        binding.tvEmpty.setVisibility(View.GONE);
        binding.content.setVisibility(View.VISIBLE);

        monthlyBurn = c.avgExpense;
        weeklyBurn = monthlyBurn / 4.33;
        dailyBurn = monthlyBurn / 30d;
        renderDominant(c);

        binding.etBalance.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int d) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int d) {}
            @Override public void afterTextChanged(Editable s) { recalc(); }
        });
        recalc();
    }

    private void recalc() {
        double balance = parse(binding.etBalance.getText());
        LocalDate today = LocalDate.now();

        int daysLeftInWeek = 7 - today.getDayOfWeek().getValue(); // Monday=1..Sunday=7
        double projectedWeek = balance - (weeklyBurn / 7d) * daysLeftInWeek;

        int daysInMonth = YearMonth.from(today).lengthOfMonth();
        int daysLeftInMonth = daysInMonth - today.getDayOfMonth();
        double projectedMonth = balance - dailyBurn * daysLeftInMonth;

        binding.tvWeek.setText(getString(R.string.forecast_week, Money.format(Math.max(0, projectedWeek))));
        binding.tvMonth.setText(getString(R.string.forecast_month, Money.format(Math.max(0, projectedMonth))));
        int neutralColor = ContextCompat.getColor(requireContext(), android.R.color.darker_gray);
        int lowColor = ContextCompat.getColor(requireContext(), R.color.score_low);
        binding.tvWeek.setTextColor(projectedWeek < 0 ? lowColor : neutralColor);
        binding.tvMonth.setTextColor(projectedMonth < 0 ? lowColor : neutralColor);

        renderRunway(balance);
    }

    /** A "runway" ring — how many days the current balance lasts at the observed burn rate,
     *  colour-coded like the rest of the app's urgency indicators. My own addition: makes the
     *  otherwise-abstract weekly/monthly numbers land as one concrete, at-a-glance figure. */
    private void renderRunway(double balance) {
        if (dailyBurn <= 0 || balance <= 0) {
            binding.tvRunwayHeadline.setText(R.string.forecast_runway_empty);
            binding.tvRunwayHeadline.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.darker_gray));
            binding.runwayRing.setSegments(Collections.emptyList());
            binding.runwayRing.setCenterText("—", getString(R.string.forecast_runway_suffix));
            return;
        }
        int runwayDays = (int) Math.max(0, Math.round(balance / dailyBurn));
        boolean capped = runwayDays > RUNWAY_DISPLAY_CAP;
        int displayDays = Math.min(runwayDays, RUNWAY_DISPLAY_CAP);

        int colorRes;
        if (runwayDays >= 30) {
            colorRes = R.color.score_high;
        } else if (runwayDays >= 10) {
            colorRes = R.color.score_mid;
        } else {
            colorRes = R.color.score_low;
        }
        int color = ContextCompat.getColor(requireContext(), colorRes);

        binding.runwayRing.setSegments(
                Collections.singletonList(new DonutChartView.Segment("Хватит", displayDays, color)),
                RUNWAY_DISPLAY_CAP);
        String suffix = getString(R.string.forecast_runway_suffix);
        if (capped) {
            binding.runwayRing.setCenterText(RUNWAY_DISPLAY_CAP + "+", suffix);
        } else {
            binding.runwayRing.setCenterCount(displayDays, suffix);
        }

        int headlineRes = capped ? R.string.forecast_runway_headline_cap
                : runwayDays >= 10 ? R.string.forecast_runway_headline_ok
                : R.string.forecast_runway_headline_low;
        binding.tvRunwayHeadline.setText(getString(headlineRes, capped ? RUNWAY_DISPLAY_CAP : runwayDays));
        binding.tvRunwayHeadline.setTextColor(color);
    }

    private void renderDominant(CreditCase c) {
        boolean hasDominant = !c.topDiscretionaryCategory.isEmpty() && c.avgIncome > 0
                && c.topDiscretionaryMonthlyAmount / c.avgIncome > 0.35;
        if (hasDominant) {
            binding.tvDominant.setText(getString(R.string.forecast_dominant_warn,
                    displayName(c.topDiscretionaryCategory),
                    Money.percent1(c.topDiscretionaryMonthlyAmount / c.avgIncome)));
        } else {
            binding.tvDominant.setText(R.string.forecast_dominant_ok);
        }
    }

    private static String displayName(String enumName) {
        try {
            return ExpenseCategory.valueOf(enumName).displayName();
        } catch (IllegalArgumentException e) {
            return enumName;
        }
    }

    private static double parse(@Nullable CharSequence cs) {
        if (cs == null) return 0d;
        String s = cs.toString().replace(",", ".").replaceAll("[^0-9.]", "");
        if (s.isEmpty()) return 0d;
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return 0d;
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
