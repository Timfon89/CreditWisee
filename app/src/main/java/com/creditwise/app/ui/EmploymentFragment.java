package com.creditwise.app.ui;

import android.os.Bundle;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.navigation.NavBackStackEntry;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;

import com.creditwise.app.R;
import com.creditwise.app.data.local.CreditCaseStore;
import com.creditwise.app.data.local.LocalAuthStore;
import com.creditwise.app.data.model.EmploymentType;
import com.creditwise.app.databinding.FragmentEmploymentBinding;
import com.google.android.material.card.MaterialCardView;

public class EmploymentFragment extends BaseFragment {

    private FragmentEmploymentBinding binding;
    private EmploymentType selected = EmploymentType.EMPLOYEE;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentEmploymentBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        String email = new LocalAuthStore(requireContext()).currentEmail();
        selected = new CreditCaseStore(requireContext()).loadEmploymentType(email);
        applySelection();

        binding.cardStudent.setOnClickListener(v -> select(EmploymentType.STUDENT));
        binding.cardFreelancer.setOnClickListener(v -> select(EmploymentType.FREELANCER));
        binding.cardEmployee.setOnClickListener(v -> select(EmploymentType.EMPLOYEE));

        binding.btnNext.setOnClickListener(v -> {
            viewModel().setEmploymentType(requireContext(), selected);
            NavController nav = NavHostFragment.findNavController(this);
            NavBackStackEntry prev = nav.getPreviousBackStackEntry();
            if (prev != null && prev.getDestination().getId() == R.id.profileFragment) {
                // Reached from Profile to change the answer later — just go back, no reset to home.
                nav.popBackStack();
            } else {
                // Reached during registration — this is the very first thing a new account does,
                // continuing straight into the one-time intake (rating → statement → analysis).
                nav.navigate(R.id.action_employment_to_rating);
            }
        });
    }

    private void select(EmploymentType type) {
        selected = type;
        binding.getRoot().performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
        applySelection();
    }

    private void applySelection() {
        setChecked(binding.cardStudent, selected == EmploymentType.STUDENT);
        setChecked(binding.cardFreelancer, selected == EmploymentType.FREELANCER);
        setChecked(binding.cardEmployee, selected == EmploymentType.EMPLOYEE);
    }

    private static void setChecked(MaterialCardView card, boolean checked) {
        card.setChecked(checked);
        float density = card.getResources().getDisplayMetrics().density;
        card.setStrokeWidth(Math.round((checked ? 2.5f : 1.5f) * density));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
