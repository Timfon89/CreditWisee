package com.creditwise.app.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;

import com.creditwise.app.R;

/** Small colored pill labelling where one score factor's data came from. */
public final class SourceTag {

    public enum Type { BANK, TELEGRAM, SITE, APP, RISK }

    private SourceTag() {}

    public static TextView create(Context ctx, Type type) {
        TextView tv = new TextView(ctx);
        float d = ctx.getResources().getDisplayMetrics().density;
        int hPad = Math.round(8 * d);
        int vPad = Math.round(3 * d);
        tv.setPadding(hPad, vPad, hPad, vPad);
        tv.setTextSize(10.5f);
        tv.setTypeface(tv.getTypeface(), Typeface.BOLD);
        tv.setText(label(type));
        tv.setTextColor(textColor(ctx, type));

        Drawable bg = ContextCompat.getDrawable(ctx, R.drawable.bg_tag);
        if (bg != null) {
            bg = bg.mutate();
            DrawableCompat.setTint(bg, bgColor(ctx, type));
            tv.setBackground(bg);
        }
        tv.setLayoutParams(new ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return tv;
    }

    private static String label(Type type) {
        switch (type) {
            case BANK: return "Банковская выписка";
            case TELEGRAM: return "Telegram";
            case SITE: return "Сайт бюро";
            case RISK: return "Риск-фактор";
            default: return "Данные приложения";
        }
    }

    private static int bgColor(Context ctx, Type type) {
        switch (type) {
            case TELEGRAM: return ContextCompat.getColor(ctx, R.color.brand_emerald_tint);
            case SITE: return ContextCompat.getColor(ctx, R.color.score_mid_tint);
            case APP: return ContextCompat.getColor(ctx, R.color.brand_violet_tag_tint);
            case RISK: return ContextCompat.getColor(ctx, R.color.score_low_tint);
            default: return 0xFFE7EEF6; // muted navy tint for "bank statement"
        }
    }

    private static int textColor(Context ctx, Type type) {
        switch (type) {
            case TELEGRAM: return ContextCompat.getColor(ctx, R.color.brand_emerald_deep);
            case SITE: return ContextCompat.getColor(ctx, R.color.score_mid);
            case APP: return ContextCompat.getColor(ctx, R.color.brand_violet_tag);
            case RISK: return ContextCompat.getColor(ctx, R.color.score_low);
            default: return ContextCompat.getColor(ctx, R.color.brand_navy_2);
        }
    }
}
