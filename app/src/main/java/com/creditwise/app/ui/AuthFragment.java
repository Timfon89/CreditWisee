package com.creditwise.app.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.navigation.fragment.NavHostFragment;

import com.creditwise.app.R;
import com.creditwise.app.data.local.LocalAuthStore;
import com.creditwise.app.databinding.FragmentAuthBinding;

public class AuthFragment extends BaseFragment {

    private FragmentAuthBinding binding;
    private boolean registerMode;
    private LocalAuthStore authStore;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentAuthBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        authStore = new LocalAuthStore(requireContext());
        if (authStore.isLoggedIn()) {
            goHome();
            return;
        }

        binding.modeToggle.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            registerMode = checkedId == binding.btnModeRegister.getId();
            binding.btnSubmit.setText(registerMode ? R.string.auth_tab_register : R.string.auth_tab_login);
            binding.tvAuthError.setVisibility(View.GONE);
        });

        binding.btnSubmit.setOnClickListener(v -> submit());
        binding.btnGoogle.setOnClickListener(v -> new AlertDialog.Builder(requireContext())
                .setTitle(R.string.auth_google)
                .setMessage(R.string.auth_google_unavailable)
                .setPositiveButton(android.R.string.ok, null)
                .show());
    }

    private void submit() {
        String email = text(binding.etEmail);
        String password = text(binding.etPassword);
        if (email.isEmpty() || password.length() < 4) {
            showError(getString(R.string.auth_error_fields));
            return;
        }

        LocalAuthStore.Result result = registerMode
                ? authStore.register(email, password)
                : authStore.login(email, password);

        switch (result) {
            case OK:
                goHome();
                break;
            case ALREADY_EXISTS:
                showError(getString(R.string.auth_error_exists));
                break;
            case NOT_FOUND:
                showError(getString(R.string.auth_error_not_found));
                break;
            case WRONG_PASSWORD:
                showError(getString(R.string.auth_error_wrong_password));
                break;
            default:
                showError(getString(R.string.auth_error_fields));
        }
    }

    private void showError(String message) {
        binding.tvAuthError.setText(message);
        binding.tvAuthError.setVisibility(View.VISIBLE);
    }

    private void goHome() {
        NavHostFragment.findNavController(this).navigate(R.id.action_auth_to_home);
    }

    private static String text(com.google.android.material.textfield.TextInputEditText et) {
        return et.getText() == null ? "" : et.getText().toString().trim();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
