package com.creditwise.app.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.navigation.fragment.NavHostFragment;

import com.creditwise.app.R;
import com.creditwise.app.databinding.FragmentWelcomeBinding;
import com.creditwise.app.util.Web;

public class WelcomeFragment extends BaseFragment {

    private FragmentWelcomeBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentWelcomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        viewModel().reset();

        binding.btnOpenSite.setOnClickListener(v ->
                Web.open(requireContext(), getString(R.string.rating_site_url)));

        binding.btnStart.setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigate(R.id.action_welcome_to_rating));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
