package com.creditwise.app.ui;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
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
import com.creditwise.app.databinding.FragmentHomeBinding;

import java.util.List;

/**
 * The "Кредиты" tab: a quick summary of the current index (chips) plus teaser cards leading into
 * the two things that actually matter now that manual loan entry is gone — the spend-rate
 * forecast, and the dedicated "Кредитные предложения" screen showing which demo lenders the
 * current index already clears.
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

        binding.cardForecastTeaser.setOnClickListener(v -> {
            bounce(v);
            NavHostFragment.findNavController(this).navigate(R.id.action_home_to_forecast);
        });
        binding.cardOffersTeaser.setOnClickListener(v -> {
            bounce(v);
            NavHostFragment.findNavController(this).navigate(R.id.action_home_to_offers);
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

        animateIn(binding.cardForecastTeaser, 0);
        if (cases.isEmpty()) {
            binding.cardOffersTeaser.setVisibility(View.GONE);
        } else {
            binding.cardOffersTeaser.setVisibility(View.VISIBLE);
            animateIn(binding.cardOffersTeaser, 90);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
