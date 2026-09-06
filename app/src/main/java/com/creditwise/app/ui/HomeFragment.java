package com.creditwise.app.ui;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.os.Build;
import android.os.Bundle;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
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
import com.creditwise.app.data.model.ChallengeState;
import com.creditwise.app.data.model.CreditCase;
import com.creditwise.app.data.model.ExpenseCategory;
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
    private ActivityResultLauncher<String> notificationPermissionLauncher;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        notificationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(), granted -> { });
    }

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
        requestNotificationPermissionIfNeeded();

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
        binding.cardForecastTeaser.setOnClickListener(v -> {
            bounce(v);
            NavHostFragment.findNavController(this).navigate(R.id.action_home_to_forecast);
        });
        binding.cardOffersTeaser.setOnClickListener(v -> {
            bounce(v);
            List<CreditCase> cases = caseStore.loadCases(authStore.currentEmail());
            Bundle args = new Bundle();
            args.putInt("trustTotal", cases.isEmpty() ? 0 : cases.get(0).trustTotal);
            NavHostFragment.findNavController(this).navigate(R.id.action_home_to_offers, args);
        });
    }

    /** One-time ask, gated to API 33+ (older versions don't need it) — lets the weekly
     *  challenge-reminder worker actually show its notification. */
    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < 33) return;
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) {
            return;
        }
        SharedPreferences prefs = requireContext()
                .getSharedPreferences("creditwise_notifications", Context.MODE_PRIVATE);
        if (prefs.getBoolean("asked_post_notifications", false)) return;
        prefs.edit().putBoolean("asked_post_notifications", true).apply();
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
    }

    /** A quick tactile "squish" on tap — purely decorative, doesn't delay navigation. */
    private void bounce(View v) {
        v.animate().cancel();
        v.animate().scaleX(0.95f).scaleY(0.95f).setDuration(80)
                .withEndAction(() -> v.animate().scaleX(1f).scaleY(1f).setDuration(140).start())
                .start();
    }

    /** Staggered pop-in — fades and slides a teaser card up into place. */
    private void animateIn(View v, long startDelay) {
        v.animate().cancel();
        v.setAlpha(0f);
        v.setTranslationY(24f);
        v.animate().alpha(1f).translationY(0f).setStartDelay(startDelay).setDuration(280).start();
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
        ChallengeState challenges = caseStore.loadChallengeState(email);
        boolean hasChallengeHistory = !challenges.savingsHistory.isEmpty() || !challenges.regularityHistory.isEmpty();

        binding.chipIndex.setText(cases.isEmpty() ? "—" : String.valueOf(cases.get(0).trustTotal));
        binding.chipTotal.setText(String.valueOf(cases.size()));
        binding.chipBonus.setText("+" + Math.round(challenges.combinedPoints()));
        binding.chipQuest.setText(hasChallengeHistory ? "Есть" : "Нет");

        adapter.submit(cases);
        binding.emptyState.setVisibility(cases.isEmpty() ? View.VISIBLE : View.GONE);
        binding.casesRecycler.setVisibility(cases.isEmpty() ? View.GONE : View.VISIBLE);

        animateIn(binding.cardForecastTeaser, 0);
        if (cases.isEmpty()) {
            binding.cardOffersTeaser.setVisibility(View.GONE);
        } else {
            binding.cardOffersTeaser.setVisibility(View.VISIBLE);
            animateIn(binding.cardOffersTeaser, 90);
        }
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
