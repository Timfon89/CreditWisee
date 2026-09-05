package com.creditwise.app.ui;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.creditwise.app.data.model.TelegramFlag;
import com.creditwise.app.databinding.ItemTgFlagBinding;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** One category's worth of Telegram phrases — swipe right to mark "not about credit/finance". */
public class TelegramFlagAdapter extends RecyclerView.Adapter<TelegramFlagAdapter.VH> {

    private static final DateTimeFormatter DF = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final List<TelegramFlag> items;

    public TelegramFlagAdapter(List<TelegramFlag> items) {
        this.items = new ArrayList<>(items);
    }

    public TelegramFlag removeAt(int position) {
        return items.remove(position);
    }

    public void notifyRemoved(int position) {
        notifyItemRemoved(position);
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemTgFlagBinding b = ItemTgFlagBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new VH(b);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        TelegramFlag f = items.get(position);
        holder.binding.tvSnippet.setText("«" + f.snippet + "»");
        String meta = f.chatName;
        if (f.date != null) meta += " · " + f.date.format(DF);
        holder.binding.tvMeta.setText(meta);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static final class VH extends RecyclerView.ViewHolder {
        final ItemTgFlagBinding binding;

        VH(ItemTgFlagBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
