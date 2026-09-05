package com.creditwise.app.ui;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import com.creditwise.app.R;
import com.creditwise.app.data.local.CreditCaseStore;
import com.creditwise.app.data.local.LocalAuthStore;
import com.creditwise.app.data.model.CreditCase;
import com.creditwise.app.data.model.ExpenseCategory;
import com.creditwise.app.data.model.SavingsQuest;
import com.creditwise.app.databinding.DialogCaseDetailsBinding;
import com.creditwise.app.databinding.FragmentHomeBinding;
import com.creditwise.app.databinding.ItemDetailRowBinding;
import com.creditwise.app.util.Money;

import java.time.format.DateTimeFormatter;
import java.util.List;

public class HomeFragment extends BaseFragment {

    private static final DateTimeFormatter DF = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private FragmentHomeBinding binding;
    private LocalAuthStore authStore;
    private CreditCaseStore caseStore;
    private CreditCaseAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        authStore = new LocalAuthStore(requireContext());
        caseStore = new CreditCaseStore(requireContext());

        adapter = new CreditCaseAdapter(this::showDetails);
        binding.casesRecycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.casesRecycler.setAdapter(adapter);
        binding.casesRecycler.setNestedScrollingEnabled(false);

        new ItemTouchHelper(new SwipeToDeleteCallback(requireContext(), this::onSwipeDelete))
                .attachToRecyclerView(binding.casesRecycler);

        binding.fabAdd.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
            NavHostFragment.findNavController(this).navigate(R.id.action_home_to_welcome);
        });
        binding.cardForecastTeaser.setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigate(R.id.action_home_to_forecast));
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
        int bonus = caseStore.loadBonusPoints(email);
        SavingsQuest quest = caseStore.loadActiveQuest(email);

        binding.chipIndex.setText(cases.isEmpty() ? "—" : String.valueOf(cases.get(0).trustTotal));
        binding.chipTotal.setText(String.valueOf(cases.size()));
        binding.chipBonus.setText("+" + bonus);
        binding.chipQuest.setText(quest != null ? "Активно" : "Нет");

        adapter.submit(cases);
        binding.emptyState.setVisibility(cases.isEmpty() ? View.VISIBLE : View.GONE);
        binding.casesRecycler.setVisibility(cases.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void onSwipeDelete(int position) {
        String email = authStore.currentEmail();
        CreditCase removed = adapter.removeAt(position);
        caseStore.deleteCase(email, removed.id);
        requireView().performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);

        Snackbar.make(binding.getRoot(), R.string.home_deleted, Snackbar.LENGTH_LONG)
                .setAction(R.string.home_undo, v -> {
                    caseStore.saveCase(email, removed);
                    render();
                })
                .addCallback(new Snackbar.Callback() {
                    @Override
                    public void onDismissed(Snackbar transientBottomBar, int event) {
                        if (binding != null) {
                            binding.emptyState.setVisibility(adapter.isEmpty() ? View.VISIBLE : View.GONE);
                            binding.casesRecycler.setVisibility(adapter.isEmpty() ? View.GONE : View.VISIBLE);
                            binding.chipTotal.setText(String.valueOf(caseStore.loadCases(email).size()));
                        }
                    }
                })
                .show();
    }

    private void showDetails(CreditCase c) {
        DialogCaseDetailsBinding view = DialogCaseDetailsBinding.inflate(LayoutInflater.from(requireContext()));

        int color = ContextCompat.getColor(requireContext(), ApprovalColors.colorRes(c.approvalBand));
        view.approvalBadge.setBackgroundTintList(ColorStateList.valueOf(color));
        view.tvApprovalPercent.setText(c.approvalPercent + "%");
        view.tvLoanAmount.setText(Money.format(c.loanAmount));
        view.tvLoanTerm.setText(c.termMonths + " мес. · " + c.annualRatePercent + "% годовых");

        addDetailRow(view.rowContainer, "Индекс благонадёжности",
                c.trustTotal + "/100 · " + c.trustBand.toLowerCase());
        addDetailRow(view.rowContainer, "Шанс одобрения",
                c.approvalPercent + "% · " + bandRu(c.approvalBand));
        addDetailRow(view.rowContainer, "Дата оценки", c.createdAt.format(DF));
        if (!c.topDiscretionaryCategory.isEmpty()) {
            addDetailRow(view.rowContainer, "Больше всего сверх необходимого уходит на",
                    displayName(c.topDiscretionaryCategory) + " · "
                            + Money.format(c.topDiscretionaryMonthlyAmount) + "/мес.");
        }

        new MaterialAlertDialogBuilder(requireContext())
                .setView(view.getRoot())
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private void addDetailRow(ViewGroup container, String label, String value) {
        ItemDetailRowBinding row = ItemDetailRowBinding.inflate(LayoutInflater.from(requireContext()), container, false);
        row.tvLabel.setText(label);
        row.tvValue.setText(value);
        container.addView(row.getRoot());
    }

    private static String displayName(String enumName) {
        try {
            return ExpenseCategory.valueOf(enumName).displayName();
        } catch (IllegalArgumentException e) {
            return enumName;
        }
    }

    private static String bandRu(String band) {
        switch (band) {
            case "HIGH": return "высокий";
            case "MEDIUM": return "средний";
            default: return "низкий";
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
