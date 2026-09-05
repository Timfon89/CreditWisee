package com.creditwise.app.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.creditwise.app.R;

/** Swipe-left-to-delete for a RecyclerView row, with a red "trash" reveal behind the card. */
public class SwipeToDeleteCallback extends ItemTouchHelper.SimpleCallback {

    public interface OnSwipeListener {
        void onSwiped(int position);
    }

    private final OnSwipeListener listener;
    private final ColorDrawable background;
    private final Drawable icon;
    private final int iconMargin;

    public SwipeToDeleteCallback(Context context, OnSwipeListener listener) {
        super(0, ItemTouchHelper.LEFT);
        this.listener = listener;
        this.background = new ColorDrawable(ContextCompat.getColor(context, R.color.score_low));
        Drawable d = ContextCompat.getDrawable(context, R.drawable.ic_delete);
        if (d != null) {
            d = d.mutate();
            DrawableCompat.setTint(d, Color.WHITE);
        }
        this.icon = d;
        this.iconMargin = Math.round(20 * context.getResources().getDisplayMetrics().density);
    }

    @Override
    public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder,
                          @NonNull RecyclerView.ViewHolder target) {
        return false;
    }

    @Override
    public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
        listener.onSwiped(viewHolder.getBindingAdapterPosition());
    }

    @Override
    public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView recyclerView,
                            @NonNull RecyclerView.ViewHolder viewHolder,
                            float dX, float dY, int actionState, boolean isCurrentlyActive) {
        View item = viewHolder.itemView;
        if (dX < 0) {
            background.setBounds(item.getRight() + (int) dX, item.getTop(), item.getRight(), item.getBottom());
            background.draw(c);
            if (icon != null) {
                int iconTop = item.getTop() + (item.getHeight() - icon.getIntrinsicHeight()) / 2;
                int iconRight = item.getRight() - iconMargin;
                int iconLeft = iconRight - icon.getIntrinsicWidth();
                if (iconLeft > item.getRight() + dX) {
                    icon.setBounds(iconLeft, iconTop, iconRight, iconTop + icon.getIntrinsicHeight());
                    icon.draw(c);
                }
            }
        } else {
            background.setBounds(0, 0, 0, 0);
        }
        super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
    }

    @Override
    public float getSwipeEscapeVelocity(float defaultValue) {
        return defaultValue * 0.6f; // a bit easier to trigger — feels snappier
    }
}
