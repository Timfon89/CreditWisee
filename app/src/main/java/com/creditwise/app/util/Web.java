package com.creditwise.app.util;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

public final class Web {

    private Web() {}

    /** Opens {@code url} in a browser / Custom Tab. Returns false if it could not be opened. */
    public static boolean open(Context context, String url) {
        if (url == null) return false;
        String trimmed = url.trim();
        if (trimmed.isEmpty()) {
            Toast.makeText(context, "Ссылка не настроена", Toast.LENGTH_SHORT).show();
            return false;
        }
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            trimmed = "https://" + trimmed;
        }
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(trimmed));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return true;
        } catch (ActivityNotFoundException e) {
            Toast.makeText(context, "Не найдено приложение для открытия ссылки", Toast.LENGTH_SHORT).show();
            return false;
        }
    }
}
