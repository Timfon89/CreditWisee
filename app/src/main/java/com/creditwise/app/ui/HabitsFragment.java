package com.creditwise.app.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioButton;
import android.widget.RadioGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.navigation.fragment.NavHostFragment;

import com.creditwise.app.R;
import com.creditwise.app.data.local.CreditCaseStore;
import com.creditwise.app.data.local.LocalAuthStore;
import com.creditwise.app.data.model.HabitsQuestionnaire;
import com.creditwise.app.data.model.TrustworthinessScore;
import com.creditwise.app.databinding.FragmentHabitsBinding;
import com.creditwise.app.databinding.ItemHabitQuestionBinding;
import com.creditwise.app.domain.HabitsScorer;

import java.util.ArrayList;
import java.util.List;

/** A short, transparent self-assessment of money habits — see {@link HabitsQuestionnaire}. */
public class HabitsFragment extends BaseFragment {

    private FragmentHabitsBinding binding;
    private final List<RadioGroup> groups = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentHabitsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        String email = new LocalAuthStore(requireContext()).currentEmail();
        int[] existing = new CreditCaseStore(requireContext()).loadHabitsSelections(email);

        LayoutInflater inflater = LayoutInflater.from(requireContext());
        List<HabitsQuestionnaire.Question> questions = HabitsQuestionnaire.QUESTIONS;
        for (int qi = 0; qi < questions.size(); qi++) {
            HabitsQuestionnaire.Question q = questions.get(qi);
            ItemHabitQuestionBinding row = ItemHabitQuestionBinding.inflate(
                    inflater, binding.questionsContainer, false);
            row.tvQuestion.setText((qi + 1) + ". " + q.text);

            for (int oi = 0; oi < q.options.length; oi++) {
                RadioButton rb = new RadioButton(requireContext());
                rb.setId(View.generateViewId());
                rb.setText(q.options[oi]);
                rb.setTag(oi);
                rb.setPadding(rb.getPaddingLeft(), dp(4), rb.getPaddingRight(), dp(4));
                row.optionsGroup.addView(rb);
                if (existing != null && qi < existing.length && existing[qi] == oi) {
                    rb.setChecked(true);
                }
            }
            groups.add(row.optionsGroup);
            binding.questionsContainer.addView(row.getRoot());
        }

        binding.btnSubmit.setOnClickListener(v -> submit(email));
    }

    private void submit(String email) {
        int[] selections = new int[groups.size()];
        for (int i = 0; i < groups.size(); i++) {
            RadioGroup group = groups.get(i);
            int checkedId = group.getCheckedRadioButtonId();
            if (checkedId == -1) {
                binding.tvError.setVisibility(View.VISIBLE);
                return;
            }
            RadioButton checked = group.findViewById(checkedId);
            selections[i] = (int) checked.getTag();
        }
        binding.tvError.setVisibility(View.GONE);

        new CreditCaseStore(requireContext()).saveHabitsSelections(email, selections);
        int score = HabitsScorer.score(selections);
        int clamped = Math.max(-TrustworthinessScore.HABITS_CAP,
                Math.min(TrustworthinessScore.HABITS_CAP, score));

        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.habits_result_title)
                .setMessage(getString(R.string.habits_result_body, clamped))
                .setPositiveButton(android.R.string.ok, (d, w) ->
                        NavHostFragment.findNavController(this).popBackStack())
                .setCancelable(false)
                .show();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        groups.clear();
        binding = null;
    }
}
