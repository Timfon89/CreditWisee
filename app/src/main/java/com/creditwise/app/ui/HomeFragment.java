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

import com.creditwise.app.R;
import com.creditwise.app.data.local.CreditCaseStore;
import com.creditwise.app.data.local.LocalAuthStore;
import com.creditwise.app.data.model.ChallengeState;
import com.creditwise.app.data.model.CreditCase;
import com.creditwise.app.data.model.LenderOffer;
import com.creditwise.app.databinding.FragmentHomeBinding;
import com.creditwise.app.databinding.ItemLenderOfferBinding;
import com.creditwise.app.util.Money;

import java.util.List;
import java.util.Locale;

/**
 * The "Кредиты" tab: a quick summary of the current index, then — the whole point of this
 * screen since manual loan entry was removed — which of the demo lenders in
 * {@link LenderOffer#CATALOG} the current 0–100 index already clears, and which still need a
 * higher score. Below the catalog's lowest threshold there's nothing to show at all, just a
 * nudge toward the ongoing challenges. Updating the underlying statement happens through the
 * standalone "Обновить выписку" screen (the FAB), not by re-running the registration wizard.
 */
public class HomeFragment extends BaseFragment {

    private FragmentHomeBinding binding;
    private LocalAuthStore authStore;
    private CreditCaseStore caseStore;
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

        binding.fabAdd.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
            NavHostFragment.findNavController(this).navigate(R.id.quickUpdateFragment);
        });
        binding.cardForecastTeaser.setOnClickListener(v -> {
            bounce(v);
            NavHostFragment.findNavController(this).navigate(R.id.action_home_to_forecast);
        });
        binding.btnGoChallenges.setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigate(R.id.questsFragment));
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

        int trustTotal = cases.isEmpty() ? 0 : cases.get(0).trustTotal;
        binding.chipIndex.setText(cases.isEmpty() ? "—" : String.valueOf(trustTotal));
        binding.chipTotal.setText(String.valueOf(cases.size()));
        binding.chipBonus.setText("+" + Math.round(challenges.combinedPoints()));
        binding.chipQuest.setText(hasChallengeHistory ? "Есть" : "Нет");

        animateIn(binding.cardForecastTeaser, 0);
        renderOffers(trustTotal);
    }

    private void renderOffers(int trustTotal) {
        boolean anyEligible = trustTotal >= LenderOffer.lowestThreshold();
        binding.emptyState.setVisibility(anyEligible ? View.GONE : View.VISIBLE);
        binding.offersContent.setVisibility(anyEligible ? View.VISIBLE : View.GONE);
        if (!anyEligible) {
            binding.tvEmptyBody.setText(getString(R.string.offers_empty_body, LenderOffer.lowestThreshold()));
            return;
        }

        binding.eligibleContainer.removeAllViews();
        binding.ineligibleContainer.removeAllViews();
        int eligibleCount = 0;
        for (LenderOffer offer : LenderOffer.CATALOG) {
            if (offer.isEligible(trustTotal)) {
                eligibleCount++;
                binding.eligibleContainer.addView(eligibleRow(offer));
            } else {
                binding.ineligibleContainer.addView(ineligibleRow(offer, trustTotal));
            }
        }
        binding.tvNoneEligible.setVisibility(eligibleCount == 0 ? View.VISIBLE : View.GONE);
    }

    private View eligibleRow(LenderOffer offer) {
        ItemLenderOfferBinding row = ItemLenderOfferBinding.inflate(
                LayoutInflater.from(requireContext()), binding.eligibleContainer, false);
        row.tvName.setText(offer.name);
        row.tvType.setText(offer.type);
        row.tvTerms.setText(getString(R.string.offers_terms, Money.format(offer.maxAmount),
                offer.maxTermMonths, String.format(Locale.US, "%.1f", offer.annualRatePercent)));
        row.tvNote.setText(R.string.offers_ready_note);
        row.tvBadge.setText(R.string.offers_badge_ready);
        row.tvBadge.setTextColor(ContextCompat.getColor(requireContext(), R.color.score_high));
        row.tvBadge.setBackgroundTintList(ColorStateList.valueOf(
                ContextCompat.getColor(requireContext(), R.color.score_high_tint)));
        row.progress.setIndicatorColor(ContextCompat.getColor(requireContext(), R.color.score_high));
        row.progress.setProgress(100);
        return row.getRoot();
    }

    private View ineligibleRow(LenderOffer offer, int trustTotal) {
        ItemLenderOfferBinding row = ItemLenderOfferBinding.inflate(
                LayoutInflater.from(requireContext()), binding.ineligibleContainer, false);
        row.tvName.setText(offer.name);
        row.tvType.setText(offer.type);
        row.tvTerms.setText(getString(R.string.offers_terms, Money.format(offer.maxAmount),
                offer.maxTermMonths, String.format(Locale.US, "%.1f", offer.annualRatePercent)));
        int missing = offer.minTrustScore - trustTotal;
        row.tvNote.setText(getString(R.string.offers_missing_note, offer.minTrustScore, missing));
        row.tvBadge.setText(R.string.offers_badge_locked);
        row.tvBadge.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.darker_gray));
        row.tvBadge.setBackgroundTintList(ColorStateList.valueOf(
                ContextCompat.getColor(requireContext(), R.color.score_low_tint)));
        row.progress.setIndicatorColor(ContextCompat.getColor(requireContext(), android.R.color.darker_gray));
        int pct = (int) Math.round(Math.max(0d, Math.min(1d, trustTotal / (double) offer.minTrustScore)) * 100);
        row.progress.setProgress(pct);
        return row.getRoot();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
