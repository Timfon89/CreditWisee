package com.creditwise.app.ui;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.navigation.fragment.NavHostFragment;

import com.creditwise.app.R;
import com.creditwise.app.data.model.SberbankLoanOffer;
import com.creditwise.app.data.model.StatementAnalysis;
import com.creditwise.app.databinding.FragmentTariffBinding;
import com.creditwise.app.domain.LoanCalculator;
import com.creditwise.app.util.Money;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class TariffFragment extends BaseFragment {

    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("MM.yyyy");

    private FragmentTariffBinding binding;
    private final LoanCalculator loanCalculator = new LoanCalculator();
    private final List<YearMonth> months = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentTariffBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        YearMonth start = YearMonth.now();
        List<String> labels = new ArrayList<>();
        for (int i = 0; i < 18; i++) {
            YearMonth ym = start.plusMonths(i);
            months.add(ym);
            labels.add(ym.format(MONTH_FMT));
        }
        binding.etStart.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_list_item_1, labels));
        binding.etStart.setText(labels.get(0), false);

        binding.etSberOffer.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_list_item_1, SberbankLoanOffer.CATALOG));
        binding.etSberOffer.setOnItemClickListener((parent, v, position, id) -> applyOffer(SberbankLoanOffer.CATALOG[position]));

        TextWatcher watcher = new SimpleWatcher(this::recalculate);
        binding.etAmount.addTextChangedListener(watcher);
        binding.etTerm.addTextChangedListener(watcher);
        binding.etRate.addTextChangedListener(watcher);

        binding.btnNext.setOnClickListener(v -> onNext());
        recalculate();
    }

    /** Pre-fills the manual fields from a chosen reference offer; user can still edit them. */
    private void applyOffer(SberbankLoanOffer offer) {
        binding.etAmount.setText(String.format(Locale.US, "%.0f", offer.amount));
        binding.etTerm.setText(String.valueOf(offer.termMonths));
        binding.etRate.setText(String.format(Locale.US, "%.1f", offer.annualRatePercent));
        recalculate();
    }

    private void recalculate() {
        double amount = parse(binding.etAmount.getText());
        int term = (int) parse(binding.etTerm.getText());
        double rate = parse(binding.etRate.getText());
        StatementAnalysis a = viewModel().data.analysis;

        double payment = loanCalculator.annuityPayment(amount, rate, term);
        binding.tvPayment.setText(payment > 0 ? Money.format(payment) : "—");

        if (a != null && a.avgIncome > 0 && payment > 0) {
            double income = a.avgIncome;
            binding.tvPti.setText(Money.percent((payment) / income));
            binding.tvDti.setText(Money.percent((payment + a.avgFixedExpense) / income));
        } else {
            binding.tvPti.setText("—");
            binding.tvDti.setText("—");
        }
    }

    private void onNext() {
        double amount = parse(binding.etAmount.getText());
        int term = (int) parse(binding.etTerm.getText());
        double rate = parse(binding.etRate.getText());
        if (amount <= 0 || term <= 0) {
            binding.etAmount.setError(getString(R.string.tariff_error_fields));
            return;
        }
        if (viewModel().data.analysis == null) {
            android.widget.Toast.makeText(requireContext(),
                    R.string.upload_error, android.widget.Toast.LENGTH_LONG).show();
            return;
        }
        int idx = Math.max(0, indexOfLabel(binding.etStart.getText().toString()));
        viewModel().setLoan(amount, term, rate, months.get(idx));
        viewModel().computeResults(requireContext());
        NavHostFragment.findNavController(this).navigate(R.id.action_tariff_to_result);
    }

    private int indexOfLabel(String label) {
        for (int i = 0; i < months.size(); i++) {
            if (months.get(i).format(MONTH_FMT).equals(label)) return i;
        }
        return 0;
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

    private static final class SimpleWatcher implements TextWatcher {
        private final Runnable onChange;
        SimpleWatcher(Runnable onChange) { this.onChange = onChange; }
        @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
        @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
        @Override public void afterTextChanged(Editable s) { onChange.run(); }
    }
}
