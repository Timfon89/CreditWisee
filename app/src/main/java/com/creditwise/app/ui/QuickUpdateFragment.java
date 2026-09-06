package com.creditwise.app.ui;

import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.navigation.fragment.NavHostFragment;

import com.creditwise.app.R;
import com.creditwise.app.data.local.LocalAuthStore;
import com.creditwise.app.data.model.LenderOffer;
import com.creditwise.app.data.model.QuickUpdateResult;
import com.creditwise.app.databinding.FragmentQuickUpdateBinding;
import com.creditwise.app.util.Money;

/**
 * The standalone "keep the challenges going" screen — reachable from Quests or the weekly
 * reminder notification, deliberately outside the full onboarding wizard (no employment/external
 * rating/telegram/tariff steps: those are reused from what's already persisted). Uploading one
 * fresh statement here feeds the ongoing {@link com.creditwise.app.data.model.ChallengeState}
 * and previews whether the recomputed index would unlock any new lender in the demo catalog.
 */
public class QuickUpdateFragment extends BaseFragment {

    private FragmentQuickUpdateBinding binding;
    private ActivityResultLauncher<String[]> picker;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        picker = registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
            if (uri != null) viewModel().quickUpdate(requireContext(), uri);
        });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentQuickUpdateBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        if (!new LocalAuthStore(requireContext()).isLoggedIn()) {
            NavHostFragment.findNavController(this).navigate(R.id.authFragment);
            return;
        }

        binding.btnPick.setOnClickListener(v -> picker.launch(new String[]{"application/pdf"}));
        binding.btnDone.setOnClickListener(v -> NavHostFragment.findNavController(this).navigate(R.id.homeFragment));
        binding.btnViewOffers.setOnClickListener(v -> NavHostFragment.findNavController(this).navigate(R.id.creditOffersFragment));

        viewModel().quickUpdateStatus().observe(getViewLifecycleOwner(), status -> {
            boolean loading = status == AssessmentViewModel.Status.LOADING;
            binding.progress.setVisibility(loading ? View.VISIBLE : View.GONE);
            binding.btnPick.setEnabled(!loading);
            binding.btnPick.setText(loading ? R.string.quick_update_parsing : R.string.quick_update_pick);
        });

        viewModel().quickUpdateResult().observe(getViewLifecycleOwner(), this::render);
    }

    private void render(QuickUpdateResult result) {
        if (result == null) return;
        if (!result.success) {
            binding.tvStatus.setVisibility(View.VISIBLE);
            binding.tvStatus.setText(getString(R.string.upload_error) + "\n" + result.message);
            binding.cardResult.setVisibility(View.GONE);
            binding.cardUnlocked.setVisibility(View.GONE);
            return;
        }

        binding.tvStatus.setVisibility(View.GONE);
        binding.cardResult.setVisibility(View.VISIBLE);
        int delta = result.trustAfter - result.trustBefore;
        binding.tvScoreChange.setText(getString(R.string.quick_update_score_change,
                result.trustBefore, result.trustAfter));
        binding.tvScoreNote.setText(delta >= 0
                ? getString(R.string.quick_update_score_up, delta)
                : getString(R.string.quick_update_score_down, Math.abs(delta)));

        if (result.newlyUnlocked.isEmpty()) {
            binding.cardUnlocked.setVisibility(View.GONE);
        } else {
            binding.cardUnlocked.setVisibility(View.VISIBLE);
            binding.tvUnlockedTitle.setText(getString(
                    R.string.quick_update_unlocked_title, result.newlyUnlocked.size()));
            StringBuilder names = new StringBuilder();
            for (LenderOffer offer : result.newlyUnlocked) {
                if (names.length() > 0) names.append(", ");
                names.append(offer.name).append(" (").append(Money.format(offer.maxAmount)).append(")");
            }
            binding.tvUnlockedBody.setText(names.toString());
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
