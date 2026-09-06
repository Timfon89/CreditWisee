package com.creditwise.app.ui;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.navigation.fragment.NavHostFragment;

import com.creditwise.app.R;
import com.creditwise.app.data.model.LenderOffer;
import com.creditwise.app.databinding.FragmentCreditOffersBinding;
import com.creditwise.app.databinding.ItemLenderOfferBinding;
import com.creditwise.app.util.Money;

import java.util.Locale;

/**
 * Shows which illustrative lenders (see {@link LenderOffer#CATALOG}) the current 0–100 trust
 * index already clears, and which still require a higher score — reached right after the index
 * is computed on {@link ResultFragment}, or later from a Home teaser card. Below the catalog's
 * lowest threshold there are no offers at all; the only way forward is raising the index via
 * the ongoing challenges or a cleaner statement.
 */
public class CreditOffersFragment extends BaseFragment {

    private FragmentCreditOffersBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentCreditOffersBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        int trustTotal = getArguments() != null ? getArguments().getInt("trustTotal", 0) : 0;

        binding.tvScoreLine.setText(getString(R.string.offers_score_line, trustTotal));
        binding.btnGoChallenges.setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigate(R.id.questsFragment));

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
