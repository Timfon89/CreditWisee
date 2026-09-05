package com.creditwise.app.ui;

import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.creditwise.app.R;
import com.creditwise.app.data.model.CreditCase;
import com.creditwise.app.data.model.ExpenseCategory;
import com.creditwise.app.databinding.ItemCreditCaseBinding;
import com.creditwise.app.util.Money;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class CreditCaseAdapter extends RecyclerView.Adapter<CreditCaseAdapter.VH> {

    public interface OnCaseClick {
        void onClick(CreditCase item);
    }

    private static final DateTimeFormatter DF = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final List<CreditCase> items = new ArrayList<>();
    private final OnCaseClick onClick;

    public CreditCaseAdapter(OnCaseClick onClick) {
        this.onClick = onClick;
        setHasStableIds(false);
    }

    public void submit(List<CreditCase> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    /** Called by {@link SwipeToDeleteCallback} once the swipe gesture completes. */
    public CreditCase removeAt(int position) {
        CreditCase removed = items.remove(position);
        notifyItemRemoved(position);
        return removed;
    }

    public void restore(int position, CreditCase item) {
        items.add(Math.min(position, items.size()), item);
        notifyItemInserted(position);
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemCreditCaseBinding b = ItemCreditCaseBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new VH(b);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        CreditCase c = items.get(position);
        holder.binding.tvLoanSummary.setText(Money.format(c.loanAmount) + " · " + c.termMonths + " мес.");
        holder.binding.tvDate.setText(c.createdAt.format(DF) + " · " + c.trustBand + " благонадёжность");
        holder.binding.tvApproval.setText(holder.itemView.getContext()
                .getString(R.string.home_item_approval, c.trustTotal));

        holder.binding.tvIndex.setText(c.approvalPercent + "%");
        int color = ContextCompat.getColor(holder.itemView.getContext(), ApprovalColors.colorRes(c.approvalBand));
        holder.binding.scoreBadge.setBackgroundTintList(ColorStateList.valueOf(color));

        holder.itemView.setOnClickListener(v -> onClick.onClick(c));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static String displayCategory(String enumName) {
        try {
            return ExpenseCategory.valueOf(enumName).displayName();
        } catch (IllegalArgumentException e) {
            return enumName;
        }
    }

    static final class VH extends RecyclerView.ViewHolder {
        final ItemCreditCaseBinding binding;

        VH(ItemCreditCaseBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
