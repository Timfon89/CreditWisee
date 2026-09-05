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
import com.creditwise.app.data.model.ParseResult;
import com.creditwise.app.databinding.FragmentStatementUploadBinding;
import com.creditwise.app.databinding.ItemStatementRowBinding;

import java.util.List;

public class StatementUploadFragment extends BaseFragment {

    private FragmentStatementUploadBinding binding;
    private ActivityResultLauncher<String[]> picker;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        picker = registerForActivityResult(new ActivityResultContracts.OpenMultipleDocuments(), uris -> {
            for (Uri uri : uris) viewModel().addStatement(requireContext(), uri);
        });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentStatementUploadBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        binding.btnPick.setOnClickListener(v ->
                picker.launch(new String[]{"application/pdf"}));

        binding.btnNext.setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigate(R.id.action_upload_to_analysis));

        viewModel().statementStatus().observe(getViewLifecycleOwner(), status -> {
            boolean loading = status == AssessmentViewModel.Status.LOADING;
            binding.progress.setVisibility(loading ? View.VISIBLE : View.GONE);
            binding.tvStatus.setVisibility(status == AssessmentViewModel.Status.IDLE ? View.GONE : View.VISIBLE);
            binding.btnPick.setEnabled(!loading);
            binding.btnPick.setText(viewModel().statementCount() == 0
                    ? R.string.upload_pick : R.string.upload_pick_more);
            binding.btnNext.setEnabled(viewModel().statementCount() > 0);

            switch (status) {
                case LOADING:
                    binding.tvStatus.setText(R.string.upload_parsing);
                    break;
                case OK:
                    binding.tvStatus.setText(getString(R.string.upload_ok) + ". "
                            + safe(viewModel().statementMessage().getValue()));
                    renderStatements();
                    break;
                case ERROR:
                    binding.tvStatus.setText(getString(R.string.upload_error) + "\n"
                            + safe(viewModel().statementMessage().getValue()));
                    break;
                default:
                    break;
            }
        });
    }

    private void renderStatements() {
        binding.statementContainer.removeAllViews();
        List<ParseResult> statements = viewModel().data.statements;
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (int i = 0; i < statements.size(); i++) {
            int index = i;
            ItemStatementRowBinding row = ItemStatementRowBinding.inflate(inflater, binding.statementContainer, false);
            row.tvLabel.setText(getString(R.string.upload_statement_row, i + 1,
                    statements.get(i).transactions.size()));
            row.btnRemove.setOnClickListener(v -> {
                viewModel().removeStatement(index);
                binding.btnPick.setText(viewModel().statementCount() == 0
                        ? R.string.upload_pick : R.string.upload_pick_more);
                binding.btnNext.setEnabled(viewModel().statementCount() > 0);
                binding.tvStatus.setText(safe(viewModel().statementMessage().getValue()));
                binding.tvStatus.setVisibility(viewModel().statementCount() > 0 ? View.VISIBLE : View.GONE);
                renderStatements();
            });
            binding.statementContainer.addView(row.getRoot());
        }
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
