package com.creditwise.app.ui;

import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.creditwise.app.R;
import com.creditwise.app.data.model.TelegramFlag;
import com.creditwise.app.data.model.TelegramScanResult;
import com.creditwise.app.databinding.FragmentTelegramBinding;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class TelegramFragment extends BaseFragment {

    private FragmentTelegramBinding binding;
    private ActivityResultLauncher<String[]> picker;
    /** Which categories the user has expanded past the always-visible first message —
     *  kept across re-renders (e.g. after deleting a flag) so the section doesn't collapse
     *  on you mid-review. */
    private final java.util.Set<TelegramFlag.Category> expanded = new java.util.HashSet<>();

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        picker = registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
            if (uri != null) viewModel().scanTelegram(requireContext(), uri);
        });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentTelegramBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        binding.btnPick.setOnClickListener(v ->
                picker.launch(new String[]{"application/json", "text/plain", "*/*"}));

        binding.cbConsent.setChecked(viewModel().data.telegramConsent);
        binding.cbConsent.setOnCheckedChangeListener((b, checked) ->
                viewModel().setTelegramConsent(checked));

        binding.btnSkip.setOnClickListener(v -> {
            viewModel().clearTelegram();
            viewModel().setTelegramConsent(false);
            goNext();
        });
        binding.btnNext.setOnClickListener(v -> goNext());

        viewModel().telegramStatus().observe(getViewLifecycleOwner(), status -> {
            boolean loading = status == AssessmentViewModel.Status.LOADING;
            binding.progress.setVisibility(loading ? View.VISIBLE : View.GONE);
            binding.btnPick.setEnabled(!loading);
            binding.tvStatus.setVisibility(status == AssessmentViewModel.Status.IDLE ? View.GONE : View.VISIBLE);

            binding.flagsContainer.removeAllViews();
            switch (status) {
                case LOADING:
                    binding.tvRemoveHint.setVisibility(View.GONE);
                    binding.tvStatus.setText(R.string.tg_parsing);
                    break;
                case ERROR:
                    binding.tvRemoveHint.setVisibility(View.GONE);
                    binding.tvStatus.setText(getString(R.string.tg_error) + "\n"
                            + safe(viewModel().telegramMessage().getValue()));
                    break;
                case OK:
                    renderResult(viewModel().data.telegram);
                    break;
                default:
                    break;
            }
        });
    }

    private void renderResult(@Nullable TelegramScanResult scan) {
        if (scan == null) return;
        binding.flagsContainer.removeAllViews();

        StringBuilder status = new StringBuilder(getString(
                R.string.tg_summary, scan.messagesScanned, scan.ownMessages));
        if (scan.warnings.contains("need_full_export")) {
            status.append("\n").append(getString(R.string.tg_need_full_export));
        }
        binding.tvStatus.setText(status.toString());

        if (scan.flags.isEmpty()) {
            binding.tvRemoveHint.setVisibility(View.GONE);
            TextView empty = new TextView(requireContext());
            empty.setText(R.string.tg_empty);
            binding.flagsContainer.addView(empty);
            return;
        }
        binding.tvRemoveHint.setVisibility(View.VISIBLE);

        Map<TelegramFlag.Category, Integer> counts = scan.countByCategory();

        renderGroup(scan, counts, true, getString(R.string.tg_positive_header));
        renderGroup(scan, counts, false, getString(R.string.tg_negative_header));
    }

    private void renderGroup(TelegramScanResult scan, Map<TelegramFlag.Category, Integer> counts,
                             boolean positive, String groupTitle) {
        boolean any = false;
        for (TelegramFlag.Category category : TelegramFlag.Category.values()) {
            if (category.isPositive() != positive) continue;
            Integer c = counts.get(category);
            if (c == null || c == 0) continue;

            if (!any) {
                TextView groupHeader = new TextView(requireContext());
                groupHeader.setText(groupTitle);
                groupHeader.setAllCaps(true);
                groupHeader.setTextSize(12f);
                groupHeader.setLetterSpacing(0.08f);
                groupHeader.setPadding(0, dp(20), 0, 0);
                binding.flagsContainer.addView(groupHeader);
                any = true;
            }

            TextView header = new TextView(requireContext());
            header.setText(category.displayName() + " · " + c);
            header.setTextSize(16f);
            header.setTypeface(header.getTypeface(), android.graphics.Typeface.BOLD);
            header.setPadding(0, dp(12), 0, dp(6));
            binding.flagsContainer.addView(header);

            List<TelegramFlag> categoryFlags = new ArrayList<>();
            for (TelegramFlag flag : scan.flags) {
                if (flag.category == category) categoryFlags.add(flag);
            }

            addFlagRecycler(scan, categoryFlags.subList(0, 1));

            if (categoryFlags.size() > 1) {
                List<TelegramFlag> rest = categoryFlags.subList(1, categoryFlags.size());
                boolean isExpanded = expanded.contains(category);

                TextView toggle = new TextView(requireContext());
                toggle.setTextSize(13f);
                toggle.setPadding(0, dp(6), 0, dp(6));
                toggle.setTypeface(toggle.getTypeface(), android.graphics.Typeface.BOLD);
                binding.flagsContainer.addView(toggle);

                RecyclerView restRecycler = addFlagRecycler(scan, rest);
                restRecycler.setVisibility(isExpanded ? View.VISIBLE : View.GONE);

                toggle.setText(isExpanded ? getString(R.string.tg_show_less)
                        : getString(R.string.tg_show_more, rest.size()));
                toggle.setOnClickListener(v -> {
                    boolean nowExpanded = restRecycler.getVisibility() != View.VISIBLE;
                    restRecycler.setVisibility(nowExpanded ? View.VISIBLE : View.GONE);
                    toggle.setText(nowExpanded ? getString(R.string.tg_show_less)
                            : getString(R.string.tg_show_more, rest.size()));
                    if (nowExpanded) expanded.add(category); else expanded.remove(category);
                });
            }
        }
    }

    /** One swipe-to-delete-enabled list of flags, backed by its own copy so deleting from the
     *  "rest" recycler doesn't disturb the always-visible first one (and vice versa). */
    private RecyclerView addFlagRecycler(TelegramScanResult scan, List<TelegramFlag> flags) {
        RecyclerView recycler = new RecyclerView(requireContext());
        recycler.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        recycler.setNestedScrollingEnabled(false);
        TelegramFlagAdapter flagAdapter = new TelegramFlagAdapter(flags);
        recycler.setAdapter(flagAdapter);
        new ItemTouchHelper(new SwipeToDeleteCallback(requireContext(), position -> {
            TelegramFlag removed = flagAdapter.removeAt(position);
            flagAdapter.notifyRemoved(position);
            scan.removeFlag(removed);
            Toast.makeText(requireContext(), R.string.tg_removed_toast, Toast.LENGTH_SHORT).show();
            renderResult(scan);
        })).attachToRecyclerView(recycler);
        binding.flagsContainer.addView(recycler);
        return recycler;
    }

    private void goNext() {
        NavHostFragment.findNavController(this).navigate(R.id.action_telegram_to_tariff);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
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
