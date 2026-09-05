package com.creditwise.app.data.local;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;

/** Persists the user's light/dark/system theme choice and applies it app-wide. */
public final class ThemeStore {

    private static final String PREFS = "creditwise_theme";
    private static final String KEY_MODE = "mode";

    public static final String SYSTEM = "system";
    public static final String LIGHT = "light";
    public static final String DARK = "dark";

    private ThemeStore() {}

    /** Call once, as early as possible (Application.onCreate), before any Activity is created. */
    public static void applyStartup(Context context) {
        AppCompatDelegate.setDefaultNightMode(toConstant(load(context)));
    }

    public static String load(Context context) {
        return prefs(context).getString(KEY_MODE, SYSTEM);
    }

    public static void save(Context context, String mode) {
        prefs(context).edit().putString(KEY_MODE, mode).apply();
        AppCompatDelegate.setDefaultNightMode(toConstant(mode));
    }

    private static int toConstant(String mode) {
        switch (mode) {
            case LIGHT: return AppCompatDelegate.MODE_NIGHT_NO;
            case DARK: return AppCompatDelegate.MODE_NIGHT_YES;
            default: return AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
