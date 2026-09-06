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
import com.creditwise.app.databinding.FragmentExternalRatingBinding;
import com.creditwise.app.util.Web;

public class ExternalRatingFragment extends BaseFragment {

    private FragmentExternalRatingBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentExternalRatingBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        int existing = viewModel().data.externalRating;
        if (existing < 0) {
            String email = new LocalAuthStore(requireContext()).currentEmail();
            existing = new CreditCaseStore(requireContext()).loadExternalRating(email);
        }
        if (existing >= 0) binding.etRating.setText(String.valueOf(existing));

        binding.btnOpenSite.setOnClickListener(v ->
                Web.open(requireContext(), getString(R.string.rating_site_url)));

        binding.btnNext.setOnClickListener(v -> {
            Integer value = parseRating();
            if (value == null) {
                binding.tilRating.setError(getString(R.string.rating_error_range));
                return;
            }
            binding.tilRating.setError(null);
            viewModel().setExternalRating(requireContext(), value);
            NavHostFragment.findNavController(this).navigate(R.id.action_rating_to_upload);
        });
    }

    @Nullable
    private Integer parseRating() {
        CharSequence raw = binding.etRating.getText();
        if (raw == null || raw.toString().trim().isEmpty()) return null;
        try {
            int v = Integer.parseInt(raw.toString().trim());
            return (v >= 0 && v <= 999) ? v : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
