package com.creditwise.app.data.local;

import android.content.Context;
import android.content.SharedPreferences;

import com.creditwise.app.util.Hashing;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Fully local, on-device "account" — email + hashed password, or a placeholder Google entry.
 * There is no backend: nothing here is a real identity or authentication service.
 */
public final class LocalAuthStore {

    private static final String PREFS = "creditwise_auth";
    private static final String KEY_USERS = "users"; // JSON: {email: {salt, hash}}
    private static final String KEY_SESSION = "session_email";

    private final SharedPreferences prefs;

    public LocalAuthStore(Context context) {
        this.prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public enum Result { OK, ALREADY_EXISTS, NOT_FOUND, WRONG_PASSWORD, INVALID_INPUT }

    public Result register(String email, String password) {
        String key = normalize(email);
        if (key.isEmpty() || password.length() < 4) return Result.INVALID_INPUT;
        try {
            JSONObject users = usersJson();
            if (users.has(key)) return Result.ALREADY_EXISTS;
            String salt = Hashing.randomSaltHex();
            JSONObject entry = new JSONObject();
            entry.put("salt", salt);
            entry.put("hash", Hashing.hash(password, salt));
            users.put(key, entry);
            prefs.edit().putString(KEY_USERS, users.toString()).apply();
            setSession(key);
            return Result.OK;
        } catch (JSONException e) {
            return Result.INVALID_INPUT;
        }
    }

    public Result login(String email, String password) {
        String key = normalize(email);
        try {
            JSONObject users = usersJson();
            if (!users.has(key)) return Result.NOT_FOUND;
            JSONObject entry = users.getJSONObject(key);
            String expected = entry.getString("hash");
            String actual = Hashing.hash(password, entry.getString("salt"));
            if (!expected.equals(actual)) return Result.WRONG_PASSWORD;
            setSession(key);
            return Result.OK;
        } catch (JSONException e) {
            return Result.INVALID_INPUT;
        }
    }

    private JSONObject usersJson() {
        try {
            String raw = prefs.getString(KEY_USERS, "{}");
            return new JSONObject(raw);
        } catch (JSONException e) {
            return new JSONObject();
        }
    }

    private void setSession(String email) {
        prefs.edit().putString(KEY_SESSION, email).apply();
    }

    public String currentEmail() {
        return prefs.getString(KEY_SESSION, null);
    }

    public boolean isLoggedIn() {
        return currentEmail() != null;
    }

    public void logout() {
        prefs.edit().remove(KEY_SESSION).apply();
    }

    private static String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }
}
