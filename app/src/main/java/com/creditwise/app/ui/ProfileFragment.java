package com.creditwise.app.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.navigation.fragment.NavHostFragment;

import com.creditwise.app.R;
import com.creditwise.app.data.local.CreditCaseStore;
import com.creditwise.app.data.local.LocalAuthStore;
import com.creditwise.app.data.local.ThemeStore;
import com.creditwise.app.data.model.CreditCase;
import com.creditwise.app.databinding.FragmentProfileBinding;
import com.creditwise.app.util.Web;

import java.util.List;
import java.util.Locale;

public class ProfileFragment extends BaseFragment {

    private FragmentProfileBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentProfileBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        binding.btnOpenSite.setOnClickListener(v ->
                Web.open(requireContext(), getString(R.string.rating_site_url)));
        binding.btnLogout.setOnClickListener(v -> {
            new LocalAuthStore(requireContext()).logout();
            NavHostFragment.findNavController(this).navigate(R.id.action_profile_to_auth);
        });

        setUpThemeToggle();
    }

    private void setUpThemeToggle() {
        String current = ThemeStore.load(requireContext());
        int checkedId;
        switch (current) {
            case ThemeStore.LIGHT: checkedId = binding.btnThemeLight.getId(); break;
            case ThemeStore.DARK: checkedId = binding.btnThemeDark.getId(); break;
            default: checkedId = binding.btnThemeSystem.getId();
        }
        binding.themeToggle.check(checkedId);

        binding.themeToggle.addOnButtonCheckedListener((group, checkedButtonId, isChecked) -> {
            if (!isChecked) return;
            String mode;
            if (checkedButtonId == binding.btnThemeLight.getId()) mode = ThemeStore.LIGHT;
            else if (checkedButtonId == binding.btnThemeDark.getId()) mode = ThemeStore.DARK;
            else mode = ThemeStore.SYSTEM;
            ThemeStore.save(requireContext(), mode);
        });
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
        int bonus = new CreditCaseStore(requireContext()).loadBonusPoints(email);

        String safeEmail = email == null ? "" : email;
        String initials = safeEmail.length() >= 2
                ? safeEmail.substring(0, 2).toUpperCase(Locale.ROOT)
                : safeEmail.toUpperCase(Locale.ROOT);
        binding.tvAvatar.setText(initials);
        binding.tvEmail.setText(safeEmail);

        binding.tvCasesCount.setText(String.valueOf(cases.size()));
        binding.tvBonusPoints.setText(String.valueOf(bonus));
        binding.tvLastIndex.setText(cases.isEmpty() ? "—" : String.valueOf(cases.get(0).trustTotal));
        binding.tvEmployment.setText(new CreditCaseStore(requireContext())
                .loadEmploymentType(email).displayName());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
