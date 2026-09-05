package com.creditwise.app.ui;

import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.transition.TransitionManager;

import com.creditwise.app.databinding.ItemAccordionSectionBinding;

/** One collapsible row-group on the result screen, tagged with a title + running value. */
public final class AccordionSection {

    private final ItemAccordionSectionBinding binding;
    private final ViewGroup transitionRoot;

    private AccordionSection(ItemAccordionSectionBinding binding, ViewGroup transitionRoot) {
        this.binding = binding;
        this.transitionRoot = transitionRoot;
    }

    public static AccordionSection inflate(ViewGroup container, ViewGroup transitionRoot,
                                           String title, String value, boolean startOpen) {
        LayoutInflater inflater = LayoutInflater.from(container.getContext());
        ItemAccordionSectionBinding b = ItemAccordionSectionBinding.inflate(inflater, container, false);
        AccordionSection section = new AccordionSection(b, transitionRoot);
        b.tvTitle.setText(title);
        b.tvValue.setText(value);
        section.setOpen(startOpen, false);
        b.header.setOnClickListener(v -> section.toggle());
        container.addView(b.getRoot());
        return section;
    }

    public View body() {
        return binding.body;
    }

    public void addBody(View child) {
        binding.body.addView(child);
    }

    private void toggle() {
        binding.header.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
        setOpen(binding.body.getVisibility() != View.VISIBLE, true);
    }

    private void setOpen(boolean open, boolean animate) {
        if (animate) TransitionManager.beginDelayedTransition(transitionRoot);
        binding.body.setVisibility(open ? View.VISIBLE : View.GONE);
        binding.ivChevron.animate().rotation(open ? 180f : 0f).setDuration(180).start();
    }
}
